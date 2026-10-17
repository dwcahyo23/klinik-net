package net.klinik.rental.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.klinik.rental.data.db.KlinikDatabase
import net.klinik.rental.data.db.dao.AllocationDao
import net.klinik.rental.data.db.dao.ContractDao
import net.klinik.rental.data.db.dao.PaymentDao
import net.klinik.rental.data.db.dao.ServiceExceptionDao
import net.klinik.rental.data.db.dao.SettingDao
import net.klinik.rental.data.db.entity.AllocationEntity
import net.klinik.rental.data.db.entity.ContractEntity
import net.klinik.rental.data.db.entity.PaymentEntity
import net.klinik.rental.domain.calculate.ChargeSchedule
import net.klinik.rental.domain.calculate.CutoffPeriod
import net.klinik.rental.domain.calculate.allocatePayments
import net.klinik.rental.domain.calculate.arrearsOf
import net.klinik.rental.domain.calculate.chargeDaysIn
import net.klinik.rental.domain.model.Allocation
import net.klinik.rental.domain.model.Payment
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
    ): ComputedAllocation {
        // 1. The stored contract, for its frozen rate, its period and its schedule.
        //    Read rather than taken from the caller: a `Contract` handed in may be
        //    stale, and a rate re-derived from the vehicle would reprice a running
        //    rental.
        val contract = contractDao.findById(contractId)
            ?: throw UnknownContractException(contractId)

        // 2. The cutoff day, with the default supplied on read. A fresh install has no
        //    settings row at all and must still record a deposit.
        val cutoffDay = settingDao.findInt(KEY_CUTOFF_DAY, DEFAULT_CUTOFF_DAY)

        // 3. The charge days this contract owes in the current period, with its stored
        //    exceptions applied. `contractEnd` comes from the row, so an ended contract
        //    stops producing days without this class knowing.
        val chargeDays = chargeDaysIn(
            period = CutoffPeriod.containing(cutoffDay, asOf),
            schedule = ChargeSchedule(
                contractStart = contract.startDate,
                restDay = contract.restDay,
                serviceExceptions = storedExceptions(contract.id),
                contractEnd = contract.endDate,
            ),
            asOf = asOf,
        )

        // 4. The payment and everything already recorded, allocated **together**.
        val existing = paymentDao.forContract(contract.id).map { it.toDomain() }
        val allRows = allocatePayments(
            payments = existing + payment,
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

    /** Every deposit on one contract, as domain values. */
    suspend fun paymentsFor(contractId: String): List<Payment> =
        paymentDao.forContract(contractId).map { it.toDomain() }

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

/** Thrown when a deposit names a contract nobody holds. */
class UnknownContractException(val contractId: String) :
    IllegalStateException("no contract with id $contractId")

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