package com.makeran218.recommendtmdb

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "SyncWorker"
        const val WORK_NAME = "tmdb_sync_worker"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting TMDB sync...")

        try {
            val context = applicationContext
            val settings = AppPreferences.readPreferences(context).first()
            val apiKey = settings.apiKey

            if (!settings.hasApiKey()) {
                Log.w(TAG, "TMDB API key not set. Sync skipped.")
                return Result.failure()
            }

            // Set the playback provider
            DeepLinks.setProvider(DeepLinks.getProvider(settings.playbackProvider))

            // Determine display type from settings
            val displayType = when (settings.displayType) {
                "WIDE" -> DisplayType.WIDE
                else -> DisplayType.POSTER
            }

            // Fetch all enabled categories in parallel
            val itemsByCategory = fetchCategoryData(apiKey, settings.enabledCategories)

            // Update launcher channels with display type
            LauncherChannels.syncAll(context, itemsByCategory, displayType, kotlinx.coroutines.MainScope())

            // Update JSON cache so the UI shows fresh data
            val cachedRows = itemsByCategory.mapNotNull { (key, items) ->
                val category = Category.values().find { it.key == key } ?: return@mapNotNull null
                CategoryRow(category, items)
            }
            if (cachedRows.isNotEmpty()) {
                AppPreferences.saveCachedItems(context, cachedRows)
            }

            // Update last sync time
            AppPreferences.setLastSyncTime(context, System.currentTimeMillis())

            Log.d(TAG, "Sync completed. Categories: ${itemsByCategory.size}, Display: $displayType")
            return Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            return Result.retry()
        }
    }

    private suspend fun fetchCategoryData(
        apiKey: String,
        enabledCategories: Set<String>
    ): Map<String, List<TmdbItem>> {
        return coroutineScope {
            enabledCategories.associateWith { categoryKey ->
                async { fetchCategory(apiKey, categoryKey) }.await()
            }
        }
    }

    private suspend fun fetchCategory(apiKey: String, categoryKey: String): List<TmdbItem> {
        return try {
            val category = Category.values().find { it.key == categoryKey } ?: return emptyList()

            val response = when (categoryKey) {
                // TMDB rows
                Category.TRENDING_MOVIES.key -> TmdbClient.api.getTrending("movie", "week", apiKey)
                Category.TRENDING_TV.key -> TmdbClient.api.getTrending("tv", "week", apiKey)
                Category.LATEST_MOVIES.key -> TmdbClient.api.discoverLatestMovies(apiKey)
                Category.LATEST_TV.key -> TmdbClient.api.discoverTvShows(apiKey, "first_air_date.desc")
                Category.POPULAR_MOVIES.key -> TmdbClient.api.discoverMovies(apiKey, "popularity.desc")
                Category.POPULAR_TV.key -> TmdbClient.api.discoverTvShows(apiKey, "popularity.desc")
                // Netflix rows
                Category.NETFLIX_POPULAR_MOVIES.key -> TmdbClient.api.discoverNetflixPopularMovies(apiKey)
                Category.NETFLIX_POPULAR_TV.key -> TmdbClient.api.discoverNetflixPopularTv(apiKey)
                Category.NETFLIX_NEW_MOVIES.key -> TmdbClient.api.discoverNetflixNewMovies(apiKey)
                Category.NETFLIX_NEW_TV.key -> TmdbClient.api.discoverNetflixNewTv(apiKey)
                else -> TmdbListResponse(0, 0, 0, emptyList())
            }

            // Filter out unreleased items (trending can include upcoming titles)
            response.results
                .filter { it.isReleased }
                .take(20)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch category: $categoryKey", e)
            emptyList()
        }
    }
}
