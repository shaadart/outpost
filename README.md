# Purpose

**The people beside you are still there when the network is gone.**

Outpost is a nearby messenger for Android, built on BitChat. It brings familiar conversations to the radios already in our pockets, without asking for a phone number or email address.

Sometimes, “Did you bring snacks?” should only have to travel across the park.

## How

1. **Choose a name.** Your profile is created on your phone.
2. **Turn on Bluetooth.** Allow the nearby connection permissions Android requests.
3. **Find your people.** Start a private chat with someone nearby.
4. **Make a group.** Share an invitation link or QR code with the people you want there.

Participating phones can pass messages onward through the mesh. Reach depends on nearby devices, distance, walls, batteries, and Android’s background rules.

## What

* Private conversations and invited groups.
* Small photos, files, and short voice notes.
* Encrypted text history kept on your phone.
* Controls to clear a chat, leave a group, or erase your profile and app data from this device.

### Inside the app

<a href="docs/images/profile.png"><img src="docs/images/profile.png" width="160" alt="Outpost profile creation screen"></a>
<a href="docs/images/nearby-demo.png"><img src="docs/images/nearby-demo.png" width="160" alt="Labeled Outpost demo showing two sample nearby contacts"></a>
<a href="docs/images/chat-demo.png"><img src="docs/images/chat-demo.png" width="160" alt="Labeled Outpost demo showing messages between Alex and Maya"></a>
<a href="docs/images/erase.png"><img src="docs/images/erase.png" width="160" alt="Outpost account erasure confirmation explaining what is removed"></a>

Screens captured in an Android emulator. The nearby contacts and conversation are clearly labeled samples, not a live Bluetooth exchange. Tap a screenshot to enlarge it.

### Yours to understand

Nearby public chat is visible to the mesh. Anyone holding a group invitation can read, send, and invite others. Deletion affects your device; other people keep their copies.

This release candidate still needs physical Bluetooth delivery validation and release signing. Private text is limited to 255 encoded bytes and files to 96 KB. Invitations do not guarantee delivery to someone far away.

### Try it

Requires Android 8 or newer with Bluetooth Low Energy. To build, use JDK 17 and Android SDK 35:

```bash
./gradlew assembleDebug
```

[Build and test](docs/development.md) · [Validation](docs/single-device-validation.md) · [About](ABOUT.md) · [License](LICENSE.md)

Built with gratitude to BitChat and its contributors. Shared under GPLv3.
