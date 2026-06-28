package io.homeassistant.companion.android.webview.rayneo

import android.view.Choreographer
import kotlin.math.sqrt

internal data class RayNeoCursorPosition(val x: Float = 0f, val y: Float = 0f)

/** Keeps high-frequency companion controller input aligned to display frames. */
internal class RayNeoCursorController {
    private var width = 1f
    private var height = 1f
    private var mode = RayNeoControllerMode.TRACKPAD
    private var targetX = 0.5f
    private var targetY = 0.5f
    private var currentX = targetX
    private var currentY = targetY
    private var lastX = Float.NaN
    private var lastY = Float.NaN
    private var running = false
    private var listener: ((RayNeoCursorPosition) -> Unit)? = null

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            updateFrame()
            emitIfChanged()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun setOnPositionChanged(listener: (RayNeoCursorPosition) -> Unit) {
        this.listener = listener
        emit(force = true)
    }

    fun setBounds(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        if (this.width == width.toFloat() && this.height == height.toFloat()) return
        val oldWidth = this.width
        val oldHeight = this.height
        this.width = width.toFloat()
        this.height = height.toFloat()
        targetX = if (oldWidth > 1f) targetX / oldWidth * this.width else this.width / 2f
        targetY = if (oldHeight > 1f) targetY / oldHeight * this.height else this.height / 2f
        currentX = targetX
        currentY = targetY
        emit(force = true)
    }

    fun start() {
        if (running) return
        running = true
        currentX = targetX
        currentY = targetY
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    fun stop() {
        running = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    fun moveBy(dx: Float, dy: Float) {
        targetX = (targetX + dx).coerceIn(0f, width)
        targetY = (targetY + dy).coerceIn(0f, height)
        if (!running) snapToTarget()
    }

    fun moveTo(x: Float, y: Float) {
        targetX = x.coerceIn(0f, width)
        targetY = y.coerceIn(0f, height)
        if (!running) snapToTarget()
    }

    fun onTrackpadGesture(action: RayNeoTrackpadAction, dx: Float, dy: Float, pointerCount: Int) {
        if (mode != RayNeoControllerMode.TRACKPAD) return
        when (action) {
            RayNeoTrackpadAction.MOVE -> if (pointerCount < 2) moveBy(dx, dy)
            RayNeoTrackpadAction.UP, RayNeoTrackpadAction.CANCEL -> snapToTarget()
            RayNeoTrackpadAction.DOWN, RayNeoTrackpadAction.POINTER -> Unit
        }
    }

    fun onAirMouseRay(x: Float, y: Float) {
        if (mode != RayNeoControllerMode.AIR_MOUSE) return
        moveTo(x = x.coerceIn(0f, 1f) * width, y = y.coerceIn(0f, 1f) * height)
    }

    fun setMode(mode: RayNeoControllerMode) {
        this.mode = mode
    }

    fun position(): RayNeoCursorPosition = RayNeoCursorPosition(currentX, currentY)

    private fun updateFrame() {
        val dx = targetX - currentX
        val dy = targetY - currentY
        val distance = sqrt(dx * dx + dy * dy)
        if (distance < SNAP_DISTANCE_PX) {
            currentX = targetX
            currentY = targetY
            return
        }
        val alpha = when {
            distance >= FAST_DISTANCE_PX -> 0.92f
            distance >= MEDIUM_DISTANCE_PX -> 0.78f
            distance >= SMALL_DISTANCE_PX -> 0.58f
            else -> 0.38f
        }
        currentX += dx * alpha
        currentY += dy * alpha
    }

    private fun snapToTarget() {
        currentX = targetX
        currentY = targetY
        emit(force = true)
    }

    private fun emitIfChanged() {
        if (currentX != lastX || currentY != lastY) emit(force = false)
    }

    private fun emit(force: Boolean) {
        if (!force && currentX == lastX && currentY == lastY) return
        lastX = currentX
        lastY = currentY
        listener?.invoke(RayNeoCursorPosition(currentX, currentY))
    }
}

private const val SNAP_DISTANCE_PX = 0.3f
private const val SMALL_DISTANCE_PX = 4f
private const val MEDIUM_DISTANCE_PX = 18f
private const val FAST_DISTANCE_PX = 64f
