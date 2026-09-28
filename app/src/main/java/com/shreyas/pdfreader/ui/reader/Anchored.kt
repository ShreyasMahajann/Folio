package com.shreyas.pdfreader.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.node.Ref
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * Where [content] goes to sit next to [anchor] and inside [bounds].
 * Above the anchor when there is room, else below. Without room on both sides
 * it takes the side with more room and covers a part of the anchor.
 */
fun placeAnchored(anchor: IntRect, content: IntSize, bounds: IntRect, gap: Int): IntOffset {
    val x = (anchor.center.x - content.width / 2)
        .coerceIn(bounds.left, max(bounds.left, bounds.right - content.width))
    val above = anchor.top - gap - content.height
    val below = anchor.bottom + gap
    val lowest = max(bounds.top, bounds.bottom - content.height)
    val y = when {
        above >= bounds.top && above <= lowest -> above
        below >= bounds.top && below <= lowest -> below
        // Also for an anchor that a scroll moved off the screen: the content waits at that edge.
        anchor.top - bounds.top >= bounds.bottom - anchor.bottom -> above.coerceIn(bounds.top, lowest)
        else -> below.coerceIn(bounds.top, lowest)
    }
    return IntOffset(x, y)
}

/**
 * Shows [content] next to [anchor] while [value] and [anchor] are not null. For a parent that fills the screen.
 * An overlay: the page below is not measured again. During the exit the last value stays on the screen.
 */
@Composable
fun <T : Any> AnchoredPopIn(
    value: T?,
    /** In the coordinates of the parent. */
    anchor: IntRect?,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    val lastValue = remember { Ref<T>() }
    // A state: the layout below reads it and places the content again when the anchor moves.
    val lastAnchor = remember { mutableStateOf(IntRect.Zero) }
    if (value != null && anchor != null) {
        lastValue.value = value
        lastAnchor.value = anchor
    }
    val insets = WindowInsets.safeDrawing

    AnimatedVisibility(
        visible = value != null && anchor != null,
        enter = popIn(),
        exit = popOut(),
        modifier = modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
            val density = this
            layout(constraints.maxWidth, constraints.maxHeight) {
                val edge = 8.dp.roundToPx()
                // Status bar, navigation bar, display cutout and keyboard, in every orientation.
                val bounds = IntRect(
                    left = insets.getLeft(density, layoutDirection) + edge,
                    top = insets.getTop(density) + edge,
                    right = constraints.maxWidth - insets.getRight(density, layoutDirection) - edge,
                    bottom = constraints.maxHeight - insets.getBottom(density) - edge,
                )
                val at = placeAnchored(
                    anchor = lastAnchor.value,
                    content = IntSize(placeable.width, placeable.height),
                    bounds = bounds,
                    gap = edge,
                )
                placeable.place(at)
            }
        },
    ) {
        lastValue.value?.let { content(it) }
    }
}
