package sk.kvz.priestor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import sk.kvz.priestor.data.Repo
import sk.kvz.priestor.data.Space
import sk.kvz.priestor.data.TaskItem
import sk.kvz.priestor.reminders.Reminders

private const val ALL = "__all__"

/** Poradie: najprv s termínom (najbližší hore), potom bez termínu (najnovšie hore). */
internal val taskOrder = compareBy<TaskItem>({ it.dueAt == null }, { it.dueAt ?: 0L }, { -it.createdAt })

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpaceScreen(
    spaceId: String,
    onBack: () -> Unit,
    onArchive: () -> Unit,
    onMembers: () -> Unit,
    onGone: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val spaceFlow = remember(spaceId) { Repo.spaceFlow(spaceId) }
    val tasksFlow = remember(spaceId) { Repo.tasksFlow(spaceId) }
    val spaceState = spaceFlow.collectAsStateWithLifecycle(initialValue = null)
    val tasks by tasksFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val space = spaceState.value

    // Oblasť zmizla (zmazaná alebo nás odobrali) – vrátime sa na zoznam.
    var seen by remember { mutableStateOf(false) }
    LaunchedEffect(space) {
        if (space != null) seen = true
        else if (seen) onGone()
    }

    var filter by rememberSaveable { mutableStateOf(ALL) }
    var editing by remember { mutableStateOf<TaskItem?>(null) }
    var creating by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }

    fun complete(task: TaskItem) {
        Repo.setDone(task, true)
        Reminders.syncNow(context)
        scope.launch {
            val r = snackbar.showSnackbar("Hotovo: ${task.title}", actionLabel = "Späť", withDismissAction = true)
            if (r == SnackbarResult.ActionPerformed) {
                Repo.setDone(task, false)
                Reminders.syncNow(context)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(space?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Späť") } },
                actions = {
                    IconButton(onClick = onMembers) { Icon(Icons.Rounded.PersonAdd, "Členovia a pozvánky") }
                    IconButton(onClick = onArchive) { Icon(Icons.Rounded.Inventory2, "Archív") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Ďalšie") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Upraviť sekcie") }, onClick = { menu = false; dialog = "sections" })
                            DropdownMenuItem(text = { Text("Premenovať") }, onClick = { menu = false; dialog = "rename" })
                            if (space != null && space.ownerId == Repo.uid) {
                                DropdownMenuItem(text = { Text("Zmazať oblasť") }, onClick = { menu = false; dialog = "delete" })
                            } else {
                                DropdownMenuItem(text = { Text("Opustiť oblasť") }, onClick = { menu = false; dialog = "leave" })
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) { Icon(Icons.Rounded.Add, "Nová úloha") }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (space == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val open = tasks.filter { !it.done }
        val validSectionIds = space.sections.map { it.id }.toSet()
        val active = if (filter != ALL && filter !in validSectionIds) ALL else filter

        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            if (space.sections.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(selected = active == ALL, onClick = { filter = ALL }, label = { Text("Všetko") })
                    space.sections.forEach { s ->
                        val count = open.count { it.sectionId == s.id }
                        FilterChip(
                            selected = active == s.id,
                            onClick = { filter = s.id },
                            label = { Text(if (count > 0) "${s.name}  $count" else s.name) },
                        )
                    }
                }
            }

            val shown = if (active == ALL) open else open.filter { it.sectionId == active }
            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Nič tu nie je.\nPridajte úlohu tlačidlom +",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 88.dp)) {
                    if (active == ALL && space.sections.isNotEmpty()) {
                        val groups = space.sections.map { s -> s.name to shown.filter { it.sectionId == s.id } } +
                            ("Bez sekcie" to shown.filter { it.sectionId !in validSectionIds })
                        groups.filter { it.second.isNotEmpty() }.forEach { (title, list) ->
                            item(key = "h-${list.first().id}") { SectionHeader(title) }
                            items(list.sortedWith(taskOrder), key = { it.id }) { t ->
                                TaskRow(t, null, onCheck = { complete(t) }, onClick = { editing = t })
                            }
                        }
                    } else {
                        items(shown.sortedWith(taskOrder), key = { it.id }) { t ->
                            TaskRow(t, null, onCheck = { complete(t) }, onClick = { editing = t })
                        }
                    }
                }
            }
        }
    }

    if (space != null) {
        if (creating || editing != null) {
            TaskEditor(
                space = space,
                task = editing,
                defaultSectionId = filter.takeIf { it != ALL && space.sections.any { s -> s.id == it } },
                onDismiss = { creating = false; editing = null },
                onSaved = { Reminders.syncNow(context) },
            )
        }
        when (dialog) {
            "sections" -> EditSectionsDialog(space.sections, onDismiss = { dialog = null }) {
                Repo.updateSections(space.id, it); dialog = null
            }
            "rename" -> TextInputDialog("Premenovať oblasť", space.name, "Uložiť", onDismiss = { dialog = null }) {
                Repo.renameSpace(space.id, it); dialog = null
            }
            "delete" -> ConfirmDialog(
                "Zmazať „${space.name}“?",
                "Zmažú sa všetky úlohy aj archív pre všetkých členov. Nedá sa to vrátiť.",
                "Zmazať", onDismiss = { dialog = null },
            ) {
                dialog = null
                scope.launch { runCatching { Repo.deleteSpace(space.id) }; Reminders.syncNow(context) }
                onGone()
            }
            "leave" -> ConfirmDialog(
                "Opustiť „${space.name}“?",
                "Oblasť zmizne z vášho zoznamu. Späť sa dostanete len cez novú pozvánku.",
                "Opustiť", onDismiss = { dialog = null },
            ) {
                dialog = null
                scope.launch { runCatching { Repo.leaveSpace(space.id) }; Reminders.syncNow(context) }
                onGone()
            }
        }
    }
}

@Composable
internal fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
internal fun TaskRow(
    task: TaskItem,
    sectionName: String?,
    onCheck: () -> Unit,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 8.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = task.done, onCheckedChange = { onCheck() })
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(task.title, style = MaterialTheme.typography.bodyLarge)
            val meta = buildList {
                task.dueAt?.let { add(formatDue(it, task.hasTime)) }
                sectionName?.let { add(it) }
                if (task.note.isNotBlank()) add(task.note.lines().first())
            }
            if (meta.isNotEmpty()) {
                val overdue = !task.done && task.dueAt != null && isOverdue(task.dueAt, task.hasTime)
                Text(
                    meta.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

@Suppress("unused")
private fun Space.isOwner() = ownerId == Repo.uid
