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
import com.assem.mechanicus.Car
import com.assem.mechanicus.DesktopPlatform
import com.assem.mechanicus.Status
import com.assem.mechanicus.Store

fun main() {
    DesktopPlatform.install()
    val store = Store()
    store.ensureOwnerUser()
    val users = store.users()
    val car = store.saveCar(
        Car(
            id = store.newId(),
            plate = "س ط ص 1234",
            engine = "ENG-001",
            odometer = "125000",
            make = "تويوتا",
            model = "كورولا",
            deliveryDate = store.monthKey(System.currentTimeMillis()),
            customer = "عميل تجريبي",
            phone = "01000000000",
            worker = "فني",
            intake = "صيانة دورية",
            status = Status.WORK,
            monthKey = "",
            createdAt = 0L,
            updatedAt = 0L,
        )
    )
    store.addPayment(car, 1500.0, "مقدم", "عاصم حسين")
    val detail = store.carDetail(car.id)
    val stats = store.stats()
    val info = "DB OK · users=${users.size} · cars=${store.listCars("all", "").size} · " +
        "plate=${detail?.plate} · paid=${detail?.payments?.sumOf { it.amount }} · inWork=${stats.inWork}"
    application {
        Window(onCloseRequest = ::exitApplication, title = "MECHANICUS") {
            Stub(store.root().absolutePath, info)
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
            Text("Shared data layer OK", color = Color(0xFFB9B9C0), fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Text(info, color = Color(0xFF8DF0A0), fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Text(folder, color = Color(0xFF8A8A92), fontSize = 11.sp)
        }
    }
}
