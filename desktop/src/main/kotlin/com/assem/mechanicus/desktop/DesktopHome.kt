package com.assem.mechanicus.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assem.mechanicus.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HomeDesktop(
    store: Store,
    L: Lang,
    version: Int,
    scope: kotlinx.coroutines.CoroutineScope,
    go: (Scr) -> Unit,
    bump: () -> Unit,
) {
    val banner = LocalBanner.current
    val stats = remember(version) { store.stats() }
    val cars = remember(version) { store.listCars("all", "").take(6) }
    val manager = store.isManager()

    val doSync: () -> Unit = {
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { com.assem.mechanicus.AutoSync.run(store) }.getOrDefault("") }
            when (r) {
                com.assem.mechanicus.AutoSync.SYNCED -> { bump(); banner(L.s("تمت المزامنة ✅", "Synced ✅")) }
                com.assem.mechanicus.AutoSync.LOCKED -> banner(L.s("قاعدة البيانات محمية — محتاج تصريح المسؤول", "Database protected — admin authorization needed"))
                com.assem.mechanicus.AutoSync.PENDING -> banner(L.s("أوفلاين — هيتم تلقائيًا لما النت يرجع", "Offline — will sync when back online"))
                else -> banner(L.s("مفيش مزامنة — اتأكد من مفتاح الشركة من الإعدادات", "No sync — check the company key in Settings"))
            }
        }
    }

    ContentScroll {
        TopBar(
            L.s("الرئيسية", "Home"),
            L.s("نظرة سريعة على الورشة", "A quick look at the shop"),
        ) {
            GhostButton(L.s("🔄 مزامنة الآن", "🔄 Sync now"), Modifier.width(155.dp)) { doSync() }
            Spacer(Modifier.width(10.dp))
            GhostButton(L.s("＋ عربية جديدة", "＋ New car"), Modifier.width(160.dp)) { go(Scr.Edit(null)) }
            Spacer(Modifier.width(10.dp))
            GhostButton(L.s("كل العربيات", "All cars"), Modifier.width(130.dp)) { go(Scr.Cars) }
        }

        Spacer(Modifier.height(18.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("🚗", "${stats.carsToday}", L.s("دخلت النهارده", "Entered today"), Color(0xFFF43F5E), Color(0xFF3A1418), Modifier.weight(1f))
            StatCard("🛠️", "${stats.inWork}", L.s("شغّالين الآن", "In the shop now"), Color(0xFFFBBF24), AmberSoft, Modifier.weight(1f))
            if (manager) {
                StatCard("💵", money(stats.todayIncome), L.s("دخل النهارده", "Income today"), Green, GreenSoft, Modifier.weight(1f))
                StatCard("📅", money(stats.monthIncome), L.s("دخل الشهر", "Income this month"), Green, GreenSoft, Modifier.weight(1f))
                StatCard("⏳", money(stats.pending), L.s("متبقّي على الشغل", "Outstanding"), Color(0xFFFCA5A5), Color(0xFF3A1418), Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(22.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.weight(2f),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(L.s("آخر العربيات", "Recent cars"), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(10.dp))
                    if (cars.isEmpty()) {
                        Text(L.s("لسه مفيش عربيات.", "No cars yet."), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.5.sp)
                    } else {
                        for (row in cars) {
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                PlateText(row.plate)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(row.customer.ifBlank { "—" }, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text(row.adate.ifBlank { row.month }, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                StatusChip(row.status)
                                if (manager) {
                                    Spacer(Modifier.width(10.dp))
                                    Text(money(row.pay), fontSize = 13.sp, fontWeight = FontWeight.Black, color = Green)
                                }
                            }
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.weight(1f),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(L.s("المزامنة", "Sync"), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    val state = when {
                        !store.driveConnected -> L.s("غير متصل", "Not connected")
                        store.syncPending -> L.s("فيه تغييرات في الانتظار", "Changes pending")
                        else -> L.s("متزامن ✅", "Synced ✅")
                    }
                    Text(state, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (store.syncPending) Color(0xFFFBBF24) else Green)
                    Spacer(Modifier.height(12.dp))
                    GhostButton(L.s("مزامنة الآن", "Sync now"), Modifier.fillMaxWidth()) { doSync() }
                }
            }
        }
    }
}
