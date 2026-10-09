package com.assem.mechanicus.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import javax.imageio.ImageIO

object DesktopActions {
    private fun digits(phone: String): String {
        var d = phone.filter { it.isDigit() }
        if (d.startsWith("00")) d = d.substring(2)
        else if (d.startsWith("0")) d = "20" + d.substring(1)
        return d
    }

    fun valid(phone: String): Boolean = phone.filter { it.isDigit() }.length >= 7

    fun copy(text: String) {
        runCatching {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        }
    }

    fun whatsapp(phone: String): Boolean {
        if (!valid(phone)) return false
        return runCatching {
            if (!java.awt.Desktop.isDesktopSupported()) return false
            java.awt.Desktop.getDesktop().browse(java.net.URI("https://wa.me/${digits(phone)}"))
            true
        }.getOrDefault(false)
    }

    fun openPhotosFolder(): Boolean = runCatching {
        val dir = File(com.assem.mechanicus.Platform.dataRoot, "MECHANICUS")
        if (!java.awt.Desktop.isDesktopSupported()) return false
        java.awt.Desktop.getDesktop().open(dir.also { if (!it.exists()) it.mkdirs() })
        true
    }.getOrDefault(false)
}

@Composable
fun CarPhoto(path: String?, modifier: Modifier = Modifier) {
    val bmp: ImageBitmap? = remember(path) {
        if (path.isNullOrBlank()) null
        else runCatching {
            val f = File(File(com.assem.mechanicus.Platform.dataRoot, "MECHANICUS"), "photos").resolve(path)
            if (!f.exists()) null else ImageIO.read(f)?.toComposeImageBitmap()
        }.getOrNull()
    }
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(14.dp)),
        )
    }
}
