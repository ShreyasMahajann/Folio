package com.shreyas.pdfreader.lite

import org.junit.Assert.assertEquals
import org.junit.Test

class PagingTest {

    @Test
    fun forward_movesOneScreenThenStopsAtTheEndThenTurns() {
        assertEquals(432, Paging.forward(scrollY = 0, viewHeight = 480, pageHeight = 1100))
        assertEquals(620, Paging.forward(scrollY = 432, viewHeight = 480, pageHeight = 1100))
        assertEquals(Paging.TURN, Paging.forward(scrollY = 620, viewHeight = 480, pageHeight = 1100))
    }

    @Test
    fun forward_turnsAtOnceWhenThePageFitsTheScreen() {
        assertEquals(Paging.TURN, Paging.forward(scrollY = 0, viewHeight = 480, pageHeight = 300))
    }

    @Test
    fun back_movesOneScreenThenStopsAtTheStartThenTurns() {
        assertEquals(188, Paging.back(scrollY = 620, viewHeight = 480))
        assertEquals(0, Paging.back(scrollY = 188, viewHeight = 480))
        assertEquals(Paging.TURN, Paging.back(scrollY = 0, viewHeight = 480))
    }
}
