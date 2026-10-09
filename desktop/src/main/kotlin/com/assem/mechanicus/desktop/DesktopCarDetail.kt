package com.assem.mechanicus.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assem.mechanicus.Car
import com.assem.mechanicus.Payment
import com.assem.mechanicus.Status
import com.assem.mechanicus.Store

@Composable
fun DetailDesktop(
    store: Store,
    L: Lang,
    version: Int,
    id: String,
    go: (Scr) -> Unit,
    bump: () -> Unit,
) {
    val banner = LocalBanner.current
    val car = remember(version) { store.carDetail(id) }
    var confirmDelete by remember { mutableStateOf(false) }
    var payAmount by remember { mutableStateOf("") }
    var payStage by remember { mutableStateOf("on") }
    val manager = store.isManager()
    val owner = store.isOwner(store.activeUserName)

    if (car == null) {
        ContentScroll { Text(L.s("العربية غير موجودة.", "Car not found.")) }
        return
    }
    val totalPaid = car.payments.sumOf { it.amount }

    ContentScroll {
        TopBar(car.plate, L.s("ملف العربية", "Vehicle file")) {
            GhostButton(L.s("تعديل", "Edit"), Modifier.width(110.dp)) { go(Scr.Edit(car.id)) }
            Spacer(Modifier.width(10.dp))
            if (owner) {
                Surface(
                    shape = RoundedCornerShape(15.dp),
                    color = Color(0xFF3A1418),
                    border = BorderStroke(1.dp, Color(0xFF7A2A30)),
                    modifier = Modifier.height(48.dp).clickable { confirmDelete = true },
                ) { Box(Modifier.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) { Text(L.s("حذف", "Delete"), color = Color(0xFFFCA5A5), fontWeight = FontWeight.Bold) } }
            } else {
                GhostButton(L.s("إخفاء من عندي", "Hide for me"), Modifier.width(140.dp)) {
                    store.hideLocally(car.id); banner(L.s("تم الإخفاء على الجهاز", "Hidden on this device")); go(Scr.Cars)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        CarPhoto(car.photo)
        if (car.photo != null) Spacer(Modifier.height(14.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CardBox(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlateText(car.plate)
                    Spacer(Modifier.width(9.dp))
                    StatusChip(car.status)
                }
                SectionTitle(L.s("بيانات العربية", "Vehicle"))
                InfoRow(L.s("الماركة", "Make"), car.make)
                InfoRow(L.s("الموديل", "Model"), car.model)
                InfoRow(L.s("رقم الموتور", "Engine no."), car.engine)
                InfoRow(L.s("العداد", "Odometer"), car.odometer)
                InfoRow(L.s("تاريخ التسليم", "Delivery date"), car.deliveryDate)
                InfoRow(L.s("الفني", "Technician"), car.worker)
                if (car.intake.isNotBlank()) InfoRow(L.s("ملاحظات الاستلام", "Intake notes"), car.intake)
            }

            CardBox(Modifier.weight(1f)) {
                SectionTitle(L.s("بيانات العميل والعربية", "Customer & vehicle"))
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(L.s("الهاتف", "Phone"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(car.phone.ifBlank { "—" }, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        if (DesktopActions.valid(car.phone)) {
                            Spacer(Modifier.width(10.dp))
                            ContactButtons(car.phone, L)
                        }
                    }
                }
                InfoRow(L.s("العميل", "Customer"), car.customer)
                if (car.parts.isNotEmpty()) {
                    SectionTitle(L.s("القطع", "Parts"))
                    car.parts.forEach { Text("• $it", fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp)) }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        CardBox {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(L.s("تغيير الحالة", "Change status"), Modifier.weight(1f))
                GhostButton(L.s("شغّال", "Working"), Modifier.width(110.dp)) { setStatus(store, bump, banner, L, car, Status.WORK) }
                Spacer(Modifier.width(8.dp))
                GhostButton(L.s("تم التسليم", "Delivered"), Modifier.width(130.dp)) { setStatus(store, bump, banner, L, car, Status.DONE) }
                Spacer(Modifier.width(8.dp))
                GhostButton(L.s("متأخر", "Late"), Modifier.width(100.dp)) { setStatus(store, bump, banner, L, car, Status.LATE) }
            }
        }

        if (manager) {
            Spacer(Modifier.height(14.dp))
            CardBox {
                SectionTitle(L.s("المدفوعات", "Payments"))
                InfoRow(L.s("الإجمالي المدفوع", "Total paid"), money(totalPaid))
                car.payments.forEach { p -> PaymentRow(p, L) }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Field(L.s("المبلغ", "Amount"), payAmount, { payAmount = it }, Modifier.weight(1f), digitsOnly = true)
                    StagePicker(payStage, L) { payStage = it }
                    GhostButton(L.s("تسجيل دفعة", "Add payment"), Modifier.width(140.dp)) {
                        val amt = payAmount.trim().toDoubleOrNull()
                        if (amt == null || amt <= 0) banner(L.s("اكتب مبلغ صحيح", "Enter a valid amount"))
                        else {
                            store.addPayment(car, amt, payStage, store.activeUserName)
                            payAmount = ""
                            bump()
                            banner(L.s("تم تسجيل الدفعة ✅", "Payment recorded ✅"))
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(L.s("حذف العربية؟", "Delete this car?")) },
            text = { Text(L.s("سيتم حذفها من كل الأجهزة عند المزامنة.", "It will be removed everywhere after syncing.")) },
            confirmButton = {
                TextButton(onClick = {
                    store.deleteCar(car); confirmDelete = false; bump(); banner(L.s("تم الحذف", "Deleted")); go(Scr.Cars)
                }) { Text(L.s("حذف", "Delete"), color = Red, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(L.s("إلغاء", "Cancel")) } },
        )
    }
}

private fun setStatus(store: Store, bump: () -> Unit, banner: (String) -> Unit, L: Lang, car: Car, status: String) {
    store.setStatus(car, status)
    store.addLog(store.activeUserName, "status", car.plate, status)
    bump()
    banner(L.s("تم تحديث الحالة", "Status updated"))
}

@Composable
private fun PaymentRow(p: Payment, L: Lang) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("${stageLabel(L, p.stage)} · ${p.userName}", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(money(p.amount), fontSize = 13.5.sp, fontWeight = FontWeight.Black, color = Green)
    }
}

@Composable
private fun StagePicker(stage: String, L: Lang, onSet: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (s in listOf("on", "mid", "final")) {
            Surface(
                shape = RoundedCornerShape(50),
                color = if (stage == s) Red.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, if (stage == s) Red else MaterialTheme.colorScheme.outline),
                modifier = Modifier.clickable { onSet(s) },
            ) { Text(stageLabel(L, s), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)) }
        }
    }
}

@Composable
fun EditDesktop(
    store: Store,
    L: Lang,
    version: Int,
    id: String?,
    go: (Scr) -> Unit,
    bump: () -> Unit,
) {
    val banner = LocalBanner.current
    val existing = remember(version, id) { if (id != null) store.carDetail(id) else null }
    var plate by remember { mutableStateOf(existing?.plate ?: "") }
    var engine by remember { mutableStateOf(existing?.engine ?: "") }
    var odometer by remember { mutableStateOf(existing?.odometer ?: "") }
    var make by remember { mutableStateOf(existing?.make ?: "") }
    var model by remember { mutableStateOf(existing?.model ?: "") }
    var delivery by remember { mutableStateOf(existing?.deliveryDate ?: todayString()) }
    var customer by remember { mutableStateOf(existing?.customer ?: "") }
    var phone by remember { mutableStateOf(existing?.phone ?: "") }
    var worker by remember { mutableStateOf(existing?.worker ?: store.activeUserName) }
    var intake by remember { mutableStateOf(existing?.intake ?: "") }
    var parts by remember { mutableStateOf(existing?.parts?.joinToString("\n") ?: "") }
    var firstPay by remember { mutableStateOf("") }

    ContentScroll {
        TopBar(
            if (existing == null) L.s("عربية جديدة", "New car") else L.s("تعديل العربية", "Edit car"),
            L.s("املأ بيانات العربية والعميل", "Fill in the vehicle and customer details"),
        ) {
            GhostButton(L.s("رجوع", "Back"), Modifier.width(110.dp)) { go(if (existing != null) Scr.Detail(existing.id) else Scr.Cars) }
        }

        Spacer(Modifier.height(16.dp))
        CardBox {
            SectionTitle(L.s("بيانات العربية", "Vehicle"))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field(L.s("رقم اللوحة *", "Plate *"), plate, { plate = it }, Modifier.weight(1f))
                Field(L.s("الماركة", "Make"), make, { make = it }, Modifier.weight(1f))
                Field(L.s("الموديل", "Model"), model, { model = it }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field(L.s("رقم الموتور", "Engine no."), engine, { engine = it }, Modifier.weight(1f))
                Field(L.s("العداد", "Odometer"), odometer, { odometer = it }, Modifier.weight(1f), digitsOnly = true)
                Field(L.s("تاريخ التسليم (سنة-شهر-يوم)", "Delivery date (yyyy-mm-dd)"), delivery, { delivery = it }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Field(L.s("ملاحظات الاستلام", "Intake notes"), intake, { intake = it }, singleLine = false, minLines = 2)

            SectionTitle(L.s("بيانات العميل", "Customer"))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field(L.s("اسم العميل *", "Customer name *"), customer, { customer = it }, Modifier.weight(1f))
                Field(L.s("الهاتف *", "Phone *"), phone, { phone = it }, Modifier.weight(1f), keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone)
                Field(L.s("الفني", "Technician"), worker, { worker = it }, Modifier.weight(1f))
            }

            SectionTitle(L.s("القطع", "Parts"))
            Field(L.s("قطعة في كل سطر", "One part per line"), parts, { parts = it }, singleLine = false, minLines = 3)

            if (existing == null) {
                SectionTitle(L.s("دفعة أولى (اختياري)", "First payment (optional)"))
                Field(L.s("المبلغ", "Amount"), firstPay, { firstPay = it }, Modifier.width(220.dp), digitsOnly = true)
            }

            Spacer(Modifier.height(16.dp))
            PrimaryButton(L.s("حفظ", "Save")) {
                if (plate.isBlank() || customer.isBlank() || phone.isBlank()) {
                    banner(L.s("رقم اللوحة والعميل والهاتف مطلوبين", "Plate, customer and phone are required"))
                } else {
                    val now = System.currentTimeMillis()
                    val car = Car(
                        id = existing?.id ?: store.newId(),
                        plate = plate.trim(),
                        engine = engine.trim(),
                        odometer = odometer.trim(),
                        make = make.trim(),
                        model = model.trim(),
                        deliveryDate = delivery.trim(),
                        customer = customer.trim(),
                        phone = phone.trim(),
                        worker = worker.trim(),
                        intake = intake.trim(),
                        status = existing?.status ?: Status.WORK,
                        monthKey = existing?.monthKey ?: "",
                        createdAt = existing?.createdAt ?: now,
                        updatedAt = now,
                        photo = existing?.photo,
                        parts = parts.split("\n").map { it.trim() }.filter { it.isNotBlank() },
                    )
                    val saved = store.saveCar(car)
                    val amt = firstPay.trim().toDoubleOrNull()
                    if (existing == null && amt != null && amt > 0) store.addPayment(saved, amt, "on", store.activeUserName)
                    store.addLog(store.activeUserName, if (existing == null) "create" else "update", saved.plate, "")
                    bump()
                    banner(L.s("تم الحفظ ✅", "Saved ✅"))
                    go(Scr.Detail(saved.id))
                }
            }
        }
    }
}
