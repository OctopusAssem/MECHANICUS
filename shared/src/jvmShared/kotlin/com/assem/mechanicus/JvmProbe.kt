package com.assem.mechanicus

import java.io.File

// Probe: confirms the shared jvm source set (used by both Android and desktop)
// can use java.io.File. The app data folder lives under Platform.dataRoot.
object JvmProbe {
    fun appFolder(): File {
        val f = File(Platform.dataRoot, "MECHANICUS")
        if (!f.exists()) f.mkdirs()
        return f
    }
}
