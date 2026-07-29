#!/bin/bash
sed -i 's/private fun ChannelRow/fun ChannelRow/g' app/src/main/java/com/bitchat/android/ui/MeshPeerListSheet.kt
sed -i 's/private fun PeerItem/fun PeerItem/g' app/src/main/java/com/bitchat/android/ui/MeshPeerListSheet.kt
