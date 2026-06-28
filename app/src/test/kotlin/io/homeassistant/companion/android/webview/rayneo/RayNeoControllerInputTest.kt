package io.homeassistant.companion.android.webview.rayneo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RayNeoControllerInputTest {
    @Test
    fun `Given controller mode values when parsed then known values are mapped`() {
        assertEquals(RayNeoControllerMode.AIR_MOUSE, parseMode("airMouse"))
        assertEquals(RayNeoControllerMode.TRACKPAD, parseMode("trackpad"))
        assertEquals(RayNeoControllerMode.META, parseMode("meta"))
        assertNull(parseMode("unknown"))
    }

    @Test
    fun `Given trackpad actions when parsed then known values are mapped`() {
        assertEquals(RayNeoTrackpadAction.DOWN, parseTrackpadAction("down"))
        assertEquals(RayNeoTrackpadAction.MOVE, parseTrackpadAction("move"))
        assertEquals(RayNeoTrackpadAction.POINTER, parseTrackpadAction("pointer"))
        assertEquals(RayNeoTrackpadAction.UP, parseTrackpadAction("up"))
        assertEquals(RayNeoTrackpadAction.CANCEL, parseTrackpadAction("cancel"))
        assertNull(parseTrackpadAction("unknown"))
    }

    @Test
    fun `Given touch actions when parsed then known values are mapped`() {
        assertEquals(RayNeoTouchAction.DOWN, parseTouchAction("down"))
        assertEquals(RayNeoTouchAction.MOVE, parseTouchAction("move"))
        assertEquals(RayNeoTouchAction.UP, parseTouchAction("up"))
        assertEquals(RayNeoTouchAction.CANCEL, parseTouchAction("cancel"))
        assertNull(parseTouchAction("unknown"))
    }
}
