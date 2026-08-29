package com.makeran218.recommendtmdb

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object AppPreferences {

    private val ENABLED_CATEGORIES_KEY = stringSetPreferencesKey("enabled_categories")
    private val API_KEY_KEY = stringPreferencesKey("tmdb_api_key")
    private val LAST_SYNC_TIME_KEY = longPreferencesKey("last_sync_time")
    private val PLAYBACK_PROVIDER_KEY = stringPreferencesKey("playback_provider")
    private val DISPLAY_TYPE_KEY = stringPreferencesKey("display_type")
    private val POSTER_PROVIDER_KEY = stringPreferencesKey("poster_provider")
    private val ITEMS_PER_CATEGORY_KEY = intPreferencesKey("items_per_category")

    // Cache: store each category's items as a separate JSON string
    // Key format: "cache_<categoryKey>" -> JSON array of item objects
    // "cache_meta" -> { "cachedAt": <timestamp> }
    // "cached_items" -> old format (single JSON object) — migrated away
    private val CACHED_ITEMS_KEY = stringPreferencesKey("cached_items")
    private val CACHED_TIME_KEY = longPreferencesKey("cached_time")

    val DEFAULT_PLAYBACK_PROVIDER = "nuvio"
    val DEFAULT_DISPLAY_TYPE = "POSTER"
    val DEFAULT_POSTER_PROVIDER = "tmdb" // tmdb | bttr (better-posters)
    val DEFAULT_ITEMS_PER_CATEGORY = 20
    val ITEMS_PER_CATEGORY_OPTIONS = listOf(20, 40, 60) // 1, 2, or 3 pages of TMDB results

    val DEFAULT_CATEGORIES = setOf(
        Category.TRENDING_MOVIES.key,
        Category.TRENDING_TV.key,
        Category.LATEST_MOVIES.key,
        Category.LATEST_TV.key,
        Category.POPULAR_MOVIES.key,
        Category.POPULAR_TV.key,
        Category.NETFLIX_POPULAR_MOVIES.key,
        Category.NETFLIX_POPULAR_TV.key,
        Category.NETFLIX_NEW_MOVIES.key,
        Category.NETFLIX_NEW_TV.key,
    )

    data class Settings(
        val enabledCategories: Set<String>,
        val lastSyncTime: Long,
        val apiKey: String,
        val playbackProvider: String,
        val displayType: String,
        val posterProvider: String,
        val itemsPerCategory: Int
    ) {
        fun hasApiKey(): Boolean = apiKey.isNotEmpty() && apiKey != "YOUR_TMDB_API_KEY_HERE"
    }

    fun readPreferences(context: Context): Flow<Settings> {
        return context.dataStore.data.map { prefs ->
            // Merge saved categories with defaults so new categories
            // are automatically added to existing user settings.
            val saved = prefs[ENABLED_CATEGORIES_KEY] ?: emptySet()
            val enabled = DEFAULT_CATEGORIES + saved
            val lastSync = prefs[LAST_SYNC_TIME_KEY] ?: 0L
            val apiKey = prefs[API_KEY_KEY] ?: ""
            val provider = prefs[PLAYBACK_PROVIDER_KEY] ?: DEFAULT_PLAYBACK_PROVIDER
            val display = prefs[DISPLAY_TYPE_KEY] ?: DEFAULT_DISPLAY_TYPE
            val posterProvider = prefs[POSTER_PROVIDER_KEY] ?: DEFAULT_POSTER_PROVIDER
            val itemsPerCategory = prefs[ITEMS_PER_CATEGORY_KEY] ?: DEFAULT_ITEMS_PER_CATEGORY
            Settings(enabled, lastSync, apiKey, provider, display, posterProvider, itemsPerCategory)
        }
    }

    suspend fun setEnabledCategories(context: Context, categories: Set<String>) {
        context.dataStore.edit { prefs -> prefs[ENABLED_CATEGORIES_KEY] = categories }
    }

    suspend fun setLastSyncTime(context: Context, time: Long) {
        context.dataStore.edit { prefs -> prefs[LAST_SYNC_TIME_KEY] = time }
    }

    suspend fun setApiKey(context: Context, key: String) {
        context.dataStore.edit { prefs -> prefs[API_KEY_KEY] = key }
    }

    suspend fun setPlaybackProvider(context: Context, provider: String) {
        context.dataStore.edit { prefs -> prefs[PLAYBACK_PROVIDER_KEY] = provider }
        DeepLinks.setProvider(DeepLinks.getProvider(provider))
    }

    suspend fun setDisplayType(context: Context, displayType: String) {
        context.dataStore.edit { prefs -> prefs[DISPLAY_TYPE_KEY] = displayType }
    }

    suspend fun setPosterProvider(context: Context, posterProvider: String) {
        context.dataStore.edit { prefs -> prefs[POSTER_PROVIDER_KEY] = posterProvider }
    }

    suspend fun setItemsPerCategory(context: Context, itemsPerCategory: Int) {
        context.dataStore.edit { prefs -> prefs[ITEMS_PER_CATEGORY_KEY] = itemsPerCategory }
    }

    // ─── Cache ───────────────────────────────────────────────

    data class CacheEntry(
        val items: List<Map<String, Any?>>,
        val cachedAt: Long
    )

    suspend fun saveCachedItems(context: Context, items: List<CategoryRow>) {
        context.dataStore.edit { prefs ->
            val gson = Gson()
            for (row in items) {
                // Store each category as a separate key: "cache_trending_movies", etc.
                val json = gson.toJson(row.items)
                prefs[stringPreferencesKey("cache_${row.category.key}")] = json
            }
            prefs[CACHED_TIME_KEY] = System.currentTimeMillis()
        }
    }

    suspend fun loadCachedItems(context: Context): List<CategoryRow>? {
        return try {
            val prefs = context.dataStore.data.first()
            val gson = Gson()
            val rows = mutableListOf<CategoryRow>()

            for (category in Category.values()) {
                val json = prefs[stringPreferencesKey("cache_${category.key}")]
                if (json.isNullOrEmpty()) continue
                val items = gson.fromJson(
                    json,
                    object : com.google.gson.reflect.TypeToken<List<TmdbItem>>() {}.type
                ) as List<TmdbItem>
                if (items.isNotEmpty()) {
                    rows.add(CategoryRow(category, items))
                }
            }

            rows.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            // Any cache error — clear everything and return null
            android.util.Log.e("AppPreferences", "Cache load failed, clearing: ${e.message}", e)
            try {
                context.dataStore.edit { p ->
                    for (category in Category.values()) {
                        p.remove(stringPreferencesKey("cache_${category.key}"))
                    }
                    p.remove(CACHED_TIME_KEY)
                    p.remove(CACHED_ITEMS_KEY)
                }
            } catch (ignore: Exception) {
                // Ignore clear errors
            }
            null
        }
    }

    suspend fun clearCachedItems(context: Context) {
        context.dataStore.edit { prefs ->
            for (category in Category.values()) {
                prefs.remove(stringPreferencesKey("cache_${category.key}"))
            }
            prefs.remove(CACHED_TIME_KEY)
        }
    }

    fun getCachedTime(context: Context): Flow<Long> {
        return context.dataStore.data.map { it[CACHED_TIME_KEY] ?: 0L }
    }
}

enum class Category(val key: String, val displayNameRes: Int) {
    TRENDING_MOVIES("trending_movies", R.string.channel_trending_movies),
    TRENDING_TV("trending_tv", R.string.channel_trending_tv),
    LATEST_MOVIES("latest_movies", R.string.channel_latest_movies),
    LATEST_TV("latest_tv", R.string.channel_latest_tv),
    POPULAR_MOVIES("popular_movies", R.string.channel_popular_movies),
    POPULAR_TV("popular_tv", R.string.channel_popular_tv),
    NETFLIX_POPULAR_MOVIES("netflix_popular_movies", R.string.channel_netflix_popular_movies),
    NETFLIX_POPULAR_TV("netflix_popular_tv", R.string.channel_netflix_popular_tv),
    NETFLIX_NEW_MOVIES("netflix_new_movies", R.string.channel_netflix_new_movies),
    NETFLIX_NEW_TV("netflix_new_tv", R.string.channel_netflix_new_tv);

    companion object {
        fun fromKey(key: String): Category? = values().find { it.key == key }
    }

    fun channelId(): String = "tmdb_$key"
    fun channelName(context: android.content.Context): String = context.getString(displayNameRes)
    fun channelDescription(): String = when (this) {
        TRENDING_MOVIES -> "Trending movies on TMDB"
        TRENDING_TV -> "Trending TV shows on TMDB"
        LATEST_MOVIES -> "Latest movies added to TMDB"
        LATEST_TV -> "Latest TV shows added to TMDB"
        POPULAR_MOVIES -> "Popular movies on TMDB"
        POPULAR_TV -> "Popular TV shows on TMDB"
        NETFLIX_POPULAR_MOVIES -> "Popular movies on Netflix US"
        NETFLIX_POPULAR_TV -> "Popular TV shows on Netflix US"
        NETFLIX_NEW_MOVIES -> "Top rated movies on Netflix US"
        NETFLIX_NEW_TV -> "Top rated TV shows on Netflix US"
    }
}
