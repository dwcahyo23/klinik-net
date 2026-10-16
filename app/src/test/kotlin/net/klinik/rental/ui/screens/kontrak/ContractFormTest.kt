package net.klinik.rental.ui.screens.kontrak

import net.klinik.rental.domain.model.Renter
import net.klinik.rental.domain.model.Vehicle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The form's own rules, as pure functions.
 *
 * ## Why this file has no runner and no Android dependency
 *
 * Every rule the open-contract form enforces on its own — the schedule pair, the
 * rest-day default, the rate prefill, the missing-field order, whether Save is
 * allowed — is a top-level pure function over [ContractFormState]. So they are
 * asserted here with no device, no database and no ViewModel, and the ViewModel's
 * handlers are one-line delegations to them. That is what makes the
 * `OPEN_NO_SELECTION` and `OPEN_RATE_ZERO` rows' *form* half testable at all, and it
 * is the same split `PenyewaFilterTest` makes for the search predicate.
 *
 * The other half of those rows — that nothing is written — is
 * `ContractRepositoryTest`'s, because only a real database can refuse a write.
 */
class ContractFormTest {

    // -------------------------------------------------------------- the schedule

    /**
     * A fresh form is a six-day week resting on Minggu.
     *
     * Six days is the common case for this fleet, and the AC names Minggu as the
     * rest-day default. A form that opened on seven days would make the rest-day
     * picker absent, which the AC explicitly expects to be visible.
     */
    @Test
    fun `a fresh form is six days resting on Minggu`() {
        val form = ContractFormState()
        assertTrue("six days is the default schedule", form.sixDays)
        assertEquals(DayOfWeek.SUNDAY, form.restDay)
    }

    /** Choosing seven days clears the rest day — `null` *is* the seven-day week. */
    @Test
    fun `choosing seven days clears the rest day`() {
        val form = ContractFormState().withSchedule(sixDays = false)
        assertFalse(form.sixDays)
        assertNull(
            "a rest day left set while seven days is chosen would be a second, " +
                "contradicting encoding of the same fact",
            form.restDay,
        )
    }

    /**
     * And going back to six days restores Minggu rather than leaving it empty.
     *
     * A six-day week always has a day off, so a picker that opened with nothing
     * selected would be a form the owner cannot complete by reading it.
     */
    @Test
    fun `going back to six days restores the default rest day`() {
        val form = ContractFormState()
            .withSchedule(sixDays = false)
            .withSchedule(sixDays = true)
        assertTrue(form.sixDays)
        assertEquals(DayOfWeek.SUNDAY, form.restDay)
    }

    /**
     * Toggling to seven days and back lands on the default rest day again.
     *
     * The seven-day schedule has no rest day to carry over — `null` *is* the
     * seven-day week — so the day the owner had picked is not remembered across the
     * toggle. That is deliberate rather than an omission: remembering it would need a
     * third field holding a value the form cannot currently represent, and hidden
     * state that disagrees with the visible picker is exactly what the epic context's
     * `workSchedule` + `offDay` pair was rejected for. The picker always shows what
     * will be stored.
     */
    @Test
    fun `toggling to seven days and back lands on the default rest day`() {
        val form = ContractFormState()
            .selectRestDay(DayOfWeek.WEDNESDAY)
            .withSchedule(sixDays = false)
            .withSchedule(sixDays = true)
        assertEquals(DayOfWeek.SUNDAY, form.restDay)
    }

    /**
     * Exactly one day is selectable, structurally.
     *
     * `restDay` is a single nullable `DayOfWeek`, so there is no way to express two
     * selections and no validation to forget — which is the property the AC's
     * "exactly one rest day" is really about.
     */
    @Test
    fun `exactly one rest day is selectable at a time`() {
        var form = ContractFormState()
        DayOfWeek.entries.forEach { day ->
            form = form.selectRestDay(day)
            assertEquals("selecting $day must replace the previous choice", day, form.restDay)
        }
    }

    /**
     * Tapping the already-selected day is a no-op rather than a deselect: a six-day
     * week with no rest day is not a schedule this app can represent, and a chip row
     * that could end up with nothing selected would let the owner reach a state the
     * model rejects.
     */
    @Test
    fun `tapping the selected rest day does not deselect it`() {
        val form = ContractFormState().selectRestDay(DayOfWeek.SUNDAY)
        assertEquals(DayOfWeek.SUNDAY, form.restDay)
    }

    /** And selecting a rest day while seven days is chosen does nothing at all. */
    @Test
    fun `a rest day cannot be set on a seven day week`() {
        val form = ContractFormState().withSchedule(sixDays = false).selectRestDay(DayOfWeek.MONDAY)
        assertNull(form.restDay)
    }

    // ------------------------------------------------------------- the rate prefill

    /**
     * The AC: opening the form for a vehicle prefills the rate from *that vehicle*.
     *
     * The rate is the price the owner would otherwise retype, and it is the one field
     * on this form that is money.
     */
    @Test
    fun `selecting a vehicle prefills the rate from that vehicle`() {
        val form = ContractFormState().selectVehicle(vehicle(rate = 275_000L))
        assertEquals("275000", form.rateDigits)
        assertEquals(275_000L, form.parsedRate())
    }

    /**
     * And selecting a *different* vehicle replaces the digits rather than keeping
     * them.
     *
     * This is the direction that matters: the digits on screen belonged to the
     * previous car's rate, so keeping them would silently carry one vehicle's price
     * onto another — a wrong number that looks exactly like a right one.
     */
    @Test
    fun `selecting another vehicle replaces the previous rate`() {
        val form = ContractFormState()
            .selectVehicle(vehicle(id = "v1", plate = "B 1235 TF", rate = 250_000L))
            .selectVehicle(vehicle(id = "v2", plate = "B 9876 AB", rate = 400_000L))

        assertEquals("v2", form.vehicleId)
        assertEquals("B 9876 AB", form.vehiclePlate)
        assertEquals("400000", form.rateDigits)
    }

    /**
     * The prefilled rate stays *editable*, which is what makes a negotiated rate
     * recordable — the AC allows one explicitly.
     *
     * A locked field would make the common case work and the negotiated one
     * impossible, and the owner would have no way to record what was actually agreed.
     */
    @Test
    fun `the prefilled rate can be edited to a negotiated value`() {
        val form = ContractFormState()
            .selectVehicle(vehicle(rate = 250_000L))
            .copy(rateDigits = "310000")

        assertEquals(310_000L, form.parsedRate())
    }

    /** Selecting a vehicle records its id and plate, so the field can show it. */
    @Test
    fun `selecting a vehicle records its id and plate`() {
        val form = ContractFormState().selectVehicle(vehicle(id = "v9", plate = "B 9 XX"))
        assertEquals("v9", form.vehicleId)
        assertEquals("B 9 XX", form.vehiclePlate)
    }

    /** The same for the renter, which carries no rate and so is simpler. */
    @Test
    fun `selecting a renter records its id and name`() {
        val form = ContractFormState().selectRenter(renter(id = "r9", name = "Siti Aminah"))
        assertEquals("r9", form.renterId)
        assertEquals("Siti Aminah", form.renterName)
    }

    /** And a selection clears the refusal on its own field, because it *is* the fix. */
    @Test
    fun `a selection clears its field's refusal`() {
        val refused = ContractFormState(
            renterError = "Wajib diisi.",
            vehicleError = "Wajib diisi.",
            rateError = "Tarif harian harus lebih dari nol.",
        )

        assertNull(refused.selectRenter(renter()).renterError)
        // The vehicle's refusal goes with the vehicle's selection…
        val withVehicle = refused.selectVehicle(vehicle())
        assertNull(withVehicle.vehicleError)
        // …and it takes the rate refusal with it, because the digits it replaced
        // were the refused ones.
        assertNull(withVehicle.rateError)
    }

    // -------------------------------------------------------- the missing fields

    /**
     * OPEN_NO_SELECTION: a form with nothing filled in is missing its renter first,
     * then its vehicle, then its date, then its rate.
     *
     * The order is the order the fields appear on the sheet, top to bottom, so the
     * message lands on the first thing the owner would notice rather than on
     * whichever field a dictionary happened to yield.
     */
    @Test
    fun `the missing-field order follows the sheet from top to bottom`() {
        assertEquals(ContractField.Renter, ContractFormState().firstMissingField())

        val withRenter = ContractFormState().selectRenter(renter())
        assertEquals(ContractField.Vehicle, withRenter.firstMissingField())

        val withVehicle = withRenter.selectVehicle(vehicle())
        assertEquals(ContractField.StartDate, withVehicle.firstMissingField())

        val withDate = withVehicle.selectStartDate(LocalDate.of(2026, 10, 1))
        assertEquals(ContractField.Rate, withDate.copy(rateDigits = "").firstMissingField())
    }

    /**
     * A rate of zero is *missing* in the sense that matters: `parseRupiahInput`
     * returns `null` for it, so the field is refused with the rate message rather
     * than being accepted as a value.
     */
    @Test
    fun `a zero rate counts as missing`() {
        val form = complete().copy(rateDigits = "0")
        assertEquals(ContractField.Rate, form.firstMissingField())
        assertFalse(form.canSave)
    }

    /** And so does a negative one, which the parser cannot even represent. */
    @Test
    fun `a negative rate counts as missing`() {
        val form = complete().copy(rateDigits = "-1")
        assertNull(form.parsedRate())
        assertEquals(ContractField.Rate, form.firstMissingField())
    }

    /** A complete form is missing nothing, and Save is allowed. */
    @Test
    fun `a complete form allows Save`() {
        val form = complete()
        assertNull(form.firstMissingField())
        assertTrue(form.canSave)
        assertEquals(250_000L, form.parsedRate())
    }

    /**
     * A six-day week with no rest day is incomplete — the one schedule state the
     * model cannot represent, and therefore the one the form has to refuse.
     *
     * Reachable only by hand-building the state, since `withSchedule` and
     * `selectRestDay` both keep it filled in; it is asserted because a state the
     * model rejects must not be a state Save accepts.
     */
    @Test
    fun `a six day week without a rest day is incomplete`() {
        val form = complete().copy(restDay = null)
        assertEquals(ContractField.RestDay, form.firstMissingField())
        assertFalse(form.canSave)
    }

    /**
     * And a seven-day week with a rest day set is *not* refused on that account: the
     * model reads `restDay` alone, so `withSchedule` is what keeps the two in step
     * and the completeness check does not need to police it.
     */
    @Test
    fun `a seven day week is complete without a rest day`() {
        val form = complete().withSchedule(sixDays = false)
        assertNull(form.firstMissingField())
        assertTrue(form.canSave)
    }

    // ------------------------------------------------------------- the start date

    /**
     * A start date in the past is accepted by the form, and stored verbatim.
     *
     * Contracts that began before this app existed are real (FR-7), so there is
     * deliberately no range check on this field — the only impossible date would be
     * one after the contract ends, and this form has no end-date field at all.
     */
    @Test
    fun `a start date in the past is accepted`() {
        val past = LocalDate.of(2019, 5, 4)
        val form = complete().selectStartDate(past)

        assertEquals(past, form.startDate)
        assertTrue(form.canSave)
    }

    /** And a future one is accepted too — the owner may be booking ahead. */
    @Test
    fun `a future start date is accepted`() {
        val future = LocalDate.of(2030, 1, 1)
        assertEquals(future, complete().selectStartDate(future).startDate)
    }

    // ------------------------------------------------------- the projection

    /**
     * The form projects into the model with each field going to its own argument.
     *
     * ## Why this is asserted field by field
     *
     * This is the one place the form's five fields become the model's five arguments,
     * and a swapped pair here writes a contract against the wrong car or the wrong
     * person — a wrong row that looks exactly like a right one, and one the unique
     * indexes would *accept*, because they only check that the slots are free. So
     * every argument is asserted against a distinct value, which is what makes a swap
     * detectable rather than merely unlikely.
     */
    @Test
    fun `a complete form projects into the model field by field`() {
        val contract = complete().toContract(id = "c1")

        assertNotNull("a complete form must project", contract)
        assertEquals("c1", contract!!.id)
        assertEquals("r1", contract.renterId)
        assertEquals("v1", contract.vehicleId)
        assertEquals(LocalDate.of(2026, 10, 1), contract.startDate)
        assertEquals(250_000L, contract.dailyRate)
        assertEquals(DayOfWeek.SUNDAY, contract.restDay)
        // This story never opens a contract that has already ended.
        assertNull(contract.endDate)
        assertTrue("a freshly opened contract is open", contract.isOpen)
    }

    /** And an incomplete form projects to nothing, rather than to a partial row. */
    @Test
    fun `an incomplete form projects to nothing`() {
        assertNull(ContractFormState().toContract(id = "c1"))
        assertNull(ContractFormState().selectRenter(renter()).toContract(id = "c1"))
        assertNull(complete().copy(rateDigits = "0").toContract(id = "c1"))
        assertNull(complete().copy(restDay = null).toContract(id = "c1"))
    }

    /**
     * The projection reconciles the schedule's two representations, so a seven-day
     * week stores no rest day whatever the picker last held.
     *
     * `withSchedule` already keeps the two in step, so this asserts the *second* line
     * of defence: even a hand-built state with `sixDays = false` and a rest day still
     * set projects a seven-day contract. Two encodings of one fact cannot disagree in
     * a stored row.
     */
    @Test
    fun `a seven day week projects with no rest day even if one is set`() {
        val form = complete().copy(sixDays = false, restDay = DayOfWeek.WEDNESDAY)
        assertNull(form.toContract(id = "c1")!!.restDay)
    }

    /** And a six-day week projects the day the picker shows. */
    @Test
    fun `a six day week projects its rest day`() {
        val form = complete().selectRestDay(DayOfWeek.THURSDAY)
        assertEquals(DayOfWeek.THURSDAY, form.toContract(id = "c1")!!.restDay)
    }

    /** The id is passed in, so the projection stays pure and a test can pin it. */
    @Test
    fun `the id comes from the caller`() {
        assertEquals("pinned", complete().toContract(id = "pinned")!!.id)
    }

    // ------------------------------------------------------------------ helper

    /**
     * A form with every field answered: a renter, a vehicle, a date and a rate.
     *
     * Built through the same pure helpers the sheet uses, so this is a state the
     * screen can actually produce rather than one assembled by hand — a fixture that
     * set the fields directly could describe a form the sheet cannot reach.
     */
    private fun complete(): ContractFormState = ContractFormState()
        .selectRenter(renter())
        .selectVehicle(vehicle())
        .selectStartDate(LocalDate.of(2026, 10, 1))

    private fun renter(id: String = "r1", name: String = "Budi Santoso") = Renter(
        id = id,
        name = name,
        phone = "08123456789",
        bankAccount = null,
        emergencyContact = "08129876543",
    )

    private fun vehicle(
        id: String = "v1",
        plate: String = "B 1235 TF",
        rate: Long = 250_000L,
    ) = Vehicle(
        id = id,
        plate = plate,
        brand = "Toyota",
        model = "Avanza",
        year = 2023,
        dailyRate = rate,
    )
}
