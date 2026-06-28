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

    fun handleTap() {
        if (!registerTap(SystemClock.uptimeMillis())) onTap()
    }

    fun handleTrackpad(action: RayNeoTrackpadAction, dx: Float, dy: Float): Boolean {
        if (!scrollMode) return false
        if (action == RayNeoTrackpadAction.MOVE) onScroll(dx, dy)
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
                tripleTapInProgress = registerTap(event.eventTime)
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - armLastX
                val dy = event.y - armLastY
                if (abs(dx) >= ARM_MOVE_THRESHOLD_PX || abs(dy) >= ARM_MOVE_THRESHOLD_PX) {
                    armMoved = true
                    if (scrollMode) {
                        onScroll(dx, dy)
                    } else {
                        cursorController.moveBy(dx = dx, dy = dy)
                    }
                    armLastX = event.x
                    armLastY = event.y
                }
            }

            MotionEvent.ACTION_UP -> {
                if (!armMoved && !tripleTapInProgress) onTap()
                tripleTapInProgress = false
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
    }

    private fun registerTap(time: Long): Boolean {
        if (time - lastTapTime > TAP_INTERVAL_MS) {
            tapCount = 1
            firstTapTime = time
        } else {
            tapCount++
        }
        lastTapTime = time
        if (tapCount != TRIPLE_TAP_COUNT || time - firstTapTime > TRIPLE_TAP_DURATION_MS) return false

        tapCount = 0
        scrollMode = !scrollMode
        onScrollModeChanged(scrollMode)
        return true
    }
}

private const val TRIPLE_TAP_COUNT = 3
private const val TAP_INTERVAL_MS = 400L
private const val TRIPLE_TAP_DURATION_MS = 800L
private const val ARM_MOVE_THRESHOLD_PX = 1f
private const val DIRECT_TOUCH_SLOP_PX = 10f
private const val POINTER_SCROLL_SCALE = 30f
