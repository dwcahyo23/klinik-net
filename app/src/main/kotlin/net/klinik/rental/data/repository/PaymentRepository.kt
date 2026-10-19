package net.klinik.rental.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import net.klinik.rental.data.db.KlinikDatabase
import net.klinik.rental.data.db.dao.AllocationDao
import net.klinik.rental.data.db.dao.BusinessAccountDao
import net.klinik.rental.data.db.dao.ContractDao
import net.klinik.rental.data.db.dao.PaymentDao
import net.klinik.rental.data.db.dao.RenterDao
import net.klinik.rental.data.db.dao.ServiceExceptionDao
import net.klinik.rental.data.db.dao.SettingDao
import net.klinik.rental.data.db.dao.VehicleDao
import net.klinik.rental.data.db.dao.DailyIncome
import net.klinik.rental.data.db.entity.AllocationEntity
import net.klinik.rental.data.db.entity.ContractEntity
import net.klinik.rental.data.db.entity.PaymentEntity
import net.klinik.rental.data.db.entity.SettingEntity
import net.klinik.rental.domain.calculate.ArrearsEntry
import net.klinik.rental.domain.calculate.ChargeSchedule
import net.klinik.rental.domain.calculate.CutoffPeriod
import net.klinik.rental.domain.calculate.allocatePayments
import net.klinik.rental.domain.calculate.arrearsOf
import net.klinik.rental.domain.calculate.chargeDaysIn
import net.klinik.rental.domain.calculate.settledDays
import net.klinik.rental.domain.model.Allocation
import net.klinik.rental.domain.model.Payment
import net.klinik.rental.domain.validation.InvalidAmountException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * The only way the UI records a deposit and reads back what it closed.
 *
 * ## Why this is a repository of its own
 *
 * A deposit relates to a contract, an account and the allocation rows underneath it — but not
 * to the fleet or the renter list, which is the same argument that gave
 * `BusinessAccountRepository` its own class in Story 3.1. Putting it on
 * `ContractRepository` would give one class three tables and two unrelated reasons to change.
 *
 * ## The one rule this class exists to hold
 *
 * **A deposit and its allocations are written in one transaction, or neither is written.**
 *
 * The user chose this over deferring the allocation to Story 3.3, and the reason is worth
 * stating so a future refactor does not split it for convenience: a stored deposit whose
 * allocation rows are missing is money the owner took that closes no day. Arrears then reads
 * too high forever, and nothing on screen says why. There is no state in which half of this
 * write is recoverable and the other half is not — the whole point of the transaction.
 *
 * The user chose the same for 3.3's sake: the preview it will show is the same figure the
 * database already holds, rather than a second computation that could disagree with it.
 *
 * ## Why the whole contract is re-allocated rather than just the new deposit
 *
 * Because AD-3 stores *"this much of that deposit closed this day"* and credit is the
 * `chargeDate = null` row. Adding a deposit changes which days are still open, so the days
 * every earlier deposit lands on can shift. Re-running `allocatePayments` over the contract's
 * full payment list is what keeps the oldest-day-first rule intact — and Story 3.4's
 * cancellation needs exactly this operation, which is why it is here rather than inline.
 *
 * The preconditions the allocator asserts — ascending days, unique payment ids — come free
 * from `chargeDaysIn` and from the row being new.
 */
class PaymentRepository(
    private val paymentDao: PaymentDao,
    private val allocationDao: AllocationDao,
    private val contractDao: ContractDao,
    private val exceptionDao: ServiceExceptionDao,
    private val settingDao: SettingDao,
    /**
     * The three relations [findDeposit] resolves, so the correction form can be handed a
     * complete deposit rather than ids it would have to look up itself.
     *
     * Nullable rather than required for the same reason [database] is: the ViewModel tests
     * run on a `StandardTestDispatcher` that drains on the main thread, where
     * `withTransaction` throws, so they pass no database — and `findDeposit` opens no
     * transaction, so a test that only exercises `saveDeposit` has no need of these.
     */
    private val renterDao: RenterDao? = null,
    private val vehicleDao: VehicleDao? = null,
    private val businessAccountDao: BusinessAccountDao? = null,
    private val database: KlinikDatabase? = null,
) {

    /**
     * Records [payment] and the charge days it closes, in one transaction.
     *
     * ## Why the whole contract is re-allocated
     *
     * See the class KDoc. Adding a deposit changes which days are open, so an earlier
     * deposit's placement can shift; recomputing from every stored payment is what keeps
     * "oldest open day first" true rather than approximately true.
     *
     * ## Why the reads happen inside the transaction
     *
     * The existing payments, the contract and the exceptions are all read here, not by the
     * caller. Two concurrent deposits for one contract would otherwise each read a stale
     * payment list and each write an allocation set derived from it — two deposits totalling
     * more than the contract's open days, with the second silently overwriting rows the first
     * had placed. Reading and writing in one transaction is what makes the sum hold.
     *
     * @param cutoffDay read from `settings` inside this transaction. Not a parameter
     *   because the value belongs to storage, and passing it in would let a caller
     *   allocate against a cutoff day the database does not hold.
     * @param asOf the date the calculation is made "as of" — normally today, so a
     *   deposit dated in the future does not close a day before it arrives.
     */
    suspend fun saveDeposit(
        contractId: String,
        payment: Payment,
        method: PaymentMethod,
        accountId: String?,
        referenceNo: String?,
        notes: String?,
        asOf: LocalDate,
    ) {
        inTransaction {
            // 1-4. Read and allocate — the same call the preview makes. See
            //    [previewAllocation]: one function, two callers, is what makes "the
            //    preview cannot disagree with the save" a property of the code rather
            //    than a property of a test.
            val computed = previewAllocation(contractId, payment, asOf)

            // 5. The deposit itself, then its allocation rows.
            paymentDao.insertPayment(
                PaymentEntity(
                    id = payment.id,
                    contractId = contractId,
                    accountId = accountId,
                    date = payment.date,
                    amount = payment.amount,
                    method = method.name,
                    referenceNo = referenceNo,
                    notes = notes,
                    // Distinct from `payment.date`: the owner may date a deposit in
                    // the past for a correction while this records when they typed it.
                    createdAt = Instant.now(),
                ),
            )
            computed.newRows.forEach { row ->
                allocationDao.insertAllocation(row.toEntity(contractId))
            }
        }
    }

    /**
     * What a deposit *would* do, computed without writing anything.
     *
     * ## Why this exists as its own method
     *
     * Story 3.3's AC: *"pratinjau dihitung dengan mesin yang tidak menulis apa pun — dia
     * murni membaca, sehingga kalkulasi pratinjau dan kalkulasi penyimpanan tidak mungkin
     * berbeda"*. "Tidak mungkin berbeda" — *cannot* differ — is the requirement, and it is
     * met structurally: [previewAllocation] is the one function both this and
     * [saveDeposit] call. A second implementation, kept in step by hand, would satisfy a
     * test and not the requirement.
     *
     * ## Why it opens no transaction
     *
     * Nothing is written, so there is nothing to isolate. A transaction here would buy
     * nothing and cost something real: `withTransaction` asserts it is not on the main
     * thread, which would make the preview untestable under the `StandardTestDispatcher`
     * the ViewModel tests use.
     *
     * @throws UnknownContractException when nobody holds [contractId].
     */
    suspend fun previewDeposit(
        contractId: String,
        payment: Payment,
        asOf: LocalDate,
    ): DepositPreview {
        val computed = previewAllocation(contractId, payment, asOf)
        val contract = computed.contract

        // Arrears *before*: the contract as it stands, with its stored deposits.
        val before = arrearsOf(
            contractId = contractId,
            chargeDays = computed.chargeDays,
            allocations = computed.existingRows,
            dailyRate = contract.dailyRate,
        )
        // And *after*: the same list, with what the new deposit would place added.
        val after = arrearsOf(
            contractId = contractId,
            chargeDays = computed.chargeDays,
            allocations = computed.existingRows + computed.newRows,
            dailyRate = contract.dailyRate,
        )
        return DepositPreview(
            arrearsBefore = before.amount,
            arrearsAfter = after.amount,
            daysClosed = before.days - after.days,
            daysRemaining = after.days,
            // The credit is not a number the deposit *might* leave behind: it is the
            // `chargeDate = null` row the allocator produced, so it is read back rather
            // than computed as "amount − what was owed" — a second derivation of the
            // same fact, and the one that could disagree with the rows on disk.
            credit = computed.newRows.filter { it.chargeDate == null }.sumOf { it.amount },
        )
    }

    /**
     * Reads everything a deposit needs and allocates it — **writing nothing**.
     *
     * ## Why this is extracted, and why it is private
     *
     * It is the read-and-allocate half of [saveDeposit] as Story 3.2 wrote it: the
     * contract, the cutoff day, the exceptions, the charge days, the stored deposits, and
     * `allocatePayments` over all of them. Story 3.3's preview needs exactly those steps
     * and **not** the two writes, so the two callers diverge at the last line rather than
     * re-deriving the arithmetic.
     *
     * Private because it is not a useful public verb: a caller wanting only the charge
     * days, or only the rows, would be able to skip the allocation and get an answer
     * that disagrees with what a save would do. One entry point per question.
     *
     * ## Why it returns the existing rows as well as the new ones
     *
     * The preview needs **both** — the before-arrears is computed over the stored
     * allocations alone, and the after over those plus the new. Returning only the new rows
     * would force the caller to read them again, and a second read is a second snapshot.
     */
    private suspend fun previewAllocation(
        contractId: String,
        payment: Payment,
        asOf: LocalDate,
        /**
         * A stored deposit to leave out entirely, or `""` for none.
         *
         * Applied **before** the allocator, not by dropping rows afterwards — see
         * [arrearsExcluding] for why. Kept as a parameter rather than a second read chain
         * so the status filter below stays in exactly one place.
         */
        excludePaymentId: String = "",
        /**
         * Whether [payment] is a **discarded placeholder** rather than a real deposit.
         *
         * A placeholder exists only because [allocatePayments] needs a payment to place,
         * and it takes a slice of the oldest open day doing it. Every row a stored
         * deposit places afterwards then lands one rupiah short of `dailyRate`, and
         * `arrearsOf` counts a partly-paid day as **open** — so the rupiah total survives
         * while the open-day count is inflated by one.
         *
         * `true` allocates the stored deposits alone and hands the placeholder nothing,
         * which is what an arrears-only read wants: there is no new deposit to place, and
         * inventing one is what cost the day count. See [arrearsEntry], and the KDoc on
         * [correctDeposit] for the same hazard reached from the other side.
         */
        placeholderAmountIsDiscarded: Boolean = false,
        /**
         * A cutoff day to compute against **instead of** the stored one.
         *
         * `null` means "read `settings`", which is what every caller before Story 5.4 wants and
         * what they get by not passing this. The override exists for exactly one caller —
         * `previewCutoffChange` — which has to compute the same contracts under a day the owner
         * has *not* saved yet. Without it, a preview would have to write the setting first and
         * roll it back, and a rollback that failed would leave the owner's books on a cutoff they
         * never confirmed.
         */
        cutoffDayOverride: Int? = null,
    ): ComputedAllocation {
        // 1. The stored contract, for its frozen rate, its period and its schedule.
        //    Read rather than taken from the caller: a `Contract` handed in may be
        //    stale, and a rate re-derived from the vehicle would reprice a running
        //    rental.
        val contract = contractDao.findById(contractId)
            ?: throw UnknownContractException(contractId)

        // 2. The cutoff day, with the default supplied on read. A fresh install has no
        //    settings row at all and must still record a deposit.
        //
        //    The override is Story 5.4's preview: it asks what a contract would owe under a day
        //    the owner has not saved yet. `?:` rather than a separate branch, so the stored read
        //    stays the one path everything else takes.
        val cutoffDay = cutoffDayOverride ?: settingDao.findInt(KEY_CUTOFF_DAY, DEFAULT_CUTOFF_DAY)

        // 3. The charge days this contract owes in the current period, with its stored
        //    exceptions applied. `contractEnd` comes from the row, so an ended contract
        //    stops producing days without this class knowing.
        //
        //    Then narrowed through `settledDays` (FR-9b): charge days at or before
        //    `settledThroughDate` are already paid elsewhere and drop out here, without
        //    a row and without a Setoran. **This is the single place settlement is
        //    applied**, which is why one line reaches `currentArrears`, the preview,
        //    cancellation and correction together — a second application site would be
        //    a second derivation of one rule, and the two would drift.
        val chargeDays = settledDays(
            chargeDays = chargeDaysIn(
                period = CutoffPeriod.containing(cutoffDay, asOf),
                schedule = ChargeSchedule(
                    contractStart = contract.startDate,
                    restDay = contract.restDay,
                    serviceExceptions = storedExceptions(contract.id),
                    contractEnd = contract.endDate,
                ),
                asOf = asOf,
            ),
            settledThroughDate = contract.settledThroughDate,
        )

        // 4. The payment and everything already recorded, allocated **together**.
        //
        //    `forContract` returns **every** deposit, cancelled ones included, so both
        //    exclusions happen here and in one place. That is the whole answer to the
        //    `Payment` KDoc's warning that a status "would invite callers to filter
        //    instead": a filter in the query would put the rule in every reader of this
        //    table, while here it sits in the only place that computes anything. A
        //    cancelled deposit reaching the allocator by mistake would fail
        //    `Σ allocation == amount` loudly, not quietly.
        val existing = paymentDao.forContract(contract.id)
            .filter {
                it.status == PaymentEntity.STATUS_CONFIRMED && it.id != excludePaymentId
            }
            .map { it.toDomain() }
        val allRows = allocatePayments(
            // The placeholder is withheld from the allocator entirely when it is a
            // discarded one — see [placeholderAmountIsDiscarded]. An arrears-only read
            // has nothing to place, and letting the placeholder take a rupiah off the
            // oldest day is what made a fully-paid day read as open.
            payments = if (placeholderAmountIsDiscarded) existing else existing + payment,
            chargeDays = chargeDays,
            dailyRate = contract.dailyRate,
            asOf = asOf,
        )
        return ComputedAllocation(
            contract = contract,
            chargeDays = chargeDays,
            // The stored deposits' own allocations, re-derived rather than read back:
            // `allocatePayments` regenerates every payment's rows from the payment list,
            // so these are the same rows the last save wrote, and reading them from the
            // database instead would make the preview's "before" disagree with the save
            // the moment history and re-derivation ever drift.
            existingRows = allRows.filter { it.paymentId != payment.id },
            // Only the **new** deposit's rows. The allocator returns a row per payment,
            // so `allRows` also covers the stored ones — and inserting those again
            // collides with `UNIQUE(paymentId, chargeDate)`, which is the constraint
            // doing its job.
            newRows = allRows.filter { it.paymentId == payment.id },
        )
    }

    /** What one allocation pass produced. Private, like the function that builds it. */
    private data class ComputedAllocation(
        val contract: ContractEntity,
        val chargeDays: List<LocalDate>,
        val existingRows: List<Allocation>,
        val newRows: List<Allocation>,
    )

    /**
     * Cancels one deposit, keeping the row and dropping its effect.
     *
     * ## What happens, in this order, in one transaction (AR-8)
     *
     *  1. Read the deposit. Refuse a blank reason and an already-cancelled one.
     *  2. Mark it `canceled` with the reason. **The row survives** — the epic's criterion
     *     is *"tidak ada hard delete"*, because a row that cannot be read cannot answer
     *     why the owner's arrears jumped on a given day.
     *  3. Delete its allocation rows: those encode its *effect*, which is exactly what
     *     cancelling reverses.
     *  4. Re-allocate the contract from the deposits that survive, and write **all** their
     *     rows back.
     *
     * ## Why step 4 rewrites every row and not just the cancelled deposit's
     *
     * Because releasing Rp 500.000 can shift what deposits recorded *later* land on: an
     * earlier day opens up and the next deposit's money moves onto it. Re-allocating only
     * the cancelled row would leave the survivors placed as if nothing had changed — and
     * `Σ allocation == amount` would still hold while the days each deposit closes were
     * wrong. Recomputing wholesale is what [previewAllocation] already does for a save, so
     * this is the same function with a different final write.
     *
     * ## Why a partial cancel is not recoverable
     *
     * If step 3 and step 4 could interleave with a crash, the contract would be left with
     * some deposits' rows released and others placed under the old day list — an arrears
     * figure that is wrong and looks right. That is what the transaction is for.
     *
     * @throws BlankCancelReasonException when [reason] is blank. Checked **before** the
     *   transaction opens: a blank reason is a form state, not a data condition, and there
     *   is no reason to take a write lock to discover one.
     * @throws DepositAlreadyCanceledException when the deposit is already cancelled.
     * @throws UnknownDepositException when [paymentId] names no stored deposit.
     */
    suspend fun cancelDeposit(
        contractId: String,
        paymentId: String,
        reason: String,
        asOf: LocalDate = asOf(),
    ) {
        // The AC's rule, refused before anything is written, so a refused cancellation
        // leaves the deposit exactly as it was.
        if (reason.isBlank()) throw BlankCancelReasonException(paymentId)

        inTransaction {
            // 1. The stored deposit. Read rather than taken from the caller — a `Payment`
            //    handed in may be stale, and this decides whether it is still cancellable.
            val stored = paymentDao.findById(paymentId)
                ?: throw UnknownDepositException(paymentId)
            if (stored.contractId != contractId) {
                throw UnknownDepositException(paymentId)
            }
            if (stored.status == PaymentEntity.STATUS_CANCELED) {
                // The original reason stands: a second cancellation must not overwrite the
                // audit trail with a newer, less accurate explanation.
                throw DepositAlreadyCanceledException(paymentId, stored.cancelReason)
            }

            // 2. Mark it, keeping the row.
            paymentDao.updatePayment(
                stored.copy(
                    status = PaymentEntity.STATUS_CANCELED,
                    cancelReason = reason.trim(),
                ),
            )

            // 3. Release what it placed.
            allocationDao.deleteForPayment(paymentId)

            // 4. Re-allocate the contract from the survivors. `previewAllocation` filters
            //    cancelled deposits out on its own, so the one marked in step 2 is
            //    excluded here without a second rule — which is the point of putting the
            //    filter there.
            val computed = previewAllocation(
                contractId = contractId,
                // A Rp 1 placeholder: `allocatePayments` needs a payment to place, and
                // this call happens *after* the cancellation, so the survivors' rows are
                // what comes back and the placeholder's own row is discarded.
                payment = Payment(
                    id = RECOMPUTE_ID,
                    date = asOf,
                    amount = 1,
                ),
                asOf = asOf,
            )

            // Wholesale, because step 4 can move the survivors as well as the cancelled
            // deposit — see the KDoc.
            allocationDao.deleteForContract(contractId)
            computed.existingRows.forEach { row ->
                allocationDao.insertAllocation(row.toEntity(contractId))
            }
        }
    }

    /**
     * Corrects a stored deposit in place, in one transaction with its allocations.
     *
     * ## What happens, in this order (AR-8)
     *
     *  1. Refuse a non-positive amount, an unknown or foreign-contract deposit, a cancelled
     *     one, and a transfer with no account — every refusal before anything is written.
     *  2. Write the deposit's new values. **The row keeps its identity and its history
     *     position**: this is an in-place update, not a revision table, because the epic's
     *     criterion is *"Setoran hanya punya satu nilai terkini — riwayat perubahan tidak
     *     menyimpan versi lama."*
     *  3. Release every allocation on the contract and rewrite them all, from the stored
     *     deposits with **this one already updated**.
     *
     * ## Why step 3 excludes the stored row rather than placing it twice
     *
     * Because [previewAllocation] reads the deposits from the database, and step 2 has
     * already written the correction. Passing the corrected value as the payment to place,
     * with `excludePaymentId` naming the same row, is what makes the allocator see exactly
     * what a fresh save would have seen — the survivors plus one deposit carrying the
     * corrected date and amount.
     *
     * The obvious alternative is the `RECOMPUTE_ID` placeholder [cancelDeposit] uses, and
     * it is **wrong here**: the placeholder takes a slice of the oldest open day, so every
     * survivor lands one rupiah short of closing it and a day that should be closed reopens.
     * `CorrectDepositTest` caught exactly that — a Rp 500.000 correction produced three
     * allocation rows where two were due.
     *
     * ## Why the write is wholesale rather than this deposit's rows alone
     *
     * The reason [cancelDeposit] gives: changing what one deposit places can move the
     * survivors onto different days, so recomputing only this deposit's rows would leave
     * them placed as if nothing had changed — and `Σ allocation == amount` would still hold
     * while the days each deposit closes were wrong.
     *
     * @param method deliberately **absent**. The AC is explicit that payment method cannot
     *   change — repairing a method means cancelling and recording again — so there is no
     *   parameter through which a caller could express one, and the stored value is carried
     *   by the `copy` below. A structural absence rather than a check, because a check
     *   invites a caller to pass the old value and rely on the refusal.
     * @throws InvalidAmountException when [amount] is zero or negative.
     * @throws UnknownDepositException when [paymentId] names no stored deposit, or one on a
     *   different contract.
     * @throws DepositAlreadyCanceledException when the deposit is already cancelled.
     * @throws AccountRequiredException when the deposit is a transfer with a null [accountId].
     * @throws UnknownAccountException when [accountId] names no stored account.
     */
    suspend fun correctDeposit(
        contractId: String,
        paymentId: String,
        date: LocalDate,
        amount: Long,
        accountId: String?,
        referenceNo: String?,
        notes: String?,
        asOf: LocalDate = asOf(),
    ) {
        // The amount rule, before the transaction opens — a form state rather than a data
        // condition, so there is no reason to take a write lock to discover one.
        InvalidAmountException.requireValid(amount)

        inTransaction {
            // 1. The stored deposit. Read rather than taken from the caller: this decides
            //    whether it is still correctable, and it carries the method that step 2
            //    keeps.
            val stored = paymentDao.findById(paymentId)
                ?: throw UnknownDepositException(paymentId)
            if (stored.contractId != contractId) {
                throw UnknownDepositException(paymentId)
            }
            if (stored.status == PaymentEntity.STATUS_CANCELED) {
                throw DepositAlreadyCanceledException(paymentId, stored.cancelReason)
            }
            // The transfer-account rule **below the sheet**, for the reason the epic context
            // gives: two screens can pass the same UI check, so the check that counts is the
            // one under them. A transfer with no account is a claim the app cannot support.
            if (stored.method == PaymentMethod.TransferBank.name && accountId == null) {
                throw AccountRequiredException(paymentId)
            }
            // And the account must be one that exists. The sheet offers only stored ones, but
            // this is the layer a caller that bypasses the sheet reaches, and the foreign key
            // would otherwise refuse with a raw `SQLiteConstraintException` mid-transaction —
            // a constraint failure where the owner can act, naming no account.
            if (accountId != null && businessAccountDao?.findById(accountId) == null) {
                throw UnknownAccountException(accountId)
            }

            // 2. The correction itself, in place.
            paymentDao.updatePayment(
                stored.copy(
                    date = date,
                    amount = amount,
                    accountId = accountId,
                    referenceNo = referenceNo?.trim()?.ifBlank { null },
                    notes = notes?.trim()?.ifBlank { null },
                ),
            )

            // 3. Re-allocate from the deposits that survive, the corrected one among them.
            val computed = previewAllocation(
                contractId = contractId,
                payment = Payment(id = paymentId, date = date, amount = amount),
                excludePaymentId = paymentId,
                asOf = asOf,
            )

            allocationDao.deleteForContract(contractId)
            // **Both** halves: `newRows` is the corrected deposit's own placement, and
            // dropping it would leave a stored deposit that closes no day.
            (computed.existingRows + computed.newRows).forEach { row ->
                allocationDao.insertAllocation(row.toEntity(contractId))
            }
        }
    }

    /**
     * The stored deposit, projected for the correction form — Story 3.5's `findDeposit`.
     *
     * ## Why the relations resolve here rather than in the ViewModel
     *
     * Because a correction form that opens with a blank plate or a blank rate is a form the
     * owner cannot check what they are correcting against. Resolving them here means this
     * returns either a complete [EditableDeposit] or nothing, so the sheet has no state in
     * which it is holding half a deposit and would rather render the blanks than say so.
     *
     * It is also what keeps the three separate `first()` reads out of `SetoranViewModel`:
     * that class already holds repositories for all three relations, so resolving them there
     * would duplicate the lookups [SetoranViewModel]'s own options flow performs — the
     * "two readers of the same table" shape `ContractRepository.observeContracts`'s KDoc
     * warns about.
     *
     * @return the projection, or `null` when no confirmed deposit holds [paymentId]. A
     *   cancelled deposit reads as absent rather than as a third answer, because correcting
     *   one is not a thing this method can describe.
     * @throws MissingDepositRelationException when the deposit, its contract, its renter or
     *   its vehicle cannot be resolved — a state the foreign keys forbid, so the throw is
     *   the loud half of a pair whose quiet half would be a form full of blanks.
     */
    suspend fun findDeposit(paymentId: String): EditableDeposit? {
        val stored = paymentDao.findById(paymentId) ?: return null
        if (stored.status != PaymentEntity.STATUS_CONFIRMED) return null

        val contract = contractDao.findById(stored.contractId)
            ?: throw MissingDepositRelationException(paymentId, "kontrak")
        val renter = renterDao?.findById(contract.renterId)
            ?: throw MissingDepositRelationException(paymentId, "penyewa")
        val vehicle = vehicleDao?.findById(contract.vehicleId)
            ?: throw MissingDepositRelationException(paymentId, "kendaraan")

        // `valueOf` rather than a fallback: a stored method this cannot decode is a
        // database holding a value the app does not understand, and inventing one — a cash
        // deposit shown as a transfer, or the reverse — is exactly the "a figure that looks
        // right and is not" the epic refuses. The throw names it instead.
        val method = PaymentMethod.entries.firstOrNull { it.name == stored.method }
            ?: throw MissingDepositRelationException(paymentId, "metode pembayaran")

        return EditableDeposit(
            id = stored.id,
            contractId = stored.contractId,
            renterId = renter.id,
            renterName = renter.name,
            vehiclePlate = vehicle.plate,
            dailyRate = contract.dailyRate,
            date = stored.date,
            amount = stored.amount,
            method = method,
            accountId = stored.accountId,
            referenceNo = stored.referenceNo,
            notes = stored.notes,
        )
    }

    /**
     * The arrears the contract would show once [paymentId] is cancelled — the number the
     * confirmation dialog has to name.
     *
     * ## Why this allocates rather than subtracts
     *
     * Because "arrears after cancelling" is **not** "arrears now minus the deposit's
     * amount". Cancelling can move *later* deposits onto days that just opened, so the
     * contract's arrears is whatever the survivors place, and the only honest way to know
     * that is to allocate without the deposit and read the result.
     *
     * Subtracting would be a second derivation of a fact the allocator already owns — the
     * exact shape Story 3.3 exists to prevent, and the one whose failure the owner finds
     * too late because the figure looks plausible.
     *
     * @return whole rupiah, or the contract's **current** arrears when the deposit is
     *   unknown or already cancelled, so a dialog never promises a change it cannot make.
     */
    suspend fun arrearsAfterCanceling(
        contractId: String,
        paymentId: String,
        asOf: LocalDate,
    ): Long {
        val stored = paymentDao.findById(paymentId) ?: return currentArrears(contractId, asOf)
        // Already cancelled: nothing would change, so the "after" is the present.
        if (stored.status == PaymentEntity.STATUS_CANCELED) return currentArrears(contractId, asOf)
        return arrearsExcluding(contractId, paymentId, asOf)
    }

    /**
     * The contract's arrears with one deposit left out, allocated honestly.
     *
     * ## Why it goes through [previewAllocation] rather than deriving its own answer
     *
     * Because the alternative is a **second** copy of the read chain and the second copy
     * of the status filter — and the two would drift, which is the exact shape this
     * story's design notes refuse.
     *
     * ## Why the exclusion cannot be a placeholder
     *
     * Because a placeholder takes days **after** the survivors, so dropping its rows
     * afterwards leaves every deposit placed as if the excluded money were still there —
     * and the figure would then be the one that deposit would produce if it had not been
     * cancelled. Excluding it **before** the allocation is what makes this the honest
     * answer, and is the whole difference between allocating and subtracting.
     */
    private suspend fun arrearsExcluding(
        contractId: String,
        paymentId: String,
        asOf: LocalDate,
    ): Long {
        val computed = previewAllocation(
            contractId = contractId,
            payment = Payment(id = RECOMPUTE_ID, date = asOf, amount = 1),
            // Dropped before the allocator sees it. `currentArrears` passes `""`, which
            // no stored deposit carries, so nothing is excluded there.
            excludePaymentId = paymentId,
            asOf = asOf,
        )
        return arrearsOf(
            contractId = contractId,
            chargeDays = computed.chargeDays,
            allocations = computed.existingRows,
            dailyRate = computed.contract.dailyRate,
        ).amount
    }

    /** The contract's arrears exactly as it stands — every confirmed deposit applied. */
    suspend fun currentArrears(contractId: String, asOf: LocalDate): Long =
        arrearsExcluding(contractId, paymentId = "", asOf = asOf)

    /**
     * What a settlement through [date] would remove from the arrears, and what would
     * remain — Story 3.6's confirmation figures.
     *
     * ## Why this allocates rather than subtracts
     *
     * Because "arrears after settling" is **not** "arrears now minus the settled days ×
     * rate", and the difference is not a rounding slip. A contract with a rest day or a
     * service exception produces fewer charge days than its calendar span, so the days a
     * settlement closes and the days actually billed are different lists. Subtracting
     * would name a figure the calculator never produces — the second derivation
     * [arrearsAfterCanceling] above already refuses, for the same reason.
     *
     * ## Why the settlement is applied before the allocation rather than as a subtraction
     *
     * Both figures come from [previewAllocation]'s own day list, narrowed through
     * [settledDays] — which is where the stored settlement is already applied. To ask
     * "what if the settlement were [date]" the day list is narrowed once more by the
     * **later** of the two dates: a contract already settled through October cannot
     * un-settle days by settling through March, so the effective date is the maximum.
     * Reading it as a subtraction would instead report that March's settlement *adds*
     * arrears, which is the opposite of what the owner asked for.
     *
     * @return the present, with nothing removed, when the contract is unknown or already
     *   settled at or past [date] — so the dialog never promises a change it cannot make.
     */
    suspend fun settlementImpact(
        contractId: String,
        date: LocalDate,
        asOf: LocalDate,
    ): SettlementImpact {
        val stored = contractDao.findById(contractId)
            ?: return presentImpact(contractId, asOf)
        val alreadySettled = stored.settledThroughDate
        // Already settled at or past this date: nothing would change, so the honest
        // answer is the present.
        if (alreadySettled != null && !alreadySettled.isBefore(date)) {
            return presentImpact(contractId, asOf)
        }

        val before = arrearsEntry(contractId, asOf)
        val after = arrearsEntry(contractId, asOf, extraSettledThroughDate = date)
        return SettlementImpact(
            daysClosed = before.days - after.days,
            amountRemoved = before.amount - after.amount,
            daysRemaining = after.days,
            amountRemaining = after.amount,
        )
    }

    /** The contract as it stands, with nothing removed. For both fallbacks above. */
    private suspend fun presentImpact(contractId: String, asOf: LocalDate): SettlementImpact {
        val now = arrearsEntry(contractId, asOf)
        return SettlementImpact(
            daysClosed = 0,
            amountRemoved = 0,
            daysRemaining = now.days,
            amountRemaining = now.amount,
        )
    }

    /**
     * The contract's arrears, optionally as if it were settled through one further date.
     *
     * The extra date is a **floor** applied on top of whatever the contract already
     * carries, never a replacement: settling cannot un-close a day, so the effective
     * settlement is the later of the stored and the proposed date.
     */
    private suspend fun arrearsEntry(
        contractId: String,
        asOf: LocalDate,
        extraSettledThroughDate: LocalDate? = null,
        /**
         * A cutoff day to compute against instead of the stored one — Story 5.4's preview.
         *
         * Threaded through rather than read here for the reason [previewAllocation]'s KDoc gives:
         * one function reads the cutoff, so a preview and a real read cannot disagree about which
         * day is in force.
         */
        cutoffDayOverride: Int? = null,
    ): ArrearsEntry {
        // The `RECOMPUTE_ID` placeholder is **wrong here**, and the KDoc on
        // [correctDeposit] names why: the allocator has to be given a payment, so a
        // placeholder takes a slice of the oldest open day. Every row the deposit
        // places then lands one rupiah short of `dailyRate`, and `arrearsOf` counts a
        // partly-paid day as **open**. The rupiah total survives — the missing rupiah
        // simply moves onto the placeholder's own row, which is filtered out — so the
        // figure the owner reads stays right while the **day count is inflated by one**.
        // `SettleContractTest` caught exactly that: 13 days closed where 12 were.
        //
        // So this reads the contract and allocates with **no** payment of its own. The
        // stored deposits are already `previewAllocation`'s input; an arrears question
        // does not have a new deposit to place, and inventing one is what cost the day.
        val computed = previewAllocation(
            contractId = contractId,
            payment = Payment(id = RECOMPUTE_ID, date = asOf, amount = 1),
            asOf = asOf,
            // The placeholder's own row, dropped before the allocator can hand a
            // rupiah of a real day to it.
            placeholderAmountIsDiscarded = true,
            cutoffDayOverride = cutoffDayOverride,
        )
        val stored = computed.contract.settledThroughDate
        val days = when {
            extraSettledThroughDate == null -> computed.chargeDays
            stored == null -> settledDays(computed.chargeDays, extraSettledThroughDate)
            stored.isAfter(extraSettledThroughDate) -> computed.chargeDays
            else -> settledDays(computed.chargeDays, extraSettledThroughDate)
        }
        return arrearsOf(
            contractId = contractId,
            chargeDays = days,
            allocations = computed.existingRows,
            dailyRate = computed.contract.dailyRate,
        )
    }

    /** "Today" for a write that has no date of its own. See the call sites. */
    private fun asOf(): LocalDate = LocalDate.now()

    /**
     * Every contract's arrears as one list, observed — Story 4.1's.
     *
     * ## Why this is not three reads joined by the caller
     *
     * Because the epic context is explicit that a figure leaving the app comes from one
     * snapshot: reading arrears, then the day count, then the name can land on either side of
     * a write, and the number the owner sends to a tenant has no undo behind it. So one call
     * returns one **complete** entry per contract — amount, day count and the contract's own
     * fields together — rather than three things the UI assembles.
     *
     * ## Why the entry is per contract rather than a sum
     *
     * Because a total is not what the screen shows. Every figure here is read through
     * [previewAllocation], which is the **one** place that narrows charge days through
     * `settledDays` and the one place that allocates the stored deposits — so a contract
     * settled through a date reports zero here without this method knowing FR-9b exists, and
     * a day that is partly paid is counted as open because that is `arrearsOf`'s rule rather
     * than one re-derived here.
     *
     * ## Why the placeholder is withheld
     *
     * Same reason [arrearsEntry] withholds it, and for the same failure: a discarded
     * placeholder takes a rupiah off the oldest open day, so every deposit's row lands one
     * rupiah short of `dailyRate` and a closed day reads as open. The **day count** is what a
     * tenant is told, so inflating it here would be the worst place for that bug to live.
     *
     * @param asOf the date the bill is cut at, defaulting to **today read per emission**.
     *   Story 4.5's report calls this with a period's own last day, which is what makes
     *   "the same function with a different time point" true in the strict sense — one body,
     *   two arguments — rather than true only in the sense that two callers happen to agree
     *   today. The default keeps this path byte-identical to what Beranda already ran, so
     *   nothing on the morning screen moves.
     */
    fun observeArrearsForAllContracts(
        clock: Clock,
        asOf: () -> LocalDate = { LocalDate.now(clock) },
    ): Flow<List<ContractArrears>> =
        combine(
            contractDao.observeAll(),
            // The three tables the figure is derived from, **besides** `contract` — each one
            // named because Room's tracker re-emits a flow only for the tables its query
            // touches. Omitting any one of them leaves a stale figure that looks live, which
            // is worse than a missing feature because nothing announces it:
            //
            //  - `payment` and `allocation`, because a deposit writes both and never
            //    touches `contract`.
            //  - `service_exception`, because marking a day unbilled takes it **out** of
            //    the charge-day list, so it changes the day count and the rupiah without
            //    writing either of the other two.
            //
            // The rows are discarded; what is being observed is *that the table changed*.
            // The two `observeContractIds` queries exist only because `observeAll` on those
            // two tables would read far more than the trigger needs.
            paymentDao.observeContractIds(),
            allocationDao.observeContractIds(),
            exceptionDao.observeAll(),
        ) { contracts, _, _, _ ->
            // **Read per emission, not once when the flow was built.** The bill depends on
            // which period "today" falls in, so an app left open across midnight would
            // otherwise show yesterday's figure until the owner left and came back — and
            // nothing would say so. `LIST_MIDNIGHT` is that case: the figure must grow by
            // the day that fell behind, and only a per-emission read can do that.
            val billAsOf = asOf()
            contracts.mapNotNull { contract ->
                // Per-contract, so **one** contract that cannot be computed costs that row
                // and not the list. This screen is what the owner opens every morning, and
                // voiding the whole thing over a single bad row would leave them with no
                // answer at all — a worse outcome than one missing tenant, and one they
                // cannot see the cause of. A genuine `UnknownContractException` (the row
                // vanished between the query and here) is the same reasoning: skip it.
                val entry = runCatching { arrearsEntry(contract.id, billAsOf) }.getOrNull()
                    ?: return@mapNotNull null
                ContractArrears(
                    contractId = entry.contractId,
                    amount = entry.amount,
                    days = entry.days,
                    oldestOpenDay = entry.oldestOpenDay,
                    renterId = contract.renterId,
                    vehicleId = contract.vehicleId,
                    isEnded = contract.endDate != null,
                )
            }
        }

    /** Every deposit on one contract, as domain values. */
    suspend fun paymentsFor(contractId: String): List<Payment> =
        paymentDao.forContract(contractId).map { it.toDomain() }

    /**
     * Today's income in whole rupiah, observed — Story 4.3's "Pendapatan hari ini".
     *
     * ## Why the clock is handed in rather than read here
     *
     * Because "today" is decided by the **caller's** clock, and the read must happen per
     * emission: an app left open across midnight would otherwise show yesterday's income
     * until the owner left and came back, and nothing would say so. The same argument
     * [observeArrearsForAllContracts] gives, and the same shape the epic context's "one
     * clock abstraction" requires.
     *
     * ## Why the query groups by date rather than filtering to one
     *
     * Because a bound date in the SQL **freezes the date in the subscription** — Room re-runs
     * the query with the same bound value when the table changes, so a deposit landing after
     * midnight would be counted against yesterday. Grouping hands the choice here, where the
     * clock is read on every emission, and the figure moves when the day does. This is the
     * same defect and the same fix as the frozen `asOf` in Story 4.1's arrears read.
     *
     * ## Why this is a **sum**, not a list
     *
     * Because the screen shows one figure — a total — and a total the UI adds up is a second
     * place the arithmetic lives. The query sums, the repository picks today's bucket, and the
     * ViewModel formats it.
     */
    fun observeTodaysIncome(clock: Clock): Flow<Long> =
        paymentDao.observeIncomeByDate().map { byDate ->
            byDate.firstOrNull { it.date == LocalDate.now(clock) }?.total ?: 0L
        }

    /**
     * Confirmed income from [start] to [end] inclusive, observed — Story 4.5's period total.
     *
     * ## Why this filters the **existing** grouping rather than running its own query
     *
     * Because [observeIncomeByDate] is already the app's answer to "what came in on which
     * day", and a second query would be a second place the status filter lives — and
     * therefore a second thing that can forget that a cancelled deposit is not income. This
     * takes the buckets the query already grouped and adds up the ones in range.
     *
     * ## Why the date decides membership, and nothing else does
     *
     * Because the acceptance criterion is that a deposit belongs to the period its **recorded
     * date** falls in. There is no booking date in this app, so there is nothing else it
     * could belong to: a deposit dated 20 March counts in March and not in April, because
     * the bucket it is in is decided by `date` and nothing reads it twice.
     *
     * The range is **inclusive** at both ends, because a billing period's own boundaries are
     * inclusive — [net.klinik.rental.domain.calculate.CutoffPeriod] starts on the cutoff day
     * and ends the day before the next one, and a deposit on either boundary day is inside.
     */
    fun observeIncomeBetween(start: LocalDate, end: LocalDate): Flow<Long> =
        paymentDao.observeIncomeByDate().map { byDate ->
            byDate.filter { !it.date.isBefore(start) && !it.date.isAfter(end) }
                .sumOf { it.total }
        }

    /**
     * Income per day across [start] to [end] inclusive, zero-filled — Story 4.5's trend.
     *
     * ## Why the days are zero-filled here and not by the screen
     *
     * Because a gap is a fact about the period, and a chart that skipped a quiet day would
     * draw a straight line through it — reading as a steady Tuesday when nothing arrived.
     * Filling here rather than in the composable is what makes the Canvas a straight
     * "one point per day" mapping rather than a chart that has to infer the missing days.
     */
    fun observeIncomeByDay(start: LocalDate, end: LocalDate): Flow<List<DailyIncome>> =
        paymentDao.observeIncomeByDate().map { byDate ->
            start.datesUntil(end.plusDays(1))
                .map { day -> byDate.firstOrNull { it.date == day } ?: DailyIncome(day, 0L) }
                .toList()
        }

    /**
     * Every deposit on one contract as a [Flow], for the arrears screens in Epic 4.
     *
     * A subscription rather than a one-shot read because a deposit must move the arrears
     * number the moment it lands, with no refresh anywhere in the app.
     */
    fun observePayments(contractId: String): Flow<List<Payment>> =
        paymentDao.observeForContract(contractId).map { rows -> rows.map { it.toDomain() } }

    /** The stored charge days this contract's exceptions excuse, as a set for the schedule. */
    private suspend fun storedExceptions(contractId: String): Set<LocalDate> =
        exceptionDao.forContract(contractId).map { it.date }.toSet()

    /** A fresh deposit id. See `BusinessAccountRepository.newAccountId` for why it is here. */
    fun newPaymentId(): String = UUID.randomUUID().toString()

    /**
     * Runs [block] as one transaction.
     *
     * Nullable for the reason its siblings are: the transaction path is covered by the
     * repository tests, which use `runBlocking` and therefore a real background thread,
     * while the ViewModel tests run on a `StandardTestDispatcher` that drains *on* the main
     * thread — where `withTransaction` throws. `PaymentRepositoryTest` and
     * `PaymentViewModelTest` differ in exactly that one argument, deliberately.
     */
    suspend fun inTransaction(block: suspend () -> Unit) {
        database?.withTransaction { block() } ?: block()
    }

    /** Entity -> domain. Only the three fields the arithmetic reads. */
    private fun PaymentEntity.toDomain() = Payment(
        id = id,
        date = date,
        amount = amount,
    )

    /**
     * Domain -> entity, with the two ids the domain value deliberately does not carry.
     *
     * `Allocation` has no `contractId` — the allocator replays one contract at a time
     * and the caller already holds the id. This is where that becomes a column, because
     * the arrears read is a single per-contract query and joining back through `payment`
     * for every row would make the number the app is built around a two-hop query.
     */
    private fun Allocation.toEntity(contractId: String) = AllocationEntity(
        id = newAllocationId(),
        paymentId = paymentId,
        contractId = contractId,
        chargeDate = chargeDate,
        amount = amount,
    )

    private fun newAllocationId(): String = UUID.randomUUID().toString()

    // ------------------------------------------------- Story 5.4: the cutoff day

    /**
     * The cutoff day in force — the stored one, or [DEFAULT_CUTOFF_DAY] when none is stored.
     *
     * Read through the same `findInt` every computation uses, so the figure the settings screen
     * shows and the figure the allocator applies cannot disagree about "unset".
     */
    suspend fun cutoffDay(): Int = settingDao.findInt(KEY_CUTOFF_DAY, DEFAULT_CUTOFF_DAY)

    /**
     * What changing the cutoff to [newCutoffDay] would do to every contract's arrears.
     *
     * ## Why this runs `arrearsEntry` twice per contract rather than calling `cutoffShift`
     *
     * Because the app's arrears read **re-derives** allocation rows from the deposits instead of
     * reading the stored ones — `previewAllocation`'s KDoc explains why, and the reason is that a
     * stored read would let the preview disagree with the save the moment history and
     * re-derivation drifted. So the two day lists come from two `previewAllocation` calls over
     * the same deposits, which is the same property `cutoffShift` proves at the domain level:
     * the deposits are the input, the range is what moves.
     *
     * ## Why a contract that cannot be computed is skipped rather than failing the preview
     *
     * The same rule `observeArrearsForAllContracts` follows: one bad row costs that contract and
     * not the answer. An owner about to change their billing cycle needs a figure, and a preview
     * that refused entirely because one contract was mid-write would be worse than one that
     * counted the rest.
     *
     * @throws InvalidCutoffDayException when [newCutoffDay] is outside 1..28.
     */
    suspend fun previewCutoffChange(
        newCutoffDay: Int,
        asOf: LocalDate,
    ): CutoffChangePreview {
        CutoffPeriod.requireValidCutoffDay(newCutoffDay)
        val oldCutoffDay = cutoffDay()

        var changed = 0
        var before = 0L
        var after = 0L

        contractDao.observeAll().first().forEach { contract ->
            val entryBefore = runCatching {
                arrearsEntry(contract.id, asOf, cutoffDayOverride = oldCutoffDay)
            }.getOrNull() ?: return@forEach
            val entryAfter = runCatching {
                arrearsEntry(contract.id, asOf, cutoffDayOverride = newCutoffDay)
            }.getOrNull() ?: return@forEach

            before += entryBefore.amount
            after += entryAfter.amount
            // The AC's count is of contracts whose **arrears figure moves**, not of contracts
            // examined: fourteen contracts where two change is "2 kontrak berubah", because the
            // owner's question is what this does to their books.
            if (entryBefore.amount != entryAfter.amount || entryBefore.days != entryAfter.days) {
                changed += 1
            }
        }

        return CutoffChangePreview(
            oldCutoffDay = oldCutoffDay,
            newCutoffDay = newCutoffDay,
            changedContractCount = changed,
            totalBefore = before,
            totalAfter = after,
        )
    }

    /**
     * Writes the cutoff day. **The only write Story 5.4 performs.**
     *
     * ## Why there is no recalculation here
     *
     * Because there is nothing to recalculate. Every arrears read calls `previewAllocation`, which
     * reads the cutoff from `settings` on each call — so the next read after this write already
     * uses the new day. A batch rewrite would be a second way to reach the same figures and a
     * second thing to keep in step with the first.
     *
     * ## Why this is one statement inside a transaction
     *
     * The AC requires the change be written in one transaction, and it is: one `putSetting`. The
     * transaction wrapper is there because it is the shape every multi-row write in this app
     * takes, and a future change that added a second row should inherit atomicity rather than
     * having to remember to add it.
     *
     * **No allocation row is touched, and that is structural rather than tested-by-review**: this
     * function has no statement that could reach the allocation table. `CutoffChangeTest` asserts
     * every row is identical before and after anyway, because a structural property is only worth
     * claiming if something would notice it breaking.
     *
     * @throws InvalidCutoffDayException when [newCutoffDay] is outside 1..28.
     */
    suspend fun setCutoffDay(newCutoffDay: Int) {
        CutoffPeriod.requireValidCutoffDay(newCutoffDay)
        inTransaction {
            settingDao.putSetting(
                SettingEntity(key = KEY_CUTOFF_DAY, value = newCutoffDay.toString()),
            )
        }
    }

    companion object {
        /** The settings key holding the cutoff day. Story 5.4 is what changes it. */
        const val KEY_CUTOFF_DAY = "cutoffDay"

        /**
         * The cutoff day used when nothing is stored.
         *
         * 25, because it is the day Indonesian rental cycles turn over in practice and it
         * keeps every month long enough to allocate. It is a **default, not a choice the
         * owner made** — which is why `SettingDao.findInt` supplies it on read instead of
         * the migration seeding a row: "unset" then stays distinguishable from "set to 25",
         * which is the difference Story 5.4 needs to show a real value.
         */
        const val DEFAULT_CUTOFF_DAY = 25

        /**
         * The id the re-allocation allocates a Rp 1 placeholder under, and then discards.
         *
         * A placeholder rather than "no payment at all" because `allocatePayments` needs a
         * payment to place, and a second allocate-nothing path would be the second
         * derivation this story exists to refuse. One rupiah cannot close a day at any rate
         * this app uses, and its row is filtered out before anything is written.
         */
        const val RECOMPUTE_ID = "__recompute__"
    }
}

/** How the money arrived, as the `payment.method` column spells it. */
enum class PaymentMethod {
    /** A transfer to a business account — verifiable against a bank mutation. */
    TransferBank,

    /**
     * Cash handed over in person.
     *
     * **No external record can verify it**, and that is not a gap — it is the nature of the
     * method. The epic context is explicit that the owner's note is then the only
     * breadcrumb, which is why `notes` exists rather than being an optional extra.
     */
    Tunai,
}

/**
 * A write naming a contract nobody holds — a deposit's, or Story 3.6's settlement.
 *
 * One type for the fact rather than one per caller, and **deliberately not
 * `ContractNotOpenException`**: that type's wording is "this contract has ended, so it is
 * read-only", which is false for a settlement. An ended contract is precisely the row
 * FR-9b exists for — its days were paid elsewhere — and `settleThrough` settles one happily,
 * so reporting it as un-openable would tell the owner the opposite of the truth about the
 * case they are trying to fix.
 */
class UnknownContractException(val contractId: String) :
    IllegalStateException("no contract with id $contractId")

/**
 * A correction that left a bank transfer with no business account.
 *
 * `null` means Tunai on every other path, so this cannot be a shape the entity rejects —
 * which is why the rule is a refusal here rather than a `require` on the row. Epic context:
 * *"two screens can pass the same UI check, so the check that counts is the one below the
 * screen."* The sheet already refuses it; this is the copy for a caller that did not come
 * through the sheet.
 */
class AccountRequiredException(val paymentId: String) :
    IllegalStateException("correcting deposit $paymentId needs a business account")

/**
 * A correction naming a business account the app does not hold.
 *
 * Checked rather than left to `payment.accountId`'s `RESTRICT` foreign key, because the
 * constraint refuses with a `SQLiteConstraintException` that names a table rather than an
 * account — and the sheet's own picker already offers only stored accounts, so the only way
 * here is a caller that bypassed it.
 */
class UnknownAccountException(val accountId: String) :
    IllegalStateException("no business account with id $accountId")

/**
 * A deposit that exists but whose surrounding records do not.
 *
 * ## Why it is a throw rather than a half-built return
 *
 * Because every row this names is protected by a `RESTRICT` foreign key — a contract
 * cannot go while a deposit points at it, a renter or vehicle cannot go while a contract
 * points at it — so the state is one the database forbids. That makes the throw the *loud*
 * half of a pair whose quiet half would be a correction form with a blank plate and a blank
 * rate: the owner sees an edit form and cannot tell what they are editing. Failing here
 * says the record is unusable, which is true and actionable.
 */
class MissingDepositRelationException(val paymentId: String, val relation: String) :
    IllegalStateException("deposit $paymentId has no resolvable $relation")

/**
 * What a deposit would do, in whole rupiah and whole days.
 *
 * ## Why these are plain figures and not dates
 *
 * The acceptance criterion forbids a per-day ledger here, and the reason is the owner's
 * actual question: they want to know whether the figure *looks right*, and a table of dates
 * is something they have to verify rather than something they can read. The per-day audit
 * still exists — in the deposit history — and this type deliberately has no field to put a
 * date in, so the ledger cannot creep back in as a "helpful" extra.
 *
 * ## Why [credit] is read from the allocator's own output
 *
 * Because credit is the `chargeDate = null` allocation row (AD-3), not "the amount that did
 * not fit". Deriving it as `amount − what was owed` would be a second derivation of a fact
 * the rows already hold, and a second derivation is a second thing that can be wrong.
 */
/**
 * What a cutoff change would do, computed before anything is written — Story 5.4.
 *
 * ## Why a count and a delta rather than per-contract rows
 *
 * Because the decision the owner is making is binary. A list of fourteen contracts with
 * before/after figures is a screen to read; the AC asks for *"berapa Kontrak dan berapa rupiah"*,
 * which is the two numbers that answer "is this worth doing".
 *
 * ## Why both totals, not only the difference
 *
 * Because the difference alone is ambiguous: `Rp 0` is both "nothing changes" and "everything
 * changed and cancelled out". The dialog shows the delta prominently and the two totals beneath,
 * so an owner who sees a zero delta still sees whether the books moved.
 */
data class CutoffChangePreview(
    val oldCutoffDay: Int,
    val newCutoffDay: Int,
    /** How many contracts' arrears **figure** changes — not how many were examined. */
    val changedContractCount: Int,
    /** Every contract's arrears under the day currently in force. */
    val totalBefore: Long,
    /** Every contract's arrears under the day the owner picked. */
    val totalAfter: Long,
) {
    /** `totalAfter - totalBefore`: positive means the owner would owe more after the change. */
    val totalDelta: Long get() = totalAfter - totalBefore

    /** Whether the change moves anything at all. */
    val isNoOp: Boolean get() = changedContractCount == 0 && totalDelta == 0L
}

data class DepositPreview(
    /** Arrears in whole rupiah before this deposit lands. */
    val arrearsBefore: Long,
    /** And after. The pair is the whole point — the AC's format is `before → after`. */
    val arrearsAfter: Long,
    /** How many charge days this deposit closes. */
    val daysClosed: Int,
    /** How many remain open afterwards. */
    val daysRemaining: Int,
    /**
     * Money with no day left to sit on, in whole rupiah.
     *
     * Zero is a real value here — it means "this deposit closed days and left nothing" —
     * and the preview **omits the credit line entirely** at zero rather than showing
     * `Kredit: Rp 0`, because a zero credit is not news and a line that always appears
     * stops being read.
     */
    val credit: Long,
) {
    /** Whether a credit line is warranted at all. See [credit]. */
    val hasCredit: Boolean get() = credit > 0L
}
/** A cancellation with no reason. The epic refuses these before the write. */
class BlankCancelReasonException(val paymentId: String) :
    IllegalStateException("cancelling deposit $paymentId requires a reason")

/** A second cancellation of a deposit already cancelled. */
class DepositAlreadyCanceledException(
    val paymentId: String,
    val existingReason: String?,
) : IllegalStateException("deposit $paymentId was already canceled")

/** A cancellation naming a deposit nobody holds. */
class UnknownDepositException(val paymentId: String) :
    IllegalStateException("no deposit with id $paymentId")
