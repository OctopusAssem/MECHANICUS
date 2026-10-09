package com.assem.mechanicus.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assem.mechanicus.Store
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val tsFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

@Composable
fun PaymentsDesktop(store: Store, L: Lang, version: Int, go: (Scr) -> Unit, bump: () -> Unit) {
    if (!store.isManager()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(L.s("المدفوعات للمدير فقط.", "Payments are for the manager only."), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }
        return
    }
    val stats = remember(version) { store.stats() }
    val pays = remember(version) { store.recentPayments(50) }

    Column(Modifier.fillMaxSize()) {
        TopBar(L.s("المدفوعات", "Payments"), L.s("آخر الدفعات المسجّلة", "Latest recorded payments"))
        Column(Modifier.fillMaxWidth().padding(22.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("💵", money(stats.todayIncome), L.s("دخل النهارده", "Income today"), Green, GreenSoft, Modifier.weight(1f))
                StatCard("📅", money(stats.monthIncome), L.s("دخل الشهر", "Income this month"), Green, GreenSoft, Modifier.weight(1f))
                StatCard("⏳", money(stats.pending), L.s("متبقّي على الشغل", "Outstanding"), Color(0xFFFCA5A5), Color(0xFF3A1418), Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
        }
        if (pays.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(L.s("مفيش دفعات الشهر ده.", "No payments this month."), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(pays) { p ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(horizontal = 15.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            PlateText(p.plate)
                            Spacer(Modifier.width(12.dp))
                            Text(stageLabel(L, p.stage), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(10.dp))
                            Text(p.userName, fontSize = 12.sp, color = Muted)
                            Spacer(Modifier.weight(1f))
                            Text(tsFmt.format(Date(p.ts)), fontSize = 11.sp, color = Muted)
                            Spacer(Modifier.width(14.dp))
                            Text(money(p.amount), fontSize = 14.sp, fontWeight = FontWeight.Black, color = Green)
                        }
                    }
                }
                item { Spacer(Modifier.height(18.dp)) }
            }
        }
    }
}

@Composable
fun UsersDesktop(store: Store, L: Lang, version: Int, bump: () -> Unit) {
    val banner = LocalBanner.current
    val manager = store.isManager()
    val users = remember(version) { store.visibleUsers() }
    var name by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("tech") }

    ContentScroll {
        TopBar(L.s("المستخدمون", "Users"), L.s("${users.size} مستخدم", "${users.size} user(s)"))

        if (manager) {
            CardBox {
                SectionTitle(L.s("إضافة مستخدم", "Add user"))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Field(L.s("الاسم", "Name"), name, { name = it }, Modifier.weight(1f))
                    Field(L.s("الرقم السري", "PIN"), pin, { pin = it }, Modifier.width(160.dp), digitsOnly = true, maxLen = 4)
                    FilterChip(L.s("مدير", "Manager"), role == "admin") { role = if (role == "admin") "tech" else "admin" }
                    GhostButton(L.s("إضافة", "Add"), Modifier.width(110.dp)) {
                        if (name.trim().isBlank() || pin.length != 4) banner(L.s("اكتب اسم ورقم سري من 4 أرقام", "Enter a name and a 4-digit PIN"))
                        else {
                            store.addUser(name.trim(), pin, role)
                            store.addLog(store.activeUserName, "user", "", L.s("إضافة مستخدم ${name.trim()}", "Added user ${name.trim()}"))
                            name = ""; pin = ""; bump()
                            banner(L.s("تمت الإضافة ✅", "Added ✅"))
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        for (u in users) {
            Surface(
                shape = RoundedCornerShape(15.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.width(40.dp).height(40.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(if (u.role == "admin") "👑" else "🔧", fontSize = 20.sp) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(u.name, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (u.role == "admin") L.s("مدير", "Manager") else L.s("فني", "Technician"),
                            fontSize = 11.5.sp, color = if (u.role == "admin") Red else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (manager && u.name != "عاصم حسين") {
                        GhostButton(L.s("تصفير الرقم السري", "Reset PIN"), Modifier.width(170.dp)) {
                            val np = (1000..9999).random().toString()
                            store.setPin(u.id, np)
                            bump()
                            banner(L.s("الرقم السري الجديد: $np", "New PIN: $np"))
                        }
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(15.dp),
                            color = Color(0xFF3A1418),
                            border = BorderStroke(1.dp, Color(0xFF7A2A30)),
                            modifier = Modifier.height(48.dp),
                        ) {
                            Box(Modifier.padding(horizontal = 16.dp)) {
                                TextButton(onClick = { store.deleteUser(u.id); bump(); banner(L.s("تم الحذف", "Deleted")) }) {
                                    Text(L.s("حذف", "Delete"), color = Color(0xFFFCA5A5), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LogsDesktop(store: Store, L: Lang, version: Int) {
    if (!store.isManager()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(L.s("السجل للمدير فقط.", "The change log is for the manager only."), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }
        return
    }
    val logs = remember(version) { store.logs() }
    Column(Modifier.fillMaxSize()) {
        TopBar(L.s("سجل التغييرات", "Change log"), L.s("${logs.size} حركة", "${logs.size} entr(ies)"))
        if (logs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(L.s("مفيش حركات.", "No entries."), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                items(logs) { e ->
                    Surface(
                        shape = RoundedCornerShape(13.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(horizontal = 15.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(e.userName.ifBlank { "—" }, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(130.dp))
                            Text(e.action, fontSize = 12.sp, color = Red, modifier = Modifier.width(80.dp))
                            Text(e.plate.ifBlank { e.detail }, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
                            Text(tsFmt.format(Date(e.ts)), fontSize = 11.sp, color = Muted)
                        }
                    }
                }
                item { Spacer(Modifier.height(18.dp)) }
            }
        }
    }
}
