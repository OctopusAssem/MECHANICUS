package com.assem.mechanicus

import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

// Cross-platform port of the Android data layer. It talks to the shared Db
// (androidx.sqlite bundled) and Platform (Prefs / dataRoot), so the exact same
// database code runs on Android and on the JVM desktop.
class Store {
    private val prefs = Platform.prefs("mechanicus")

    var lang: String
        get() = prefs.getString("lang", "ar")
        set(v) = prefs.putString("lang", v)

    var useExternal: Boolean
        get() = prefs.getBoolean("external", false)
        set(v) = prefs.putBoolean("external", v)

    var dark: Boolean
        get() = prefs.getBoolean("dark", true)
        set(v) = prefs.putBoolean("dark", v)

    var driveEmail: String
        get() = prefs.getString("drive", "eng.agency.auto@gmail.com")
        set(v) = prefs.putString("drive", v)

    var activeUserId: Long
        get() = prefs.getLong("uid", -1L)
        set(v) = prefs.putLong("uid", v)

    var activeUserName: String
        get() = prefs.getString("uname", "")
        set(v) = prefs.putString("uname", v)

    var bioOn: Boolean
        get() = prefs.getBoolean("bio_on", false)
        set(v) = prefs.putBoolean("bio_on", v)

    var bioUserId: Long
        get() = prefs.getLong("bio_uid", -1L)
        set(v) = prefs.putLong("bio_uid", v)

    var driveConnected: Boolean
        get() = prefs.getBoolean("drive_connected", false)
        set(v) = prefs.putBoolean("drive_connected", v)

    var lastSync: Long
        get() = prefs.getLong("last_sync", 0L)
        set(v) = prefs.putLong("last_sync", v)

    var driveFolderId: String
        get() = prefs.getString("drive_folder", "")
        set(v) = prefs.putString("drive_folder", v)

    var syncPending: Boolean
        get() = prefs.getBoolean("sync_pending", false)
        set(v) = prefs.putBoolean("sync_pending", v)

    var ownerName: String
        get() = prefs.getString("owner_name", "عاصم حسين")
        set(v) = prefs.putString("owner_name", v)

    var adminMode: Boolean
        get() = prefs.getBoolean("admin_mode", false)
        set(v) = prefs.putBoolean("admin_mode", v)

    var adminPass: String
        get() = prefs.getString("admin_pass", "5555")
        set(v) = prefs.putString("admin_pass", v.trim())

    var dbProtected: Boolean
        get() = prefs.getBoolean("db_protected", false)
        set(v) = prefs.putBoolean("db_protected", v)

    var dbAuthorized: Boolean
        get() = prefs.getBoolean("db_authorized", false)
        set(v) = prefs.putBoolean("db_authorized", v)

    fun verifyAdminPass(pass: String): Boolean = pass.trim() == adminPass

    fun dbAccessAllowed(): Boolean = !dbProtected || dbAuthorized

    fun checkAdmin(userName: String, pin: String): Boolean =
        normName(userName) == normName("عاصم حسين") && pin.trim() == adminPass

    // ---------- storage roots ----------
    fun root(): File = rootFor(useExternal)

    fun rootFor(external: Boolean): File {
        val base = if (external && Platform.externalRoot.isNotBlank()) Platform.externalRoot else Platform.dataRoot
        val r = File(base, "MECHANICUS")
        if (!r.exists()) r.mkdirs()
        return r
    }

    fun switchStorage(external: Boolean): Boolean {
        if (external == useExternal) return true
        if (external && Platform.externalRoot.isBlank()) return false
        val from = rootFor(useExternal)
        val to = rootFor(external)
        return try {
            if (from.absolutePath != to.absolutePath) copyRecursive(from, to)
            useExternal = external
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun copyRecursive(src: File, dst: File) {
        if (!src.exists()) { dst.mkdirs(); return }
        if (src.isDirectory) {
            if (!dst.exists()) dst.mkdirs()
            src.listFiles()?.forEach { copyRecursive(it, File(dst, it.name)) }
        } else {
            dst.parentFile?.mkdirs()
            src.inputStream().use { i -> dst.outputStream().use { o -> i.copyTo(o) } }
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

    // ---------- db ----------
    private fun openCentral(): Db {
        val db = Db.open(File(root(), "app.db").absolutePath)
        db.exec("CREATE TABLE IF NOT EXISTS users(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, pin TEXT, role TEXT, active INTEGER)")
        db.exec("CREATE TABLE IF NOT EXISTS logs(id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER, uname TEXT, action TEXT, plate TEXT, detail TEXT)")
        db.exec("CREATE TABLE IF NOT EXISTS car_index(id TEXT PRIMARY KEY, plate TEXT, customer TEXT, phone TEXT, make TEXT, status TEXT, month TEXT, created INTEGER, updated INTEGER, pay REAL, photo TEXT, adate TEXT)")
        try { db.exec("ALTER TABLE car_index ADD COLUMN adate TEXT") } catch (_: Exception) {}
        db.exec("CREATE TABLE IF NOT EXISTS tombstones(id TEXT PRIMARY KEY, updated INTEGER)")
        db.exec("CREATE TABLE IF NOT EXISTS hidden(id TEXT PRIMARY KEY)")
        return db
    }

    private fun openMonth(key: String): Db {
        val db = Db.open(File(dataDir(), "$key.db").absolutePath)
        db.exec("CREATE TABLE IF NOT EXISTS cars(id TEXT PRIMARY KEY, plate TEXT, engine TEXT, odometer TEXT, make TEXT, model TEXT, delivery TEXT, customer TEXT, phone TEXT, worker TEXT, intake TEXT, status TEXT, created INTEGER, updated INTEGER, photo TEXT)")
        db.exec("CREATE TABLE IF NOT EXISTS parts(id INTEGER PRIMARY KEY AUTOINCREMENT, carId TEXT, name TEXT)")
        db.exec("CREATE TABLE IF NOT EXISTS payments(id INTEGER PRIMARY KEY AUTOINCREMENT, carId TEXT, amount REAL, stage TEXT, uid INTEGER, uname TEXT, ts INTEGER)")
        try { db.exec("ALTER TABLE payments ADD COLUMN pid TEXT") } catch (_: Exception) {}
        return db
    }

    // ---------- users ----------
    fun users(): List<User> {
        val db = openCentral()
        val out = db.query("SELECT id,name,pin,role,active FROM users ORDER BY id") { c ->
            User(c.long(0), c.textOr(1, ""), c.textOr(2, ""), c.textOr(3, "tech"), c.int(4) == 1)
        }
        db.close()
        return out
    }

    fun checkLogin(name: String, pin: String): User? {
        val db = openCentral()
        val u = db.query(
            "SELECT id,name,pin,role,active FROM users WHERE name=? AND pin=? AND active=1",
            name, pin,
        ) { c -> User(c.long(0), c.textOr(1, ""), c.textOr(2, ""), c.textOr(3, "tech"), true) }.firstOrNull()
        db.close()
        return u
    }

    fun loginOrCreate(name: String, pin: String): User? {
        val n = name.trim()
        if (n.isBlank() || pin.length != 4) return null
        val existing = users().firstOrNull { normName(it.name) == normName(n) }
        if (existing != null) return if (existing.pin == pin) existing else null
        addUser(n, pin, "tech")
        return users().firstOrNull { normName(it.name) == normName(n) }
    }

    fun addUser(name: String, pin: String, role: String) {
        val db = openCentral()
        db.run("INSERT INTO users(name,pin,role,active) VALUES(?,?,?,1)", name, pin, role)
        db.close()
    }

    fun setPin(id: Long, pin: String) {
        val db = openCentral()
        db.run("UPDATE users SET pin=? WHERE id=?", pin, id)
        db.close()
    }

    fun deleteUser(id: Long) {
        val db = openCentral()
        db.run("DELETE FROM users WHERE id=?", id)
        db.close()
    }

    fun visibleUsers(): List<User> = users().filter { u ->
        val n = normName(u.name)
        !(n == normName(ownerName) || n == normName("عاصم حسين") || n == normName("Assem Hussein") || u.role == "owner")
    }

    fun bioUser(): User? = users().firstOrNull { it.id == bioUserId && it.active }

    fun ensureOwnerUser() {
        try {
            val db = openCentral()
            val cnt = db.query("SELECT COUNT(*) FROM users WHERE name=?", "عاصم حسين") { it.int(0) }.firstOrNull() ?: 0
            if (cnt == 0) db.run("INSERT INTO users(name,pin,role,active) VALUES(?,?,?,1)", "عاصم حسين", "5555", "admin")
            db.close()
        } catch (_: Exception) {}
    }

    // ---------- logs ----------
    fun addLog(userName: String, action: String, plate: String, detail: String) {
        val db = openCentral()
        db.run("INSERT INTO logs(ts,uname,action,plate,detail) VALUES(?,?,?,?,?)", System.currentTimeMillis(), userName, action, plate, detail)
        db.close()
    }

    fun logs(): List<LogEntry> {
        val db = openCentral()
        val out = db.query("SELECT id,ts,uname,action,plate,detail FROM logs ORDER BY ts DESC LIMIT 500") { c ->
            LogEntry(c.long(0), c.long(1), c.textOr(2, ""), c.textOr(3, ""), c.textOr(4, ""), c.textOr(5, ""))
        }
        db.close()
        return out
    }

    // ---------- cars ----------
    fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(16)

    private fun totalPaid(key: String, carId: String): Double {
        val db = openMonth(key)
        val v = db.query("SELECT IFNULL(SUM(amount),0) FROM payments WHERE carId=?", carId) { it.double(0) }.firstOrNull() ?: 0.0
        db.close()
        return v
    }

    fun saveCar(car: Car): Car {
        val now = System.currentTimeMillis()
        val mk = if (car.monthKey.isBlank()) monthKey(now) else car.monthKey
        val created = if (car.createdAt == 0L) now else car.createdAt
        val db = openMonth(mk)
        db.run(
            "INSERT OR REPLACE INTO cars(id,plate,engine,odometer,make,model,delivery,customer,phone,worker,intake,status,created,updated,photo) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            car.id, car.plate, car.engine, car.odometer, car.make, car.model, car.deliveryDate, car.customer, car.phone, car.worker, car.intake, car.status, created, now, car.photo,
        )
        db.run("DELETE FROM parts WHERE carId=?", car.id)
        for (p in car.parts) if (p.isNotBlank()) db.run("INSERT INTO parts(carId,name) VALUES(?,?)", car.id, p.trim())
        db.close()
        val pay = totalPaid(mk, car.id)
        val cdb = openCentral()
        cdb.run(
            "INSERT OR REPLACE INTO car_index(id,plate,customer,phone,make,status,month,created,updated,pay,photo,adate) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
            car.id, car.plate, car.customer, car.phone, car.make, car.status, mk, created, now, pay, car.photo, car.deliveryDate,
        )
        cdb.run("DELETE FROM tombstones WHERE id=?", car.id)
        cdb.close()
        syncPending = true
        return car.copy(monthKey = mk, createdAt = created, updatedAt = now)
    }

    fun listCars(filter: String, query: String): List<IndexRow> {
        val sb = StringBuilder("SELECT id,plate,customer,phone,status,month,updated,pay,photo,adate FROM car_index WHERE 1=1")
        val args = ArrayList<Any?>()
        if (filter != "all") { sb.append(" AND status=?"); args.add(filter) }
        if (query.isNotBlank()) {
            sb.append(" AND (plate LIKE ? OR customer LIKE ? OR phone LIKE ? OR make LIKE ? OR id LIKE ? OR adate LIKE ?)")
            val q = "%$query%"
            repeat(6) { args.add(q) }
        }
        sb.append(" ORDER BY updated DESC")
        val db = openCentral()
        val out = db.query(sb.toString(), *args.toTypedArray()) { c ->
            IndexRow(c.textOr(0, ""), c.textOr(1, ""), c.textOr(2, ""), c.textOr(3, ""),
                c.textOr(4, Status.WORK), c.textOr(5, ""), c.long(6), c.double(7), c.textOr(8, ""), c.textOr(9, ""))
        }
        db.close()
        return out
    }

    fun carDetail(id: String): Car? {
        val cdb = openCentral()
        val idx = cdb.query("SELECT id,plate,customer,phone,status,month,created,updated,photo FROM car_index WHERE id=?", id) { c ->
            arrayOf<Any?>(c.textOr(5, ""), c.textOr(4, Status.WORK), c.long(6), c.long(7), c.textOr(8, ""))
        }.firstOrNull() ?: run { cdb.close(); return null }
        cdb.close()
        val month = idx[0] as String
        val status = idx[1] as String
        val created = idx[2] as Long
        val updated = idx[3] as Long
        val idxPhoto = (idx[4] as String).ifBlank { null }
        if (month.isBlank()) return null

        val mdb = openMonth(month)
        val data = mdb.query("SELECT plate,engine,odometer,make,model,delivery,customer,phone,worker,intake,status,photo FROM cars WHERE id=?", id) { c ->
            arrayOf<Any?>(c.textOr(0, ""), c.textOr(1, ""), c.textOr(2, ""), c.textOr(3, ""), c.textOr(4, ""), c.textOr(5, ""), c.textOr(6, ""), c.textOr(7, ""), c.textOr(8, ""), c.textOr(9, ""), c.textOr(10, status), c.textOr(11, ""))
        }.firstOrNull() ?: run { mdb.close(); return null }

        val parts = mdb.query("SELECT name FROM parts WHERE carId=? ORDER BY id", id) { it.textOr(0, "") }
        val pays = mdb.query("SELECT id,carId,amount,stage,uname,ts,pid FROM payments WHERE carId=? ORDER BY ts", id) { c ->
            Payment(c.long(0), c.textOr(1, ""), c.double(2), c.textOr(3, ""), c.textOr(4, ""), c.long(5), c.textOr(6, ""))
        }
        mdb.close()

        val photo = (data[11] as String).ifBlank { idxPhoto }
        return Car(
            id = id,
            plate = data[0] as String,
            engine = data[1] as String,
            odometer = data[2] as String,
            make = data[3] as String,
            model = data[4] as String,
            deliveryDate = data[5] as String,
            customer = data[6] as String,
            phone = data[7] as String,
            worker = data[8] as String,
            intake = data[9] as String,
            status = data[10] as String,
            monthKey = month,
            createdAt = created,
            updatedAt = updated,
            photo = photo,
            parts = parts,
            payments = pays,
        )
    }

    fun addPayment(car: Car, amount: Double, stage: String, userName: String) {
        val db = openMonth(car.monthKey)
        db.run("INSERT INTO payments(carId,amount,stage,uid,uname,ts,pid) VALUES(?,?,?,?,?,?,?)",
            car.id, amount, stage, activeUserId, userName, System.currentTimeMillis(), newId())
        db.close()
        val pay = totalPaid(car.monthKey, car.id)
        val cdb = openCentral()
        cdb.run("UPDATE car_index SET pay=?, updated=? WHERE id=?", pay, System.currentTimeMillis(), car.id)
        cdb.close()
        syncPending = true
    }

    fun setStatus(car: Car, status: String) {
        val db = openMonth(car.monthKey)
        db.run("UPDATE cars SET status=?, updated=? WHERE id=?", status, System.currentTimeMillis(), car.id)
        db.close()
        val cdb = openCentral()
        cdb.run("UPDATE car_index SET status=?, updated=? WHERE id=?", status, System.currentTimeMillis(), car.id)
        cdb.close()
        syncPending = true
    }

    fun deleteCar(car: Car) {
        val db = openMonth(car.monthKey)
        db.run("DELETE FROM cars WHERE id=?", car.id)
        db.run("DELETE FROM parts WHERE carId=?", car.id)
        db.run("DELETE FROM payments WHERE carId=?", car.id)
        db.close()
        val cdb = openCentral()
        cdb.run("DELETE FROM car_index WHERE id=?", car.id)
        cdb.run("INSERT OR REPLACE INTO tombstones(id,updated) VALUES(?,?)", car.id, System.currentTimeMillis())
        cdb.close()
        syncPending = true
    }

    fun recentPayments(limit: Int = 20): List<PayRow> {
        val mk = monthKey(System.currentTimeMillis())
        val mdb = openMonth(mk)
        val out = mdb.query(
            "SELECT p.amount,p.stage,p.uname,p.ts,c.plate FROM payments p LEFT JOIN cars c ON c.id=p.carId ORDER BY p.ts DESC LIMIT ?",
            limit,
        ) { c -> PayRow(c.textOr(4, ""), c.double(0), c.textOr(1, ""), c.textOr(2, ""), c.long(3)) }
        mdb.close()
        return out
    }

    // ---------- stats ----------
    fun stats(): Stats {
        val cdb = openCentral()
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val today = cal.timeInMillis
        val carsToday = cdb.query("SELECT COUNT(*) FROM car_index WHERE created>=?", today) { it.int(0) }.firstOrNull() ?: 0
        val inWork = cdb.query("SELECT COUNT(*) FROM car_index WHERE status=?", Status.WORK) { it.int(0) }.firstOrNull() ?: 0
        val pending = cdb.query("SELECT IFNULL(SUM(pay),0) FROM car_index WHERE status=?", Status.WORK) { it.double(0) }.firstOrNull() ?: 0.0
        cdb.close()

        val mk = monthKey(System.currentTimeMillis())
        val mdb = openMonth(mk)
        val todayIncome = mdb.query("SELECT IFNULL(SUM(amount),0) FROM payments WHERE ts>=?", today) { it.double(0) }.firstOrNull() ?: 0.0
        val monthIncome = mdb.query("SELECT IFNULL(SUM(amount),0) FROM payments") { it.double(0) }.firstOrNull() ?: 0.0
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

    fun allCarsFull(): List<Car> = carIndexIds().mapNotNull { carDetail(it) }

    private fun carIndexIds(): List<String> {
        val db = openCentral()
        val out = db.query("SELECT id FROM car_index") { it.textOr(0, "") }
        db.close()
        return out
    }

    fun tombstones(): Map<String, Long> {
        val db = openCentral()
        val out = HashMap<String, Long>()
        db.query("SELECT id,updated FROM tombstones") { c -> out[c.textOr(0, "")] = c.long(1) }
        db.close()
        return out
    }

    fun replaceTombstones(map: Map<String, Long>) {
        val db = openCentral()
        db.run("DELETE FROM tombstones")
        for ((k, v) in map) if (k.isNotBlank()) db.run("INSERT OR REPLACE INTO tombstones(id,updated) VALUES(?,?)", k, v)
        db.close()
    }

    // ---------- owner & local hide ----------
    fun isOwner(userName: String): Boolean {
        val n = normName(userName)
        if (n.isBlank()) return false
        if (n == normName(ownerName)) return true
        if (n == normName("عاصم حسين") || n == normName("Assem Hussein")) return true
        return try { users().any { normName(it.name) == n && it.role == "owner" } } catch (_: Exception) { false }
    }

    private fun normName(s: String) = s.trim().replace("\\s+".toRegex(), " ").lowercase()

    fun isActiveOwner(): Boolean = adminMode

    fun activeUserRole(): String = try { users().firstOrNull { it.id == activeUserId }?.role ?: "" } catch (_: Exception) { "" }
    fun isManager(): Boolean = adminMode || activeUserRole() == "admin"

    fun hiddenIds(): MutableSet<String> {
        val db = openCentral()
        val out = HashSet<String>()
        db.query("SELECT id FROM hidden") { c -> c.textOr(0, "").let { if (it.isNotBlank()) out.add(it) } }
        db.close()
        return out
    }

    fun hideLocally(id: String) {
        val db = openCentral()
        db.run("INSERT OR REPLACE INTO hidden(id) VALUES(?)", id)
        db.close()
    }

    fun unhide(id: String) {
        val db = openCentral()
        db.run("DELETE FROM hidden WHERE id=?", id)
        db.close()
    }

    fun hideCar(car: Car) {
        val db = openMonth(car.monthKey)
        db.run("DELETE FROM cars WHERE id=?", car.id)
        db.run("DELETE FROM parts WHERE carId=?", car.id)
        db.run("DELETE FROM payments WHERE carId=?", car.id)
        db.close()
        val cdb = openCentral()
        cdb.run("DELETE FROM car_index WHERE id=?", car.id)
        cdb.run("INSERT OR REPLACE INTO hidden(id) VALUES(?)", car.id)
        cdb.close()
    }

    fun upsertCarFull(car: Car) {
        val mk = car.monthKey.ifBlank { monthKey(if (car.createdAt > 0) car.createdAt else System.currentTimeMillis()) }
        val created = if (car.createdAt > 0) car.createdAt else System.currentTimeMillis()
        val updated = if (car.updatedAt > 0) car.updatedAt else System.currentTimeMillis()
        val mdb = openMonth(mk)
        mdb.run(
            "INSERT OR REPLACE INTO cars(id,plate,engine,odometer,make,model,delivery,customer,phone,worker,intake,status,created,updated,photo) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            car.id, car.plate, car.engine, car.odometer, car.make, car.model, car.deliveryDate, car.customer, car.phone, car.worker, car.intake, car.status, created, updated, car.photo,
        )
        mdb.run("DELETE FROM parts WHERE carId=?", car.id)
        for (p in car.parts) if (p.isNotBlank()) mdb.run("INSERT INTO parts(carId,name) VALUES(?,?)", car.id, p.trim())
        mdb.run("DELETE FROM payments WHERE carId=?", car.id)
        for (p in car.payments) mdb.run(
            "INSERT INTO payments(carId,amount,stage,uid,uname,ts,pid) VALUES(?,?,?,?,?,?,?)",
            car.id, p.amount, p.stage, activeUserId, p.userName, p.ts, p.pid.ifBlank { newId() },
        )
        mdb.close()
        val pay = totalPaid(mk, car.id)
        val cdb = openCentral()
        cdb.run(
            "INSERT OR REPLACE INTO car_index(id,plate,customer,phone,make,status,month,created,updated,pay,photo,adate) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
            car.id, car.plate, car.customer, car.phone, car.make, car.status, mk, created, updated, pay, car.photo, car.deliveryDate,
        )
        cdb.run("DELETE FROM tombstones WHERE id=?", car.id)
        cdb.close()
    }

    fun removeCarLocal(id: String, month: String) {
        val db = openMonth(month)
        db.run("DELETE FROM cars WHERE id=?", id)
        db.run("DELETE FROM parts WHERE carId=?", id)
        db.run("DELETE FROM payments WHERE carId=?", id)
        db.close()
        val cdb = openCentral()
        cdb.run("DELETE FROM car_index WHERE id=?", id)
        cdb.close()
    }

    fun totalSize(): Long =
        dbFiles().sumOf { it.length() } + photosDir().walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun compact(): String {
        var saved = 0L
        for (f in dbFiles()) {
            val before = f.length()
            val db = Db.open(f.absolutePath)
            db.run("VACUUM")
            db.close()
            saved += (before - f.length()).coerceAtLeast(0)
        }
        val cdb = openCentral()
        cdb.run("DELETE FROM logs WHERE ts < ?", System.currentTimeMillis() - 365L * 24 * 3600 * 1000)
        cdb.run("VACUUM")
        cdb.close()
        return saved.toString()
    }
}
