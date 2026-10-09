package com.assem.mechanicus.desktop

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
            state = rememberWindowState(size = DpSize(1260.dp, 860.dp)),
        ) {
            DesktopApp(store)
        }
    }
}
