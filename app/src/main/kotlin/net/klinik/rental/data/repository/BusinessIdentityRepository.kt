package net.klinik.rental.data.repository

import androidx.room.withTransaction
import net.klinik.rental.data.db.KlinikDatabase
import net.klinik.rental.data.db.dao.SettingDao
import net.klinik.rental.data.db.entity.SettingEntity

/**
 * The business identity the reminder message quotes — Story 4.4.
 *
 * ## Why this is a repository of its own, and not a screen-held key
 *
 * Because the name is **read by more than one place**. Story 4.2 composes the WhatsApp
 * message and 4.5's report header repeats it, and a key owned by a screen would have to be
 * re-declared at each of those. `PaymentRepository.KEY_CUTOFF_DAY` is the same argument for the
 * same reason: the cutoff is read by the allocation engine and written by a settings screen, so
 * neither owns it alone.
 *
 * ## Why the default lives here and not in a migration
 *
 * Because `SettingDao` supplies its defaults on read, and its KDoc already argues why: a row
 * seeded at migration time is a value the owner never chose, and it makes "unset"
 * indistinguishable from "set to the default" — which is exactly the distinction Story 4.4
 * needs, because the default name **is** editable. Writing a row the owner never entered would
 * turn an edit into an update of something they never set.
 *
 * ## What it owes the layers above
 *
 * One typed exception, [BlankIdentityNameException], never a `Result<_, String>`: a `String`
 * that has already been rendered into Indonesian cannot be re-checked or re-worded. So this
 * class names the fault and leaves the copy to `PengaturanViewModel`.
 *
 * ## Why it never exposes the DAO
 *
 * So `ui/` cannot reach `putSetting` directly and write a name this class would have refused —
 * the rule the epic context restates: two screens can pass the same UI check, so the check that
 * counts is the one under them.
 *
 * @param database used only for [inTransaction]. Nullable to match the account and contract
 *   precedents, whose tests build a repository over a DAO alone.
 */
class BusinessIdentityRepository(
    private val dao: SettingDao,
    private val database: KlinikDatabase? = null,
) {

    /**
     * The identity as stored, with the default name filled in for a key that was never written.
     *
     * The default is applied **here**, on the way out, rather than at each read site — so a
     * screen that forgot to apply it would show a blank name rather than the owner's last
     * choice. One place supplies it, and a caller cannot opt out by forgetting.
     *
     * **Three keys, three `findValue` calls, one snapshot.** They are separate queries because
     * `SettingDao` reads a key at a time and the alternative — three callers each reading one
     * key and assembling the identity — is a figure built from three moments in time, which is
     * the mixed-state failure the epic context names for anything that leaves the app.
     */
    suspend fun identity(): BusinessIdentity = BusinessIdentity(
        name = resolveName(),
        address = readOptional(KEY_ADDRESS),
        phone = readOptional(KEY_PHONE),
    )

    /**
     * The business name alone — the one value Story 4.2 puts in a message.
     *
     * Its own method rather than `identity().name` at every call site, because this is the one
     * field that leaves the app and it must never be assembled with a fallback that differs from
     * this repository's: two defaults would be two names in the same message. And it reads
     * **one** key rather than going through [identity] — a message needs the name, not three
     * settings rows it will never print.
     */
    suspend fun businessName(): String = resolveName()

    /**
     * The stored name, or the default for a key that was never written.
     *
     * The one place [DEFAULT_NAME] is applied, so a caller cannot reach a different fallback
     * than this repository's — which matters because a second default would be a second
     * business name in the same message.
     */
    private suspend fun resolveName(): String =
        dao.findValue(KEY_NAME)?.takeIf { it.isNotBlank() } ?: DEFAULT_NAME

    /**
     * An optional stored value, or `null` for a key that was never written.
     *
     * `null` and `""` are kept apart deliberately: a row holding an empty string is a value the
     * owner set to nothing, and a message quoting it would print a blank line where the owner
     * expected their address.
     */
    private suspend fun readOptional(key: String): String? =
        dao.findValue(key)?.takeIf { it.isNotBlank() }

    /**
     * Stores all three fields as one block.
     *
     * ## Why one transaction and not three writes
     *
     * Because a half-saved identity is an identity the owner did not enter: a name with no
     * address is a business whose outgoing message quotes half of itself. Three upserts without
     * a transaction could interleave with a second save and leave a name from one attempt beside
     * an address from another — and nothing would show it, because each row is individually
     * valid.
     *
     * A blank [name] is refused **before** the transaction opens. That is a form state rather
     * than a data condition, so there is no reason to take a write lock to discover one.
     *
     * ## Why address and phone are not validated
     *
     * Because they are free text the owner types for outsiders, and a format rule here would
     * refuse real addresses and real phone formats — the owner writes them once and expects them
     * to send. They are trimmed, and a blank one is stored as **absent** rather than as `""`,
     * so a later read cannot confuse "not filled in" with "filled in with nothing".
     *
     * @throws BlankIdentityNameException when [name] is blank. Whitespace is blank — whatever
     *   produced it, an empty business name is an empty business name.
     */
    suspend fun save(name: String, address: String?, phone: String?) {
        if (name.isBlank()) throw BlankIdentityNameException(name)

        val trimmedName = name.trim()
        val trimmedAddress = address?.trim()?.takeIf { it.isNotBlank() }
        val trimmedPhone = phone?.trim()?.takeIf { it.isNotBlank() }

        inTransaction {
            dao.putSetting(SettingEntity(key = KEY_NAME, value = trimmedName))
            // The absent ones are **deleted**, not written as empty. A row holding `""` is a
            // value the owner can clear later, which is not the same as a field they never
            // filled in — and a cleared field would otherwise read back as present-but-empty.
            clearIfAbsent(KEY_ADDRESS, trimmedAddress)
            clearIfAbsent(KEY_PHONE, trimmedPhone)
        }
    }

    /**
     * Runs [block] as one transaction.
     *
     * The same nullable-database shape as `BusinessAccountRepository.inTransaction`, for the
     * same reason: the tests build the repository over a DAO alone, and `withTransaction`
     * asserts it is off the main thread.
     */
    suspend fun inTransaction(block: suspend () -> Unit) {
        database?.withTransaction { block() } ?: block()
    }

    /**
     * Writes the value, or removes the key when there is none to write.
     *
     * `SettingDao` has no delete and this story does not add one: a clearable field that cannot
     * be cleared is a setting the owner can set once and never change — and the address and
     * phone are exactly the fields an owner changes when the business moves.
     */
    private suspend fun clearIfAbsent(key: String, value: String?) {
        if (value == null) dao.deleteSetting(key) else dao.putSetting(SettingEntity(key, value))
    }

    companion object {
        const val KEY_NAME = "businessName"
        const val KEY_ADDRESS = "businessAddress"
        const val KEY_PHONE = "businessPhone"

        /**
         * The name a fresh install starts with.
         *
         * The prototype's own, pre-filled and **editable** rather than compiled into a message.
         * Three hardcoded copies of it are what this story exists to replace, so the default
         * lives in exactly one place and every reader takes it from here — a second copy in a
         * screen would be the third.
         */
        const val DEFAULT_NAME = "PT Klinik Net Rent Car"
    }
}

/**
 * The stored business identity, address and phone **nullable**.
 *
 * Nullable rather than empty-string because the two states are genuinely different: an address
 * the owner typed and one they never filled in are not the same fact, and a message that quotes
 * an address should not have to decide which of them it is quoting.
 */
data class BusinessIdentity(
    val name: String,
    val address: String?,
    val phone: String?,
)

/**
 * A save with no business name.
 *
 * The epic's acceptance criterion, *"Saya mengubah identitas usaha"* — the identity the message
 * quotes must name something. An empty string is worse than the default, because it looks
 * deliberate: the owner would read their own blank field in a message they sent.
 */
class BlankIdentityNameException(val attempted: String) :
    IllegalStateException("the business name must not be blank, but was '$attempted'")
