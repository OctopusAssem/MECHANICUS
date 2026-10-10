package com.assem.mechanicus.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.assem.mechanicus.DesktopPlatform
import com.assem.mechanicus.Platform
import com.assem.mechanicus.Store

fun main() {
    DesktopPlatform.install()
    Platform.syncBlob = SYNC_BLOB
    val store = Store()
    store.ensureOwnerUser()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "MECHANICUS",
            icon = painterResource("icon.png"),
            state = rememberWindowState(size = DpSize(1120.dp, 800.dp)),
        ) {
            // The window can be shrunk down to a phone-sized width; below ~820dp the
            // sidebar collapses to an icon rail so the content always fits.
            LaunchedEffect(Unit) { window.minimumSize = java.awt.Dimension(460, 720) }
            DesktopApp(store)
        }
    }
}
