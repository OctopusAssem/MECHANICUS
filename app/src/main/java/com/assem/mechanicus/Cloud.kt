package com.assem.mechanicus

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// Sync transport: the shop's own MySQL database on engautoagency.com,
// reached through a small PHP API on the cPanel host. The API key ships
// inside the app, so no Google account or company key is needed.
object Cloud {
    const val BASE = "https://engautoagency.com/api/app/"
    const val KEY = "mk_5f3a9d7c2e814b6fa0c3d8e51b7a49c2e6d01483"

    private fun open(action: String): HttpURLConnection {
        val c = URL(BASE + "?action=" + action).openConnection() as HttpURLConnection
        c.connectTimeout = 20000
        c.readTimeout = 45000
        c.setRequestProperty("X-Api-Key", KEY)
        c.setRequestProperty("Accept", "application/json")
        return c
    }

    private fun body(c: HttpURLConnection): String {
        val s = if (c.responseCode in 200..299) c.inputStream else c.errorStream
        val text = s?.bufferedReader()?.use { it.readText() } ?: ""
        if (c.responseCode !in 200..299) throw RuntimeException("HTTP ${c.responseCode} $text")
        return text
    }

    // Returns the stored snapshot object (may be empty on a fresh database).
    fun pull(): JSONObject {
        val c = open("pull")
        body(c).let { return JSONObject(it).optJSONObject("snapshot") ?: JSONObject() }
    }

    // Sends this device's merged snapshot; the server merges it again and
    // returns the current cloud snapshot.
    fun push(snapshot: JSONObject): JSONObject {
        val c = open("push")
        c.requestMethod = "POST"
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        c.outputStream.use { it.write(snapshot.toString().toByteArray(Charsets.UTF_8)) }
        return JSONObject(body(c)).optJSONObject("snapshot") ?: JSONObject()
    }

    // Uploads a car photo into the customer's own storage folder on the site
    // (served back to him by email on the client page).
    fun uploadPhoto(email: String, carId: String, name: String, bytes: ByteArray, ts: Long) {
        val b = JSONObject()
        b.put("email", email)
        b.put("carId", carId)
        b.put("name", name)
        b.put("ts", ts)
        b.put("data", android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))
        val c = open("upload_photo")
        c.requestMethod = "POST"
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        c.outputStream.use { it.write(b.toString().toByteArray(Charsets.UTF_8)) }
        body(c)
    }
}