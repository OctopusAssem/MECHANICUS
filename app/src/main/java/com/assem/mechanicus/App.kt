package com.assem.mechanicus

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Dest {
    data object Splash : Dest
    data object Login : Dest
    data object Home : Dest
    data object Cars : Dest
    data class Edit(val id: String?) : Dest
    data class Detail(val id: String) : Dest
    data object Payments : Dest
    data object Users : Dest
    data object Logs : Dest
    data object Settings : Dest
    data object Sync : Dest
}

class AppCtx(
    val store: Store,
    val L: Lang,
    val lang: String,
    val setLang: (String) -> Unit,
    val dark: Boolean,
    val setDark: (Boolean) -> Unit,
    val go: (Dest) -> Unit,
    val requestSync: () -> Unit,
    val bump: () -> Unit,
    val version: Int,
)

class MainActivity : ComponentActivity() {
    private val incoming = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App(incoming) }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        if (i == null) return
        if (i.action != Intent.ACTION_VIEW && i.action != Intent.ACTION_SEND) return
        val uri: Uri? = i.data ?: if (Build.VERSION.SDK_INT >= 33) {
            i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION") (i.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)
        }
        if (uri != null) incoming.value = uri
    }
}

@Composable
fun App(incoming: MutableState<Uri?>? = null) {
    val context = LocalContext.current
    val store = remember { Store(context) }
    val scope = rememberCoroutineScope()
    var lang by remember { mutableStateOf(store.lang) }
    var dark by remember { mutableStateOf(store.dark) }
    var dest by remember { mutableStateOf<Dest>(Dest.Splash) }
    val backStack = remember { mutableStateListOf<Dest>() }
    var version by remember { mutableStateOf(0) }
    var showExit by remember { mutableStateOf(false) }
    // When the owner protects the database, every device must be approved by
    // the admin once. Until then the whole app is locked behind the admin PW.
    var locked by remember { mutableStateOf(store.dbProtected && !store.dbAuthorized) }
    val L = Lang(lang == "ar")

    fun navigate(d: Dest) {
        val root = d == Dest.Home || d == Dest.Cars || d == Dest.Payments || d == Dest.Settings
        if (root) backStack.clear() else backStack.add(dest)
        dest = d
    }

    LaunchedEffect(incoming?.value) {
        val uri = incoming?.value ?: return@LaunchedEffect
        try {
            val user = store.activeUserName.ifBlank { "import" }
            val n = Transfer.importUri(context, store, uri, user)
            store.addLog(user, "import", "", "Imported $n")
            version++
            backStack.clear()
            dest = Dest.Cars
            Toast.makeText(context, L.s("تم استيراد $n عربية ✅", "Imported $n cars ✅"), Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, L.s("الملف غير صالح", "Invalid file"), Toast.LENGTH_LONG).show()
        }
        incoming?.value = null
    }

    // Flush any pending (made-offline) changes to Drive on launch. When the app
    // carries the shop's service account, sync is on for everyone from the start
    // (no per-phone Google sign-in) and we also pull in that first sync.
    LaunchedEffect(Unit) {
        if (ServiceAuth.isConfigured(context) && !store.driveConnected) store.driveConnected = true
        if (store.driveConnected && (store.syncPending || ServiceAuth.isConfigured(context))) {
            val r = withContext(Dispatchers.IO) { AutoSync.run(context, store) }
            if (r == AutoSync.SYNCED) version++
            if (r == AutoSync.SYNCED || r == AutoSync.LOCKED) locked = store.dbProtected && !store.dbAuthorized
        }
    }

    val ctx = AppCtx(
        store = store,
        L = L,
        lang = lang,
        setLang = { lang = it; store.lang = it },
        dark = dark,
        setDark = { dark = it; store.dark = it },
        go = { navigate(it) },
        requestSync = {
            if (store.driveConnected) scope.launch {
                when (withContext(Dispatchers.IO) { AutoSync.run(context, store) }) {
                    AutoSync.SYNCED -> { version++; locked = store.dbProtected && !store.dbAuthorized; Toast.makeText(context, L.s("تمت المزامنة ✅", "Synced ✅"), Toast.LENGTH_SHORT).show() }
                    AutoSync.LOCKED -> { locked = store.dbProtected && !store.dbAuthorized; Toast.makeText(context, L.s("قاعدة البيانات محمية — لازم تصريح المسؤول", "Database protected — admin authorization needed"), Toast.LENGTH_LONG).show() }
                    AutoSync.PENDING -> Toast.makeText(context, L.s("أوفلاين — هيتم تلقائيًا لما النت يرجع", "Offline — will sync automatically when back online"), Toast.LENGTH_SHORT).show()
                }
            }
        },
        bump = { version++; locked = store.dbProtected && !store.dbAuthorized },
        version = version,
    )

    BackHandler(enabled = backStack.isNotEmpty()) {
        dest = backStack.removeAt(backStack.lastIndex)
    }
    BackHandler(enabled = backStack.isEmpty() && (dest == Dest.Home || dest == Dest.Cars || dest == Dest.Payments || dest == Dest.Settings)) {
        showExit = true
    }

    val dir = if (lang == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLang provides L, LocalLayoutDirection provides dir) {
        MechanicusTheme(dark) {
            Box(
                Modifier.fillMaxSize().background(
                    if (dark) Brush.verticalGradient(listOf(Color(0xFF511722), Color(0xFF2C141C), Color(0xFF1A0F13)))
                    else Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFF6F7FB), Color(0xFFECEFF6)))
                )
            ) {
                Box(
                    Modifier.fillMaxWidth().height(420.dp).align(Alignment.TopCenter).background(
                        if (dark) Brush.radialGradient(listOf(Color(0x55DC2626), Color(0x00000000)))
                        else Brush.radialGradient(listOf(Color(0x1ADC2626), Color(0x00000000)))
                    )
                )
                Surface(color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground, modifier = Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f)) {
                        if (locked && dest != Dest.Splash) {
                            LockScreen(ctx) { locked = false; ctx.requestSync() }
                        } else when (val d = dest) {
                            Dest.Splash -> SplashScreen(onLaunch = { dest = Dest.Login })
                            Dest.Login -> LoginScreen(ctx)
                            Dest.Home -> HomeScreen(ctx)
                            Dest.Cars -> CarsScreen(ctx)
                            is Dest.Edit -> EditCarScreen(ctx, d.id)
                            is Dest.Detail -> DetailScreen(ctx, d.id)
                            Dest.Payments -> PaymentsScreen(ctx)
                            Dest.Users -> UsersScreen(ctx)
                            Dest.Logs -> LogsScreen(ctx)
                            Dest.Settings -> SettingsScreen(ctx)
                            Dest.Sync -> SyncScreen(ctx)
                        }
                    }
                    val showBar = !locked && (dest == Dest.Home || dest == Dest.Cars || dest == Dest.Payments || dest == Dest.Settings)
                    if (showBar) BottomBar(ctx, dest)
                }
                }
            }
            if (showExit) {
                val pending = store.syncPending
                AlertDialog(
                    onDismissRequest = { showExit = false },
                    title = { Text(L.s("الخروج من البرنامج", "Exit app")) },
                    text = {
                        Text(
                            when {
                                pending && store.driveConnected -> L.s("فيه تغييرات لسه متزامنتش مع درايف. تحب تزامن قبل الخروج؟", "There are changes not synced to Drive yet. Sync before exiting?")
                                pending -> L.s("فيه تغييرات متزامنتش (مفيش اتصال بدرايف). تخرج عادي؟", "There are unsynced changes (Drive not connected). Exit anyway?")
                                else -> L.s("كل حاجة متزامنة. تحب تقفل البرنامج؟", "Everything is synced. Close the app?")
                            },
                            fontSize = 14.sp,
                        )
                    },
                    confirmButton = {
                        if (pending && store.driveConnected) {
                            TextButton(onClick = {
                                showExit = false
                                scope.launch {
                                    withContext(Dispatchers.IO) { try { AutoSync.run(context, store) } catch (_: Exception) {} }
                                    (context as? android.app.Activity)?.finish()
                                }
                            }) { Text(L.s("مزامنة وخروج", "Sync & exit"), fontWeight = FontWeight.Bold) }
                        } else {
                            TextButton(onClick = { showExit = false; (context as? android.app.Activity)?.finish() }) {
                                Text(L.s("موافق للخروج", "Exit"), fontWeight = FontWeight.Bold)
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showExit = false }) { Text(L.s("إلغاء", "Cancel")) }
                    },
                )
            }
        }
    }
}

@Composable
fun BottomBar(ctx: AppCtx, dest: Dest) {
    val L = ctx.L
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        val items = listOf(
            Triple(Dest.Home, Icons.Filled.Home, L.s("الرئيسية", "Home")),
            Triple(Dest.Cars, Icons.Filled.DirectionsCar, L.s("العربيات", "Cars")),
            Triple(Dest.Payments, Icons.Filled.Payments, L.s("المدفوعات", "Payments")),
            Triple(Dest.Settings, Icons.Filled.Settings, L.s("الإعدادات", "Settings")),
        )
        for ((d, icon, label) in items) {
            NavigationBarItem(
                selected = dest == d,
                onClick = { ctx.go(d) },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label, fontSize = 10.sp) },
            )
        }
    }
}

// Shown when the owner protected the database and this device has not been
// approved yet. Only the admin password opens it — this is how the owner gives
// an employee (and only an employee he trusts) permission to use the app.
@Composable
fun LockScreen(ctx: AppCtx, onUnlock: () -> Unit) {
    val L = ctx.L
    val context = LocalContext.current
    var pass by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(84.dp).clip(RoundedCornerShape(24.dp)).background(RedSoft),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Lock, contentDescription = null, tint = RedDeep, modifier = Modifier.size(42.dp)) }
        Spacer(Modifier.height(16.dp))
        Text(L.s("البرنامج مقفول", "App locked"), fontSize = 24.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        Text(
            L.s(
                "الجهاز ده مش مفعّل. خلي المسؤول (عاصم حسين) يكتب مفتاح الشركة هنا مرة واحدة، فيتفعّل البرنامج وتشتغل المزامنة.",
                "This device isn't activated. Ask the admin (Assem Hussein) to enter the company key once to activate the app and enable sync.",
            ),
            color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        val needsKey = !ServiceAuth.isConfigured(context)
        CardBox {
            OutlinedTextField(
                value = pass,
                onValueChange = { pass = it; err = "" },
                label = { Text(if (needsKey) L.s("مفتاح الشركة", "Company key") else L.s("باسورد المسؤول", "Admin password"), fontSize = 12.sp) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = if (needsKey) KeyboardType.Ascii else KeyboardType.NumberPassword),
                shape = RoundedCornerShape(13.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            if (err.isNotEmpty()) Text(err, color = Red, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(12.dp))
            PrimaryButton(if (needsKey) L.s("تفعيل وتصريح", "Activate & approve") else L.s("تصريح وفتح", "Approve & unlock")) {
                val activated = if (needsKey) ServiceAuth.provision(context, pass.trim()) else false
                val ok = if (needsKey) activated else ctx.store.verifyAdminPass(pass)
                if (ok) {
                    ctx.store.dbAuthorized = true
                    ctx.store.addLog("عاصم حسين", "user", "", L.s("تصريح جهاز وفتح البرنامج", "Approved device & unlocked app"))
                    ctx.bump()
                    Toast.makeText(context, L.s("تم التفعيل ✅", "Activated ✅"), Toast.LENGTH_SHORT).show()
                    onUnlock()
                } else {
                    err = if (needsKey) L.s("مفتاح الشركة غلط", "Wrong company key") else L.s("باسورد المسؤول غلط", "Wrong admin password")
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            L.s("مفيش بيانات ولا مزامنة قبل التصريح.", "No data or sync before approval."),
            color = Muted, fontSize = 11.5.sp, textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun SplashScreen(onLaunch: () -> Unit) {
    val L = LocalLang.current
    LaunchedEffect(Unit) {
        delay(3000)
        onLaunch()
    }
    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(
                colors = listOf(Color(0xFF4A1515), Color(0xFF1A0F13), Color(0xFF120A0E)),
            )
        )
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher_foreground),
                contentDescription = "MECHANICUS",
                modifier = Modifier.size(180.dp),
            )
            Text("MECHANICUS", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = 5.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                L.s("مساعد إصلاح السيارات", "YOUR AUTO REPAIR ASSISTANT"),
                color = Color(0xFFF43F5E), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            CircularProgressIndicator(
                color = Color(0xFFF43F5E),
                strokeWidth = 3.dp,
                modifier = Modifier.size(32.dp),
            )
        }
        Column(
            Modifier.align(Alignment.BottomCenter).padding(horizontal = 34.dp).padding(bottom = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LinearProgressIndicator(
                color = Color(0xFFF43F5E),
                trackColor = Color(0x33FFFFFF),
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                L.s("جاري التحميل...", "Loading..."),
                color = Color(0xFFB9B9C0), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
            )
        }
    }
}

@Composable
fun LoginScreen(ctx: AppCtx) {
    val L = ctx.L
    val users = remember(ctx.version) { ctx.store.users() }
    var selected by remember { mutableStateOf(users.firstOrNull()?.name ?: "") }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var adminOpen by remember { mutableStateOf(false) }
    var forgot by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(18.dp))
        Image(
            painter = painterResource(R.mipmap.ic_launcher_foreground),
            contentDescription = "MECHANICUS",
            modifier = Modifier.size(120.dp),
        )
        Text("MECHANICUS", fontSize = 26.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
        Text(L.s("مساعد إصلاح السيارات", "YOUR AUTO REPAIR ASSISTANT"), color = Red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(18.dp))

        var name by remember { mutableStateOf("") }
        CardBox {
            if (users.isEmpty()) {
                SectionTitle(L.s("أول تشغيل — اعمل حسابك", "First run — create your account"))
                Field(L.s("اكتب اسمك", "Enter your name"), name, { name = it })
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 4) pin = it.filter { c -> c.isDigit() }; error = "" },
                    label = { Text(L.s("الرقم السري (4 أرقام)", "PIN (4 digits)")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(13.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error.isNotEmpty()) {
                    Text(error, color = Red, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
                Spacer(Modifier.height(12.dp))
                PrimaryButton(L.s("إنشاء ودخول", "Create & sign in")) {
                    val nm = name.trim()
                    if (nm.isBlank()) error = L.s("اكتب الاسم", "Enter a name")
                    else if (pin.length != 4) error = L.s("اكتب رقم سري من 4 أرقام", "Enter a 4-digit PIN")
                    else {
                        ctx.store.addUser(nm, pin, "admin")
                        val u = ctx.store.checkLogin(nm, pin)
                        ctx.store.activeUserId = u?.id ?: -1L
                        ctx.store.activeUserName = nm
                        ctx.store.adminMode = false
                        ctx.store.addLog(nm, "login", "", L.s("أول تشغيل", "First run"))
                        ctx.go(Dest.Home)
                    }
                }
            } else {
                SectionTitle(L.s("اختر المستخدم", "Select user"))
                for (u in users) {
                    val on = u.name == selected
                    Surface(
                        color = if (on) RedSoft else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(13.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (on) Red else MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { selected = u.name },
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(u.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (on) RedDeep else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 4) pin = it.filter { c -> c.isDigit() }; error = "" },
                    label = { Text(L.s("الرقم السري (4 أرقام)", "PIN (4 digits)")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(13.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error.isNotEmpty()) {
                    Text(error, color = Red, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
                Spacer(Modifier.height(12.dp))
                PrimaryButton(L.s("دخول", "Sign in")) {
                    val u = ctx.store.checkLogin(selected, pin)
                    if (u == null) {
                        error = L.s("الرقم السري غلط", "Wrong PIN")
                    } else {
                        ctx.store.activeUserId = u.id
                        ctx.store.activeUserName = u.name
                        ctx.store.adminMode = false
                        ctx.store.addLog(u.name, "login", "", L.s("تسجيل دخول", "Signed in"))
                        ctx.go(Dest.Home)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    L.s("نسيت الرقم السري؟ اطلب من المسؤول يعيده لك", "Forgot your PIN? Ask the admin to reset it"),
                    color = Muted, fontSize = 11.5.sp,
                    modifier = Modifier.fillMaxWidth().clickable { forgot = true },
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.clickable { adminOpen = true },
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AdminPanelSettings, contentDescription = "admin", modifier = Modifier.size(14.dp), tint = Muted)
                Spacer(Modifier.width(6.dp))
                Text("admin", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Muted)
            }
        }
        Text(
            L.s("الاسم للتعريف بمن سجّل فقط، والبيانات محفوظة على الجهاز.", "The name only identifies who logged the change; data stays on this device."),
            color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp),
        )
    }

    if (adminOpen) {
        var aName by remember { mutableStateOf("") }
        var aPin by remember { mutableStateOf("") }
        var aErr by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adminOpen = false },
            title = { Text(L.s("دخول المسؤول", "Admin access")) },
            text = {
                Column {
                    Text(L.s("الاسم والرقم السري للمسؤول.", "Admin name and PIN."), color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    Field(L.s("اسم المسؤول", "Admin name"), aName, { aName = it; aErr = "" })
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = aPin,
                        onValueChange = { if (it.length <= 4) aPin = it.filter { c -> c.isDigit() }; aErr = "" },
                        label = { Text(L.s("الرقم السري (4 أرقام)", "PIN (4 digits)"), fontSize = 12.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(13.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (aErr.isNotEmpty()) Text(aErr, color = Red, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (ctx.store.checkAdmin(aName, aPin)) {
                        adminOpen = false
                        ctx.store.adminMode = true
                        ctx.store.activeUserId = -1L
                        ctx.store.activeUserName = "عاصم حسين"
                        ctx.store.addLog("عاصم حسين", "login", "", L.s("دخول المسؤول (صلاحيات مطلقة)", "Admin sign-in (absolute access)"))
                        ctx.go(Dest.Home)
                    } else {
                        aErr = L.s("الاسم أو الرقم غلط", "Wrong name or PIN")
                    }
                }) { Text(L.s("دخول", "Sign in"), fontWeight = FontWeight.Bold, color = Red) }
            },
            dismissButton = { TextButton(onClick = { adminOpen = false }) { Text(L.s("إلغاء", "Cancel")) } },
        )
    }

    if (forgot) {
        val target = users.firstOrNull { it.name == selected }
        if (target != null) ResetPinDialog(ctx, target.id, target.name) { forgot = false }
    }
}
