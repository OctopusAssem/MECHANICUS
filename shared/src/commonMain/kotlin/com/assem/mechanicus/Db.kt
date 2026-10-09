package com.assem.mechanicus

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

// Thin cross-platform SQLite wrapper. Same code runs on Android and the JVM
// desktop because androidx.sqlite's bundled driver embeds SQLite for both.
class Db private constructor(private val conn: SQLiteConnection) {

    companion object {
        fun open(path: String): Db = Db(BundledSQLiteDriver().open(path))
    }

    fun exec(sql: String) {
        conn.prepare(sql).use { it.step() }
    }

    fun run(sql: String, vararg args: Any?): Int {
        conn.prepare(sql).use { st ->
            bind(st, args)
            st.step()
        }
        return 0
    }

    fun <T> query(sql: String, vararg args: Any?, map: (Row) -> T): List<T> {
        val out = ArrayList<T>()
        conn.prepare(sql).use { st ->
            bind(st, args)
            while (st.step()) out.add(map(Row(st)))
        }
        return out
    }

    fun close() = conn.close()

    private fun bind(st: SQLiteStatement, args: Array<out Any?>) {
        args.forEachIndexed { i, a ->
            val idx = i + 1
            when (a) {
                null -> st.bindNull(idx)
                is String -> st.bindText(idx, a)
                is Int -> st.bindInt(idx, a)
                is Long -> st.bindLong(idx, a)
                is Double -> st.bindDouble(idx, a)
                is Float -> st.bindDouble(idx, a.toDouble())
                is Boolean -> st.bindInt(idx, if (a) 1 else 0)
                else -> st.bindText(idx, a.toString())
            }
        }
    }
}

class Row internal constructor(private val st: SQLiteStatement) {
    fun text(i: Int): String = st.getText(i)
    fun long(i: Int): Long = st.getLong(i)
    fun int(i: Int): Int = st.getInt(i)
    fun double(i: Int): Double = st.getDouble(i)
    fun isNull(i: Int): Boolean = st.isNull(i)
}
