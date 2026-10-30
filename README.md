FIN – Offline Android Voice Assistant

FIN is a private Android voice assistant designed to process Bengali voice commands locally on the device.

Current Features

- Offline Bengali speech recognition using Sherpa-ONNX.
- Local speech processing without Google, Gemini, or cloud-based speech recognition.
- Foreground microphone service.
- Open supported apps using voice commands.
- Find contacts by name.
- Open the phone dialer for calls.
- Open the SMS composer for messages.

Technology

- Kotlin
- Android SDK
- Sherpa-ONNX
- Bengali Zipformer streaming model
- Android Foreground Service

Build

The GitHub Actions workflow automatically downloads the required Sherpa-ONNX libraries and Bengali model, then builds a Debug APK.

To download the APK:

1. Open the repository's Actions tab.
2. Select a successful workflow run.
3. Find the "FIN-Debug-APK" artifact.
4. Download the artifact.

Current Limitations

- English speech recognition is not yet implemented.
- Wake-word detection is not yet implemented.
- WhatsApp direct messaging is not yet implemented.
- Calls and SMS require user confirmation through Android apps.
- Device-level testing is still required.

Privacy

Speech recognition is designed to run locally using bundled model files. FIN does not intentionally send voice recordings to a cloud speech-recognition service.

Project Status

Early development. Build success does not guarantee that all features work correctly on every Android device.
