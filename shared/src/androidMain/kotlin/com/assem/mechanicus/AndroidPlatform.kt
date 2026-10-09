package com.assem.mechanicus

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import android.widget.Toast

private class AndroidPrefs(private val sp: SharedPreferences) : Prefs {
    override fun getString(key: String, def: String) = sp.getString(key, def) ?: def
    override fun putString(key: String, value: String) { sp.edit().putString(key, value).apply() }
    override fun getBoolean(key: String, def: Boolean) = sp.getBoolean(key, def)
    override fun putBoolean(key: String, value: Boolean) { sp.edit().putBoolean(key, value).apply() }
    override fun getLong(key: String, def: Long) = sp.getLong(key, def)
    override fun putLong(key: String, value: Long) { sp.edit().putLong(key, value).apply() }
    override fun getInt(key: String, def: Int) = sp.getInt(key, def)
    override fun putInt(key: String, value: Int) { sp.edit().putInt(key, value).apply() }
}

object AndroidPlatform {
    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val version = try {
            app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: ""
        } catch (_: Exception) { "" }
        val external = run {
            val dirs = app.getExternalFilesDirs(null)
            var ext = ""
            for (d in dirs) {
                if (d == null) continue
                val removable = try { Environment.isExternalStorageRemovable(d) } catch (_: Exception) { false }
                if (removable) { ext = d.absolutePath; break }
            }
            if (ext.isBlank()) ext = dirs.filterNotNull().firstOrNull()?.absolutePath ?: ""
            ext
        }
        Platform.install(
            prefsFactory = { name -> AndroidPrefs(app.getSharedPreferences(name, Context.MODE_PRIVATE)) },
            dataRoot = app.filesDir.absolutePath,
            version = version,
            notify = { msg -> Toast.makeText(app, msg, Toast.LENGTH_SHORT).show() },
            externalRoot = external,
        )
    }
}
