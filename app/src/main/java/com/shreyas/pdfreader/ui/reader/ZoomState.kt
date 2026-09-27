package com.shreyas.pdfreader.ui.reader

import androidx.compose.animation.core.animate
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.abs

const val MIN_ZOOM = 1f
const val MAX_ZOOM = 4f
private const val DOUBLE_TAP_ZOOM = 2.5f

data class Zoom(val scale: Float = 1f, val offset: Offset = Offset.Zero)

/**
 * Zoom maths for a viewport-sized layer that scales around its centre:
 * `screen = centre + scale * (local - centre) + offset`.
 *
 * Returns the zoom after a gesture step. The content under [centroid] stays under it,
 * and the layer never shows space outside its own bounds.
 */
fun zoomAround(
    current: Zoom,
    centroid: Offset,
    pan: Offset,
    zoomChange: Float,
    viewport: Size,
    panVertically: Boolean,
): Zoom {
    val scale = (current.scale * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
    val factor = scale / current.scale
    val fromCentre = centroid - Offset(viewport.width / 2, viewport.height / 2)
    val offset = fromCentre - (fromCentre - current.offset) * factor + pan
    return Zoom(scale, clampOffset(offset, scale, viewport, panVertically))
}

fun clampOffset(offset: Offset, scale: Float, viewport: Size, panVertically: Boolean): Offset {
    val maxX = viewport.width * (scale - 1) / 2
    val maxY = if (panVertically) viewport.height * (scale - 1) / 2 else 0f
    return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
}

/**
 * Scroll mode has no vertical offset. The list scrolls instead.
 * Returns how far the list must scroll, in list pixels, to keep the content under [centroidY] in place.
 */
fun listScrollForZoom(centroidY: Float, oldScale: Float, newScale: Float): Float =
    centroidY * (1 / oldScale - 1 / newScale)

@Stable
class ZoomState {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set

    var viewport = Size.Zero

    /** Paged mode pans both ways. Scroll mode leaves vertical movement to the list. */
    var panVertically = true

    /** Called with the list scroll that keeps the pinch centre in place. Scroll mode only. */
    var onListScroll: (Float) -> Unit = {}

    val isZoomed get() = scale > MIN_ZOOM

    fun transform(centroid: Offset, pan: Offset, zoomChange: Float) {
        val old = scale
        val next = zoomAround(Zoom(scale, offset), centroid, pan, zoomChange, viewport, panVertically)
        scale = next.scale
        offset = next.offset
        if (!panVertically && next.scale != old) onListScroll(listScrollForZoom(centroid.y, old, next.scale))
    }

    /** True when a drag of [delta] moves content that is still off screen. */
    fun canPan(delta: Offset): Boolean {
        if (!isZoomed) return false
        val max = clampOffset(Offset(Float.MAX_VALUE, Float.MAX_VALUE), scale, viewport, panVertically)
        return if (abs(delta.x) >= abs(delta.y)) {
            if (delta.x > 0) offset.x < max.x - 0.5f else offset.x > -max.x + 0.5f
        } else {
            if (delta.y > 0) offset.y < max.y - 0.5f else offset.y > -max.y + 0.5f
        }
    }

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }

    suspend fun toggle(centroid: Offset) {
        val target = if (isZoomed) MIN_ZOOM else DOUBLE_TAP_ZOOM
        var previous = scale
        animate(initialValue = scale, targetValue = target) { value, _ ->
            transform(centroid, Offset.Zero, value / previous)
            previous = value
        }
    }
}
