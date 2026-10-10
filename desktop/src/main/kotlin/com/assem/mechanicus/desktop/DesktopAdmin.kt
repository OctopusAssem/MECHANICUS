package com.assem.mechanicus.desktop

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.LaunchedEffect
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
import com.assem.mechanicus.AutoSync
import com.assem.mechanicus.Platform
import com.assem.mechanicus.ServiceAuth
import com.assem.mechanicus.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsDesktop(
    store: Store,
    L: Lang,
    version: Int,
    scope: CoroutineScope,
    bump: () -> Unit,
    setLang: (String) -> Unit,
    isAr: Boolean,
    go: (Scr) -> Unit,
) {
    val banner = LocalBanner.current
    val owner = store.isActiveOwner()
    val ownerName = store.isOwner(store.activeUserName)
    val configured = remember(version) { ServiceAuth.isConfigured() }
    var key by remember { mutableStateOf("") }
    var oldPass by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }
    var dbPass by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }
    var confirmUn by remember { mutableStateOf(false) }

    // The owner is the only one who reads the comments, so opening Settings marks
    // them as seen (clears the "new comments" badge).
    LaunchedEffect(version, owner) { if (owner) store.markCommentsSeen() }

    ContentScroll {
        TopBar(L.s("الإعدادات", "Settings"), L.s("النسخة ${Platform.version}", "Version ${Platform.version}"))

        SectionTitle(L.s("اللغة", "Language"))
        CardBox {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(L.s("العربية", "Arabic"), isAr) { setLang("ar") }
                FilterChip("English", !isAr) { setLang("en") }
            }
        }

        if (owner) {
        SectionTitle(L.s("مزامنة الشركة", "Company sync"))
        CardBox {
            if (!configured) {
                Text(
                    L.s(
                        "أدخل مفتاح الشركة مرة واحدة على هذا الجهاز لتفعيل المزامنة الصامتة بدون تسجيل دخول جوجل.",
                        "Enter the company key once on this device to enable silent sync with no Google sign-in.",
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.5.sp,
                )
                Spacer(Modifier.height(10.dp))
                Field(L.s("مفتاح الشركة", "Company key"), key, { key = it })
                Spacer(Modifier.height(10.dp))
                PrimaryButton(L.s("تفعيل المزامنة", "Activate sync")) {
                    if (ServiceAuth.provision(key.trim())) {
                        store.driveConnected = true
                        scope.launch {
                            val r = withContext(Dispatchers.IO) { runCatching { AutoSync.run(store) }.getOrDefault("") }
                            bump()
                            banner(if (r == AutoSync.SYNCED) L.s("تم التفعيل والمزامنة ✅", "Activated & synced ✅") else L.s("تم التفعيل", "Activated"))
                        }
                        key = ""
                    } else banner(L.s("مفتاح الشركة غلط", "Wrong company key"))
                }
            } else {
                InfoRow(L.s("الحساب", "Account"), ServiceAuth.email())
                InfoRow(L.s("المجلد", "Folder"), "MECHANICUS")
                InfoRow(L.s("آخر مزامنة", "Last sync"), if (store.lastSync > 0) java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date(store.lastSync)) else "—")
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton(L.s("مزامنة الآن", "Sync now"), Modifier.weight(1f)) {
                        scope.launch {
                            val r = withContext(Dispatchers.IO) { runCatching { AutoSync.run(store) }.getOrDefault("") }
                            bump()
                            banner(
                                when (r) {
                                    AutoSync.SYNCED -> L.s("تمت المزامنة ✅", "Synced ✅")
                                    AutoSync.PENDING -> L.s("أوفلاين — هيتم تلقائيًا", "Offline — will retry")
                                    AutoSync.LOCKED -> L.s("قاعدة البيانات محمية", "Database protected")
                                    else -> L.s("تعذّرت المزامنة", "Sync failed")
                                }
                            )
                        }
                    }
                    GhostButton(L.s("فصل", "Disconnect"), Modifier.weight(1f)) {
                        ServiceAuth.clear(); store.driveConnected = false; bump(); banner(L.s("تم الفصل", "Disconnected"))
                    }
                }
            }
        }
        }

        if (owner) {
            SectionTitle(L.s("باسورد المسؤول", "Admin password"))
            CardBox {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field(L.s("الباسورد الحالي", "Current password"), oldPass, { oldPass = it }, Modifier.weight(1f))
                    Field(L.s("الباسورد الجديد", "New password"), newPass, { newPass = it }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                PrimaryButton(L.s("تغيير الباسورد", "Change password")) {
                    when {
                        !store.verifyAdminPass(oldPass) -> banner(L.s("الباسورد الحالي غلط", "Current password is wrong"))
                        newPass.trim().length < 4 -> banner(L.s("الباسورد الجديد قصير", "New password is too short"))
                        else -> {
                            store.adminPass = newPass.trim(); oldPass = ""; newPass = ""; bump()
                            banner(L.s("تم تغيير الباسورد ✅", "Password changed ✅"))
                        }
                    }
                }
            }

            SectionTitle(L.s("تأمين قاعدة البيانات", "Database protection"))
            CardBox {
                Text(
                    if (store.dbProtected) L.s("قاعدة البيانات محمية — أي جهاز جديد يحتاج تصريحك.", "Database protected — any new device needs your approval.")
                    else L.s("قاعدة البيانات غير محمية.", "Database is not protected."),
                    fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field(L.s("باسورد المسؤول", "Admin password"), dbPass, { dbPass = it }, Modifier.weight(1f))
                    PrimaryButton(if (store.dbProtected) L.s("إلغاء التأمين", "Turn off") else L.s("تفعيل التأمين", "Turn on"), Modifier.weight(1f)) {
                        if (!store.verifyAdminPass(dbPass)) banner(L.s("باسورد غلط", "Wrong password"))
                        else {
                            store.dbProtected = !store.dbProtected
                            store.dbAuthorized = true
                            dbPass = ""; bump()
                            banner(if (store.dbProtected) L.s("تم التأمين ✅", "Protection on ✅") else L.s("تم إلغاء التأمين", "Protection off"))
                        }
                    }
                }
            }
        }

        SectionTitle(L.s("البيانات", "Data"))
        CardBox {
            InfoRow(L.s("مجلد البيانات", "Data folder"), Platform.dataRoot)
            InfoRow(L.s("الحجم", "Size"), "${store.totalSize() / 1024 / 1024} MB")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton(L.s("فتح المجلد", "Open folder"), Modifier.weight(1f)) {
                    if (!DesktopActions.openPhotosFolder()) banner(L.s("تعذّر فتح المجلد", "Could not open folder"))
                }
                GhostButton(L.s("ضغط القاعدة", "Compact"), Modifier.weight(1f)) {
                    val saved = store.compact().toLongOrNull() ?: 0L
                    bump()
                    banner(L.s("تم التوفير: ${saved / 1024} KB", "Saved: ${saved / 1024} KB"))
                }
                GhostButton(L.s("المستخدمون", "Users"), Modifier.weight(1f)) { go(Scr.Users) }
            }
        }

        SectionTitle(L.s("حول", "About"))
        CardBox {
            Text("MECHANICUS", fontWeight = FontWeight.Black, fontSize = 18.sp)
            Text(L.s("مساعد إصلاح السيارات", "YOUR AUTO REPAIR ASSISTANT"), color = Red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(L.s("نسخة الويندوز — تعمل بدون إنترنت وتتزامن مع درايف لما النت يرجع.", "Windows edition — works offline and syncs to Drive when back online."), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Last section on the last screen: a short comment box about the program.
        SectionTitle(L.s("تعليق على البرنامج", "Comment about the program"))
        CardBox {
            Text(
                L.s(
                    "اكتب ملاحظتك أو اقتراحك عن البرنامج (200 حرف كحد أقصى). بتتحفظ باسمك والوقت وبتتزامن مع باقي الأجهزة.",
                    "Write your feedback or suggestion about the program (200 characters max). Saved with your name and time, and synced with the other devices.",
                ),
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Field(
                L.s("تعليقك", "Your comment"), comment, { comment = it.take(200) },
                singleLine = false, minLines = 3, maxLen = 200,
            )
            Spacer(Modifier.height(6.dp))
            Text("${comment.length}/200", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            PrimaryButton(L.s("إرسال", "Send")) {
                if (store.addComment(store.activeUserName.ifBlank { "—" }, comment)) {
                    comment = ""
                    bump()
                    banner(L.s("تم إرسال تعليقك، شكرًا لك ✅", "Thanks! Your comment was sent ✅"))
                } else banner(L.s("اكتب تعليقك الأول", "Write your comment first"))
            }
        }

        if (owner) {
            val all = remember(version) { store.comments() }
            SectionTitle(L.s("تعليقات المستخدمين (لك فقط)", "User comments (you only)"))
            CardBox {
                if (all.isEmpty()) {
                    Text(L.s("مفيش تعليقات لسه.", "No comments yet."), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.5.sp)
                } else {
                    for (c in all) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(c.userName.ifBlank { "—" }, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(fmtTs(c.ts), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                            }
                            Text(c.body, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        if (owner) {
            SectionTitle(L.s("إزالة البرنامج", "Remove the program"))
            CardBox {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    BlackOctopus(56.dp)
                    Spacer(Modifier.width(14.dp))
                    Text(
                        L.s(
                            "ده هيشيل البرنامج من الجهاز زي إزالة أي برنامج من ويندوز. بياناتك على درايف مش بتتأثر.",
                            "This removes the program from this PC like any Windows uninstall. Your Drive data is not affected.",
                        ),
                        fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(10.dp))
                GhostButton(L.s("إزالة البرنامج", "Uninstall MECHANICUS"), Modifier.fillMaxWidth()) { confirmUn = true }
            }
        }
    }

    if (confirmUn) {
        AlertDialog(
            onDismissRequest = { confirmUn = false },
            icon = { BlackOctopus(64.dp) },
            title = { Text(L.s("تأكيد إزالة البرنامج", "Confirm uninstall")) },
            text = {
                Text(
                    L.s(
                        "هل تريد إزالة MECHANICUS من الجهاز؟ البرنامج هيتقفل وتبدأ عملية الإزالة.",
                        "Remove MECHANICUS from this PC? The app will close and the uninstall will start.",
                    ),
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmUn = false
                    if (DesktopUninstall.launchUninstall()) {
                        banner(L.s("بدأت الإزالة، البرنامج هيتقفل.", "Uninstall started, the app will close."))
                        scope.launch { kotlinx.coroutines.delay(1200); kotlin.system.exitProcess(0) }
                    } else {
                        banner(L.s("مش لاقي برنامج الإزالة — استخدم إعدادات ويندوز > التطبيقات.", "Couldn't find the uninstaller — use Windows Settings > Apps."))
                    }
                }) { Text(L.s("إزالة", "Uninstall"), fontWeight = FontWeight.Bold, color = Red) }
            },
            dismissButton = { TextButton(onClick = { confirmUn = false }) { Text(L.s("إلغاء", "Cancel")) } },
        )
    }
}

private fun fmtTs(ts: Long): String =
    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date(ts))
