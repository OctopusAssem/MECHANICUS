package com.assem.mechanicus

import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ------------------------- PAYMENTS -------------------------
@Composable
fun PaymentsScreen(ctx: AppCtx) {
    val L = ctx.L
    val context = LocalContext.current
    val stats = remember(ctx.version) { ctx.store.stats() }
    val cars = remember(ctx.version) { ctx.store.listCars("all", "") }
    val recents = remember(ctx.version) { ctx.store.recentPayments(15) }

    var selected by remember { mutableStateOf<IndexRow?>(cars.firstOrNull()) }
    var menu by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf("") }
    var stage by remember { mutableStateOf("work") }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 20.dp)) {
        item { ScreenBar(title = L.s("المدفوعات", "Payments")) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                StatCard("💵", money(stats.monthIncome), L.s("إجمالي الشهر", "This month"), Green, GreenSoft, Modifier.weight(1f))
                StatCard("⏳", money(stats.pending), L.s("قيد التسليم", "In progress"), Color(0xFFB45309), AmberSoft, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
        }
        item { SectionTitle(L.s("تسجيل دفعة", "Record payment")) }
        item {
            CardBox {
                Text(L.s("العربية", "Vehicle"), fontSize = 12.sp, color = Muted, fontWeight = FontWeight.SemiBold)
                Box {
                    Surface(
                        shape = RoundedCornerShape(13.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).clickable { menu = true },
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(selected?.let { it.plate + " — " + it.customer } ?: L.s("اختر عربية", "Choose a car"), modifier = Modifier.weight(1f), fontSize = 14.sp)
                            Icon(Icons.Filled.ExpandMore, contentDescription = null)
                        }
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        for (c in cars) DropdownMenuItem(
                            text = { Text(c.plate + " — " + c.customer) },
                            onClick = { selected = c; menu = false },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Field(L.s("المبلغ (ج.م)", "Amount (EGP)"), amount, { amount = it })
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = stage == "work", onClick = { stage = "work" }, label = { Text(L.s("أثناء العمل", "During work"), fontSize = 12.sp) })
                    FilterChip(selected = stage == "on", onClick = { stage = "on" }, label = { Text(L.s("عند التسليم", "On delivery"), fontSize = 12.sp) })
                    FilterChip(selected = stage == "final", onClick = { stage = "final" }, label = { Text(L.s("تسليم نهائي", "Final"), fontSize = 12.sp) })
                }
                Spacer(Modifier.height(12.dp))
                PrimaryButton(L.s("حفظ الدفعة", "Save payment")) {
                    val a = amount.toDoubleOrNull()
                    val sel = selected
                    if (a == null || a <= 0 || sel == null) {
                        Toast.makeText(context, L.s("أدخل مبلغ صحيح واختر عربية", "Enter a valid amount and car"), Toast.LENGTH_SHORT).show()
                    } else {
                        val car = ctx.store.carDetail(sel.id)
                        if (car != null) {
                            ctx.store.addPayment(car, a, stage, ctx.store.activeUserName)
                            ctx.store.addLog(ctx.store.activeUserName, "payment", car.plate, stageLabel(L, stage) + " " + money(a))
                        }
                        ctx.bump()
                        amount = ""
                        Toast.makeText(context, L.s("تم حفظ الدفعة", "Payment saved"), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        item { SectionTitle(L.s("أحدث الدفعات", "Latest payments")) }
        if (recents.isEmpty()) item { EmptyNote(L.s("لا مدفوعات هذا الشهر", "No payments this month")) }
        items(recents) { p ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = GreenSoft,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.plate, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(stageLabel(L, p.stage) + " • " + p.userName + " • " + SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(p.ts)), color = Muted, fontSize = 11.sp)
                    }
                    Text(money(p.amount) + " " + L.s("ج.م", "EGP"), color = Green, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

// ------------------------- USERS -------------------------
@Composable
fun UsersScreen(ctx: AppCtx) {
    val L = ctx.L
    val context = LocalContext.current
    val users = remember(ctx.version) { ctx.store.users() }
    var name by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("tech") }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 20.dp)) {
        item { ScreenBar(title = L.s("المستخدمون", "Users"), onBack = { ctx.go(Dest.Settings) }) }
        items(users) { u ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(RedSoft), contentAlignment = Alignment.Center) {
                        Text(u.name.take(1), color = RedDeep, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.size(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(u.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text((if (u.role == "admin") L.s("مدير", "Manager") else L.s("فني", "Technician")) + " • PIN ••••", color = Muted, fontSize = 11.sp)
                    }
                    if (u.id != ctx.store.activeUserId) IconButton(onClick = {
                        ctx.store.deleteUser(u.id); ctx.bump()
                    }) { Icon(Icons.Filled.Delete, contentDescription = "delete", tint = Red) }
                }
            }
        }
        item { SectionTitle(L.s("إضافة مستخدم", "Add user")) }
        item {
            CardBox {
                Field(L.s("الاسم", "Name"), name, { name = it })
                Spacer(Modifier.height(10.dp))
                Field(L.s("الرقم السري (4 أرقام)", "PIN (4 digits)"), pin, { if (it.length <= 4) pin = it.filter { c -> c.isDigit() } })
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = role == "tech", onClick = { role = "tech" }, label = { Text(L.s("فني", "Technician"), fontSize = 12.sp) })
                    FilterChip(selected = role == "admin", onClick = { role = "admin" }, label = { Text(L.s("مدير", "Manager"), fontSize = 12.sp) })
                }
                Spacer(Modifier.height(12.dp))
                PrimaryButton(L.s("حفظ", "Save")) {
                    if (name.isNotBlank() && pin.length == 4) {
                        ctx.store.addUser(name.trim(), pin, role)
                        ctx.store.addLog(ctx.store.activeUserName, "add", "", L.s("إضافة مستخدم ", "Added user ") + name)
                        name = ""; pin = ""
                        ctx.bump()
                    } else {
                        Toast.makeText(context, L.s("أدخل اسم ورقم سري من 4 أرقام", "Enter name and a 4-digit PIN"), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}

// ------------------------- LOGS -------------------------
@Composable
fun LogsScreen(ctx: AppCtx) {
    val L = ctx.L
    val logs = remember(ctx.version) { ctx.store.logs() }
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 20.dp)) {
        item { ScreenBar(title = L.s("سجل التغييرات", "Change log"), onBack = { ctx.go(Dest.Settings) }) }
        if (logs.isEmpty()) item { EmptyNote(L.s("السجل فاضي", "Log is empty")) }
        items(logs) { e ->
            Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 6.dp).size(8.dp).clip(RoundedCornerShape(50)).background(Red))
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(buildString {
                        append(e.userName)
                        append(" — ")
                        append(actionLabel(L, e.action))
                        if (e.plate.isNotBlank()) { append(" ("); append(e.plate); append(")") }
                        if (e.detail.isNotBlank()) { append(" · "); append(e.detail) }
                    }, fontSize = 13.sp)
                    Text(fmt.format(Date(e.ts)), color = Muted, fontSize = 11.sp)
                }
            }
        }
    }
}

fun actionLabel(L: Lang, action: String): String = when (action) {
    "add" -> L.s("إضافة", "Added")
    "edit" -> L.s("تعديل", "Edited")
    "delete" -> L.s("حذف", "Deleted")
    "payment" -> L.s("دفعة", "Payment")
    "status" -> L.s("تغيير حالة", "Status change")
    "login" -> L.s("تسجيل دخول", "Sign in")
    "user" -> L.s("مستخدم", "User")
    else -> action
}

// ------------------------- SETTINGS -------------------------
@Composable
fun SettingsScreen(ctx: AppCtx) {
    val L = ctx.L
    val context = LocalContext.current
    var external by remember { mutableStateOf(ctx.store.useExternal) }
    var dark by remember { mutableStateOf(ctx.store.dark) }
    var email by remember { mutableStateOf(ctx.store.driveEmail) }
    var sizeText by remember { mutableStateOf(humanSize(ctx.store.totalSize())) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        ScreenBar(title = L.s("الإعدادات", "Settings"))

        SectionTitle(L.s("مكان حفظ قاعدة البيانات", "Database storage"))
        CardBox {
            StorageOption(L.s("الذاكرة الداخلية", "Internal storage"), L.s("أسرع للاستخدام اليومي", "Fastest for daily use"), external = external) {
                external = false; ctx.store.useExternal = false; ctx.bump()
                Toast.makeText(context, L.s("تم التبديل للذاكرة الداخلية", "Switched to internal storage"), Toast.LENGTH_SHORT).show()
            }
            StorageOption(L.s("الذاكرة الخارجية (SD)", "External storage (SD)"), L.s("مناسبة للنقل بفلاشة", "Good for moving via USB"), external = !external) {
                external = true; ctx.store.useExternal = true; ctx.bump()
                Toast.makeText(context, L.s("تم التبديل للذاكرة الخارجية", "Switched to external storage"), Toast.LENGTH_SHORT).show()
            }
            Text(L.s("المسار الحالي: ", "Current path: ") + ctx.store.root().absolutePath, color = Muted, fontSize = 10.5.sp, modifier = Modifier.padding(top = 8.dp))
        }

        SectionTitle(L.s("عام", "General"))
        CardBox {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(L.s("اللغة", "Language"), fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = ctx.lang == "ar", onClick = { ctx.setLang("ar") }, label = { Text("العربية", fontSize = 12.sp) })
                    FilterChip(selected = ctx.lang == "en", onClick = { ctx.setLang("en") }, label = { Text("English", fontSize = 12.sp) })
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(L.s("الوضع الليلي", "Dark mode"), fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Switch(checked = dark, onCheckedChange = { dark = it; ctx.setDark(it) })
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(L.s("حساب جوجل درايف", "Google Drive account"), fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(L.s("متصل", "Connected"), color = Green, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            OutlinedTextField(
                value = email, onValueChange = { email = it; ctx.store.driveEmail = it },
                label = { Text(L.s("إيميل الدرايف (قابل للتغيير)", "Drive email (editable)"), fontSize = 12.sp) },
                singleLine = true, shape = RoundedCornerShape(13.dp), modifier = Modifier.fillMaxWidth(),
            )
        }

        SectionTitle(L.s("السرعة وتنظيف البيانات", "Speed & cleanup"))
        CardBox {
            InfoRow(L.s("حجم قاعدة البيانات والصور", "Database + photos size"), sizeText)
            Text(
                L.s("الضغط يزيل الفراغات ويحذف السجلات القديمة جدًا ويضغط الملفات، فالتطبيق يفضل خفيف.", "Compaction removes blanks, prunes very old logs and shrinks files so the app stays light."),
                color = Muted, fontSize = 11.5.sp, modifier = Modifier.padding(vertical = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(L.s("ضغط القاعدة", "Compact DB"), Modifier.weight(1f)) {
                    val saved = ctx.store.compact()
                    sizeText = humanSize(ctx.store.totalSize())
                    Toast.makeText(context, L.s("تم الضغط ✅", "Compacted ✅"), Toast.LENGTH_SHORT).show()
                    ctx.bump()
                }
                Surface(
                    shape = RoundedCornerShape(15.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.weight(1f).height(52.dp).clickable { ctx.go(Dest.Sync) },
                ) { Box(contentAlignment = Alignment.Center) { Text(L.s("مزامنة درايف", "Drive sync"), fontWeight = FontWeight.Bold, fontSize = 14.sp) } }
            }
        }

        SectionTitle(L.s("المستخدمون والسجل", "Users & log"))
        CardBox {
            SettingLink(L.s("إدارة المستخدمين", "Manage users")) { ctx.go(Dest.Users) }
            SettingLink(L.s("سجل التغييرات", "Change log")) { ctx.go(Dest.Logs) }
        }

        Spacer(Modifier.height(16.dp))
        Text("MECHANICUS v0.1.0 • " + L.s("صناعة عاصم حسين", "by Assem Hussein"), color = Muted, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(bottom = 26.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
fun StorageOption(title: String, sub: String, external: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.5.dp, if (external) Red else MaterialTheme.colorScheme.outline),
        color = if (external) RedSoft else MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { onClick() },
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(sub, color = Muted, fontSize = 11.sp)
            }
            if (external) Text("✓", color = RedDeep, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun SettingLink(title: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text("›", fontSize = 20.sp, color = Muted)
    }
}

fun humanSize(bytes: Long): String = when {
    bytes > 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
    bytes > 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}

// ------------------------- SYNC -------------------------
@Composable
fun SyncScreen(ctx: AppCtx) {
    val L = ctx.L
    val context = LocalContext.current
    var lastSync by remember { mutableStateOf(L.s("لم تتم بعد", "Not yet")) }
    val files = remember(ctx.version) { ctx.store.dbFiles() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        ScreenBar(title = L.s("المزامنة", "Sync"), onBack = { ctx.go(Dest.Settings) })

        CardBox {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("☁️", fontSize = 40.sp)
                Text(ctx.store.driveEmail, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(L.s("حساب جوجل درايف المرتبط", "Linked Google Drive account"), color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusChipPill("✅ " + L.s("مزامنة تلقائية", "Auto-sync"), GreenSoft, Green)
                    StatusChipPill(L.s("آخر مزامنة: ", "Last sync: ") + lastSync, BlueSoft, Blue)
                }
            }
        }

        CardBox {
            InfoRow(L.s("تغييرات محلية للرفع", "Local changes"), "—")
            InfoRow(L.s("تغييرات جديدة من جهاز تاني", "New from another device"), "—")
            InfoRow(L.s("تعارضات", "Conflicts"), "0")
            Spacer(Modifier.height(10.dp))
            PrimaryButton(L.s("مزامنة الآن", "Sync now")) {
                lastSync = L.s("الآن", "just now")
                Toast.makeText(context, L.s("المزامنة الحقيقية مع درايف تحتاج تسجيل صلاحية جوجل (الخطوة الجاية).", "Real Drive sync needs Google sign-in setup (next step)."), Toast.LENGTH_LONG).show()
            }
        }

        SectionTitle(L.s("ملفات قاعدة البيانات (مقسّمة شهريًا)", "Database files (monthly shards)"))
        CardBox {
            Text(ctx.store.root().name + "/", fontWeight = FontWeight.Black, fontSize = 13.sp)
            Text("index/db central: app.db", color = Muted, fontSize = 12.sp)
            for (f in files) Text("data/" + f.name + "  (" + humanSize(f.length()) + ")", fontSize = 12.sp)
            Text(L.s("الصور محفوظة في مجلد photos، خارج قاعدة البيانات لتبقى خفيفة.", "Photos live in the photos folder, outside the DB, to keep it light."), color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(26.dp))
    }
}

@Composable
fun StatusChipPill(text: String, bg: Color, fg: Color) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(text, color = fg, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
    }
}
