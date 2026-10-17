package net.klinik.rental.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.klinik.rental.data.db.KlinikDatabase
import net.klinik.rental.data.db.dao.BusinessAccountDao
import net.klinik.rental.data.db.entity.BusinessAccountEntity
import net.klinik.rental.domain.model.BusinessAccount
import net.klinik.rental.domain.parse.normalizeAccountNumber
import net.klinik.rental.domain.validation.BlankAccountFieldException
import net.klinik.rental.domain.validation.InvalidAccountNumberException
import java.util.UUID

/**
 * The only way the UI reaches business accounts.
 *
 * ## Why this is a repository of its own rather than an extension of an existing one
 *
 * `ContractRepository` grew a second DAO in Story 2.6 rather than the app growing a second
 * repository, and the argument there was that the two tables are one concept — "a contract,
 * and the days not billed on it" — already held by one class. That argument does **not**
 * apply here: a business account has no relationship to a contract, a vehicle or a renter.
 * It is a standalone record the owner maintains, so folding it into another repository
 * would put two unrelated concepts in one class and give that class a reason to change that
 * has nothing to do with its name.
 *
 * ## What this layer owes the layers above
 *
 * Typed domain exceptions, never a `Result<_, String>`:
 *
 *  - [BlankAccountFieldException] — the bank name or the number was blank
 *  - [InvalidAccountNumberException] — the number was not in a storable format
 *
 * The epic context forbids carrying a domain message in a `Result`, because a `String` that
 * has already been rendered into Indonesian cannot be re-checked, re-translated or
 * re-worded. A typed exception keeps the *fact* and lets `PengaturanViewModel` choose the
 * copy at the moment it is shown. So this class does **not** build Indonesian text.
 *
 * ## What it never does
 *
 * It never exposes a DAO, so `ui/` cannot reach [BusinessAccountDao] and bypass the
 * blank-field and format checks by calling `insertBusinessAccount` directly.
 *
 * ## What it deliberately does not have
 *
 * No `delete`. Story 3.1's acceptance criterion is that an account a deposit uses cannot be
 * deleted, enforced in the data layer — and the `PAYMENT` table that would reference an
 * account is Story 3.2's and does not exist. A delete now would carry a guard whose
 * condition is vacuously true. See [BusinessAccountDao]'s KDoc for the full argument; the
 * short version is that shipping the appearance of a rule is worse than shipping neither
 * the rule nor its appearance.
 *
 * @param dao the account table.
 * @param database used only for [inTransaction]. Nullable to match the vehicle and contract
 *   precedents, whose tests build a repository over a DAO alone — see [inTransaction].
 */
class BusinessAccountRepository(
    private val dao: BusinessAccountDao,
    private val database: KlinikDatabase? = null,
) {

    /**
     * Every account, bank first, as a [Flow].
     *
     * The projection to the domain model happens here rather than in the ViewModel, so
     * `ui/` never sees an entity and there is one place a column rename has to be followed
     * to.
     */
    fun observeAccounts(): Flow<List<BusinessAccount>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    /**
     * Stores [account], inserting when its id is new and updating in place when it is not.
     *
     * ## Why one method rather than separate add and edit calls
     *
     * The caller does not know which it is. `AccountFormState` carries a nullable id —
     * `null` while adding, set while editing — and the *repository* decides from the stored
     * row rather than from a flag the UI passed. A caller that sent `id = null` for an edit
     * would otherwise insert a second account for one bank, and the owner would have two
     * rows that look identical.
     *
     * ## Why the number is normalised here rather than trusted from the caller
     *
     * The stored value is the one the owner will later match against a bank mutation, so
     * every writer has to produce the same form or two spellings of one account become two
     * rows. Normalising in the repository means a caller holding a form and no domain model
     * still gets the canonical value — the same reason `VehicleRepository.save` normalises
     * the plate itself rather than relying on `Vehicle`'s constructor.
     *
     * ## Why the blank-field checks are not here
     *
     * They are in `BusinessAccount.init`, which throws the same typed exceptions. This method
     * takes a `BusinessAccount`, so a blank field cannot arrive — the model refused it before
     * the call. A second check here would never run, and dead validation reads as a guard while
     * protecting nothing.
     *
     * @throws BlankAccountFieldException from the **model's** constructor, not from this method.
     * @throws InvalidAccountNumberException when the number is not in a storable format —
     *   see `normalizeAccountNumber`, which validates format and **nothing else**.
     */
    suspend fun save(account: BusinessAccount) {
        // 1. The number's **format**, which is the one rule this layer owns. A blank bank name
        //    or a blank number cannot reach here: `BusinessAccount.init` refuses both with the
        //    typed exceptions, and this method takes a `BusinessAccount` rather than raw
        //    strings. Duplicating those checks would be dead code, and dead validation is worse
        //    than none — it reads as a guard while never running.
        //
        //    Format is different: the model accepts any non-blank string, because whether a
        //    string is a plausible account number is a storage question rather than an
        //    identity one. So the refusal lives here.
        val number = normalizeAccountNumber(account.number)
            ?: throw InvalidAccountNumberException(account.number)

        val normalized = account.copy(bankName = account.bankName.trim(), number = number)

        // 3. Insert or update, decided from the stored row. One transaction, because the
        //    decision reads and the write follows and the two must see one snapshot.
        inTransaction {
            val stored = dao.findById(normalized.id)
            if (stored == null) {
                dao.insertBusinessAccount(normalized.toEntity())
            } else {
                dao.updateBusinessAccount(normalized.toEntity())
            }
        }
    }

    /**
     * A fresh id for a new account.
     *
     * Generated here rather than in the domain model, which AD-1 keeps free of
     * `java.util.UUID` — the same split `newContractId` and `newVehicleId` use.
     */
    fun newAccountId(): String = UUID.randomUUID().toString()

    /**
     * Runs [block] as one transaction.
     *
     * Mirrors `VehicleRepository.inTransaction` and `ContractRepository.inTransaction`. A
     * `null` database falls back to running [block] directly, which is not a weakening: the
     * only multi-step write here is insert-vs-update on a single row, and a race between two
     * callers for the same id surfaces as a primary-key refusal rather than as a lost row.
     * The parameter is nullable to match the vehicle precedent, whose tests build the
     * repository over a DAO alone.
     */
    suspend fun inTransaction(block: suspend () -> Unit) {
        database?.withTransaction { block() } ?: block()
    }

    /** Entity -> domain. The mapping the UI never sees. */
    private fun BusinessAccountEntity.toDomain() = BusinessAccount(
        id = id,
        bankName = bankName,
        number = number,
    )

    /**
     * Domain -> entity.
     *
     * A plain copy in both directions: an account owns every field it carries, and there is
     * nothing to derive — unlike `ContractEntity`'s active columns, which are a projection
     * of `isOpen`. That is what keeps an edit from having to recompute anything.
     */
    private fun BusinessAccount.toEntity() = BusinessAccountEntity(
        id = id,
        bankName = bankName,
        number = number,
    )
}
