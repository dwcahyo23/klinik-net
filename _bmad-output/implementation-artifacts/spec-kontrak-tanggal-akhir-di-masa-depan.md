---
title: 'Ending a contract on a future date frees the car today'
type: 'bugfix'
created: '2026-10-20'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: 'cf016430cccb951ec653f9c881741357ac386447'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/spec-2-7-mengakhiri-kontrak-sewa.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `Contract.isOpen` is `endDate == null` (`Contract.kt:143`), and every "is this car
rented?" query asks `endDate IS NULL` (`VehicleDao.kt:90,102`). So writing *any* end date — including
one six weeks out — ends the contract on the spot: the car reads `Tersedia` today, the renter's slot
frees today, and charge days stop today, for a rental that is still running. `endContract` refuses
only `endDate < startDate` (`ContractRepository.kt:243`); a date in the future passes every guard.
The sheet even invites it: `EndContractFormState.endDate`'s KDoc accepts any date and binds no
`selectableDates` (`KontrakUiState.kt:664`, `ExceptionFormSheet.kt:529`).

**Approach:** Refuse an end date after today, the same way a date before the start date is already
refused — with a named exception the sheet renders on the date field. "Today" arrives as an `asOf`
parameter, so the repository keeps no clock of its own and the test pins the day.

## Boundaries & Constraints

**Always:**
- **The refusal names the day it compared against.** A message that says only "tanggal tidak boleh
  di masa depan" leaves the owner to work out which day the app thinks it is.
- **A past date stays accepted.** A contract that ended before this app existed is real, and
  `spec-2-7`'s own KDoc says so. Only strictly-after-today is refused.
- **Nothing is written when it throws.** Same as the before-start refusal: the guard runs before
  `dao.update`, inside the existing `inTransaction`.
- **`asOf` is a parameter, never a `LocalDate.now()` inside the repository** — AD-8's rule, which
  `KontrakViewModel` already follows at `:1042`.

**Never:**
- Do not make `isOpen` date-aware, add a status column, or change any `endDate IS NULL` query. That
  is the schedule-a-future-end design, rejected at the gate below.
- Do not clamp a future date down to today. Clamping ends a rental on a day the owner never chose,
  which is the exact failure `endContract`'s own comment at `:240-242` forbids.
- Do not touch `ContractRepository.open()`'s missing `isOpen` guard — that is a separate deferred
  item (`spec-2-5`) and changes a different method's contract.

**Decided at the gate (2026-10-20):**
- **Reject, do not schedule.** Storing a future end date and keeping the car occupied until it
  arrives would need `isOpen` to take a date, which rewrites both active `UNIQUE` indexes, every
  `endDate IS NULL` query and the backup schema — for a case the owner can express by ending the
  contract on the day it actually ends.
- **`asOf` parameter, not a `Clock` constructor argument.** `ContractRepository` stays a
  three-argument class, so `MainActivity`'s five construction sites and the tests' thirteen are
  untouched; only `endContract`'s callers change, and the ViewModel already holds the clock.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| TODAY | open contract, `endDate == asOf` | ends; car and renter freed | N/A |
| PAST | open contract, `endDate < asOf` | ends; car and renter freed | N/A |
| FUTURE | open contract, `endDate == asOf + 1 day` | refused; row unchanged; car still held | `ContractEndInFutureException(asOf)` |
| FUTURE_FAR | open contract, `endDate == asOf + 6 weeks` | same refusal | same |
| BEFORE_START | `endDate < startDate` (unchanged) | refused | `ContractEndBeforeStartException` |
| ALREADY_ENDED | contract already has an end date | refused | `ContractNotOpenException` |
| ORDER | future **and** before start | `ContractEndBeforeStartException` first | the start-date message names a figure the owner can act on; "too far ahead" would be a second, confusing complaint |

</frozen-after-approval>

## Code Map

- `app/src/main/kotlin/net/klinik/rental/data/repository/ContractRepository.kt:234-278` — `endContract`.
  The guard goes beside the existing before-start check at `:243`, before `dao.update`. Update its
  `@throws` block and the class KDoc's exception list at `:36`.
- `domain/src/main/kotlin/net/klinik/rental/domain/validation/ContractEndBeforeStartException.kt` — the
  pattern to copy: `IllegalStateException`, two `val`s, one message sentence.
- `app/src/main/kotlin/net/klinik/rental/ui/screens/kontrak/KontrakViewModel.kt:919-970` — the caller.
  Add a `catch (ContractEndInFutureException)` beside the before-start one at `:946`; it writes
  `dateError` on the live sheet. `clock` is at `:101`; `asOf = LocalDate.now(clock)` is the idiom at
  `:1042`.
- `app/src/main/kotlin/net/klinik/rental/ui/strings/RStrings.kt:347` — `kontrakErrorContractEndBeforeStart`,
  the shape to mirror (takes a `LocalDate`, formats it).
- `app/src/main/res/values/strings.xml:751` — `kontrak_error_contract_end_before_start`, the string
  to mirror. Indonesian, `%1$s` is the formatted date.
- `app/src/test/kotlin/net/klinik/rental/ui/screens/kontrak/ContractRepositoryTest.kt:1019-1223` — 12
  `endContract` calls; `END_DAY` is `2026-10-20` at `:1545`.
- `app/src/test/.../beranda/BerandaKpiTest.kt:174`, `.../setoran/SettleContractTest.kt:408,432`,
  `.../tunggakan/TunggakanArrearsTest.kt:167` — four more callers, all with dates at or before their
  own fixed clock.

## Tasks & Acceptance

**Execution:**
- [x] `domain/.../validation/ContractEndInFutureException.kt` — new; `contractId`, `asOf`, message
      naming the day — the refusal must be actionable without the owner guessing today's date.
- [x] `app/.../data/repository/ContractRepository.kt` — `endContract` gains `asOf: LocalDate`, and
      after the before-start check refuses `endDate.isAfter(asOf)` — ordered second so the start-date
      message wins when both apply (I/O matrix `ORDER`).
- [x] `app/.../ui/screens/kontrak/KontrakViewModel.kt` — pass `asOf = LocalDate.now(clock)`, and catch
      the new exception into `dateError` on the live sheet, mirroring `:951-957`.
- [x] `app/.../ui/strings/RStrings.kt` + `res/values/strings.xml` — `kontrakErrorContractEndInFuture(asOf)`.
- [x] `app/src/test/.../ContractRepositoryTest.kt` — add the `FUTURE` and `FUTURE_FAR` rows; update the
      12 existing calls with `asOf = END_DAY` (or later where the case needs it).
- [x] `app/src/test/.../{BerandaKpiTest,SettleContractTest,TunggakanArrearsTest}.kt` — pass `asOf`
      matching each test's own clock date.
- [x] `app/src/test/.../KontrakViewModelTest.kt` — one case: a future date shows the error on the
      date field and the sheet stays open with the contract still running.

**Acceptance Criteria:**
- Given an open contract and an end date one day after today, when the owner confirms, then the
  contract stays open, the car still reads `Tersedia: tidak`, and the date field carries a message
  naming today.
- Given an open contract and an end date of today, when the owner confirms, then the contract ends.
- Given an open contract and an end date before its start, when the owner confirms, then the message
  is still the start-date one.
- Given the app builds, then `:domain:test`, `:app:testDebugUnitTest` and `:app:assembleDebug` pass.

## Implementation Notes

**A matrix row the plan missed — `NOT_STARTED`.** `open()` guards only the rate, so a contract may
be saved with a start date in the future. Under `isOpen = endDate == null` that contract already
holds its car, and the new guard makes it un-endable: below the start it is refused by
`BEFORE_START`, at or after the start it is still ahead of today and refused by `FUTURE`. Both
refusals are truthful and both name a figure; the outcome — a booking cannot be cancelled before it
begins — is the consequence of `isOpen` being a null check, not of this change. Pinned by
`a contract that has not started refuses every end date`, and documented in `endContract`'s comment.
Cancelling a not-yet-started booking is a separate feature, not in this spec.

**`SettleContractTest`'s two ends now pass `asOf`.** One uses the suite's own `asOf`; the other
(`ending a settled contract preserves the settlement`) passes its end date as `asOf`, because its
subject is the settlement surviving an end, not the future-date rule.

**Existing ViewModel fixtures moved from 20 Oct to 17 Oct.** Three tests ended contracts on 20 Oct
while the suite pins its clock at 17 Oct; the guard correctly refuses that now, so the fixture dates
follow the clock rather than the guard being weakened.

## Spec Change Log

## Review Triage Log

Three layers ran (blind hunter, edge-case hunter, verification-gap). Every claim below was checked
against the file and line it cites; `carried` marks a verdict already settled in an earlier pass.

| # | Finding | Verdict | Evidence |
|---|---------|---------|----------|
| 1 | New `catch` writes `dateError` onto whatever sheet is live, with no identity check; the success path two lines above guards on `live === current` | **medium** → patch | `KontrakViewModel.kt:941` guards, `:966` does not. `closeEndContract`/`openEndContract` are unguarded by `savingException`, so dismissing A and opening B mid-write lands A's refusal on B. The `ContractEndBeforeStartException` catch has the same omission, so both get the guard. |
| 2 | Exception message asserts `asOf` "is today" while the repository calls it "the day the caller says it is" | **medium** → patch | `ContractEndInFutureException.kt:43` vs `ContractRepository.kt:241`. The exception cannot know; every test passes a pinned value. |
| 3 | `@throws` never states which of the two date refusals wins, though matrix `ORDER` promises one | **low** → patch | `ContractRepository.kt:246-252` lists both as independent. Swapping the two `if`s would break an AC with no compiler signal. |
| 4 | `endContract()`'s KDoc documents neither new refusal nor their order | **low** → patch | `KontrakViewModel.kt:904-919`; the ordering rationale lives only in a repository inline comment and a test KDoc. |
| 5 | Refusal formats `asOf` without a year while the field above it renders `date.toString()` | **low** → patch | `Rupiah.kt:148` has no year; `ExceptionFormSheet.kt:515` renders `2027-10-18`. The spec makes naming the day load-bearing, so the two must agree. |
| 6 | `ContractDateField` binds no `selectableDates` with nothing recording that this is deliberate | **low** → patch | `ExceptionFormSheet.kt:491`. Adding `max(today)` would silently break the START date field, which shares the component and whose `open()` accepts a future start. The hazard is recorded nowhere at the picker. |
| 7 | `spec-2-7:585-587` points at a test this diff deleted; its `deferred:` entry stays open | **medium** → patch | Verified on disk: the old name `a future end date ends the contract immediately, which is the known consequence` appears only in the removed hunk. A later sweep would re-open closed work. |
| 8 | No test dismisses the end sheet while the write is in flight, so neither `else` arm is covered | **medium** → patch | `KontrakViewModelTest.kt:1145` leaves the sheet open throughout; `closeEndContract` appears only at `:1271`, after `advanceUntilIdle()`. `GatedUpdateDao` (`:1287`) already parks the first `update()`. |
| 9 | `END_DAY` is an arbitrary pivot but reads as "today"; `SettleContractTest` mixes two `asOf` conventions | **low** → patch | `KontrakViewModelTest`'s clock is 17 Oct while `ContractRepositoryTest.END_DAY` is 20 Oct — which is why three ViewModel fixtures had to move. Documentation only. |
| 10 | `settleThrough` accepts a settlement date after today — the same failure class, sibling path | **medium** → defer | `ContractRepository.kt:430-438` refuses only `date < startDate`. `SettleContractTest.kt:101,349` pass 24 Oct against `asOf` 17 Oct, so it is pinned working behaviour, not a regression. Untouched by this diff. `spec-3-6` called it unreachable ("the picker only offers past dates"); that is false in code — all three sheets share `ContractDateField`. Recorded in `deferred-work.md`. |
| 11 | `BackupJsonReader` restores `endDate` verbatim, bypassing the new invariant | **medium** → defer | `BackupJsonReader.kt:220` builds a `ContractEntity` directly; `ContractEntity.init` pins the active columns but never compares to today. Pre-existing, outside this spec's scope, no matrix row covers restore. Recorded in `deferred-work.md`. |
| 12 | The new string's `%1$s` placeholder is unpinned | **false** | Deleting the placeholder renders `...hari ini ().`; `KontrakViewModelTest.kt:1164` asserts `contains("17 Okt")` and would fail. The existing assertion already pins it. |
| 13 | A caller could pass a non-today `asOf` and silently invert the rule | **low** → rejected | The only production caller passes `LocalDate.now(clock)` (`KontrakViewModel.kt:940`) and the only other caller sites are tests. A guard on an injected date would guard nothing; the KDoc states the contract. |
| 14 | A not-yet-started contract has no escape hatch, holding its car for the life of the database | **medium** → rejected as in-scope; recorded above | True, and pinned by `a contract that has not started refuses every end date`. It is the consequence of `isOpen` being a null check, not of this change; cancelling a booking is a separate feature. Already in Implementation Notes as `NOT_STARTED`. |

All 9 patches were sent to the step-03 implementation agent; both defers are recorded in
`deferred-work.md`.

## Verification

**Commands:**
- `./gradlew :domain:test :app:testDebugUnitTest :app:assembleDebug` -- expected: SUCCESS
- `git grep -n "endContract(" -- app/src/main app/src/test` -- expected: every call site passes
  `asOf`, and no call site passes `LocalDate.now()` bare.

**Result (run locally, not taken from the implementation agent's report):**
- `:domain:test :app:testDebugUnitTest` — 1364 tests, 0 failures, 0 errors, 0 skipped.
- `:app:assembleDebug` — exit 0.
- Every I/O matrix row confirmed present and passing by name in the JUnit XML reports: `TODAY`
  (`ending frees both the vehicle and the renter for a new contract`), `PAST`
  (`an ended contract stays in the observed list`), `FUTURE` (`an end date after today is refused
  and the row stays open`), `FUTURE_FAR` (`an end date six weeks out is refused the same way`),
  `BEFORE_START`, `ALREADY_ENDED`, `ORDER` (`a future date before the start reports the
  start-date refusal first`), `NOT_STARTED` (`a contract that has not started refuses every end
  date`), plus the ViewModel case `a future end date is refused on the date field naming today`.

**Manual checks (a device is required, NOT RUN — none available):**
- End sheet: pick tomorrow, confirm, then confirm the car still shows as rented in Mobil.

