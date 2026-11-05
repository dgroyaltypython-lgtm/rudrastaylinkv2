package com.rudradigital.rudrastaylink.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts the JWT pair with an AES key stored in Android Keystore. */
class TokenStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("rudra_secure_session", Context.MODE_PRIVATE)
    private val alias = "rudra_staylink_session_key_v1"

    @Synchronized
    fun save(access: String, refresh: String) {
        prefs.edit()
            .putString("access", encrypt(access))
            .putString("refresh", encrypt(refresh))
            .apply()
    }

    @Synchronized
    fun accessToken(): String? = prefs.getString("access", null)?.let(::decryptSafely)

    @Synchronized
    fun refreshToken(): String? = prefs.getString("refresh", null)?.let(::decryptSafely)

    @Synchronized
    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decryptSafely(encoded: String): String? = try {
        val raw = Base64.decode(encoded, Base64.NO_WRAP)
        require(raw.size > 12) { "Invalid encrypted value" }
        val iv = raw.copyOfRange(0, 12)
        val encrypted = raw.copyOfRange(12, raw.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(encrypted), Charsets.UTF_8)
    } catch (_: Exception) {
        clear()
        null
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }
}
