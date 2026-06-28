package io.homeassistant.companion.android.webview.rayneo

import android.webkit.WebView
import org.json.JSONObject

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
          const beforeInput = new InputEvent('beforeinput', {
            bubbles: true, cancelable: true, composed: true, inputType: 'insertText', data: text
          });
          if (!element.dispatchEvent(beforeInput)) return false;
          if (element.isContentEditable) {
            document.execCommand('insertText', false, text);
          } else if ('value' in element) {
            const start = element.selectionStart ?? element.value.length;
            const end = element.selectionEnd ?? start;
            const nextValue = element.value.slice(0, start) + text + element.value.slice(end);
            const prototype = element instanceof HTMLTextAreaElement
              ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
            Object.getOwnPropertyDescriptor(prototype, 'value')?.set?.call(element, nextValue);
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
          if (!element.dispatchEvent(beforeInput)) return false;
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
            const prototype = element instanceof HTMLTextAreaElement
              ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
            Object.getOwnPropertyDescriptor(prototype, 'value')?.set?.call(element, nextValue);
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

internal fun WebView.scrollFromRayNeo(dx: Float, dy: Float) {
    evaluateJavascript("window.scrollBy(${dx.toDouble()}, ${dy.toDouble()});", null)
}

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
