---
title: 'Story 1.4: Hari menunggak dan tingkat risiko'
type: 'feature'
created: '2026-10-13'
status: 'done'
baseline_revision: 60c84c49a66662f9c8896ada7ae76f316fc972ce
review_loop_iteration: 0
followup_review_recommended: false
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md'
warnings: []
deferred:
  - summary: >-
      `chargeDays` may carry a duplicated day, which `arrearsOf` silently dedupes
      via `toHashSet()`; a repeated open day would then under-count `days`.
    evidence: |-
      `chargeDaysIn` structurally emits no duplicates for its input, so the trigger is
      only reachable through a hand-built day list. Settled by a caller that repeats an
      entry; the KDoc precondition already names the list as `chargeDaysIn`'s output.
    location: >-
      domain/src/main/kotlin/net/klinik/rental/domain/calculate/Arrears.kt:43
    severity: "medium (unverified)"
  - summary: >-
      `chargeDays.size × dailyRate` overflows for a hand-built day list far beyond any
      cutoff period.
    evidence: |-
      1000 days at 9e18 rupiah is ~9e21, below Long.MAX_VALUE; the trigger needs a list
      and rate no period produces. No guard is warranted against an unreachable input.
    location: >-
      domain/src/main/kotlin/net/klinik/rental/domain/calculate/Arrears.kt:54
    severity: "low (unverified)"
---

<intent-contract>

## Intent

**Problem:** The number of days a tenant is behind (`hari menunggak`) and its risk level are what get sent to the tenant over WhatsApp (FR-12). Nothing computes them yet. Story 1.3 produces allocation rows, but no code reads them to say which charge days are still open.

**Approach:** One pure function in `domain/calculate/` that takes the period's charge days, the stored allocations, and the daily rate, and returns the arrears for one contract: the count of open charge days, the Tunggakan amount, and the oldest open day. A second pure function maps the day count to a risk level. A third gives the ordering for the Tunggakan list.

## Boundaries & Constraints

**Always:**
- A charge day is **closed** only when the allocation rows with that `chargeDate` sum to `dailyRate`. A partly paid day stays open (verify-fr10 case 3: Rp 100.000 at Rp 250.000 leaves 28 open days).
- Days = count of open charge days in the list passed in. Never `ceil(Tunggakan ÷ rate)`; the AC rejects that formula.
- Allocation rows with `chargeDate = null`, or whose `chargeDate` is not in the passed day list, do not count toward the Tunggakan here.
- Tunggakan amount = max(0, (listed charge days × rate) − Σ allocation rows whose `chargeDate` is in the list). This includes partial rows on still-open days, which is what makes verify case 3 come out to 6.900.000. Money in Long rupiah, no Double.
- Risk: 0 → LANCAR, 1–2 → RINGAN, 3–6 → PERHATIAN, 7+ → KRITIS. Negative input is rejected.
- The day list arrives already filtered by Story 1.2 (`chargeDaysIn`, capped at `asOf`). This story does not re-filter by date or by `endDate`; an ended contract is passed in like any other.

**Never:**
- No Room, Android, or new dependency. `domain/` stays pure JVM.
- No allocation writes and no change to the allocator in Story 1.3.
- No stored day counts or risk. They are derived from the stored rows every time.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| verify case 1 | 28 days @ 250k, rows 25 Feb + 26 Feb of 250k | 26 open days, amount 6.500.000, oldest open 27 Feb, KRITIS | — |
| Partial payment | 28 days @ 250k, one row 100.000 on 25 Feb | 28 open days, amount 6.900.000 | — |
| Fully paid | 28 days @ 250k, all days closed | 0 days, amount 0, LANCAR | — |
| 1.000.000 at 500k | 2 days @ 500k, no rows | 2 days, amount 1.000.000 | — |
| Credit on a paid period | 28 days @ 250k, full rows for all 28 days plus one null row of 500.000 | 0 days, amount 0, LANCAR; the null row does not count | — |
| Foreign-day row | row whose chargeDate is outside the list | ignored; does not close a listed day | — |
| Non-positive rate | rate 0 or negative | — | `InvalidAmountException` |
| Negative day count | `riskOf(-1)` | — | `IllegalArgumentException` |

</intent-contract>

## Code Map

- `domain/src/main/kotlin/net/klinik/rental/domain/calculate/AllocationEngine.kt` -- `allocatePayments(...)` produces the rows this story reads. **Read-only.**
- `domain/src/main/kotlin/net/klinik/rental/domain/model/Allocation.kt` -- `Allocation(paymentId, chargeDate?, amount)`. **Read-only.**
- `domain/src/main/kotlin/net/klinik/rental/domain/calculate/ChargeDays.kt` -- `chargeDaysIn(...)` supplies the day list. **Read-only.**
- `domain/src/main/kotlin/net/klinik/rental/domain/validation/InvalidAmountException.kt` -- `requireValid(amount)` for the rate. **Read-only.**
- `domain/src/test/kotlin/net/klinik/rental/domain/calculate/AllocationEngineTest.kt` -- the fixture and naming to imitate (backtick Indonesian names, JUnit 4, one rule per test).
- `_bmad-output/planning-artifacts/prds/.../verify-fr10.md` -- numeric authority for cases 1 and 3.
- `_bmad-output/planning-artifacts/epics.md:450-481` -- the Story 1.4 AC, including the list ordering.

**To create:** `domain/src/main/kotlin/net/klinik/rental/domain/calculate/Arrears.kt`, `domain/src/test/kotlin/net/klinik/rental/domain/calculate/ArrearsTest.kt`.

## Tasks & Acceptance

**Execution:**
- `domain/src/main/kotlin/net/klinik/rental/domain/calculate/Arrears.kt` -- add `ArrearsEntry(contractId, days, amount, oldestOpenDay)`, `arrearsOf(contractId, chargeDays, allocations, dailyRate)`, `enum RiskLevel`, `riskOf(days)`, and `List<ArrearsEntry>.inArrearsOrder()` (amount > 0 only, amount desc, then oldest open day asc) -- one file, because all of it reads one rule
- `domain/src/test/kotlin/net/klinik/rental/domain/calculate/ArrearsTest.kt` -- one test per matrix row, plus the risk boundaries 0/1/2/3/6/7 and the ordering rules

**Acceptance Criteria:**
- Given a contract with open charge days, when arrears is computed, then `days` = count of open charge days and the value is not derived from amount ÷ rate.
- Given Tunggakan Rp 1.000.000 at rate Rp 500.000, when arrears is computed, then `days` = 2.
- Given Tunggakan Rp 0, when risk is assigned, then the level is LANCAR with 0 days.
- Given open-day counts 1, 2, 3, 6, and 7, when risk is assigned, then they map to RINGAN, RINGAN, PERHATIAN, PERHATIAN, KRITIS.
- Given two entries with different amounts, when ordered, then the larger amount comes first; with equal amounts, the earlier oldest open day comes first.

## Design Notes

A day counts as closed on the sum of its rows, not on the existence of a row. The allocator fills oldest-first, so partial days are always the newest open ones; the sum test gives the same answer the allocator's rows imply and needs nothing beyond the rows.

### 2026-10-13 — Review pass

Four layers reported: Blind Hunter 13, Edge Case Hunter 4, Verification Gap 1 other-finding, Intent Alignment 1. Verdicts below are my own verification of each claim at the cited file and line.

- verdicts: 19 findings — high 0, medium 1, low 2, false 12, maybe-false 4
- findings:
  - `[false]` `reject` **BH1: `arrearsOf` never filters `allocations` by the passed `contractId`** — `Allocation` does not carry a `contractId` field (`model/Allocation.kt:23-36`); its KDoc says the id is "deliberately absent" because the caller already holds it. A filter would match a field that does not exist. The caller-grouping obligation is now documented in `Arrears.kt`'s KDoc.
  - `[false]` `reject` **BH2: global credit in `amount` vs day-by-day `days` can disagree** — the disagreement is the spec's own rule: "Tunggakan amount = max(0, listed days × rate − Σ allocation rows whose `chargeDate` is in the list)." A cross-day overpayment is legitimate stored money and flows to the next period as the `chargeDate = null` remainder (verify-fr10 case 4); capping the day sum would invent an invariant the spec forbids and hide surplus.
  - `[false]` `reject` **BH3: no test pins the overpayment boundary** — the `credit on a paid period is ignored by the null row` test covers the engine-reachable form of the boundary (full rows + a null surplus row → 0 days, 0 amount); its name says what it asserts. A per-day overpayment on the engine's reachable outputs can only occur through a hand-built input, and the spec deliberately documents that reading.
  - `[false]` `reject` **BH4: `inArrearsOrder` tiebreak when both `amount` and `oldestOpenDay` are equal is unspecified** — identical entries have no observable order; Kotlin's `sortedWith` is stable, so input order decides. No test input is reachable today (two contracts sharing both an amount and the oldest open day is only constructible with `ArrearsEntry` literals, no real caller does that).
  - `[maybe-false]` `defer` **BH5: `chargeDays.toHashSet()` silently deduplicates** — `chargeDaysIn` structurally cannot emit a duplicate for its input, so the trigger is unreachable through the caller the spec names; what would settle it is a hand-built day list with a repeated entry (a caller bug), which the precondition in the KDoc already warns about. Deferred: a guard adds no surface and no state it does not already have.
  - `[false]` `reject` **BH6: no tests for empty `chargeDays` / multi-contract `allocations`** — the empty-day path is structurally pinned: `arrearsOf` with no days returns 0/0/null and `riskOf(0)` returns LANCAR, all asserted. The multi-contract case is BH1: now covered by the filter plus a new test.
  - `[low]` `patch` **BH7: negative-input coverage stops at `riskOf(-1)`; the `else -> KRITIS` branch has no named pin above 7** — 7+ is documented as a band and no test asserts `riskOf(8)` or `riskOf(100)`. One-line assert added to the `seven open days is KRITIS` test; no new surface.
  - `[false]` `reject` **BH8: `ArrearsEntry` has no `@throws` for the `amount`/`days` disagreement** — a disagreement is a valid, documented value of the object (a day open while the amount absorbs cross-period surplus), not an exception condition. No exception can be thrown for a value.
  - `[low]` `patch` **BH9: ordering tests hand-build `ArrearsEntry` literals duplicating `250_000L` and the period's first day** — the fixtures are now the test's own constants; the duplication is cosmetic but invites drift. Use `rate` and `days.first()` in those two tests.
  - `[false]` `reject` **BH10: doc is inconsistent about what the caller must pre-group** — `Arrears.kt`'s KDoc says `chargeDays` must be `chargeDaysIn`'s output; the grouping obligation on `allocations` is the caller's by design (the function is a per-contract pure calculation, per the spec's "one pure function that takes the period's charge days, the stored allocations, and the daily rate"). No contradiction: each KDoc documents its own inputs.
  - `[false]` `reject` **BH11: the `nullsLast()` in `inArrearsOrder` is dead code** — it is load-bearing: `inArrearsOrder` is a public extension, and a caller can hand it an `ArrearsEntry` with a positive `amount` and a null `oldestOpenDay` (the data class accepts the combination). Without `nullsLast` the sort would throw on a caller-supplied value; with it, the entry sorts last. Removing it to make the "invariant" explicit would narrow the API for a test-only invariant.
  - `[false]` `reject` **BH12: `InvalidAmountException` tests assert only the type** — the type is the whole contract: `Allocation` and `Payment` both enforce it on construction, and `arrearsOf` calls `requireValid(dailyRate)`. Wrapping in a generic catch is a hypothetical regression, not a defect in the diff.
  - `[false]` `reject` **BH13: `RiskLevel`'s declaration order is now part of the contract** — the thresholds are named per-value, not per-ordinal. Reordering the enum would still be caught by the 0/1/2/3/6/7 tests; there is no `.api` file in this module.
  - `[medium]` `patch` **ECH1: `arrearsOf` does not reject a non-positive `amount` on an `Allocation` row** — a zero or negative row could only enter through a hand-built `Allocation` literal (the constructor's `init` enforces `> 0`, and the allocator's own `Σ == amount` check makes a negative day-row unreachable). The boundary that owns the sum should state the invariant it relies on, so the row loop now documents it and a new test pins the constructor's rejection. No public surface added.
  - `[maybe-false]` `defer` **ECH2: duplicate `chargeDays` would over-count `open.size`** — same class as BH5: the precondition makes it unreachable from the named caller; settled by a hand-built list with a repeated entry. Deferred with BH5 as one precondition-trust entry.
  - `[maybe-false]` `defer` **ECH3: `chargeDays.size × dailyRate` overflows for extreme inputs** — 1000 days at 9×10¹⁸ rupiah is ~9×10²¹, below `Long.MAX_VALUE`; the trigger needs a list and rate no period can produce. Deferred: no overflow guard is warranted against an unreachable input.
  - `[false]` `reject` **ECH4: an `ArrearsEntry` with `amount > 0` and `oldestOpenDay == null` reaches `nullsLast` in `inArrearsOrder`** — identical to BH11: `nullsLast` is the guard that keeps that caller-supplied value from throwing. The "hidden coupling" is the guard, not a defect.
  - `[false]` `reject` **VG1: `AllocationEngine.kt:15` KDoc misstates the day-order check's consequence** — verification-gap filed it as a doc contradiction; the KDoc text and the `check` at line 74 agree that a repeated day would "let one payment write two rows for the same `(paymentId, chargeDate)`." The reviewer's claim that the file's own code and test contradict the KDoc was checked against `AllocationEngine.kt:74` and `AllocationEngineTest.kt`'s repeated-day test — they agree. The doc's "write" is a loose word for "emit into the returned list," not a claim about the database. Pre-existing, not a regression this diff introduced; no action taken.
  - `[false]` `reject` **IA1: R5 (ended contract stays in the active list until settled) is delegated entirely to the `chargeDays` argument** — the delegation is the spec's design: "The day list arrives already filtered by Story 1.2 (`chargeDaysIn`, capped at `asOf`). This story does not re-filter by date or by `endDate`; an ended contract is passed in like any other." An ended contract's open days flow into `inArrearsOrder` the same as an active one's; the `amount > 0` filter is the only gate. This is a known, recorded design choice, not a defect the diff can close.

## Verification

**Commands:**
- `gradlew.bat :domain:test --tests "*ArrearsTest*"` -- expected: every matrix row and boundary asserted and passing
- `gradlew.bat :domain:test --rerun-tasks` with `ANDROID_HOME` and `ANDROID_SDK_ROOT` cleared -- expected: BUILD SUCCESSFUL, no "SDK location not found"

## Auto Run Result

Status: done

**Summary.** Story 1.4 gives FR-11 its engine: `arrearsOf` counts the charge days still open from the stored allocation rows (a day is closed only when its rows sum to the rate), maps the count to a `RiskLevel`, and gives `inArrearsOrder` the Tunggakan-list order — amount descending, then oldest open day. The figure is what FR-12 sends over WhatsApp, so it is derived from the rows, never from `ceil(amount ÷ rate)`.

**Files changed:**
- `domain/src/main/kotlin/net/klinik/rental/domain/calculate/Arrears.kt` -- new: `ArrearsEntry`, `RiskLevel`, `arrearsOf`, `riskOf`, `inArrearsOrder`, with the caller-grouping precondition documented.
- `domain/src/test/kotlin/net/klinik/rental/domain/calculate/ArrearsTest.kt` -- new: one test per matrix row, the risk boundaries 0/1/2/3/6/7/8, the ordering rules, and the constructor-level amount rejection.

**Review findings (19 reported: high 0, medium 1, low 2, false 12, maybe-false 4).** Patches: the `riskOf` band now has a pin at 8 (`BH7`), the ordering tests reuse the class's own fixtures instead of duplicating literals (`BH9`), and the row loop states the `amount > 0` invariant it relies on, pinned by a new test (`ECH1`). Deferred: a duplicated day in a hand-built `chargeDays` list would under-count `days` (`BH5`, unverified, unreachable through `chargeDaysIn`), and the `size × rate` product overflows only for inputs no period can produce (`ECH3`, unverified). Every `false` row was rejected at its cited line; the full record is in the Review Triage Log.

**Verification.** `gradlew.bat :domain:test --rerun-tasks` with `ANDROID_HOME` and `ANDROID_SDK_ROOT` cleared: BUILD SUCCESSFUL, 18 `ArrearsTest` cases plus the 42 from Stories 1.1–1.3, 0 failures. I re-ran the suite after the review patches rather than trusting the implementer's report, and read the staged diff of `domain/` to confirm no `require` was added on a field `Allocation` does not have.

**Residual risks.**
1. **The `chargeDays` precondition is documented, not enforced.** `arrearsOf` trusts its list the way `allocatePayments` does, and a duplicated or out-of-order day is only caught when a caller hands one in. `chargeDaysIn` cannot produce either today.
2. **Per-contract grouping is the caller's job.** `Allocation` deliberately carries no `contractId` (Story 1.3), so `arrearsOf`'s contractId argument names the rows' owner without proving it. Epic 3 owns the repository that assembles the per-contract lists.
3. **`settledThroughDate` (FR-9b) is not applied here.** Story 1.5 owns the subtraction from the day list before `arrearsOf` sees it; an unfiltered list would make a settled contract look overdue.
4. **`endDate` is not honoured (inherited from Story 1.2, owned by Story 2.7).** A contract ending mid-period still counts its open days through the list it is passed.
