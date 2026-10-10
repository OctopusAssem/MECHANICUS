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

    // jpackage registers UninstallString as "MsiExec.exe /I{ProductCode}".
    private val PRODUCT = Regex("(?i)/I\\s*(\\{[0-9A-Fa-f-]{36}})")

    fun findUninstall(): String? {
        for (key in KEYS) {
            val out = regQuery(key) ?: continue
            parse(out)?.let { return it }
        }
        return null
    }

    // `reg query <key> /s` dumps every subkey. We cannot use "/f MECHANICUS"
    // because that filters out the UninstallString line (its data has no
    // "MECHANICUS" in it), so we read the whole subtree and walk it block by
    // block instead.
    private fun regQuery(key: String): String? = runCatching {
        val p = ProcessBuilder("reg", "query", key, "/s").redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor(20, TimeUnit.SECONDS)
        out
    }.getOrNull()

    // Each subkey is one "block": a HKEY_... header line followed by its value
    // lines. Return the UninstallString of the block whose DisplayName is ours.
    private fun parse(out: String): String? {
        var block = ArrayList<String>()
        fun flush(): String? {
            val name = value(block, "DisplayName")
            val un = value(block, "UninstallString") ?: value(block, "QuietUninstallString")
            return if (name != null && name.contains("MECHANICUS", ignoreCase = true) && !un.isNullOrBlank()) un else null
        }
        for (raw in out.lineSequence()) {
            val t = raw.trim()
            if (t.startsWith("HKEY_")) {
                flush()?.let { return it }
                block = ArrayList()
            } else if (t.isNotEmpty()) {
                block.add(t)
            }
        }
        return flush()
    }

    // Data of a named REG_SZ value inside one registry block.
    private fun value(block: List<String>, name: String): String? {
        for (line in block) {
            val i = line.indexOf("REG_SZ")
            if (i < 0) continue
            val valueName = line.substring(0, i).trim().split(Regex("\\s+")).firstOrNull() ?: continue
            if (valueName.equals(name, ignoreCase = true)) return line.substring(i + 6).trim()
        }
        return null
    }

    // Returns false when the uninstaller can't be located or started.
    fun launchUninstall(): Boolean {
        val u = findUninstall() ?: return false
        // Run the removal directly ("msiexec /x {code}") instead of the
        // registered "/I" which only opens the Change/Repair/Remove menu.
        val guid = PRODUCT.find(u)?.groupValues?.get(1)
        val cmd = if (guid != null) "msiexec /x $guid" else u
        return runCatching {
            ProcessBuilder("cmd", "/c", "start", "", cmd).start()
            true
        }.getOrDefault(false)
    }
}
