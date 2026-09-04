package no.mwmai.pyquest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Scratch-style block assembly.
 *
 * Two ways to answer, both required. Tapping a tray block appends it to the
 * answer and tapping a placed block sends it home, which keeps the whole game
 * playable one-handed on a moving bus. Long-pressing a block picks it up and
 * drops it at whatever position your finger is over, which is the gesture that
 * makes this feel like Scratch rather than a list widget.
 *
 * [placed] holds indices into [choices], so two identical blocks (a pair of
 * closing parens, say) stay distinguishable.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BlocksAnswer(
    choices: List<String>,
    placed: List<Int>,
    onPlacedChange: (List<Int>) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val remaining = remember(choices, placed) { choices.indices.filterNot { it in placed } }

    // Every geometry value below lives in window coordinates so hit-testing needs
    // no conversion; only the floating chip converts back to parent space.
    var parentOrigin by remember { mutableStateOf(Offset.Zero) }
    var answerArea by remember { mutableStateOf(Rect.Zero) }
    val slotBounds = remember { mutableListOf<Rect>() }

    var pointer by remember { mutableStateOf(Offset.Zero) }
    var dragChoice by remember { mutableStateOf(-1) }
    var dragFromPlaced by remember { mutableStateOf(-1) }
    var dragSize by remember { mutableStateOf(IntSize.Zero) }

    fun resetDrag() {
        dragChoice = -1
        dragFromPlaced = -1
    }

    fun finishDrag() {
        val choice = dragChoice
        if (choice < 0) return
        val without = placed.filterIndexed { i, _ -> i != dragFromPlaced }
        if (answerArea.contains(pointer)) {
            val raw = insertionIndex(slotBounds.take(placed.size), pointer)
            val target = if (dragFromPlaced in 0 until raw) raw - 1 else raw
            val next = without.toMutableList()
            next.add(target.coerceIn(0, next.size), choice)
            onPlacedChange(next)
        } else if (dragFromPlaced >= 0) {
            onPlacedChange(without)
        }
        resetDrag()
    }

    Box(
        modifier = modifier.onGloballyPositioned { parentOrigin = it.positionInWindow() },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // The answer row. Blocks land here, in order, left to right.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 96.dp)
                    .border(
                        width = 2.dp,
                        color = if (answerArea.contains(pointer) && dragChoice >= 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = RoundedCornerShape(14.dp),
                    )
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        RoundedCornerShape(14.dp),
                    )
                    .padding(10.dp)
                    .onGloballyPositioned { answerArea = it.boundsInWindowCompat() },
            ) {
                if (placed.isEmpty()) {
                    Text(
                        text = "Drop blocks here, left to right",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        placed.forEachIndexed { slot, choice ->
                            BlockChip(
                                label = choices[choice],
                                enabled = enabled,
                                placed = true,
                                dimmed = dragFromPlaced == slot,
                                onBounds = { slotBounds.putAt(slot, it) },
                                onTap = {
                                    onPlacedChange(placed.filterIndexed { i, _ -> i != slot })
                                },
                                onPickUp = { origin, size, local ->
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    dragChoice = choice
                                    dragFromPlaced = slot
                                    dragSize = size
                                    pointer = origin + local
                                },
                                onDrag = { delta -> pointer += delta },
                                onRelease = { finishDrag() },
                            )
                        }
                    }
                }
            }

            Text(
                text = "Blocks",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                remaining.forEach { choice ->
                    BlockChip(
                        label = choices[choice],
                        enabled = enabled,
                        placed = false,
                        dimmed = dragChoice == choice && dragFromPlaced < 0,
                        onTap = { onPlacedChange(placed + choice) },
                        onPickUp = { origin, size, local ->
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            dragChoice = choice
                            dragFromPlaced = -1
                            dragSize = size
                            pointer = origin + local
                        },
                        onDrag = { delta -> pointer += delta },
                        onRelease = { finishDrag() },
                    )
                }
            }
        }

        // The block under the finger, drawn above everything else.
        if (dragChoice >= 0) {
            val local = pointer - parentOrigin
            Box(
                modifier = Modifier.offset {
                    IntOffset(
                        (local.x - dragSize.width / 2f).roundToInt(),
                        (local.y - dragSize.height / 2f).roundToInt(),
                    )
                },
            ) {
                ChipSurface(
                    label = choices[dragChoice],
                    placed = true,
                    modifier = Modifier.alpha(0.95f),
                )
            }
        }
    }
}

@Composable
private fun BlockChip(
    label: String,
    enabled: Boolean,
    placed: Boolean,
    dimmed: Boolean,
    onTap: () -> Unit,
    onPickUp: (origin: Offset, size: IntSize, local: Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
    onBounds: (Rect) -> Unit = {},
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    ChipSurface(
        label = label,
        placed = placed,
        modifier = modifier
            .alpha(if (dimmed) 0.25f else 1f)
            .onGloballyPositioned { coords ->
                origin = coords.positionInWindow()
                onBounds(coords.boundsInWindowCompat())
            }
            .pointerInput(label, enabled, placed) {
                if (!enabled) return@pointerInput
                val chipSize = size
                detectDragGesturesAfterLongPress(
                    onDragStart = { local -> onPickUp(origin, chipSize, local) },
                    onDrag = { change, delta ->
                        change.consume()
                        onDrag(delta)
                    },
                    onDragEnd = { onRelease() },
                    onDragCancel = { onRelease() },
                )
            }
            .clickable(enabled = enabled, onClick = onTap),
    )
}

@Composable
private fun ChipSurface(
    label: String,
    placed: Boolean,
    modifier: Modifier = Modifier,
) {
    val background =
        if (placed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val foreground =
        if (placed) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .background(background, RoundedCornerShape(10.dp))
            .border(1.dp, Color.Black.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            color = foreground,
        )
    }
}
