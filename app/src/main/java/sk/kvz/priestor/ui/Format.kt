package sk.kvz.priestor.ui

import androidx.compose.ui.graphics.Color
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val sk = Locale.forLanguageTag("sk")
private val dayFmt = DateTimeFormatter.ofPattern("EEE d. M.", sk)
private val dayYearFmt = DateTimeFormatter.ofPattern("d. M. yyyy", sk)
private val timeFmt = DateTimeFormatter.ofPattern("H:mm", sk)

/** „dnes 14:00“, „zajtra“, „pi 17. 10.“ */
fun formatDue(millis: Long, hasTime: Boolean): String {
    val zone = ZoneId.systemDefault()
    val dt = Instant.ofEpochMilli(millis).atZone(zone)
    val today = LocalDate.now(zone)
    val date = dt.toLocalDate()
    val day = when (date) {
        today -> "dnes"
        today.plusDays(1) -> "zajtra"
        today.minusDays(1) -> "včera"
        else -> if (date.year == today.year) dt.format(dayFmt) else dt.format(dayYearFmt)
    }
    return if (hasTime) "$day ${dt.format(timeFmt)}" else day
}

fun formatDate(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(dayYearFmt)

/** Je termín už po splatnosti? Úloha bez času je po termíne až od nasledujúceho dňa. */
fun isOverdue(millis: Long, hasTime: Boolean): Boolean {
    val zone = ZoneId.systemDefault()
    return if (hasTime) millis < System.currentTimeMillis()
    else Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().isBefore(LocalDate.now(zone))
}

/** Farby oblastí. */
val spaceColors = listOf(
    Color(0xFF2F6B4F), // zelená
    Color(0xFF3B6EA8), // modrá
    Color(0xFFB5643C), // terakota
    Color(0xFF8A5A9E), // fialová
    Color(0xFFB8912F), // horčicová
    Color(0xFF4F8C8C), // tyrkysová
    Color(0xFFA8485E), // vínová
    Color(0xFF6B6B6B), // sivá
)

fun spaceColor(index: Int): Color = spaceColors[index.mod(spaceColors.size)]
