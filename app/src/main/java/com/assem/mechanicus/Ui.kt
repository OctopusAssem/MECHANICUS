package com.assem.mechanicus

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    secondary = Color(0xFFDC2626),
    onSecondary = Color.White,
    background = Color(0xFF0A0507),
    onBackground = Color(0xFFF5ECEC),
    surface = Color(0xFF190A0E),
    onSurface = Color(0xFFF7EFF0),
    surfaceVariant = Color(0xFF2A1015),
    onSurfaceVariant = Color(0xFFC9A2A6),
    outline = Color(0xFF4A2027),
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
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
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

// Three numeric boxes (month / day / year) combined into "yyyy-MM-dd".
@Composable
fun DateField3(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val L = LocalLang.current
    val init = remember { value.ifBlank { todayString() } }
    val ip = init.split("-")
    var y by remember { mutableStateOf(ip.getOrNull(0)?.filter { it.isDigit() }?.take(4) ?: "") }
    var m by remember { mutableStateOf(ip.getOrNull(1)?.filter { it.isDigit() }?.take(2) ?: "") }
    var d by remember { mutableStateOf(ip.getOrNull(2)?.filter { it.isDigit() }?.take(2) ?: "") }

    fun push(mo: String, da: String, ye: String) {
        if (ye.isBlank() && mo.isBlank() && da.isBlank()) { onValue(""); return }
        val yy = if (ye.isBlank()) "" else ye.padStart(4, '0')
        val mm = if (mo.isBlank()) "" else mo.padStart(2, '0')
        val dd = if (da.isBlank()) "" else da.padStart(2, '0')
        onValue(listOf(yy, mm, dd).joinToString("-"))
    }

    Column(modifier) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 5.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = m,
                onValueChange = { v -> m = v.filter { it.isDigit() }.take(2); push(m, d, y) },
                label = { Text(L.s("الشهر", "Month"), fontSize = 11.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(13.dp),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = d,
                onValueChange = { v -> d = v.filter { it.isDigit() }.take(2); push(m, d, y) },
                label = { Text(L.s("اليوم", "Day"), fontSize = 11.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(13.dp),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = y,
                onValueChange = { v -> y = v.filter { it.isDigit() }.take(4); push(m, d, y) },
                label = { Text(L.s("السنة", "Year"), fontSize = 11.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(13.dp),
                modifier = Modifier.weight(1.3f),
            )
        }
    }
}

@Composable
fun ReadOnlyField(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 5.dp),
        )
        Surface(
            shape = RoundedCornerShape(13.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (value.isBlank()) "—" else value,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 15.dp),
            )
        }
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(52.dp).shadow(7.dp, RoundedCornerShape(15.dp)),
        shape = RoundedCornerShape(15.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        modifier = modifier.height(52.dp).clickable { onClick() },
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
        shadowElevation = 5.dp,
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
