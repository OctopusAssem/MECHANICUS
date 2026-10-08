package com.assem.mechanicus

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec

// Silent Drive auth for the whole shop. The app carries a service account that
// the company Drive folder (MECHANICUS) is shared with as Editor, so any phone
// can sync without signing in to any Google account. The credential is injected
// at build time (GitHub secret -> BuildConfig.SA_JSON_B64), never committed.
object ServiceAuth {
    // Shared company folder (owned by eng.agency.auto@gmail.com). Knowing the id
    // grants nothing on its own; only the service account has access.
    const val FOLDER_ID = "1InZEX11KAVJhoS7NSt_C6vpH7egTgz_i"
    const val SCOPE = "https://www.googleapis.com/auth/drive"

    @Volatile private var cached: String? = null
    @Volatile private var expiresAt: Long = 0L

    private val sa: JSONObject? by lazy {
        val b64 = try { BuildConfig.SA_JSON_B64 } catch (_: Throwable) { "" }
        if (b64.isBlank()) return@lazy null
        try {
            val json = String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
            JSONObject(json)
        } catch (_: Exception) { null }
    }

    fun isConfigured(): Boolean = sa != null

    fun email(): String = sa?.optString("client_email").orEmpty()

    @Synchronized
    fun token(ctx: Context): String? {
        val conf = sa ?: return null
        val now = System.currentTimeMillis()
        cached?.let { if (now < expiresAt - 60_000) return it }
        return try {
            val jwt = signJwt(conf.optString("client_email"), conf.optString("private_key"), SCOPE)
            val tok = exchange(jwt) ?: return null
            tok
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
        val key = parseKey(pem)
        val sig = Signature.getInstance("SHA256withRSA").apply {
            initSign(key)
            update(input.toByteArray(Charsets.UTF_8))
        }.sign()
        return input + "." + b64url(sig)
    }

    private fun parseKey(pem: String) =
        KeyFactory.getInstance("RSA").generatePrivate(
            PKCS8EncodedKeySpec(
                Base64.decode(
                    pem.replace("-----BEGIN PRIVATE KEY-----", "")
                        .replace("-----END PRIVATE KEY-----", "")
                        .replace("\\n", "")
                        .replace("\n", "")
                        .replace("\r", "")
                        .replace(" ", ""),
                    Base64.DEFAULT
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
        val ttl = o.optLong("expires_in", 3600L)
        cached = tok
        expiresAt = System.currentTimeMillis() + ttl * 1000
        return tok
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    private fun b64url(b: ByteArray) =
        Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}
