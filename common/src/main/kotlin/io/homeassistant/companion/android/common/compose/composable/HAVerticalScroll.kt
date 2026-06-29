package io.homeassistant.companion.android.common.compose.composable

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme

/**
 * A vertically scrollable modifier with a persistent position indicator whenever content exceeds
 * the viewport. This keeps scrolling discoverable on non-touch and glasses displays.
 */
@Composable
fun Modifier.haVerticalScroll(state: ScrollState): Modifier {
    val indicatorColor = LocalHAColorScheme.current.colorOnNeutralQuiet.copy(alpha = 0.72f)
    val trackColor = LocalHAColorScheme.current.colorOnNeutralQuiet.copy(alpha = 0.18f)
    return drawWithContent {
        drawContent()
        if (state.maxValue <= 0 || size.height <= 0f) return@drawWithContent

        val verticalMargin = SCROLLBAR_VERTICAL_MARGIN.toPx()
        val trackHeight = (size.height - verticalMargin * 2).coerceAtLeast(0f)
        val contentHeight = size.height + state.maxValue
        val thumbHeight = (size.height / contentHeight * trackHeight)
            .coerceIn(MINIMUM_THUMB_HEIGHT.toPx().coerceAtMost(trackHeight), trackHeight)
        val availableTravel = (trackHeight - thumbHeight).coerceAtLeast(0f)
        val progress = state.value.toFloat() / state.maxValue.toFloat()
        val thumbTop = verticalMargin + availableTravel * progress
        val trackWidth = SCROLLBAR_TRACK_WIDTH.toPx()
        val thumbWidth = SCROLLBAR_THUMB_WIDTH.toPx()

        drawRoundRect(
            color = trackColor,
            topLeft = Offset(size.width - SCROLLBAR_END_MARGIN.toPx() - trackWidth, verticalMargin),
            size = Size(trackWidth, trackHeight),
            cornerRadius = CornerRadius(trackWidth / 2f),
        )
        drawRoundRect(
            color = indicatorColor,
            topLeft = Offset(size.width - SCROLLBAR_END_MARGIN.toPx() - thumbWidth, thumbTop),
            size = Size(thumbWidth, thumbHeight),
            cornerRadius = CornerRadius(thumbWidth / 2f),
        )
    }.verticalScroll(state)
}

private val SCROLLBAR_VERTICAL_MARGIN = 4.dp
private val SCROLLBAR_END_MARGIN = 2.dp
private val SCROLLBAR_TRACK_WIDTH = 2.dp
private val SCROLLBAR_THUMB_WIDTH = 3.dp
private val MINIMUM_THUMB_HEIGHT = 28.dp
