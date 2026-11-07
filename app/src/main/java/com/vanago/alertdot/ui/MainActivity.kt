package com.vanago.alertdot.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vanago.alertdot.app
import com.vanago.alertdot.data.AppSettings
import kotlinx.coroutines.launch

enum class Screen(val title: String, val tab: String, val emoji: String) {
    HOME("AlertDot", "Головна", "🏠"),
    SOURCE("Джерело подій", "Джерело", "📨"),
    OVERLAY("Кружечок", "Кружечок", "🔴"),
    SOUNDS("Звуки", "Звуки", "🔊"),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = app.settingsRepo
        setContent {
            val settings by repo.settings.collectAsStateWithLifecycle(initialValue = null)
            AlertDotTheme(dark = settings?.darkTheme ?: true) {
                val s = settings
                if (s == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    AppRoot(s)
                }
            }
        }
    }
}

/** Passed to screens to persist a settings change. */
typealias UpdateSettings = ((AppSettings) -> AppSettings) -> Unit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(settings: AppSettings) {
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // Remembered so the screens do not see a new lambda (and recompose) on every recomposition.
    val update: UpdateSettings = remember(scope, context) {
        { t -> scope.launch { context.app.settingsRepo.update(t) } }
    }

    BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(screen.title, fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            NavigationBar {
                Screen.entries.forEach { s ->
                    NavigationBarItem(
                        selected = s == screen,
                        onClick = { screen = s },
                        icon = { Text(s.emoji, fontSize = 20.sp) },
                        label = { Text(s.tab) },
                    )
                }
            }
        },
    ) { padding ->
        // A separate scroll state per tab, so switching tabs does not keep the old offset.
        key(screen) {
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (screen) {
                    Screen.HOME -> HomeScreen(settings, update) { screen = it }
                    Screen.SOURCE -> SourceScreen(settings, update)
                    Screen.OVERLAY -> OverlayScreen(settings, update)
                    Screen.SOUNDS -> SoundsScreen(settings, update)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
