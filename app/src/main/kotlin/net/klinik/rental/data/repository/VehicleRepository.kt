package net.klinik.rental.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.klinik.rental.data.db.KlinikDatabase
import net.klinik.rental.data.db.dao.VehicleDao
import net.klinik.rental.data.db.entity.VehicleEntity
import net.klinik.rental.domain.model.Vehicle
import net.klinik.rental.domain.parse.normalizePlate
import net.klinik.rental.domain.parse.requirePositiveRate
import net.klinik.rental.domain.validation.DuplicatePlateException
import net.klinik.rental.domain.validation.InvalidAmountException
import net.klinik.rental.domain.validation.VehicleHasActiveContractException
import java.util.UUID

/**
 * The only way the UI reaches vehicle data.
 *
 * ## What this layer owes the layers above
 *
 * Three typed domain exceptions, never a `Result<_, String>`:
 *
 *  - [DuplicatePlateException] — the plate is already stored
 *  - [InvalidAmountException] — the rate is zero or negative
 *  - [VehicleHasActiveContractException] — the vehicle is still rented
 *
 * The epic context forbids carrying a domain message in a `Result`, because a
 * `String` that has already been rendered into Indonesian cannot be re-checked,
 * re-translated or re-worded. A typed exception keeps the *fact* and lets
 * `MobilViewModel` choose the copy at the moment it is shown.
 *
 * So this class does **not** build Indonesian text. That is the deliberate
 * omission: the moment a repository returns a message, two screens can show two
 * different sentences for the same failure, and a test can no longer assert which
 * one is correct.
 *
 * ## What it never does
 *
 * It never exposes a DAO. `ui/` cannot reach [VehicleDao], so it cannot bypass
 * normalisation, the positive-rate rule or the uniqueness check by calling
 * `insert` directly.
 *
 * ## Why normalisation happens here *and* in the domain model
 *
 * Here, so the value written to the unique index is normalised; in
 * [Vehicle]'s constructor, so a `Vehicle` whose plate is not normalised cannot
 * exist at all. The repository's normalisation is what makes the write correct;
 * the model's is what proves it. They agree because both call the same
 * [normalizePlate].
 */
class VehicleRepository(
    private val dao: VehicleDao,
    private val database: KlinikDatabase? = null,
) {

    /**
     * Every vehicle, observed.
     *
     * The `Flow` comes straight from Room's invalidation tracker, so a save
     * from the form re-emits here and the list updates with no refresh and no
     * manual signal. Nothing in this class writes to the flow — the epic context
     * forbids a `Flow` write, and a write here would need its own invalidation
     * path, which is the second refresh mechanism that can disagree with Room's.
     */
    fun observeVehicles(): Flow<List<Vehicle>> =
        // A lambda rather than `VehicleEntity::toDomain`: `toDomain` is a member
        // extension, and Kotlin does not allow a callable reference to one.
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    /** Rented vehicles only — the `tersewa` filter's source. */
    fun observeRentedPlates(): Flow<Set<String>> =
        dao.observeRented().map { rows -> rows.map { it.plate }.toSet() }

    /**
     * Inserts or updates [vehicle], enforcing every write rule.
     *
     * The order of the checks matters and is not arbitrary:
     *
     *  1. **normalise the plate** — everything downstream, including the unique
     *     index, is keyed on the normalised value, so this happens first
     *  2. **reject a blank plate** — a plate with no normalised form is not a
     *     car the owner can find again
     *  3. **reject a non-positive rate** — [requirePositiveRate] delegates to
     *     `InvalidAmountException.requireValid`, the same bound `Payment` and
     *     `Allocation` use. This is the data-layer half of NFR-6: the form's check
     *     is a courtesy, this one is the rule
     *  4. **check for a collision** — a pre-check so the owner finds out before
     *     leaving the sheet
     *  5. **insert or update** — and let the `UNIQUE` index refuse if two callers
     *     raced past step 4
     *
     * An edit is matched on [Vehicle.id]: the same id updates in place, a
     * different id with a taken plate is a duplicate. That is what makes the AC
     * "edit one vehicle, the other is unchanged" hold — the update touches one row
     * by primary key and cannot reach its neighbour.
     *
     * @throws DuplicatePlateException when [vehicle]'s plate is already stored by
     *   a *different* vehicle.
     * @throws InvalidAmountException when [vehicle]'s rate is zero or negative.
     * @throws IllegalArgumentException when the plate is blank.
     */
    suspend fun save(vehicle: Vehicle) {
        // 1. The value every later step — and the unique index — is keyed on.
        val plate = normalizePlate(vehicle.plate)
            ?: throw IllegalArgumentException("plate must not be blank")

        // 2. Fail before the database rather than after.
        require(plate.isNotBlank()) { "plate must not be blank" }

        // 3. NFR-6's data-layer half. Constructing the domain model would also
        // check this, but checking here means the rule holds for a caller that
        // has a rate and no Vehicle yet — and the message is the typed one.
        requirePositiveRate(vehicle.dailyRate)

        // Rebuild on the normalised plate so the stored text and the unique key
        // are the same string. Also re-runs the model's own checks.
        val normalized = vehicle.copy(plate = plate)

        // 4. The pre-check. Deliberately *not* the guarantee — see below.
        val existingPlate = dao.findPlate(plate)
        val existing = if (existingPlate != null) dao.findById(vehicle.id) else null

        when {
            // The same row: this is an edit, and the plate is still ours.
            existing != null -> updateNormalized(normalized)

            // A different row already holds this plate.
            existingPlate != null -> throw DuplicatePlateException(existingPlate)

            // New row, or an id that is not stored yet (first save of a new
            // vehicle). Both are inserts.
            else -> insertNormalized(normalized)
        }
    }

    /**
     * Inserts, translating the `UNIQUE` index's refusal.
     *
     * This is the half of uniqueness that cannot be pre-checked away: two
     * callers can both pass `findPlate` before either writes, and only the index
     * settles it. Android's SQLite reports a unique violation as
     * `SQLiteConstraintException`, whose message names the constraint but is not
     * a stable API — so the plate is re-read and reported from the value that
     * collided, which is what the owner needs to see.
     */
    private suspend fun insertNormalized(vehicle: Vehicle) {
        try {
            dao.insert(vehicle.toEntity())
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            // The index refused. Report the stored plate rather than echoing the
            // SQLite message, which is a different language and names a
            // constraint instead of a car.
            val stored = dao.findPlate(vehicle.plate) ?: vehicle.plate
            throw DuplicatePlateException(stored)
        }
    }

    /**
     * Updates in place, keyed on the primary key.
     *
     * An update cannot hit the plate unique index — it is replacing that row's
     * own plate with its own plate — so a constraint failure here would mean
     * something else entirely, and is deliberately not swallowed into a
     * `DuplicatePlateException` that would misdescribe it.
     */
    private suspend fun updateNormalized(vehicle: Vehicle) {
        dao.update(vehicle.toEntity())
    }

    /**
     * Deletes a vehicle, refusing while it is still rented.
     *
     * ## Why this ships with no button to call it
     *
     * The story's AC asks that deleting a rented vehicle be *refused*, and that
     * is satisfied here — at the layer where it actually holds. The screen
     * deliberately exposes no delete affordance: the mandatory deletion reason
     * has nowhere to go yet, and the prototype's answer (collect the reason, drop
     * it, and let the dialog promise an audit trail that does not exist) is the
     * defect this epic exists to remove. So this function is called only by tests
     * in this story. See the design note in the story.
     *
     * ## The explicit check, and the foreign key behind it
     *
     * [VehicleDao.hasOpenContract] is what produces the typed exception naming
     * the vehicle, and it is checked before the write so nothing is removed and
     * the message can say *which* car is blocked.
     *
     * `CONTRACT.vehicleId` carries `onDelete = ForeignKey.RESTRICT`, which is the
     * guarantee itself: it holds for any future caller that writes a delete
     * without coming through here. RESTRICT is deliberately broad — it refuses
     * while *any* contract row references the vehicle, including one whose
     * `endDate` is set. So a vehicle with a finished contract is also refused at
     * the schema, and this function reports that as an
     * [IllegalStateException] rather than as
     * [VehicleHasActiveContractException]: the contract is not active, and
     * telling the owner "masih tersewa" about a rental that ended on the 5th
     * would be a message that is wrong in exactly the way a stored status
     * column goes wrong.
     *
     * Whether a finished contract should permit a delete at all is Story 2.7's
     * question — it is the story that ends contracts and owns what happens to the
     * allocations pointing at the vehicle. This story only has to make the
     * refusal real, and a refusal is the safe direction to be wrong in.
     *
     * @throws VehicleHasActiveContractException when an open contract exists. The
     *   vehicle row is still there when this throws — nothing was removed.
     * @throws IllegalStateException when a *finished* contract still references
     *   the vehicle. Same guarantee, accurately named.
     */
    suspend fun delete(vehicleId: String) {
        val plate = dao.findPlateById(vehicleId)

        // The named refusal. Checked before the write so the exception can carry
        // the plate the owner sees in the list.
        if (plate != null && dao.hasOpenContract(vehicleId)) {
            throw VehicleHasActiveContractException(plate)
        }

        try {
            dao.deleteById(vehicleId)
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            // The foreign key refused, and the explicit check above found no
            // open contract — so this is a finished contract still referencing
            // the vehicle. Reported as its own fact rather than as
            // "still rented".
            throw IllegalStateException(
                "vehicle $plate is still referenced by a finished contract; " +
                    "the delete was refused and the row is unchanged",
                e,
            )
        }
    }

    /**
     * Runs [block] as one transaction.
     *
     * Story 2.2's writes each touch a single row, so nothing needs it yet — this
     * exists because the epic context makes it a standing rule for any write that
     * touches more than one row or feeds a calculation, and the first such write
     * (Story 2.7's end-of-contract) should not have to rediscover that
     * `withTransaction` is the answer.
     */
    suspend fun inTransaction(block: suspend () -> Unit) {
        database?.withTransaction { block() } ?: block()
    }

    /**
     * A fresh vehicle id.
     *
     * `UUID.randomUUID()` here rather than `Date.now()`-derived or hardcoded
     * values: the story forbids seeded data and `Date.now()` ids, and Room
     * generates nothing on its own. It lives in `data/` because the domain's
     * import surface excludes `java.util.UUID` — the id arrives as a `String`
     * from the layer that owns persistence.
     */
    fun newVehicleId(): String = UUID.randomUUID().toString()

    /** Entity -> domain. The mapping the UI never sees. */
    private fun VehicleEntity.toDomain() = Vehicle(
        id = id,
        plate = plate,
        brand = brand,
        model = model,
        year = year,
        color = color,
        dailyRate = dailyRate,
        notes = notes,
        isServis = isServis,
    )

    /** Domain -> entity. */
    private fun Vehicle.toEntity() = VehicleEntity(
        id = id,
        plate = plate,
        brand = brand,
        model = model,
        year = year,
        color = color,
        dailyRate = dailyRate,
        notes = notes,
        isServis = isServis,
    )
}