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
         * @param catalogDisplayTypes Map of catalogKey -> resolved DisplayType
         */
        suspend fun syncAll(
            context: Context,
            itemsByCatalog: Map<String, List<ChannelItem>>,
            catalogDisplayTypes: Map<String, DisplayType>
        ) {
            val launcher = LauncherChannels(context)
            try {
                launcher.syncChannels(itemsByCatalog, catalogDisplayTypes)
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
        itemsByCatalog: Map<String, List<ChannelItem>>,
        catalogDisplayTypes: Map<String, DisplayType>
    ) {
        val mutex = Mutex()
        var totalInserted = 0
        val totalItems = itemsByCatalog.values.sumOf { it.size }

        Log.d(TAG, "Starting channel sync: ${itemsByCatalog.size} catalogs, $totalItems total items")
        Log.d(TAG, "Catalog display types: $catalogDisplayTypes")

        // Collect all existing catalog IDs so we can clean up stale channels later
        val existingChannels = context.contentResolver.query(
            TvContractCompat.Channels.CONTENT_URI,
            null, null, null, null
        )
        val existingCatalogIds = mutableSetOf<String>()
        existingChannels?.use { cursor ->
            val idIndex = cursor.getColumnIndex(TvContractCompat.Channels._ID)
            val uriIndex = cursor.getColumnIndex(TvContractCompat.Channels.COLUMN_APP_LINK_INTENT_URI)
            while (cursor.moveToNext()) {
                val channelId = cursor.getLong(idIndex)
                val intentUri = cursor.getString(uriIndex)
                existingCatalogIds.add(extractCatalogId(intentUri, channelId))
            }
        }

        val currentCatalogIds = mutableSetOf<String>()

        for ((catalogKey, items) in itemsByCatalog) {
            val catalogInfo = parseCatalogKey(context, catalogKey) ?: continue
            if (items.isEmpty()) continue

            val displayType = catalogDisplayTypes[catalogKey] ?: DisplayType.POSTER
            Log.d(TAG, "Syncing catalog: ${catalogInfo.catalogName} (${items.size} items), displayType=$displayType")

            syncCatalog(context, catalogInfo, items, displayType, mutex) {
                totalInserted++
                if (totalInserted % 10 == 0 || totalInserted == totalItems) {
                    Log.d(TAG, "Inserted $totalInserted / $totalItems items...")
                }
            }
            currentCatalogIds.add(catalogInfo.catalogId)
        }

        // Clean up stale channels that are no longer in any catalog
        for (catalogId in existingCatalogIds) {
            if (catalogId.isNotEmpty() && !currentCatalogIds.contains(catalogId)) {
                deleteChannelByCatalogId(context, catalogId)
            }
        }

        Log.d(TAG, "Channels synced: ${itemsByCatalog.size} catalogs, $totalInserted items inserted")
    }

    private data class CatalogInfo(
        val catalogId: String,
        val catalogName: String,
        val catalogType: String
    )

    private suspend fun parseCatalogKey(context: Context, catalogKey: String): CatalogInfo? {
        val parts = catalogKey.split("::", limit = 3)
        if (parts.size != 3) return null

        val manifestUrl = parts[0]
        val catalogType = parts[1]
        val catalogId = parts[2]

        // Try to get catalog name from cache
        val cachedCatalogs = try {
            ManifestRepository.loadCachedCatalogs(context, manifestUrl)
        } catch (e: Exception) {
            null
        }

        val catalogInfo = cachedCatalogs?.find { it.catalogId == catalogId }

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
        displayType: DisplayType,
        mutex: Mutex,
        onProgress: () -> Unit
    ) {
        // Build map of existing channels indexed by catalogId
        val channelMap = HashMap<String, Long>()
        val existingChannels = context.contentResolver.query(
            TvContractCompat.Channels.CONTENT_URI,
            null, null, null, null
        )
        existingChannels?.use { cursor ->
            val idIndex = cursor.getColumnIndex(TvContractCompat.Channels._ID)
            val uriIndex = cursor.getColumnIndex(TvContractCompat.Channels.COLUMN_APP_LINK_INTENT_URI)
            while (cursor.moveToNext()) {
                val channelId = cursor.getLong(idIndex)
                val intentUri = cursor.getString(uriIndex)
                val catalogId = extractCatalogId(intentUri, channelId)
                if (catalogId.isNotEmpty()) {
                    channelMap[catalogId] = channelId
                }
            }
        }

        // Reuse existing channel or create new one
        val channelId = channelMap[catalogInfo.catalogId] ?: createChannel(catalogInfo)

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
                createProgram(context, channelId, catalogInfo, item, displayType)
            }
            onProgress()
            Log.d(TAG, "Created program: ${item.name} (type=${item.type}, displayType=$displayType)")
        }

        Log.d(TAG, "Completed catalog: ${catalogInfo.catalogName} (${items.size} items)")
    }

    private fun createChannel(catalogInfo: CatalogInfo): Long {
        val logoUri = Uri.Builder()
            .scheme("android.resource")
            .authority(context.packageName)
            .path(context.resources.getResourceEntryName(R.drawable.ic_launcher_foreground))
            .build()

        val builder = Channel.Builder()
            .setType(TvContractCompat.Channels.TYPE_PREVIEW)
            .setDisplayName(catalogInfo.catalogName)
            .setDescription(catalogInfo.catalogType)
            .setAppLinkIconUri(logoUri)
            .setAppLinkIntentUri(Uri.parse("channel://tvhome/${catalogInfo.catalogId}"))

        val channelUri = context.contentResolver.insert(
            TvContractCompat.Channels.CONTENT_URI,
            builder.build().toContentValues()
        )

        val channelId = ContentUris.parseId(channelUri!!)
        TvContractCompat.requestChannelBrowsable(context, channelId)

        return channelId
    }

    /**
     * Extract catalogId from appLinkIntentUri (format: channel://tvhome/{catalogId}).
     * Returns empty string if the URI format is unrecognized.
     */
    private fun extractCatalogId(intentUri: String?, channelId: Long): String {
        if (intentUri?.startsWith("channel://tvhome/") == true) {
            return intentUri.substring("channel://tvhome/".length)
        }
        // Fallback: unknown channel format, use channel ID as fallback
        return channelId.toString()
    }

    private fun createProgram(
        context: Context,
        channelId: Long,
        catalogInfo: CatalogInfo,
        item: ChannelItem,
        displayType: DisplayType
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

        when (displayType) {
            DisplayType.POSTER -> {
                val posterUri = Uri.parse(item.posterUrl)
                programBuilder
                    .setPosterArtUri(posterUri)
                    .setPosterArtAspectRatio(ASPECT_RATIO_2_3)
            }

            DisplayType.WIDE -> {
                val wideUri = Uri.parse(item.backdropUrl)
                programBuilder
                    .setPosterArtUri(wideUri)
                    .setPosterArtAspectRatio(ASPECT_RATIO_16_9)
                    .setThumbnailUri(wideUri)
                    .setThumbnailAspectRatio(ASPECT_RATIO_16_9)
                    .setPreviewVideoUri(wideUri)
            }
        }

        val program = programBuilder.build()

        context.contentResolver.insert(
            TvContractCompat.PreviewPrograms.CONTENT_URI,
            program.toContentValues()
        )
    }

    /**
     * Delete a specific channel by its catalogId.
     * Queries existing channels and finds the one matching the catalogId.
     */
    private fun deleteChannelByCatalogId(context: Context, catalogId: String) {
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
                    if (intentUri == "channel://tvhome/$catalogId") {
                        val channelUri = ContentUris.withAppendedId(
                            TvContractCompat.Channels.CONTENT_URI,
                            channelId
                        )
                        context.contentResolver.delete(channelUri, null, null)
                        Log.d(TAG, "Deleted stale channel: $catalogId")
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete channel for catalog $catalogId", e)
        }
    }
}

enum class DisplayType {
    POSTER,  // Vertical poster (2:3)
    WIDE     // Wide landscape (16:9)
}
