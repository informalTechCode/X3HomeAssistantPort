package io.homeassistant.companion.android.launch

import android.Manifest
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult.ActionPerformed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import io.homeassistant.companion.android.WIPFeature
import io.homeassistant.companion.android.authenticator.Authenticator
import io.homeassistant.companion.android.authenticator.Authenticator.Companion.AuthenticationResult
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.theme.HATheme
import io.homeassistant.companion.android.common.util.CheckLocalNetworkPermissionUseCase
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.launch.applock.HazeLockOverlay
import io.homeassistant.companion.android.sensors.SensorReceiver
import io.homeassistant.companion.android.sensors.SensorWorker
import io.homeassistant.companion.android.util.ChangeLog
import io.homeassistant.companion.android.util.CheckLocationDisabledUseCase
import io.homeassistant.companion.android.util.PLAY_SERVICES_FLAVOR_DOC_URL
import io.homeassistant.companion.android.util.PlayServicesAvailability
import io.homeassistant.companion.android.util.compose.HAApp
import io.homeassistant.companion.android.util.compose.navigateToUri
import io.homeassistant.companion.android.util.enableEdgeToEdgeCompat
import io.homeassistant.companion.android.websocket.WebsocketManager
import io.homeassistant.companion.android.webview.rayneo.GLASSES_INPUT_PORT
import io.homeassistant.companion.android.webview.rayneo.RayNeoBluetoothControllerClient
import io.homeassistant.companion.android.webview.rayneo.RayNeoControllerInputListener
import io.homeassistant.companion.android.webview.rayneo.RayNeoControllerMode
import io.homeassistant.companion.android.webview.rayneo.RayNeoCursorController
import io.homeassistant.companion.android.webview.rayneo.RayNeoCursorPosition
import io.homeassistant.companion.android.webview.rayneo.RayNeoNetworkInputServer
import io.homeassistant.companion.android.webview.rayneo.RayNeoPointerMapper
import io.homeassistant.companion.android.webview.rayneo.RayNeoScrollModeController
import io.homeassistant.companion.android.webview.rayneo.RayNeoTouchAction
import io.homeassistant.companion.android.webview.rayneo.RayNeoTrackpadAction
import io.homeassistant.companion.android.webview.rayneo.RayNeoWebViewMirror
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import timber.log.Timber

private const val DEEP_LINK_KEY = "deep_link_key"

/**
 * Fully qualified class name of the non-exported `<activity-alias>` declared in the manifest.
 *
 * Trusted in-process callers route through this alias to bring up the dashboard over the
 * keyguard. [LaunchActivity.onCreate] only calls [android.app.Activity.setShowWhenLocked] when
 * the inbound intent's component matches it — because the alias is `android:exported="false"`,
 * external apps cannot use it and therefore cannot force the activity to render over the lock
 * screen by themselves.
 */
private const val LOCK_SCREEN_ALIAS_CLASS = "io.homeassistant.companion.android.launch.LaunchOverLockScreen"

/**
 * Main entry point of the application, responsible for holding the whole navigation graph
 * and triggering lifecycle-based refresh of background work.
 *
 * It also handles the splash screen display based on a condition exposed by the [LaunchViewModel].
 *
 * On resume, refreshes the scheduling of periodic sensor collection via [SensorWorker]
 * and the background WebSocket work via [WebsocketManager].
 * These jobs are managed outside the Activity and may continue beyond this lifecycle.
 * On pause, triggers an immediate sensor update via [SensorReceiver] so the server
 * has fresh data before the app goes to the background.
 */
@AndroidEntryPoint
class LaunchActivity :
    AppCompatActivity(),
    RayNeoControllerInputListener {
    @Inject
    internal lateinit var playServicesAvailability: PlayServicesAvailability

    @Inject
    internal lateinit var checkLocationDisabled: CheckLocationDisabledUseCase

    @Inject
    internal lateinit var checkLocalNetworkPermission: CheckLocalNetworkPermissionUseCase

    @Inject
    internal lateinit var changeLog: ChangeLog

    /**
     * Represents deep link actions that can be passed to [LaunchActivity] to navigate to specific destinations.
     */
    @Parcelize
    sealed interface DeepLink : Parcelable {
        /**
         * Opens the onboarding flow for a new Home Assistant server.
         * @property urlToOnboard Optional server URL to connect to directly. If null, shows server discovery.
         * @property hideExistingServers When true, hides already registered servers from discovery results.
         * @property skipWelcome When true, skips the welcome screen and navigates directly to server discovery,
         *  or to the connection screen if [urlToOnboard] is provided.
         */
        data class OpenOnboarding(
            val urlToOnboard: String?,
            val hideExistingServers: Boolean,
            val skipWelcome: Boolean,
        ) : DeepLink

        /**
         * Opens the onboarding flow from an invitation link.
         *
         * @property serverUrl The Home Assistant server URL the invitation wants to connect to.
         */
        data class OpenInvitation(val serverUrl: String) : DeepLink

        /**
         * Navigates to a specific path within the webview.
         * @property path The path to navigate to within the Home Assistant interface.
         * @property serverId The ID of the server to use for navigation.
         */
        data class NavigateTo(val path: String?, val serverId: Int) : DeepLink

        /**
         * Opens the Wear OS device onboarding flow.
         * @property wearName The name of the Wear device being onboarded.
         * @property urlToOnboard Optional server URL to connect to directly. If null, shows server discovery.
         */
        data class OpenWearOnboarding(val wearName: String, val urlToOnboard: String?) : DeepLink
    }

    companion object {
        /**
         * Builds an intent to start [LaunchActivity].
         *
         * @param showWhenLocked when `true`, routes through the non-exported
         *   `LaunchOverLockScreen` activity-alias so the dashboard renders over the keyguard.
         *   Intended for trusted in-process callers (e.g. the device controls panel) — external
         *   apps cannot reach the alias and therefore cannot opt into this behavior.
         */
        fun newInstance(context: Context, deepLink: DeepLink? = null, showWhenLocked: Boolean = false): Intent {
            return Intent().apply {
                component = if (showWhenLocked) {
                    ComponentName(context, LOCK_SCREEN_ALIAS_CLASS)
                } else {
                    ComponentName(context, LaunchActivity::class.java)
                }
                if (deepLink != null) {
                    putExtra(DEEP_LINK_KEY, deepLink)
                }
            }
        }
    }

    private val viewModel: LaunchViewModel by viewModels(
        extrasProducer = {
            defaultViewModelCreationExtras.withCreationCallback<LaunchViewModelFactory> {
                it.create(IntentCompat.getParcelableExtra(intent, DEEP_LINK_KEY, DeepLink::class.java))
            }
        },
    )

    private lateinit var rayNeoMirror: RayNeoWebViewMirror
    private lateinit var rayNeoCursorController: RayNeoCursorController
    private lateinit var rayNeoNetworkInputServer: RayNeoNetworkInputServer
    private lateinit var rayNeoBluetoothController: RayNeoBluetoothControllerClient
    private lateinit var rayNeoScrollModeController: RayNeoScrollModeController
    private val rayNeoPointerMapper = RayNeoPointerMapper()
    private val rayNeoCursorPosition = mutableStateOf(RayNeoCursorPosition())
    private val rayNeoCursorVisible = mutableStateOf(true)
    private var rayNeoViewportX = 0f
    private var rayNeoViewportY = 0f
    private var rayNeoViewportWidth = 1
    private var rayNeoViewportHeight = 1
    private var rayNeoAirMouseSelected = false
    private var rayNeoTouchDownTime = 0L
    private var rayNeoBluetoothPermissionRequested = false

    private val requestRayNeoBluetoothPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted && ::rayNeoBluetoothController.isInitialized) {
                rayNeoBluetoothController.start(lifecycleScope)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate so the window flag is set before the platform decides
        // whether to draw over the keyguard. Gated on the non-exported [LOCK_SCREEN_ALIAS_CLASS]
        // so external apps reaching the public LAUNCHER intent-filter cannot force this on.
        if (SdkVersion.isAtLeast(Build.VERSION_CODES.O_MR1) &&
            intent.component?.className == LOCK_SCREEN_ALIAS_CLASS
        ) {
            setShowWhenLocked(true)
        }

        super.onCreate(savedInstanceState)
        val splashScreen = installSplashScreen()

        splashScreen.setKeepOnScreenCondition {
            viewModel.shouldShowSplashScreen()
        }

        enableEdgeToEdgeCompat()

        window.setFormat(PixelFormat.RGBA_8888)
        rayNeoMirror = RayNeoWebViewMirror(this).apply { setSource(window.decorView) }
        rayNeoCursorController = RayNeoCursorController().apply {
            setOnPositionChanged { rayNeoCursorPosition.value = it }
        }
        rayNeoNetworkInputServer = RayNeoNetworkInputServer(this)
        rayNeoBluetoothController = RayNeoBluetoothControllerClient(this, this)
        rayNeoScrollModeController = RayNeoScrollModeController(
            cursorController = rayNeoCursorController,
            onTap = ::dispatchRayNeoCursorClick,
            onScroll = ::dispatchRayNeoScroll,
            onScrollModeChanged = { enabled ->
                rayNeoCursorVisible.value = !enabled
                Timber.i("RayNeo launch ${if (enabled) "scroll" else "cursor"} mode activated")
            },
        )

        setContent {
            HATheme {
                RayNeoLaunchScreen(
                    mirror = rayNeoMirror,
                    cursorPosition = rayNeoCursorPosition.value,
                    cursorVisible = rayNeoCursorVisible.value,
                    onViewportChanged = ::updateRayNeoViewport,
                ) {
                    val navController = rememberNavController()
                    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                    val isFullScreen by viewModel.isFullScreen.collectAsStateWithLifecycle()
                    val isAppLocked by viewModel.isAppLocked.collectAsStateWithLifecycle()
                    val hazeState = rememberHazeState(blurEnabled = isAppLocked)
                    val snackbarHostState = remember { SnackbarHostState() }

                    FullscreenEffect(isFullScreen = isFullScreen)

                    MissingPlayServicesNotice(
                        isMissingRequiredPlayServices = playServicesAvailability.isMissingRequiredPlayServices(),
                        snackbarHostState = snackbarHostState,
                        navController = navController,
                    )

                    HAApp(
                        navController = navController,
                        startDestination = (uiState as? LaunchUiState.Ready)?.startDestination,
                        snackbarHostState = snackbarHostState,
                        onRequestFullscreen = viewModel::onFullscreenRequested,
                        onPipReadinessChanged = viewModel::onPipReadinessChanged,
                        modifier = Modifier.hazeSource(hazeState),
                    )

                    // We don't apply the overlay on top of the dialogs
                    HazeLockOverlay(hazeState)

                    when (uiState) {
                        LaunchUiState.NetworkUnavailable -> NetworkUnavailableDialog(onBackClick = ::finish)
                        LaunchUiState.WearUnsupported -> WearUnsupportedDialog(onBackClick = ::finish)
                        LaunchUiState.Loading, is LaunchUiState.Ready -> {
                            AppLockEffect(
                                isAppLocked = isAppLocked,
                                onAuthSucceeded = viewModel::onAuthenticated,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (WIPFeature.USE_FRONTEND_V2) {
            viewModel.refreshAppLockState()
        }
    }

    override fun onResume() {
        super.onResume()
        rayNeoMirror.start()
        rayNeoCursorController.start()
        rayNeoNetworkInputServer.start(lifecycleScope)
        startRayNeoBluetoothController()
        if (WIPFeature.USE_FRONTEND_V2) {
            SensorWorker.start(this)
            lifecycleScope.launch {
                WebsocketManager.start(this@LaunchActivity)
                checkLocationDisabled()
                checkLocalNetworkPermission()
                changeLog.showChangeLog(this@LaunchActivity, forceShow = false)
            }
        }
    }

    override fun onPause() {
        rayNeoNetworkInputServer.stop()
        rayNeoBluetoothController.stop()
        rayNeoScrollModeController.stop()
        rayNeoMirror.stop()
        rayNeoCursorController.stop()
        super.onPause()
        if (!isFinishing && WIPFeature.USE_FRONTEND_V2) SensorReceiver.updateAllSensors(this)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (WIPFeature.USE_FRONTEND_V2) {
            viewModel.onAppPaused()

            if (!SdkVersion.isAtLeast(Build.VERSION_CODES.O)) return
            if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
            val readiness = viewModel.pipReadiness.value ?: return
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(readiness.aspectRatio)
                .apply { readiness.sourceRect?.let(::setSourceRectHint) }
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onStop() {
        super.onStop()
        if (WIPFeature.USE_FRONTEND_V2) {
            viewModel.onAppPaused()
        }
    }

    override fun onDestroy() {
        rayNeoMirror.release()
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val isRayNeoNavigationKey = event.keyCode == KeyEvent.KEYCODE_DPAD_UP ||
            event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
            event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
            event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
            event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER
        if (!isRayNeoNavigationKey) return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN) {
            val handledAsScroll = when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> rayNeoScrollModeController.handleDirectionalScroll(
                    dx = 0f,
                    dy = -RAYNEO_KEY_SCROLL_PX,
                )
                KeyEvent.KEYCODE_DPAD_DOWN -> rayNeoScrollModeController.handleDirectionalScroll(
                    dx = 0f,
                    dy = RAYNEO_KEY_SCROLL_PX,
                )
                KeyEvent.KEYCODE_DPAD_LEFT -> rayNeoScrollModeController.handleDirectionalScroll(
                    dx = -RAYNEO_KEY_SCROLL_PX,
                    dy = 0f,
                )
                KeyEvent.KEYCODE_DPAD_RIGHT -> rayNeoScrollModeController.handleDirectionalScroll(
                    dx = RAYNEO_KEY_SCROLL_PX,
                    dy = 0f,
                )
                else -> false
            }
            if (handledAsScroll) return true
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> rayNeoCursorController.moveBy(dx = 0f, dy = -RAYNEO_CURSOR_STEP_PX)
                KeyEvent.KEYCODE_DPAD_DOWN -> rayNeoCursorController.moveBy(dx = 0f, dy = RAYNEO_CURSOR_STEP_PX)
                KeyEvent.KEYCODE_DPAD_LEFT -> rayNeoCursorController.moveBy(dx = -RAYNEO_CURSOR_STEP_PX, dy = 0f)
                KeyEvent.KEYCODE_DPAD_RIGHT -> rayNeoCursorController.moveBy(dx = RAYNEO_CURSOR_STEP_PX, dy = 0f)
                KeyEvent.KEYCODE_DPAD_CENTER -> if (event.repeatCount == 0) dispatchRayNeoCursorClick()
            }
        }
        return true
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (rayNeoScrollModeController.handleArmTouch(event, rayNeoPointerMapper)) return true
        if (rayNeoScrollModeController.handleMouseOrMudra(event, rayNeoPointerMapper)) return true
        val mapped = rayNeoPointerMapper.map(event) ?: return super.dispatchTouchEvent(event)
        return try {
            super.dispatchTouchEvent(mapped)
        } finally {
            mapped.recycle()
        }
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (rayNeoScrollModeController.handleMouseOrMudra(event, rayNeoPointerMapper)) return true
        val mapped = rayNeoPointerMapper.map(event) ?: return super.dispatchGenericMotionEvent(event)
        return try {
            super.dispatchGenericMotionEvent(mapped)
        } finally {
            mapped.recycle()
        }
    }

    override fun onControllerConnected(name: String, address: String) {
        Timber.d("RayNeo controller connected on launch flow")
        rayNeoBluetoothController.sendNetworkEndpoint(
            scope = lifecycleScope,
            port = GLASSES_INPUT_PORT,
            addresses = rayNeoNetworkInputServer.localIpv4Addresses(),
        )
    }

    override fun onControllerDisconnected() {
        Timber.d("RayNeo controller disconnected from launch flow")
    }

    override fun onControllerModeChanged(mode: RayNeoControllerMode) {
        rayNeoCursorController.setMode(mode)
    }

    override fun onControllerKey(key: String) {
        when (key) {
            "backspace", "Backspace" -> dispatchNativeKey(KeyEvent.KEYCODE_DEL)
            "enter", "Enter" -> dispatchNativeKey(KeyEvent.KEYCODE_ENTER)
            "hideKeyboard" -> hideNativeKeyboard()
            "ArrowLeft" -> dispatchNativeKey(KeyEvent.KEYCODE_DPAD_LEFT)
            "ArrowRight" -> dispatchNativeKey(KeyEvent.KEYCODE_DPAD_RIGHT)
            "ArrowUp" -> dispatchNativeKey(KeyEvent.KEYCODE_DPAD_UP)
            "ArrowDown" -> dispatchNativeKey(KeyEvent.KEYCODE_DPAD_DOWN)
            else -> if (key.isNotEmpty()) commitNativeText(key)
        }
    }

    override fun onControllerAirMouseRay(x: Float, y: Float, select: Boolean) {
        rayNeoCursorController.onAirMouseRay(x = x, y = y)
        if (select && !rayNeoAirMouseSelected) dispatchRayNeoCursorClick()
        rayNeoAirMouseSelected = select
    }

    override fun onControllerTrackpadGesture(action: RayNeoTrackpadAction, dx: Float, dy: Float, pointerCount: Int) {
        if (rayNeoScrollModeController.handleTrackpad(action = action, dx = dx, dy = dy)) return
        rayNeoCursorController.onTrackpadGesture(action = action, dx = dx, dy = dy, pointerCount = pointerCount)
    }

    override fun onControllerScroll(dx: Float, dy: Float) {
        rayNeoScrollModeController.handleScroll(dx = dx, dy = dy)
    }

    override fun onControllerTap() {
        rayNeoScrollModeController.handleTap()
    }

    override fun onControllerTouch(action: RayNeoTouchAction, x: Float, y: Float) {
        val viewportX = x * rayNeoViewportWidth
        val viewportY = y * rayNeoViewportHeight
        if (rayNeoScrollModeController.handleDirectTouch(action = action, x = viewportX, y = viewportY)) return
        val motionAction = when (action) {
            RayNeoTouchAction.DOWN -> MotionEvent.ACTION_DOWN
            RayNeoTouchAction.MOVE -> MotionEvent.ACTION_MOVE
            RayNeoTouchAction.UP -> MotionEvent.ACTION_UP
            RayNeoTouchAction.CANCEL -> MotionEvent.ACTION_CANCEL
        }
        val eventTime = SystemClock.uptimeMillis()
        if (motionAction == MotionEvent.ACTION_DOWN) rayNeoTouchDownTime = eventTime
        dispatchRayNeoTouch(
            action = motionAction,
            x = viewportX,
            y = viewportY,
            downTime = rayNeoTouchDownTime.takeIf { it > 0L } ?: eventTime,
        )
        if (motionAction == MotionEvent.ACTION_UP || motionAction == MotionEvent.ACTION_CANCEL) {
            rayNeoTouchDownTime = 0L
        }
    }

    private fun updateRayNeoViewport(width: Int, height: Int, x: Float, y: Float) {
        rayNeoViewportX = x
        rayNeoViewportY = y
        rayNeoViewportWidth = width
        rayNeoViewportHeight = height
        rayNeoCursorController.setBounds(width = width, height = height)
        rayNeoPointerMapper.setViewport(x = x, y = y, width = width, height = height)
    }

    private fun startRayNeoBluetoothController() {
        if (rayNeoBluetoothController.start(lifecycleScope)) return
        if (
            SdkVersion.isAtLeast(Build.VERSION_CODES.S) &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED &&
            !rayNeoBluetoothPermissionRequested
        ) {
            rayNeoBluetoothPermissionRequested = true
            requestRayNeoBluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    private fun dispatchRayNeoCursorClick() {
        val position = rayNeoCursorController.position()
        val downTime = SystemClock.uptimeMillis()
        dispatchRayNeoTouch(MotionEvent.ACTION_DOWN, position.x, position.y, downTime)
        dispatchRayNeoTouch(MotionEvent.ACTION_UP, position.x, position.y, downTime)
    }

    private fun dispatchRayNeoTouch(action: Int, x: Float, y: Float, downTime: Long) {
        val event = MotionEvent.obtain(
            downTime,
            SystemClock.uptimeMillis(),
            action,
            rayNeoViewportX + x,
            rayNeoViewportY + y,
            0,
        ).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
        try {
            super.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun dispatchRayNeoScroll(dx: Float, dy: Float) {
        val position = rayNeoCursorController.position()
        val properties = MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_MOUSE
        }
        val coordinates = MotionEvent.PointerCoords().apply {
            x = rayNeoViewportX + position.x
            y = rayNeoViewportY + position.y
            setAxisValue(MotionEvent.AXIS_HSCROLL, -dx / RAYNEO_SCROLL_SCALE)
            setAxisValue(MotionEvent.AXIS_VSCROLL, -dy / RAYNEO_SCROLL_SCALE)
        }
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(
            now,
            now,
            MotionEvent.ACTION_SCROLL,
            1,
            arrayOf(properties),
            arrayOf(coordinates),
            0,
            0,
            1f,
            1f,
            0,
            0,
            InputDevice.SOURCE_MOUSE,
            0,
        )
        try {
            super.dispatchGenericMotionEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun dispatchNativeKey(keyCode: Int) {
        val now = SystemClock.uptimeMillis()
        super.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
        super.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
    }

    private fun commitNativeText(text: String) {
        val keyEvents = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD).getEvents(text.toCharArray())
        if (keyEvents != null) {
            keyEvents.forEach { super.dispatchKeyEvent(it) }
            return
        }
        currentFocus?.onCreateInputConnection(EditorInfo())?.commitText(text, 1)
    }

    private fun hideNativeKeyboard() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(window.decorView.windowToken, 0)
    }
}

@Composable
private fun RayNeoLaunchScreen(
    mirror: RayNeoWebViewMirror,
    cursorPosition: RayNeoCursorPosition,
    cursorVisible: Boolean,
    onViewportChanged: (width: Int, height: Int, x: Float, y: Float) -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .onGloballyPositioned { coordinates ->
                    val position = coordinates.positionInWindow()
                    mirror.setCaptureBounds(
                        left = position.x.toInt(),
                        top = position.y.toInt(),
                        right = position.x.toInt() + coordinates.size.width,
                        bottom = position.y.toInt() + coordinates.size.height,
                    )
                    onViewportChanged(
                        coordinates.size.width,
                        coordinates.size.height,
                        position.x,
                        position.y,
                    )
                },
        ) {
            content()
            if (cursorVisible) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = Color.Black,
                        radius = 8.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(cursorPosition.x, cursorPosition.y),
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 5.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(cursorPosition.x, cursorPosition.y),
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Color.Black),
        ) {
            AndroidView(
                factory = { mirror },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private const val RAYNEO_CURSOR_STEP_PX = 28f
private const val RAYNEO_KEY_SCROLL_PX = 96f
private const val RAYNEO_SCROLL_SCALE = 30f

/**
 * Triggers biometric authentication when the app is locked.
 *
 * Launches the system biometric prompt when [isAppLocked] becomes `true`.
 * On success, calls [onAuthSucceeded] to unlock. On user cancel, closes the app.
 */
@Composable
private fun AppLockEffect(isAppLocked: Boolean, onAuthSucceeded: () -> Unit) {
    val activity = LocalActivity.current as? FragmentActivity ?: return
    val biometricTitle = stringResource(commonR.string.biometric_title)
    val authenticator = remember {
        Authenticator(activity) { result ->
            when (result) {
                AuthenticationResult.ERROR, AuthenticationResult.CANCELED -> activity.finishAffinity()
                AuthenticationResult.SUCCESS -> onAuthSucceeded()
            }
        }
    }

    LaunchedEffect(isAppLocked) {
        if (isAppLocked) {
            authenticator.authenticate(biometricTitle)
        }
    }
}

@Composable
private fun FullscreenEffect(isFullScreen: Boolean) {
    val view = LocalView.current
    val window = LocalActivity.current?.window ?: return
    LaunchedEffect(isFullScreen) {
        val controller = WindowInsetsControllerCompat(window, view)
        if (isFullScreen) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(systemBars())
        } else {
            controller.show(systemBars())
        }
    }
}

@Composable
private fun MissingPlayServicesNotice(
    isMissingRequiredPlayServices: Boolean,
    snackbarHostState: SnackbarHostState,
    navController: NavController,
) {
    if (isMissingRequiredPlayServices) {
        val message = stringResource(commonR.string.play_services_unavailable_full_flavor)
        val learnMore = stringResource(commonR.string.learn_more)
        LaunchedEffect(message) {
            if (snackbarHostState.showSnackbar(
                    message = message,
                    duration = SnackbarDuration.Long,
                    actionLabel = learnMore,
                ) == ActionPerformed
            ) {
                navController.navigateToUri(
                    uri = PLAY_SERVICES_FLAVOR_DOC_URL,
                    onShowSnackbar = { snackbarMessage, action ->
                        snackbarHostState.showSnackbar(snackbarMessage, action) == ActionPerformed
                    },
                )
            }
        }
    }
}
