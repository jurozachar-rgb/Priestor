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

// ---------- Kalendár činností ----------

private val monthsNom = listOf(
    "január", "február", "marec", "apríl", "máj", "jún",
    "júl", "august", "september", "október", "november", "december",
)
private val monthsGen = listOf(
    "januára", "februára", "marca", "apríla", "mája", "júna",
    "júla", "augusta", "septembra", "októbra", "novembra", "decembra",
)
val weekdaysShort = listOf("po", "ut", "st", "št", "pi", "so", "ne")
private val weekdaysLong = listOf("pondelok", "utorok", "streda", "štvrtok", "piatok", "sobota", "nedeľa")

/** „Október 2026“ */
fun monthTitle(month: java.time.YearMonth): String =
    monthsNom[month.monthValue - 1].replaceFirstChar { it.uppercase() } + " " + month.year

/** „1. novembra 2026“ alebo bez roka „1. novembra“ */
fun longDate(d: LocalDate, withYear: Boolean = true): String =
    "${d.dayOfMonth}. ${monthsGen[d.monthValue - 1]}" + if (withYear) " ${d.year}" else ""

/** „streda 18. októbra 2026“ */
fun longDateWithDay(d: LocalDate): String = "${weekdaysLong[d.dayOfWeek.value - 1]} ${longDate(d)}"

fun weekdayShort(d: LocalDate): String = weekdaysShort[d.dayOfWeek.value - 1]

/** „dnes“, „včera“, „pred 5 dňami“, „o 3 dni“ */
fun relativeDays(d: LocalDate, today: LocalDate = LocalDate.now()): String {
    val n = java.time.temporal.ChronoUnit.DAYS.between(d, today)
    return when {
        n == 0L -> "dnes"
        n == 1L -> "včera"
        n == -1L -> "zajtra"
        n > 1 -> "pred $n dňami"
        -n in 2..4 -> "o ${-n} dni"
        else -> "o ${-n} dní"
    }
}

/** „1 raz“, „3 razy“, „7 krát“ */
fun timesCount(n: Int): String = when (n) {
    1 -> "1 raz"
    in 2..4 -> "$n razy"
    0 -> "0-krát"
    else -> "$n-krát"
}

/** „žiadne záznamy“, „1 záznam“, „3 záznamy“, „8 záznamov“ */
fun entriesCount(n: Int): String = when (n) {
    0 -> "žiadne záznamy"
    1 -> "1 záznam"
    in 2..4 -> "$n záznamy"
    else -> "$n záznamov"
}
