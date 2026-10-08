package com.assem.mechanicus

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
    val bump: () -> Unit,
    val version: Int,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

@Composable
fun App() {
    val context = LocalContext.current
    val store = remember { Store(context) }
    var lang by remember { mutableStateOf(store.lang) }
    var dark by remember { mutableStateOf(store.dark) }
    var dest by remember { mutableStateOf<Dest>(Dest.Splash) }
    var version by remember { mutableStateOf(0) }
    val L = Lang(lang == "ar")

    val ctx = AppCtx(
        store = store,
        L = L,
        lang = lang,
        setLang = { lang = it; store.lang = it },
        dark = dark,
        setDark = { dark = it; store.dark = it },
        go = { dest = it },
        bump = { version++ },
        version = version,
    )

    val dir = if (lang == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLang provides L, LocalLayoutDirection provides dir) {
        MechanicusTheme(dark) {
            Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        when (val d = dest) {
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
                    val showBar = dest == Dest.Home || dest == Dest.Cars || dest == Dest.Payments || dest == Dest.Settings
                    if (showBar) BottomBar(ctx, dest)
                }
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

@Composable
fun SplashScreen(onLaunch: () -> Unit) {
    val L = LocalLang.current
    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(
                colors = listOf(Color(0xFF350D0D), Color(0xFF0A0505), Color(0xFF050506)),
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
        }
        Box(
            Modifier.align(Alignment.BottomCenter).padding(18.dp)
        ) {
            Surface(
                color = Color(0xFFF4F4F5),
                shape = RoundedCornerShape(15.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp).clickable { onLaunch() },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("LAUNCH APP", color = Color(0xFF111111), fontWeight = FontWeight.Black, letterSpacing = 2.sp, fontSize = 15.sp)
                }
            }
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

        CardBox {
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
                    ctx.store.addLog(u.name, "login", "", L.s("تسجيل دخول", "Signed in"))
                    ctx.go(Dest.Home)
                }
            }
        }
        Text(
            L.s("الاسم للتعريف بمن سجّل فقط، والبيانات محفوظة على الجهاز.", "The name only identifies who logged the change; data stays on this device."),
            color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp),
        )
    }
}
