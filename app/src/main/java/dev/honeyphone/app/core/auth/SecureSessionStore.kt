package dev.honeyphone.app.core.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts the server access token with a non-exportable Android Keystore key. */
class SecureSessionStore(context: Context) {
    private val preferences = context.getSharedPreferences("honeyphone_session", Context.MODE_PRIVATE)

    fun accessToken(): String? = runCatching {
        val encoded = preferences.getString(KEY_TOKEN, null) ?: return null
        val packed = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = packed.copyOfRange(0, IV_LENGTH)
        val ciphertext = packed.copyOfRange(IV_LENGTH, packed.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }.getOrNull().also { if (it == null) clear() }

    fun saveAccessToken(value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        preferences.edit().putString(KEY_TOKEN, Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
    }

    fun accountId(): String? = preferences.getString(KEY_ACCOUNT_ID, null)

    fun saveAccountId(value: String) { preferences.edit().putString(KEY_ACCOUNT_ID, value).putBoolean(KEY_EVER_BOUND, true).apply() }

    fun hasEverBoundAccount(): Boolean = preferences.getBoolean(KEY_EVER_BOUND, false)

    fun clear() { preferences.edit().remove(KEY_TOKEN).apply() }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build())
        return generator.generateKey()
    }

    private companion object {
        const val KEY_ALIAS = "honeyphone_access_token"
        const val KEY_TOKEN = "encrypted_access_token"
        const val KEY_ACCOUNT_ID = "account_id"
        const val KEY_EVER_BOUND = "ever_bound_account"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
    }
}
