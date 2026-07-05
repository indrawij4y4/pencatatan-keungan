package com.example.pencatatankeungaan.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object KeyStoreHelper {
    private const val KEY_ALIAS = "secure_db_key_alias"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KEY_SIZE = 256

    @Synchronized
    fun getOrCreatePassphrase(context: Context): ByteArray {
        val file = File(context.filesDir, "db_pass_encrypted")
        return if (!file.exists()) {
            // Generate a secure random 32-byte (256-bit) passphrase
            val passphrase = ByteArray(32)
            SecureRandom().nextBytes(passphrase)

            // Generate a master AES key in Android KeyStore
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                KEYSTORE_PROVIDER
            )
            val parameterSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE)
                .build()
            keyGenerator.init(parameterSpec)
            val secretKey = keyGenerator.generateKey()

            // Encrypt the passphrase
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val ciphertext = cipher.doFinal(passphrase)
            val iv = cipher.iv

            // Store IV length, IV, and ciphertext to private storage
            file.writeBytes(byteArrayOf(iv.size.toByte()) + iv + ciphertext)
            passphrase
        } else {
            val bytes = file.readBytes()
            val ivSize = bytes[0].toInt()
            val iv = bytes.copyOfRange(1, 1 + ivSize)
            val ciphertext = bytes.copyOfRange(1 + ivSize, bytes.size)

            // Retrieve the key from Android KeyStore
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val secretKey = keyStore.getKey(KEY_ALIAS, null) as SecretKey

            // Decrypt the passphrase
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            cipher.doFinal(ciphertext)
        }
    }
}
