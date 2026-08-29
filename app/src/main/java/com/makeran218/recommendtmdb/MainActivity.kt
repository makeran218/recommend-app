package com.makeran218.recommendtmdb

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.MainScope

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if this is a deep link from the launcher
        if (handleDeepLinkForward()) {
            // Forwarded to Nuvio/Stremio, finish immediately
            finish()
            return
        }

        val viewModel = TmdbViewModel(application)

        setContent {
            TMDBTVTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(viewModel)
                }
            }
        }

        // Sync channels on startup
        MainScope().launch {
            SyncScheduler.triggerSync(application)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (handleDeepLinkForward()) {
            finish()
        }
    }

    /**
     * Handle deep link forwarding.
     * When the launcher launches us with a Nuvio/Stremio URI, we forward it
     * to the actual app and finish.
     */
    private fun handleDeepLinkForward(): Boolean {
        val data = intent.data ?: return false
        val scheme = data.scheme ?: return false

        return when (scheme) {
            "nuvio" -> {
                Log.d(TAG, "Forwarding Nuvio deep link: $data")
                forwardToApp("nuvio", data)
                true
            }

            "stremio" -> {
                Log.d(TAG, "Forwarding Stremio deep link: $data")
                forwardToApp("stremio", data)
                true
            }

            else -> false
        }
    }

    /**
     * Forward a deep link URI to the appropriate app with explicit package targeting.
     */
    private fun forwardToApp(scheme: String, uri: Uri) {
        val targetPackage = when (scheme) {
            "nuvio" -> "com.nuvio.tv"
            "stremio" -> "com.stremio.one"
            else -> null
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = uri
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // Explicitly target the app package
            if (targetPackage != null) {
                setPackage(targetPackage)
            }
        }

        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch $scheme with explicit package, trying implicit: ${e.message}")
            // Fallback: implicit intent (system will show chooser)
            val fallback = Intent(Intent.ACTION_VIEW).apply {
                data = uri
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                startActivity(fallback)
            } catch (e2: Exception) {
                Log.e(TAG, "Fallback also failed: ${e2.message}")
            }
        }
    }
}

@Composable
fun MainScreen(viewModel: TmdbViewModel) {
    val uiState by viewModel.uiState.collectAsState(initial = TmdbUiState.Loading)
    val settings by viewModel.settings.collectAsState()

    when (uiState) {
        is TmdbUiState.Loading -> LoadingScreen()
        is TmdbUiState.Error -> {
            val e = uiState as TmdbUiState.Error
            ErrorScreen(e.message) { viewModel.retry() }
        }

        is TmdbUiState.Success -> {
            val state = uiState as TmdbUiState.Success

            if (settings.apiKey.isBlank() || settings.apiKey == "YOUR_TMDB_API_KEY_HERE") {
                SetupScreen(onApiKeySet = { key -> viewModel.setApiKey(key) })
            } else {
                ChannelsScreen(
                    rows = state.rows,
                    settings = settings,
                    onApiKeyChange = { key -> viewModel.setApiKey(key) },
                    onSync = { viewModel.syncChannels() },
                    onRefresh = { viewModel.retry() },
                    onProviderChange = { provider -> viewModel.setPlaybackProvider(provider) },
                    onDisplayChange = { display -> viewModel.setDisplayType(display) }
                )
            }
        }
    }
}

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text("Loading...", color = MaterialTheme.colorScheme.onBackground)
        }
    }
}

@Composable
fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text("⚠️", fontSize = 48.sp)
            Spacer(Modifier.height(16.dp))
            Text(message, color = Color.Red)
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) { Text("Retry", color = MaterialTheme.colorScheme.onBackground) }
        }
    }
}

@Composable
fun SetupScreen(onApiKeySet: (String) -> Unit) {
    var apiKey by remember { mutableStateOf("") }
    var showSuccess by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A2E)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "TMDB TV Home",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text("Set up your TMDB API key to create TV channels", fontSize = 18.sp, color = Color.White)
            Text("1. Go to https://www.themoviedb.org/settings/api", fontSize = 14.sp, color = Color.Gray)
            Text("2. Create an API key (it's free)", fontSize = 14.sp, color = Color.Gray)
            Text("3. Paste your API key below", fontSize = 14.sp, color = Color.Gray)

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("TMDB API Key", color = Color.Gray) },
                modifier = Modifier.fillMaxWidth().height(60.dp),
                singleLine = false
            )

            Button(
                onClick = {
                    if (apiKey.isNotBlank() && apiKey != "YOUR_TMDB_API_KEY_HERE") {
                        onApiKeySet(apiKey)
                        showSuccess = true
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save", color = MaterialTheme.colorScheme.onPrimary) }

            if (showSuccess) {
                Text("API key saved! Syncing channels...", color = Color(0xFF4CAF50))
            }
        }
    }
}

@Composable
fun ChannelsScreen(
    rows: List<CategoryRow>,
    settings: AppPreferences.Settings,
    onApiKeyChange: (String) -> Unit,
    onSync: () -> Unit,
    onRefresh: () -> Unit,
    onProviderChange: (String) -> Unit,
    onDisplayChange: (String) -> Unit
) {
    val enabledSet = settings.enabledCategories
    var showApiKeyEdit by remember { mutableStateOf(false) }
    var tempApiKey by remember { mutableStateOf(settings.apiKey) }

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A2E)),
        contentAlignment = Alignment.Center
    ) {
        Column(modifier = Modifier.padding(32.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "TMDB TV Home",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, "Sync", tint = Color.White)
                }
            }

            Spacer(Modifier.height(8.dp))

            // Sync button
            Button(
                onClick = onSync,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, null, tint = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(8.dp))
                Text("Sync Channels Now", color = MaterialTheme.colorScheme.onPrimary)
            }

            Spacer(Modifier.height(24.dp))

            // ─── Settings Section ───
            Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(8.dp))

            // Playback Provider selection
            Text(
                "Playback App",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val isNuvioSelected = settings.playbackProvider == "nuvio"
                OutlinedButton(
                    onClick = { onProviderChange("nuvio") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (isNuvioSelected) MaterialTheme.colorScheme.primary else Color.White
                    )
                ) {
                    Text("Nuvio", color = if (isNuvioSelected) MaterialTheme.colorScheme.primary else Color.White)
                }

                val isStremioSelected = settings.playbackProvider == "stremio"
                OutlinedButton(
                    onClick = { onProviderChange("stremio") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (isStremioSelected) MaterialTheme.colorScheme.primary else Color.White
                    )
                ) {
                    Text("Stremio", color = if (isStremioSelected) MaterialTheme.colorScheme.primary else Color.White)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Display Type selection
            Text(
                "Display Type",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val isPosterSelected = settings.displayType == "POSTER"
                OutlinedButton(
                    onClick = { onDisplayChange("POSTER") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (isPosterSelected) MaterialTheme.colorScheme.primary else Color.White
                    )
                ) {
                    Text("Poster", color = if (isPosterSelected) MaterialTheme.colorScheme.primary else Color.White)
                }

                val isWideSelected = settings.displayType == "WIDE"
                OutlinedButton(
                    onClick = { onDisplayChange("WIDE") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (isWideSelected) MaterialTheme.colorScheme.primary else Color.White
                    )
                ) {
                    Text("Wide", color = if (isWideSelected) MaterialTheme.colorScheme.primary else Color.White)
                }
            }

            Spacer(Modifier.height(16.dp))

            // API Key section
            Text("API Key", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))

            OutlinedTextField(
                value = settings.apiKey,
                onValueChange = {},
                label = { Text("TMDB API Key", color = Color.Gray) },
                modifier = Modifier.fillMaxWidth(),
                readOnly = !showApiKeyEdit,
                trailingIcon = {
                    IconButton(onClick = {
                        if (showApiKeyEdit) {
                            if (tempApiKey.isNotBlank() && tempApiKey != "YOUR_TMDB_API_KEY_HERE") {
                                onApiKeyChange(tempApiKey)
                            }
                            showApiKeyEdit = false
                        } else {
                            tempApiKey = settings.apiKey
                            showApiKeyEdit = true
                        }
                    }) {
                        Icon(
                            imageVector = if (showApiKeyEdit) Icons.Default.Check else Icons.Default.Edit,
                            contentDescription = if (showApiKeyEdit) "Save" else "Edit",
                            tint = Color.White
                        )
                    }
                }
            )

            if (showApiKeyEdit) {
                OutlinedTextField(
                    value = tempApiKey,
                    onValueChange = { tempApiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 3
                )
            }

            Spacer(Modifier.height(24.dp))

            // Channels list — show all enabled categories
            val itemCount = rows.size
            val emptyCount = enabledSet.size - itemCount
            Text(
                "TV Channels ($itemCount / ${enabledSet.size})",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(rows) { row ->
                    ChannelItem(row.category, row.items.size)
                }
                if (emptyCount > 0) {
                    item {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                            Text(
                                "⚠️ $emptyCount channel(s) had no data — check API key or retry",
                                color = Color(0xFFFFA500)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChannelItem(category: Category, itemCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(Color(0xFF16213E), shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("🎬", fontSize = 24.sp)
        }

        Spacer(Modifier.width(16.dp))

        Column {
            Text(
                category.channelName(LocalContext.current),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text("$itemCount items • ${category.channelDescription()}", fontSize = 12.sp, color = Color.Gray)
        }

        Spacer(Modifier.weight(1f))
        Text("✓", fontSize = 20.sp, color = Color(0xFF4CAF50))
    }
}

@Composable
fun TMDBTVTheme(content: @Composable () -> Unit) {
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

class TmdbViewModel(application: android.app.Application) : androidx.lifecycle.AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<TmdbUiState>(TmdbUiState.Success(emptyList()))
    val uiState: StateFlow<TmdbUiState> = _uiState

    private val _settings = MutableStateFlow(AppPreferences.Settings(emptySet(), 0, "", "nuvio", "POSTER"))
    val settings: StateFlow<AppPreferences.Settings> = _settings

    private val context = application.applicationContext

    init {
        MainScope().launch {
            AppPreferences.readPreferences(context).collect { s ->
                _settings.value = s
                DeepLinks.setProvider(DeepLinks.getProvider(s.playbackProvider))
                if (s.hasApiKey()) {
                    loadCategories()
                } else {
                    _uiState.value = TmdbUiState.Success(emptyList())
                }
            }
        }
    }

    fun setApiKey(key: String) {
        MainScope().launch {
            AppPreferences.setApiKey(context, key)
            loadCategories()
        }
    }

    fun setPlaybackProvider(provider: String) {
        MainScope().launch {
            AppPreferences.setPlaybackProvider(context, provider)
        }
    }

    fun setDisplayType(displayType: String) {
        MainScope().launch {
            AppPreferences.setDisplayType(context, displayType)
        }
    }

    fun syncChannels() {
        MainScope().launch {
            SyncScheduler.triggerSync(context)
        }
    }

    suspend fun loadCategories() {
        val settings = AppPreferences.readPreferences(context).first()
        val apiKey = settings.apiKey

        if (!settings.hasApiKey()) {
            _uiState.value = TmdbUiState.Error("TMDB API key not configured")
            return
        }

        _uiState.value = TmdbUiState.Loading

        try {
            val rows = mutableListOf<CategoryRow>()

            for (categoryKey in settings.enabledCategories) {
                val category = Category.values().find { it.key == categoryKey } ?: continue
                val items = fetchCategoryItems(apiKey, category)
                val count = items.size
                android.util.Log.d(
                    "TmdbViewModel",
                    "Category $categoryKey: ${count} items (enabled=${settings.enabledCategories.contains(categoryKey)})"
                )
                if (items.isNotEmpty()) {
                    rows.add(CategoryRow(category, items))
                }
            }

            _uiState.value = TmdbUiState.Success(rows)
        } catch (e: Exception) {
            _uiState.value = TmdbUiState.Error(e.message ?: "Unknown error")
        }
    }

    private suspend fun fetchCategoryItems(apiKey: String, category: Category): List<TmdbItem> {
        return try {
            val response = when (category.key) {
                Category.TRENDING_MOVIES.key -> TmdbClient.api.getTrending("movie", "week", apiKey)
                Category.TRENDING_TV.key -> TmdbClient.api.getTrending("tv", "week", apiKey)
                Category.LATEST_MOVIES.key -> TmdbClient.api.discoverLatestMovies(apiKey)
                Category.LATEST_TV.key -> TmdbClient.api.discoverTvShows(apiKey, "first_air_date.desc")
                Category.POPULAR_MOVIES.key -> TmdbClient.api.discoverMovies(apiKey, "popularity.desc")
                Category.POPULAR_TV.key -> TmdbClient.api.discoverTvShows(apiKey, "popularity.desc")
                Category.NETFLIX_POPULAR_MOVIES.key -> TmdbClient.api.discoverNetflixPopularMovies(apiKey)
                Category.NETFLIX_POPULAR_TV.key -> TmdbClient.api.discoverNetflixPopularTv(apiKey)
                Category.NETFLIX_NEW_MOVIES.key -> TmdbClient.api.discoverNetflixNewMovies(apiKey)
                Category.NETFLIX_NEW_TV.key -> TmdbClient.api.discoverNetflixNewTv(apiKey)
                else -> TmdbListResponse(0, 0, 0, emptyList())
            }

            // Filter out unreleased items (trending can include upcoming titles)
            // Discover endpoints already filter server-side, but this is a safety net
            response.results
                .filter { it.isReleased }
                .take(20)
        } catch (e: Exception) {
            android.util.Log.w("TmdbViewModel", "Failed to fetch ${category.key}: ${e.message}")
            emptyList()
        }
    }

    fun retry() {
        MainScope().launch { loadCategories() }
    }
}

sealed interface TmdbUiState {
    object Loading : TmdbUiState
    data class Success(val rows: List<CategoryRow>) : TmdbUiState
    data class Error(val message: String) : TmdbUiState
}

data class CategoryRow(val category: Category, val items: List<TmdbItem>)
