package com.shreyas.pdfreader.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Original icons. The core Material icon set has no bookmark. */
object ReaderIcons {

    val Bookmark: ImageVector by lazy { ribbon("Bookmark", filled = true) }
    val BookmarkOutline: ImageVector by lazy { ribbon("BookmarkOutline", filled = false) }

    /** Circle with one dark half. Stands for the page theme. */
    val Theme: ImageVector by lazy {
        ImageVector.Builder("Theme", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f) {
                moveTo(12f, 3.5f)
                arcToRelative(8.5f, 8.5f, 0f, true, true, 0f, 17f)
                arcToRelative(8.5f, 8.5f, 0f, true, true, 0f, -17f)
                close()
            }
            .path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 3.5f)
                arcToRelative(8.5f, 8.5f, 0f, false, true, 0f, 17f)
                close()
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
