package no.mwmai.pyquest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import no.mwmai.pyquest.data.CurriculumRepository
import no.mwmai.pyquest.data.ProgressStore
import no.mwmai.pyquest.ui.QuestionScreen
import no.mwmai.pyquest.ui.QuizSession
import no.mwmai.pyquest.ui.TrackScreen
import no.mwmai.pyquest.ui.theme.Pal
import no.mwmai.pyquest.ui.theme.PyQuestTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PyQuestTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Pal.Screen) {
                    PyQuestApp()
                }
            }
        }
    }
}

private data class LevelRoute(val tier: Int, val level: Int)

@Composable
private fun PyQuestApp() {
    val context = LocalContext.current
    val repository = remember { CurriculumRepository(context.assets) }
    val store = remember { ProgressStore(context) }
    val tiers = remember { repository.allTiers() }

    var route by remember { mutableStateOf<LevelRoute?>(null) }
    var progress by remember { mutableStateOf(store.load()) }
    var plainLabels by remember { mutableStateOf(store.plainLabels) }

    val active = route
    if (active == null) {
        TrackScreen(
            tiers = tiers,
            progress = progress,
            plainLabels = plainLabels,
            onTogglePlainLabels = {
                plainLabels = !plainLabels
                store.plainLabels = plainLabels
            },
            onStartLevel = { tier, level -> route = LevelRoute(tier, level) },
        )
    } else {
        val tier = tiers.first { it.tier == active.tier }
        val session = remember(active) { QuizSession(tier.level(active.level), store) }
        QuestionScreen(
            tier = tier,
            session = session,
            plainLabels = plainLabels,
            onBack = {
                progress = store.load()
                route = null
            },
        )
    }
}
