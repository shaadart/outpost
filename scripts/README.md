# Repeatable device checks

The `qa` build uses release shrinking and optimization, debug signing, the label **Outpost QA**, and the separate application ID `com.bitchat.droid.qa`. It can be installed beside an existing Outpost profile. It is a test artifact, not a production-signed release.

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest assembleQa
adb -s SERIAL install -r app/build/outputs/apk/qa/app-arm64-v8a-qa.apk
python3 scripts/qa-device-smoke.py --serial SERIAL
```

Use `app-x86_64-qa.apk` on an x86_64 emulator. Add `--adb /path/to/adb` when platform-tools is not on PATH.

The script requires JDK 17, Python 3, and Android SDK platform/build tools. It compiles the shell-only `QaUiDump.java` helper into the ignored build directory and temporarily pushes its dex file to the selected device. This reads fresh accessibility snapshots without Android's idle timeout on blinking text cursors. The helper is not included in any APK and is removed after a successful run.

**The smoke script clears all data in the disposable QA app.** Its package is a fixed constant; it refuses to tap controls in the original Outpost installation. It creates a synthetic local profile/group/message, refuses connection permissions, verifies history after process restart, clears the chat, leaves the group, and invokes Android's full application-data reset. Leave the selected device unlocked and do not switch apps during the run. It does not share invitations or select personal attachments.

The four Android instrumented tests use only dedicated QA files and namespaced preferences. To run them on a specific phone without invoking tests on every connected device:

```bash
adb -s SERIAL install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
adb -s SERIAL install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s SERIAL shell am instrument -w -r com.bitchat.droid.test/androidx.test.runner.AndroidJUnitRunner
```

CI runs the Android tests and optimized QA smoke script on API 28 and API 35 emulators before packaging release candidates. A successful emulator run validates Android UI/storage behavior; the two independent Noise peers in the JVM suite validate cryptography, serialization and fragment delivery without a second phone. Neither substitutes for radio interoperability and range testing between separate physical devices.
