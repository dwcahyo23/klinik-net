package net.klinik.rental.domain.validation

/**
 * Thrown when an exception is added to, or removed from, a contract that has ended.
 *
 * ## Why one class for both `add` and `remove`
 *
 * They are the same fact — *this contract has ended* — so two classes would let one
 * of the two paths forget to check, and a forgotten check on the remove path is
 * invisible: the write would succeed, the row would disappear, and an ended
 * contract's record of what was not billed would quietly lose a day. One class and
 * two call sites is the smaller surface.
 *
 * This is the data-layer half of the epic's FR-9 rule that a finished contract
 * cannot be edited. The UI half is the sheet unmounting its add block and its
 * remove controls entirely — see `ExceptionFormSheet` — and the two halves are
 * separate on purpose: a screen check is a courtesy, this is the rule.
 *
 * ## Why the contract id is carried
 *
 * [ContractAlreadyActiveException], [DuplicatePlateException] and
 * [VehicleHasActiveContractException] all carry the value the owner needs to fix
 * the refusal, and this one is the id because the *message* is about a contract the
 * owner did not choose to open — they tapped a row, and the row is one rental among
 * many. Carrying it means a caller that wants to name the contract can, without
 * having to reconstruct which one the sheet belonged to.
 *
 * @param contractId the ended contract, read back from the `contract` row.
 */
class ContractNotOpenException(
    val contractId: String,
) : IllegalStateException(
    "contract $contractId has ended and its service exceptions are read-only",
)
