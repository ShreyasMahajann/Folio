package com.shreyas.pdfreader.lite

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.Scroller
import kotlin.math.abs
import kotlin.math.max

/**
 * Shows one page. A drag moves the page. A tap on the left or right quarter of the screen moves
 * one screen up or down, then turns the page. A sideways swipe turns the page.
 */
class PageView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    interface Listener {
        /** @param direction +1 for the next page, -1 for the previous page */
        fun onTurn(direction: Int, atBottom: Boolean)
        fun onCentreTap()
        fun onResize()
    }

    var listener: Listener? = null

    var night = false
        set(value) {
            field = value
            paint.colorFilter = if (value) INVERT else null
            invalidate()
        }

    private var bitmap: Bitmap? = null
    private var offsetX = 0
    private var offsetY = 0
    private val paint = Paint()
    private val scroller = Scroller(context)

    fun show(page: Bitmap, atBottom: Boolean) {
        scroller.forceFinished(true)
        bitmap = page
        offsetX = 0
        offsetY = if (atBottom) maxY() else 0
        invalidate()
    }

    /** Moves one screen down (+1) or up (-1). At the end of the page it asks for the next page. */
    fun step(direction: Int) {
        val page = bitmap ?: return
        scroller.forceFinished(true)
        val y = if (direction > 0) Paging.forward(offsetY, height, page.height) else Paging.back(offsetY, height)
        if (y == Paging.TURN) {
            listener?.onTurn(direction, atBottom = direction < 0)
        } else {
            offsetY = y
            invalidate()
        }
    }

    private fun maxX() = max(0, (bitmap?.width ?: 0) - width)
    private fun maxY() = max(0, (bitmap?.height ?: 0) - height)

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(if (night) Color.BLACK else Color.WHITE)
        val page = bitmap ?: return
        // A page that is shorter than the screen stays in the centre.
        val top = if (page.height < height) (height - page.height) / 2 else -offsetY
        canvas.drawBitmap(page, -offsetX.toFloat(), top.toFloat(), paint)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        listener?.onResize()
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            offsetX = scroller.currX
            offsetY = scroller.currY
            invalidate()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = gestures.onTouchEvent(event)

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            scroller.forceFinished(true)
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            offsetX = (offsetX + distanceX.toInt()).coerceIn(0, maxX())
            offsetY = (offsetY + distanceY.toInt()).coerceIn(0, maxY())
            invalidate()
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            // A zoomed page moves sideways, so there a sideways swipe cannot turn the page.
            if (maxX() == 0 && abs(velocityX) > 2 * abs(velocityY)) {
                listener?.onTurn(if (velocityX < 0) 1 else -1, atBottom = false)
            } else {
                scroller.fling(offsetX, offsetY, -velocityX.toInt(), -velocityY.toInt(), 0, maxX(), 0, maxY())
                invalidate()
            }
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            when {
                e.x < width / 4 -> step(-1)
                e.x > width * 3 / 4 -> step(1)
                else -> listener?.onCentreTap()
            }
            return true
        }
    })

    private companion object {
        val INVERT = ColorMatrixColorFilter(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    }
}
