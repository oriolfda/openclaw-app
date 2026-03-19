package com.openclaw.app

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

class E2eeKeyManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("openclaw_app_e2ee", Context.MODE_PRIVATE)

    data class LocalBundle(
        val deviceId: String,
        val identityKey: String,
        val signedPreKey: String,
        val signedPreKeySignature: String,
        val oneTimePreKeys: List<String>,
    )

    fun ensureLocalBundle(): LocalBundle {
        val deviceId = prefs.getString("device_id", null) ?: randomB64(16).also { prefs.edit().putString("device_id", it).apply() }
        val identity = prefs.getString("identity_key", null) ?: randomB64(32).also { prefs.edit().putString("identity_key", it).apply() }
        val spk = prefs.getString("signed_prekey", null) ?: randomB64(32).also { prefs.edit().putString("signed_prekey", it).apply() }
        val sig = prefs.getString("signed_prekey_sig", null) ?: signLike(identity, spk).also { prefs.edit().putString("signed_prekey_sig", it).apply() }

        val otkRaw = prefs.getString("otk_list", null)
        val otks = if (otkRaw.isNullOrBlank()) {
            List(10) { randomB64(32) }.also {
                prefs.edit().putString("otk_list", JSONObject().put("keys", it).toString()).apply()
            }
        } else {
            try {
                val arr = JSONObject(otkRaw).optJSONArray("keys")
                if (arr == null) List(10) { randomB64(32) } else List(arr.length()) { i -> arr.optString(i) }
            } catch (_: Exception) {
                List(10) { randomB64(32) }
            }
        }

        return LocalBundle(deviceId, identity, spk, sig, otks)
    }

    private fun randomB64(size: Int): String {
        val b = ByteArray(size)
        SecureRandom().nextBytes(b)
        return Base64.encodeToString(b, Base64.NO_WRAP)
    }

    private fun signLike(identity: String, payload: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest("$identity::$payload".toByteArray())
        return Base64.encodeToString(digest, Base64.NO_WRAP)
    }
}
