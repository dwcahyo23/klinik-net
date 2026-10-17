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

    /** The screen state the sheet reads. */
    val uiState: StateFlow<SetoranUiState> = combine(
        form,
        errorMessage,
        combine(options, message) { currentOptions, currentMessage ->
            OptionsSnapshot(currentOptions, currentMessage)
        },
    ) { currentForm, currentError, snapshot ->
        SetoranUiState(
            form = currentForm,
            errorMessage = currentError,
            renterOptions = snapshot.options.renters,
            accountOptions = snapshot.options.accounts,
            successMessage = snapshot.message,
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

    private data class OptionsSnapshot(
        val options: Options,
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

    fun closeDeposit() {
        errorMessage.value = null
        form.value = null
    }

    fun onFormChange(transform: (DepositFormState) -> DepositFormState) {
        form.value = form.value?.let(transform)
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

    fun consumeMessage() {
        message.value = null
    }

    fun consumeError() {
        errorMessage.value = null
    }
}
