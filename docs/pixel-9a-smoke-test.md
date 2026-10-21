# Pixel 9a device smoke test

This records the initial device pass. See [the hardening follow-up](single-device-validation.md) for the later permission, replay, retention, test and lint fixes.

Tested on the connected Pixel 9a running Android 17 (API 37), using the current ARM64 debug APK installed over `com.bitchat.droid` with `adb install -r`. The existing profile and application data were retained. This is a single-device smoke test, not an end-to-end delivery certification.

## Device results

| Check | Result |
| --- | --- |
| Install and cold launch | Passed; measured cold launches were approximately 1.0–1.1 seconds. |
| Inbox and navigation | Passed; no CJP feed appeared. |
| Create group | Passed using a temporary QA group; invitation QR rendered. |
| Share invitation | Android share sheet opened; cancelled without sharing to anyone. |
| Invitation deep link | A synthetic `outpost://group` link opened a confirmation; joining succeeded. |
| Send and restore local history | A synthetic group message appeared and survived force-stop/relaunch. No other members were invited; peer delivery was not tested. |
| Encrypted preferences | History and group preferences were readable by the app through Android Keystore-backed storage. The synthetic message and group name did not appear as plaintext in the respective preference XML files. This is a storage smoke check, not a cryptographic audit. |
| Clear chat | Synthetic message disappeared and stayed deleted after force-stop/relaunch. |
| Leave group | Both temporary groups were removed using Leave and clear. |
| File and photo controls | Android document picker and photo picker opened. Cancelled without selecting personal files or photos. |
| Profile | Profile sheet and identity QR rendered; profile values were not changed. |
| Account deletion guard | Confirmation displayed the scope of deletion; Cancel preserved the account. Actual reset was deliberately not performed on this installation. |
| Bluetooth | Bluetooth enabled for testing. BLE scanning and advertising success were observed in app logs. No second Outpost peer was available. |
| Background service | Foreground mesh service remained active with Outpost backgrounded and notification permission declined. |
| Runtime stability | No app fatal exception, ANR, or SecurityException observed in the captured test logs. |

The updated debug app was left running on the Pixel. Notifications remained declined. No invitations or messages were shared with other people, and temporary QA groups were removed.

## Bug found and fixed

The foreground mesh service incorrectly treated `POST_NOTIFICATIONS` as a requirement for running. On this phone, Bluetooth permissions were granted but notifications were declined, so the service did not run.

Service eligibility now depends on the required Bluetooth permissions and the background-service preference. It still supplies the mandatory foreground-service notification. Ordinary notification posts explicitly check notification permission and handle revocation; the wrapper's Notifications menu opens the app's Android notification settings. The device then ran its foreground mesh service with `POST_NOTIFICATIONS` still declined, and BLE scanning and advertising started.

Android explicitly allows starting a foreground service without notification permission; see [notification runtime permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission).

## Local validation and remaining limits

`./gradlew testDebugUnitTest assembleDebug` passed: **118 passed, 9 existing tests skipped, zero failures or errors**. Strict `lintDebug` still fails with **414 errors and 342 warnings**, down from 422 errors before the notification fixes. Remaining errors comprise 359 missing translations, 33 permission diagnostics, 17 plural diagnostics, and 5 indentation diagnostics. The baseline was not expanded. No Android instrumented test suite exists in this checkout; device checks used ADB and UI Automator.

Production release remains blocked by strict lint and the broader [release gates](production-readiness.md#remaining-release-gates). In particular:

- Two-device private/group text and attachment delivery, receipts, reconnect, and replay behavior remain unverified.
- Existing Tor/Arti logs included remote stream errors; internet relay delivery was not established.
- Fresh onboarding, camera scanning, microphone recording, actual account reset, screen-off/reboot behavior, and other Android versions need separate validation.
- Picker launch and local sent-message persistence do not establish that files or messages reached another device.
- No release was published; the phone runs a debug build.

The updated optimized release also assembled successfully with `./gradlew assembleRelease`. Its ARM64 APK is approximately 16 MB and unsigned; it was not installed over the phone's debug build.
