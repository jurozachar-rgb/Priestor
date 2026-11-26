package sk.kvz.priestor.ui

import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DragIndicator
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
import androidx.compose.material3.Surface
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import sk.kvz.priestor.data.Repo
import sk.kvz.priestor.data.Space
import sk.kvz.priestor.data.TaskItem
import sk.kvz.priestor.reminders.Reminders
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private const val ALL = "__all__"
private const val NO_SECTION = "__none__"

/**
 * Poradie úloh:
 *  1. úlohy, ktoré ešte nikto ručne nepresúval (napr. práve pridané) – najprv s termínom (najbližší hore),
 *     potom bez termínu (najnovšie hore),
 *  2. potom úlohy v poradí, ako ich niekto poukladal prstom.
 */
internal val taskOrder = compareBy<TaskItem>(
    { it.order != null }, { it.order ?: 0.0 },
    { it.dueAt == null }, { it.dueAt ?: 0L }, { -it.createdAt },
)

/** Riadok zoznamu: nadpis sekcie alebo úloha. [group] = v rámci ktorej skupiny sa dá úloha presúvať. */
private sealed interface ListRow { val key: String }
private data class HeaderRow(val title: String, override val key: String) : ListRow
private data class TaskLine(val task: TaskItem, val group: String) : ListRow {
    override val key: String get() = task.id
}

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
                            DropdownMenuItem(text = { Text("Zmeniť farbu") }, onClick = { menu = false; dialog = "color" })
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
            val grouped = active == ALL && space.sections.isNotEmpty()
            val computed: List<ListRow> = buildList {
                if (grouped) {
                    val groups = space.sections.map { s -> Triple(s.id, s.name, shown.filter { it.sectionId == s.id }) } +
                        Triple(NO_SECTION, "Bez sekcie", shown.filter { it.sectionId !in validSectionIds })
                    groups.filter { it.third.isNotEmpty() }.forEach { (gid, title, list) ->
                        add(HeaderRow(title, "h-$gid"))
                        list.sortedWith(taskOrder).forEach { add(TaskLine(it, gid)) }
                    }
                } else {
                    shown.sortedWith(taskOrder).forEach { add(TaskLine(it, active)) }
                }
            }

            // Počas ťahania prstom zobrazujeme lokálne poradie; po uložení ho nahradí poradie z databázy.
            var pending by remember { mutableStateOf<List<ListRow>?>(null) }
            var dragging by remember { mutableStateOf(false) }
            var dragGroup by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(computed) { if (!dragging) pending = null }
            val rows = pending ?: computed

            val haptic = LocalHapticFeedback.current
            val listState = rememberLazyListState()
            val reorderState = rememberReorderableLazyListState(listState) { from, to ->
                val cur = pending
                if (cur != null) {
                    val fromIdx = cur.indexOfFirst { it.key == from.key }
                    val toIdx = cur.indexOfFirst { it.key == to.key }
                    val a = cur.getOrNull(fromIdx) as? TaskLine
                    val b = cur.getOrNull(toIdx) as? TaskLine
                    // presúvať sa dá iba v rámci tej istej sekcie
                    if (a != null && b != null && a.group == b.group) {
                        pending = cur.toMutableList().apply { add(toIdx, removeAt(fromIdx)) }
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                }
            }

            fun dragStarted(line: TaskLine) {
                pending = rows
                dragging = true
                dragGroup = line.group
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }

            fun dragStopped() {
                dragging = false
                val cur = pending ?: return
                val group = cur.filterIsInstance<TaskLine>().filter { it.group == dragGroup }
                Repo.reorderTasks(space.id, group.map { it.task.id }, group.associate { it.task.id to it.task.order })
            }

            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Nič tu nie je.\nPridajte úlohu tlačidlom +",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 88.dp),
                ) {
                    items(rows, key = { it.key }) { row ->
                        when (row) {
                            is HeaderRow -> SectionHeader(row.title)
                            is TaskLine -> ReorderableItem(reorderState, key = row.key) { isDragging ->
                                val elevation by animateDpAsState(if (isDragging) 6.dp else 0.dp, label = "drag")
                                Surface(shadowElevation = elevation, color = MaterialTheme.colorScheme.background) {
                                    TaskRow(
                                        row.task, null,
                                        onCheck = { complete(row.task) },
                                        onClick = { editing = row.task },
                                        // podržať prst na úlohe a potiahnuť, alebo chytiť za úchyt vpravo
                                        modifier = Modifier.longPressDraggableHandle(
                                            onDragStarted = { dragStarted(row) },
                                            onDragStopped = { dragStopped() },
                                        ),
                                        trailing = {
                                            Icon(
                                                Icons.Rounded.DragIndicator,
                                                contentDescription = "Presunúť",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier
                                                    .draggableHandle(
                                                        onDragStarted = { dragStarted(row) },
                                                        onDragStopped = { dragStopped() },
                                                    )
                                                    .padding(8.dp),
                                            )
                                        },
                                    )
                                }
                            }
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
                allTasks = tasks,
            )
        }
        when (dialog) {
            "sections" -> EditSectionsDialog(space.sections, onDismiss = { dialog = null }) {
                Repo.updateSections(space.id, it); dialog = null
            }
            "color" -> {
                var c by remember { mutableIntStateOf(space.color) }
                AlertDialog(
                    onDismissRequest = { dialog = null },
                    title = { Text("Farba oblasti") },
                    text = { ColorPicker(c) { c = it } },
                    confirmButton = { TextButton(onClick = { Repo.setColor(space.id, c); dialog = null }) { Text("Uložiť") } },
                    dismissButton = { TextButton(onClick = { dialog = null }) { Text("Zrušiť") } },
                )
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
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 8.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
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
