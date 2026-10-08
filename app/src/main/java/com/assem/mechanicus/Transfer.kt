package com.assem.mechanicus

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Export a session/car to a portable ".mech" file that another MECHANICUS
// install can open and import.
object Transfer {
    const val MIME = "application/vnd.mechanicus+json"
    const val EXT = "mech"

    private fun startOfDay(): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun carJson(c: Car): JSONObject {
        val o = JSONObject()
        o.put("id", c.id)
        o.put("plate", c.plate)
        o.put("engine", c.engine)
        o.put("odometer", c.odometer)
        o.put("make", c.make)
        o.put("model", c.model)
        o.put("deliveryDate", c.deliveryDate)
        o.put("customer", c.customer)
        o.put("phone", c.phone)
        o.put("worker", c.worker)
        o.put("intake", c.intake)
        o.put("status", c.status)
        o.put("createdAt", c.createdAt)
        o.put("parts", JSONArray(c.parts))
        val pays = JSONArray()
        for (p in c.payments) {
            pays.put(JSONObject().apply {
                put("amount", p.amount); put("stage", p.stage); put("userName", p.userName); put("ts", p.ts)
            })
        }
        o.put("payments", pays)
        return o
    }

    private fun build(cars: List<Car>, type: String, userName: String): String {
        val root = JSONObject()
        root.put("app", "MECHANICUS")
        root.put("format", 1)
        root.put("type", type)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("exportedBy", userName)
        val arr = JSONArray()
        for (c in cars) arr.put(carJson(c))
        root.put("cars", arr)
        return root.toString(1)
    }

    private fun write(context: Context, text: String, type: String): File {
        val stamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
        val f = File(context.cacheDir, "MECHANICUS-$type-$stamp.$EXT")
        f.writeText(text, Charsets.UTF_8)
        return f
    }

    private fun share(context: Context, file: File, subject: String) {
        val uri: Uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = MIME
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, subject)
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun shareCars(context: Context, cars: List<Car>, type: String, L: Lang, userName: String) {
        val subject = L.s("جلسة MECHANICUS", "MECHANICUS session")
        val f = write(context, build(cars, type, userName), type)
        share(context, f, subject)
    }

    fun exportToday(context: Context, store: Store, L: Lang, userName: String) {
        val today = startOfDay()
        val cars = store.listCars("all", "").filter { it.updated >= today }.mapNotNull { store.carDetail(it.id) }
        shareCars(context, cars, "today", L, userName)
    }

    fun exportAll(context: Context, store: Store, L: Lang, userName: String) {
        val cars = store.listCars("all", "").mapNotNull { store.carDetail(it.id) }
        shareCars(context, cars, "all", L, userName)
    }

    fun exportOne(context: Context, car: Car, L: Lang, userName: String) {
        shareCars(context, listOf(car), "car", L, userName)
    }

    fun importUri(context: Context, store: Store, uri: Uri, userName: String): Int {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw IllegalStateException("empty")
        return importJson(store, text, userName)
    }

    fun importJson(store: Store, text: String, userName: String): Int {
        val root = JSONObject(text)
        if (root.optString("app") != "MECHANICUS") throw IllegalArgumentException("not_mechanicus")
        val arr = root.optJSONArray("cars") ?: throw IllegalArgumentException("no_cars")
        var n = 0
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val parts = ArrayList<String>()
            o.optJSONArray("parts")?.let { for (j in 0 until it.length()) parts.add(it.optString(j)) }
            val car = Car(
                id = o.optString("id").ifBlank { store.newId() },
                plate = o.optString("plate"),
                engine = o.optString("engine"),
                odometer = o.optString("odometer"),
                make = o.optString("make"),
                model = o.optString("model"),
                deliveryDate = o.optString("deliveryDate"),
                customer = o.optString("customer"),
                phone = o.optString("phone"),
                worker = o.optString("worker"),
                intake = o.optString("intake"),
                status = o.optString("status").ifBlank { Status.WORK },
                monthKey = "",
                createdAt = o.optLong("createdAt", 0L),
                updatedAt = 0L,
                photo = null,
                parts = parts,
            )
            val saved = store.saveCar(car)
            o.optJSONArray("payments")?.let { pays ->
                for (j in 0 until pays.length()) {
                    val p = pays.getJSONObject(j)
                    val amt = p.optDouble("amount", 0.0)
                    if (amt > 0) store.addPayment(saved, amt, p.optString("stage").ifBlank { "on" }, p.optString("userName").ifBlank { userName })
                }
            }
            n++
        }
        return n
    }
}
