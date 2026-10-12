package sk.kvz.priestor.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import sk.kvz.priestor.data.Repo
import sk.kvz.priestor.data.Space
import sk.kvz.priestor.data.TaskItem
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Spodný panel na pridanie alebo úpravu úlohy. [task] == null → nová úloha. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditor(
    space: Space,
    task: TaskItem?,
    defaultSectionId: String?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val initialDue = task?.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone) }

    var title by remember { mutableStateOf(task?.title ?: "") }
    var note by remember { mutableStateOf(task?.note ?: "") }
    var sectionId by remember { mutableStateOf(task?.sectionId ?: defaultSectionId) }
    var date by remember { mutableStateOf<LocalDate?>(initialDue?.toLocalDate()) }
    var time by remember { mutableStateOf<LocalTime?>(if (task?.hasTime == true) initialDue?.toLocalTime() else null) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (task == null) {
            delay(250)
            runCatching { focus.requestFocus() }
        }
    }

    fun save() {
        if (title.isBlank()) return
        val dueAt = date?.let { d -> d.atTime(time ?: LocalTime.MIDNIGHT).atZone(zone).toInstant().toEpochMilli() }
        Repo.saveTask(space.id, task?.id, title, note, sectionId, dueAt, time != null)
        onSaved()
        onDismiss()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (task == null) "Nová úloha" else "Upraviť úlohu", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("Čo treba urobiť") },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            OutlinedTextField(
                value = note, onValueChange = { note = it },
                label = { Text("Poznámka (nepovinné)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 1, maxLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )

            if (space.sections.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(selected = sectionId == null, onClick = { sectionId = null }, label = { Text("Bez sekcie") })
                    space.sections.forEach { s ->
                        FilterChip(selected = sectionId == s.id, onClick = { sectionId = s.id }, label = { Text(s.name) })
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val d = date
                if (d == null) {
                    AssistChip(
                        onClick = { pickDate = true },
                        label = { Text("Termín") },
                        leadingIcon = { Icon(Icons.Rounded.Event, null) },
                    )
                } else {
                    InputChip(
                        selected = true,
                        onClick = { pickDate = true },
                        label = { Text(formatDue(d.atStartOfDay(zone).toInstant().toEpochMilli(), false)) },
                        leadingIcon = { Icon(Icons.Rounded.Event, null) },
                    )
                    InputChip(
                        selected = time != null,
                        onClick = { pickTime = true },
                        label = { Text(time?.let { "%d:%02d".format(it.hour, it.minute) } ?: "Čas") },
                        leadingIcon = { Icon(Icons.Rounded.Schedule, null) },
                    )
                    TextButton(onClick = { date = null; time = null }) { Text("Bez termínu") }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (task != null) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Zmazať", color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Zrušiť") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = ::save, enabled = title.isNotBlank()) { Text("Uložiť") }
            }
            if (task != null && task.createdByName != null) {
                Text(
                    "Pridal(a): ${task.createdByName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (pickDate) {
        // DatePicker pracuje s polnocou v UTC
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (date ?: LocalDate.now(zone)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Zrušiť") } },
        ) { DatePicker(state = state) }
    }

    if (pickTime) {
        val now = time ?: LocalTime.of(9, 0)
        val state = rememberTimePickerState(initialHour = now.hour, initialMinute = now.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = { time = LocalTime.of(state.hour, state.minute); pickTime = false }) { Text("OK") }
            },
            dismissButton = {
                Row {
                    if (time != null) TextButton(onClick = { time = null; pickTime = false }) { Text("Bez času") }
                    TextButton(onClick = { pickTime = false }) { Text("Zrušiť") }
                }
            },
        )
    }

    if (confirmDelete && task != null) {
        ConfirmDialog("Zmazať úlohu?", task.title, "Zmazať", onDismiss = { confirmDelete = false }) {
            Repo.deleteTask(task)
            onSaved()
            onDismiss()
        }
    }
}
