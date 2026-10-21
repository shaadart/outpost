package com.bitchat.android.groups

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Shared by the foreground transports and UI, including when no Activity is attached. */
class GroupRepository private constructor(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context, "outpost_groups",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    @Synchronized
    fun join(invite: GroupInvite): Boolean {
        if (GroupInvite.parse(invite.encode()) != invite) return false
        val existing = get(invite.id)
        if (existing != null && existing.key != invite.key) return false
        return prefs.edit().putString(invite.id, invite.encode()).commit()
    }

    fun get(id: String): GroupInvite? = prefs.getString(id, null)?.let(GroupInvite::parse)
    fun all(): List<GroupInvite> = prefs.all.keys.mapNotNull(::get)
    fun leave(id: String): Boolean = prefs.edit().remove(id).commit()

    companion object {
        @Volatile private var instance: GroupRepository? = null
        fun getInstance(context: Context): GroupRepository = instance ?: synchronized(this) {
            instance ?: GroupRepository(context.applicationContext).also { instance = it }
        }
    }
}
