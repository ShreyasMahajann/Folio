package com.shreyas.pdfreader.pdf

import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import java.io.File
import java.io.InputStream

/**
 * The part of [box] that [crop] keeps, in PDF coordinates (origin at the bottom left).
 *
 * [crop] is measured on the page as the reader shows it: after [rotation] (clockwise degrees), from the top left.
 */
fun cropBox(box: PDRectangle, rotation: Int, crop: PageCrop): PDRectangle {
    // Fractions of the page before rotation: left and right from the left side, top and bottom from the top side.
    val (left, top, right, bottom) = when (((rotation % 360) + 360) % 360) {
        90 -> PageCrop(crop.top, 1f - crop.right, crop.bottom, 1f - crop.left)
        180 -> PageCrop(1f - crop.right, 1f - crop.bottom, 1f - crop.left, 1f - crop.top)
        270 -> PageCrop(1f - crop.bottom, crop.left, 1f - crop.top, crop.right)
        else -> crop
    }
    return PDRectangle(
        box.lowerLeftX + left * box.width,
        box.upperRightY - bottom * box.height,
        (right - left) * box.width,
        (bottom - top) * box.height,
    )
}

/**
 * Writes the PDF from [input] to [out] the way the reader shows it: without deleted pages, with crops.
 * A crop hides the rest of the page. The hidden part stays in the file.
 */
fun exportEdited(input: InputStream, out: File, edits: PageEdits) {
    PDDocument.load(input, MemoryUsageSetting.setupTempFileOnly()).use { document ->
        val pageCount = document.numberOfPages
        for (index in 0 until pageCount) {
            val crop = edits.cropFor(index) ?: continue
            val page = document.getPage(index)
            val box = cropBox(page.cropBox, page.rotation, crop)
            page.mediaBox = box
            page.cropBox = box
        }
        val visible = edits.visiblePages(pageCount).toSet()
        for (index in pageCount - 1 downTo 0) {
            if (index !in visible) document.removePage(index)
        }
        document.isAllSecurityToBeRemoved = true
        document.save(out)
    }
}
