package com.shreyas.pdfreader.ui.reader

import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.shreyas.pdfreader.data.PageTheme
import com.shreyas.pdfreader.data.ReaderSettings

/**
 * Window state that belongs to the reader only: hidden system bars, brightness, keep awake,
 * orientation lock. Everything is restored when the reader leaves the screen.
 */
@Composable
fun ReaderWindowEffects(settings: ReaderSettings, chromeVisible: Boolean) {
    val activity = LocalActivity.current ?: return
    val window = activity.window
    val insets = remember(window) { WindowCompat.getInsetsController(window, window.decorView) }
    val systemDark = isSystemInDarkTheme()

    LaunchedEffect(chromeVisible) {
        insets.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (chromeVisible) {
            insets.show(WindowInsetsCompat.Type.systemBars())
        } else {
            insets.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    LaunchedEffect(settings.theme) {
        val lightBars = settings.theme != PageTheme.DARK
        insets.isAppearanceLightStatusBars = lightBars
        insets.isAppearanceLightNavigationBars = lightBars
    }

    LaunchedEffect(settings.brightness) {
        window.attributes = window.attributes.apply {
            // The window rejects 0 on some devices and turns the screen off.
            screenBrightness = settings.brightness?.coerceIn(0.01f, 1f)
                ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }

    LaunchedEffect(settings.keepAwake) {
        if (settings.keepAwake) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(settings.lockOrientation) {
        activity.requestedOrientation = if (settings.lockOrientation) {
            ActivityInfo.SCREEN_ORIENTATION_LOCKED
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    DisposableEffect(window) {
        onDispose {
            insets.show(WindowInsetsCompat.Type.systemBars())
            insets.isAppearanceLightStatusBars = !systemDark
            insets.isAppearanceLightNavigationBars = !systemDark
            window.attributes = window.attributes.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}
