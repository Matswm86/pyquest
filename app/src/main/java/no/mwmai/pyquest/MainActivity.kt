package no.mwmai.pyquest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.mwmai.pyquest.data.CurriculumRepository
import no.mwmai.pyquest.data.EventLog
import no.mwmai.pyquest.data.Progress
import no.mwmai.pyquest.data.ProgressStore
import no.mwmai.pyquest.model.CodexEntry
import no.mwmai.pyquest.model.Question
import no.mwmai.pyquest.model.Tier
import no.mwmai.pyquest.pytor.PytorCoach
import no.mwmai.pyquest.ui.PytorAvatar
import no.mwmai.pyquest.ui.PytorChat
import no.mwmai.pyquest.ui.PytorScreen
import no.mwmai.pyquest.ui.QuestionScreen
import no.mwmai.pyquest.ui.QuizSession
import no.mwmai.pyquest.ui.StatsScreen
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

private enum class Tab(val label: String) {
    TRACK("Track"),
    PYTOR("Pytor"),
    YOU("You"),
}

private data class LevelRoute(val tier: Int, val level: Int)

private class Content(val tiers: List<Tier>, val codex: List<CodexEntry>)

/**
 * The whole app is one composable with a handful of state variables: which tab,
 * which level (if any), the live session, and whether Pytor's chat is open on
 * top of a question. No navigation library, because three tabs and one modal
 * route do not need one, and every transition has to keep the session alive.
 */
@Composable
private fun PyQuestApp() {
    val context = LocalContext.current
    val repository = remember { CurriculumRepository(context.assets) }
    val store = remember { ProgressStore(context) }
    val log = remember { EventLog(context) }

    // Eight tier files and the Codex parse off the main thread so the first
    // frame is Pytor, not a blank screen.
    val content by produceState<Content?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { Content(repository.allTiers(), repository.codex()) }
    }
    val loaded = content
    if (loaded == null) {
        LoadingScreen()
        return
    }

    var tab by remember { mutableStateOf(Tab.TRACK) }
    var route by remember { mutableStateOf<LevelRoute?>(null) }
    var session by remember { mutableStateOf<QuizSession?>(null) }
    var pytorOverQuestion by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(store.load()) }
    var plainLabels by remember { mutableStateOf(store.plainLabels) }
    var pytorOnline by remember { mutableStateOf(store.pytorOnline) }
    val chat = remember { PytorChat().also { it.log = log } }
    val today = Progress.today()

    fun leaveLevel() {
        progress = store.load()
        pytorOverQuestion = false
        session = null
        route = null
    }

    BackHandler(enabled = pytorOverQuestion || route != null || tab != Tab.TRACK) {
        when {
            pytorOverQuestion -> pytorOverQuestion = false
            route != null -> leaveLevel()
            else -> tab = Tab.TRACK
        }
    }

    val active = route
    val live = session
    if (active != null && live != null) {
        val tier = loaded.tiers.first { it.tier == active.tier }
        if (pytorOverQuestion) {
            PytorScreen(
                chat = chat,
                codex = loaded.codex,
                online = pytorOnline,
                context = questionContext(tier, active.level, live.current),
                onClose = { pytorOverQuestion = false },
            )
        } else {
            QuestionScreen(
                tier = tier,
                level = active.level,
                session = live,
                plainLabels = plainLabels,
                onBack = ::leaveLevel,
                onReview = {
                    session = QuizSession(
                        live.misses, store, isReview = true,
                        tier = active.tier, level = active.level, log = log,
                    )
                },
                onAskPytor = {
                    val q = live.current
                    if (q != null && chat.draft.isBlank()) {
                        chat.draft = "I'm on this question: \"${q.prompt}\". Give me a hint, not the answer."
                    }
                    pytorOverQuestion = true
                },
            )
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize().background(Pal.Screen)) {
        Box(modifier = Modifier.weight(1f)) {
            when (tab) {
                Tab.TRACK -> TrackScreen(
                    tiers = loaded.tiers,
                    progress = progress,
                    today = today,
                    onStartLevel = { t, l ->
                        val tier = loaded.tiers.first { it.tier == t }
                        session = QuizSession(tier.level(l), store, tier = t, level = l, log = log)
                        route = LevelRoute(t, l)
                    },
                    onOpenPytor = { tab = Tab.PYTOR },
                )

                Tab.PYTOR -> PytorScreen(
                    chat = chat,
                    codex = loaded.codex,
                    online = pytorOnline,
                    context = trackContext(progress, loaded.tiers, today),
                    onClose = null,
                )

                Tab.YOU -> StatsScreen(
                    progress = progress,
                    tiers = loaded.tiers,
                    today = today,
                    plainLabels = plainLabels,
                    onTogglePlainLabels = {
                        plainLabels = !plainLabels
                        store.plainLabels = plainLabels
                    },
                    pytorOnline = pytorOnline,
                    onTogglePytorOnline = {
                        pytorOnline = !pytorOnline
                        store.pytorOnline = pytorOnline
                    },
                    onAskAbout = { tag ->
                        chat.draft = "I keep missing questions tagged \"$tag\". Explain the concept and give me one thing to try."
                        tab = Tab.PYTOR
                    },
                    onReset = {
                        store.reset()
                        progress = store.load()
                        log.log("reset")
                    },
                    log = log,
                )
            }
        }
        BottomBar(current = tab, onSelect = { tab = it })
    }
}

/** What Pytor knows about where the player is, sent with every chat message. */
private fun questionContext(tier: Tier, level: Int, question: Question?): String {
    val base = "PyQuest tier ${tier.tier} '${tier.title}', level $level."
    if (question == null) return base
    val choices = when {
        question.options.isNotEmpty() -> "Options: ${question.options.joinToString(" | ")}"
        question.tray.isNotEmpty() -> "Tray blocks: ${question.tray.joinToString(" | ")}"
        question.blocks.isNotEmpty() -> "Blocks: ${question.blocks.joinToString(" | ") { it.code }}"
        else -> ""
    }
    val code = question.code?.let { "Code shown:\n$it" } ?: question.template?.let { "Template with gaps:\n$it" } ?: ""
    return listOf(
        base,
        "Current question (${question.type.name.lowercase()}): ${question.prompt}",
        code,
        choices,
        "Tags: ${question.tags.joinToString(", ")}.",
        "The player is mid-question: hint first unless they explicitly ask for the answer.",
    ).filter { it.isNotBlank() }.joinToString("\n")
}

private fun trackContext(progress: Progress, tiers: List<Tier>, today: String): String {
    val weak = progress.weakTags.take(3).joinToString(", ") { "${it.first} (${(it.second * 100).toInt()}% missed)" }
    val next = PytorCoach.nextLevel(progress, tiers)
    return listOf(
        "PyQuest track screen. Player rank ${progress.rank}, ${progress.xp} XP, streak ${progress.streakOn(today)} days, ${progress.answered} answered.",
        next?.let { "Next level: tier ${it.first.tier} '${it.first.title}' level ${it.second}." } ?: "",
        if (weak.isNotBlank()) "Weak tags: $weak." else "",
    ).filter { it.isNotBlank() }.joinToString(" ")
}

@Composable
private fun LoadingScreen() {
    Column(
        modifier = Modifier.fillMaxSize().background(Pal.Screen),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PytorAvatar(size = 96.dp)
        Spacer(Modifier.height(18.dp))
        Text("PyQuest", style = MaterialTheme.typography.headlineMedium, color = Pal.Text)
        Spacer(Modifier.height(6.dp))
        Text("Pytor is unpacking the curriculum", style = MaterialTheme.typography.bodyMedium, color = Pal.Faint)
        Spacer(Modifier.height(18.dp))
        CircularProgressIndicator(color = Pal.Lime, trackColor = Pal.Chip, strokeWidth = 3.dp)
    }
}

@Composable
private fun BottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Pal.Ground)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Tab.entries.forEach { tab ->
            val selected = tab == current
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .background(if (selected) Pal.LimeSoft else Pal.Ground, RoundedCornerShape(12.dp))
                    .clickable { onSelect(tab) }
                    .padding(vertical = 8.dp),
            ) {
                if (tab == Tab.PYTOR) {
                    PytorAvatar(size = 22.dp)
                } else {
                    Text(
                        if (tab == Tab.TRACK) ">_" else "◎",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (selected) Pal.Lime else Pal.Faint,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    tab.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) Pal.Lime else Pal.Faint,
                )
            }
        }
    }
}
