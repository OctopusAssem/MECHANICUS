package com.assem.mechanicus

import android.widget.Toast
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
                Field(L.s("المبلغ (ج.م)", "Amount (EGP)"), amount, { amount = it }, keyboardType = KeyboardType.Number, digitsOnly = true)
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
    var resetTarget by remember { mutableStateOf<User?>(null) }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 20.dp)) {
        item { ScreenBar(title = L.s("المستخدمون", "Users"), onBack = { ctx.go(Dest.Settings) }) }
        if (users.isNotEmpty()) item {
            Text(
                L.s("لو موظف نسي الرقم السري، دوس على القفل جنبه وحط باسورد المسؤول والرقم الجديد.", "If an employee forgot his PIN, tap the lock next to him and enter the admin password plus the new PIN."),
                color = Muted, fontSize = 11.5.sp, modifier = Modifier.padding(bottom = 6.dp),
            )
        }
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
                    IconButton(onClick = { resetTarget = u }) {
                        Icon(Icons.Filled.LockReset, contentDescription = "reset PIN", tint = Blue)
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
                Field(L.s("الرقم السري (4 أرقام)", "PIN (4 digits)"), pin, { pin = it }, keyboardType = KeyboardType.Number, digitsOnly = true, maxLen = 4)
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

    resetTarget?.let { target ->
        ResetPinDialog(ctx, target.id, target.name) { resetTarget = null }
    }
}

// Admin-only: change an employee's forgotten PIN. The admin password is the
// owner's secret, so only the owner can do it — even if anyone has the phone.
@Composable
fun ResetPinDialog(ctx: AppCtx, userId: Long, userName: String, onClose: () -> Unit) {
    val L = ctx.L
    val context = LocalContext.current
    var pass by remember { mutableStateOf("") }
    var np by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(L.s("إعادة تعيين الرقم السري", "Reset PIN")) },
        text = {
            Column {
                Text(L.s("للموظف: ", "For: ") + userName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                Text(L.s("اكتب باسورد المسؤول والرقم السري الجديد.", "Enter the admin password and the new PIN."), color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it; err = "" },
                    label = { Text(L.s("باسورد المسؤول", "Admin password"), fontSize = 12.sp) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(13.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Field(L.s("رقم سري جديد (4 أرقام)", "New PIN (4 digits)"), np, { np = it }, keyboardType = KeyboardType.Number, digitsOnly = true, maxLen = 4)
                if (err.isNotEmpty()) Text(err, color = Red, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    !ctx.store.verifyAdminPass(pass) -> err = L.s("باسورد المسؤول غلط", "Wrong admin password")
                    np.length != 4 -> err = L.s("اكتب رقم سري من 4 أرقام", "Enter a 4-digit PIN")
                    else -> {
                        ctx.store.setPin(userId, np)
                        ctx.store.addLog(ctx.store.activeUserName.ifBlank { "admin" }, "user", "", L.s("إعادة تعيين رقم سري لـ ", "Reset PIN for ") + userName)
                        ctx.bump()
                        Toast.makeText(context, L.s("تم تعيين الرقم السري ✅", "PIN updated ✅"), Toast.LENGTH_SHORT).show()
                        onClose()
                    }
                }
            }) { Text(L.s("حفظ", "Save"), fontWeight = FontWeight.Bold, color = Red) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(L.s("إلغاء", "Cancel")) } },
    )
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
    "import" -> L.s("استيراد", "Imported")
    else -> action
}

// ------------------------- SETTINGS -------------------------
@Composable
fun SettingsScreen(ctx: AppCtx) {
    val L = ctx.L
    val context = LocalContext.current
    var external by remember { mutableStateOf(ctx.store.useExternal) }
    var email by remember { mutableStateOf(ctx.store.driveEmail) }
    var sizeText by remember { mutableStateOf(humanSize(ctx.store.totalSize())) }
    var newPin by remember { mutableStateOf("") }
    var folderId by remember { mutableStateOf(ctx.store.driveFolderId) }
    var adminNow by remember { mutableStateOf("") }
    var adminNew by remember { mutableStateOf("") }
    var dbAction by remember { mutableStateOf("") }
    var dbPass by remember { mutableStateOf("") }
    var dbErr by remember { mutableStateOf("") }
    var dbOn by remember { mutableStateOf(ctx.store.dbProtected) }
    var syncKey by remember { mutableStateOf("") }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val user = ctx.store.activeUserName.ifBlank { "import" }
                val n = Transfer.importUri(context, ctx.store, uri, user)
                ctx.store.addLog(user, "import", "", "Imported $n")
                ctx.bump()
                Toast.makeText(context, L.s("تم استيراد $n عربية ✅", "Imported $n cars ✅"), Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, L.s("الملف غير صالح", "Invalid file"), Toast.LENGTH_LONG).show()
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val n = Backup.restore(context, ctx.store, uri)
                ctx.store.addLog(ctx.store.activeUserName, "restore", "", "Restored $n db files")
                sizeText = humanSize(ctx.store.totalSize())
                ctx.bump()
                Toast.makeText(context, L.s("تمت استعادة النسخة ($n ملف) ✅", "Backup restored ($n files) ✅"), Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, L.s("ملف النسخة غير صالح", "Invalid backup file"), Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        ScreenBar(title = L.s("الإعدادات", "Settings"))

        SectionTitle(L.s("مكان حفظ قاعدة البيانات", "Database storage"))
        CardBox {
            StorageOption(L.s("الذاكرة الداخلية", "Internal storage"), L.s("أسرع للاستخدام اليومي", "Fastest for daily use"), external = !external) {
                val ok = ctx.store.switchStorage(false)
                external = ctx.store.useExternal
                sizeText = humanSize(ctx.store.totalSize())
                ctx.bump()
                Toast.makeText(context, if (ok) L.s("تم التبديل للذاكرة الداخلية ✅", "Switched to internal storage ✅") else L.s("فشل التبديل", "Switch failed"), Toast.LENGTH_SHORT).show()
            }
            StorageOption(L.s("الذاكرة الخارجية (SD)", "External storage (SD)"), L.s("مناسبة للنقل بفلاشة", "Good for moving via USB"), external = external) {
                val ok = ctx.store.switchStorage(true)
                external = ctx.store.useExternal
                sizeText = humanSize(ctx.store.totalSize())
                ctx.bump()
                Toast.makeText(context, if (ok) L.s("تم التبديل للذاكرة الخارجية ونقل البيانات ✅", "Switched to external storage and moved data ✅") else L.s("مفيش ذاكرة خارجية متاحة", "No external storage available"), Toast.LENGTH_LONG).show()
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
                Switch(checked = ctx.dark, onCheckedChange = { ctx.setDark(it) })
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(L.s("صلاحيات ومزامنة جوجل", "Google permissions & sync"), fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(
                    if (ctx.store.driveConnected) L.s("متصل", "Connected") else L.s("غير مرتبط", "Not linked"),
                    color = if (ctx.store.driveConnected) Green else Color(0xFFB45309),
                    fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    modifier = Modifier.clickable { ctx.go(Dest.Sync) },
                )
            }
            OutlinedTextField(
                value = email, onValueChange = { email = it; ctx.store.driveEmail = it },
                label = { Text(L.s("إيميل الدرايف (قابل للتغيير)", "Drive email (editable)"), fontSize = 12.sp) },
                singleLine = true, shape = RoundedCornerShape(13.dp), modifier = Modifier.fillMaxWidth(),
            )
        }

        if (ctx.store.isActiveOwner()) {
        SectionTitle(L.s("مشاركة البيانات بين الحسابات", "Share data between accounts"))
        CardBox {
            Text(
                L.s("افتح فولدر MECHANICUS على درايف من حساب الورشة، اعمله مشاركة (Editor) مع أي حساب جوجل تاني، وبعدين الصق «معرّف الفولدر» هنا في كل الأجهزة — كده كل الأجهزة تشوف نفس البيانات حتى لو كل واحد بحساب جوجل مختلف.",
                    "Open the MECHANICUS folder on Drive from the shop account, share it as Editor with any other Google account, then paste the folder ID here on every phone — so all phones see the same data even with different Google accounts."),
                color = Muted, fontSize = 11.5.sp, modifier = Modifier.padding(bottom = 8.dp),
            )
            Field(L.s("معرّف الفولدر (Folder ID)", "Folder ID"), folderId, { folderId = it })
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(L.s("حفظ المعرّف", "Save ID"), Modifier.weight(1f)) {
                    val v = folderId.trim().substringAfterLast("/folders/").substringAfterLast("id=").trim('/')
                    if (v.isBlank()) {
                        ctx.store.driveFolderId = ""
                        Toast.makeText(context, L.s("اتمسح ✅", "Cleared ✅"), Toast.LENGTH_SHORT).show()
                    } else {
                        ctx.store.driveFolderId = v
                        folderId = v
                        Toast.makeText(context, L.s("تم الحفظ ✅", "Saved ✅"), Toast.LENGTH_SHORT).show()
                    }
                    ctx.bump()
                }
                if (folderId.isNotBlank()) {
                    GhostButton(L.s("فتح على درايف", "Open on Drive"), Modifier.weight(1f)) {
                        val id = folderId.trim().substringAfterLast("/folders/").substringAfterLast("id=").trim('/')
                        val u = "https://drive.google.com/drive/folders/$id"
                        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) } catch (_: Exception) {}
                    }
                }
            }
            if (ctx.store.driveFolderId.isNotBlank()) {
                Text(
                    L.s("المعرّف الحالي: ", "Current ID: ") + ctx.store.driveFolderId,
                    color = Muted, fontSize = 10.5.sp, modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        }

        SectionTitle(L.s("الأمان", "Security"))
        CardBox {
            Text(
                L.s("غيّر الرقم السري بتاعك — المستخدم الحالي: ", "Change your PIN — current user: ") + ctx.store.activeUserName,
                color = Muted, fontSize = 12.sp,
            )
            Spacer(Modifier.height(8.dp))
            Field(L.s("رقم سري جديد (4 أرقام)", "New PIN (4 digits)"), newPin, { newPin = it }, keyboardType = KeyboardType.Number, digitsOnly = true, maxLen = 4)
            Spacer(Modifier.height(8.dp))
            PrimaryButton(L.s("حفظ الرقم السري", "Save PIN")) {
                if (newPin.length == 4 && ctx.store.activeUserId > 0) {
                    ctx.store.setPin(ctx.store.activeUserId, newPin)
                    ctx.store.addLog(ctx.store.activeUserName, "user", "", L.s("تغيير الرقم السري", "Changed PIN"))
                    newPin = ""
                    ctx.requestSync()
                    Toast.makeText(context, L.s("تم تغيير الرقم السري ✅", "PIN changed ✅"), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, L.s("اكتب 4 أرقام", "Enter 4 digits"), Toast.LENGTH_SHORT).show()
                }
            }
        }

        if (ctx.store.isActiveOwner()) {
        SectionTitle(L.s("مزامنة الشركة", "Company sync"))
        CardBox {
            val configured = ServiceAuth.isConfigured(context)
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (configured) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
                    contentDescription = null,
                    tint = if (configured) Green else Color(0xFFB45309),
                )
                Spacer(Modifier.size(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (configured) L.s("المزامنة مفعّلة على الجهاز ده", "Sync is active on this device")
                        else L.s("المزامنة محتاجة مفتاح الشركة", "Sync needs the company key"),
                        fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    )
                    Text(
                        if (configured) L.s("متصل بحساب الشركة من غير أي تسجيل دخول جوجل.", "Connected to the company account with no Google sign-in.")
                        else L.s("اكتب مفتاح الشركة (اللي معاك انت بس) مرة واحدة هنا عشان تفعّل المزامنة. من غير المفتاح، اللي معاه البرنامج لوحده مش هيقدر يزامن.", "Enter the company key (only you have it) once here to activate sync. Without it, having the app alone cannot sync."),
                        color = Muted, fontSize = 11.5.sp,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            if (!configured) {
                OutlinedTextField(
                    value = syncKey, onValueChange = { syncKey = it },
                    label = { Text(L.s("مفتاح الشركة", "Company key"), fontSize = 12.sp) },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(13.dp), modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                PrimaryButton(L.s("تفعيل المزامنة", "Activate sync")) {
                    if (ServiceAuth.provision(context, syncKey.trim())) {
                        syncKey = ""
                        ctx.store.driveConnected = true
                        ctx.store.dbAuthorized = true
                        ctx.store.addLog(ctx.store.activeUserName, "user", "", L.s("تفعيل مزامنة الشركة", "Activated company sync"))
                        Toast.makeText(context, L.s("تم تفعيل المزامنة ✅", "Sync activated ✅"), Toast.LENGTH_SHORT).show()
                        ctx.bump()
                        ctx.requestSync()
                    } else {
                        Toast.makeText(context, L.s("مفتاح الشركة غلط", "Wrong company key"), Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Text(ServiceAuth.email(context), color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                GhostButton(L.s("فصل المزامنة عن الجهاز ده", "Disconnect sync on this device")) {
                    ServiceAuth.clear(context)
                    ctx.store.driveConnected = false
                    ctx.bump()
                }
            }
        }

        }

        SectionTitle(L.s("تأمين قاعدة البيانات", "Database protection"))
        CardBox {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (dbOn) Icons.Filled.Lock else Icons.Filled.LockOpen,
                    contentDescription = null,
                    tint = if (dbOn) (if (ctx.store.dbAuthorized) Green else Color(0xFFB45309)) else Muted,
                )
                Spacer(Modifier.size(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (dbOn) L.s("القاعدة محمية", "Database protected") else L.s("القاعدة غير محمية", "Database not protected"),
                        fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    )
                    Text(
                        if (dbOn && ctx.store.dbAuthorized) L.s("هذا الجهاز مصرّح له بالوصول.", "This device is authorized.")
                        else if (dbOn) L.s("هذا الجهاز مش مصرّح — مش هيقدر يزامن أو يتعامل مع القاعدة.", "This device is not authorized — it can't sync or touch the database.")
                        else L.s("لو فعّلت الحماية، البرنامج نفسه بيتقفل على أي جهاز جديد، ولازم تصريح منك بباسورد المسؤول قبل الاستخدام أو المزامنة.", "If you turn protection on, the whole app locks on any new device — it needs your approval with the admin password before use or sync."),
                        color = Muted, fontSize = 11.5.sp,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            if (dbOn && !ctx.store.dbAuthorized) {
                PrimaryButton(L.s("تصريح هذا الجهاز", "Authorize this device")) { dbAction = "auth"; dbPass = ""; dbErr = "" }
                Spacer(Modifier.height(8.dp))
            }
            if (dbOn) {
                GhostButton(L.s("إلغاء الحماية", "Turn off protection")) { dbAction = "off"; dbPass = ""; dbErr = "" }
            } else {
                PrimaryButton(L.s("تفعيل الحماية", "Turn on protection")) { dbAction = "on"; dbPass = ""; dbErr = "" }
            }
        }

        if (ctx.store.isActiveOwner()) {
        SectionTitle(L.s("باسورد المسؤول", "Admin password"))
        CardBox {
            Text(
                L.s("ده الباسورد السري اللي بيحمي قاعدة البيانات والصلاحيات المطلقة (زي إعادة تعيين رقم موظف نسي). افتراضيًا 5555 — غيّره لباسورد خاص بيك.", "This secret protects the database and absolute permissions (like resetting a forgotten employee PIN). Default is 5555 — change it to your own."),
                color = Muted, fontSize = 11.5.sp, modifier = Modifier.padding(bottom = 8.dp),
            )
            OutlinedTextField(
                value = adminNow, onValueChange = { adminNow = it },
                label = { Text(L.s("الباسورد الحالي", "Current password"), fontSize = 12.sp) },
                singleLine = true, visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(13.dp), modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = adminNew, onValueChange = { adminNew = it },
                label = { Text(L.s("الباسورد الجديد", "New password"), fontSize = 12.sp) },
                singleLine = true, visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(13.dp), modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            PrimaryButton(L.s("حفظ باسورد المسؤول", "Save admin password")) {
                when {
                    !ctx.store.verifyAdminPass(adminNow) -> Toast.makeText(context, L.s("الباسورد الحالي غلط", "Wrong current password"), Toast.LENGTH_SHORT).show()
                    adminNew.trim().length < 4 -> Toast.makeText(context, L.s("اكتب باسورد جديد 4 حروف أو أرقام على الأقل", "Enter a new password of at least 4 characters"), Toast.LENGTH_SHORT).show()
                    else -> {
                        ctx.store.adminPass = adminNew.trim()
                        ctx.store.addLog(ctx.store.activeUserName, "user", "", L.s("تغيير باسورد المسؤول", "Changed admin password"))
                        adminNow = ""; adminNew = ""
                        Toast.makeText(context, L.s("تم تغيير باسورد المسؤول ✅", "Admin password changed ✅"), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        }

        SectionTitle(L.s("مشاركة البرنامج", "Share the app"))
        CardBox {
            Text(
                L.s("ابعت البرنامج نفسه كـ APK أو كملف مضغوط ZIP على واتساب أو أي حاجة، عشان يتسطّب على جهاز جديد.", "Send the app itself as an APK or a ZIP to WhatsApp or anywhere, so it can be installed on a new phone."),
                color = Muted, fontSize = 11.5.sp, modifier = Modifier.padding(bottom = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(L.s("مشاركة APK", "Share APK"), Modifier.weight(1f)) {
                    try { AppShare.shareApk(context, L.s("تطبيق MECHANICUS", "MECHANICUS app")) }
                    catch (e: Exception) { Toast.makeText(context, L.s("تعذر المشاركة", "Share failed"), Toast.LENGTH_SHORT).show() }
                }
                GhostButton(L.s("مشاركة ZIP", "Share ZIP"), Modifier.weight(1f)) {
                    try { AppShare.shareZip(context, L.s("تطبيق MECHANICUS", "MECHANICUS app")) }
                    catch (e: Exception) { Toast.makeText(context, L.s("تعذر المشاركة", "Share failed"), Toast.LENGTH_SHORT).show() }
                }
            }
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

        SectionTitle(L.s("تصدير واستيراد", "Export & import"))
        CardBox {
            Text(
                L.s(
                    "صدّر جلسة كملف (.mech) وابعته لأي حد عنده التطبيق — يفتحه ويعمله استيراد عنده.",
                    "Export a session as a .mech file and send it to anyone using the app — they can open and import it.",
                ),
                color = Muted, fontSize = 11.5.sp, modifier = Modifier.padding(vertical = 6.dp),
            )
            PrimaryButton(L.s("تصدير جلسة اليوم", "Export today's session")) {
                Transfer.exportToday(context, ctx.store, L, ctx.store.activeUserName)
            }
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(15.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth().height(52.dp).clickable { Transfer.exportAll(context, ctx.store, L, ctx.store.activeUserName) },
            ) { Box(contentAlignment = Alignment.Center) { Text(L.s("تصدير كل البيانات", "Export all data"), fontWeight = FontWeight.Bold, fontSize = 15.sp) } }
            Spacer(Modifier.height(8.dp))
            PrimaryButton(L.s("استيراد ملف جلسة", "Import a session file")) {
                importLauncher.launch(arrayOf("*/*"))
            }
        }

        SectionTitle(L.s("نسخة احتياطية لقاعدة البيانات", "Database backup"))
        CardBox {
            Text(
                L.s(
                    "خد نسخة كاملة من قاعدة البيانات الحالية (كل العربيات والدفعات) واحفظها عندك أو ابعتها. وانشتغلت بيها على موبايل تاني اعمل استعادة من نفس الملف.",
                    "Take a full copy of the current database (all cars and payments) and keep or send it. To move it to another phone, restore from that same file.",
                ),
                color = Muted, fontSize = 11.5.sp, modifier = Modifier.padding(vertical = 6.dp),
            )
            PrimaryButton(L.s("أخذ نسخة احتياطية لقاعدة البيانات الحالية", "Take a backup of the current database")) {
                try {
                    Backup.export(context, ctx.store, L.s("نسخة احتياطية MECHANICUS", "MECHANICUS backup"))
                } catch (e: Exception) {
                    Toast.makeText(context, L.s("فشل إنشاء النسخة", "Backup failed"), Toast.LENGTH_LONG).show()
                }
            }
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(15.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth().height(52.dp).clickable { restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
            ) { Box(contentAlignment = Alignment.Center) { Text(L.s("استعادة من نسخة احتياطية", "Restore from a backup"), fontWeight = FontWeight.Bold, fontSize = 15.sp) } }
        }

        SectionTitle(L.s("المستخدمون والسجل", "Users & log"))
        CardBox {
            SettingLink(L.s("إدارة المستخدمين", "Manage users")) { ctx.go(Dest.Users) }
            SettingLink(L.s("سجل التغييرات", "Change log")) { ctx.go(Dest.Logs) }
        }

        SectionTitle(L.s("الجلسة", "Session"))
        CardBox {
            Text(
                L.s("مسجّل الدخول باسم: ", "Signed in as: ") + ctx.store.activeUserName,
                color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                L.s("لو خرجت، ترجع لشاشة الدخول وتكتب اسم المستخدم والرقم السري تاني.", "Logging out returns to the login screen, where you enter any username and PIN again."),
                color = Muted, fontSize = 11.5.sp,
            )
            Spacer(Modifier.height(10.dp))
            GhostButton(L.s("تسجيل الخروج", "Log out")) {
                ctx.store.activeUserId = -1L
                ctx.store.activeUserName = ""
                ctx.store.adminMode = false
                ctx.store.addLog("", "logout", "", L.s("تسجيل خروج", "Logged out"))
                ctx.bump()
                ctx.go(Dest.Login)
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("MECHANICUS v" + appVersion(context) + " • " + L.s("صناعة عاصم حسين", "by Assem Hussein"), color = Muted, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(bottom = 26.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }

    if (dbAction.isNotBlank()) {
        val title = when (dbAction) {
            "on" -> L.s("تفعيل حماية قاعدة البيانات", "Turn on database protection")
            "off" -> L.s("إلغاء حماية قاعدة البيانات", "Turn off database protection")
            else -> L.s("تصريح هذا الجهاز", "Authorize this device")
        }
        AlertDialog(
            onDismissRequest = { dbAction = "" },
            title = { Text(title) },
            text = {
                Column {
                    Text(
                        when (dbAction) {
                            "on" -> L.s("بعد التفعيل، أي جهاز تاني لازم تصريح منك بباسورد المسؤول قبل ما يزامن أو يتعامل مع القاعدة.", "After turning it on, any other device needs your approval with the admin password before it can sync or touch the database.")
                            "off" -> L.s("هيرجع أي حد يقدر يزامن. اكتب باسورد المسؤول للتأكيد.", "This lets anyone sync again. Enter the admin password to confirm.")
                            else -> L.s("اكتب باسورد المسؤول لتصريح الجهاز ده بالوصول للقاعدة.", "Enter the admin password to authorize this device to access the database.")
                        },
                        color = Muted, fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = dbPass, onValueChange = { dbPass = it; dbErr = "" },
                        label = { Text(L.s("باسورد المسؤول", "Admin password"), fontSize = 12.sp) },
                        singleLine = true, visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(13.dp), modifier = Modifier.fillMaxWidth(),
                    )
                    if (dbErr.isNotEmpty()) Text(dbErr, color = Red, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (!ctx.store.verifyAdminPass(dbPass)) { dbErr = L.s("باسورد المسؤول غلط", "Wrong admin password"); return@TextButton }
                    when (dbAction) {
                        "on" -> { ctx.store.dbProtected = true; ctx.store.dbAuthorized = true }
                        "off" -> { ctx.store.dbProtected = false; ctx.store.dbAuthorized = false }
                        else -> ctx.store.dbAuthorized = true
                    }
                    dbOn = ctx.store.dbProtected
                    ctx.store.addLog(ctx.store.activeUserName, "user", "", L.s("تأمين قاعدة البيانات: ", "Database protection: ") + dbAction)
                    dbAction = ""
                    ctx.bump()
                    ctx.requestSync()
                    Toast.makeText(context, L.s("تم ✅", "Done ✅"), Toast.LENGTH_SHORT).show()
                }) { Text(L.s("تأكيد", "Confirm"), fontWeight = FontWeight.Bold, color = Red) }
            },
            dismissButton = { TextButton(onClick = { dbAction = "" }) { Text(L.s("إلغاء", "Cancel")) } },
        )
    }
}

@Composable
fun StorageOption(title: String, sub: String, external: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.5.dp, if (external) Red else MaterialTheme.colorScheme.outline),
        color = if (external) RedSoft else MaterialTheme.colorScheme.surface,
        shadowElevation = 5.dp,
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

fun appVersion(context: android.content.Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
} catch (e: Exception) {
    ""
}

// ------------------------- SYNC / GOOGLE -------------------------
@Composable
fun SyncScreen(ctx: AppCtx) {
    val L = ctx.L
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = ctx.store
    var account by remember(ctx.version) { mutableStateOf(GDrive.account(context)) }
    val auto = ServiceAuth.isConfigured(context)
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    val files = remember(ctx.version) { store.dbFiles() }
    val lastFmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }
    val lastSync = if (store.lastSync > 0) lastFmt.format(Date(store.lastSync)) else L.s("لم تتم بعد", "Not yet")

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        try {
            val acc = GoogleSignIn.getSignedInAccountFromIntent(res.data).getResult(ApiException::class.java)
            account = acc
            store.driveConnected = true
            acc.email?.let { store.driveEmail = it }
            status = L.s("تم ربط حساب جوجل ومنح صلاحية درايف ✅", "Google account linked and Drive permission granted ✅")
            ctx.bump()
        } catch (e: Exception) {
            val code = (e as? ApiException)?.statusCode
            status = L.s("فشل تسجيل الدخول", "Sign-in failed") + (if (code != null) " (code $code)" else "") +
                (if (code == 10) L.s(" — لازم تسجّل بيانات التطبيق في Google Cloud (موجودة تحت).", " — register the app fingerprint in Google Cloud (below).") else "")
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        ScreenBar(title = L.s("صلاحيات ومزامنة جوجل", "Google sync & permissions"), onBack = { ctx.go(Dest.Settings) })

        CardBox {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("☁️", fontSize = 40.sp)
                Text(if (auto) ServiceAuth.email(context) else (account?.email ?: store.driveEmail), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    when {
                        auto -> L.s("متصل تلقائيًا بحساب الشركة — مش مطلوب تسجيل دخول جوجل على الموبايل", "Auto-connected to the company account — no Google sign-in needed on the phone")
                        account != null -> L.s("الحساب مرتبط بصلاحية Google Drive", "Account linked with Google Drive permission")
                        else -> L.s("غير مرتبط — اضغط لربط الحساب وطلب الصلاحية", "Not linked — tap to connect and grant permission")
                    },
                    color = Muted, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                if (auto || account != null) {
                    PrimaryButton(if (busy) L.s("جاري المزامنة...", "Syncing...") else L.s("مزامنة الآن", "Sync now")) {
                        if (!busy) {
                            busy = true
                            status = L.s("جاري المزامنة مع درايف...", "Syncing with Drive...")
                            scope.launch {
                                val res = withContext(Dispatchers.IO) {
                                    try {
                                        val tok = GDrive.token(context)
                                            ?: return@withContext SyncResult(0, 0, L.s("مش قادر أجيب صلاحية الوصول", "Could not get access token"))
                                        SyncEngine.run(context, tok, store)
                                    } catch (e: Exception) {
                                        SyncResult(0, 0, e.message ?: "error")
                                    }
                                }
                                busy = false
                                status = when {
                                    res.error.isBlank() -> L.s("تمت المزامنة ✅ — من جهازك ${res.uploaded} / من السحابة ${res.downloaded}", "Synced ✅ — from this phone ${res.uploaded} / from cloud ${res.downloaded}")
                                    res.error == SyncEngine.LOCKED -> L.s("قاعدة البيانات محمية — لازم تصريح من المسؤول من الإعدادات قبل المزامنة.", "The database is protected — the admin must authorize this device in Settings before syncing.")
                                    else -> L.s("خطأ: ", "Error: ") + res.error
                                }
                                ctx.bump()
                            }
                        }
                    }
                } else {
                    Text(
                        L.s("المزامنة مش مفعّلة على الجهاز ده. افتح الإعدادات واكتب مفتاح الشركة مرة واحدة لتفعيلها.", "Sync isn't active on this device. Open Settings and enter the company key once to activate it."),
                        color = Muted, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton(L.s("فتح الإعدادات لتفعيل المزامنة", "Open Settings to activate sync")) { ctx.go(Dest.Settings) }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatusChipPill("✅ " + L.s("آخر مزامنة: ", "Last sync: ") + lastSync, BlueSoft, Blue)
                    StatusChipPill((if (store.driveConnected) "✅ " else "⚪ ") + L.s("درايف", "Drive"), if (store.driveConnected) GreenSoft else AmberSoft, if (store.driveConnected) Green else Color(0xFFB45309))
                }
                if (status.isNotBlank()) Text(status, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }

        SectionTitle(L.s("إرسال جلسة اليوم (بلوتوث / مشاركة)", "Send today's session (Bluetooth / share)"))
        CardBox {
            Text(L.s("يجهّز تقرير بكل عربيات ودفعات اليوم، وبعدها تختار البلوتوث من قائمة المشاركة.", "Builds a report of today's cars and payments; pick Bluetooth from the share sheet."), color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            PrimaryButton(L.s("إرسال جلسة اليوم", "Send today's session")) {
                Report.share(context, Report.today(store, L.isAr), L.s("تقرير جلسة اليوم", "Today session report"))
            }
        }

        SectionTitle(L.s("بيانات لازمة لتفعيل صلاحية جوجل", "Data needed to enable Google permission"))
        CardBox {
            Text(L.s("لو ظهر خطأ رقم 10، دي بيانات التطبيق اللي تتسجّل في Google Cloud:", "If error 10 appears, register this app data in Google Cloud:"), color = Muted, fontSize = 12.sp)
            InfoRow(L.s("اسم الحزمة", "Package name"), GDrive.PKG)
            Text("SHA-1: " + GDrive.SHA1, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(8.dp))
            PillLink(L.s("نسخ بيانات التسجيل", "Copy registration data")) {
                val clip = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clip.setPrimaryClip(android.content.ClipData.newPlainText("info", "package=${GDrive.PKG}\nsha1=${GDrive.SHA1}\nscope=${GDrive.SCOPE}"))
                Toast.makeText(context, L.s("تم النسخ", "Copied"), Toast.LENGTH_SHORT).show()
            }
        }

        SectionTitle(L.s("ملفات قاعدة البيانات (مقسّمة شهريًا)", "Database files (monthly shards)"))
        CardBox {
            Text(store.root().name + "/ (" + L.s("مجلد على الجهاز", "on-device folder") + ")", fontWeight = FontWeight.Black, fontSize = 13.sp)
            for (f in files) Text(f.name + "  (" + humanSize(f.length()) + ")", fontSize = 12.sp)
            Text(L.s("المزامنة بتدمج العربيات واحدة واحدة حسب آخر تعديل وبتخزّن نسخة مجمّعة (sync.json) على درايف، فمفيش بيانات بتضيع لو أعدت التثبيت أو لو أكتر من موبايل بيستخدموا نفس الحساب.", "Sync merges cars one by one by latest edit and keeps a combined snapshot (sync.json) on Drive, so nothing is lost on reinstall or when several phones share the same account."), color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
        }

        if (account != null) {
            Spacer(Modifier.height(10.dp))
            PillLink(L.s("فصل الحساب (تسجيل خروج درايف)", "Disconnect Google account")) {
                GDrive.signOut(context)
                store.driveConnected = false
                account = null
                status = L.s("تم الفصل", "Disconnected")
                ctx.bump()
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
fun PillLink(text: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(13.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
    ) {
        Text(text, textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
    }
}

@Composable
fun StatusChipPill(text: String, bg: Color, fg: Color) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(text, color = fg, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
    }
}
