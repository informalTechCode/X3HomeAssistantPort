package io.homeassistant.companion.android.onboarding.welcome

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.ButtonSize
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.composable.haVerticalScroll
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.MaxButtonWidth

private val ICON_SIZE = 120.dp
private val COMPACT_ICON_SIZE = 72.dp
private val COMPACT_VIEWPORT_MAX_HEIGHT = 520.dp

/**
 * Maximum width applied to the textual content of the welcome screens so it stays readable on
 * large screens. Shared so that any extra [WelcomeTemplate] content (such as a warning) can align
 * with the title and details.
 */
internal val WelcomeContentMaxWidth = MaxButtonWidth

/**
 * Shared layout for the onboarding welcome screens ([WelcomeScreen] and [WelcomeInvitationScreen]).
 *
 * Displays the Home Assistant branding, a [title] and [details], any optional [content] placed below
 * the details, and two bottom action buttons: a primary ([primaryButtonText]/[onPrimaryClick]) and a
 * secondary ([secondaryButtonText]/[onSecondaryClick]). An optional [topBar] is shown above the
 * content; pass an [io.homeassistant.companion.android.common.compose.composable.HATopBar] for screens
 * that need top actions.
 */
@Composable
internal fun WelcomeTemplate(
    title: String,
    details: String,
    primaryButtonText: String,
    onPrimaryClick: () -> Unit,
    secondaryButtonText: String,
    onSecondaryClick: () -> Unit,
    modifier: Modifier = Modifier,
    compactForShortViewport: Boolean = false,
    topBar: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        topBar = topBar,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            val compact = compactForShortViewport && maxHeight <= COMPACT_VIEWPORT_MAX_HEIGHT
            val spacing = if (compact) HADimens.SPACE2 else HADimens.SPACE6
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .haVerticalScroll(rememberScrollState())
                    .padding(
                        horizontal = HADimens.SPACE4,
                        vertical = if (compact) HADimens.SPACE1 else HADimens.SPACE0,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing),
            ) {
                // Position the regular welcome content lower when surplus height is available.
                val positionPercentage = 0.2f
                if (!compact) Spacer(modifier = Modifier.weight(positionPercentage))

                Image(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_home_assistant_branding),
                    contentDescription = stringResource(
                        commonR.string.home_assistant_branding_icon_content_description,
                    ),
                    modifier = Modifier.size(if (compact) COMPACT_ICON_SIZE else ICON_SIZE),
                )

                Text(
                    text = title,
                    style = if (compact) {
                        HATextStyle.HeadlineMedium.copy(fontSize = 24.sp, lineHeight = 28.sp)
                    } else {
                        HATextStyle.Headline
                    },
                    modifier = Modifier.widthIn(max = WelcomeContentMaxWidth),
                )
                Text(
                    text = details,
                    style = if (compact) HATextStyle.BodyMedium else HATextStyle.Body,
                    modifier = Modifier.widthIn(max = WelcomeContentMaxWidth),
                )

                content()

                if (!compact) Spacer(modifier = Modifier.weight(1f - positionPercentage))

                BottomButtons(
                    primaryButtonText = primaryButtonText,
                    onPrimaryClick = onPrimaryClick,
                    secondaryButtonText = secondaryButtonText,
                    onSecondaryClick = onSecondaryClick,
                    compact = compact,
                )
            }
        }
    }
}

@Composable
private fun BottomButtons(
    primaryButtonText: String,
    onPrimaryClick: () -> Unit,
    secondaryButtonText: String,
    onSecondaryClick: () -> Unit,
    compact: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) HADimens.SPACE1 else HADimens.SPACE4),
    ) {
        HAAccentButton(
            text = primaryButtonText,
            onClick = onPrimaryClick,
            size = if (compact) ButtonSize.SMALL else ButtonSize.MEDIUM,
            modifier = Modifier.fillMaxWidth(),
        )

        HAPlainButton(
            text = secondaryButtonText,
            onClick = onSecondaryClick,
            size = if (compact) ButtonSize.SMALL else ButtonSize.MEDIUM,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = if (compact) HADimens.SPACE1 else HADimens.SPACE6),
        )
    }
}
