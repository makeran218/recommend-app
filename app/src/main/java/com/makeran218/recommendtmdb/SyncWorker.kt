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

            Log.d(
                TAG,
                "Settings: posterProvider=${settings.posterProvider}, displayType=${settings.displayType}, itemsPerCategory=${settings.itemsPerCategory}"
            )

            // Set the playback provider
            DeepLinks.setProvider(DeepLinks.getProvider(settings.playbackProvider))

            // Determine display type from settings
            val displayType = when (settings.displayType) {
                "WIDE" -> DisplayType.WIDE
                else -> DisplayType.POSTER
            }

            // Fetch all enabled categories in parallel
            var itemsByCategory = fetchCategoryData(apiKey, settings.enabledCategories, settings.itemsPerCategory)

            // If using bttr.cc posters, enrich items with IMDb IDs
            if (settings.posterProvider == "bttr") {
                Log.d(TAG, "Poster provider is bttr — fetching IMDb IDs for enrichment")
                itemsByCategory = enrichWithImdbIds(apiKey, itemsByCategory)
            } else {
                Log.d(TAG, "Poster provider is ${settings.posterProvider} — skipping IMDb enrichment")
            }

            // Update launcher channels with display type and poster provider
            // This is now a suspend function — waits for ALL inserts to complete
            LauncherChannels.syncAll(
                context,
                itemsByCategory,
                displayType,
                settings.posterProvider
            )

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

            Log.d(
                TAG,
                "Sync completed. Categories: ${itemsByCategory.size}, Display: $displayType, Poster: ${settings.posterProvider}, Items: ${settings.itemsPerCategory}"
            )
            return Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            return Result.retry()
        }
    }

    private suspend fun fetchCategoryData(
        apiKey: String,
        enabledCategories: Set<String>,
        itemsPerCategory: Int
    ): Map<String, List<TmdbItem>> {
        return coroutineScope {
            enabledCategories.associateWith { categoryKey ->
                async { fetchCategory(apiKey, categoryKey, itemsPerCategory) }.await()
            }
        }
    }

    private suspend fun fetchCategory(
        apiKey: String,
        categoryKey: String,
        itemsPerCategory: Int
    ): List<TmdbItem> {
        return try {
            val category = Category.values().find { it.key == categoryKey } ?: return emptyList()

            val totalPages = TmdbClient.pagesNeeded(itemsPerCategory)
            val shouldFetchMultiplePages = totalPages > 1

            val results = if (shouldFetchMultiplePages) {
                // Fetch multiple pages and combine
                TmdbClient.fetchMultiplePages(
                    { page -> fetchSinglePage(apiKey, categoryKey, page) },
                    totalPages
                )
            } else {
                fetchSinglePage(apiKey, categoryKey, 1).results
            }

            Log.d(TAG, "Category $categoryKey: fetched ${results.size} items from $totalPages page(s)")

            // Filter out unreleased items (trending can include upcoming titles)
            val filteredResults = results.filter { it.isReleased }.take(itemsPerCategory)
            if (filteredResults.size < itemsPerCategory) {
                Log.w(
                    TAG,
                    "Category $categoryKey: only ${filteredResults.size} items available (requested $itemsPerCategory)"
                )
            }
            filteredResults
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch category: $categoryKey", e)
            emptyList()
        }
    }

    /**
     * Fetch a single page for a category.
     * Used internally for multi-page fetching.
     */
    private suspend fun fetchSinglePage(apiKey: String, categoryKey: String, page: Int): TmdbListResponse {
        return try {
            val response = when (categoryKey) {
                // TMDB rows
                Category.TRENDING_MOVIES.key -> TmdbClient.api.getTrending("movie", "week", apiKey, page)
                Category.TRENDING_TV.key -> TmdbClient.api.getTrending("tv", "week", apiKey, page)
                Category.LATEST_MOVIES.key -> TmdbClient.api.discoverLatestMovies(apiKey, page = page)
                Category.LATEST_TV.key -> TmdbClient.api.discoverTvShows(apiKey, "first_air_date.desc", page = page)
                Category.POPULAR_MOVIES.key -> TmdbClient.api.discoverMovies(apiKey, "popularity.desc", page = page)
                Category.POPULAR_TV.key -> TmdbClient.api.discoverTvShows(apiKey, "popularity.desc", page = page)
                // Netflix rows
                Category.NETFLIX_POPULAR_MOVIES.key -> TmdbClient.api.discoverNetflixPopularMovies(apiKey, page = page)
                Category.NETFLIX_POPULAR_TV.key -> TmdbClient.api.discoverNetflixPopularTv(apiKey, page = page)
                Category.NETFLIX_NEW_MOVIES.key -> TmdbClient.api.discoverNetflixNewMovies(apiKey, page = page)
                Category.NETFLIX_NEW_TV.key -> TmdbClient.api.discoverNetflixNewTv(apiKey, page = page)
                else -> TmdbListResponse(0, 0, 0, emptyList())
            }
            if (page > 1) {
                Log.d(TAG, "Fetched page $page of $categoryKey (${response.results.size} items)")
            }
            response
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch page $page of $categoryKey", e)
            TmdbListResponse(page, 0, 0, emptyList())
        }
    }

    /**
     * Enrich items with IMDb IDs by fetching external_ids from TMDB.
     * This is needed for bttr.cc poster URLs which require IMDb IDs.
     * Falls back gracefully if the API call fails or returns no IMDb ID.
     */
    private suspend fun enrichWithImdbIds(
        apiKey: String,
        itemsByCategory: Map<String, List<TmdbItem>>
    ): Map<String, List<TmdbItem>> {
        var successCount = 0
        var failCount = 0

        return coroutineScope {
            itemsByCategory.mapValues { (categoryKey, items) ->
                async {
                    val enrichedItems = items.map { item ->
                        val imdbId = try {
                            val mediaType = if (item.title != null) "movie" else "tv"
                            val externalIds = TmdbClient.api.getExternalIds(
                                mediaType,
                                item.id,
                                apiKey
                            )
                            externalIds.imdb_id
                        } catch (e: Exception) {
                            // If we can't fetch external IDs, continue without IMDb ID
                            // The poster will fall back to TMDB automatically
                            Log.w(TAG, "Failed to fetch IMDb ID for ${item.displayName}: ${e.message}")
                            null
                        }

                        if (imdbId != null) {
                            Log.d(TAG, "Found IMDb ID for ${item.displayName}: $imdbId")
                            successCount++
                        } else {
                            Log.d(
                                TAG,
                                "No IMDb ID for ${item.displayName} (TMDB ID: ${item.id}) - will use TMDB poster"
                            )
                            failCount++
                        }

                        // Always return the item, even without IMDb ID
                        item.copy(imdb_id = imdbId)
                    }

                    Log.d(
                        TAG,
                        "Category $categoryKey: ${items.size} items → ${enrichedItems.size} items after enrichment"
                    )
                    enrichedItems
                }.await()
            }
        }.also {
            val totalItems = it.values.sumOf { list -> list.size }
            Log.d(
                TAG,
                "IMDb enrichment complete: $successCount found, $failCount missing/failed, $totalItems total items"
            )
        }
    }
}
