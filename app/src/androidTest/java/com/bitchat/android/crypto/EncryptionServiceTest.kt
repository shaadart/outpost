package com.bitchat.android.crypto

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.bitchat.android.noise.NoiseEncryptionService
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.Arrays

@RunWith(AndroidJUnit4::class)
class EncryptionServiceTest {

    private lateinit var context: Context
    private lateinit var encryptionService: EncryptionService

    @org.junit.After
    fun cleanupQaPreferences() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        for (name in listOf("bitchat_crypto_secure", "bitchat_crypto", "bitchat_identity")) {
            target.deleteSharedPreferences("outpost_identity_qa_" + name)
        }
    }

    @Before
    fun setup() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        // Each tested component uses only namespaced QA preferences, never the user's identity.
        context = object : android.content.ContextWrapper(target) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences =
                target.getSharedPreferences("outpost_identity_qa_" + name, mode)
        }
        encryptionService = EncryptionService(context)
    }

    @Test
    fun clearPersistentIdentityChangesOnlyQaKeys() {
        // 1. Get initial keys
        val initialStaticKey = encryptionService.getStaticPublicKey()
        val initialSigningKey = encryptionService.getSigningPublicKey()
        val initialFingerprint = encryptionService.getIdentityFingerprint()

        assertNotNull("Initial static key should not be null", initialStaticKey)
        assertNotNull("Initial signing key should not be null", initialSigningKey)

        // 2. Call clearPersistentIdentity (Panic Mode)
        encryptionService.clearPersistentIdentity()

        // 3. Get keys again. 
        val afterStaticKey = encryptionService.getStaticPublicKey()
        val afterSigningKey = encryptionService.getSigningPublicKey()
        val afterFingerprint = encryptionService.getIdentityFingerprint()

        // 4. Verify keys are different (Panic Mode should clear/rotate in-memory keys)
        // Note: We use string comparison for byte arrays to be safe in assertion messages
        assertNotEquals("Static key should change after panic", 
            Arrays.toString(initialStaticKey), Arrays.toString(afterStaticKey))
        
        assertNotEquals("Signing key should change after panic", 
            Arrays.toString(initialSigningKey), Arrays.toString(afterSigningKey))

        assertNotEquals("Fingerprint should change after panic", 
            initialFingerprint, afterFingerprint)
    }
}
