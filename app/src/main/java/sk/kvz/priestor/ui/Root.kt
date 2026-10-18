package sk.kvz.priestor.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import sk.kvz.priestor.data.Repo
import sk.kvz.priestor.reminders.Reminders

private object Loading

@Composable
fun PriestorRoot(
    joinCode: String?,
    onJoinCodeHandled: () -> Unit,
    openSpaceId: String?,
    onOpenSpaceHandled: () -> Unit,
) {
    val userFlow = remember { Repo.userFlow() }
    val user by userFlow.collectAsStateWithLifecycle(initialValue = Loading)

    Surface(modifier = Modifier.fillMaxSize()) {
        when (user) {
            Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            null -> SignInScreen()
            else -> SignedIn(joinCode, onJoinCodeHandled, openSpaceId, onOpenSpaceHandled)
        }
    }
}

@Composable
private fun SignedIn(
    joinCode: String?,
    onJoinCodeHandled: () -> Unit,
    openSpaceId: String?,
    onOpenSpaceHandled: () -> Unit,
) {
    val context = LocalContext.current
    val nav = rememberNavController()

    // Povolenie notifikácií (Android 13+) – opýtame sa raz po prihlásení.
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        Reminders.syncNow(context)
        if (Build.VERSION.SDK_INT >= 33 && !Reminders.canNotify(context)) {
            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Otvorenie oblasti z notifikácie
    LaunchedEffect(openSpaceId) {
        if (openSpaceId != null) {
            nav.navigate("space/$openSpaceId") { launchSingleTop = true }
            onOpenSpaceHandled()
        }
    }

    // Pripojenie cez odkaz s pozvánkou
    var joinDialogCode by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(joinCode) {
        if (joinCode != null) {
            joinDialogCode = joinCode
            onJoinCodeHandled()
        }
    }
    joinDialogCode?.let { code ->
        JoinDialog(
            initialCode = code,
            onDismiss = { joinDialogCode = null },
            onJoined = { spaceId ->
                joinDialogCode = null
                nav.navigate("open/$spaceId") { launchSingleTop = true }
            },
        )
    }

    val spaceArg = listOf(navArgument("id") { type = NavType.StringType })
    NavHost(navController = nav, startDestination = "spaces") {
        composable("spaces") {
            SpacesScreen(
                onOpen = { nav.navigate("space/$it") },
                onJoin = { joinDialogCode = "" },
                onSettings = { nav.navigate("settings") },
                onLog = { nav.navigate("log") },
            )
        }
        composable(
            "log?select={select}",
            arguments = listOf(navArgument("select") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { entry ->
            ActivityLogScreen(
                initialSelectedId = entry.arguments?.getString("select"),
                onBack = { nav.popBackStack() },
                onMembers = { nav.navigate("members/$it") },
            )
        }
        // Po pripojení cez pozvánku: otvorí oblasť alebo činnosť podľa jej typu.
        composable("open/{id}", arguments = spaceArg) { entry ->
            val id = entry.arguments?.getString("id") ?: return@composable
            val flow = remember(id) { Repo.spaceFlow(id) }
            val space by flow.collectAsStateWithLifecycle(initialValue = null)
            LaunchedEffect(space) {
                val s = space ?: return@LaunchedEffect
                nav.navigate(if (s.isLog) "log?select=${s.id}" else "space/${s.id}") {
                    popUpTo("open/{id}") { inclusive = true }
                }
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        composable("space/{id}", arguments = spaceArg) { entry ->
            val id = entry.arguments?.getString("id") ?: return@composable
            SpaceScreen(
                spaceId = id,
                onBack = { nav.popBackStack() },
                onArchive = { nav.navigate("archive/$id") },
                onMembers = { nav.navigate("members/$id") },
                onGone = { nav.popBackStack("spaces", inclusive = false) },
            )
        }
        composable("archive/{id}", arguments = spaceArg) { entry ->
            val id = entry.arguments?.getString("id") ?: return@composable
            ArchiveScreen(spaceId = id, onBack = { nav.popBackStack() })
        }
        composable("members/{id}", arguments = spaceArg) { entry ->
            val id = entry.arguments?.getString("id") ?: return@composable
            MembersScreen(spaceId = id, onBack = { nav.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}
