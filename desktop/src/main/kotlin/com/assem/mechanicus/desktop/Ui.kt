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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assem.mechanicus.Status
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Lang(val isAr: Boolean) {
    fun s(ar: String, en: String): String = if (isAr) ar else en
}

val LocalLang = staticCompositionLocalOf { Lang(true) }
val LocalBanner = staticCompositionLocalOf<(String) -> Unit> { { } }

val Red = Color(0xFFDC2626)
val RedDeep = Color(0xFFB91C1C)
val RedSoft = Color(0xFFFFE4E6)
val Ink = Color(0xFF0F172A)
val Muted = Color(0xFF94A3B8)
val MutedDark = Color(0xFFC9A2A6)
val Green = Color(0xFF0E9F6E)
val GreenSoft = Color(0xFF10301F)
val AmberSoft = Color(0xFF3A2A12)
val Line = Color(0xFF4A2027)

val DarkScheme = darkColorScheme(
    primary = Color(0xFFF43F5E),
    onPrimary = Color.White,
    secondary = Color(0xFFDC2626),
    onSecondary = Color.White,
    background = Color(0xFF0A0507),
    onBackground = Color(0xFFF5ECEC),
    surface = Color(0xFF190A0E),
    onSurface = Color(0xFFF7EFF0),
    surfaceVariant = Color(0xFF2A1015),
    onSurfaceVariant = Color(0xFFC9A2A6),
    outline = Line,
)

@Composable
fun MechanicusTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, content = content)
}

fun todayString(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
fun money(v: Double): String = String.format(Locale.US, "%,.0f", v)

fun stageLabel(L: Lang, stage: String): String = when (stage) {
    "on" -> L.s("مقدم", "Deposit")
    "mid" -> L.s("دفعة وسطى", "Mid payment")
    "final" -> L.s("دفعة نهائية", "Final payment")
    else -> stage
}

@Composable
fun MutedTextColor() = MaterialTheme.colorScheme.onSurfaceVariant

@Composable
fun CardBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(16.dp)) { content() }
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
    keyboardType: KeyboardType = KeyboardType.Text,
    digitsOnly: Boolean = false,
    maxLen: Int? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw ->
            var v = if (digitsOnly) raw.filter { it.isDigit() } else raw
            if (maxLen != null) v = v.take(maxLen)
            onValue(v)
        },
        label = { Text(label, fontSize = 12.sp) },
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(13.dp),
    )
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(48.dp).shadow(7.dp, RoundedCornerShape(15.dp)),
        shape = RoundedCornerShape(15.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        modifier = modifier.height(48.dp).clickable { onClick() },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
fun StatusChip(status: String, modifier: Modifier = Modifier) {
    val L = LocalLang.current
    val label: String
    val bg: Color
    val fg: Color
    when (status) {
        Status.DONE -> { label = L.s("تم التسليم", "Delivered"); bg = GreenSoft; fg = Color(0xFF34D399) }
        Status.LATE -> { label = L.s("متأخر", "Late"); bg = Color(0xFF3A1418); fg = Color(0xFFFCA5A5) }
        else -> { label = L.s("جاري", "Working"); bg = AmberSoft; fg = Color(0xFFFBBF24) }
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
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(value.ifBlank { "—" }, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
fun StatCard(emoji: String, number: String, label: String, tint: Color, soft: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 5.dp,
    ) {
        Column(Modifier.padding(14.dp)) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(soft),
                contentAlignment = Alignment.Center,
            ) { Text(emoji, fontSize = 17.sp) }
            Spacer(Modifier.height(8.dp))
            Text(number, fontSize = 20.sp, fontWeight = FontWeight.Black, color = tint)
            Text(label, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
        }
    }
}
