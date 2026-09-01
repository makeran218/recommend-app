package com.makeran218.recommendtmdb

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.tvprovider.media.tv.Channel
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.HashMap
import java.util.concurrent.atomic.AtomicInteger

class LauncherChannels(private val context: Context) {
    companion object {
        private const val TAG = "LauncherChannels"

        // Official framework constants — do NOT hardcode these integers.
        // They map to internal TvContract database values that the launcher
        // uses to decide the tile container size.
        const val ASPECT_RATIO_2_3 = TvContractCompat.PreviewPrograms.ASPECT_RATIO_2_3
        const val ASPECT_RATIO_16_9 = TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9

        /**
         * Program type constants (from android.media.tv.TvContract.Programs).
         */
        const val TYPE_MOVIE = 1
        const val TYPE_TV_SHOW = 2

        /**
         * Sync all channels synchronously — waits for ALL items to be inserted
         * before returning. This prevents race conditions where the launcher
         * reads channels before all items are inserted.
         *
         * @param context Application context
         * @param itemsByCatalog Map of catalogKey -> ChannelItems
         */
        suspend fun syncAll(
            context: Context,
            itemsByCatalog: Map<String, List<ChannelItem>>
        ) {
            val launcher = LauncherChannels(context)
            try {
                launcher.syncChannels(itemsByCatalog)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync channels", e)
            }
        }
    }

    /**
     * Sync all catalogs while preserving existing channels.
     * Channels are identified by catalogId in appLinkIntentUri to avoid
     * recreation (which would require re-enabling in the launcher).
     */
    private suspend fun syncChannels(
        itemsByCatalog: Map<String, List<ChannelItem>>
    ) {
        val mutex = Mutex()
        var totalInserted = 0
        val totalItems = itemsByCatalog.values.sumOf { it.size }

        Log.d(TAG, "╔═══════════════════════════════════════════════════════╗")
        Log.d(TAG, "║              CHANNEL SYNC STARTED                     ║")
        Log.d(TAG, "╚═══════════════════════════════════════════════════════╝")
        Log.d(TAG, "Starting channel sync: ${itemsByCatalog.size} catalogs, $totalItems total items")

        // Collect all existing catalog keys so we can clean up stale channels later
        // Also delete old-format channels (using underscore) to avoid duplicates
        val existingChannels = context.contentResolver.query(
            TvContractCompat.Channels.CONTENT_URI,
            null, null, null, null
        )
        val existingCatalogKeys = mutableSetOf<String>()
        existingChannels?.use { cursor ->
            val uriIndex = cursor.getColumnIndex(TvContractCompat.Channels.COLUMN_APP_LINK_INTENT_URI)
            val idIndex = cursor.getColumnIndex(TvContractCompat.Channels._ID)
            while (cursor.moveToNext()) {
                val intentUri = cursor.getString(uriIndex)
                val channelId = cursor.getLong(idIndex)

                // Delete old-format channels (underscore separator)
                if (intentUri?.startsWith("channel://tvhome/") == true) {
                    val fullId = intentUri.substring("channel://tvhome/".length)
                    if (fullId.contains('_') && !fullId.contains('.')) {
                        // Old format: {catalogId}_{catalogType} — delete it
                        val channelUri = ContentUris.withAppendedId(
                            TvContractCompat.Channels.CONTENT_URI,
                            channelId
                        )
                        context.contentResolver.delete(channelUri, null, null)
                        Log.d(TAG, "MIGRATED: deleted old-format channel $intentUri")
                        continue
                    }
                }

                val catalogInfoParsed = extractCatalogInfo(intentUri)
                if (catalogInfoParsed != null) {
                    val (catalogId, catalogType) = catalogInfoParsed
                    // Normalize to new format: {catalogId}.{catalogType}
                    existingCatalogKeys.add("${catalogId}.${catalogType}")
                }
            }
        }

        val currentCatalogKeys = mutableSetOf<String>()

        for ((catalogKey, items) in itemsByCatalog) {
            val catalogInfo = parseCatalogKey(context, catalogKey) ?: continue
            if (items.isEmpty()) continue

            Log.d(TAG, "Syncing catalog: ${catalogInfo.catalogName} (${items.size} items)")

            syncCatalog(context, catalogInfo, items, mutex) {
                totalInserted++
                if (totalInserted % 10 == 0 || totalInserted == totalItems) {
                    Log.d(TAG, "Inserted $totalInserted / $totalItems items...")
                }
            }
            currentCatalogKeys.add("${catalogInfo.catalogId}.${catalogInfo.catalogType}")
        }

        // Clean up stale channels that are no longer in any catalog
        for (catalogKey in existingCatalogKeys) {
            if (catalogKey.isNotEmpty() && !currentCatalogKeys.contains(catalogKey)) {
                Log.d(TAG, "STALE CHANNEL FOUND: $catalogKey → will be deleted")
                deleteChannelByCatalogKey(context, catalogKey)
            }
        }

        Log.d(TAG, "╔═══════════════════════════════════════════════════════╗")
        Log.d(TAG, "║              CHANNEL SYNC COMPLETED                   ║")
        Log.d(TAG, "╚═══════════════════════════════════════════════════════╝")
        Log.d(TAG, "Channels synced: ${itemsByCatalog.size} catalogs, $totalInserted items inserted")
    }

    private data class CatalogInfo(
        val catalogId: String,
        val catalogName: String,
        val catalogType: String
    )

    private suspend fun parseCatalogKey(context: Context, catalogKey: String): CatalogInfo? {
        // New format: manifestUrl::uniqueId (e.g., manifestUrl::tmdb.trending.movie)
        val parts = catalogKey.split("::", limit = 2)
        if (parts.size != 2) return null

        val manifestUrl = parts[0]
        val uniqueId = parts[1]

        // Extract catalogId and catalogType from uniqueId (format: catalogId.catalogType)
        val lastDot = uniqueId.lastIndexOf('.')
        if (lastDot <= 0) return null
        val catalogId = uniqueId.substring(0, lastDot)
        val catalogType = uniqueId.substring(lastDot + 1)

        // Try to get catalog name from cache
        val cachedCatalogs = try {
            ManifestRepository.loadCachedCatalogs(context, manifestUrl)
        } catch (e: Exception) {
            null
        }

        val catalogInfo = cachedCatalogs?.find { it.catalogId == catalogId && it.catalogType == catalogType }

        return CatalogInfo(
            catalogId = catalogId,
            catalogName = catalogInfo?.catalogName ?: catalogId,
            catalogType = catalogInfo?.catalogType ?: catalogType
        )
    }

    /**
     * Sync a single catalog's items to the launcher.
     * Reuses existing channel if one with the same catalogId already exists.
     */
    private suspend fun syncCatalog(
        context: Context,
        catalogInfo: CatalogInfo,
        items: List<ChannelItem>,
        mutex: Mutex,
        onProgress: () -> Unit
    ) {
        Log.d(TAG, "═══════════════════════════════════════════════════════")
        Log.d(TAG, "SYNC CATALOG: ${catalogInfo.catalogId} | ${catalogInfo.catalogName} (${catalogInfo.catalogType})")

        // Build map of existing channels indexed by uniqueId "{catalogId}.{catalogType}"
        val channelMap = HashMap<String, Long>()
        val existingChannels = context.contentResolver.query(
            TvContractCompat.Channels.CONTENT_URI,
            null, null, null, null
        )
        Log.d(TAG, "EXISTING CHANNELS IN TV PROVIDER:")
        existingChannels?.use { cursor ->
            val idIndex = cursor.getColumnIndex(TvContractCompat.Channels._ID)
            val uriIndex = cursor.getColumnIndex(TvContractCompat.Channels.COLUMN_APP_LINK_INTENT_URI)
            val nameIndex = cursor.getColumnIndex(TvContractCompat.Channels.COLUMN_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val channelId = cursor.getLong(idIndex)
                val intentUri = cursor.getString(uriIndex)
                val displayName = cursor.getString(nameIndex)
                val catalogInfoParsed = extractCatalogInfo(intentUri)
                Log.d(TAG, "  Channel #$channelId | URI: $intentUri | Name: $displayName")
                if (catalogInfoParsed != null) {
                    val (catalogId, catalogType) = catalogInfoParsed
                    // Use dot format for new channels
                    channelMap["${catalogId}.${catalogType}"] = channelId
                }
            }
        }

        // Reuse existing channel or create new one
        val channelKey = "${catalogInfo.catalogId}.${catalogInfo.catalogType}"
        val isReused = channelMap.containsKey(channelKey)
        val channelId = channelMap[channelKey] ?: createChannel(catalogInfo)
        Log.d(
            TAG,
            "CHANNEL ${if (isReused) "REUSED" else "CREATED"}: channelId=$channelId | key=$channelKey"
        )

        // Ensure channel is browsable (re-affirm after each sync)
        TvContractCompat.requestChannelBrowsable(context, channelId)

        // Delete ALL existing programs in this channel to remove stale items
        // (e.g., from provider changes: Nuvio → Stremio changes URIs)
        // NOTE: TV provider doesn't allow selection clauses, so we query ALL programs
        // and filter client-side by channel_id
        try {
            val existingPrograms = context.contentResolver.query(
                TvContractCompat.PreviewPrograms.CONTENT_URI,
                arrayOf(
                    TvContractCompat.PreviewPrograms._ID,
                    TvContractCompat.PreviewPrograms.COLUMN_CHANNEL_ID
                ),
                null, // NO selection clause - TV provider rejects it
                null,
                null
            )
            existingPrograms?.use { cursor ->
                val idIndex = cursor.getColumnIndex(TvContractCompat.PreviewPrograms._ID)
                val channelIndex = cursor.getColumnIndex(TvContractCompat.PreviewPrograms.COLUMN_CHANNEL_ID)
                while (cursor.moveToNext()) {
                    val programId = cursor.getLong(idIndex)
                    val programChannelId = cursor.getLong(channelIndex)
                    // Only delete programs belonging to this channel
                    if (programChannelId == channelId) {
                        val programUri = ContentUris.withAppendedId(
                            TvContractCompat.PreviewPrograms.CONTENT_URI,
                            programId
                        )
                        context.contentResolver.delete(programUri, null, null)
                        Log.d(TAG, "Deleted old program $programId from channel $channelId")
                    }
                }
            }
            Log.d(TAG, "Cleared all programs for channel $channelId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear programs for channel $channelId", e)
        }

        for (item in items) {
            mutex.withLock {
                createProgram(context, channelId, catalogInfo, item)
            }
            onProgress()
            Log.d(TAG, "Created program: ${item.name} (type=${item.type})")
        }

        Log.d(TAG, "Completed catalog: ${catalogInfo.catalogName} (${items.size} items)")
    }

    private fun createChannel(catalogInfo: CatalogInfo): Long {
        val logoUri = Uri.Builder()
            .scheme("android.resource")
            .authority(context.packageName)
            .path(context.resources.getResourceEntryName(R.drawable.ic_launcher_foreground))
            .build()

        // Format channel title as "Type - Name" to prevent launcher from merging
        // channels with similar names (e.g., "Series - Disney+" vs "Movies - Disney+")
        // Strip type suffix from name if already present (e.g., "Disney+ (series)" -> "Disney+")
        val baseName = catalogInfo.catalogName
            .replace(Regex("\\s*\\(${catalogInfo.catalogType}\\)", RegexOption.IGNORE_CASE), "")
            .trim()
        val channelTitle = "${catalogInfo.catalogType.replaceFirstChar { it.uppercase() }} - $baseName"

        // Use unique description so launcher doesn't merge channels with same title
        val uniqueId = "${catalogInfo.catalogId}.${catalogInfo.catalogType}"
        val uniqueDescription = uniqueId

        val builder = Channel.Builder()
            .setType(TvContractCompat.Channels.TYPE_PREVIEW)
            .setDisplayName(channelTitle)
            .setDescription(uniqueDescription)
            .setAppLinkIconUri(logoUri)
            .setAppLinkIntentUri(Uri.parse("channel://tvhome/$uniqueId"))

        Log.d(
            TAG,
            "CREATE CHANNEL: title=$channelTitle | desc=$uniqueDescription | uri=channel://tvhome/$uniqueId"
        )

        val channelUri = context.contentResolver.insert(
            TvContractCompat.Channels.CONTENT_URI,
            builder.build().toContentValues()
        )

        val channelId = ContentUris.parseId(channelUri!!)
        TvContractCompat.requestChannelBrowsable(context, channelId)

        Log.d(TAG, "CREATED: channelId=$channelId")
        Log.d(TAG, "═══════════════════════════════════════════════════════")

        return channelId
    }

    /**
     * Extract catalogId from appLinkIntentUri (format: channel://tvhome/{uniqueId}).
     * uniqueId format: {catalogId}.{catalogType} (e.g., "tmdb.trending.movie")
     * Returns the catalogId (without the .{catalogType} suffix) if the URI format is recognized.
     * Returns empty string if the URI format is unrecognized.
     */
    private fun extractCatalogId(intentUri: String?, channelId: Long): String {
        if (intentUri?.startsWith("channel://tvhome/") == true) {
            val fullId = intentUri.substring("channel://tvhome/".length)
            // Strip the .{catalogType} suffix to get the base catalogId
            return fullId.substringBeforeLast('.')
        }
        // Fallback: unknown channel format, use channel ID as fallback
        return channelId.toString()
    }

    /**
     * Extract catalogId and catalogType from appLinkIntentUri.
     * Supports two formats:
     *   NEW: channel://tvhome/{catalogId}.{catalogType} (e.g., "tmdb.trending.movie")
     *   OLD: channel://tvhome/{catalogId}_{catalogType} (e.g., "tmdb.trending_movie")
     * Returns a pair of (catalogId, catalogType) or null if the URI format is unrecognized.
     */
    private fun extractCatalogInfo(intentUri: String?): Pair<String, String>? {
        if (intentUri?.startsWith("channel://tvhome/") != true) return null
        val fullId = intentUri.substring("channel://tvhome/".length)

        // Try NEW format first: {catalogId}.{catalogType}
        val lastDot = fullId.lastIndexOf('.')
        if (lastDot > 0) {
            val potentialType = fullId.substring(lastDot + 1)
            if (potentialType in listOf("movie", "series")) {
                return fullId.substring(0, lastDot) to potentialType
            }
        }

        // Fall back to OLD format: {catalogId}_{catalogType}
        val lastUnderscore = fullId.lastIndexOf('_')
        if (lastUnderscore > 0) {
            val potentialType = fullId.substring(lastUnderscore + 1)
            if (potentialType in listOf("movie", "series")) {
                return fullId.substring(0, lastUnderscore) to potentialType
            }
        }

        return null
    }

    private fun createProgram(
        context: Context,
        channelId: Long,
        catalogInfo: CatalogInfo,
        item: ChannelItem
    ) {
        val deepLinkUri = DeepLinks.buildChannelUri(item)
        Log.d(TAG, "Building program: ${item.name} -> URI: $deepLinkUri")

        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = deepLinkUri
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val programBuilder = PreviewProgram.Builder()
            .setChannelId(channelId)
            .setTitle(item.name)
            .setDescription(item.description ?: "")
            .setIntent(intent)
            .setType(if (item.type == "series") TYPE_TV_SHOW else TYPE_MOVIE)

        // Items with video → use wide (16:9), items without video → use poster (2:3)
        if (item.trailerYtId != null) {
            val wideUri = Uri.parse(item.backdropUrl)
            programBuilder
                .setPosterArtUri(wideUri)
                .setPosterArtAspectRatio(ASPECT_RATIO_16_9)
                .setThumbnailUri(wideUri)
                .setThumbnailAspectRatio(ASPECT_RATIO_16_9)

            val videoUri = Uri.parse("http://192.168.2.50/youtube.php?id=${item.trailerYtId}")
            programBuilder.setPreviewVideoUri(videoUri)
            Log.d(TAG, "  Wide + video: ${item.name} -> ytId=${item.trailerYtId} -> $videoUri")
        } else {
            val posterUri = Uri.parse(item.posterUrl)
            programBuilder
                .setPosterArtUri(posterUri)
                .setPosterArtAspectRatio(ASPECT_RATIO_2_3)
            Log.d(TAG, "  Poster (2:3): ${item.name}")
        }

        val program = programBuilder.build()

        context.contentResolver.insert(
            TvContractCompat.PreviewPrograms.CONTENT_URI,
            program.toContentValues()
        )
    }

    /**
     * Delete a specific channel by its catalog key (catalogId_catalogType).
     * Queries existing channels and finds the one matching the catalog key.
     */
    private fun deleteChannelByCatalogKey(context: Context, uniqueId: String) {
        try {
            val channels = context.contentResolver.query(
                TvContractCompat.Channels.CONTENT_URI,
                null, null, null, null
            )
            channels?.use { cursor ->
                val idIndex = cursor.getColumnIndex(TvContractCompat.Channels._ID)
                val uriIndex = cursor.getColumnIndex(TvContractCompat.Channels.COLUMN_APP_LINK_INTENT_URI)
                while (cursor.moveToNext()) {
                    val channelId = cursor.getLong(idIndex)
                    val intentUri = cursor.getString(uriIndex)
                    if (intentUri == "channel://tvhome/$uniqueId") {
                        val channelUri = ContentUris.withAppendedId(
                            TvContractCompat.Channels.CONTENT_URI,
                            channelId
                        )
                        context.contentResolver.delete(channelUri, null, null)
                        Log.d(TAG, "Deleted stale channel: $uniqueId")
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete channel for catalog $uniqueId", e)
        }
    }
}
