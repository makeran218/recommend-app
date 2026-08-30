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
         */
        suspend fun syncAll(
            context: Context,
            itemsByCatalog: Map<String, List<ChannelItem>>,
            displayType: DisplayType
        ) {
            val launcher = LauncherChannels(context)
            try {
                launcher.syncChannels(itemsByCatalog, displayType)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync channels", e)
            }
        }
    }

    /**
     * Delete all existing channels first, then insert all items for all catalogs.
     */
    private suspend fun syncChannels(
        itemsByCatalog: Map<String, List<ChannelItem>>,
        displayType: DisplayType
    ) {
        val mutex = Mutex()
        var totalInserted = 0
        val totalItems = itemsByCatalog.values.sumOf { it.size }

        Log.d(TAG, "Starting channel sync: ${itemsByCatalog.size} catalogs, $totalItems total items")

        // Delete all existing channels first
        deleteAllChannels()

        for ((catalogKey, items) in itemsByCatalog) {
            val catalogInfo = parseCatalogKey(context, catalogKey) ?: continue
            if (items.isEmpty()) continue

            Log.d(TAG, "Syncing catalog: ${catalogInfo.catalogName} (${items.size} items)")

            syncCatalog(context, catalogInfo, items, displayType, mutex) {
                totalInserted++
                if (totalInserted % 10 == 0 || totalInserted == totalItems) {
                    Log.d(TAG, "Inserted $totalInserted / $totalItems items...")
                }
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
     */
    private suspend fun syncCatalog(
        context: Context,
        catalogInfo: CatalogInfo,
        items: List<ChannelItem>,
        displayType: DisplayType,
        mutex: Mutex,
        onProgress: () -> Unit
    ) {
        val channelId = createChannel(catalogInfo)

        for (item in items) {
            mutex.withLock {
                createProgram(channelId, catalogInfo, item, displayType)
            }
            onProgress()
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

    private fun createProgram(
        channelId: Long,
        catalogInfo: CatalogInfo,
        item: ChannelItem,
        displayType: DisplayType
    ) {
        val deepLinkUri = DeepLinks.buildChannelUri(item)

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

    private fun deleteAllChannels() {
        try {
            // Query all channels and delete by ID (more reliable than input-based delete)
            val allChannels = context.contentResolver.query(
                TvContractCompat.Channels.CONTENT_URI,
                null, null, null, null
            )
            allChannels?.use { cursor ->
                val idIndex = cursor.getColumnIndex(TvContractCompat.Channels._ID)
                while (cursor.moveToNext()) {
                    val channelId = cursor.getLong(idIndex)
                    val channelUri = ContentUris.withAppendedId(TvContractCompat.Channels.CONTENT_URI, channelId)
                    context.contentResolver.delete(channelUri, null, null)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete channels", e)
        }
    }
}

enum class DisplayType {
    POSTER,  // Vertical poster (2:3)
    WIDE     // Wide landscape (16:9)
}
