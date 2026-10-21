package net.klinik.rental.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.klinik.rental.data.db.dao.RenterDao
import net.klinik.rental.data.db.entity.RenterEntity
import net.klinik.rental.domain.model.Renter
import net.klinik.rental.domain.validation.DuplicateNameException
import java.util.UUID

/**
 * The only way the UI reaches renter data.
 *
 * ## What this layer owes the layers above
 *
 * One typed domain exception, never a `Result<_, String>`:
 *
 *  - [DuplicateNameException] — the name is already stored by another renter
 *
 * The epic context forbids carrying a domain message in a `Result`, because a
 * `String` that has already been rendered into Indonesian cannot be re-checked,
 * re-translated or re-worded. A typed exception keeps the *fact* and lets
 * `PenyewaViewModel` choose the copy at the moment it is shown.
 *
 * So this class does **not** build Indonesian text. That is the deliberate
 * omission: the moment a repository returns a message, two screens can show two
 * different sentences for the same failure, and a test can no longer assert which
 * one is correct.
 *
 * ## What it never does
 *
 * It never exposes a DAO. `ui/` cannot reach [RenterDao], so it cannot bypass
 * the uniqueness check by calling `insert` directly.
 *
 * ## Why the save order matches `VehicleRepository`
 *
 * The vehicle path is the established precedent for every data-layer write in
 * this app, and the renter path follows it step for step — check the rule,
 * pre-check the collision, then insert-or-update matched on the primary key —
 * so a second screen writes a renter the same way the fleet is written today.
 *
 * There is one thing deliberately absent: no normalisation step. The vehicle path
 * normalises the plate because the index is built on the normalised value; this
 * index is built on the exact name, so there is nothing to normalise and
 * normalising anyway would corrupt the name the owner typed.
 *
 * And one thing the vehicle path has that this deliberately does not: no
 * `inTransaction` helper and no database handle. Every renter write touches a
 * single row and feeds no calculation, so a transaction wrapper here would be
 * scaffolding for a write that does not exist yet; `VehicleRepository` grew one
 * because its delete path could.
 */
class RenterRepository(
    private val dao: RenterDao,
) {

    /**
     * Every renter, observed.
     *
     * The `Flow` comes straight from Room's invalidation tracker, so a save
     * from the form re-emits here and the list updates with no refresh and no
     * manual signal. Nothing in this class writes to the flow — the epic context
     * forbids a `Flow` write, and a write here would need its own invalidation
     * path, which is the second refresh mechanism that can disagree with Room's.
     */
    fun observeRenters(): Flow<List<Renter>> =
        // A lambda rather than `RenterEntity::toDomain`: `toDomain` is a member
        // extension, and Kotlin does not allow a callable reference to one.
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    /**
     * Inserts or updates [renter], enforcing every write rule.
     *
     * The order of the checks matters and is not arbitrary:
     *
     *  1. **reject a blank name** — a name with no non-whitespace character is
     *     not a person the owner can search for again, and it is what an
     *     untouched field produces when Save is tapped
     *  2. **check for a collision** — a pre-check so the owner finds out before
     *     leaving the sheet
     *  3. **insert or update** — and let the `UNIQUE` index refuse if two
     *     callers raced past step 2
     *
     * An edit is matched on [Renter.id]: the same id updates in place, a
     * different id with a taken name is a duplicate. That is what makes the AC
     * "edit one renter, the other is unchanged" hold — the update touches one
     * row by primary key and cannot reach its neighbour.
     *
     * @throws DuplicateNameException when [renter]'s name is already stored by a
     *   *different* renter.
     * @throws IllegalArgumentException when the name is blank.
     *
     * ## Why edit-or-insert is asked separately from the duplicate check
     *
     * Two questions that look like one:
     *
     *  - is this name taken by someone else? — the duplicate rule (AR-12)
     *  - is this id already stored? — whether the write is an edit or an insert
     *
     * [VehicleRepository.save] answers the second only when the first is yes, and
     * that ordering has a hole: an edit that *changes* the key to an unused value
     * finds no collision, asks no second question, and falls through to an
     * INSERT carrying an id already on disk. Room refuses that as a
     * primary-key conflict rather than a name conflict, so the refusal names the
     * wrong column. This path asks both questions unconditionally, which is why
     * it also fixes renaming rather than reproducing the precedent's shape.
     */
    suspend fun save(renter: Renter) {
        // 1. Fail before the database rather than after. `Renter`'s constructor
        // would also refuse this, but checking here means the rule holds for a
        // caller that has a form and no `Renter` yet.
        require(renter.name.isNotBlank()) { "name must not be blank" }

        // Trim here, not only in the ViewModel. The name IS the unique key, so
        // the string that reaches the index must not carry invisible differences:
        // "Budi Santoso" and " Budi Santoso" are one person but two keys, and the
        // second is invisible in every list the owner sees. Trimming in the form
        // is a courtesy for one caller; doing it here is what makes the guarantee
        // hold for the next one. VehicleRepository does the same with
        // normalizePlate for the same reason.
        val name = renter.name.trim()
        require(name.isNotBlank()) { "name must not be blank" }
        val clean = renter.copy(name = name)

        // 2. Two independent facts, and they are not interchangeable.
        //
        // `holderId` answers "is this name taken, and by whom?" — the duplicate
        // question. `existing` answers "is this id already stored?" — the
        // edit-or-insert question.
        //
        // Asking the second question only when the first is true would make an
        // edit that *changes* the name fall through to an insert: the new name is
        // unused, so nothing says "existing", so an INSERT goes out carrying an id
        // that is already on disk. SQLite refuses it as a primary-key conflict,
        // and `insertEntity` would report that as a duplicate name — a message
        // about the wrong column, naming a collision the owner never caused.
        val holderId = dao.findIdByName(name)
        val existing = dao.findById(clean.id)

        when {
            // Somebody else holds this name, whether or not this row is stored.
            holderId != null && holderId != clean.id ->
                throw DuplicateNameException(name)

            // The name is ours (or unused) and the id is stored: this is an edit,
            // and the update touches one row by primary key.
            existing != null -> updateEntity(clean)

            // A new renter.
            else -> insertEntity(clean)
        }
    }

    /**
     * Inserts, translating the `UNIQUE` index's refusal.
     *
     * This is the half of uniqueness that cannot be pre-checked away: two
     * callers can both pass `findName` before either writes, and only the index
     * settles it. Android's SQLite reports a unique violation as
     * `SQLiteConstraintException`, whose message names the constraint but is not
     * a stable API — so the name is re-read and reported from the value that
     * collided, which is what the owner needs to see.
     */
    private suspend fun insertEntity(renter: Renter) {
        try {
            dao.insert(renter.toEntity())
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            // The translation is verified, not assumed. Re-read who holds the
            // name: only if somebody else does is this a name collision, and only
            // then is "duplicate name" the truth. A primary-key clash or a NOT
            // NULL violation is rethrown untouched, because relabelling it as a
            // duplicate would tell the owner that a name nobody holds is already
            // taken, and send them to edit the one field that was never wrong.
            val holderId = dao.findIdByName(renter.name)
            if (holderId != null && holderId != renter.id) {
                throw DuplicateNameException(dao.findName(renter.name) ?: renter.name)
            }
            throw e
        }
    }

    /**
     * Updates in place, keyed on the primary key.
     *
     * An update cannot hit the name unique index — it is replacing that row's
     * own name with its own name — so a constraint failure here would mean
     * something else entirely, and is deliberately not swallowed into a
     * `DuplicateNameException` that would misdescribe it.
     */
    private suspend fun updateEntity(renter: Renter) {
        try {
            dao.update(renter.toEntity())
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            // An update CAN hit the name index when it renames: the pre-check and
            // the write are two statements, so a concurrent caller that takes the
            // name in between makes SQLite refuse here. That refusal is a real
            // duplicate and is translated, because the alternative is a raw
            // SQLiteConstraintException reaching the ViewModel, landing in its
            // generic handler and showing "Gagal menyimpan. Coba lagi." with no
            // name and no person to look at. Anything else rethrows.
            val holderId = dao.findIdByName(renter.name)
            if (holderId != null && holderId != renter.id) {
                throw DuplicateNameException(dao.findName(renter.name) ?: renter.name)
            }
            throw e
        }
    }

    /**
     * A fresh renter id.
     *
     * `UUID.randomUUID()` here rather than `Date.now()`-derived or hardcoded
     * values: the story forbids seeded data and `Date.now()` ids, and Room
     * generates nothing on its own. It lives in `data/` because the domain's
     * import surface excludes `java.util.UUID` — the id arrives as a `String`
     * from the layer that owns persistence.
     */
    fun newRenterId(): String = UUID.randomUUID().toString()

    /** Entity -> domain. The mapping the UI never sees. */
    private fun RenterEntity.toDomain() = Renter(
        id = id,
        name = name,
        phone = phone,
        bankAccount = bankAccount,
        emergencyContact = emergencyContact,
        avatar = avatar,
    )

    /** Domain -> entity. */
    private fun Renter.toEntity() = RenterEntity(
        id = id,
        name = name,
        phone = phone,
        bankAccount = bankAccount,
        emergencyContact = emergencyContact,
        avatar = avatar,
    )
}