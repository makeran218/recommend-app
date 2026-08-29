package com.makeran218.recommendtmdb

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object AppPreferences {

    private val ENABLED_CATEGORIES_KEY = stringSetPreferencesKey("enabled_categories")
    private val API_KEY_KEY = stringPreferencesKey("tmdb_api_key")
    private val LAST_SYNC_TIME_KEY = longPreferencesKey("last_sync_time")
    private val PLAYBACK_PROVIDER_KEY = stringPreferencesKey("playback_provider")
    private val DISPLAY_TYPE_KEY = stringPreferencesKey("display_type")

    val DEFAULT_PLAYBACK_PROVIDER = "nuvio"
    val DEFAULT_DISPLAY_TYPE = "POSTER"

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
        val displayType: String
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
            Settings(enabled, lastSync, apiKey, provider, display)
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
