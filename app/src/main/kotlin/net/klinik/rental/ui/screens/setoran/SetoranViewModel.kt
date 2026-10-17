package net.klinik.rental.ui.screens.setoran

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.klinik.rental.data.repository.BusinessAccountRepository
import net.klinik.rental.data.repository.ContractRepository
import net.klinik.rental.data.repository.PaymentRepository
import net.klinik.rental.data.repository.RenterRepository
import net.klinik.rental.data.repository.VehicleRepository
import net.klinik.rental.domain.model.Payment
import net.klinik.rental.ui.format.formatRupiah
import net.klinik.rental.ui.strings.RStrings
import java.time.Clock
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The deposit sheet: who paid, how much, and how it arrived.
 *
 * ## Why this ViewModel exists separately from the contract screen's
 *
 * The sheet has no destination of its own — it is mounted by the shell's FAB over
 * whichever screen the owner was on — so it needs no route and no entry in `Screens`. What
 * it *does* need is three repositories the contract ViewModel does not hold (renters,
 * vehicles, accounts) plus the payment one, and a lifetime that outlives the rotation of
 * whichever screen is behind it. A ViewModel is the cheapest thing that survives a
 * configuration change.
 *
 * ## Why [today] is a [Clock] and not `LocalDate.now()`
 *
 * Because a deposit is dated relative to the billing period, and a period depends on
 * "today". Pinning it to the system clock makes the allocation untestable — a test that
 * passes in October fails in November. The epic context requires the single `Clock`
 * abstraction, and this is the first caller that has one.
 */
class SetoranViewModel(
    private val payments: PaymentRepository,
    private val renterRepository: RenterRepository,
    private val vehicleRepository: VehicleRepository,
    private val accountRepository: BusinessAccountRepository,
    private val contractRepository: ContractRepository,
    private val strings: RStrings,
    private val clock: Clock,
) : ViewModel() {

    /** The open form, or `null` when the sheet is closed. */
    private val form = MutableStateFlow<DepositFormState?>(null)

    /** The last banner-level refusal. A field refusal lands on its field instead. */
    private val errorMessage = MutableStateFlow<String?>(null)

    private val message = MutableStateFlow<String?>(null)

    /**
     * Whether a write is in flight, so a double tap cannot record the same deposit twice.
     *
     * An `AtomicBoolean`, not a rendered flag, for the reason its three siblings use one:
     * this is not drawn, so putting it in the state would recompose the sheet on a value
     * nothing reads.
     */
    private val saving = AtomicBoolean(false)

    /**
     * The pickers' options, read once and shared.
     *
     * Every renter is listed whether or not they have an open contract — the owner needs
     * to see *who* has none, and a filtered list makes a renter look deleted.
     */
    private data class Options(
        val renters: List<RenterOption>,
        val accounts: List<AccountOption>,
    )

    /** The options the pickers read. Shared, so one subscription feeds both. */
    private val options: StateFlow<Options> = combine(
        renterRepository.observeRenters(),
        contractRepository.observeContracts(),
        combine(
            vehicleRepository.observeVehicles(),
            accountRepository.observeAccounts(),
        ) { vehicles, accounts -> vehicles.associate { it.id to it.plate } to accounts },
    ) { renters, contracts, (plates, accounts) ->
        // The contract a renter pays into is the one **currently running** — `isOpen`,
        // which is `endDate == null`. A finished contract's deposits are history, and a
        // deposit dated today belongs to the rental that is still going.
        val openByRenter = contracts
            .filter { it.isOpen }
            .associateBy { it.renterId }
        Options(
            renters = renters.map { renter ->
                val contract = openByRenter[renter.id]
                RenterOption(
                    renter = renter,
                    contract = contract?.let {
                        DepositContract(
                            contractId = it.id,
                            vehiclePlate = plates[it.vehicleId] ?: it.vehicleId,
                            // From the **contract**, never the vehicle: the rate was
                            // frozen when the rental opened, and reading the vehicle
                            // would reprice a running contract the moment the fleet
                            // list changed.
                            rateText = formatRupiah(it.dailyRate),
                        )
                    },
                )
            },
            accounts = accounts.map { account ->
                // `bank · number`, because two accounts at one bank are legitimate and
                // the owner must be able to tell them apart — which is why the number is
                // in the label rather than only the bank.
                AccountOption(
                    account = account,
                    label = "${account.bankName} · ${account.number}",
                )
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = Options(emptyList(), emptyList()),
    )

    /**
     * The dialog's state, held separately from the form.
     *
     * A separate flow rather than a field on [form] so that *closing the form* and
     * *dismissing the dialog* are different acts. A preview stored on the form would be
     * discarded by code that only meant to close the sheet, and the owner would lose the
     * dialog they were reading.
     *
     * Named `previewState` so it cannot be confused with the repository's
     * `DepositPreview` or with this class's `previewDeposit` — three things in one class
     * whose whole job is to agree with each other.
     */
    private val previewState = MutableStateFlow<DepositPreviewUi?>(null)

    /**
     * The cancellation dialog's state — Story 3.4's.
     *
     * A separate flow from [form] and [previewState] so that three windows have three
     * lifetimes. They are genuinely different decisions: the form is the deposit being
     * composed, the preview is a question about it, and the cancellation is a question
     * about something already stored. Collapsing them would mean closing the sheet also
     * dismissed a dialog, and a reason typed for one would survive into another.
     */
    private val cancelState = MutableStateFlow<CancelDepositUi?>(null)


    private data class OptionsSnapshot(
        val options: Options,
        val message: String?,
    )

    /** The screen state the sheet reads. */
    val uiState: StateFlow<SetoranUiState> = combine(
        form,
        combine(errorMessage, previewState, message) { error, currentPreview, currentMessage ->
            SheetSnapshot(error, currentPreview, currentMessage)
        },
        cancelState,
        combine(options, message) { currentOptions, currentMessage ->
            OptionsSnapshot(currentOptions, currentMessage)
        },
    ) { currentForm, sheetSnapshot, currentCancel, optionsSnapshot ->
        SetoranUiState(
            form = currentForm,
            errorMessage = sheetSnapshot.error,
            renterOptions = optionsSnapshot.options.renters,
            accountOptions = optionsSnapshot.options.accounts,
            successMessage = sheetSnapshot.message,
            preview = sheetSnapshot.preview,
            cancel = currentCancel,
        )
    }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SetoranUiState(isLoading = true),
        )

    /** The transient confirmation, for the sheet's own banner. */
    val snackbarMessage: StateFlow<String?> = message

    private data class SheetSnapshot(
        val error: String?,
        val preview: DepositPreviewUi?,
        val message: String?,
    )

    // ------------------------------------------------------------------- form

    /**
     * Opens the sheet on a **blank** form — including a blank amount.
     *
     * The empty amount is the story's whole point rather than an omission: the owner is
     * required to type the figure, because typing it from zero is what catches a mistype.
     * The rate is filled for them precisely so they *cannot* be reading it off the screen
     * and re-keying it.
     *
     * The date is today rather than a blank field: a deposit arrives today unless the owner
     * is correcting, and correction is 3.5's subject.
     */
    fun openDeposit() {
        errorMessage.value = null
        message.value = null
        form.value = DepositFormState()
    }

    fun onFormChange(transform: (DepositFormState) -> DepositFormState) {
        form.value = form.value?.let(transform)
        // Any edit invalidates a preview already on screen. Not tidiness: the figures
        // would then describe a deposit the owner is no longer composing, and the AC's
        // "pratinjau wajib" would be a figure about the wrong transaction.
        previewState.value = null
    }

    // ------------------------------------------------------------- the preview

    /**
     * Computes the mandatory preview for the deposit currently on the pad.
     *
     * Called by `KnNumPad`'s Save **instead of** writing, which is what makes the
     * preview unskippable: the pad has no other exit, and the dialog is the only way
     * forward.
     *
     * The completeness refusals are the same ones `saveDepositAmount` applies, and they
     * stay here rather than in the write — because reaching this point with an incomplete
     * form means the pad offered a Save the sheet could not honour, and the message has
     * to land on the field the owner still has to fix.
     */
    fun previewDeposit(amount: Long) {
        if (!saving.compareAndSet(false, true)) return

        val current = form.value
        if (current == null) {
            saving.set(false)
            return
        }
        val contractId = current.contractId
        if (contractId == null) {
            errorMessage.value = strings.depositoErrorNoContract()
            saving.set(false)
            return
        }
        if (current.showsAccountField && current.accountId == null) {
            form.value = current.copy(accountError = strings.depositoErrorAccountRequired())
            saving.set(false)
            return
        }

        val today = LocalDate.now(clock)
        viewModelScope.launch {
            try {
                val result = payments.previewDeposit(
                    contractId = contractId,
                    payment = Payment(
                        // A throwaway id: the preview allocates against it and writes
                        // nothing, and the real id is minted at confirm time. Minting
                        // one here would mean the id in the dialog and the id stored
                        // could differ.
                        id = PREVIEW_ID,
                        date = today,
                        amount = amount,
                    ),
                    asOf = today,
                )
                errorMessage.value = null
                previewState.value = result.toUi()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Said plainly, and **no save is offered**: the epic context is explicit
                // that a preview showing the wrong thing is worse than no preview.
                previewState.value = null
                errorMessage.value = strings.depositoErrorPreviewFailed()
            } finally {
                saving.set(false)
            }
        }
    }

    /**
     * The dialog's dismiss — "Cek dulu", not "Batal".
     *
     * Discards the preview and **saves nothing**, which is the half that makes the
     * preview mandatory rather than advisory: a dismiss that saved would be a
     * confirmation the owner could wave through, and a preview that can be waved
     * through has taught them nothing.
     *
     * The form is deliberately left open with its amount: nothing was lost by looking.
     */
    fun dismissPreview() {
        previewState.value = null
    }

    /**
     * Writes the deposit the preview described.
     *
     * ## Why it refuses when no preview is up
     *
     * Because this is where the mandatory rule becomes structural rather than a comment.
     * The button is wired to the dialog's confirm, so a caller that skipped
     * [previewDeposit] gets a refusal instead of a silent write — and a future screen
     * that mounts a Save straight to this method cannot bypass the preview by wiring
     * around the sheet.
     */
    fun confirmDeposit() {
        val current = form.value
        if (current == null || previewState.value == null) return

        // The amount comes from the form, not from the preview: the preview carries no
        // amount, and re-reading the digits here is what would let a figure typed
        // between preview and confirm go in unshown.
        val amount = current.amountDigits.toLongOrNull()
        if (amount == null || amount <= 0L) {
            previewState.value = null
            form.value = current.copy(amountError = strings.depositoErrorAmountZero())
            return
        }
        saveDepositAmount(amount)
    }
    /**
     * Records the deposit with the amount `KnNumPad`'s Save handed over.
     *
     * A `Long` parameter rather than reading `amountDigits` here, because the pad has
     * **already refused zero** by the time this is called: its refusal is a red flash,
     * an *event* that changes no state and cannot be derived from the digits. Taking
     * the figure from the pad means one keystroke produces one answer — a version that
     * re-parsed the digits would have to answer the same key twice, once as a flash and
     * once as a refusal.
     *
     * So the amount is not re-checked here. `Payment.init` still refuses a non-positive
     * figure at the data layer, which is where the guarantee belongs; a caller that
     * reached this with `0` gets the domain refusal rather than a silent write.
     */
    fun saveDepositAmount(amount: Long) {
        if (!saving.compareAndSet(false, true)) return

        val current = form.value
        if (current == null) {
            saving.set(false)
            return
        }
        val contractId = current.contractId
        if (contractId == null) {
            // No contract, so nothing to bill. Not a field refusal: there is no control
            // the owner can change, because the renter has no running rental — which is
            // why the sheet says so in place of two blank rows.
            errorMessage.value = strings.depositoErrorNoContract()
            saving.set(false)
            return
        }
        if (current.showsAccountField && current.accountId == null) {
            form.value = current.copy(accountError = strings.depositoErrorAccountRequired())
            saving.set(false)
            return
        }

        val today = LocalDate.now(clock)
        val accountId = if (current.showsAccountField) current.accountId else null

        viewModelScope.launch {
            try {
                payments.saveDeposit(
                    contractId = contractId,
                    payment = Payment(
                        id = payments.newPaymentId(),
                        date = today,
                        amount = amount,
                    ),
                    method = current.method,
                    accountId = accountId,
                    // A cash deposit has no bank reference; a transfer's is the one
                    // field that makes it checkable against a mutation later.
                    referenceNo = current.referenceNo.trim().ifBlank { null },
                    // Kept on a transfer too, because an owner may want to note
                    // something — but on cash it is the **only** breadcrumb there is.
                    notes = current.notes.trim().ifBlank { null },
                    asOf = today,
                )
                // Conditional, for the reason the other screens' closes are: the write is
                // a suspension point and the owner keeps interacting across it.
                val live = form.value
                if (live != null && live === current) form.value = null
                // The dialog goes with the sheet: leaving it up over a closed form would
                // be a confirm button whose write has nowhere to go.
                previewState.value = null
                errorMessage.value = null
                message.value = strings.depositoSaved(formatRupiah(amount))
            } catch (e: net.klinik.rental.data.repository.UnknownContractException) {
                form.value = form.value?.copy(
                    contractId = null,
                    vehiclePlate = "",
                    rateText = "",
                )
                errorMessage.value = strings.depositoErrorNoContract()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Reported, never swallowed: a deposit the owner took and believes
                // they recorded is the worst outcome this app has.
                errorMessage.value = strings.depositoErrorUnexpected()
            } finally {
                saving.set(false)
            }
        }
    }

    /** A closed form takes every dialog with it. See the close in [saveDepositAmount]. */
    fun closeDeposit() {
        errorMessage.value = null
        previewState.value = null
        cancelState.value = null
        form.value = null
    }

    fun consumeMessage() {
        message.value = null
    }

    fun consumeError() {
        errorMessage.value = null
    }

    // --------------------------------------------------------- cancelling one

    /**
     * Opens the cancellation dialog for [paymentId], with its impact already computed.
     *
     * ## Why the figure is computed *before* the dialog opens
     *
     * Because a dialog that opens with a blank line and fills it in a moment later is
     * one the owner can read past. And the figure is `arrearsAfterCanceling` — allocated
     * **without** the deposit rather than the current arrears minus its amount — so it is
     * exactly what the write will produce. That is the property Story 3.3 exists to
     * protect, and `CancelDepositTest` proves it at the data layer with a case where the
     * two answers differ by a day.
     */
    fun requestCancel(paymentId: String) {
        errorMessage.value = null
        val contractId = form.value?.contractId ?: return
        val renterName = form.value?.renterName.orEmpty()
        viewModelScope.launch {
            try {
                val arrearsAfter = payments.arrearsAfterCanceling(
                    contractId = contractId,
                    paymentId = paymentId,
                    asOf = LocalDate.now(clock),
                )
                val amount = payments.paymentsFor(contractId)
                    .firstOrNull { it.id == paymentId }
                    ?.amount
                    ?: 0L
                val amountLabel = formatRupiah(amount)
                cancelState.value = CancelDepositUi(
                    paymentId = paymentId,
                    contractId = contractId,
                    amountLabel = amountLabel,
                    title = strings.depositoCancelTitle(amountLabel),
                    // The renter's own name, from the form the sheet already holds: a
                    // cancellation is about *this person's* arrears going back up.
                    impactLine = strings.depositoCancelImpact(
                        renterName = renterName,
                        arrearsAfter = formatRupiah(arrearsAfter),
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Said plainly rather than opening a dialog that promises a figure this
                // screen cannot produce — a warning the owner cannot check is worse than
                // no warning.
                errorMessage.value = strings.depositoErrorPreviewFailed()
            }
        }
    }

    /**
     * Carries a reason edit to the dialog.
     *
     * Its own handler rather than folded into [onFormChange], because the reason belongs
     * to the *cancellation* and not to the deposit: a form carrying both would let an
     * edit to one clear the other's error, and the two are never written together.
     */
    fun onCancelChange(transform: (CancelDepositUi) -> CancelDepositUi) {
        cancelState.value = cancelState.value?.let(transform)
    }

    /**
     * The dialog's dismiss.
     *
     * Discards the reason rather than keeping it: the deposit is untouched, and a reason
     * held for a cancellation the owner walked away from would reappear on the next
     * attempt with no explanation of where it came from.
     */
    fun dismissCancel() {
        cancelState.value = null
    }

    /**
     * Cancels the deposit the dialog names.
     *
     * ## Why the blank reason is refused here *and* in the repository
     *
     * Because the two refusals answer different audiences. The UI's is for the owner, who
     * needs the field to say why; the repository's is the rule, and it is the one a caller
     * bypassing the sheet would meet. A check in only one of the two leaves either a
     * control that does nothing or a hole in the data layer.
     */
    fun cancelDeposit() {
        if (!saving.compareAndSet(false, true)) return

        val current = cancelState.value
        if (current == null) {
            saving.set(false)
            return
        }
        if (current.reason.isBlank()) {
            cancelState.value = current.copy(
                reasonError = strings.depositoErrorCancelReasonRequired(),
            )
            saving.set(false)
            return
        }

        viewModelScope.launch {
            try {
                payments.cancelDeposit(
                    contractId = current.contractId,
                    paymentId = current.paymentId,
                    reason = current.reason,
                )
                // The dialog goes, and so does any preview. The **sheet stays**, which is
                // the one difference from `confirmDeposit`: cancelling undoes one deposit
                // rather than submitting the form, and the owner who is recording a second
                // payment this month should not lose the renter and amount they picked for
                // it. Dismissing the whole sheet on a *cancel* would discard typing nobody
                // asked to discard.
                cancelState.value = null
                previewState.value = null
                errorMessage.value = null
                message.value = strings.depositoCanceled(
                    // The figure the dialog named, carried on the state rather than
                    // parsed back out of the title — see `CancelDepositUi.amountLabel`.
                    amount = current.amountLabel,
                )
            } catch (e: net.klinik.rental.data.repository.BlankCancelReasonException) {
                cancelState.value = cancelState.value?.copy(
                    reasonError = strings.depositoErrorCancelReasonRequired(),
                )
            } catch (e: net.klinik.rental.data.repository.DepositAlreadyCanceledException) {
                cancelState.value = null
                errorMessage.value = strings.depositoErrorAlreadyCanceled()
            } catch (e: net.klinik.rental.data.repository.UnknownDepositException) {
                cancelState.value = null
                errorMessage.value = strings.depositoErrorPreviewFailed()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Reported, never swallowed. A cancellation the owner believes happened
                // when it did not leaves arrears understated — the failure that costs them
                // real money.
                errorMessage.value = strings.depositoErrorUnexpected()
            } finally {
                saving.set(false)
            }
        }
    }

    /**
     * The repository's figures, rendered into UX-DR10's exact sentences.
     *
     * ## Why the credit line is `null` rather than `Kredit: Rp 0`
     *
     * A line that always appears stops being read. The epic context says credit must never
     * appear as a *mystery number*, and a zero credit is not a mystery — it is nothing
     * happening, shown in the position where something would have been.
     *
     * ## Why the two "closes" shapes rather than one with a fragment
     *
     * The epic's format has two different sentences, and bolting an optional remainder
     * onto the shorter one leaves a dangling separator when nothing remains to say.
     */
    private fun net.klinik.rental.data.repository.DepositPreview.toUi() = DepositPreviewUi(
        arrearsLine = strings.depositoPreviewArrears(
            before = formatRupiah(arrearsBefore),
            after = formatRupiah(arrearsAfter),
        ),
        closesLine = if (daysRemaining > 0) {
            strings.depositoPreviewCloses(
                daysClosed = daysClosed,
                daysRemaining = daysRemaining,
                remainingAmount = formatRupiah(arrearsAfter),
            )
        } else {
            strings.depositoPreviewClosesOnly(daysClosed)
        },
        creditLine = if (hasCredit) {
            strings.depositoPreviewCredit(formatRupiah(credit))
        } else {
            null
        },
    )

    private companion object {
        /**
         * The id the preview allocates against, and never stores.
         *
         * A placeholder rather than a real id because the preview writes nothing, and
         * minting a real one here would leave the id in the dialog and the id stored able
         * to differ — a preview that described a different transaction than the one saved.
         */
        const val PREVIEW_ID = "preview"
    }
}

