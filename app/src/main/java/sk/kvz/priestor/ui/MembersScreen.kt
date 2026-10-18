package sk.kvz.priestor.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import sk.kvz.priestor.data.Repo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MembersScreen(spaceId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val flow = remember(spaceId) { Repo.spaceFlow(spaceId) }
    val space by flow.collectAsStateWithLifecycle(initialValue = null)
    var inviteCode by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var removing by remember { mutableStateOf<Pair<String, String>?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Členovia · ${space?.name ?: ""}") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Späť") } },
            )
        },
    ) { padding ->
        val s = space ?: return@Scaffold
        val isOwner = s.ownerId == Repo.uid
        LazyColumn(Modifier.padding(padding)) {
            item {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (s.isLog) "Túto činnosť a jej záznamy vidia iba ľudia v zozname nižšie. Ostatné vaše činnosti ani oblasti sa im nezobrazia."
                        else "Túto oblasť vidia iba ľudia v zozname nižšie. Ostatné vaše oblasti sa im nezobrazia.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            busy = true; error = null
                            scope.launch {
                                runCatching { Repo.createInvite(s) }
                                    .onSuccess { inviteCode = it }
                                    .onFailure { error = "Pozvánku sa nepodarilo vytvoriť. Ste pripojený na internet?" }
                                busy = false
                            }
                        },
                    ) { Text(if (busy) "Vytváram…" else "Pozvať ľudí") }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
            items(s.members, key = { it }) { uid ->
                val name = s.memberNames[uid] ?: "Neznámy"
                ListItem(
                    headlineContent = { Text(if (uid == Repo.uid) "$name (vy)" else name) },
                    supportingContent = { if (uid == s.ownerId) Text("Vlastník") },
                    trailingContent = {
                        if (isOwner && uid != Repo.uid) {
                            IconButton(onClick = { removing = uid to name }) {
                                Icon(Icons.Rounded.PersonRemove, "Odobrať")
                            }
                        }
                    },
                )
            }
        }

        inviteCode?.let { code ->
            val link = "https://priestor.jurajzachar.sk/join/$code"
            AlertDialog(
                onDismissRequest = { inviteCode = null },
                title = { Text("Pozvánka do „${s.name}“") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Pošlite tento kód alebo odkaz. Platí 7 dní a môže ho použiť ktokoľvek, kto ho dostane.")
                        Text(code, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val what = if (s.isLog) "k činnosti" else "do oblasti"
                        val text = "Pridaj sa $what „${s.name}“ v aplikácii Priestor.\n$link\nKód: $code"
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                        context.startActivity(Intent.createChooser(send, "Poslať pozvánku"))
                    }) {
                        Icon(Icons.Rounded.Share, null)
                        Text(" Zdieľať")
                    }
                },
                dismissButton = { TextButton(onClick = { inviteCode = null }) { Text("Zavrieť") } },
            )
        }

        removing?.let { (uid, name) ->
            ConfirmDialog(
                "Odobrať $name?",
                if (s.isLog) "Prestane vidieť túto činnosť aj jej záznamy." else "Prestane vidieť túto oblasť aj jej úlohy.",
                "Odobrať",
                onDismiss = { removing = null },
            ) {
                Repo.removeMember(s.id, uid)
                removing = null
            }
        }
    }
}
