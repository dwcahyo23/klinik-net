---
title: 'Story 3.6 — Menandai kontrak lunas sampai tanggal tertentu'
type: 'feature'
created: '2026-10-18'
status: 'in-progress'
baseline_revision: '46487d49f198672c0d4ae1b906d058a962ed4d73'
review_loop_iteration: 0
followup_review_recommended: false
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-3-context.md'
warnings: []
deferred: []
---

<intent-contract>

## Intent

**Problem:** A contract that ran before this app was first opened shows every one of its historic charge days as fictional arrears — "28 hari Kritis" with Rp 6–7 million owed. That is the first thing a new owner sees, and it makes the app look broken on day one. FR-7 lets a contract start in the past so such rentals are recordable, but there is no exit: the PRD rules out Excel import and bulk historic entry, so the owner has no way to say *these days were already paid for elsewhere*.

**Approach:** Add a nullable `settledThroughDate` on `CONTRACT`, and a write that sets it in one transaction without touching a single allocation row or creating a Setoran. Every charge-day list this app derives is narrowed through the **existing** `settledDays` function before anything counts it, so settlement reads as "already paid" rather than as a stored fact.

## Boundaries & Constraints

**Always:**
- **The confirmation names everything the action changes**: contract, vehicle, renter, the number of days it closes and the rupiah it removes, in the epic's own shape.
- **A settlement writes no allocation row and creates no Setoran.** It changes a field on the contract and nothing else — the check that "no allocation row is written for it" is a query against a real database, not a claim.
- **Days after the settlement date keep counting normally**, with their stored allocation rows still closing them. Settlement narrows the day list; it never rewrites rows.
- **The boundary is "at or before".** A charge day equal to `settledThroughDate` is closed, which is `settledDays`' own rule — do not re-derive it here.
- **Settling one contract leaves every other contract untouched**, asserted on the other row rather than inferred.
- **The write is one transaction** and it is a single-column update, so there is no multi-row rollback to prove.
- **The owner cannot settle to a date that closes nothing silently**, and a date before the contract's start is refused rather than accepted as a no-op.

**Never:**
- **No per-day representation and no undo.** PRD FR-9b: *"tidak ada representasi per hari, tidak ada jejak audit selain waktu aksi, dan tidak bisa di-undo."* No revision table, no cancel button, no restore.
- **No Setoran row is created to represent the settlement**, and no credit balance is stored — the narrowed day list is the whole mechanism.
- **No `settle` action on a renter.** PRD FR-9b: *"Hanya tersedia untuk Kontrak, bukan untuk Penyewa."*
- **No arrears screen, no arrears figure, no KPI, no risk tier** — those are Story 4.1's. The confirmation computes its own figures through the existing repository read.
- **No automatic settlement.** The owner chooses the date; nothing infers it from payments.
- **No editing an already-settled contract's rate, schedule or exceptions** — Story 2.5/2.6/2.7 own those, and an ended contract stays read-only.
- No second mechanism that quietly stops billing: `Pengecualian Servis` remains the only per-day one.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| SETTLE_FULL | Backdated contract, date after every charge day | `settledThroughDate` stored; **zero** allocation rows written; no Setoran created; arrears reads 0 days / Rp 0 / LANCAR | No error expected |
| SETTLE_PARTIAL | Contract with 17 charge days, settled mid-period | Days at or before the date closed; days after still count and their stored rows still close them | No error expected |
| SETTLE_NO_ROWS | Settlement date with no allocation rows written for it | `allocations.size` unchanged before and after; the closed days contribute nothing to the amount | No error expected |
| SETTLE_OTHER_CONTRACT | Two contracts; settle one | The other row's `settledThroughDate` stays null and its arrears are unchanged | No error expected |
| SETTLE_BEFORE_START | Settlement date earlier than `contract.startDate` | Refused; nothing written | `SettlementBeforeStartException` naming the start date |
| SETTLE_UNKNOWN | `contractId` nobody holds | Refused; nothing written | `UnknownContractException` |
| SETTLE_CONFIRM_FIGURES | Contract at Rp 250.000/day, 28 days, settled 1 Mar | Confirmation names renter, plate, 28 days and Rp 7.000.000 — computed by allocating, not by subtracting | No error expected |
| MIGRATE_7_8 | A v7 database with contracts and allocations | `contract` gains `settledThroughDate`; existing rows read back `null`; every allocation row intact | No error expected |
| SETTLE_PERIOD_ROLLOVER | Settled to 24 Oct, cutoff rolls to 26 Oct | New period's days at or before 24 Oct are still closed — not re-billed | No error expected |

</intent-contract>

## Code Map

- `domain/.../calculate/Settlement.kt:25-31` -- `settledDays(chargeDays, settledThroughDate)`: the FR-9b rule, **already shipped by Story 1.5 and already tested** (`SettlementTest`, 9 cases). Returns the input list *unchanged* when the date is null. This story does not touch it; it wires it in.
- `domain/.../calculate/Settlement.kt` -- **Read-only evidence:** *"a settled day is a closed day with no allocation row written for it: settlement narrows the day list, it never produces rows."*
- `app/.../data/repository/PaymentRepository.kt:250-259` -- **The integration point.** `previewAllocation`'s `chargeDays` is the one place every arrears read derives its day list, so narrowing it here propagates settlement to `currentArrears`, `previewDeposit`, `cancelDeposit` and `correctDeposit` at once. This is why settlement needs no second reader.
- `app/.../data/repository/PaymentRepository.kt:240-242` -- `contractDao.findById(contractId)` returns the stored `ContractEntity`, which is where `settledThroughDate` will be read.
- `app/.../data/db/entity/ContractEntity.kt:125-150` -- No `settledThroughDate` field yet, as Epic 2 designed. Its `init` guard pins the active columns to `endDate`; the new field is **not** part of that pair, so adding it cannot disturb the one-active rule.
- `app/.../data/db/dao/ContractDao.kt:23-26` -- KDoc states the column's absence explicitly; that KDoc becomes stale and is updated.
- `app/.../data/db/KlinikDatabase.kt:68` -- `version = 7`; this story moves it to `8`.
- `app/.../data/db/KlinikDatabase.kt:553-563` -- `MIGRATION_6_7`, the shape to copy: a bare `ALTER TABLE … ADD COLUMN` with **no** row rewrite, because the default already supplies the value.
- `app/.../data/repository/ContractRepository.kt:234-265` -- `endContract`: the write that settles a contract's lifecycle, and the pattern for a single-column contract write inside one transaction.
- `app/.../data/repository/ContractRepository.kt:250-263` -- **Read-only evidence:** `endContract` builds a domain `Contract` and calls `.toEntity()`, which **has no settlement field**. Adding one there would force every contract write to carry it. The settlement write must be `stored.copy(settledThroughDate = …)`.
- `app/.../ui/screens/kontrak/KontrakUiState.kt:656-673` -- `EndContractFormState` and `selectEndDate`: the sheet-state shape to mirror, including `dateError` cleared by the selection that fixes it.
- `app/.../ui/screens/kontrak/ContractFormSheet.kt:300-369` -- `StartDateField` + Material3 `DatePickerDialog`, with the UTC round-trip that `DepositSheetTest` now proves under a west-of-Greenwich zone.
- `app/.../ui/screens/kontrak/ExceptionFormSheet.kt` -- `KnModal`-based impact dialog naming what a removal changes; the confirmation shape for this story.
- `app/.../ui/components/Feedback.kt:500` -- `KnModal`, whose `confirmEnabled` and `onDismissRequest` a one-field confirmation needs.

**Reuse points:** `settledDays` · `ContractRepository.endContract`'s transaction shape · `EndContractFormState` · `KnModal` · `formatTanggalBulan` · `formatRupiah`.

**Gotchas carried over:**
- Every nullable Room column needs `@ColumnInfo(defaultValue = "NULL")` **and** `DEFAULT NULL` in the migration SQL — Room compares recorded defaults and refuses the whole migration when they disagree.
- `ContractDao` and `ServiceExceptionDao` are never exposed to the UI; the repository owns the write.
- `KlinikNetTheme` is `staticCompositionLocalOf`; wrap every Robolectric render.
- One `setContent` per Compose test, and `performSemanticsAction(SemanticsActions.OnClick)` rather than `performClick` — a `ModalBottomSheet` renders in its own window.
- A ViewModel test must **not** pass the database handle: `withTransaction` throws on the main thread under `StandardTestDispatcher`.

## Tasks & Acceptance

**Execution:**

*Data*
- `app/.../data/db/entity/ContractEntity.kt` -- `settledThroughDate: LocalDate?` with `@ColumnInfo(defaultValue = "NULL")`, defaulting to `null`. Outside the `endDate`/active-column guard: settlement is not part of the one-active rule.
- `app/.../data/db/KlinikDatabase.kt` -- `MIGRATION_7_8`: a bare `ALTER TABLE contract ADD COLUMN settledThroughDate TEXT DEFAULT NULL`, no row rewrite, and `version = 8`. Registered in `addMigrations`.
- `app/.../data/repository/PaymentRepository.kt` -- narrow the `chargeDays` from step 3 through `settledDays(chargeDays, contract.settledThroughDate)`. One line, and every arrears read inherits it.
- `app/.../data/repository/ContractRepository.kt` -- `settleThrough(contractId, date)` in one transaction: read the stored row, refuse a date before `startDate`, then `dao.update(stored.copy(settledThroughDate = date))`. **`copy`, not `toEntity()`** -- see the Code Map. `SettlementBeforeStartException` carries the start date so the message can name it.

*UI*
- `app/.../ui/screens/kontrak/KontrakUiState.kt` -- `SettleFormState(contractId, contractLabel, settleDate, dateError)` mirroring `EndContractFormState`, plus `selectSettleDate` and `canSettle`.
- `app/.../ui/screens/kontrak/KontrakViewModel.kt` -- `openSettle(contract)`, `onSettleChange`, `confirmSettle()`, `dismissSettle()`. The impact figures are computed **before** the dialog opens, by reading arrears with and without the settlement -- the same "allocate, never subtract" rule `arrearsAfterCanceling` follows.
- `app/.../ui/screens/kontrak/SettleContractSheet.kt` -- **New**: a `KnModal` naming renter, plate, the day count and the rupiah, over a date field. `confirmEnabled = canSettle`.
- `app/.../ui/shell/NavGraph.kt` + `MainActivity.kt` -- wire the five new callbacks into `Screens.Kontrak`, required not defaulted, per the same rule the other sheets follow.
- `app/src/main/res/values/strings.xml` + `RStrings.kt` -- the epic's copy verbatim, parameterised by the four figures.

*Tests*
- `app/src/test/.../setoran/SettleContractTest.kt` -- **New**: `SETTLE_FULL`, `SETTLE_PARTIAL`, `SETTLE_NO_ROWS`, `SETTLE_OTHER_CONTRACT`, `SETTLE_BEFORE_START`, `SETTLE_UNKNOWN`, `SETTLE_PERIOD_ROLLOVER`, `MIGRATE_7_8`, and the confirmation figures. Against a **real Room database** -- "no allocation row is written" is a schema claim, and a fake DAO could not prove it.
- `app/src/test/.../penyewa/DatabaseMigrationTest.kt` -- the 7→8 step.
- `app/src/test/.../kontrak/SettleContractViewModelTest.kt` -- **New**: the dialog opens before anything is written, the figures name the contract, and a refusal lands somewhere visible.

**Acceptance Criteria:**
- Given a contract with `settledThroughDate`, when arrears are computed, then every charge day at or before it is treated as closed, with **no** allocation row written for it
- Given a settled contract, when the period rolls over, then days at or before the settlement date are not billed again
- Given a settlement date before the contract's start, when the write runs, then it is refused and nothing is written, with the message naming the start date
- Given two contracts and one settlement, when arrears are recomputed, then the other contract is unchanged
- Given the confirmation, when it opens, then it names the contract, vehicle, renter, the number of days and the rupiah -- figures obtained by allocating, not by subtracting
- Given the app module, when it builds and its tests run, then `:domain:test`, `:app:testDebugUnitTest` and `:app:assembleDebug` all pass, and `version` is `8`
- Given the settlement surface, when it is clicked, then the write happens -- proven by clicking the control, not by `assertExists()`

## Spec Change Log

## Review Triage Log

## Design Notes

**`settledDays` already exists; this story wires rather than writes it.**
Story 1.5 shipped the FR-9b domain rule and nine tests for it in `Settlement.kt`. What is missing is the *storage* the function reads from and the one call site that applies it -- which is exactly the "control exists, wired to nothing" shape this repo has now hit twice. The narrowing goes into `previewAllocation`'s `chargeDays` because that is the single place every arrears read derives its day list, so one line reaches `currentArrears`, the preview, cancellation and correction together. A second application site would be a second derivation of one rule.

**No undo, by design, and the cost is stated rather than avoided.**
PRD FR-9b is explicit that settlement cannot be undone and leaves no per-day trace. That is a real loss -- a settlement typed one month early is wrong until someone edits the contract row -- and it is the right trade for the only exit this app offers from fictional arrears on day one. The confirmation's obligation is therefore to make the mistake hard to make, not recoverable: it must name all four figures before anything is written.

**Settlement composes with allocation rather than replacing it.**
Days after `settledThroughDate` still count, and their stored allocation rows still close them. So a contract with deposits recorded *and* a settlement marker reads as: settled days closed, allocated days closed, the rest owed. The test for this is `SETTLE_PARTIAL` on a contract that already has rows, because the failure mode is an implementation that treats settlement as clearing the whole period.

## Verification

**Commands:**
- `./gradlew :domain:test` -- expected: SUCCESS
- `./gradlew :app:testDebugUnitTest` -- expected: SUCCESS
- `./gradlew :app:assembleDebug` -- expected: SUCCESS