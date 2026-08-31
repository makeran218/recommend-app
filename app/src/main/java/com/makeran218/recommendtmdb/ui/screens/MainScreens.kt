package com.makeran218.recommendtmdb.ui.screens

import com.makeran218.recommendtmdb.ui.FocusableButton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.makeran218.recommendtmdb.AppPreferences
import com.makeran218.recommendtmdb.CatalogEntry
import com.makeran218.recommendtmdb.ui.CatalogSelectionScreen
import com.makeran218.recommendtmdb.ui.dialogs.AddManifestDialog
import com.makeran218.recommendtmdb.ui.dialogs.ManageManifestsDialog
import com.makeran218.recommendtmdb.viewmodel.AppViewModel

// ==========================================
// Main Screen — Router
// ==========================================

@Composable
fun MainScreen(viewModel: AppViewModel) {
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
// Setup Screen
// ==========================================

@Composable
fun SetupScreen(viewModel: AppViewModel) {
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

// ==========================================
// Main Content
// ==========================================

@Composable
fun MainContent(
    viewModel: AppViewModel,
    manifestUrls: List<String>,
    catalogs: Map<String, List<CatalogEntry>>,
    settings: AppPreferences.Settings
) {
    var showManageDialog by remember { mutableStateOf(false) }
    var showCatalogSelection by remember { mutableStateOf(false) }

    val allCatalogs = remember(catalogs) {
        catalogs.flatMap { (manifestUrl, catalogList) ->
            catalogList.map { it to manifestUrl }
        }
    }

    val totalCatalogs = remember(catalogs) {
        catalogs.values.sumOf { it.size }
    }
    val enabledCatalogs = remember(catalogs) {
        catalogs.values.sumOf { it.count { c -> c.enabled } }
    }

    if (showCatalogSelection) {
        // ── Catalog Selection — full screen, hide everything else ──
        val catalogDisplayTypes by viewModel.catalogDisplayTypes.collectAsState(initial = emptyMap())
        CatalogSelectionScreen(
            allCatalogs = allCatalogs,
            enabledCount = enabledCatalogs,
            totalCount = totalCatalogs,
            catalogDisplayTypes = catalogDisplayTypes,
            onBack = { showCatalogSelection = false },
            onToggle = { key, enabled -> viewModel.toggleCatalog(key, enabled) },
            onDisplayTypeChange = { key, displayType -> viewModel.setCatalogDisplayType(key, displayType) }
        )
    } else {
        // ── Main Content ──
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Title ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("TV Home", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("${manifestUrls.size} manifest(s)", fontSize = 11.sp, color = Color.Gray)
            }

            // ── Action Buttons ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FocusableButton(
                    onClick = { viewModel.syncChannels() },
                    modifier = Modifier.width(140.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Sync, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Sync Channels", fontSize = 12.sp)
                }
                FocusableButton(
                    onClick = { showManageDialog = true },
                    modifier = Modifier.width(140.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Settings, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Manage", fontSize = 12.sp)
                }
            }

            // ── Settings ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Poster Settings
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Poster Settings:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val selected = settings.displayType
                        for ((key, label) in listOf("POSTER" to "Poster", "WIDE" to "Wide")) {
                            FocusableButton(
                                onClick = { viewModel.setDisplayType(key) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    label,
                                    fontSize = 11.sp,
                                    color = if (selected == key) MaterialTheme.colorScheme.primary else Color.White
                                )
                            }
                        }
                    }
                }

                // Player Settings
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Player Settings:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val selected = settings.playbackProvider
                        for ((key, label) in listOf("nuvio" to "Nuvio", "stremio" to "Stremio")) {
                            FocusableButton(
                                onClick = { viewModel.setPlaybackProvider(key) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    label,
                                    fontSize = 11.sp,
                                    color = if (selected == key) MaterialTheme.colorScheme.primary else Color.White
                                )
                            }
                        }
                    }
                }
            }

            // ── Catalogs Summary ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Catalogs ($enabledCatalogs / $totalCatalogs)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (enabledCatalogs == 0 && totalCatalogs > 0)
                            "⚠ Tap button to enable catalogs"
                        else
                            "$enabledCatalogs catalog${if (enabledCatalogs != 1) "s" else ""} enabled",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
                FocusableButton(
                    onClick = { showCatalogSelection = true },
                    modifier = Modifier.width(200.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Default.Settings, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Manage Catalogs", fontSize = 12.sp)
                }
            }
        }
    }

    // Manage manifests dialog (only when NOT in catalog selection)
    if (showManageDialog) {
        ManageManifestsDialog(
            manifestUrls = manifestUrls,
            onDismiss = { showManageDialog = false },
            onAdd = { url -> viewModel.addManifestUrl(url) },
            onRemove = { url -> viewModel.removeManifestUrl(url) },
            onRefetchManifests = { viewModel.refetchManifests() }
        )
    }
}
