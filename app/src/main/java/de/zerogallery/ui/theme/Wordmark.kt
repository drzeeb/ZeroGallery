package de.zerogallery.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import de.zerogallery.R

/**
 * The app's name, styled as a small wordmark rather than plain body text - [ZeroGalleryWordmarkFontFamily]
 * (a distinctive geometric display face, see its own doc) plus a subtle two-stop gradient between
 * this theme's `primary` and `tertiary` colors (so it automatically matches whatever palette is
 * active, including Material You dynamic color on Android 12+) give the top bar's title a bit of
 * visual identity instead of looking like every other piece of body text on screen.
 *
 * Used for the literal "ZeroGallery" title only (see [de.zerogallery.ui.gallery.GalleryScreen]'s
 * top bar) - never for arbitrary user content like folder names, which stay in the regular type
 * scale (see [ZeroGalleryWordmarkFontFamily]'s doc for why).
 */
@Composable
fun ZeroGalleryWordmark(modifier: Modifier = Modifier) {
    val gradient = Brush.linearGradient(
        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary),
    )

    Text(
        text = stringResource(R.string.app_name),
        modifier = modifier,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.titleLarge.copy(
            fontFamily = ZeroGalleryWordmarkFontFamily,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            brush = gradient,
        ),
    )
}


