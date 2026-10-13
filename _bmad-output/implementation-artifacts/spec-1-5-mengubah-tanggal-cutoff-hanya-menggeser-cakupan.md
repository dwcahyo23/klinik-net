---
title: 'Story 1.5: Mengubah Tanggal cutoff hanya menggeser cakupan'
type: 'feature'
created: '2026-10-13'
baseline_revision: 877c9ff4cedef38034707dcf128306e26155c954
status: 'done'
baseline_revision: 877c9ff4cedef38034707dcf128306e26155c954
review_loop_iteration: 0
followup_review_recommended: false
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md'
warnings: []
deferred: []
---

<intent-contract>

## Intent

**Problem:** Changing `cutoffDay` must only shift which days count — FR-15a — and no allocation row may be deleted or rewritten. The `domain/` module has no test pinning that property, and no concept of `settledThroughDate` (FR-9b) exists yet: days closed by settlement must be treated as already paid without any allocation row being written for them.

**Approach:** One pure function `cutoffShift` in `domain/calculate/` that takes the day lists under the old and new cutoffs plus the same stored rows, and returns the before/after `ArrearsEntry` pair — proving by construction that a cutoff change moves the range, not the rows. A second pure function `settledDays` closes every day at or before `settledThroughDate` from the list, and `arrearsOf` on that narrowed list returns zero for a fully settled period without a single row written.

## Boundaries & Constraints

**Always:**
- Changing `cutoffDay` changes only which `chargeDate` values enter the computation. The stored rows themselves — every row, including `chargeDate = null` — are re-read, never deleted, rewritten, or re-derived. That is the owner's decision: digeser, bukan diturunkan ulang.
- A row whose `chargeDate` falls outside the new period's day list is ignored by `arrearsOf` for that period (the `listed` set already does this); the row still exists and counts again when the period that contains that day is next computed.
- `settledThroughDate` (FR-9b) closes every charge day at or before it. No allocation row is written for it — settlement is a field on the contract, not a fake Setoran.
- An empty post-settlement day list is indistinguishable, to `arrearsOf`, from a fully paid period: 0 days, 0 rupiah, LANCAR.
- `settledThroughDate` before the period's start closes nothing in that period.
- A contract that is settled through a date and also carries open days after that date is computed exactly like an unsettled one — `endDate` plays no role here (Story 2.7).
- All dates flow through the `Clock` abstraction; no hardcoded date in production code.

**Never:**
- No Room, Android, or new dependency. `domain/` stays pure JVM.
- No re-derivation of the stored rows when the cutoff changes: the shift is a new *range* over the *same* rows, and `allocatePayments` must not be called a second time on the same days as part of it.
- No `creditBalance` field or any other stored number that survives a cutoff change by being re-persisted.
- No `endDate` filtering in any function this story adds.

## I/O & Edge-Case Matrix

All rows assume the `verify-fr10.md` fixture: period A = 25 Feb → 24 Mar (cutoff 25, 28 charge days, 7-day schedule starting 2026-01-01), `asOf` = 2026-03-24, rate Rp 250.000. Period B under cutoff 15 with the same `today` = 2026-03-24 is 15 Mar → 14 Apr.

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Day re-enters the period | Cutoff 25→15, `today` = 2026-03-24: period A = 25 Feb–24 Mar (28 days), period B = 15 Mar–14 Apr (31 days). Stored rows: one full row on 20 Mar. | `before` (A): 20 Mar in A's list, so it is closed; 27 open days, 6.750.000. `after` (B): 20 Mar in B's list, so it still closes; 30 open days, 7.500.000. The same row list feeds both entries; no row is produced inside `cutoffShift`. | No error expected |
| Day exits the period | Cutoff 15→1, `today` = 2026-03-24: period B = 15 Mar–14 Apr (31 days, `before`), period C = 1 Mar–31 Mar (31 days, `after`). Stored rows: one full row on 14 Apr — the last day of B. | `before` (B): 14 Apr is in B's list, so it is closed; 30 open days, 7.500.000. `after` (C): 14 Apr is not in C's list (C ends 31 Mar), so it is not closed; 31 open days, 7.750.000. The row on 14 Apr remains in the row list, unchanged, and counts again when a period containing 14 Apr is next computed. | No error expected |
| Credit row survives a shift | `allocatePayments` over A with one payment of 20.000.000 → 28 full rows + one `chargeDate = null` row of 13.000.000 (verify case 4). Cutoff 25→15. | `cutoffShift`: B's 31 days all open, amount 7.750.000; the null row is not counted (its day is not in B) but remains in the row list; the 13.000.000 is what closes 51 days of the next period after B. | No error expected |
| No-op shift | Same rows; cutoff 25→25. | `before` and `after` are the identical `ArrearsEntry`. | No error expected |
| Settlement closes the whole period | `settledDays(A.days, 2026-03-24)` → empty list. | `arrearsOf` on that list: 0 days, 0 rupiah, LANCAR. No allocation row written by settlement. | No error expected |
| Settlement closes part of the period | `settledDays(A.days, 2026-03-10)` → days 11 Mar–24 Mar (14 days). | `arrearsOf` on the narrowed list: 14 open days, 3.500.000, oldest open 11 Mar. The closed days 25 Feb–10 Mar contribute nothing to the amount and no rows were written for them. | No error expected |
| Settlement boundary, at or before | `settledDays(A.days, 2026-02-25)` (period start). | 25 Feb is closed ("at or before" includes the boundary day); 27 days open. | No error expected |
| Settlement before the period | `settledDays(A.days, 2026-02-20)`. | Full list returned unchanged; 28 open days. Settlement closes nothing here. | No error expected |
| No settlement | `settledDays(A.days, null)`. | Full list returned unchanged. | No error expected |
| Settled + rows agree | Settlement 24 Mar, period A, plus full rows for all 28 days from `allocatePayments`. | `arrearsOf` on the empty post-settlement list: 0 days, 0 rupiah, LANCAR — rows and settlement agree, no double count. | No error expected |
| Settled, open days remain | Settlement 10 Mar, period A, plus rows that close 11 Mar–15 Mar. | `arrearsOf` on `settledDays` (11 Mar–24 Mar, 14 days) with those rows: 9 open days, 2.250.000. Settlement and allocation composes; no row written by the settlement itself. | No error expected |

</intent-contract>

## Code Map

- `domain/src/main/kotlin/net/klinik/rental/domain/calculate/CutoffPeriod.kt` -- `CutoffPeriod.containing(cutoffDay, today)` builds the period for a given cutoff day. **Read-only.**
- `domain/src/main/kotlin/net/klinik/rental/domain/calculate/ChargeDays.kt` -- `chargeDaysIn(period, schedule, asOf)`; `ChargeSchedule(contractStart, restDay, serviceExceptions)`. **Read-only.**
- `domain/src/main/kotlin/net/klinik/rental/domain/calculate/AllocationEngine.kt` -- `allocatePayments(payments, chargeDays, dailyRate, asOf)` produces the stored rows. Used by tests to build the rows `cutoffShift` reads; never called *by* `cutoffShift` itself — the rows are the input, not a re-derivation. **Read-only.**
- `domain/src/main/kotlin/net/klinik/rental/domain/calculate/Arrears.kt` -- `arrearsOf(contractId, chargeDays, allocations, dailyRate)`, `ArrearsEntry`, `RiskLevel`, `riskOf`. **Read-only.**
- `domain/src/main/kotlin/net/klinik/rental/domain/model/Allocation.kt` -- `Allocation(paymentId, chargeDate?, amount)`; the `null` day is the credit row. **Read-only.**
- `domain/src/main/kotlin/net/klinik/rental/domain/model/Payment.kt` -- `Payment(id, date, amount)`. **Read-only.**
- `domain/src/test/kotlin/net/klinik/rental/domain/calculate/ArrearsTest.kt` -- naming to imitate (backtick Indonesian names, JUnit 4, one rule per test) and fixtures: `period` (25 Feb–24 Mar), `asOf`, `rate = 250_000L`, `days`, `row(day, amount)`.
- `domain/src/test/kotlin/net/klinik/rental/domain/calculate/CutoffPeriodTest.kt` -- period boundary expectations; reuse for the cutoff 25→15 and 15→1 range assertions.
- `domain/src/test/kotlin/net/klinik/rental/domain/calculate/AllocationEngineTest.kt` -- the "payment 20.000.000 → 28 full rows + one null row of 13.000.000" fixture to reuse for the credit-survives-shift test.
- `_bmad-output/planning-artifacts/epics.md:485-510` -- Story 1.5 AC, the FR-9b row, and the "digeser, bukan diturunkan ulang" decision; `epics.md:1642` is the decision's own record.
- `_bmad-output/planning-artifacts/prds/prd-dev.klinik.net-2026-10-13/verify-fr10.md` -- case 4: the 13.000.000 credit row must survive the period change.

**To create:**
- `domain/src/main/kotlin/net/klinik/rental/domain/calculate/CutoffShift.kt` -- `cutoffShift`
- `domain/src/main/kotlin/net/klinik/rental/domain/calculate/Settlement.kt` -- `settledDays`
- `domain/src/test/kotlin/net/klinik/rental/domain/calculate/CutoffShiftTest.kt`
- `domain/src/test/kotlin/net/klinik/rental/domain/calculate/SettlementTest.kt`

## Tasks & Acceptance

**Execution:**
- `domain/src/main/kotlin/net/klinik/rental/domain/calculate/CutoffShift.kt` -- add `data class CutoffShiftResult(val before: ArrearsEntry, val after: ArrearsEntry)` and `fun cutoffShift(contractId, oldChargeDays, newChargeDays, allocations, dailyRate): CutoffShiftResult`. `before` = `arrearsOf(contractId, oldChargeDays, allocations, dailyRate)`, `after` = the same with `newChargeDays` — one shared `allocations` list, no row ever produced inside. KDoc: FR-15a's shift decision and the `verify-fr10` case-4 credit-survival guarantee, naming `allocatePayments` as the function that may *produce* the rows but is deliberately not called here.
- `domain/src/main/kotlin/net/klinik/rental/domain/calculate/Settlement.kt` -- add `fun settledDays(chargeDays: List<LocalDate>, settledThroughDate: LocalDate?): List<LocalDate>` returning `settledThroughDate == null` ? the input as-is : `chargeDays.filter { it.isAfter(settledThroughDate) }`. KDoc: FR-9b — a settled day is a closed day with no allocation row; an empty result must read as a fully paid period in `arrearsOf`, and the function is pure list narrowing, writing nothing.
- `domain/src/test/kotlin/net/klinik/rental/domain/calculate/CutoffShiftTest.kt` -- one test per `cutoffShift` matrix row (day re-enters, day exits, credit row survives, no-op shift), building the rows once with `allocatePayments` and passing the identical list to `cutoffShift`, asserting the `before`/`after` `days` and `amount` and that the specific day row (20 Mar for re-enter, 14 Apr for exit) is present in the shared input list.
- `domain/src/test/kotlin/net/klinik/rental/domain/calculate/SettlementTest.kt` -- `settledDays` boundary tests (full period, partial, at-period-start, before-period, `null`), then `arrearsOf` over the narrowed list for the settled+rows-compose and settled-with-rows-agree rows.

**Acceptance Criteria:**
- Given a contract with stored allocation rows and a cutoff computed over period A, when the cutoff changes to a new day and `cutoffShift` is called with period A's and period B's day lists, then both entries are computed from the same row list and no row is deleted, rewritten, or re-derived by the call.
- Given a row whose `chargeDate` falls outside the new period's day list, when the new period is computed, then that row is not counted for the new period but still exists in the row list and counts again when the period containing that day is next computed.
- Given a `chargeDate = null` row from an overpayment, when the cutoff changes and the next period is computed, then the null row is not deleted and is not counted in the new period while its day is outside the list.
- Given a contract with `settledThroughDate` at or before every charge day, when `settledDays` is called, then the list is empty and `arrearsOf` on it returns 0 days, 0 rupiah, LANCAR — without any allocation row written for it.
- Given a contract with `settledThroughDate` mid-period and allocation rows that close days after it, when `arrearsOf` is called over `settledDays` output, then only the days after `settledThroughDate` count as open, the rows on the later days still close them, and no settlement-written row appears.

## Spec Change Log

- 2026-10-13 (review pass 1): the I/O matrix's `cutoffShift` rows were written against an *uncapped* period B (31 days). `chargeDaysIn` caps every list at `asOf` = 2026-03-24, so B's day list is 15–24 Mar = 10 days, and C's is 1–24 Mar = 24 days. The matrix's "30 open / 7.500.000" and "31 open / 7.750.000" figures are unreachable in the actual engine; the tests assert the capped figures instead (after = 10 open / 2.500.000 for the 25→15 shift; after = 24 open / 6.000.000 for the 25→1 exit). The "day re-enters" row's named day (20 Mar) is now pinned by its own test at the capped numbers (before 27 open, after 9 open). Known-bad state avoided: a maintainer following the uncapped matrix would write asserts that no engine can pass.

## Review Triage Log

### 2026-10-13 — Review pass 1

Three layers: Blind Hunter 13 findings, Edge Case Hunter 8, Intent Alignment 6 checks. Verdicts below are my own verification at the cited line.

- verdicts: 21 findings — high 2, medium 3, low 5, false 7, maybe-false 4
- findings:
  - `[high→patch]` **F1: "day re-enters" matrix row (20 Mar) unpinned** — confirmed: no test used a 20 Mar row; the property was only covered in spirit by a 24 Mar row. Patched: `baris 20 Mar tetap menutup hari saat cutoff bergeser ke 15` added, asserting before = 27/6.750.000, after = 9/2.250.000 (capped B = 10 days).
  - `[high→patch]` **F2: "day exits" matrix row (14 Apr) unreachable** — the named 14 Apr day can never enter `daysB` because `chargeDaysIn` caps at `asOf` = 24 Mar; the spec row was written uncapped. Settled by a variant the capped fixture *can* reach: `baris 25 Feb keluar dari hitungan B saat cutoff berubah dari 25 ke 1` — a row inside A's range but outside C's range (C starts 1 Mar) is ignored by C, not deleted, and persists in the shared list. The uncapped 14 Apr figure is corrected in the Spec Change Log, not "fixed" in code (unreachable is unreachable).
  - `[medium→patch]` **F3: `cutoffShift` KDoc over-claims the null row "counts again"** — confirmed: this function reads, it does not re-credit; re-crediting is the allocator's job in the next period. KDoc reworded to say exactly that.
  - `[medium→patch]` **F4: `settledDays` filter path silently allocates a new list** — confirmed: only the `null` path returns the input instance; any non-null date (even one that closes nothing) returns a `filter` copy. KDoc now states the non-null path always returns a fresh list.
  - `[medium→patch]` **F5: test name `baris 24 Mar ... dari 25 ke 1` mislabels the shift** — the test shifts 25→15 (daysB is cutoff-15), not 25→1. Renamed to `... dari 25 ke 15`.
  - `[low]` **F6: diff snapshot lags the worktree** — the 5th `CutoffShiftTest` case was added between snapshot and review; no code change, noted so the reviewer judges the on-disk file.
  - `[low→reject]` **F7: `settledDays` rest-day boundary** — confirmed correct by walk; pinned by a new test rather than a patch: `settledThroughDate di hari libur sama dengan hari sebelumnya` (6-day schedule, a Sunday marker == the preceding charge day's marker).
  - `[low]` **F8: no new money overflow** — confirmed: neither function multiplies; the pre-existing `chargeDays.size.toLong() * dailyRate` is the only product and is unchanged. No action.
  - `[low]` **F9: settlement + row double-count** — confirmed prevented: settlement narrows the list first, so a row on a settled day is ignored by `arrearsOf`'s `listed` guard; the agreeing-zero test pins it. No action.
  - `[low→defer]` **F10: hand-built day list reaching `cutoffShift`/`arrearsOf`** — the precondition belongs to `arrearsOf` (Story 1.4 documented it); `cutoffShift` adds no check and none is warranted. Deferred.
  - `[low]` **F11: credit-survives test diverges from matrix** — same root cause as F2 (capped B is fully closed, not "all open"); the null-row survival assertion is the load-bearing one and is correct. No action beyond the Spec Change Log.
  - `[false→reject]` **F12: no-op test only checks entry equality** — that *is* the structural property; the day/amount values are already pinned in every other test, and a symmetric value bug in `arrearsOf` would surface there, not here. No patch.
  - `[edge→patch]` **A: marker on a non-charge day unpinned** — same as F7; pinned by the new 6-day-schedule test.
  - `[edge]` **B: marker −1 day / −365 days** — partially pinned already (`sebelum periode` = 5 days before); the −1-day case collapses to the same filter outcome. No new test warranted.
  - `[edge]` **C: same list instance for old+new** — structurally pinned by the pure-function argument; `ArrearsEntry` value equality means no identity trap. No action.
  - `[edge→patch]` **D: `cutoffShift` with empty allocations** — not previously exercised on the wrapper. Patched: `geser tanpa baris alokasi sama sekali hanya menggeser rentang` (before 28/7.000.000, after 10/2.500.000).
  - `[edge→patch]` **E: `cutoffShift` with an empty new day list** — not previously exercised. Patched: `geser ke periode yang harinya semua lewat asOf menghasilkan angka nol` (after = 0 days / 0 rupiah).
  - `[edge→patch]` **F: null credit row + empty day list** — folded into the E test: the null row contributes nothing to the zero figure and persists in the list. No separate test.
  - `[edge]` **G: `asOf` cap visibility** — already documented in `chargeDaysIn`'s KDoc and the asserted fixture sizes; no gap.
  - `[edge]` **H: `settledThroughDate` ↔ `endDate` coupling** — no `endDate` exists in `domain/` yet; `Settlement.kt`'s KDoc explicitly disclaims it (Story 2.7). No coupling surface.
  - `[false→reject]` **IA-1…IA-6 (intent alignment)** — all "Never" bullets verified clean against the diff (no Room/Android/new dependency, no re-derivation, no `creditBalance`, no `endDate` filtering); all five ACs map to a running test; the only real drift was F1/F2, already patched. No further action.

## Design Notes

`cutoffShift` takes the two day lists, not the two cutoff days: the caller (Epic 5's settings screen) already owns `CutoffPeriod.containing` and `chargeDaysIn` and passes the ranges in. Keeping the cutoff-day math out of the function is what makes it provably a range shift — there is no second `allocatePayments` call inside it, so there is no path by which a row could be rewritten, and the "no row deleted" property is structural, not enforced.

`settledDays` returns `chargeDays` as-is when `settledThroughDate` is `null` rather than filtering to the same list, so a caller never has to allocate a new list for the common "no settlement" case and the identity of the list is visible at the call site. Any non-null date — even one that closes nothing — returns a fresh `filter` list; only the `null` path preserves identity.

The `cutoffShift` tests are written against the `asOf`-capped day lists (B = 15–24 Mar = 10 days, C = 1–24 Mar = 24 days) rather than the periods' full calendar spans, because `chargeDaysIn` caps every list at `asOf` and the engine can only ever see the capped lists. The "day re-enters" test uses a 20 Mar row (in both A's and B's capped lists): `before` = 27 open, `after` = 9 open. The "day exits" test uses a 25 Feb row with C: 25 Feb is in A's list but outside C's (C starts 1 Mar), so `before` = 27 open, `after` = 24 open, and the row persists in the shared list uncounted.

## Verification

**Commands:**
- `gradlew.bat :domain:test --tests "*CutoffShiftTest*"` -- expected: every `cutoffShift` matrix row and boundary passing
- `gradlew.bat :domain:test --tests "*SettlementTest*"` -- expected: every `settledDays` boundary and compose case passing
- `gradlew.bat :domain:test --rerun-tasks` with `ANDROID_HOME` and `ANDROID_SDK_ROOT` cleared -- expected: BUILD SUCCESSFUL, all tests pass, no "SDK location not found"

## Auto Run Result

Status: done

**Summary.** Story 1.5 closes FR-15a and the FR-9b half of Epic 1's decision: `cutoffShift` composes two `arrearsOf` reads over one shared row list, so a cutoff change moves the day range and nothing else — the "dipgeser, bukan diturunkan ulang" property is structural (there is no `allocatePayments` call inside it), and the `verify-fr10` case-4 credit row survives every shift. `settledDays` implements FR-9b as pure list narrowing: a `settledThroughDate` closes days at or before it with no allocation row written, so an empty post-settlement list reads in `arrearsOf` as a fully paid period (0 days, 0 rupiah, LANCAR) — the row-settled and row-paused paths agree.

**Files changed:**
- `domain/src/main/kotlin/net/klinik/rental/domain/calculate/CutoffShift.kt` -- new: `CutoffShiftResult`, `cutoffShift`, with the shift decision and the case-4 credit-survival guarantee documented; re-crediting explicitly attributed to the allocator, not this function.
- `domain/src/main/kotlin/net/klinik/rental/domain/calculate/Settlement.kt` -- new: `settledDays`, with the FR-9b "closed day, no row" rule, the `null`-vs-non-null identity behavior, and the Story-2.7 `endDate` disclaimer.
- `domain/src/test/kotlin/net/klinik/rental/domain/calculate/CutoffShiftTest.kt` -- new: 8 tests — the 25→15 shift, the 24 Mar in-both-ranges row, the 20 Mar re-enter row, the credit-row survival, the 25 Feb exit row, empty allocations, an empty new day list with a null credit row, and the no-op shift.
- `domain/src/test/kotlin/net/klinik/rental/domain/calculate/SettlementTest.kt` -- new: 8 tests — the full/partial/boundary/before-period/no-settlement rows, the settlement+rows compose case, the 6-day-schedule rest-day marker, and the settled+full-rows agree case.

**Review findings (21 reported: high 2, medium 3, low 5, false 7, maybe-false 4).** Patches: the two unpinned matrix properties got their own tests at the `asOf`-capped numbers (`baris 20 Mar`, `geser tanpa baris alokasi`, `geser ke periode ... lewat asOf`); the `settledDays` rest-day boundary got a 6-day-schedule test; `CutoffShift.kt`'s KDoc no longer claims the null row "counts again" in this function; `Settlement.kt`'s KDoc states the non-null path always copies; the mislabeled test name was corrected to `dari 25 ke 15`. Deferred: the hand-built-day-list precondition of `arrearsOf` (F10, owned by Story 1.4's contract). The spec's matrix numbers for an uncapped 31-day period B are corrected in the Spec Change Log — the engine can only see the capped lists.

**Verification.** `gradlew.bat :domain:test --rerun-tasks` with `ANDROID_HOME` and `ANDROID_SDK_ROOT` cleared: BUILD SUCCESSFUL, 111 tests, 0 failures (AllocationEngineTest 35, ArrearsTest 18, ChargeDaysTest 20, CutoffPeriodTest 19, SmokeTest 3 = 95 pre-existing, plus 16 new in the two classes this story adds). Re-ran after the review patches rather than trusting the implementer's report, and re-read the final diff against the spec before marking done.

**Residual risks.**
1. **The `asOf` cap is a fixture property, not a domain invariant.** The tests' "day exits" case uses 25 Feb ∉ C because that is the reachable variant; a period whose day genuinely falls outside the *next* period's uncapped span (the spec's 14 Apr example) is only exercised through the cap, so a future change to `chargeDaysIn`'s cap could silently alter which rows these tests pin.
2. **`cutoffShift` trusts its two day lists.** As with `allocatePayments` and `arrearsOf` (Stories 1.3/1.4), the precondition "both lists are `chargeDaysIn` output" is documented, not enforced.
3. **`settledThroughDate` is not yet a contract field.** It has no type in `domain/` yet — this story supplies the narrowing rule only; the field and its Room column arrive with the contract entity (Epic 2), and the FR-9b button that sets it is Epic 3's.
