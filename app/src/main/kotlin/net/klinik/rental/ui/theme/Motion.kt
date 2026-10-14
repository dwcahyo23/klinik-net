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
 * ## The 420ms PIN shake is deliberately absent
 *
 * The story's acceptance criteria name "shake 420ms untuk PIN wrong", but that
 * is the only motion outside this scale and it fires from the PIN entry flow
 * Story 5.3 owns. Defining a `Motion.shake` token here would ship an unused
 * constant that later invites reuse outside its one legal home. [PinGrid] here
 * is layout and input only. Story 5.3 adds the duration together with the flow
 * that triggers it.
 *
 * Nothing in this file loops, bounces or overshoots. The easing is a single
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
            easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f),
        )
    }
}

internal val LocalKnMotion = staticCompositionLocalOf { KnMotion.Clinic }

val MaterialTheme.knMotion: KnMotion
    @Composable get() = LocalKnMotion.current
