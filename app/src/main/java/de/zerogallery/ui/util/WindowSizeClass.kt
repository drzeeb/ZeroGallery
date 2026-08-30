package de.zerogallery.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Window width size classes following Material Design 3's breakpoints
 * (https://m3.material.io/foundations/layout/applying-layout/window-size-classes):
 *
 * - [COMPACT]: < 600dp - most phones in portrait
 * - [MEDIUM]: 600-839dp - phones in landscape, small/foldable tablets
 * - [EXPANDED]: >= 840dp - tablets and desktops
 */
enum class WindowWidthSizeClass {
    COMPACT,
    MEDIUM,
    EXPANDED,
}

/** Pure, easily unit-testable classification of a width in dp into a [WindowWidthSizeClass]. */
fun windowWidthSizeClassOf(widthDp: Int): WindowWidthSizeClass = when {
    widthDp < 600 -> WindowWidthSizeClass.COMPACT
    widthDp < 840 -> WindowWidthSizeClass.MEDIUM
    else -> WindowWidthSizeClass.EXPANDED
}

/** Reads the current window width from [LocalConfiguration] and classifies it. */
@Composable
fun rememberWindowWidthSizeClass(): WindowWidthSizeClass {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return windowWidthSizeClassOf(widthDp)
}

