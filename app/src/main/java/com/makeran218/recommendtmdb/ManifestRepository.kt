package com.makeran218.recommendtmdb

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

private val Context.manifestDataStore: DataStore<Preferences> by preferencesDataStore(name = "manifest_settings")

/**
 * Repository for managing manifest URLs, catalogs, and caching.
 */
object ManifestRepository {

    private val MANIFEST_URLS_KEY = stringPreferencesKey("manifest_urls")
    private val ENABLED_CATALOGS_KEY = stringSetPreferencesKey("enabled_catalogs")
    private val CATALOG_CACHE_PREFIX = "catalog_cache_"
    private val MANIFEST_CACHE_KEY = "manifest_cache"
    private val LAST_SYNC_KEY = longPreferencesKey("last_sync_time")

    private val gson = Gson()

    /**
     * Read all stored manifest URLs.
     */
    fun readManifestUrls(context: Context): Flow<List<String>> {
        return context.manifestDataStore.data.map { prefs ->
            prefs[MANIFEST_URLS_KEY]?.split("|")?.filter { it.isNotBlank() } ?: emptyList()
        }
    }

    /**
     * Add a manifest URL (if not already present).
     * Validates URL format before storing.
     */
    suspend fun addManifestUrl(context: Context, url: String): Boolean {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) {
            android.util.Log.w("ManifestRepository", "addManifestUrl: empty URL")
            return false
        }
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            android.util.Log.w("ManifestRepository", "addManifestUrl: invalid URL format: $trimmed")
            return false
        }
        if (!trimmed.contains("manifest")) {
            android.util.Log.w("ManifestRepository", "addManifestUrl: URL doesn't look like a manifest: $trimmed")
            return false
        }

        val existing = readManifestUrls(context).first()
        if (trimmed in existing) {
            android.util.Log.d("ManifestRepository", "addManifestUrl: duplicate, skipping")
            return false
        }

        val updated = existing + trimmed
        context.manifestDataStore.edit { prefs ->
            prefs[MANIFEST_URLS_KEY] = updated.joinToString("|")
        }
        android.util.Log.d("ManifestRepository", "addManifestUrl: saved ${updated.size} URLs")
        return true
    }

    /**
     * Remove a manifest URL.
     */
    suspend fun removeManifestUrl(context: Context, url: String) {
        context.manifestDataStore.edit { prefs ->
            val existing = prefs[MANIFEST_URLS_KEY]?.split("|")?.filter { it.isNotBlank() && it != url } ?: emptyList()
            prefs[MANIFEST_URLS_KEY] = existing.joinToString("|")
        }
    }

    /**
     * Read enabled catalogs (as a set of "manifestUrl:catalogId" keys).
     */
    fun readEnabledCatalogs(context: Context): Flow<Set<String>> {
        return context.manifestDataStore.data.map { prefs ->
            prefs[ENABLED_CATALOGS_KEY] ?: emptySet()
        }
    }

    /**
     * Toggle a catalog's enabled state.
     */
    suspend fun toggleCatalog(context: Context, catalogKey: String, enabled: Boolean) {
        context.manifestDataStore.edit { prefs ->
            val existing = prefs[ENABLED_CATALOGS_KEY] ?: emptySet()
            val updated = if (enabled) {
                existing + catalogKey
            } else {
                existing - catalogKey
            }
            prefs[ENABLED_CATALOGS_KEY] = updated
        }
    }

    /**
     * Cache a manifest's parsed catalogs.
     */
    suspend fun cacheManifest(context: Context, manifestUrl: String, catalogs: List<CatalogEntry>) {
        context.manifestDataStore.edit { prefs ->
            val json = gson.toJson(catalogs)
            prefs[stringPreferencesKey("$MANIFEST_CACHE_KEY:$manifestUrl")] = json
        }
    }

    /**
     * Load cached catalogs for a manifest URL.
     */
    suspend fun loadCachedCatalogs(context: Context, manifestUrl: String): List<CatalogEntry>? {
        return try {
            val prefs = context.manifestDataStore.data.first()
            val json = prefs[stringPreferencesKey("$MANIFEST_CACHE_KEY:$manifestUrl")]
            if (json.isNullOrEmpty()) return null

            val type = object : com.google.gson.reflect.TypeToken<List<CatalogEntry>>() {}.type
            gson.fromJson(json, type) as List<CatalogEntry>
        } catch (e: Exception) {
            android.util.Log.e("ManifestRepository", "Failed to load cached catalogs: ${e.message}", e)
            null
        }
    }

    /**
     * Cache catalog items (meta list) for a catalog key.
     */
    suspend fun cacheCatalogItems(context: Context, catalogKey: String, items: List<ChannelItem>) {
        context.manifestDataStore.edit { prefs ->
            val json = gson.toJson(items)
            prefs[stringPreferencesKey("$CATALOG_CACHE_PREFIX$catalogKey")] = json
        }
    }

    /**
     * Load cached catalog items.
     */
    suspend fun loadCachedCatalogItems(context: Context, catalogKey: String): List<ChannelItem>? {
        return try {
            val prefs = context.manifestDataStore.data.first()
            val json = prefs[stringPreferencesKey("$CATALOG_CACHE_PREFIX$catalogKey")]
            if (json.isNullOrEmpty()) return null

            val type = object : com.google.gson.reflect.TypeToken<List<ChannelItem>>() {}.type
            gson.fromJson(json, type) as List<ChannelItem>
        } catch (e: Exception) {
            android.util.Log.e("ManifestRepository", "Failed to load cached catalog items: ${e.message}", e)
            null
        }
    }

    /**
     * Save last sync time.
     */
    suspend fun setLastSyncTime(context: Context, time: Long) {
        context.manifestDataStore.edit { prefs ->
            prefs[LAST_SYNC_KEY] = time
        }
    }

    /**
     * Get last sync time.
     */
    fun getLastSyncTime(context: Context): Flow<Long> {
        return context.manifestDataStore.data.map { it[LAST_SYNC_KEY] ?: 0L }
    }

    /**
     * Clear all cached data (for reset).
     * Sets known keys to empty/default values.
     */
    suspend fun clearAllCache(context: Context) {
        context.manifestDataStore.edit { prefs ->
            prefs[MANIFEST_URLS_KEY] = ""
            prefs[ENABLED_CATALOGS_KEY] = emptySet()
            prefs[LAST_SYNC_KEY] = 0L
        }
    }
}
