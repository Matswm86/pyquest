package no.mwmai.pyquest.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import no.mwmai.pyquest.data.CodexSearch
import no.mwmai.pyquest.model.CodexDomain
import no.mwmai.pyquest.model.CodexEntry
import no.mwmai.pyquest.pytor.PytorClient
import no.mwmai.pyquest.ui.theme.Pal

/**
 * Everything Pytor says in chat, kept outside the composition so switching
 * tabs or opening the chat from a question never loses the conversation, and
 * so a reply that arrives after the screen closed still lands.
 */
class PytorChat {
    data class Message(
        val fromPytor: Boolean,
        val text: String,
        val source: String = "",
        val related: List<CodexEntry> = emptyList(),
    )

    val messages = mutableStateListOf<Message>()
    var draft by mutableStateOf("")
    var busy by mutableStateOf(false)
        private set
    var lastBackend by mutableStateOf("")
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun send(question: String, context: String, online: Boolean, codex: List<CodexEntry>) {
        val q = question.trim()
        if (q.isEmpty() || busy) return
        messages += Message(fromPytor = false, text = q)
        draft = ""
        busy = true
        scope.launch {
            val related = CodexSearch.search(codex, q, limit = 2)
            val history = messages.dropLast(1).takeLast(6).map {
                PytorClient.Turn(if (it.fromPytor) "assistant" else "user", it.text)
            }
            val reply = if (online) PytorClient.chat(q, context, history) else null
            val message = when (reply) {
                is PytorClient.PytorReply.Answer -> {
                    lastBackend = reply.backend
                    Message(fromPytor = true, text = reply.text, source = "expert mode", related = related)
                }
                is PytorClient.PytorReply.Failure -> offlineReply(related, reason = reply.reason)
                null -> offlineReply(related, reason = null)
            }
            messages += message
            busy = false
        }
    }

    private fun offlineReply(related: List<CodexEntry>, reason: String?): Message {
        val top = related.firstOrNull()
        val why = when {
            reason == null -> "Offline mode"
            else -> "Could not reach the tutor service ($reason)"
        }
        return if (top != null) {
            Message(
                fromPytor = true,
                text = "$why, so this is from my Codex.\n\n**${top.title}**\n\n${top.body}" +
                    (top.code?.let { "\n\n```\n$it\n```" } ?: ""),
                source = "codex",
                related = related.drop(1),
            )
        } else {
            Message(
                fromPytor = true,
                text = "$why, and I have no Codex note that matches. Try different words, browse the Codex, or ask again with a connection.",
                source = "codex",
            )
        }
    }
}

private enum class PytorTab { CHAT, CODEX }

/**
 * Pytor's own screen: a chat with the expert, and the Codex he answers from
 * when there is no network. Opened from the tab bar, or from a question via
 * the hint sheet with the question already in the draft.
 */
@Composable
fun PytorScreen(
    chat: PytorChat,
    codex: List<CodexEntry>,
    online: Boolean,
    context: String,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableStateOf(PytorTab.CHAT) }
    var selected by remember { mutableStateOf<CodexEntry?>(null) }

    BackHandler(enabled = selected != null) { selected = null }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Pal.Screen)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        PytorHeader(
            online = online,
            backend = chat.lastBackend,
            tab = tab,
            onTab = { tab = it; selected = null },
            onClose = onClose,
        )
        val entry = selected
        when {
            entry != null -> CodexDetail(
                entry = entry,
                codex = codex,
                onOpen = { selected = it },
                onAsk = {
                    chat.draft = "Tell me more about ${entry.title.lowercase()}."
                    selected = null
                    tab = PytorTab.CHAT
                },
                modifier = Modifier.weight(1f),
            )
            tab == PytorTab.CHAT -> ChatPane(
                chat = chat,
                codex = codex,
                online = online,
                context = context,
                onOpenEntry = { selected = it },
                modifier = Modifier.weight(1f),
            )
            else -> CodexPane(codex = codex, onOpen = { selected = it }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun PytorHeader(
    online: Boolean,
    backend: String,
    tab: PytorTab,
    onTab: (PytorTab) -> Unit,
    onClose: (() -> Unit)?,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onClose != null) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(Pal.Chip, CircleShape)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("×", style = MaterialTheme.typography.titleLarge, color = Pal.Faint)
                }
                Spacer(Modifier.width(12.dp))
            }
            PytorAvatar(size = 44.dp)
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Pytor", style = MaterialTheme.typography.titleLarge, color = Pal.Text)
                Text(
                    when {
                        !online -> "Offline: answering from the Codex"
                        backend.isNotBlank() -> "Expert mode · via $backend"
                        else -> "Expert mode · Python, engineering, AI"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (online) Pal.Lime else Pal.Faint,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Chat", onClick = { onTab(PytorTab.CHAT) }, selected = tab == PytorTab.CHAT)
            Chip("Codex", onClick = { onTab(PytorTab.CODEX) }, selected = tab == PytorTab.CODEX)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ChatPane(
    chat: PytorChat,
    codex: List<CodexEntry>,
    online: Boolean,
    context: String,
    onOpenEntry: (CodexEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(chat.messages.size, chat.busy) {
        val last = listState.layoutInfo.totalItemsCount - 1
        if (last > 0) listState.animateScrollToItem(last)
    }

    Column(modifier = modifier.fillMaxWidth().imePadding()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (chat.messages.isEmpty()) {
                item {
                    PytorSays(
                        "Ask me anything: Python syntax down to the bytecode, how to test and ship a service, or how to scope a RAG system for a client. I lead with the answer.",
                    )
                }
                item {
                    FlowRowStarters(
                        starters = listOf(
                            "Why does 0.1 + 0.2 != 0.3?",
                            "When is fine-tuning worth it?",
                            "Explain decorators with an example",
                            "How do I make my RAG stop hallucinating?",
                        ),
                        onPick = { chat.draft = it },
                    )
                }
            }
            items(chat.messages) { message ->
                if (message.fromPytor) {
                    Row(verticalAlignment = Alignment.Top) {
                        PytorAvatar(size = 30.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .background(Pal.Card, RoundedCornerShape(4.dp, 14.dp, 14.dp, 14.dp))
                                .border(1.dp, Pal.Edge, RoundedCornerShape(4.dp, 14.dp, 14.dp, 14.dp))
                                .padding(13.dp),
                        ) {
                            PytorProse(message.text)
                            if (message.source.isNotBlank() || message.related.isNotEmpty()) {
                                Spacer(Modifier.height(10.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    if (message.source.isNotBlank()) {
                                        Text(message.source, style = MaterialTheme.typography.labelSmall, color = Pal.Locked)
                                    }
                                    message.related.forEach { entry ->
                                        Chip(entry.title, onClick = { onOpenEntry(entry) })
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Text(
                            message.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Pal.Screen,
                            modifier = Modifier
                                .padding(start = 48.dp)
                                .background(Pal.Lime, RoundedCornerShape(14.dp, 4.dp, 14.dp, 14.dp))
                                .padding(horizontal = 13.dp, vertical = 10.dp),
                        )
                    }
                }
            }
            if (chat.busy) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PytorAvatar(size = 30.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (online) "Pytor is thinking…" else "Searching the Codex…",
                            style = MaterialTheme.typography.bodySmall,
                            color = Pal.Faint,
                        )
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier
                .fillMaxWidth()
                .background(Pal.Screen)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            OutlinedTextField(
                value = chat.draft,
                onValueChange = { chat.draft = it },
                placeholder = { Text("Ask Pytor…", color = Pal.Locked) },
                maxLines = 4,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Pal.Text,
                    unfocusedTextColor = Pal.Text,
                    focusedBorderColor = Pal.LimeEdge,
                    unfocusedBorderColor = Pal.Edge,
                    cursorColor = Pal.Lime,
                    focusedContainerColor = Pal.Card,
                    unfocusedContainerColor = Pal.Card,
                ),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            val canSend = chat.draft.isNotBlank() && !chat.busy
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(if (canSend) Pal.Lime else Pal.Chip, RoundedCornerShape(14.dp))
                    .clickable(enabled = canSend) { chat.send(chat.draft, context, online, codex) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "↑",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (canSend) Pal.Screen else Pal.Locked,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowStarters(starters: List<String>, onPick: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(start = 46.dp),
    ) {
        starters.forEach { starter ->
            Text(
                starter,
                style = MaterialTheme.typography.bodySmall,
                color = Pal.Muted,
                modifier = Modifier
                    .background(Pal.Card, RoundedCornerShape(10.dp))
                    .border(1.dp, Pal.Edge, RoundedCornerShape(10.dp))
                    .clickable { onPick(starter) }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CodexPane(
    codex: List<CodexEntry>,
    onOpen: (CodexEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var domain by remember { mutableStateOf<String?>(null) }

    val shown = remember(codex, query, domain) {
        val base = if (query.isBlank()) codex.sortedBy { it.title } else CodexSearch.search(codex, query, limit = 25)
        if (domain == null) base else base.filter { it.domain == domain }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search the Codex…", color = Pal.Locked) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Pal.Text,
                unfocusedTextColor = Pal.Text,
                focusedBorderColor = Pal.LimeEdge,
                unfocusedBorderColor = Pal.Edge,
                cursorColor = Pal.Lime,
                focusedContainerColor = Pal.Card,
                unfocusedContainerColor = Pal.Card,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 18.dp),
        ) {
            Chip("All ${codex.size}", onClick = { domain = null }, selected = domain == null)
            CodexDomain.ALL.forEach { d ->
                val count = codex.count { it.domain == d }
                Chip("${CodexDomain.label(d)} $count", onClick = { domain = d }, selected = domain == d)
            }
        }
        Spacer(Modifier.height(6.dp))
        if (shown.isEmpty()) {
            Text(
                "Nothing in the Codex matches. Try one word, or ask in chat.",
                style = MaterialTheme.typography.bodyMedium,
                color = Pal.Faint,
                modifier = Modifier.padding(18.dp),
            )
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(shown, key = { it.id }) { entry ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Pal.Card, RoundedCornerShape(12.dp))
                        .border(1.dp, Pal.Hairline, RoundedCornerShape(12.dp))
                        .clickable { onOpen(entry) }
                        .padding(13.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            entry.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = Pal.Text,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            CodexDomain.label(entry.domain).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Pal.Locked,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(entry.summary, style = MaterialTheme.typography.bodySmall, color = Pal.Muted)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CodexDetail(
    entry: CodexEntry,
    codex: List<CodexEntry>,
    onOpen: (CodexEntry) -> Unit,
    onAsk: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        Text(
            CodexDomain.label(entry.domain).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = Pal.Faint,
        )
        Spacer(Modifier.height(6.dp))
        Text(entry.title, style = MaterialTheme.typography.headlineSmall, color = Pal.Text)
        Spacer(Modifier.height(10.dp))
        Text(inlineCode(entry.summary), style = MaterialTheme.typography.bodyLarge, color = Pal.Lime)
        Spacer(Modifier.height(14.dp))
        entry.body.split(Regex("\n\\s*\n")).forEach { paragraph ->
            Text(inlineCode(paragraph.trim()), style = MaterialTheme.typography.bodyMedium, color = Pal.Text)
            Spacer(Modifier.height(10.dp))
        }
        entry.code?.let { code ->
            Spacer(Modifier.height(4.dp))
            CodeBlock(code)
            Spacer(Modifier.height(14.dp))
        }
        if (entry.tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                entry.tags.forEach { tag ->
                    Text(
                        tag,
                        style = MaterialTheme.typography.labelSmall,
                        color = Pal.Faint,
                        modifier = Modifier
                            .background(Pal.ChipDim, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        val related = entry.related.mapNotNull { id -> codex.firstOrNull { it.id == id } }
        if (related.isNotEmpty()) {
            SectionLabel("READ NEXT")
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                related.forEach { rel -> Chip(rel.title, onClick = { onOpen(rel) }) }
            }
            Spacer(Modifier.height(18.dp))
        }
        SecondaryButton(text = "Ask Pytor about this", onClick = onAsk)
        Spacer(Modifier.height(28.dp))
    }
}
