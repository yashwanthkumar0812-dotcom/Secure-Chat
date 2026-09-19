package com.example.securechat.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey

class KeyManager {
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private val alias = "securechat_rsa_key"

    init {
        if (!keyStore.containsAlias(alias)) {
            val keyPairGenerator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
            val spec = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_ECB)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA1)
                .setKeySize(2048)
                .build()

            keyPairGenerator.initialize(spec)
            keyPairGenerator.generateKeyPair()
        }
    }

    fun getPublicKeyBase64(): String? {
        val publicKey = keyStore.getCertificate(alias)?.publicKey ?: return null
        return Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
    }

    fun getPrivateKey(): PrivateKey {
        return keyStore.getKey(alias, null) as PrivateKey
    }
}