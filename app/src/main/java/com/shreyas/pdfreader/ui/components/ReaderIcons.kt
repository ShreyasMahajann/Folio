package com.shreyas.pdfreader.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Original icons. The core Material icon set has no bookmark, highlighter or note. */
object ReaderIcons {

    val Bookmark: ImageVector by lazy { ribbon("Bookmark", filled = true) }
    val BookmarkOutline: ImageVector by lazy { ribbon("BookmarkOutline", filled = false) }

    /** A marker pen over a line. */
    val Highlighter: ImageVector by lazy {
        ImageVector.Builder("Highlighter", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineJoin = StrokeJoin.Round) {
                moveTo(15f, 3.5f)
                lineTo(20.5f, 9f)
                lineTo(12f, 17.5f)
                lineTo(6.5f, 17.5f)
                lineTo(6.5f, 12f)
                close()
            }
            .path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round) {
                moveTo(4f, 21f)
                horizontalLineToRelative(16f)
            }
            .build()
    }

    /** A sheet of paper with a folded corner and two lines of text. */
    val Note: ImageVector by lazy {
        ImageVector.Builder("Note", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineJoin = StrokeJoin.Round) {
                moveTo(5f, 4f)
                horizontalLineToRelative(14f)
                verticalLineToRelative(10f)
                lineToRelative(-6f, 6f)
                horizontalLineToRelative(-8f)
                close()
                moveTo(19f, 14f)
                horizontalLineToRelative(-6f)
                verticalLineToRelative(6f)
            }
            .path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round) {
                moveTo(8.5f, 8.5f)
                horizontalLineToRelative(7f)
                moveTo(8.5f, 12f)
                horizontalLineToRelative(4f)
            }
            .build()
    }

    private fun ribbon(name: String, filled: Boolean): ImageVector =
        ImageVector.Builder(name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .path(
                fill = if (filled) SolidColor(Color.Black) else null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(7f, 4f)
                horizontalLineToRelative(10f)
                verticalLineToRelative(16f)
                lineToRelative(-5f, -3.5f)
                lineToRelative(-5f, 3.5f)
                close()
            }
            .build()
}
