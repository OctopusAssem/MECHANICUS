package com.assem.mechanicus

// Simple key/value store, implemented per platform (Android SharedPreferences,
// desktop a properties file).
interface Prefs {
    fun getString(key: String, def: String): String
    fun putString(key: String, value: String)
    fun getBoolean(key: String, def: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun getLong(key: String, def: Long): Long
    fun putLong(key: String, value: Long)
    fun getInt(key: String, def: Int): Int
    fun putInt(key: String, value: Int)
}

// Small service locator the platform installs at startup. Shared code never
// touches Android or AWT directly; it goes through here.
object Platform {
    private var prefsFactory: ((String) -> Prefs)? = null
    private var dataRootPath: String = ""
    private var externalRootPath: String = ""
    private var versionName: String = ""
    private var notifier: (String) -> Unit = { }

    fun install(
        prefsFactory: (String) -> Prefs,
        dataRoot: String,
        version: String,
        notify: (String) -> Unit,
        externalRoot: String = "",
    ) {
        this.prefsFactory = prefsFactory
        this.dataRootPath = dataRoot
        this.externalRootPath = externalRoot
        this.versionName = version
        this.notifier = notify
    }

    fun prefs(name: String): Prefs =
        requireNotNull(prefsFactory) { "Platform not installed" }(name)

    val dataRoot: String get() = dataRootPath
    val externalRoot: String get() = externalRootPath
    val version: String get() = versionName
    fun notify(message: String) = notifier(message)
}
