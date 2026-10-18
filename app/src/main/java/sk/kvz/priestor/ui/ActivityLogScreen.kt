package sk.kvz.priestor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import sk.kvz.priestor.data.LogEntry
import sk.kvz.priestor.data.Repo
import sk.kvz.priestor.data.Space
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * Kalendár činností: vlastné činnosti (Kosenie, Polievanie…), ku každej záznamy s dátumom a poznámkou.
 * Hore mesačný kalendár s farebnými bodkami, pod ním súhrn vybranej činnosti.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityLogScreen(initialSelectedId: String?, onBack: () -> Unit, onMembers: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val activitiesFlow = remember { Repo.activitiesFlow() }
    val activities by activitiesFlow.collectAsStateWithLifecycle(initialValue = null)

    val ids = activities?.map { it.id }.orEmpty()
    val entriesFlow = remember(ids) {
        if (ids.isEmpty()) flowOf(emptyList<LogEntry>())
        else combine(ids.map { Repo.entriesFlow(it) }) { parts -> parts.flatMap { it } }
    }
    val entries by entriesFlow.collectAsStateWithLifecycle(initialValue = emptyList<LogEntry>())

    val today = LocalDate.now()
    var selectedId by rememberSaveable { mutableStateOf(initialSelectedId) }
    // mesiac uložený ako rok*12 + (mesiac-1), aby prežil otočenie obrazovky
    var monthIndex by rememberSaveable { mutableIntStateOf(today.year * 12 + today.monthValue - 1) }
    val month = YearMonth.of(monthIndex / 12, monthIndex % 12 + 1)

    var dayDialog by remember { mutableStateOf<LocalDate?>(null) }
    var editing by remember { mutableStateOf<LogEntry?>(null) }
    var dialog by remember { mutableStateOf<String?>(null) } // new, rename, color, delete, leave
    var menu by remember { mutableStateOf(false) }

    val list = activities
    val selected = list?.firstOrNull { it.id == selectedId }
    val byId = list.orEmpty().associateBy { it.id }
    val visible = entries.filter { byId.containsKey(it.spaceId) && (selected == null || it.spaceId == selected.id) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(selected?.name ?: "Kalendár činností", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Späť") } },
                actions = {
                    if (selected != null) {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Možnosti") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Zdieľať / členovia") }, onClick = { menu = false; onMembers(selected.id) })
                            DropdownMenuItem(text = { Text("Premenovať") }, onClick = { menu = false; dialog = "rename" })
                            DropdownMenuItem(text = { Text("Zmeniť farbu") }, onClick = { menu = false; dialog = "color" })
                            if (selected.ownerId == Repo.uid) {
                                DropdownMenuItem(text = { Text("Zmazať činnosť") }, onClick = { menu = false; dialog = "delete" })
                            } else {
                                DropdownMenuItem(text = { Text("Opustiť činnosť") }, onClick = { menu = false; dialog = "leave" })
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (!list.isNullOrEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { dayDialog = today },
                    icon = { Icon(Icons.Rounded.Add, null) },
                    text = { Text("Zapísať dnes") },
                )
            }
        },
    ) { padding ->
        when {
            list == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            list.isEmpty() -> EmptyLog(Modifier.padding(padding)) { dialog = "new" }
            else -> LazyColumn(
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 96.dp,
                ),
            ) {
                item {
                    ActivityChips(
                        activities = list,
                        selectedId = selected?.id,
                        onSelect = { selectedId = it },
                        onNew = { dialog = "new" },
                    )
                }
                item {
                    val dots = visible.filter { YearMonth.from(it.date) == month }
                        .groupBy({ it.date }) { spaceColor(byId[it.spaceId]?.color ?: 0) }
                    MonthCalendar(
                        month = month,
                        today = today,
                        dots = dots,
                        onPrev = { monthIndex-- },
                        onNext = { monthIndex++ },
                        onToday = { monthIndex = today.year * 12 + today.monthValue - 1 },
                        onDay = { dayDialog = it },
                    )
                    HorizontalDivider(Modifier.padding(top = 8.dp))
                }
                if (selected != null) {
                    val mine = visible.sortedBy { it.date }
                    item { SummaryHeader(selected, mine, today) }
                    val years = mine.map { it.date.year }.distinct()
                    mine.forEachIndexed { i, e ->
                        if (years.size > 1 && (i == 0 || mine[i - 1].date.year != e.date.year)) {
                            item(key = "y${e.date.year}") { YearHeader(e.date.year) }
                        }
                        item(key = e.id) {
                            EntryRow(e, null, showAuthor = selected.members.size > 1, withYear = false) { editing = e }
                        }
                    }
                } else {
                    val inMonth = visible.filter { YearMonth.from(it.date) == month }
                        .sortedWith(compareBy({ it.date }, { byId[it.spaceId]?.name?.lowercase() }))
                    item {
                        Text(
                            "${monthTitle(month)} · ${entriesCount(inMonth.size)}",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
                        )
                        Text(
                            "Ťuknite na činnosť hore, uvidíte všetky jej termíny pod sebou.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                    }
                    items(inMonth, key = { it.id }) { e ->
                        val act = byId[e.spaceId]
                        EntryRow(e, act, showAuthor = (act?.members?.size ?: 1) > 1, withYear = false) { editing = e }
                    }
                }
            }
        }
    }

    // ---------- Dialógy ----------

    dayDialog?.let { day ->
        DayDialog(
            day = day,
            activities = list.orEmpty(),
            preselectedId = selected?.id ?: list?.singleOrNull()?.id,
            dayEntries = entries.filter { it.date == day && byId.containsKey(it.spaceId) },
            byId = byId,
            onEdit = { dayDialog = null; editing = it },
            onDismiss = { dayDialog = null },
            onAdd = { spaceId, note ->
                Repo.addEntry(spaceId, day, note)
                dayDialog = null
            },
        )
    }

    editing?.let { e ->
        EditEntryDialog(
            entry = e,
            activityName = byId[e.spaceId]?.name ?: "",
            onDismiss = { editing = null },
            onSave = { date, note -> Repo.updateEntry(e, date, note); editing = null },
            onDelete = { Repo.deleteEntry(e); editing = null },
        )
    }

    when (dialog) {
        "new" -> NewActivityDialog(
            onDismiss = { dialog = null },
            onCreate = { name, color ->
                dialog = null
                selectedId = Repo.createActivity(name, color)
            },
        )
        "rename" -> selected?.let { s ->
            TextInputDialog("Premenovať činnosť", s.name, "Uložiť", onDismiss = { dialog = null }) {
                Repo.renameSpace(s.id, it); dialog = null
            }
        }
        "color" -> selected?.let { s ->
            var c by remember { mutableIntStateOf(s.color) }
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text("Farba činnosti") },
                text = { ColorPicker(c) { c = it } },
                confirmButton = { TextButton(onClick = { Repo.setColor(s.id, c); dialog = null }) { Text("Uložiť") } },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("Zrušiť") } },
            )
        }
        "delete" -> selected?.let { s ->
            ConfirmDialog(
                "Zmazať „${s.name}“?",
                "Zmažú sa všetky záznamy tejto činnosti, aj pre ostatných členov.",
                "Zmazať",
                onDismiss = { dialog = null },
            ) {
                dialog = null
                selectedId = null
                scope.launch { runCatching { Repo.deleteSpace(s.id) } }
            }
        }
        "leave" -> selected?.let { s ->
            ConfirmDialog(
                "Opustiť „${s.name}“?",
                "Prestanete vidieť túto činnosť a jej záznamy.",
                "Opustiť",
                onDismiss = { dialog = null },
            ) {
                dialog = null
                selectedId = null
                scope.launch { runCatching { Repo.leaveSpace(s.id) } }
            }
        }
    }
}

@Composable
private fun ActivityChips(activities: List<Space>, selectedId: String?, onSelect: (String?) -> Unit, onNew: () -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(selected = selectedId == null, onClick = { onSelect(null) }, label = { Text("Všetko") })
        }
        items(activities, key = { it.id }) { a ->
            FilterChip(
                selected = a.id == selectedId,
                onClick = { onSelect(if (a.id == selectedId) null else a.id) },
                label = { Text(a.name) },
                leadingIcon = { Dot(spaceColor(a.color), 10) },
            )
        }
        item {
            FilterChip(
                selected = false,
                onClick = onNew,
                label = { Text("Nová činnosť") },
                leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)) },
            )
        }
    }
}

@Composable
private fun Dot(color: Color, sizeDp: Int) {
    Box(Modifier.size(sizeDp.dp).clip(CircleShape).background(color))
}

@Composable
private fun MonthCalendar(
    month: YearMonth,
    today: LocalDate,
    dots: Map<LocalDate, List<Color>>,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onDay: (LocalDate) -> Unit,
) {
    Column(Modifier.padding(horizontal = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrev) { Icon(Icons.Rounded.ChevronLeft, "Predchádzajúci mesiac") }
            Text(
                monthTitle(month),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onNext) { Icon(Icons.Rounded.ChevronRight, "Ďalší mesiac") }
        }
        if (month != YearMonth.from(today)) {
            TextButton(onClick = onToday, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(18.dp))
                Text(" Dnes")
            }
        }
        Row {
            weekdaysShort.forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                )
            }
        }
        val offset = month.atDay(1).dayOfWeek.value - 1
        val cells = offset + month.lengthOfMonth()
        val rows = (cells + 6) / 7
        for (r in 0 until rows) {
            Row {
                for (c in 0 until 7) {
                    val dayNum = r * 7 + c - offset + 1
                    Box(Modifier.weight(1f).height(52.dp), contentAlignment = Alignment.TopCenter) {
                        if (dayNum in 1..month.lengthOfMonth()) {
                            val date = month.atDay(dayNum)
                            DayCell(date, date == today, dots[date].orEmpty()) { onDay(date) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, isToday: Boolean, dots: List<Color>, onClick: () -> Unit) {
    val hasEntries = dots.isNotEmpty()
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(top = 4.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val ring = if (isToday) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier
        Box(
            Modifier.size(30.dp).clip(CircleShape)
                .background(if (hasEntries) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                .then(ring),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${date.dayOfMonth}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday || hasEntries) FontWeight.Bold else FontWeight.Normal,
            )
        }
        Spacer(Modifier.height(3.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            dots.take(4).forEach { Dot(it, 6) }
        }
    }
}

@Composable
private fun SummaryHeader(activity: Space, entries: List<LogEntry>, today: LocalDate) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(spaceColor(activity.color), 12)
                Spacer(Modifier.width(8.dp))
                Text(activity.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(timesCount(entries.size), style = MaterialTheme.typography.titleMedium)
            }
            if (entries.isEmpty()) {
                Text(
                    "Zatiaľ žiadny záznam. Ťuknite na deň v kalendári alebo na „Zapísať dnes“.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val past = entries.filter { !it.date.isAfter(today) }
                past.lastOrNull()?.let {
                    Text("Naposledy ${longDate(it.date)} (${relativeDays(it.date, today)})")
                }
                if (entries.size >= 2) {
                    val days = ChronoUnit.DAYS.between(entries.first().date, entries.last().date)
                    val avg = Math.round(days.toDouble() / (entries.size - 1))
                    Text(
                        "Priemerne každých $avg dní · od ${longDate(entries.first().date)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun YearHeader(year: Int) {
    Text(
        "$year",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 2.dp),
    )
}

@Composable
private fun EntryRow(entry: LogEntry, activity: Space?, showAuthor: Boolean, withYear: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            weekdayShort(entry.date),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(28.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(longDate(entry.date, withYear), style = MaterialTheme.typography.bodyLarge)
            val sub = listOfNotNull(
                entry.note.takeIf { it.isNotBlank() },
                entry.createdByName?.takeIf { showAuthor }?.let { "zapísal(a) $it" },
            ).joinToString(" · ")
            if (sub.isNotEmpty()) {
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (activity != null) {
            Spacer(Modifier.width(8.dp))
            Dot(spaceColor(activity.color), 10)
            Text(
                " ${activity.name}",
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 120.dp),
            )
        }
    }
}

@Composable
private fun EmptyLog(modifier: Modifier, onCreate: () -> Unit) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text("Kalendár činností", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Zapisujte si, kedy ste čo robili – kosenie, polievanie, výmenu oleja. " +
                "Ku každej činnosti potom uvidíte všetky termíny pod sebou a môžete ju zdieľať s rodinou.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = { Repo.createActivity("Kosenie", 0) }) { Text("Vytvoriť „Kosenie“") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onCreate) { Text("Vytvoriť vlastnú činnosť") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayDialog(
    day: LocalDate,
    activities: List<Space>,
    preselectedId: String?,
    dayEntries: List<LogEntry>,
    byId: Map<String, Space>,
    onEdit: (LogEntry) -> Unit,
    onDismiss: () -> Unit,
    onAdd: (spaceId: String, note: String) -> Unit,
) {
    var chosen by remember { mutableStateOf(preselectedId) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(longDateWithDay(day).replaceFirstChar { it.uppercase() }) },
        text = {
            Column(Modifier.heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (dayEntries.isNotEmpty()) {
                    Text("Zapísané", style = MaterialTheme.typography.labelLarge)
                    dayEntries.forEach { e ->
                        val a = byId[e.spaceId]
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Dot(spaceColor(a?.color ?: 0), 10)
                            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                Text(a?.name ?: "", style = MaterialTheme.typography.bodyLarge)
                                if (e.note.isNotBlank()) {
                                    Text(e.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            IconButton(onClick = { onEdit(e) }) { Icon(Icons.Rounded.Edit, "Upraviť") }
                        }
                    }
                    HorizontalDivider()
                }
                Text("Zapísať činnosť", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    activities.forEach { a ->
                        FilterChip(
                            selected = a.id == chosen,
                            onClick = { chosen = a.id },
                            label = { Text(a.name) },
                            leadingIcon = { Dot(spaceColor(a.color), 10) },
                        )
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(1000) },
                    label = { Text("Poznámka (nepovinné)") },
                    placeholder = { Text("napr. aj za plotom") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = chosen != null, onClick = { chosen?.let { onAdd(it, note) } }) { Text("Zapísať") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zavrieť") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditEntryDialog(
    entry: LogEntry,
    activityName: String,
    onDismiss: () -> Unit,
    onSave: (LocalDate, String) -> Unit,
    onDelete: () -> Unit,
) {
    var date by remember { mutableStateOf(entry.date) }
    var note by remember { mutableStateOf(entry.note) }
    var picking by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(activityName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(18.dp))
                    Text("  ${longDate(date)}")
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(1000) },
                    label = { Text("Poznámka") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                entry.createdByName?.let {
                    Text("Zapísal(a) $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error)
                    Text(" Zmazať záznam", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(date, note) }) { Text("Uložiť") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zrušiť") } },
    )

    if (picking) {
        // DatePicker pracuje s polnocou v UTC
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    picking = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Zrušiť") } },
        ) { DatePicker(state = state) }
    }

    if (confirmDelete) {
        ConfirmDialog(
            "Zmazať záznam?",
            "$activityName · ${longDate(entry.date)}",
            "Zmazať",
            onDismiss = { confirmDelete = false },
        ) { confirmDelete = false; onDelete() }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewActivityDialog(onDismiss: () -> Unit, onCreate: (name: String, color: Int) -> Unit) {
    var name by remember { mutableStateOf("") }
    var color by remember { mutableIntStateOf((0 until spaceColors.size).random()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nová činnosť") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(100) },
                    label = { Text("Názov") }, placeholder = { Text("napr. Kosenie") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Kosenie", "Polievanie", "Strihanie plota", "Hnojenie", "Výmena oleja").forEach { s ->
                        FilterChip(selected = name == s, onClick = { name = s }, label = { Text(s) })
                    }
                }
                ColorPicker(color) { color = it }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onCreate(name, color) }) { Text("Vytvoriť") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zrušiť") } },
    )
}
