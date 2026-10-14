package net.klinik.rental.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme

/**
 * The four shadows from `shared.css` lines 63-66 — three soft warm levels plus
 * the brand-coloured shadow reserved for primary surfaces.
 *
 * The shadow *tint* is part of the token, not an accident: the light shadows are
 * `--warm-800` at low alpha and the dark shadows are pure black, because a warm
 * shadow on a near-black surface reads as a smudge. Compose accepts a shadow
 * colour from API 28 and ignores it below that; on 24-27 the elevation still
 * casts, just untinted. That is a graceful degradation, not a silent one — the
 * alternative is dropping elevation entirely on older tablets.
 *
 * [ambient]/[spot]/[brandAmbient]/[brandSpot] are not decorative. They reach
 * the screen through [knShadow], which every shadowed surface uses in place of
 * a bare `shadowElevation`; passing the height alone would render Material's
 * default black and silently drop the "warm shadow plus a brand-coloured shadow"
 * the epic context requires.
 */
@Immutable
data class KnElevation(
    /** `--el-1` — resting cards. */
    val level1: Dp,
    /** `--el-2` — raised: modals, hover. */
    val level2: Dp,
    /** `--el-3` — highest: bottom sheet, snackbar. */
    val level3: Dp,
    /** `--el-brand` — reserved for primary buttons. */
    val brand: Dp,
    val ambient: Color,
    val spot: Color,
    val brandAmbient: Color,
    val brandSpot: Color,
) {
    companion object {
        val Light = KnElevation(
            level1 = 1.dp,
            level2 = 4.dp,
            level3 = 12.dp,
            brand = 6.dp,
            ambient = Color(0x1A262220),
            spot = Color(0x1F262220),
            brandAmbient = Brand500.copy(alpha = 0.30f),
            brandSpot = Brand500.copy(alpha = 0.30f),
        )

        val Dark = KnElevation(
            level1 = 2.dp,
            level2 = 8.dp,
            level3 = 16.dp,
            brand = 6.dp,
            ambient = Color(0x66000000),
            spot = Color(0x73000000),
            brandAmbient = Brand500.copy(alpha = 0.30f),
            brandSpot = Brand500.copy(alpha = 0.30f),
        )
    }
}

internal val LocalKnElevation = staticCompositionLocalOf { KnElevation.Light }

val MaterialTheme.knElevation: KnElevation
    @Composable get() = LocalKnElevation.current

/**
 * Draws an elevation shadow in the token's tint.
 *
 * `Surface(shadowElevation = …)` takes no colour, so every shadowed surface in
 * this library goes through here instead: `Modifier.shadow` carries
 * `ambientColor`/`spotColor`, which is the only way the warm and brand tints
 * reach the screen.
 *
 * `clip = false` because the surface's own background already paints the shape;
 * clipping again would cost an offscreen pass per shadowed surface.
 */
@Composable
fun Modifier.knShadow(
    elevation: Dp,
    shape: Shape,
    brand: Boolean = false,
): Modifier {
    val kn = MaterialTheme.knElevation
    return this.shadow(
        elevation = elevation,
        shape = shape,
        clip = false,
        ambientColor = if (brand) kn.brandAmbient else kn.ambient,
        spotColor = if (brand) kn.brandSpot else kn.spot,
    )
}

/**
 * The brand-tinted shadow, read through [MaterialTheme.knElevation] rather than
 * [LocalKnElevation] so a caller outside a composition still goes through the
 * theme's value.
 */
@Composable
fun Modifier.knBrandShadow(shape: Shape): Modifier =
    knShadow(MaterialTheme.knElevation.brand, shape, brand = true)
