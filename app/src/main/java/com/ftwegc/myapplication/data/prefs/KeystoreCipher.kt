package com.ftwegc.myapplication.data.prefs

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Symmetric encryption of small secrets. Failures throw [GeneralSecurityException]. */
interface SecretCipher {
    fun encrypt(plain: ByteArray): ByteArray
    fun decrypt(data: ByteArray): ByteArray
}

/**
 * AES-256/GCM with a non-exportable key in the AndroidKeyStore. The key never leaves the secure
 * hardware / keystore daemon and is not part of backups, so ciphertext restored onto another
 * device (or after the key was deleted) fails to decrypt; callers treat that as "not set".
 *
 * Output format: `[1: version][1: iv length][iv][ciphertext + 16-byte tag]`.
 */
class KeystoreCipher(private val alias: String = DEFAULT_ALIAS) : SecretCipher {

    override fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val sealed = cipher.doFinal(plain)
        if (iv.size > Byte.MAX_VALUE) throw GeneralSecurityException("unexpected iv size")
        return byteArrayOf(VERSION, iv.size.toByte()) + iv + sealed
    }

    override fun decrypt(data: ByteArray): ByteArray {
        if (data.size < 2 || data[0] != VERSION) throw GeneralSecurityException("unsupported format")
        val ivLength = data[1].toInt()
        if (ivLength <= 0 || data.size < 2 + ivLength + TAG_BYTES) throw GeneralSecurityException("truncated")
        val iv = data.copyOfRange(2, 2 + ivLength)
        val key = existingKey() ?: throw GeneralSecurityException("key missing")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BYTES * 8, iv))
        return cipher.doFinal(data, 2 + ivLength, data.size - 2 - ivLength)
    }

    /** Removes the key; everything encrypted with it becomes unreadable. */
    fun deleteKey() {
        keyStore().deleteEntry(alias)
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun existingKey(): SecretKey? = keyStore().getKey(alias, null) as? SecretKey

    @Synchronized
    private fun getOrCreateKey(): SecretKey {
        existingKey()?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        const val DEFAULT_ALIAS: String = "vluer_komari_api_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_BYTES = 16
        private const val VERSION: Byte = 1
    }
}
