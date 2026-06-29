package io.homeassistant.companion.android.webview.rayneo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** TapLink's four-row keyboard, constrained to the calling eye's [BoxScope]. */
@Composable
internal fun BoxScope.RayNeoKeyboard(state: RayNeoKeyboardState, onKeyClick: (RayNeoKeyboardAction) -> Unit) {
    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .background(Color.Black),
    ) {
        state.rows.forEachIndexed { rowIndex, keys ->
            Row(modifier = Modifier.fillMaxWidth()) {
                keys.forEachIndexed { columnIndex, key ->
                    val selected = rowIndex == state.selectedRow && columnIndex == state.selectedColumn
                    val background = if (selected) TAPLINK_HOVER_BLUE else Color.DarkGray
                    val foreground = when {
                        selected -> Color.White
                        key.active -> Color.Cyan
                        else -> Color.White
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(key.weight)
                            .height(TAPLINK_KEY_HEIGHT)
                            .padding(TAPLINK_KEY_MARGIN)
                            .background(background)
                            .clickable { onKeyClick(key.action) },
                    ) {
                        if (key.action == RayNeoKeyboardAction.Hide) {
                            Icon(
                                imageVector = Icons.Default.VisibilityOff,
                                contentDescription = "Hide keyboard",
                                tint = foreground,
                                modifier = Modifier.padding(7.dp),
                            )
                        } else {
                            BasicText(
                                text = key.label,
                                style = TextStyle(
                                    color = foreground,
                                    fontSize = if (key.action == RayNeoKeyboardAction.Clear) 12.sp else 14.sp,
                                    fontWeight = FontWeight.Normal,
                                    textAlign = TextAlign.Center,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

private val TAPLINK_HOVER_BLUE = Color(0xff4488ff)
private val TAPLINK_KEY_HEIGHT = 35.dp
private val TAPLINK_KEY_MARGIN = 1.dp
