package com.assem.mechanicus.desktop

import java.util.concurrent.TimeUnit

// Finds and launches the Windows uninstaller that the MSI registered, so the
// program can remove itself from inside its own Settings screen.
object DesktopUninstall {
    private val KEYS = listOf(
        "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall",
        "HKLM\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall",
        "HKLM\\Software\\WOW6432Node\\Microsoft\\Windows\\CurrentVersion\\Uninstall",
    )

    fun findUninstall(): String? {
        for (key in KEYS) {
            val found = runCatching {
                val p = ProcessBuilder("reg", "query", key, "/s", "/f", "MECHANICUS")
                    .redirectErrorStream(true).start()
                val out = p.inputStream.bufferedReader().readText()
                p.waitFor(15, TimeUnit.SECONDS)
                out.lineSequence()
                    .map { it.trim() }
                    .firstNotNullOfOrNull { line ->
                        val i = line.indexOf("REG_SZ")
                        if (i < 0) null else line.substring(i + 6).trim()
                            .takeIf { it.contains("msiexec", ignoreCase = true) }
                    }
            }.getOrNull()
            if (found != null) return found
        }
        return null
    }

    // Returns false when the uninstaller can't be located or started.
    fun launchUninstall(): Boolean {
        val u = findUninstall() ?: return false
        return runCatching {
            ProcessBuilder("cmd", "/c", "start", "", u).start()
            true
        }.getOrDefault(false)
    }
}
