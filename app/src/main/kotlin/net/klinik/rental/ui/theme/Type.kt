package net.klinik.rental.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import net.klinik.rental.R
import androidx.compose.material3.MaterialTheme

/**
 * Plus Jakarta Sans — the app's **only** face.
 *
 * `shared.css` line 6 loads this from the Google Fonts CDN. An offline tablet
 * app cannot fetch at runtime, so the weights are bundled under `res/font`.
 * The static instances came from the google/fonts variable font, instanced at
 * each weight: Android's variable-font support is API 26+ and this app's minSdk
 * is 24, so shipping the variable file alone would render every weight as the
 * default instance on API 24-25.
 *
 * ## Why there is no second face
 *
 * The CSS pairs this with Fraunces for display text (`--font-display`), and the
 * app used to mirror that pairing. The owner rejected it: a serif heading over a
 * sans body read as two different products on one screen. So the display slots
 * below — [KnTypography.displayLarge], [KnTypography.displaySmall] and
 * [KnTypography.title] — carry this same family, and the hierarchy comes from
 * **size, weight and tracking** instead of from a typeface change.
 *
 * The Fraunces files are gone from `res/font` for the same reason: a bundled
 * face nothing draws is a licence to maintain and a chance to reintroduce the
 * split by accident.
 */
val PlusJakartaSans = FontFamily(
    Font(R.font.plus_jakarta_sans_400, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_500, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_600, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_700, FontWeight.Bold),
    Font(R.font.plus_jakarta_sans_800, FontWeight.ExtraBold),
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
    /**
     * A figure at display scale — the donut's centre value.
     *
     * The prototype's `.donut-center .dc-val` is `--fs-lg` (20px) weight 800 with
     * `font-variant-numeric: tabular-nums`.
     *
     * It is a slot rather than a `title.copy(...)` at the call site because it is
     * **weight 800 at 20sp** — the same size as [title] but heavier, and with
     * tabular figures, which a centre value needs so the digits do not jitter
     * as the total changes.
     */
    val figureLarge: TextStyle,
    val moneyLarge: TextStyle,
    val numpadKey: TextStyle,
    val pinKey: TextStyle,
) {
    companion object {
        val Clinic = KnTypography(
            // --fs-3xl
            displayLarge = TextStyle(
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Bold,
                fontSize = 48.sp,
                lineHeight = 56.sp,
                letterSpacing = (-1.2).sp,
            ),
            // --fs-2xl — the KPI value. `tnum` is not optional here: a KPI row is
            // read by comparing figures side by side, and without tabular digits
            // the thousands separator makes the column jitter as amounts change.
            //
            // This was `displayMedium` and `moneyLarge` as two slots. They split
            // only because the display face was Fraunces, which carries no `tnum`
            // — so a figure needed a second, body-face slot at the same size to
            // keep its digits aligned. There is one face now, so the pair is
            // one style: `--fs-2xl` weight 800, tabular.
            moneyLarge = TextStyle(
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 36.sp,
                lineHeight = 40.sp,
                letterSpacing = (-1.2).sp,
                fontFeatureSettings = TABULAR_FIGURES,
            ),
            // --fs-xl
            displaySmall = TextStyle(
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                letterSpacing = (-0.6).sp,
            ),
            // --fs-lg — the page title.
            title = TextStyle(
                fontFamily = PlusJakartaSans,
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
            // `.donut-center .dc-val` — fs-lg, weight 800, tracking -.8, tabular.
            figureLarge = TextStyle(
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
                lineHeight = 24.sp,
                letterSpacing = (-0.8).sp,
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
 *
 * ## Why this is `by lazy`
 *
 * Because this file has a **static initialisation cycle**, and a lazy delegate is
 * what breaks it.
 *
 * `KnTypography.Clinic` lives in a companion object, and its styles name
 * [PlusJakartaSans] — a top-level property of *this* file. So initialising the
 * companion initialises this file. But this property reads
 * `KnTypography.Clinic`, so initialising this file initialises the companion. The
 * two call each other, and which one is entered first decides whether the second
 * one sees a finished value or `null`.
 *
 * Entered from this file first, it works: [PlusJakartaSans] is already assigned by
 * the time this line runs, and the companion initialises cleanly. Entered from the
 * companion first, it does not: the companion is still initialising, so
 * `KnTypography.Clinic` is `null` here, and the `Typography` constructor throws
 * `NullPointerException` — `ExceptionInInitializerError` for every test that
 * touches the type afterwards, in a file that has nothing wrong with it.
 *
 * A lazy delegate removes the cycle: nothing in this file's initialisation reads
 * the companion any more, so the two no longer depend on the order they happen to
 * be entered in. The value is built on first use, which is when the theme is
 * actually installed and the companion is long since ready.
 */
val ClinicTypography: Typography by lazy {
    Typography(
        displayLarge = KnTypography.Clinic.displayLarge,
        displayMedium = KnTypography.Clinic.moneyLarge,
        displaySmall = KnTypography.Clinic.displaySmall,
        headlineLarge = KnTypography.Clinic.displaySmall,
        headlineMedium = KnTypography.Clinic.title,
        headlineSmall = KnTypography.Clinic.title,
        titleLarge = KnTypography.Clinic.title,
        titleMedium = KnTypography.Clinic.bodyLarge,
        titleSmall = KnTypography.Clinic.label,
        bodyLarge = KnTypography.Clinic.bodyLarge,
        bodyMedium = KnTypography.Clinic.body,
        bodySmall = KnTypography.Clinic.body,
        labelLarge = KnTypography.Clinic.label,
        labelMedium = KnTypography.Clinic.overline.copy(fontSize = 14.sp, lineHeight = 18.sp),
        labelSmall = KnTypography.Clinic.overline.copy(fontSize = 14.sp, lineHeight = 18.sp),
    )
}
