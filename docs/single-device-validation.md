# Release hardening and single-device validation

This follow-up addresses the release blockers found during the initial commit-history review. The first release is English-only, as requested; inherited translations are preserved in `localization/legacy-res` and are not packaged.

## Verified results

- **143 JVM tests passed, zero failed, zero skipped.** The nine previously ignored notification tests now run, together with notification refusal/revocation checks. Real Android resources are included in JVM tests. The identity-reset test that requires Android Keystore runs on Android instead of relying on a JVM Keystore substitute.
- **Four Android instrumented tests passed on the Pixel 9a, Android 17/API 37.** These cover encrypted history across store recreation, absence of synthetic plaintext in preference XML, oversized-file rejection without leftover copies, bounded image decoding/compression, and identity-key rotation in namespaced QA preferences. The original profile and chat preferences are not modified by these tests.
- **Strict debug and release lint passed with zero errors**, with the old baseline removed. Warnings remain and are reported; no error severities were lowered.
- **Optimized QA smoke suite passed on Android 9/API 28 and Android 15/API 35 emulators.** The release-shrunk APK completed profile creation with connection permissions refused, group creation and invitation QR rendering, synthetic messaging, encrypted history restoration after process restart, chat clearing across another restart, group leave, and full account erasure back to onboarding. Repeating the run also exercised erasure of a pre-existing disposable QA profile. The normal Pixel installation was never erased.
- Debug and optimized unsigned release APK assembly passed. Release distribution still requires the owner's signing credentials; no build was published.

The latest debug build was installed on the Pixel with `adb install -r`, preserving its existing profile, and all four Android tests passed again on that build. Strict debug lint reports 312 warnings and 18 hints, with no baseline or error suppression added to conceal failures.

The original [Pixel smoke report](pixel-9a-smoke-test.md) also records group creation, invitation links and QR rendering, local send/history restore, chat clearing across process restart, group leave, Android picker launches, and foreground BLE scanning/advertising while notification permission remained declined.

## Defects corrected

The optimized UI check also exposed the onboarding action being covered by the keyboard. The setup screen now applies keyboard/system-bar insets and scrolls on smaller viewports.

1. **Encrypted replay tracking:** the inherited sliding window shifted bits in the wrong direction. Regression tests first reproduced acceptance of previously seen ciphertext and rejection of valid reordered messages. The corrected window preserves previously seen nonces across byte boundaries, accepts unseen reordered messages once, and rejects replays. Large nonce jumps are bounded before integer conversion. Authentication failures do not advance the window. This follows the [Noise transport requirements for reordered messages](https://noiseprotocol.org/noise.html#out-of-order-transport-messages).
2. **Bluetooth permission revocation and cleanup:** cleanup handles revoked permissions explicitly; delayed cleanup snapshots GATT handles before connection maps are cleared. Client callbacks, scanning, server responses and writes handle SecurityException. Scanner/advertiser handles are reacquired after Bluetooth is enabled, and the foreground service reacts to radio state changes. The wrapper provides a Connection permissions settings entry, and refusal persists across restarts.
3. **Notification refusal:** notification permission controls notification posting rather than eligibility to run the foreground mesh service. Device testing confirmed service operation with that permission declined.
4. **Silent sends and misleading limits:** private text validation matches the original 255-byte UTF-8 TLV limit, while group/public text is bounded to 8 KB. Rejected drafts remain in the composer. The previous 50 MB attachment promise exceeded the receiver's 256-fragment budget; the supported limit is now 96 KB. Images are decoded with sampling and compressed within that budget; voice notes remain short. Larger transfers require a separate streaming protocol rather than a larger unbounded allocation.
5. **Restart and delivery state:** encrypted history preserves pending/sent/delivered/read states. Private text still waiting for a route is restored into the router after process restart; history is committed before dispatch. Interrupted file progress restores as a failure rather than an endless progress indicator. Sent denotes transport dispatch, not receiver confirmation. This is not an exactly-once delivery guarantee or an unlimited offline mailbox.
6. **Deletion and retention:** clearing waits for cancelled media preparation to finish before taking its deletion snapshot. Unreferenced media older than 24 hours is pruned at startup and at most hourly after history writes; referenced files and fresh preparations are retained. A failed history open keeps connectivity gated and displays an error. Unrelated Nostr aliases are no longer merged into another person's chat.

## Delivery checks without a second phone

`NoisePeerIntegrationTest` creates two independent peers with real Curve25519 keys and the production Noise implementation. The suite exercises:

- Mutual handshake authentication and Unicode messages in both directions.
- Simultaneous initiation and reconnect with old-session ciphertext rejection.
- Sequential and reordered messages, duplicates, replay-window boundaries and stale nonces.
- Forged nonce/ciphertext rejection followed by valid traffic.
- Concurrent encryption with unique nonces.
- An encrypted attachment through real packet serialization and fragment reassembly, including reordered/duplicate fragments and a deliberately withheld fragment. Partial payloads are not published.

The Android device tests use real Keystore and image/file APIs. The separate optimized `qa` build and [smoke script](../scripts/README.md) allow account-reset testing without erasing an existing profile. CI is configured to run the Android tests and QA smoke suite on API 28 and API 35 before release packaging; those remote jobs have not been run from this workspace.

These checks establish protocol and Android storage behavior without two phones. They do not establish real-world Bluetooth interoperability, range, reconnection under interference, or receipt delivery between physical radios. Tor/relay delivery was not established by this validation. Shared-key groups still lack administrator-controlled removal, key rotation and forward secrecy; invitation holders can invite others. No independent cryptographic audit is claimed.
