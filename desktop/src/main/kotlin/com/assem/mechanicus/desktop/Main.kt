package com.assem.mechanicus.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.assem.mechanicus.Db
import com.assem.mechanicus.DesktopPlatform
import com.assem.mechanicus.JvmProbe
import java.io.File

fun main() {
    DesktopPlatform.install()
    val folder = JvmProbe.appFolder()
    val db = Db.open(File(folder, "mechanicus.db").absolutePath)
    db.exec("CREATE TABLE IF NOT EXISTS probe(id INTEGER PRIMARY KEY, name TEXT, ts INTEGER)")
    db.run("INSERT OR IGNORE INTO probe(id, name, ts) VALUES(?,?,?)", 1, "مرحبا MECHANICUS", System.currentTimeMillis())
    db.run("UPDATE probe SET name = ? WHERE id = ?", "MECHANICUS على ويندوز", 1)
    val rows = db.query("SELECT name, ts FROM probe ORDER BY id") { it.text(0) to it.long(1) }
    val count = db.query("SELECT COUNT(*) FROM probe") { it.long(0) }.firstOrNull() ?: 0L
    val info = "SQLite OK · rows=$count · " + rows.joinToString { "${it.first}@${it.second}" }
    application {
        Window(onCloseRequest = ::exitApplication, title = "MECHANICUS") {
            Stub(folder.absolutePath, info)
        }
    }
}

@Composable
private fun Stub(folder: String, info: String) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF511722), Color(0xFF1A0F13))),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("MECHANICUS", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "YOUR AUTO REPAIR ASSISTANT",
                color = Color(0xFFF43F5E), fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(10.dp))
            Text("Shared module OK", color = Color(0xFFB9B9C0), fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Text(info, color = Color(0xFF8DF0A0), fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Text(folder, color = Color(0xFF8A8A92), fontSize = 11.sp)
        }
    }
}
