package com.shreyas.pdfreader.ui.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.shreyas.pdfreader.data.PageTheme
import com.shreyas.pdfreader.pdf.CropCorner
import com.shreyas.pdfreader.pdf.PageCrop
import com.shreyas.pdfreader.pdf.dragCorner
import com.shreyas.pdfreader.pdf.moveBy
import kotlin.math.min
import kotlin.math.roundToInt

private const val CROP_PREVIEW_MAX_PIXELS = 4_000_000

/**
 * Lets the user draw the visible area of a page. Shows the whole page, without the crop in use.
 * Drag a corner to change the box. Drag inside the box to move it.
 */
@Composable
fun CropScreen(
    page: Int,
    initial: PageCrop,
    loadAspect: suspend (page: Int) -> Float?,
    loadPage: PageLoader,
    onApply: (crop: PageCrop, allPages: Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    var crop by remember(page) { mutableStateOf(initial) }
    var askScope by remember { mutableStateOf(false) }
    val aspect by produceState<Float?>(initialValue = null, page) { value = loadAspect(page) }

    BackHandler(onBack = onCancel)

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text("Crop page ${page + 1}", style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Serif)
                    Text(
                        text = "Drag the corners. The PDF file is not changed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val pageAspect = aspect
                if (pageAspect == null) {
                    CircularProgressIndicator()
                } else {
                    CropArea(page, pageAspect, crop, loadPage, onChange = { crop = it })
                }
            }

            Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                    OutlinedButton(onClick = { crop = PageCrop.FULL }, enabled = !crop.isFull) { Text("Reset") }
                    Box(Modifier.weight(1f))
                    Button(onClick = { askScope = true }) { Text("Apply") }
                }
            }
        }
    }

    if (askScope) {
        AlertDialog(
            onDismissRequest = { askScope = false },
            title = { Text(if (crop.isFull) "Remove crop from" else "Apply crop to") },
            text = { Text("\"All pages\" replaces the crop of every page of this book.") },
            confirmButton = {
                TextButton(onClick = { onApply(crop, false) }) { Text("This page") }
            },
            dismissButton = {
                TextButton(onClick = { onApply(crop, true) }) { Text("All pages") }
            },
        )
    }
}

private sealed interface Grip {
    data class Corner(val corner: CropCorner) : Grip
    data object Box : Grip
}

@Composable
private fun CropArea(
    page: Int,
    aspect: Float,
    crop: PageCrop,
    loadPage: PageLoader,
    onChange: (PageCrop) -> Unit,
) {
    val density = LocalDensity.current
    val margin = with(density) { 48.dp.toPx() }
    val gripRadius = with(density) { 36.dp.toPx() }
    val handleRadius = with(density) { 9.dp.toPx() }
    val border = with(density) { 2.dp.toPx() }
    val accent = MaterialTheme.colorScheme.primary

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val areaWidth = constraints.maxWidth.toFloat()
        val areaHeight = constraints.maxHeight.toFloat()
        // The page sits in the middle, with a margin. A corner on the page edge is then easy to grab,
        // and a drag from it does not start at the screen edge, where Android reads it as "back".
        val pageWidth = min(areaWidth - 2 * margin, (areaHeight - 2 * margin) * aspect).coerceAtLeast(1f)
        val pageSize = Size(pageWidth, pageWidth / aspect)
        val origin = Offset((areaWidth - pageSize.width) / 2, (areaHeight - pageSize.height) / 2)
        // The gesture outlives recompositions and must read the newest crop.
        val latest by rememberUpdatedState(crop)

        fun boxOf(value: PageCrop) = Rect(
            left = origin.x + value.left * pageSize.width,
            top = origin.y + value.top * pageSize.height,
            right = origin.x + value.right * pageSize.width,
            bottom = origin.y + value.bottom * pageSize.height,
        )

        PdfPage(
            page = page,
            widthPx = pageSize.width.roundToInt(),
            maxPixels = CROP_PREVIEW_MAX_PIXELS,
            theme = PageTheme.LIGHT,
            loadPage = loadPage,
            onAspectKnown = {},
            modifier = Modifier
                .align(Alignment.Center)
                .size(with(density) { pageSize.width.toDp() }, with(density) { pageSize.height.toDp() }),
        )

        Canvas(
            Modifier
                .fillMaxSize()
                .systemGestureExclusion()
                .pointerInput(pageSize, origin) {
                    var grip: Grip? = null
                    detectDragGestures(
                        onDragStart = { start ->
                            val box = boxOf(latest)
                            val corners = mapOf(
                                CropCorner.TOP_LEFT to box.topLeft,
                                CropCorner.TOP_RIGHT to box.topRight,
                                CropCorner.BOTTOM_LEFT to box.bottomLeft,
                                CropCorner.BOTTOM_RIGHT to box.bottomRight,
                            )
                            val nearest = corners.minBy { (it.value - start).getDistance() }
                            grip = when {
                                (nearest.value - start).getDistance() <= gripRadius -> Grip.Corner(nearest.key)
                                box.contains(start) -> Grip.Box
                                else -> null
                            }
                        },
                        onDrag = { change, drag ->
                            val dx = drag.x / pageSize.width
                            val dy = drag.y / pageSize.height
                            when (val held = grip) {
                                is Grip.Corner -> onChange(latest.dragCorner(held.corner, dx, dy))
                                Grip.Box -> onChange(latest.moveBy(dx, dy))
                                null -> Unit
                            }
                            if (grip != null) change.consume()
                        },
                    )
                },
        ) {
            val box = boxOf(crop)
            val shade = Color.Black.copy(alpha = 0.55f)
            // Shade over the part of the page that the crop removes.
            drawRect(shade, topLeft = origin, size = Size(pageSize.width, box.top - origin.y))
            drawRect(shade, topLeft = Offset(origin.x, box.bottom), size = Size(pageSize.width, origin.y + pageSize.height - box.bottom))
            drawRect(shade, topLeft = Offset(origin.x, box.top), size = Size(box.left - origin.x, box.height))
            drawRect(shade, topLeft = Offset(box.right, box.top), size = Size(origin.x + pageSize.width - box.right, box.height))
            drawRect(accent, topLeft = box.topLeft, size = box.size, style = Stroke(border))
            listOf(box.topLeft, box.topRight, box.bottomLeft, box.bottomRight).forEach { corner ->
                drawCircle(Color.White, handleRadius, corner)
                drawCircle(accent, handleRadius, corner, style = Stroke(border))
            }
        }
    }
}
