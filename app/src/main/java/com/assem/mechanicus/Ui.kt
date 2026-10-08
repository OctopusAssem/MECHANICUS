package com.assem.mechanicus

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class Lang(val isAr: Boolean) {
    fun s(ar: String, en: String): String = if (isAr) ar else en
}

val LocalLang = staticCompositionLocalOf { Lang(true) }

val Red = Color(0xFFDC2626)
val RedDeep = Color(0xFFB91C1C)
val RedSoft = Color(0xFFFFE4E6)
val Ink = Color(0xFF0F172A)
val Muted = Color(0xFF64748B)
val Bg = Color(0xFFEEF1F7)
val CardBg = Color(0xFFFFFFFF)
val Line = Color(0xFFE2E8F0)
val Green = Color(0xFF0E9F6E)
val GreenSoft = Color(0xFFE6F7F0)
val Amber = Color(0xFFF59E0B)
val AmberSoft = Color(0xFFFFF4E2)
val Blue = Color(0xFF0369A1)
val BlueSoft = Color(0xFFE4F5FD)

val LightScheme = lightColorScheme(
    primary = Red,
    onPrimary = Color.White,
    secondary = RedDeep,
    onSecondary = Color.White,
    background = Bg,
    onBackground = Ink,
    surface = CardBg,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Muted,
    outline = Line,
)

val DarkScheme = darkColorScheme(
    primary = Color(0xFFF43F5E),
    onPrimary = Color.White,
    secondary = Red,
    background = Color(0xFF0B1020),
    onBackground = Color(0xFFE8ECF7),
    surface = Color(0xFF151A2E),
    onSurface = Color(0xFFE8ECF7),
    surfaceVariant = Color(0xFF1E2438),
    onSurfaceVariant = Color(0xFF97A1BD),
    outline = Color(0xFF2A3350),
)

@Composable
fun MechanicusTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, content = content)
}

@Composable
fun CardBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.padding(14.dp)) { content() }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(top = 14.dp, bottom = 8.dp),
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
fun Field(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label, fontSize = 12.sp) },
        singleLine = singleLine,
        minLines = minLines,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(13.dp),
    )
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(15.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun StatusChip(status: String, modifier: Modifier = Modifier) {
    val L = LocalLang.current
    val label: String
    val bg: Color
    val fg: Color
    when (status) {
        Status.DONE -> { label = L.s("تم التسليم", "Delivered"); bg = GreenSoft; fg = Green }
        Status.LATE -> { label = L.s("متأخر", "Late"); bg = RedSoft; fg = RedDeep }
        else -> { label = L.s("جاري", "Working"); bg = AmberSoft; fg = Color(0xFFB45309) }
    }
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) { Text(label, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun PlateText(plate: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(7.dp))
            .background(Color(0xFF111827))
            .padding(horizontal = 9.dp, vertical = 3.dp)
    ) { Text(plate, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black) }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, color = Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
fun StatCard(emoji: String, number: String, label: String, tint: Color, soft: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(13.dp)) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(soft),
                contentAlignment = Alignment.Center,
            ) { Text(emoji, fontSize = 17.sp) }
            Spacer(Modifier.height(8.dp))
            Text(number, fontSize = 21.sp, fontWeight = FontWeight.Black, color = tint)
            Text(label, fontSize = 11.5.sp, color = Muted, fontWeight = FontWeight.SemiBold)
        }
    }
}
