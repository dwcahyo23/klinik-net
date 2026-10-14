package net.klinik.rental.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.TriangleAlert
import net.klinik.rental.R
import net.klinik.rental.ui.theme.focusRing
import net.klinik.rental.ui.theme.knColors
import net.klinik.rental.ui.theme.knRadii
import net.klinik.rental.ui.theme.knSpacing
import net.klinik.rental.ui.theme.knTypography
import net.klinik.rental.ui.theme.minTouchTarget

/**
 * The validation failures this library knows about, each carrying copy that
 * names both the fault and the fix.
 *
 * An error message that says only "invalid" leaves the owner guessing; the
 * epic context requires naming what is wrong *and* what to do about it. Keeping
 * the known cases in an enum stops each screen from inventing a third wording
 * that says less, and keeps the wording reviewable in one place.
 *
 * A screen with a rule this enum does not cover takes the [KnField] `error:
 * String?` parameter directly — the point is not to forbid other messages, only
 * to make the common ones consistent.
 */
enum class KnFieldError {
    /** A required field left blank. */
    Required,

    /** The value is not a number where one is required. */
    NotANumber,
}

/**
 * The Indonesian copy for [KnFieldError].
 *
 * Read through `stringResource` at the call site so the strings stay in
 * `res/values/strings.xml` rather than here. Public because the mapping is part
 * of the enum's contract: it is what makes a blank field and a typo
 * distinguishable to a caller deciding which one to raise, and to a test
 * asserting the two do not collapse into the same wording.
 */
@get:Composable
val KnFieldError.messageRes: Int
    get() = when (this) {
        KnFieldError.Required -> R.string.field_required_error
        KnFieldError.NotANumber -> R.string.field_invalid_number_error
    }

/**
 * `.field` from `shared.css` section 8.
 *
 * ## Error text is hidden until validation runs
 *
 * The CSS has `.field-error { display: none }` and only `.show` reveals it. This
 * mirrors that with [error]: `null` means "validation has not run" and nothing
 * is drawn. The component never decides *when* to validate — that is the
 * caller's state, not presentation — which is also why the error does not
 * appear mid-typing unless the caller chooses to.
 *
 * An error message must name both the fault and the fix. [KnFieldError] holds
 * the two known wordings so screens cannot invent a third that says only
 * "invalid"; a screen with its own rule passes the text directly.
 */
@Composable
fun KnField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    hint: String? = null,
    /** `null` until validation has run. Nothing renders while it is null. */
    error: String? = null,
    /** A static affix such as `Rp`, rendered inside the field on the leading side. */
    leading: String? = null,
    trailingIcon: ImageVector? = null,
    /**
     * Spoken label for [trailingIcon] when it is also the button.
     *
     * Required whenever the icon is clickable: a glyph-only button with no
     * description reaches a screen reader as an unlabelled "Button".
     */
    trailingContentDescription: String? = null,
    onTrailingIconClick: (() -> Unit)? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    /**
     * Invoked by the keyboard's action key.
     *
     * `null` leaves `imeAction` at `Default`, so the field does not advertise a
     * submission it cannot perform.
     */
    onSubmit: (() -> Unit)? = null,
) {
    val kn = MaterialTheme.knColors
    val spacing = MaterialTheme.knSpacing
    // A blank string is treated as "no error": a caller defaulting to "" would
    // otherwise render an empty error row with its icon.
    val hasError = !error.isNullOrBlank()
    val shape = RoundedCornerShape(MaterialTheme.knRadii.md)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Column(modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.knTypography.label,
            color = kn.textSecondary,
        )

        val fieldModifier = Modifier
            .padding(top = 7.dp)
            .fillMaxWidth()
            .defaultMinSize(minHeight = MaterialTheme.minTouchTarget)
            .background(if (hasError) kn.semanticBg.danger else kn.bgCard, shape)
            .border(
                width = 1.5.dp,
                color = when {
                    hasError -> kn.semantic.danger
                    focused -> MaterialTheme.knColors.primaryFill
                    else -> kn.borderStrong
                },
                shape = shape,
            )
            .focusRing(
                focused = focused,
                cornerRadius = MaterialTheme.knRadii.md,
            )
            .padding(horizontal = spacing.lg, vertical = spacing.md)

        Row(
            modifier = fieldModifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            if (leading != null) {
                Text(
                    text = leading,
                    style = MaterialTheme.knTypography.label,
                    color = kn.textTertiary,
                )
            }
            Box(
                    Modifier
                        .weight(1f),
                ) {
                if (value.isEmpty() && placeholder != null) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.knTypography.body,
                        // The CSS placeholder is --warm-400, which is 2.58:1 on
                        // card. Placeholder copy is still read, so it uses the
                        // tertiary level, which clears 4.5:1 on every surface.
                        color = kn.textTertiary,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = singleLine,
                    textStyle = MaterialTheme.knTypography.body.copy(color = kn.textPrimary),
                    cursorBrush = SolidColor(MaterialTheme.knColors.primaryFill),
                    interactionSource = interaction,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = keyboardType,
                        // The IME action follows the field's keyboard type: a
                        // numeric field wants Done, a text field wants the
                        // caller to say what the key means.
                        imeAction = if (onSubmit != null) {
                            if (keyboardType == KeyboardType.Number ||
                                keyboardType == KeyboardType.Decimal ||
                                keyboardType == KeyboardType.Phone
                            ) {
                                ImeAction.Done
                            } else {
                                ImeAction.Search
                            }
                        } else {
                            ImeAction.Default
                        },
                    ),
                    // Without this the IME's action key is inert: the owner taps
                    // "search" on the keyboard and nothing happens, which reads
                    // as a broken field rather than as a missing handler.
                    keyboardActions = KeyboardActions(
                        onSearch = { onSubmit?.invoke() },
                        onDone = { onSubmit?.invoke() },
                    ),
                    // The floor goes on the input itself rather than on the Box
                    // that wraps it: the Box sizes to the text, and the
                    // text field inside it sizes to the Box, so a floor on
                    // either wrapper leaves the tappable node at ~19dp.
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = MaterialTheme.minTouchTarget),
                )
            }
            if (trailingIcon != null && onTrailingIconClick != null) {
                KnIconButton(
                    icon = trailingIcon,
                    contentDescription = trailingContentDescription,
                    onClick = onTrailingIconClick,
                )
            } else if (trailingIcon != null) {
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    tint = kn.textTertiary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        if (hasError) {
            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Lucide.TriangleAlert,
                    contentDescription = null,
                    tint = kn.errorText,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = error.orEmpty(),
                    style = MaterialTheme.knTypography.body,
                    color = kn.errorText,
                )
            }
        } else if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.knTypography.body,
                color = kn.textTertiary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/**
 * An icon-only button inside a dense row.
 *
 * Exists because the story calls out "in-row icon button" by name: it is the
 * node most likely to be sized by its icon (20dp) rather than by the touch
 * floor, and the one a reviewer skims past. [minTouchTarget] is applied here so
 * the measured bound cannot drop.
 */
@Composable
fun KnIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color? = null,
) {
    val kn = MaterialTheme.knColors
    val resolved = tint ?: kn.textSecondary
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .minTouchTarget()
            .focusRing(
                focused = focused,
                cornerRadius = MaterialTheme.knRadii.md,
            ),
        shape = RoundedCornerShape(MaterialTheme.knRadii.md),
        color = Color.Transparent,
        contentColor = resolved,
        interactionSource = interaction,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = resolved,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
