package com.assem.mechanicus.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assem.mechanicus.IndexRow
import com.assem.mechanicus.Store
import com.assem.mechanicus.Status

@Composable
fun CarsDesktop(
    store: Store,
    L: Lang,
    version: Int,
    go: (Scr) -> Unit,
    bump: () -> Unit,
) {
    val banner = LocalBanner.current
    var q by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("all") }
    val cars = remember(version, q, filter) { store.listCars(filter, q) }
    val manager = store.isManager()

    Column(Modifier.fillMaxSize()) {
        TopBar(L.s("العربيات", "Cars"), L.s("${cars.size} نتيجة", "${cars.size} result(s)")) {
            GhostButton(L.s("＋ عربية جديدة", "＋ New car"), Modifier.width(160.dp)) { go(Scr.Edit(null)) }
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Field(L.s("ابحث باللوحة أو العميل أو الهاتف", "Search plate, customer or phone"), q, { q = it }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(L.s("الكل", "All"), filter == "all") { filter = "all" }
                FilterChip(L.s("شغّال", "Working"), filter == Status.WORK) { filter = Status.WORK }
                FilterChip(L.s("تم التسليم", "Delivered"), filter == Status.DONE) { filter = Status.DONE }
                FilterChip(L.s("متأخر", "Late"), filter == Status.LATE) { filter = Status.LATE }
            }
        }

        if (cars.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(L.s("مفيش نتائج.", "No results."), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 22.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                items(cars, key = { it.id }) { row ->
                    CarRow(row, L, manager) { go(Scr.Detail(row.id)) }
                }
                item { Spacer(Modifier.height(18.dp)) }
            }
        }
    }
}

@Composable
fun CarRow(row: IndexRow, L: Lang, showMoney: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
    ) {
        Row(Modifier.padding(horizontal = 15.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF2A1015)),
                contentAlignment = Alignment.Center,
            ) { Text("🚗", fontSize = 18.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlateText(row.plate)
                    Spacer(Modifier.width(9.dp))
                    StatusChip(row.status)
                }
                Spacer(Modifier.height(3.dp))
                Text(row.customer.ifBlank { "—" }, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
            }
            if (ContactButtons(row.phone, L)) {
                Spacer(Modifier.width(6.dp))
            }
            if (showMoney) {
                Spacer(Modifier.width(12.dp))
                Text(money(row.pay), fontSize = 14.sp, fontWeight = FontWeight.Black, color = Green)
            }
            Spacer(Modifier.width(12.dp))
            Text(row.adate.ifBlank { row.month }, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// WhatsApp + call icons shown only for a usable phone number.
@Composable
fun ContactButtons(phone: String, L: Lang): Boolean {
    if (!DesktopActions.valid(phone)) return false
    val banner = LocalBanner.current
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundIconBtn("💬", Color(0xFF25D366)) {
            if (!DesktopActions.whatsapp(phone)) banner(L.s("تعذّر فتح واتساب", "Could not open WhatsApp"))
        }
        RoundIconBtn("📞", Color(0xFF2563EB)) {
            DesktopActions.copy(phone)
            banner(L.s("تم نسخ الرقم", "Number copied"))
        }
    }
    return true
}

@Composable
private fun RoundIconBtn(emoji: String, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.18f)).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Text(emoji, fontSize = 14.sp) }
}

@Composable
fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) Red.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (selected) Red else MaterialTheme.colorScheme.outline),
        modifier = Modifier.clickable { onClick() },
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 7.dp),
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color(0xFFFCA5A5) else MaterialTheme.colorScheme.onSurface,
        )
    }
}
