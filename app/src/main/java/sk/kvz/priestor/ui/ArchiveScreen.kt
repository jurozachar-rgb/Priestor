package sk.kvz.priestor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sk.kvz.priestor.data.Repo
import sk.kvz.priestor.reminders.Reminders

/** Archív urobených úloh. Odškrtnutím sa úloha vráti medzi otvorené. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(spaceId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val spaceFlow = remember(spaceId) { Repo.spaceFlow(spaceId) }
    val tasksFlow = remember(spaceId) { Repo.tasksFlow(spaceId) }
    val space by spaceFlow.collectAsStateWithLifecycle(initialValue = null)
    val tasks by tasksFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val done = tasks.filter { it.done }.sortedByDescending { it.doneAt ?: Long.MAX_VALUE }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Archív · ${space?.name ?: ""}") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Späť") } },
            )
        },
    ) { padding ->
        if (done.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Zatiaľ nič urobené.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp)) {
                items(done, key = { it.id }) { t ->
                    val by = listOfNotNull(t.doneAt?.let { formatDate(it) }, t.doneByName).joinToString(" · ")
                    TaskRow(
                        task = t.copy(note = by, dueAt = null),
                        sectionName = space?.sectionName(t.sectionId),
                        onCheck = { Repo.setDone(t, false); Reminders.syncNow(context) },
                        onClick = {},
                        trailing = {
                            IconButton(onClick = { Repo.deleteTask(t) }) {
                                Icon(Icons.Rounded.DeleteOutline, "Zmazať natrvalo")
                            }
                        },
                    )
                }
            }
        }
    }
}
