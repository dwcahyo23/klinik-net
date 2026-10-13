package net.klinik.rental.domain.calculate

import net.klinik.rental.domain.model.Payment
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Story 1.6's NFR-1 scale benchmark, the missing test behind the 50 ms figure.
 *
 * NFR-1 in `epic-1-context.md` says a full recompute at 20 contracts, 2,000 payments and
 * 500 charge days must finish below **50 ms on the Redmi Pad 2 in debug mode**, and that
 * the measured number must be recorded as a baseline rather than asserted in a code comment
 * that nobody can diff. This class is that test: it runs the loop once at the exact
 * NFR-1 scale, asserts it against a bound, and prints the measured figure so it is
 * visible in the log and reproducible.
 *
 * **The 50 ms target is on-device only.** The device condition — Redmi Pad 2, debug mode,
 * JIT warmed on real hardware — is not reproducible on a CI runner. Asserting 50 ms in
 * this test would make it flaky for the wrong reason: a slow build machine is not a slow
 * phone, and a cold JVM is not a warmed device. The CI bound in [CI_THRESHOLD_MS] is
 * therefore a *documented proxy* — loose enough to survive build-machine variance and JVM
 * warm-up, tight enough to catch a genuine order-of-magnitude regression in the engine.
 *
 * [CI_THRESHOLD_MS] is 500 ms. That is deliberately ten times the on-device target:
 * the engine at NFR-1 scale is a pure-JVM computation over 2,000 allocation rows
 * (20 contracts × 100 payments, each resolved against 25 charge days), and 500 ms is
 * far above what that should take on any runner that has ever served a build. Anything
 * that pushes the measured figure toward 500 ms is a real algorithmic or data-structure
 * regression, not machine variance.
 *
 * **Never silently remove or lower this threshold.** NFR-1's rule — "tidak boleh
 * dihapus diam-diam" — means any future change to [CI_THRESHOLD_MS], whether up or
 * down, is a spec-level decision recorded in `epic-1-context.md`'s Performance
 * Baseline section, not a drive-by edit. The KDoc here is the place that decision
 * must be written. Removing the test outright, or loosening the bound to make a
 * failing run green without a recorded reason, is a spec violation.
 *
 * **The printed figure is the baseline.** Every run logs the measured milliseconds
 * via `System.out`. The value on a clean CI run is the number that goes into
 * `epic-1-context.md` and becomes the reference for later commits. This test does
 * not assert against that number — it asserts against [CI_THRESHOLD_MS] — because
 * the on-device 50 ms and the CI bound are two different measurements, and the
 * spec keeps them separate on purpose.
 *
 * The measurement wraps the whole loop with `System.nanoTime()` per the spec's
 * timing rule: one wall-clock span around the full NFR-1-scale run, not
 * per-contract micro-timing that would average out the warm-up the spec cares about.
 */
class BenchmarkTest {

    /**
     * The CI threshold for the NFR-1-scale recompute, in milliseconds.
     *
     * This is a build-machine proxy, not the on-device 50 ms target. See the class
     * KDoc for why the two differ and for the "never silently change" rule.
     */
    private val CI_THRESHOLD_MS = 500L

    /** Number of contracts in the NFR-1 scale. */
    private val contracts = 20

    /** Total payments, 100 per contract, matching NFR-1's 2,000 figure. */
    private val paymentsPerContract = 100

    /** Charge days per contract, 25 days × 20 contracts = NFR-1's 500 figure. */
    private val chargeDaysPerContract = 25

    /** The on-device target, documented only — never asserted here. */
    private val onDeviceTargetMs = 50L

    /**
     * NFR-1 scale: 20 contracts, each with 2,000/20 = 100 payments spread across its
     * charge days, 25 charge days each. One shared `asOf` and `dailyRate`, matching the
     * `verify-fr10.md` fixture so the money values are realistic.
     */
    private val asOf = LocalDate.of(2026, 3, 24)
    private val dailyRate = 250_000L

    @Test
    fun `a full recompute at NFR-1 scale finishes within the CI threshold`() {
        // 20 contracts, 100 payments each, 25 charge days each.
        // Total: 2,000 payments, 500 charge days — the exact NFR-1 figure.
        val paymentsByContract = (0 until contracts).map { contractIndex ->
            List(paymentsPerContract) { paymentIndex ->
                // Spread payments across the period so the asOf gate is exercised,
                // not all front-loaded on day one.
                val date = LocalDate.of(2026, 2, 25).plusDays(((paymentIndex * 2) % 28).toLong())
                Payment(
                    id = "c${contractIndex}p$paymentIndex",
                    date = date,
                    amount = dailyRate,
                )
            }
        }

        // 25 charge days per contract, 20 × 25 = NFR-1's 500. The fixture period is
        // 28 calendar days (25 Feb – 24 Mar); dropping the first three days via
        // serviceExceptions lands each contract exactly on 25 charge days.
        val serviceExceptions = listOf(
            LocalDate.of(2026, 2, 25),
            LocalDate.of(2026, 2, 26),
            LocalDate.of(2026, 2, 27),
        ).toSet()

        val chargeDaysByContract = (0 until contracts).map { _ ->
            chargeDaysIn(
                period = CutoffPeriod(
                    start = LocalDate.of(2026, 2, 25),
                    end = LocalDate.of(2026, 3, 24),
                ),
                schedule = ChargeSchedule(
                    contractStart = LocalDate.of(2026, 1, 1),
                    serviceExceptions = serviceExceptions,
                ),
                asOf = asOf,
            )
        }

        // Warm-up: one full pass before the timed span so JIT has compiled the hot
        // paths. The spec says to time the loop, not the JVM's first breath.
        warmUp(paymentsByContract, chargeDaysByContract)

        val start = System.nanoTime()

        val totalAllocations = (0 until contracts).sumOf { contractIndex ->
            val allocations = allocatePayments(
                payments = paymentsByContract[contractIndex],
                chargeDays = chargeDaysByContract[contractIndex],
                dailyRate = dailyRate,
                asOf = asOf,
            )
            // The full recompute is allocate + arrears, not just allocate.
            arrearsOf(
                contractId = "contract-${contractIndex}",
                chargeDays = chargeDaysByContract[contractIndex],
                allocations = allocations,
                dailyRate = dailyRate,
            )
            allocations.size
        }

        val elapsedMs = (System.nanoTime() - start) / 1_000_000

        // The NFR-1 scale itself is pinned by assertion, not just by the printed
        // string: a fixture drift that drops the total charge-day count (e.g. a later
        // refactor adding a serviceException day, or dropping `restDay`) would otherwise
        // run a smaller workload, still print a green figure, and silently stop measuring
        // the NFR-1 input.
        assertTrue(
            "the NFR-1 fixture must produce exactly ${contracts * chargeDaysPerContract} charge " +
                "days total (${chargeDaysPerContract} per contract); the benchmark would no " +
                "longer be measuring NFR-1's scale — see epics.md Story 1.6 AC #1",
            chargeDaysByContract.sumOf { it.size } == contracts * chargeDaysPerContract,
        )
        assertTrue(
            "the NFR-1-scale recompute must produce exactly " +
                "${contracts * paymentsPerContract} allocation rows; a regression that drops " +
                "the row count (e.g. an engine change that stops writing credit rows) would " +
                "still pass the timing bound but no longer measure the NFR-1 input",
            totalAllocations == contracts * paymentsPerContract,
        )

        // The measured figure is the baseline. Print it so it is in the log and
        // can be copied into epic-1-context.md's Performance Baseline section.
        System.out.println(
            "NFR-1 benchmark: ${totalAllocations} allocation rows across $contracts contracts, " +
                "${contracts * paymentsPerContract} payments, ${contracts * chargeDaysPerContract} charge days " +
                "in $elapsedMs ms (on-device target: ${onDeviceTargetMs} ms)",
        )

        assertTrue(
            "full NFR-1-scale recompute took ${elapsedMs} ms, which exceeds the " +
                "CI threshold of ${CI_THRESHOLD_MS} ms — a genuine regression; " +
                "do not silently lower CI_THRESHOLD_MS, record the change in " +
                "epic-1-context.md's Performance Baseline section first",
            elapsedMs <= CI_THRESHOLD_MS,
        )
    }

    /**
     * One untimed pass before the timed one, so the JVM's first-touch code paths
     * (class loading, branch prediction, allocation patterns) are already hot.
     * Without this the measured figure includes interpreter overhead, which the
     * spec's on-device target does not include either — the device figure is
     * post-warm-up, and so is this baseline.
     */
    private fun warmUp(paymentsByContract: List<List<Payment>>, chargeDaysByContract: List<List<LocalDate>>) {
        (0 until contracts).forEach { contractIndex ->
            val allocations = allocatePayments(
                payments = paymentsByContract[contractIndex],
                chargeDays = chargeDaysByContract[contractIndex],
                dailyRate = dailyRate,
                asOf = asOf,
            )
            arrearsOf(
                contractId = "warm-${contractIndex}",
                chargeDays = chargeDaysByContract[contractIndex],
                allocations = allocations,
                dailyRate = dailyRate,
            )
        }
    }
}
