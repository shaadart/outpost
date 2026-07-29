Built for communication beyond the internet.

Secure messenger app that doesn't rely on the internet.

# Outpost

Outpost is an offline-first, decentralized messaging application built for situations where conventional communication infrastructure is unavailable or unreliable.

Built on top of the BitChat networking engine from Permissionless Tech, Outpost focuses on making secure mesh communication accessible to everyday users. It removes technical interfaces and replaces them with a familiar messaging experience that requires no configuration or networking knowledge.

Messages are exchanged directly between nearby devices over Bluetooth Low Energy (BLE), forming a delay-tolerant mesh network without relying on cellular service, Wi-Fi infrastructure, or centralized servers.

## Features

### Verified Broadcasts

Outpost includes a dedicated broadcast channel for trusted announcements.

Broadcast messages are signed with Ed25519 and verified against a trusted public key embedded in the application. Messages that fail signature verification are discarded automatically.

### Offline Application Sharing

The application can be shared directly between Android devices using the system share sheet and Bluetooth-based transfer mechanisms.

This allows Outpost to be distributed without internet access or an app store.

### Broadcast Channels

Users can create one-way announcement channels for teams or communities.

Each channel has its own cryptographic identity. The channel owner publishes messages while invited members join as read-only participants using a QR code or shared public key.

### Local Radius Chat

Outpost supports temporary public conversations within the local mesh network.

Messages automatically expire, making the feature suitable for short-lived coordination without accumulating long-term message history.

### Simple Onboarding

No phone number, email address, or account is required.

Users choose a display name, and the application generates all required cryptographic keys in the background.

### Panic Wipe

A built-in panic action permanently removes all local data, cryptographic keys, and Android Keystore entries, returning the application to its initial state.

## Architecture

Outpost separates the user interface from the networking layer to simplify maintenance and integration with future BitChat updates.

* User Interface: Jetpack Compose with Material Design 3
* Networking: Bluetooth Low Energy (BLE) and Wi-Fi Aware
* Cryptography: Noise Protocol, ChaCha20-Poly1305, Ed25519, Curve25519, secp256k1
* Concurrency: Kotlin Coroutines and StateFlow

All user interface components communicate with the networking layer through a clean `MeshBackend` interface.

## Building

Generate a release keystore:

```bash
keytool -genkey -v \
  -keystore release.keystore \
  -alias bitchat \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

Create a `keystore.properties` file in the project root containing your signing credentials.

Build the release APK:

```bash
./gradlew assembleRelease
```

The generated APK will be available at:

```text
app/build/outputs/apk/release/
```

## Acknowledgements

Outpost is built on the networking, routing, fragmentation, and cryptographic foundation provided by the BitChat project from Permissionless Tech.

This project extends that foundation with a simplified user experience and additional features for reliable peer-to-peer communication in disconnected environments.

## License

See the LICENSE file for licensing information.
