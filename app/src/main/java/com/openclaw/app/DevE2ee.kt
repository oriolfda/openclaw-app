package com.openclaw.app

import android.util.Base64
import org.json.JSONObject
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object DevE2ee {
    data class EncryptResult(
        val envelope: JSONObject,
        val responseKey: ByteArray,
    )

    fun encryptForBridge(plaintext: String, bridgePublicKeyB64: String, ad: String): EncryptResult {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(256)
        val eph = kpg.generateKeyPair()

        val bridgePub = decodePublicKey(bridgePublicKeyB64)
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(eph.private)
        ka.doPhase(bridgePub, true)
        val shared = ka.generateSecret()

        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val key = hkdfSha256(shared, salt, "openclaw-e2ee-v1", 32)
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(ad.toByteArray(Charsets.UTF_8))
        val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val env = JSONObject().apply {
            put("v", 1)
            put("alg", "ecdh-p256-aesgcm-v1")
            put("ephemeralPub", Base64.encodeToString(eph.public.encoded, Base64.NO_WRAP))
            put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            put("ciphertext", Base64.encodeToString(ct, Base64.NO_WRAP))
            put("ad", ad)
            put("expectEncryptedReply", true)
        }

        return EncryptResult(env, key)
    }

    fun decryptWithKey(key: ByteArray, env: JSONObject): String {
        val ad = env.optString("ad", "")
        val iv = Base64.decode(env.optString("iv", ""), Base64.DEFAULT)
        val ct = Base64.decode(env.optString("ciphertext", ""), Base64.DEFAULT)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(ad.toByteArray(Charsets.UTF_8))
        val pt = cipher.doFinal(ct)
        return String(pt, Charsets.UTF_8)
    }

    private fun decodePublicKey(b64: String): PublicKey {
        val bytes = Base64.decode(b64, Base64.DEFAULT)
        val kf = KeyFactory.getInstance("EC")
        return kf.generatePublic(X509EncodedKeySpec(bytes))
    }

    private fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: String, len: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = mac.doFinal(ikm)

        var t = ByteArray(0)
        val okm = ByteArray(len)
        var offset = 0
        var counter = 1

        while (offset < len) {
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            mac.update(t)
            mac.update(info.toByteArray(Charsets.UTF_8))
            mac.update(counter.toByte())
            t = mac.doFinal()
            val c = minOf(t.size, len - offset)
            System.arraycopy(t, 0, okm, offset, c)
            offset += c
            counter++
        }

        return okm
    }
}
