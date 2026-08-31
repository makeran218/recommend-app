package com.makeran218.recommendtmdb.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.makeran218.recommendtmdb.CatalogEntry
import com.makeran218.recommendtmdb.ui.FocusableButton

/** Display type options */
private val DISPLAY_TYPE_OPTIONS = listOf("DEFAULT", "POSTER", "WIDE")

/** Display labels for each option */
private val DISPLAY_TYPE_LABELS = mapOf(
    "DEFAULT" to "Default",
    "POSTER" to "Poster",
    "WIDE" to "Wide"
)

/** Number of catalogs displayed per page (6 rows × 3 cols = 18) */
private const val CATALOGS_PER_PAGE = 18

/** Number of columns in the catalog grid */
private const val CATALOG_COLUMNS = 3

/** Data class for catalog detail state in the modal */
data class CatalogDetailState(
    val catalog: CatalogEntry,
    val manifestUrl: String,
    val enabled: Boolean,
    val displayType: String
) {
    val key: String
        get() = "$manifestUrl::${catalog.catalogType}::${catalog.catalogId}"
}

@Composable
fun CatalogSelectionScreen(
    allCatalogs: List<Pair<CatalogEntry, String>>,
    enabledCount: Int,
    totalCount: Int,
    catalogDisplayTypes: Map<String, String>,
    onBack: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onDisplayTypeChange: (String, String) -> Unit
) {
    var currentPage by remember { mutableStateOf(0) }
    val totalPages = (totalCount + CATALOGS_PER_PAGE - 1) / CATALOGS_PER_PAGE

    // Track which catalog is selected (opened in modal)
    var selectedCatalog by remember { mutableStateOf<CatalogDetailState?>(null) }

    // Intercept TV back button — only when modal is NOT open
    if (selectedCatalog == null) {
        BackHandler(onBack = onBack)
    }

    // Track which chip should receive focus after page change
    var pendingFocusColumn by remember { mutableStateOf<Int?>(null) }

    // Slice current page
    val pageItems = remember(allCatalogs, currentPage) {
        val start = currentPage * CATALOGS_PER_PAGE
        val end = minOf((currentPage + 1) * CATALOGS_PER_PAGE, allCatalogs.size)
        allCatalogs.subList(start, end)
    }

    val rows = remember(pageItems) { pageItems.chunked(CATALOG_COLUMNS) }

    // ── Main content (grid + pagination) — only when modal is NOT open ──
    if (selectedCatalog == null) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Header ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Manage Catalogs",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Page ${currentPage + 1} / $totalPages  •  $enabledCount / $totalCount enabled",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }

                // ── Catalog Grid (6 rows × 3 cols = 18 items per page) ──
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    for ((rowIndex, rowItems) in rows.withIndex()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            for ((colIndex, item) in rowItems.withIndex()) {
                                val (catalog, manifestUrl) = item
                                val key = "$manifestUrl::${catalog.catalogType}::${catalog.catalogId}"
                                val isLeftEdge = colIndex == 0
                                val isRightEdge = colIndex == (CATALOG_COLUMNS - 1)
                                val isTargetForFocus = pendingFocusColumn == colIndex
                                val currentDisplayType = catalogDisplayTypes[key] ?: "DEFAULT"

                                Box(modifier = Modifier.weight(1f)) {
                                    CatalogChip(
                                        catalog = catalog,
                                        manifestUrl = manifestUrl,
                                        isLeftEdge = isLeftEdge,
                                        isRightEdge = isRightEdge,
                                        onDirection = { direction ->
                                            var handled = false
                                            when (direction) {
                                                "left" -> {
                                                    if (currentPage > 0) {
                                                        currentPage--
                                                        pendingFocusColumn = 0
                                                        handled = true
                                                    }
                                                }

                                                "right" -> {
                                                    if (currentPage < totalPages - 1) {
                                                        currentPage++
                                                        pendingFocusColumn = 0
                                                        handled = true
                                                    }
                                                }
                                            }
                                            handled
                                        },
                                        isTargetChip = isTargetForFocus,
                                        currentDisplayType = currentDisplayType,
                                        onOpenDetail = {
                                            selectedCatalog = CatalogDetailState(
                                                catalog = catalog,
                                                manifestUrl = manifestUrl,
                                                enabled = catalog.enabled,
                                                displayType = currentDisplayType
                                            )
                                        }
                                    )
                                }

                                // Clear pending focus after first chip renders
                                if (isTargetForFocus) {
                                    pendingFocusColumn = null
                                }
                            }
                            // Fill empty slots in last row
                            if (rowItems.size < CATALOG_COLUMNS) {
                                repeat(CATALOG_COLUMNS - rowItems.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                // ── Pagination Controls (unfocusable — D-pad only lands on chips) ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back button — unfocusable, outlined style
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier
                            .width(120.dp)
                            .focusable(false),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))
                    ) {
                        Icon(Icons.Default.ArrowBack, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Back", fontSize = 12.sp)
                    }

                    // Page indicator dots
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (i in 0 until totalPages) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(
                                        color = if (i == currentPage)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            Color.Gray.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                            )
                        }
                    }

                    // Prev / Next buttons — unfocusable, outlined style
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(
                            onClick = { if (currentPage > 0) currentPage-- },
                            enabled = currentPage > 0,
                            modifier = Modifier
                                .width(120.dp)
                                .focusable(false),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))
                        ) {
                            Icon(Icons.Default.ArrowBack, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Prev", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { if (currentPage < totalPages - 1) currentPage++ },
                            enabled = currentPage < totalPages - 1,
                            modifier = Modifier
                                .width(120.dp)
                                .focusable(false),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))
                        ) {
                            Text("Next", fontSize = 11.sp)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }

    // ── Catalog Detail Modal ──
    selectedCatalog?.let { state ->
        CatalogDetailModal(
            state = state,
            onDismiss = { finalState ->
                // Save the final state — passed directly from modal (no race condition)
                onToggle(finalState.key, finalState.enabled)
                onDisplayTypeChange(finalState.key, finalState.displayType)
                selectedCatalog = null
            },
            onEnabledChange = { enabled ->
                // Build on current selectedCatalog so both settings are preserved
                selectedCatalog?.copy(enabled = enabled)?.let { selectedCatalog = it }
            },
            onDisplayTypeChange = { displayType ->
                // Build on current selectedCatalog so both settings are preserved
                selectedCatalog?.copy(displayType = displayType)?.let { selectedCatalog = it }
            }
        )
    }
}

/**
 * Catalog chip — simple clickable card.
 * Pressing OK/Enter on this chip opens the detail modal.
 */
@Composable
private fun CatalogChip(
    catalog: CatalogEntry,
    manifestUrl: String,
    isLeftEdge: Boolean = false,
    isRightEdge: Boolean = false,
    onDirection: ((String) -> Boolean)? = null,
    isTargetChip: Boolean = false,
    currentDisplayType: String = "DEFAULT",
    onOpenDetail: () -> Unit
) {
    val key = "$manifestUrl::${catalog.catalogType}::${catalog.catalogId}"
    var isFocused by remember(key) { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    // Request focus when this chip is the target
    if (isTargetChip) {
        focusRequester.requestFocus()
    }

    val displayTypeColor = when (currentDisplayType) {
        "DEFAULT" -> Color.Gray
        "POSTER" -> Color(0xFFFFB74D) // amber
        "WIDE" -> Color(0xFF4FC3F7) // light blue
        else -> Color.Gray
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            }
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                // Handle D-pad left/right for auto-scroll
                event.type == KeyEventType.KeyDown && when (event.key) {
                    Key.DirectionLeft -> isLeftEdge && isFocused && onDirection?.invoke("left") == true
                    Key.DirectionRight -> isRightEdge && isFocused && onDirection?.invoke("right") == true
                    else -> false
                }
            }
            .border(
                if (isFocused) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                else BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                MaterialTheme.shapes.small
            )
            .onPreviewKeyEvent { event ->
                // Handle opening the modal — D-pad center works on real Android TV hardware
                if (event.type == KeyEventType.KeyUp && isFocused) {
                    when (event.key) {
                        Key.Enter, Key.DirectionCenter, Key.NumPadEnter -> {
                            onOpenDetail()
                            true
                        }

                        else -> false
                    }
                } else {
                    false
                }
            }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Icon + label on the left
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                if (catalog.catalogType == "series") Icons.Default.VideoLibrary else Icons.Default.PlayCircle,
                contentDescription = null,
                tint = if (isFocused) Color.White else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Column {
                Text(
                    catalog.catalogName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isFocused) Color.White else Color.White,
                    maxLines = 1
                )
                Text(
                    catalog.catalogType.uppercase(),
                    fontSize = 10.sp,
                    color = if (isFocused) Color.White.copy(alpha = 0.7f) else Color.Gray,
                    style = TextStyle(lineHeight = 9.sp)
                )
            }
        }

        // Display type + status grouped together on the right
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Display type indicator badge
            Box(
                modifier = Modifier
                    .background(
                        color = displayTypeColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = DISPLAY_TYPE_LABELS[currentDisplayType] ?: "Default",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = displayTypeColor
                )
            }

            // Status indicator
            val statusText = if (catalog.enabled) "ON" else "OFF"
            val statusColor = if (catalog.enabled) Color(0xFF66BB6A) else Color.Gray
            Text(
                text = statusText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = statusColor
            )
        }
    }
}

/**
 * Modal dialog for editing a catalog's settings.
 * Pressing Back or Close saves the current state.
 */
@Composable
private fun CatalogDetailModal(
    state: CatalogDetailState,
    onDismiss: (CatalogDetailState) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onDisplayTypeChange: (String) -> Unit
) {
    // Local mutable state for the modal — saves only on dismiss
    var localEnabled by remember(state) { mutableStateOf(state.enabled) }
    var localDisplayType by remember(state) { mutableStateOf(state.displayType) }

    val finalState = CatalogDetailState(
        catalog = state.catalog,
        manifestUrl = state.manifestUrl,
        enabled = localEnabled,
        displayType = localDisplayType
    )

    BackHandler {
        // Save and dismiss with the final state — no race condition
        onDismiss(finalState)
    }

    Dialog(onDismissRequest = { onDismiss(finalState) }) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .width(320.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title
                Text(
                    "Catalog Settings",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                // Catalog name
                Text(
                    state.catalog.catalogName,
                    fontSize = 14.sp,
                    color = Color.White
                )
                Text(
                    state.catalog.catalogType.uppercase(),
                    fontSize = 11.sp,
                    color = Color.Gray
                )

                Spacer(Modifier.height(8.dp))

                // ── Toggle ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable", fontSize = 14.sp, color = Color.White)
                    Switch(
                        checked = localEnabled,
                        onCheckedChange = { localEnabled = it }
                    )
                }

                // ── Display Type ──
                Text("Display Type", fontSize = 14.sp, color = Color.White)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (option in DISPLAY_TYPE_OPTIONS) {
                        val isSelected = localDisplayType == option
                        val optionColor = when (option) {
                            "DEFAULT" -> Color.Gray
                            "POSTER" -> Color(0xFFFFB74D)
                            "WIDE" -> Color(0xFF4FC3F7)
                            else -> Color.Gray
                        }
                        FocusableButton(
                            onClick = { localDisplayType = option },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isSelected) {
                                    Text("✓ ", fontSize = 14.sp, color = optionColor)
                                }
                                Text(DISPLAY_TYPE_LABELS[option] ?: option, fontSize = 13.sp)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // ── Close Button ──
                FocusableButton(
                    onClick = {
                        // Save and dismiss with the final state — no race condition
                        onDismiss(finalState)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close & Save", fontSize = 14.sp)
                }
            }
        }
    }
}
