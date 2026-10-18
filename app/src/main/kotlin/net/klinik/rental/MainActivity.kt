package net.klinik.rental

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.klinik.rental.data.db.KlinikDatabase
import net.klinik.rental.data.repository.BusinessAccountRepository
import net.klinik.rental.data.repository.BusinessIdentityRepository
import net.klinik.rental.data.repository.ContractRepository
import net.klinik.rental.data.repository.PaymentRepository
import net.klinik.rental.data.repository.RenterRepository
import net.klinik.rental.data.repository.VehicleRepository
import net.klinik.rental.ui.screens.kendaraan.KendaraanViewModel
import net.klinik.rental.ui.screens.kontrak.KontrakViewModel
import net.klinik.rental.ui.screens.pengaturan.PengaturanViewModel
import net.klinik.rental.ui.screens.setoran.SetoranViewModel
import net.klinik.rental.ui.screens.penyewa.PenyewaViewModel
import net.klinik.rental.ui.screens.tunggakan.TunggakanViewModel
import net.klinik.rental.ui.shell.AppShell
import net.klinik.rental.ui.shell.Screens
import net.klinik.rental.ui.strings.RStrings
import net.klinik.rental.ui.theme.KlinikNetTheme
import net.klinik.rental.ui.theme.knColors
import java.time.Clock
import java.time.LocalDate

/**
 * The single Activity for the app.
 *
 * `LocalDate.now()` is called here deliberately rather than somewhere in the
 * domain layer: on API 24/25 `java.time` only exists because of core library
 * desugaring. If that config is ever lost, this line is where it crashes —
 * visibly, at startup — instead of failing silently later.
 */
class MainActivity : ComponentActivity() {

    /**
     * The three ViewModels, one per destination.
     *
     * Constructed by hand rather than by a DI container because there is no
     * container yet: one Activity, one shell, three repositories. `by viewModels()`
     * scopes each to the Activity, so a rotation keeps the query, the filter and
     * any half-typed form input — which is why the form state is held in the
     * ViewModel rather than in a `remember` inside the sheet. That is what makes
     * `ROTATION_WITH_HALF_FORM` hold.
     *
     * The factories are lambdas so `KlinikDatabase.get` is not called until a
     * ViewModel is actually constructed; `by viewModels` may evaluate its factory
     * argument during `onCreate`, and the database touches disk.
     *
     * Activity-scoped rather than destination-scoped on purpose: a ViewModel
     * scoped to a nav back-stack entry would be destroyed when the owner
     * navigates away, taking a refusal message with it. `BANNER_SCOPED_TO_RAISER`
     * is the acknowledged limit of that choice — the message survives the
     * destination change but stays attached to its own screen's ViewModel.
     */
    private val kendaraanViewModel: KendaraanViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                createKendaraanViewModel(applicationContext) as T
        }
    }

    private val penyewaViewModel: PenyewaViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                createPenyewaViewModel(applicationContext) as T
        }
    }

    private val kontrakViewModel: KontrakViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                createKontrakViewModel(applicationContext) as T
        }
    }

    private val pengaturanViewModel: PengaturanViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                createPengaturanViewModel(applicationContext) as T
        }
    }

    private val setoranViewModel: SetoranViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                createSetoranViewModel(applicationContext) as T
        }
    }

    /**
     * The arrears list's ViewModel — Story 4.1.
     *
     * A fifth instance rather than folded into `KontrakViewModel`: the list is its own
     * destination, and a ViewModel per screen is what keeps each one's `combine` from
     * growing a source the others have to carry.
     */
    private val tunggakanViewModel: TunggakanViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                createTunggakanViewModel(applicationContext) as T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // The desugaring tripwire — see the class KDoc. Bound to a name rather than
        // called and discarded so the call cannot be "cleaned up" as dead code: the
        // crash it produces on a build that lost core-library desugaring is the
        // whole point of the line.
        @Suppress("UNUSED_VARIABLE")
        val today = LocalDate.now()

        setContent {
            KlinikNetTheme(darkTheme = isSystemInDarkTheme()) {
                KlinikNetApp(
                    kendaraan = kendaraanViewModel,
                    penyewa = penyewaViewModel,
                    kontrak = kontrakViewModel,
                    pengaturan = pengaturanViewModel,
                    setoran = setoranViewModel,
                    tunggakan = tunggakanViewModel,
                )
            }
        }
    }
}

/**
 * Builds the ViewModel over the database singleton.
 *
 * Top-level rather than a companion so the tests can build the same object graph
 * against an in-memory database without going through an Activity.
 */
internal fun createKendaraanViewModel(context: Context): KendaraanViewModel {
    val database = KlinikDatabase.get(context)
    return KendaraanViewModel(
        repository = VehicleRepository(database.vehicleDao(), database),
        strings = RStrings(context),
    )
}

/** The renters' ViewModel over the same singleton. See [createKendaraanViewModel]. */
internal fun createPenyewaViewModel(context: Context): PenyewaViewModel {
    val database = KlinikDatabase.get(context)
    return PenyewaViewModel(
        // `RenterRepository` takes only its DAO — it has no multi-row write that
        // needs `withTransaction`, so it never needed the database handle. Adding
        // a nullable parameter here to match its siblings would be a parameter
        // nothing uses.
        repository = RenterRepository(database.renterDao()),
        strings = RStrings(context),
    )
}

/**
 * The contracts' ViewModel, which reads three repositories because a contract row
 * carries neither a name nor a plate — the list joins them in Kotlin, and the
 * form's pickers need the whole renter and vehicle set.
 */
internal fun createKontrakViewModel(context: Context): KontrakViewModel {
    val database = KlinikDatabase.get(context)
    val strings = RStrings(context)
    return KontrakViewModel(
        contracts = ContractRepository(
            database.contractDao(),
            // The exception DAO, in `ContractRepository` rather than in a repository
            // of its own — see that class's KDoc. `MainActivity` gains a line
            // because that class grew a second DAO, and gains nothing else: no
            // constructor parameter on the ViewModel, no second `Screens` field.
            database.serviceExceptionDao(),
            database,
        ),
        renters = RenterRepository(database.renterDao()),
        vehicles = VehicleRepository(database.vehicleDao(), database),
        // Story 3.6's confirmation figures. `ContractRepository` deliberately takes no
        // second repository — the settlement *write* is one contract column, while the
        // *figures* need the whole allocation chain, so the ViewModel composes the two
        // rather than either repository owning the other. That is why this block, not a
        // parameter inside the constructor above.
        payments = PaymentRepository(
            paymentDao = database.paymentDao(),
            allocationDao = database.allocationDao(),
            contractDao = database.contractDao(),
            exceptionDao = database.serviceExceptionDao(),
            settingDao = database.settingDao(),
            database = database,
        ),
        strings = strings,
        // The single clock, shared with `SetoranViewModel`: a settlement is dated
        // relative to the billing period, so a test that passed in October would fail
        // in November without one.
        clock = Clock.systemDefaultZone(),
    )
}

/**
 * The settings ViewModel over the same singleton.
 *
 * A repository of its own rather than an extension of an existing one, because a business
 * account has no relationship to a contract, a vehicle or a renter — see
 * `BusinessAccountRepository`'s KDoc. It passes the database handle because `save` reads
 * before it writes (insert-vs-update) and that decision needs one snapshot.
 */
internal fun createPengaturanViewModel(context: Context): PengaturanViewModel {
    val database = KlinikDatabase.get(context)
    return PengaturanViewModel(
        repository = BusinessAccountRepository(database.businessAccountDao(), database),
        // Story 4.4's identity. The database handle is passed because `save` writes three
        // keys as one block and a half-written identity is one the owner did not enter.
        identity = BusinessIdentityRepository(database.settingDao(), database),
        strings = RStrings(context),
    )
}

/**
 * The deposit ViewModel over the same singleton.
 *
 * Four repositories, and that count is worth noting: a deposit names a **contract** (whose
 * rate and period it settles), a **renter** (who the owner thinks in terms of), a
 * **vehicle** (for the plate on the confirmation) and a **business account** (where the
 * transfer landed). None of the three existing ViewModels holds all four, so this is a
 * fourth factory rather than a fourth constructor parameter somewhere else.
 *
 * @param clock the single `Clock` the epic context requires. A deposit's allocation is
 *   relative to a billing period, and the period depends on today — so pinning this to
 *   `LocalDate.now()` would make the whole screen untestable, since a test passing in
 *   October fails in November.
 */
internal fun createSetoranViewModel(context: Context): SetoranViewModel {
    val database = KlinikDatabase.get(context)
    return SetoranViewModel(
        payments = PaymentRepository(
            paymentDao = database.paymentDao(),
            allocationDao = database.allocationDao(),
            contractDao = database.contractDao(),
            exceptionDao = database.serviceExceptionDao(),
            settingDao = database.settingDao(),
            // Story 3.5's three, for `findDeposit`'s resolution of the renter, the vehicle
            // and the contract the correction form displays. Without them that read finds
            // no renter and refuses every correction — and because the parameters are
            // nullable, nothing fails at build time to say so.
            renterDao = database.renterDao(),
            vehicleDao = database.vehicleDao(),
            businessAccountDao = database.businessAccountDao(),
            database = database,
        ),
        renterRepository = RenterRepository(database.renterDao()),
        vehicleRepository = VehicleRepository(database.vehicleDao()),
        accountRepository = BusinessAccountRepository(database.businessAccountDao()),
        contractRepository = ContractRepository(
            database.contractDao(),
            database.serviceExceptionDao(),
            database,
        ),
        strings = RStrings(context),
        clock = Clock.systemDefaultZone(),
    )
}

/**
 * Builds the arrears list's ViewModel over the database singleton.
 *
 * ## Why it holds a `PaymentRepository` of its own
 *
 * Because the arrears **figures** live there and nowhere else — `PaymentRepository` owns the
 * whole allocation chain, and `ContractRepository` writes one column. Building a third one
 * over the same DAOs is deliberate and harmless: the repositories are stateless, so two
 * instances read the same rows. What would *not* be harmless is a second place computing
 * arrears, which is why this one reads `observeArrearsForAllContracts` rather than
 * recomputing a figure from deposits.
 *
 * ## Why the clock is passed rather than read here
 *
 * Because an arrears figure depends on which billing period "today" falls in. Pinning it to
 * `LocalDate.now()` would make the list untestable — a test that passes in October fails in
 * November — and the epic context requires the single `Clock` abstraction.
 */
internal fun createTunggakanViewModel(context: Context): TunggakanViewModel {
    val database = KlinikDatabase.get(context)
    return TunggakanViewModel(
        payments = PaymentRepository(
            paymentDao = database.paymentDao(),
            allocationDao = database.allocationDao(),
            contractDao = database.contractDao(),
            exceptionDao = database.serviceExceptionDao(),
            settingDao = database.settingDao(),
            database = database,
        ),
        renterRepository = RenterRepository(database.renterDao()),
        vehicleRepository = VehicleRepository(database.vehicleDao()),
        strings = RStrings(context),
        clock = Clock.systemDefaultZone(),
    )
}

/**
 * The app shell: one graph, a rail in landscape and a bottom nav in portrait.
 *
 * Stateless by design — every screen is driven from its own `UiState` and every
 * action goes back through a ViewModel, so a Robolectric test can drive the whole
 * shell with hand-built state and no database at all.
 *
 * The ViewModels are parameters rather than read from an enclosing Activity
 * because this is a top-level composable: a private composable *member* would
 * reach them, and coupling the shell to the Activity is exactly the dependency a
 * test then cannot supply.
 *
 * There is deliberately no `today` parameter and no clock in this function. It
 * used to take a `LocalDate` that nothing read — "today" belongs to the domain
 * `Clock`, and a UI-layer `LocalDate.now()` would be a second source of truth no
 * test could pin. The desugaring tripwire is the one place `LocalDate.now()` is
 * still called, and it stays in `onCreate` above: if that config is ever lost, it
 * should crash visibly at startup rather than quietly here.
 *
 * This function collects the four screens' flows and hands the results to
 * [AppShell] as one [Screens]. Collection lives here, at the Activity boundary,
 * rather than inside the shell — the shell must not hold a ViewModel, or a test
 * would have to construct four of them to render one nav bar.
 */
@Composable
private fun KlinikNetApp(
    kendaraan: KendaraanViewModel,
    penyewa: PenyewaViewModel,
    kontrak: KontrakViewModel,
    pengaturan: PengaturanViewModel,
    setoran: SetoranViewModel,
    tunggakan: TunggakanViewModel,
) {
    val kendaraanState by kendaraan.uiState.collectAsStateWithLifecycle()
    val kendaraanSnackbar by kendaraan.snackbarMessage.collectAsStateWithLifecycle()
    val penyewaState by penyewa.uiState.collectAsStateWithLifecycle()
    val penyewaSnackbar by penyewa.snackbarMessage.collectAsStateWithLifecycle()
    val kontrakState by kontrak.uiState.collectAsStateWithLifecycle()
    val kontrakSnackbar by kontrak.snackbarMessage.collectAsStateWithLifecycle()
    // Story 2.6's sheet has its **own** banner, because it is a separate window and
    // the screen's is behind it — see `Screens.Kontrak.exceptionErrorMessage`.
    val kontrakExceptionError by kontrak.exceptionErrorMessage.collectAsStateWithLifecycle()
    // And Story 2.7's, on the same argument: three sheets, three windows, three slots.
    val kontrakEndError by kontrak.endErrorMessage.collectAsStateWithLifecycle()
    // And Story 3.6's, the fourth window and the fourth slot.
    val kontrakSettleError by kontrak.settleErrorMessage.collectAsStateWithLifecycle()
    // And Story 4.1's, which is a whole screen rather than a sheet — so its banner lives on
    // the screen's own state and needs no separate slot here.
    val tunggakanState by tunggakan.uiState.collectAsStateWithLifecycle()
    val renterOptions by kontrak.renterOptions.collectAsStateWithLifecycle()
    val vehicleOptions by kontrak.vehicleOptions.collectAsStateWithLifecycle()
    // Story 3.1's screen. It has no second window of its own beyond its form sheet, so one
    // banner pair is enough — unlike the contracts screen's three sheets.
    val pengaturanState by pengaturan.uiState.collectAsStateWithLifecycle()
    val pengaturanSnackbar by pengaturan.snackbarMessage.collectAsStateWithLifecycle()
    val setoranState by setoran.uiState.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.knColors.bgPage,
        contentColor = MaterialTheme.knColors.textPrimary,
    ) {
        AppShell(
            screens = Screens(
                kendaraan = Screens.Kendaraan(
                    state = kendaraanState,
                    snackbarMessage = kendaraanSnackbar,
                    onQueryChange = kendaraan::onQueryChange,
                    onFilterChange = kendaraan::onFilterChange,
                    onClearQuery = kendaraan::clearQuery,
                    onAdd = kendaraan::openAddForm,
                    onEdit = kendaraan::openEditForm,
                    onCloseForm = kendaraan::closeForm,
                    onFormChange = kendaraan::onFormChange,
                    onSave = kendaraan::saveForm,
                    onSnackbarDismiss = kendaraan::consumeMessage,
                    onErrorDismiss = kendaraan::consumeError,
                ),
                penyewa = Screens.Penyewa(
                    state = penyewaState,
                    snackbarMessage = penyewaSnackbar,
                    onQueryChange = penyewa::onQueryChange,
                    onClearQuery = penyewa::clearQuery,
                    onAdd = penyewa::openAddForm,
                    onEdit = penyewa::openEditForm,
                    onCloseForm = penyewa::closeForm,
                    onFormChange = penyewa::onFormChange,
                    onSave = penyewa::saveForm,
                    onSnackbarDismiss = penyewa::consumeMessage,
                    onErrorDismiss = penyewa::consumeError,
                ),
                kontrak = Screens.Kontrak(
                    state = kontrakState,
                    snackbarMessage = kontrakSnackbar,
                    renterOptions = renterOptions,
                    vehicleOptions = vehicleOptions,
                    onOpen = kontrak::openAddForm,
                    onCloseForm = kontrak::closeForm,
                    onFormChange = kontrak::onFormChange,
                    onSave = kontrak::saveForm,
                    // Story 2.6's seven. `createKontrakViewModel` itself is unchanged
                    // by that story: `KontrakViewModel` already held
                    // `ContractRepository`, and extending that repository with the
                    // second DAO is what kept this wiring line the only place the
                    // feature touched. A second repository would have added a
                    // constructor parameter *and* a line here.
                    onOpenExceptions = kontrak::openExceptions,
                    onCloseExceptions = kontrak::closeExceptions,
                    onExceptionChange = kontrak::onExceptionChange,
                    onAddException = kontrak::addException,
                    onRequestRemove = kontrak::requestRemoveException,
                    onConfirmRemove = kontrak::confirmRemoveException,
                    onCancelRemove = kontrak::cancelRemoveException,
                    onSnackbarDismiss = kontrak::consumeMessage,
                    onErrorDismiss = kontrak::consumeError,
                    // The exception sheet's own banner pair, and its own handlers: it
                    // is a different window from the screen's banner, so sharing
                    // either setter would clear one message through the other's
                    // channel while it stayed on screen.
                    exceptionErrorMessage = kontrakExceptionError,
                    onExceptionErrorDismiss = kontrak::consumeExceptionError,
                    // Story 2.7's four, plus its own banner pair. `createKontrakViewModel`
                    // above is **unchanged** by that story: `KontrakViewModel` already
                    // held `ContractRepository`, and ending a contract is a method on it
                    // rather than a second repository. This block is the whole of 2.7's
                    // footprint here, which is the measure of how little it touched.
                    onOpenEnd = kontrak::openEndContract,
                    onCloseEnd = kontrak::closeEndContract,
                    onEndChange = kontrak::onEndChange,
                    onConfirmEnd = kontrak::endContract,
                    endErrorMessage = kontrakEndError,
                    onEndErrorDismiss = kontrak::consumeEndError,
                    // Story 3.6's six. Same reasoning, same window — the settlement
                    // sheet is a fourth window with its own banner pair, and none of
                    // these is defaulted so a missing wiring is a compile error rather
                    // than a control that renders and does nothing.
                    onOpenSettle = kontrak::openSettle,
                    onCloseSettle = kontrak::closeSettle,
                    onSettleChange = kontrak::onSettleChange,
                    onConfirmSettle = kontrak::settleContract,
                    settleErrorMessage = kontrakSettleError,
                    onSettleErrorDismiss = kontrak::consumeSettleError,
                ),
                pengaturan = Screens.Pengaturan(
                    state = pengaturanState,
                    snackbarMessage = pengaturanSnackbar,
                    onAdd = pengaturan::openAddForm,
                    onEdit = pengaturan::openEditForm,
                    onCloseForm = pengaturan::closeForm,
                    onFormChange = pengaturan::onFormChange,
                    onSave = pengaturan::saveForm,
                    // Story 4.4's identity sheet, beside the account one rather than inside
                    // it: two sheets over one state is the defect Story 2.8's review found as
                    // its only `high`.
                    onOpenIdentity = pengaturan::openIdentityForm,
                    onCloseIdentity = pengaturan::dismissIdentity,
                    onIdentityChange = pengaturan::onIdentityChange,
                    onSaveIdentity = pengaturan::saveIdentity,
                    onIdentityErrorDismiss = pengaturan::consumeIdentityError,
                    onSnackbarDismiss = pengaturan::consumeMessage,
                    onErrorDismiss = pengaturan::consumeError,
                ),
                // Story 3.2's deposit sheet. No route — the shell mounts it over
                // whichever destination is showing, because a deposit is a quick
                // action rather than a place the owner browses.
                deposito = Screens.Deposito(
                    state = setoranState,
                    onOpen = setoran::openDeposit,
                    onClose = setoran::closeDeposit,
                    onFormChange = setoran::onFormChange,
                    onSave = setoran::previewDeposit,
                    onConfirmPreview = setoran::confirmDeposit,
                    onDismissPreview = setoran::dismissPreview,
                    onSnackbarDismiss = setoran::consumeMessage,
                    onErrorDismiss = setoran::consumeError,
                ),
                // Story 4.1's arrears list. No route of its own beyond the destination —
                // this is the whole of its wiring, which is the smallest block in `Screens`
                // because the list has no sheets, one action, and a banner that rides on
                // its own state rather than in a second channel.
                tunggakan = Screens.Tunggakan(
                    state = tunggakanState,
                    onSelect = tunggakan::onSelect,
                    onErrorDismiss = tunggakan::consumeError,
                ),
            ),
        )
    }
}


