package com.makeran218.recommendtmdb

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull

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
            for (key in enabledCatalogs) {
                Log.d(TAG, "  ENABLED: $key")
            }

            // Fetch all enabled catalogs sequentially
            val itemsByCatalog = fetchCatalogData(enabledCatalogs)

            // Filter out empty results
            val filteredItems = itemsByCatalog.filterValues { it.isNotEmpty() }

            if (filteredItems.isEmpty()) {
                Log.w(TAG, "No data fetched from any catalog.")
                return Result.failure()
            }

            // Resolve per-catalog display types (catalog-specific override or global fallback)
            val catalogDisplayTypes = resolveCatalogDisplayTypes(context, filteredItems.keys, displayType)

            // Update launcher channels
            LauncherChannels.syncAll(
                context,
                filteredItems,
                catalogDisplayTypes
            )

            // Cache all catalog items
            for ((catalogKey, items) in filteredItems) {
                ManifestRepository.cacheCatalogItems(context, catalogKey, items)
            }
            android.util.Log.d("Cache", "Sync complete. All caches refreshed with current settings.")

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
     * Always fetches fresh data from network to ensure current settings are used.
     */
    private suspend fun fetchCatalogItems(catalogKey: String): List<ChannelItem> {
        return try {
            Log.d(TAG, "Fetching catalog from network: $catalogKey")

            // Parse the catalog key to get manifest URL and uniqueId
            val (manifestUrl, uniqueId) = parseCatalogKey(catalogKey)
            if (manifestUrl == null || uniqueId == null) {
                Log.w(TAG, "Invalid catalog key: $catalogKey")
                return emptyList()
            }

            // Extract catalogId and catalogType from uniqueId (format: catalogId.catalogType)
            val lastDot = uniqueId.lastIndexOf('.')
            if (lastDot <= 0) {
                Log.w(TAG, "Invalid uniqueId format: $uniqueId")
                return emptyList()
            }
            val catalogId = uniqueId.substring(0, lastDot)
            val catalogType = uniqueId.substring(lastDot + 1)

            // Fetch manifest to get base URL
            val baseUrl = XperienceClient.extractBaseUrl(manifestUrl)
            val catalogUrl = XperienceClient.buildCatalogUrl(baseUrl, catalogType, catalogId)

            Log.d(TAG, "Fetching catalog URL: $catalogUrl")
            val response = XperienceClient.fetchCatalog(catalogUrl)

            // Convert MetaItems to ChannelItems
            val items = response.metas.map { meta ->
                // Extract first ytId from trailerStreams
                val trailerYtId = meta.trailerStreams?.firstOrNull()?.ytId
                ChannelItem(
                    id = meta.id,
                    type = meta.type,
                    name = meta.name,
                    poster = meta.poster,
                    background = meta.background,
                    landscapePoster = meta.landscapePoster,
                    description = meta.description,
                    releaseInfo = meta.releaseInfo,
                    released = meta.released,
                    imdb_id = meta.imdb_id,
                    status = meta.status,
                    runtime = meta.runtime,
                    imdbRating = meta.imdbRating?.toDoubleOrNull(),
                    genres = meta.genres,
                    posterFallback = meta.posterFallback,
                    logo = meta.logo,
                    trailerYtId = trailerYtId
                )
            }

            Log.d(TAG, "Catalog $catalogId: fetched ${items.size} items from network")
            items

        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch catalog: $catalogKey", e)
            emptyList()
        }
    }

    /**
     * Parse catalog key to extract manifest URL and uniqueId.
     * Key format: "{manifestUrl}::{uniqueId}" where uniqueId = "{catalogId}.{catalogType}"
     */
    private fun parseCatalogKey(catalogKey: String): Pair<String?, String?> {
        val parts = catalogKey.split("::", limit = 2)
        if (parts.size != 2) return Pair(null, null)
        return Pair(parts[0], parts[1])
    }

    /**
     * Resolve display type for each catalog.
     * Uses catalog-specific setting if set, otherwise falls back to global displayType.
     */
    private suspend fun resolveCatalogDisplayTypes(
        context: Context,
        catalogKeys: Set<String>,
        globalDisplayType: DisplayType
    ): Map<String, DisplayType> {
        val catalogDisplayTypes = mutableMapOf<String, DisplayType>()

        // Load per-catalog overrides
        val overrides = ManifestRepository.readCatalogDisplayTypes(context).firstOrNull() ?: emptyMap()

        for (catalogKey in catalogKeys) {
            val override = overrides[catalogKey]
            val resolved = when (override) {
                "POSTER" -> DisplayType.POSTER
                "WIDE" -> DisplayType.WIDE
                else -> globalDisplayType // DEFAULT falls back to global
            }
            catalogDisplayTypes[catalogKey] = resolved
            Log.d(TAG, "Catalog $catalogKey -> displayType=$resolved (override=$override)")
        }

        return catalogDisplayTypes
    }
}
