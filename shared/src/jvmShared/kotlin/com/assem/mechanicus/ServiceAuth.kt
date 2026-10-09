package com.assem.mechanicus

import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

// Silent Drive auth for the whole shop, gated by the admin's private Company Key.
// The app only carries an AES-256-GCM encrypted copy of the Google service
// account (Platform.syncBlob). It is useless until the admin enters the Company
// Key once on a device; that key derives the AES key (PBKDF2) and decrypts the
// credential, which is cached privately so nothing else is needed again.
object ServiceAuth {
    const val FOLDER_ID = "1InZEX11KAVJhoS7NSt_C6vpH7egTgz_i"
    const val SCOPE = "https://www.googleapis.com/auth/drive"
    private const val PREFS = "mechanicus_creds"
    private const val KEY_JSON = "sa_json"
    private const val ITER = 120_000

    @Volatile private var cachedJson: String? = null
    @Volatile private var cachedTok: String? = null
    @Volatile private var expiresAt: Long = 0L

    private fun prefs() = Platform.prefs(PREFS)

    private fun sa(): JSONObject? {
        cachedJson?.let { return try { JSONObject(it) } catch (_: Exception) { null } }
        val j = prefs().getString(KEY_JSON, "")
        if (j.isBlank()) return null
        return try { JSONObject(j).also { cachedJson = j } } catch (_: Exception) { null }
    }

    fun isConfigured(): Boolean = sa() != null

    fun email(): String = sa()?.optString("client_email").orEmpty()

    fun provision(key: String): Boolean {
        if (key.isBlank()) return false
        val blobB64 = Platform.syncBlob
        if (blobB64.isBlank()) return false
        return try {
            val all = Base64.getMimeDecoder().decode(blobB64.trim())
            if (all.size < 29) return false
            val salt = all.copyOfRange(0, 16)
            val iv = all.copyOfRange(16, 28)
            val ct = all.copyOfRange(28, all.size)
            val dk = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(PBEKeySpec(key.toCharArray(), salt, ITER, 256)).encoded
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(dk, "AES"), GCMParameterSpec(128, iv))
            val plain = String(cipher.doFinal(ct), Charsets.UTF_8)
            val o = JSONObject(plain)
            if (o.optString("client_email").isBlank() || o.optString("private_key").isBlank()) return false
            prefs().putString(KEY_JSON, plain)
            cachedJson = plain
            cachedTok = null
            expiresAt = 0L
            true
        } catch (_: Exception) {
            false
        }
    }

    fun clear() {
        prefs().putString(KEY_JSON, "")
        cachedJson = null; cachedTok = null; expiresAt = 0L
    }

    @Synchronized
    fun token(): String? {
        val now = System.currentTimeMillis()
        cachedTok?.let { if (now < expiresAt - 60_000) return it }
        val conf = sa() ?: return null
        return try {
            val jwt = signJwt(conf.optString("client_email"), conf.optString("private_key"), SCOPE)
            exchange(jwt)
        } catch (_: Exception) { null }
    }

    private fun signJwt(email: String, pem: String, scope: String): String {
        val now = System.currentTimeMillis() / 1000
        val header = "{\"alg\":\"RS256\",\"typ\":\"JWT\"}"
        val claim = JSONObject()
            .put("iss", email)
            .put("scope", scope)
            .put("aud", "https://oauth2.googleapis.com/token")
            .put("iat", now)
            .put("exp", now + 3600)
            .toString()
        val input = b64url(header.toByteArray(Charsets.UTF_8)) + "." + b64url(claim.toByteArray(Charsets.UTF_8))
        val sig = Signature.getInstance("SHA256withRSA").apply {
            initSign(parseKey(pem))
            update(input.toByteArray(Charsets.UTF_8))
        }.sign()
        return input + "." + b64url(sig)
    }

    private fun parseKey(pem: String) =
        KeyFactory.getInstance("RSA").generatePrivate(
            PKCS8EncodedKeySpec(
                Base64.getMimeDecoder().decode(
                    pem.replace("-----BEGIN PRIVATE KEY-----", "")
                        .replace("-----END PRIVATE KEY-----", "")
                        .replace("\\n", "").replace("\n", "").replace("\r", "").replace(" ", "")
                )
            )
        )

    private fun exchange(jwt: String): String? {
        val conn = URL("https://oauth2.googleapis.com/token").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 20000
        conn.readTimeout = 30000
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        val body = "grant_type=" + enc("urn:ietf:params:oauth:grant-type:jwt-bearer") + "&assertion=" + enc(jwt)
        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
        val text = (if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() } ?: return null
        val o = JSONObject(text)
        val tok = o.optString("access_token").ifBlank { return null }
        cachedTok = tok
        expiresAt = System.currentTimeMillis() + o.optLong("expires_in", 3600L) * 1000
        return tok
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    private fun b64url(b: ByteArray) =
        Base64.getUrlEncoder().withoutPadding().encodeToString(b)
}
