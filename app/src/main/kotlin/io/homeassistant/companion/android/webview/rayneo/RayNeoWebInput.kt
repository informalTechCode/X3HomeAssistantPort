package io.homeassistant.companion.android.webview.rayneo

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.webkit.WebView
import org.json.JSONObject
import timber.log.Timber

internal fun WebView.disableRayNeoSystemKeyboard() {
    try {
        WebView::class.java.getMethod("setShowSoftInputOnFocus", Boolean::class.javaPrimitiveType)
            .invoke(this, false)
    } catch (exception: ReflectiveOperationException) {
        Timber.d(exception, "WebView does not expose setShowSoftInputOnFocus")
    } catch (exception: SecurityException) {
        Timber.w(exception, "Unable to disable the WebView system keyboard")
    }
}

internal fun WebView.insertRayNeoText(text: String) {
    evaluateJavascript(
        """
        (() => {
          const deepActiveElement = () => {
            let element = document.activeElement;
            while (element?.shadowRoot?.activeElement) element = element.shadowRoot.activeElement;
            return element;
          };
          const element = deepActiveElement();
          if (!element) return false;
          const text = ${JSONObject.quote(text)};
          const originalValue = 'value' in element ? String(element.value ?? '') : '';
          const beforeInput = new InputEvent('beforeinput', {
            bubbles: true, cancelable: true, composed: true, inputType: 'insertText', data: text
          });
          element.dispatchEvent(beforeInput);
          if (element.isContentEditable) {
            document.execCommand('insertText', false, text);
          } else if ('value' in element) {
            const start = element.selectionStart ?? originalValue.length;
            const end = element.selectionEnd ?? start;
            const nextValue = originalValue.slice(0, start) + text + originalValue.slice(end);
            let prototype = element;
            let valueSetter;
            while (prototype && !valueSetter) {
              valueSetter = Object.getOwnPropertyDescriptor(prototype, 'value')?.set;
              prototype = Object.getPrototypeOf(prototype);
            }
            if (valueSetter) valueSetter.call(element, nextValue); else element.value = nextValue;
            element.setSelectionRange?.(start + text.length, start + text.length);
          } else {
            return false;
          }
          element.dispatchEvent(new InputEvent('input', {
            bubbles: true, composed: true, inputType: 'insertText', data: text
          }));
          return true;
        })();
        """.trimIndent(),
        null,
    )
}

internal fun WebView.deleteRayNeoText(clearAll: Boolean = false) {
    evaluateJavascript(
        """
        (() => {
          const deepActiveElement = () => {
            let element = document.activeElement;
            while (element?.shadowRoot?.activeElement) element = element.shadowRoot.activeElement;
            return element;
          };
          const element = deepActiveElement();
          if (!element) return false;
          const inputType = ${if (clearAll) "'deleteContent'" else "'deleteContentBackward'"};
          const beforeInput = new InputEvent('beforeinput', {
            bubbles: true, cancelable: true, composed: true, inputType
          });
          element.dispatchEvent(beforeInput);
          if (element.isContentEditable) {
            if ($clearAll) document.execCommand('selectAll', false);
            document.execCommand('delete', false);
          } else if ('value' in element) {
            let start = element.selectionStart ?? element.value.length;
            let end = element.selectionEnd ?? start;
            if ($clearAll) {
              start = 0;
              end = element.value.length;
            } else if (start === end && start > 0) {
              start -= 1;
            }
            const nextValue = element.value.slice(0, start) + element.value.slice(end);
            let prototype = element;
            let valueSetter;
            while (prototype && !valueSetter) {
              valueSetter = Object.getOwnPropertyDescriptor(prototype, 'value')?.set;
              prototype = Object.getPrototypeOf(prototype);
            }
            if (valueSetter) valueSetter.call(element, nextValue); else element.value = nextValue;
            element.setSelectionRange?.(start, start);
          } else {
            return false;
          }
          element.dispatchEvent(new InputEvent('input', {
            bubbles: true, composed: true, inputType, data: null
          }));
          return true;
        })();
        """.trimIndent(),
        null,
    )
}

internal fun WebView.dispatchRayNeoEnter() {
    evaluateJavascript(
        """
        (() => {
          let element = document.activeElement;
          while (element?.shadowRoot?.activeElement) element = element.shadowRoot.activeElement;
          if (!element) return false;
          const options = { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true,
            cancelable: true, composed: true };
          element.dispatchEvent(new KeyboardEvent('keydown', options));
          if (element instanceof HTMLTextAreaElement || element.isContentEditable) {
            document.execCommand('insertLineBreak', false);
            element.dispatchEvent(new InputEvent('input', {
              bubbles: true, composed: true, inputType: 'insertLineBreak', data: null
            }));
          } else if (element.form?.requestSubmit) {
            element.form.requestSubmit();
          }
          element.dispatchEvent(new KeyboardEvent('keyup', options));
          return true;
        })();
        """.trimIndent(),
        null,
    )
}

internal fun WebView.moveRayNeoCaret(delta: Int) {
    evaluateJavascript(
        """
        (() => {
          let element = document.activeElement;
          while (element?.shadowRoot?.activeElement) element = element.shadowRoot.activeElement;
          if (!element || !element.setSelectionRange) return false;
          const position = Math.max(0, Math.min(element.value.length, (element.selectionStart ?? 0) + $delta));
          element.setSelectionRange(position, position);
          return true;
        })();
        """.trimIndent(),
        null,
    )
}

internal fun WebView.scrollFromRayNeo(dx: Float, dy: Float, x: Float, y: Float) {
    val axes = mapRayNeoControllerScrollToAxes(dx = dx, dy = dy)
    val properties = MotionEvent.PointerProperties().apply {
        id = 0
        toolType = MotionEvent.TOOL_TYPE_MOUSE
    }
    val coordinates = MotionEvent.PointerCoords().apply {
        this.x = x.coerceIn(0f, width.toFloat())
        this.y = y.coerceIn(0f, height.toFloat())
        setAxisValue(MotionEvent.AXIS_HSCROLL, axes.horizontal)
        setAxisValue(MotionEvent.AXIS_VSCROLL, axes.vertical)
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
        dispatchGenericMotionEvent(event)
    } finally {
        event.recycle()
    }
}

internal data class RayNeoScrollAxes(val horizontal: Float, val vertical: Float)

/** Matches the mouse-wheel axes used by TapLink's controller receiver. */
internal fun mapRayNeoControllerScrollToAxes(dx: Float, dy: Float): RayNeoScrollAxes =
    RayNeoScrollAxes(horizontal = -dx / CONTROLLER_SCROLL_SCALE, vertical = -dy / CONTROLLER_SCROLL_SCALE)

private const val CONTROLLER_SCROLL_SCALE = 30f

internal fun WebView.hasRayNeoEditableFocus(callback: (Boolean) -> Unit) {
    evaluateJavascript(
        """
        (() => {
          let element = document.activeElement;
          while (element?.shadowRoot?.activeElement) element = element.shadowRoot.activeElement;
          return !!element && (element.isContentEditable || element instanceof HTMLInputElement ||
            element instanceof HTMLTextAreaElement);
        })();
        """.trimIndent(),
    ) { callback(it == "true") }
}

internal fun WebView.installRayNeoKeyboardFocusListener() {
    evaluateJavascript(
        """
        (() => {
          if (window.__rayNeoKeyboardFocusInstalled) return true;
          window.__rayNeoKeyboardFocusInstalled = true;
          const isEditable = element => {
            if (!element) return false;
            if (element.isContentEditable || element instanceof HTMLTextAreaElement) return true;
            if (!(element instanceof HTMLInputElement)) return false;
            return !['button', 'checkbox', 'color', 'file', 'hidden', 'image', 'radio', 'range', 'reset',
              'submit'].includes((element.type || 'text').toLowerCase());
          };
          document.addEventListener('focusin', event => {
            const path = event.composedPath?.() ?? [event.target];
            if (path.some(isEditable)) window.$RAYNEO_KEYBOARD_BRIDGE_NAME?.onInputFocus();
          }, true);
          return true;
        })();
        """.trimIndent(),
        null,
    )
}

internal fun WebView.blurRayNeoEditableFocus() {
    evaluateJavascript(
        """
        (() => {
          let element = document.activeElement;
          while (element?.shadowRoot?.activeElement) element = element.shadowRoot.activeElement;
          element?.blur?.();
        })();
        """.trimIndent(),
        null,
    )
}
