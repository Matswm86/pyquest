package no.mwmai.pyquest.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import no.mwmai.pyquest.R
import no.mwmai.pyquest.ui.theme.CodeStyle
import no.mwmai.pyquest.ui.theme.JetBrainsMono
import no.mwmai.pyquest.ui.theme.Pal

/**
 * The pieces that make Pytor a presence rather than a logo: the avatar, the
 * speech bubble, and the small shared controls every screen uses so the app
 * reads as one thing.
 */
@Composable
fun PytorAvatar(size: Dp = 40.dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.pytor),
        contentDescription = stringResource(R.string.pytor_avatar),
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .border(1.dp, Pal.LimeEdge, CircleShape),
    )
}

/** Pytor's line in a bubble, avatar on the left. Tappable when [onClick] is set. */
@Composable
fun PytorSays(
    text: String,
    modifier: Modifier = Modifier,
    avatarSize: Dp = 36.dp,
    onClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = modifier.fillMaxWidth(),
    ) {
        PytorAvatar(size = avatarSize)
        Spacer(Modifier.width(10.dp))
        val bubble = Modifier
            .weight(1f)
            .background(Pal.Card, RoundedCornerShape(4.dp, 14.dp, 14.dp, 14.dp))
            .border(1.dp, Pal.Edge, RoundedCornerShape(4.dp, 14.dp, 14.dp, 14.dp))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 13.dp, vertical = 10.dp)
        Box(modifier = bubble) {
            Text(text = inlineCode(text), style = MaterialTheme.typography.bodyMedium, color = Pal.Text)
        }
    }
}

/** Small uppercase mono caption used for section headers. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = Pal.Faint, modifier = modifier)
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(13.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Pal.Lime,
            contentColor = Pal.Screen,
            disabledContainerColor = Pal.Chip,
            disabledContentColor = Pal.Locked,
        ),
        modifier = modifier.fillMaxWidth().height(52.dp),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(13.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Pal.Chip,
            contentColor = Pal.Text,
            disabledContainerColor = Pal.ChipDim,
            disabledContentColor = Pal.Locked,
        ),
        modifier = modifier.fillMaxWidth().height(48.dp),
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

/** A tappable pill. */
@Composable
fun Chip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = if (selected) Pal.Screen else Pal.Lime,
        modifier = modifier
            .background(if (selected) Pal.Lime else Pal.LimeSoft, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/** A monospace code panel that scrolls sideways rather than wrapping. */
@Composable
fun CodeBlock(code: String, modifier: Modifier = Modifier, color: Color = Pal.Text) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Pal.Ground, RoundedCornerShape(12.dp))
            .border(1.dp, Pal.Edge, RoundedCornerShape(12.dp))
            .horizontalScroll(rememberScrollState())
            .padding(14.dp),
    ) {
        Text(code, style = CodeStyle, color = color, softWrap = false)
    }
}

/**
 * Renders a reply the way Pytor writes it: paragraphs, with ``` fences as code
 * panels and `inline code` in mono. No markdown library; the two shapes Pytor
 * actually produces are enough.
 */
@Composable
fun PytorProse(text: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val parts = text.split("```")
        parts.forEachIndexed { index, part ->
            if (index % 2 == 1) {
                // Drop the language tag on the fence's first line.
                val code = part.trimStart().let { body ->
                    val firstBreak = body.indexOf('\n')
                    if (firstBreak in 0..20 && body.substring(0, firstBreak).none { it.isWhitespace() }) {
                        body.substring(firstBreak + 1)
                    } else {
                        body
                    }
                }.trimEnd()
                if (code.isNotBlank()) CodeBlock(code)
            } else {
                part.trim().split(Regex("\n\\s*\n")).filter { it.isNotBlank() }.forEach { paragraph ->
                    Text(
                        text = inlineCode(paragraph.replace("**", "")),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Pal.Text,
                    )
                }
            }
        }
    }
}

private val INLINE_CODE = Regex("`([^`\n]+)`")

/** Styles `code` spans in mono without a markdown dependency. */
fun inlineCode(text: String): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    for (match in INLINE_CODE.findAll(text)) {
        append(text.substring(cursor, match.range.first))
        pushStyle(SpanStyle(fontFamily = JetBrainsMono, background = Pal.Chip, color = Pal.Lime))
        append(match.groupValues[1])
        pop()
        cursor = match.range.last + 1
    }
    append(text.substring(cursor))
}
