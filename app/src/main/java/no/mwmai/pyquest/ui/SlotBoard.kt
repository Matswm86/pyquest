package no.mwmai.pyquest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import no.mwmai.pyquest.model.Block
import no.mwmai.pyquest.model.BlockKind
import no.mwmai.pyquest.ui.theme.CodeStyle
import no.mwmai.pyquest.ui.theme.Pal

/**
 * Shared state for a board of fixed slots that blocks drop into.
 *
 * Both ways of answering run through here. Tapping a tray block drops it in the
 * first free slot and tapping a filled slot empties it, which is the one-handed
 * path. Long-pressing a block picks it up and drops it in whichever slot the
 * finger is over, which is the Scratch-like path. Neither is optional: the tap
 * path is what makes the game usable on a moving bus, and the drag path is what
 * makes placing a block feel like placing a thing.
 *
 * Geometry is kept in window coordinates so hit-testing needs no conversion.
 */
class SlotDragState {
    var draggingId by mutableStateOf<String?>(null)
        private set
    var pointer by mutableStateOf(Offset.Zero)
        private set
    var chipSize by mutableStateOf(IntSize.Zero)
        private set

    var parentOrigin by mutableStateOf(Offset.Zero)

    /** Slot rectangles by index, rewritten on every layout pass. */
    private val bounds = mutableListOf<Rect>()

    fun setSlotBounds(index: Int, rect: Rect) {
        while (bounds.size <= index) bounds.add(Rect.Zero)
        bounds[index] = rect
    }

    fun slotUnder(point: Offset, slotCount: Int): Int? =
        bounds.take(slotCount).indexOfFirst { it.contains(point) }.takeIf { it >= 0 }

    fun isOver(index: Int, point: Offset): Boolean =
        bounds.getOrNull(index)?.contains(point) == true

    fun pickUp(id: String, origin: Offset, size: IntSize, local: Offset) {
        draggingId = id
        chipSize = size
        pointer = origin + local
    }

    fun drag(delta: Offset) {
        pointer += delta
    }

    /** Returns the slot the block was dropped on, or null when it missed. */
    fun release(slotCount: Int): Int? {
        val target = slotUnder(pointer, slotCount)
        draggingId = null
        return target
    }

    val localPointer: Offset
        get() = pointer - parentOrigin
}

@Composable
fun rememberSlotDragState(): SlotDragState = remember { SlotDragState() }

/** Fill, edge and text colours for a block kind. */
fun kindColors(kind: BlockKind): Triple<Color, Color, Color> = when (kind) {
    BlockKind.VAR -> Triple(Pal.VarFill, Pal.VarEdge, Pal.VarText)
    BlockKind.EXPR -> Triple(Pal.ExprFill, Pal.ExprEdge, Pal.ExprText)
    BlockKind.CALL -> Triple(Pal.CallFill, Pal.CallEdge, Pal.CallText)
    BlockKind.STAGE -> Triple(Pal.LimeSoft, Pal.LimeEdge, Pal.Lime)
}

/**
 * A block face. Used for tray blocks, for filled slots and for the copy that
 * follows the finger during a drag, so all three read as the same object.
 */
@Composable
fun BlockFace(
    label: String,
    kind: BlockKind,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
) {
    val (fill, edge, text) = kindColors(kind)
    Box(
        modifier = modifier
            .alpha(if (dimmed) 0.3f else 1f)
            .background(fill, RoundedCornerShape(9.dp))
            .border(1.dp, edge, RoundedCornerShape(9.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text(text = label, style = CodeStyle, color = text)
    }
}

/**
 * A block sitting in the tray. Tap places it; long-press picks it up.
 */
@Composable
fun TrayBlock(
    block: Block,
    plainLabels: Boolean,
    enabled: Boolean,
    state: SlotDragState,
    slotCount: Int,
    onTap: () -> Unit,
    onDroppedOn: (slot: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    BlockFace(
        label = block.label(plainLabels),
        kind = block.kind,
        dimmed = state.draggingId == block.id,
        modifier = modifier
            .onGloballyPositioned { origin = it.positionInWindow() }
            .pointerInput(block.id, enabled) {
                if (!enabled) return@pointerInput
                val size = size
                detectDragGesturesAfterLongPress(
                    onDragStart = { local ->
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        state.pickUp(block.id, origin, size, local)
                    },
                    onDrag = { change, delta ->
                        change.consume()
                        state.drag(delta)
                    },
                    onDragEnd = { state.release(slotCount)?.let(onDroppedOn) },
                    onDragCancel = { state.release(slotCount) },
                )
            }
            .clickable(enabled = enabled, onClick = onTap),
    )
}

/**
 * One gap on the board. Empty it shows a dashed-looking placeholder sized to the
 * hint text; filled it shows the block, and tapping it sends the block back.
 */
@Composable
fun DropSlot(
    index: Int,
    state: SlotDragState,
    filled: Block?,
    plainLabels: Boolean,
    enabled: Boolean,
    hint: String,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    verdict: SlotVerdict = SlotVerdict.NONE,
) {
    val hovered = state.draggingId != null && state.isOver(index, state.pointer)
    val edge = when {
        verdict == SlotVerdict.RIGHT -> Pal.Lime
        verdict == SlotVerdict.WRONG -> Pal.Coral
        hovered -> Pal.Lime
        else -> Pal.Edge
    }
    Box(
        modifier = modifier
            .onGloballyPositioned { state.setSlotBounds(index, it.boundsInWindowCompat()) }
            .defaultMinSize(minWidth = 74.dp, minHeight = 34.dp)
            .background(
                if (filled == null) Pal.Ground else Color.Transparent,
                RoundedCornerShape(9.dp),
            )
            .border(if (filled == null || verdict != SlotVerdict.NONE) 1.5.dp else 0.dp, edge, RoundedCornerShape(9.dp))
            .clickable(enabled = enabled && filled != null, onClick = onClear),
        contentAlignment = Alignment.Center,
    ) {
        if (filled == null) {
            Text(
                text = hint,
                style = CodeStyle,
                color = Pal.Locked,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            )
        } else {
            BlockFace(label = filled.label(plainLabels), kind = filled.kind)
        }
    }
}

enum class SlotVerdict { NONE, RIGHT, WRONG }

/** The block that follows the finger. Draw it last so it sits above the board. */
@Composable
fun DragOverlay(
    state: SlotDragState,
    block: Block?,
    plainLabels: Boolean,
) {
    if (block == null) return
    val local = state.localPointer
    Box(
        modifier = Modifier.offsetPx(
            x = local.x - state.chipSize.width / 2f,
            y = local.y - state.chipSize.height / 2f,
        ),
    ) {
        BlockFace(label = block.label(plainLabels), kind = block.kind)
    }
}
