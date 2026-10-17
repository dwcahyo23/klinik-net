---
title: 'Story 2.7 — Mengakhiri kontrak sewa'
type: 'feature'
created: '2026-10-17'
status: 'done'
review_loop_iteration: 0
followup_review_recommended: true
baseline_revision: '073211abe2b6cbd2a01cabeb192460dc349f5c33'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-2-context.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-2-6-menandai-hari-tanpa-tagihan.md'
warnings:
  - oversized
deferred:
  - summary: >-
      A future end date ends the contract immediately, because `Contract.isOpen`
      is `endDate == null` — so the car reads available today for a rental that
      continues for weeks.
    evidence: |-
      Found by this story's own review pass, and initially waved off as
      legitimate pre-marking before being re-checked against `Contract.isOpen`.
      Writing a date six weeks out flips the contract to ended today: the
      derived `VehicleDao.hasOpenContract` answers no and the charge days stop,
      for a rental still running. Same failure class as clamping a date the
      owner chose, which `Contract.init` refuses on exactly that reasoning
      ("a date typed into the wrong field").

      Pinned by `a future end date ends the contract immediately, which is the
      known consequence` in `ContractRepositoryTest`, so the consequence stays
      executable rather than being a sentence in a log someone reads once.

      Not patched here because the fix is not local. Either reject a future end
      date with a sentence naming today, or make "scheduled return" a distinct
      concept with its own representation — and choosing between those is a UX
      decision this story's intent does not settle. Guessing at it inside this
      story is the exact failure mode `Contract.init`'s guard exists to prevent.
      Goes to the Epic 2 retrospective with the other manual checks.
    location: >-
      app/src/main/kotlin/net/klinik/rental/data/repository/ContractRepository.kt:341
    severity: high (unverified)
  - summary: >-
      Whether the end sheet's confirm button is reachable on a Redmi Pad 2 with
      the keyboard up, and whether the three sheets never stack in either
      orientation.
    evidence: |-
      Unverifiable under Robolectric: a node below the fold measures zero, so
      `assertExists()` cannot distinguish "off-screen, not missing" from
      "missing". The end sheet has one field and two buttons, so it is much
      less exposed than Story 2.6's add-block-plus-list, but the shared
      `ContractDateField` is new to both.
    location: >-
      app/src/main/kotlin/net/klinik/rental/ui/screens/kontrak/ExceptionFormSheet.kt:613
    severity: medium
  - summary: >-
      Rotation survival of a half-chosen end date and of the open sheet.
    evidence: |-
      Rests on the form living in an Activity-scoped ViewModel
      (`MainActivity.kt:47-61`), which no test asserts — a destination-scoped
      ViewModel would pass the whole suite and lose the owner's chosen date.
      Already deferred by this spec's Verification section to the Epic 2
      retrospective.
    location: >-
      app/src/main/kotlin/net/klinik/rental/MainActivity.kt:47
    severity: medium
---

<intent-contract>

## Intent

**Problem:** A contract can be opened but never closed. `Contract.endDate` and
`Contract.isOpen` exist since Story 2.5, `ContractDao.update` is written and waits for
"Story 2.7 as its first caller" (`ContractDao.kt:179`), and the two `UNIQUE` active-slot
indexes already free themselves when both active columns go `NULL` — so the schema is
ready and nothing writes to it. The owner whose renter hands the car back has no way to
record that, which means the vehicle never reads `tersedia` again, the charge days never
stop, and Story 2.6's read-only exception rule is unreachable because no contract can
ever be ended.

**Approach:** Add `endContract(contractId, endDate)` to the existing
`ContractRepository` — the one write that sets `endDate` and clears both active columns,
inside the `inTransaction` the class already owns. Both released slots and the
`endDate` are the *same* update on the *same* row, so "status changes but the vehicle
stays rented" is not a state the code can reach. No new table, no new column, no new DAO
method: the schema already expresses everything.

## Boundaries & Constraints

**Always:**
- `endDate >= startDate`, refused otherwise. `ContractEntity`'s `init` already refuses it
  (`ContractEntity.kt`), and the repository re-reads the stored row and builds the domain
  model rather than copying fields, so the guard cannot be bypassed by a caller holding a
  stale `Contract`.
- The write clears **both** `activeVehicleId` and `activeRenterId` in the same statement.
  `ContractEntity.toEntity()` derives them from `isOpen` (`ContractRepository.kt:453-454`),
  so there is no second writer that could clear one and forget the other.
- Ending is **one transaction** (AD-4). `inTransaction` already exists and is already
  proven on the `open` path.
- An **already-ended** contract refuses a second end — the same `ContractNotOpenException`
  Story 2.6 uses for its read-only rule, because "this contract has ended" is one fact
  and two exception types would let one path forget to check it.
- An ended contract keeps counting toward arrears: **do not delete, do not null, and do
  not archive the row.** The read-only rule and the exceptions it already owns stay
  (`SERVICE_EXCEPTION` is `onDelete = RESTRICT`, so ending cannot take the history even
  if something later tried).
- Ending must not touch allocations. That is what makes surplus survive: credit **is** the
  `chargeDate = null` allocation row (`Allocation.kt:12`, AD-3), and the PRD is explicit
  that credit is *"bukan sekadar angka yang tersimpan"* — it is re-derived from stored
  rows every period. An end writes one contract row and touches no other table, so surplus
  is structurally incapable of being lost. **This is verified by a test, not built as a
  column.**

**Never:**
- **No `settledThroughDate`, no "mark as settled", no credit column, no arrears number.**
  `settledThroughDate` is FR-9b and belongs to Story 3.6; arrears, overdue days, risk
  tier and credit are Epic 4's surfaces (`epic-2-context.md:48`). This story stores the
  *fact* those surfaces will read, exactly as 2.6 stored the facts the engine reads.
- **No payment or deposit table.** There is none. "Setoran lama pada Kontrak yang sudah
  berakhir tetap dapat dibaca dan masih dapat dibatalkan dengan alasan wajib" is
  discharged structurally by *not* deleting or hiding the contract — a deposit hanging off
  it stays reachable. The cancellation flow and its mandatory reason are Story 3.4's.
- No reopen, no un-end, no edit of rate, schedule or exceptions on an ended contract.
  The exceptions half already refuses (2.6); the rate/schedule half has **no edit UI at
  all**, so there is nothing to lock — and that is the correct state, not a gap.
- No new column, no migration, no `version` bump. `version` stays `4`.
- No FAB, no confirmation dialog — ending a contract is one `KnButton` and a date field.
  It is not reversible, but the AC names no impact to disclose and the end date is the
  owner's own figure; a dialog would add a step to the one action they do every few weeks.
- No Material Icons, no new dependency, no `settledThroughDate`, no search, no filter.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| END_OK | Open contract, end date after its start | `endDate` stored; both active slots `NULL`; vehicle reads `tersedia` with no sync action | No error expected |
| END_OK_BEFORE_TODAY | Open contract, end date in the past | Stored verbatim — a contract that ended before the app existed is real | No error expected |
| END_OK_NO_EXCEPTIONS | Open contract with no stored exceptions | Row ends; `SERVICE_EXCEPTION` untouched (there is none to touch) | No error expected |
| END_ON_END | Open contract, end date equal to `startDate` | Accepted — a same-day rental is a real rental | No error expected |
| END_BEFORE_START | End date before `startDate` | Refused; nothing written; row still open | `kontrakErrorContractEndBeforeStart`, on the date field, naming the start date |
| END_NO_DATE | Save tapped with no date chosen | Refused; nothing written | `kontrakErrorContractEndBlankDate`, on the date field |
| END_ALREADY_ENDED | A contract that already carries an `endDate` | Refused; `endDate` unchanged | `kontrakErrorContractNotOpen`, on the sheet |
| END_TWICE_UI | Same contract, end attempted again after the first | Refused by the repository even if the UI raced | `kontrakErrorContractNotOpen` |
| END_FREES_SLOTS | Contract ended; a new contract opened for the same vehicle **and** the same renter | Both succeed — SQLite treats `NULL`s as distinct in a unique index, so the released slots are free | No error expected |
| END_PRESERVES_EXCEPTIONS | Contract with stored exceptions is ended | Every exception row survives, readable through its own DAO | No error expected |
| END_PRESERVES_SURPLUS | An ended contract's row, read back after the write | Exactly one row changed — the contract's. No cascade exists on `contract` that could reach another table, and the two `UNIQUE` indexes have no `ON DELETE CASCADE` to fire | No error expected |
| END_STOPS_CHARGES | An ended contract, end date 14 Oct | `chargeDaysIn` for an October period omits every day **after** 14 Oct; days before it unchanged | No error expected |
| END_LIST_ORDER | Ended and open contracts together | Ended rows still render, still readable; the list is not filtered | No error expected |
| END_ROW_VERB | Open contract row vs ended contract row | Open offers a verb; an ended row offers none — and its exceptions stay read-only | No error expected |
| END_ATOMIC | Ending inspected after the write | `endDate`, `activeVehicleId` and `activeRenterId` are consistent in one observation — never half-changed | No error expected |

</intent-contract>

## Code Map

**Read-only evidence — do not modify:**
- `domain/.../model/Contract.kt:67-68` — *"`null` while the contract is running. Story 2.7 is
  what sets it; nothing in this story ends a contract."* The sentence this story discharges.
- `domain/.../model/Contract.kt:108-111` — `require(!endDate.isBefore(startDate))`, the
  "a date typed into the wrong field" rule the end-date refusal copies.
- `domain/.../model/Contract.kt:122` — `val isOpen get() = endDate == null`, the one
  derivation `toEntity` and every "is this still editable?" question must ask.
- `domain/.../model/Allocation.kt:7-22` — *"This is a stored fact, not a computed result
  (AD-3) … credit is the [chargeDate] `null` row itself, not a separate number somebody has
  to remember to carry forward."* **This is why END_PRESERVES_SURPLUS needs no new code.**
- `domain/.../model/Payment.kt` — same shape; no table exists for either yet.
- `app/.../data/db/dao/ContractDao.kt:179-181` — `@Update suspend fun update(contract)`, whose
  own KDoc reads *"Story 2.7 is the first caller."* No new DAO method is needed.
- `app/.../data/db/dao/ContractDao.kt:87-94` — `findActiveByVehicleId` reads the **indexed**
  active column, not `endDate`, because that column is what the `UNIQUE` index answers.
  Ending frees the slot by nulling the column the index covers.
- `app/.../data/db/dao/VehicleDao.kt:86-107` — `hasOpenContract` and `observeRented` both ask
  `endDate IS NULL`. **This is the whole "no manual sync" mechanism**: ending the contract
  makes the vehicle available through the same derived read every other story uses, so there
  is no second status to keep in step.
- `app/.../data/repository/ContractRepository.kt:137-188` — `open`'s shape to copy: one
  `inTransaction`, one read of the slot holders, one insert.
- `app/.../data/repository/ContractRepository.kt:397-399` — `inTransaction`, whose nullable
  `database` is why ViewModel tests must not pass the database.
- `app/.../data/repository/ContractRepository.kt:341-345` — `requireOpenContract`, **the exact
  helper the add path uses**; the end path's first step is the same call, and
  `ContractNotOpenException` is therefore already the right type for END_ALREADY_ENDED.
- `app/.../data/repository/ContractRepository.kt:446-456` — `Contract.toEntity()`, where the
  active columns are derived from `isOpen`. Ending is `toEntity()` of a `Contract` carrying
  the new `endDate` — the derivation is the whole mechanism, already written.
- `app/.../data/db/entity/ServiceExceptionEntity.kt` — `onDelete = RESTRICT` on the contract,
  which is why ending cannot take an ended contract's exception history even if a future
  delete tried.
- `app/.../data/db/KlinikDatabase.kt:55` — `version = 4`. **Stays 4**: no schema change.
- `app/.../ui/screens/kontrak/KontrakScreen.kt:440-487` — the row's `trailing` slot, where 2.6
  put the exception-count badge, and where the end verb's affordance has to fit without
  disturbing what is there.
- `app/.../ui/screens/kontrak/KontrakScreen.kt:294-312` — the banner suppression that runs
  `if (state.form != null || state.exceptionSheet != null)`. A third sheet must be added to
  that condition, or the screen's banner draws beneath the end sheet — the exact defect 2.6
  fixed.
- `app/.../ui/screens/kontrak/ExceptionFormSheet.kt:428-497` — `ExceptionDateField`: a disabled
  `KnField` with `Lucide.CalendarDays` opening a Material3 `DatePickerDialog`, **including the
  UTC round-trip at :472-475**. The end sheet reuses the shape. Note the deliberate
  consequence: 2.6 bound its picker loosely and refused impossible dates at the repository;
  **this story does the same**, for the same reason — a `SelectableDates` bound needs a
  `LocalDate`↔millis bridge that is the third copy of that documented trap.
- `app/.../ui/screens/kontrak/KontrakUiState.kt` — `KontrakUiState`'s eight combine sources;
  a third sheet means a ninth, and `ExceptionFormState` is the precedent for a sheet state
  that holds **identity and a form, never the rows it displays**.
- `app/.../ui/shell/NavGraph.kt:213-231` — why `KontrakScreen` mounts its own sheets and
  `KontrakRoute` mounts none: two `ModalBottomSheet` windows over one state is the `high`
  Story 2.8's review found. The third sheet mounts from `KontrakScreen` too.
- `app/src/test/.../penyewa/DatabaseMigrationTest.kt` — the v4 fixture. **Untouched by this
  story**, because `version` does not move; that is itself the assertion that no migration
  was needed.
- `domain/src/test/.../calculate/ChargeDaysTest.kt:93-202` — the six existing exception cases.
  They construct `ChargeSchedule` without an `endDate`, so a `contractEnd: LocalDate? = null`
  parameter leaves all twenty cases passing **unchanged**.

**Reuse points — nothing new to build, only to call:**
- `ExceptionDateField`'s shape (`ExceptionFormSheet.kt:428-497`) — the end sheet's date field,
  including the UTC conversion. It is `private` to that file, so either it is extracted or
  the end field lives in the same file; see Design Notes.
- `KnField` — the reason field is not needed here; only the date and a button.
- `KnSnackbar` + `KnBadgeTone` — the end sheet's own success/failure banners, same as 2.6's,
  for the same window reason.
- `RStrings` — every refusal is Indonesian and resolved through `getString`.
- `ContractRepository.requireOpenContract` — reused verbatim, which is what makes
  `ContractNotOpenException` correct for both stories without a second type.

**Gotchas carried over from Stories 2.1–2.8:**
- `KlinikNetTheme` uses `staticCompositionLocalOf`; every Robolectric test wraps in it.
- `performSemanticsAction(SemanticsActions.OnClick)`, not `performClick`, inside a sheet or dialog.
- `assertExists()`, never `assertIsDisplayed()`, below the Robolectric fold.
- One `setContent` per test; hold `var state by mutableStateOf(...)` outside.
- One `testTag` per node; tags are fixed literals, never built from `stringResource`.
- `ContractRepository(dao, exceptionDao, database)` in repository tests, `ContractRepository(dao,
  exceptionDao)` in ViewModel tests — do not "fix" this asymmetry.
- Every number and date on a row arrives **pre-formatted in the ViewModel**.
- `stateIn(WhileSubscribed)` needs a subscriber or `.value` stays at its initial value —
  `observingViewModel()` exists for exactly this.

## Tasks & Acceptance

**Execution:**

*Domain*
- `domain/.../domain/validation/ContractEndBeforeStartException.kt` — `(val contractId: String)`
  plus `(val startDate: LocalDate)`, mirroring `ServiceExceptionBeforeContractStartException`
  but naming the **contract's start date** rather than the offending end date — the fix is
  "pick a day on or after the contract began", and the owner cannot act on that without
  knowing which day that was.
- `domain/.../domain/calculate/ChargeDays.kt` — add `contractEnd: LocalDate? = null` to
  `ChargeSchedule`, and the filter `it <= schedule.contractEnd` when it is non-null. **This
  discharges an explicit deferral in that file's own KDoc** — *"the deliberate omission:
  `contract.endDate` is not honoured … Story 2.7 settles it"* — and it is the one place
  the story adds behaviour to `domain/`. Defaulted `null`, so all twenty existing
  `ChargeDaysTest` cases pass untouched, which is what proves the default preserved the
  meaning rather than redefining it.

  **The blank date never reaches this layer.** `endContract(contractId, endDate: LocalDate)`
  takes a **non-nullable** date, so "no date given" is a compile error for every caller
  rather than a `null` that means "running" on the one path whose whole job is to set it.
  The blank-date refusal is the *sheet's* `canEndContract` plus its own field error, exactly
  as 2.6's blank reason is — so `kontrak_error_contract_end_blank_date` is reached from the
  UI and never from the repository.

*Data*
- `app/.../data/repository/ContractRepository.kt` — add
  `endContract(contractId: String, endDate: LocalDate)` with a **non-nullable** date, for the
  reason the Domain section gives. Inside `inTransaction`, in this order: (1)
  `requireOpenContract(contractId)` — reuses 2.6's helper and its `ContractNotOpenException`,
  which covers `END_ALREADY_ENDED`; (2) refuse `endDate < contract.startDate` with
  `ContractEndBeforeStartException`, **before** any write; (3) build the domain `Contract`
  from the stored row carrying the new `endDate`, letting `Contract`'s own `require` be the
  second guard rather than a copy of it; (4) `dao.update(row.toEntity())`. One row, one
  statement, one transaction. Its KDoc must say plainly that ending writes **exactly one row
  and touches no other table**, and that this is what preserves surplus — the claim a future
  reader would otherwise try to "fix" by adding a credit column.
- **No DAO change.** `ContractDao.update` already exists and already names this story as its
  first caller. **No migration, no `version` bump.**

*UI*
- `app/.../ui/screens/kontrak/KontrakUiState.kt` — `ContractRow` gains `canEnd: Boolean`
  derived from `contract.isOpen` at projection time, plus `endDateText: String?` (null while
  open) so an ended row shows when it ended without a second formatter call. Add
  `endSheet: EndContractFormState?`, `EndContractFormState(contractId, contractLabel, endDate,
  dateError)`, and the pure helpers `selectEndDate` (clears its own error) and
  `canEndContract`. The sheet state holds **identity and one field** — there are no rows to
  derive here, which is why this state is smaller than 2.6's.
- `app/.../ui/screens/kontrak/KontrakViewModel.kt` — add `openEndContract(ContractRow)`,
  `closeEndContract()`, `onEndChange(transform)` and `endContract()`, mirroring the exception
  sheet's handlers and re-using `savingException` rather than adding a third `AtomicBoolean`:
  both sheets write to the same contract and a shared guard is the *correct* scope here —
  unlike 2.6's split from `saving`, which was two genuinely different records. Add the end
  sheet to the `combine` tree (ninth source) and to the banner-suppression condition in
  `KontrakScreen`. The catch chain is `ContractEndBeforeStartException` → `dateError` naming
  the start date, then `ContractNotOpenException` → the sheet's own banner, then
  `CancellationException { throw e }`, then `Exception`. `ServiceExceptionBeforeContractStartException`
  is **not** in it: that one fires only on the exception path.
- `app/.../ui/screens/kontrak/KontrakScreen.kt` — an `Akhiri` affordance on an **open** row's
  `trailing` slot (2.6 put the exception badge there, so both must fit), **absent** on an
  ended row for the same reason the exception sheet's add block is absent rather than
  disabled. Three **required** callbacks — `onOpenEnd`, `onCloseEnd`, `onEndChange`,
  `onConfirmEnd` — following the rule that a defaulted lambda produces a control that renders
  and silently does nothing. Mount the sheet from **this** file beside the other two, and add
  `state.endSheet != null` to the banner-suppression condition so the screen's banner does
  not draw beneath the third sheet.
- `app/.../ui/screens/kontrak/EndContractSheet.kt` — **new**, one `ModalBottomSheet`: a
  `StartDateField`-shaped disabled `KnField` opening a Material3 `DatePickerDialog`, a
  `KnButton` whose `enabled = canEndContract`, the sheet's own success and failure banners,
  and the Close ghost button. No reason field, no modal dialog, no impact list — see the
  `Never` list for why.
- `app/src/main/res/values/strings.xml` — a `<!-- Story 2.7 — Mengakhiri kontrak -->` block
  after 2.6's: `kontrak_end_action`, `kontrak_end_title`, `kontrak_end_field_date`,
  `kontrak_end_confirm`, `kontrak_end_empty_date_hint`, and the refusals
  `kontrak_error_contract_end_before_start`, `kontrak_error_contract_end_blank_date`,
  `kontrak_error_contract_not_open`, `kontrak_error_contract_end_unexpected`.
- `app/.../ui/strings/RStrings.kt` — a `// ---- Story 2.7` section for each, and
  `formatContractEnd(date)` reusing `formatTanggalBulan` so the date the owner is *told*
  about and the date they *see* on the row come from one formatter.
- `app/.../ui/shell/NavGraph.kt` — extend `Screens.Kontrak` and forward in `KontrakRoute`.
  `createKontrakViewModel` in `MainActivity.kt` **does not change** — 2.7 adds no repository
  and no constructor parameter.

*Tests*
- `app/src/test/.../kontrak/ContractRepositoryTest.kt` — extend: `END_OK`,
  `END_BEFORE_START` (refused, row still open), `END_ALREADY_ENDED`, `END_FREES_SLOTS` (new
  contract on the same vehicle *and* renter), `END_PRESERVES_EXCEPTIONS`, `END_ON_END`,
  `END_LIST_ORDER` (ended rows are still observed, not filtered). **`END_ATOMIC` is the one
  that matters**: read the row back and assert `endDate != null` **and** both active columns
  `NULL` in a single observation, so an implementation that wrote them separately fails.
- `app/src/test/.../kontrak/ContractEndBillingTest.kt` — **new**, and the test that discharges
  the AC's real claim. `END_STOPS_CHARGES`: build
  `ChargeSchedule(contractStart, restDay, contractEnd = …)` and assert the days after the end
  date are gone while every earlier day is **byte-identical** to a baseline list computed
  without the end — so a filter that also drops a neighbour, or clamps rather than cuts,
  fails. `END_ON_END`: an end date equal to the start leaves exactly that one day charged.
  This is the file that proves the story's single `domain/` change behaves.
- `app/src/test/.../kontrak/EndContractSheetTest.kt` — Robolectric render of the **real**
  sheet: date field and confirm exist; confirm's `enabled` follows `canEndContract`; a blank
  date refuses; an ended row carries **no** end affordance while the sheet's absence and the
  exception sheet's read-only state still hold; a refusal renders **inside** the sheet; no
  interactive node measures below 48dp.
- `app/src/test/.../kontrak/KontrakScreenTest.kt` — the row's end verb exists on an open row,
  is **absent** on an ended one, a tap reaches `onOpenEnd` **with that row's contract**, the
  end sheet mounts when its state is non-null, and the screen banner is suppressed while **any
  of the three** sheets is up — the condition 2.6 left at two.
- `app/src/test/.../kontrak/KontrakViewModelTest.kt` — the sheet's write reaches the list and
  the vehicle reads `tersedia` with **no** explicit refresh call anywhere in the test (the
  AC's own clause: *"tidak ada tombol sinkronisasi manual"*); a second end while one is in
  flight is ignored; a before-start refusal lands on the date field naming the start date; an
  already-ended contract refuses; and the new flow is subscribed in `observingViewModel()`.
- `domain/src/test/.../calculate/ChargeDaysTest.kt` — extend with `contractEnd` cases only. The
  twenty existing cases must pass **unchanged**, which is what proves the defaulted `null`
  preserved the meaning.

**Acceptance Criteria:**
- Given an open contract and an end date, when it is ended, then `endDate` is stored, both
  active columns are cleared, and the vehicle reads `tersedia` **without any sync action** —
  the status is derived, not pushed
- Given an ended contract, when `chargeDaysIn` runs for a period containing the end date, then
  no day **after** the end date is charged and every earlier day is unchanged
- Given an ended contract, when the contract list renders, then its row is still **there** —
  nothing deletes, archives or filters it out — and `chargeDaysIn` still returns its days up
  to the end date, so whatever engine reads it later sees an unchanged history
- Given a contract with a surplus, when it is ended, then the write touched **only the
  contract's own row** — no `ON DELETE CASCADE` on `contract` reaches another table, so no
  money record can move — and the surplus therefore remains derivable next period, because
  credit is a stored `chargeDate = null` allocation row and never a stored number
- Given an ended contract with stored exceptions, when they are read, then every row survives
  and the sheet stays read-only
- Given an end date before the contract's start, when it is submitted, then the write is
  refused and the message names the contract's own start date
- Given an already-ended contract, when it is ended again, then the write is refused and
  `endDate` is unchanged — from the repository, so a caller bypassing the UI cannot
- Given an open contract row and an ended one, when they render, then the open row offers the
  end verb and the ended row **has none** — absent, not disabled — while the ended row still
  shows when it ended
- Given an end write, when the row is read back, then `endDate` and both active columns are
  observed consistent in one read — never half-changed (AD-4)
- Given an ended contract and the same vehicle and renter, when a new contract is opened, then
  it succeeds, because releasing both active slots freed the unique indexes
- Given the app module, when it builds and its unit tests run, then `:domain:test`,
  `:app:testDebugUnitTest` and `:app:assembleDebug` all pass, `git grep -n
  "androidx.compose.material.icons" app/` returns nothing, and `KlinikDatabase`'s `version`
  is still `4`

## Auto Run Result

Status: done

### Summary

Story 2.7 shipped end to end, and it shipped **without a schema change**. A contract
could be opened but never closed: `ContractDao.update` existed and named this story as its
first caller, the two `UNIQUE` active-slot indexes already freed themselves when both
active columns went `NULL`, and nothing wrote to it.

The write is one row and one statement. `ContractEntity.toEntity()` already derives both
active columns from `isOpen`, and `isOpen` is `endDate == null` — so nulling them *is*
setting the end date. There is no second writer that could set the date and forget a
slot, and no status column that could disagree with it, so "the contract ended but the
car still reads as rented" is a state the code cannot reach rather than one a test has to
catch. The AC's "tidak ada tombol sinkronisasi manual" is satisfied by not having a second
status to synchronise.

The AC most likely to be misread — *"kelebihan itu tidak hilang — menjadi Kredit"* — is
satisfied by touching exactly one row. The PRD forbids a stored credit twice
(*"bukan sekadar angka yang tersimpan"*), and credit **is** the `chargeDate = null`
allocation row (AD-3), re-derived every period. No `ON DELETE CASCADE` on `contract`
reaches a money record, so there is no code here that could drop the surplus.

### Files changed

**Domain**
- `domain/.../calculate/ChargeDays.kt` — `ChargeSchedule.contractEnd` and the filter
  `it <= contractEnd` when non-null. **This discharges an explicit deferral in that
  file's own KDoc**, which named Story 2.7 as the story to settle it. Defaulted `null`,
  so all twenty pre-existing `ChargeDaysTest` cases pass unchanged.
- `domain/.../validation/ContractEndBeforeStartException.kt` — carries the contract's
  **start date**, not the offending end date, because the form already shows what the
  owner typed and the fix is not actionable without knowing what the valid range is.
- `domain/src/test/.../calculate/ChargeDaysTest.kt` — five `contractEnd` cases.

**Data**
- `app/.../data/repository/ContractRepository.kt` — `endContract(contractId, endDate)`
  with a **non-nullable** date, reusing 2.6's `requireOpenContract` so
  `ContractNotOpenException` correctly covers both "already ended" and "edit an ended
  contract's exceptions". **No DAO change, no migration, `version` stays 4.**

**UI**
- `app/.../ui/screens/kontrak/KontrakUiState.kt` — `ContractRow.canEnd` and
  `endDateText`, both derived in `toRow`; `EndContractFormState`, `selectEndDate`,
  `canEndContract`.
- `app/.../ui/screens/kontrak/KontrakViewModel.kt` — its own `combine` layer, four
  handlers, and a third message channel. The write **shares** `savingException` with the
  exception sheet deliberately: both write the same row, so one flag is the honest scope.
- `app/.../ui/screens/kontrak/KontrakScreen.kt` — the row's end verb (**absent** on an
  ended row), the ended-date line, five **required** callbacks, the third sheet mounted
  from this file, and the banner condition extended from two sheets to three.
- `app/.../ui/screens/kontrak/ExceptionFormSheet.kt` — `EndContractSheet`, and
  `ContractDateField` extracted from `ExceptionDateField` (see below).
- `app/.../ui/strings/RStrings.kt` + `strings.xml` — the 2.7 copy block.
- `app/.../ui/shell/NavGraph.kt` + `MainActivity.kt` — forwarding only;
  `createKontrakViewModel` is **unchanged**, verified by diff.

### Two decisions worth recording

**The date field was extracted, not copied.** The spec offered a choice: the end sheet's
field could be a sibling in the same file, or `ContractDateField` could be shared. The
existing field was `private`, so the choice was duplication or extraction. The UTC
round-trip is documented as an off-by-one trap at each of its two existing copies
*precisely because each was written as if it were the only one*. A third copy would be
the first written with that knowledge and still not sharing it.

**The end date itself is charged.** Same-day rentals charge exactly one day — the
degenerate case that *proves* the inclusive rule rather than assuming it. Exclusive would
take a day the renter still had the car for.

### Tests

| File | Covers |
|---|---|
| `ContractRepositoryTest.kt` (+10) | `END_ATOMIC` (one read, three assertions), `END_BEFORE_START`, `END_ALREADY_ENDED`, `END_ON_END`, `END_OK_BEFORE_TODAY`, `END_FREES_SLOTS`, `END_PRESERVES_EXCEPTIONS`, `END_LIST_ORDER`, `END_PRESERVES_SURPLUS`, the future-date consequence |
| `ChargeDaysTest.kt` (+5) | inclusive end, earlier days byte-identical, same-day, `contractEnd`/`asOf` composition, out-of-period |
| `EndContractSheetTest.kt` (new, 11) | contents, empty field + hint, confirm's enabled rule, error clearing, refusals on field vs banner, both banners render inside, both handlers, 48dp floor, and **no** impact dialog |
| `KontrakScreenTest.kt` (+10) | the verb on an open row, absent on an ended one, payload per row, ended-date line, sheet mount/absence, banner suppression, the nested-click negative, touch floor |
| `KontrakViewModelTest.kt` (+7) | the write with no sync call, the rented list emptying, blank date, before-start on the field, already-ended on the banner, the in-flight guard, close |

### Review findings breakdown

6 findings — 1 high, 2 medium, 1 low, 2 false.

- **Patched (3):** the exception sheet's date field showed the **reason's** example text
  under a date prompt (a `high` the green suite could not see — nothing asserts hint
  copy); the end verb was nested in a clickable row with **no test asserting the outer
  click does not also fire** (added, and it passes — the inner control does consume the
  tap); and `ContractRow.canEnd`'s default is not re-evaluated by `copy()`, which is a
  trap for every future fixture rather than only this one.
- **Deferred (3):** the future-end-date consequence above, plus this story's own device
  checks and rotation survival.
- **Rejected (2):** one `low` (`EndSheetMaxHeight` is not load-bearing — the comment says
  so) and one `false` (a money-row assertion against tables that do not exist).

### Follow-up review recommendation

`true`. The threshold for a first pass is "any patched `high`", and this pass patched one:
the exception sheet's date field was displaying *reason* copy. The specific unverified
risk is that **no test asserts hint or example copy on any date field**, so the same
class of mistake can recur in Story 3.6's numpad-adjacent forms or anywhere else a
placeholder was passed by proximity rather than by meaning.

### Verification performed

- `./gradlew :domain:test` — **SUCCESS**, 185 tests, 0 failures.
- `./gradlew :app:testDebugUnitTest` — **SUCCESS**, 505 tests, 0 failures, **0 skipped**.
- `./gradlew :app:assembleDebug` — **SUCCESS**. **690 total, 0 failures.**
- `androidx.compose.material.icons` under `app/` — **no match**.
- `version = 4` in `KlinikDatabase` — **unchanged**, as the spec required.
- `creditBalance` — **no match**. `settledThroughDate` appears only in **pre-existing**
  files (`Settlement.kt`, from Story 1.5's domain model); `git diff` confirms 2.7 added
  no line containing it.
- `EndContractSheet(` — declared in `ExceptionFormSheet.kt`, mounted **only** in
  `KontrakScreen.kt:435`, **not** in `NavGraph.kt`.
- The five callbacks — **required**, no `= {}` default. That rule caught five unwired
  call sites across `AccessibilityTest`, `KontrakScreenTest`, `FourStatesTest`,
  `AppShellTest` and `BannerScopeTest` at compile time, including in `BannerScopeTest`
  whose entire job is measuring which window each message draws in.
- `activeVehicleId`/`activeRenterId` written in `ContractRepository.kt` — **only** in
  `toEntity()`. The single-writer invariant the spec's grep gate exists to protect holds.

### Residual risks

Three deferred items. The one that is not a device check: **a future end date ends the
contract immediately**, because `isOpen` is `endDate == null`. I found this in review and
initially waved it off as legitimate pre-marking before re-checking the derivation — it is
a money bug: a car reads available today for a rental that continues for weeks. It is
pinned by an executable test rather than left as prose, and it is **not** patched here
because the honest fix is a UX decision this story's intent does not settle: reject a
future date with a sentence, or make "scheduled return" a distinct concept. That belongs
at the Epic 2 retrospective.

### Where Epic 2 stands

2.7 was the last open story in Epic 2. Its deferred checks now sit with 2.6's and 2.8's —
sheet scroll-and-reachability, the `maxLines` default across sixteen `KnField` call sites,
rotation survival, and the future-end-date decision — all on a Redmi Pad 2 at the
retrospective, which is the first point at which every Epic 2 screen exists to be checked
together.

## Spec Change Log

## Review Triage Log

### 2026-10-17 — Review pass
- verdicts: 6 findings — high 1, medium 2, low 1, false 2, maybe-false 0
- findings:
  - `[high]` `[patch]` **The exception sheet's date field showed the *reason's* example.**
    Extracting `ContractDateField` with a `placeholder` parameter meant the exception
    sheet had to supply one, and it supplied `kontrak_exception_reason_hint` —
    "Contoh: ganti ban, mobil di bengkel" — under a field asking for a **date**. The
    field had shown nothing before this story, so nothing regressed *visibly*; the copy
    is simply wrong, and it is wrong in the one field where a plausible-looking example
    teaches the owner to expect a free-text answer to a date prompt. Fixed by adding
    `kontrak_exception_date_hint` ("Pilih hari yang tidak ditagihkan") and pointing the
    call site at it. Caught by reading my own diff for the blind-hunter pass, and it is
    the kind of thing a green test suite cannot see: nothing asserts that hint copy.
  - `[medium]` `[patch]` **The end verb was nested inside a clickable row, and nothing
    asserted the outer click does *also* fire.** The button sits in `KontrakRow`'s
    `trailing` slot while the row itself carries `onClick`; whether the inner control
    consumes the tap is Compose's pointer handling, not a property this codebase states.
    A double-fire would mount the end sheet *and* the exception sheet — two
    `ModalBottomSheet` windows over one state, the exact `high` Story 2.8's review found
    and the reason all three sheets mount from `KontrakScreen`. The existing test passed
    either way, because a double-fire still set the expected payload. Added
    `the end verb does not also fire the row's own click`, which asserts the negative.
    It passes: the inner control does consume the tap, and now that is a fact rather than
    an assumption.
  - `[medium]` `[patch]` **`ContractRow.canEnd`'s default is not re-evaluated by `copy()`.**
    The default exists so the package's ~25 hand-built fixtures need not restate it, but
    a fixture that copies a row to make a contract *ended* keeps the original `canEnd =
    true` and draws an end verb on a contract that already ended. The one such fixture
    set the field explicitly and was correct; the trap is that every *future* fixture
    inherits it. Recorded on the field's KDoc, which is where a fixture author reads it,
    and stated on `stateWithOneEndedRow` next to the `copy()` it guards.
  - `[low]` `[reject]` `EndSheetMaxHeight` is copied from the exception sheet despite its
    own KDoc admitting it is not load-bearing for one field and two buttons. Rejected:
    removing it is a deletion with no behavioural claim behind it, and keeping it means a
    later field cannot silently push the button off a short landscape tablet. The comment
    already says it is not load-bearing, so nobody is misled.
  - `[false]` `[reject]` The verification-gap layer wanted a test that ending a contract
    writes nothing to `PAYMENT`/`ALLOCATION`, on the reading that credit is preserved
    because no money row is touched. Refuted as written: **there is no `PAYMENT` or
    `ALLOCATION` table**, so there is nothing to assert against. `ending writes one row
    and nothing else` asserts what *is* true today — every other existing table is
    untouched by count, and `PRAGMA foreign_key_list(service_exception)` shows nothing
    cascades out — and the spec's Design Notes record that the stronger version belongs
    to Story 3.6, where the table arrives.
  - `[false]` `[reject]` The blind-hunter layer reported that `endContract` does not check
    the end date against *today*, so an owner could end a contract in the future.
    **Confirmed, and the consequence is money.** `Contract.isOpen` is `endDate == null`,
    so writing a *future* end date flips the contract to ended **immediately** — the car
    reads `tersedia` today and the charge days stop today, for a rental that continues for
    weeks. That is the same class of defect as clamping a date the owner chose, which
    `Contract.init` refuses on exactly this reasoning ("a date typed into the wrong
    field"). Rejected as a *patch* because the fix is not local: the honest fix is either
    to reject a future end date with a sentence, or to make "scheduled return" a distinct
    concept — and choosing between those is a UX decision this story's intent does not
    settle, so it is **deferred to the Epic 2 retrospective** and pinned by
    `a future end date ends the contract immediately, which is the known consequence`
    so it cannot be forgotten.

## Design Notes

**Ending is one row and one statement, and that is the entire mechanism.** The two `UNIQUE`
active-slot indexes already free themselves when both active columns go `NULL` — which is
why SQLite's `NULL`-distinctness is what makes `END_FREES_SLOTS` work with no `DROP INDEX`,
no status column and no rebalancing. `ContractEntity.toEntity()` already derives both columns
from `isOpen`, so ending is `toEntity()` of a `Contract` carrying an `endDate`. There is no
second writer that could clear the vehicle and forget the renter, because there is no second
writer. The alternative — a stored `status` column — would be a second encoding of a fact
`endDate` already holds, and 2.5's `ContractEntity` KDoc already rejected exactly that.

**"Kelebihan itu tidak hilang — menjadi Kredit" is satisfied by touching exactly one row, and
the test is what makes that a fact.** This is the AC most likely to be misread as "add a
credit column". The PRD forbids it twice: *"Kredit mengurangi Kewajiban periode berikutnya,
bukan sekadar angka yang tersimpan"* and *"tanpa perlu menghitung ulang atau menyimpan angka
Kredit terpisah."* Credit **is** the `chargeDate = null` allocation row (`Allocation.kt:12`,
AD-3), re-derived each period from stored rows. An end writes one contract row and touches no
other table, so surplus is structurally incapable of vanishing — there is no code that could
drop it.

The honest limit of that claim, recorded rather than hidden: **there is no `ALLOCATION` table
yet**, so a test cannot count allocation rows before and after an end. `END_PRESERVES_SURPLUS`
therefore asserts what is actually true today — the write changed exactly one row, and no
`ON DELETE CASCADE` exists on `contract` that could reach a money record — and
`ContractEndBillingTest` covers the arithmetic that will read those rows once Epic 3 creates
them. A stronger version of this test belongs to Story 3.6, which is where the table arrives.

**No confirmation dialog, and this is the one place the three-level UX rule is deliberately
not applied.** UX-DR11's middle tier is for actions that *change numbers*; ending a contract
changes no number — the end date is the owner's own figure and the charge days after it are
simply no longer owed. The third tier (mandatory reason) belongs to deleting data. So this is
one field and one button. A dialog would add a confirmation step to the one action the owner
performs every few weeks, and it would ask them to acknowledge a consequence they just typed.

**The end sheet is smaller than 2.6's, and there is no third copy of the date conversion.**
It holds identity and one field, because it has no rows to derive — the contrast with
`ExceptionFormState` is instructive: 2.6's state exists at all only to hold the add form
*while the rows are derived*, and here there is nothing to derive. On the date field: 2.6's
`ExceptionDateField` is `private` to that file, so the end sheet's field is a sibling in the
**same file** rather than a third copy of the UTC round-trip in its own. That is the smallest
honest option: extracting a shared `ContractDateField` would be the right call if a *third*
consumer appeared, and YAGNI says two call sites in one file is not that.

**The end sheet's refusal lands on its own field, and the already-ended case is the
repository's, not the sheet's.** `requireOpenContract` is reused verbatim, which is what
makes `ContractNotOpenException` correct for both stories without a second exception type —
and it is also why `END_TWICE_UI` cannot be produced by racing the UI: the row is re-read
inside the transaction.

## Verification

**Commands:**
- `./gradlew :domain:test` -- expected: SUCCESS -- the twenty existing `ChargeDaysTest` cases
  unchanged, plus the `contractEnd` ones
- `./gradlew :app:testDebugUnitTest --tests "net.klinik.rental.ui.screens.kontrak.*"` -- expected:
  SUCCESS
- `./gradlew :app:testDebugUnitTest` -- expected: SUCCESS -- `KlinikDatabase` and `NavGraph`
  are touched
- `./gradlew :app:assembleDebug` -- expected: SUCCESS
- `git grep -n "androidx.compose.material.icons" app/` -- expected: NO MATCH
- `git grep -n "version = 4" app/src/main/kotlin/net/klinik/rental/data/db` -- expected:
  **still 4** — a version bump here would mean 2.7 invented a column, and the grep is the
  cheapest possible assertion of that
- `git grep -n "settledThroughDate\|creditBalance" app/ domain/` -- expected: NO MATCH --
  both are FR-9b's and Epic 4's, and this story adds neither
- `git grep -n "EndContractSheet(" app/src/main` -- expected: a mount in `KontrakScreen.kt`
  and **no mount in `NavGraph.kt`** -- two `ModalBottomSheet` windows over one state is the
  `high` Story 2.8 found, and this is the third sheet
- `git grep -n "onOpenEnd\|onConfirmEnd\|onEndChange" app/src/main/kotlin/net/klinik/rental/ui/screens/kontrak/KontrakScreen.kt`
  -- expected: all **required** (no `= {}` default) and forwarded in `NavGraph.kt`, so a partial
  wiring is a compile error rather than a control that does nothing
- `git grep -n "no service-exception editor (Story 2.6)\|service exceptions are Story 2.6's\|a row is not clickable until a story gives it something to do" app/src`
  -- expected: NO MATCH -- those deferral sentences are discharged, so surviving ones point at
  a shipped thing that is gone
- `git grep -rn "activeVehicleId\|activeRenterId" app/src/main/kotlin/net/klinik/rental/data/repository/ContractRepository.kt`
  -- expected: hits only in `toEntity()` -- the single derivation. A second writer assigning
  them by hand is the defect this grep exists to catch.

**Manual checks — DEFERRED to the end of Epic 2, with 2.6's and 2.8's existing deferrals:**

- On the Redmi Pad 2, in both orientations: end a contract and confirm the vehicle list flips
  to `tersedia` without a refresh, and that the three sheets never stack.
- With the end sheet open, rotate and confirm the chosen date survives.

  **Why deferred, and what it costs.** These need the device; every automated gate passes. The
  claims rest on structure the tests cannot execute. "No sync button" is `hasOpenContract`'s
  `endDate IS NULL` query plus Room's invalidation tracker, which `KontrakViewModelTest`
  exercises as a *re-emission* — there is no refresh method to assert absent, which is the
  point. The rotation claim rests on the form living in an Activity-scoped ViewModel, which
  no test asserts.

  **Where to do it.** The Epic 2 retrospective, on a Redmi Pad 2, alongside 2.6's exception-sheet
  checks and 2.8's rail/bottom-nav swap — that is the first point at which every Epic 2 screen
  exists to be checked together.
