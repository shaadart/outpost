# Build and test Outpost

## Build

Use JDK 17 and Android SDK 35. Open the repository in Android Studio, or run these commands from its root.

Set your SDK location in a local file named `local.properties`:

```properties
sdk.dir=/absolute/path/to/Android/Sdk
```

This file is ignored by Git.

Build the debug app:

```bash
./gradlew assembleDebug
```

The APKs appear in `app/build/outputs/apk/debug/`. For an ARM64 Android phone connected with USB debugging enabled, install with:

```bash
adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

Choose the APK matching your device’s processor. Android Studio can also run the app directly.

For a smaller release build:

```bash
./gradlew assembleRelease
```

Release APKs appear in `app/build/outputs/apk/release/`, with unused code and resources removed. Without signing credentials, the output is unsigned and intended for review.

To sign a release, copy `keystore.properties.example` to `keystore.properties` and supply your own keystore and credentials. Relative keystore paths resolve from the repository root. Keep these files private.

The release workflow requires signing secrets, checks the APK signatures, and uploads artifacts for review. It does not automatically publish a release.

## Tests

Run the logic tests and Android code checks:

```bash
./gradlew testDebugUnitTest lintDebug lintRelease
```

With a test device or emulator connected, run the Android tests:

```bash
./gradlew connectedDebugAndroidTest
```

The [recorded validation](single-device-validation.md) includes:

* 143 logic and protocol tests passed, with no failures or skipped tests.
* Four Android tests passed on a Pixel 9a, covering encrypted history, files, image limits, and identity keys.
* The optimized QA app passed profile, permission refusal, history, chat clearing, group leave, and account erasure checks on Android 9 and Android 15 emulators.
* Debug and release builds passed. Both lint checks reported zero errors; warnings remain documented.

Two independent software peers also test encrypted communication, reconnects, replay rejection, and file fragments arriving out of order. Physical Bluetooth delivery still needs validation. No independent security audit is claimed.

See [the device testing guide](../scripts/README.md) for the separate **Outpost QA** app. It installs beside an existing profile. Its smoke script erases the disposable QA app’s data to test account reset.

## Explore

The app uses Kotlin and Jetpack Compose, with a background service for mesh connectivity.

* [Interface](../app/src/main/java/com/bitchat/android/ui/) contains screens and the state they display.
* [Mesh](../app/src/main/java/com/bitchat/android/mesh/) handles discovery, connections, routing, and message fragments.
* [Groups](../app/src/main/java/com/bitchat/android/groups/) contains invitations and group encryption.
* [Services](../app/src/main/java/com/bitchat/android/services/) contains shared chat state, encrypted history, and message routing.
* [Noise](../app/src/main/java/com/bitchat/android/noise/) and [crypto](../app/src/main/java/com/bitchat/android/crypto/) contain encryption and key management.
* [Protocol](../app/src/main/java/com/bitchat/android/protocol/) defines the messages exchanged between phones.

Read [About Outpost](../ABOUT.md) for the thinking behind it, and [the project review](production-readiness.md) for its history and remaining release work.

