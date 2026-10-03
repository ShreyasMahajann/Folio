package com.shreyas.pdfreader

import com.shreyas.pdfreader.pdf.PageCrop
import com.shreyas.pdfreader.pdf.cropBox
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import org.junit.Assert.assertEquals
import org.junit.Test

class PdfExportTest {

    // Lower left corner (10, 20), 100 wide, 200 high.
    private val box = PDRectangle(10f, 20f, 100f, 200f)
    private val crop = PageCrop(left = 0.1f, top = 0.2f, right = 0.6f, bottom = 0.7f)

    private fun assertBox(left: Float, bottom: Float, right: Float, top: Float, actual: PDRectangle) {
        assertEquals(left, actual.lowerLeftX, 0.01f)
        assertEquals(bottom, actual.lowerLeftY, 0.01f)
        assertEquals(right, actual.upperRightX, 0.01f)
        assertEquals(top, actual.upperRightY, 0.01f)
    }

    @Test
    fun cropBox_measuresFromTheTopOfThePage() {
        assertBox(20f, 80f, 70f, 180f, cropBox(box, 0, crop))
    }

    @Test
    fun cropBox_followsThePageRotation() {
        assertBox(30f, 40f, 80f, 140f, cropBox(box, 90, crop))
        assertBox(50f, 60f, 100f, 160f, cropBox(box, 180, crop))
        assertBox(40f, 100f, 90f, 200f, cropBox(box, 270, crop))
    }

    @Test
    fun cropBox_fullCropKeepsTheBox() {
        assertBox(10f, 20f, 110f, 220f, cropBox(box, 90, PageCrop.FULL))
    }
}
