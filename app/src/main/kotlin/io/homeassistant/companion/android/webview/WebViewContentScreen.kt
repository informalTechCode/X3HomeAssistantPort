package io.homeassistant.companion.android.webview

import android.annotation.SuppressLint
import android.os.Build
import android.view.View
import android.webkit.WebView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.theme.HATheme
import io.homeassistant.companion.android.common.data.prefs.NightModeTheme
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.frontend.permissions.NotificationPermissionPrompt
import io.homeassistant.companion.android.util.compose.media.player.HAMediaPlayer
import io.homeassistant.companion.android.util.compose.webview.HAWebView
import io.homeassistant.companion.android.webview.rayneo.RayNeoCursorPosition
import io.homeassistant.companion.android.webview.rayneo.RayNeoKeyboardController
import io.homeassistant.companion.android.webview.rayneo.RayNeoKeyboardState
import io.homeassistant.companion.android.webview.rayneo.RayNeoWebViewMirror
import kotlinx.coroutines.launch
import timber.log.Timber

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
internal fun WebViewContentScreen(
    webView: WebView?,
    player: Player?,
    snackbarHostState: SnackbarHostState,
    playerSize: DpSize?,
    playerTop: Dp,
    playerLeft: Dp,
    currentAppLocked: Boolean,
    customViewFromWebView: View?,
    shouldAskNotificationPermission: Boolean,
    webViewInitialized: Boolean,
    onFullscreenClicked: (isFullscreen: Boolean) -> Unit,
    onNotificationPermissionResult: (Boolean) -> Unit,
    serverHandleInsets: Boolean,
    nightModeTheme: NightModeTheme? = null,
    statusBarColor: Color? = null,
    backgroundColor: Color? = null,
    supportsNotificationPermission: Boolean = SdkVersion.isAtLeast(Build.VERSION_CODES.TIRAMISU),
    rayNeoMirror: RayNeoWebViewMirror? = null,
    rayNeoCursorPosition: RayNeoCursorPosition = RayNeoCursorPosition(),
    rayNeoCursorVisible: Boolean = true,
    rayNeoKeyboardController: RayNeoKeyboardController? = null,
    rayNeoKeyboardVisible: Boolean = false,
    onRayNeoViewportChanged: (width: Int, height: Int, x: Float, y: Float) -> Unit = { _, _, _, _ -> },
) {
    HATheme {
        Scaffold(
            snackbarHost = {
                SnackbarHost(
                    snackbarHostState,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
                )
            },
            // Delegate the insets handling to the webview
            contentWindowInsets = WindowInsets(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colorResource(commonR.color.colorLaunchScreenBackground)),
            ) {
                if (rayNeoMirror == null) {
                    WebViewEyeContent(
                        webView = webView,
                        player = player,
                        snackbarHostState = snackbarHostState,
                        playerSize = playerSize,
                        playerTop = playerTop,
                        playerLeft = playerLeft,
                        currentAppLocked = currentAppLocked,
                        customViewFromWebView = customViewFromWebView,
                        onFullscreenClicked = onFullscreenClicked,
                        serverHandleInsets = serverHandleInsets,
                        nightModeTheme = nightModeTheme,
                        statusBarColor = statusBarColor,
                        backgroundColor = backgroundColor,
                    )
                } else {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .onGloballyPositioned { coordinates ->
                                    val position = coordinates.positionInWindow()
                                    onRayNeoViewportChanged(
                                        coordinates.size.width,
                                        coordinates.size.height,
                                        position.x,
                                        position.y,
                                    )
                                },
                        ) {
                            WebViewEyeContent(
                                webView = webView,
                                player = player,
                                snackbarHostState = snackbarHostState,
                                playerSize = playerSize,
                                playerTop = playerTop,
                                playerLeft = playerLeft,
                                currentAppLocked = currentAppLocked,
                                customViewFromWebView = customViewFromWebView,
                                onFullscreenClicked = onFullscreenClicked,
                                serverHandleInsets = serverHandleInsets,
                                nightModeTheme = nightModeTheme,
                                statusBarColor = statusBarColor,
                                backgroundColor = backgroundColor,
                            )
                            if (rayNeoCursorVisible) RayNeoCursor(rayNeoCursorPosition)
                            if (rayNeoKeyboardVisible && rayNeoKeyboardController != null) {
                                RayNeoKeyboard(
                                    state = rayNeoKeyboardController.state.value,
                                    onKeyClick = rayNeoKeyboardController::press,
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(Color.Black),
                        ) {
                            AndroidView(
                                factory = { rayNeoMirror },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
        if (webViewInitialized && shouldAskNotificationPermission && supportsNotificationPermission) {
            @SuppressLint("InlinedApi")
            NotificationPermissionPrompt(
                onPermissionResult = onNotificationPermissionResult,
                onDismiss = {},
            )
        }
    }
}

@Composable
private fun BoxScope.WebViewEyeContent(
    webView: WebView?,
    player: Player?,
    snackbarHostState: SnackbarHostState,
    playerSize: DpSize?,
    playerTop: Dp,
    playerLeft: Dp,
    currentAppLocked: Boolean,
    customViewFromWebView: View?,
    onFullscreenClicked: (isFullscreen: Boolean) -> Unit,
    serverHandleInsets: Boolean,
    nightModeTheme: NightModeTheme?,
    statusBarColor: Color?,
    backgroundColor: Color?,
) {
    SafeHAWebView(
        webView,
        nightModeTheme,
        snackbarHostState = snackbarHostState,
        currentAppLocked = currentAppLocked,
        statusBarColor = statusBarColor,
        backgroundColor = backgroundColor,
        serverHandleInsets = serverHandleInsets,
    )

    player?.let { activePlayer ->
        playerSize?.let { size ->
            HAMediaPlayer(
                player = activePlayer,
                contentScale = ContentScale.Inside,
                modifier = Modifier
                    .offset(playerLeft, playerTop)
                    .size(size),
                fullscreenModifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                onFullscreenClicked = onFullscreenClicked,
            )
        }
    }
    customViewFromWebView?.let { customView ->
        AndroidView(
            factory = { customView },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun BoxScope.RayNeoCursor(position: RayNeoCursorPosition) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawCircle(
            color = Color.Black,
            radius = 8.dp.toPx(),
            center = androidx.compose.ui.geometry.Offset(position.x, position.y),
        )
        drawCircle(
            color = Color.White,
            radius = 5.dp.toPx(),
            center = androidx.compose.ui.geometry.Offset(position.x, position.y),
        )
    }
}

@Composable
private fun BoxScope.RayNeoKeyboard(
    state: RayNeoKeyboardState,
    onKeyClick: (io.homeassistant.companion.android.webview.rayneo.RayNeoKeyboardAction) -> Unit,
) {
    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.94f))
            .padding(2.dp),
    ) {
        state.rows.forEachIndexed { rowIndex, keys ->
            Row(modifier = Modifier.fillMaxWidth()) {
                keys.forEachIndexed { columnIndex, key ->
                    val selected = rowIndex == state.selectedRow && columnIndex == state.selectedColumn
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(key.weight)
                            .height(36.dp)
                            .padding(1.dp)
                            .background(if (selected) Color.White else Color(0xff262626))
                            .clickable { onKeyClick(key.action) },
                    ) {
                        BasicText(
                            text = key.label,
                            style = TextStyle(
                                color = if (selected) Color.Black else Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Wrapper for WebView, blurring the contents when the app is locked.
 *
 * If the Home Assistant frontend does not handle edge-to-edge insets
 * (core <2025.12), it also wraps the WebView with colored overlays matching
 * the safe area insets.
 *
 * This wrapper ensures the [HAWebView] is not removed from composition when
 * the app lock, theme or server inset support changes, to avoid losing loading
 * progress or frontend state when it isn't necessary.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
private fun SafeHAWebView(
    webView: WebView?,
    nightModeTheme: NightModeTheme?,
    snackbarHostState: SnackbarHostState,
    currentAppLocked: Boolean,
    statusBarColor: Color?,
    backgroundColor: Color?,
    serverHandleInsets: Boolean,
) {
    val hazeModifier = if (currentAppLocked) Modifier.hazeEffect(style = HazeMaterials.thin()) else Modifier
    val insets = WindowInsets.safeDrawing
    val insetsPaddingValues = insets.asPaddingValues()
    val coroutineScope = rememberCoroutineScope()
    val webViewCreationFailedMessage = stringResource(commonR.string.webview_creation_failed)

    Column(modifier = hazeModifier) {
        if (!serverHandleInsets) {
            statusBarColor?.Overlay(
                modifier = Modifier
                    .height(insetsPaddingValues.calculateTopPadding())
                    .fillMaxWidth()
                    // We don't want the status bar to color the left and right areas
                    .padding(insets.only(WindowInsetsSides.Horizontal).asPaddingValues()),
            )
        }
        // The height is based on whatever is left between the statusBar and navigationBar
        Row(modifier = Modifier.weight(1f)) {
            if (!serverHandleInsets) {
                // Left safe area
                backgroundColor?.Overlay(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(insetsPaddingValues.calculateLeftPadding(LayoutDirection.Ltr)),
                )
            }
            HAWebView(
                nightModeTheme = nightModeTheme,
                factory = { webView },
                onWebViewCreationFailed = { exception ->
                    Timber.e(exception, "Failed to instantiate WebView")
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(webViewCreationFailedMessage)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .background(Color.Transparent),
            )
            if (!serverHandleInsets) {
                // Right safe area
                backgroundColor?.Overlay(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(insetsPaddingValues.calculateRightPadding(LayoutDirection.Ltr)),
                )
            }
        }
        if (!serverHandleInsets) {
            backgroundColor?.Overlay(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(insetsPaddingValues.calculateBottomPadding()),
            )
        }
    }
}

@Composable
private fun Color.Overlay(modifier: Modifier = Modifier) {
    Spacer(
        modifier = modifier
            .background(this),
    )
}

@Preview
@Composable
private fun WebViewContentScreenPreview() {
    WebViewContentScreen(
        webView = null,
        player = null,
        snackbarHostState = SnackbarHostState(),
        playerSize = null,
        playerTop = 0.dp,
        playerLeft = 0.dp,
        currentAppLocked = false,
        shouldAskNotificationPermission = false,
        webViewInitialized = true,
        customViewFromWebView = null,
        onFullscreenClicked = {},
        onNotificationPermissionResult = {},
        serverHandleInsets = false,
    )
}
