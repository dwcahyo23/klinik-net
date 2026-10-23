package net.klinik.rental.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale as ComposeLocale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.klinik.rental.R
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
                    // `.list-row .lr-title { letter-spacing: -.2px }` — the token carries
                    // -.1sp, which left a renter's name very slightly looser than the
                    // prototype's tight-set names.
                    style = MaterialTheme.knTypography.label.copy(
                        letterSpacing = (-0.2).sp,
                    ),
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
                    // The prototype's `.lr-right { max-width: 46% }`, and it stays a **cap**
                    // rather than a fixed share: `fill = false` lets a narrow slot (a chevron,
                    // a single figure) stay narrow.
                    //
                    // The width floor is what a *text* slot needs. Measured on the device, a
                    // rupiah figure plus a two-word status pill came to more than 46% of the
                    // row once the avatar and title had taken theirs, so the pill was given a
                    // box narrower than its own label and ellipsised "Perhatian" to
                    // "Perhatiar". A slot that cannot fit the words in it is not a cap, it is
                    // a truncation.
                    modifier = Modifier
                        .weight(ListRowTrailingWeight, fill = false)
                        .widthIn(min = ListRowTrailingMinWidth),
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
    /**
     * An optional chip drawn beside [text] — `payments.html`'s "Sebagian" badge on the amount.
     *
     * Beside the value rather than in a column of its own, because the badge describes
     * **this** cell: a table with a badge column would put "Sebagian" one column away from
     * the figure it is about.
     */
    val badge: String? = null,
    /** The badge's tone. Ignored when [badge] is `null`. */
    val badgeTone: KnBadgeTone = KnBadgeTone.Neutral,
    /**
     * This cell's own tag, applied to the **text**, not to the cell's box.
     *
     * On the text, because a test that wants to know what a cell says needs a node that
     * *says* it: a tag on the cell's container would find the right box and read an empty
     * string from it. A tag on the text is findable and says the same thing.
     */
    val tag: String? = null,
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
    /**
     * The row's own verbs, drawn after the last cell — `payments.html`'s trailing `<td>`.
     *
     * A slot rather than a cell, because a cell's text is data and these are controls: the
     * row's cancel and edit buttons must clear the 48dp floor and carry their own tags, and
     * a `String` cell cannot. Absent on a row that offers no verb, which is why it is
     * nullable rather than an empty lambda — an empty lambda would still reserve the gutter.
     */
    trailing: (@Composable () -> Unit)? = null,
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
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = spacing.lg, vertical = spacing.md),
                    horizontalArrangement = if (cell.numeric) {
                        Arrangement.End
                    } else {
                        Arrangement.Start
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = cell.text,
                        style = tableCellStyle(cell),
                        color = kn.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = cell.tag?.let { Modifier.testTag(it) } ?: Modifier,
                    )
                    cell.badge?.let { badge ->
                        KnBadge(
                            text = badge,
                            tone = cell.badgeTone,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
            trailing?.let { verbs ->
                Row(
                    modifier = Modifier.padding(end = spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    content = { verbs() },
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
 * `.avatar` — a picked face from the app's icon pack, or initials when none was chosen.
 *
 * ## Why the initials are derived rather than passed in
 *
 * Because the derivation is the part that is easy to get subtly wrong: the first letters of up
 * to two words, uppercased. A name is the one identifier the owner already has, and a screen
 * that also asked the caller for initials would produce a second, disagreeing spelling of the
 * same person.
 *
 * ## Why the avatar is a key and not a glyph
 *
 * The first version of this took a typed emoji and drew it as text, which made the disc whatever
 * the system font decides a face looks like — and let a stray character reach a value nothing
 * could validate. A key (`avatar_07`) names an entry in [AVATAR_KEYS] instead: it survives a font
 * change, it cannot be half-typed, and an unknown one is *recognisably* unknown rather than
 * rendering as somebody else's face.
 *
 * ## Why an unknown key falls back to initials rather than throwing
 *
 * Because two real paths produce one: a backup written by a build carrying a different pack, and
 * a row restored from before the pack existed. Neither is a programming error, and a list that
 * crashed on either would be an app that cannot open. Initials are a true statement about the
 * person; an empty disc is not.
 *
 * Not interactive — see [KnAvatarSize] on why Small is allowed below the floor.
 *
 * @param name the display name. A blank name renders an empty disc rather than a placeholder
 *   glyph: a row with no name yet should read as empty, not as a broken image.
 * @param avatar a key from [AVATAR_KEYS], or `null`/blank/unknown to fall back to initials.
 */
@Composable
fun KnAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: KnAvatarSize = KnAvatarSize.Medium,
    avatar: String? = null,
) {
    val kn = MaterialTheme.knColors
    val face = avatarDrawable(avatar)

    Box(
        modifier = modifier
            .size(size.diameter)
            .background(kn.brandSurface, CircleShape)
            // `--brand-100` in the CSS against a `--brand-50` fill — the prototype's own
            // pairing, read from a token rather than spelled as a hex.
            //
            // Measured, so the claim is honest: brand-100 on brand-50 is **1.11:1**, so
            // this is a *tint* around the disc, not a visible edge. The previous token,
            // borderStrong, was 1.41:1 — also not a visible edge, and grey where the
            // prototype's is pink. The KDoc here used to claim "real separation", which
            // was never true of either; the fix is the prototype's value, and the claim
            // is now what the value actually does. The disc separates from the card by
            // its *fill*, which is what makes it legible.
            //
            // The dark scheme supplies its own step — brand-700, at 2.78:1 — because a
            // pale pink ring on a near-black disc is a halo, not an edge.
            .border(width = 1.5.dp, color = kn.brand100, shape = CircleShape)
            // The disc is a visual abbreviation, not the person's identity.
            // Announced as `name`, the disc reads as the person's name; announced
            // as its own content it reads "A R" or a face with no label, which
            // tells a screen-reader user nothing and hides the name from them.
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        if (face != null) {
            // The pack's faces are square with a transparent background, so clipping to the
            // disc is what makes them sit inside it rather than over its corners. `Crop` rather
            // than `Fit`: the source is already square, and a fit would leave a hairline of
            // brand surface showing at the rim.
            Image(
                painter = painterResource(face),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
        } else {
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
}

/** `21px / 44px`, the CSS avatar's own initials-to-diameter ratio. */
private const val AVATAR_INITIALS_RATIO = 0.477f

/**
 * The drawable for each key in [AVATAR_KEYS], in the same order.
 *
 * Declared **before** [AVATAR_KEYS] because a top-level `val` is initialised in source order,
 * and the keys list is sized from this one.
 */
private val AVATAR_FACES: List<Int> = listOf(
    R.drawable.avatar_01, R.drawable.avatar_02, R.drawable.avatar_03, R.drawable.avatar_04,
    R.drawable.avatar_05, R.drawable.avatar_06, R.drawable.avatar_07, R.drawable.avatar_08,
    R.drawable.avatar_09, R.drawable.avatar_10, R.drawable.avatar_11, R.drawable.avatar_12,
    R.drawable.avatar_13, R.drawable.avatar_14, R.drawable.avatar_15, R.drawable.avatar_16,
    R.drawable.avatar_17, R.drawable.avatar_18, R.drawable.avatar_19, R.drawable.avatar_20,
    R.drawable.avatar_21, R.drawable.avatar_22, R.drawable.avatar_23, R.drawable.avatar_24,
    R.drawable.avatar_25, R.drawable.avatar_26, R.drawable.avatar_27, R.drawable.avatar_28,
    R.drawable.avatar_29, R.drawable.avatar_30, R.drawable.avatar_31, R.drawable.avatar_32,
    R.drawable.avatar_33, R.drawable.avatar_34, R.drawable.avatar_35, R.drawable.avatar_36,
    R.drawable.avatar_37, R.drawable.avatar_38, R.drawable.avatar_39, R.drawable.avatar_40,
    R.drawable.avatar_41, R.drawable.avatar_42, R.drawable.avatar_43, R.drawable.avatar_44,
    R.drawable.avatar_45,
)

/**
 * The icon pack's keys, in sheet order — `avatar_01` is the top-left face.
 *
 * A list rather than a `Map<String, Int>` because the key *is* the position: `avatar_07` means
 * the seventh entry, and storing both a name and an index would be two spellings of one fact
 * that can disagree. The drawable ids are here rather than looked up by name, because
 * `getIdentifier` is stripped by R8 and would return 0 in a release build.
 */
internal val AVATAR_KEYS: List<String> = List(AVATAR_FACES.size) { "avatar_%02d".format(it + 1) }

/**
 * The drawable for [key], or `null` when it names nothing this build carries.
 *
 * `null` is the answer for every one of: no avatar, a blank string, a key from a future pack, and
 * a key that is not one of ours at all. All four mean the same thing to a caller — draw the
 * initials — so they are one return value rather than four branches at each call site.
 */
internal fun avatarDrawable(key: String?): Int? {
    val index = AVATAR_KEYS.indexOf(key?.trim())
    return if (index >= 0) AVATAR_FACES[index] else null
}

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

/**
 * The narrowest a **text** trailing slot may be.
 *
 * 200dp is what Rp 2.070.000 plus a two-word status pill beside it needs; below that the
 * pill ellipsises its own label, which is the defect this floor exists to stop. A slot holding
 * only an icon is unaffected — it never asks for the floor.
 */
private val ListRowTrailingMinWidth = 200.dp

private val WHITESPACE = Regex("\\s+")