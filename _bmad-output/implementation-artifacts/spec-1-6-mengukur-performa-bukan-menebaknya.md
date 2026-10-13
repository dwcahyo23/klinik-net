---
title: 'Story 1.6: Mengukur performa, bukan menebaknya'
type: 'feature'
created: '2026-10-13'
baseline_revision: 5d472351f28732150f021283272976b643b21a7e
status: 'in-progress'
review_loop_iteration: 0
followup_review_recommended: false
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md'
warnings: []
deferred: []
---

<intent-contract>

## Intent

**Problem:** NFR-1's 50 ms figure for a full recompute (20 contracts, 2,000 payments, 500 charge days) has no test behind it — a performance regression would pass every green test silently. The figure also has no recorded baseline.

**Approach:** One JUnit 4 test in `domain/` that runs the full recompute at the NFR-1 scale and asserts it completes in bounded time, with the measured figure printed as the baseline; a companion spec section records the threshold policy (loosened threshold for build machines, explicit never-silently-removed rule) and the on-device-vs-CI distinction the epic requires.

## Boundaries & Constraints

**Always:**
- The benchmark runs at the exact NFR-1 scale: 20 contracts, 2,000 total payments, 500 charge days total (25 days × 20 contracts), full `allocatePayments` + `arrearsOf` per contract.
- The wall-clock figure is asserted with a CI threshold that is explicitly documented as looser than the on-device 50 ms target, and the printed value is the recorded baseline (one line in the test output is enough — the spec itself records the policy, not the number).
- Timing uses `System.nanoTime()` around the whole loop, not per-contract micro-timing.
- All seven `verify-fr10.md` cases stay green as part of the story's own test run (`gradlew :domain:test`).
- No new dependency that is not already in `gradle/libs.versions.toml`; JUnit 4.13.2 is sufficient (a plain `@Test` with wall-clock timing — no JUnit Benchmark library).

**Never:**
- No new JVM test framework or benchmark library (no JUnit5, no JUnit-Benchmark, no JMH) — JUnit 4 with `System.nanoTime()` is the minimal answer.
- No Android/Room/SQL in the measurement — this benchmarks the pure domain engine only.
- No correctness change: the benchmark must not alter `allocatePayments`, `arrearsOf`, or any engine code; it is read-only over the engine's public API.
- No silently removing or silently lowering the threshold once it exists — any change to the threshold is an explicit, logged spec change (NFR-1's "tidak boleh dihapus diam-diam").

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| NFR-1 scale benchmark | 20 contracts, 2,000 payments split across them, 500 charge days (25 days each), one `asOf` | Total loop wall-clock asserted `<=` the CI threshold; baseline figure printed | No error expected |
| Threshold is explicit and looser than 50 ms | The 50 ms figure is the on-device target (Redmi Pad 2, debug mode) | The CI test asserts a larger, explicitly named bound (e.g. 500 ms on a build machine) and documents the relationship in a KDoc | No error expected |
| Regression detection | Engine change slows the loop past the CI bound | Test fails with the measured figure in the message | Failing assertion, message carries the measured ms |
| All seven verify-fr10 cases | The existing `AllocationEngineTest`, `ArrearsTest`, etc. fixtures | All green in the same `:domain:test` run | No error expected |

</intent-contract>

## Code Map

- `domain/src/main/kotlin/net/klinik/rental/domain/calculate/AllocationEngine.kt` -- `allocatePayments(payments, chargeDays, dailyRate, asOf): List<Allocation>` (L61–66). The measurement wraps repeated calls to this. **Read-only.**
- `domain/src/main/kotlin/net/klinik/rental/domain/calculate/Arrears.kt` -- `arrearsOf(contractId, chargeDays, allocations, dailyRate): ArrearsEntry` (L39–44); `inArrearsOrder()` (L88). **Read-only.**
- `domain/src/main/kotlin/net/klinik/rental/domain/calculate/CutoffPeriod.kt` -- `CutoffPeriod.containing(cutoffDay, today)` (L77). **Read-only.**
- `domain/src/main/kotlin/net/klinik/rental/domain/calculate/ChargeDays.kt` -- `chargeDaysIn(period, schedule, asOf)` (L55); `ChargeSchedule` (L16–23). **Read-only.**
- `domain/src/main/kotlin/net/klinik/rental/domain/model/Payment.kt` -- `Payment(id, date, amount)` (L25–32). **Read-only.**
- `domain/src/main/kotlin/net/klinik/rental/domain/model/Allocation.kt` -- `Allocation(paymentId, chargeDate?, amount)` (L23–36). **Read-only.**
- `domain/build.gradle.kts` -- pure-JVM module, `testImplementation(libs.junit)` = JUnit 4.13.2 only; **no change needed** — no new dependency required. **Read-only.**
- `domain/src/test/kotlin/net/klinik/rental/domain/calculate/AllocationEngineTest.kt` -- fixture style to imitate: class-level `period`/`asOf`/`rate`/`days` fields, `row(day, amount)` helper, backtick Indonesian test names, one matrix row per test.
- `_bmad-output/planning-artifacts/epics.md:512-534` -- Story 1.6 ACs (the 50 ms figure, the explicit-lowering rule, the CI-vs-device distinction).
- `_bmad-output/planning-artifacts/prds/prd-dev.klinik.net-2026-10-13/verify-fr10.md` -- the seven cases that must all be green.

**To create:**
- `domain/src/test/kotlin/net/klinik/rental/domain/calculate/BenchmarkTest.kt` -- the NFR-1 scale benchmark test.

## Tasks & Acceptance

**Execution:**
- `domain/src/test/kotlin/net/klinik/rental/domain/calculate/BenchmarkTest.kt` -- add one class with: (1) a fixture that builds 20 contracts × 25 charge days each and 2,000 `Payment` objects distributed across them (e.g. 100 payments per contract), matching the NFR-1 scale; (2) a `@Test` that runs the full loop — for each contract: `allocatePayments(...)` then `arrearsOf(...)` — wrapped in one `System.nanoTime()` span; (3) asserts the total elapsed milliseconds is `<=` the CI threshold constant, named e.g. `CI_THRESHOLD_MS` and set to a value explicitly documented as looser than the on-device 50 ms target (e.g. 500 ms), with the KDoc carrying the NFR-1 policy: the 50 ms figure is the on-device baseline, this test is the build-machine proxy with an explicitly looser bound, and any future change to the bound must be an explicit spec-level decision, never silent; (4) prints the measured figure as the baseline via `System.out` (or an assertion message that carries it) so the number is visible in the test log.
- `_bmad-output/implementation-artifacts/epic-1-context.md` -- append a `## Performance Baseline` section under the existing structure recording: the measured CI figure from the benchmark run, the on-device 50 ms target it proxies, the explicit CI threshold in use, and the rule that lowering or removing it is a logged decision (NFR-1's "tidak boleh dihapus diam-diam").

**Acceptance Criteria:**
- Given a domain engine with 20 contracts, 2,000 payments, 500 charge days, when the benchmark test runs the full recompute loop, then total elapsed time is asserted below the explicitly-named CI threshold and the measured figure is printed as the baseline.
- Given the CI threshold is a build-machine proxy, when the on-device 50 ms target is the real requirement, then the test's KDoc makes the distinction explicit and does not claim the CI figure is the on-device figure.
- Given any future change to the threshold, when the change is not recorded as an explicit decision, then it is a spec violation (the "never silently remove" rule from NFR-1 is documented in the test's KDoc and in `epic-1-context.md`).
- Given the benchmark is part of `:domain:test`, when the full suite runs on a machine without Android SDK, then the benchmark passes and all seven `verify-fr10.md` cases remain green.

## Spec Change Log

## Review Triage Log

## Design Notes

`CI_THRESHOLD_MS` is deliberately not 50 ms: the 50 ms figure is specified against the Redmi Pad 2 in debug mode, a condition the build machine does not reproduce. Asserting 50 ms on a CI runner would make the test flaky for the wrong reason — a slow CI machine is not a slow phone. The bound chosen is a documented proxy, large enough to survive build-machine variance but small enough to catch a genuine order-of-magnitude regression in the engine; the exact value is an implementer choice and must be named and justified in the KDoc, which is the "explicit, not silent" rule NFR-1 requires.

The `epic-1-context.md` append is the "recorded as baseline in the epic document" half of the AC — the test prints the number; the context file records it so it is diffable across commits and the between-commit comparison the epic's Notes ask for.

**Implementer's record (Story 1.6, 2026-10-13).**
- `CI_THRESHOLD_MS = 500` chosen and set in `BenchmarkTest.kt`; the KDoc there carries the justification above.
- Measured baseline on the build machine (host `IOT04`, JVM 17 target, post-warm-up): **14 ms** for the full NFR-1-scale recompute (2,000 allocation rows). Recorded in `epic-1-context.md`'s `## Performance Baseline` section; re-record there on any later commit that changes the figure.
- To reach exactly 500 charge days total at 25 per contract, the fixture period (28 calendar days, 25 Feb – 24 Mar) drops its first three days via `serviceExceptions`.
- No threshold change and no engine change; this record is the "explicit, not silent" half of NFR-1 for this run.

## Verification

**Commands:**
- `gradlew.bat :domain:test --tests "*BenchmarkTest*"` -- expected: BUILD SUCCESSFUL, benchmark test green, baseline figure printed in the test log
- `gradlew.bat :domain:test --rerun-tasks` with `ANDROID_HOME` and `ANDROID_SDK_ROOT` cleared -- expected: BUILD SUCCESSFUL, all tests pass including the seven `verify-fr10.md` cases, no "SDK location not found"
