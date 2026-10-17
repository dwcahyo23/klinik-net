package net.klinik.rental.data.repository

import java.time.LocalDate

/**
 * One deposit, projected for the correction form.
 *
 * ## Why this exists rather than handing the UI a `PaymentEntity`
 *
 * Every sibling repository returns domain values — `Renter`, `Contract`,
 * `BusinessAccount` — so returning a Room entity here would be the one place the UI module
 * reads `data.db.entity`, and the sheet would then own the *spelling* of a persistence
 * column: `PaymentEntity.method` is `String`, and parsing it in the UI is how a renamed
 * column silently becomes a wrong payment method.
 *
 * So the projection carries [method] as the enum the row's string decodes to, and carries
 * **no `status` and no `cancelReason`**: those are the cancellation story's columns
 * (`CancelDepositDialog` reads the reason, nothing here does), and a projection that
 * carried them would invite the correction form to grow a cancellation feature.
 *
 * ## Why the relations are resolved here rather than by the caller
 *
 * Because a correction form that opens with a blank plate or a blank rate is a form the
 * owner cannot check what they are correcting. Every relation here is resolved in one place,
 * so [PaymentRepository.findDeposit] either returns a complete value or returns nothing —
 * there is no shape in which the sheet receives half a deposit.
 *
 * @property dailyRate the contract's frozen rate, **raw**.
 *
 *   Unformatted on purpose: `formatRupiah` lives in `ui.format`, and the epic context
 *   pins the dependency direction as domain → data → ui, so a data-layer value that
 *   formatted a rupiah figure would point the wrong way. `SetoranViewModel`'s own options
 *   flow formats its rate for the same reason, and this is the same figure reaching the
 *   same formatter by the same path.
 * @property method the decoded [PaymentMethod], so the UI never parses the column's
 *   spelling. See the class KDoc.
 * @property accountId the account a transfer arrived through, or `null` for cash — which
 *   is why [needsAccount] and this field are read together rather than alone.
 */
data class EditableDeposit(
    val id: String,
    val contractId: String,
    val renterId: String,
    val renterName: String,
    val vehiclePlate: String,
    val dailyRate: Long,
    val date: LocalDate,
    val amount: Long,
    val method: PaymentMethod,
    val accountId: String?,
    val referenceNo: String?,
    val notes: String?,
) {
    /**
     * Whether a business account is required.
     *
     * Derived from [method] rather than from `accountId == null`, because those two are not
     * the same question: a transfer whose account was cleared is a broken row, and cash's
     * null account is the design. The epic context is explicit that a cash deposit has *no
     * external source to verify it*, so the distinction is the point.
     */
    val needsAccount: Boolean get() = method == PaymentMethod.TransferBank
}
