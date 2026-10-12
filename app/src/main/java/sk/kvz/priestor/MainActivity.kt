package sk.kvz.priestor

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import sk.kvz.priestor.reminders.Reminders
import sk.kvz.priestor.ui.PriestorRoot
import sk.kvz.priestor.ui.theme.PriestorTheme

class MainActivity : ComponentActivity() {

    /** Kód pozvánky z odkazu https://priestor.kvz.sk/join/KOD */
    private val pendingJoinCode = mutableStateOf<String?>(null)
    /** Oblasť, ktorú treba otvoriť (z notifikácie). */
    private val pendingSpaceId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            PriestorTheme {
                PriestorRoot(
                    joinCode = pendingJoinCode.value,
                    onJoinCodeHandled = { pendingJoinCode.value = null },
                    openSpaceId = pendingSpaceId.value,
                    onOpenSpaceHandled = { pendingSpaceId.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        Reminders.syncNow(this)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        intent.data?.let { uri ->
            if (uri.host == "priestor.kvz.sk" && uri.pathSegments.firstOrNull() == "join") {
                pendingJoinCode.value = uri.pathSegments.getOrNull(1) ?: uri.getQueryParameter("code")
            }
        }
        intent.getStringExtra(Reminders.EXTRA_SPACE_ID)?.let { pendingSpaceId.value = it }
    }
}
