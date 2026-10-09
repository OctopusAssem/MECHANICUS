package com.assem.mechanicus

import java.io.File
import java.util.Properties

private class FilePrefs(private val file: File) : Prefs {
    private val props = Properties().apply {
        if (file.exists()) runCatching { file.inputStream().use { load(it) } }
    }

    private fun save() {
        file.parentFile?.mkdirs()
        runCatching { file.outputStream().use { props.store(it, "MECHANICUS") } }
    }

    private fun s(key: String, def: String) = props.getProperty(key) ?: def

    override fun getString(key: String, def: String) = s(key, def)
    override fun putString(key: String, value: String) { props.setProperty(key, value); save() }
    override fun getBoolean(key: String, def: Boolean) = s(key, def.toString()).toBoolean()
    override fun putBoolean(key: String, value: Boolean) { props.setProperty(key, value.toString()); save() }
    override fun getLong(key: String, def: Long) = s(key, def.toString()).toLongOrNull() ?: def
    override fun putLong(key: String, value: Long) { props.setProperty(key, value.toString()); save() }
    override fun getInt(key: String, def: Int) = s(key, def.toString()).toIntOrNull() ?: def
    override fun putInt(key: String, value: Int) { props.setProperty(key, value.toString()); save() }
}

object DesktopPlatform {
    fun install() {
        val home = System.getProperty("user.home") ?: "."
        val base = File(home, ".mechanicus")
        if (!base.exists()) base.mkdirs()
        Platform.install(
            prefsFactory = { name -> FilePrefs(File(base, "$name.properties")) },
            dataRoot = base.absolutePath,
            version = "1.0.5",
            notify = { msg -> println("[MECHANICUS] $msg") },
        )
    }
}
