package com.assem.mechanicus

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
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

// Make a distinctive MECHANICUS photo: honest but modest size (max 1600px)
// plus a small red "MECHANICUS" tag stamped at the bottom, kept in the app
// folder (so a backup carries it) and copied to the phone Gallery.
object PhotoStore {
    const val PREFIX = "MECHANICUS_plate_"

    fun build(ctx: Context, store: Store, src: File, name: String): File? = try {
        val raw = BitmapFactory.decodeFile(src.absolutePath) ?: return null
        write(ctx, store, raw, name)
    } catch (_: Exception) { null }

    fun buildUri(ctx: Context, store: Store, uri: Uri, name: String): File? = try {
        val raw = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
        write(ctx, store, raw, name)
    } catch (_: Exception) { null }

    private fun write(ctx: Context, store: Store, raw: Bitmap, name: String): File {
        val bmp = stamp(raw)
        val out = File(store.photosDir(), name)
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        bmp.recycle()
        return out
    }

    private fun stamp(src: Bitmap): Bitmap {
        val max = 1600
        val biggest = maxOf(src.width, src.height)
        val bmp = if (biggest > max) {
            val s = max.toFloat() / biggest
            Bitmap.createScaledBitmap(src, (src.width * s).toInt(), (src.height * s).toInt(), true)
        } else src.copy(Bitmap.Config.ARGB_8888, true) ?: src

        val c = Canvas(bmp)
        val w = bmp.width.toFloat()
        val h = bmp.height.toFloat()
        val strip = maxOf(46f, h * 0.085f)

        val stripPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        stripPaint.color = android.graphics.Color.argb(130, 8, 4, 5)
        c.drawRect(0f, h - strip, w, h, stripPaint)

        val text = "MECHANICUS"
        val tp = Paint(Paint.ANTI_ALIAS_FLAG)
        tp.color = android.graphics.Color.WHITE
        tp.typeface = Typeface.DEFAULT_BOLD
        tp.textSize = strip * 0.40f
        val tw = tp.measureText(text)
        val pillH = strip * 0.62f
        val pad = strip * 0.22f
        val left = strip * 0.28f
        val top = h - strip + (strip - pillH) / 2f
        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        pillPaint.color = android.graphics.Color.parseColor("#DC2626")
        c.drawRoundRect(RectF(left, top, left + tw + pad * 2, top + pillH), pillH / 2f, pillH / 2f, pillPaint)
        c.drawText(text, left + pad, top + pillH / 2f + tp.textSize * 0.35f, tp)
        return bmp
    }
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
            store.photosDir().listFiles()?.forEach { f ->
                zos.putNextEntry(ZipEntry("photos/${f.name}"))
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
                    if (!e.isDirectory && name.startsWith("photos/")) {
                        val fn = name.substringAfterLast('/')
                        if (fn.isNotBlank()) {
                            val dest = File(store.photosDir(), fn)
                            dest.outputStream().use { zis.copyTo(it) }
                            try { Gallery.saveFile(ctx, dest, fn) } catch (_: Exception) {}
                            n++
                        }
                    } else if (!e.isDirectory && name.endsWith(".db")) {
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
