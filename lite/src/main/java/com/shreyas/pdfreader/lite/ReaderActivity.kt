package com.shreyas.pdfreader.lite

import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.KeyEvent
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.io.File

class ReaderActivity : Activity(), PageView.Listener {

    private lateinit var renderer: PageRenderer
    private lateinit var pageView: PageView
    private lateinit var bar: View
    private lateinit var label: TextView
    private lateinit var seek: SeekBar
    private lateinit var key: String

    private val progress by lazy { getSharedPreferences(Prefs.PROGRESS, MODE_PRIVATE) }
    private val settings by lazy { getSharedPreferences(Prefs.SETTINGS, MODE_PRIVATE) }

    private var page = 0
    private var pageCount = 0
    private var zoom = ZOOM_STEPS[0]

    // The next page, drawn before the reader asks for it, so a page turn has no wait.
    private var ahead: Pair<Int, Bitmap>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.data ?: return finish()
        key = uri.toString()

        setContentView(R.layout.reader)
        pageView = findViewById(R.id.page)
        bar = findViewById(R.id.bar)
        label = findViewById(R.id.label)
        seek = findViewById(R.id.seek)

        zoom = settings.getFloat(Prefs.ZOOM, zoom)
        pageView.night = settings.getBoolean(Prefs.NIGHT, false)
        pageView.listener = this
        // Android 4 tablets cannot hide the system bar. This makes its buttons dim dots.
        pageView.systemUiVisibility = View.SYSTEM_UI_FLAG_LOW_PROFILE

        findViewById<View>(R.id.zoom_out).setOnClickListener { changeZoom(-1) }
        findViewById<View>(R.id.zoom_in).setOnClickListener { changeZoom(1) }
        findViewById<View>(R.id.night).setOnClickListener {
            pageView.night = !pageView.night
            settings.edit().putBoolean(Prefs.NIGHT, pageView.night).apply()
        }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) = showLabel(value)
            override fun onStartTrackingTouch(bar: SeekBar) = Unit
            override fun onStopTrackingTouch(bar: SeekBar) = showPage(bar.progress)
        })

        renderer = PageRenderer(this) { openFile(uri) }
        renderer.start(
            onReady = { count ->
                pageCount = count
                seek.max = count - 1
                showPage(progress.getInt(Prefs.PAGE + key, 0).coerceIn(0, count - 1))
            },
            onError = {
                Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_LONG).show()
                finish()
            },
        )
    }

    private fun openFile(uri: Uri): ParcelFileDescriptor =
        if (uri.scheme == "file") {
            ParcelFileDescriptor.open(File(uri.path!!), ParcelFileDescriptor.MODE_READ_ONLY)
        } else {
            contentResolver.openFileDescriptor(uri, "r")!!
        }

    private fun pageWidth() = (pageView.width * zoom).toInt()

    private fun showPage(number: Int, atBottom: Boolean = false) {
        // The first call can come before the layout, or the layout before the page count.
        val width = pageWidth()
        if (width == 0 || pageCount == 0) return
        page = number
        seek.progress = number
        showLabel(number)
        progress.edit()
            .putInt(Prefs.PAGE + key, number)
            .putInt(Prefs.COUNT + key, pageCount)
            .putLong(Prefs.TIME + key, System.currentTimeMillis())
            .apply()

        renderer.cancelPending()
        val ready = ahead?.takeIf { it.first == number && it.second.width == width }
        if (ready != null) {
            pageView.show(ready.second, atBottom)
            drawAhead(number + 1, width)
        } else {
            renderer.render(number, width) { bitmap ->
                if (page == number && pageWidth() == width) {
                    pageView.show(bitmap, atBottom)
                    drawAhead(number + 1, width)
                }
            }
        }
    }

    private fun drawAhead(number: Int, width: Int) {
        ahead = null
        if (number < pageCount) renderer.render(number, width) { ahead = number to it }
    }

    private fun showLabel(number: Int) {
        label.text = getString(R.string.page_short, number + 1, pageCount)
    }

    private fun changeZoom(direction: Int) {
        val index = (ZOOM_STEPS.indexOfFirst { it == zoom } + direction).coerceIn(0, ZOOM_STEPS.size - 1)
        if (ZOOM_STEPS[index] == zoom) return
        zoom = ZOOM_STEPS[index]
        settings.edit().putFloat(Prefs.ZOOM, zoom).apply()
        showPage(page)
    }

    override fun onTurn(direction: Int, atBottom: Boolean) {
        val target = page + direction
        if (target in 0 until pageCount) showPage(target, atBottom)
    }

    override fun onCentreTap() {
        bar.visibility = if (bar.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    override fun onResize() = showPage(page)

    // The volume keys turn pages, as on an e-reader with page buttons.
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_DOWN -> true.also { pageView.step(1) }
        KeyEvent.KEYCODE_VOLUME_UP -> true.also { pageView.step(-1) }
        else -> super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP || super.onKeyUp(keyCode, event)

    override fun onDestroy() {
        if (::renderer.isInitialized) renderer.stop()
        super.onDestroy()
    }

    private companion object {
        // The largest step keeps a page bitmap near 7 MB on an 800 pixel wide screen.
        val ZOOM_STEPS = floatArrayOf(1f, 1.25f, 1.5f, 2f)
    }
}
