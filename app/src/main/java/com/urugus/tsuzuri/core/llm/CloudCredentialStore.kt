package com.urugus.tsuzuri.core.llm

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

interface CloudCredentialStore {
    fun apiKey(): String?
    fun hasApiKey(): Boolean = apiKey()?.isNotBlank() == true
    fun saveApiKey(value: String)
    fun clearApiKey()
}

/**
 * BYOKのAPIキーをSharedPreferencesへ平文保存しないための小さな保存境界。
 * 暗号鍵はAndroid Keystoreに置き、Preferencesには暗号文とIVだけを保存する。
 */
@Singleton
class AndroidCloudCredentialStore @Inject constructor(
    @ApplicationContext context: Context,
) : CloudCredentialStore {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun apiKey(): String? {
        val encodedCipher = prefs.getString(KEY_CIPHER_TEXT, null) ?: return null
        val encodedIv = prefs.getString(KEY_IV, null) ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(GCM_TAG_BITS, Base64.decode(encodedIv, Base64.NO_WRAP)),
            )
            String(cipher.doFinal(Base64.decode(encodedCipher, Base64.NO_WRAP)), Charsets.UTF_8)
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    override fun saveApiKey(value: String) {
        val trimmed = value.trim()
        require(trimmed.isNotEmpty()) { "API key must not be empty" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val cipherText = cipher.doFinal(trimmed.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(KEY_CIPHER_TEXT, Base64.encodeToString(cipherText, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    override fun clearApiKey() {
        prefs.edit()
            .remove(KEY_CIPHER_TEXT)
            .remove(KEY_IV)
            .apply()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private companion object {
        const val PREFS = "cloud_credentials"
        const val KEY_CIPHER_TEXT = "api_key_cipher_text"
        const val KEY_IV = "api_key_iv"
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "tsuzuri_cloud_api_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
    }
}
