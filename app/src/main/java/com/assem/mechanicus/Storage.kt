package com.assem.mechanicus

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

// Save plate photos into the phone's public Gallery (Pictures/MECHANICUS) so
// the user sees them in their own gallery app, not only inside MECHANICUS.
object Gallery {
    private fun target(): Uri =
        if (Build.VERSION.SDK_INT >= 29) MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    private fun values(name: String): ContentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, name)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MECHANICUS")
    }

    fun saveFile(ctx: Context, src: File, name: String): String? = try {
        val uri = ctx.contentResolver.insert(target(), values(name)) ?: return null
        val ok = ctx.contentResolver.openOutputStream(uri)?.use { out -> src.inputStream().use { it.copyTo(out) } }
        if (ok == null) null else uri.toString()
    } catch (_: Exception) { null }

    fun saveUri(ctx: Context, src: Uri, name: String): String? = try {
        val uri = ctx.contentResolver.insert(target(), values(name)) ?: return null
        val ok = ctx.contentResolver.openInputStream(src)?.use { input ->
            ctx.contentResolver.openOutputStream(uri)?.use { input.copyTo(it) }
        }
        if (ok == null) null else uri.toString()
    } catch (_: Exception) { null }
}

// Whole-database backup: export a .zip the user can keep or send, and restore
// it later as input.
object Backup {
    fun export(ctx: Context, store: Store, subject: String) {
        val stamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
        val zip = File(ctx.cacheDir, "MECHANICUS-backup-$stamp.zip")
        ZipOutputStream(zip.outputStream()).use { zos ->
            for (f in store.dbFiles()) {
                val rel = if (f.parentFile?.name == "data") "data/${f.name}" else f.name
                zos.putNextEntry(ZipEntry(rel))
                f.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", zip)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, subject)
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(chooser)
    }

    fun restore(ctx: Context, store: Store, uri: Uri): Int {
        var n = 0
        ctx.contentResolver.openInputStream(uri)?.use { ins ->
            ZipInputStream(ins).use { zis ->
                var e = zis.nextEntry
                while (e != null) {
                    val name = e.name
                    if (!e.isDirectory && name.endsWith(".db")) {
                        val dest = if (name.contains('/')) File(store.dataDir(), name.substringAfterLast('/')) else File(store.root(), name)
                        dest.parentFile?.mkdirs()
                        dest.outputStream().use { zis.copyTo(it) }
                        n++
                    }
                    zis.closeEntry()
                    e = zis.nextEntry
                }
            }
        }
        return n
    }
}
