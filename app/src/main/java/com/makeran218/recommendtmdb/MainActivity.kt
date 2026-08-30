package com.makeran218.recommendtmdb

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.MainScope

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (handleDeepLinkForward()) {
            finish()
            return
        }

        val viewModel = ManifestViewModel(application)

        setContent {
            TVHomeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (handleDeepLinkForward()) finish()
    }

    private fun handleDeepLinkForward(): Boolean {
        val data = intent.data ?: return false
        val scheme = data.scheme ?: return false
        if (scheme in listOf("nuvio", "stremio")) {
            Log.d(TAG, "Forwarding $scheme link: $data")
            forwardToApp(scheme, data)
            return true
        }
        return false
    }

    private fun forwardToApp(scheme: String, uri: Uri) {
        val targetPackage = when (scheme) {
            "nuvio" -> "com.nuvio.tv"
            "stremio" -> "com.stremio.one"
            else -> null
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = uri
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            targetPackage?.let { setPackage(it) }
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed: ${e.message}")
        }
    }
}

// ==========================================
// Main Screen
// ==========================================

@Composable
fun MainScreen(viewModel: ManifestViewModel) {
    val syncInProgress by viewModel.syncInProgress.collectAsState()
    val manifestUrls by viewModel.manifestUrls.collectAsState(initial = emptyList())
    val catalogs by viewModel.catalogs.collectAsState(initial = emptyMap())
    val settings by viewModel.settings.collectAsState()

    if (syncInProgress) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text("Syncing...", fontSize = 18.sp)
            }
        }
        return
    }

    if (manifestUrls.isEmpty()) {
        SetupScreen(viewModel)
    } else {
        MainContent(viewModel, manifestUrls, catalogs, settings)
    }
}

// ==========================================
// Setup Screen - Simple Dialog
// ==========================================

@Composable
fun SetupScreen(viewModel: ManifestViewModel) {
    var showAddDialog by remember { mutableStateOf(false) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(successMessage) {
        successMessage?.let {
            kotlinx.coroutines.delay(2000)
            successMessage = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("TV Home", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Add manifest URL to start", fontSize = 16.sp, color = Color.Gray)

        Spacer(Modifier.height(32.dp))

        // Big Add Button
        Button(
            onClick = { showAddDialog = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("Add Manifest URL", fontSize = 18.sp)
        }

        Spacer(Modifier.height(16.dp))
        Text("https://.../manifest.json", fontSize = 12.sp, color = Color.Gray)

        // Success message
        if (successMessage != null) {
            Spacer(Modifier.height(16.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF4CAF50),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    successMessage!!,
                    modifier = Modifier.padding(12.dp),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    // Add URL Dialog
    if (showAddDialog) {
        AddManifestDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { url ->
                viewModel.addManifestUrl(url)
                showAddDialog = false
                successMessage = "Manifest added! Catalogs will load automatically."
            }
        )
    }
}

@Composable
fun AddManifestDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var url by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Add Manifest URL", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Manifest URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(Modifier.height(16.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            if (url.isNotBlank()) {
                                onAdd(url)
                            }
                        },
                        enabled = url.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Add")
                    }
                }
            }
        }
    }
}

// ==========================================
// Manage Manifests Dialog
// ==========================================

@Composable
fun ManageManifestsDialog(
    manifestUrls: List<String>,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    var addUrl by remember { mutableStateOf("") }
    var showAddField by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(24.dp).fillMaxWidth()) {
                Text("Manage Manifests", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))

                // Add URL field
                if (showAddField) {
                    OutlinedTextField(
                        value = addUrl,
                        onValueChange = { addUrl = it },
                        label = { Text("Manifest URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("https://.../manifest.json") }
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(onClick = { showAddField = false }) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                if (addUrl.isNotBlank()) {
                                    onAdd(addUrl)
                                    addUrl = ""
                                    showAddField = false
                                }
                            },
                            enabled = addUrl.isNotBlank()
                        ) {
                            Text("Add")
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                } else {
                    Button(
                        onClick = { showAddField = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Add Manifest URL")
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // List of manifests
                if (manifestUrls.isEmpty()) {
                    Text("No manifests added", fontSize = 14.sp, color = Color.Gray)
                } else {
                    Text("${manifestUrls.size} manifest(s)", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.small
                            )
                            .padding(8.dp)
                    ) {
                        manifestUrls.forEach { url ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = url,
                                    modifier = Modifier.weight(1f),
                                    fontSize = 11.sp,
                                    color = Color.White,
                                    maxLines = 2
                                )
                                TextButton(
                                    onClick = { onRemove(url) },
                                    modifier = Modifier.padding(start = 8.dp)
                                ) {
                                    Icon(Icons.Default.Delete, null, tint = Color.Red, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                // Close button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close")
                }
            }
        }
    }
}

// ==========================================
// Main Content - Simple List
// ==========================================

@Composable
fun MainContent(
    viewModel: ManifestViewModel,
    manifestUrls: List<String>,
    catalogs: Map<String, List<CatalogEntry>>,
    settings: AppPreferences.Settings
) {
    var showManageDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("TV Home", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("${manifestUrls.size} manifest(s)", fontSize = 14.sp, color = Color.Gray)
            }
        }

        // Manage manifests button
        item {
            Button(
                onClick = { showManageDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Settings, null)
                Spacer(Modifier.width(8.dp))
                Text("Manage Manifests")
            }
        }

        // Refresh button
        item {
            Button(
                onClick = { viewModel.refreshCatalogs() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, null)
                Spacer(Modifier.width(8.dp))
                Text("Refresh Catalogs")
            }
        }

        // Catalogs header
        item {
            val total = catalogs.values.sumOf { it.size }
            val enabled = catalogs.values.sumOf { it.count { c -> c.enabled } }
            Text("Catalogs ($enabled / $total)", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        // Catalog items
        catalogs.forEach { (manifestUrl, catalogList) ->
            catalogList.forEach { catalog ->
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (catalog.catalogType == "series") Icons.Default.VideoLibrary else Icons.Default.PlayCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(catalog.catalogName, fontWeight = FontWeight.Bold)
                            Text(catalog.catalogType.uppercase(), fontSize = 12.sp, color = Color.Gray)
                        }
                        Switch(
                            checked = catalog.enabled,
                            onCheckedChange = { enabled ->
                                viewModel.toggleCatalog(
                                    "$manifestUrl::${catalog.catalogType}::${catalog.catalogId}",
                                    enabled
                                )
                            }
                        )
                    }
                }
            }
        }

        // Sync button
        item {
            Button(
                onClick = { viewModel.syncChannels() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, null)
                Spacer(Modifier.width(8.dp))
                Text("Sync Channels Now")
            }
        }

        // Settings
        item {
            Text("Settings", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        // Playback provider
        item {
            Text(
                "Playback App",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val selected = settings.playbackProvider
                for ((key, label) in listOf("nuvio" to "Nuvio", "stremio" to "Stremio")) {
                    OutlinedButton(
                        onClick = { viewModel.setPlaybackProvider(key) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (selected == key) MaterialTheme.colorScheme.primary else Color.White
                        )
                    ) {
                        Text(label, color = if (selected == key) MaterialTheme.colorScheme.primary else Color.White)
                    }
                }
            }
        }

        // Display type
        item {
            Text(
                "Display Type",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val selected = settings.displayType
                for ((key, label) in listOf("POSTER" to "Poster", "WIDE" to "Wide")) {
                    OutlinedButton(
                        onClick = { viewModel.setDisplayType(key) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (selected == key) MaterialTheme.colorScheme.primary else Color.White
                        )
                    ) {
                        Text(label, color = if (selected == key) MaterialTheme.colorScheme.primary else Color.White)
                    }
                }
            }
        }

        // Warning if no catalogs enabled
        item {
            val total = catalogs.values.sumOf { it.size }
            val enabled = catalogs.values.sumOf { it.count { c -> c.enabled } }
            if (enabled == 0 && total > 0) {
                Text("⚠ Enable at least one catalog", color = Color(0xFFFFA500), fontSize = 12.sp)
            }
        }
    }

    // Manage manifests dialog
    if (showManageDialog) {
        ManageManifestsDialog(
            manifestUrls = manifestUrls,
            onDismiss = { showManageDialog = false },
            onAdd = { url ->
                viewModel.addManifestUrl(url)
            },
            onRemove = { url ->
                viewModel.removeManifestUrl(url)
            }
        )
    }
}

// ==========================================
// ViewModel
// ==========================================

class ManifestViewModel(app: android.app.Application) : androidx.lifecycle.AndroidViewModel(app) {

    private val context = app.applicationContext

    private val _syncInProgress = MutableStateFlow(false)
    val syncInProgress: StateFlow<Boolean> = _syncInProgress

    private val _settings = MutableStateFlow(AppPreferences.Settings("nuvio", "POSTER"))
    val settings: StateFlow<AppPreferences.Settings> = _settings

    private val _manifestUrls = MutableStateFlow<List<String>>(emptyList())
    val manifestUrls: StateFlow<List<String>> = _manifestUrls

    private val _catalogs = MutableStateFlow<Map<String, List<CatalogEntry>>>(emptyMap())
    val catalogs: StateFlow<Map<String, List<CatalogEntry>>> = _catalogs

    init {
        // Each collector must run in its own coroutine — collect() blocks
        MainScope().launch {
            AppPreferences.readPreferences(context).collect { s ->
                _settings.value = s
                DeepLinks.setProvider(DeepLinks.getProvider(s.playbackProvider))
            }
        }
        MainScope().launch {
            ManifestRepository.readManifestUrls(context).collect { urls ->
                _manifestUrls.value = urls
                if (urls.isNotEmpty() && _catalogs.value.isEmpty()) {
                    loadCachedCatalogs()
                }
            }
        }
        MainScope().launch {
            ManifestRepository.readEnabledCatalogs(context).collect { enabled ->
                val current = _catalogs.value
                val updated = current.mapValues { (manifestUrl, catalogList) ->
                    catalogList.map { c ->
                        c.copy(enabled = enabled.contains("$manifestUrl::${c.catalogType}::${c.catalogId}"))
                    }
                }
                _catalogs.value = updated
            }
        }
    }

    private suspend fun loadCachedCatalogs() {
        try {
            val urls = ManifestRepository.readManifestUrls(context).first()
            val enabled = ManifestRepository.readEnabledCatalogs(context).first()
            val newCatalogs = mutableMapOf<String, List<CatalogEntry>>()

            for (manifestUrl in urls) {
                val cached = ManifestRepository.loadCachedCatalogs(context, manifestUrl)
                if (cached != null) {
                    newCatalogs[manifestUrl] = cached.map { c ->
                        c.copy(enabled = enabled.contains("$manifestUrl::${c.catalogType}::${c.catalogId}"))
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

    fun addManifestUrl(url: String) {
        MainScope().launch {
            val trimmed = url.trim()
            // Auto-add https:// if missing
            val cleanUrl = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                trimmed
            } else {
                "https://$trimmed"
            }
            Log.d("VM", "addManifestUrl: $cleanUrl")
            val saved = ManifestRepository.addManifestUrl(context, cleanUrl)
            // Always update state and load catalogs, whether new or existing URL
            val allUrls = ManifestRepository.readManifestUrls(context).first()
            _manifestUrls.value = allUrls
            Log.d("VM", "addManifestUrl: saved=$saved, total URLs=${allUrls.size}, loading catalogs")
            // Automatically fetch catalogs
            refreshCatalogs()
        }
    }

    fun removeManifestUrl(url: String) {
        MainScope().launch {
            ManifestRepository.removeManifestUrl(context, url)
            _catalogs.value = _catalogs.value.filterKeys { it != url }
        }
    }

    fun refreshCatalogs() {
        MainScope().launch {
            try {
                val urls = ManifestRepository.readManifestUrls(context).first()
                val enabled = ManifestRepository.readEnabledCatalogs(context).first()
                val newCatalogs = mutableMapOf<String, List<CatalogEntry>>()

                for (manifestUrl in urls) {
                    // Skip invalid URLs
                    if (!manifestUrl.startsWith("http://") && !manifestUrl.startsWith("https://")) {
                        Log.w("VM", "Skipping invalid URL: $manifestUrl")
                        continue
                    }
                    try {
                        val cached = ManifestRepository.loadCachedCatalogs(context, manifestUrl)
                        if (cached != null) {
                            newCatalogs[manifestUrl] = cached.map { c ->
                                c.copy(enabled = enabled.contains("$manifestUrl::${c.catalogType}::${c.catalogId}"))
                            }
                            continue
                        }

                        val manifest = XperienceClient.fetchManifest(manifestUrl)
                        val entries = manifest.catalogs
                            .filter {
                                !it.id.startsWith("xperience.search") &&
                                        it.extraRequired?.contains("genre") != true
                            }
                            .map { c ->
                                CatalogEntry(c.id, c.name, c.type, enabled.contains("$manifestUrl::${c.type}::${c.id}"))
                            }

                        ManifestRepository.cacheManifest(context, manifestUrl, entries)
                        newCatalogs[manifestUrl] = entries
                        Log.d("VM", "Loaded ${entries.size} catalogs")
                    } catch (e: Exception) {
                        Log.e("VM", "Failed to fetch manifest: $manifestUrl", e)
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

    fun toggleCatalog(key: String, enabled: Boolean) {
        MainScope().launch {
            ManifestRepository.toggleCatalog(context, key, enabled)
        }
    }

    fun syncChannels() {
        MainScope().launch {
            _syncInProgress.value = true
            Log.d("VM", "Sync started")
            try {
                SyncScheduler.triggerSync(context)
                WorkManager.getInstance(context)
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

    fun setPlaybackProvider(provider: String) {
        MainScope().launch { AppPreferences.setPlaybackProvider(context, provider) }
    }

    fun setDisplayType(display: String) {
        MainScope().launch { AppPreferences.setDisplayType(context, display) }
    }
}

// ==========================================
// Theme
// ==========================================

@Composable
fun TVHomeTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = Color(0xFF4D50FF),
        onPrimary = Color.White,
        background = Color(0xFF1A1A2E),
        onBackground = Color.White,
        surface = Color(0xFF16213E),
        onSurface = Color.White
    )
    MaterialTheme(colorScheme = colors, content = content)
}
