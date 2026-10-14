package net.klinik.rental.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import net.klinik.rental.R
import androidx.compose.material3.MaterialTheme

/**
 * Plus Jakarta Sans — the body face (`--font-body`).
 *
 * `shared.css` line 6 loads this from the Google Fonts CDN. An offline tablet
 * app cannot fetch at runtime, so the weights are bundled under `res/font`.
 * The static instances came from the google/fonts variable font, instanced at
 * each weight: Android's variable-font support is API 26+ and this app's minSdk
 * is 24, so shipping the variable file alone would render every weight as the
 * default instance on API 24-25.
 */
val PlusJakartaSans = FontFamily(
    Font(R.font.plus_jakarta_sans_400, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_500, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_600, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_700, FontWeight.Bold),
    Font(R.font.plus_jakarta_sans_800, FontWeight.ExtraBold),
)

/**
 * Fraunces — the display face (`--font-display`).
 *
 * Instanced at `opsz=144`, `SOFT=0`, `WONK=0`: the CSS requests `opsz,wght@9..144`
 * and never mentions SOFT or WONK, so both stay at their axis minimum.
 */
val Fraunces = FontFamily(
    Font(R.font.fraunces_600, FontWeight.SemiBold),
    Font(R.font.fraunces_700, FontWeight.Bold),
)

/**
 * The `--fs-*` scale from `shared.css` line 72, plus a 14sp floor.
 *
 * ## The 14sp floor
 *
 * The epic context sets "14sp as the floor for labels and data", and the CSS
 * annotates `--fs-xs: 12px`. Those are in tension: `.badge`, `.kpi-label` and
 * `.field-hint` all use `--fs-xs`. The floor wins for anything the owner has to
 * *read* — field labels, data values, table cells — so [xs] is 14sp rather than
 * 12. The visual weight the CSS was reaching for with a small tracked-out label
 * is kept with the letter spacing instead of the smaller size.
 */
@Immutable
data class KnTypography(
    val displayLarge: TextStyle,
    val displayMedium: TextStyle,
    val displaySmall: TextStyle,
    val title: TextStyle,
    val bodyLarge: TextStyle,
    val body: TextStyle,
    /** 14sp floor — labels and data. */
    val label: TextStyle,
    /**
     * Tracked-out small label (`--fs-xs`, weight 700/800).
     *
     * Not uppercased by the style: `TextStyle` has no `textTransform`, so a
     * caller that wants the CSS's `text-transform: uppercase` applies it at the
     * call site with locale-aware casing. `KnTableHeaderRow` is the one that
     * does.
     */
    val overline: TextStyle,
    /** Money and other figures that must align in a column. */
    val figure: TextStyle,
    val moneyLarge: TextStyle,
    val numpadKey: TextStyle,
    val pinKey: TextStyle,
) {
    companion object {
        val Clinic = KnTypography(
            // --fs-3xl
            displayLarge = TextStyle(
                fontFamily = Fraunces,
                fontWeight = FontWeight.Bold,
                fontSize = 48.sp,
                lineHeight = 56.sp,
                letterSpacing = (-1.2).sp,
            ),
            // --fs-2xl — used for KPI values, which are usually money.
            // `tnum` is not optional here: a KPI row is read by comparing
            // figures side by side, and without tabular digits the thousands
            // separator makes the column jitter as amounts change. Fraunces has
            // no `onum` figures, so this is inert on this face — which is
            // exactly why the KPI tile also renders its figure in
            // [KnKpiTile]'s dedicated style rather than relying on the display
            // face for alignment.
            displayMedium = TextStyle(
                fontFamily = Fraunces,
                fontWeight = FontWeight.Bold,
                fontSize = 36.sp,
                lineHeight = 42.sp,
                letterSpacing = (-1.0).sp,
                fontFeatureSettings = TABULAR_FIGURES,
            ),
            // --fs-xl
            displaySmall = TextStyle(
                fontFamily = Fraunces,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                letterSpacing = (-0.6).sp,
            ),
            // --fs-lg — the page title.
            title = TextStyle(
                fontFamily = Fraunces,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                lineHeight = 26.sp,
                letterSpacing = (-0.3).sp,
            ),
            // --fs-md
            bodyLarge = TextStyle(
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                lineHeight = 24.sp,
            ),
            // --fs-sm — 1.5 line-height, matching html/body in the CSS.
            body = TextStyle(
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 21.sp,
            ),
            // --fs-sm, weight 700. At the 14sp floor.
            label = TextStyle(
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = (-0.1).sp,
            ),
            // --fs-xs with the CSS's .8px tracking. Uppercasing is NOT
            // part of this style: `TextStyle` has no `textTransform`, so a style
            // that claims to be an uppercase label renders lowercase unless each
            // call site uppercases separately. The KDoc says so, and the two
            // callers that want it uppercase do it themselves with the
            // locale-aware casing the CSS `text-transform` implies.
            overline = TextStyle(
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                letterSpacing = 0.8.sp,
            ),
            figure = TextStyle(
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontFeatureSettings = TABULAR_FIGURES,
            ),
            moneyLarge = TextStyle(
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 36.sp,
                lineHeight = 40.sp,
                letterSpacing = (-1.2).sp,
                fontFeatureSettings = TABULAR_FIGURES,
            ),
            numpadKey = TextStyle(
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                lineHeight = 30.sp,
                fontFeatureSettings = TABULAR_FIGURES,
            ),
            pinKey = TextStyle(
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                lineHeight = 32.sp,
                fontFeatureSettings = TABULAR_FIGURES,
            ),
        )
    }
}

/**
 * `font-variant-numeric: tabular-nums`.
 *
 * Comparing a column of money is the owner's actual morning job, so every
 * figure style carries this. Without it the thousands separator makes digits
 * jitter as values change.
 */
const val TABULAR_FIGURES = "tnum"

internal val LocalKnTypography = staticCompositionLocalOf { KnTypography.Clinic }

val MaterialTheme.knTypography: KnTypography
    @Composable get() = LocalKnTypography.current

/**
 * Material 3 slots, mapped onto the CSS scale. Components should prefer
 * [KnTypography]; these exist so anything Material 3 renders internally (text
 * fields, dialogs) still gets Klinik.Net typefaces.
 */
val ClinicTypography = Typography(
    displayLarge = KnTypography.Clinic.displayLarge.copy(fontFamily = Fraunces),
    displayMedium = KnTypography.Clinic.displayMedium.copy(fontFamily = Fraunces),
    displaySmall = KnTypography.Clinic.displaySmall.copy(fontFamily = Fraunces),
    headlineLarge = KnTypography.Clinic.displaySmall.copy(fontFamily = Fraunces),
    headlineMedium = KnTypography.Clinic.title.copy(fontFamily = Fraunces),
    headlineSmall = KnTypography.Clinic.title.copy(fontFamily = Fraunces),
    titleLarge = KnTypography.Clinic.title.copy(fontFamily = Fraunces),
    titleMedium = KnTypography.Clinic.bodyLarge.copy(fontFamily = PlusJakartaSans),
    titleSmall = KnTypography.Clinic.label.copy(fontFamily = PlusJakartaSans),
    bodyLarge = KnTypography.Clinic.bodyLarge,
    bodyMedium = KnTypography.Clinic.body,
    bodySmall = KnTypography.Clinic.body,
    labelLarge = KnTypography.Clinic.label,
    labelMedium = KnTypography.Clinic.overline.copy(fontSize = 14.sp, lineHeight = 18.sp),
    labelSmall = KnTypography.Clinic.overline.copy(fontSize = 14.sp, lineHeight = 18.sp),
)
