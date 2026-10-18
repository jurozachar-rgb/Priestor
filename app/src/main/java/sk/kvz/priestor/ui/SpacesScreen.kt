package sk.kvz.priestor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sk.kvz.priestor.data.Repo
import sk.kvz.priestor.data.Space

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpacesScreen(onOpen: (String) -> Unit, onJoin: () -> Unit, onSettings: () -> Unit, onLog: () -> Unit) {
    val flow = remember { Repo.spacesFlow() }
    val spaces by flow.collectAsStateWithLifecycle(initialValue = null)
    var showNew by remember { mutableStateOf(false) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Priestor") },
                actions = {
                    IconButton(onClick = onLog) { Icon(Icons.Rounded.CalendarMonth, "Kalendár činností") }
                    IconButton(onClick = onJoin) { Icon(Icons.Rounded.GroupAdd, "Pripojiť sa kódom") }
                    IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Nastavenia") }
                },
                scrollBehavior = scroll,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showNew = true },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("Nová oblasť") },
            )
        },
    ) { padding ->
        val list = spaces
        when {
            list == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            list.isEmpty() -> EmptySpaces(Modifier.padding(padding), onCreate = { showNew = true }, onJoin = onJoin, onLog = onLog)
            else -> LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 4.dp,
                    bottom = padding.calculateBottomPadding() + 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "log") { LogCard(onLog) }
                items(list, key = { it.id }) { space -> SpaceCard(space) { onOpen(space.id) } }
            }
        }
    }

    if (showNew) {
        NewSpaceDialog(
            onDismiss = { showNew = false },
            onCreate = { name, sections, color ->
                showNew = false
                onOpen(Repo.createSpace(name, sections, color))
            },
        )
    }
}

@Composable
private fun LogCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(32.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Kalendár činností", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Kedy ste kosili, polievali, menili olej…",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SpaceCard(space: Space, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(spaceColor(space.color)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    space.name.take(1).uppercase(),
                    color = androidx.compose.ui.graphics.Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(space.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (space.sections.isNotEmpty()) {
                    Text(
                        space.sections.joinToString(" · ") { it.name },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (space.members.size > 1) {
                Icon(
                    Icons.Rounded.People, contentDescription = "Zdieľané",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp),
                )
                Text(
                    " ${space.members.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptySpaces(modifier: Modifier, onCreate: () -> Unit, onJoin: () -> Unit, onLog: () -> Unit) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Zatiaľ žiadne oblasti", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Oblasť je zoznam úloh pre jednu časť života – domov, nákup, chata, dovolenka. Každú môžete zdieľať s inými ľuďmi.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = {
            Repo.createSpace("Domov", emptyList(), 0)
            Repo.createSpace("Nákup", emptyList(), 2)
            Repo.createSpace("Chata", listOf("Nakúpiť", "Urobiť"), 1)
            Repo.createSpace("Rodičia", emptyList(), 3)
            Repo.createSpace("Dieťa", emptyList(), 4)
        }) { Text("Vytvoriť základné oblasti") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onCreate) { Text("Vytvoriť vlastnú") }
        Spacer(Modifier.height(8.dp))
        androidx.compose.material3.TextButton(onClick = onJoin) { Text("Mám kód pozvánky") }
        androidx.compose.material3.TextButton(onClick = onLog) { Text("Kalendár činností") }
    }
}
