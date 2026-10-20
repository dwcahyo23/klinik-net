package net.klinik.rental.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.klinik.rental.data.db.KlinikDatabase
import net.klinik.rental.data.db.dao.ContractDao
import net.klinik.rental.data.db.dao.ServiceExceptionDao
import net.klinik.rental.data.db.entity.ContractEntity
import net.klinik.rental.data.db.entity.ServiceExceptionEntity
import net.klinik.rental.domain.model.Contract
import net.klinik.rental.domain.model.ServiceException
import net.klinik.rental.domain.parse.requirePositiveRate
import net.klinik.rental.domain.validation.ContractAlreadyActiveException
import net.klinik.rental.domain.validation.ContractEndBeforeStartException
import net.klinik.rental.domain.validation.ContractEndInFutureException
import net.klinik.rental.domain.validation.ContractNotOpenException
import net.klinik.rental.domain.validation.DuplicateServiceExceptionDateException
import net.klinik.rental.domain.validation.ServiceExceptionBeforeContractStartException
import java.time.LocalDate
import java.util.UUID

/**
 * The only way the UI reaches contract data — and, since Story 2.6, the only way it
 * reaches the days a contract was **not** charged.
 *
 * ## What this layer owes the layers above
 *
 * Typed domain exceptions, never a `Result<_, String>`:
 *
 *  - [ContractAlreadyActiveException] — the vehicle or the renter already has an
 *    open contract
 *  - [ContractNotOpenException] — the contract has ended, so its exceptions are
 *    read-only (add *and* remove; they are the same fact)
 *  - [DuplicateServiceExceptionDateException] — this contract already carries an
 *    exception for that date
 *  - [ContractEndBeforeStartException] — the end date precedes the contract's own
 *    start date, which is structurally impossible
 *  - [ContractEndInFutureException] — the end date is after the day the caller says
 *    it is, which would end a running rental on the spot
 *
 * The settlement refusals — [SettlementBeforeStartException],
 * [SettlementInFutureException] and [SettlementAlreadySettledException] — live in
 * `Settlement.kt` beside the settlement's own types rather than in `domain/validation/`,
 * because they are not contract-shape rules: each is about the one marker FR-9b writes.
 *
 * [ContractNotOpenException] is used by two paths for the same reason: an ended
 * contract refuses *both* exception edits and a second end, because "this contract
 * has ended" is one fact and two exception types would let one path forget it.
 *
 * The epic context forbids carrying a domain message in a `Result`, because a
 * `String` that has already been rendered into Indonesian cannot be re-checked,
 * re-translated or re-worded. A typed exception keeps the *fact* and lets
 * `KontrakViewModel` choose the copy at the moment it is shown.
 *
 * So this class does **not** build Indonesian text. That is the deliberate
 * omission: the moment a repository returns a message, two screens can show two
 * different sentences for the same failure, and a test can no longer assert which
 * one is correct.
 *
 * ## What it never does
 *
 * It never exposes a DAO. `ui/` cannot reach [ContractDao] or [ServiceExceptionDao],
 * so it cannot bypass the one-active check or the duplicate-date check by calling
 * `insert` directly.
 *
 * ## Why this class writes two tables rather than there being a second repository
 *
 * A `ServiceExceptionRepository` would need the [ServiceExceptionDao], a
 * [ContractDao] to answer "is this contract still open?", its own `inTransaction`,
 * its own `newExceptionId`, a fifth constructor parameter on `KontrakViewModel`,
 * and a new line in `createKontrakViewModel`. This class already holds the contract
 * DAO, already owns the transaction helper, already owns the id generator, and the
 * ViewModel already holds it — so extending it leaves `MainActivity`'s wiring
 * **byte-for-byte unchanged**, which is the real measure of how little this touches.
 *
 * The cost is that one class writes two tables, and that is paid honestly here
 * rather than hidden. The "one DAO owns one table" rule is untouched:
 * [ServiceExceptionDao] remains the only thing that moves exception rows and this
 * repository still never exposes it. The concept being kept whole is "a contract,
 * and the days not billed on it"; splitting it back out later is a mechanical move.
 *
 * ## Why the one-active rule needs both a pre-check and an index
 *
 * The pre-check exists so the owner finds out before leaving the sheet, and it is
 * deliberately *not* the guarantee: two callers can both pass it before either
 * writes. Only the two `UNIQUE` indexes settle that, which is why [open] runs as
 * one transaction and why `ContractRepositoryTest` bypasses the pre-check on
 * purpose in its `INDEX_REFUSES_SECOND` row.
 *
 * The vehicle side is checked first when both are busy, so the refusal is
 * deterministic rather than dependent on which query happened to return first. The
 * same argument gives [addServiceException] its order: read-only first, then the
 * impossible date, then the duplicate.
 */
class ContractRepository(
    private val dao: ContractDao,
    private val serviceExceptions: ServiceExceptionDao,
    private val database: KlinikDatabase? = null,
) {

    /**
     * Every contract, observed.
     *
     * The `Flow` comes straight from Room's invalidation tracker, so a contract
     * opened by the form re-emits here and the list updates with no refresh and no
     * manual signal. Nothing in this class writes to the flow — the epic context
     * forbids a `Flow` write, and a write here would need its own invalidation
     * path, which is the second refresh mechanism that can disagree with Room's.
     *
     * The names the list shows are *not* joined here. The ViewModel already
     * observes the renters and the vehicles, so it resolves `renterId` and
     * `vehicleId` against those flows in Kotlin. A `@Query` with two JOINs would
     * need a new row POJO and would put a second way to read renters and vehicles
     * next to `observeRenters()`/`observeVehicles()` — the two-readers problem AD-5
     * exists to prevent. `KendaraanViewModel` already combines several flows in
     * Kotlin, and this is the same pattern.
     */
    fun observeContracts(): Flow<List<Contract>> =
        // A lambda rather than `ContractEntity::toDomain`: `toDomain` is a member
        // extension, and Kotlin does not allow a callable reference to one.
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    /**
     * Opens [contract], enforcing every write rule.
     *
     * The order of the checks matters and is not arbitrary:
     *
     *  1. **reject a non-positive rate** — [requirePositiveRate] delegates to
     *     `InvalidAmountException.requireValid`, the same bound `Vehicle`,
     *     `Payment` and `Allocation` use. This is the data-layer half of NFR-6:
     *     the form's check is a courtesy, this one is the rule
     *  2. **pre-check the vehicle's active slot** — so the owner finds out before
     *     leaving the sheet, and so the refusal can name the plate
     *  3. **pre-check the renter's active slot** — the same, naming the person
     *  4. **insert** — and let the two `UNIQUE` indexes refuse if two callers raced
     *     past steps 2 and 3
     *
     * Steps 2 to 4 run as one transaction, so the pre-checks and the insert see a
     * single snapshot. Without it, a contract opened by another caller between the
     * check and the insert would be reported as a raw `SQLiteConstraintException`
     * rather than as the named refusal — the index would still hold, but the
     * message would be about a constraint instead of a car.
     *
     * A start date in the past is accepted and stored verbatim: contracts that
     * began before this app existed are real (FR-7), and [Contract] has already
     * refused anything that is structurally impossible.
     *
     * @throws ContractAlreadyActiveException when the vehicle or the renter already
     *   has an open contract. Nothing is written when this throws.
     * @throws InvalidAmountException when [contract]'s rate is zero or negative.
     * @throws IllegalArgumentException when [contract] is not open — that is,
     *   when [Contract.endDate] is set. Deliberately not
     *   [ContractNotOpenException]: that type reads "this contract has already
     *   ended", which is the *result* an open call could see after a concurrent
     *   end. This guard reads "the caller passed an ended `Contract`", which
     *   is an argument the standard type names correctly without a new domain
     *   concept. No caller in the app reaches this today — `endContract` is the
     *   only writes path that holds an ended `Contract`, and it has its own
     *   repository method — but the require makes that an invariant rather than
     *   a coincidence, so a future caller cannot read past it.
     */
    suspend fun open(contract: Contract) {
        // 1. Fail before the database rather than after. `Contract`'s constructor
        // would also refuse this, but checking here means the rule holds for a
        // caller that has a form and no `Contract` yet.
        requirePositiveRate(contract.dailyRate)

        // 1a. And refuse an `open` call whose contract is already ended. `Contract.isOpen`
        // is `endDate == null` (`Contract.kt:143`), and `toEntity()` derives both active
        // columns from it — so writing an ended contract through `open` would insert a
        // row with both slots NULL and the unique indexes would never see it. That is the
        // structural failure mode the indexes exist to prevent, reached through the wrong
        // entry point. `IllegalArgumentException`, not `ContractNotOpenException`, for the
        // reason recorded on the `@throws` block above.
        require(contract.isOpen) {
            "contract ${contract.id} cannot be opened: endDate is set"
        }

        inTransaction {
            // 2. The vehicle side first, so a vehicle *and* renter that are both
            // busy produce one deterministic answer rather than whichever query
            // the executor happened to run first.
            val vehicleHolder = dao.findActiveByVehicleId(contract.vehicleId)
            if (vehicleHolder != null) {
                throw ContractAlreadyActiveException(
                    side = ContractAlreadyActiveException.Side.Vehicle,
                    // The plate read back from the vehicle row, not the id: the
                    // message names a car the owner sees in the fleet list.
                    blockingName = dao.findOpenPlateFor(contract.vehicleId)
                        ?: contract.vehicleId,
                )
            }

            // 3. Then the renter's.
            val renterHolder = dao.findActiveByRenterId(contract.renterId)
            if (renterHolder != null) {
                throw ContractAlreadyActiveException(
                    side = ContractAlreadyActiveException.Side.Renter,
                    blockingName = dao.findOpenRenterNameFor(contract.renterId)
                        ?: contract.renterId,
                )
            }

            // 4. Insert, translating the index's refusal.
            insertEntity(contract)
        }
    }

    /**
     * Ends the contract with [contractId] on [endDate], freeing its vehicle and its
     * renter.
     *
     * ## Why this is one row and one statement
     *
     * The AC asks that the vehicle "otomatis tampil sebagai tersedia — tidak ada tombol
     * sinkronisasi manual", and the mechanism is that there is nothing to synchronise.
     * `ContractEntity.toEntity()` derives `activeVehicleId` and `activeRenterId` from
     * `isOpen`, and `isOpen` is `endDate == null` — so nulling both *is* setting the end
     * date. There is no second writer that could set the date and forget a slot, and no
     * status column that could disagree with the date, so "the contract ended but the car
     * still reads as rented" is a state this code cannot produce rather than one a test
     * has to catch.
     *
     * Releasing the slots is also what lets the same vehicle and the same renter be
     * rented again: the two `UNIQUE` active indexes answer on the column, and SQLite
     * treats `NULL`s as distinct, so a cleared slot is a free slot.
     *
     * ## Why surplus needs nothing from this method
     *
     * The AC says a surplus "tidak hilang — menjadi Kredit pada Kontrak tersebut". Credit
     * is **not** a column and **not** computed here: it is the `chargeDate = null`
     * allocation row (AD-3, `Allocation`'s KDoc), re-derived every period from stored
     * rows. This method writes exactly one row and touches no other table, and no
     * `ON DELETE CASCADE` on `contract` reaches a money record — so there is no code
     * here that could drop the surplus. A credit column would be a second source of
     * truth for a fact the engine already derives, and the PRD forbids it twice.
     *
     * The same single-row fact discharges "kontrak yang sudah berakhir tetap dihitung
     * tunggakannya": the row is neither deleted nor filtered out, so whatever reads
     * arrears later still sees every day up to [endDate].
     *
     * ## Why [endDate] is not nullable
     *
     * `Contract.endDate` is `null` while a contract is running, so a `LocalDate?`
     * parameter would make "no date given" indistinguishable from "running" on the one
     * method whose entire job is to stop it running. Non-null here makes a blank field
     * a compile error for every other caller, and the sheet's own refusal stays in the
     * UI where the blank field is.
     *
     * ## Why the row is re-read rather than taken from the caller
     *
     * The stored `startDate` is what the before-start refusal names, and the frozen
     * rate and schedule come from storage rather than from a `Contract` a caller may
     * have been holding for an hour. The only field this method takes from the caller
     * is the date they chose.
     *
     * ## Why "today" arrives as [asOf] rather than from a clock here
     *
     * AD-8: this class keeps no clock. A `LocalDate.now()` inside the repository would be
     * a second source of "today" that a test cannot pin, and the refusal it drives names
     * that day — so the day has to be the caller's, exactly as `KontrakViewModel` already
     * passes `LocalDate.now(clock)` for a settlement. [asOf] is therefore a parameter, not
     * a constructor argument: the class stays three-argument and every existing
     * construction site is untouched.
     *
     * @param asOf the caller's "today". An [endDate] strictly after it is refused, because
     *   `isOpen` is `endDate == null` and any written date ends the contract immediately —
     *   so a future date would free the car and stop the charges today, for a rental still
     *   running. A past date is accepted: a contract that ended before this app existed is
     *   real.
     *
     * @throws ContractNotOpenException when the contract has already ended, or when
     *   nobody holds that id. Nothing is written.
     * @throws ContractEndBeforeStartException when [endDate] precedes the contract's
     *   own stored start date. Nothing is written.
     * @throws ContractEndInFutureException when [endDate] is after [asOf]. Nothing is
     *   written.
     *
     *   **These two overlap, and the start-date one wins.** An [endDate] can be both
     *   before `startDate` and after `asOf` — reachable whenever a contract's own start
     *   date is in the future, which `open` permits. The two `if`s below are ordered, not
     *   independent: the before-start check runs first and wins, so the refusal names the
     *   figure the owner cannot choose again rather than adding a second complaint about
     *   one field. Swapping the two would change the answer with **no compiler signal**,
     *   so the order is part of the contract and `ContractRepositoryTest`'s `ORDER` row
     *   pins it.
     */
    suspend fun endContract(contractId: String, endDate: LocalDate, asOf: LocalDate) {
        inTransaction {
            // 1. The same read-only guard the exception writes use, so "this contract
            // has ended" is one question asked in one place.
            val stored = requireOpenContract(contractId)

            // 2. Before any write. Clamping forward would end the contract on a day the
            // owner never chose, and ending a contract is what frees the car — so the
            // error would surface as a vehicle that reads `tersedia` on the wrong day.
            if (endDate.isBefore(stored.startDate)) {
                throw ContractEndBeforeStartException(
                    contractId = contractId,
                    startDate = stored.startDate,
                )
            }

            // 3. And a future date is refused for the same reason, from the other side.
            // `isOpen` is `endDate == null`, so *any* written date ends the contract
            // immediately: a date six weeks out would free the car today and stop the
            // charges today, for a rental that is still running. Ordered after the
            // start-date check so that when both apply the owner gets the message naming
            // a figure they can act on, rather than a second complaint about the same
            // figure.
            //
            // Note what this makes impossible: a contract whose *start* date is in the
            // future — which `open()` permits, since it guards only the rate — has no end
            // date that satisfies both checks. Anything before its start is refused by
            // check 2; anything from its start onward is still in the future and refused
            // here. So such a contract cannot be ended until its start date arrives, and
            // whichever of the two messages the owner sees names the constraint that
            // actually blocks them.
            //
            // That is the correct outcome under `isOpen = endDate == null`: an open
            // contract occupies its car from the moment it is written, so the car is held
            // for a rental that has not begun — the state the owner chose when they saved
            // a future start date. Cancelling a booking is a different action, and this
            // method is not it.
            if (endDate.isAfter(asOf)) {
                throw ContractEndInFutureException(
                    contractId = contractId,
                    asOf = asOf,
                )
            }

            // 4. Build the domain model so `Contract`'s own `require` is the second
            // guard rather than a copy of it, then project it. `toEntity()` nulls both
            // active columns here, which is what frees the vehicle and the renter.
            dao.update(
                Contract(
                    id = stored.id,
                    renterId = stored.renterId,
                    vehicleId = stored.vehicleId,
                    startDate = stored.startDate,
                    dailyRate = stored.dailyRate,
                    restDay = stored.restDay,
                    endDate = endDate,
                    // Carried, not dropped: ending a contract that was already settled
                    // through an earlier date must not un-settle it. The two actions are
                    // independent — a contract can both end mid-period and have its
                    // earlier days settled offline — and rebuilding the row without this
                    // would make "Akhiri" silently undo "Tandai lunas".
                    settledThroughDate = stored.settledThroughDate,
                ).toEntity(),
            )
        }
    }

    /**
     * Inserts, translating a `UNIQUE` index's refusal into the named exception.
     *
     * This is the half of the one-active rule that cannot be pre-checked away: two
     * callers can both pass the checks above before either writes, and only the
     * index settles it. Android's SQLite reports a unique violation as
     * `SQLiteConstraintException`, whose message names the constraint but is not a
     * stable API — so the slot is re-read and reported from the row that actually
     * holds it, which is what the owner needs to see.
     *
     * The translation is verified, not assumed. Re-reading decides which of the
     * two slots is occupied; if neither is, this is not a one-active violation at
     * all — a primary-key clash or a foreign-key refusal, most likely — and it is
     * rethrown untouched rather than relabelled as a refusal that would send the
     * owner to change a vehicle or a person that was never the problem.
     */
    private suspend fun insertEntity(contract: Contract) {
        try {
            dao.insertContract(contract.toEntity())
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            // The same order as the pre-check, so a raced refusal names the same
            // side the pre-check would have.
            val vehicleHolder = dao.findActiveByVehicleId(contract.vehicleId)
            if (vehicleHolder != null) {
                throw ContractAlreadyActiveException(
                    side = ContractAlreadyActiveException.Side.Vehicle,
                    blockingName = dao.findOpenPlateFor(contract.vehicleId)
                        ?: contract.vehicleId,
                )
            }

            val renterHolder = dao.findActiveByRenterId(contract.renterId)
            if (renterHolder != null) {
                throw ContractAlreadyActiveException(
                    side = ContractAlreadyActiveException.Side.Renter,
                    blockingName = dao.findOpenRenterNameFor(contract.renterId)
                        ?: contract.renterId,
                )
            }

            throw e
        }
    }

    // ------------------------------------------------------ FR-9b: settlement

    /**
     * Marks [contractId]'s charge days at or before [date] as already paid — Story 3.6.
     *
     * ## What this writes, and what it deliberately does not
     *
     * One column on one row. The PRD is explicit that FR-9b *"tidak ada representasi per
     * hari, tidak ada alokasi yang disimpan, dan tidak ada jejak audit selain waktu aksi
     * itu dilakukan"* — so this creates **no** Setoran and **no** allocation row. The
     * settlement takes effect because `PaymentRepository.previewAllocation` narrows the
     * day list through `settledDays`, and a day that is not on the list is a closed day.
     *
     * A fake payment would have been the tempting encoding — one row closing each day —
     * and it would have been wrong twice over: it would put unrecoverable fiction into a
     * table the owner reconciles against a bank statement, and it would leave arrears
     * depending on money that never moved.
     *
     * ## Why `copy` and not `toEntity()`
     *
     * [endContract] projects through the domain model because it changes [endDate], and
     * the active columns derive from that. Settlement changes nothing the model carries,
     * so projecting through it would mean every contract write in this class had to start
     * deciding what a settlement is — and the domain model deliberately has no such field.
     * Copying the stored row keeps the write to the one column it owns.
     *
     * ## Why a before-start date is refused
     *
     * It is structurally inert: `chargeDaysIn` filters `it >= contractStart` before
     * settlement is applied, so such a date closes nothing while looking like it closed
     * something. Refusing names the start date, which is the day the owner is looking
     * for — the same reasoning [addServiceException] gives for the identical check.
     *
     * ## Why a future date is refused
     *
     * Because the marker is not inert. `settledDays` filters every charge day at or before
     * `settledThroughDate`, so a marker six weeks out does not merely store a date — it closes
     * **today's** arrears too, and the rupiah behind them leaves the arrears figure without
     * anyone having been paid. FR-9b marks obligations that are *"sudah tercatat di tempat
     * lain"*, which cannot be true of days that have not happened. Refused rather than
     * clamped: clamping forward would close days the owner never named, which is the same
     * failure [endContract] refuses to commit for an end date.
     *
     * ## Why an ended contract may still be settled
     *
     * Deliberately, and unlike the exception writes. A contract that ended months ago
     * with days already paid elsewhere is exactly the case FR-9b exists for — the owner
     * opens a historic contract precisely because its arrears are fictional. Requiring
     * the contract to be running would make the day-one exit unavailable on the contracts
     * that need it most.
     *
     * The figures for the confirmation live in `PaymentRepository`, which already owns
     * the read chain — this class deliberately takes no second repository, so the two
     * stories' halves stay separate rather than one calling the other.
     *
     * ## Why a backwards date is refused
     *
     * Because FR-9b has no undo, and a backwards write is the one that silently does damage:
     * it would **reopen** every day between the two dates, turning days the owner declared
     * paid back into arrears they thought were gone — with no message, no trace and no way
     * back except hand-editing a row in a database file.
     *
     * The UI hides the settlement verb once a contract carries a marker, so this cannot be
     * reached by the sheet; that is a convenience, not the rule. The rule lives here, because
     * this is the layer that owns it and a second caller would not consult the sheet.
     *
     * ## Why "today" arrives as [asOf] rather than from a clock here
     *
     * The same rule, and for the same reason, as [endContract]: this class keeps no clock
     * (AD-8), so a `LocalDate.now()` inside it would be a second source of "today" that a test
     * cannot pin — and the refusal it drives names that day, so the day has to be the
     * caller's. [asOf] is a parameter rather than a constructor argument, so the class stays
     * three-argument and every construction site is untouched.
     *
     * @param contractId the contract the settlement is written to.
     * @param date the day to settle through; `date < startDate` raises
     *   [SettlementBeforeStartException], `date > asOf` raises [SettlementInFutureException],
     *   and when both apply the before-start check wins so the message names the field the
     *   owner cannot choose again.
     * @param asOf the caller's "today". A [date] strictly after it is refused, because
     *   `settledDays` closes every charge day at or before the marker — so a future date would
     *   settle arrears the owner has not been paid for. A past date is accepted: a day marked
     *   paid elsewhere is the whole point of FR-9b, and it may be last year.
     *
     * @throws SettlementBeforeStartException when [date] precedes the contract's own
     *   start date. Nothing is written.
     * @throws SettlementInFutureException when [date] is after [asOf]. Nothing is written.
     * @throws SettlementAlreadySettledException when [date] precedes a settlement this
     *   contract already carries. Nothing is written.
     * @throws UnknownContractException when nobody holds that id. Deliberately **not**
     *   [ContractNotOpenException]: that type reads "this contract has ended", which is
     *   the one state this write accepts — an ended contract with days paid elsewhere is
     *   precisely what FR-9b exists for.
     *
     *   **The first two overlap, and the start date wins.** A [date] can be both before
     *   `startDate` and after `asOf` — reachable whenever a contract's own start date is in
     *   the future, which `open` permits. The two `if`s below are ordered, not independent:
     *   the before-start check runs first, so the refusal names the figure the owner cannot
     *   choose again rather than adding a second complaint about one field. Swapping them
     *   would change the answer with **no compiler signal**, so the order is part of the
     *   contract and `SettleContractTest`'s `ORDER` row pins it.
     */
    suspend fun settleThrough(contractId: String, date: LocalDate, asOf: LocalDate) {
        inTransaction {
            val stored = dao.findById(contractId) ?: throw UnknownContractException(contractId)
            if (date.isBefore(stored.startDate)) {
                throw SettlementBeforeStartException(
                    contractId = contractId,
                    startDate = stored.startDate,
                )
            }
            // And a future date is refused from the other side, for the same reason
            // `endContract` refuses one. The marker is not inert: `settledDays` closes every
            // charge day at or before it, so a date six weeks out would settle the arrears of
            // days that have not happened — hiding rupiah the owner has not been paid. Ordered
            // after the start-date check so that when both apply the owner gets the message
            // naming a figure they can act on, rather than a second complaint about the same
            // figure.
            if (date.isAfter(asOf)) {
                throw SettlementInFutureException(
                    contractId = contractId,
                    asOf = asOf,
                )
            }
            // Equal or later is accepted: re-settling the same day is idempotent, and
            // moving the marker forward closes more days, which is the direction the
            // owner can always mean.
            val existing = stored.settledThroughDate
            if (existing != null && date.isBefore(existing)) {
                throw SettlementAlreadySettledException(
                    contractId = contractId,
                    settledThroughDate = existing,
                    attemptedDate = date,
                )
            }
            // `copy`, so every other column keeps the value the owner already has —
            // including the active pair, which must stay pinned to `endDate`.
            dao.update(stored.copy(settledThroughDate = date))
        }
    }

    // ------------------------------------------------------- service exceptions

    /**
     * Every service exception, observed.
     *
     * Read straight from Room's invalidation tracker, for the same reason
     * [observeContracts] is: a write re-emits here, so the sheet's list updates with
     * no refresh and no manual signal. The `Flow` is **not** filtered to one
     * contract — the sheet's rows are derived in the ViewModel's `combine` from
     * whichever contract is open, which is the same "join in Kotlin, not in SQL"
     * shape the contract list already uses, and it means one subscription rather
     * than one query per opened sheet.
     */
    fun observeServiceExceptions(): Flow<List<ServiceException>> =
        serviceExceptions.observeAll().map { rows -> rows.map { it.toDomain() } }

    /**
     * Marks [exception]'s day as not billed on its contract.
     *
     * The order of the four steps matters and is not arbitrary:
     *
     *  1. **read the contract** and refuse if it has ended — a finished contract is
     *     read-only, and that is a fact about the *contract*, not about the date, so
     *     it is asked before anything else
     *  2. **refuse a date before `startDate`** — such a row is inert: `chargeDaysIn`
     *     filters `it >= contractStart` *before* it ever reaches the exception
     *     filter, so a day before the contract began can never stop a charge and the
     *     row would sit in the list claiming a compensation the bill never got
     *  3. **pre-check the duplicate** — so the owner finds out while the sheet is
     *     open, and so the refusal can name the stored date
     *  4. **insert** — and let the `UNIQUE(contractId, date)` index refuse a raced
     *     duplicate, translating it by re-reading the row that holds the pair
     *
     * Steps 1 to 4 run as one transaction, so the checks and the insert see a single
     * snapshot. Without it, an exception written by another caller between the check
     * and the insert would surface as a raw `SQLiteConstraintException` — the index
     * would still hold, but the message would be about a constraint instead of a
     * day.
     *
     * ## Why a bad date is refused rather than normalised
     *
     * Swapping a before-start date with the contract's start, or clamping it
     * forward, would silently mark a **different** day than the owner chose — and the
     * marked day is a day the renter is not charged. `Contract.init` refused an end
     * before the start on exactly this reasoning ("a date typed into the wrong
     * field"), and the same sentence applies here.
     *
     * @throws ContractNotOpenException when the contract has ended, or when nobody
     *   holds that id. Nothing is written.
     * @throws ServiceExceptionBeforeContractStartException when [exception]'s date
     *   precedes the contract's own start. Nothing is written.
     * @throws DuplicateServiceExceptionDateException when the contract already
     *   carries an exception for that date. The exception carries the **stored**
     *   date, read back from the row.
     */
    suspend fun addServiceException(exception: ServiceException) {
        inTransaction {
            val contract = requireOpenContract(exception.contractId)
            if (exception.date.isBefore(contract.startDate)) {
                throw ServiceExceptionBeforeContractStartException(
                    contractId = exception.contractId,
                    startDate = contract.startDate,
                )
            }

            // The courtesy half. The index is the guarantee.
            serviceExceptions.findByContractAndDate(exception.contractId, exception.date)
                ?.let { stored ->
                    throw DuplicateServiceExceptionDateException(
                        // The stored date, not the submitted one — the same rule
                        // `insertEntity` below follows.
                        date = stored.date,
                        contractId = stored.contractId,
                    )
                }

            insertServiceExceptionEntity(exception)
        }
    }

    /**
     * Removes the exception with [id] from [contractId].
     *
     * The same read-only rule as the add, and the same
     * [ContractNotOpenException] — an ended contract's record of what was not
     * billed is not something to edit, in either direction, and two exception types
     * would let one of the two paths forget to check.
     *
     * The contract id is in the delete predicate rather than passed to a
     * primary-key-only `@Delete`, and that is the rule rather than tidiness: a row
     * id is not something `ui/` can reach without a lookup, so a delete keyed on the
     * id alone is a delete any caller can aim at any contract it happens to hold a
     * row id for.
     *
     * Removing a row that is already gone is **silent** rather than raising: an id
     * that no longer exists is the state the owner asked for, and the list re-emits
     * from Room either way. Inventing a refusal for it would show a message about a
     * row that is not there.
     *
     * @throws ContractNotOpenException when the contract has ended. The row stays.
     */
    suspend fun removeServiceException(id: String, contractId: String) {
        inTransaction {
            requireOpenContract(contractId)
            serviceExceptions.deleteServiceExceptionById(id, contractId)
        }
    }

    /**
     * The read-only rule, asked once for both writes.
     *
     * `Contract.isOpen` is a derivation over `endDate`, so "is this contract still
     * running?" is asked of the **stored row** rather than of a copy the caller
     * happens to be holding — a caller holding a stale `Contract` would otherwise be
     * writing to a contract that ended in the meantime.
     *
     * A contract id nobody holds raises the same [ContractNotOpenException]: there is
     * no open contract, and no other exception type would be true. It is reachable
     * only through a bug — the foreign key would refuse the write either way — and it
     * fails loudly rather than letting an orphan row land.
     *
     * Returns the row because the add path needs its `startDate`, and reading it
     * twice would be a second query for the same answer.
     */
    private suspend fun requireOpenContract(contractId: String): ContractEntity {
        val contract = dao.findById(contractId) ?: throw ContractNotOpenException(contractId)
        if (contract.endDate != null) throw ContractNotOpenException(contractId)
        return contract
    }

    /**
     * Inserts, translating the `UNIQUE` index's refusal into the named exception.
     *
     * This is the half of the duplicate rule that cannot be pre-checked away: two
     * callers can both pass the check above before either writes, and only the index
     * settles it. Android's SQLite reports a unique violation as
     * `SQLiteConstraintException`, whose message names the constraint but is not a
     * stable API — so the pair is re-read and reported from the row that actually
     * holds it, which is what the owner needs to see.
     *
     * The translation is verified, not assumed. If the re-read finds nothing, this is
     * not a duplicate `(contractId, date)` violation at all — a primary-key clash or a
     * foreign-key refusal, most likely — and it is rethrown untouched rather than
     * relabelled as a refusal that would send the owner to change a date that was
     * never the problem. Same shape as [insertEntity] beside it.
     */
    private suspend fun insertServiceExceptionEntity(exception: ServiceException) {
        try {
            serviceExceptions.insertServiceException(exception.toEntity())
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            val stored = serviceExceptions.findByContractAndDate(
                exception.contractId,
                exception.date,
            )
            if (stored != null) {
                throw DuplicateServiceExceptionDateException(
                    date = stored.date,
                    contractId = stored.contractId,
                )
            }
            throw e
        }
    }

    /**
     * Runs [block] as one transaction.
     *
     * Mirrors `VehicleRepository.inTransaction`, and is the helper that story added
     * in 2.2 precisely so the first multi-statement write would not have to
     * rediscover `withTransaction`. Opening a contract is that write: a pre-check
     * plus an insert that must see one snapshot. So is adding an exception: a
     * read-only check, an impossible-date check, a duplicate pre-check and an insert.
     *
     * A `null` database falls back to running [block] directly. That is not a
     * weakening of the rule — the `UNIQUE` indexes are the rule and they hold
     * whatever the caller does — it only means the *pre-check* is not isolated, so
     * a race surfaces as the translated constraint refusal instead of the
     * pre-check's own. The parameter is nullable to match the vehicle precedent,
     * whose tests build the repository over a DAO alone.
     */
    suspend fun inTransaction(block: suspend () -> Unit) {
        database?.withTransaction { block() } ?: block()
    }

    /**
     * A fresh contract id.
     *
     * `UUID.randomUUID()` here rather than `Date.now()`-derived or hardcoded
     * values: the story forbids seeded data and `Date.now()` ids, and Room
     * generates nothing on its own. It lives in `data/` because the domain's
     * import surface excludes `java.util.UUID` — the id arrives as a `String` from
     * the layer that owns persistence.
     */
    fun newContractId(): String = UUID.randomUUID().toString()

    /**
     * A fresh exception id.
     *
     * The same generator as [newContractId] and for the same reasons: `UUID.randomUUID()`
     * rather than a `Date.now()`-derived or hardcoded value, because the story forbids
     * seeded data and Room generates nothing on its own. It lives in `data/` because the
     * domain's import surface excludes `java.util.UUID`.
     *
     * Two exceptions on the same contract and the same date cannot both exist — the
     * index says so — so this id is only ever compared against the primary key, and a
     * collision would be a UUID collision rather than a semantic one.
     */
    fun newServiceExceptionId(): String = UUID.randomUUID().toString()

    /** Entity -> domain. The mapping the UI never sees. */
    private fun ContractEntity.toDomain() = Contract(
        id = id,
        renterId = renterId,
        vehicleId = vehicleId,
        startDate = startDate,
        dailyRate = dailyRate,
        restDay = restDay,
        endDate = endDate,
        // Story 3.6: carried so the calculation layer can narrow a settled contract's
        // day list, and so the contract row can render the marker beside its own dates.
        settledThroughDate = settledThroughDate,
    )

    /**
     * Domain -> entity.
     *
     * The active columns are derived here and nowhere else, which is what makes
     * them a *copy* that cannot drift: an open contract carries its own ids in
     * both slots, an ended one carries `NULL` in both, and
     * [ContractEntity]'s `init` refuses anything else. There is no second writer
     * to disagree with.
     *
     * [settledThroughDate] is carried rather than dropped, because [endContract]
     * rebuilds the row through here: ending a contract that had been settled would
     * silently un-settle it, which is the one outcome neither action asked for.
     */
    private fun Contract.toEntity() = ContractEntity(
        id = id,
        renterId = renterId,
        vehicleId = vehicleId,
        startDate = startDate,
        dailyRate = dailyRate,
        restDay = restDay,
        activeVehicleId = if (isOpen) vehicleId else null,
        activeRenterId = if (isOpen) renterId else null,
        endDate = endDate,
        settledThroughDate = settledThroughDate,
    )

    /** Entity -> domain, for an exception. The mapping the UI never sees. */
    private fun ServiceExceptionEntity.toDomain() = ServiceException(
        id = id,
        contractId = contractId,
        date = date,
        reason = reason,
    )

    /**
     * Domain -> entity, for an exception.
     *
     * A plain copy in both directions — unlike `ContractEntity`'s active columns there
     * is nothing to derive, because the exception carries no copy of any fact it does
     * not own: the date is absolute, the reason is text, and "is this contract open?"
     * is asked of the `contract` row rather than stored here. That is what keeps the
     * read-only rule a single question asked in one place rather than two that can
     * disagree.
     */
    private fun ServiceException.toEntity() = ServiceExceptionEntity(
        id = id,
        contractId = contractId,
        date = date,
        reason = reason,
    )
}
