package net.klinik.rental.domain.validation

/**
 * Thrown when a vehicle or a renter already has an open contract.
 *
 * ## Why this is a distinct type rather than a generic "conflict"
 *
 * One active contract per vehicle **and** per renter is a real rule with two
 * independent sides, and the owner has to be told *which* side is blocked: "that
 * car is already out" and "that person is already renting something" need
 * different actions from the owner. A single exception type carrying a string
 * would collapse the two, and the copy would then have to guess.
 *
 * The guarantee itself is not this exception and not a pre-check — it is the pair
 * of `UNIQUE` indexes on the contract's active columns. Two callers can both pass
 * the pre-check before either writes, so only the index settles the race. This is
 * the named form of the index's refusal, the same way [DuplicatePlateException] is
 * the named form of the plate index's.
 *
 * ## Why the blocking name is carried
 *
 * The same rule [DuplicatePlateException] and
 * [VehicleHasActiveContractException] follow: a refusal the owner cannot act on is
 * not a message. Naming the plate or the person means the Indonesian copy can say
 * *which* car or *which* renter is holding the slot, and point at a row that
 * exists in the list behind the sheet, rather than reporting a constraint name or
 * a bare "already active".
 *
 * @param side which half of the one-active rule was refused.
 * @param blockingName the plate or the renter name that actually holds the slot —
 *   read back from the database rather than echoed from the input, so the message
 *   names something that exists.
 */
class ContractAlreadyActiveException(
    val side: Side,
    val blockingName: String,
) : IllegalStateException(
    "an open contract already exists for the ${side.label} $blockingName",
) {

    /**
     * The two halves of the rule, each with its own wording at the UI layer.
     *
     * An enum rather than a `Boolean isVehicle`, because a boolean at the call
     * site reads as `ContractAlreadyActiveException(true, "B 1235 TF")` — and the
     * two sides produce two different sentences, so a swapped argument would show
     * the owner a message about the wrong entity.
     */
    enum class Side(val label: String) {
        /** The car is already out. */
        Vehicle("vehicle"),

        /** The person is already renting. */
        Renter("renter"),
    }
}
