package sk.kvz.priestor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import sk.kvz.priestor.data.Repo
import sk.kvz.priestor.data.Section

@Composable
fun JoinDialog(initialCode: String, onDismiss: () -> Unit, onJoined: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf(initialCode) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val err = error
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pripojiť sa kódom") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Zadajte kód pozvánky do oblasti alebo činnosti, ktorý vám niekto poslal.")
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().take(12); error = null },
                    label = { Text("Kód") },
                    singleLine = true,
                    isError = err != null,
                    supportingText = if (err != null) {
                        { Text(err) }
                    } else null,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && code.isNotBlank(),
                onClick = {
                    busy = true
                    scope.launch {
                        Repo.joinByCode(code)
                            .onSuccess(onJoined)
                            .onFailure { error = it.message ?: "Nepodarilo sa pripojiť. Skontrolujte internet." }
                        busy = false
                    }
                },
            ) { Text(if (busy) "Pripájam…" else "Pripojiť") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zrušiť") } },
    )
}

@Composable
fun NewSpaceDialog(onDismiss: () -> Unit, onCreate: (name: String, sections: List<String>, color: Int) -> Unit) {
    var name by remember { mutableStateOf("") }
    var sections by remember { mutableStateOf("") }
    var color by remember { mutableStateOf((0 until spaceColors.size).random()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nová oblasť") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Názov") }, placeholder = { Text("napr. Chata") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                OutlinedTextField(
                    value = sections, onValueChange = { sections = it },
                    label = { Text("Sekcie (nepovinné)") },
                    placeholder = { Text("Nakúpiť, Urobiť") },
                    supportingText = { Text("Oddeľte čiarkou. Dajú sa zmeniť aj neskôr.") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                ColorPicker(color) { color = it }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                onCreate(name, sections.split(",").map { it.trim() }.filter { it.isNotEmpty() }, color)
            }) { Text("Vytvoriť") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zrušiť") } },
    )
}

@Composable
fun ColorPicker(selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        spaceColors.forEachIndexed { i, c ->
            val ring = if (i == selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier
            androidx.compose.foundation.layout.Box(
                Modifier.size(28.dp).clip(CircleShape).background(c).then(ring).clickable { onSelect(i) }
            )
        }
    }
}

@Composable
fun TextInputDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zrušiť") } },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirm, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zrušiť") } },
    )
}

/** Úprava sekcií oblasti: premenovať, pridať, odstrániť. */
@Composable
fun EditSectionsDialog(initial: List<Section>, onDismiss: () -> Unit, onSave: (List<Section>) -> Unit) {
    val items = remember { mutableStateListOf(*initial.toTypedArray()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sekcie") },
        text = {
            Column {
                Text(
                    "Úlohy z odstránenej sekcie zostanú v oblasti bez sekcie.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    itemsIndexed(items, key = { _, s -> s.id }) { i, s ->
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = s.name,
                                onValueChange = { items[i] = s.copy(name = it) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            )
                            IconButton(onClick = { items.removeAt(i) }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Odstrániť")
                            }
                        }
                    }
                }
                TextButton(onClick = { items.add(Section(Repo.newId(), "")) }) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Text("Pridať sekciu")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(items.filter { it.name.isNotBlank() }) }) { Text("Uložiť") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zrušiť") } },
    )
}
