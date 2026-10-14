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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale as ComposeLocale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.klinik.rental.ui.theme.focusRing
import net.klinik.rental.ui.theme.knColors
import net.klinik.rental.ui.theme.knRadii
import net.klinik.rental.ui.theme.knSpacing
import net.klinik.rental.ui.theme.knTypography
import net.klinik.rental.ui.theme.minTouchTarget

/**
 * `.list-row` from `shared.css` section 10 — the row shape every list in the
 * app reuses.
 *
 * ## Why the selection edge is a border, not an accent box
 *
 * `.list-row` sets `border-left: 3px solid transparent` and `.list-row.active`
 * only changes its *colour*. Keeping the 3dp reserved in every state is the
 * point: a row that grows by 3dp when selected makes the whole list shift
 * sideways at the moment of tapping, which is exactly the moment the owner is
 * looking at their own finger.
 *
 * The colour is [net.klinik.rental.ui.theme.KnColors.primaryFill] rather than
 * [net.klinik.rental.ui.theme.KnColors.focusRing]: this bar marks the *current*
 * item, and `primaryFill` is the token reserved for white label text, which is
 * what sits against it.
 *
 * ## Why [onClick] is nullable
 *
 * A row that looks tappable but is not is a gesture-only path to an important
 * action. A non-clickable row is explicit, and its padding and type are
 * unchanged so a list can mix both without the text reflowing.
 *
 * @param leading an avatar, icon tile or checkbox. The slot is [ListRowLeadingSize]
 *   so a small leading element still lines the title up with its neighbours;
 *   the element itself is sized by the caller.
 * @param trailing right-aligned metadata: a badge, a chevron, an amount. Capped
 *   at 46% of the row (`max-width: 46%` in the CSS) so a long amount cannot
 *   squeeze the title into ellipsis on a 1280px-wide tablet.
 */
@Composable
fun KnListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val kn = MaterialTheme.knColors
    val spacing = MaterialTheme.knSpacing
    val shape = RoundedCornerShape(MaterialTheme.knRadii.md)
    val container = when {
        !enabled -> kn.bgSunken
        selected -> kn.brandSurface
        else -> kn.bgCard
    }
    // `border-left: 3px solid transparent` → `var(--brand-500)` when active.
    // Drawn in both the clickable and non-clickable form: a selected row in a
    // read-only list still has to look selected.
    val edge = Modifier.border(
        width = 3.dp,
        color = if (selected) kn.primaryFill else Color.Transparent,
        shape = shape,
    )

    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = MaterialTheme.minTouchTarget)
                .padding(spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            if (leading != null) {
                Box(Modifier.size(ListRowLeadingSize)) { leading() }
            }
            // The weight is what lets the title ellipsis instead of pushing the
            // trailing slot off the row; the min-width of zero inside `weight`
            // is what lets it shrink at all.
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.knTypography.label,
                    color = if (enabled) kn.textPrimary else kn.textTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.knTypography.body,
                        color = kn.textTertiary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (trailing != null) {
                Box(
                    modifier = Modifier.weight(ListRowTrailingWeight, fill = false),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    trailing()
                }
            }
        }
    }

    if (onClick != null) {
        val interaction = remember { MutableInteractionSource() }
        val focused by interaction.collectIsFocusedAsState()
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier
                .fillMaxWidth()
                .then(edge)
                .minTouchTarget()
                .focusRing(focused, MaterialTheme.knRadii.md),
            shape = shape,
            color = container,
            contentColor = if (enabled) kn.textPrimary else kn.textTertiary,
            interactionSource = interaction,
            content = { content() },
        )
    } else {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .then(edge),
            shape = shape,
            color = container,
            contentColor = if (enabled) kn.textPrimary else kn.textTertiary,
        ) {
            content()
        }
    }
}

/**
 * One `td` of a `.data` table.
 *
 * [numeric] switches to [net.klinik.rental.ui.theme.TABULAR_FIGURES] and
 * right-aligns: comparing a column of money is the owner's actual job, and a
 * decimal point that does not line up makes a column unreadable no matter how
 * long it is. [bold] is separate because a numeric column that is *also* the
 * emphasis column should not lose its figures.
 */
data class KnTableCell(
    val text: String,
    val numeric: Boolean = false,
    val bold: Boolean = false,
)

/**
 * `table.data thead th` — the header band.
 *
 * `bgSunken` rather than a second brand tint: the header has to be readable
 * *next to* a selected row without competing with it, and sunken is the one
 * surface in the scale that says "not selectable".
 *
 * Labels are uppercased here rather than asked for uppercased at each call site:
 * a table header is uppercase in every table, and trusting every caller to
 * remember would make a lower-case header a silent inconsistency.
 *
 * Casing goes through [androidx.compose.ui.text.intl.Locale.current] rather
 * than a fixed `Locale.ROOT`, because the CSS `text-transform` the port is
 * copying is defined against the user's locale: in a Turkish locale `i` uppercases
 * to a dotted `İ`, and `Locale.ROOT` would silently disagree with the rest of
 * the system.
 */
@Composable
fun KnTableHeaderRow(
    headers: List<String>,
    modifier: Modifier = Modifier,
) {
    val kn = MaterialTheme.knColors
    val spacing = MaterialTheme.knSpacing
    // `String.uppercase()` takes a `java.util.Locale`; Compose exposes its own
    // locale type, so it is bridged rather than silently defaulting to ROOT.
    val locale = ComposeLocale.current.toLanguageTag()
        .let { java.util.Locale.forLanguageTag(it) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(kn.bgSunken),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        headers.forEach { header ->
            Text(
                text = header.uppercase(locale),
                style = MaterialTheme.knTypography.overline.copy(
                    // `.data thead th { letter-spacing: .7px }`
                    letterSpacing = 0.7.sp,
                ),
                color = kn.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = spacing.lg, vertical = spacing.md),
            )
        }
    }
}

/**
 * `table.data tbody tr` — one body row.
 *
 * The 1dp top rule is the CSS's `border-top` on every `td`, drawn once per row
 * instead of once per cell: same line, one fewer seam to align, and it cannot
 * double up where two cells meet.
 *
 * Selection is [net.klinik.rental.ui.theme.KnColors.brandSurface], matching
 * `.data tbody tr.selected`.
 */
@Composable
fun KnTableRow(
    cells: List<KnTableCell>,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val kn = MaterialTheme.knColors
    val spacing = MaterialTheme.knSpacing
    val shape = RoundedCornerShape(MaterialTheme.knRadii.md)
    val container = if (selected) kn.brandSurface else kn.bgCard

    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(container)
                // `.data tbody td { border-top: 1px solid var(--border) }`
                .border(width = 1.dp, color = kn.border, shape = RoundedCornerShape(0.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            cells.forEach { cell ->
                Text(
                    text = cell.text,
                    style = tableCellStyle(cell),
                    color = kn.textPrimary,
                    textAlign = if (cell.numeric) TextAlign.End else TextAlign.Start,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = spacing.lg, vertical = spacing.md),
                )
            }
        }
    }

    if (onClick != null) {
        val interaction = remember { MutableInteractionSource() }
        val focused by interaction.collectIsFocusedAsState()
        Surface(
            onClick = onClick,
            modifier = modifier
                .fillMaxWidth()
                .minTouchTarget()
                .focusRing(focused, MaterialTheme.knRadii.md),
            shape = shape,
            color = container,
            contentColor = kn.textPrimary,
            interactionSource = interaction,
            content = { content() },
        )
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = container,
            contentColor = kn.textPrimary,
        ) {
            content()
        }
    }
}

/**
 * `.num { font-variant-numeric: tabular-nums; font-weight: 700 }` for numeric
 * cells; `body` for text.
 *
 * The figures come from `knTypography.figure`, which already carries
 * `tabular-nums`, rather than `body` with a weight override. One style owns the
 * feature, so a column cannot mix two styles and lose the alignment the moment
 * someone edits one cell.
 */
@Composable
internal fun tableCellStyle(cell: KnTableCell) = when {
    cell.numeric -> MaterialTheme.knTypography.figure.copy(
        fontWeight = if (cell.bold) FontWeight.ExtraBold else FontWeight.Bold,
    )

    cell.bold -> MaterialTheme.knTypography.label
    else -> MaterialTheme.knTypography.body
}

/**
 * Avatar diameters from `shared.css` section 10.
 *
 * Small (34dp) and Medium (44dp) are *under* the 48dp floor. That is fine here:
 * an avatar labels the row it sits in, it is not a control. It becomes a target
 * only inside an interactive [KnListRow], and in that case the row carries the
 * target, not the avatar.
 */
enum class KnAvatarSize(val diameter: Dp) {
    /** `.avatar-sm` */
    Small(34.dp),

    /** `.avatar` — the default. */
    Medium(44.dp),

    /** `.avatar-lg` */
    Large(64.dp),
}

/**
 * `.avatar` — initials on a brand-tinted disc.
 *
 * Initials are derived here rather than passed in, because the derivation is the
 * part that is easy to get subtly wrong: the first letters of up to two words,
 * uppercased. A name is the one identifier the owner already has, and a screen
 * that also asked the caller for initials would produce a second, disagreeing
 * spelling of the same person.
 *
 * Not interactive — see [KnAvatarSize] on why Small is allowed below the floor.
 *
 * @param name the display name. A blank name renders an empty disc rather than
 *   a placeholder glyph: a row with no name yet should read as empty, not as a
 *   broken image.
 */
@Composable
fun KnAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: KnAvatarSize = KnAvatarSize.Medium,
) {
    val kn = MaterialTheme.knColors

    Box(
        modifier = modifier
            .size(size.diameter)
            .background(kn.brandSurface, CircleShape)
            // `--brand-100` in the CSS against a `--brand-50` fill, so the disc
            // keeps an edge against a card that is itself close to `--brand-50`.
            // `borderStrong` is the nearest token this library carries with real
            // separation from `brandSurface`; the earlier version stroked the
            // disc in its own fill colour, which is what made the comment above
            // it untrue and the outline invisible.
            .border(width = 1.5.dp, color = kn.borderStrong, shape = CircleShape)
            // The initials are a visual abbreviation, not the person's identity.
            // Announced as `name`, the disc reads as the person's name; announced
            // as its own text it reads "A R", which tells a screen-reader user
            // nothing and hides the name from them entirely.
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = avatarInitials(name),
            // `.avatar { font-size: 21px }` scaled off the diameter, so Small and
            // Large stay proportionate instead of sharing one fixed size. 21/44 is
            // the CSS's own ratio.
            style = MaterialTheme.knTypography.title.copy(
                fontSize = (size.diameter.value * AVATAR_INITIALS_RATIO).sp,
            ),
            color = kn.onBrand,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

/** `21px / 44px`, the CSS avatar's own initials-to-diameter ratio. */
private const val AVATAR_INITIALS_RATIO = 0.477f

/**
 * First letters of up to two words, uppercased.
 *
 * Splitting on whitespace rather than looking for a first/last name pair: the
 * input may be a full legal name, a single-word nickname or a company, and the
 * rule that survives all three is "the first letters of the first two words".
 */
private fun avatarInitials(name: String): String =
    name.trim()
        .split(WHITESPACE)
        .filter { it.isNotEmpty() }
        .take(2)
        .map { it.first().uppercaseChar() }
        .joinToString("")

/** The avatar's leading slot in a [KnListRow] — the default `.avatar`. */
private val ListRowLeadingSize = 44.dp

/** `.lr-right { max-width: 46% }`, as a share of the row's width. */
private const val ListRowTrailingWeight = 0.46f

private val WHITESPACE = Regex("\\s+")