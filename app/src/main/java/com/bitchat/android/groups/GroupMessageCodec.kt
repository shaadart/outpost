package com.bitchat.android.groups

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Versioned Outpost extension; public BitChat packets and Noise private messages remain unchanged. */
object GroupMessageCodec {
    private val magic = "OUTPOST_GROUP_V1\u0000".toByteArray(Charsets.US_ASCII)
    private const val ID_SIZE = 39
    private const val NONCE_SIZE = 12
    const val MAX_BODY_BYTES = 96 * 1024 + 4096
    data class Payload(val channel: String, val isFile: Boolean, val body: ByteArray)

    fun isGroupPayload(bytes: ByteArray): Boolean = bytes.size >= magic.size &&
        magic.indices.all { bytes[it] == magic[it] }

    fun channel(bytes: ByteArray): String? {
        if (!isGroupPayload(bytes) || bytes.size < magic.size + ID_SIZE + NONCE_SIZE + 17) return null
        return String(bytes, magic.size, ID_SIZE, Charsets.US_ASCII).takeIf(GroupInvite::isGroup)
    }

    fun encrypt(channel: String, key: ByteArray, body: ByteArray, isFile: Boolean = false): ByteArray {
        require(GroupInvite.isGroup(channel) && key.size == 32 && body.size <= MAX_BODY_BYTES)
        require(isFile || body.size <= 8192)
        val header = magic + channel.toByteArray(Charsets.US_ASCII)
        val nonce = ByteArray(NONCE_SIZE).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(header)
        return header + nonce + cipher.doFinal(byteArrayOf(if (isFile) 1 else 0) + body)
    }

    fun decrypt(bytes: ByteArray, key: ByteArray): Payload? = runCatching {
        val channel = channel(bytes) ?: return null
        if (key.size != 32 || bytes.size > magic.size + ID_SIZE + NONCE_SIZE + 17 + MAX_BODY_BYTES) return null
        val headerSize = magic.size + ID_SIZE
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"),
            GCMParameterSpec(128, bytes.copyOfRange(headerSize, headerSize + NONCE_SIZE)))
        cipher.updateAAD(bytes.copyOfRange(0, headerSize))
        val plain = cipher.doFinal(bytes.copyOfRange(headerSize + NONCE_SIZE, bytes.size))
        if (plain.isEmpty() || plain[0].toInt() !in 0..1) return null
        if (plain[0].toInt() == 0 && plain.size > 8193) return null
        Payload(channel, plain[0].toInt() == 1, plain.copyOfRange(1, plain.size))
    }.getOrNull()
}
