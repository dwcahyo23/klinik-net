package net.klinik.rental.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color

/**
 * The Klinik.Net theme.
 *
 * Wraps Material 3 and adds the extension tokens the CSS defines and Material 3
 * has no slot for: [knColors], [radii], [spacing], [elevation], [knTypography]
 * and [motion]. Components read those rather than literals, so a token can move
 * in one place.
 *
 * @param dark when true the dark scheme applies — including its own `-bg`
 *   companions and its own `brand-50`. The four semantic foregrounds are the
 *   same constants either way; see [Color.kt].
 */
@Composable
fun KlinikNetTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val scheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val elevation = if (darkTheme) KnElevation.Dark else KnElevation.Light

    CompositionLocalProvider(
        LocalKnColors provides colors,
        LocalKnRadii provides KnRadii.Clinic,
        LocalKnSpacing provides KnSpacing.Clinic,
        LocalKnElevation provides elevation,
        LocalKnMotion provides KnMotion.Clinic,
        LocalKnTypography provides KnTypography.Clinic,
        // The 48dp floor and the focus-ring geometry. Provided rather than
        // left to the `staticCompositionLocalOf` default, so the value the
        // components read is the same one `KlinikNetTheme` was built with —
        // two sources of truth for one floor, one of them unreachable, is how a
        // token silently stops being the one in force.
        LocalKnAccessibility provides KnAccessibility.Clinic,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = ClinicTypography,
            shapes = ClinicShapes,
            content = content,
        )
    }
}
