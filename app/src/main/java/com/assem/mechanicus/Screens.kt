package com.assem.mechanicus

import android.Manifest
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ScreenBar(title: String, onBack: (() -> Unit)? = null, action: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back") }
        }
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f).padding(start = if (onBack == null) 2.dp else 4.dp))
        if (action != null) action()
    }
}

@Composable
fun PhotoThumb(path: String?, onClick: () -> Unit) {
    val context = LocalContext.current
    val bmp = remember(path) {
        try {
            when {
                path.isNullOrBlank() -> null
                path.startsWith("content://") -> BitmapFactory.decodeStream(context.contentResolver.openInputStream(Uri.parse(path)))?.asImageBitmap()
                else -> {
                    val p = if (path.contains("/")) File(path) else File(Store(context).photosDir(), path)
                    if (p.exists() && p.length() > 0) BitmapFactory.decodeFile(p.absolutePath)?.asImageBitmap() else null
                }
            }
        } catch (_: Exception) { null }
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().height(150.dp).clickable { onClick() },
    ) {
        if (bmp != null) {
            Image(bmp, contentDescription = "plate", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📷", fontSize = 30.sp)
                    Text(LocalLang.current.s("التقط صورة لرقم اللوحة", "Take a plate photo"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Muted)
                }
            }
        }
    }
}

fun todayString(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

fun money(v: Double): String = String.format(Locale.US, "%,.0f", v)

// ------------------------- HOME -------------------------
@Composable
fun HomeScreen(ctx: AppCtx) {
    val L = ctx.L
    val stats = remember(ctx.version) { ctx.store.stats() }
    val recent = remember(ctx.version) { ctx.store.listCars("all", "").take(4) }
    val context = LocalContext.current
    var showSummary by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 20.dp)) {
        item {
            ScreenBar(title = L.s("مرحبًا، ", "Hello, ") + ctx.store.activeUserName)
        }
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF18181B), Color(0xFFB91C1C))))
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(L.s("حالة المزامنة", "Sync status"), color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
                            Text("☁️ " + L.s("جاهز للمزامنة", "Ready to sync"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        Surface(
                            color = Color.White.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.clickable { ctx.go(Dest.Sync) },
                        ) { Text(L.s("مزامنة", "Sync"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) }
                    }
                }
            }
        }
        item { SectionTitle(L.s("إجراءات سريعة", "Quick actions")) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                QuickAction("🚗", L.s("إضافة عربية", "Add car"), Modifier.weight(1f)) { ctx.go(Dest.Edit(null)) }
                QuickAction("💰", L.s("تسجيل دفعة", "Record payment"), Modifier.weight(1f)) { ctx.go(Dest.Payments) }
                QuickAction("📤", L.s("تصدير جلسة اليوم", "Export today"), Modifier.weight(1f)) {
                    Transfer.exportToday(context, ctx.store, L, ctx.store.activeUserName)
                }
            }
        }
        item {
            Spacer(Modifier.height(11.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                QuickAction("🔍", L.s("بحث عن عميل", "Find customer"), Modifier.weight(1f)) { ctx.go(Dest.Cars) }
                QuickAction("📜", L.s("السجل", "Log"), Modifier.weight(1f)) { ctx.go(Dest.Logs) }
                QuickAction("☁️", L.s("صلاحيات درايف", "Drive"), Modifier.weight(1f)) { ctx.go(Dest.Sync) }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().clickable { showSummary = !showSummary }.padding(top = 18.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(L.s("ملخص اليوم (الأرقام)", "Today's summary (numbers)"), fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(if (showSummary) "▾" else "▸", fontSize = 18.sp, color = Muted)
            }
        }
        if (showSummary) item {
            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    StatCard("🚗", stats.carsToday.toString(), L.s("عربيات النهاردة", "Cars today"), Red, RedSoft, Modifier.weight(1f))
                    StatCard("🔧", stats.inWork.toString(), L.s("شغل جاري", "In progress"), Color(0xFFB45309), AmberSoft, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    StatCard("💵", money(stats.todayIncome), L.s("تحصيل اليوم", "Today's income"), Green, GreenSoft, Modifier.weight(1f))
                    StatCard("📅", money(stats.monthIncome), L.s("إجمالي الشهر", "This month"), Blue, BlueSoft, Modifier.weight(1f))
                }
            }
        }
        item { SectionTitle(L.s("أحدث العربيات", "Recent cars")) }
        if (recent.isEmpty()) item { EmptyNote(L.s("لا يوجد عربيات بعد", "No cars yet")) }
        items(recent) { row -> CarListItem(row) { ctx.go(Dest.Detail(row.id)) } }
    }
}

@Composable
fun QuickAction(emoji: String, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.clickable { onClick() },
    ) {
        Column(Modifier.padding(13.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 22.sp)
            Spacer(Modifier.height(4.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
fun EmptyNote(text: String) {
    CardBox { Text(text, color = Muted, fontSize = 13.sp, modifier = Modifier.fillMaxWidth()) }
}

// ------------------------- CARS -------------------------
@Composable
fun CarsScreen(ctx: AppCtx) {
    val L = ctx.L
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("all") }
    val rows = remember(ctx.version, query, filter) { ctx.store.listCars(filter, query) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        ScreenBar(title = L.s("العربيات", "Cars"), action = {
            IconButton(onClick = { ctx.go(Dest.Edit(null)) }) { Icon(Icons.Filled.Add, contentDescription = "add") }
        })
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            placeholder = { Text(L.s("ابحث برقم اللوحة أو التليفون أو تاريخ الدخول", "Search plate, phone or entry date")) },
            singleLine = true,
            shape = RoundedCornerShape(13.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
            val opts = listOf("all" to L.s("الكل", "All"), Status.WORK to L.s("جاري", "Working"), Status.DONE to L.s("تم التسليم", "Delivered"), Status.LATE to L.s("متأخر", "Late"))
            for ((k, lab) in opts) {
                FilterChip(selected = filter == k, onClick = { filter = k }, label = { Text(lab, fontSize = 12.sp) })
            }
        }
        Spacer(Modifier.height(8.dp))
        if (rows.isEmpty()) EmptyNote(L.s("لا نتائج", "No results"))
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 20.dp)) {
            items(rows) { row -> CarListItem(row) { ctx.go(Dest.Detail(row.id)) } }
        }
    }
}

@Composable
fun CarListItem(row: IndexRow, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { onClick() },
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(RedSoft),
                contentAlignment = Alignment.Center,
            ) { Text(row.customer.take(1).ifBlank { "?" }, color = RedDeep, fontWeight = FontWeight.Black) }
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlateText(row.plate)
                    Spacer(Modifier.size(6.dp))
                    StatusChip(row.status)
                }
                Text(row.customer, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 5.dp))
                Text(
                    listOf(row.phone, row.adate).filter { it.isNotBlank() }.joinToString("   •   "),
                    color = Muted, fontSize = 11.5.sp,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(money(row.pay), color = Green, fontWeight = FontWeight.Black, fontSize = 15.sp)
                Text(LocalLang.current.s("ج.م", "EGP"), color = Muted, fontSize = 10.sp)
            }
        }
    }
}

// ------------------------- EDIT CAR -------------------------
@Composable
fun EditCarScreen(ctx: AppCtx, id: String?) {
    val L = ctx.L
    val context = LocalContext.current
    val existing = remember(id) { id?.let { ctx.store.carDetail(it) } }

    var plate by remember { mutableStateOf(existing?.plate ?: "") }
    var engine by remember { mutableStateOf(existing?.engine ?: "") }
    var odometer by remember { mutableStateOf(existing?.odometer ?: "") }
    var make by remember { mutableStateOf(existing?.make ?: "") }
    var model by remember { mutableStateOf(existing?.model ?: "") }
    var delivery by remember { mutableStateOf(existing?.deliveryDate ?: todayString()) }
    var customer by remember { mutableStateOf(existing?.customer ?: "") }
    var phone by remember { mutableStateOf(existing?.phone ?: "") }
    var worker by remember { mutableStateOf(existing?.worker ?: ctx.store.activeUserName) }
    var intake by remember { mutableStateOf(existing?.intake ?: "") }
    var parts by remember { mutableStateOf(existing?.parts?.joinToString("، ") ?: "") }
    var photo by remember { mutableStateOf(existing?.photo) }
    var payNow by remember { mutableStateOf("") }
    var payStage by remember { mutableStateOf("on") }
    var pendingFile by remember { mutableStateOf<File?>(null) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = pendingFile
        if (ok && f != null) {
            val name = PhotoStore.PREFIX + System.currentTimeMillis() + ".jpg"
            val wm = PhotoStore.build(context, ctx.store, f, name)
            f.delete()
            if (wm != null) {
                Gallery.saveFile(context, wm, wm.name)
                photo = wm.name
            }
        } else {
            f?.delete()
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val name = PhotoStore.PREFIX + System.currentTimeMillis() + ".jpg"
            val wm = PhotoStore.buildUri(context, ctx.store, uri, name)
            if (wm != null) {
                Gallery.saveFile(context, wm, wm.name)
                photo = wm.name
            }
        }
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT < 29 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            permLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        ScreenBar(title = if (id == null) L.s("إضافة عربية", "New car") else L.s("تعديل عربية", "Edit car"), onBack = { ctx.go(Dest.Home) })

        CardBox {
            Field(L.s("رقم اللوحة", "Plate number"), plate, { plate = it })
            Spacer(Modifier.height(10.dp))
            PhotoThumb(photo) {
                val f = File(ctx.store.photosDir(), "plate_${System.currentTimeMillis()}.jpg")
                pendingFile = f
                val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", f)
                camera.launch(uri)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton(L.s("📷 كاميرا", "📷 Camera"), Modifier.weight(1f)) {
                    val f = File(ctx.store.photosDir(), "plate_${System.currentTimeMillis()}.jpg")
                    pendingFile = f
                    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", f)
                    camera.launch(uri)
                }
                GhostButton(L.s("🖼️ من المعرض", "🖼️ Gallery"), Modifier.weight(1f)) { gallery.launch("image/*") }
            }
        }

        CardBox {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { Field(L.s("رقم الموتور", "Engine no."), engine, { engine = it }, keyboardType = KeyboardType.Number, digitsOnly = true) }
                Box(Modifier.weight(1f)) { Field(L.s("العداد (كم)", "Odometer"), odometer, { odometer = it }, keyboardType = KeyboardType.Number, digitsOnly = true) }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { Field(L.s("النوع", "Make"), make, { make = it }) }
                Box(Modifier.weight(1f)) { Field(L.s("الموديل", "Model"), model, { model = it }) }
            }
            Spacer(Modifier.height(12.dp))
            if (id == null) {
                DateField3(L.s("تاريخ الدخول", "Entry date"), delivery, { delivery = it })
            } else {
                ReadOnlyField(L.s("تاريخ الدخول (لا يمكن تعديله)", "Entry date (not editable)"), delivery)
            }
            Spacer(Modifier.height(12.dp))
            Field(L.s("تليفون العميل", "Phone"), phone, { phone = it }, keyboardType = KeyboardType.Phone, digitsOnly = true)
            Spacer(Modifier.height(10.dp))
            Field(L.s("اسم العميل", "Customer name"), customer, { customer = it })
            Spacer(Modifier.height(10.dp))
            Field(L.s("العامل المسؤول", "Worker"), worker, { worker = it })
            Spacer(Modifier.height(10.dp))
            Field(L.s("ملاحظات الدخول", "Intake notes"), intake, { intake = it }, singleLine = false, minLines = 3)
            Spacer(Modifier.height(10.dp))
            Field(L.s("قطع الغيار (افصل بفاصلة)", "Spare parts (comma separated)"), parts, { parts = it }, singleLine = false, minLines = 2)
        }

        if (id == null) {
            CardBox {
                SectionTitle(L.s("دفعة مبدئية (اختياري)", "Initial payment (optional)"))
                Field(L.s("المبلغ", "Amount"), payNow, { payNow = it }, keyboardType = KeyboardType.Number, digitsOnly = true)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = payStage == "on", onClick = { payStage = "on" }, label = { Text(L.s("عند التسليم", "On delivery"), fontSize = 12.sp) })
                    FilterChip(selected = payStage == "work", onClick = { payStage = "work" }, label = { Text(L.s("أثناء العمل", "During work"), fontSize = 12.sp) })
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        PrimaryButton(L.s("حفظ العربية", "Save car")) {
            if (plate.isBlank() || phone.isBlank() || delivery.isBlank()) {
                Toast.makeText(
                    context,
                    L.s("لازم رقم اللوحة وتليفون العميل وتاريخ الدخول", "Plate number, customer phone and entry date are required"),
                    Toast.LENGTH_LONG,
                ).show()
                return@PrimaryButton
            }
            val carId = existing?.id ?: ctx.store.newId()
            val car = Car(
                id = carId, plate = plate, engine = engine, odometer = odometer, make = make, model = model,
                deliveryDate = delivery, customer = customer, phone = phone, worker = worker, intake = intake,
                status = existing?.status ?: Status.WORK, monthKey = existing?.monthKey ?: "",
                createdAt = existing?.createdAt ?: 0L, updatedAt = 0L, photo = photo,
                parts = parts.split(",", "،").map { it.trim() }.filter { it.isNotEmpty() },
            )
            val saved = ctx.store.saveCar(car)
            val amount = payNow.toDoubleOrNull()
            if (amount != null && amount > 0) {
                ctx.store.addPayment(saved, amount, payStage, ctx.store.activeUserName)
                ctx.store.addLog(ctx.store.activeUserName, "payment", saved.plate, L.s("دفعة مبدئية", "Initial payment") + " " + money(amount))
            }
            ctx.store.addLog(ctx.store.activeUserName, if (id == null) "add" else "edit", saved.plate, L.s("حفظ بيانات العربية", "Saved car data"))
            ctx.bump()
            ctx.requestSync()
            ctx.go(Dest.Home)
        }
        Spacer(Modifier.height(26.dp))
    }
}

// ------------------------- DETAIL -------------------------
@Composable
fun DetailScreen(ctx: AppCtx, id: String) {
    val L = ctx.L
    val context = LocalContext.current
    val car = remember(ctx.version, id) { ctx.store.carDetail(id) }
    if (car == null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) { ScreenBar(L.s("ملف العربية", "Vehicle"), { ctx.go(Dest.Cars) }); EmptyNote(L.s("غير موجودة", "Not found")) }
        return
    }
    val total = car.payments.sumOf { it.amount }
    val owner = ctx.store.isActiveOwner()
    var showDel by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        ScreenBar(title = L.s("ملف العربية", "Vehicle file"), onBack = { ctx.go(Dest.Cars) }, action = {
            Row {
                IconButton(onClick = { Transfer.exportOne(context, car, L, ctx.store.activeUserName) }) { Icon(Icons.Filled.Share, contentDescription = "export") }
                IconButton(onClick = { ctx.go(Dest.Edit(id)) }) { Icon(Icons.Filled.Edit, contentDescription = "edit") }
                IconButton(onClick = {
                    if (owner) {
                        showDel = true
                    } else {
                        ctx.store.hideCar(car)
                        ctx.store.addLog(ctx.store.activeUserName, "hide", car.plate, L.s("إزالة من الجهاز فقط", "Hidden on this phone only"))
                        Toast.makeText(context, L.s("اتشالت من جهازك بس — الحذف النهائي للمالك، والعربية محفوظة على جوجل.", "Removed from your phone only — permanent delete is for the owner; it stays on Google."), Toast.LENGTH_LONG).show()
                        ctx.bump(); ctx.go(Dest.Cars)
                    }
                }) { Icon(Icons.Filled.Delete, contentDescription = "delete", tint = Red) }
            }
        })
        Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFF111827), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(car.plate, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Text("${car.make} ${car.model}", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        PhotoThumb(car.photo) { }
        SectionTitle(L.s("بيانات العميل والعربية", "Customer & vehicle"))
        CardBox {
            InfoRow(L.s("اسم العميل", "Customer"), car.customer)
            InfoRow(L.s("التليفون", "Phone"), car.phone)
            InfoRow(L.s("رقم الموتور", "Engine"), car.engine)
            InfoRow(L.s("العداد", "Odometer"), car.odometer)
            InfoRow(L.s("تاريخ الدخول", "Entry date"), car.deliveryDate)
            InfoRow(L.s("العامل المسؤول", "Worker"), car.worker)
            InfoRow(L.s("ملاحظات الدخول", "Intake notes"), car.intake.ifBlank { "—" })
        }
        SectionTitle(L.s("قطع الغيار", "Spare parts"))
        CardBox { Text(if (car.parts.isEmpty()) "—" else car.parts.joinToString(" • "), fontSize = 13.sp) }
        SectionTitle(L.s("المدفوعات", "Payments") + " (" + money(total) + " " + L.s("ج.م", "EGP") + ")")
        CardBox {
            if (car.payments.isEmpty()) Text(L.s("لا مدفوعات بعد", "No payments yet"), color = Muted, fontSize = 13.sp)
            for (p in car.payments.reversed()) {
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(stageLabel(L, p.stage), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(p.userName + " • " + SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(p.ts)), color = Muted, fontSize = 11.sp)
                    }
                    Text(money(p.amount) + " " + L.s("ج.م", "EGP"), color = Green, fontWeight = FontWeight.Black)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(15.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.weight(1f).clickable { ctx.go(Dest.Payments) },
            ) { Text(L.s("تسجيل دفعة", "Add payment"), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.Bold, modifier = Modifier.padding(14.dp)) }
            PrimaryButton(
                text = if (car.status == Status.DONE) L.s("إرجاع لجاري", "Reopen") else L.s("تم التسليم", "Mark delivered"),
                modifier = Modifier.weight(1f),
            ) {
                val ns = if (car.status == Status.DONE) Status.WORK else Status.DONE
                ctx.store.setStatus(car, ns)
                ctx.store.addLog(ctx.store.activeUserName, "status", car.plate, ns)
                ctx.bump()
            }
        }
        Spacer(Modifier.height(26.dp))
    }

    if (showDel) {
        AlertDialog(
            onDismissRequest = { showDel = false },
            title = { Text(L.s("حذف نهائي", "Delete permanently")) },
            text = {
                Text(
                    L.s("هتحذف العربية نهائيًا من درايف ومن كل الأجهزة. متأكد؟", "This deletes the car permanently from Drive and all devices. Are you sure?"),
                    fontSize = 14.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDel = false
                    ctx.store.deleteCar(car)
                    ctx.store.addLog(ctx.store.activeUserName, "delete", car.plate, L.s("حذف عربية", "Deleted car"))
                    ctx.bump(); ctx.go(Dest.Cars)
                }) { Text(L.s("حذف", "Delete"), fontWeight = FontWeight.Bold, color = Red) }
            },
            dismissButton = { TextButton(onClick = { showDel = false }) { Text(L.s("إلغاء", "Cancel")) } },
        )
    }
}

fun stageLabel(L: Lang, stage: String): String = when (stage) {
    "on" -> L.s("عند التسليم", "On delivery")
    "work" -> L.s("أثناء العمل", "During work")
    else -> L.s("تسليم نهائي", "Final delivery")
}
