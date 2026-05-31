package com.example

import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object MeshCrypto {
    private const val EC_ALGORITHM = "EC"
    private const val AES_ALGORITHM = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BIT = 128
    private const val IV_LENGTH_BYTE = 12

    private var myKeyPair: java.security.KeyPair? = null

    fun init() {
        if (myKeyPair != null) return
        try {
            val kpg = KeyPairGenerator.getInstance(EC_ALGORITHM)
            kpg.initialize(256)
            myKeyPair = kpg.generateKeyPair()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getMyPublicKeyString(): String {
        init()
        return Base64.encodeToString(myKeyPair!!.public.encoded, Base64.NO_WRAP)
    }

    fun encrypt(plaintext: String, peerPublicKeyStr: String): Pair<String, String> {
        init()
        val secretKey = deriveKey(myKeyPair!!.private, peerPublicKeyStr)
        val cipher = Cipher.getInstance(AES_ALGORITHM)
        val iv = ByteArray(IV_LENGTH_BYTE)
        SecureRandom().nextBytes(iv)
        val spec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
        val cipherText = cipher.doFinal(plaintext.toByteArray())
        return Pair(Base64.encodeToString(cipherText, Base64.NO_WRAP), Base64.encodeToString(iv, Base64.NO_WRAP))
    }

    fun decrypt(cipherTextStr: String, ivStr: String, peerPublicKeyStr: String): String {
        init()
        val secretKey = deriveKey(myKeyPair!!.private, peerPublicKeyStr)
        val cipher = Cipher.getInstance(AES_ALGORITHM)
        val iv = Base64.decode(ivStr, Base64.NO_WRAP)
        val cipherText = Base64.decode(cipherTextStr, Base64.NO_WRAP)
        val spec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        return String(cipher.doFinal(cipherText))
    }

    private fun deriveKey(privateKey: PrivateKey, peerPublicKeyStr: String): SecretKeySpec {
        val pubKeyBytes = Base64.decode(peerPublicKeyStr, Base64.NO_WRAP)
        val keyFactory = KeyFactory.getInstance(EC_ALGORITHM)
        val x509KeySpec = X509EncodedKeySpec(pubKeyBytes)
        val peerPublicKey = keyFactory.generatePublic(x509KeySpec)

        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(privateKey)
        keyAgreement.doPhase(peerPublicKey, true)
        val sharedSecret = keyAgreement.generateSecret()
        return SecretKeySpec(sharedSecret, 0, 32, "AES")
    }
}
