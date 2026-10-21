package net.klinik.rental.ui.screens.penyewa

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.klinik.rental.R
import net.klinik.rental.ui.components.AVATAR_KEYS
import net.klinik.rental.ui.components.avatarDrawable
import net.klinik.rental.ui.theme.knColors
import net.klinik.rental.ui.theme.knSpacing
import net.klinik.rental.ui.theme.knTypography

/**
 * The avatar pack, as a grid the owner picks from.
 *
 * ## Why a grid and not a text field
 *
 * Because the first version of this asked the owner to *type* an emoji, which is two problems in
 * one: finding the glyph in the OS keyboard's own picker, and getting whatever the system font
 * decides that glyph looks like — inconsistent with an app whose whole visual language is one
 * hand-drawn set. A key into a fixed pack cannot be half-typed and always renders the same.
 *
 * ## Why the pack is 45 separate drawables
 *
 * Because one 5014×5014 sheet decodes to ~100 MB of bitmap and would have to be held by every
 * screen showing an avatar. Separate files load only what is drawn, and a wrong offset in a
 * `BitmapPainter` cannot slip a seam between two faces.
 *
 * ## Why tapping the selected face clears it
 *
 * Because "no avatar" is a real state, not an oversight — it means the disc falls back to
 * initials, which is what every renter recorded before this feature shows. Making the second tap
 * the way to get there saves a "clear" button that would have one job.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AvatarPicker(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.knSpacing
    val kn = MaterialTheme.knColors

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.penyewa_field_avatar),
            style = MaterialTheme.knTypography.overline,
            color = kn.textTertiary,
        )
        Text(
            text = stringResource(R.string.penyewa_avatar_hint),
            style = MaterialTheme.knTypography.body,
            color = kn.textTertiary,
            modifier = Modifier.padding(top = spacing.xs),
        )
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = spacing.sm)
                .testTag(TAG_AVATAR_GRID),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
            // Six across on a tablet, three on a phone: a 44dp disc plus a 8dp gap is 52dp, so
            // six fit a 360dp screen with room over. Fixed rather than "as many as fit" so the
            // grid does not reflow when a face is added.
            maxItemsInEachRow = 6,
        ) {
            AVATAR_KEYS.forEach { key ->
                AvatarOption(
                    key = key,
                    selected = key == selected,
                    onClick = {
                        // The second tap on the chosen face clears it — the way back to the
                        // initials, which is what a renter with no avatar should show.
                        onSelect(if (key == selected) "" else key)
                    },
                )
            }
        }
    }
}

/**
 * One face in the grid.
 *
 * The selected one is marked by a ring rather than a tick, so the choice reads as "this is the
 * one" without a second glyph competing with the face for attention.
 */
@Composable
private fun AvatarOption(
    key: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val kn = MaterialTheme.knColors
    val drawable = avatarDrawable(key)
    // Read out here, not inside `semantics { }`: that lambda is not a composable scope, so a
    // `stringResource` call inside it would not compile. The number is the face's position in
    // the pack — the key's trailing digits — not the whole key, which is a storage detail.
    val label = stringResource(R.string.penyewa_avatar_option, avatarOrdinal(key))

    Box(
        modifier = Modifier
            .size(AvatarOptionSize)
            .clip(CircleShape)
            .background(kn.bgCard)
            .then(
                if (selected) {
                    Modifier.border(width = 2.dp, color = kn.primaryFill, shape = CircleShape)
                } else {
                    Modifier
                },
            )
            .testTag(avatarTag(key))
            // The face itself is decoration, so it is not announced. The label names the
            // **position** in the pack, which is the only handle a screen-reader user has
            // over a grid of pictures — the person's name is not known here.
            .semantics { contentDescription = label }
            .clickableWithoutRipple(onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (drawable != null) {
            Image(
                painter = painterResource(drawable),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
        }
    }
}

/**
 * A tap with no ripple.
 *
 * The ripple would be a coloured wash over the face the owner is trying to look at, and the
 * selection is already shown by the ring — the ripple would say the same thing twice, worse.
 */
private fun Modifier.clickableWithoutRipple(onClick: () -> Unit): Modifier =
    this.clickable(
        interactionSource = MutableInteractionSource(),
        indication = null,
        onClick = onClick,
    )

/** A grid cell's disc. 44dp: the prototype's own `.avatar` width. */
private val AvatarOptionSize = 44.dp

/** The test tag for one grid option, so a test can tap a face by key. */
internal fun avatarTag(key: String): String = "avatar-option-$key"

/**
 * The face's position in the pack — `avatar_07` is the seventh.
 *
 * Parsed from the key's own trailing digits rather than looked up, so it cannot disagree
 * with the key. A key that is not one of ours has no position, and the grid never builds one:
 * every option comes from `AVATAR_KEYS`.
 */
private fun avatarOrdinal(key: String): Int = key.substringAfterLast('_').toIntOrNull() ?: 0
