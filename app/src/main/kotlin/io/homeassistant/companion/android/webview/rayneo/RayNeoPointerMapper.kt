package io.homeassistant.companion.android.webview.rayneo

import android.view.InputDevice
import android.view.MotionEvent

/** Maps physical right-eye, RayNeo temple, and Mudra pointer events into the interactive left eye. */
internal class RayNeoPointerMapper {
    private var viewportX = 0f
    private var viewportY = 0f
    private var viewportWidth = 0f
    private var viewportHeight = 0f

    fun setViewport(x: Float, y: Float, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        viewportX = x
        viewportY = y
        viewportWidth = width.toFloat()
        viewportHeight = height.toFloat()
    }

    fun map(event: MotionEvent): MotionEvent? {
        if (viewportWidth <= 0f || viewportHeight <= 0f) return null
        val mappedX = mapX(event.x)
        val mappedY = event.y.coerceIn(viewportY, viewportY + viewportHeight)
        if (mappedX == event.x && mappedY == event.y) return null
        return MotionEvent.obtain(event).apply {
            offsetLocation(mappedX - event.x, mappedY - event.y)
        }
    }

    fun cursorX(event: MotionEvent): Float = (mapX(event.x) - viewportX).coerceIn(0f, viewportWidth)

    fun cursorY(event: MotionEvent): Float = (event.y - viewportY).coerceIn(0f, viewportHeight)

    fun isArmOrMousePointer(event: MotionEvent): Boolean {
        return isMouseOrMudraPointer(event) || isRayNeoArmTouch(event)
    }

    fun isMouseOrMudraPointer(event: MotionEvent): Boolean {
        val deviceName = event.device?.name ?: InputDevice.getDevice(event.deviceId)?.name.orEmpty()
        return event.isFromSource(InputDevice.SOURCE_MOUSE) ||
            (event.pointerCount > 0 && event.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE) ||
            deviceName.contains("Mudra", ignoreCase = true)
    }

    fun isRayNeoArmTouch(event: MotionEvent): Boolean {
        val deviceName = event.device?.name ?: InputDevice.getDevice(event.deviceId)?.name.orEmpty()
        return deviceName.contains("cyttsp5", ignoreCase = true) ||
            deviceName.contains("cyttsp6", ignoreCase = true)
    }

    private fun mapX(x: Float): Float = when {
        x < viewportX -> viewportX
        x <= viewportX + viewportWidth -> x
        x <= viewportX + viewportWidth * 2f -> x - viewportWidth
        else -> viewportX + viewportWidth
    }
}
