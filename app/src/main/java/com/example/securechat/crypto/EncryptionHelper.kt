package com.example.securechat.crypto

import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

object EncryptionHelper {
    private const val AES_ALGORITHM = "AES/GCM/NoPadding"
    private const val RSA_ALGORITHM = "RSA/ECB/OAEPPadding" // generic; params supplied explicitly below

    // Main digest SHA-256, MGF1 digest SHA-1 — matches AndroidKeyStore's hardware behavior
    private val OAEP_SPEC = OAEPParameterSpec(
        "SHA-256",
        "MGF1",
        MGF1ParameterSpec.SHA1,
        PSource.PSpecified.DEFAULT
    )

    fun generateAESKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES)
        keyGenerator.init(256)
        return keyGenerator.generateKey()
    }

    fun encryptMessage(message: String, aesKey: SecretKey): String {
        val cipher = Cipher.getInstance(AES_ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, aesKey)
        val iv = cipher.iv
        val encryptedBytes = cipher.doFinal(message.toByteArray(Charsets.UTF_8))
        val combined = iv + encryptedBytes
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decryptMessage(encryptedMessage: String, aesKey: SecretKey): String {
        val combined = Base64.decode(encryptedMessage, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, 12)
        val encryptedBytes = combined.copyOfRange(12, combined.size)
        val cipher = Cipher.getInstance(AES_ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
        return String(cipher.doFinal(encryptedBytes), Charsets.UTF_8)
    }

    fun encryptAESKeyWithRSA(aesKey: SecretKey, publicKeyBase64: String): String {
        val publicKeyBytes = Base64.decode(publicKeyBase64, Base64.NO_WRAP)
        val keySpec = X509EncodedKeySpec(publicKeyBytes)
        val keyFactory = KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_RSA)
        val publicKey = keyFactory.generatePublic(keySpec)

        val cipher = Cipher.getInstance(RSA_ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, publicKey, OAEP_SPEC)   // <-- explicit spec
        val encryptedBytes = cipher.doFinal(aesKey.encoded)
        return Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
    }

    fun decryptAESKeyWithRSA(encryptedAESKeyBase64: String, privateKey: PrivateKey): SecretKey {
        val encryptedBytes = Base64.decode(encryptedAESKeyBase64, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(RSA_ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, privateKey, OAEP_SPEC)  // <-- explicit spec, matches Keystore
        val decryptedBytes = cipher.doFinal(encryptedBytes)
        return SecretKeySpec(decryptedBytes, 0, decryptedBytes.size, KeyProperties.KEY_ALGORITHM_AES)
    }

    fun encryptBytes(bytes: ByteArray, secretKey: SecretKey): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val encryptedData = cipher.doFinal(bytes)
        return iv + encryptedData
    }

    fun decryptBytes(encryptedBytes: ByteArray, secretKey: SecretKey): ByteArray {
        val iv = encryptedBytes.copyOfRange(0, 16)
        val encryptedData = encryptedBytes.copyOfRange(16, encryptedBytes.size)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))
        return cipher.doFinal(encryptedData)
    }
}