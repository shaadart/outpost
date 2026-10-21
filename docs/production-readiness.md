# Outpost implementation and release review

## Product intent from the complete commit log

All ten commits available in this checkout were reviewed in chronological context, alongside the current implementation and relevant wrapper/transport diffs. There are no additional commits on other local refs.

| Commit | Direction |
| --- | --- |
| `e5cb4d7` | Initial BitChat engine and Arti JNI import; substantial networking, crypto, media, and location infrastructure. |
| `3a9f6e6` | Replace the original technical UI with an inbox/chat wrapper; add a backend facade and phonetic identity names. |
| `b2063eb` | Local display-name onboarding and familiar message styling; start release-signing configuration. |
| `545763f` | Profile settings and identity QR sharing/scanning. |
| `7579cb6` | Keyboard and navigation inset handling. |
| `b4a0084` | Experiment with broadcast creation and QR invitations. |
| `2ab5028` | Describe the simpler offline messenger direction in the README. |
| `ce773e6` | Rename application and shared sheet classes. |
| `45386c4` | Outpost branding across the interface and resources. |
| `ef6d67a` | Refine broadcast display names and handling. |

The intended product is a lightweight local-profile messenger over the existing engine: private conversations, group invitations, media, and user-controlled local deletion. It is not a server-backed phone-number account system. The CJP feed and one-way broadcast experiments do not fit this interface.

## Implemented in this change

- A single inbox and conversation navigation model, nearby-person discovery, ordinary group rows, profile access, recent-message sorting, and explicit public-chat labeling. QR matches use public keys rather than treating a nickname as a peer ID. Message ownership uses transport identity when available.
- Create/join groups, validated versioned invitations, QR and system-share invitations, links opened from another app, user confirmation before joining, repeat invitations, and leave/clear controls.
- An Outpost group carrier inside signed mesh packets. Previously both senders ignored their channel arguments, so channel messages became public text. New groups encrypt content with AES-256-GCM, random 96-bit nonces, a 256-bit group key, and authenticated group/version headers. Group keys are stored in encrypted preferences and available to background receivers. Unknown groups, missing keys, malformed envelopes, and authentication failures are dropped without plaintext fallback. Text and file carriers are covered by the same envelope.
- Photo, file, and short voice controls connected to the wrapper. File copying, image processing, and media preparation run off the UI thread. Private media waits for a mesh Noise session and reports a timeout. File input sizes, filename extensions, wire lengths, and incoming storage are bounded. FileProvider exposes media directories and offline APK copies rather than the whole private files directory.
- Local encrypted history, capped at 500 messages per conversation, with a stable persisted schema protected from R8 field renaming. Versioned writes prevent a delayed snapshot from undoing deletion. History is flushed on ordinary app shutdown.
- Conversation clearing removes the process-wide source of truth, read/unread metadata, pending private text outbox entries, and unshared referenced media. Persisted deletion times reject old packet/history replay. Shared media still used by another chat is retained.
- Full account deletion delegates to Android's application-data reset. The former animated wipe delayed real work, deleted only selected preferences, and restarted networking under a fresh identity. No simulated wipe remains. Backups and device transfers exclude app data.
- Removal of the CJP feed, placeholder verification key, announcement interception, duplicate Application class, obsolete patch scripts, and `.orig`/`.rej` sources. Existing legacy feed tags are removed during migration.
- Permission refusal no longer starts networking anyway; permission requests are issued once after onboarding. Boot startup is gated by onboarding and guarded against Android startup restrictions. The service no longer declares unused `dataSync`. Legacy image export avoids Android 10-only Downloads APIs on Android 8/9.
- Release optimization strips verbose/debug/info log calls so message and peer diagnostics are not emitted by production builds.
- Strict lint errors now fail CI. Local unsigned release artifacts can be built without signing credentials. The release workflow requires signing secrets, tests, lint, and signature verification and only uploads candidate artifacts.

## Latest follow-up

See [release hardening and single-device validation](single-device-validation.md) for the current results: 143 JVM tests with no skips, four passing Pixel device tests, strict lint with zero errors and no baseline, and the optimized QA workflow. The counts below record the initial review and are retained as history.

## Initial validation

Final local verification: **118 unit tests passed, 9 existing tests were skipped, and no tests failed or errored**. Debug and optimized release APK assembly passed. Strict debug lint failed with **422 errors and 342 warnings**; the combined command therefore correctly returned a failure. Release vital lint passed, which does not replace the stricter full lint gate. The ARM64 unsigned release APK is approximately **16 MB**; it needs production signing before installation/distribution. No release was published.

Local commands:

```bash
./gradlew testDebugUnitTest assembleDebug assembleRelease lintDebug --continue
```

Focused tests cover invitation Unicode and reserved characters, malformed links, random keys and IDs, encryption/decryption for text and binary data, tampered headers/ciphertext, wrong keys, truncated packets, unknown-group receive isolation, private/group/public deletion scope, replay rejection, bounded history, persisted-history round trips, and stale-write ordering.

No connected Android device or configured emulator was available during the initial review. A subsequent [Pixel 9a smoke test](pixel-9a-smoke-test.md) verified Keystore-backed persistence, group creation/join/leave, history restoration and clearing, picker launches, and background mesh operation on Android 17. It exposed and fixed notification permission incorrectly blocking the foreground service. The updated debug build, optimized release build, and unit tests passed; strict lint now reports **414 errors and 342 warnings**. Actual account reset was not performed on the user's installation, and two-device delivery remains unverified.

The persisted-history unit tests inject ordinary preferences to exercise schema and stale-write ordering; they do not simulate hardware-backed Keystore security. The first lint run reported 429 inherited errors, including incomplete translations/plurals, permission checks, a missing manifest receiver, legacy API use, and suspicious indentation. The baseline was not expanded to conceal existing failures.

## Original release gates and follow-up status

1. **Strict lint: resolved in the follow-up.** Permission handling and indentation were corrected. The first release is consistently English; incomplete translations are preserved outside the APK. The baseline was removed and strict lint passes with zero errors.
2. **Run a real-device matrix.** At least two physical devices: Android 8/9, Android 12+, Android 13 notification refusal, and current Android background restrictions. Verify onboarding denial/retry; QR scanning after blank frames; create/join and received deep links; Unicode names; text/photo/file/audio over BLE and optional Wi-Fi Aware; attachment cancellation; reconnect; process death; screen off; reboot; revoked permissions; history restoration; group leave; chat clear and sync replay; full data reset and next-launch onboarding.
3. **Review the group security model.** Shared invitation keys provide confidentiality and authenticated content, not administrator-managed membership, member removal, key rotation, or forward secrecy. Group IDs, timing, and sender metadata remain visible on the mesh. Have the envelope and storage changes independently reviewed before making broad security claims. Existing BitChat compatibility applies to original private/public traffic; this group extension requires Outpost recipients.
4. **Exercise delivery guarantees.** The follow-up persists delivery state and recovers private text waiting for a route from encrypted history. Mesh gossip caches and already dispatched transfers still have no general durable retry guarantee. Unsent messages, receipts after restart, disconnected recipients, duplicate media from multiple transports, and the original engine's routing/favorite behavior need end-to-end validation before promising WhatsApp-level reliability.
5. **Review retention and media storage.** The follow-up prunes unreferenced attachments older than 24 hours at startup and at most hourly after history writes, and drains cancelled preparation before clearing a chat. In-flight receives coordinate with clearing, duplicate group attachments are discarded, and temporary app viewer copies are cleared with a conversation; exported files remain outside the app's control. The app-wide reset clears app-owned files; conversation clearing is not a forensic erase guarantee. Attachments are app-private files, not individually encrypted blobs. Device storage pressure, exported copies, and repeated oversized incoming transfers need further testing.
6. **Finish product polish and operations.** Localize the new wrapper strings, test accessibility/font scaling and small screens, choose final application ID/version/signing identity before first distribution, and confirm backup/restore and upgrade behavior on OEM devices. The engine namespace/application ID was deliberately retained for existing installations. Release credentials and distribution remain external configuration.

## Platform references

- [Android application-data reset](https://developer.android.com/reference/android/app/ActivityManager#clearApplicationUserData()) describes the platform reset and its effect on permissions, notifications, and URI grants.
- [Foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types) documents connected-device services and Android's restrictions on `dataSync` startup after boot.
