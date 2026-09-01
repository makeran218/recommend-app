package com.makeran218.recommendtmdb.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.makeran218.recommendtmdb.AppPreferences
import com.makeran218.recommendtmdb.CatalogEntry
import com.makeran218.recommendtmdb.DeepLinks
import com.makeran218.recommendtmdb.ManifestRepository
import com.makeran218.recommendtmdb.SyncScheduler
import com.makeran218.recommendtmdb.SyncWorker
import com.makeran218.recommendtmdb.XperienceClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.work.WorkInfo

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val context = app.applicationContext

    private val _syncInProgress = MutableStateFlow(false)
    val syncInProgress: StateFlow<Boolean> = _syncInProgress

    private val _settings = MutableStateFlow(AppPreferences.Settings("nuvio"))
    val settings: StateFlow<AppPreferences.Settings> = _settings

    private val _manifestUrls = MutableStateFlow<List<String>>(emptyList())
    val manifestUrls: StateFlow<List<String>> = _manifestUrls

    private val _catalogs = MutableStateFlow<Map<String, List<CatalogEntry>>>(emptyMap())
    val catalogs: StateFlow<Map<String, List<CatalogEntry>>> = _catalogs

    init {
        // Each collector must run in its own coroutine — collect() blocks
        viewModelScope.launch {
            AppPreferences.readPreferences(context).collect { s ->
                _settings.value = s
                DeepLinks.setProvider(DeepLinks.getProvider(s.playbackProvider))
            }
        }
        viewModelScope.launch {
            ManifestRepository.readManifestUrls(context).collect { urls ->
                _manifestUrls.value = urls
                if (urls.isNotEmpty() && _catalogs.value.isEmpty()) {
                    loadCachedCatalogs()
                }
            }
        }
        viewModelScope.launch {
            ManifestRepository.readEnabledCatalogs(context).collect { enabled ->
                val current = _catalogs.value
                val updated = current.mapValues { (manifestUrl, catalogList) ->
                    catalogList.map { c ->
                        c.copy(enabled = enabled.contains("$manifestUrl::${c.uniqueId}"))
                    }
                }
                _catalogs.value = updated
            }
        }
    }

    // ── Catalog Loading ──────────────────────────────────────────────

    private suspend fun loadCachedCatalogs() {
        try {
            val urls = ManifestRepository.readManifestUrls(context).first()
            val enabled = ManifestRepository.readEnabledCatalogs(context).first()
            val newCatalogs = mutableMapOf<String, List<CatalogEntry>>()

            for (manifestUrl in urls) {
                val cached = ManifestRepository.loadCachedCatalogs(context, manifestUrl)
                if (cached != null) {
                    newCatalogs[manifestUrl] = cached.map { c ->
                        c.copy(enabled = enabled.contains("$manifestUrl::${c.uniqueId}"))
                    }
                }
            }

            if (newCatalogs.isNotEmpty()) {
                _catalogs.value = newCatalogs
            }
        } catch (e: Exception) {
            Log.e("VM", "Failed to load cached catalogs", e)
        }
    }

    // ── Manifest URL Management ──────────────────────────────────────

    fun addManifestUrl(url: String) {
        viewModelScope.launch {
            val trimmed = url.trim()
            val cleanUrl = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                trimmed
            } else {
                "https://$trimmed"
            }
            Log.d("VM", "addManifestUrl: $cleanUrl")
            val saved = ManifestRepository.addManifestUrl(context, cleanUrl)
            val allUrls = ManifestRepository.readManifestUrls(context).first()
            _manifestUrls.value = allUrls
            Log.d("VM", "addManifestUrl: saved=$saved, total URLs=${allUrls.size}")
            // Fetch from network so catalogs appear immediately (cache is empty on first add)
            refetchManifests()
        }
    }

    fun removeManifestUrl(url: String) {
        viewModelScope.launch {
            ManifestRepository.removeManifestUrl(context, url)
            _catalogs.value = _catalogs.value.filterKeys { it != url }
        }
    }

    /** Always fetch manifest.json from network for all URLs. */
    fun refetchManifests() {
        viewModelScope.launch {
            try {
                val urls = ManifestRepository.readManifestUrls(context).first()
                val enabled = ManifestRepository.readEnabledCatalogs(context).first()
                val newCatalogs = mutableMapOf<String, List<CatalogEntry>>()

                for (manifestUrl in urls) {
                    if (!manifestUrl.startsWith("http://") && !manifestUrl.startsWith("https://")) {
                        Log.w("VM", "Skipping invalid URL: $manifestUrl")
                        continue
                    }
                    try {
                        val manifest = XperienceClient.fetchManifest(manifestUrl)
                        val entries = manifest.catalogs
                            .filter { c ->
                                // Exclude search catalogs (any catalog with extra field named "search")
                                !c.id.startsWith("search.") &&
                                        c.extra.none { it.name == "search" }
                            }
                            .map { c ->
                                val uniqueId = "${c.id}.${c.type}"
                                CatalogEntry(
                                    c.id,
                                    c.name,
                                    c.type,
                                    enabled.contains("$manifestUrl::$uniqueId")
                                )
                            }

                        ManifestRepository.cacheManifest(context, manifestUrl, entries)
                        newCatalogs[manifestUrl] = entries
                        Log.d("VM", "Refetched ${entries.size} catalogs for $manifestUrl")
                        for (entry in entries) {
                            Log.d(
                                "VM",
                                "  Catalog: ${entry.catalogName} | Type: ${entry.catalogType} | ID: ${entry.catalogId}"
                            )
                        }
                    } catch (e: Exception) {
                        Log.e("VM", "Failed to refetch manifest: $manifestUrl", e)
                    }
                }

                if (newCatalogs.isNotEmpty()) {
                    _catalogs.value = newCatalogs
                }
            } catch (e: Exception) {
                Log.e("VM", "Refetch failed", e)
            }
        }
    }

    /** Uses ONLY cached catalog data (no network fetch). */
    fun refreshCatalogs() {
        viewModelScope.launch {
            Log.d("VM", "Refresh Catalogs button clicked")
            try {
                val urls = ManifestRepository.readManifestUrls(context).first()
                val enabled = ManifestRepository.readEnabledCatalogs(context).first()
                val newCatalogs = mutableMapOf<String, List<CatalogEntry>>()

                for (manifestUrl in urls) {
                    val cached = ManifestRepository.loadCachedCatalogs(context, manifestUrl)
                    if (cached != null) {
                        newCatalogs[manifestUrl] = cached.map { c ->
                            c.copy(enabled = enabled.contains("$manifestUrl::${c.uniqueId}"))
                        }
                    }
                }

                if (newCatalogs.isNotEmpty()) {
                    _catalogs.value = newCatalogs
                }
            } catch (e: Exception) {
                Log.e("VM", "Refresh failed", e)
            }
        }
    }

    // ── Catalog Management ───────────────────────────────────────────

    fun toggleCatalog(key: String, enabled: Boolean) {
        viewModelScope.launch {
            ManifestRepository.toggleCatalog(context, key, enabled)
        }
    }

    // ── Sync ─────────────────────────────────────────────────────────

    fun syncChannels() {
        viewModelScope.launch {
            _syncInProgress.value = true
            Log.d("VM", "Sync started")
            try {
                SyncScheduler.triggerSync(context)
                androidx.work.WorkManager.getInstance(context)
                    .getWorkInfosForUniqueWorkFlow(SyncWorker.WORK_NAME)
                    .first { infos ->
                        infos.any { it.state == WorkInfo.State.SUCCEEDED || it.state == WorkInfo.State.FAILED }
                    }
                Log.d("VM", "Sync done")
                refreshCatalogs()
            } catch (e: Exception) {
                Log.e("VM", "Sync failed", e)
            } finally {
                _syncInProgress.value = false
            }
        }
    }

    // ── Settings ─────────────────────────────────────────────────────

    fun setPlaybackProvider(provider: String) {
        viewModelScope.launch { AppPreferences.setPlaybackProvider(context, provider) }
    }
}
