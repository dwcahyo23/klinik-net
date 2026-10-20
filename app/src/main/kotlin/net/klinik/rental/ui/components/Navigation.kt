package net.klinik.rental.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Moon
import com.composables.icons.lucide.Sun
import net.klinik.rental.R
import net.klinik.rental.ui.theme.LocalThemeControl
import net.klinik.rental.ui.theme.ThemeControl
import net.klinik.rental.ui.theme.focusRing
import net.klinik.rental.ui.theme.knColors
import net.klinik.rental.ui.theme.knRadii
import net.klinik.rental.ui.theme.knSpacing
import net.klinik.rental.ui.theme.knTypography
import net.klinik.rental.ui.theme.minTouchTarget

/**
 * One destination in a [KnNavRail] or [KnBottomNav].
 *
 * [badgeCount] is a raw count, not a string: the component decides whether to
 * render a badge at all (`0` and `null` both mean "nothing outstanding") and
 * what the badge looks like. Formatting is not a rail's job — a caller that
 * wants "9+" owns that decision.
 */
data class KnNavItem(
    val label: String,
    val icon: ImageVector,
    val badgeCount: Int? = null,
)

/**
 * `.nav-rail` from `shared.css` section 4 — the landscape navigation.
 *
 * ## Why the rail owns its own width
 *
 * `--rail-w` (88dp) is a layout constant of the shell, not something each screen
 * gets to re-pick. It is fixed here as [KnNavRailWidth] so the rail and the page
 * body cannot disagree about how much horizontal space is left, which is the
 * failure that shows up as a horizontal scrollbar on the busiest screen.
 *
 * ## Why the accent bar is a Box and not a border
 *
 * `.rail-item.active::before` is a 4dp x 26dp bar sitting 8dp outside the item's
 * left edge — outside the rounded rect, so it stays square against the rail edge
 * while the item's own corners stay rounded. A `border()` cannot express that:
 * it rounds with the shape and hugs the content. Drawing it as a sibling in a
 * Row lets it overhang, which is what the CSS does.
 *
 * Every item is wrapped in [minTouchTarget]. The CSS `.rail-item` sets
 * `min-height: 60px`, already above the floor, but the floor is applied here
 * anyway: the assertion is that *no* interactive node measures under 48dp, and a
 * component that only holds because its CSS happened to be generous is one edit
 * away from failing.
 *
 * Presentation only — a rail draws destinations and reports taps. It does not
 * decide which is reachable, or what a badge means.
 *
 * @param compact the prototype's narrow rail: `--rail-w-compact` (72dp) with the
 *   item labels hidden (`shared.css:1030-1032`). It narrows the rail and removes the
 *   *label*; it never shrinks a touch target — see [KnNavRailItem].
 * @param logoSlot the brand mark, drawn in a 52dp band at the top of the rail.
 *   A slot rather than a fixed logo because the mark is an asset Story 2.x
 *   supplies; until then the caller passes nothing and the band stays empty.
 */
@Composable
fun KnNavRail(
    items: List<KnNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    logoSlot: (@Composable () -> Unit)? = null,
) {
    val kn = MaterialTheme.knColors
    val spacing = MaterialTheme.knSpacing
    val railWidth = if (compact) KnNavRailWidthCompact else KnNavRailWidth

    Column(
        modifier = modifier
            .width(railWidth)
            .fillMaxHeight()
            .background(kn.bgRail)
            .padding(horizontal = spacing.sm, vertical = spacing.lg),
    ) {
        // `.rail-logo { height: 52px; margin-bottom: var(--sp-4) }`
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            contentAlignment = Alignment.Center,
        ) {
            logoSlot?.invoke()
        }
        Spacer(Modifier.height(spacing.lg))

        // `.nav-rail { gap: var(--sp-1); overflow-y: auto }`. Scrolling matters
        // here: a rail with a badge on every item can outgrow a short tablet in
        // landscape, and a clipped destination is unreachable rather than merely
        // hidden.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                // `.nav-rail { gap: var(--sp-1); overflow-y: auto }`.
                //
                // The scroll is **load-bearing, not decoration**, and it was missing until
                // Story 4.1 added a fifth destination: with four, the items fit a landscape
                // tablet and nothing was lost. The `weight(1f)` above hands this Column a
                // *bounded* height, so five items no longer fit — and a child measured inside
                // a bounded column is compressed below its own `minTouchTarget()`, which is
                // how `AccessibilityTest` caught `Tunggakan` measuring 38dp instead of 48dp.
                //
                // Scrolling fixes it the way the CSS does: the rail grows past the viewport
                // and the owner scrolls to a destination that would otherwise be squashed
                // into an untappable strip — or, without this, clipped and unreachable.
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            items.forEachIndexed { index, item ->
                // Keyed on the destination, not the position. An unkeyed
                // `remember` inside a loop is keyed by slot, so inserting or
                // removing a destination migrates the focus state onto the wrong
                // item and the ring follows the index rather than the control the
                // user actually focused.
                key(item.label) {
                    KnNavRailItem(
                        item = item,
                        selected = index == selectedIndex,
                        compact = compact,
                        onClick = { onSelect(index) },
                    )
                }
            }
        }
    }
}

/**
 * A single `.rail-item`.
 *
 * Separate from [KnNavRail] because the accent bar has to sit *outside* the
 * item's rounded surface: the rail lays out a Row of accent + item, and the
 * accent is drawn only when the item is selected.
 *
 * No explicit `contentDescription` here — the label `Text` is the destination's
 * name, and setting one on the surface as well would make a screen reader say it
 * twice.
 *
 * @param compact hides the label, per `.device-frame.portrait .rail-item span
 *   { display: none }`. The label is *not* rendered rather than made transparent or
 *   zero-height: a transparent one still occupies the row and still gets read out,
 *   which is the opposite of what the rule is for.
 *
 * ## Why hiding the label does not shrink the target
 *
 * Because the floor is applied to the `Surface` by [minTouchTarget] and not derived from
 * the item's contents. With the label gone the icon alone is 24dp, and the surface
 * falls back to the 48dp minimum — so the compact rail is 72dp wide, not 48dp.
 * Shrinking the target to match the visible content is how the compact mode would have
 * become an accessibility regression: the whole point of `--rail-w-compact` is to save
 * horizontal space, and the 48dp floor is about vertical reach.
 *
 * ## Why the name survives the label
 *
 * Because a screen reader still has to announce the destination. The `Icon` is
 * `contentDescription = null` on the strength that "the label `Text` is the
 * destination's name" — which stops being true the moment the label is hidden. So in
 * compact mode the `Surface` carries the label itself as a merged
 * `contentDescription`, and the visible `Text` is not drawn. Non-compact keeps the
 * `Text` and takes no description, so the name is spoken exactly once either way.
 */
@Composable
private fun KnNavRailItem(
    item: KnNavItem,
    selected: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
) {
    val kn = MaterialTheme.knColors
    val spacing = MaterialTheme.knSpacing
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(MaterialTheme.knRadii.md)

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (selected) {
            // 4dp x 26dp, offset -8dp from the item edge. The parent rail's
            // horizontal padding is what this overdraws into.
            Box(
                Modifier
                    .offset(x = (-8).dp)
                    .size(width = 4.dp, height = 26.dp)
                    .clip(RoundedCornerShape(topEnd = MaterialTheme.knRadii.full, bottomEnd = MaterialTheme.knRadii.full))
                    .background(kn.primaryFill),
            )
        }
        Surface(
            onClick = onClick,
            modifier = Modifier
                .weight(1f)
                .minTouchTarget()
                .focusRing(focused, MaterialTheme.knRadii.md)
                .then(
                    // Only in compact, and only because the label it would otherwise
                    // duplicate is not drawn there — see this function's KDoc.
                    if (compact) {
                        Modifier.semantics(mergeDescendants = true) {
                            contentDescription = item.label
                        }
                    } else {
                        Modifier
                    },
                ),
            shape = shape,
            color = if (selected) kn.brandSurface else Color.Transparent,
            contentColor = if (selected) kn.onBrand else kn.textTertiary,
            interactionSource = interaction,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = spacing.xs, vertical = spacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    modifier = Modifier.size(RailIconSize),
                )
                if (!compact) {
                    Text(
                        text = item.label,
                        style = MaterialTheme.knTypography.overline.copy(fontSize = RailLabelSp, lineHeight = 14.sp),
                        color = if (selected) kn.onBrand else kn.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (item.badgeCount != null && item.badgeCount > 0) {
                    RailBadge(count = item.badgeCount)
                }
            }
        }
    }
}

/** `.rail-badge` — 20dp min-width pill on the brand fill. */
@Composable
private fun RailBadge(count: Int, modifier: Modifier = Modifier) {
    val kn = MaterialTheme.knColors
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
            .background(kn.primaryFill, RoundedCornerShape(MaterialTheme.knRadii.full))
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.knTypography.overline.copy(fontSize = 11.sp, lineHeight = 14.sp),
            color = Color.White,
            maxLines = 1,
        )
    }
}

/**
 * `.bottom-nav` — the portrait equivalent of the rail.
 *
 * The CSS hides it in landscape and this component does not try to detect
 * orientation: which shell variant a screen uses is the shell's decision, and a
 * composable that measured the window to pick between two layouts would put a
 * layout rule in two places. A screen picks one.
 *
 * Selection is carried by colour alone, not by an accent bar — there is no
 * leading edge to hang one on at the bottom of the screen, and a bar there would
 * fight the gesture inset.
 */
@Composable
fun KnBottomNav(
    items: List<KnNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val kn = MaterialTheme.knColors
    val spacing = MaterialTheme.knSpacing
    val shape = RoundedCornerShape(MaterialTheme.knRadii.md)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(kn.bgRail)
            .padding(horizontal = spacing.sm, vertical = spacing.xs),
    ) {
        // Eight destinations on a 360dp phone leave each tab a 45dp share, under the
        // 48dp floor — `weight` constrains a child in a `Row` whether it comes before or
        // after `minTouchTarget`, because the minimum is measured *inside* an already-fixed
        // share. Ordering the modifiers does not help; the Row's own width has to be able
        // to exceed the screen.
        //
        // So the row **scrolls**, exactly as the landscape rail does. Each destination
        // keeps its full 48dp and the owner slides the bar sideways to reach the last one,
        // which is a tab that is hard to reach rather than one that cannot be hit. A tab
        // squeezed to 44dp is a destination the owner stops using, and a hidden one is at
        // least visible.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            items.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                // Keyed on the destination for the same reason as the rail: an
                // unkeyed `remember` in a loop is keyed by slot, so adding or
                // removing a destination moves the focus ring onto a different
                // control.
                key(item.label) {
                    val interaction = remember { MutableInteractionSource() }
                    val focused by interaction.collectIsFocusedAsState()
                    Surface(
                    onClick = { onSelect(index) },
                    modifier = Modifier
                        // `width`, not `weight`: the floor is the floor, and a weighted
                        // share on eight items is below it.
                        .width(BOTTOM_NAV_ITEM_WIDTH)
                        .minTouchTarget()
                        .focusRing(focused, MaterialTheme.knRadii.md),
                    shape = shape,
                    color = if (selected) kn.brandSurface else Color.Transparent,
                    contentColor = if (selected) kn.onBrand else kn.textTertiary,
                    interactionSource = interaction,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = spacing.xs),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                modifier = Modifier.size(RailIconSize),
                            )
                            if (item.badgeCount != null && item.badgeCount > 0) {
                                RailBadge(
                                    count = item.badgeCount,
                                    modifier = Modifier.align(Alignment.TopEnd),
                                )
                            }
                        }
                        Text(
                            text = item.label,
                            style = MaterialTheme.knTypography.overline.copy(fontSize = RailLabelSp, lineHeight = 14.sp),
                            color = if (selected) kn.onBrand else kn.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    }
                }
            }
        }
    }
}

/**
 * `.top-bar-header` from `shared.css` section 4.
 *
 * ## Why the title is Fraunces
 *
 * `.page-title` sets `--font-display` at `--fs-lg`. That is the one place the
 * display face appears above the fold on every screen, so the shell commits to
 * it here rather than leaving each screen to remember.
 *
 * ## Why the nav icon is its own target
 *
 * Back is the gesture every owner makes first and under time pressure, and a
 * 24dp glyph on a 48dp transparent box is the hardest thing on the bar to hit.
 * The icon button therefore carries its own [minTouchTarget] rather than relying
 * on the bar's padding.
 *
 * [actions] is a [RowScope] slot so a caller can `weight(1f)` inside it without
 * passing a modifier to every button it drops in.
 *
 * ## The bar's right side, in the prototype's order
 *
 * `index.html:92-97` reads: the search field (capped at 280px), then the screen's own actions, then
 * the circular theme button. That order is reproduced here rather than left to callers, because
 * eight callers would reproduce it eight ways.
 *
 * ## Whose search field it is
 *
 * **The screen's**, whenever it passes one. `cars.html`, `renters.html` and `arrears.html` each
 * put *their own* field in their own bar, and the field is the only control that can widen a list
 * the global search narrowed — so a screen that drew no field would have a query it could not
 * clear.
 *
 * [LocalTopBarSearch] is the **fallback**: the shell provides the cross-app search, and a screen
 * that supplies its own replaces it. That is why the parameter comes first — a screen that has a
 * bar of its own must win over a shell default, and the shell default must not have to know that
 * any screen does.
 */
@Composable
fun KnTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigationIcon: ImageVector? = null,
    onNavigationClick: (() -> Unit)? = null,
    search: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {    val kn = MaterialTheme.knColors
    val spacing = MaterialTheme.knSpacing
    val shape = RoundedCornerShape(MaterialTheme.knRadii.md)
    val navDescription = stringResource(R.string.action_back)
    val themeControl = LocalThemeControl.current
    val searchSlot = search ?: LocalTopBarSearch.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(TopBarHeight),
        color = kn.bgRail,
        contentColor = kn.textPrimary,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // `.top-bar-header { padding: 0 var(--sp-6) }` — 24dp, the same gutter the
                    // page body uses (`shared.css:288`), so the title and the first card below it
                    // start on the same line.
                    .padding(horizontal = spacing.xxl),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                if (navigationIcon != null && onNavigationClick != null) {
                    val interaction = remember { MutableInteractionSource() }
                    val focused by interaction.collectIsFocusedAsState()
                    Surface(
                        onClick = onNavigationClick,
                        modifier = Modifier
                            .minTouchTarget()
                            .focusRing(focused, MaterialTheme.knRadii.md),
                        shape = shape,
                        color = Color.Transparent,
                        contentColor = kn.textSecondary,
                        interactionSource = interaction,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = navigationIcon,
                                contentDescription = navDescription,
                                modifier = Modifier.size(RailIconSize),
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.knTypography.title,
                        color = kn.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.knTypography.body,
                            color = kn.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (actions != null) {
                    // `.top-bar-actions { margin-left: auto; gap: var(--sp-2) }`
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                        content = actions,
                    )
                }

                if (searchSlot != null) {
                    searchSlot()
                }
                if (themeControl != null) {
                    ThemeToggleButton(control = themeControl)
                }
            }
            // `.top-bar-header { border-bottom: 1px solid var(--border) }`
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(kn.border),
            )
        }
    }
}

/** `.rail-item svg { width: 24px }`. Buttons in the bar measure 27dp — see [ThemeToggleButton]. */
private val RailIconSize = 24.dp

/**
 * The prototype's `.btn.btn-secondary.btn-icon` in the top bar — a 48dp pill.
 *
 * ## Why the glyph says what is *not* shown
 *
 * `dashboard.html:147` draws the **sun** while the theme is light and the **moon** while it is
 * dark, which reads as "here is the theme you are in". That is the opposite of what tapping it does,
 * and a button whose picture contradicts its effect is a button the owner has to learn. The glyph
 * here is the destination: a moon means "tap to go dark", a sun means "tap to go light". The
 * accessible name follows the same rule, and names it — a lone moon is not a name.
 *
 * A `Surface` rather than `KnIconButton` because the bar's copy is a card on `--bg-rail` with
 * `--el-1`, not the transparent in-row target `KnIconButton` draws.
 */
@Composable
private fun ThemeToggleButton(control: ThemeControl) {
    val kn = MaterialTheme.knColors
    val shape = RoundedCornerShape(MaterialTheme.knRadii.full)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Surface(
        onClick = control.onToggle,
        modifier = Modifier
            .minTouchTarget()
            .focusRing(focused, MaterialTheme.knRadii.md),
        shape = shape,
        color = kn.bgCard,
        contentColor = kn.textSecondary,
        shadowElevation = 1.dp,
        interactionSource = interaction,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (control.dark) Lucide.Sun else Lucide.Moon,
                contentDescription = stringResource(
                    if (control.dark) R.string.action_theme_to_light else R.string.action_theme_to_dark,
                ),
                modifier = Modifier
                    .size(RailIconSize)
                    .testTag(TAG_THEME_TOGGLE),
            )
        }
    }
}

/**
 * The shell's cross-app search, for whichever [KnTopBar] has none of its own.
 *
 * A **fallback**, not a source: `KendaraanScreen` and `PenyewaScreen` pass their own field into
 * the bar's `search` slot, because that field is the only control which can widen a list the
 * global search narrowed — a screen with only the shell's field would have a query it cannot
 * clear. `compositionLocalOf` (not static) because the shell's search state changes on every
 * keystroke and the field inside the slot must recompose with it.
 */
val LocalTopBarSearch = compositionLocalOf<(@Composable () -> Unit)?> { null }

/**
 * The theme button's tag.
 *
 * On the **icon**, not the `Surface`: a test addresses it with `performClick()` and Compose
 * dispatches that to the nearest clickable ancestor, which is the button itself. Putting it on the
 * `Surface` would work too — this is only where the measure lands.
 */
const val TAG_THEME_TOGGLE = "top-bar-theme-toggle"

/**
 * `.rail-item { font-size: 11px }`.
 *
 * Held at 11sp rather than the 14sp floor: a rail label is a redundant cue next
 * to a recognisable icon, not the only copy of the destination, and the CSS is
 * explicit about the size. The floor in `Type.kt` covers text the owner has to
 * read; this is navigation chrome read at a glance.
 */
private val RailLabelSp = 11.sp

/**
 * `--rail-w`
 *
 * The full rail, with labels. Reachable only when a caller passes `compact = false`,
 * which today the shell never does — it draws the bottom nav instead. It stays because
 * the CSS defines it and a screen that wants the wide rail on a large display should not
 * have to re-derive 88dp.
 */
private val KnNavRailWidth = 88.dp

/**
 * `--rail-w-compact`
 *
 * `shared.css:1030` — the narrow rail, and `shared.css:1031` hides the item labels with
 * it. Until this story the constant was dead: `AppShell` never passed `compact = true`, so
 * nothing in the app rendered at this width. The layout it describes was in the prototype
 * and in this file, and on no screen between them.
 */
private val KnNavRailWidthCompact = 72.dp

/**
 * One bottom-nav destination's width.
 *
 * ## Why a fixed width rather than a weighted share
 *
 * Because `weight(1f)` hands each destination whatever is left after the others, and with
 * eight destinations on a narrow phone that share is **43dp** — under the 48dp floor the
 * whole app is measured against. `minTouchTarget` cannot save it: a child's minimum is
 * measured *inside* an already-fixed share, so the floor was declared and then not reached.
 * Reordering the two modifiers changes nothing, for the same reason — which is worth
 * recording, because reordering is the first thing anyone tries.
 *
 * A fixed width keeps every tab at the floor and the bar scrolls instead: the same trade
 * the landscape rail makes for height, and the same reason — a tab that is hard to reach
 * beats one that cannot be hit.
 */
private val BOTTOM_NAV_ITEM_WIDTH = 64.dp

/** `--topbar-h` */
private val TopBarHeight = 72.dp

/**
 * `cars.html:29`, `renters.html:29` — the search field's `max-width` inside the top bar.
 *
 * ## Why this is one constant and not a copy per screen
 *
 * Because it is one rule in the prototype's CSS (`.search-bar { max-width:280px }`) and it was
 * copied into two screens that cannot see each other. The field is capped rather than weighted so
 * it looks the same on a 360dp phone and a 711dp tablet instead of eating the bar on one and
 * shrinking to nothing on the other — a decision about the *bar*, which this file owns.
 */
internal val TopBarSearchMaxWidth = 280.dp