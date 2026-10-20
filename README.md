# MYRA AI — Android Native Voice Assistant (JARVIS Protocol)

This directory contains the complete **native Android application** architecture for **MYRA AI**, built with **Kotlin** and **Jetpack Compose**. It upgrades the MYRA AI experience to operate directly from the Android Home Screen, Lock Screen, and via the **"Hey MYRA"** on-device wake-word.

---

## 1. Key Android Architecture Components

| Component | Class | Description |
|-----------|-------|-------------|
| **Wake-Word System** | `service.MyraWakeWordManager` | Dual-engine on-device wake word system (Picovoice Porcupine + Android Acoustic Hotword detector). Operates 100% locally on CPU without streaming audio to cloud. |
| **Background Service** | `service.MyraForegroundService` | Android Foreground Service typed as `microphone`. Maintains persistent status notification with "Talk Now" & "Stop" controls. |
| **Lock Screen Overlay** | `VoiceInteractionActivity` | Floating HUD configured with `setShowWhenLocked(true)` and `setTurnScreenOn(true)` for instant interaction without PIN unlock for non-sensitive tasks. |
| **Home Screen Widget** | `widget.MyraWidgetProvider` | Interactive 2x2 home screen widget featuring the animated JARVIS Arc Reactor and direct microphone tap trigger. |
| **Launcher Shortcuts** | `xml/shortcuts.xml` | Android App Shortcuts available on long-pressing the app launcher icon ("Talk to MYRA"). |
| **Speech-to-Text** | `speech.MyraSpeechRecognizer` | Native Android `SpeechRecognizer` with real-time partial transcription, RMS dB audio wave levels, and Hindi (`hi-IN`), Indian English (`en-IN`), and US English (`en-US`) support. |
| **Text-to-Speech** | `speech.MyraTextToSpeech` | Native Android `TextToSpeech` with natural language pitch, rate slider, and interruptible playback. |
| **Device Intent Handler**| `actions.DeviceActionHandler` | Executes real phone actions: opens installed apps (WhatsApp, YouTube, Maps, Camera, Settings, Clock), sets alarms, dials phone (with confirmation), and reads time/battery. |
| **AI Cloud Proxy** | `network.MyraApiClient` | Connects securely to the deployed MYRA AI Express backend (`/api/chat`), keeping Gemini 3.8 Flash API keys off client devices. |

---

## 2. Wake-Word Engine & Limitations

- **On-Device Execution**: Wake-word listening operates completely on-device (`Porcupine` or built-in PCM acoustic detector). No microphone audio is ever uploaded or recorded secretly.
- **Persistent Notification**: In compliance with Android security policies, Android requires a persistent foreground service notification whenever the microphone is actively armed in the background.
- **Privacy Indicator**: Android 12+ displays a system green dot in the top-right status bar indicating the microphone hardware is active.
- **Battery Optimization**: To prevent Android Doze Mode from killing the background service, the user must grant battery optimization exemption via `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (provided as a 1-tap button in the app dashboard).

---

## 3. Lock Screen Interaction & Security Boundaries

MYRA respects user privacy and Android keyguard security:
- **Permitted Safe Actions (No Unlock Required)**:
  - Answering general intelligence queries, facts, world news, and weather.
  - Setting timers and alarms via standard system intents.
  - Taking photos using the secure camera intent (`MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE`).
  - Checking the time and device battery status.
- **Restricted Actions (Device Unlock Enforced)**:
  - Opening messaging apps (WhatsApp, Gmail).
  - Accessing the photo gallery or device files.
  - Changing system settings.
  - When a restricted action is triggered, `DeviceActionHandler` invokes `KeyguardManager.requestDismissKeyguard()`, prompting the user for fingerprint, face, or PIN authentication before proceeding.

---

## 4. How to Build the APK in Android Studio

### Prerequisites
- **Android Studio**: Koala, Ladybug, or newer.
- **JDK**: Java Development Kit (JDK 17) selected in `Settings > Build, Execution, Deployment > Build Tools > Gradle`.
- **Android Device**: Android 8.0 (API 26) or higher.

### Step-by-Step Instructions

1. **Open Project**:
   - In Android Studio, select **Open** and choose this `android` folder.
2. **Sync Gradle**:
   - Android Studio will automatically resolve Gradle dependencies (Kotlin 2.0, Jetpack Compose BOM, OkHttp, AndroidX).
3. **Build Debug APK**:
   - In the terminal:
     ```bash
     ./gradlew assembleDebug
     ```
   - The compiled APK is created at:
     `app/build/outputs/apk/debug/app-debug.apk`
4. **Build Signed Release APK**:
   - In the Android Studio top menu:
     `Build > Generate Signed Bundle / APK...`
   - Select **APK**, choose or create a Keystore, select **Release**, and click **Finish**.
5. **Install on Phone**:
   - Connect your Android phone with **USB Debugging** enabled:
     ```bash
     adb install -r app/build/outputs/apk/debug/app-debug.apk
     ```
   - Or send `app-debug.apk` directly to your phone via Google Drive, WhatsApp, or USB cable, and tap **Install**.
