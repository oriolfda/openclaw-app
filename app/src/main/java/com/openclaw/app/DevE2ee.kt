package com.openclaw.app

import android.util.Base64
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

object DevE2ee {
    private fun keyFromToken(token: String): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest((if (token.isBlank()) "openclaw-dev-e2ee" else token).toByteArray())
    }

    private fun keystream(key: ByteArray, nonce: ByteArray, n: Int): ByteArray {
        val out = ByteArray(n)
        var offset = 0
        var counter = 0
        while (offset < n) {
            val h = MessageDigest.getInstance("SHA-256")
            h.update(key)
            h.update(nonce)
            h.update(byteArrayOf((counter ushr 24).toByte(), (counter ushr 16).toByte(), (counter ushr 8).toByte(), counter.toByte()))
            val block = h.digest()
            val len = minOf(block.size, n - offset)
            System.arraycopy(block, 0, out, offset, len)
            offset += len
            counter++
        }
        return out
    }

    fun encrypt(token: String, plaintext: String, ad: String): JSONObject {
        val key = keyFromToken(token)
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val pt = plaintext.toByteArray(Charsets.UTF_8)
        val ks = keystream(key, nonce, pt.size)
        val ct = ByteArray(pt.size) { i -> (pt[i].toInt() xor ks[i].toInt()).toByte() }

        val macMd = MessageDigest.getInstance("SHA-256")
        macMd.update(key)
        macMd.update(nonce)
        macMd.update(ct)
        macMd.update(ad.toByteArray(Charsets.UTF_8))
        val mac = macMd.digest().copyOfRange(0, 16)

        return JSONObject().apply {
            put("v", 1)
            put("alg", "dev-sha256-stream-v1")
            put("nonce", Base64.encodeToString(nonce, Base64.NO_WRAP))
            put("ciphertext", Base64.encodeToString(ct, Base64.NO_WRAP))
            put("mac", Base64.encodeToString(mac, Base64.NO_WRAP))
            put("ad", ad)
            put("expectEncryptedReply", true)
        }
    }

    fun decrypt(token: String, env: JSONObject): String {
        val key = keyFromToken(token)
        val nonce = Base64.decode(env.optString("nonce", ""), Base64.DEFAULT)
        val ct = Base64.decode(env.optString("ciphertext", ""), Base64.DEFAULT)
        val mac = Base64.decode(env.optString("mac", ""), Base64.DEFAULT)
        val ad = env.optString("ad", "")

        val macMd = MessageDigest.getInstance("SHA-256")
        macMd.update(key)
        macMd.update(nonce)
        macMd.update(ct)
        macMd.update(ad.toByteArray(Charsets.UTF_8))
        val calc = macMd.digest().copyOfRange(0, 16)
        if (!calc.contentEquals(mac)) throw IllegalStateException("invalid_mac")

        val ks = keystream(key, nonce, ct.size)
        val pt = ByteArray(ct.size) { i -> (ct[i].toInt() xor ks[i].toInt()).toByte() }
        return String(pt, Charsets.UTF_8)
    }
}
