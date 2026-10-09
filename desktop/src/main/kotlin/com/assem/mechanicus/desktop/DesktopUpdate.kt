package com.assem.mechanicus.desktop

import java.io.File
import java.util.Locale

// Auto-update helper. The shop drops an "update.msi" inside the program folder;
// on launch (and while running) the UI notices it and offers to install it.
object DesktopUpdate {
    private const val FILE_NAME = "update.msi"

    // Where the installed program lives. jpackage launchers set
    // "jpackage.app-path" to the launcher .exe, so its parent is the install dir.
    fun appDir(): File {
        val jp = System.getProperty("jpackage.app-path")
        if (!jp.isNullOrBlank()) {
            File(jp).parentFile?.let { return it }
        }
        return File(System.getProperty("user.dir") ?: ".").absoluteFile
    }

    fun pending(): File? {
        val f = File(appDir(), FILE_NAME)
        return if (f.isFile && f.length() > 0L) f else null
    }

    // Copy the installer aside, remove the original (so the app doesn't offer the
    // same update again after restarting) and launch it. Returns true when the
    // installer process started.
    fun install(msi: File, osName: String = System.getProperty("os.name") ?: ""): Boolean = runCatching {
        val tmp = File(
            System.getProperty("java.io.tmpdir") ?: ".",
            "mechanicus-update-${System.currentTimeMillis()}.msi",
        )
        msi.copyTo(tmp, overwrite = true)
        runCatching { msi.delete() }

        if (osName.lowercase(Locale.ROOT).contains("win")) {
            ProcessBuilder("msiexec", "/i", tmp.absolutePath).start()
        } else {
            if (!java.awt.Desktop.isDesktopSupported()) return false
            java.awt.Desktop.getDesktop().open(tmp)
        }
        true
    }.getOrDefault(false)
}
