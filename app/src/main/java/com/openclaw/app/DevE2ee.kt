package com.openclaw.app

import android.util.Base64
import org.json.JSONObject
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
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

    fun encryptForBridge(plaintext: String, bridgePublicKeyB64: String, ad: String, otkId: String? = null, counter: Int = 0): EncryptResult {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(256)
        val eph = kpg.generateKeyPair()

        val bridgePub = decodePublicKey(bridgePublicKeyB64)
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(eph.private)
        ka.doPhase(bridgePub, true)
        val shared = ka.generateSecret()

        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        var baseKey = hkdfSha256(shared, salt, "openclaw-e2ee-v1", 32)

        val ratchet = kpg.generateKeyPair()
        val kaRatchet = KeyAgreement.getInstance("ECDH")
        kaRatchet.init(ratchet.private)
        kaRatchet.doPhase(bridgePub, true)
        val ratchetShared = kaRatchet.generateSecret()
        val ratchetPubBytes = ratchet.public.encoded
        val ratchetSalt = MessageDigest.getInstance("SHA-256").digest(ratchetPubBytes).copyOfRange(0, 16)
        baseKey = hkdfSha256(baseKey + ratchetShared, ratchetSalt, "openclaw-ratchet-step-v1", 32)

        val key = deriveMessageKey(baseKey, counter, "c2s")
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(ad.toByteArray(Charsets.UTF_8))
        val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val env = JSONObject().apply {
            put("v", 1)
            put("alg", "ecdh-p256-aesgcm-v1")
            put("ephemeralPub", Base64.encodeToString(eph.public.encoded, Base64.NO_WRAP))
            put("ratchetPub", Base64.encodeToString(ratchetPubBytes, Base64.NO_WRAP))
            put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            put("ciphertext", Base64.encodeToString(ct, Base64.NO_WRAP))
            put("ad", ad)
            put("counter", counter)
            if (!otkId.isNullOrBlank()) put("otkId", otkId)
            put("expectEncryptedReply", true)
        }

        return EncryptResult(env, baseKey)
    }

    fun decryptWithKey(baseKey: ByteArray, env: JSONObject): String {
        val ad = env.optString("ad", "")
        val iv = Base64.decode(env.optString("iv", ""), Base64.DEFAULT)
        val ct = Base64.decode(env.optString("ciphertext", ""), Base64.DEFAULT)
        val counter = env.optInt("counter", 0)
        val key = deriveMessageKey(baseKey, counter, "s2c")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(ad.toByteArray(Charsets.UTF_8))
        val pt = cipher.doFinal(ct)
        return String(pt, Charsets.UTF_8)
    }

    fun encryptAttachment(base64Data: String, baseKey: ByteArray, name: String, mime: String, ad: String, counter: Int): JSONObject {
        val raw = Base64.decode(base64Data, Base64.DEFAULT)
        val key = deriveMessageKey(baseKey, counter, "att")
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(ad.toByteArray(Charsets.UTF_8))
        val ct = cipher.doFinal(raw)
        return JSONObject().apply {
            put("name", name)
            put("mime", mime)
            put("alg", "aes-gcm-v1")
            put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            put("ciphertext", Base64.encodeToString(ct, Base64.NO_WRAP))
            put("ad", ad)
            put("counter", counter)
        }
    }

    fun verifySignedPreKey(identitySignPubB64: String, signedPreKeyPubB64: String, sigB64: String): Boolean {
        return try {
            val pubBytes = Base64.decode(identitySignPubB64, Base64.DEFAULT)
            val kf = KeyFactory.getInstance("Ed25519")
            val pub = kf.generatePublic(X509EncodedKeySpec(pubBytes))
            val sig = Signature.getInstance("Ed25519")
            sig.initVerify(pub)
            sig.update(Base64.decode(signedPreKeyPubB64, Base64.DEFAULT))
            sig.verify(Base64.decode(sigB64, Base64.DEFAULT))
        } catch (_: Exception) {
            false
        }
    }

    private fun decodePublicKey(b64: String): PublicKey {
        val bytes = Base64.decode(b64, Base64.DEFAULT)
        val kf = KeyFactory.getInstance("EC")
        return kf.generatePublic(X509EncodedKeySpec(bytes))
    }

    private fun deriveMessageKey(baseKey: ByteArray, counter: Int, label: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(baseKey, "HmacSHA256"))
        val d = mac.doFinal("$label:$counter".toByteArray(Charsets.UTF_8))
        return d.copyOfRange(0, 32)
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
