package com.makeran218.recommendtmdb

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
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
import com.makeran218.recommendtmdb.ui.FocusableButton
import com.makeran218.recommendtmdb.ui.screens.MainContent
import com.makeran218.recommendtmdb.viewmodel.AppViewModel

// ==========================================
// Activity
// ==========================================

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

        val viewModel = AppViewModel(application)

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

        FocusableButton(
            onClick = { showAddDialog = true },
            modifier = Modifier.fillMaxWidth()
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

@Composable
private fun AddManifestDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
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
                    FocusableButton(
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
// Theme
// ==========================================

@Composable
fun TVHomeTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = Color(0xFFC2185B),
        onPrimary = Color.White,
        background = Color(0xFF0A0A14),
        onBackground = Color.White,
        surface = Color(0xFF0E0E1A),
        onSurface = Color.White
    )
    MaterialTheme(colorScheme = colors, content = content)
}
