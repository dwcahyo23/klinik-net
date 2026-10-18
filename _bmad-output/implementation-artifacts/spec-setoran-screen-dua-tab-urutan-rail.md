---
title: 'Layar Setoran — dua tab, dan urutan rail UX-DR17'
type: 'feature'
created: '2026-10-18'
status: 'done'
baseline_revision: '3253f1d23ab081d0a76a4714da60508db8fc4e25'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-4-context.md'
warnings:
  - oversized
---

<intent-contract>

## Intent

**Problem:** The rail is ordered by enum-declaration accident, not by UX-DR17's frequency order,
and "Setoran" — the action the owner performs most — is a floating button rather than a screen.
The prototype gives Setoran a screen with two tabs (Input Setoran, Riwayat Transaksi); the app
scatters its deposits across a quick-action sheet and a per-contract block inside another screen.

**Approach:** A Setoran destination with two tabs, added to the rail in UX-DR17's order. The
Input tab opens the **existing** deposit sheet; the Riwayat tab shows a **cross-contract**
history — all deposits, all contracts, newest first — reusing the row shape the per-contract
block already draws. The per-contract block in the contract sheet stays: one surface answers
"what did this rental's payments look like", the other "what did money come in, period".

## Boundaries & Constraints

**Always:**
- **The rail order is UX-DR17's frequency order with Kontrak inserted after Kendaraan** (the
  prototype's screen map hangs Kontrak beneath it): Beranda · Setoran · Kendaraan · Kontrak ·
  Penyewa · Tunggakan · Laporan · Pengaturan — eight entries.
- **One deposit sheet.** The Input tab triggers the existing sheet — numpad, mandatory preview
  and validation exist once. A second form is a second derivation of everything Story 3.2 built.
- **Cancelled deposits appear in the history, marked, with the owner's reason visible.** The
  status filter belongs to the allocator; a history that hides a cancellation undoes Story 3.4's
  decision one more screen later.
- **A cancelled row offers neither verb.** Both repositories refuse it; a control that renders
  and ignores the tap is the "wired to nothing" defect shipped three times.
- **The FAB is absent on Beranda and Setoran.** UX-DR17: the grid and the Input tab are the two
  doors, and they must contain the same actions.
- **The cross-contract summary derives from the same flow as its list** — one subscription; a
  second counting query is a second thing that can disagree with the rows above it.

**Never:**
- **No second deposit form**, no second numpad, no second preview.
- **No chart, no KPI hierarchy** on the summary strip — four plain figures.
- **No filter, no search, no pagination** on the cross-contract history. The per-contract list
  already scrolls; this one does too.
- **No per-contract block removal** from the contract sheet — it answers a different question.
- **No gesture-only actions**; every verb is a visible button clearing 48dp.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| SETORAN_TABS | The screen opens | Two chips: Input and Riwayat; Input selected first; Riwayat shows the history | No error expected |
| SETORAN_INPUT | Input tab, button tapped | The existing deposit sheet opens with a blank form | No error expected |
| SETORAN_CROSS | Deposits on two contracts | Both listed, newest first, **each row naming its renter · plate** | No error expected |
| SETORAN_CANCELLED | A cancelled deposit with a reason | Listed, marked, reason visible, **no verbs** | No error expected |
| SETORAN_SUMMARY | Mixed deposits | Total, active, cancelled counts and received-today — derived from the same flow as the rows | No error expected |
| SETORAN_FAB | The Setoran destination visible | The deposit FAB is absent | No error expected |
| SETORAN_RAIL | The rail drawn | Eight entries in the order above; every entry ≥ 48dp | No error expected |
| SETORAN_EMPTY | No deposits anywhere | The explained empty state, not a blank | No error expected |

</intent-contract>

## Decisions taken at the gate

- **Eight rail entries, Kontrak inserted after Kendaraan.** UX-DR17's list predates the contract
  screen; the prototype's map hangs Kontrak beneath Kendaraan, so that is where it goes rather
  than appended at the end.
- **The rows become self-identifying.** `DepositHistoryRowUi` gains `contractId`, `renterName` and
  `contractLabel` per row, and `DepositHistoryBlock`'s cancel callback reads the **row** rather
  than block-level state — a cross-contract list whose cancel verb opened a dialog about the wrong
  contract is the failure the wiring test was written to catch, reached by widening the block
  rather than by writing a second block.

## Code Map

- `app/.../ui/shell/Destination.kt` -- seven entries today, enum order = rail order. `FAB_HIDDEN_ROUTES`
  goes in the companion beside `START`.
- `app/.../ui/shell/AppShell.kt:116,278` -- `isOnBeranda` and the FAB's `visible`; replaced by the
  route-set check.
- `app/.../ui/screens/kontrak/DepositHistoryBlock.kt:62-123` -- the block, its row composable, and
  the tag functions. The cancel callback reads block-level state at line 115; becomes row-level.
- `app/.../ui/screens/setoran/SetoranUiState.kt:278-314` -- `DepositHistoryRowUi` and
  `DepositHistoryUi`; the row gains three fields.
- `app/.../data/repository/DepositHistoryRepository.kt` -- `forContract` exists; gains `observeAll`.
  `toHistoryRow` already carries `contractId`.
- `app/.../data/db/dao/PaymentDao.kt` -- no all-rows query; gains `observeAllForHistory()`.
- `app/.../data/repository/PaymentRepository.kt` -- `observeTodaysIncome(clock)` for the summary's
  received-today figure.
- `app/.../ui/components/Badge.kt` -- `KnChip`, the tab control (no `TabRow` exists anywhere).
- `app/.../ui/shell/NavGraph.kt` -- `Screens.Deposito`, `KontrakRoute`'s history forwarding, and
  where `Screens.Setoran` is added.
- `app/.../MainActivity.kt` -- `createSetoranViewModel` already passes `DepositHistoryRepository`.

**Gotchas carried over:**
- Rail/bottom-nav count tests assert `Destination.entries.size` — they auto-adapt, but the
  48dp-per-target assertions run against all eight.
- Every new `Screens.*` callback is **required, not defaulted**.
- `KlinikNetTheme` is `staticCompositionLocalOf`; one `setContent` per Compose test;
  `performSemanticsAction` rather than `performClick` inside sheets.

## Tasks & Acceptance

**Execution:**
- [x] `app/.../ui/shell/Destination.kt` -- add `Setoran`, reorder to the eight-entry order, add
  `FAB_HIDDEN_ROUTES`, update the two KDocs that deferred this re-order, add `nav_setoran`.
- [x] `app/.../data/db/dao/PaymentDao.kt` -- `observeAllForHistory(): Flow<List<PaymentEntity>>`,
  newest first, no status filter.
- [x] `app/.../data/repository/DepositHistoryRepository.kt` -- `observeAll(): Flow<List<DepositHistoryRow>>`.
- [x] `app/.../ui/screens/setoran/SetoranUiState.kt` -- extend `DepositHistoryRowUi` with
  `contractId`/`renterName`/`contractLabel`; add the screen's own state (rows, summary, selected tab).
- [x] `app/.../ui/screens/kontrak/DepositHistoryBlock.kt` -- cancel callback reads the row; title
  becomes a `titleText: String` parameter.
- [x] `app/.../ui/screens/setoran/SetoranViewModel.kt` + `SetoranScreen.kt` -- the screen, the tab
  state, the cross-contract flow, and the summary derivation.
- [x] `app/.../ui/shell/NavGraph.kt`, `AppShell.kt`, `MainActivity.kt`, `strings.xml` +
  `RStrings.kt` -- the route, the FAB rule, the wiring, the copy.
- [x] `app/src/test/.../setoran/SetoranScreenTest.kt` -- **new**: the matrix, including rows from
  two contracts and the cancelled row's absent verbs.
- [x] `app/src/test/.../shell/AppShellTest.kt` -- FAB absence on Setoran; the count tests adapt
  automatically; **the rail order itself is now asserted**, which nothing did before.
- [x] `app/src/test/.../kontrak/DepositHistoryWiringTest.kt` -- green, with the row fixture
  carrying the three new identity fields and **one test added** (see the Auto Run Result).

**Acceptance Criteria:**
- Given the rail is drawn, then it shows eight entries in the order Beranda · Setoran · Kendaraan ·
  Kontrak · Penyewa · Tunggakan · Laporan · Pengaturan, each clearing 48dp
- Given the Setoran destination, then two tabs exist and Input is selected first
- Given the Input tab's action, when tapped, then the existing deposit sheet opens
- Given deposits on two contracts, when the Riwayat tab is shown, then rows from both appear
  newest-first, each naming its renter · plate
- Given a cancelled deposit, when listed, then it is marked with its reason and offers no verbs
- Given the summary, when rendered, then total/active/cancelled come from the same flow as the
  rows, and received-today from `observeTodaysIncome`
- Given Beranda or Setoran is visible, then the deposit FAB is absent
- Given the app builds, when tests run, then `:domain:test`, `:app:testDebugUnitTest` and
  `:app:assembleDebug` all pass, and the existing wiring test stays green unmodified

## Spec Change Log

### The wiring test could not stay unmodified, and the reason matters

The acceptance criterion said the existing `DepositHistoryWiringTest` must stay green
**unmodified**, on the reasoning that a row-level callback refactor is a dangerous change and
an untouched suite is the proof it was safe. Adding three **required** fields to
`DepositHistoryRowUi` makes that impossible: every construction site must supply them, and the
wiring test's `row()` helper is one. So the criterion as written could not be met without
either giving the fields defaults (a fabricated identity — the exact wrong-target defect this
change exists to prevent) or making the block fall back to block-level state when a row omits
it (a silent wrong-target path, guarded only by remembering to fill it in).

The suite's **assertions** stayed untouched. The fixture gained three defaulted parameters
(`contractId = CONTRACT_ID`, `renterName = "Budi Santoso"`) that are exactly what the
per-contract surface produces, so `Triple(CONTRACT_ID, "Budi Santoso", "p2")` still asserts
what it asserted.

But a fixture that *agrees with the block* cannot tell the two possible rules apart — which is
why the refactor was risky in the first place. So one test was **added**:
`the cancel verb prefers the row's identity over the block's`, which builds a row whose identity
differs from the block's and asserts the row's wins. That is the proof the criterion was reaching
for, and it is stronger than "the suite did not change".

**Verified by mutation**: with `onRequestCancel(state.contractId, state.renterName, …)` restored,
that test fails (`expected contract-2/Siti Aminah/p9, got contract-1/Budi Santoso/p9`). With the
row-level read it passes. A test that cannot fail is a comment.

### The rail order had no test at all

`every entry maps to its own index` proves the table is internally consistent — a re-ordering
satisfies it exactly as well as the current order. Nothing anywhere asserted *which* order, so
the whole point of this story could have been undone by moving one line and every suite would
have stayed green. `the rail is ordered by UX-DR17's frequency order` now pins the eight entries
in sequence.

## Review Triage Log

## Auto Run Result

### Deviations from the Code Map

- **`Destination.kt` had to be rewritten, not edited.** The first pass *appended* the new
  eight-entry order after the old seven instead of replacing it, which does not compile —
  `Destination.Kendaraan` became an ambiguity at fourteen call sites and the whole `:app` module
  failed. Caught by compiling, not by a test.
- **`observeAllForHistory` needs `ORDER BY date DESC, id DESC`.** The Code Map said "newest
  first"; the tie-break on `id` is load-bearing, because two deposits on the same day would
  otherwise come back in whatever order SQLite feels like and the list would reorder itself
  between emissions.
- **The second contract in the repository test needed its own vehicle.** `CONTRACT.activeVehicleId`
  is UNIQUE, so a "two contracts" fixture that reuses the seeded van is rejected by the schema —
  two live contracts on one vehicle is a state the database forbids, not a test convenience.

### Files changed

| File | Change |
|---|---|
| `ui/shell/Destination.kt` | `Setoran` added; eight entries reordered to UX-DR17's frequency order; `FAB_HIDDEN_ROUTES` in the companion; the two deferring KDocs rewritten to say the deferral is now paid |
| `ui/screens/setoran/SetoranScreen.kt` | **new** — two `KnChip` tabs, the Input tab opening the existing sheet, the Riwayat tab's summary strip and cross-contract rows |
| `ui/screens/setoran/SetoranUiState.kt` | `DepositHistoryRowUi` gains `contractId`/`renterName`/`contractLabel`; `SetoranTab`, `DepositSummaryUi`, `SetoranScreenState` added |
| `ui/screens/setoran/SetoranViewModel.kt` | `screenState` from `observeAll()` + `observeTodaysIncome`; `selectTab`, `consumeScreenError`, `toScreenRow`, `resolveContractLabel` |
| `ui/screens/kontrak/DepositHistoryBlock.kt` | cancel callback reads the **row**; `titleText` parameter |
| `data/db/dao/PaymentDao.kt` | `observeAllForHistory()`, no status filter |
| `data/repository/DepositHistoryRepository.kt` | `observeAll(): Flow<…>` |
| `ui/shell/NavGraph.kt`, `AppShell.kt`, `MainActivity.kt`, `strings.xml` | the route, `isFabHidden`, the wiring, the copy |

### Verification

- `./gradlew :domain:test` -- **SUCCESS** (203 tests)
- `./gradlew :app:testDebugUnitTest` -- **SUCCESS** (940 tests, 70 suites, 0 failures)
- `./gradlew :app:assembleDebug` -- **SUCCESS**
- Mutation check: the row-vs-block test fails under the old implementation, passes under the new.

### Residual risks

- `toScreenRow` resolves the renter · plate label with a Kotlin join over
  `observeContracts().first()` per row — O(n) reads per emission. Fine at fourteen deposits,
  and the same pattern `TunggakanScreen` already uses; a fleet with thousands of deposits would
  want the label joined in SQL.
- The manual checks (rail order on the device, both tabs, FAB absent on Beranda and Setoran,
  two contracts visible in the history) were not run — no device in this environment.

## Design Notes

**The row becomes self-identifying because a verb must name its target.** A cancel callback that
reads block-level state was correct for a block that serves one contract; the moment the same rows
serve several, the callback must read the row — and the wiring test that asserts
`(CONTRACT_ID, "Budi Santoso", "p2")` is what proves the change is safe for the surface it came
from.

**One sheet, two doors.** The Input tab and the Beranda grid both open the same deposit sheet,
which is why UX-DR17 allows exactly this shape: the doors are *supposed* to contain the same
actions, and the FAB is the third door that would go stale.

**Newest-first for the cross-contract view is a reading direction, not a sort rule.** The
per-contract list reads like a bank statement (oldest first); the cross-contract list answers
"what came in recently" (newest first). Both orderings come from their queries, so neither is a
sort in the UI that could disagree with its own data.

## Verification

**Commands:**
- `./gradlew :domain:test` -- expected: SUCCESS
- `./gradlew :app:testDebugUnitTest` -- expected: SUCCESS
- `./gradlew :app:assembleDebug` -- expected: SUCCESS