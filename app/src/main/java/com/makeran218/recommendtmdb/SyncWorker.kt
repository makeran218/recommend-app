package com.makeran218.recommendtmdb

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "SyncWorker"
        const val WORK_NAME = "tv_home_sync"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting sync...")

        try {
            val context = applicationContext

            // Get playback provider setting
            val settings = AppPreferences.readPreferences(context).first()
            DeepLinks.setProvider(DeepLinks.getProvider(settings.playbackProvider))

            // Determine display type from settings
            val displayType = when (settings.displayType) {
                "WIDE" -> DisplayType.WIDE
                else -> DisplayType.POSTER
            }

            // Get enabled catalogs
            val enabledCatalogs = ManifestRepository.readEnabledCatalogs(context).first()
            if (enabledCatalogs.isEmpty()) {
                Log.w(TAG, "No catalogs enabled. Sync skipped.")
                return Result.failure()
            }

            Log.d(TAG, "Syncing ${enabledCatalogs.size} catalog(s)...")

            // Fetch all enabled catalogs sequentially
            val itemsByCatalog = fetchCatalogData(enabledCatalogs)

            // Filter out empty results
            val filteredItems = itemsByCatalog.filterValues { it.isNotEmpty() }

            if (filteredItems.isEmpty()) {
                Log.w(TAG, "No data fetched from any catalog.")
                return Result.failure()
            }

            // Update launcher channels
            LauncherChannels.syncAll(
                context,
                filteredItems,
                displayType
            )

            // Cache all catalog items
            for ((catalogKey, items) in filteredItems) {
                ManifestRepository.cacheCatalogItems(context, catalogKey, items)
            }

            // Update last sync time
            ManifestRepository.setLastSyncTime(context, System.currentTimeMillis())

            Log.d(
                TAG,
                "Sync completed. Catalogs: ${filteredItems.size}, Display: $displayType"
            )
            return Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            return Result.retry()
        }
    }

    /**
     * Fetch data for all enabled catalogs sequentially (one at a time).
     */
    private suspend fun fetchCatalogData(
        enabledCatalogs: Set<String>
    ): Map<String, List<ChannelItem>> {
        val result = mutableMapOf<String, List<ChannelItem>>()
        for (catalogKey in enabledCatalogs) {
            result[catalogKey] = fetchCatalogItems(catalogKey)
        }
        return result
    }

    /**
     * Fetch items for a single catalog.
     * Tries cache first, then falls back to network fetch.
     */
    private suspend fun fetchCatalogItems(catalogKey: String): List<ChannelItem> {
        return try {
            // Try cache first
            val cached = ManifestRepository.loadCachedCatalogItems(applicationContext, catalogKey)
            if (cached != null) {
                Log.d(TAG, "Catalog $catalogKey: loaded from cache (${cached.size} items)")
                return cached
            }

            // Parse the catalog key to get manifest URL, type, and catalog ID
            val (manifestUrl, catalogType, catalogId) = parseCatalogKey(catalogKey)
            if (manifestUrl == null || catalogType == null || catalogId == null) {
                Log.w(TAG, "Invalid catalog key: $catalogKey")
                return emptyList()
            }

            // Fetch manifest to get base URL
            val baseUrl = XperienceClient.extractBaseUrl(manifestUrl)
            val catalogUrl = XperienceClient.buildCatalogUrl(baseUrl, catalogType, catalogId)

            Log.d(TAG, "Fetching catalog: $catalogUrl")
            val response = XperienceClient.fetchCatalog(catalogUrl)

            // Convert MetaItems to ChannelItems
            val items = response.metas.map { meta ->
                ChannelItem(
                    id = meta.id,
                    type = meta.type,
                    name = meta.name,
                    poster = meta.poster,
                    background = meta.background,
                    description = meta.description,
                    releaseInfo = meta.releaseInfo,
                    released = meta.released,
                    imdb_id = meta.imdb_id,
                    status = meta.status,
                    runtime = meta.runtime,
                    imdbRating = meta.imdbRating,
                    genres = meta.genres,
                    posterFallback = meta.posterFallback,
                    logo = meta.logo
                )
            }

            Log.d(TAG, "Catalog $catalogId: fetched ${items.size} items")
            items

        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch catalog: $catalogKey", e)
            emptyList()
        }
    }

    /**
     * Parse catalog key to extract manifest URL, catalog type, and catalog ID.
     * Key format: "{manifestUrl}::{catalogType}::{catalogId}"
     */
    private fun parseCatalogKey(catalogKey: String): Triple<String?, String?, String?> {
        val parts = catalogKey.split("::", limit = 3)
        if (parts.size != 3) return Triple(null, null, null)
        return Triple(parts[0], parts[1], parts[2])
    }
}
