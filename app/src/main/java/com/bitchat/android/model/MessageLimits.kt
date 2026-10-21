package com.bitchat.android.model

object MessageLimits {
    fun validationError(content: String, privateChat: Boolean): String? {
        val bytes = content.toByteArray(Charsets.UTF_8).size
        return when {
            bytes > 8192 -> "Message is too long. Please use 8 KB or less."
            privateChat && bytes > 255 -> "Private text supports up to 255 UTF-8 bytes. Send longer text as a file."
            else -> null
        }
    }
}
