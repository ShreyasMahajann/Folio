package com.shreyas.pdfreader.ui.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged
import kotlin.math.abs

/**
 * Pinch zoom and pan for the reader. Must sit outside the pager or list.
 *
 * Runs in the initial pass, so it sees events before the pager or list does:
 * - Two fingers: zoom. Events are consumed, so the pager or list does not move.
 * - One finger, paged mode, zoomed: pan while content is off screen in that direction.
 *   At the edge the drag is left alone and the pager turns the page.
 * - One finger, scroll mode, zoomed: horizontal pan. A mostly vertical drag is not consumed,
 *   so the list scrolls in the same drag. A mostly horizontal drag is consumed,
 *   so it does not count as a tap.
 */
suspend fun PointerInputScope.detectReaderZoom(zoom: ZoomState, paged: Boolean) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var dragged = Offset.Zero
        var panning: Boolean? = null // null until the drag passes touch slop

        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val fingers = event.changes.count { it.pressed }
            val pan = event.calculatePan()

            if (fingers >= 2) {
                val centroid = event.calculateCentroid(useCurrent = false)
                if (centroid.isSpecified) zoom.transform(centroid, pan, event.calculateZoom())
                event.changes.forEach { it.consume() }
                panning = true
            } else if (fingers == 1 && zoom.isZoomed) {
                if (panning == null) {
                    dragged += pan
                    if (dragged.getDistance() > viewConfiguration.touchSlop) {
                        panning = if (paged) zoom.canPan(dragged) else abs(dragged.x) > abs(dragged.y)
                    }
                }
                if (!paged) zoom.transform(Offset.Zero, Offset(pan.x, 0f), 1f)
                if (panning == true) {
                    if (paged) zoom.transform(Offset.Zero, pan, 1f)
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            }
        } while (event.changes.any { it.pressed })
        zoom.settle()
    }
}
