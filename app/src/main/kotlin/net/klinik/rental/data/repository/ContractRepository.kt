package net.klinik.rental.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.klinik.rental.data.db.KlinikDatabase
import net.klinik.rental.data.db.dao.ContractDao
import net.klinik.rental.data.db.entity.ContractEntity
import net.klinik.rental.domain.model.Contract
import net.klinik.rental.domain.parse.requirePositiveRate
import net.klinik.rental.domain.validation.ContractAlreadyActiveException
import java.util.UUID

/**
 * The only way the UI reaches contract data.
 *
 * ## What this layer owes the layers above
 *
 * One typed domain exception, never a `Result<_, String>`:
 *
 *  - [ContractAlreadyActiveException] — the vehicle or the renter already has an
 *    open contract
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
 * It never exposes a DAO. `ui/` cannot reach [ContractDao], so it cannot bypass
 * the one-active check by calling `insert` directly.
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
 * deterministic rather than dependent on which query happened to return first.
 */
class ContractRepository(
    private val dao: ContractDao,
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
     */
    suspend fun open(contract: Contract) {
        // 1. Fail before the database rather than after. `Contract`'s constructor
        // would also refuse this, but checking here means the rule holds for a
        // caller that has a form and no `Contract` yet.
        requirePositiveRate(contract.dailyRate)

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

    /**
     * Runs [block] as one transaction.
     *
     * Mirrors `VehicleRepository.inTransaction`, and is the helper that story added
     * in 2.2 precisely so the first multi-statement write would not have to
     * rediscover `withTransaction`. Opening a contract is that write: a pre-check
     * plus an insert that must see one snapshot.
     *
     * A `null` database falls back to running [block] directly. That is not a
     * weakening of the rule — the two `UNIQUE` indexes are the rule and they hold
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

    /** Entity -> domain. The mapping the UI never sees. */
    private fun ContractEntity.toDomain() = Contract(
        id = id,
        renterId = renterId,
        vehicleId = vehicleId,
        startDate = startDate,
        dailyRate = dailyRate,
        restDay = restDay,
        endDate = endDate,
    )

    /**
     * Domain -> entity.
     *
     * The active columns are derived here and nowhere else, which is what makes
     * them a *copy* that cannot drift: an open contract carries its own ids in
     * both slots, an ended one carries `NULL` in both, and
     * [ContractEntity]'s `init` refuses anything else. There is no second writer
     * to disagree with.
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
    )
}
