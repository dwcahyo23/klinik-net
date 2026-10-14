package net.klinik.rental.domain.validation

/**
 * Thrown when a vehicle is deleted while it still has an open contract.
 *
 * ## Why this exists even though no delete button ships yet
 *
 * The story's AC asks that deleting a rented vehicle be *refused*. It does not
 * ask for a delete control, and this story deliberately ships none — see the
 * design note: the mandatory deletion reason has nowhere to go, and a dialog
 * that collects one and discards it is the prototype's own defect. The
 * guarantee still has to hold at the data layer, or it holds nowhere at all:
 * `CONTRACT.vehicleId` carries `onDelete = ForeignKey.RESTRICT`, SQLite refuses,
 * and this is the typed form that refusal takes.
 *
 * So `DELETE_VEHICLE` is a real, asserted behaviour with no UI attached.
 *
 * ## Why the plate is carried
 *
 * A refusal the owner cannot act on is not a message. Naming the vehicle means
 * the Indonesian copy can say which one is blocked and what to do about it
 * ("B 1235 TF masih tersewa — akhiri sewanya dulu"), instead of a bare
 * "tidak bisa dihapus" they have to work out the referent of themselves.
 *
 * @param plate the blocking vehicle's plate, normalised — the same string the
 *   owner sees in the list, so the message names a row that exists on screen.
 */
class VehicleHasActiveContractException(
    val plate: String,
) : IllegalStateException(
    "vehicle $plate still has an open contract and cannot be deleted",
)