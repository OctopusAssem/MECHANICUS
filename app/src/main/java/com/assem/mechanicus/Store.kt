package com.assem.mechanicus

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

class Store(private val ctx: Context) {

    private val prefs = ctx.getSharedPreferences("mechanicus", Context.MODE_PRIVATE)

    var lang: String
        get() = prefs.getString("lang", "ar")!!
        set(v) = prefs.edit().putString("lang", v).apply()

    var useExternal: Boolean
        get() = prefs.getBoolean("external", false)
        set(v) = prefs.edit().putBoolean("external", v).apply()

    var dark: Boolean
        get() = prefs.getBoolean("dark", true)
        set(v) = prefs.edit().putBoolean("dark", v).apply()

    var driveEmail: String
        get() = prefs.getString("drive", "eng.agency.auto@gmail.com")!!
        set(v) = prefs.edit().putString("drive", v).apply()

    var activeUserId: Long
        get() = prefs.getLong("uid", -1L)
        set(v) = prefs.edit().putLong("uid", v).apply()

    var activeUserName: String
        get() = prefs.getString("uname", "")!!
        set(v) = prefs.edit().putString("uname", v).apply()

    var driveConnected: Boolean
        get() = prefs.getBoolean("drive_connected", false)
        set(v) = prefs.edit().putBoolean("drive_connected", v).apply()

    var lastSync: Long
        get() = prefs.getLong("last_sync", 0L)
        set(v) = prefs.edit().putLong("last_sync", v).apply()

    // True when local data changed and still needs to reach Drive.
    var syncPending: Boolean
        get() = prefs.getBoolean("sync_pending", false)
        set(v) = prefs.edit().putBoolean("sync_pending", v).apply()

    // The owner is the only one allowed to really delete (on Drive too).
    var ownerName: String
        get() = prefs.getString("owner_name", "عاصم حسين")!!
        set(v) = prefs.edit().putString("owner_name", v).apply()

    // Hidden admin sign-in (عاصم حسين + 5555) grants absolute permissions.
    var adminMode: Boolean
        get() = prefs.getBoolean("admin_mode", false)
        set(v) = prefs.edit().putBoolean("admin_mode", v).apply()

    fun checkAdmin(userName: String, pin: String): Boolean =
        normName(userName) == normName("عاصم حسين") && pin.trim() == "5555"

    fun root(): File = rootFor(useExternal)

    fun rootFor(external: Boolean): File {
        val r = File(baseDir(external), "MECHANICUS")
        if (!r.exists()) r.mkdirs()
        return r
    }

    private fun externalBase(): File? {
        val dirs = ctx.getExternalFilesDirs(null)
        for (d in dirs) {
            if (d == null) continue
            val removable = try { Environment.isExternalStorageRemovable(d) } catch (_: Exception) { false }
            if (removable) return d
        }
        return dirs.firstOrNull { it != null }
    }

    private fun baseDir(external: Boolean): File {
        if (external) externalBase()?.let { return it }
        return ctx.filesDir
    }

    // Move existing data to the requested storage so the switch is real.
    fun switchStorage(external: Boolean): Boolean {
        if (external == useExternal) return true
        if (external && externalBase() == null) return false
        val from = rootFor(useExternal)
        val to = rootFor(external)
        try {
            if (from.absolutePath != to.absolutePath) copyRecursive(from, to)
            useExternal = external
            return true
        } catch (e: Exception) {
            return false
        }
    }

    private fun copyRecursive(src: File, dst: File) {
        if (!src.exists()) { dst.mkdirs(); return }
        if (src.isDirectory) {
            if (!dst.exists()) dst.mkdirs()
            src.listFiles()?.forEach { copyRecursive(it, File(dst, it.name)) }
        } else {
            dst.parentFile?.mkdirs()
            src.inputStream().use { i -> dst.outputStream().use { i.copyTo(it) } }
        }
    }

    fun dataDir(): File {
        val d = File(root(), "data")
        if (!d.exists()) d.mkdirs()
        return d
    }

    fun photosDir(): File {
        val d = File(root(), "photos")
        if (!d.exists()) d.mkdirs()
        return d
    }

    fun monthKey(ts: Long): String = SimpleDateFormat("yyyy-MM", Locale.US).format(Date(ts))

    private fun openCentral(): SQLiteDatabase {
        val db = SQLiteDatabase.openOrCreateDatabase(File(root(), "app.db"), null)
        db.execSQL("CREATE TABLE IF NOT EXISTS users(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, pin TEXT, role TEXT, active INTEGER)")
        db.execSQL("CREATE TABLE IF NOT EXISTS logs(id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER, uname TEXT, action TEXT, plate TEXT, detail TEXT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS car_index(id TEXT PRIMARY KEY, plate TEXT, customer TEXT, phone TEXT, make TEXT, status TEXT, month TEXT, created INTEGER, updated INTEGER, pay REAL, photo TEXT, adate TEXT)")
        try { db.execSQL("ALTER TABLE car_index ADD COLUMN adate TEXT") } catch (_: Exception) {}
        db.execSQL("CREATE TABLE IF NOT EXISTS tombstones(id TEXT PRIMARY KEY, updated INTEGER)")
        db.execSQL("CREATE TABLE IF NOT EXISTS hidden(id TEXT PRIMARY KEY)")
        return db
    }

    private fun openMonth(key: String): SQLiteDatabase {
        val db = SQLiteDatabase.openOrCreateDatabase(File(dataDir(), "$key.db"), null)
        db.execSQL("CREATE TABLE IF NOT EXISTS cars(id TEXT PRIMARY KEY, plate TEXT, engine TEXT, odometer TEXT, make TEXT, model TEXT, delivery TEXT, customer TEXT, phone TEXT, worker TEXT, intake TEXT, status TEXT, created INTEGER, updated INTEGER, photo TEXT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS parts(id INTEGER PRIMARY KEY AUTOINCREMENT, carId TEXT, name TEXT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS payments(id INTEGER PRIMARY KEY AUTOINCREMENT, carId TEXT, amount REAL, stage TEXT, uid INTEGER, uname TEXT, ts INTEGER)")
        try { db.execSQL("ALTER TABLE payments ADD COLUMN pid TEXT") } catch (_: Exception) {}
        return db
    }

    // ---------- users ----------
    fun users(): List<User> {
        val db = openCentral()
        val c = db.rawQuery("SELECT id,name,pin,role,active FROM users ORDER BY id", null)
        val out = ArrayList<User>()
        while (c.moveToNext()) out.add(User(c.getLong(0), c.getString(1) ?: "", c.getString(2) ?: "", c.getString(3) ?: "tech", c.getInt(4) == 1))
        c.close(); db.close()
        return out
    }

    fun checkLogin(name: String, pin: String): User? {
        val db = openCentral()
        val c = db.rawQuery("SELECT id,name,pin,role,active FROM users WHERE name=? AND pin=? AND active=1", arrayOf(name, pin))
        val u = if (c.moveToFirst()) User(c.getLong(0), c.getString(1) ?: "", c.getString(2) ?: "", c.getString(3) ?: "tech", true) else null
        c.close(); db.close()
        return u
    }

    fun addUser(name: String, pin: String, role: String) {
        val db = openCentral()
        db.execSQL("INSERT INTO users(name,pin,role,active) VALUES(?,?,?,1)", arrayOf(name, pin, role))
        db.close()
    }

    fun setPin(id: Long, pin: String) {
        val db = openCentral()
        db.execSQL("UPDATE users SET pin=? WHERE id=?", arrayOf<Any?>(pin, id))
        db.close()
    }

    fun deleteUser(id: Long) {
        val db = openCentral()
        db.execSQL("DELETE FROM users WHERE id=?", arrayOf(id))
        db.close()
    }

    // ---------- logs ----------
    fun addLog(userName: String, action: String, plate: String, detail: String) {
        val db = openCentral()
        db.execSQL("INSERT INTO logs(ts,uname,action,plate,detail) VALUES(?,?,?,?,?)",
            arrayOf<Any?>(System.currentTimeMillis(), userName, action, plate, detail))
        db.close()
    }

    fun logs(): List<LogEntry> {
        val db = openCentral()
        val c = db.rawQuery("SELECT id,ts,uname,action,plate,detail FROM logs ORDER BY ts DESC LIMIT 500", null)
        val out = ArrayList<LogEntry>()
        while (c.moveToNext()) out.add(LogEntry(c.getLong(0), c.getLong(1), c.getString(2) ?: "", c.getString(3) ?: "", c.getString(4) ?: "", c.getString(5) ?: ""))
        c.close(); db.close()
        return out
    }

    // ---------- cars ----------
    fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(16)

    private fun totalPaid(key: String, carId: String): Double {
        val db = openMonth(key)
        val c = db.rawQuery("SELECT IFNULL(SUM(amount),0) FROM payments WHERE carId=?", arrayOf(carId))
        val v = if (c.moveToFirst()) c.getDouble(0) else 0.0
        c.close(); db.close()
        return v
    }

    fun saveCar(car: Car): Car {
        val now = System.currentTimeMillis()
        val mk = if (car.monthKey.isBlank()) monthKey(now) else car.monthKey
        val created = if (car.createdAt == 0L) now else car.createdAt
        val db = openMonth(mk)
        db.execSQL(
            "INSERT OR REPLACE INTO cars(id,plate,engine,odometer,make,model,delivery,customer,phone,worker,intake,status,created,updated,photo) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            arrayOf<Any?>(car.id, car.plate, car.engine, car.odometer, car.make, car.model, car.deliveryDate, car.customer, car.phone, car.worker, car.intake, car.status, created, now, car.photo)
        )
        db.execSQL("DELETE FROM parts WHERE carId=?", arrayOf(car.id))
        for (p in car.parts) if (p.isNotBlank()) db.execSQL("INSERT INTO parts(carId,name) VALUES(?,?)", arrayOf(car.id, p.trim()))
        db.close()
        val pay = totalPaid(mk, car.id)
        val cdb = openCentral()
        cdb.execSQL(
            "INSERT OR REPLACE INTO car_index(id,plate,customer,phone,make,status,month,created,updated,pay,photo,adate) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
            arrayOf<Any?>(car.id, car.plate, car.customer, car.phone, car.make, car.status, mk, created, now, pay, car.photo, car.deliveryDate)
        )
        cdb.execSQL("DELETE FROM tombstones WHERE id=?", arrayOf<Any?>(car.id))
        cdb.close()
        syncPending = true
        return car.copy(monthKey = mk, createdAt = created, updatedAt = now)
    }

    fun listCars(filter: String, query: String): List<IndexRow> {
        val db = openCentral()
        val sb = StringBuilder("SELECT id,plate,customer,phone,status,month,updated,pay,photo,adate FROM car_index WHERE 1=1")
        val args = ArrayList<String>()
        if (filter != "all") { sb.append(" AND status=?"); args.add(filter) }
        if (query.isNotBlank()) {
            sb.append(" AND (plate LIKE ? OR customer LIKE ? OR phone LIKE ? OR make LIKE ? OR id LIKE ? OR adate LIKE ?)")
            val q = "%$query%"
            repeat(6) { args.add(q) }
        }
        sb.append(" ORDER BY updated DESC")
        val c = db.rawQuery(sb.toString(), args.toTypedArray())
        val out = ArrayList<IndexRow>()
        while (c.moveToNext()) out.add(
            IndexRow(c.getString(0) ?: "", c.getString(1) ?: "", c.getString(2) ?: "", c.getString(3) ?: "",
                c.getString(4) ?: Status.WORK, c.getString(5) ?: "", c.getLong(6), c.getDouble(7), c.getString(8),
                c.getString(9) ?: "")
        )
        c.close(); db.close()
        return out
    }

    fun carDetail(id: String): Car? {
        val cdb = openCentral()
        val c = cdb.rawQuery("SELECT id,plate,customer,phone,status,month,created,updated,photo FROM car_index WHERE id=?", arrayOf(id))
        if (!c.moveToFirst()) { c.close(); cdb.close(); return null }
        val month = c.getString(5) ?: return null
        val status = c.getString(4) ?: Status.WORK
        val created = c.getLong(6)
        val updated = c.getLong(7)
        val idxPhoto = c.getString(8)
        c.close(); cdb.close()

        val mdb = openMonth(month)
        val cc = mdb.rawQuery("SELECT plate,engine,odometer,make,model,delivery,customer,phone,worker,intake,status,photo FROM cars WHERE id=?", arrayOf(id))
        if (!cc.moveToFirst()) { cc.close(); mdb.close(); return null }
        val car = Car(
            id = id,
            plate = cc.getString(0) ?: "",
            engine = cc.getString(1) ?: "",
            odometer = cc.getString(2) ?: "",
            make = cc.getString(3) ?: "",
            model = cc.getString(4) ?: "",
            deliveryDate = cc.getString(5) ?: "",
            customer = cc.getString(6) ?: "",
            phone = cc.getString(7) ?: "",
            worker = cc.getString(8) ?: "",
            intake = cc.getString(9) ?: "",
            status = cc.getString(10) ?: status,
            monthKey = month,
            createdAt = created,
            updatedAt = updated,
            photo = cc.getString(11) ?: idxPhoto,
        )
        cc.close()

        val parts = ArrayList<String>()
        val pc = mdb.rawQuery("SELECT name FROM parts WHERE carId=? ORDER BY id", arrayOf(id))
        while (pc.moveToNext()) parts.add(pc.getString(0) ?: "")
        pc.close()

        val pays = ArrayList<Payment>()
        val yc = mdb.rawQuery("SELECT id,carId,amount,stage,uname,ts,pid FROM payments WHERE carId=? ORDER BY ts", arrayOf(id))
        while (yc.moveToNext()) pays.add(Payment(yc.getLong(0), yc.getString(1) ?: "", yc.getDouble(2), yc.getString(3) ?: "", yc.getString(4) ?: "", yc.getLong(5), yc.getString(6) ?: ""))
        yc.close()
        mdb.close()

        return car.copy(parts = parts, payments = pays)
    }

    fun addPayment(car: Car, amount: Double, stage: String, userName: String) {
        val db = openMonth(car.monthKey)
        db.execSQL("INSERT INTO payments(carId,amount,stage,uid,uname,ts,pid) VALUES(?,?,?,?,?,?,?)",
            arrayOf<Any?>(car.id, amount, stage, activeUserId, userName, System.currentTimeMillis(), newId()))
        db.close()
        val pay = totalPaid(car.monthKey, car.id)
        val cdb = openCentral()
        cdb.execSQL("UPDATE car_index SET pay=?, updated=? WHERE id=?", arrayOf<Any?>(pay, System.currentTimeMillis(), car.id))
        cdb.close()
        syncPending = true
    }

    fun setStatus(car: Car, status: String) {
        val db = openMonth(car.monthKey)
        db.execSQL("UPDATE cars SET status=?, updated=? WHERE id=?", arrayOf<Any?>(status, System.currentTimeMillis(), car.id))
        db.close()
        val cdb = openCentral()
        cdb.execSQL("UPDATE car_index SET status=?, updated=? WHERE id=?", arrayOf<Any?>(status, System.currentTimeMillis(), car.id))
        cdb.close()
        syncPending = true
    }

    fun deleteCar(car: Car) {
        val db = openMonth(car.monthKey)
        db.execSQL("DELETE FROM cars WHERE id=?", arrayOf(car.id))
        db.execSQL("DELETE FROM parts WHERE carId=?", arrayOf(car.id))
        db.execSQL("DELETE FROM payments WHERE carId=?", arrayOf(car.id))
        db.close()
        val cdb = openCentral()
        cdb.execSQL("DELETE FROM car_index WHERE id=?", arrayOf(car.id))
        cdb.execSQL("INSERT OR REPLACE INTO tombstones(id,updated) VALUES(?,?)", arrayOf<Any?>(car.id, System.currentTimeMillis()))
        cdb.close()
        syncPending = true
    }

    fun recentPayments(limit: Int = 20): List<PayRow> {
        val mk = monthKey(System.currentTimeMillis())
        val mdb = openMonth(mk)
        val c = mdb.rawQuery(
            "SELECT p.amount,p.stage,p.uname,p.ts,c.plate FROM payments p LEFT JOIN cars c ON c.id=p.carId ORDER BY p.ts DESC LIMIT ?",
            arrayOf(limit.toString())
        )
        val out = ArrayList<PayRow>()
        while (c.moveToNext()) out.add(PayRow(c.getString(4) ?: "", c.getDouble(0), c.getString(1) ?: "", c.getString(2) ?: "", c.getLong(3)))
        c.close(); mdb.close()
        return out
    }

    // ---------- stats ----------
    fun stats(): Stats {
        val cdb = openCentral()
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val today = cal.timeInMillis

        fun count(sql: String, args: Array<String>): Int {
            val c = cdb.rawQuery(sql, args); val v = if (c.moveToFirst()) c.getInt(0) else 0; c.close(); return v
        }
        fun sum(sql: String, args: Array<String>): Double {
            val c = cdb.rawQuery(sql, args); val v = if (c.moveToFirst()) c.getDouble(0) else 0.0; c.close(); return v
        }
        val carsToday = count("SELECT COUNT(*) FROM car_index WHERE created>=?", arrayOf(today.toString()))
        val inWork = count("SELECT COUNT(*) FROM car_index WHERE status=?", arrayOf(Status.WORK))
        val pending = sum("SELECT IFNULL(SUM(pay),0) FROM car_index WHERE status=?", arrayOf(Status.WORK))
        cdb.close()

        val mk = monthKey(System.currentTimeMillis())
        val mdb = openMonth(mk)
        val todayIncome = run {
            val c = mdb.rawQuery("SELECT IFNULL(SUM(amount),0) FROM payments WHERE ts>=?", arrayOf(today.toString()))
            val v = if (c.moveToFirst()) c.getDouble(0) else 0.0; c.close(); v
        }
        val monthIncome = run {
            val c = mdb.rawQuery("SELECT IFNULL(SUM(amount),0) FROM payments", null)
            val v = if (c.moveToFirst()) c.getDouble(0) else 0.0; c.close(); v
        }
        mdb.close()
        return Stats(carsToday, inWork, todayIncome, monthIncome, pending)
    }

    // ---------- maintenance ----------
    fun dbFiles(): List<File> {
        val list = ArrayList<File>()
        File(root(), "app.db").takeIf { it.exists() }?.let { list.add(it) }
        dataDir().listFiles()?.filter { it.name.endsWith(".db") }?.let { list.addAll(it) }
        return list
    }

    // ---------- sync helpers ----------
    fun allCarsFull(): List<Car> = carIndexIds().mapNotNull { carDetail(it) }

    private fun carIndexIds(): List<String> {
        val db = openCentral()
        val c = db.rawQuery("SELECT id FROM car_index", null)
        val out = ArrayList<String>()
        while (c.moveToNext()) c.getString(0)?.let { out.add(it) }
        c.close(); db.close()
        return out
    }

    fun tombstones(): Map<String, Long> {
        val db = openCentral()
        val c = db.rawQuery("SELECT id,updated FROM tombstones", null)
        val out = HashMap<String, Long>()
        while (c.moveToNext()) out[c.getString(0) ?: ""] = c.getLong(1)
        c.close(); db.close()
        return out
    }

    fun replaceTombstones(map: Map<String, Long>) {
        val db = openCentral()
        db.execSQL("DELETE FROM tombstones")
        for ((k, v) in map) if (k.isNotBlank()) db.execSQL("INSERT OR REPLACE INTO tombstones(id,updated) VALUES(?,?)", arrayOf<Any?>(k, v))
        db.close()
    }

    // ---------- owner & local hide ----------
    // Only the owner (عاصم حسين) may delete for real (on Google too). Everyone
    // else can only hide a car on their own phone; it stays on Drive.
    fun isOwner(userName: String): Boolean {
        val n = normName(userName)
        if (n.isBlank()) return false
        if (n == normName(ownerName)) return true
        if (n == normName("عاصم حسين") || n == normName("Assem Hussein")) return true
        return try { users().any { normName(it.name) == n && it.role == "owner" } } catch (_: Exception) { false }
    }

    private fun normName(s: String) = s.trim().replace("\\s+".toRegex(), " ").lowercase()

    fun isActiveOwner(): Boolean = adminMode || isOwner(activeUserName)

    fun hiddenIds(): MutableSet<String> {
        val db = openCentral()
        val c = db.rawQuery("SELECT id FROM hidden", null)
        val out = HashSet<String>()
        while (c.moveToNext()) c.getString(0)?.let { out.add(it) }
        c.close(); db.close()
        return out
    }

    fun hideLocally(id: String) {
        val db = openCentral()
        db.execSQL("INSERT OR REPLACE INTO hidden(id) VALUES(?)", arrayOf<Any?>(id))
        db.close()
    }

    fun unhide(id: String) {
        val db = openCentral()
        db.execSQL("DELETE FROM hidden WHERE id=?", arrayOf<Any?>(id))
        db.close()
    }

    // Removes a car from this phone only (keeps it on Drive), for non-owners.
    fun hideCar(car: Car) {
        val db = openMonth(car.monthKey)
        db.execSQL("DELETE FROM cars WHERE id=?", arrayOf(car.id))
        db.execSQL("DELETE FROM parts WHERE carId=?", arrayOf(car.id))
        db.execSQL("DELETE FROM payments WHERE carId=?", arrayOf(car.id))
        db.close()
        val cdb = openCentral()
        cdb.execSQL("DELETE FROM car_index WHERE id=?", arrayOf(car.id))
        cdb.execSQL("INSERT OR REPLACE INTO hidden(id) VALUES(?)", arrayOf<Any?>(car.id))
        cdb.close()
    }

    fun upsertCarFull(car: Car) {
        val mk = car.monthKey.ifBlank { monthKey(if (car.createdAt > 0) car.createdAt else System.currentTimeMillis()) }
        val created = if (car.createdAt > 0) car.createdAt else System.currentTimeMillis()
        val updated = if (car.updatedAt > 0) car.updatedAt else System.currentTimeMillis()
        val mdb = openMonth(mk)
        mdb.execSQL(
            "INSERT OR REPLACE INTO cars(id,plate,engine,odometer,make,model,delivery,customer,phone,worker,intake,status,created,updated,photo) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            arrayOf<Any?>(car.id, car.plate, car.engine, car.odometer, car.make, car.model, car.deliveryDate, car.customer, car.phone, car.worker, car.intake, car.status, created, updated, car.photo)
        )
        mdb.execSQL("DELETE FROM parts WHERE carId=?", arrayOf(car.id))
        for (p in car.parts) if (p.isNotBlank()) mdb.execSQL("INSERT INTO parts(carId,name) VALUES(?,?)", arrayOf(car.id, p.trim()))
        mdb.execSQL("DELETE FROM payments WHERE carId=?", arrayOf(car.id))
        for (p in car.payments) mdb.execSQL(
            "INSERT INTO payments(carId,amount,stage,uid,uname,ts,pid) VALUES(?,?,?,?,?,?,?)",
            arrayOf<Any?>(car.id, p.amount, p.stage, activeUserId, p.userName, p.ts, p.pid.ifBlank { newId() })
        )
        mdb.close()
        val pay = totalPaid(mk, car.id)
        val cdb = openCentral()
        cdb.execSQL(
            "INSERT OR REPLACE INTO car_index(id,plate,customer,phone,make,status,month,created,updated,pay,photo,adate) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
            arrayOf<Any?>(car.id, car.plate, car.customer, car.phone, car.make, car.status, mk, created, updated, pay, car.photo, car.deliveryDate)
        )
        cdb.execSQL("DELETE FROM tombstones WHERE id=?", arrayOf<Any?>(car.id))
        cdb.close()
    }

    fun removeCarLocal(id: String, month: String) {
        val db = openMonth(month)
        db.execSQL("DELETE FROM cars WHERE id=?", arrayOf(id))
        db.execSQL("DELETE FROM parts WHERE carId=?", arrayOf(id))
        db.execSQL("DELETE FROM payments WHERE carId=?", arrayOf(id))
        db.close()
        val cdb = openCentral()
        cdb.execSQL("DELETE FROM car_index WHERE id=?", arrayOf(id))
        cdb.close()
    }

    fun totalSize(): Long = dbFiles().sumOf { it.length() } + photosDir().walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun compact(): String {
        var saved = 0L
        for (f in dbFiles()) {
            val before = f.length()
            val db = SQLiteDatabase.openOrCreateDatabase(f, null)
            db.execSQL("VACUUM")
            db.close()
            saved += (before - f.length()).coerceAtLeast(0)
        }
        val cdb = openCentral()
        cdb.execSQL("DELETE FROM logs WHERE ts < ?", arrayOf(System.currentTimeMillis() - 365L * 24 * 3600 * 1000))
        cdb.execSQL("VACUUM")
        cdb.close()
        return saved.toString()
    }
}
