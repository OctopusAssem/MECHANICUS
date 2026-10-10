package com.assem.mechanicus

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// Real, mergeable sync. The cloud keeps ONE JSON snapshot (sync.json) inside the
// "MECHANICUS" Drive folder. Every sync downloads it, merges it with local data
// record-by-record (cars by id, payments by pid, deletions by tombstone) and
// uploads the union.
object SyncEngine {
    const val FILE = "sync.json"
    const val LOCKED = "DB_LOCKED"

    data class Counts(var pushed: Int = 0, var pulled: Int = 0, var deleted: Int = 0)

    fun run(token: String, store: Store): SyncResult {
        // Locate the shared snapshot by NAME. We deliberately do not look the
        // folder up by id: for a My-Drive folder owned by another account and
        // only inherited-shared to our service account, Drive returns 404 on
        // folder-id lookups (and on "in parents" queries), even though the
        // files inside are readable and writable by their own ids. Name search
        // plus download/update-by-id is reliable in that situation.
        val meta = GDrive.findFile(token, FILE)
        val cache = File(store.root(), FILE)

        if (meta != null && store.lastSync > 0 && !store.syncPending && meta.second <= store.lastSync + 1000) {
            return SyncResult(0, 0, "")
        }

        val remote = if (meta != null) {
            try {
                GDrive.downloadFile(token, meta.first, cache)
                parse(cache.readText(Charsets.UTF_8))
            } catch (_: Exception) {
                Snap()
            }
        } else Snap()

        // The database owner can protect the shared database. Once protected, a
        // device that has not been authorized with the admin password must not
        // read or write it: refuse here, before any data is applied or uploaded.
        if (remote.prot) {
            if (!store.dbAuthorized) {
                store.dbProtected = true
                return SyncResult(0, 0, LOCKED)
            }
            store.dbProtected = true
        }

        // One-time recovery: an older MECHANICUS version synced raw .db files.
        if (meta == null && store.lastSync == 0L) {
            try { seedFromCloudDb(token, store.driveFolderId.ifBlank { ServiceAuth.FOLDER_ID }, store) } catch (_: Exception) {}
        }

        val local = localSnap(store)
        val (merged, counts) = merge(store, local, remote)
        applyMerged(store, merged, local)

        val text = merged.toString()
        cache.writeText(text, Charsets.UTF_8)
        val folder = store.driveFolderId.ifBlank { ServiceAuth.FOLDER_ID }
        GDrive.uploadFile(token, folder, cache, meta?.first)

        store.lastSync = System.currentTimeMillis()
        store.syncPending = false
        return SyncResult(counts.pushed, counts.pulled + counts.deleted, "")
    }

    // ---------------- snapshots ----------------
    class Snap {
        val cars = LinkedHashMap<String, JSONObject>()
        val tomb = HashMap<String, Long>()
        val comments = LinkedHashMap<String, JSONObject>()
        var prot: Boolean = false
    }

    private fun localSnap(store: Store): Snap {
        val s = Snap()
        for (c in store.allCarsFull()) s.cars[c.id] = carJson(c)
        for ((k, v) in store.tombstones()) s.tomb[k] = v
        for (c in store.comments()) if (c.pid.isNotBlank()) s.comments[c.pid] = commentJson(c)
        return s
    }

    private fun commentJson(c: Comment): JSONObject {
        val o = JSONObject()
        o.put("pid", c.pid)
        o.put("ts", c.ts)
        o.put("user", c.userName)
        o.put("body", c.body)
        return o
    }

    private fun parse(text: String): Snap {
        val s = Snap()
        val root = JSONObject(text)
        if (root.optString("app") != "MECHANICUS") return s
        s.prot = root.optBoolean("protected", false)
        root.optJSONArray("cars")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id")
                if (id.isNotBlank()) s.cars[id] = o
            }
        }
        root.optJSONObject("tombstones")?.let { t ->
            val it = t.keys()
            while (it.hasNext()) {
                val k = it.next()
                s.tomb[k] = t.optLong(k, 0L)
            }
        }
        root.optJSONArray("comments")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val pid = o.optString("pid")
                if (pid.isNotBlank()) s.comments[pid] = o
            }
        }
        return s
    }

    private fun carJson(c: Car): JSONObject {
        val o = JSONObject()
        o.put("id", c.id)
        o.put("plate", c.plate)
        o.put("engine", c.engine)
        o.put("odometer", c.odometer)
        o.put("make", c.make)
        o.put("model", c.model)
        o.put("delivery", c.deliveryDate)
        o.put("customer", c.customer)
        o.put("phone", c.phone)
        o.put("worker", c.worker)
        o.put("intake", c.intake)
        o.put("status", c.status)
        o.put("month", c.monthKey)
        o.put("created", c.createdAt)
        o.put("updated", c.updatedAt)
        o.put("photo", c.photo ?: "")
        o.put("parts", JSONArray(c.parts))
        val pays = JSONArray()
        for (p in c.payments) {
            pays.put(JSONObject().apply {
                put("pid", p.pid)
                put("amount", p.amount)
                put("stage", p.stage)
                put("user", p.userName)
                put("ts", p.ts)
            })
        }
        o.put("payments", pays)
        return o
    }

    // ---------------- merge ----------------
    private fun merge(store: Store, local: Snap, remote: Snap): Pair<JSONObject, Counts> {
        val counts = Counts()
        val ids = LinkedHashSet<String>()
        ids.addAll(local.cars.keys)
        ids.addAll(remote.cars.keys)

        val tombs = HashMap<String, Long>()
        tombs.putAll(local.tomb)
        for ((k, v) in remote.tomb) if ((tombs[k] ?: 0L) < v) tombs[k] = v

        val outCars = JSONArray()
        for (id in ids) {
            val l = local.cars[id]
            val r = remote.cars[id]
            val lu = l?.optLong("updated", 0L) ?: 0L
            val ru = r?.optLong("updated", 0L) ?: 0L
            val death = tombs[id] ?: 0L

            if (death > 0L && death >= maxOf(lu, ru)) {
                counts.deleted++
                outCars.put(JSONObject().put("id", id).put("deleted", true).put("updated", death))
                continue
            }
            if (death > 0L) tombs.remove(id)

            if (l != null && r == null) counts.pushed++
            else if (r != null && l == null) counts.pulled++
            else if (ru > lu) counts.pulled++
            else if (lu > ru) counts.pushed++

            val base = if (r != null && (l == null || ru > lu)) r else l!!
            val other = if (base === r) l else r
            val merged = JSONObject(base.toString())
            merged.put("parts", unionParts(base, other))
            merged.put("payments", unionPayments(base, other))
            merged.put("photo", pickPhoto(store, l, r) ?: "")
            merged.put("updated", maxOf(lu, ru))
            merged.put("created", minOf(l?.optLong("created", 0L) ?: 0L, r?.optLong("created", 0L) ?: 0L)
                .takeIf { it > 0L } ?: (l?.optLong("created", 0L) ?: r?.optLong("created", 0L) ?: 0L))
            outCars.put(merged)
        }

        val root = JSONObject()
        root.put("app", "MECHANICUS")
        root.put("format", 2)
        root.put("protected", store.dbProtected)
        root.put("updatedAt", System.currentTimeMillis())
        root.put("cars", outCars)
        val tombObj = JSONObject()
        for ((k, v) in tombs) tombObj.put(k, v)
        root.put("tombstones", tombObj)
        val allComments = LinkedHashMap<String, JSONObject>()
        allComments.putAll(local.comments)
        for ((k, v) in remote.comments) allComments.putIfAbsent(k, v)
        val commentsArr = JSONArray()
        for ((_, o) in allComments) commentsArr.put(o)
        root.put("comments", commentsArr)
        return root to counts
    }

    private fun unionParts(a: JSONObject?, b: JSONObject?): JSONArray {
        val seen = LinkedHashSet<String>()
        val out = JSONArray()
        for (o in listOf(a, b)) {
            o?.optJSONArray("parts")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val v = arr.optString(i).trim()
                    if (v.isNotEmpty() && seen.add(v)) out.put(v)
                }
            }
        }
        return out
    }

    private fun unionPayments(a: JSONObject?, b: JSONObject?): JSONArray {
        val seen = LinkedHashSet<String>()
        val out = JSONArray()
        for (o in listOf(a, b)) {
            o?.optJSONArray("payments")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val p = arr.optJSONObject(i) ?: continue
                    val amt = p.optDouble("amount", 0.0)
                    if (amt <= 0.0) continue
                    val key = p.optString("pid").ifBlank {
                        "${o.optString("id")}|${p.optLong("ts", 0L)}|$amt|${p.optString("stage")}"
                    }
                    if (seen.add(key)) out.put(p)
                }
            }
        }
        return out
    }

    private fun pickPhoto(store: Store, l: JSONObject?, r: JSONObject?): String? {
        val lp = l?.optString("photo").orEmpty()
        val rp = r?.optString("photo").orEmpty()
        if (photoExists(store, lp)) return lp
        if (photoExists(store, rp)) return rp
        return rp.ifBlank { lp }.ifBlank { null }
    }

    private fun photoExists(store: Store, p: String): Boolean {
        if (p.isBlank() || p.startsWith("content://")) return false
        return try {
            val f = if (p.contains("/")) File(p) else File(store.photosDir(), p)
            f.exists() && f.length() > 0
        } catch (_: Exception) { false }
    }

    private fun applyMerged(store: Store, merged: JSONObject, local: Snap) {
        val arr = merged.optJSONArray("cars") ?: return
        val hidden = store.hiddenIds()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val id = o.optString("id")
            if (id.isBlank()) continue
            if (o.optBoolean("deleted", false)) {
                val month = local.cars[id]?.optString("month").orEmpty()
                    .ifBlank { store.monthKey(System.currentTimeMillis()) }
                store.removeCarLocal(id, month)
                store.unhide(id)
                continue
            }
            if (hidden.contains(id)) continue
            store.upsertCarFull(carFromJson(o))
        }
        val tombs = HashMap<String, Long>()
        merged.optJSONObject("tombstones")?.let { t ->
            val it = t.keys()
            while (it.hasNext()) { val k = it.next(); tombs[k] = t.optLong(k, 0L) }
        }
        store.replaceTombstones(tombs)
        merged.optJSONArray("comments")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val pid = o.optString("pid")
                val body = o.optString("body")
                if (pid.isNotBlank() && body.isNotBlank()) {
                    store.upsertComment(Comment(pid, o.optLong("ts", System.currentTimeMillis()), o.optString("user"), body))
                }
            }
        }
    }

    private fun seedFromCloudDb(token: String, folder: String, store: Store) {
        val files = GDrive.listFiles(token, folder)
        val tmp = File(store.root(), "cloud_seed")
        tmp.mkdirs()
        for ((name, meta) in files) {
            if (!name.endsWith(".db") || name == "app.db") continue
            val month = name.removeSuffix(".db")
            val f = File(tmp, name)
            try {
                GDrive.downloadFile(token, meta.first, f)
                val db = Db.open(f.absolutePath)
                val rows = db.query("SELECT id,plate,engine,odometer,make,model,delivery,customer,phone,worker,intake,status,created,updated,photo FROM cars") { c ->
                    arrayOf<Any?>(
                        c.textOr(0, ""), c.textOr(1, ""), c.textOr(2, ""), c.textOr(3, ""), c.textOr(4, ""),
                        c.textOr(5, ""), c.textOr(6, ""), c.textOr(7, ""), c.textOr(8, ""), c.textOr(9, ""),
                        c.textOr(10, ""), c.textOr(11, Status.WORK), c.long(12), c.long(13), c.textOr(14, ""),
                    )
                }
                db.close()
                for (r in rows) {
                    val id = (r[0] as String)
                    if (id.isBlank()) continue
                    val parts = ArrayList<String>()
                    val pays = ArrayList<Payment>()
                    val car = Car(
                        id = id, plate = r[1] as String, engine = r[2] as String, odometer = r[3] as String,
                        make = r[4] as String, model = r[5] as String, deliveryDate = r[6] as String,
                        customer = r[7] as String, phone = r[8] as String, worker = r[9] as String,
                        intake = r[10] as String, status = r[11] as String, monthKey = month,
                        createdAt = r[12] as Long, updatedAt = r[13] as Long,
                        photo = (r[14] as String).ifBlank { null }, parts = parts, payments = pays,
                    )
                    store.upsertCarFull(car)
                }
            } catch (_: Exception) {
            } finally {
                f.delete()
            }
        }
        tmp.delete()
    }

    private fun carFromJson(o: JSONObject): Car {
        val parts = ArrayList<String>()
        o.optJSONArray("parts")?.let { for (i in 0 until it.length()) parts.add(it.optString(i)) }
        val pays = ArrayList<Payment>()
        o.optJSONArray("payments")?.let { arr ->
            for (i in 0 until arr.length()) {
                val p = arr.optJSONObject(i) ?: continue
                val amt = p.optDouble("amount", 0.0)
                if (amt <= 0.0) continue
                pays.add(Payment(
                    id = 0L,
                    carId = o.optString("id"),
                    amount = amt,
                    stage = p.optString("stage").ifBlank { "on" },
                    userName = p.optString("user"),
                    ts = p.optLong("ts", System.currentTimeMillis()),
                    pid = p.optString("pid"),
                ))
            }
        }
        return Car(
            id = o.optString("id"),
            plate = o.optString("plate"),
            engine = o.optString("engine"),
            odometer = o.optString("odometer"),
            make = o.optString("make"),
            model = o.optString("model"),
            deliveryDate = o.optString("delivery"),
            customer = o.optString("customer"),
            phone = o.optString("phone"),
            worker = o.optString("worker"),
            intake = o.optString("intake"),
            status = o.optString("status").ifBlank { Status.WORK },
            monthKey = o.optString("month"),
            createdAt = o.optLong("created", 0L),
            updatedAt = o.optLong("updated", 0L),
            photo = o.optString("photo").ifBlank { null },
            parts = parts,
            payments = pays,
        )
    }
}
