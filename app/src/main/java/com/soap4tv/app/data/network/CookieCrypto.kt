package com.soap4tv.app.data.network

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-GCM encryption backed by the Android Keystore. Used to protect the
 * persisted cookie jar (PHPSESSID) at rest in DataStore.
 *
 * Format of encoded payload: base64(iv || ciphertext) where iv is 12 bytes.
 */
object CookieCrypto {
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "soap4tv_cookies_v1"
    private const val TRANSFORMATION =
        "${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_GCM}/${KeyProperties.ENCRYPTION_PADDING_NONE}"
    private const val GCM_TAG_BITS = 128
    private const val IV_SIZE = 12

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE_PROVIDER
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val out = ByteArray(iv.size + ct.size).also {
            System.arraycopy(iv, 0, it, 0, iv.size)
            System.arraycopy(ct, 0, it, iv.size, ct.size)
        }
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    fun decrypt(encoded: String): String? = try {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        if (bytes.size <= IV_SIZE) null
        else {
            val iv = bytes.copyOfRange(0, IV_SIZE)
            val ct = bytes.copyOfRange(IV_SIZE, bytes.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        }
    } catch (_: Exception) {
        null
    }
}
