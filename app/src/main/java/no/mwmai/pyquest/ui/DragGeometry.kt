package no.mwmai.pyquest.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow

/** This composable's rectangle in window coordinates. */
fun LayoutCoordinates.boundsInWindowCompat(): Rect {
    val topLeft = positionInWindow()
    return Rect(
        left = topLeft.x,
        top = topLeft.y,
        right = topLeft.x + size.width,
        bottom = topLeft.y + size.height,
    )
}

/** Records a slot's bounds, growing the backing list when a new slot appears. */
fun MutableList<Rect>.putAt(index: Int, rect: Rect) {
    while (size <= index) add(Rect.Zero)
    this[index] = rect
}

/**
 * Where a dropped block belongs, given the rectangles of the blocks already in
 * the answer row and the finger position.
 *
 * A block counts as coming before the finger when its whole row sits above the
 * finger, or when it shares the finger's row and its centre is to the left. Drop
 * below every block and the count equals the row length, which appends.
 */
fun insertionIndex(slots: List<Rect>, pointer: Offset): Int = slots.count { slot ->
    val rowIsAbove = slot.bottom < pointer.y
    val sameRow = pointer.y >= slot.top && pointer.y <= slot.bottom
    rowIsAbove || (sameRow && slot.center.x < pointer.x)
}
