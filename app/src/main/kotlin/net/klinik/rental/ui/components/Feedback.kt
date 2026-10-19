package net.klinik.rental.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.Delete
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.TriangleAlert
import com.composables.icons.lucide.X
import net.klinik.rental.R
import net.klinik.rental.ui.theme.focusRing
import net.klinik.rental.ui.theme.knColors
import net.klinik.rental.ui.theme.knShadow
import net.klinik.rental.ui.theme.knElevation
import net.klinik.rental.ui.theme.knMotion
import net.klinik.rental.ui.theme.knRadii
import net.klinik.rental.ui.theme.knSpacing
import net.klinik.rental.ui.theme.knTypography
import net.klinik.rental.ui.theme.minTouchTarget

/**
 * `.snackbar` from `shared.css` section 13.
 *
 * ## Why the tone does not recolour the surface
 *
 * The CSS varies the *glyph* per tone and keeps one dark surface for all of
 * them. That is the right call and this component keeps it: a snackbar that
 * turned green, amber and red would stop reading as one transient message and
 * start competing with the page behind it. The tone arrives as the icon, which
 * is the part a glance reads fastest.
 *
 * ## Why nothing here names a raw hex
 *
 * Text on a snackbar has to clear 4.5:1, and the CSS's `--warm-900` on white
 * does — but only because the dark theme *flips* the snackbar to a light
 * surface, where that same hex measures 1.1:1. `Color.kt` already carries the
 * corrected pair for both themes as `surfaceInverse`/`onSurfaceInverse`, and the
 * semantic foregrounds fail the same way on the flipped surface (a 3:1 icon
 * floor is not 2.5:1). So the glyph is `onSurfaceInverse` too: the *shape* of
 * the icon carries the tone, and the tone does not cost a contrast failure.
 *
 * The action and the dismiss button both clear the 48dp floor even though the
 * CSS gives the action 4px/8px of padding and no height at all. A snackbar is
 * often the only affordance a confirmation offers, and a 24dp-tall target
 * inside a moving surface is not a target.
 *
 * @param actionLabel brand-tinted in the CSS; here it is [KnColors.onSurfaceInverse]
 *   at bold weight, which is what actually clears the floor on both the dark and
 *   the flipped-light inverse surface.
 * @param onAction paired with [actionLabel] — a label with no handler would
 *   render a button that does nothing.
 */
@Composable
fun KnSnackbar(
    message: String,
    modifier: Modifier = Modifier,
    tone: KnBadgeTone = KnBadgeTone.Neutral,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val kn = MaterialTheme.knColors
    val spacing = MaterialTheme.knSpacing
    val dismissLabel = stringResource(R.string.snackbar_dismiss)

    Surface(
        modifier = modifier
            // `.snackbar { max-width: 88% }`, read as the stylesheet's own fraction.
            //
            // `widthIn(max = 560.dp)` was the previous reading of that line, and it
            // was wrong in a way only arithmetic finds: 560dp is 43.75% of a
            // 1280dp tablet, not 88%. The recorded reasoning for revisiting it (see
            // `SnackbarWidthFraction`) is that the number described a screen rather
            // than a rule.
            //
            // `SnackbarMaxWidth` is kept as an absolute ceiling *as well*, and the
            // two are combined rather than swapped: `fillMaxWidth` alone would make
            // this component's width depend on its caller, so a bar composed inside
            // an already-narrow column (`ContractFormSheet` is 620dp of modal)
            // would be as wide as that column instead of 88% of the *screen*. The
            // smaller of the two is what ships: the fraction where it binds, and the
            // cap where a narrow parent would otherwise let it run long.
            .fillMaxWidth(SnackbarWidthFraction)
            .widthIn(max = SnackbarMaxWidth)
            .minTouchTarget()
            .knShadow(MaterialTheme.knElevation.level3, RoundedCornerShape(MaterialTheme.knRadii.md)),
        shape = RoundedCornerShape(MaterialTheme.knRadii.md),
        color = kn.surfaceInverse,
        contentColor = kn.onSurfaceInverse,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.xl, vertical = spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Icon(
                imageVector = snackbarGlyph(tone),
                contentDescription = null,
                tint = kn.onSurfaceInverse,
                modifier = Modifier.size(SnackbarIconSize),
            )
            Text(
                text = message,
                style = MaterialTheme.knTypography.body,
                color = kn.onSurfaceInverse,
                modifier = Modifier.weight(1f),
            )
            if (actionLabel != null && onAction != null) {
                KnSnackbarAction(label = actionLabel, onClick = onAction)
            }
            if (onDismiss != null) {
                KnSnackbarDismiss(label = dismissLabel, onClick = onDismiss)
            }
        }
    }
}

/**
 * `.snackbar .sn-action`.
 *
 * A text button rather than a full [KnButton]: a filled primary button inside a
 * snackbar would read as a second call to action for the page behind it, which
 * is the opposite of what a confirmation wants.
 */
@Composable
private fun KnSnackbarAction(label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(MaterialTheme.knRadii.sm)
    Surface(
        onClick = onClick,
        modifier = Modifier
            .minTouchTarget()
            .focusRing(focused, MaterialTheme.knRadii.sm),
        shape = shape,
        color = Color.Transparent,
        contentColor = MaterialTheme.knColors.onSurfaceInverse,
        interactionSource = interaction,
    ) {
        Text(
            text = label,
            // `font-weight: 800` in the CSS — the weight is what distinguishes
            // the action from the message, since the two share a colour here.
            style = MaterialTheme.knTypography.label.copy(fontWeight = FontWeight.ExtraBold),
            maxLines = 1,
            modifier = Modifier.padding(horizontal = MaterialTheme.knSpacing.sm),
        )
    }
}

/** The trailing close affordance: 48dp square, reachable while the bar moves. */
@Composable
private fun KnSnackbarDismiss(label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(MaterialTheme.knRadii.sm)
    Surface(
        onClick = onClick,
        modifier = Modifier
            .minTouchTarget()
            .focusRing(focused, MaterialTheme.knRadii.sm),
        shape = shape,
        color = Color.Transparent,
        // The full inverse ink, not a reduced one. `copy(alpha = …)` yields an
        // effective colour that no token names and no contrast assertion
        // measures — the same special-casing `KnChip`'s count avoids. The
        // dismiss affordance reads as secondary because it is a bare glyph with
        // no filled button behind it, not because it is a lighter shade of the
        // text it sits inside.
        contentColor = MaterialTheme.knColors.onSurfaceInverse,
        interactionSource = interaction,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Lucide.X,
                contentDescription = label,
                modifier = Modifier.size(SnackbarIconSize),
            )
        }
    }
}

/**
 * The glyph for a tone.
 *
 * Success and Brand share `Check`: they differ only in context, and a success
 * mark that meant "brand" would be a different icon for the same news.
 */
@Composable
private fun snackbarGlyph(tone: KnBadgeTone): ImageVector = when (tone) {
    KnBadgeTone.Success, KnBadgeTone.Brand -> Lucide.Check
    KnBadgeTone.Warning -> Lucide.TriangleAlert
    KnBadgeTone.Danger -> Lucide.CircleAlert
    KnBadgeTone.Info, KnBadgeTone.Neutral -> Lucide.Info
}

/**
 * `.empty-state` from `shared.css` section 14.
 *
 * [action] is a slot rather than a `(String, () -> Unit)` pair: an empty state
 * usually wants a primary [KnButton], but sometimes a text link, and a component
 * that only accepted buttons would force the second case to fake one.
 *
 * @param iconTileColor the tile behind [icon]. `null` is the neutral
 *   `brandSurface`; [KnScreenError] passes the danger companion instead, because
 *   the two states must not be told apart by a hue that reads the same in both
 *   themes.
 * @param iconTint the glyph's own ink. `null` is [net.klinik.rental.ui.theme.KnColors.onBrand],
 *   measured against `brandSurface`; a caller passing [iconTileColor] owns the
 *   contrast against its own tile.
 */
@Composable
fun KnEmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTileColor: Color? = null,
    iconTint: Color? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val kn = MaterialTheme.knColors
    val spacing = MaterialTheme.knSpacing

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.xl, vertical = spacing.giant),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.md, Alignment.CenterVertically),
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    // `.empty-state .es-icon { margin-bottom: var(--sp-2) }`
                    .padding(bottom = spacing.sm)
                    .size(EmptyStateTileSize)
                    .background(
                        iconTileColor ?: kn.brandSurface,
                        RoundedCornerShape(MaterialTheme.knRadii.xl),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    // The CSS paints `--brand-400` here, which measures 1.5:1 on
                    // `--brand-50`. An icon is not text so the 4.5:1 floor does
                    // not bind, but a glyph nobody can resolve is not a cue
                    // either, so the token that is measured on this surface wins.
                    tint = iconTint ?: kn.onBrand,
                    modifier = Modifier.size(EmptyStateIconSize),
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.knTypography.title,
            color = kn.textPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = MaterialTheme.knTypography.body,
            color = kn.textTertiary,
            // `.empty-state p { max-width: 340px }` — the cap is what keeps a
            // two-line explanation from stretching the full width of a 1280px
            // tablet into an unreadable ribbon.
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = EmptyStateBodyMaxWidth),
        )
        if (action != null) {
            Box(Modifier.padding(top = spacing.sm)) { action() }
        }
    }
}

/**
 * One placeholder block: a static `bgSunken` fill.
 *
 * ## Why a static block, and not a pulsing one
 *
 * Two independent constraints forbid the obvious implementation. `KnMotion`'s
 * KDoc states "Nothing in this file loops, bounces or overshoots", and
 * `EXPERIENCE.md:273` makes it a rule — no repeating motion, because the app is
 * open all day and a loop that charms on day one tires on day two. A skeleton
 * pulse would break both, and `tween()` would also refuse it: it accepts only
 * 140/220/300ms and has no looping primitive at all. A static placeholder block
 * is therefore the whole component, and `Color.kt`'s own comment on
 * `KnTableHeaderRow` already names `bgSunken` as the surface that says
 * "not selectable" — a placeholder is the same claim.
 *
 * `neutralMarker` (`#A8A096`) is deliberately not used: it is byte-identical in
 * both themes and too dark against dark `bgCard`, so a block filled with it
 * would be invisible in one theme and legible in the other.
 *
 * ## Why it carries no semantics at all
 *
 * A placeholder says nothing. Announcing "loading" on every block would make
 * TalkBack read the same word several times over a list that is about to be
 * replaced, and it would imply a duration the owner cannot act on.
 * [KnSkeletonList] is where the decorative marking lives, because that is the
 * node a caller reaches for.
 *
 * @param height the block's height. Passed rather than derived so a caller can
 *   mirror the shape of whatever it is standing in for — a list row, a card, a
 *   title bar.
 */
@Composable
fun KnSkeleton(
    modifier: Modifier = Modifier,
    height: Dp,
    shape: Shape = RoundedCornerShape(MaterialTheme.knRadii.md),
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(MaterialTheme.knColors.bgSunken, shape),
    )
}

/**
 * A column of [KnSkeleton] blocks shaped like list rows.
 *
 * ## Why the row height mirrors `KnListRow`
 *
 * A skeleton whose blocks are a different height from the rows that replace them
 * makes the list jump when the data lands, which reads as a *second* change
 * after the load and is exactly the "loading lasted too long" feeling the AC
 * forbids. [SkeletonRowHeight] is the height a titled two-line row lays out at,
 * and SkeletonTest measures the two against each other.
 *
 * ## Why the whole group is marked decorative
 *
 * A semantics node with no content description is still a node a screen reader
 * can land on. `hideFromAccessibility()` removes the group and its children from
 * the accessibility tree outright, so a loading fleet is silent rather than a
 * list of unreadable empty boxes.
 *
 * @param rows how many placeholder rows to draw. Defaults to [SkeletonRows], which is the
 *   count `FourStatesTest` asserts for every screen at once — a screen that passed its own
 *   number would be the one the cross-cutting contract could not cover. `0` draws nothing at
 *   all, so a caller can pass a count it computed without branching on an empty `Column`.
 */
@Composable
fun KnSkeletonList(
    rows: Int = SkeletonRows,
    modifier: Modifier = Modifier,
) {
    if (rows <= 0) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { hideFromAccessibility() },
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.knSpacing.sm),
    ) {
        repeat(rows) {
            KnSkeleton(
                height = SkeletonRowHeight,
                modifier = Modifier.testTag(SkeletonRowTag),
            )
        }
    }
}

/**
 * The fatal-read-failure state: the app could not open its own data.
 *
 * ## Why this is not [KnEmptyState] with different words
 *
 * The epic context is explicit that a failure "never resets to defaults, because
 * data that disappears and looks like a fresh account is worse than a crash", and
 * the story's acceptance criterion says the same: an error state may never look
 * like a new account. So the layout is reused — same column, same body cap, same
 * action slot — but the tile is the **danger** companion rather than the neutral
 * `brandSurface`, and the glyph is [net.klinik.rental.ui.theme.KnColors.dangerFill].
 *
 * The tone is the whole point. Two states that differ only in their sentence are
 * two states an owner under time pressure cannot tell apart at a glance, and the
 * mistake they would make from the wrong one is adding a fleet they already have.
 *
 * Reusing [KnEmptyState] rather than forking it is deliberate: a forked copy is a
 * second layout to keep aligned, and this state has the same shape.
 *
 * @param actionLabel the way forward, or `null` when there is nothing the owner
 *   can do from here. A label with no [onAction] would render a button that does
 *   nothing, so the two travel together.
 */
@Composable
fun KnScreenError(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val kn = MaterialTheme.knColors
    KnEmptyState(
        title = title,
        body = body,
        icon = Lucide.CircleAlert,
        // `dangerFill` on the danger companion. `errorText` is the text token and
        // is shifted per theme for a 4.5:1 text floor; a glyph needs 3:1, which
        // `dangerFill` clears on `semanticBg.danger` in both themes.
        iconTileColor = kn.semanticBg.danger,
        iconTint = kn.dangerFill,
        modifier = modifier,
        action = if (actionLabel != null && onAction != null) {
            {
                KnButton(
                    label = actionLabel,
                    onClick = onAction,
                    modifier = Modifier.testTag(ScreenErrorActionTag),
                )
            }
        } else {
            null
        },
    )
}

/**
 * `.modal` from `shared.css` section 12.
 *
 * A `Dialog` rather than a `Box` over a scrim: the platform dialog moves with
 * the window and gets the back gesture, so the owner cannot be left looking at a
 * dialog that belongs to another app. [onDismissRequest] is wired to both the
 * back gesture and the scrim tap — a destructive dialog that ignores it has no
 * way out except its own buttons.
 *
 * ## Why there is no bounce
 *
 * The CSS opens with `scale(.96) → scale(1)` on `--t-base`, and that is exactly
 * what happens: a fade plus a 4% scale, on the 220ms token, through
 * [MaterialTheme.knMotion] — which only accepts the three documented durations
 * and one monotone easing. A spring here would be a second motion language in a
 * system that has already declared it has one.
 *
 * ## Why the body scrolls and the footer does not
 *
 * `.modal-body { overflow-y: auto; flex: 1 }` with `flex: 0 0 auto` on the
 * header and footer. So does this: the buttons have to stay reachable when the
 * content is taller than the dialog, and that is the case that matters — a
 * modal whose confirm button scrolls out of view is a modal that gets dismissed
 * by habit instead of by decision.
 *
 * @param confirmLabel / [dismissLabel] render at most two buttons, right
 *   aligned. Two is a ceiling, not a suggestion: a footer that grows turns a
 *   decision into a menu.
 * @param confirmEnabled whether the confirm button accepts presses. `true` for
 *   every dialog whose action needs nothing but the dialog itself, which is all
 *   of them until Story 3.4's cancellation — a mandatory reason inside the body
 *   has to be able to hold its own confirm shut, and [KnModal] rather than its
 *   caller is the only thing that owns that button.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun KnModal(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    confirmLabel: String? = null,
    onConfirm: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    dismissLabel: String? = null,
    onDismiss: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val kn = MaterialTheme.knColors
    val spacing = MaterialTheme.knSpacing
    val radii = MaterialTheme.knRadii
    val motion = MaterialTheme.knMotion
    val closeLabel = stringResource(R.string.action_close)

    // Unmounted rather than emptied when hidden. A Dialog whose content is gone
    // but whose window is still up is an invisible full-screen tap blocker, so
    // the exit transition is the caller's to stage — the platform dialog's own
    // dismiss animation, not a second one layered on top.
    if (!visible) return

    Dialog(
        onDismissRequest = onDismissRequest,
        // The CSS sizes the modal itself (`width: min(620px, …)`), so the
        // platform's screen-width default would fight the token.
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(motion.tween(motion.baseMillis)) +
                scaleIn(
                    initialScale = ModalEnterScale,
                    animationSpec = motion.tween(motion.baseMillis),
                ),
        ) {
            Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .widthIn(max = ModalMaxWidth)
                    .heightIn(max = ModalMaxHeight)
                    .knShadow(MaterialTheme.knElevation.level3, RoundedCornerShape(radii.xl)),
                shape = RoundedCornerShape(radii.xl),
                color = kn.bgCard,
                contentColor = kn.textPrimary,
            ) {
                Column {
                    if (title != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = spacing.xl,
                                    end = spacing.sm,
                                    top = spacing.lg,
                                    bottom = spacing.lg,
                                ),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.knTypography.title,
                                color = kn.textPrimary,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(top = spacing.md),
                            )
                            KnIconButton(
                                icon = Lucide.X,
                                contentDescription = closeLabel,
                                onClick = onDismissRequest,
                                tint = kn.textSecondary,
                            )
                        }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(kn.border),
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(spacing.xl),
                    ) {
                        content()
                    }

                    if ((confirmLabel != null && onConfirm != null) ||
                        (dismissLabel != null && onDismiss != null)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(kn.border),
                        )
                        // A wrapping footer, not a fixed Row. The confirm and
                        // dismiss labels are caller-supplied Indonesian, so a
                        // narrow dialog would otherwise squeeze whichever
                        // button sits at the trailing edge below the 48dp
                        // floor rather than wrapping — measured at 46dp on a
                        // 320dp-wide dialog. Wrapping keeps every button whole.
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = spacing.xl, vertical = spacing.lg),
                            horizontalArrangement = Arrangement.End,
                            verticalArrangement = Arrangement.spacedBy(spacing.sm),
                            maxItemsInEachRow = 2,
                        ) {
                            if (dismissLabel != null && onDismiss != null) {
                                KnButton(
                                    label = dismissLabel,
                                    onClick = onDismiss,
                                    variant = KnButtonVariant.Ghost,
                                )
                            }
                            if (confirmLabel != null && onConfirm != null) {
                                KnButton(
                                    label = confirmLabel,
                                    onClick = onConfirm,
                                    variant = KnButtonVariant.Primary,
                                    enabled = confirmEnabled,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * `.pin-dots` + `.pin-grid` from `shared.css` section 15.
 *
 * ## The 420ms shake is deliberately absent
 *
 * `.pin-error` runs `animation: shake 420ms` and the acceptance criteria name
 * it, but it is the only motion outside `KnMotion`'s scale and it fires from the
 * PIN entry *flow* — Story 5.3. Adding the duration here would put the token in
 * the layout component instead of in the flow that owns it, and a constant
 * available to everyone is a constant that ends up somewhere it should not be.
 * This is layout and input only.
 *
 * ## Why the dots are not interactive
 *
 * The dots report progress; the grid is the input. A tappable dot would suggest
 * you can jump to a position, which is not what PIN entry does.
 *
 * @param enteredDigits how many of [totalDigits] are filled. Passed in rather
 *   than derived from the digits: the grid never holds the PIN, so the screen
 *   owns it and nothing here can leak it into a saved state.
 * @param totalDigits the PIN length. Six is what the flow assumes, but the
 *   caller states it because the length is a policy of the screen, not a
 *   property of the keypad.
 */
@Composable
fun KnPinGrid(
    enteredDigits: Int,
    totalDigits: Int,
    modifier: Modifier = Modifier,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
) {
    val kn = MaterialTheme.knColors
    val spacing = MaterialTheme.knSpacing
    val deleteLabel = stringResource(R.string.pin_key_delete)
    val filledLabel = stringResource(R.string.pin_dot_filled)
    val emptyLabel = stringResource(R.string.pin_dot_empty)
    val full = CircleShapeToken

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.xl),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            repeat(totalDigits) { index ->
                val filled = index < enteredDigits
                Box(
                    modifier = Modifier
                        .size(PinDotSize)
                        .background(
                            color = if (filled) kn.primaryFill else Color.Transparent,
                            shape = full,
                        )
                        // `.pin-dot.filled` recolours the border too, so an
                        // unfilled dot keeps its 2px `--border-strong` ring and a
                        // filled one has no visible edge.
                        .border(
                            width = 2.dp,
                            color = if (filled) kn.primaryFill else kn.borderStrong,
                            shape = full,
                        )
                        .semantics { contentDescription = if (filled) filledLabel else emptyLabel },
                )
            }
        }

        // `grid-template-columns: repeat(3, 84px); gap: var(--sp-3)`. Rows of
        // Columns rather than a lazy grid: twelve keys, all always measured, and
        // a lazy grid would nest three scroll containers inside the dialog for
        // no benefit.
        Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            PinDigitRow("1", "2", "3", onDigit)
            PinDigitRow("4", "5", "6", onDigit)
            PinDigitRow("7", "8", "9", onDigit)
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                // The CSS leaves the leading cell of the last row empty. It is a
                // spacer, not a key: putting a digit there would break the 1-9
                // column the hand has just traced three rows up.
                Box(Modifier.size(PinKeySize))
                KnPinKey(label = "0", onClick = { onDigit('0') })
                KnPinDeleteKey(label = deleteLabel, onClick = onDelete)
            }
        }
    }
}

/** One row of three digit keys. */
@Composable
private fun PinDigitRow(
    first: String,
    second: String,
    third: String,
    onDigit: (Char) -> Unit,
) {
    val spacing = MaterialTheme.knSpacing
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
        listOf(first, second, third).forEach { digit ->
            KnPinKey(label = digit, onClick = { onDigit(digit[0]) })
        }
    }
}

/**
 * `.pin-key` — 84dp disc, `--el-1`, [net.klinik.rental.ui.theme.KnTypography.pinKey].
 *
 * 84dp is far above the 48dp floor, so [minTouchTarget] is belt-and-braces. It is
 * applied anyway because the assertion is on *every* interactive node, and a key
 * that only holds because its CSS is generous is one constant change away from
 * failing.
 */
@Composable
private fun KnPinKey(label: String, onClick: () -> Unit) {
    val kn = MaterialTheme.knColors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(PinKeySize)
            .minTouchTarget()
            .focusRing(focused, MaterialTheme.knRadii.full)
            .knShadow(MaterialTheme.knElevation.level1, CircleShapeToken),
        shape = CircleShapeToken,
        color = kn.bgCard,
        contentColor = kn.textPrimary,
        interactionSource = interaction,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.knTypography.pinKey,
                color = kn.textPrimary,
                maxLines = 1,
            )
        }
    }
}

/** The backspace key: a glyph, so it carries its own spoken description. */
@Composable
private fun KnPinDeleteKey(label: String, onClick: () -> Unit) {
    val kn = MaterialTheme.knColors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(PinKeySize)
            .minTouchTarget()
            .focusRing(focused, MaterialTheme.knRadii.full),
        shape = CircleShapeToken,
        color = kn.bgCard,
        contentColor = kn.textSecondary,
        shadowElevation = MaterialTheme.knElevation.level1,
        interactionSource = interaction,
    ) {
        Box(
            modifier = Modifier.semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Lucide.Delete,
                contentDescription = null,
                modifier = Modifier.size(PinDeleteIconSize),
            )
        }
    }
}

/**
 * `--r-full` as a shape.
 *
 * `RoundedCornerShape(999.dp)` on an 84dp disc renders as a circle but carries
 * corner arithmetic into every layout pass; [CircleShape] is the same shape with
 * none of it.
 */
private val CircleShapeToken = CircleShape

/** `.snackbar svg { width: 20px }` */
private val SnackbarIconSize = 20.dp

/**
 * `.snackbar { max-width: 88% }`, as the stylesheet's own fraction.
 *
 * A fraction rather than a dp bound, because a dp value is a guess at one screen
 * and the guess is wrong by half on the 1280dp tablet this app ships on: 560dp is
 * 43.75% of it, not 88%. Story 2.8 is the story the earlier annotation deferred
 * to. See [SnackbarMaxWidth] for why the fraction is a ceiling rather than the
 * only bound.
 */
private const val SnackbarWidthFraction = 0.88f

/**
 * The absolute ceiling the fraction is combined with.
 *
 * Unchanged from before this story, and deliberately *not* removed with the
 * fraction: `fillMaxWidth` resolves against whatever the bar is composed in, so
 * without this a snackbar inside a 620dp modal would claim 88% of the modal
 * rather than of the screen. Kept so the parent's width still bounds the bar.
 */
private val SnackbarMaxWidth = 560.dp

/** `.pin-dot { width: 17px }` */
private val PinDotSize = 17.dp

/** `.pin-key { width: 84px }` */
private val PinKeySize = 84.dp

/** The backspace glyph at keypad proportion. */
private val PinDeleteIconSize = 28.dp

/** `.empty-state .es-icon { width: 92px }` */
private val EmptyStateTileSize = 92.dp

/** `.empty-state .es-icon svg { width: 44px }` */
private val EmptyStateIconSize = 44.dp

/** `.empty-state p { max-width: 340px }` */
private val EmptyStateBodyMaxWidth = 340.dp

/** `.modal { width: min(620px, calc(100% - 48px)) }` */
private val ModalMaxWidth = 620.dp

/**
 * `.modal { max-height: 84% }`.
 *
 * A fixed bound rather than a fraction because `Dialog` reports its own window
 * constraints in dp, not as a percentage of the screen; 640dp is roughly 84% of
 * the 760dp-tall portrait tablet this ships on, and the scrollable body absorbs
 * the difference either way.
 */
private val ModalMaxHeight = 640.dp

/** `.modal { transform: scale(.96) }` */
private const val ModalEnterScale = 0.96f

/**
 * The block height of the list row it stands in for: 104dp.
 *
 * ## This number is calibrated, not derived
 *
 * It was originally written as `48dp + 2x16dp = 76dp` on the reasoning that a
 * `KnListRow` is its 48dp touch floor plus its vertical padding. That arithmetic
 * is wrong twice over: `defaultMinSize` is a floor rather than an addition, and
 * the row's own content column — the taller of the two text lines' stack and the
 * 44dp leading box, with whatever the platform's own padding around the text
 * contributes — comes to more than the floor. The figure that ships is the one
 * Robolectric actually measures for a titled two-line row, not the one the
 * arithmetic produced.
 *
 * It is worth being explicit that two hand-derivations were tried and both were
 * wrong, which is why the number is not presented as derived here. A skeleton
 * whose blocks are a different height from the rows that replace them makes the
 * list jump when the data lands, and that jump reads as a *second* change after
 * the load — the exact "loading lasted too long" feeling the acceptance criterion
 * forbids.
 *
 * `SkeletonTest.a placeholder row is exactly as tall as the row it replaces`
 * lays a real `KnListRow` and a placeholder side by side and asserts the heights
 * are equal. That test is what keeps this constant honest: a change to the row's
 * padding, its leading box or its typography fails there, naming both heights,
 * rather than shipping as a list that visibly moves when data arrives.
 */
private val SkeletonRowHeight = 104.dp

/**
 * Tag on each placeholder row, so `rows = 3` is countable without reading the
 * layout's pixel geometry.
 *
 * `testTag` rather than a `contentDescription`: a tag is a test affordance and a
 * description is a promise to a screen reader. A skeleton promises nothing, and
 * giving it a description would be the exact failure the no-spinner rule is
 * guarding against in a different costume.
 */
internal const val SkeletonRowTag = "skeleton-row"

/**
 * How tall a sheet's content may grow before it scrolls — **one figure for every sheet**.
 *
 * ## Why this is one constant and not a copy per sheet
 *
 * Because four sheets declared their own `900.dp`, and three of the four KDocs admitted the bound
 * was *not* load-bearing for their content ("one field plus two buttons"). Four copies of a figure
 * that none of them justified is four places to change the day a tall sheet really does need a
 * different bound — and the sheet that needs it can pass its own [Modifier.heightIn] instead.
 *
 * The figure itself is a ceiling, not a decision about any one sheet: `ModalBottomSheet` already
 * constrains itself to the window, and this only stops a very tall sheet on a very tall tablet from
 * pushing its confirm button off the top.
 */
internal val SheetMaxHeight = 900.dp

/**
 * How many skeleton rows a loading list draws — **one number for the whole app**.
 *
 * ## Why this is a shared constant rather than a per-screen choice
 *
 * Because a skeleton's job is to occupy the space the content will, and the *count* is a claim
 * about how tall a list is, not about what is in it. Six screens each declaring their own copy is
 * how `TunggakanScreen` ended up drawing five while `FourStatesTest` asserted six for every other
 * screen — a discrepancy that only showed up because that screen had no entry in the test.
 *
 * `PengaturanScreen` argued this exact point in a KDoc above its own private copy. The copy is gone;
 * the argument now lives here, where the value does.
 *
 * The default belongs on [KnSkeletonList] too, so most call sites need not mention it at all.
 */
internal const val SkeletonRows = 6

/** Tag on [KnScreenError]'s action, the component's only interactive node. */
internal const val ScreenErrorActionTag = "screen-error-action"
