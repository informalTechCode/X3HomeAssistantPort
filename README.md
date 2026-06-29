# Home Assistant Companion for Android — Rayneo X3 Pro Port

[![Build Status](https://github.com/home-assistant/android/actions/workflows/onPush.yml/badge.svg)](https://github.com/home-assistant/android/actions/workflows/onPush.yml)  
[![Stars](https://img.shields.io/github/stars/home-assistant/android?style=social)](https://github.com/home-assistant/android/stargazers)

This repository is a specialized port of the official **Home Assistant Companion for Android** application, customized specifically to run on the **Rayneo X3 Pro** AR glasses. It integrates stereoscopic dual-eye rendering and advanced wearable input systems.

<a href="https://www.buymeacoffee.com/informaltech" target="_blank"><img src="https://cdn.buymeacoffee.com/buttons/v2/default-yellow.png" alt="Buy Me A Coffee" style="height: 60px !important;width: 217px !important;" ></a>

---

## Features & Customizations

- **Stereoscopic Dual-Eye Rendering**: Automatically mirrors the Home Assistant frontend WebView into a stereoscopic viewport suitable for AR glasses comfort.
- **Custom Wearable Control Server**: Integrates background input listeners for smooth interaction.
- **TapLink X3 Companion App Compatibility**: Seamlessly pairs with the [TapLink X3 Companion App](https://github.com/informalTechCode/TAPLINKX3) running on a mobile phone to act as a wireless controller.
- **Low-Latency Input Network**: Uses a dedicated UDP connection to stream high-frequency cursor coordinates, scroll inputs, and air mouse gestures.

---

## Gesture & Control Reference

You can interact with the interface using gestures on the temple arm of the glasses or via the companion controller.

| Gesture / Input | Action | Description |
| --- | --- | --- |
| **Single Tap** | Click / Focus | Primary interaction to select items, toggle lights, or focus inputs. |
| **Double Tap** | Go Back | Standard navigation action to go back to the previous screen. |
| **Triple Tap** | Toggle Scroll Mode | Toggles Scroll Mode on the temple. When active, swipe gestures scroll the page instead of moving the cursor. |

---

## TapLink X3 Companion App Integration

The companion controller app serves as the input bridge for the Rayneo X3 Pro glasses.
* **Repository**: [TAPLINKX3](https://github.com/informalTechCode/TAPLINKX3)
* **Features Provided**:
  * **Precision Trackpad**: Move the cursor smoothly across the Home Assistant UI.
  * **Air Mouse**: Aim and point using the phone's gyroscope.
  * **Custom Radial Keyboard**: Simplifies entering text (e.g. passwords, URLs, or entity names).
  * **UDP Lane**: High-performance cursor updates on UDP ports `37692` and `37693`.

---

## Technical Architecture (RayNeo Classes)

The custom logic for RayNeo X3 Pro is housed under `app/src/main/kotlin/io/homeassistant/companion/android/webview/rayneo/`:
- [RayNeoBluetoothControllerClient.kt](app/src/main/kotlin/io/homeassistant/companion/android/webview/rayneo/RayNeoBluetoothControllerClient.kt): Manages pairing and communication with the TapLink companion app over Bluetooth.
- [RayNeoNetworkInputServer.kt](app/src/main/kotlin/io/homeassistant/companion/android/webview/rayneo/RayNeoNetworkInputServer.kt): Establishes a UDP server to process high-frequency mouse movements and scroll inputs.
- [RayNeoScrollModeController.kt](app/src/main/kotlin/io/homeassistant/companion/android/webview/rayneo/RayNeoScrollModeController.kt): Handles the gesture detection and state machine (e.g., tap, double-tap back, triple-tap scroll mode toggle).
- [RayNeoWebViewMirror.kt](app/src/main/kotlin/io/homeassistant/companion/android/webview/rayneo/RayNeoWebViewMirror.kt): Splices the WebView into left and right views for AR stereoscopic rendering.

---

## Build & Development

Follow the standard Gradle build commands. For convenience:

```bash
# Clean and build the debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew test
```

For general details on the core Home Assistant Companion app (architecture, sensors, widgets, Wear OS integration, etc.), please refer to the [official developer guide](https://developers.home-assistant.io/docs/android/) or check out [companion.home-assistant.io](https://companion.home-assistant.io/).

---

## License

This project is open-source and licensed under the Apache License 2.0. See [LICENSE.md](LICENSE.md) for more details.
