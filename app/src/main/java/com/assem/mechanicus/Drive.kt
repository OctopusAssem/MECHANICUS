package com.assem.mechanicus

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object GDrive {
    const val SCOPE = "https://www.googleapis.com/auth/drive.file"
    const val FOLDER = "MECHANICUS"
    private const val API = "https://www.googleapis.com/drive/v3/files"
    private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3/files"
    const val SHA1 = "77:96:93:23:38:83:DF:04:43:64:FF:E4:7A:37:9F:FA:A6:75:91:97"
    const val PKG = "com.assem.mechanicus"

    fun options(): GoogleSignInOptions =
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(SCOPE))
            .build()

    fun client(ctx: Context) = GoogleSignIn.getClient(ctx, options())

    fun account(ctx: Context): GoogleSignInAccount? = GoogleSignIn.getLastSignedInAccount(ctx)

    fun token(ctx: Context, acc: GoogleSignInAccount): String? {
        val a: Account = acc.account ?: return null
        return try {
            GoogleAuthUtil.getToken(ctx, a, "oauth2:$SCOPE")
        } catch (e: Exception) {
            null
        }
    }

    fun signOut(ctx: Context) {
        try { client(ctx).signOut() } catch (_: Exception) {}
    }

    // ---------------- REST ----------------
    private fun open(url: String, token: String, method: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.setRequestProperty("Authorization", "Bearer $token")
        c.connectTimeout = 20000
        c.readTimeout = 40000
        return c
    }

    private fun readAll(c: HttpURLConnection): String {
        val s = (if (c.responseCode in 200..299) c.inputStream else c.errorStream)
        return s?.bufferedReader()?.use { it.readText() } ?: ""
    }

    private fun parseTime(s: String?): Long {
        if (s.isNullOrBlank()) return 0L
        for (p in listOf("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'")) {
            try {
                val f = SimpleDateFormat(p, Locale.US)
                f.timeZone = TimeZone.getTimeZone("UTC")
                return f.parse(s)!!.time
            } catch (_: Exception) {}
        }
        return 0L
    }

    fun findOrCreateFolder(token: String): String {
        val q = URLEncoder.encode("name='$FOLDER' and mimeType='application/vnd.google-apps.folder' and trashed=false", "UTF-8")
        val c = open("$API?q=$q&fields=files(id,name)&spaces=drive", token, "GET")
        val body = readAll(c)
        if (c.responseCode !in 200..299) throw RuntimeException("Drive list failed: ${c.responseCode} $body")
        val files = JSONObject(body).optJSONArray("files")
        if (files != null && files.length() > 0) return files.getJSONObject(0).getString("id")
        val c2 = open("$API?fields=id", token, "POST")
        c2.doOutput = true
        c2.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        c2.outputStream.use { it.write("{\"name\":\"$FOLDER\",\"mimeType\":\"application/vnd.google-apps.folder\"}".toByteArray()) }
        val b2 = readAll(c2)
        if (c2.responseCode !in 200..299) throw RuntimeException("Drive create folder failed: ${c2.responseCode} $b2")
        return JSONObject(b2).getString("id")
    }

    fun listFiles(token: String, folderId: String): MutableMap<String, Pair<String, Long>> {
        val q = URLEncoder.encode("'$folderId' in parents and trashed=false", "UTF-8")
        val c = open("$API?q=$q&fields=files(id,name,modifiedTime)&pageSize=200", token, "GET")
        val body = readAll(c)
        if (c.responseCode !in 200..299) throw RuntimeException("Drive list children failed: ${c.responseCode} $body")
        val out = mutableMapOf<String, Pair<String, Long>>()
        val files = JSONObject(body).optJSONArray("files") ?: return out
        for (i in 0 until files.length()) {
            val o = files.getJSONObject(i)
            out[o.getString("name")] = Pair(o.getString("id"), parseTime(o.optString("modifiedTime")))
        }
        return out
    }

    fun uploadFile(token: String, folderId: String, file: File, existingId: String?): String {
        val boundary = "mech" + System.nanoTime()
        val meta = JSONObject()
        meta.put("name", file.name)
        if (existingId == null) meta.put("parents", org.json.JSONArray().put(folderId))
        val pre = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n--$boundary\r\nContent-Type: application/octet-stream\r\n\r\n"
        val post = "\r\n--$boundary--\r\n"
        val url = if (existingId == null) "$UPLOAD?uploadType=multipart&fields=id" else "$UPLOAD/$existingId?uploadType=multipart&fields=id"
        val method = if (existingId == null) "POST" else "PATCH"
        val c = open(url, token, method)
        c.doOutput = true
        c.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
        c.outputStream.use { out: OutputStream ->
            out.write(pre.toByteArray(Charsets.UTF_8))
            file.inputStream().use { it.copyTo(out) }
            out.write(post.toByteArray(Charsets.UTF_8))
        }
        val body = readAll(c)
        if (c.responseCode !in 200..299) throw RuntimeException("Drive upload failed (${file.name}): ${c.responseCode} $body")
        return JSONObject(body).getString("id")
    }

    fun downloadFile(token: String, fileId: String, dest: File) {
        val c = open("$API/$fileId?alt=media", token, "GET")
        if (c.responseCode !in 200..299) throw RuntimeException("Drive download failed: ${c.responseCode}")
        dest.parentFile?.mkdirs()
        c.inputStream.use { input -> dest.outputStream().use { input.copyTo(it) } }
    }

    fun sync(ctx: Context, token: String, store: Store): SyncResult {
        val fid = findOrCreateFolder(token)
        val remote = listFiles(token, fid)
        var up = 0
        var down = 0
        val locals = store.dbFiles()
        for (f in locals) {
            val r = remote[f.name]
            if (r == null) {
                uploadFile(token, fid, f, null); up++
            } else if (f.lastModified() > r.second + 2000) {
                uploadFile(token, fid, f, r.first); up++
            }
        }
        for ((name, meta) in remote) {
            val local = locals.find { it.name == name }
            if (local == null) {
                val dest = if (name == "app.db") File(store.root(), name) else File(store.dataDir(), name)
                downloadFile(token, meta.first, dest); down++
            } else if (meta.second > local.lastModified() + 2000) {
                downloadFile(token, meta.first, local); down++
            }
        }
        store.lastSync = System.currentTimeMillis()
        return SyncResult(up, down, "")
    }
}

data class SyncResult(val uploaded: Int, val downloaded: Int, val error: String)

object Report {
    fun today(store: Store, isAr: Boolean): String {
        val sb = StringBuilder()
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        sb.append(if (isAr) "MECHANICUS — تقرير جلسة يوم " else "MECHANICUS — session report ")
        sb.append(day).append("\n")
        sb.append("========================\n\n")

        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0); cal.set(java.util.Calendar.MILLISECOND, 0)
        val today = cal.timeInMillis

        val cars = store.listCars("all", "").filter { it.updated >= today }
        sb.append(if (isAr) "عدد العربيات: " else "Cars: ").append(cars.size).append("\n\n")
        for (c in cars) {
            sb.append("• ").append(c.plate).append(" — ").append(c.customer).append("\n")
            sb.append("   ").append(if (isAr) "الحالة: " else "status: ").append(c.status)
                .append(" | ").append(if (isAr) "مدفوع: " else "paid: ").append(String.format(Locale.US, "%,.0f", c.pay)).append("\n")
        }

        val pays = store.recentPayments(200).filter { it.ts >= today }
        val total = pays.sumOf { it.amount }
        sb.append("\n").append(if (isAr) "دفعات اليوم: " else "Today's payments: ").append(pays.size).append("\n")
        for (p in pays) {
            sb.append("   ").append(p.plate).append("  ").append(String.format(Locale.US, "%,.0f", p.amount))
                .append("  (").append(p.stage).append(", ").append(p.userName).append(")\n")
        }
        sb.append("\n").append(if (isAr) "إجمالي تحصيل اليوم: " else "Total collected: ")
            .append(String.format(Locale.US, "%,.0f", total)).append(if (isAr) " ج.م" else " EGP")
        return sb.toString()
    }

    fun share(ctx: Context, text: String, subject: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(send, subject)
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(chooser)
    }
}
