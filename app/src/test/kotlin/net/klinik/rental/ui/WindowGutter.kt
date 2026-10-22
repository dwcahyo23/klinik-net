package net.klinik.rental.ui

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import net.klinik.rental.ui.theme.KnSpacing

/**
 * The window gutter, measured the way an owner sees it: **against the window**, not against an
 * internal parent.
 *
 * `shared.css:285-289` pads `.page-body` once, with `--sp-6` — 24dp. The three master-detail
 * screens (`TunggakanScreen`, `MobilScreen`, `PenyewaScreen`) each apply that padding to
 * their own body column, and `KnMasterDetail` used to apply it a second time, which shipped 48dp
 * where the prototype has 24dp.
 *
 * ## Why this lives outside any one screen's test
 *
 * Because the padding is a property of **each screen's own body column**, and the three columns
 * are three separate pieces of code. A helper shared by all three assertions is also the thing
 * that makes them comparable: one `Dp` token, one measurement, one place to read what the number
 * is supposed to be. Duplicating the arithmetic three times would let the three drift.
 *
 * ## Why it measures the window
 *
 * Measuring from a parent node would let a bug hide behind cancelling offsets — a screen that
 * padded its column twice and got subtracted twice would still read 24dp against its own parent
 * while rendering at 48dp against the screen. The window is the frame the owner judges, so it is
 * the origin here.
 *
 * @param node the row or pane whose leading edge should sit one gutter in. Must have real bounds;
 *   a node laid out at zero measures zero on every edge and would pass for the right answer.
 * @return the distance from the window's left edge to [node]'s left edge, in dp.
 */
fun ComposeContentTestRule.leftInsetOf(node: SemanticsNodeInteraction): Dp {
    val root = onRoot().fetchSemanticsNode()
    val bounds = node.fetchSemanticsNode().boundsInRoot
    val density = root.layoutInfo.density
    return with(density) { (bounds.left - root.boundsInRoot.left).toDp() }
}

/**
 * `shared.css:285-289` — `--sp-6`.
 *
 * Read from the spacing token rather than typed as `24.dp`, for the reason every other
 * expectation in this repo's UI tests reads a token: a literal goes on passing after the token
 * changes, which is exactly when the assertion is worth having.
 */
val WindowGutter: Dp = KnSpacing.Clinic.xxl