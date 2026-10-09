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
import com.assem.mechanicus.DesktopPlatform
import com.assem.mechanicus.JvmProbe

fun main() {
    DesktopPlatform.install()
    val folder = JvmProbe.appFolder().absolutePath
    application {
        Window(onCloseRequest = ::exitApplication, title = "MECHANICUS") {
            Stub(folder)
        }
    }
}

@Composable
private fun Stub(folder: String) {
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
            Text(folder, color = Color(0xFF8A8A92), fontSize = 11.sp)
        }
    }
}
