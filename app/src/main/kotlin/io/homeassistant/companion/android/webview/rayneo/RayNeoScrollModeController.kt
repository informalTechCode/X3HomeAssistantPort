package io.homeassistant.companion.android.webview.rayneo

import android.os.SystemClock
import android.view.MotionEvent
import kotlin.math.abs

/** Implements TapLink's triple-tap cursor/scroll mode and RayNeo temple-arm gestures. */
internal class RayNeoScrollModeController(
    private val cursorController: RayNeoCursorController,
    private val onTap: () -> Unit,
    private val onScroll: (dx: Float, dy: Float) -> Unit,
    private val onScrollModeChanged: (Boolean) -> Unit,
    private val onBack: () -> Unit = {},
) {
    private var scrollMode = false
    private var tapCount = 0
    private var firstTapTime = 0L
    private var lastTapTime = 0L
    private var armLastX = 0f
    private var armLastY = 0f
    private var armMoved = false
    private var tripleTapInProgress = false
    private var directTouchStartX = 0f
    private var directTouchStartY = 0f
    private var directTouchMoved = false

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var singleTapRunnable: Runnable? = null
    private var doubleTapRunnable: Runnable? = null
    private var pendingDoubleTapAction = false
    private var isProcessingDoubleTap = false

    fun handleTap() {
        processTapSequence(SystemClock.uptimeMillis())
    }

    fun handleTrackpad(action: RayNeoTrackpadAction, dx: Float, dy: Float): Boolean {
        if (!scrollMode) return false
        if (action == RayNeoTrackpadAction.MOVE) {
            val scroll = mapRayNeoTrackpadScroll(dx = dx, dy = dy)
            onScroll(scroll.dx, scroll.dy)
        }
        return true
    }

    fun handleScroll(dx: Float, dy: Float): Boolean {
        if (!scrollMode) return false
        onScroll(dx, dy)
        return true
    }

    fun handleDirectTouch(action: RayNeoTouchAction, x: Float, y: Float): Boolean {
        if (scrollMode) return false
        cursorController.moveTo(x = x, y = y)
        when (action) {
            RayNeoTouchAction.DOWN -> {
                directTouchStartX = x
                directTouchStartY = y
                directTouchMoved = false
            }

            RayNeoTouchAction.MOVE -> {
                if (abs(x - directTouchStartX) >= DIRECT_TOUCH_SLOP_PX ||
                    abs(y - directTouchStartY) >= DIRECT_TOUCH_SLOP_PX
                ) {
                    directTouchMoved = true
                }
            }

            RayNeoTouchAction.UP -> {
                if (!directTouchMoved) handleTap()
                directTouchMoved = false
            }

            RayNeoTouchAction.CANCEL -> directTouchMoved = false
        }
        return true
    }

    fun handleDirectionalScroll(dx: Float, dy: Float): Boolean = handleScroll(dx = dx, dy = dy)

    fun handleArmTouch(event: MotionEvent, pointerMapper: RayNeoPointerMapper): Boolean {
        if (!pointerMapper.isRayNeoArmTouch(event)) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                armLastX = event.x
                armLastY = event.y
                armMoved = false
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - armLastX
                val dy = event.y - armLastY
                if (abs(dx) >= ARM_MOVE_THRESHOLD_PX || abs(dy) >= ARM_MOVE_THRESHOLD_PX) {
                    armMoved = true
                    if (scrollMode) {
                        val scroll = mapRayNeoArmScroll(dx = dx, dy = dy)
                        onScroll(scroll.dx, scroll.dy)
                    } else {
                        cursorController.moveBy(dx = dx, dy = dy)
                    }
                    armLastX = event.x
                    armLastY = event.y
                }
            }

            MotionEvent.ACTION_UP -> {
                if (!armMoved) handleTap()
            }

            MotionEvent.ACTION_CANCEL -> {
                armMoved = false
                tripleTapInProgress = false
            }
        }
        return true
    }

    fun handleMouseOrMudra(event: MotionEvent, pointerMapper: RayNeoPointerMapper): Boolean {
        if (!pointerMapper.isMouseOrMudraPointer(event)) return false
        cursorController.moveTo(x = pointerMapper.cursorX(event), y = pointerMapper.cursorY(event))
        when (event.actionMasked) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_BUTTON_RELEASE -> handleTap()
            MotionEvent.ACTION_SCROLL -> {
                if (scrollMode) {
                    onScroll(
                        -event.getAxisValue(MotionEvent.AXIS_HSCROLL) * POINTER_SCROLL_SCALE,
                        -event.getAxisValue(MotionEvent.AXIS_VSCROLL) * POINTER_SCROLL_SCALE,
                    )
                }
            }
        }
        return true
    }

    fun stop() {
        armMoved = false
        tripleTapInProgress = false
        directTouchMoved = false
        singleTapRunnable?.let { handler.removeCallbacks(it) }
        doubleTapRunnable?.let { handler.removeCallbacks(it) }
        singleTapRunnable = null
        doubleTapRunnable = null
        pendingDoubleTapAction = false
        isProcessingDoubleTap = false
    }

    private fun processTapSequence(time: Long) {
        if (time - lastTapTime > TAP_INTERVAL_MS) {
            tapCount = 1
            firstTapTime = time
            tripleTapInProgress = false
        } else {
            tapCount++
        }
        lastTapTime = time

        if (tapCount == TRIPLE_TAP_COUNT && time - firstTapTime <= TRIPLE_TAP_DURATION_MS) {
            singleTapRunnable?.let { handler.removeCallbacks(it) }
            doubleTapRunnable?.let { handler.removeCallbacks(it) }
            singleTapRunnable = null
            doubleTapRunnable = null
            pendingDoubleTapAction = false
            isProcessingDoubleTap = false
            tripleTapInProgress = true
            tapCount = 0

            scrollMode = !scrollMode
            onScrollModeChanged(scrollMode)
            return
        }

        if (tapCount == 2 && time - firstTapTime <= TRIPLE_TAP_DURATION_MS) {
            singleTapRunnable?.let { handler.removeCallbacks(it) }
            singleTapRunnable = null

            val remainingTripleTapWindow = TRIPLE_TAP_DURATION_MS - (time - firstTapTime)
            val delay = if (remainingTripleTapWindow > 0) remainingTripleTapWindow + 30L else DOUBLE_TAP_CONFIRMATION_DELAY_MS

            if (isProcessingDoubleTap) return
            isProcessingDoubleTap = true
            pendingDoubleTapAction = true

            val runnable = Runnable {
                try {
                    if (tripleTapInProgress) return@Runnable
                    if (pendingDoubleTapAction) {
                        onBack()
                    }
                } finally {
                    pendingDoubleTapAction = false
                    isProcessingDoubleTap = false
                    doubleTapRunnable = null
                }
            }
            doubleTapRunnable = runnable
            handler.postDelayed(runnable, delay)
            return
        }

        if (tapCount == 1) {
            val runnable = Runnable {
                if (!tripleTapInProgress && !isProcessingDoubleTap) {
                    onTap()
                }
                singleTapRunnable = null
            }
            singleTapRunnable = runnable
            handler.postDelayed(runnable, TAP_INTERVAL_MS)
        }
    }
}

internal data class RayNeoScrollDelta(val dx: Float, val dy: Float)

internal fun mapRayNeoTrackpadScroll(dx: Float, dy: Float): RayNeoScrollDelta = RayNeoScrollDelta(dx = -dx, dy = -dy)

/** Matches TapLink's forward-swipe behavior after conversion to the shared controller scroll protocol. */
internal fun mapRayNeoArmScroll(dx: Float, dy: Float): RayNeoScrollDelta = RayNeoScrollDelta(dx = 0f, dy = dx - dy)

private const val TRIPLE_TAP_COUNT = 3
private const val TAP_INTERVAL_MS = 400L
private const val TRIPLE_TAP_DURATION_MS = 800L
private const val DOUBLE_TAP_CONFIRMATION_DELAY_MS = 200L
private const val ARM_MOVE_THRESHOLD_PX = 1f
private const val DIRECT_TOUCH_SLOP_PX = 10f
private const val POINTER_SCROLL_SCALE = 30f
