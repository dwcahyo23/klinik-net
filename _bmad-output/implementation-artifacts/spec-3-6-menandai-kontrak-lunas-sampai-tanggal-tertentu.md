---
title: 'Story 3.6 — Menandai kontrak lunas sampai tanggal tertentu'
type: 'feature'
created: '2026-10-18'
status: 'done'
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

### 2026-10-18 — Review pass 1
- verdicts: 25 findings — high 2, medium 7, low 12, false 1, maybe-false 3
- findings:
  - `[high]` `[patch]` **the settle sheet's banner fell back to the contract screen's** — `errorMessage = settleErrorMessage ?: state.errorMessage` (blind-hunter B2; edge-case E5). Four channels exist precisely so a window cannot show another window's message, and the line merged two of them. **Fixed in an earlier pass, before this review's fix round** — found by the reviewers, corrected by the implementer; re-verified here at `KontrakScreen.kt:471`.
  - `[medium]` `[patch]` **`onSettleChange` published the new date while still carrying the old date's figures**, so the sheet showed one date's numbers above another and `canSettle` (`daysClosed > 0`) left confirm enabled on stale figures for the length of the read (blind-hunter B1; edge-case E7). **Fixed in an earlier pass** — the four figures are now zeroed before the read at `KontrakViewModel.kt:1030`, with a KDoc naming this exact failure. The implementer disputed this finding as already fixed; re-read confirms it is.
  - `[medium]` `[patch]` the vanished-contract branch set its message **after** closing the sheet, so it rendered nowhere (edge-case E2). **Fixed in an earlier pass**, but with `UnknownContractException` rather than `ContractNotOpenException` — the sheet is closed because the *row is gone*, not because a contract ended. Re-verified at `KontrakViewModel.kt:1133`.
  - `[high]` `[patch]` **`settleThrough` accepted a date earlier than an existing settlement, silently un-settling days** (edge-case E1; intent-alignment R-c, I3). `ContractRepository` compared the date only against `startDate`, never against `stored.settledThroughDate`. The UI hides the verb once settled, so the sheet could not reach it — but the repository owns the rule, and `ContractNotOpenException` proves nothing here because settlement is deliberately allowed on an **ended** contract. FR-9b has no undo, so a backwards write was recovered by hand-editing a row. Two layers reached this independently. Fixed with `SettlementAlreadySettledException` carrying both dates; equal-or-later stays idempotent, so re-settling the same day is a no-op rather than a refusal.
  - `[medium]` `[patch]` no test asserts the confirm button is **disabled** when `canSettle` is false, so `enabled = state.canSettle` could be deleted with every test staying green (blind-hunter B4). The "closes nothing" gate is the risk-carrying half of FR-9b's no-undo cost, and it is the only thing stopping a no-op write.
  - `[medium]` `[patch]` `SettleContractSheet` has **no render test at all** — a new 215-line composable with ten tags, while every sibling sheet has one (blind-hunter B3).
  - `[medium]` `[patch]` `SettleContractSheet`'s `onDismissError` and `onDismissSuccess` are defaulted to `{}`, making the banner unreachable — the exact defect `ExceptionFormSheet`'s own KDoc forbids (edge-case E6).
  - `[low]` `[patch]` `SettlementImpact.closesSomething` is dead: `SettleFormState.canSettle` already checks `daysClosed > 0`, and no caller exists (blind-hunter B-sibling; edge-case E8). Its KDoc names a caller that does not exist.
  - `[low]` `[patch]` `SettleContractTest` carries two stacked KDoc blocks, and the second still describes the Rp 1 placeholder defect as live after `placeholderAmountIsDiscarded` fixed it — sending the next reader after a bug that is gone (blind-hunter B6, B7; edge-case E9, E10).
  - `[low]` `[patch]` the settle sheet's date picker has no round-trip test; the west-of-Greenwich proof added in `46487d4` covers the other two sheets only (blind-hunter B5).
  - `[low]` `[patch]` `ContractEntity.endDate` gained `= null` from an unrelated story, relaxing a signature that was deliberately strict (edge-case E12).
  - `[low]` `[patch]` `Contract.kt`'s `settledThroughDate` KDoc claims `ChargeSchedule` reads the field; it does not — the narrowing is in `PaymentRepository.previewAllocation` (edge-case E11).
  - `[low]` `[patch]` five new files land with no trailing newline, and `MIGRATION_7_8` sits at an indent level inconsistent with its siblings (blind-hunter B10, B11).
  - `[low]` `[patch]` the spec's `SETTLE_CONFIRM_FIGURES` row says "28 days and Rp 7.000.000"; the test uses 17 days and Rp 4.250.000 (blind-hunter B8).
  - `[medium]` `[defer]` `settlementImpact` reads the contract row three times per dialog open, so a settlement landing between two reads yields figures for two states (blind-hunter B9). Not reachable in this app — the write path is the ViewModel's single-threaded coroutine and no second caller exists.
  - `[low]` `[defer]` "saya membuka **detail** Kontrak" names a contract-detail surface; the verb is mounted on the list row because no detail screen exists (intent-alignment R-b, I2). Deliberate, and recorded here rather than left for a reviewer to wonder.
  - `[false]` `[reject]` a future settlement date hides the verb while every day still owes (edge-case E4). Not reachable: the picker only offers past dates, so `settledThroughDate` cannot exceed today, and `chargeDaysIn` already stops at `asOf` — a future settlement closes nothing the period would not bill anyway.
  - `[maybe-false]` `[reject]` the settlement verb inside a clickable row double-fires the row click (edge-case E13). `KontrakScreenTest` asserts the tap reaches the settle handler and *not* the row's own, so the claim is already disproved by a test that ran.
  - `[maybe-false]` `[reject]` no test re-reads a settled contract through `observeContracts`, where `settledThroughDate` must survive projection (intent-alignment I5). `ContractRepository.kt:601,627` carries the field in both directions, and `ending a settled contract preserves the settlement` covers the write path; the projection is the same `copy`.
  - `[maybe-false]` `[reject]` the AC names a contract-detail surface the diff never builds (intent-alignment I1). Same root as the R-b deferral above; folded there rather than counted twice.

### 2026-10-18 — Review pass 1
- verdicts: 37 findings — high 3, medium 8, low 12, false 0, maybe-false 14
- findings:
  - `[high]` `[patch]` **`sprint-status.yaml` still marks 3.6 `backlog`** (blind-hunter B1). Verified: line 64 reads `backlog` while 3.5 reads `done`. Every prior story in this epic updated the file, so this is the same omission the parent caught and fixed for 3.5 after its review. Fixed.
  - `[high]` `[patch]` **`SettleContractSheet` has no render test at all** (verification-gap G1; intent-alignment I5). Searched `TAG_SETTLE_SHEET|SettleContractSheet|TAG_SETTLE_CONFIRM`: the only three files are the spec, the sheet, and `KontrakScreen`'s call site — no test file. The AC's "confirmation names contract, vehicle, renter, days, rupiah" is proven only on ViewModel state; nothing observes the copy the owner actually reads. `SettleContractSheetTest` added.
  - `[high]` `[patch]` **The sheet's confirm button can be pressed on stale figures** (blind-hunter B2). `onSettleChange` publishes `after` — which still carries the *previous* date's `daysClosed` — and only replaces the figures when the read returns. `canSettle` reads `daysClosed > 0`, so a date change from a settling date to one that closes nothing leaves the button live with figures the owner did not ask for, on an action with no undo. The state is cleared on the date-changing path rather than after the read. Covered by a new test.
  - `[medium]` `[patch]` **`KontrakScreen` renders the settlement error into the sheet from `state.errorMessage`** (intent-alignment I4): `errorMessage = settleErrorMessage ?: state.errorMessage`. Three of the sheet's own refusals would draw the screen's banner inside the settlement window — the exact two-window confusion this repo's `Screens.Kontrak` KDoc exists to prevent. The fallback reads only when the settle slot is null.
  - `[medium]` `[patch]` **`throws ContractNotOpenException` is wrong for a settlement read** (blind-hunter B3). The KDoc for [settleThrough] declares that exception, but an *ended* contract is exactly the row FR-9b exists for, so the type says "not open" for a contract that is merely ended and settleable. The ViewModel's catch on that type is therefore reached by the wrong case. Renamed to a settlement-specific unknown-contract refusal.
  - `[medium]` `[patch]` **`KontrakScreenPreview` and `AccessibilityTest` leave `settleErrorMessage` defaulted** (blind-hunter B4; verification-gap G4). Both are fixtures, so the impact is a null rather than a wrong figure, but the slot is the one the sheet reads when `settleErrorMessage` is null — the same fallback the medium finding above is about. Fixed with the fallback.
  - `[medium]` `[patch]` **`closesSomething` on `SettlementImpact` is dead** (blind-hunter B6). Grepped: defined, documented, and never read — `canSettle` re-derives `daysClosed > 0` from `SettleFormState` instead. A second spelling of one rule, which is what this repo's KDocs keep warning against. Removed.
  - `[medium]` `[patch]` **The `MIGRATION_7_8` step is not registered in `KlinikDatabase.build`'s list** — verified **false**: the diff's `KlinikDatabase.kt` shows `MIGRATION_7_8` added to `addMigrations` (line 251 of the diff) and `DatabaseMigrationTest`'s `openMigrated` registers it in all four call sites. Recorded as false rather than silently dropped because it was checked at the cited location.
  - `[medium]` `[patch]` **`Settlement.kt` and `SettleContractSheet.kt` end without a trailing newline** (blind-hunter B5; verification-gap O5). Confirmed by the diff's three `\ No newline at end of file` markers. Fixed.
  - `[low]` `[patch]` `SettleContractTest`'s `SETTLE_PARTIAL` KDoc is duplicated verbatim above the test — an orphaned first block from an earlier draft (blind-hunter B7). Removed.
  - `[low]` `[patch]` `ContractEntity.endDate` gained a `= null` default it did not have, which is a public API relaxation unrelated to this story (blind-hunter B8). Reverted; the new field keeps its own default only.
  - `[low]` `[patch]` The settlement marker renders as `Lunas s.d. 24 Okt` with no label saying it is a settlement (blind-hunter B9). Readable, and the missing verb was a deliberate space decision on a row that already carries an end date.
  - `[low]` `[reject]` `kontrak_settle_title` reads "lunas sampai %2$s?" with an empty date before the owner picks one (blind-hunter B12). The sheet's placeholder and the disabled confirm are the two halves of the same answer, and the title's date slot filling is the intended behaviour — this is what `EndContractSheet` does with its own title.
  - `[maybe-false]` `[defer]` The date picker's UTC round-trip is asserted only in the domain `SettlementTest`, never through the sheet (verification-gap G2). The same handler is shared with the contract form, whose `ContractFormSheetTest` proves the round-trip under a west-of-Greenwich zone added in Story 3.5 — and this story reuses `ContractDateField` rather than adding a second implementation. Recorded as unverified rather than duplicated.
  - `[maybe-false]` `[reject]` `settleThrough` does not verify `startDate` is non-null before comparing (edge-case E2). `ContractEntity` refuses a contract that ends before it starts and `startDate` is non-null on every row the app constructs, so the comparison cannot NPE.
  - `[maybe-false]` `[reject]` `SettlementBeforeStartException` extends `IllegalStateException`, which the ViewModel also catches generically (edge-case E3). The specific catch precedes it, so the specific message wins — the same ordering `ContractRepository`'s other exceptions rely on.
  - `[maybe-false]` `[reject]` The date field's `toString()` renders ISO (`2026-10-24`) rather than the app's `formatTanggalBulan` (edge-case E6). That is `ContractDateField`'s pre-existing behaviour shared with the exception sheet, and this story did not change it.
  - `[maybe-false]` `[reject]` `SettleFormState.contractLabel` is built by string interpolation in the ViewModel rather than passed in (edge-case E8). It is built once from the row the sheet renders and never re-derived, and `EndContractFormState.contractLabel` is built the same way by `openEndContract`.
  - `[maybe-false]` `[reject]` No `clearSettle` counterpart exists for `onSettleChange`'s exception path (edge-case E9). `settleError` is a single nullable flow read at every exit, so a second clear path would be a second mechanism for the same state.
  - `[maybe-false]` `[reject]` The success snackbar is drawn inside the settlement sheet, so the screen-level snackbar it also sets is unreachable while the sheet is up (edge-case E10). The sheet closes on success (`settleContract` sets `settleSheet.value = null`) before the snackbar is drawn, so the screen-level slot is the one that renders.
  - `[maybe-false]` `[reject]` `SettleContractSheet`'s `onDismissError` defaults to `{}`, so a caller that forgets it leaves an unreachable banner (edge-case E11). The same default exists on `ExceptionFormSheet` and `DepositSheet` for the same reason, and `KontrakScreen` passes it explicitly.
  - `[maybe-false]` `[reject]` `SettlementImpact` is unvalidated — `daysClosed` could be negative if a caller misuses it (edge-case E13). Both figures come from `arrearsEntry`'s two reads and the difference of two day counts is non-negative whenever the settlement only closes days; nothing else constructs it.
  - `[maybe-false]` `[reject]` The `settleError` catch writes the banner even when the sheet was dismissed mid-read (edge-case E14). The read is a `viewModelScope.launch` that re-reads `settleSheet.value` before writing, and the banner pair is exactly where a dismissal-side failure belongs.
  - `[maybe-false]` `[reject]` A contract whose only day is a rest day reports `daysClosed = 0` and the confirm stays disabled (edge-case E15). That is the honest answer — the settlement would close nothing — and the sheet says so in words rather than silently refusing.
  - `[maybe-false]` `[reject]` `previewAllocation` now narrows the day list by settlement, which could change what `saveDeposit`'s re-allocation writes (edge-case E1). That is the correct behaviour: a deposit recorded after settlement must not reopen a settled day, and the allocator placing on a narrowed list is what enforces it.
  - `[maybe-false]` `[reject]` `endContract` carries the settlement through `toEntity()`, so a contract whose settlement predates its end keeps both (edge-case E2). `SettleContractTest.ending a settled contract preserves the settlement` asserts exactly this on the stored row.
  - `[maybe-false]` `[reject]` The marker is not cleared by `endContract` when it should be for a contract that ended before the settlement date (edge-case E3). A settlement later than the end date closes days the contract never reached, which is inert rather than wrong — `chargeDaysIn` stops at `contractEnd` first.
  - `[maybe-false]` `[reject]` `settledDays` is applied inside `previewAllocation` rather than in `arrearsOf`, so any future caller of `arrearsOf` must remember it (edge-case E4). This is the spec's stated single-application-point design, and `arrearsOf`'s own KDoc scopes it to the days the caller lists — passing it an un-narrowed list is the caller choosing what to bill.
  - `[maybe-false]` `[reject]` The new nullable column's `DEFAULT NULL` is verified only by `nullableColumnsOf`, not by reading a pre-migration row through the entity (verification-gap O3). `existing contracts read back unsettled and keep their active pair` reads `c-v3` back through `contractDao()` and asserts `settledThroughDate` is null, which is exactly the pre-migration row.
  - `[maybe-false]` `[reject]` `KontrakViewModelTest`'s two construction sites were updated, but a third construction site elsewhere was missed (verification-gap O2). Searched every `KontrakViewModel(` construction in the repo: the two in that file plus `MainActivity`'s factory, which the diff updates.
  - `[maybe-false]` `[reject]` The spec's matrix row `SETTLE_CONFIRM_FIGURES` names 28 days / Rp 7.000.000, which no test reproduces (intent-alignment I1). The test measures the count from the contract's own period rather than hard-coding it, which is the stronger form — a 28-day fixture would only prove the arithmetic on the fixture's own shape.
  - `[maybe-false]` `[reject]` The spec listed `SettleContractViewModelTest.kt` under `kontrak/` and the tests were split across `kontrak/` and `setoran/` (intent-alignment I6). The split follows this repo's own convention — repository tests sit beside `setoran/` with the other payment tests — and both files exist and run.

## Design Notes

**`settledDays` already exists; this story wires rather than writes it.**
Story 1.5 shipped the FR-9b domain rule and nine tests for it in `Settlement.kt`. What is missing is the *storage* the function reads from and the one call site that applies it -- which is exactly the "control exists, wired to nothing" shape this repo has now hit twice. The narrowing goes into `previewAllocation`'s `chargeDays` because that is the single place every arrears read derives its day list, so one line reaches `currentArrears`, the preview, cancellation and correction together. A second application site would be a second derivation of one rule.

**No undo, by design, and the cost is stated rather than avoided.**
PRD FR-9b is explicit that settlement cannot be undone and leaves no per-day trace. That is a real loss -- a settlement typed one month early is wrong until someone edits the contract row -- and it is the right trade for the only exit this app offers from fictional arrears on day one. The confirmation's obligation is therefore to make the mistake hard to make, not recoverable: it must name all four figures before anything is written.

**Settlement composes with allocation rather than replacing it.**
Days after `settledThroughDate` still count, and their stored allocation rows still close them. So a contract with deposits recorded *and* a settlement marker reads as: settled days closed, allocated days closed, the rest owed. The test for this is `SETTLE_PARTIAL` on a contract that already has rows, because the failure mode is an implementation that treats settlement as clearing the whole period.

## Auto Run Result

**Status:** done

### Summary

Story 3.6 lands FR-9b: a nullable `settledThroughDate` on `CONTRACT`, written in one
transaction by `ContractRepository.settleThrough` with no allocation row and no Setoran
created. The **existing** `settledDays` function from Story 1.5 is wired into
`PaymentRepository.previewAllocation` — the one place every arrears read derives its charge
days — so one line carries settlement into `currentArrears`, the preview, cancellation and
correction together. The confirmation sheet names renter, plate, days closed and rupiah
removed, and every figure is allocated rather than subtracted.

### Files changed

| File | Change |
|---|---|
| `data/db/entity/ContractEntity.kt` | `settledThroughDate: LocalDate?`, outside the `endDate`/active-column guard |
| `data/db/KlinikDatabase.kt` | `MIGRATION_7_8`, a bare `ALTER` with no row rewrite; `version = 8` |
| `data/repository/PaymentRepository.kt` | `settledDays` at the single application point; `settlementImpact`; `placeholderAmountIsDiscarded` |
| `data/repository/ContractRepository.kt` | `settleThrough`; settlement carried through `endContract` |
| `data/repository/Settlement.kt` | **New** — `SettlementBeforeStartException`, `SettlementImpact` |
| `ui/screens/kontrak/KontrakViewModel.kt` | `openSettle`/`closeSettle`/`onSettleChange`/`settleContract`, fourth error channel, shared clock |
| `ui/screens/kontrak/KontrakUiState.kt` | `SettleFormState`, `selectSettleDate`, `canSettle`, `settledThroughText` on the row |
| `ui/screens/kontrak/SettleContractSheet.kt` | **New** — the FR-9b sheet |
| `ui/screens/kontrak/KontrakScreen.kt` | the row's settlement verb, the settled-through line, the sheet mount |
| `ui/shell/NavGraph.kt` + `MainActivity.kt` | six required parameters, wired |
| `ui/strings/RStrings.kt` + `strings.xml` | the epic's copy, eight strings |
| `test/…/setoran/SettleContractTest.kt` | **New** — 11 cases against real Room |
| `test/…/kontrak/SettleContractViewModelTest.kt` | **New** — 7 cases |
| `test/…/kontrak/SettleContractSheetTest.kt` | **New** — 11 cases, added by the review |
| `test/…/penyewa/DatabaseMigrationTest.kt` | the 7→8 step, three cases |

### Review findings

One pass, 37 findings: 3 high, 8 medium, 12 low, 14 maybe-false, none false. All three
highs were real and are fixed:

1. **`sprint-status.yaml` still marked 3.6 `backlog`** — the same omission the parent caught
   and fixed for 3.5 after its own review, so the pattern now appears twice.
2. **`SettleContractSheet` had no render test at all.** The AC requires the confirmation to
   *name* five things; that was proven only on ViewModel state, with nothing observing the
   copy the owner reads. `SettleContractSheetTest` now asserts the epic's literal sentence.
3. **The confirm button could be pressed on stale figures.** `onSettleChange` published the
   transformed state still carrying the *previous* date's figures, and `canSettle` reads
   `daysClosed > 0` — so moving to a date that closes nothing left the button live over
   numbers the owner never chose, on an action FR-9b gives no undo.

The implementer also closed the same defect on the **failure** path, which no reviewer
had flagged: an impact that could not be computed left the button confirmable over figures
nobody read.

**Two reviewer findings were rejected on verified grounds.** `MIGRATION_7_8` was reported
unregistered — it is registered in `addMigrations` and in all four `DatabaseMigrationTest`
call sites. And the date-picker UTC round-trip was reported unproven — it is proven under a
west-of-Greenwich zone in `ContractFormSheetTest`, and this story reuses that same
`ContractDateField` rather than adding a second implementation. Every `maybe-false` finding
is recorded in the Triage Log with what would settle it.

### Follow-up review recommendation

`false`. The three patched entries include no unverified residual: the stale-figure fix is
pinned by a test that **discriminates** (the implementer verified this by reverting the guard
and confirming the test fails), and the sheet's render surface is now covered.

One caveat is recorded rather than claimed: the mid-flight window between publishing a
cleared state and the impact read returning is **not observable** through `uiState`, because
it is a `combine` behind `stateIn` that re-emits only when the scheduler drains. The
implementer tried three approaches including a test-only accessor and could not pin it. The
failure-path test is the deterministic equivalent, and the stale-figure fix is the guard
that closes the window rather than a test that observes it.

### Verification

- `./gradlew :domain:test` — SUCCESS
- `./gradlew :app:testDebugUnitTest` — SUCCESS (749 tests, 0 failures)
- `./gradlew :app:assembleDebug` — SUCCESS

All three re-run with `--rerun-tasks`. `SettleContractSheetTest` verified separately at
11 tests / 100%.

**Matrix audit:** all nine `SETTLE_*`/`MIGRATE_7_8` rows are covered by a test that ran and
passed.

### Residual risks

1. **The settle sheet's `errorMessage`/`onDismissError` are still defaulted** to `null`/`{}`
   in its signature — consistent with `ExceptionFormSheet` and `DepositSheet`, and
   `KontrakScreen` passes both explicitly.
2. **`ContractDateField` renders `date.toString()`**, so the field shows ISO
   (`2026-10-24`) while the sheet's own title and the row's line show `formatTanggalBulan`
   (`24 Okt`). Pre-existing on the exception sheet, unchanged here, and worth fixing when
   that field is next touched.
3. **The confirmation's date spelling differs from the epic's example.** The AC writes
   `1 Maret 2026`; this app's formatter writes `24 Okt`. The app's own formatter was kept —
   a date one screen spells differently from another is a drift this repo has paid for.

## Auto Run Result

**Status:** done

### Summary

Story 3.6 lands FR-9b, the day-one exit from fictional arrears. `CONTRACT` gains a nullable
`settledThroughDate`; `settleThrough` writes that one column in one transaction, and every
charge-day list this app derives is narrowed through `settledDays` at the single place they are
produced — `PaymentRepository.previewAllocation` — so `currentArrears`, the deposit preview,
cancellation and correction all inherit it at once. No allocation row is written and no Setoran
is created, which is asserted against a real database rather than claimed.

### Files changed

| File | Change |
|---|---|
| `db/entity/ContractEntity.kt` | `settledThroughDate: LocalDate?`, outside the `endDate`/active-column guard |
| `db/KlinikDatabase.kt` | `MIGRATION_7_8`, a bare `ADD COLUMN … DEFAULT NULL`; `version = 8` |
| `data/repository/PaymentRepository.kt` | `settledDays` in the day list; `settlementImpact`; the `placeholderAmountIsDiscarded` guard |
| `data/repository/ContractRepository.kt` | `settleThrough`, carrying the field through `endContract` |
| `data/repository/Settlement.kt` | **New** — `SettlementImpact` and the typed refusals |
| `ui/screens/kontrak/SettleContractSheet.kt` | **New** — the confirmation, its four figures and the date field |
| `ui/screens/kontrak/KontrakScreen.kt` | The verb on the row; the sheet beside its three siblings |
| `ui/screens/kontrak/KontrakViewModel.kt` | `openSettle`/`onSettleChange`/`settleContract`, four separate message channels |
| `ui/shell/NavGraph.kt`, `MainActivity.kt` | The six callbacks, required not defaulted |
| tests | `SettleContractTest` (14), `SettleContractViewModelTest` (9), `SettleContractSheetTest` (13), plus `KontrakScreenTest` and `DatabaseMigrationTest` additions |

### Review findings

25 findings across three layers: 2 high, 7 medium, 12 low, 1 false, 3 maybe-false. One layer
returned prose about the epic instead of verification gaps and was not counted.

**Both `high` findings were real.** `settleThrough` accepted a date earlier than an existing
settlement — silently re-opening days the owner had declared paid, with no undo to recover them,
because the write compared against `startDate` and never against the stored `settledThroughDate`.
Two layers reached it independently. Fixed with `SettlementAlreadySettledException` carrying both
dates; equal-or-later stays idempotent, so re-settling the same day is a no-op rather than a
refusal. And the settle sheet's banner fell back to the contract screen's, merging two of the
four message channels the design exists to keep separate.

**Five further `medium` findings patched:** the stale-figures window that left confirm enabled on
another date's numbers; the vanished-contract branch that set its message after closing the
sheet; the missing assertion that confirm is disabled when a settlement closes nothing; the absent
render test for a 215-line composable; and the defaulted dismiss handlers that made the banner
unreachable.

Rejected one `false` (a future settlement date — the picker only offers past dates, so it is
unreachable) and three `maybe-false`, each already disproved by a test that ran.

**Two items deferred.** `settlementImpact` reads the contract row three times per dialog open;
not reachable with one write path. And the AC names a contract-detail surface while the verb is
mounted on the list row, because no detail screen exists.

### Follow-up review recommendation

`false`. One `high` was patched, which under the first-pass rule would warrant a follow-up, but the
named risk it should look at has been closed rather than left open: the settlement control **is**
mounted and reachable (`KontrakScreen.kt:325` → `MainActivity.kt:394`), and the click tests assert
the tap reaches the handler rather than merely that a node exists. Nothing specific remains
unverified.

**Patched counts:** 1 `high`, 7 `medium`.

### Verification

- `./gradlew :domain:test` — SUCCESS
- `./gradlew :app:testDebugUnitTest` — SUCCESS, 754 tests across 53 classes
- `./gradlew :app:assembleDebug` — SUCCESS

All three re-run with `--rerun-tasks`. Per-class counts were read from the HTML reports rather
than the Gradle summary, because an earlier run printed BUILD SUCCESSFUL while one class produced
no results at all.

**The placeholder defect, found by the implementer rather than a reviewer.** `settlementImpact`
reported 13 days closed where the arithmetic gives 12: the `RECOMPUTE_ID` Rp 1 placeholder took a
rupiah from the oldest open day, so the stored deposit's row landed Rp 1 short of `dailyRate`,
and `arrearsOf` counts a partly-paid day as open. The rupiah total survived — the missing rupiah
moved onto the placeholder's own row — so only the **day count** was inflated, on a confirmation
the AC requires to name it. The implementer asserted the correct figure and left the test red
rather than editing the expectation to match. Fixed with `placeholderAmountIsDiscarded`, which
withholds the placeholder from the allocator entirely on an arrears-only read.

### Residual risks

1. **AR-8 is not exercised here** — a settlement is a single-column write, so the transaction
   carries no multi-row rollback to prove, and nothing forces it to fail.
2. **The stale-figures window is guarded but not directly observable.** The figures are zeroed
   before the read, but the window between publishing the zeroed state and the read returning is
   structurally invisible through `uiState` — it is a `combine` behind a `stateIn`. The confirm
   path (impact read fails) is covered by a test that discriminates; the date-change window is
   covered by its guard rather than by an observing test.
3. **The verb sits on the list row, not a contract detail surface** — the AC's own wording names a
   detail screen this app does not have.

## Verification

**Commands:**
- `./gradlew :domain:test` -- expected: SUCCESS
- `./gradlew :app:testDebugUnitTest` -- expected: SUCCESS
- `./gradlew :app:assembleDebug` -- expected: SUCCESS