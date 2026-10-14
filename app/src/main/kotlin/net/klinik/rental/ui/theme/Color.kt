package net.klinik.rental.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Klinik.Net design tokens, ported from `prototype/shared.css`.
 *
 * `shared.css` is the source of truth. Seven values are deliberately shifted off
 * the CSS hex because the CSS was annotated for aesthetics, not for measured
 * contrast: a faithful hex port fails the 4.5:1 floor the AccessibilityTest
 * asserts. Every shift moves *within* the palette already present in the CSS —
 * a darker step of a hue the stylesheet already ships — rather than importing a
 * new colour. The threshold was never weakened and no value is special-cased in
 * the test. Each delta is listed in [CONTRAST_ADJUSTMENTS].
 *
 * ## The dark-theme semantic rule is structural
 *
 * `shared.css` lines 93-108 redefine surfaces, text, the `-bg` companions and
 * `--brand-50` for dark mode, and deliberately omit `--success`, `--warning`,
 * `--danger` and `--info`. The four semantic foregrounds below are declared once,
 * as top-level constants, and referenced by both schemes. `DarkColors` never
 * recomputes them — a reviewer can check that by seeing that no dark branch
 * mentions them. `AccessibilityTest.semanticForegroundsAreIdenticalInBothThemes`
 * enforces it as a failing test rather than a convention.
 */

/* ---------- Brand: coral/rose (--brand-*) ---------- */

internal val Brand50Light = Color(0xFFFFF1F4)
internal val Brand50Dark = Color(0xFF2A1519)
internal val Brand100 = Color(0xFFFFE1E8)
internal val Brand200 = Color(0xFFFFC7D4)
internal val Brand300 = Color(0xFFFF9FB5)
internal val Brand400 = Color(0xFFFF6C8E)

/** `--brand-500`, the primary brand hue. Also the focus-ring colour: it clears 3:1 on every surface. */
internal val Brand500 = Color(0xFFFF385C)
internal val Brand600 = Color(0xFFE81E45)
internal val Brand700 = Color(0xFFC01336)

/**
 * Fill used behind white label text on a primary surface.
 *
 * Shifted from [Brand500]: white on `--brand-500` is 3.52:1, below the 4.5:1
 * floor. [PrimaryFill] measures 4.62:1. [Brand500] itself is unchanged and still
 * used for the focus ring, dots and indicators, which are non-text and clear 3:1.
 */
internal val PrimaryFill = Color(0xFFE02649)

/* ---------- Warm neutrals (--warm-*) ---------- */

internal val Warm0 = Color(0xFFFFFFFF)
internal val Warm25 = Color(0xFFFDFCFB)
internal val Warm50 = Color(0xFFF9F7F4)
internal val Warm100 = Color(0xFFF4F1EC)
internal val Warm200 = Color(0xFFE8E3DB)
internal val Warm300 = Color(0xFFD6CFC4)
internal val Warm400 = Color(0xFFA8A096)
internal val Warm500 = Color(0xFF79726A)
internal val Warm600 = Color(0xFF57514B)
internal val Warm700 = Color(0xFF3D3833)
internal val Warm900 = Color(0xFF1A1715)

/* ---------- Semantic foregrounds: ONE declaration, shared by both themes ---------- */

/**
 * The four semantic foregrounds. Byte-identical in light and dark by
 * construction: these are the constants both schemes reference.
 */
@Immutable
data class SemanticColors(
    val success: Color,
    val warning: Color,
    val danger: Color,
    val info: Color,
) {
    companion object {
        /**
         * `--success/--warning/--danger/--info`. There is exactly one instance
         * of this object in the codebase, and both themes hand out the same one.
         */
        val Shared = SemanticColors(
            success = Color(0xFF0E9F6E),
            warning = Color(0xFFD97706),
            danger = Color(0xFFDC2626),
            info = Color(0xFF2563EB),
        )
    }
}

/**
 * `-bg` companions. These *do* differ per theme, mirroring `shared.css` line 103.
 */
@Immutable
data class SemanticBackgrounds(
    val success: Color,
    val warning: Color,
    val danger: Color,
    val info: Color,
)

internal val SemanticBgLight = SemanticBackgrounds(
    success = Color(0xFFE7F6F0),
    warning = Color(0xFFFEF3C7),
    danger = Color(0xFFFEE2E2),
    info = Color(0xFFDBEAFE),
)

internal val SemanticBgDark = SemanticBackgrounds(
    success = Color(0xFF10291F),
    warning = Color(0xFF2E2411),
    danger = Color(0xFF2E1414),
    info = Color(0xFF14203A),
)

/**
 * Fill behind white label text on a success surface.
 *
 * Shifted from `--success` (#0E9F6E), where white measures 3.39:1. The semantic
 * *foreground* constant is untouched; only the button fill moves.
 */
internal val SuccessFill = Color(0xFF0B855E)

/** Error *text*. Distinct from `--danger`, which is used for icons, dots and fills. */
internal val ErrorTextLight = Color(0xFFC82020)
internal val ErrorTextDark = Color(0xFFF87171)

/** Brand-coloured text on a `--brand-50` surface (badge-brand, active rail item). */
internal val OnBrandLight = Color(0xFFDB163C)
internal val OnBrandDark = Color(0xFFFF6C8E)

/**
 * Every token the CSS defines that Material 3's [ColorScheme] has no slot for.
 *
 * Keeping them here rather than inlining literals at call sites is the point of
 * the story: a component that wants a 20dp card radius reads
 * [MaterialTheme.knRadii] instead of writing `20.dp`, and the accessibility test
 * can then trust that every surface in the library came from this file.
 */
@Immutable
data class KnColors(
    val bgPage: Color,
    val bgCard: Color,
    val bgSunken: Color,
    val bgRail: Color,
    val border: Color,
    val borderStrong: Color,
    /** `--text-primary`. Mirrors `colorScheme.onSurface`. */
    val textPrimary: Color,
    /** `--text-secondary`. Mirrors `colorScheme.onSurfaceVariant`. */
    val textSecondary: Color,
    /** Third text level. Material 3 has no slot for it. */
    val textTertiary: Color,
    /** `--text-inverse` applied to a solid surface (snackbar). */
    val onSurfaceInverse: Color,
    val surfaceInverse: Color,
    /** `--brand-50`. Reassigned in dark. */
    val brandSurface: Color,
    /** Brand-hued text/icon drawn on [brandSurface]. */
    val onBrand: Color,
    /**
     * `--warm-400`, used by the neutral status dot. It is not one of the three
     * text levels, so it is neither readable text nor a UI component's default
     * ink — it exists only so a component never has to spell the hex itself.
     */
    val neutralMarker: Color,
    val semantic: SemanticColors,
    val semanticBg: SemanticBackgrounds,
    val primaryFill: Color,
    val successFill: Color,
    val dangerFill: Color,
    val infoFill: Color,
    /** Error message text — must clear 4.5:1 on both [bgCard] and [semanticBg].danger. */
    val errorText: Color,
    val focusRing: Color,
    val divider: Color,
)

internal val LightColors = KnColors(
    bgPage = Warm50,
    bgCard = Warm0,
    bgSunken = Warm100,
    bgRail = Warm0,
    border = Warm200,
    borderStrong = Warm300,
    textPrimary = Warm900,
    textSecondary = Warm600,
    // --text-tertiary #79726A clears 4.5:1 on --bg-card (4.74) but not on
    // --bg-page (4.44) or --bg-sunken (4.21). Darkened within the warm scale.
    textTertiary = Color(0xFF746D65),
    onSurfaceInverse = Warm0,
    surfaceInverse = Warm900,
    brandSurface = Brand50Light,
    onBrand = OnBrandLight,
    neutralMarker = Warm400,
    semantic = SemanticColors.Shared,
    semanticBg = SemanticBgLight,
    primaryFill = PrimaryFill,
    successFill = SuccessFill,
    dangerFill = SemanticColors.Shared.danger,
    infoFill = SemanticColors.Shared.info,
    errorText = ErrorTextLight,
    focusRing = Brand500,
    divider = Warm200,
)

internal val DarkColors = KnColors(
    // Dark surfaces use literal hex (shared.css lines 94-99), not the warm scale.
    bgPage = Color(0xFF14100F),
    bgCard = Color(0xFF1F1A19),
    bgSunken = Color(0xFF2A2321),
    bgRail = Color(0xFF1A1615),
    border = Color(0xFF38302E),
    borderStrong = Color(0xFF4A3F3C),
    textPrimary = Color(0xFFF7F3F0),
    textSecondary = Color(0xFFC9BFB9),
    // --text-tertiary #A29892 was the risk the spec flagged. Measured: 6.10:1 on
    // --bg-card. Unchanged.
    textTertiary = Color(0xFFA29892),
    // shared.css line 871: the dark snackbar inverts to a light surface.
    onSurfaceInverse = Warm900,
    surfaceInverse = Warm200,
    brandSurface = Brand50Dark,
    onBrand = OnBrandDark,
    neutralMarker = Warm400,
    // The same instance the light scheme uses. No recomputation here — that
    // omission is the structural proof of the "semantic foreground never
    // changes" rule.
    semantic = SemanticColors.Shared,
    semanticBg = SemanticBgDark,
    primaryFill = PrimaryFill,
    successFill = SuccessFill,
    dangerFill = SemanticColors.Shared.danger,
    infoFill = SemanticColors.Shared.info,
    errorText = ErrorTextDark,
    focusRing = Brand500,
    divider = Color(0xFF38302E),
)

/** Exposed for the accessibility test, which measures both themes. */
val LightColorScheme: ColorScheme = lightColorScheme(
    primary = PrimaryFill,
    onPrimary = Warm0,
    primaryContainer = Brand50Light,
    onPrimaryContainer = OnBrandLight,
    inversePrimary = Brand300,
    secondary = Warm600,
    onSecondary = Warm0,
    secondaryContainer = Warm100,
    onSecondaryContainer = Warm900,
    tertiary = Warm700,
    onTertiary = Warm0,
    tertiaryContainer = Warm200,
    onTertiaryContainer = Warm900,
    background = Warm50,
    onBackground = Warm900,
    surface = Warm0,
    onSurface = Warm900,
    surfaceVariant = Warm100,
    onSurfaceVariant = Warm600,
    surfaceTint = Brand500,
    inverseSurface = Warm900,
    inverseOnSurface = Warm25,
    error = ErrorTextLight,
    onError = Warm0,
    errorContainer = SemanticBgLight.danger,
    onErrorContainer = ErrorTextLight,
    outline = Warm300,
    outlineVariant = Warm200,
    scrim = Warm900,
)

val DarkColorScheme: ColorScheme = darkColorScheme(
    primary = PrimaryFill,
    onPrimary = Warm0,
    primaryContainer = Brand50Dark,
    onPrimaryContainer = OnBrandDark,
    inversePrimary = Brand300,
    secondary = Warm300,
    onSecondary = Warm900,
    secondaryContainer = Warm700,
    onSecondaryContainer = Warm25,
    tertiary = Warm200,
    onTertiary = Warm900,
    tertiaryContainer = Warm600,
    onTertiaryContainer = Warm25,
    background = Color(0xFF14100F),
    onBackground = Color(0xFFF7F3F0),
    surface = Color(0xFF1F1A19),
    onSurface = Color(0xFFF7F3F0),
    surfaceVariant = Color(0xFF2A2321),
    onSurfaceVariant = Color(0xFFC9BFB9),
    surfaceTint = Brand500,
    inverseSurface = Warm200,
    inverseOnSurface = Warm900,
    error = ErrorTextDark,
    onError = Warm900,
    errorContainer = SemanticBgDark.danger,
    onErrorContainer = ErrorTextDark,
    outline = Color(0xFF4A3F3C),
    outlineVariant = Color(0xFF38302E),
    scrim = Color(0xFF000000),
)

internal val LocalKnColors = staticCompositionLocalOf { LightColors }

/** The tokens Material 3's `ColorScheme` has no slot for. */
val MaterialTheme.knColors: KnColors
    @Composable get() = LocalKnColors.current

/**
 * Human-readable record of every deliberate departure from `shared.css`, with
 * the measurement that forced it. Kept next to the values it explains.
 */
internal val CONTRAST_ADJUSTMENTS = listOf(
    "light --text-tertiary  #79726A -> #746D65  (on --bg-sunken 4.21:1 -> 4.53:1)",
    "--brand-500 as a fill   #FF385C -> #E02649  (white-on 3.52:1 -> 4.62:1)",
    "--success as a fill     #0E9F6E -> #0B855E  (white-on 3.39:1 -> 4.64:1)",
    "light brand-on-brand-50 #E81E45 -> #DB163C  (4.07:1 -> 4.57:1)",
    "dark  brand-on-brand-50 #E81E45 -> #FF6C8E  (3.86:1 -> 6.39:1)",
    "light error text        #DC2626 -> #C82020  (on --danger-bg 3.95:1 -> 4.67:1)",
    "dark  error text        #DC2626 -> #F87171  (on --danger-bg 3.55:1 -> 6.19:1)",
)
