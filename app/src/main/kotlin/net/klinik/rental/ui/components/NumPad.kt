package net.klinik.rental.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Delete
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Save
import kotlinx.coroutines.delay
import net.klinik.rental.R
import net.klinik.rental.ui.format.formatRupiahDigits
import net.klinik.rental.ui.theme.focusRing
import net.klinik.rental.ui.theme.knBrandShadow
import net.klinik.rental.ui.theme.knColors
import net.klinik.rental.ui.theme.knElevation
import net.klinik.rental.ui.theme.knMotion
import net.klinik.rental.ui.theme.knRadii
import net.klinik.rental.ui.theme.knSpacing
import net.klinik.rental.ui.theme.knTypography
import net.klinik.rental.ui.theme.minTouchTarget

/**
 * The rupiah entry numpad (`.numpad-*`, `shared.css` section 9).
 *
 * Nominal entry uses this rather than the system keyboard: the owner is
 * standing and holding something, so large targets beat typing speed.
 *
 * ## Presentation only
 *
 * The composable renders a digit string and forwards taps to `NumPadLogic.kt`,
 * which owns the cap and the save rule. It computes no business logic: whether
 * an amount is *acceptable* for a given screen belongs to that screen.
 *
 * ## Rejection has to be visible
 *
 * Rejecting a non-positive save is only meaningful if the user sees it. The red
 * flash is the feedback, held briefly and then released — it does not loop, and
 * there is deliberately no shake (see `Motion.kt`: the 420ms PIN shake belongs
 * to Story 5.3). Refusing the tap silently would teach the owner the button is
 * broken.
 *
 * @param value the digits typed so far, without separators. Hoisted so the
 *   caller can restore or observe it.
 * @param onValueChange called with the new digit string.
 * @param onSave called with the amount only when it is greater than zero. A
 *   non-positive amount is rejected here and never reaches the caller, so no
 *   screen can accidentally accept one.
 */
@Composable
fun KnNumPad(
    value: String,
    onValueChange: (String) -> Unit,
    onSave: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val motion = MaterialTheme.knMotion

    /**
     * Counts refusals; the flash is up whenever this is non-zero.
     *
     * A rejection is an *event*, not a property of the digits - a Save on
     * zero must flash even though nothing about the display changed - so
     * this cannot be derived from the value.
     *
     * A counter rather than a boolean so a second refused Save restarts the
     * hold. A boolean leaves the key unchanged, the keyed effect does not
     * re-run, and the flash does not refresh - which the owner reads as the
     * key having stopped responding.
     */
    var rejectionCount by remember { mutableStateOf(0) }

    /**
     * The digits this pad is working on.
     *
     * Held in a mutable state rather than read from the [value] parameter
     * directly, because every handler below closes over `value` as of the last
     * recomposition. Two taps that land before Compose recomposes — exactly what
     * happens when a key is held down, and what the 90Hz panel makes easy — both
     * read the same string, so both append to the pre-tap value and the second
     * digit is lost. The 12-digit cap is checked against the stale string too,
     * so the pad can hold fewer than twelve digits while still refusing a
     * thirteenth tap.
     *
     * [next] is the single place a tap reads and writes the current digits, so
     * handlers compose on the result of the previous one regardless of
     * recomposition timing.
     *
     * The seed is normalised, and the re-sync only fires when the caller's value
     * has genuinely moved. Both matter: a caller seeding `"abc"`, `"12.5"` or a
     * fifteen-digit string would otherwise render `Rp 0` with Save rejecting and
     * no error shown, and a caller that clamps or ignores the value it was
     * handed would see its digits silently discarded the moment it echoed back
     * something different.
     */
    val digits = remember { mutableStateOf(normaliseNumPadDigits(value)) }
    // `lastSeen` is what the pad last *emitted or accepted*, so the effect can
    // tell a real caller-side change from an echo of this pad's own output.
    val lastSeen = remember { mutableStateOf(digits.value) }
    LaunchedEffect(value) {
        val incoming = normaliseNumPadDigits(value)
        // An echo of our own output is not a caller change.
        if (incoming != lastSeen.value) {
            digits.value = incoming
            lastSeen.value = incoming
        }
    }

    fun next(transform: (String) -> String) {
        val updated = transform(digits.value)
        digits.value = updated
        lastSeen.value = updated
        onValueChange(updated)
    }

    val rejected = rejectionCount > 0

    /**
     * Holds the rejection for four `slowMillis` frames, then clears it.
     *
     * The duration comes from the motion scale — the AC allows only
     * 140/220/300ms — so this is a `KnMotion` token rather than a fourth
     * hand-rolled constant that `KnMotion.tween`'s `require` could never catch.
     *
     * Four frames rather than one, and the reason is correctness rather than
     * taste: the colour animates over `fastMillis` (140ms) while the hold is
     * `slowMillis` (300ms). A recomposition landing late in the animation would
     * otherwise find the hold already expired and the two would disagree about
     * what the owner is being shown — a rejected tap that flashes almost
     * imperceptibly, or one whose message vanishes mid-glance.
     */
    LaunchedEffect(rejectionCount) {
        if (rejectionCount > 0) {
            repeat(REJECT_HOLD_FRAMES) {
                delay(motion.slowMillis.toLong())
            }
            rejectionCount = 0
        }
    }

    fun tap() = haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)

    fun rejectSave() {
        // A heavier haptic than a digit: the key was pressed and refused, and
        // that needs to be felt as well as seen.
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        rejectionCount++
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.knColors.bgCard)
            .padding(MaterialTheme.knSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.knSpacing.lg),
    ) {
        NumpadDisplay(value = digits.value, flash = rejected)

        // Four rows of three digits plus a function column. The function column
        // is what keeps 1-2-3 aligned with 4-5-6 and 7-8-9 when the numpad is
        // held in two hands, so the layout follows the CSS grid rather than a
        // bare 3x4.
        val gap = MaterialTheme.knSpacing.sm
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            NumpadDigitRow(
                digits = listOf("7", "8", "9"),
                function = NumpadFunction.Delete,
                onTap = { tap() },
                onValueChange = ::next,
            )
            NumpadDigitRow(
                digits = listOf("4", "5", "6"),
                function = NumpadFunction.Clear,
                onTap = { tap() },
                onValueChange = ::next,
            )
            NumpadDigitRow(
                digits = listOf("1", "2", "3"),
                function = NumpadFunction.PadZeros,
                onTap = { tap() },
                onValueChange = ::next,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                // The zero key spans the three digit columns.
                NumpadKey(
                    label = stringResource(R.string.numpad_digit_zero),
                    onClick = {
                        tap()
                        next { appendDigit(it, '0') }
                    },
                    modifier = Modifier.weight(3f),
                )
                NumpadSaveKey(
                    onClick = {
                        when (val result = parseNumPadSave(digits.value)) {
                            is NumPadSaveResult.Accepted -> onSave(result.amount)
                            NumPadSaveResult.Rejected -> rejectSave()
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * The three function keys down the right-hand column.
 *
 * Each carries what the key *shows* ([labelRes]) and what it *announces*
 * ([contentDescriptionRes]) separately, because for the clear key those differ:
 * the face is `C`, the announcement is "Hapus".
 */
internal enum class NumpadFunction(val labelRes: Int, val contentDescriptionRes: Int) {
    /** Shows a backspace glyph; the spoken form is its purpose. */
    Delete(R.string.numpad_key_delete, R.string.numpad_key_delete),

    /** Shows `C`, announces "Hapus". */
    Clear(R.string.numpad_key_clear_face, R.string.numpad_key_clear),

    /** Shows `000` and announces the same. */
    PadZeros(R.string.numpad_key_zero_pad, R.string.numpad_key_zero_pad),
}

@Composable
private fun NumpadDigitRow(
    digits: List<String>,
    function: NumpadFunction,
    onTap: () -> Unit,
    onValueChange: ((String) -> String) -> Unit,
) {
    val gap = MaterialTheme.knSpacing.sm
    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
        digits.forEach { d ->
            NumpadKey(
                label = d,
                onClick = {
                    onTap()
                    onValueChange { appendDigit(it, d[0]) }
                },
                modifier = Modifier.weight(1f),
            )
        }
        NumpadFunctionKey(
            function = function,
            onClick = {
                onTap()
                onValueChange { current ->
                    when (function) {
                        NumpadFunction.Delete -> deleteLastDigit(current)
                        NumpadFunction.Clear -> ""
                        NumpadFunction.PadZeros -> appendZeroPad(current)
                    }
                }
            },
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * The calculator-style display: the amount in progress, large and tabular.
 *
 * Grouped by [formatRupiahDigits] — the same grouping every other screen uses,
 * so an amount looks identical here and in a contract row. The `Rp` prefix is a
 * separate span so it can sit at the smaller `.rp` size from the CSS.
 *
 * ## Why rejection changes the text, not only the colour
 *
 * A rejected Save used to flash the background and recolour the amount. That is
 * a colour-only signal: a screen-reader user gets a live region that announces
 * nothing, because no text changed, and a colourblind user may not see the
 * flash at all. The message line below the amount is what makes the rejection
 * real — it says what is wrong and what to do, which is the same bar every other
 * error in the app is held to. The flash is kept as reinforcement, not as the
 * signal.
 */
@Composable
private fun NumpadDisplay(value: String, flash: Boolean) {
    val kn = MaterialTheme.knColors
    val spacing = MaterialTheme.knSpacing
    val shape = RoundedCornerShape(MaterialTheme.knRadii.lg)
    val background by animateColorAsState(
        targetValue = if (flash) kn.semanticBg.danger else kn.bgSunken,
        animationSpec = MaterialTheme.knMotion.tween(MaterialTheme.knMotion.fastMillis),
        label = "numpad-reject-flash",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(background, shape)
            .border(1.dp, kn.border, shape)
            .padding(horizontal = spacing.xl, vertical = spacing.lg)
            .testTag(DISPLAY_TEST_TAG)
            // Unmerged, so the rejection message is its own node. A merged
            // region would fold the message into the amount and TalkBack would
            // read them as one run of text.
            .semantics(mergeDescendants = false) { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = stringResource(R.string.currency_symbol_rupiah),
                style = MaterialTheme.knTypography.title,
                color = kn.textTertiary,
                modifier = Modifier.padding(end = spacing.sm),
            )
            Text(
                text = if (value.isEmpty()) {
                    // An untouched display still has to show a figure: an empty
                    // panel reads as broken rather than as "nothing typed yet".
                    // `numpad_display_empty` is the label line below, not this.
                    stringResource(R.string.numpad_digit_zero)
                } else {
                    formatRupiahDigits(numPadAmount(value))
                },
                style = MaterialTheme.knTypography.moneyLarge,
                color = if (flash) kn.errorText else kn.textPrimary,
                textAlign = TextAlign.End,
                maxLines = 1,
            )
        }
        Text(
            // While the flash is showing, this line *is* the rejection: it
            // replaces the static label so the change is announced.
            text = if (flash) {
                stringResource(R.string.numpad_rejected_zero)
            } else {
                stringResource(R.string.numpad_display_label)
            },
            style = MaterialTheme.knTypography.body,
            color = if (flash) kn.errorText else kn.textTertiary,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun NumpadKey(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val kn = MaterialTheme.knColors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Surface(
        onClick = onClick,
        modifier = modifier
            .height(NUMPAD_KEY_HEIGHT)
            .minTouchTarget()
            .focusRing(focused, MaterialTheme.knRadii.md),
        shape = RoundedCornerShape(MaterialTheme.knRadii.md),
        color = kn.bgCard,
        contentColor = kn.textPrimary,
        border = BorderStroke(1.5.dp, kn.border),
        interactionSource = interaction,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.knTypography.numpadKey,
                color = kn.textPrimary,
            )
        }
    }
}

@Composable
private fun NumpadFunctionKey(
    function: NumpadFunction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val kn = MaterialTheme.knColors
    // Read outside `semantics {}`, which is not a composable scope.
    val description = stringResource(function.contentDescriptionRes)
    val label = stringResource(function.labelRes)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Surface(
        onClick = onClick,
        modifier = modifier
            .height(NUMPAD_KEY_HEIGHT)
            .minTouchTarget()
            .focusRing(focused, MaterialTheme.knRadii.md)
            .semantics {
                // The key shows a glyph, `C` or `000`; a screen reader needs the
                // spoken form, so the description is set explicitly rather than
                // left to the Icon's null.
                contentDescription = description
            },
        shape = RoundedCornerShape(MaterialTheme.knRadii.md),
        color = kn.bgSunken,
        contentColor = kn.textSecondary,
        interactionSource = interaction,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (function == NumpadFunction.Delete) {
                Icon(
                    imageVector = Lucide.Delete,
                    contentDescription = null,
                    tint = kn.textSecondary,
                    modifier = Modifier.size(26.dp),
                )
            } else {
                Text(
                    text = label,
                    style = MaterialTheme.knTypography.label,
                    color = kn.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun NumpadSaveKey(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(NUMPAD_KEY_HEIGHT)
            .minTouchTarget()
            .focusRing(focused, MaterialTheme.knRadii.md)
            .knBrandShadow(RoundedCornerShape(MaterialTheme.knRadii.md)),
        shape = RoundedCornerShape(MaterialTheme.knRadii.md),
        color = MaterialTheme.knColors.primaryFill,
        contentColor = Color.White,
        interactionSource = interaction,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = MaterialTheme.knSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Lucide.Save,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = stringResource(R.string.numpad_key_save),
                style = MaterialTheme.knTypography.label,
                color = Color.White,
                maxLines = 1,
            )
        }
    }
}

/** `.numpad-key { min-height: 66px }`. Comfortably above the 48dp floor. */
private val NUMPAD_KEY_HEIGHT = 66.dp

/**
 * Test tag on the display panel, so NumPadRejectTest can sample the pixels it
 * paints rather than guessing a position in a rendered grid.
 */

/**
 * Test tag on the display panel, so NumPadRejectTest can sample the pixels the
 * panel paints rather than guessing a position inside a rendered grid.
 */
internal const val DISPLAY_TEST_TAG = "numpad-display"

/**
 * How many knMotion.slowMillis frames the rejection is held for. See
 * LaunchedEffect(rejectionCount) above.
 */
private const val REJECT_HOLD_FRAMES = 4