package com.makeran218.recommendtmdb.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Outlined button with a visible border when focused (for D-pad/TV navigation).
 * Transparent background, pink text, white border by default.
 * When focused, a thick pink border appears so the active state is obvious.
 */
@Composable
fun FocusableButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val border = if (isFocused) {
        BorderStroke(3.dp, MaterialTheme.colorScheme.primary) // pink border when focused
    } else {
        BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)) // subtle white border
    }

    OutlinedButton(
        onClick = onClick,
        modifier = modifier.onFocusChanged { focusState ->
            isFocused = focusState.isFocused
        },
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary // pink text
        ),
        border = border,
        contentPadding = contentPadding,
        content = content
    )
}
