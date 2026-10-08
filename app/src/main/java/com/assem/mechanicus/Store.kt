package com.assem.mechanicus

import android.content.Context
import android.database.sqlite.SQLiteDatabase
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
        get() = prefs.getBoolean("dark", false)
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

    fun root(): File {
        val base = if (useExternal) (ctx.getExternalFilesDir(null) ?: ctx.filesDir) else ctx.filesDir
        val r = File(base, "MECHANICUS")
        if (!r.exists()) r.mkdirs()
        return r
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
        return db
    }

    private fun openMonth(key: String): SQLiteDatabase {
        val db = SQLiteDatabase.openOrCreateDatabase(File(dataDir(), "$key.db"), null)
        db.execSQL("CREATE TABLE IF NOT EXISTS cars(id TEXT PRIMARY KEY, plate TEXT, engine TEXT, odometer TEXT, make TEXT, model TEXT, delivery TEXT, customer TEXT, phone TEXT, worker TEXT, intake TEXT, status TEXT, created INTEGER, updated INTEGER, photo TEXT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS parts(id INTEGER PRIMARY KEY AUTOINCREMENT, carId TEXT, name TEXT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS payments(id INTEGER PRIMARY KEY AUTOINCREMENT, carId TEXT, amount REAL, stage TEXT, uid INTEGER, uname TEXT, ts INTEGER)")
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
        val yc = mdb.rawQuery("SELECT id,carId,amount,stage,uname,ts FROM payments WHERE carId=? ORDER BY ts", arrayOf(id))
        while (yc.moveToNext()) pays.add(Payment(yc.getLong(0), yc.getString(1) ?: "", yc.getDouble(2), yc.getString(3) ?: "", yc.getString(4) ?: "", yc.getLong(5)))
        yc.close()
        mdb.close()

        return car.copy(parts = parts, payments = pays)
    }

    fun addPayment(car: Car, amount: Double, stage: String, userName: String) {
        val db = openMonth(car.monthKey)
        db.execSQL("INSERT INTO payments(carId,amount,stage,uid,uname,ts) VALUES(?,?,?,?,?,?)",
            arrayOf<Any?>(car.id, amount, stage, activeUserId, userName, System.currentTimeMillis()))
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
