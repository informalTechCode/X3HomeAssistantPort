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

    @Test
    fun `Given arm movement in scroll mode when mapped then direction matches TapLink`() {
        assertEquals(RayNeoScrollDelta(dx = 0f, dy = -5f), mapRayNeoArmScroll(dx = 3f, dy = 8f))
        assertEquals(RayNeoScrollDelta(dx = 0f, dy = 5f), mapRayNeoArmScroll(dx = 8f, dy = 3f))
        val forwardSwipe = mapRayNeoArmScroll(dx = 0f, dy = -30f)
        val axes = mapRayNeoControllerScrollToAxes(dx = forwardSwipe.dx, dy = forwardSwipe.dy)
        assertEquals(0f, axes.horizontal, 0f)
        assertEquals(-1f, axes.vertical)
    }

    @Test
    fun `Given companion trackpad movement in scroll mode when mapped then natural direction matches TapLink`() {
        assertEquals(RayNeoScrollDelta(dx = -3f, dy = 8f), mapRayNeoTrackpadScroll(dx = 3f, dy = -8f))
    }

    @Test
    fun `Given companion scrollbar movement when mapped then wheel axes match TapLink`() {
        assertEquals(
            RayNeoScrollAxes(horizontal = -1f, vertical = 2f),
            mapRayNeoControllerScrollToAxes(dx = 30f, dy = -60f),
        )
    }

    @Test
    fun `Given TapLink keyboard when created then it has the TapLink rows without microphone`() {
        val controller = RayNeoKeyboardController(
            object : RayNeoKeyboardListener {
                override fun onKeyboardAction(action: RayNeoKeyboardAction) = Unit
            },
        )

        assertEquals(
            listOf("Clear", "123", "Space", "@", "Enter"),
            controller.state.value.rows[3].map(RayNeoKeyboardKey::label),
        )

        controller.press(RayNeoKeyboardAction.ToggleSymbols)
        assertEquals(
            listOf("Clear", "ABC", "Space", "◀", "Enter"),
            controller.state.value.rows[3].map(RayNeoKeyboardKey::label),
        )
    }

    @Test
    fun `Given companion keys when parsed then every non AI action is supported`() {
        assertEquals(RayNeoControllerKeyCommand.Backspace, parseRayNeoControllerKey("backspace"))
        assertEquals(RayNeoControllerKeyCommand.Enter, parseRayNeoControllerKey("enter"))
        assertEquals(RayNeoControllerKeyCommand.HideKeyboard, parseRayNeoControllerKey("hideKeyboard"))
        assertEquals(RayNeoControllerKeyCommand.ZoomIn, parseRayNeoControllerKey("zoomIn"))
        assertEquals(RayNeoControllerKeyCommand.ZoomOut, parseRayNeoControllerKey("zoomOut"))
        assertEquals(RayNeoControllerKeyCommand.ToggleMask, parseRayNeoControllerKey("toggleMask"))
        assertEquals(RayNeoControllerKeyCommand.ArrowUp, parseRayNeoControllerKey("ArrowUp"))
        assertEquals(RayNeoControllerKeyCommand.Text("a"), parseRayNeoControllerKey("a"))
    }
}
