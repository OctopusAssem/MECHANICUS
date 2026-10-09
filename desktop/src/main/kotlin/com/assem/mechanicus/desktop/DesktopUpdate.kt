package com.assem.mechanicus.desktop

import com.assem.mechanicus.GDrive
import com.assem.mechanicus.Platform
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

// A ready-to-install update: the installer file plus its version.
data class UpdateInfo(val file: File, val version: String)

// Auto-update helper. A newer version may arrive either as an "*.msi" inside the
// program folder, or be published to the shared Drive folder (pulled on sync).
//
// The version is taken from the file name when it contains one
// (e.g. update-1.0.7.msi); otherwise it is read from the installer's own
// ProductVersion. An update is only offered when its version is strictly
// HIGHER than the installed one, so re-dropping the same (or an older) file
// never nags the user again.
object DesktopUpdate {
    private val VER = Regex("(\\d+)\\.(\\d+)(?:\\.(\\d+))?")
    private val versionCache = HashMap<String, String>()

    // Where the installed program lives. jpackage launchers set
    // "jpackage.app-path" to the launcher .exe, so its parent is the install dir.
    fun appDir(): File {
        val jp = System.getProperty("jpackage.app-path")
        if (!jp.isNullOrBlank()) File(jp).parentFile?.let { return it }
        return File(System.getProperty("user.dir") ?: ".").absoluteFile
    }

    fun versionFromName(name: String): String? = VER.find(name)?.value

    fun isNewer(candidate: String, current: String): Boolean {
        val a = candidate.split('.').map { it.toIntOrNull() ?: 0 }
        val b = current.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun cacheKey(f: File) = "${f.absolutePath}|${f.lastModified()}|${f.length()}"

    // Version of an installer: from its file name, else read on Windows.
    fun msiVersion(file: File): String? {
        versionFromName(file.name)?.let { return it }
        val key = cacheKey(file)
        synchronized(versionCache) { versionCache[key]?.let { return it.ifBlank { null } } }
        val v = readWindowsVersion(file) ?: ""
        synchronized(versionCache) { versionCache[key] = v }
        return v.ifBlank { null }
    }

    // Ask the Windows Installer for the MSI's ProductVersion.
    private fun readWindowsVersion(file: File): String? {
        val os = System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT)
        if (!os.contains("win")) return null
        val script =
            "\$ErrorActionPreference='Stop';" +
            "\$i=New-Object -ComObject WindowsInstaller.Installer;" +
            "\$db=\$i.GetType().InvokeMember('OpenDatabase','InvokeMethod',\$null,\$i,@(\$env:MECHANICUS_MSI,0));" +
            "\$v=\$db.GetType().InvokeMember('OpenView','InvokeMethod',\$null,\$db,@(\"SELECT Value FROM Property WHERE Property='ProductVersion'\"));" +
            "\$v.GetType().InvokeMember('Execute','InvokeMethod',\$null,\$v,\$null);" +
            "\$r=\$v.GetType().InvokeMember('Fetch','InvokeMethod',\$null,\$v,\$null);" +
            "Write-Output \$r.GetType().InvokeMember('StringData','GetProperty',\$null,\$r,@(1))"
        return try {
            val pb = ProcessBuilder(
                "powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command", script,
            )
            pb.environment()["MECHANICUS_MSI"] = file.absolutePath
            pb.redirectErrorStream(true)
            val p = pb.start()
            val out = p.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (!p.waitFor(40, TimeUnit.SECONDS)) { p.destroyForcibly(); return null }
            out.lineSequence().map { it.trim() }.lastOrNull { it.matches(Regex("\\d+(\\.\\d+){1,3}")) }
        } catch (_: Exception) {
            null
        }
    }

    // The update to offer: the highest newer installer found in the program
    // folder, or null when there is none (same / older versions are ignored).
    fun pending(current: String = Platform.version): UpdateInfo? {
        val files = (appDir().listFiles() ?: emptyArray())
            .filter { it.isFile && it.length() > 0L && it.name.lowercase(Locale.ROOT).endsWith(".msi") }
        var bestFile: File? = null
        var bestVer = ""
        for (f in files) {
            val v = msiVersion(f) ?: continue
            if (!isNewer(v, current)) continue
            if (bestFile == null || isNewer(v, bestVer)) { bestFile = f; bestVer = v }
        }
        return bestFile?.let { UpdateInfo(it, bestVer) }
    }

    // Pull a published installer from the shared Drive folder into the program
    // folder when it is a new, higher version. Name-first, so a versioned file
    // (e.g. update-1.0.7.msi) is compared without downloading the whole MSI.
    fun pullFromDrive(): Boolean {
        val token = GDrive.token() ?: return false
        val found = GDrive.findUpdate(token) ?: return false
        val prefs = Platform.prefs("update")
        val stamp = found.first + ":" + found.third
        if (prefs.getString("seen", "") == stamp) return false
        val ver = versionFromName(found.second)
        if (ver != null && !isNewer(ver, Platform.version)) {
            prefs.putString("seen", stamp)
            return false
        }
        return try {
            GDrive.downloadFile(token, found.first, File(appDir(), found.second))
            prefs.putString("seen", stamp)
            true
        } catch (_: Exception) {
            false
        }
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
