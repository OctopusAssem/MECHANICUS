package com.assem.mechanicus.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assem.mechanicus.AutoSync
import com.assem.mechanicus.Platform
import com.assem.mechanicus.ServiceAuth
import com.assem.mechanicus.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface Scr {
    data object Login : Scr
    data object Home : Scr
    data object Cars : Scr
    data object Payments : Scr
    data object Users : Scr
    data object Logs : Scr
    data object Settings : Scr
    data class Detail(val id: String) : Scr
    data class Edit(val id: String?) : Scr
}

val AppGradient = Brush.verticalGradient(listOf(Color(0xFF511722), Color(0xFF2C141C), Color(0xFF1A0F13)))

@Composable
fun DesktopApp(store: Store) {
    var lang by remember { mutableStateOf(store.lang.ifBlank { "ar" }) }
    var dest by remember { mutableStateOf<Scr>(if (store.activeUserId > 0) Scr.Home else Scr.Login) }
    var version by remember { mutableStateOf(0) }
    var banner by remember { mutableStateOf<String?>(null) }
    val isAr = lang == "ar"
    val L = remember(lang) { Lang(isAr) }
    val scope = rememberCoroutineScope()
    val dir = if (isAr) LayoutDirection.Rtl else LayoutDirection.Ltr

    // A newer "*.msi" (in the program folder, or pulled from Drive) is an update.
    var updateFile by remember { mutableStateOf<File?>(null) }
    var updateVersion by remember { mutableStateOf("") }
    var updateDismissedKey by remember { mutableStateOf("") }
    var showUpdate by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            // Read the installer's version off the UI thread (it may invoke the
            // Windows Installer), and pull any published update from Drive.
            val info = withContext(Dispatchers.IO) {
                runCatching { DesktopUpdate.pullFromDrive() }
                runCatching { DesktopUpdate.pending() }.getOrNull()
            }
            if (info != null) {
                val f = info.file
                val key = "${f.absolutePath}:${f.lastModified()}:${f.length()}"
                if (key != updateDismissedKey) {
                    updateFile = f
                    updateVersion = info.version
                    showUpdate = true
                }
            }
            delay(15000)
        }
    }

    LaunchedEffect(Unit) {
        if (ServiceAuth.isConfigured() && !store.driveConnected) store.driveConnected = true
        if (store.driveConnected) {
            val r = withContext(Dispatchers.IO) { runCatching { AutoSync.run(store) }.getOrDefault("") }
            if (r == AutoSync.SYNCED) version++
        }
    }
    LaunchedEffect(banner) {
        if (banner != null) { delay(2600); banner = null }
    }

    CompositionLocalProvider(
        LocalLang provides L,
        LocalBanner provides { banner = it },
        LocalLayoutDirection provides dir,
    ) {
        MechanicusTheme {
            Box(Modifier.fillMaxSize().background(AppGradient)) {
                Box(
                    Modifier.fillMaxWidth().height(420.dp).align(Alignment.TopCenter)
                        .background(Brush.radialGradient(listOf(Color(0x55DC2626), Color(0x00000000)))),
                )
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        if (dest == Scr.Login) {
                            LoginDesktop(store, L, version) { dest = Scr.Home }
                        } else {
                            Row(Modifier.fillMaxSize()) {
                                SideNav(store, L, dest, { version++ }) { d -> dest = d }
                                Column(Modifier.weight(1f).fillMaxSize()) {
                                    Surface(color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground) {
                                        Column(Modifier.fillMaxSize()) {
                                            Box(Modifier.weight(1f)) {
                                                ScreenHost(
                                                    store = store, dest = dest, L = L, version = version,
                                                    scope = scope, isAr = isAr,
                                                    go = { dest = it },
                                                    bump = { version++ },
                                                    setLang = { lang = it; store.lang = it },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    StatusBar(L, isAr)
                }
                banner?.let { msg ->
                    Surface(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 46.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shadowElevation = 10.dp,
                    ) {
                        Text(msg, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (showUpdate && updateFile != null) {
                    UpdateDialog(
                        L = L,
                        version = updateVersion,
                        current = Platform.version,
                        onNow = {
                            val f = updateFile
                            if (f != null && DesktopUpdate.install(f)) {
                                kotlin.system.exitProcess(0)
                            } else {
                                banner = L.s("مش قادر أفتح ملف التحديث", "Couldn't start the update")
                                showUpdate = false
                            }
                        },
                        onLater = {
                            showUpdate = false
                            updateFile?.let { updateDismissedKey = "${it.absolutePath}:${it.lastModified()}:${it.length()}" }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ScreenHost(
    store: Store,
    dest: Scr,
    L: Lang,
    version: Int,
    scope: kotlinx.coroutines.CoroutineScope,
    isAr: Boolean,
    go: (Scr) -> Unit,
    bump: () -> Unit,
    setLang: (String) -> Unit,
) {
    when (dest) {
        Scr.Home -> HomeDesktop(store, L, version, scope, go, bump)
        Scr.Cars -> CarsDesktop(store, L, version, go, bump)
        is Scr.Detail -> DetailDesktop(store, L, version, dest.id, go, bump)
        is Scr.Edit -> EditDesktop(store, L, version, dest.id, go, bump)
        Scr.Payments -> PaymentsDesktop(store, L, version, go, bump)
        Scr.Users -> UsersDesktop(store, L, version, bump)
        Scr.Logs -> LogsDesktop(store, L, version)
        Scr.Settings -> SettingsDesktop(store, L, version, scope, bump, setLang, isAr, go)
        Scr.Login -> {}
    }
}

@Composable
private fun SideNav(store: Store, L: Lang, dest: Scr, bump: () -> Unit, go: (Scr) -> Unit) {
    var confirmOut by remember { mutableStateOf(false) }
    Surface(color = Color(0xFF160A0E), modifier = Modifier.requiredWidth(210.dp).fillMaxHeight()) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandLogo(42.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("MECHANICUS", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color.White)
                    Text(L.s("مساعد الإصلاح", "Auto repair"), fontSize = 10.sp, color = MutedDark)
                }
            }
            Spacer(Modifier.height(20.dp))
            NavItem("🏠", L.s("الرئيسية", "Home"), dest == Scr.Home) { go(Scr.Home) }
            NavItem("🚗", L.s("العربيات", "Cars"), dest == Scr.Cars) { go(Scr.Cars) }
            if (store.isManager()) NavItem("💰", L.s("المدفوعات", "Payments"), dest == Scr.Payments) { go(Scr.Payments) }
            NavItem("👥", L.s("المستخدمون", "Users"), dest == Scr.Users) { go(Scr.Users) }
            NavItem("📜", L.s("السجل", "Change log"), dest == Scr.Logs) { go(Scr.Logs) }
            NavItem("⚙️", L.s("الإعدادات", "Settings"), dest == Scr.Settings) { go(Scr.Settings) }
            Spacer(Modifier.weight(1f))
            Surface(
                color = Color(0xFF23101522),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(10.dp)) {
                    Text(stripWhitespace(store.activeUserName), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        if (store.isManager()) L.s("مدير", "Manager") else L.s("فني", "Technician"),
                        fontSize = 10.5.sp, color = Red,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            GhostButton(L.s("خروج", "Sign out"), Modifier.fillMaxWidth()) {
                store.activeUserId = 0
                store.activeUserName = ""
                store.adminMode = false
                go(Scr.Login)
            }
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                OwnerUnlockOctopus(store, L, bump)
            }
        }
    }
}

// Secret owner unlock (same behaviour as the phone app): tap the little octopus
// 7 times with gaps under 1.5s while signed in as the owner to toggle the real
// admin powers (which reveal the full settings). Anyone else just gets a 🐙.
@Composable
private fun OwnerUnlockOctopus(store: Store, L: Lang, bump: () -> Unit) {
    val banner = LocalBanner.current
    var taps by remember { mutableStateOf(0) }
    var last by remember { mutableStateOf(0L) }
    Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(50)).clickable {
            val now = System.currentTimeMillis()
            if (now - last > 1500L) taps = 0
            last = now
            taps++
            if (taps >= 7) {
                taps = 0
                if (store.isOwner(store.activeUserName)) {
                    store.adminMode = !store.adminMode
                    store.addLog(store.activeUserName, "user", "", if (store.adminMode) "admin unlock" else "admin lock")
                    bump()
                    banner(if (store.adminMode) L.s("أهلاً يا مسؤول 🔧", "Welcome, admin 🔧") else L.s("تم قفل صلاحيات المسؤول", "Admin locked"))
                } else {
                    banner("🐙")
                }
            }
        },
        contentAlignment = Alignment.Center,
    ) { Text("🐙", fontSize = 19.sp, modifier = Modifier.alpha(0.45f)) }
}

private fun stripWhitespace(s: String) = s.trim()

@Composable
private fun NavItem(emoji: String, label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) Red.copy(alpha = 0.22f) else Color.Transparent,
        shape = RoundedCornerShape(11.dp),
        border = if (selected) BorderStroke(1.dp, Red.copy(alpha = 0.5f)) else null,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { onClick() },
    ) {
        Row(Modifier.padding(horizontal = 11.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 15.sp)
            Spacer(Modifier.width(11.dp))
            Text(label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, color = if (selected) Color.White else MutedDark)
        }
    }
}

@Composable
private fun LoginDesktop(store: Store, L: Lang, version: Int, onDone: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    val configured = remember(version) { ServiceAuth.isConfigured() }
    val users = remember(version) { store.visibleUsers() }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shadowElevation = 16.dp,
            modifier = Modifier.width(430.dp),
        ) {
            Column(Modifier.padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                BrandLogo(96.dp)
                Spacer(Modifier.height(14.dp))
                Text("MECHANICUS", fontSize = 26.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp, color = Color.White)
                Text(L.s("مساعد إصلاح السيارات", "YOUR AUTO REPAIR ASSISTANT"), color = Red, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.height(22.dp))

                if (!configured) {
                    Text(
                        L.s(
                            "الجهاز ده محتاج تفعيل مرة واحدة: المسؤول (عاصم حسين) يدخل مفتاح الشركة من الإعدادات بعد الدخول.",
                            "This device needs a one-time activation: the admin enters the company key from Settings after signing in.",
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.5.sp, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                }

                Field(L.s("اسم المستخدم", "Username"), name, { name = it; err = "" })
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 4) pin = it.filter { c -> c.isDigit() }; err = "" },
                    label = { Text(L.s("الرقم السري (4 أرقام)", "PIN (4 digits)"), fontSize = 12.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(13.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (err.isNotEmpty()) {
                    Text(err, color = Red, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(top = 7.dp))
                }
                Spacer(Modifier.height(16.dp))
                PrimaryButton(L.s("دخول", "Sign in")) {
                    val nm = name.trim()
                    if (nm.isBlank()) err = L.s("اكتب الاسم", "Enter a name")
                    else if (pin.length != 4) err = L.s("اكتب رقم سري من 4 أرقام", "Enter a 4-digit PIN")
                    else {
                        val u = store.loginOrCreate(nm, pin)
                        if (u == null) err = L.s("الاسم ده متسجّل برقم سري تاني", "That name already has a different PIN")
                        else {
                            store.activeUserId = u.id
                            store.activeUserName = u.name
                            store.adminMode = false
                            store.addLog(u.name, "login", "", L.s("تسجيل دخول", "Signed in"))
                            onDone()
                        }
                    }
                }
                if (users.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Text(L.s("مستخدمون محفوظون", "Saved users"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    Spacer(Modifier.height(7.dp))
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (u in users) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.clickable { name = u.name; err = "" },
                            ) {
                                Text(u.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFCA5A5), modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    L.s("الاسم بيعرّف مين سجّل التغيير، والبيانات محفوظة على الجهاز.", "The name identifies who logged the change; data stays on this device."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun TopBar(title: String, subtitle: String, actions: @Composable () -> Unit = {}) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandLogo(38.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                if (subtitle.isNotBlank()) Text(subtitle, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            actions()
        }
    }
}

@Composable
fun ContentScroll(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp)) { content() }
}

// Bottom status bar: the program version sits in the bottom-right corner.
@Composable
private fun StatusBar(L: Lang, isAr: Boolean) {
    Surface(color = Color(0xFF160A0E), modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
            horizontalArrangement = if (isAr) Arrangement.Start else Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                L.s("الإصدار", "Version") + " " + Platform.version,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MutedDark,
            )
        }
    }
}

// Shown when a newer version's installer is found.
@Composable
private fun UpdateDialog(L: Lang, version: String, current: String, onNow: () -> Unit, onLater: () -> Unit) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text(L.s("في تحديث جديد متاح", "A new update is available")) },
        text = {
            Column {
                Text(
                    L.s(
                        "لقينا نسخة أحدث من البرنامج: الإصدار $version (النسخة الحالية $current).",
                        "A newer version of the program is available: $version (current $current).",
                    ),
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    L.s(
                        "تحب تحدّث دلوقتي؟ البرنامج هيتقفل لحظيًا عشان التثبيت يكمّل، وبعدها افتحه تاني.",
                        "Update now? The app will close briefly so the install can finish, then open it again.",
                    ),
                    fontSize = 12.5.sp,
                    color = MutedDark,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onNow) { Text(L.s("تحديث الآن", "Update now"), fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onLater) { Text(L.s("لاحقًا", "Later")) }
        },
    )
}
