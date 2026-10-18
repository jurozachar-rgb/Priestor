package sk.kvz.priestor.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import sk.kvz.priestor.auth.GoogleAuth
import sk.kvz.priestor.data.Repo
import sk.kvz.priestor.reminders.Reminders

const val PRIVACY_URL = "https://priestor.jurajzachar.sk/privacy/"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()
    val user = Repo.user
    var confirmDelete by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nastavenia") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Späť") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            ListItem(
                headlineContent = { Text(user?.displayName ?: "Účet") },
                supportingContent = { Text(user?.email ?: "") },
            )
            HorizontalDivider()
            ListItem(
                modifier = Modifier.clickable {
                    val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    context.startActivity(i)
                },
                leadingContent = { Icon(Icons.Rounded.Notifications, null) },
                headlineContent = { Text("Upozornenia") },
                supportingContent = {
                    Text(
                        (if (Reminders.canNotify(context)) "Zapnuté. " else "Vypnuté – ťuknite pre zapnutie. ") +
                            "Termín s časom: hodinu vopred a v čase termínu. Bez času: deň vopred o 18:00 a v deň termínu o 8:00."
                    )
                },
            )
            ListItem(
                modifier = Modifier.clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL))) },
                leadingContent = { Icon(Icons.Rounded.Policy, null) },
                headlineContent = { Text("Zásady ochrany súkromia") },
            )
            ListItem(
                modifier = Modifier.clickable {
                    scope.launch {
                        Reminders.cancelAll(context)
                        GoogleAuth.signOut(activity)
                    }
                },
                leadingContent = { Icon(Icons.AutoMirrored.Rounded.Logout, null) },
                headlineContent = { Text("Odhlásiť sa") },
            )
            ListItem(
                modifier = Modifier.clickable(enabled = !deleting) { confirmDelete = true },
                leadingContent = { Icon(Icons.Rounded.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
                headlineContent = { Text(if (deleting) "Mažem…" else "Zmazať účet a všetky dáta", color = MaterialTheme.colorScheme.error) },
                supportingContent = { Text("Vaše oblasti sa zmažú, z cudzích oblastí odídete.") },
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
            ListItem(headlineContent = {
                Text("Verzia ${runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""}", style = MaterialTheme.typography.bodySmall)
            })
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Zmazať účet?") },
            text = {
                Text("Zmažú sa všetky oblasti, ktoré ste vytvorili, vrátane úloh ostatných členov. Z oblastí iných ľudí budete odobratý. Nedá sa to vrátiť. Pre potvrdenie sa ešte raz prihlásite cez Google.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    deleting = true
                    error = null
                    scope.launch {
                        try {
                            GoogleAuth.reauthenticate(activity)
                            Reminders.cancelAll(context)
                            Repo.deleteAllMyData()
                            GoogleAuth.signOut(activity)
                        } catch (_: GoogleAuth.Cancelled) {
                        } catch (e: Exception) {
                            error = "Mazanie zlyhalo: ${e.localizedMessage}"
                        } finally {
                            deleting = false
                        }
                    }
                }) { Text("Zmazať", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Zrušiť") } },
        )
    }
}
