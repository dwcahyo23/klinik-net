package net.klinik.rental.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf

/**
 * Whether the app is dark, and how to flip it.
 *
 * ## Why a `CompositionLocal` and not a parameter
 *
 * The control lives in `KnTopBar`, which eight screens call. Threading two values through eight
 * call sites, `Screens` and every shell fixture would be a great deal of plumbing for one button —
 * and each of those eight is a place to forget it. A local provided once at the Activity reaches
 * every bar, and a screen rendered alone in a test (where nothing provides it) draws no button,
 * which is the right answer for a component that is being measured in isolation.
 *
 * `compositionLocalOf` rather than `staticCompositionLocalOf`: the value changes when the owner
 * taps, and a static local would not invalidate the bars that read it.
 *
 * `null` is meaningful — "this subtree has no theme control" — so the type is nullable and the
 * default is `null`. A non-null default would need a no-op `onToggle` and a made-up `dark`, which
 * is a control that renders and does nothing.
 */
@Immutable
data class ThemeControl(
    val dark: Boolean,
    val onToggle: () -> Unit,
)

val LocalThemeControl = compositionLocalOf<ThemeControl?> { null }

/** The current control, or `null` outside a themed shell. */
@Composable
fun rememberThemeControl(): ThemeControl? = LocalThemeControl.current
