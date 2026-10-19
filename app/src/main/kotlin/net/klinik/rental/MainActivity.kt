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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import net.klinik.rental.ui.lock.LockScreen
import net.klinik.rental.ui.lock.PinLockViewModel
import net.klinik.rental.data.backup.BackupRepository
import net.klinik.rental.data.backup.BackupRestorer
import net.klinik.rental.data.db.KlinikDatabase
import net.klinik.rental.data.repository.BusinessAccountRepository
import net.klinik.rental.data.repository.PinLockRepository
import net.klinik.rental.data.repository.DepositHistoryRepository
import net.klinik.rental.data.repository.BusinessIdentityRepository
import net.klinik.rental.data.repository.ContractRepository
import net.klinik.rental.data.repository.PaymentRepository
import net.klinik.rental.data.repository.RenterRepository
import net.klinik.rental.data.repository.ThemeMode
import net.klinik.rental.data.repository.ThemeRepository
import net.klinik.rental.data.repository.VehicleRepository
import net.klinik.rental.ui.screens.beranda.BerandaViewModel
import net.klinik.rental.ui.screens.laporan.LaporanViewModel
import net.klinik.rental.ui.screens.kendaraan.KendaraanViewModel
import net.klinik.rental.ui.screens.kontrak.KontrakViewModel
import net.klinik.rental.ui.screens.pengaturan.PengaturanViewModel
import net.klinik.rental.ui.screens.setoran.SetoranViewModel
import net.klinik.rental.ui.screens.penyewa.PenyewaViewModel
import net.klinik.rental.ui.screens.tunggakan.TunggakanViewModel
import net.klinik.rental.ui.shell.AppShell
import net.klinik.rental.ui.shell.GlobalSearchViewModel
import net.klinik.rental.ui.shell.Screens
import net.klinik.rental.ui.strings.RStrings
import net.klinik.rental.ui.theme.KlinikNetTheme
import net.klinik.rental.ui.theme.LocalThemeControl
import net.klinik.rental.ui.theme.ThemeControl
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

    /**
     * The morning summary's ViewModel — Story 4.3.
     *
     * A sixth instance rather than folded into `TunggakanViewModel`: the summary reads the
     * same arrears the list does, but it is a different screen with a different lifetime,
     * and a ViewModel per screen is what keeps each one's `combine` from growing a source
     * the others have to carry.
     */
    private val berandaViewModel: BerandaViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                createBerandaViewModel(applicationContext) as T
        }
    }

    /**
     * The period report's ViewModel. Its own lifetime, for the reason the others have one:
     * the report re-reads at whatever period is selected, and a shared `combine` would carry
     * that as a source every other screen then has to hold.
     */
    private val laporanViewModel: LaporanViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                createLaporanViewModel(applicationContext) as T
        }
    }

    /**
     * Story 4.6's global search. Its own lifetime for the reason every other ViewModel here has
     * one: the query must survive a rotation, and a field that emptied itself on every
     * configuration change is a search the owner retypes each time they turn the tablet.
     */
    private val searchViewModel: GlobalSearchViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                createGlobalSearchViewModel(applicationContext) as T
        }
    }

/**
     * Whether the app is showing the lock rather than the shell.
     *
     * **`true` until the first read says otherwise.** The lock is on screen for the frame before
     * the database answers, which is the only safe default: a start that assumed "unlocked" and
     * corrected itself would flash the owner's records on every cold start with the PIN on. The
     * opposite mistake — a frame of lock screen for an owner with no PIN — is invisible, because
     * the shell replaces it as soon as the read lands.
     */
    private val locked = MutableStateFlow(true)

    /**
     * Whether this **process** has already come to the foreground once.
     *
     * The AC's cold-start clause: *"proses mati atau restart → terkunci lagi"*. `onStop` never ran
     * if the process was killed in the foreground, so there is no timestamp to compare — and the
     * case where the app cannot know what happened is the case where it must lock. Every `onStart`
     * after the first compares the persisted timestamp instead.
     */
    private var foregroundedOnce = false

    private val pinLock: PinLockRepository by lazy {
        PinLockRepository(
            dao = KlinikDatabase.get(applicationContext).settingDao(),
            clock = Clock.systemDefaultZone(),
            database = KlinikDatabase.get(applicationContext),
        )
    }

    /**
     * The theme choice, read and written through one repository.
     *
     * `lazy` for the same reason [pinLock] is: `onCreate` must not touch disk before `setContent`
     * needs the first value.
     */
    private val themeRepository: ThemeRepository by lazy {
        ThemeRepository(KlinikDatabase.get(applicationContext).settingDao())
    }

    /**
     * What the owner chose about the theme, collected once for the whole Activity.
     *
     * `System` until the first read lands, which is also the default an unwritten key resolves to —
     * so the frame before the database answers is already the right answer for an owner who never
     * chose, and one frame of the OS's theme for an owner who did.
     */
    private val themeMode = MutableStateFlow(ThemeMode.System)

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
            // The stored choice, followed live: writing it below is what repaints, so the button
            // needs no local state and no restart.
            LaunchedEffect(Unit) {
                themeRepository.observeMode().collect { themeMode.value = it }
            }
            val mode by themeMode.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val dark = mode.resolvesDark(systemDark)

            KlinikNetTheme(darkTheme = dark) {
                val isLocked by locked.collectAsStateWithLifecycle()

                // The button lives in `KnTopBar`, which eight screens draw. Providing it here
                // rather than passing it through `Screens` keeps those eight call sites untouched;
                // see `LocalThemeControl`.
                CompositionLocalProvider(
                    LocalThemeControl provides ThemeControl(
                        dark = dark,
                        onToggle = {
                            // An explicit choice, written once. `System` is never written back:
                            // the owner pressed a button that said what it would do, and a value
                            // that silently means "whatever the OS does next" is not that.
                            val next = if (dark) ThemeMode.Light else ThemeMode.Dark
                            // Optimistic, so the tap paints on the same frame; the flow above
                            // confirms it a moment later with the value actually stored.
                            themeMode.value = next
                            lifecycleScope.launch { themeRepository.setMode(next) }
                        },
                    ),
                ) {
                    // **Either** the lock **or** the shell — never both.
                    //
                    // An overlay would leave the destination composed underneath, so a screenshot, a
                    // screen reader traversal, or a rendering bug would expose the books behind a
                    // lock. Not composing the shell at all is the only version where "locked" means
                    // the content is not on screen.
                    if (isLocked) {
                        val lockViewModel: PinLockViewModel = viewModel(
                            factory = lockViewModelFactory(),
                        )
                        val lockState by lockViewModel.uiState.collectAsStateWithLifecycle()
                        LockScreen(
                            state = lockState,
                            onDigit = lockViewModel::onDigit,
                            onDelete = lockViewModel::onDelete,
                            onRecovery = lockViewModel::openRecovery,
                            onTick = lockViewModel::refreshLockout,
                            shakeKey = lockState.shakeKey,
                        )
                    } else {
                        KlinikNetApp(
                            kendaraan = kendaraanViewModel,
                            penyewa = penyewaViewModel,
                            kontrak = kontrakViewModel,
                            pengaturan = pengaturanViewModel,
                            setoran = setoranViewModel,
                            tunggakan = tunggakanViewModel,
                            beranda = berandaViewModel,
                            laporan = laporanViewModel,
                            search = searchViewModel,
                            // Story 5.1's export, built here from the same singleton the settings
                            // ViewModel reads — the route needs it for the picker's filename, and a
                            // repository rather than a ViewModel because it holds no state.
                            backups = createBackupRepository(
                                database = KlinikDatabase.get(applicationContext),
                                clock = Clock.systemDefaultZone(),
                            ),
                        )
                    }
                }
            }
        }
    }

    /**
     * The lock screen's ViewModel, whose `onUnlocked` is this Activity's.
     *
     * A factory rather than `by viewModels` because the callback closes over [locked], which is
     * created with the Activity — and the ViewModel must be created only when the lock is actually
     * shown, so an unlocked app never constructs one.
     */
    private fun lockViewModelFactory(): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PinLockViewModel(
                    lock = pinLock,
                    strings = RStrings(applicationContext),
                    onUnlocked = { locked.value = false },
                ) as T
        }

    /**
     * Decides whether to lock, on every return to the foreground.
     *
     * The first `onStart` of a process always locks when the PIN is on — see [foregroundedOnce].
     * Every later one compares the timestamp persisted at the previous `onStop`, which is the
     * AC's *"timestamp absolut … bukan counter yang reset"*.
     */
    override fun onStart() {
        super.onStart()
        val coldStart = !foregroundedOnce
        foregroundedOnce = true

        // The read is on the main thread's coroutine scope rather than a ViewModel's, because the
        // lock gate is the Activity's — a ViewModel scoped to a destination would be destroyed
        // when the owner navigates, taking the gate with it.
        lifecycleScope.launch {
            locked.value = pinLock.shouldLock(coldStart = coldStart)
        }
    }

    /** Records the instant the app left the foreground, for the next [onStart] to compare against. */
    override fun onStop() {
        super.onStop()
        // Only meaningful while the lock is on; recording it always would write a row for owners
        // who never enabled a PIN.
        lifecycleScope.launch {
            if (pinLock.isEnabled()) pinLock.recordBackgrounded()
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
        // The detail pane's two joins: who is renting the selected car, and on what terms.
        // Passed as repositories rather than as a second `@Query` with JOINs, so `renter` and
        // `contract` keep one reader each — the rule `KontrakViewModel` established.
        contracts = ContractRepository(
            database.contractDao(),
            database.serviceExceptionDao(),
            database,
        ),
        renters = RenterRepository(database.renterDao()),
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
        // The detail pane's join: which car this person is renting, and on what terms.
        contracts = ContractRepository(
            database.contractDao(),
            database.serviceExceptionDao(),
            database,
        ),
        vehicles = VehicleRepository(database.vehicleDao(), database),
    )
}

/**
 * Story 4.6's global search, over the two repositories it reads.
 *
 * Both are **existing** ones, and that is the design rather than a shortcut: the field searches
 * the same two lists the renters and fleet screens show, through Room's own invalidation flows.
 * A search-specific query would be a second place the matching rule lives — and a second answer
 * to "does this renter match", which is exactly what `matchesRenterQuery`'s KDoc warns about.
 */
internal fun createGlobalSearchViewModel(context: Context): GlobalSearchViewModel {
    val database = KlinikDatabase.get(context)
    return GlobalSearchViewModel(
        renterRepository = RenterRepository(database.renterDao()),
        vehicleRepository = VehicleRepository(database.vehicleDao()),
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
    // AD-8 in force: the one `Clock` every other ViewModel factory here builds, rather than a
    // seventh call to `Clock.systemDefaultZone()` in the same file. A second construction of the
    // same value is not wrong today — it is a seventh place to change when the rule about which
    // zone counts as "today" moves.
    val clock = Clock.systemDefaultZone()
    return PengaturanViewModel(
        repository = BusinessAccountRepository(database.businessAccountDao(), database),
        // Story 4.4's identity. The database handle is passed because `save` writes three
        // keys as one block and a half-written identity is one the owner did not enter.
        identity = BusinessIdentityRepository(database.settingDao(), database),
        strings = RStrings(context),
        // Story 5.3's lock. The database handle is needed because enabling writes six rows as one
        // block, and a half-written lock is a PIN with no salt.
        pinLock = PinLockRepository(
            dao = database.settingDao(),
            clock = clock,
            database = database,
        ),
        // Story 5.4's cutoff. The **same** `PaymentRepository` the arrears screens read, over the
        // same `clock` — a preview computed against a different "today" than the arrears list
        // would show the owner a figure for a period they are not in.
        payments = PaymentRepository(
            paymentDao = database.paymentDao(),
            allocationDao = database.allocationDao(),
            contractDao = database.contractDao(),
            exceptionDao = database.serviceExceptionDao(),
            settingDao = database.settingDao(),
            database = database,
        ),
        clock = clock,
        // Story 5.1's export, over the same singleton — eight readers, no writer. It is built
        // here rather than in `KlinikNetApp` because the filename the picker is offered needs
        // the same [Clock] the rest of the app's dates use, and a second clock is a second
        // "today".
        backups = createBackupRepository(database, clock),
        // Story 5.2's restore. The database handle is the difference from the exporter: this one
        // clears and writes, so it cannot be built without it.
        restorer = BackupRestorer(
            database = database,
            vehicleDao = database.vehicleDao(),
            renterDao = database.renterDao(),
            contractDao = database.contractDao(),
            serviceExceptionDao = database.serviceExceptionDao(),
            businessAccountDao = database.businessAccountDao(),
            paymentDao = database.paymentDao(),
            allocationDao = database.allocationDao(),
            settingDao = database.settingDao(),
        ),
    )
}

/**
 * Story 5.1's export read, over the database singleton.
 *
 * Its own factory because it needs eight DAOs and every other repository needs one or two: an
 * inline `BackupRepository(...)` in the settings factory would be a ten-line constructor call
 * sitting next to two two-line ones, and the count is itself the argument for naming it.
 *
 * Returns the repository rather than the ViewModel, because the *filename* is needed outside
 * this file — the picker is launched in `AppNavGraph`, which has no database handle — and a
 * second derivation of that format string in the graph would be the defect the whole class
 * exists to prevent.
 */
internal fun createBackupRepository(database: KlinikDatabase, clock: Clock): BackupRepository =
    BackupRepository(
        vehicleDao = database.vehicleDao(),
        renterDao = database.renterDao(),
        contractDao = database.contractDao(),
        serviceExceptionDao = database.serviceExceptionDao(),
        businessAccountDao = database.businessAccountDao(),
        paymentDao = database.paymentDao(),
        allocationDao = database.allocationDao(),
        settingDao = database.settingDao(),
        clock = clock,
    )

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
        // And the history read, over the same DAO the repositories already hold. Left
        // out, `openHistory` refuses rather than showing an empty list -- and because the
        // parameter is nullable for the tests' sake, nothing fails at build time to say so.
        // That is the same trap the three above record, and it is why this line matters
        // more than it looks.
        historyRepository = DepositHistoryRepository(database.paymentDao()),
    )
}

/**
 * The morning summary's ViewModel over the same singleton.
 *
 * Two repositories, and both of them are **existing** ones: the payments repository (for
 * today's income and the arrears total) and the vehicle repository (for the fleet split).
 * The screen has no arithmetic of its own — that is the whole of its design — so it needs
 * nothing new to read from.
 */
internal fun createBerandaViewModel(context: Context): BerandaViewModel {
    val database = KlinikDatabase.get(context)
    return BerandaViewModel(
        payments = PaymentRepository(
            paymentDao = database.paymentDao(),
            allocationDao = database.allocationDao(),
            contractDao = database.contractDao(),
            exceptionDao = database.serviceExceptionDao(),
            settingDao = database.settingDao(),
            database = database,
        ),
        vehicleRepository = VehicleRepository(database.vehicleDao(), database),
        strings = RStrings(context),
        // The dashboard's charts and its transaction table. `contracts` carries the frozen
        // daily rate the trend's target line draws; the other three join a deposit row to the
        // renter, the plate and the bank that make it readable.
        contracts = ContractRepository(
            database.contractDao(),
            database.serviceExceptionDao(),
            database,
        ),
        history = DepositHistoryRepository(database.paymentDao()),
        renters = RenterRepository(database.renterDao()),
        accounts = BusinessAccountRepository(database.businessAccountDao(), database),
        // The same clock every other ViewModel gets, for the same reason: "today" decides
        // which deposits count, and a test that passed in October would fail in November.
        clock = Clock.systemDefaultZone(),
        // Story 5.5's reminder, over the **same** repository the settings screen exports
        // through. Two instances would still read the same rows, but the reminder's date and
        // the export's date must be one fact, and one repository makes that structural.
        backups = createBackupRepository(database, Clock.systemDefaultZone()),
    )
}

/**
 * Builds the period report's ViewModel over the database singleton.
 *
 * [PaymentRepository] is built over the **same** DAO set `createBerandaViewModel` builds, and
 * that is the point rather than an accident of wiring: the report's figures and the morning
 * summary's must come from the same reads, so they share the repository's shape. Two
 * repositories over one database would be harmless here but would invite the second
 * arithmetic this story exists to prevent.
 */
internal fun createLaporanViewModel(context: Context): LaporanViewModel {
    val database = KlinikDatabase.get(context)
    val settingDao = database.settingDao()
    return LaporanViewModel(
        payments = PaymentRepository(
            paymentDao = database.paymentDao(),
            allocationDao = database.allocationDao(),
            contractDao = database.contractDao(),
            exceptionDao = database.serviceExceptionDao(),
            settingDao = settingDao,
            database = database,
        ),
        strings = RStrings(context),
        // The same clock every other ViewModel gets: which period "today" falls in decides
        // every figure on this screen.
        clock = Clock.systemDefaultZone(),
        // Read through the DAO the report already holds, so the period's boundaries are the
        // ones the allocation engine used rather than a constant typed here.
        cutoffDay = {
            settingDao.findInt(PaymentRepository.KEY_CUTOFF_DAY, PaymentRepository.DEFAULT_CUTOFF_DAY)
        },
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
        // Story 4.4's single source for the name a reminder quotes. Passed as the one
        // suspending read the ViewModel needs, not as the repository, so the ViewModel
        // depends on a value rather than on a storage layer.
        businessName = { BusinessIdentityRepository(database.settingDao()).businessName() },
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
    beranda: BerandaViewModel,
    laporan: LaporanViewModel,
    search: GlobalSearchViewModel,
    // Story 5.1's export, **not** a ViewModel.
    //
    // The export is a read plus a write, it holds no state of its own between calls, and
    // everything it produces travels to the settings screen through that screen's ViewModel.
    // Giving it one would mean a second set of flows collected here for no new information —
    // and the one fact the route needs from it, the filename, is a pure function of a date.
    backups: BackupRepository,
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
    // The reminder link is a one-shot: the screen launches it and reports back, so the same
    // message cannot open twice on a recomposition.
    val tunggakanPendingLink by tunggakan.pendingLink.collectAsStateWithLifecycle()
    val renterOptions by kontrak.renterOptions.collectAsStateWithLifecycle()
    val vehicleOptions by kontrak.vehicleOptions.collectAsStateWithLifecycle()
    // Story 3.1's screen. It has no second window of its own beyond its form sheet, so one
    // banner pair is enough — unlike the contracts screen's three sheets.
    val pengaturanState by pengaturan.uiState.collectAsStateWithLifecycle()
    val pengaturanSnackbar by pengaturan.snackbarMessage.collectAsStateWithLifecycle()
    val setoranState by setoran.uiState.collectAsStateWithLifecycle()
    // The deposit screen's own state, a separate flow from the sheet's for the reason
    // `SetoranScreenState`'s KDoc gives: the sheet is a window over any screen, this is a
    // destination with its own lifetime.
    val setoranScreenState by setoran.screenState.collectAsStateWithLifecycle()
    // Story 4.3's morning summary. A whole screen rather than a sheet, so its banner would
    // live on its own state — but it has no banner, because every figure it shows comes
    // from a read that either succeeded or is still loading, and neither is a refusal.
    val berandaState by beranda.uiState.collectAsStateWithLifecycle()
    // Story 4.5's two collections: the figures, and the periods the picker offers. The
    // picker is its own flow so a chip appearing does not recompose the four tiles.
    val laporanState by laporan.uiState.collectAsStateWithLifecycle()
    val laporanPeriods by laporan.periodOptions.collectAsStateWithLifecycle()
    // Story 4.6's global search field, mounted by the shell above every destination.
    val searchState by search.uiState.collectAsStateWithLifecycle()

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
                    onSelect = kendaraan::select,
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
                    onSelect = penyewa::select,
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
                    // The deposit history, and the two verbs that mount Stories 3.4 and 3.5.
                    // Wired to the **deposit** ViewModel rather than the contract one,
                    // because `requestCancel` and `openEditDeposit` live there -- and the
                    // history state does too, for the reason `SetoranViewModel` records on
                    // its own: a read by one ViewModel and acted on by another is a state
                    // that has to be kept in step across two objects.
                    depositHistory = setoranState.history,
                    onRequestDepositCancel = setoran::requestCancel,
                    onRequestDepositEdit = setoran::openEditDeposit,
                    onDepositHistoryDismiss = setoran::consumeHistoryError,
                    // And the verb that opens it. A method reference rather than a lambda
                    // wrapping one, because the row already carries the contract and the
                    // renter name — a lambda would re-read the renter by id to fill an
                    // argument the row was holding.
                    onOpenHistory = { row ->
                        setoran.openHistory(
                            contractId = row.contract.id,
                            renterName = row.renterName,
                            vehiclePlate = row.vehiclePlate,
                        )
                    },
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
                    // Story 5.1's backup. `backupFilename` is read at the moment the picker
                    // opens rather than stored, so a settings screen left open across midnight
                    // still offers today's date — and the name comes from the repository's own
                    // formatter rather than a second spelling of the same format here.
                    onExportErrorDismiss = pengaturan::consumeExportError,
                    onExportGranted = pengaturan::exportTo,
                    backupFilename = { backups.filenameFor(LocalDate.now(Clock.systemDefaultZone())) },
                    // Story 5.2's restore. The parse happens in the ViewModel because the
                    // preview **is** the decision; the file read happened in the route, which
                    // holds the granted `Uri`.
                    onRestoreFileChosen = pengaturan::onRestoreFileChosen,
                    onConfirmRestore = pengaturan::confirmRestore,
                    onDismissRestore = pengaturan::dismissRestore,
                    onRestoreErrorDismiss = pengaturan::consumeRestoreError,
                    // Story 5.3's lock, configured here. The lock *screen* is not this screen's
                    // business: it is mounted by the Activity above the shell.
                    onOpenPinSetup = pengaturan::openPinSetup,
                    onDisablePin = pengaturan::disablePin,
                    onAutoLockChange = pengaturan::setAutoLockMinutes,
                    onPinSetupDigit = pengaturan::onSetupDigit,
                    onPinSetupDelete = pengaturan::onSetupDelete,
                    onConfirmPinCodeWritten = pengaturan::confirmPinCodeWritten,
                    onDismissPinSetup = pengaturan::dismissPinSetup,
                    // Story 5.4's cutoff. `onCutoffPick` computes and shows; only
                    // `onConfirmCutoff` writes, so the preview cannot be skipped.
                    onCutoffPick = pengaturan::requestCutoffChange,
                    onConfirmCutoff = pengaturan::confirmCutoffChange,
                    onDismissCutoffPreview = pengaturan::dismissCutoffPreview,
                    onCutoffErrorDismiss = pengaturan::consumeCutoffError,
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
                    onRiskFilterChange = tunggakan::onRiskFilterChange,
                    onErrorDismiss = tunggakan::consumeError,
                    // Story 4.2's five. The reminder's text lives on the state, so only the
                    // intents are passed — the same shape as onSelect.
                    onRemind = tunggakan::openReminder,
                    onReminderChange = tunggakan::onReminderChange,
                    onReminderSend = tunggakan::sendReminder,
                    onReminderCopy = tunggakan::onReminderCopied,
                    onReminderDismiss = tunggakan::dismissReminder,
                    onReminderLinkOpened = tunggakan::onReminderLinkOpened,
                    onReminderOpenFailed = tunggakan::onReminderOpenFailed,
                    pendingReminderLink = tunggakanPendingLink,
                ),
                // Story 4.3's morning summary. Only the state and the **deposit sheet**
                // action are wired here — the other three actions are *navigations*, and
                // the shell owns the nav controller, so the route derives them from it.
                // Putting a navigation here would need a second nav controller, and two
                // of them is a back stack that disagrees with itself.
                beranda = Screens.Beranda(
                    state = berandaState,
                    onDeposit = setoran::openDeposit,
                    // Story 5.5: the owner tapped the backup reminder. Nothing is written and
                    // no route is taken here — the dialog is the screen's own window and the
                    // navigation belongs to the shell. This is here so the tap is observable
                    // at all, which is the difference between "a dialog appeared" and "the
                    // app knows the owner is worried about their backup".
                    onBackupReminderTap = beranda::onBackupReminderTapped,
                ),
                // Story 4.5's report. No navigation verb: the only control is picking a
                // period, which is the screen's own state, so a second nav controller here
                // would be a back stack disagreeing with itself over nothing.
                laporan = Screens.Laporan(
                    state = laporanState,
                    periodOptions = laporanPeriods,
                    onSelectPeriod = laporan::selectPeriod,
                ),
                // Story 4.6's search field. No `onSelect` here: choosing a result is a
                // navigation, and the nav controller lives in the shell — see
                // `Screens.Search`'s KDoc.
                search = Screens.Search(
                    state = searchState,
                    onQueryChange = search::onQueryChange,
                    onClear = search::clearQuery,
                ),
                // The deposit screen's own wiring. Its three callbacks go to the
                // **deposit** ViewModel — the same object the sheet uses — because the
                // verbs must land where the cancellation dialog and the correction form
                // live. Required, not defaulted: both verbs shipped unreachable once.
                setoran = Screens.Setoran(
                    state = setoranScreenState,
                    onSelectTab = setoran::selectTab,
                    onRequestDepositCancel = setoran::requestCancel,
                    onRequestDepositEdit = setoran::openEditDeposit,
                    onDismissError = setoran::consumeScreenError,
                ),
            ),
        )
    }
}


