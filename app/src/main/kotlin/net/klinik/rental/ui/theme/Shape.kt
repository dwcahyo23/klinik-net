package net.klinik.rental.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme

/**
 * Corner radii from `shared.css` line 60:
 * `--r-sm 10 / --r-md 14 / --r-lg 20 / --r-xl 26 / --r-full 999`.
 *
 * `--r-lg` (20) is the card radius, per the epic context. [Shapes] covers the
 * five Material slots; [KnRadii] names the CSS scale directly so a component
 * asking for "the card radius" reads [KnRadii.card] instead of guessing which
 * Material slot happens to hold 20.
 */
@Immutable
data class KnRadii(
    val sm: Dp,
    val md: Dp,
    val lg: Dp,
    val xl: Dp,
    val full: Dp,
) {
    companion object {
        val Clinic = KnRadii(sm = 10.dp, md = 14.dp, lg = 20.dp, xl = 26.dp, full = 999.dp)
    }
}

internal val LocalKnRadii = staticCompositionLocalOf { KnRadii.Clinic }

val MaterialTheme.knRadii: KnRadii
    @Composable get() = LocalKnRadii.current

val ClinicShapes = Shapes(
    extraSmall = RoundedCornerShape(KnRadii.Clinic.sm),
    small = RoundedCornerShape(KnRadii.Clinic.sm),
    medium = RoundedCornerShape(KnRadii.Clinic.md),
    large = RoundedCornerShape(KnRadii.Clinic.lg),
    extraLarge = RoundedCornerShape(KnRadii.Clinic.xl),
)
