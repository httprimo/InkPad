package com.personal.inkpad

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.personal.inkpad.ui.navigation.InkPadNavHost
import com.personal.inkpad.ui.theme.InkPadTheme
import com.personal.inkpad.ui.theme.ThemeMode
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val prefs = InkPadApp.instance.syncPreferences
            val syncSettings by prefs.settings.collectAsState(
                initial = com.personal.inkpad.data.sync.SyncSettings()
            )
            var themeMode by remember { mutableStateOf(ThemeMode.SYSTEM) }
            val scope = rememberCoroutineScope()

            LaunchedEffect(syncSettings.themeMode) {
                themeMode = runCatching { ThemeMode.valueOf(syncSettings.themeMode) }
                    .getOrDefault(ThemeMode.SYSTEM)
            }

            InkPadTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    InkPadNavHost(
                        themeMode = themeMode,
                        onThemeModeChange = { mode ->
                            themeMode = mode
                            scope.launch { prefs.setThemeMode(mode.name) }
                        }
                    )
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Opportunistic sync when leaving the app if online
        InkPadApp.instance.autoSync.syncNowInBackground()
    }
}
