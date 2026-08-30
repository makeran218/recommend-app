package com.makeran218.recommendtmdb.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.makeran218.recommendtmdb.CatalogEntry

/** Number of catalogs displayed per page (6 rows × 3 cols = 18) */
private const val CATALOGS_PER_PAGE = 18

/** Number of columns in the catalog grid */
private const val CATALOG_COLUMNS = 3

@Composable
fun CatalogSelectionScreen(
    allCatalogs: List<Pair<CatalogEntry, String>>,
    enabledCount: Int,
    totalCount: Int,
    onBack: () -> Unit,
    onToggle: (String, Boolean) -> Unit
) {
    var currentPage by remember { mutableStateOf(0) }
    val totalPages = (totalCount + CATALOGS_PER_PAGE - 1) / CATALOGS_PER_PAGE

    // Intercept TV back button
    BackHandler(onBack = onBack)

    // Track which chip should receive focus after page change
    var pendingFocusColumn by remember { mutableStateOf<Int?>(null) }

    // Slice current page
    val pageItems = remember(allCatalogs, currentPage) {
        val start = currentPage * CATALOGS_PER_PAGE
        val end = minOf((currentPage + 1) * CATALOGS_PER_PAGE, allCatalogs.size)
        allCatalogs.subList(start, end)
    }

    val rows = remember(pageItems) { pageItems.chunked(CATALOG_COLUMNS) }

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

                            Box(modifier = Modifier.weight(1f)) {
                                CatalogChip(
                                    catalog = catalog,
                                    manifestUrl = manifestUrl,
                                    onToggle = { onToggle(key, it) },
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
                                    isTargetChip = isTargetForFocus
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
                // Back button — unfocusable
                Button(
                    onClick = onBack,
                    modifier = Modifier
                        .width(120.dp)
                        .focusable(false),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
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

                // Prev / Next buttons — unfocusable
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(
                        onClick = { if (currentPage > 0) currentPage-- },
                        enabled = currentPage > 0,
                        modifier = Modifier
                            .width(120.dp)
                            .focusable(false),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Prev", fontSize = 11.sp)
                    }
                    Button(
                        onClick = { if (currentPage < totalPages - 1) currentPage++ },
                        enabled = currentPage < totalPages - 1,
                        modifier = Modifier
                            .width(120.dp)
                            .focusable(false),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Next", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogChip(
    catalog: CatalogEntry,
    manifestUrl: String,
    onToggle: (Boolean) -> Unit,
    isLeftEdge: Boolean = false,
    isRightEdge: Boolean = false,
    onDirection: ((String) -> Boolean)? = null,
    isTargetChip: Boolean = false
) {
    val key = "$manifestUrl::${catalog.catalogType}::${catalog.catalogId}"
    var isFocused by remember(key) { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    // Request focus when this chip is the target
    if (isTargetChip) {
        focusRequester.requestFocus()
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
            .background(
                color = if (isFocused) Color(0xFF4D50FF) else Color.Transparent,
                shape = MaterialTheme.shapes.small
            )
            .padding(4.dp),
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

        // Switch on the far right — not independently focusable
        Switch(
            checked = catalog.enabled,
            onCheckedChange = { newValue ->
                onToggle(newValue)
                // Re-request focus so the chip keeps its focus background after toggle
                focusRequester.requestFocus()
            },
            modifier = Modifier
                .focusable(false)
        )
    }
}
