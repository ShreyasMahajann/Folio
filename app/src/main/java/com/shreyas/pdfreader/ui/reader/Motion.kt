package com.shreyas.pdfreader.ui.reader

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically

/**
 * One motion language for the reader: quick, ease-out, no bounce.
 * Compose multiplies every duration with the animator scale of the system,
 * so "Remove animations" in the accessibility settings needs no code here.
 */
object Motion {
    const val FAST_MS = 140
    const val STANDARD_MS = 220
    val EaseOut = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** For things that appear at the finger: menus and cards. */
    fun <T> fast(): FiniteAnimationSpec<T> = tween(FAST_MS, easing = EaseOut)

    /** For changes of the page and of panels. */
    fun <T> standard(): FiniteAnimationSpec<T> = tween(STANDARD_MS, easing = EaseOut)
}

/** A menu or card settles into place: fade, small rise, slight growth. */
fun popIn(): EnterTransition =
    fadeIn(Motion.fast()) + scaleIn(Motion.fast(), initialScale = 0.96f) + slideInVertically(Motion.fast()) { it / 8 }

fun popOut(): ExitTransition =
    fadeOut(Motion.fast()) + scaleOut(Motion.fast(), targetScale = 0.96f) + slideOutVertically(Motion.fast()) { it / 8 }
