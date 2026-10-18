package net.klinik.rental.ui.screens.kontrak

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.klinik.rental.domain.model.Contract
import net.klinik.rental.domain.model.ServiceException
import net.klinik.rental.ui.format.formatTanggalBulan
import net.klinik.rental.ui.theme.KlinikNetTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Robolectric render of the contract screen.
 *
 * ## What is asserted here and why it is not a review item
 *
 * Three things the AC names: the empty state says *what to do*, a populated row
 * names the renter and the plate, and no interactive node measures below 48dp.
 *
 * The third cannot be checked by reading the code. Every component here clears the
 * floor on its own, but a component that only clears it because its own modifier is
 * generous is one edit away from failing — and the failing node is often a glyph
 * that *looks* small, which is exactly what a reviewer skims past. So the nodes are
 * laid out and measured.
 *
 * The screen is stateless, so no database and no ViewModel is involved: the states
 * below are real `KontrakUiState` values, built by hand.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class KontrakScreenTest {

    @get:Rule
    val compose = createComposeRule()

    // ------------------------------------------------------------- empty state

    /**
     * The empty state says what to do — the title *and* the instruction, with a
     * `Buka Kontrak` action.
     *
     * A bare "no data" would satisfy "an empty state exists" and fail the AC, which
     * is why the body is asserted separately rather than as part of the title. The
     * AC names all three parts explicitly.
     *
     * `assertExists` rather than `assertIsDisplayed`: the action lays out below the
     * bottom edge of the Robolectric window, and a node past the bottom edge measures
     * zero — off-screen, not missing. The displayed claim would then be about the
     * harness rather than the screen.
     */
    @Test
    fun `the empty state says what to do`() {
        compose.setContent { theme { Screen(state = KontrakUiState()) } }

        compose.onNodeWithTag(TAG_EMPTY).assertExists()
        compose.onNodeWithText("Belum ada Kontrak").assertExists()
        compose.onNodeWithText(
            "Buka kontrak pertama untuk mulai mencatat sewa.",
        ).assertExists()
        // The action is what makes it an instruction rather than a statement. Both
        // it and the top bar draw `Buka Kontrak`, so it is addressed by its own tag —
        // the label alone matches two nodes.
        compose.onNodeWithTag(TAG_ADD).assertExists().assertHasClickAction()
        // The label is asserted on the action itself rather than screen-wide, because
        // the top bar carries the same words.
        compose.onAllNodes(hasText("Buka Kontrak")).assertCountEquals(2)
    }

    /**
     * An empty list must not look like an error. A bare "no data" or a red message
     * would satisfy "an empty state exists" and break the epic's rule that an error
     * state never looks like a new account.
     */
    @Test
    fun `an empty list renders no error copy`() {
        compose.setContent { theme { Screen(state = KontrakUiState()) } }

        assertTrue(
            "an empty list must not show a failure message",
            compose.onAllNodes(
                hasClickAction().not() and hasText("Gagal menyimpan. Coba lagi.", substring = true),
            ).fetchSemanticsNodes().isEmpty(),
        )
    }

    /**
     * A refusal the form does not model *is* shown.
     *
     * `KendaraanUiState.errorMessage` exists but `KendaraanScreen` never renders it —
     * dead state that made a refused write invisible. This screen's is rendered, and
     * this pins the difference: a failed write that looks like a save that landed is
     * the outcome the epic context forbids, and it cannot happen if nothing is ever
     * drawn.
     */
    @Test
    fun `a screen-level refusal is rendered rather than swallowed`() {
        compose.setContent {
            theme {
                Screen(state = KontrakUiState(errorMessage = "Gagal menyimpan. Coba lagi."))
            }
        }

        compose.onNodeWithText("Gagal menyimpan. Coba lagi.").assertExists()
    }

    // ------------------------------------------------------------ populated list

    /**
     * The AC: a populated row names the renter *and* the plate.
     *
     * Both, because either alone does not identify a contract — the owner needs to
     * know who has which car.
     */
    @Test
    fun `a populated row names the renter and the plate`() {
        compose.setContent { theme { Screen(state = populatedState()) } }

        compose.onNodeWithTag("kontrak-row-c1").assertExists()
        compose.onNodeWithText("Budi Santoso").assertExists()
        // The date comes from the *real* formatter — see `populatedState`.
        compose.onNodeWithText("B 1235 TF · 1 Okt").assertExists()
    }

    /**
     * The row's date carries the **month**, so two rentals begun on the same day of
     * different months are distinguishable.
     *
     * This is the AC the weekday form could not meet: `1 Okt` and `1 Des` differ, and
     * a weekday format would render both as `1 <weekday>` and leave the owner unable
     * to tell which rental they are looking at. Two rows whose dates are a month apart
     * are asserted side by side, which is the shape the failure actually took.
     */
    @Test
    fun `a row's date carries the month`() {
        compose.setContent {
            theme {
                Screen(
                    state = KontrakUiState(
                        rows = listOf(
                            row(id = "c1", date = LocalDate.of(2026, 10, 1)),
                            row(id = "c2", date = LocalDate.of(2026, 12, 1)),
                        ),
                    ),
                )
            }
        }

        compose.onNodeWithText("B 1235 TF · 1 Okt").assertExists()
        compose.onNodeWithText("B 1235 TF · 1 Des").assertExists()
    }

    /**
     * The rate and the schedule are on the row, so the owner can read the terms
     * without opening anything.
     *
     * The rate is the pre-rendered `formatRupiah` output, so this also pins that the
     * screen does not assemble money by hand — a second `"Rp " + value` here is how
     * two screens end up showing the same amount two ways.
     */
    @Test
    fun `a populated row shows the rate and the schedule`() {
        compose.setContent { theme { Screen(state = populatedState()) } }

        compose.onNodeWithText("Rp 250.000").assertExists()
        // A six-day week names its rest day…
        compose.onNodeWithText("Libur Min").assertExists()
        // …and a seven-day one says so, rather than showing a rest day it does not
        // have.
        compose.onAllNodes(hasText("7 hari")).assertCountEquals(1)
    }

    /**
     * Every row's spoken label names **both** the renter and the car.
     *
     * The label used to be the plate alone, which made TalkBack read the plate twice —
     * once as the label and once as the subtitle — and never read the renter at all.
     * Both are asserted, and the plate-alone form is asserted *absent*, so a
     * regression to the old wording fails rather than silently passing the first
     * assertion.
     */
    @Test
    fun `each row names its renter and its vehicle to a screen reader`() {
        compose.setContent { theme { Screen(state = populatedState()) } }

        compose.onAllNodes(
            hasContentDescription("Kontrak Budi Santoso dengan B 1235 TF"),
        ).assertCountEquals(1)
        compose.onAllNodes(
            hasContentDescription("Kontrak Siti Aminah dengan B 9876 AB"),
        ).assertCountEquals(1)
        // The old plate-only label must not still be in the tree: it read the plate
        // twice and the renter never.
        assertTrue(
            "the row label must not be the plate alone",
            compose.onAllNodes(hasContentDescription("Kontrak B 1235 TF"))
                .fetchSemanticsNodes().isEmpty(),
        )
    }

    /**
     * The empty state's own action opens the form too — not only the top bar's.
     *
     * On an empty list this button is the *only* affordance, so `assertHasClickAction`
     * on it is the whole of what pass 2 proved about it, and that assertion is
     * satisfied by any lambda at all: `KnButton` hands `onClick` to a clickable
     * `Surface` regardless of the body's contents. Change `KontrakEmpty`'s action to
     * `{}` and every other assertion in this file stays green — the top bar opens the
     * form, the empty state's button does nothing, and the owner is told to press the
     * one control that is dead.
     */
    @Test
    fun `the empty state's action opens the form`() {
        var opened = false
        compose.setContent { theme { Screen(state = KontrakUiState(), onOpen = { opened = true }) } }

        compose.onNodeWithTag(TAG_ADD)
            .assertExists()
            .assertHasClickAction()
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertTrue("the empty state's action must invoke onOpen", opened)
    }

    /**
     * Dismissing the confirmation clears the confirmation, and dismissing the refusal
     * clears the refusal.
     *
     * ## Why the tone, not the state
     *
     * The banner's dismiss handler used to branch on `state.errorMessage != null`
     * while the *selection* above it suppressed that same value whenever the sheet was
     * open. So with the sheet up and a confirmation queued, the drawn banner was the
     * confirmation and the dismiss cleared the hidden error — leaving the visible
     * confirmation stuck on screen with nothing left to clear it. The handler now
     * tests the tone of the banner it is closing, which is the banner that was drawn.
     *
     * Both directions are asserted, so "always calls `onSnackbarDismiss`" fails as
     * loudly as the original bug.
     */
    @Test
    fun `dismissing the banner clears the message that was drawn`() {
        var snackbarCleared = false
        var errorCleared = false
        val state = KontrakUiState(
            rows = populatedState().rows,
            errorMessage = "Gagal menyimpan. Coba lagi.",
        )

        compose.setContent {
            theme {
                Screen(
                    state = state,
                    snackbarMessage = "Kontrak B 1235 TF tersimpan",
                    onSnackbarDismiss = { snackbarCleared = true },
                    onErrorDismiss = { errorCleared = true },
                )
            }
        }

        // The error wins when both are set — that is the pre-existing rule, and this
        // is the case the old handler got wrong once the sheet was involved.
        compose.onNodeWithText("Gagal menyimpan. Coba lagi.").assertExists()

        // The dismiss control is the clickable node nested inside the banner.
        // Addressed structurally rather than by a label, because
        // `KnSnackbarDismiss` renders a bare `Lucide.X` glyph with no content
        // description of its own. `useUnmergedTree` is required because the banner's
        // own semantics merge its descendants away.
        compose.onNode(
            hasClickAction() and hasAnyAncestor(hasTestTag(TAG_BANNER)),
            useUnmergedTree = true,
        ).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertTrue("dismissing a refusal must clear the refusal", errorCleared)
        assertFalse(
            "dismissing a refusal must not clear the confirmation behind it",
            snackbarCleared,
        )
    }

    // --------------------------------------------------------------- the banner

    /**
     * The success snackbar renders on its own.
     *
     * The banner branch has two inputs and three reachable states, and a screen that
     * drew neither — or drew the wrong one — would satisfy every other assertion in
     * this file. This is the confirmation half.
     */
    @Test
    fun `a confirmation renders as a snackbar`() {
        compose.setContent {
            theme {
                Screen(
                    state = KontrakUiState(),
                    snackbarMessage = "Kontrak B 1235 TF tersimpan",
                )
            }
        }

        compose.onNodeWithText("Kontrak B 1235 TF tersimpan").assertExists()
    }

    /**
     * With both messages non-null the **error wins**.
     *
     * The two are not mutually exclusive upstream: a success followed by a failure
     * leaves both non-null, because neither clears the other. Drawing them
     * independently would stack two bars on the same pixels, and the owner would read
     * whichever the renderer put last. So the error is asserted present *and* the
     * confirmation asserted absent — a screen that drew both, or drew the wrong one,
     * fails here.
     */
    @Test
    fun `the error wins when both messages are set`() {
        compose.setContent {
            theme {
                Screen(
                    state = KontrakUiState(errorMessage = "Gagal menyimpan. Coba lagi."),
                    snackbarMessage = "Kontrak B 1235 TF tersimpan",
                )
            }
        }

        compose.onNodeWithText("Gagal menyimpan. Coba lagi.").assertExists()
        assertTrue(
            "the confirmation must not be drawn alongside the error",
            compose.onAllNodes(hasText("Kontrak B 1235 TF tersimpan"))
                .fetchSemanticsNodes().isEmpty(),
        )
    }

    /**
     * And while the form is open the error is handed to the **sheet**, which draws it.
     *
     * A `ModalBottomSheet` is a separate window above the screen's, so a banner
     * composed here would be invisible behind it. The screen therefore suppresses its
     * own banner and passes the message down — and this asserts the message reaches
     * the sheet's error slot, which is where it is actually visible. `Robolectric`
     * sees both windows in one tree, so "the text exists" would pass either way; the
     * sheet's own tag is what distinguishes the two.
     */
    @Test
    fun `an open sheet draws the error rather than the screen beneath it`() {
        compose.setContent {
            theme {
                Screen(
                    state = KontrakUiState(
                        form = ContractFormState(),
                        errorMessage = "Gagal menyimpan. Coba lagi.",
                    ),
                )
            }
        }

        compose.onNodeWithTag(TAG_SHEET_ERROR).assertExists()
        compose.onNodeWithText("Gagal menyimpan. Coba lagi.").assertExists()
    }

    /**
     * The row carries a click action, and the tap invokes `onOpenExceptions` **with
     * that row's contract**.
     *
     * ## Why this replaced the "not clickable" test it stands in for
     *
     * That test asserted the *absence* of a verb, and its own KDoc named this story as
     * the one that would supply it — so it was a deferral sentence pointing at a thing
     * that now exists, and the row's only verb would have arrived unexercised. Both
     * halves are asserted here: a row that carries a click action (so `onClick = {}`
     * fails) and a tap that reaches the handler **with the right row** (so a tap wired
     * to some other row also fails — a sheet for the wrong contract is exactly as
     * broken as no sheet at all, and only the payload assertion catches it).
     */
    @Test
    fun `a row is clickable and opens its own contract exceptions`() {
        var opened: ContractRow? = null
        compose.setContent {
            theme {
                Screen(state = populatedState(), onOpenExceptions = { opened = it })
            }
        }

        compose.onNodeWithTag("kontrak-row-c1")
            .assertExists()
            .assertHasClickAction()
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertNotNull("tapping a row must reach the handler", opened)
        assertEquals("c1", opened!!.contract.id)
    }

    /**
     * And the tap carries **that** row, not whichever row happens to be first.
     *
     * The second row is tapped here and the assertion names `c2`. A handler wired to a
     * row captured from the previous composition would pass the test above and fail
     * this one, and the pair is what makes "the row's own contract" a measured claim
     * rather than an assumption.
     */
    @Test
    fun `tapping the second row opens the second row exceptions`() {
        var opened: ContractRow? = null
        compose.setContent {
            theme {
                Screen(state = populatedState(), onOpenExceptions = { opened = it })
            }
        }

        compose.onNodeWithTag("kontrak-row-c2")
            .assertHasClickAction()
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertEquals("c2", opened!!.contract.id)
    }

    /**
     * The exception count badge renders on a row that has exceptions, and is **absent**
     * on one that has none.
     *
     * Both halves, because the AC asks the owner to see which rentals carry
     * compensation days *before* opening anything — and a badge drawn unconditionally
     * would satisfy the first half while making the second meaningless. A `0` on every
     * row is a claim about data nobody asked for, and a badge that is always there
     * stops being read as news.
     */
    @Test
    fun `the exception count badge shows only on a row that has exceptions`() {
        val populated = populatedState().rows
        compose.setContent {
            theme {
                Screen(
                    state = KontrakUiState(
                        rows = listOf(
                            populated[0].copy(exceptionCount = 2),
                            populated[1],
                        ),
                    ),
                )
            }
        }

        compose.onNodeWithText("2 pengecualian").assertExists()
        assertTrue(
            "an uncounted row must carry no badge at all",
            compose.onAllNodes(hasText("0 pengecualian")).fetchSemanticsNodes().isEmpty(),
        )
        // The uncounted row is still on screen, so the assertion above is about the
        // badge rather than about a list that failed to render.
        compose.onNodeWithTag("kontrak-row-c2").assertExists()
    }

    // ------------------------------------------------- the exception sheet mounts

    /**
     * The screen mounts the exception sheet when its state says one is open, and not
     * otherwise.
     *
     * The sheet is the only path to this story's verb, and the row test above asserts
     * the *tap*; this asserts the sheet's own presence and absence, because a screen
     * that invokes the handler and renders nothing is the other half of the same
     * defect class Story 2.5's review found for the open form.
     */
    @Test
    fun `the exception sheet mounts when the sheet state is open`() {
        compose.setContent { theme { Screen(state = stateWithExceptionSheet()) } }

        compose.onNodeWithTag(TAG_EXCEPTION_SHEET).assertExists()
    }

    @Test
    fun `the exception sheet is absent when the sheet state is null`() {
        compose.setContent { theme { Screen(state = populatedState()) } }

        assertTrue(
            "a closed sheet must leave nothing over the list",
            compose.onAllNodes(hasTestTag(TAG_EXCEPTION_SHEET)).fetchSemanticsNodes().isEmpty(),
        )
    }

    /**
     * And the screen's banner is suppressed while the exception sheet is up, because a
     * `ModalBottomSheet` is a separate window and a banner drawn here would be
     * *beneath* it.
     *
     * The banner's own tag is asserted absent and the sheet's error slot asserted
     * present with the same message. Robolectric sees both windows in one tree, so "the
     * text exists" would pass either way — the tags are what distinguishes the two
     * surfaces.
     */
    @Test
    fun `the screen banner is suppressed while the exception sheet is up`() {
        compose.setContent {
            theme {
                Screen(
                    state = stateWithExceptionSheet().copy(
                        errorMessage = "Gagal menyimpan pengecualian. Coba lagi.",
                    ),
                )
            }
        }

        compose.onNodeWithTag(TAG_EXCEPTION_ERROR).assertExists()
        compose.onNodeWithText("Gagal menyimpan pengecualian. Coba lagi.").assertExists()
        assertTrue(
            "the screen's banner must not be drawn beneath the sheet",
            compose.onAllNodes(hasTestTag(TAG_BANNER)).fetchSemanticsNodes().isEmpty(),
        )
    }

    /**
     * And the screen's own confirmation is suppressed for the same reason: the sheet
     * covers this window, so a snackbar drawn here is invisible until the sheet closes.
     *
     * Asserting the **banner's absence** rather than the message's is the point. The
     * message is still shown — inside the sheet, in the window that is actually on
     * top — because "shown exactly once, where the owner can see it" is the goal; an
     * assertion that the text appears nowhere would demand the owner be told *nothing*
     * and pass an implementation that simply dropped the confirmation.
     *
     * Both halves are asserted: no banner node here, and the sheet's own copy present.
     */
    @Test
    fun `the screen confirmation is suppressed while the exception sheet is up`() {
        compose.setContent {
            theme {
                Screen(
                    state = stateWithExceptionSheet(),
                    snackbarMessage = "14 Okt ditandai tanpa tagihan",
                )
            }
        }

        assertTrue(
            "a banner beneath the sheet is invisible and reads as nothing happened",
            compose.onAllNodes(hasTestTag(TAG_BANNER)).fetchSemanticsNodes().isEmpty(),
        )
        assertTrue(
            "and the confirmation is not simply lost: the sheet draws it in its own window",
            compose.onAllNodes(hasTestTag(TAG_EXCEPTION_SUCCESS)).fetchSemanticsNodes().isNotEmpty(),
        )
        compose.onNodeWithText("14 Okt ditandai tanpa tagihan").assertExists()
    }

    // -------------------------------------------------------- the remove controls

    /**
     * Every remove control on the exception sheet clears the 48dp floor.
     *
     * ## Why the sheet's rows need this and the list's did not
     *
     * These are the first bare `KnIconButton`s this screen has had — every other
     * control on it is a `KnButton`, a `KnChip` or a whole `KnListRow`, all of which
     * carry the floor internally. An icon button is exactly the shape that *looks*
     * small: a 20dp glyph in a row, which is the node a reviewer skims past and the
     * one the accessibility criterion names ("including badges, chips, and in-row icon
     * buttons"). `KnIconButton` applies `minTouchTarget` itself, and this is the test
     * that keeps it from being dropped.
     *
     * A taller viewport (`qualifiers` below) because the sheet's rows sit below the
     * Robolectric window's bottom edge, and a node past the bottom edge measures zero
     * — which would make this pass or fail for a reason that has nothing to do with
     * touch targets.
     */
    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h2400dp")
    fun `every remove control clears the touch floor`() {
        compose.setContent {
            theme { Screen(state = stateWithExceptionSheet(rows = exceptionRows())) }
        }

        val remove = compose.onNodeWithTag(exceptionRemoveTag("exception-1"))
        remove.assertExists().assertHasClickAction()
        remove.assertAtLeast(MIN_TARGET, "exception remove control")
    }

    /**
     * And the row's remove is wired: the tap reaches `onRequestRemove` **with that
     * row's id**, which is the only handle the delete is keyed on.
     */
    @Test
    fun `a row remove asks for that row id`() {
        var requested: String? = null
        compose.setContent {
            theme {
                Screen(
                    state = stateWithExceptionSheet(rows = exceptionRows()),
                    onRequestRemove = { requested = it },
                )
            }
        }

        compose.onNodeWithTag(exceptionRemoveTag("exception-1"))
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertEquals("exception-1", requested)
    }

    /**
     * And its spoken label names the **day**, not just "a button exists".
     *
     * A bare `Hapus` on a row says *that* something is removable and not *what* — and
     * the owner may be several rows down a list when they hear it. The date is the
     * thing they are looking at, so it is what the label names.
     */
    @Test
    fun `each remove control names the day it removes`() {
        compose.setContent {
            theme { Screen(state = stateWithExceptionSheet(rows = exceptionRows())) }
        }

        compose.onNodeWithContentDescription("Hapus 14 Okt").assertExists()
        compose.onNodeWithContentDescription("Hapus 16 Okt").assertExists()
    }

    /**
     * And an **ended** contract's sheet renders its rows while unmounting every
     * remove control.
     *
     * Unmounted, not disabled: a greyed-out icon on a contract that cannot be edited
     * still says it can. The rows are asserted *present* in the same test, because
     * reading an ended contract's record of what was not billed is the one thing that
     * record is for — a sheet that hid them would be hiding the answer to a question
     * the owner opened it to ask.
     */
    @Test
    fun `an ended contract shows its rows with no remove control at all`() {
        compose.setContent {
            theme {
                Screen(
                    state = stateWithExceptionSheet(
                        rows = exceptionRows(),
                        isReadOnly = true,
                    ),
                )
            }
        }

        compose.onNodeWithTag(TAG_EXCEPTION_SHEET).assertExists()
        compose.onNodeWithTag(exceptionRowTag("exception-1")).assertExists()
        compose.onNodeWithText("Ganti ban, mobil di bengkel").assertExists()
        assertTrue(
            "an ended contract must not offer a remove control",
            compose.onAllNodes(hasTestTag(exceptionRemoveTag("exception-1")))
                .fetchSemanticsNodes().isEmpty(),
        )
    }

    /**
     * The settlement verb's tap reaches `onOpenSettle` **with that row's contract**.
     *
     * `assertExists()` and `assertHasClickAction()` are both satisfied by a control wired to
     * `{}` — which is how Stories 3.4 and 3.5 each shipped a whole feature with no production
     * caller. So the payload is what this asserts, and it is what a bare existence check
     * cannot see.
     */
    @Test
    fun `a row's settlement verb reaches the handler with that row`() {
        var opened: ContractRow? = null
        compose.setContent {
            theme {
                Screen(state = populatedState(), onOpenSettle = { opened = it })
            }
        }

        compose.onNodeWithTag("kontrak-row-settle-c1")
            .assertExists()
            .assertHasClickAction()
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertNotNull("tapping the verb must reach the handler", opened)
        assertEquals("and it carries the row it was on", "c1", opened!!.contract.id)
    }

    /**
     * And the settlement verb does **not** also fire the row's own click.
     *
     * The same nesting hazard `the end verb does not also fire the row's own click` covers,
     * repeated because two verbs now sit inside one clickable row and each needs its own
     * proof. A double-fire would open the exception sheet *and* the settlement sheet — two
     * `ModalBottomSheet` windows over one state, the exact defect Story 2.8's review found.
     */
    @Test
    fun `the settlement verb does not also fire the row's own click`() {
        var openedExceptions: ContractRow? = null
        var openedSettle: ContractRow? = null
        compose.setContent {
            theme {
                Screen(
                    state = populatedState(),
                    onOpenExceptions = { openedExceptions = it },
                    onOpenSettle = { openedSettle = it },
                )
            }
        }

        compose.onNodeWithTag("kontrak-row-settle-c1")
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertNotNull("the settlement handler fired", openedSettle)
        assertNull("and only it — the row's own click did not also fire", openedExceptions)
    }

    /**
     * An **ended** row still offers the settlement verb — the deliberate opposite of the end
     * verb, and the one case FR-9b exists for.
     *
     * The day-one exit is needed most on the contracts that ended long ago with their days
     * paid elsewhere, which are exactly the rows `an ended row offers no end verb at all`
     * shows carry no end control. Absent the settlement verb on those rows, the app's only
     * escape from fictional historic arrears would be unreachable exactly where it is needed.
     */
    @Test
    fun `an ended row still offers the settlement verb`() {
        val ended = stateWithOneEndedRow()
        compose.setContent { theme { Screen(state = ended) } }

        assertTrue(
            "the ended row must keep its settlement control",
            compose.onAllNodes(hasTestTag("kontrak-row-settle-c1")).fetchSemanticsNodes().isNotEmpty(),
        )
        // And the end verb is still absent there — the two are genuinely independent.
        assertTrue(
            "while it still has no end control",
            compose.onAllNodes(hasTestTag("kontrak-row-end-c1")).fetchSemanticsNodes().isEmpty(),
        )
    }

    /**
     * An **already settled** row offers no second settlement verb, and says through which day.
     *
     * Absence rather than disabled, for the reason the end verb's absence is: a greyed-out
     * "Tandai lunas" still claims the action is available, and there is nothing left for it to
     * do — a settlement cannot be extended by settling again, it can only be edited.
     */
    @Test
    fun `an already settled row offers no second verb and shows the day`() {
        val settled = populatedState().copy(
            rows = populatedState().rows.map { row ->
                row.copy(settledThroughText = "24 Okt")
            },
        )
        compose.setContent { theme { Screen(state = settled) } }

        assertTrue(
            "a settled row must not offer a second settlement",
            compose.onAllNodes(hasTestTag("kontrak-row-settle-c1")).fetchSemanticsNodes().isEmpty(),
        )
        // And it says what it was settled through, so the absence is explained rather than
        // merely silent. `useUnmergedTree` because the row merges its descendants — the
        // same reason `an ended row says when it ended` uses it.
        compose.onNodeWithTag("kontrak-row-settled-c1", useUnmergedTree = true).assertExists()
        // And the copy is the app's own, rather than a bare date the owner would have to
        // interpret. Counted rather than asserted singly because both rows are settled in
        // this fixture on purpose, so a single-node assertion would be ambiguous.
        compose.onAllNodes(hasText("Lunas s.d. 24 Okt"), useUnmergedTree = true)
            .assertCountEquals(2)
    }

    /**
     * And the settlement verb clears the touch floor, like every other control on this row.
     */
    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h2400dp")
    fun `the row settlement verb clears the touch floor`() {
        compose.setContent { theme { Screen(state = populatedState()) } }

        compose.onNodeWithTag("kontrak-row-settle-c1").assertAtLeast(MIN_TARGET, "contract settle verb")
    }

    // ----------------------------------------------------- the end verb on a row

    /**
     * The open row carries an end verb, and the tap reaches `onOpenEnd` **with that
     * row's contract**.
 *
     * Both halves for the same reason the exception row's click test gives: a control
     * wired to `{}` fails the first assertion's *purpose* while passing a bare
     * `assertExists`, and a sheet for the wrong contract is exactly as broken as no
     * sheet at all — which only the payload assertion catches.
     */
    @Test
    fun `an open row offers the end verb and the tap carries that row`() {
        var opened: ContractRow? = null
        compose.setContent {
            theme {
                Screen(state = populatedState(), onOpenEnd = { opened = it })
            }
        }

        compose.onNodeWithTag("kontrak-row-end-c1")
            .assertExists()
            .assertHasClickAction()
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertNotNull("tapping the verb must reach the handler", opened)
        assertEquals("and it carries the row it was on", "c1", opened!!.contract.id)
    }

    /**
     * And the end verb does **not** also fire the row's own click.
     *
     * The two verbs are nested — the button sits inside a row that is itself clickable —
     * and whether the inner control consumes the tap or whether both handlers fire is
     * decided by Compose's pointer handling, not by anything stated in this codebase.
     * If both fired, the owner tapping "Akhiri" would get the *exception* sheet as well
     * as the end sheet: two `ModalBottomSheet` windows over one state, which is the exact
     * `high` Story 2.8's review found and the reason all three sheets mount from
     * `KontrakScreen`.
     *
     * Asserted negatively on purpose: the tests above pass either way, because a
     * double-fire still sets `opened` with the right row.
     */
    @Test
    fun `the end verb does not also fire the row's own click`() {
        var openedExceptions: ContractRow? = null
        var openedEnd: ContractRow? = null
        compose.setContent {
            theme {
                Screen(
                    state = populatedState(),
                    onOpenExceptions = { openedExceptions = it },
                    onOpenEnd = { openedEnd = it },
                )
            }
        }

        compose.onNodeWithTag("kontrak-row-end-c1")
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertNotNull("the end verb fires", openedEnd)
        assertNull(
            "and nothing else does — two sheets over one state is the Story 2.8 defect",
            openedExceptions,
        )
    }

    /**
     * And the **second** row's verb carries the second row — the pair that makes "its
     * own contract" a measured claim rather than an assumption.
     */
    @Test
    fun `the second row's end verb carries the second row`() {
        var opened: ContractRow? = null
        compose.setContent {
            theme {
                Screen(state = populatedState(), onOpenEnd = { opened = it })
            }
        }

        compose.onNodeWithTag("kontrak-row-end-c2")
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertEquals("c2", opened!!.contract.id)
    }

    /**
     * An **ended** row has no end control at all — absent, not disabled.
     *
     * This is the AC's exact wording and the assertion most worth keeping, for the same
     * reason `an ended contract still shows its rows` keeps its add-block assertion: a
     * greyed-out "Akhiri" on a contract that already ended still claims the action is
     * available, and the repository would refuse it anyway — so a disabled control is a
     * claim the data layer will not honour.
     */
    @Test
    fun `an ended row offers no end verb at all`() {
        val ended = stateWithOneEndedRow()
        compose.setContent { theme { Screen(state = ended) } }

        assertTrue(
            "the ended row must have no end control",
            compose.onAllNodes(hasTestTag("kontrak-row-end-c1")).fetchSemanticsNodes().isEmpty(),
        )
        assertTrue(
            "while the row that is still running keeps its own",
            compose.onAllNodes(hasTestTag("kontrak-row-end-c2")).fetchSemanticsNodes().isNotEmpty(),
        )
        // And the row is still there — a filtered-out row would pass this vacuously.
        compose.onNodeWithTag("kontrak-row-c1").assertExists()
    }

    /**
     * An ended row says **when** it ended, in place of its start date.
     *
     * The owner reconciling a bill needs the end, and a row that only ever named the
     * start leaves that open.
     *
     * Found on the **unmerged** tree: `KontrakRow` sets an explicit
     * `contentDescription` on the row, which merges its descendants away in the merged
     * tree — so the text is present in the composition but not addressable there. That
     * is a property of the row, not a missing node, and the fix is the query rather than
     * the semantics: the merged description is deliberately about who and which car,
     * so widening it to swallow the end date would undo the label Story 2.1 pinned.
     */
    @Test
    fun `an ended row shows when it ended`() {
        compose.setContent { theme { Screen(state = stateWithOneEndedRow()) } }

        compose.onNodeWithTag("kontrak-row-ended-c1", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Berakhir 20 Okt", useUnmergedTree = true).assertExists()
    }

    /**
     * The end sheet mounts when its state says one is open, and not otherwise — the
     * same presence-and-absence pair the exception sheet gets, for the same reason: a
     * screen that invokes the handler and renders nothing would ship a story whose
     * primary action leads nowhere while every other assertion still passes.
     */
    @Test
    fun `the end sheet mounts when the sheet state is open`() {
        compose.setContent { theme { Screen(state = stateWithEndSheet()) } }

        compose.onNodeWithTag(TAG_END_SHEET).assertExists()
    }

    @Test
    fun `the end sheet is absent when the sheet state is null`() {
        compose.setContent { theme { Screen(state = populatedState()) } }

        assertTrue(
            "a closed sheet must leave nothing over the list",
            compose.onAllNodes(hasTestTag(TAG_END_SHEET)).fetchSemanticsNodes().isEmpty(),
        )
    }

    /**
     * The screen banner is suppressed while the **end** sheet is up — the third sheet
     * added to a condition 2.6 left at two.
     *
     * This is the line a later story adding a sheet most easily forgets, and forgetting
     * it draws a confirmation *beneath* the sheet, which reads to the owner as nothing
     * happening at all. Asserted with both sheets' tags so the exception sheet's own
     * suppression (asserted above) and this one cannot pass for each other.
     */
    @Test
    fun `the screen banner is suppressed while the end sheet is up`() {
        compose.setContent {
            theme {
                Screen(
                    state = stateWithEndSheet().copy(
                        errorMessage = "Gagal mengakhiri kontrak. Coba lagi.",
                    ),
                    snackbarMessage = "Kontrak berakhir 20 Okt",
                )
            }
        }

        compose.onNodeWithTag(TAG_END_SHEET).assertExists()
        assertTrue(
            "no banner beneath the sheet",
            compose.onAllNodes(hasTestTag(TAG_BANNER)).fetchSemanticsNodes().isEmpty(),
        )
        compose.onNodeWithTag(TAG_END_ERROR).assertExists()
        assertTrue(
            "and the confirmation is handed to the sheet, not lost",
            compose.onAllNodes(hasTestTag(TAG_END_SUCCESS)).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    /**
     * The row's end button clears the touch floor.
     *
     * Measured on the real node rather than composed separately: `KnButton` clears the
     * floor internally, and this is the test that keeps it from being dropped — the
     * failure being a `Ghost` variant in a `trailing` slot, which is exactly the shape
     * that looks small in a screenshot.
     */
    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h2400dp")
    fun `the row end verb clears the touch floor`() {
        compose.setContent { theme { Screen(state = populatedState()) } }

        compose.onNodeWithTag("kontrak-row-end-c1").assertAtLeast(MIN_TARGET, "contract end verb")
    }

    /**
     * A state whose first contract has ended and whose second is still running.
     *
     * Built by copying the contract and then **re-deriving** both projections by hand —
     * `canEnd` and `endDateText` — rather than leaving the source row's values in place.
     * That is the trap a `copy()` has here: `ContractRow.canEnd` *defaults* to
     * `contract.isOpen`, but `copy()` does not re-evaluate a default, so a row whose
     * contract was just given an `endDate` would carry `canEnd = true` from the original
     * and draw an end verb on a contract that already ended. Setting both explicitly is
     * what keeps this fixture from asserting the opposite of the rule it exists to test.
     */
    private fun stateWithOneEndedRow(): KontrakUiState {
        val base = populatedState().rows
        val endedOn = LocalDate.of(2026, 10, 20)
        val endedFirst = base[0].copy(
            contract = base[0].contract.copy(endDate = endedOn),
            canEnd = false,
            endDateText = formatTanggalBulan(endedOn),
        )
        return KontrakUiState(rows = listOf(endedFirst, base[1]))
    }

    /** A state with the end sheet open on `c1`. */
    private fun stateWithEndSheet() = KontrakUiState(
        rows = populatedState().rows,
        endSheet = EndContractFormState(
            contractId = "c1",
            contractLabel = "Budi Santoso · B 1235 TF",
        ),
    )

    // ------------------------------------------------------------- the sheet mounts

    /**
     * The screen mounts the form sheet when its state says the form is open.
     *
     * ## Why this is the assertion the story most needed
     *
     * The sheet is the only path to this story's verb. A screen that took `onOpen`
     * and rendered no sheet would ship a story whose primary action no user could
     * reach — and every other assertion in this file, plus every pure-function test
     * in `ContractFormTest`, would still pass. So the sheet's presence is asserted
     * directly, by a tag only the sheet declares.
     */
    @Test
    fun `the form sheet is mounted when the form is open`() {
        compose.setContent {
            theme { Screen(state = KontrakUiState(form = ContractFormState())) }
        }

        compose.onNodeWithTag(TAG_SHEET).assertExists()
    }

    /**
     * And it is *not* mounted when the form is closed — a sheet that never unmounts
     * would cover the list it was opened over.
     */
    @Test
    fun `the form sheet is absent when the form is closed`() {
        compose.setContent { theme { Screen(state = populatedState()) } }

        assertTrue(
            "a closed form must leave no sheet over the list",
            compose.onAllNodes(hasTestTag(TAG_SHEET)).fetchSemanticsNodes().isEmpty(),
        )
    }

    // -------------------------------------------------------- touch target floor

    /**
     * The 48dp floor, measured across every interactive node on the screen.
     *
     * Walked by query rather than by a hard-coded list of tags, so a control added
     * later without a tag is still covered — and a new 32dp button fails the build
     * whether or not anyone remembered to tag it.
     */
    @Test
    fun `every interactive node clears the touch floor`() {
        compose.setContent { theme { Screen(state = populatedState()) } }

        val interactive = compose.onAllNodes(hasClickAction())
        val nodes = interactive.fetchSemanticsNodes()

        // A guard against a vacuous pass: if the walk ever matches nothing, a broken
        // screen would look like a screen full of compliant controls.
        assertTrue("expected interactive nodes, found none", nodes.isNotEmpty())

        nodes.indices.forEach { i ->
            interactive[i].assertAtLeast(MIN_TARGET, "kontrak control #$i")
        }
    }

    /**
     * The empty state's action clears the floor, measured on **the node the screen
     * actually draws**.
     *
     * ## Why this replaces a standalone button
     *
     * The previous version of this test composed its own `KnButton` carrying
     * `TAG_ADD` and measured *that*. It measured a different instance from the one
     * on screen, so the claim it appeared to make — "the empty contract screen's
     * action clears the floor" — was not the claim it actually proved. Pass a
     * smaller `KnButtonSize` to `KontrakEmpty`'s action, or constrain it in a box,
     * and the old test stayed green while the screen shipped a sub-48dp control.
     *
     * Inside the full screen the `KnEmptyState` lays out below the Robolectric
     * window's bottom edge — the empty state is a tall block, and a node laid out
     * past the bottom edge measures zero, which would make this test pass or fail
     * for a reason that has nothing to do with touch targets.
     *
     * So this one test runs with a **taller viewport** (`qualifiers` below) rather
     * than scrolling: the screen's inner column fills its parent and clips, so a
     * scroll host outside it cannot bring the button into view, and shrinking the
     * box just moves the clipping. Giving the screen a window tall enough for its
     * own content is the only way to measure the node it really draws.
     */
    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h2400dp")
    fun `the empty state's action clears the touch floor`() {
        compose.setContent { theme { Screen(state = KontrakUiState()) } }

        val action = compose.onNodeWithTag(TAG_ADD)
        action.assertExists().assertHasClickAction()
        // And it is *inside* the screen's empty state, not a node the test planted.
        compose.onNodeWithTag(TAG_EMPTY).assertExists()
        action.assertAtLeast(MIN_TARGET, "empty-state action")
    }

    /**
     * The top-bar `Buka Kontrak` action is clickable and invokes `onOpen`.
     *
     * The empty state's half of "the owner can reach the form" is covered above, and
     * the sheet's mount is covered by `the form sheet is mounted when the form is
     * open` — but between them sits a gap: nothing ever *clicked* the top-bar
     * action, so `onClick = onOpen` was unverified on a populated screen, where the
     * empty state's button is not there to be mistaken for it.
     */
    @Test
    fun `the top bar opens the form from a populated list`() {
        var opened = false
        compose.setContent {
            theme { Screen(state = populatedState(), onOpen = { opened = true }) }
        }

        compose.onNodeWithTag(TAG_ADD_ACTION)
            .assertExists()
            .assertHasClickAction()
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertTrue("the top-bar action must invoke onOpen", opened)
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Wraps in the app theme — a screen composed outside it silently gets the light
     * defaults (`staticCompositionLocalOf`, Story 2.1's noted gotcha).
     */
    @Composable
    private fun theme(content: @Composable () -> Unit) {
        KlinikNetTheme(darkTheme = false) { content() }
    }

    /**
     * Routes the state through the screen with no-op actions.
     *
     * `snackbarMessage` is a parameter rather than a fixed `null` so the banner
     * branch's confirmation half can be rendered — see the banner tests above.
     *
     * Every callback the screen now takes is a parameter here too, for the reason
     * the screen makes them required: a helper that filled one in itself would be a
     * test that cannot tell a wired control from an unwired one.
     */
    @Composable
    private fun Screen(
        state: KontrakUiState,
        snackbarMessage: String? = null,
        onOpen: () -> Unit = {},
        onCloseForm: () -> Unit = {},
        onFormChange: ((ContractFormState) -> ContractFormState) -> Unit = {},
        onSave: () -> Unit = {},
        onOpenExceptions: (ContractRow) -> Unit = {},
        onCloseExceptions: () -> Unit = {},
        onExceptionChange: ((ExceptionFormState) -> ExceptionFormState) -> Unit = {},
        onAddException: () -> Unit = {},
        onRequestRemove: (String) -> Unit = {},
        onConfirmRemove: () -> Unit = {},
        onCancelRemove: () -> Unit = {},
        onSnackbarDismiss: () -> Unit = {},
        onErrorDismiss: () -> Unit = {},
        exceptionErrorMessage: String? = null,
        onExceptionErrorDismiss: () -> Unit = {},
        // Story 2.7's four, and its banner pair — the same required-for-a-reason
        // argument one layer down. `onOpenEnd` is a per-test parameter rather than `{}`
        // because the row's end verb is a claim a test makes about the screen: "the open
        // row has one and the ended row has none" is only meaningful if a test can also
        // prove the tap reaches a handler it chose.
        onOpenEnd: (ContractRow) -> Unit = {},
        onCloseEnd: () -> Unit = {},
        onEndChange: ((EndContractFormState) -> EndContractFormState) -> Unit = {},
        onConfirmEnd: () -> Unit = {},
        endErrorMessage: String? = null,
        onEndErrorDismiss: () -> Unit = {},
        // Story 3.6's four, and its banner pair — same argument one layer up. `onOpenSettle`
        // is per-test for the same reason as `onOpenEnd`: the row's settlement verb is a
        // claim a test makes about the screen, and it is only meaningful if a test can
        // also prove the tap reaches a handler it chose.
        onOpenSettle: (ContractRow) -> Unit = {},
        onCloseSettle: () -> Unit = {},
        onSettleChange: ((SettleFormState) -> SettleFormState) -> Unit = {},
        onConfirmSettle: () -> Unit = {},
        settleErrorMessage: String? = null,
        onSettleErrorDismiss: () -> Unit = {},
        // And the deposit history's four, defaulted here **for the same reason as every
        // other parameter in this fixture**: the screen's own parameters are required, so
        // a fixture that did not pass them would not compile. A test that cares about the
        // history's wiring passes a handler and asserts it received the right row —
        // `DepositHistoryWiringTest` is where that happens.
        depositHistory: net.klinik.rental.ui.screens.setoran.DepositHistoryUi? = null,
        onOpenHistory: (ContractRow) -> Unit = {},
        onRequestDepositCancel: (String, String, String) -> Unit = { _, _, _ -> },
        onRequestDepositEdit: (String) -> Unit = {},
        onDepositHistoryDismiss: () -> Unit = {},
    ) {
        KontrakScreen(
            state = state,
            onOpen = onOpen,
            // Required since review pass 2, so these three are passed explicitly —
            // the point of making them required is that a test cannot accidentally
            // omit them and get a sheet that silently does nothing.
            onCloseForm = onCloseForm,
            onFormChange = onFormChange,
            onSave = onSave,
            // And the exception sheet's seven, for the same reason: a row that opens
            // nothing, and a sheet whose Add is wired to `{}`, are the same defect at
            // two depths.
            onOpenExceptions = onOpenExceptions,
            onCloseExceptions = onCloseExceptions,
            onExceptionChange = onExceptionChange,
            onAddException = onAddException,
            onRequestRemove = onRequestRemove,
            onConfirmRemove = onConfirmRemove,
            onCancelRemove = onCancelRemove,
            snackbarMessage = snackbarMessage,
            // Both supplied explicitly, and deliberately not aliased to one another:
            // the aliasing is what let a refusal be cleared through the
            // confirmation's setter.
            onSnackbarDismiss = onSnackbarDismiss,
            onErrorDismiss = onErrorDismiss,
            // The sheet's own banner, supplied explicitly and deliberately not
            // aliased: it is a different window from the screen's banner.
            exceptionErrorMessage = exceptionErrorMessage,
            onExceptionErrorDismiss = onExceptionErrorDismiss,
            onOpenEnd = onOpenEnd,
            onCloseEnd = onCloseEnd,
            onEndChange = onEndChange,
            onConfirmEnd = onConfirmEnd,
            endErrorMessage = endErrorMessage,
            onEndErrorDismiss = onEndErrorDismiss,
            onOpenSettle = onOpenSettle,
            onCloseSettle = onCloseSettle,
            onSettleChange = onSettleChange,
            onConfirmSettle = onConfirmSettle,
            settleErrorMessage = settleErrorMessage,
            onSettleErrorDismiss = onSettleErrorDismiss,
            depositHistory = depositHistory,
            onOpenHistory = onOpenHistory,
            onRequestDepositCancel = onRequestDepositCancel,
            onRequestDepositEdit = onRequestDepositEdit,
            onDepositHistoryDismiss = onDepositHistoryDismiss,
        )
    }

    /**
     * A state with the exception sheet open on `c1`.
     *
     * The rows default to [exceptionRows] so a test that is about the *sheet* does not
     * have to restate them, and `isReadOnly` is a parameter because the read-only
     * case is the one with the interesting absence in it.
     */
    private fun stateWithExceptionSheet(
        rows: List<ServiceExceptionRow> = exceptionRows(),
        isReadOnly: Boolean = false,
    ) = KontrakUiState(
        rows = populatedState().rows,
        exceptionSheet = ExceptionFormState(
            contractId = "c1",
            contractLabel = "Budi Santoso · B 1235 TF",
            isReadOnly = isReadOnly,
        ),
        exceptionRows = rows,
    )

    /**
     * Two stored exceptions, built through the *real* projection.
     *
     * `toRow()` rather than a hand-built `ServiceExceptionRow`, for the reason
     * `populatedState` gives: a literal `dateText` here would be a second, unchecked
     * spelling of the format the sheet renders, and the fixture would keep describing
     * the old one after a change.
     */
    private fun exceptionRows(): List<ServiceExceptionRow> = listOf(
        ServiceException(
            id = "exception-1",
            contractId = "c1",
            date = LocalDate.of(2026, 10, 14),
            reason = "Ganti ban, mobil di bengkel",
        ).toRow(),
        ServiceException(
            id = "exception-2",
            contractId = "c1",
            date = LocalDate.of(2026, 10, 16),
            reason = "Antre servis",
        ).toRow(),
    )

    /** One six-day contract and one seven-day contract, so both badges are covered. */
    private fun populatedState() = KontrakUiState(
        rows = listOf(
            ContractRow(
                contract = Contract(
                    id = "c1",
                    renterId = "r1",
                    vehicleId = "v1",
                    startDate = LocalDate.of(2026, 10, 1),
                    dailyRate = 250_000L,
                    restDay = DayOfWeek.SUNDAY,
                ),
                renterName = "Budi Santoso",
                vehiclePlate = "B 1235 TF",
                rateText = "Rp 250.000",
                dateText = formatTanggalBulan(LocalDate.of(2026, 10, 1)),
            ),
            ContractRow(
                contract = Contract(
                    id = "c2",
                    renterId = "r2",
                    vehicleId = "v2",
                    startDate = LocalDate.of(2026, 10, 2),
                    dailyRate = 275_000L,
                    restDay = null,
                ),
                renterName = "Siti Aminah",
                vehiclePlate = "B 9876 AB",
                rateText = "Rp 275.000",
                dateText = formatTanggalBulan(LocalDate.of(2026, 10, 2)),
            ),
        ),
    )

    /**
     * One row built through the **real** date formatter.
     *
     * `dateText = formatTanggalBulan(date)` rather than a hand-written string, because
     * a literal here is a second, unchecked spelling of the format the screen renders:
     * the fixture would keep describing the old format after a change and the test
     * would keep passing against a screen that no longer renders it.
     */
    private fun row(id: String, date: LocalDate) = ContractRow(
        contract = Contract(
            id = id,
            renterId = "r1",
            vehicleId = "v1",
            startDate = date,
            dailyRate = 250_000L,
            restDay = null,
        ),
        renterName = "Budi Santoso",
        vehiclePlate = "B 1235 TF",
        rateText = "Rp 250.000",
        dateText = formatTanggalBulan(date),
    )

    /**
     * Both axes, in one helper.
     *
     * The AC says "no interactive node measures below 48dp", and the failure mode
     * that matters is the one nobody looks at: a control that is wide enough and
     * short, or short and wide. Asserting one axis would let that through.
     */
    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertAtLeast(
        target: Dp,
        what: String,
    ) {
        try {
            assertWidthIsAtLeast(target)
            assertHeightIsAtLeast(target)
        } catch (e: AssertionError) {
            throw AssertionError("$what failed the 48dp floor: ${e.message}", e)
        }
    }

    private companion object {
        val MIN_TARGET: Dp = 48.dp
        const val TAG_SHEET = "contract-form-sheet"
        const val TAG_SHEET_ERROR = "contract-sheet-error"
    }
}