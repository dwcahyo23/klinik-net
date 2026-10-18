package net.klinik.rental.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.IntOffset
import androidx.compose.material3.MaterialTheme

/**
 * Motion from `shared.css` lines 75-77: one easing, three durations, tuned for
 * the 90Hz panel.
 *
 * ## The 420ms PIN shake is a **named exception**, and this is where it lives
 *
 * The UX doc's rule is "no motion longer than 300ms", and it carves out exactly
 * one case by name: *"Shake untuk PIN salah — 420ms, sekali, hanya saat
 * benar-benar salah."* [shakeMillis] is that case.
 *
 * It is **not** added to [allowedMillis], so [tween] still refuses 420ms. That
 * guard is worth keeping exactly as it is: it is what stops a `tween(500)`
 * appearing on some future screen because one component was allowed to reach
 * past the scale. The lock screen builds `tween(KnMotion.shakeMillis)` directly,
 * and the one call site is greppable.
 *
 * Nothing else in this file loops, bounces or overshoots. The easing is a single
 * monotone curve, so a transition that accidentally reuses it cannot surprise
 * anyone.
 */
@Immutable
data class KnMotion(
    /** `--t-fast` */
    val fastMillis: Int,
    /** `--t-base` */
    val baseMillis: Int,
    /** `--t-slow` */
    val slowMillis: Int,
    /**
     * The PIN-failure shake. Outside [allowedMillis] **on purpose** — see the class KDoc.
     *
     * A field rather than a top-level constant so a screen reaches it through the theme it already
     * has, and so the value stays in the file that explains why it exists.
     */
    val shakeMillis: Int,
    /** `cubic-bezier(.2,.8,.2,1)` — the one curve. */
    val easing: Easing,
) {
    fun fast(): Int = fastMillis
    fun base(): Int = baseMillis
    fun slow(): Int = slowMillis

    /** The complete set of legal durations, for the assertion in `AccessibilityTest`. */
    val allowedMillis: Set<Int> = setOf(fastMillis, baseMillis, slowMillis)

    /** `tween` bound to this scale — the only animation spec components should build. */
    fun <T> tween(durationMillis: Int): androidx.compose.animation.core.TweenSpec<T> {
        require(durationMillis in allowedMillis) {
            "duration ${durationMillis}ms is outside the motion scale " +
                "$allowedMillis. Add it to KnMotion deliberately, do not widen the " +
                "tween at the call site."
        }
        return tween(durationMillis = durationMillis, easing = easing)
    }

    companion object {
        val Clinic = KnMotion(
            fastMillis = 140,
            baseMillis = 220,
            slowMillis = 300,
            // The one exception to the 300ms ceiling, named in the UX doc. Not in
            // `allowedMillis` — see the class KDoc.
            shakeMillis = 420,
            easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f),
        )
    }
}

internal val LocalKnMotion = staticCompositionLocalOf { KnMotion.Clinic }

val MaterialTheme.knMotion: KnMotion
    @Composable get() = LocalKnMotion.current
