package com.smsforwarder.app.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.RSAKeyGenParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeyManager @Inject constructor() {

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "sms_forwarder_rsa_identity_key"
        private const val KEY_SIZE_BITS = 2048 // 2048-bit RSA for optimal compatibility & speed
    }

    private val keyStore: KeyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply {
        load(null)
    }

    /**
     * Checks if this device already has an identity keypair generated in KeyStore.
     */
    fun hasKeyPair(): Boolean {
        return keyStore.containsAlias(KEY_ALIAS)
    }

    /**
     * Generates or retrieves the hardware-backed RSA key pair.
     */
    @Synchronized
    fun getOrCreateKeyPair(): KeyPair {
        if (hasKeyPair()) {
            val privateKey = keyStore.getKey(KEY_ALIAS, null) as PrivateKey
            val publicKey = keyStore.getCertificate(KEY_ALIAS).publicKey
            return KeyPair(publicKey, privateKey)
        }
        return generateNewKeyPair()
    }

    /**
     * Generates a new RSA KeyPair inside the Android KeyStore.
     */
    private fun generateNewKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_RSA,
            KEYSTORE_PROVIDER
        )

        val parameterSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_DECRYPT
        )
            .setAlgorithmParameterSpec(
                RSAKeyGenParameterSpec(KEY_SIZE_BITS, RSAKeyGenParameterSpec.F4)
            )
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
            .setKeySize(KEY_SIZE_BITS)
            .build()

        kpg.initialize(parameterSpec)
        return kpg.generateKeyPair()
    }

    /**
     * Retrieves the private key from KeyStore.
     */
    fun getPrivateKey(): PrivateKey {
        return getOrCreateKeyPair().private
    }

    /**
     * Retrieves the public key from KeyStore.
     */
    fun getPublicKey(): PublicKey {
        return getOrCreateKeyPair().public
    }

    /**
     * Returns the Public Key in standard PEM format.
     */
    fun getPublicKeyPem(): String {
        val pubKey = getPublicKey()
        val base64Key = Base64.encodeToString(pubKey.encoded, Base64.NO_WRAP)
        return "-----BEGIN PUBLIC KEY-----\n$base64Key\n-----END PUBLIC KEY-----"
    }

    /**
     * Deletes the key from KeyStore.
     */
    fun deleteKey() {
        if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.deleteEntry(KEY_ALIAS)
        }
    }
}
