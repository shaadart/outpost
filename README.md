# Why Outpost exists

Because a person nearby should still be someone you can reach when the usual network is gone.

Two people can be standing on the same platform with charged phones and no mobile signal. Each phone still has a radio. Each person still has something to say.

It feels a little silly to own two pocket computers and still have to shout across the platform.

**Outpost is an Android messenger built on BitChat, made for familiar conversations over a nearby Bluetooth mesh.** Your profile begins on your phone, without a phone number or email address.

The hope is simple: make good use of what people already carry, and give them more say over what they share and what they keep.

[Begin](#begin) · [Privacy](#privacy) · [Build](#build) · [Tests](#tests) · [Contribute](#contribute)

> **Current status:** Outpost is a release candidate. Delivery between physical phones still needs validation, and distribution requires the owner’s release signing credentials. The [validation report](docs/single-device-validation.md) records what has been checked.

## Conversations

* **A name of your choosing.** Create a profile with identity keys generated on your phone.
* **People nearby.** Open a private conversation. Use profile QR codes to begin identity verification.
* **A place for your group.** Create a group, share its link or QR code, and review invitations before joining.
* **More than words.** Share small photos, files, and short voice messages.
* **A conversation you can return to.** Encrypted text history returns when you reopen the app.
* **A way to leave.** Clear a chat, leave a group, or erase your profile and all the app’s data from your device.

The first release is in English. Earlier translations are preserved in [localization](localization/README.md) for future work.

## Begin

You need Android 8 or newer and a phone with Bluetooth Low Energy. To try this checkout, follow the [build instructions](#build).

1. **Choose your name.** Open Outpost and create a profile.
2. **Allow nearby connections.** Turn on Bluetooth and grant the requested connection permissions. Some Android versions also require location permission for discovery. Notifications are optional. Camera and microphone access are requested when used.
3. **Find someone nearby.** Open **People nearby** or choose **New chat** from the action menu. Both people need participating apps and a reachable connection.
4. **Make a group.** Tap the plus button and choose **New group**. Share its invitation with the people you want there. To join, open an invitation link, scan its QR code, or choose **Join group**.

An invitation gives access to a group. Messages still need a path to the people in it.

## Connection

The nearby mesh is designed to exchange messages without an internet connection. Bluetooth lets participating phones discover one another and pass messages onward through other phones.

Think of passing a folded note along a row of people. Each person helps it travel a little farther. With encrypted private messages, the people carrying the note can help without reading its words.

Distance, walls, batteries, and Android’s background rules all matter. Delivery can be delayed or fail. In private chats, **Sent** means the app dispatched the message. A delivery receipt confirms that the recipient received it.

The source retains Tor, Nostr, and WiFi Aware code from the original engine. The simpler interface leaves out location channels and the old broadcast templates. Delivery through internet relays remains unverified.

## Privacy

Privacy should be understandable enough to make a choice about it.

**Private conversations** use BitChat’s Noise encryption. A display name alone does not establish who someone is; use identity verification when that matters.

**Groups** encrypt messages and file transfers with a shared key carried in the invitation. Anyone holding it can read, send, and invite others. These groups require Outpost; original BitChat clients do not support them.

Leaving a group does not revoke anyone else’s invitation. Groups currently have no administrator removal or automatic key rotation. If an invitation escapes the intended circle, create a new group.

**Nearby public chat** is visible to the mesh. Choose it with that audience in mind.

**Stored history** is encrypted on your phone. Attachments stay in the app’s private files and rely on Android’s device encryption, without separate encryption for each file. Private app data is excluded from Android backups and device transfers.

## Leaving

You deserve a way to put a conversation down.

Use the conversation menu to **Clear chat**, or **Leave group** to leave and clear its history. Clearing removes local messages, unread state, private text waiting in the outbox, and the chat’s media held by the app. A saved cutoff prevents older synchronized messages from returning. New messages can still arrive in a chat you have only cleared.

Use **Options → Erase account and all data** to ask Android to remove your profile, keys, groups, chats, and app attachments. Android stops the app, and the next launch returns to profile setup.

These controls affect this device. Other people keep their copies. Exported files, screenshots, and copies elsewhere remain outside the app’s control. A reset cannot guarantee that every trace is physically unrecoverable from flash storage.

Privacy belongs among ordinary things: closing a door, folding a letter, deciding when a conversation is finished. The controls should carry that same care.

## Limits

For now, the messenger travels light. These limits come from the current message and transfer formats.

* **Private text:** 255 bytes of encoded text. Letters and emoji can use several bytes each. Longer text can be attached as a file within the file limit.
* **Group and public text:** 8 KB per message.
* **Attachments:** 96 KB per file. Photos are resized and compressed automatically.
* **Voice notes:** up to 10 seconds.
* **Saved history:** the latest 500 messages per conversation.
* **Incoming media:** further files are refused when the app’s media storage reaches 200 MB.

Oversized text stays in the composer with an explanation. Eventual delivery to someone absent or far away is not guaranteed.

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

The [recorded validation](docs/single-device-validation.md) includes:

* 143 logic and protocol tests passed, with no failures or skipped tests.
* Four Android tests passed on a Pixel 9a, covering encrypted history, files, image limits, and identity keys.
* The optimized QA app passed profile, permission refusal, history, chat clearing, group leave, and account erasure checks on Android 9 and Android 15 emulators.
* Debug and release builds passed. Both lint checks reported zero errors; warnings remain documented.

Two independent software peers also test encrypted communication, reconnects, replay rejection, and file fragments arriving out of order. Physical Bluetooth delivery still needs validation. No independent security audit is claimed.

See [the device testing guide](scripts/README.md) for the separate **Outpost QA** app. It installs beside an existing profile. Its smoke script erases the disposable QA app’s data to test account reset.

## Explore

The app uses Kotlin and Jetpack Compose, with a background service for mesh connectivity.

* [Interface](app/src/main/java/com/bitchat/android/ui/) contains screens and the state they display.
* [Mesh](app/src/main/java/com/bitchat/android/mesh/) handles discovery, connections, routing, and message fragments.
* [Groups](app/src/main/java/com/bitchat/android/groups/) contains invitations and group encryption.
* [Services](app/src/main/java/com/bitchat/android/services/) contains shared chat state, encrypted history, and message routing.
* [Noise](app/src/main/java/com/bitchat/android/noise/) and [crypto](app/src/main/java/com/bitchat/android/crypto/) contain encryption and key management.
* [Protocol](app/src/main/java/com/bitchat/android/protocol/) defines the messages exchanged between phones.

Read [About Outpost](ABOUT.md) for the thinking behind it, and [the project review](docs/production-readiness.md) for its history and remaining release work.

## Contribute

A careful bug report is a contribution. So is a clearer sentence, a screen that works with larger text, or a fix that lets a phone keep its battery a little longer.

Testing conversations between real phones is especially useful. Record the phone models, Android versions, connection conditions, what you expected, and what happened. Use invented messages and keep private conversations and group invitations out of public reports.

Keep changes clear and test the behavior you change. When working on the interface, consider someone opening the app for the first time.

Attention, battery, and storage are things people lend to software. We should spend them carefully.

## Thanks

Outpost builds on the BitChat Android engine from Permissionless Tech. Its networking and encryption foundations come from that work and its contributors.

The source is open under [GPLv3](LICENSE.md), so others can inspect it, learn from it, improve it, and carry it forward.

An outpost is a small place from which someone can answer.

We hope this one helps.
