package de.zerogallery.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import de.zerogallery.R

// Default Material 3 type scale. Customize further once real typography needs emerge.
val Typography = Typography(
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
)

/**
 * Space Grotesk (SIL Open Font License 1.1, bundled at `res/font/space_grotesk.ttf` - see
 * `NOTICE`), used exclusively for the "ZeroGallery" wordmark itself
 * ([de.zerogallery.ui.theme.ZeroGalleryWordmark]) - a distinctive, geometric display face that
 * gives the app a bit of visual identity, deliberately *not* applied to the regular type scale
 * above. It's a variable font (a single file covering a whole weight range rather than one file
 * per weight, supported natively since Android 8.0/API 26, matching this project's `minSdk`), so
 * the specific weight has to be requested both via [FontVariation.Settings] *and* the plain
 * [FontWeight] (the latter is what non-variable-font-aware code, e.g. `fontWeight` overrides,
 * still keys off).
 *
 * Deliberately not reused for arbitrary user content like folder names: Space Grotesk's glyph
 * coverage is Latin/Latin Extended/Cyrillic only, so a folder named in e.g. Arabic or Japanese
 * would silently fall back to a system font mid-name (or render as tofu) - the regular type scale
 * remains the only thing used for anything that isn't this app's own, Latin-script, name.
 */
val ZeroGalleryWordmarkFontFamily = FontFamily(
    Font(
        R.font.space_grotesk,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
)

