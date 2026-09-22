package com.personal.inkpad.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.personal.inkpad.InkPadApp
import com.personal.inkpad.ui.auth.SignInScreen
import com.personal.inkpad.ui.auth.SignUpScreen
import com.personal.inkpad.ui.create.CreateNotebookScreen
import com.personal.inkpad.ui.editor.EditorScreen
import com.personal.inkpad.ui.library.LibraryScreen
import com.personal.inkpad.ui.settings.SettingsScreen
import com.personal.inkpad.ui.theme.ThemeMode

object Routes {
    const val AUTH = "auth"
    const val SIGN_UP = "sign_up"
    const val LIBRARY = "library"
    const val CREATE = "create?folderId={folderId}"
    const val EDITOR = "editor/{notebookId}"
    const val SETTINGS = "settings"

    fun create(folderId: String?) =
        if (folderId == null) "create?folderId=" else "create?folderId=$folderId"

    fun editor(notebookId: String) = "editor/$notebookId"

    fun isAuthRoute(route: String?): Boolean =
        route == AUTH || route == SIGN_UP
}

@Composable
fun InkPadNavHost(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit
) {
    val prefs = InkPadApp.instance.syncPreferences
    val syncSettings by prefs.settings.collectAsState(
        initial = com.personal.inkpad.data.sync.SyncSettings()
    )
    var ready by remember { mutableStateOf(false) }
    var startDest by remember { mutableStateOf(Routes.AUTH) }

    LaunchedEffect(Unit) {
        val current = prefs.current()
        startDest = if (current.isLoggedIn) Routes.LIBRARY else Routes.AUTH
        ready = true
    }

    if (!ready) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val navController = rememberNavController()

    LaunchedEffect(syncSettings.isLoggedIn) {
        if (!syncSettings.isLoggedIn) {
            val route = navController.currentBackStackEntry?.destination?.route
            if (route != null && !Routes.isAuthRoute(route)) {
                navController.navigate(Routes.AUTH) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
    }

    fun enterLibrary() {
        navController.navigate(Routes.LIBRARY) {
            popUpTo(0) { inclusive = true }
            launchSingleTop = true
        }
    }

    NavHost(navController = navController, startDestination = startDest) {
        composable(Routes.AUTH) {
            SignInScreen(
                onAuthenticated = { enterLibrary() },
                onGoToSignUp = {
                    navController.navigate(Routes.SIGN_UP) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(Routes.SIGN_UP) {
            SignUpScreen(
                onAuthenticated = { enterLibrary() },
                onGoToSignIn = {
                    navController.popBackStack(Routes.AUTH, inclusive = false)
                }
            )
        }
        composable(Routes.LIBRARY) {
            LibraryScreen(
                onOpenNotebook = { navController.navigate(Routes.editor(it)) },
                onCreateNotebook = { folderId -> navController.navigate(Routes.create(folderId)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(
            route = "create?folderId={folderId}",
            arguments = listOf(navArgument("folderId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { entry ->
            val folderId = entry.arguments?.getString("folderId")?.ifBlank { null }
            CreateNotebookScreen(
                folderId = folderId,
                onCreated = { id ->
                    navController.popBackStack()
                    navController.navigate(Routes.editor(id))
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.EDITOR,
            arguments = listOf(navArgument("notebookId") { type = NavType.StringType })
        ) { entry ->
            val notebookId = entry.arguments?.getString("notebookId") ?: return@composable
            EditorScreen(
                notebookId = notebookId,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                onBack = { navController.popBackStack() },
                onSignedOut = {
                    navController.navigate(Routes.AUTH) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
    }
}
