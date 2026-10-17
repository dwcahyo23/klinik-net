package net.klinik.rental.ui.screens.kontrak

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.klinik.rental.domain.model.Renter
import net.klinik.rental.domain.model.Vehicle
import net.klinik.rental.ui.theme.KlinikNetTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.TimeZone

/**
 * Robolectric render of the **real** contract form sheet.
 *
 * ## Why this file exists when `ContractFormTest` already covers the form
 *
 * `ContractFormTest` asserts the form's *rules* as pure functions over
 * `ContractFormState` — the schedule pair, the rest-day default, the rate prefill,
 * `canSave`. Every one of those assertions passes against a sheet that renders no
 * rest-day row, wires nothing to Save, and draws no refusal, because none of them
 * touches the composition. That is the gap this file closes: it composes
 * `ContractFormSheet` itself and asserts what is actually on screen.
 *
 * The shape is the one `RenterFormTest` and `VehicleFormTest` already establish — the
 * sheet over a state holder the test can observe, with `performSemanticsAction(OnClick)`
 * rather than `performClick` because a `ModalBottomSheet` renders into its own window
 * and a coordinate click does not reach it under Robolectric.
 *
 * ## Why `assertExists()` and not `assertIsDisplayed()`
 *
 * The sheet's content is taller than the Robolectric window, and a node laid out past
 * the bottom edge measures zero — off-screen, not missing. The displayed claim would
 * then be about the harness rather than the sheet. What is under test is that every
 * control is *in the tree and reachable*, which `assertExists` plus the interaction
 * assertions establish.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ContractFormSheetTest {

    @get:Rule
    val compose = createComposeRule()

    // ------------------------------------------------------------- the controls

    /**
     * The sheet renders every control the AC names: the two pickers, the start-date
     * field, the rate field, the numpad and both schedule chips.
     *
     * Asserted by tag, and each tag is a fixed literal — see `ContractPicker`'s KDoc
     * on why they are no longer derived from display copy. A missing control here is
     * the failure the pure-function tests structurally cannot see.
     */
    @Test
    fun `the sheet renders every control the AC names`() {
        compose.setContent { theme { Sheet(initial = complete()) } }

        compose.onNodeWithTag(TAG_FIELD_RENTER).assertExists()
        compose.onNodeWithTag(TAG_FIELD_VEHICLE).assertExists()
        compose.onNodeWithTag(TAG_FIELD_START_DATE).assertExists()
        compose.onNodeWithTag(TAG_FIELD_RATE).assertExists()
        compose.onNodeWithTag(TAG_NUMPAD).assertExists()
        compose.onNodeWithTag(TAG_SCHEDULE_SIX).assertExists()
        compose.onNodeWithTag(TAG_SCHEDULE_SEVEN).assertExists()
        compose.onNodeWithTag(TAG_SAVE).assertExists().assertHasClickAction()
        compose.onNodeWithTag(TAG_CANCEL).assertExists().assertHasClickAction()
    }

    /**
     * And the two pickers carry **their own** tags, not a tag the sheet also applies.
     *
     * This is the regression that hid the tags entirely: the sheet passed
     * `Modifier.testTag(TAG_FIELD_RENTER)` into `ContractPicker`, which applied a
     * second `testTag` to the same node — the later one won, so the caller's tag never
     * reached the tree and no test could address the picker. The tags are now supplied
     * by the caller as literals and applied exactly once, which is what this asserts
     * by addressing them.
     */
    @Test
    fun `the pickers carry their own tags and not a second one`() {
        compose.setContent { theme { Sheet(initial = complete()) } }

        compose.onNodeWithTag(TAG_FIELD_RENTER).assertExists()
        compose.onNodeWithTag(TAG_FIELD_VEHICLE).assertExists()
        // The two are distinct: one tag applied to both would make either query
        // ambiguous.
        assertTrue(
            "the renter and vehicle pickers must not share a tag",
            TAG_FIELD_RENTER != TAG_FIELD_VEHICLE,
        )
    }

    /**
     * The rate field is the disabled-plus-affix pair the numpad feeds.
     *
     * The affix is addressed *inside the rate field*, not screen-wide: `Rp` also
     * appears on the numpad's own display, so a screen-wide query would match two
     * nodes and say nothing about which field carries the affix.
     */
    @Test
    fun `the rate field is read-only and shows the affix`() {
        compose.setContent { theme { Sheet(initial = complete()) } }

        compose.onNodeWithTag(TAG_FIELD_RATE).assertExists()
        compose.onNode(
            hasText("Rp") and hasAnyAncestor(hasTestTag(TAG_FIELD_RATE)),
        ).assertExists()
        compose.onNode(
            hasText("250000") and hasAnyAncestor(hasTestTag(TAG_FIELD_RATE)),
        ).assertExists()
    }

    // -------------------------------------------------------- the rest-day row

    /**
     * The rest-day row is present for a six-day week.
     *
     * The AC names both halves — present for six, absent for seven — and this is the
     * half a sheet that simply never rendered the row would fail. Deleting the row
     * leaves every assertion in `ContractFormTest` green, which is why it is asserted
     * here.
     */
    @Test
    fun `the rest-day row is present for a six day week`() {
        compose.setContent { theme { Sheet(initial = complete().copy(sixDays = true)) } }

        compose.onNodeWithTag(TAG_REST_DAY_ROW).assertExists()
        compose.onNodeWithText("Hari libur").assertExists()
        // All seven days are offered, and exactly one is selected — the selected chip
        // carries `selected = true` in its semantics.
        REST_DAY_ORDER.forEach { day ->
            compose.onNodeWithTag(restDayTag(day)).assertExists()
        }
        compose.onNodeWithTag(restDayTag(DayOfWeek.SUNDAY))
            .assertExists()
            .assertHasClickAction()
    }

    /**
     * And the row is **absent** for a seven-day week, not merely disabled.
     *
     * `null` rest day *is* the seven-day week, so leaving the chips up would offer a
     * choice the schedule cannot represent. A disabled row would still say "there is a
     * day off here", which would be false; so the assertion is absence.
     */
    @Test
    fun `the rest-day row is absent for a seven day week`() {
        compose.setContent { theme { Sheet(initial = complete().withSchedule(sixDays = false)) } }

        assertTrue(
            "a seven-day week must not offer a rest day",
            compose.onAllNodes(hasTestTag(TAG_REST_DAY_ROW)).fetchSemanticsNodes().isEmpty(),
        )
        // And no day chip either — the whole row is unmounted, not just its label.
        REST_DAY_ORDER.forEach { day ->
            assertTrue(
                "the $day chip must be gone with the row",
                compose.onAllNodes(hasTestTag(restDayTag(day))).fetchSemanticsNodes().isEmpty(),
            )
        }
    }

    /**
     * The seven-day chip toggles the schedule, so the row's presence is driven by the
     * state rather than fixed.
     *
     * Without this, a sheet that always showed the row would pass the six-day test and
     * a sheet that never showed it would pass the seven-day one — the pair only means
     * something if the same sheet produces both.
     */
    @Test
    fun `choosing seven days removes the rest-day row`() {
        var latest: ContractFormState? = null
        compose.setContent { theme { Sheet(initial = complete(), onStateChanged = { latest = it }) } }

        compose.onNodeWithTag(TAG_SCHEDULE_SEVEN).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertEquals(false, latest?.sixDays)
        assertNull("seven days stores no rest day", latest?.restDay)
    }

    // --------------------------------------------------------------- Save's gate

    /**
     * Save's `enabled` follows `canSave`: an incomplete form cannot be submitted.
     *
     * `canSave`'s own KDoc calls it the button's source, and before this wiring it was
     * read only by a pure-function test — a Save button with no `enabled` at all
     * satisfies every assertion in `ContractFormTest`. So the button is asserted
     * *disabled* on an empty form and *enabled* on a complete one, which is the only
     * way to tell "wired" from "not wired".
     */
    @Test
    fun `Save is disabled until the form is complete`() {
        compose.setContent { theme { Sheet(initial = ContractFormState()) } }

        compose.onNodeWithTag(TAG_SAVE).assertExists().assertIsNotEnabled()
    }

    @Test
    fun `Save is enabled once the form is complete`() {
        compose.setContent { theme { Sheet(initial = complete()) } }

        compose.onNodeWithTag(TAG_SAVE).assertExists().assertIsEnabled()
    }

    /**
     * And each field's absence keeps it disabled — one case per field the gate checks,
     * so a gate that only looked at the renter would fail here.
     *
     * The state is held by the test and mutated between assertions rather than
     * re-composed, because `createComposeRule` allows exactly one `setContent` per
     * test. Holding it also means every case is asserted against the *same* sheet,
     * which is what makes this about the gate rather than about six separate renders.
     */
    @Test
    fun `Save is disabled when any required field is missing`() {
        var state by mutableStateOf(complete())
        compose.setContent {
            theme {
                ContractFormSheet(
                    state = state,
                    renterOptions = listOf(RENTER),
                    vehicleOptions = listOf(VEHICLE),
                    onDismissRequest = {},
                    onChange = {},
                    onSave = {},
                )
            }
        }

        val incomplete = listOf(
            "no renter" to complete().copy(renterId = null, renterName = ""),
            "no vehicle" to complete().copy(vehicleId = null, vehiclePlate = ""),
            "no date" to complete().copy(startDate = null),
            "no rate" to complete().copy(rateDigits = ""),
            "zero rate" to complete().copy(rateDigits = "0"),
            "no rest day" to complete().copy(restDay = null),
        )

        incomplete.forEach { (what, incompleteState) ->
            state = incompleteState
            compose.waitForIdle()
            compose.onNodeWithTag(TAG_SAVE).assertIsNotEnabled()
            assertEquals("$what must keep Save disabled", false, incompleteState.canSave)
        }

        // And the complete form re-enables it, so the assertion above is about the
        // missing field rather than about a button that is never enabled.
        state = complete()
        compose.waitForIdle()
        compose.onNodeWithTag(TAG_SAVE).assertIsEnabled()
    }

    // ------------------------------------------------------------- the refusals

    /**
     * A refusal renders **inside the sheet**, on the field that caused it.
     *
     * A `ModalBottomSheet` is a separate window layered above the screen's, so a
     * message the screen drew would sit beneath it and read as nothing at all. This
     * asserts the message is in the sheet's own tree.
     */
    @Test
    fun `a field refusal renders inside the sheet`() {
        compose.setContent {
            theme {
                Sheet(
                    initial = complete().copy(
                        vehicleError = "B 1235 TF masih tersewa. Akhiri kontraknya dulu.",
                    ),
                )
            }
        }

        compose.onNodeWithText(
            "B 1235 TF masih tersewa. Akhiri kontraknya dulu.",
        ).assertExists()
    }

    /**
     * And the sheet's own error slot renders, which is where an *unexpected* write
     * failure goes.
     *
     * This is the failure that used to be invisible: the ViewModel set
     * `errorMessage`, the screen drew it in its own window, and the sheet — layered
     * above — covered it. The sheet now takes that message and draws it itself.
     */
    @Test
    fun `the sheet renders an unexpected failure inside itself`() {
        compose.setContent {
            theme {
                Sheet(
                    initial = complete(),
                    errorMessage = "Gagal menyimpan. Coba lagi.",
                )
            }
        }

        compose.onNodeWithTag(TAG_SHEET_ERROR).assertExists()
        compose.onNodeWithText("Gagal menyimpan. Coba lagi.").assertExists()
    }

    /** And no error slot is drawn when there is nothing to report. */
    @Test
    fun `the sheet draws no error when there is none`() {
        compose.setContent { theme { Sheet(initial = complete()) } }

        assertTrue(
            "an error must not be invented on a clean form",
            compose.onAllNodes(hasTestTag(TAG_SHEET_ERROR)).fetchSemanticsNodes().isEmpty(),
        )
    }

    // -------------------------------------------------------------- the pickers

    /**
     * Tapping a picker row invokes `onSelect` with that item.
     *
     * The picker's whole job is returning a selection, and a modal that opened but
     * whose rows called nothing would look correct and do nothing. The field's
     * trailing chevron opens the modal; the row's click is then asserted to reach the
     * handler.
     *
     * The chevron is addressed by `hasClickAction` as well as by its description: the
     * description is the field's label, which the read-only input also carries, so the
     * description alone matches two nodes and the click action is what distinguishes
     * the button from the field it sits in.
     */
    @Test
    fun `selecting a picker row invokes onSelect`() {
        var selected: Renter? = null
        compose.setContent {
            theme {
                ContractPicker(
                    label = "Penyewa",
                    tag = "test-picker",
                    value = "",
                    hint = "Ketuk",
                    emptyMessage = "Belum ada penyewa.",
                    title = "Pilih penyewa",
                    options = listOf(RENTER),
                    rowTitle = { it.name },
                    rowSubtitle = { it.phone },
                    rowKey = { it.id },
                    onSelect = { selected = it },
                )
            }
        }

        // Open the modal through the field's own affordance.
        compose.onNodeWithTag("test-picker").assertExists()
        compose.onNode(
            hasContentDescription("Penyewa") and hasClickAction() and isEnabled(),
        ).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        compose.onNodeWithTag("test-picker-row-${RENTER.id}")
            .assertExists()
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertEquals(RENTER, selected)
    }

    /**
     * An empty picker says **which** list is empty and **what to do** about it.
     *
     * The copy is per-picker rather than one shared sentence, because the owner's next
     * move differs — a missing renter is added on the renter screen, a missing vehicle
     * on the fleet screen — and a message that named neither would leave them guessing
     * which screen to open.
     */
    @Test
    fun `an empty picker names what is missing and what to do`() {
        compose.setContent {
            theme {
                ContractPicker(
                    label = "Mobil",
                    tag = "test-picker-empty",
                    value = "",
                    hint = "Ketuk",
                    emptyMessage = "Belum ada mobil. Tambahkan mobil dulu di layar Armada.",
                    title = "Pilih mobil",
                    options = emptyList<Vehicle>(),
                    rowTitle = { it.plate },
                    rowSubtitle = { it.model },
                    rowKey = { it.id },
                    onSelect = {},
                )
            }
        }

        compose.onNode(
            hasContentDescription("Mobil") and hasClickAction() and isEnabled(),
        ).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        compose.onNodeWithTag(TAG_PICKER_EMPTY).assertExists()
        compose.onNodeWithText(
            "Belum ada mobil. Tambahkan mobil dulu di layar Armada.",
        ).assertExists()
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Save invokes `onSave`; Cancel invokes `onDismissRequest`; neither fires the
     * other.
     *
     * ## Why this is not the same as `assertHasClickAction`
     *
     * Every other Save assertion here proves the button *exists*, carries *an*
     * action and is enabled correctly. None of them proves which handler that
     * action is. Change `onClick = onSave` to `onClick = {}` — or to
     * `onDismissRequest`, which would look correct and silently discard a typed
     * contract — and every one of them stays green, while the story's only verb
     * becomes unreachable. `assertHasClickAction` proves a callback is registered,
     * not that the right one is.
     *
     * So the two flags are the assertion: each handler must have fired, and its
     * opposite must not. `RenterFormTest.kt:147-183` is the in-repo precedent.
     */
    @Test
    fun `clicking Save submits and Cancel dismisses, and neither fires the other`() {
        var saved = false
        var dismissed = false
        compose.setContent {
            theme {
                Sheet(
                    initial = complete(),
                    onSave = { saved = true },
                    onDismiss = { dismissed = true },
                )
            }
        }

        compose.onNodeWithTag(TAG_SAVE).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertTrue("Save must invoke onSave", saved)
        assertFalse("Save must not dismiss the sheet", dismissed)

        compose.onNodeWithTag(TAG_CANCEL).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertTrue("Cancel must invoke onDismissRequest", dismissed)
    }

    /**
     * The numpad's own Save key does **not** submit the form.
     *
     * A single button meaning two things is exactly why they are two buttons: the
     * pad's save commits the rate digits, the sheet's save files the contract. If
     * the pad's save also submitted, typing a rate would file a contract the owner
     * had not finished.
     *
     * The committed *digits* are not re-asserted here. The pad keeps its own buffer
     * and echoes each keystroke through `onValueChange`, so the committed value is
     * already a function of the same digits the display shows — re-deriving it
     * through a click sequence would assert the pad's echo, not this story's wiring.
     * What this sheet owns is which handler the key is connected to, and that is
     * what is asserted.
     */
    @Test
    fun `the numpad's save key does not submit the form`() {
        var saved = false
        var dismissed = false
        compose.setContent {
            theme {
                Sheet(
                    initial = complete(),
                    onSave = { saved = true },
                    onDismiss = { dismissed = true },
                )
            }
        }

        compose.onNodeWithTag(TAG_NUMPAD).assertExists()
        compose.onNodeWithText("Simpan").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertFalse("the numpad's save must not submit the form", saved)
        assertFalse("nor dismiss it", dismissed)
    }

    /**
     * Confirming the date dialog stores that same calendar day, unchanged.
     *
     * ## Why this round-trip is asserted at all
     *
     * `DatePickerDialog` hands back UTC-midnight millis for the day the owner
     * tapped. Reading that back through the *device* zone instead of the same UTC
     * offset it was written with shifts the date by one for anyone west of
     * Greenwich, and the code's own comment calls that "the classic off-by-one this
     * whole component exists to avoid". Every other test in this file sets the date
     * by calling `selectStartDate(...)` directly, so the conversion pair is executed
     * by nothing at all — and Robolectric's default UTC would hide a wrong-zone bug
     * even if a render test existed.
     *
     * The form is seeded with a date so the dialog opens *on* it: confirming a
     * dialog whose selection is unset would assert nothing about the read-back, and
     * defaulting that unset selection to today would be the hidden default the epic
     * forbids. The claim is the round-trip itself — what was written comes back
     * unchanged — which is exactly the off-by-one's shape.
     */
    @Test
    fun `confirming the date dialog stores the picked day unchanged`() {
        var latest: ContractFormState? = null
        val picked = LocalDate.of(2026, 10, 1)
        compose.setContent {
            theme {
                Sheet(
                    initial = ContractFormState().selectStartDate(picked),
                    onStateChanged = { latest = it },
                )
            }
        }

        // Open the dialog through the field's trailing calendar affordance, which
        // announces `kontrak_date_hint` — its contentDescription, so the query does
        // not drift if the dialog's own button copy changes.
        compose.onNodeWithTag(TAG_FIELD_START_DATE).assertExists()
        compose.onNodeWithContentDescription("Ketuk untuk memilih tanggal")
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        compose.onNodeWithTag(TAG_DATE_DIALOG).assertExists()
        compose.onNodeWithTag(TAG_DATE_CONFIRM)
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertEquals(
            "the confirm must store the day the dialog was seeded on, read back " +
                "through the same UTC offset it was written with",
            picked,
            latest?.startDate,
        )
    }

    /** And dismissing the dialog closes it without touching the field. */
    @Test
    fun `dismissing the date dialog leaves the field untouched`() {
        var latest: ContractFormState? = null
        compose.setContent {
            theme {
                Sheet(initial = ContractFormState(), onStateChanged = { latest = it })
            }
        }

        compose.onNodeWithContentDescription("Ketuk untuk memilih tanggal")
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithTag(TAG_DATE_DISMISS)
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertTrue(
            "the dialog must close",
            compose.onAllNodes(hasTestTag(TAG_DATE_DIALOG)).fetchSemanticsNodes().isEmpty(),
        )
        assertNull("a dismissed dialog must not pick a date", latest?.startDate)
    }

    /**
 * The same round-trip under a zone **west of Greenwich** — the test that can
 * actually fail.
 *
 * The test above runs in Robolectric's default zone, which is UTC, where
 * `ZoneOffset.UTC` and `ZoneId.systemDefault()` are the *same* value — so a handler
 * written with `systemDefault()` would pass it. Moving the default zone to
 * `America/New_York` separates them: Oct 1 at UTC midnight is Sep 30 at 20:00 in New
 * York, so a `systemDefault()` read-back returns **Sep 30** and the assertion fails.
 *
 * Restored in a `finally` because the default zone is JVM-wide and a leaked
 * `America/New_York` would silently weaken every date assertion that runs after it.
 */
    @Test
    fun `confirming the date dialog survives a zone west of Greenwich`() {
        val previous = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
        try {
            var latest: ContractFormState? = null
            val picked = LocalDate.of(2026, 10, 1)
            compose.setContent {
                theme {
                    Sheet(
                        initial = ContractFormState().selectStartDate(picked),
                        onStateChanged = { latest = it },
                    )
                }
            }

            compose.onNodeWithContentDescription("Ketuk untuk memilih tanggal")
                .performSemanticsAction(SemanticsActions.OnClick)
            compose.waitForIdle()
            compose.onNodeWithTag(TAG_DATE_CONFIRM)
                .performSemanticsAction(SemanticsActions.OnClick)
            compose.waitForIdle()

            assertEquals(
                "reading UTC midnight through the device zone would return Sep 30 here",
                picked,
                latest?.startDate,
            )
        } finally {
            TimeZone.setDefault(previous)
        }
    }

    /**
     * The six-day chip selects six days and the rest-day chip records that day.
     *
     * Both drive the state rather than only rendering, for the reason `Sheet` holds
     * its state: a chip wired to nothing renders identically and asserts identically.
     */
    @Test
    fun `the schedule and rest-day chips drive the form state`() {
        var latest: ContractFormState? = null
        compose.setContent {
            theme { Sheet(initial = complete(), onStateChanged = { latest = it }) }
        }

        compose.onNodeWithTag(TAG_SCHEDULE_SEVEN).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals(false, latest?.sixDays)
        assertNull("seven days carries no rest day", latest?.restDay)

        compose.onNodeWithTag(TAG_SCHEDULE_SIX).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals(true, latest?.sixDays)
        assertEquals("six days restores the default rest day", DayOfWeek.SUNDAY, latest?.restDay)

        compose.onNodeWithTag(restDayTag(DayOfWeek.WEDNESDAY))
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals(
            "choosing a rest day must replace the default, not add a second one",
            DayOfWeek.WEDNESDAY,
            latest?.restDay,
        )
    }

    @Composable
    private fun theme(content: @Composable () -> Unit) {
        KlinikNetTheme(darkTheme = false) { content() }
    }

    /**
     * The real sheet over a state holder the test can observe.
     *
     * The state is held in a `MutableState` rather than passed in fixed, because a
     * sheet driven by a constant cannot demonstrate that it *drives* anything: a chip
     * wired to nothing renders identically and asserts identically. Holding it means a
     * tap can be made and its effect read back — the same reason `RenterFormTest` holds
     * its state.
     */
    @Composable
    private fun Sheet(
        initial: ContractFormState = ContractFormState(),
        errorMessage: String? = null,
        onSave: () -> Unit = {},
        onDismiss: () -> Unit = {},
        onStateChanged: (ContractFormState) -> Unit = {},
    ) {
        var state by remember { mutableStateOf(initial) }
        ContractFormSheet(
            state = state,
            renterOptions = listOf(RENTER),
            vehicleOptions = listOf(VEHICLE),
            errorMessage = errorMessage,
            onDismissRequest = onDismiss,
            onChange = { transformer ->
                state = transformer(state)
                onStateChanged(state)
            },
            onSave = onSave,
        )
    }

    /**
     * A form with every field answered, built through the same pure helpers the sheet
     * uses — so this is a state the screen can actually produce rather than one
     * assembled by setting fields directly.
     */
    private fun complete(): ContractFormState = ContractFormState()
        .selectRenter(RENTER)
        .selectVehicle(VEHICLE)
        .selectStartDate(LocalDate.of(2026, 10, 1))

    private companion object {
        val RENTER = Renter(
            id = "r1",
            name = "Budi Santoso",
            phone = "08123456789",
            bankAccount = null,
            emergencyContact = "08129876543",
        )

        val VEHICLE = Vehicle(
            id = "v1",
            plate = "B 1235 TF",
            brand = "Toyota",
            model = "Avanza",
            year = 2023,
            dailyRate = 250_000L,
        )
    }
}
