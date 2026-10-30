# FIN Android Starter — corrected project layout

This is a starter scaffold, not a finished or verified voice assistant.

## Important: keep the folder paths

Do not upload all files flat into the repository root. These two files have the same name but belong in different places:
- `/build.gradle.kts`
- `/app/build.gradle.kts`

The GitHub Actions workflow belongs at:
- `/.github/workflows/build-apk.yml`

The Android source files belong under:
- `/app/src/main/AndroidManifest.xml`
- `/app/src/main/java/com/fin/assistant/MainActivity.kt`
- `/app/src/main/java/com/fin/assistant/FinVoiceService.kt`

## Build from an Android phone

The easiest phone-only route is:
1. Upload this ZIP file to the root of a new GitHub repository (do not extract and flatten its contents).
2. In GitHub, create a new file at `.github/workflows/build-from-zip.yml`.
3. Paste the bootstrap workflow supplied separately with this project.
4. Open Actions and run "Build FIN from uploaded ZIP".
5. Download the `FIN-debug-apk` artifact from the completed run and extract it on your phone.

## Known limitations

- This starter does not yet implement a reliable "Hey FIN" wake-word detector.
- It does not yet provide reliable mixed Bangla-English offline speech recognition. The prototype requests Bengali (`bn-BD`) recognition from Android's on-device speech provider, if available.
- It does not directly send WhatsApp messages. The prototype's message handling is incomplete; SMS opens the system composer.
- Calls open the dialer for user confirmation; they are not silently placed.
- The Android OS may show a microphone privacy indicator and a foreground-service notification while listening. These must not be hidden.
- Battery restrictions and Android vendor policies can interrupt background listening.
- The APK has not been built or tested on the user's phone yet. Do not treat it as production-ready.
