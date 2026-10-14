package net.klinik.rental.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme

/**
 * Spacing scale from `shared.css` lines 56-57 — a 4dp base.
 *
 * Exposed as a theme extension so components cannot hardcode `16.dp`. There is
 * no `sp-7` or `sp-9` in the CSS and there is none here: the scale is what it
 * is, and a gap that does not exist is a decision someone has to make on
 * purpose.
 */
@Immutable
data class KnSpacing(
    /** `--sp-1` */
    val xs: Dp,
    /** `--sp-2` */
    val sm: Dp,
    /** `--sp-3` */
    val md: Dp,
    /** `--sp-4` */
    val lg: Dp,
    /** `--sp-5` */
    val xl: Dp,
    /** `--sp-6` */
    val xxl: Dp,
    /** `--sp-8` */
    val xxxl: Dp,
    /** `--sp-10` */
    val huge: Dp,
    /** `--sp-12` */
    val giant: Dp,
    /** `--sp-16` */
    val colossal: Dp,
) {
    companion object {
        val Clinic = KnSpacing(
            xs = 4.dp,
            sm = 8.dp,
            md = 12.dp,
            lg = 16.dp,
            xl = 20.dp,
            xxl = 24.dp,
            xxxl = 32.dp,
            huge = 40.dp,
            giant = 48.dp,
            colossal = 64.dp,
        )
    }
}

internal val LocalKnSpacing = staticCompositionLocalOf { KnSpacing.Clinic }

val MaterialTheme.knSpacing: KnSpacing
    @Composable get() = LocalKnSpacing.current
