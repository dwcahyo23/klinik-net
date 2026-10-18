package net.klinik.rental.ui.lock

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.klinik.rental.R
import net.klinik.rental.ui.components.KnPinGrid
import net.klinik.rental.ui.components.KnButton
import net.klinik.rental.ui.components.KnButtonVariant
import net.klinik.rental.ui.theme.knColors
import net.klinik.rental.ui.theme.knMotion
import net.klinik.rental.ui.theme.knSpacing
import net.klinik.rental.ui.theme.knTypography

/**
 * The lock screen: a full-screen PIN entry above everything.
 *
 * ## Why this replaces the shell rather than covering it
 *
 * `MainActivity` composes **either** this **or** `AppShell`, never both. An overlay would leave
 * the destination composed underneath — a screenshot, a screen reader traversal, or a rendering
 * bug would expose the books behind a lock. Not composing the shell at all is the only version
 * where "locked" means the content is not on screen.
 *
 * ## Why the copy claims nothing
 *
 * FR-17 spends more words on what the lock is *not* than on what it is, and the reason is concrete:
 * the prototype's lock screen says *"Data tersimpan lokal & terenkripsi di perangkat ini"*, the
 * database is a plain SQLite file, and an owner who believes that sentence will hand the tablet to
 * a customer with more confidence than the truth allows. The subtitle here says the barrier and
 * stops.
 *
 * ## Why the shake is 420ms and not on the motion scale
 *
 * `KnMotion.tween` refuses anything outside 140/220/300, and that guard is worth keeping — it is
 * what stops a stray `tween(500)` appearing on some future screen. The UX doc carves out exactly
 * one exception by name and duration, so `shakeMillis` is a field on `KnMotion` that this one
 * composable reads. See `Motion.kt`.
 *
 * @param shakeKey a value that changes when a **wrong** PIN is submitted. The animation keys on
 *   it rather than on `errorMessage`, so a message that persists across a correct entry cannot
 *   re-fire the shake, and a second wrong attempt always shakes even if the message is unchanged.
 */
@Composable
fun LockScreen(
    state: PinLockUiState,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onRecovery: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Called once a second while a countdown is showing.
     *
     * **Required, not defaulted**: a defaulted `{}` would be a countdown that never moves, which
     * is the lock screen telling the owner to wait for a number that never changes.
     */
    onTick: () -> Unit,
    shakeKey: Int = 0,
) {
    val spacing = MaterialTheme.knSpacing
    val motion = MaterialTheme.knMotion

    // The horizontal offset the shake drives. Zero except during the animation, so the layout is
    // identical before and after a failure — a screen that stayed shifted would read as broken.
    val shake = remember { Animatable(0f) }

    LaunchedEffect(shakeKey) {
        if (shakeKey == 0) return@LaunchedEffect
        // One sweep left, right, back to centre. `tween` is built directly rather than through
        // `KnMotion.tween` because 420ms is deliberately outside the scale — see the class KDoc.
        val spec = tween<Float>(durationMillis = motion.shakeMillis, easing = motion.easing)
        shake.animateTo(-1f, spec)
        shake.animateTo(1f, spec)
        shake.animateTo(0f, spec)
    }

    // The countdown's redraw cadence, owned here rather than by the ViewModel.
    //
    // "How often to redraw" is a UI concern; the **deadline** is the ViewModel's and is persisted.
    // The loop runs only while a countdown is showing, so an unlocked or unlocked-out screen
    // schedules nothing — and a ViewModel with no `delay` loop is one a test can drive by hand
    // instead of racing a virtual clock.
    if (state.lockedOutSeconds != null) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(1_000)
                onTick()
            }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag(TAG_LOCK_SCREEN),
        color = MaterialTheme.knColors.bgPage,
        contentColor = MaterialTheme.knColors.textPrimary,
    ) {
        // A `Box` centring a scrollable `Column`, **not** a centred scrollable `Column`.
        //
        // `Column(verticalScroll).arrangement = Center` is the version that looks right and is
        // broken: when the content is taller than the viewport the arrangement centres the
        // *overflow*, so both ends sit outside the scroll range and cannot be scrolled to. The
        // recovery button is the last item, which makes the only way back in for a forgotten PIN
        // unreachable on a short screen or at a large system font size.
        //
        // Found by a test whose `performClick` missed while the semantics action worked — the
        // button was drawn, wired, and un-tappable.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(spacing.xl),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
            Text(
                text = stringResource(R.string.pin_lock_title),
                style = MaterialTheme.knTypography.title,
                color = MaterialTheme.knColors.textPrimary,
            )
            // The honest sentence. Not "encrypted", not "secure" — a barrier, and only that.
            Text(
                text = stringResource(R.string.pin_lock_subtitle),
                style = MaterialTheme.knTypography.body,
                color = MaterialTheme.knColors.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = spacing.xs)
                    .testTag(TAG_LOCK_SUBTITLE),
            )

            Box(
                modifier = Modifier
                    .padding(top = spacing.xl)
                    // 8dp of travel: enough to read as "no", small enough not to break the grid's
                    // alignment with the dots above it.
                    .offset(x = (shake.value * 8).dp),
            ) {
                KnPinGrid(
                    enteredDigits = state.enteredDigits,
                    totalDigits = state.totalDigits,
                    onDigit = onDigit,
                    onDelete = onDelete,
                )
            }

            // The error line, or the countdown. One slot, because they are the same question —
            // "why is my input not working" — and two lines would be two answers on screen at once.
            val message = when {
                state.lockedOutSeconds != null -> stringResource(
                    R.string.pin_error_locked_out,
                    state.lockedOutSeconds,
                )
                state.errorMessage != null -> state.errorMessage
                else -> null
            }
            message?.let {
                Text(
                    text = it,
                    style = MaterialTheme.knTypography.body,
                    color = MaterialTheme.knColors.errorText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(top = spacing.md)
                        .testTag(TAG_LOCK_ERROR),
                )
            }

            // The way out, always visible. A forgotten PIN with no recovery route is the data loss
            // this whole epic exists to prevent, so the door is a button rather than a gesture or
            // a hidden long-press.
                KnButton(
                    label = stringResource(R.string.pin_recovery_action),
                    onClick = onRecovery,
                    variant = KnButtonVariant.Ghost,
                    modifier = Modifier
                        .padding(top = spacing.sm)
                        .testTag(TAG_LOCK_RECOVERY),
                )
            }
        }
    }
}

internal const val TAG_LOCK_SCREEN = "pin-lock-screen"
internal const val TAG_LOCK_SUBTITLE = "pin-lock-subtitle"
internal const val TAG_LOCK_ERROR = "pin-lock-error"
internal const val TAG_LOCK_RECOVERY = "pin-lock-recovery"