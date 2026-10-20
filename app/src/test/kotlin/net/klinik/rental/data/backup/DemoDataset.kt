package net.klinik.rental.data.backup

import net.klinik.rental.data.db.KlinikDatabase
import net.klinik.rental.data.db.SCHEMA_VERSION
import net.klinik.rental.data.db.entity.BusinessAccountEntity
import net.klinik.rental.data.db.entity.RenterEntity
import net.klinik.rental.data.db.entity.SettingEntity
import net.klinik.rental.data.db.entity.VehicleEntity
import net.klinik.rental.data.repository.ContractRepository
import net.klinik.rental.data.repository.PaymentMethod
import net.klinik.rental.data.repository.PaymentRepository
import net.klinik.rental.domain.calculate.CutoffPeriod
import net.klinik.rental.domain.model.Contract
import net.klinik.rental.domain.model.Payment
import net.klinik.rental.domain.model.ServiceException
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The prototype's mock dataset, rebuilt as a restorable backup file.
 *
 * ## Why this writes through the repositories rather than building rows by hand
 *
 * Because `AllocationEngine` enforces `Σ allocation == amount` per deposit with a `check()`, and
 * hand-written allocation rows violate it the first time a figure is edited. Building the file
 * through `ContractRepository.open`, `addServiceException`, `PaymentRepository.saveDeposit` and
 * `cancelDeposit` means the fixture **is** what the app would have produced — the same derivation,
 * the same period boundaries, the same surplus rows — so restoring it exercises the real write
 * rules instead of a parallel copy of them.
 *
 * ## Why every date is shifted
 *
 * `prototype/data.js` anchors at `SIM_TODAY = 2026-03-24`. Restored literally today, the staged
 * arrears would be months old and every contract would show the same uniform accrual, which
 * demonstrates nothing. The offset moves the far end onto the caller's day, so the demo opens
 * with the arrears it was designed to show. Figures therefore no longer match `data.js` digit for
 * digit; that is the accepted cost, recorded in `deferred-work.md`.
 *
 * ## What is deliberately dropped
 *
 * `ktp`, `address`, `avatar`, `joinDate` and `currentRenterId` have no columns. `status` is not
 * stored either: `rented` and `available` are derived from contract existence, so `car-9` and
 * `car-10` both read `Tersedia` — only `car-10`'s `isServis` is a stored fact.
 */
object DemoDataset {

    /** The prototype's own anchor (`data.js:21`). Everything below is expressed relative to it. */
    val SIM_TODAY: LocalDate = LocalDate.of(2026, 3, 24)

    private val PERIOD_START: LocalDate = LocalDate.of(2026, 2, 1)

    /** The prototype's cutoff day (`DEFAULT_SETTINGS.cutoffDay`), which the app reads from storage. */
    private const val CUTOFF_DAY = 25

    private data class Car(
        val id: String,
        val plate: String,
        val brand: String,
        val model: String,
        val year: Int,
        val color: String,
        val rate: Long,
        val notes: String,
        val isServis: Boolean = false,
    )

    private data class Person(
        val id: String,
        val name: String,
        val phone: String,
        val emergencyContact: String,
    )

    private data class Lease(
        val id: String,
        val renterId: String,
        val vehicleId: String,
        val start: LocalDate,
        val rate: Long,
        /** `workSchedule: "6days"` rests on Sunday; `"7days"` stores no rest day. */
        val sixDays: Boolean,
        val exemptions: List<LocalDate> = emptyList(),
    )

    private val CARS = listOf(
        Car("car-1", "B 1235 TF", "Toyota", "Agya 1.2 G", 2022, "Silver Metalik", 250_000, "Kondisi terawat, servis berkala Auto2000"),
        Car("car-2", "B 2841 SK", "Daihatsu", "Sigra 1.2 R", 2021, "Putih", 270_000, "Ban depan baru ganti"),
        Car("car-3", "B 1904 WX", "Toyota", "Avanza 1.3 E", 2020, "Hitam", 300_000, "Pajak hidup sd Nov 2026"),
        Car("car-4", "B 2419 PQA", "Honda", "Brio Satya E", 2023, "Kuning Karnival", 280_000, "Unit favorit driver online"),
        Car("car-5", "B 1782 URF", "Daihatsu", "Ayla 1.0 X", 2020, "Merah", 230_000, "Ekonomis, konsumsi BBM irit"),
        Car("car-6", "B 2011 RZ", "Suzuki", "Ertiga GL", 2021, "Abu-abu Gelap", 320_000, "Baru servis berkala 40.000km"),
        Car("car-7", "B 1553 VKM", "Toyota", "Calya 1.2 G", 2022, "Hitam", 270_000, "Penyewa tertib setor subuh"),
        Car("car-8", "B 2980 TZQ", "Mitsubishi", "Xpander GLS", 2022, "Putih Mutiara", 380_000, "Unit premium mingguan"),
        Car("car-9", "B 1472 NPL", "Toyota", "Agya 1.2 GR", 2023, "Merah Sporty", 260_000, "Siap sewa hari ini"),
        Car("car-10", "B 2309 CDE", "Daihatsu", "Xenia 1.3 R", 2019, "Silver", 290_000, "Ganti kampas rem & oli mesin bengkel resmi", isServis = true),
    )

    private val RENTERS = listOf(
        Person("renter-1", "Budi Santoso", "0812-9844-3211", "0813-8811-2200 (Istri)"),
        Person("renter-2", "Ahmad Maulana", "0813-1122-8799", "0857-1234-9988 (Kakak)"),
        Person("renter-3", "Rian Hidayat", "0857-7612-4401", "0812-9988-1122 (Paman)"),
        Person("renter-4", "Dedi Supriadi", "0818-0922-3118", "0819-4455-6677 (Teman)"),
        Person("renter-5", "Hendra Gunawan", "0812-3344-9081", "0813-7766-5544 (Adik)"),
        Person("renter-6", "Fajar Nugraha", "0878-8812-6523", "0856-9900-1122 (Orang Tua)"),
        Person("renter-7", "Wahyu Setiawan", "0813-8877-4433", "0812-4455-2211 (Istri)"),
        Person("renter-8", "Teguh Prasetyo", "0815-9922-1178", "0818-2233-4455 (Saudara)"),
    )

    private val LEASES = listOf(
        Lease("cont-1", "renter-1", "car-1", LocalDate.of(2026, 1, 1), 250_000, true, listOf(LocalDate.of(2026, 3, 5))),
        Lease("cont-2", "renter-2", "car-2", LocalDate.of(2026, 1, 15), 270_000, false),
        Lease("cont-3", "renter-3", "car-3", LocalDate.of(2026, 2, 1), 300_000, true, listOf(LocalDate.of(2026, 3, 12), LocalDate.of(2026, 3, 13))),
        Lease("cont-4", "renter-4", "car-4", LocalDate.of(2026, 2, 10), 280_000, false),
        Lease("cont-5", "renter-5", "car-5", LocalDate.of(2026, 2, 15), 230_000, true),
        Lease("cont-6", "renter-6", "car-6", LocalDate.of(2026, 3, 1), 320_000, true),
        Lease("cont-7", "renter-7", "car-7", LocalDate.of(2026, 3, 1), 270_000, false),
        Lease("cont-8", "renter-8", "car-8", LocalDate.of(2026, 3, 10), 380_000, true),
    )

    /**
     * How many charge days each of three contracts leaves unpaid, so the demo shows three
     * arrears tiers — 8 (Kritis), 3 (Perhatian) and 2 (Ringan).
     *
     * ## Why these three contracts start paying only in the current period
     *
     * Two measured facts about this app's arrears, both of which the prototype's generator
     * ignores:
     *
     * 1. `arrearsEntry` **re-derives** the allocation from every confirmed deposit against the
     *    current period's charge days — it does not ask which day a deposit was dated. So a gap
     *    in the middle is filled by the next deposit, and `prototype/data.js`'s scattered
     *    `MISSED` dates produce **zero** arrears here. Measured: transcribing them literally gave
     *    `cont-5` zero open days.
     * 2. Because the re-derivation is all-time, earlier periods' deposits also pay for this
     *    period. A contract with months of history can never be in arrears, however many days it
     *    skipped recently.
     *
     * So an arrears tier needs (a) a payment stream that stops early, and (b) no older deposits
     * to cover the gap. These three contracts therefore begin paying at the current period's
     * start rather than at [PERIOD_START]; the other five keep the full history the charts read.
     * The tail is the last N charge days, where nothing follows to cover them — which is also
     * the honest shape: a renter who stopped paying is a stream that ended, not a ledger with
     * holes in it.
     */
    private val UNPAID_TAIL: Map<String, Int> = mapOf(
        "cont-5" to 8,
        "cont-3" to 3,
        "cont-6" to 2,
    )

    /** Partial and over-payment, which is what makes credit and the surplus row visible. */
    private val OVERRIDE: Map<Pair<String, LocalDate>, Long> = mapOf(
        ("cont-5" to LocalDate.of(2026, 3, 20)) to 150_000L,
        ("cont-4" to LocalDate.of(2026, 3, 21)) to 560_000L,
    )

    private data class Account(val id: String, val bank: String, val number: String)

    private val ACCOUNTS = listOf(
        Account("acc-bca", "BCA", "0882199001"),
        Account("acc-mandiri", "Mandiri", "12400998811"),
        Account("acc-bri", "BRI", "03410199881"),
        Account("acc-bni", "BNI", "021998822"),
    )

    /**
     * Seeds [db] with the demo dataset and returns it as a backup file's text.
     *
     * @param asOf the day the demo's far end should land on. Every date in the prototype is
     *   shifted by the same number of days, so the staged arrears arrive at "today" rather than
     *   months ago.
     */
    suspend fun build(db: KlinikDatabase, asOf: LocalDate): String {
        val shift = ChronoUnit.DAYS.between(SIM_TODAY, asOf)
        fun moved(date: LocalDate): LocalDate = date.plusDays(shift)

        CARS.forEach { car ->
            db.vehicleDao().insert(
                VehicleEntity(
                    id = car.id,
                    plate = car.plate,
                    brand = car.brand,
                    model = car.model,
                    year = car.year,
                    color = car.color,
                    dailyRate = car.rate,
                    notes = car.notes,
                    isServis = car.isServis,
                ),
            )
        }

        RENTERS.forEach { person ->
            db.renterDao().insert(
                RenterEntity(
                    id = person.id,
                    name = person.name,
                    phone = person.phone,
                    bankAccount = null,
                    emergencyContact = person.emergencyContact,
                ),
            )
        }

        ACCOUNTS.forEach { account ->
            db.businessAccountDao().insertBusinessAccount(
                BusinessAccountEntity(id = account.id, bankName = account.bank, number = account.number),
            )
        }

        db.settingDao().putSetting(SettingEntity("cutoffDay", "25"))
        db.settingDao().putSetting(SettingEntity("businessName", "PT Klinik Net Rent Car"))
        db.settingDao().putSetting(
            SettingEntity("businessAddress", "Jl. Boulevard Raya Blok Q No. 18, Jakarta Timur"),
        )
        db.settingDao().putSetting(SettingEntity("businessPhone", "0812-8899-7722"))

        val contracts = ContractRepository(db.contractDao(), db.serviceExceptionDao(), db)
        val payments = PaymentRepository(
            paymentDao = db.paymentDao(),
            allocationDao = db.allocationDao(),
            contractDao = db.contractDao(),
            exceptionDao = db.serviceExceptionDao(),
            settingDao = db.settingDao(),
            renterDao = db.renterDao(),
            vehicleDao = db.vehicleDao(),
            businessAccountDao = db.businessAccountDao(),
            database = db,
        )

        LEASES.forEach { lease ->
            contracts.open(
                Contract(
                    id = lease.id,
                    renterId = lease.renterId,
                    vehicleId = lease.vehicleId,
                    startDate = moved(lease.start),
                    dailyRate = lease.rate,
                    // `workSchedule` + `offDays: [0]` is one fact in two encodings; the stored
                    // form is the nullable rest day, and `DayOfWeek.SUNDAY.value` is 7 — JS's
                    // `getDay()` uses 0, so the mapping is by name, never by number.
                    restDay = if (lease.sixDays) DayOfWeek.SUNDAY else null,
                ),
            )
            lease.exemptions.forEachIndexed { index, date ->
                contracts.addServiceException(
                    ServiceException(
                        id = "${lease.id}-ex-$index",
                        contractId = lease.id,
                        date = moved(date),
                        // `data.js` carries no reason text; the demo needs one that reads like
                        // something the owner would actually have typed.
                        reason = "Servis berkala di bengkel resmi",
                    ),
                )
            }
        }

        // Each contract's charge days, so the unpaid tail can be the **last** ones. Computed
        // here rather than by hand because the tail has to land on real charge days: a Sunday
        // or an exempt day is not owed, so skipping it would produce fewer arrears days than
        // the tier says.
        // The period "today" falls in, in the prototype's own dates — the window the arrears
        // read bills, and therefore the earliest a behind-on-rent contract may start paying.
        val currentPeriodStart = CutoffPeriod.containing(CUTOFF_DAY, SIM_TODAY).start

        fun firstPayDay(lease: Lease): LocalDate =
            if (lease.id in UNPAID_TAIL) maxOf(lease.start, currentPeriodStart) else lease.start

        val unpaidTail: Map<String, Set<LocalDate>> = LEASES.associate { lease ->
            val chargeDays = generateSequence(firstPayDay(lease)) { it.plusDays(1) }
                .takeWhile { it <= SIM_TODAY }
                .filter { !(lease.sixDays && it.dayOfWeek == DayOfWeek.SUNDAY) }
                .filter { it !in lease.exemptions }
                .toList()
            lease.id to chargeDays.takeLast(UNPAID_TAIL[lease.id] ?: 0).toSet()
        }

        var counter = 0
        var day = PERIOD_START
        while (day <= SIM_TODAY) {
            val actualDay = moved(day)
            val dow = day.dayOfWeek

            LEASES.forEach { lease ->
                if (day < firstPayDay(lease)) return@forEach
                if (lease.sixDays && dow == DayOfWeek.SUNDAY) return@forEach
                if (day in lease.exemptions) return@forEach
                if (day in unpaidTail.getValue(lease.id)) return@forEach

                val override = OVERRIDE[lease.id to day]
                val amount = override ?: lease.rate
                // The prototype rotates five bank strings of which "Tunai Kasir" is a method,
                // not an account — so four real accounts and cash with no account id.
                val slot = (counter + 1) % 5
                val account = ACCOUNTS.getOrNull(slot)
                counter++

                payments.saveDeposit(
                    contractId = lease.id,
                    payment = Payment(id = "pay-$counter", date = actualDay, amount = amount),
                    method = if (account == null) PaymentMethod.Tunai else PaymentMethod.TransferBank,
                    accountId = account?.id,
                    referenceNo = "TRX-${actualDay.toString().replace("-", "")}-${lease.id.last()}",
                    notes = when {
                        override == null -> "Setoran harian auto verified IB"
                        override < lease.rate -> "Bayar sebagian, kurang Rp ${lease.rate - override}"
                        else -> "Bayar lebih — 2 hari sekaligus"
                    },
                    asOf = actualDay,
                )
            }
            day = day.plusDays(1)
        }

        // The prototype's one audit trail: a mistyped deposit that was cancelled, not deleted.
        // `cancelDeposit` is what drops its allocations — keeping both double-counts arrears.
        val cancelledId = "pay-${++counter}"
        val cancelledDay = moved(LocalDate.of(2026, 3, 22))
        payments.saveDeposit(
            contractId = "cont-1",
            payment = Payment(id = cancelledId, date = cancelledDay, amount = 250_000),
            method = PaymentMethod.TransferBank,
            accountId = ACCOUNTS.first().id,
            referenceNo = "TRX-BCA-ERR01",
            notes = "Salah input nominal oleh staf",
            asOf = cancelledDay,
        )
        payments.cancelDeposit(
            contractId = "cont-1",
            paymentId = cancelledId,
            reason = "Salah input nominal rekening bank, diulang dengan ref TRX-BCA-96022",
            // `asOf` is **today**, not the deposit's own date. `cancelDeposit` deletes every
            // allocation row on the contract and re-derives them against this day, so passing
            // the deposit's date would cap the charge days there and drop the allocations of
            // every deposit made after it. The app passes `LocalDate.now()`, and so does this.
            asOf = asOf,
        )

        return BackupJson.build(
            vehicles = db.vehicleDao().observeAll().first(),
            renters = db.renterDao().observeAll().first(),
            contracts = db.contractDao().observeAll().first(),
            serviceExceptions = db.serviceExceptionDao().observeAll().first(),
            businessAccounts = db.businessAccountDao().observeAll().first(),
            payments = LEASES.flatMap { db.paymentDao().forContract(it.id) },
            allocations = LEASES.flatMap { db.allocationDao().forContract(it.id) },
            settings = db.settingDao().all(),
            schemaVersion = SCHEMA_VERSION,
        )
    }
}
