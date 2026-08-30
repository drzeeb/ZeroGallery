package de.zerogallery.ui.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * Walks up the [ContextWrapper] chain to find the enclosing [Activity], if any.
 *
 * Needed wherever a composable needs the actual `Activity`/`Window` (e.g. to override this
 * window's screen brightness, see [de.zerogallery.ui.detail.VideoPlayer]) rather than just a
 * generic [Context] - [androidx.compose.ui.platform.LocalContext] only guarantees *a* `Context`,
 * which in practice is normally the `Activity` itself but is wrapped by intermediate
 * `ContextWrapper`s in some hosting scenarios (e.g. previews, some test harnesses).
 */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

