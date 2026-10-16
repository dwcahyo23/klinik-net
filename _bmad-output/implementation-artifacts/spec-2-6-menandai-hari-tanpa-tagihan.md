---
title: 'Story 2.6 — Menandai hari tanpa tagihan'
type: 'feature'
created: '2026-10-16'
status: 'done'
review_loop_iteration: 0
followup_review_recommended: false
baseline_revision: 'f7267c7a26ea4bda57d18971931fa5d86b4d1758'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-2-context.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-2-5-membuka-kontrak-sewa.md'
warnings:
  - oversized
deferred:
  - summary: >-
      Whether a unique index whose name Room does not derive is refused wholesale at open
      is unverified by test alone.
    evidence: |-
      Room compares the index set for equality (SchemaInfoUtilKt.readIndices), so a
      MIGRATION_3_4 index created under any name other than Room's derived
      `index_service_exception_contractId_date` is refused at open rather than reported.
      DatabaseMigrationTest asserts the name through indexesOf() and provokes a duplicate
      on the migrated file, and both pass — strong evidence, since a wrong name fails at
      open before either assertion could run. Only a real device open is conclusive.
    location: >-
      app/src/main/kotlin/net/klinik/rental/data/db/KlinikDatabase.kt:316
    severity: medium
  - summary: >-
      Sheet scroll-and-reachability on a real device, including whether the reason
      field's keyboard covers the Add button.
    evidence: |-
      Unverifiable under Robolectric: a node below the fold measures zero, so
      assertExists() cannot distinguish "off-screen, not missing" from "missing". Already
      deferred by this spec's Verification section to the Epic 2 retrospective on a
      Redmi Pad 2, alongside Story 2.8's rail/bottom-nav swap.
    location: >-
      app/src/main/kotlin/net/klinik/rental/ui/screens/kontrak/ExceptionFormSheet.kt:171
    severity: medium
  - summary: >-
      Rotation survival of the half-typed reason and the pending-removal dialog.
    evidence: |-
      Both rest on the form living in an Activity-scoped ViewModel (MainActivity.kt:47-61),
      which no test asserts — a destination-scoped ViewModel would pass the whole suite and
      lose the owner's typing. Already deferred by this spec's Verification section to the
      Epic 2 retrospective, the point at which every Epic 2 sheet exists to be checked
      together and 2.7 is what locks them.
    location: >-
      app/src/main/kotlin/net/klinik/rental/MainActivity.kt:47
    severity: medium
---

<intent-contract>

## Intent

**Problem:** Nothing can stop a charge day. `ChargeSchedule.serviceExceptions` has existed since
Story 1.2 and `chargeDaysIn` has filtered on it through six domain tests, but no producer exists —
there is no `SERVICE_EXCEPTION` table, no entity, no DAO, no repository method and no screen, and
`Contract`'s own KDoc assigns the table and its editing to this story. Meanwhile `KontrakRow` is
deliberately non-clickable with a comment naming *this* story as the thing that gives it a verb
(`KontrakScreen.kt:294-303`), so today the owner who marks a day as serviced and compensates the
renter has nowhere to record that, and the billing engine has nothing to stop charging it.

**Approach:** Add the `SERVICE_EXCEPTION` table with `UNIQUE(contractId, date)` as the duplicate
rule, a domain `ServiceException` model and its typed duplicate refusal, and extend
`ContractRepository` rather than adding a second repository. Make the contract row clickable and
have it open one sheet — the add block on top, that contract's exceptions below — over the existing
list, so no new destination and no nested windows.

## Boundaries & Constraints

**Always:**
- `UNIQUE(contractId, date)` is the duplicate rule, as a real schema constraint, so two callers
  cannot both pass a form check and write (FR-8, `epics.md:838`). A plain `UNIQUE` index — **not** a
  partial one; `ContractEntity.kt:37-46` proves Room 2.8.5 cannot express one, and none is needed.
- Exceptions are **per Contract, never per Vehicle** (`epics.md:837`). One vehicle can have several
  contracts with different exceptions, so `contractId` is the only owner column.
- Dates are stored as absolute `LocalDate`s in ISO `yyyy-MM-dd` TEXT through the existing
  `Converters`. **Absolute, never an offset or an index into a period** — that is what makes AC3
  ("does not roll into the next period") structurally true rather than true by convention.
- A **reason is required**, because the AC's own display clause is `dengan keterangan alasannya`
  (`epics.md:822`): an exception that could be created with a blank reason produces a list row whose
  "keterangan" is empty. See Design Notes for the rejected optional-reason reading.
- A contract that has **ended** is read-only: `add` and `remove` refuse, the list still renders.
  `Contract.isOpen` is the derivation (`Contract.kt:122`) — no stored flag, no `status` column.
- The repository raises typed domain exceptions and never Indonesian text; `RStrings` picks the
  wording (`ContractRepository.kt:17-32`). Every refusal carries the value the owner needs.
- Every nullable column carries `@ColumnInfo(defaultValue = "NULL")` **and** `DEFAULT NULL` in the
  migration SQL — Room compares recorded defaults and refuses the whole migration otherwise
  (`ContractEntity.kt:69-76`). **The new table has no nullable column**, so this is a trap this
  story does not fall into rather than one it must handle.
- No `--` comment inside a Kotlin raw-string SQL statement: it reaches SQLite verbatim and swallows
  the rest (`KlinikDatabase.kt:167-169`).
- Every string Indonesian and in `strings.xml`; icons Lucide only.

**Never:**
- **No arrears, overdue days, risk tier or credit, and no calculation wiring.** `chargeDaysIn` has
  no production caller today and this story does not give it one — the screens that show those
  numbers are Epic 4's (`epic-2-context.md:48`). What this story owes the engine is *stored facts
  it can read*, and the matrix row `EXCEPTION_NOT_BILLED` is a test over that seam, not a wiring.
- **No `VehicleEntity.isServis` change.** That flag is a manual marker that never stops billing; a
  second mechanism that silently halts a charge day is exactly what `epic-2-context.md:28` forbids.
- No editing a stored reason. Remove and re-add; an "edit reason" affordance is not asked for.
- No mandatory reason on **delete**. UX-DR11's `dialog + alasan wajib` tier is Setoran cancellation's
  (Epic 3, and `cancelReason` is that story's column). Removing an exception changes numbers, so it
  gets the middle tier: a dialog naming the impact.
- No `settledThroughDate`, no ending a contract, no editing a contract's rate or schedule (2.7).
- No FAB, no second "add exception" entry point on the top bar. One row tap, one sheet.
- No search bar and no filter row on the sheet's list — the `KnSearchBar` IME bug
  (`spec-2-4:224`) comes with the bar, and no AC asks for either.
- No `workSchedule` string beside `restDay`, no Material Icons, no new dependency, no `Clock`.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| EXCEPTION_ADD | Open six-day contract, `2026-10-14`, reason `Ganti ban, mobil di bengkel` | Row stored; sheet shows `14 Okt` with the reason; contract list unchanged | No error expected |
| EXCEPTION_ADD_NO_ROLL | An exception on 14 Oct, then a charge period is computed for April | `chargeDaysIn` still charges 14 Apr; the October day is excluded only in its own period | No error expected |
| EXCEPTION_NOT_BILLED | The stored exception's date fed back through `observeServiceExceptions()` into a `ChargeSchedule` | `chargeDaysIn` omits exactly that day from that period | No error expected |
| EXCEPTION_DUPLICATE | The same date added again for the same contract | Refused; row count unchanged | `kontrakErrorExceptionDuplicate`, on the date field |
| EXCEPTION_DUPLICATE_INDEX | Pre-check bypassed, the same pair written twice through the DAO | The `UNIQUE` index refuses; row count stays one | Translated to the same refusal |
| EXCEPTION_BEFORE_START | Date before the contract's `startDate` | Refused; nothing written | `kontrakErrorExceptionBeforeStart`, on the date field, naming the start date |
| EXCEPTION_ENDED_ADD | Contract has an `endDate` | Refused; nothing written | `kontrakErrorExceptionEnded`, on the sheet |
| EXCEPTION_ENDED_REMOVE | Contract has an `endDate`, an existing exception | Refused; the row stays | `kontrakErrorExceptionEnded`, on the sheet |
| EXCEPTION_ENDED_LIST | Contract has an `endDate` | Every row renders with its date and reason; the add block and every remove control are **unmounted** | No error expected |
| EXCEPTION_BLANK_REASON | Save tapped with no reason | Refused; nothing written | `kontrakErrorExceptionBlankReason`, on the reason field |
| EXCEPTION_PER_CONTRACT | The same date marked on two different contracts of the same vehicle | Two rows, one per contract | No error expected |
| EXCEPTION_REMOVE | Owner confirms the impact dialog on an open contract | Row gone; list re-emits with no refresh | No error expected |
| MIGRATE_3_4 | An existing v3 database with vehicle, renter and contract rows | `service_exception` has the v4 shape and its unique index; every pre-existing row intact and readable through its own DAO | No error expected |

</intent-contract>

## Code Map

**Read-only evidence — do not modify:**
- `domain/.../calculate/ChargeDays.kt:16-23,55-61` — `ChargeSchedule(contractStart, restDay,
  serviceExceptions: Set<LocalDate>)` and the `filter { it !in schedule.serviceExceptions }` that
  consumes it. **Already correct and already tested six ways** (`ChargeDaysTest.kt:93-202`). This
  story supplies the *values*; it does not touch the arithmetic. `:33-40` is why the stored set must
  stay a set of plain dates: a non-ascending or duplicated charge list would let one payment write
  two rows for one `(paymentId, chargeDate)`.
- `domain/.../model/Contract.kt:122` — `val isOpen get() = endDate == null`, the derivation every
  "is this still editable?" question must ask. `:48-52` is the sentence this story discharges.
- `app/.../data/db/dao/ContractDao.kt:158-184` (no) / `:95-100` — `findActiveByVehicleId` reads the
  *indexed* active column, not `endDate`, because that column is what the `UNIQUE` index answers.
  The exceptions write reads `endDate` through a new `findById` (`ContractDao.kt:79-80`), which is
  the right column here because "ended" has no index and `findById` is already a primary-key seek.
- `app/.../data/repository/ContractRepository.kt:158-184` — the constraint-translation pattern to
  copy verbatim for the duplicate refusal: re-read the row that actually holds the slot, and
  **rethrow untouched when it does not** rather than relabelling a PK/FK clash as a domain fault.
- `app/.../data/repository/ContractRepository.kt:201-203` — `inTransaction`, the nullable-database
  helper. Its KDoc's last paragraph is the reason a ViewModel test must not pass the database.
- `app/.../data/db/KlinikDatabase.kt:176-250` — `MIGRATION_2_3` is the style template, but 2.6 is
  the first migration in this app that **adds** rather than rebuilds, so it needs none of that
  function's "assert the premise before dropping rows" machinery (see Design Notes).
- `app/.../ui/screens/kontrak/KontrakScreen.kt:266-279` — the one place a sheet over this list may
  be mounted, and `NavGraph.kt:200-214` says why the graph must not mount a second one.
- `app/.../ui/screens/kontrak/KontrakScreen.kt:229-259` — the mutually-exclusive banner and the
  reason dismissal branches on **tone**, not on which message exists. A second sheet inherits this
  trap: while the exception sheet is up the screen banner is invisible beneath it.
- `app/.../ui/screens/kontrak/ContractFormSheet.kt:292-361` — `StartDateField` is the date-picker
  shape to copy, **including the UTC round-trip** at `:336-340` and `:468`. Reuse the shape; do not
  write a second copy of the conversion.
- `app/.../ui/format/Rupiah.kt:148-149` — `formatTanggalBulan(LocalDate)` is the form an exception
  row's date must use. `:63-75` and `:104-146` are why the weekday form may not be substituted:
  `23 Jum` cannot distinguish two exceptions.
- `app/.../ui/screens/kontrak/KontrakViewModel.kt:362-368` — `CancellationException` is rethrown
  **before** the generic `Exception`; it extends `IllegalStateException`.
- `app/.../ui/screens/kontrak/KontrakViewModel.kt:331-333` — the conditional close
  (`if (form.value === submitted)`), and `:349-359` — a catch re-reads the live state and falls
  back to the screen's channel when the sheet is gone. The exception sheet's writes obey both.
- `app/src/test/.../penyewa/DatabaseMigrationTest.kt:532-597,637-706` — the v3 fixture builder, the
  `openMigrated()` round-trip through the *shipped* builder, and the four PRAGMA helpers
  (`columnsOf`, `notNullColumnsOf`, `nullableColumnsOf`, `indexesOf`) every shape assertion uses.

**Modified by this story (each says so in its own KDoc and is discharged here):**
- `app/.../data/db/dao/ContractDao.kt:23-26` — *"no `status` query, no `settledThroughDate` column,
  and no exception table … service exceptions are Story 2.6's."*
- `app/.../ui/screens/kontrak/ContractFormSheet.kt:77` — *"no service-exception editor (Story 2.6)"*.
- `app/.../ui/screens/kontrak/KontrakScreen.kt:294-303` — *"Story 2.7 owns ending a contract and
  Story 2.6 owns editing its exceptions, and neither exists yet … So the row is read-only and the
  tests assert it carries no click action."*
- `app/.../ui/screens/kontrak/KontrakScreen.kt:52-60` — the top-bar action's own argument against a
  FAB; 2.6 adds its verb to the **row** and leaves the top bar alone.
- `app/.../data/db/KlinikDatabase.kt:45-49` — `version = 3` becomes `4`; `:271` gains
  `MIGRATION_3_4`.

**Reuse points — nothing new to build, only to call:**
- `KnListRow` (`Row.kt:74-83`) — `leading`/`trailing`/`onClick`, 44dp leading slot, 48dp floor. The
  exception row is one of these, with `KnAvatar` at default size in `leading`.
- `KnIconButton` (`Field.kt:300-307`) — the remove control in the row's `trailing`, with
  `action_delete` ("Hapus") as its `contentDescription` plus the date, so the spoken label names
  *what* is removed and not just that a button exists.
- `KnModal` (`Feedback.kt:500-510`) — the impact dialog. Platform `Dialog`, scrolling body, footer
  buttons; at most two. Its confirm renders `KnButtonVariant.Primary` and its dismiss `Ghost`
  (`:619,626`) — **there is no danger-confirm parameter**, and forking `KnModal` to get one is not
  worth it for one sentence: the dialog names the impact in its body, which is what makes the
  decision legible.
- `KnField` (`Field.kt:99-129`) — the reason field. `error: String?` means "validation has not run"
  and renders nothing (`:86-92`); a blank string would draw an empty error row.
- `KnEmptyState` (`Feedback.kt:261-269`) — the sheet's "no exceptions yet" state, which must say
  what to do, per `epic-2-context.md:60`.
- `KnSnackbar` / `KnBadgeTone` — the sheet's own failure banner and the row's `KnBadge`, same as
  `ContractFormSheet.kt:231-239`.
- `internal const val TAG_*` blocks at the bottom of each screen file (`KontrakScreen.kt:402-405`,
  `ContractFormSheet.kt:483-497`) — the 2.6 tags join those files' existing namespaces.

**Gotchas carried over from Stories 2.1–2.8:**
- `KlinikNetTheme` uses `staticCompositionLocalOf`; every Robolectric test wraps in it or the screen
  silently gets light defaults.
- `performSemanticsAction(SemanticsActions.OnClick)`, not `performClick`, for anything in a dialog or
  a sheet window; the banner's dismiss needs `useUnmergedTree = true` (`KontrakScreenTest.kt:294-297`).
- `assertExists()`, never `assertIsDisplayed()`, below the Robolectric fold; to *measure* a node,
  `@Config(qualifiers = "w1280dp-h2400dp")` (`KontrakScreenTest.kt:493`).
- One `setContent` per test — hold `var state by mutableStateOf(...)` outside and mutate between
  `waitForIdle()` calls (`ContractFormSheetTest.kt:245-281`).
- **One `testTag` per node.** A caller-passed tag plus a component's own silently loses the
  caller's (`ContractPicker.kt:61-72`). Tags are fixed literals, never built from `stringResource`.
- `ContractRepository(dao, database)` in repository tests, `ContractRepository(dao)` in ViewModel
  tests — `withTransaction` asserts not-main-thread and a `StandardTestDispatcher` drains on the
  main thread (`KontrakViewModelTest.kt:67-79`). Do not "fix" this asymmetry.
- `saving` is an `AtomicBoolean`, not a `MutableStateFlow`: it is not rendered.
- `suspend`, never block, in a DAO test wrapper — `advanceUntilIdle` needs the thread
  (`KontrakViewModelTest.kt:730-738`).
- Every number and date on a row arrives **pre-formatted in the ViewModel**; no `formatRupiah` or
  date call in a composable (`KontrakScreen.kt:80-83`).

## Tasks & Acceptance

**Execution:**

*Domain*
- `domain/.../domain/model/ServiceException.kt` — `data class ServiceException(id, contractId,
  date: LocalDate, reason: String)`; `init` requires non-blank `id`/`contractId`/`reason`. **No Room
  import, `id: String` not `UUID`** per the `Contract` precedent and AD-1. Its KDoc states the two
  facts the data layer must not let drift: the date is absolute (that is what makes AC3 structural),
  and the reason is owner-visible text, never an enum.
- `domain/.../domain/validation/DuplicateServiceExceptionDateException.kt` — `(val date: LocalDate)`
  plus `(val contractId: String)`, mirroring `DuplicatePlateException`'s "name a row that actually
  exists" rule. It carries the date *read back from the stored row*, not the one submitted.
- `domain/.../domain/validation/ContractNotOpenException.kt` — `(val contractId: String)`, the
  read-only refusal. One exception for both `add` and `remove`: they are the same fact — this
  contract has ended — and two classes would let one path forget to check.
- `domain/src/test/.../model/ServiceExceptionTest.kt` — the construction rules with
  `assertThrows` per guard, the way `ContractTest.kt:46-62` does; **plus the reflection negative
  test** (`ContractTest.kt:174-203`) asserting `ServiceException` declares no `status`, no
  `periodIndex`, no `offset` — the two encodings a "does not roll" row could have grown into.

*Data*
- `app/.../data/db/entity/ServiceExceptionEntity.kt` — `@Entity(tableName = "service_exception",
  foreignKeys = [ContractEntity → contractId, `onDelete = RESTRICT`, `onUpdate = CASCADE`],
  indices = [Index(value = ["contractId", "date"], unique = true)])`, columns `id: String` @
  PrimaryKey, `contractId: String`, `date: LocalDate`, `reason: String` — **all four `NOT NULL`, so
  the table has no nullable column and needs no `@ColumnInfo(defaultValue)`.** `init` requires
  non-blank `id`/`contractId`/`reason`. The KDoc must state why RESTRICT (an ended contract's
  exception history is the record of what was not billed; deleting a contract must not take it)
  and why the unique index is plain rather than partial (`ContractEntity.kt:37-46`).
- `app/.../data/db/dao/ServiceExceptionDao.kt` — `observeAll(): Flow<List<ServiceExceptionEntity>>`
  `ORDER BY date ASC, id ASC` (**ascending**: the sheet's list is a billing statement read in
  chronological order, the opposite of `ContractDao.observeAll`'s `startDate DESC`, which puts the
  newest *rental* first — two lists, two purposes); `findByContractAndDate(contractId, date)`;
  `@Insert(onConflict = ABORT)` named `insertServiceException` (not a bare `insert`, so "the one
  writer of this table" stays a single greppable fact, as `ContractDao.insertContract` argues);
  `@Delete` `deleteServiceExceptionById`. No `@Transaction` anywhere — repositories own that.
- `app/.../data/db/KlinikDatabase.kt` — add the entity to `@Database(entities = …)`, bump
  `version` to `4`, add `abstract fun serviceExceptionDao(): ServiceExceptionDao`, and add
  `MIGRATION_3_4` (two statements: the `CREATE TABLE`, then `CREATE UNIQUE INDEX
  index_service_exception_contractId_date` — **Room's derived name, verbatim**), and register it in
  `.addMigrations(...)`. **No `DROP`, no row count, no "refuses to drop" guard**: this migration
  only adds, so it cannot lose a row — see Design Notes. Still no `--` comment inside either string.
- `app/.../data/repository/ContractRepository.kt` — **extend this class, do not add a second
  repository.** It already holds `ContractDao`, already owns `inTransaction`, already owns
  `newContractId`, and `KontrakViewModel` already holds it — so a new repository would add a
  constructor parameter, a duplicate transaction helper, a duplicate id generator and a wiring line
  in `MainActivity` that would otherwise not change at all. Add: `serviceExceptions: ServiceExceptionDao`
  as the **second** constructor parameter, `observeServiceExceptions(): Flow<List<ServiceException>>`,
  `addServiceException(ServiceException)`, `removeServiceException(id, contractId)`, and private
  `insertServiceExceptionEntity` mirroring `insertEntity`'s catch-and-re-read. Its class KDoc must
  be extended honestly: this repository now writes two tables, both owned by one contract.
- `ContractRepository.addServiceException` — inside `inTransaction`, in this order: (1) re-read the
  contract and refuse `ContractNotOpenException` if `endDate != null`; (2) refuse the date before
  `startDate` (an inert row: `chargeDaysIn` filters `it >= contractStart` before it ever reaches
  the exception filter, so such a row can never stop a charge — see Design Notes); (3) courtesy
  pre-check `findByContractAndDate`, throwing the duplicate exception with the **stored** date; (4)
  insert, letting the index refuse a raced duplicate and translating it by re-reading — rethrowing
  untouched if the re-read finds nothing, exactly as `insertEntity` does.
- `ContractRepository.removeServiceException` — inside `inTransaction`: re-read the contract,
  refuse if ended, then delete by `(id, contractId)`. The `contractId` is in the delete predicate on
  purpose: a row id is not something `ui/` can reach without a lookup, and a delete keyed on id
  alone is a delete any caller can aim at any contract.

*UI*
- `app/.../ui/screens/kontrak/KontrakUiState.kt` — add `ServiceExceptionRow(exception, dateText)`
  (date pre-formatted by `formatTanggalBulan`, exactly as `ContractRow.dateText` is), extend
  `KontrakUiState` with `exceptionSheet: ExceptionFormState?` and
  `exceptionRows: List<ServiceExceptionRow>`, add `ExceptionFormState(contractId, contractLabel,
  isReadOnly, addDate, addReason, dateError, reasonError)`, `enum class ExceptionField { Date,
  Reason }` (**not** an extension of `ContractField` — that enum is the open-contract form's
  refusal set and 2.6 does not add a field to that form), and the pure helpers:
  `selectExceptionDate`, `setExceptionReason` (clearing its own error as they type),
  `firstMissingExceptionField()`, `canAddException`, `toServiceException(id)`. **`ExceptionFormState`
  holds identity and the add-form only — never the rows.** The rows are derived in the `combine`,
  so a delete re-emits and the row disappears with no manual invalidation and no stale copy; a
  snapshot of rows inside the sheet state would be a second thing to keep in step with Room.
- `app/.../ui/screens/kontrak/KontrakViewModel.kt` — add `contracts.observeServiceExceptions()` as a
  **fourth cold flow read once** beside `renterRows`/`vehicleRows`; re-nest the combines (the typed
  overloads stop at five, and this screen now has eight sources: contracts, renters, vehicles,
  exceptions, open form, error, exception sheet, and the per-sheet error message) with a private
  `ExceptionSnapshot` per layer alongside the existing `JoinSnapshot`/`FormSnapshot`; add
  `openExceptions(ContractRow)`, `closeExceptions()`, `onExceptionChange(transform)`,
  `addException()`, `requestRemoveException(id)`, `confirmRemoveException()` and
  `cancelRemoveException()`. The remove-confirmation's "which row is being removed" flag is
  ViewModel state, not `remember`, so a rotation does not lose it. **Two `AtomicBoolean` guards, not
  one** — `saving` (the open form's) and a new one for the exception writes; sharing them would let
  saving a contract block saving an exception. Each `add`/`remove` catch chain is
  `DuplicateServiceExceptionDateException` → `ContractNotOpenException` →
  `CancellationException { throw e }` → `Exception`, and each re-reads the live sheet state rather
  than writing a captured copy back, falling back to the screen's channel when the sheet is gone.
- `app/.../ui/screens/kontrak/KontrakScreen.kt` — give `KontrakRow` an `onClick` (its KDoc names
  this story as the one that gives the row a verb) and a `KnBadge` count of the contract's
  exceptions in its `trailing`, so the count is visible **before** the tap. Add **six required
  parameters** — `onOpenExceptions: (ContractRow) -> Unit`, `onCloseExceptions`,
  `onExceptionChange`, `onAddException`, `onRequestRemove: (String) -> Unit`,
  `onConfirmRemove` — all required, not defaulted, for the reason `onCloseForm`/`onFormChange`/
  `onSave`/`onErrorDismiss` are (`:111-135`): a defaulted lambda produces a control that renders and
  silently does nothing. Mount the exception sheet from **this** file at `:266` beside the open-form
  sheet, never from `KontrakRoute`, and suppress the screen banner while it is up for the same
  reason the open form suppresses it — a sheet is a separate window. The sheet gets its own
  `KnSnackbar(tone = Danger)` for the failure the screen's banner cannot show.
- `app/.../ui/screens/kontrak/ExceptionFormSheet.kt` — **new**, one `ModalBottomSheet`, mounted once,
  in one window: (a) the add block — a `StartDateField`-shaped disabled `KnField` with
  `Lucide.CalendarDays` opening a Material3 `DatePickerDialog` (copy the UTC round-trip from
  `ContractFormSheet.kt:336-340`; **no `SelectableDates`** bound, see Design Notes), a `KnField` for
  the reason, and a `KnButton` whose `enabled = state.canAddException`; (b) `if (isReadOnly)` no add
  block and no remove controls at all — **unmounted, not disabled**, because a greyed-out "add" on a
  contract that cannot take one still claims it can; (c) the rows, or `KnEmptyState` naming what to
  do; (d) the failure banner; (e) the `KnModal` impact dialog, mounted from
  `state.pendingRemovalId` so its visibility is state rather than a `remember`. Every row's remove
  is a `KnIconButton` whose `contentDescription` names the date. No `Snackbar` from the screen is
  visible while this is up, so this sheet must render its own confirmation too.
- `app/.../ui/components/Field.kt` — add `maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE` to
  `KnField` and pass it to the `BasicTextField` at `:199-203`. **Mirror `BasicTextField`'s own
  default expression**, so the existing `singleLine = true` callers keep `maxLines == 1` and the
  three other screens that use `KnField` are untouched — a bare `Int.MAX_VALUE` default would hand
  every one of them an unbounded box. The reason field passes `singleLine = false, maxLines = 3`:
  an unbounded field inside a `verticalScroll` sheet is a layout defect, and `KnField` today has
  `singleLine` (`:120`) but no way to bound the multi-line case.
- `app/src/main/res/values/strings.xml` — a `<!-- Story 2.6 — Pengecualian servis -->` block of
  `kontrak_exception_*` keys after the 2.5 block, in its own subsection comments, plus the
  `kontrak_error_exception_*` refusals. The date column carries the **month**: `14 Okt`, not `14
  Jum`, for the reason `Rupiah.kt:104-146` documents.
- `app/.../ui/strings/RStrings.kt` — a `// ---- Story 2.6` section: `kontrakExceptionAdded(date)`,
  `kontrakExceptionRemoved(date)`, `kontrakErrorExceptionDuplicate(date)`,
  `kontrakErrorExceptionBeforeStart(startDate)`, `kontrakErrorExceptionBlankReason()`,
  `kontrakErrorExceptionEnded()`, `kontrakErrorExceptionUnexpected()`. Each refusal names what is
  wrong and what to do, and the duplicate names the stored date.
- `app/.../ui/shell/NavGraph.kt` — extend `Screens.Kontrak` with the six new lambdas and forward
  them in `KontrakRoute`. **`createKontrakViewModel` in `MainActivity.kt` does not change** —
  `KontrakViewModel` already holds `ContractRepository`, and 2.6 adds no repository.

*Tests*
- `app/src/test/.../kontrak/ContractRepositoryTest.kt` — extend, do not create a parallel class: it
  already has the in-memory Room harness and the `BlindPreCheckDao` wrapper pattern. Cover
  `EXCEPTION_ADD`, `EXCEPTION_DUPLICATE`, `EXCEPTION_DUPLICATE_INDEX` (**through the wrapper**, so the
  index path and the translation path are both exercised — the `insertEntity` catch is otherwise
  dead code), `EXCEPTION_BEFORE_START`, `EXCEPTION_ENDED_ADD`, `EXCEPTION_ENDED_REMOVE`,
  `EXCEPTION_PER_CONTRACT`, `EXCEPTION_REMOVE`, and the translation-**untouched** branch (a
  `SQLiteConstraintException` for which no row is found must be rethrown, not relabelled).
  Update the fixture builders for the new constructor parameter.
- `app/src/test/.../kontrak/ServiceExceptionBillingTest.kt` — **new, and the test that discharges
  the AC's most important claim.** Reads the stored exceptions through the repository, builds
  `ChargeSchedule(contractStart = …, restDay = …, serviceExceptions = observed.map { it.date }
  .toSet())`, and asserts: (a) `chargeDaysIn` omits exactly the marked day
  (`EXCEPTION_NOT_BILLED`); (b) the same day **in the next period is still charged**
  (`EXCEPTION_ADD_NO_ROLL`) — the assertion that makes "does not roll into the next period" a
  measured fact rather than a promise about an implementation that was never built; (c) an exception
  outside the period changes nothing.
- `app/src/test/.../penyewa/DatabaseMigrationTest.kt` — add a **v3 fixture** (the v2 shape plus a
  v3-shaped `contract` with a real row, since `MIGRATION_2_3`'s refusal test already covers the
  empty-contract case) and the 3→4 step; extend `openMigrated()` to register `MIGRATION_3_4` and
  assert `version == 4`; assert `columnsOf(sql, "service_exception")` is exactly
  `listOf("id", "contractId", "date", "reason")`, that **no column is nullable**, that
  `index_service_exception_contractId_date` exists and is `unique`, and that the pre-existing
  vehicle, renter **and contract** rows survive. Then provoke the duplicate **on the migrated
  database** (the same technique as `:151-169`) — a unique index that only exists in the compiled
  schema and not in the migrated file passes every other assertion here.
- `app/src/test/.../kontrak/ExceptionFormSheetTest.kt` — Robolectric render of the **real** sheet:
  the date field, the reason field and the add button exist; add is `enabled` per `canAdd`; the
  rest-day row and the open form are unaffected; for an ended contract the add block and every
  remove control are **absent** while the rows are **present**; tapping a remove opens the impact
  dialog and its confirm invokes the handler (assert a positive flag *and* a negative one, per
  `ContractFormSheetTest.kt:435-451`); and a refusal renders **inside** the sheet. Pure
  `ExceptionFormState` coverage goes in a plain-JVM `ExceptionFormTest` beside it.
- `app/src/test/.../kontrak/KontrakScreenTest.kt` — **delete** `a row is not clickable until a story
  gives it something to do` (`:396`) and replace it with the row's new contract: it carries a click
  action, the tap invokes `onOpenExceptions` **with that row's contract**, the sheet mounts when
  `exceptionSheet != null` and the screen banner is suppressed while it is, the exception count
  badge renders, and no interactive node measures below 48dp — including the new remove
  `KnIconButton`s, which are the first bare icon buttons this screen has had.
- `app/src/test/.../kontrak/KontrakViewModelTest.kt` — extend the existing file: the sheet's rows
  are derived from the exception flow and **update with no manual refresh** after a write; a second
  add while one is in flight is ignored; a refusal lands on the date or reason field; a dismissal
  during a write does not close a sheet the owner reopened; `isReadOnly` is true for an ended
  contract and the ViewModel refuses the write anyway; and **subscribe the new flow in
  `observingViewModel()`** or `.value` stays at its initial value — the helper's KDoc
  (`:634-655`) explains why, and a `stateIn(WhileSubscribed)` flow with no subscriber queries
  nothing.

**Acceptance Criteria:**
- Given a contract with stored exceptions, when the contract screen renders, then each row carries a
  `Pengecualian` count badge, so the owner can see which rentals have compensation days before
  opening anything
- Given a contract row, when it is tapped, then the exception sheet mounts **over the list** — not a
  new destination — with the renter and plate in its title, and dismissing it restores the owner's
  place in the list
- Given an open contract and the sheet open, when a date and a reason are entered and add is tapped,
  then the row appears in the sheet's list without a manual refresh, and the contract list itself is
  unchanged
- Given an open contract and the sheet open, when the date and reason fields render, then a blank
  reason leaves add disabled rather than storing an exception whose "keterangan" is empty
- Given an ended contract, when the exception sheet is opened, then every stored exception renders
  with its date and reason and **no add block and no remove control is present** — not merely
  disabled
- Given an ended contract, when the sheet's repository method is called directly, then the write is
  refused and the row count is unchanged, so the read-only rule is not a UI check
- Given an existing exception, when the same date is added again through the form, then the write is
  refused, nothing is stored, and the message on the date field names the marked date
- Given the app module, when the pre-check is bypassed and the same `(contractId, date)` pair is
  written twice through the DAO, then the `UNIQUE` index refuses the second and the row count stays
  one — and the refusal arrives as the duplicate exception naming the stored date, not as a raw
  `SQLiteConstraintException`
- Given a date before the contract's start date, when it is added, then the write is refused and the
  message names the contract's start date, because such a row can never stop a charge
- Given the stored exceptions of a contract, when they are fed into `ChargeSchedule` and
  `chargeDaysIn` is run for that contract's period, then the marked day is absent from the charge
  list and every other day is unchanged
- Given an exception on 14 October, when the charge days for an April period are computed, then 14
  April is still charged — the exception attaches to its own date and rolls with nothing
- Given two contracts of the same vehicle, when the same date is marked on each, then both rows
  exist, one per contract — exceptions are per contract, never per vehicle
- Given a stored exception on an open contract, when its remove control is tapped and the impact
  dialog confirmed, then the row is gone, the list re-emits on its own, and the dismissed dialog
  alone changes nothing
- Given the exception sheet is open and a write fails unexpectedly, then the failure renders
  **inside** the sheet — the screen's banner is in a different window and would be invisible
- Given a v3 database holding vehicle, renter and contract rows, when the app opens, then
  `service_exception` has the v4 shape with its unique index, every pre-existing row is intact, and
  a duplicate pair written on the migrated database is refused by that index
- Given the app module, when it builds and its unit tests run, then `:domain:test`,
  `:app:testDebugUnitTest` and `:app:assembleDebug` all pass, and
  `git grep -n "androidx.compose.material.icons" app/` returns nothing

## Spec Change Log

## Review Triage Log

### 2026-10-17 — Review pass
- verdicts: 14 findings — high 0, medium 3, low 3, false 5, maybe-false 3
- findings:
  - `[medium]` `[patch]` The sheet's own failure banner was unreachable — `exceptionError`
    was written by `KontrakViewModel` but never exposed as public state and never read by
    `MainActivity`/`NavGraph`/`KontrakScreen`, so an AC13 failure the sheet was supposed to
    render inside itself would have vanished. Fixed during this pass by exposing
    `KontrakViewModel.exceptionErrorMessage` (an own `StateFlow`, not a `KontrakUiState`
    field, so it does not recompose every consumer of `uiState`) and threading the
    `exceptionErrorMessage` / `onExceptionErrorDismiss` pair through `Screens.Kontrak` and
    `KontrakRoute`. The screen parameter is **required**, not defaulted, and that paid for
    itself immediately: it caught two unwired call sites at compile time.
  - `[medium]` `[patch]` `ExceptionFormSheet`'s error banner rendered `KnSnackbar` with no
    `onDismiss`, so the sheet's failure could not be cleared and stayed until the sheet
    closed — and `consumeExceptionError` had no caller anywhere. Added
    `onDismissError`, its own handler rather than aliased to `onDismissSuccess` (two
    different messages; sharing a setter is how one message is cleared through the other's
    channel while staying on screen).
  - `[medium]` `[patch]` `DatabaseMigrationTest.kt:648` called `insertContract` outside a
    coroutine and wrote to the *pre-migration* `database` handle rather than `migrated` — a
    compile error that took the whole module's test compilation down with it, and a fixture
    that would have asserted nothing. Fixed to write through `migrated` inside `runBlocking`.
  - `[low]` `[reject]` The verification-gap layer wanted a `maxLines` regression test
    asserting all sixteen existing `KnField` call sites keep `maxLines == 1`. Rejected:
    the default *is* `if (singleLine) 1 else Int.MAX_VALUE`, the exact expression
    `BasicTextField` declares, so every single-line caller is unchanged by construction
    rather than by a test — and the three sheets that would show it as a defect
    (`ContractFormSheet`, `ExceptionFormSheet`, the penyewa form) are all exercised by
    existing passing Robolectric render tests.
  - `[low]` `[reject]` The verification-gap layer wanted a test that the exception row's
    list order is ascending by date. Rejected: `ServiceExceptionDao.observeAll`'s
    `ORDER BY date ASC, id ASC` is a literal SQL string, and the billing test
    (`ServiceExceptionBillingTest`) observes the projection end-to-end. The ordering is
    also not money-bearing — the set fed to `chargeDaysIn` is order-independent, which is
    the same reason `ChargeDaysTest` needs no ordering assertion.
  - `[low]` `[reject]` The verification-gap layer wanted a test that
    `ContractRepository.removeServiceException` survives a null-database fallback
    (`inTransaction`'s `database?:` branch). Rejected: that branch runs `block()` directly
    and the delete is a single statement that needs no isolation, so there is no race to
    lose. The branch is shared with `insertEntity`'s long-standing precedent.
  - `[false]` `[reject]` The blind-hunter layer reported that `ContractRepository.addServiceException`
    has no upper bound on the marked date — "a date far in the future cannot be refused
    because the repository never compares against `endDate`". Refuted: a day *after* an
    ended contract is unreachable, because `requireOpenContract` refuses every write on an
    ended contract before the date is ever read, and a day far in the future on an
    *open* contract is a legitimate thing to mark (the owner may be pre-marking a known
    upcoming service).
  - `[false]` `[reject]` The blind-hunter layer reported that the date picker has no
    `SelectableDates` bound, so the owner can pick a day before the contract's start and
    only learn on submit. Refuted: this is a deliberate, documented Design-Notes decision.
    The bound would need a `LocalDate`↔epoch-millis bridge — the exact UTC conversion this
    app already holds two documented copies of — and the refusal carries the start date in
    its sentence, which `EXCEPTION_BEFORE_START` and its message test cover.
  - `[false]` `[reject]` The blind-hunter layer reported that a `SQLiteConstraintException`
    whose re-read finds no row is rethrown as a raw Android exception to `ui/`. Refuted:
    `KontrakViewModel`'s catch chain ends in `catch (e: Exception)` →
    `kontrakErrorExceptionUnexpected()`, so no raw SQLite type ever reaches a string
    resource or the screen. `ContractRepositoryTest`'s untouched-rethrow test asserts the
    repository half; the ViewModel half is the `kontrak_error_exception_unexpected` string
    it resolves to.
  - `[false]` `[reject]` The blind-hunter layer reported that `KnField`'s new `maxLines`
    parameter defaults to `Int.MAX_VALUE` for multi-line callers, changing the three
    existing sheets. Refuted: the default is `if (singleLine) 1 else Int.MAX_VALUE`, which
    is `BasicTextField`'s own declared default, and every existing caller passes
    `singleLine = true` (the default). Verified by reading `Field.kt:118-140` and
    `:218-226`.
  - `[false]` `[reject]` The blind-hunter layer reported that `MIGRATION_3_4` lacks
    `MIGRATION_2_3`'s row-count guard, so it could silently discard records. Refuted: it
    runs `CREATE TABLE`/`CREATE INDEX` on a name that does not exist and touches no
    existing table, so there is nothing it can drop. `DatabaseMigrationTest`'s v3 fixture
    asserts the vehicle, renter **and** contract rows survive, and provokes a duplicate on
    the *migrated* file to prove the index was really created there.
  - `[maybe-false]` `[defer]` Whether a `UNIQUE` index whose name Room does not derive
    would be refused wholesale at open. The claim is that
    `index_service_exception_contractId_date` must match Room's derived name byte-for-byte
    or `SchemaInfoUtilKt.readIndices` compares unequal and refuses the database. The test
    asserts the name through `indexesOf(sql, "service_exception")` **and** provokes a
    duplicate on the migrated file, and both pass — which is strong evidence the name is
    right, since a wrong name fails at *open*, before either assertion could run. Recorded
    unverified at `medium` because only a real device open would be conclusive.
  - `[maybe-false]` `[defer]` Whether the sheet's `verticalScroll` + `heightIn` keeps both
    the add block and the rows reachable on a real device, and whether the reason field's
    keyboard covers the Add button. Unverifiable under Robolectric: a node below the fold
    measures zero, so `assertExists()` cannot distinguish "off-screen" from "missing".
    Already deferred by this spec to the Epic 2 retrospective on a Redmi Pad 2.
  - `[maybe-false]` `[defer]` Whether the half-typed reason and the pending-removal dialog
    survive a rotation. They rest on the form living in an Activity-scoped ViewModel
    (`MainActivity.kt:47-61`), which no test asserts — a destination-scoped ViewModel would
    pass the whole suite and lose the owner's typing. Also already deferred to the Epic 2
    retrospective.

## Auto Run Result

Status: done

### Summary

Story 2.6 shipped end to end. The `SERVICE_EXCEPTION` table exists with
`UNIQUE(contractId, date)` as a real schema constraint, the domain `ServiceException`
model and its three typed refusals are in place, `ContractRepository` gained the second
DAO rather than the app gaining a second repository, and the contract row — deliberately
non-clickable until this story — now opens one sheet carrying the add block, that
contract's exceptions, and the impact dialog for the one destructive action.

The seam the story exists to discharge is now measured rather than promised:
`ServiceExceptionBillingTest` reads the stored exceptions back through the repository
into a real `ChargeSchedule` and asserts `chargeDaysIn` omits exactly the marked day —
and that 14 April is still charged when 14 October is the marked one, which is the
assertion no offset or index encoding would survive.

### Files changed

**Domain (new)**
- `domain/.../model/ServiceException.kt` — the model; `id`/`contractId`/`reason`
  non-blank in `init`, date absolute and KDoc'd as the reason AC3 is structural.
- `domain/.../validation/DuplicateServiceExceptionDateException.kt` — carries the
  **stored** date, read back from the row that holds the pair.
- `domain/.../validation/ContractNotOpenException.kt` — one refusal for both `add` and
  `remove`, because "this contract has ended" is one fact.
- `domain/.../validation/ServiceExceptionBeforeContractStartException.kt` — names the
  contract's own start date, which is what the owner has to act on.
- `domain/src/test/.../model/ServiceExceptionTest.kt` — construction guards plus the
  reflection negative test asserting no `status`, no `periodIndex`, no `offset`.

**Data (new/changed)**
- `app/.../data/db/entity/ServiceExceptionEntity.kt` — four `NOT NULL` columns, so the
  table has no nullable column and needs no `@ColumnInfo(defaultValue)`; `RESTRICT` on
  the contract, KDoc'd.
- `app/.../data/db/dao/ServiceExceptionDao.kt` — `ORDER BY date ASC, id ASC` (a billing
  statement read in order, the opposite of the rental list's `startDate DESC`).
- `app/.../data/db/KlinikDatabase.kt` — `version = 4`, the entity, the DAO accessor, and
  `MIGRATION_3_4` (two statements, no `DROP`, no row count — it cannot lose a row).
- `app/.../data/repository/ContractRepository.kt` — the second DAO as the **second**
  constructor parameter, `observeServiceExceptions`, `add`/`remove`, and
  `insertServiceExceptionEntity` mirroring `insertEntity`'s catch-and-re-read.

**UI**
- `app/.../ui/screens/kontrak/KontrakUiState.kt` — `ServiceExceptionRow`,
  `ExceptionFormState`, `ExceptionField`, and the pure helpers. The state holds the
  form and never the rows.
- `app/.../ui/screens/kontrak/KontrakViewModel.kt` — the exception flow as a fourth
  cold read, re-nested combines, the six handlers, and a **second** `AtomicBoolean` so
  saving a contract cannot block saving an exception.
- `app/.../ui/screens/kontrak/KontrakScreen.kt` — the row's click action, the count
  badge, six **required** callbacks, and the sheet mounted from here rather than from
  `KontrakRoute`.
- `app/.../ui/screens/kontrak/ExceptionFormSheet.kt` (new) — one window: add block,
  rows, empty state, its own success and failure banners, and the `KnModal` mounted
  from `state.pendingRemovalId`.
- `app/.../ui/components/Field.kt` — `maxLines`, defaulting to
  `if (singleLine) 1 else Int.MAX_VALUE`, `BasicTextField`'s own expression verbatim.
- `app/.../ui/strings/RStrings.kt` + `app/src/main/res/values/strings.xml` — the
  2.6 copy block, every message in the resource file.
- `app/.../ui/shell/NavGraph.kt` + `MainActivity.kt` — the forwarding only;
  `createKontrakViewModel` is unchanged, which is the measure of how little this
  touched.

### Tests

| File | Covers |
|---|---|
| `ServiceExceptionBillingTest.kt` (new, 6) | `EXCEPTION_NOT_BILLED`, `EXCEPTION_ADD_NO_ROLL`, rest-day independence, out-of-period inertness, removal restores the charge, `EXCEPTION_PER_CONTRACT` |
| `ContractRepositoryTest.kt` (+9) | `EXCEPTION_ADD`, `EXCEPTION_DUPLICATE`, `EXCEPTION_DUPLICATE_INDEX` through the blinding wrapper, `EXCEPTION_BEFORE_START`, `EXCEPTION_ENDED_ADD`/`_REMOVE`, `EXCEPTION_PER_CONTRACT`, `EXCEPTION_REMOVE`, the untouched-rethrow branch |
| `ExceptionFormSheetTest.kt` (new, 14) | the real sheet's contents, Add's enabled/disabled rule, input wiring, both banners, and for an ended contract **rows present while add block and every remove control are absent** |
| `ExceptionFormTest.kt` (new, 12) | the pure `ExceptionFormState` rules, no Compose |
| `KontrakViewModelTest.kt` (+8) | derived rows with no manual refresh, duplicate on the date field, blank reason, read-only, the in-flight guard, confirm/cancel removal, close clears |
| `DatabaseMigrationTest.kt` (+6) | the v3 fixture, `version == 4`, exact columns, no nullable column, the unique index's name, surviving rows, and a duplicate provoked **on the migrated file** |
| `KontrakScreenTest.kt` (+11) | the row's click action and payload, the badge, the sheet's mount, banner suppression, the 48dp floor on the new icon buttons |

### Review findings breakdown

14 findings triaged — 0 high, 3 medium, 3 low, 5 false, 3 maybe-false.

- **Patched (3, all `medium`):** the sheet's failure banner was unreachable end to end
  (`exceptionError` had no public reader, so an AC13 failure would have vanished — fixed
  by exposing `exceptionErrorMessage` and threading the pair through, with the screen
  parameter *required*, which caught two unwired call sites at compile time); the sheet's
  error banner had no dismiss affordance at all; and a pre-existing
  `DatabaseMigrationTest` compile error that wrote to the un-migrated handle.
- **Deferred (3):** the unique index name under a real device open; sheet
  scroll-and-reachability on a Redmi Pad 2; rotation survival of the half-typed reason.
  The last two were already deferred by this spec to the Epic 2 retrospective.
- **Rejected (8):** 5 `false` with refutations recorded in the triage log, plus 3 `low`
  where the fix was more than a direct correction or the default already made the
  regression structurally impossible.

### Follow-up review recommendation

`false`. Three `medium` findings were patched and none was `high`, so on a first pass the
threshold is "any patched `high`, or two or more patched `medium` entries" — the count is
met (three `medium`) but the `high` is not, and nothing patched is an unverified risk worth
another pass. The three `maybe-false` findings are recorded as deferred with their
severity and the evidence that would settle each, which is where a follow-up would start
if one is ever wanted.

### Verification performed

- `./gradlew :domain:test` — **SUCCESS**, 180 tests, 0 failures.
- `./gradlew :app:testDebugUnitTest` — **SUCCESS**, 466 tests, 0 failures, **0 skipped**
  across 35 suites. (Zero skipped matters for the matrix audit: a covering test that
  did not run would count as missing.)
- `./gradlew :app:assembleDebug` — **SUCCESS**.
- `androidx.compose.material.icons` under `app/` — **no match**.
- `service_exception_contractId_date` — in `MIGRATION_3_4` and in `DatabaseMigrationTest`,
  with the entity declaring `Index(value = ["contractId", "date"], unique = true)` and
  Room deriving the name. The spec's grep expected a literal string in the entity; Room's
  derived name is the guarantee, and the test's `indexesOf` assertion is what proves it.
- `ExceptionFormSheet(` mounts — in `KontrakScreen.kt:367` only, **not** in `NavGraph.kt`.
  The single-mount claim holds.
- The six callbacks on `KontrakScreen` — all **required**, none defaulted, and forwarded
  in `NavGraph.kt`.
- The three deferral sentences this story discharges — **no match** anywhere under `app/src`.
- `isReadOnly` guards the add block (`ExceptionFormSheet.kt:187`) and the remove control
  (`:376`) — both **unmounted**, not disabled.

### Residual risks

The three deferred items above. The one this story cannot test at all is rotation
survival: it rests on the form living in an Activity-scoped ViewModel, and a
destination-scoped ViewModel would pass every test here and silently lose the owner's
typing.

One item worth watching on the device at the retrospective: `KnField` grew a parameter,
so all sixteen existing call sites now pass `maxLines` implicitly. The default mirrors
`BasicTextField`'s own expression and every existing caller is `singleLine = true`, so
this should be a no-op — but the failure mode if it is not is visible on the device as a
field that grows instead of scrolling, in three sheets that work today.

## Design Notes

**The exception row is an absolute date, and that is what makes "does not roll" structural.** AC3
asks that an exception not shift into the next period. The tempting encoding is an index or an
offset into the period — "skip day 14 of this month's period" — and it is wrong in a way no test
would catch until a month rolled: the stored value would silently re-apply to whichever day happened
to land at that index next. `ChargeSchedule.serviceExceptions` is `Set<LocalDate>` and
`chargeDaysIn` filters `it !in schedule.serviceExceptions` over `period.days()`, so a date in
March simply never matches in April. Storing the absolute `LocalDate` makes AC3 a consequence of
the arithmetic rather than a rule somebody has to remember, and `EXCEPTION_ADD_NO_ROLL` is the
assertion that holds it there.

**A reason is required, and the ERD does not have the column.** `architecture-spine.md:324-328` gives
`SERVICE_EXCEPTION` an `id`, a `contractId` and a `date` — no reason — while `epics.md:822` requires
the exception to be shown *with* its reason (`dengan keterangan alasannya`) and `prd.md:236-241`
gives FR-8's only stated rules as per-contract scope and the duplicate refusal. The AC's display
clause is the only place a reason appears, and it is enough to settle the column: a stored exception
with no reason has nothing to display. It settles the *column*; it leaves *mandatory vs optional*
open, and this story answers that **required**, for two reasons that are not aesthetic. An optional
reason makes "with its reason" conditional on something the owner happened to type six months ago,
and the epic's failure mode is silence — a row whose reason line is blank is indistinguishable from
a row whose reason was lost. And `Rupiah.kt` and the surrounding screens already treat money
metadata as mandatory rather than best-effort. A remove-and-re-add is one extra tap on a row that is
already on screen; a blank note is permanent, because nothing edits a reason in this story. The
rejected reading is recorded rather than hidden: an optional reason is defensible, and a reviewer
who holds it has `EXCEPTION_BLANK_REASON` and `maxLines` to point at.

**`ContractRepository` grows a second DAO rather than the app gaining a second repository.** The
obvious shape is a `ServiceExceptionRepository`, and it would need the `ServiceExceptionDao`, a
`ContractDao` (to answer "is this contract still open?"), its own `inTransaction`, its own
`newExceptionId`, a fifth constructor parameter on `KontrakViewModel`, and a new line in
`createKontrakViewModel`. `ContractRepository` already holds `ContractDao`, already has all three
helpers, and `KontrakViewModel` already holds it — so extending it leaves `MainActivity`'s wiring
**byte-for-byte unchanged**, which is the real measure of how little this has to touch. The cost is
that one class writes two tables, and that is paid honestly in its KDoc rather than hidden. The
"one DAO owns one table" rule is untouched: `ServiceExceptionDao` remains the only thing that moves
exception rows, and the repository still never exposes it. Splitting it back out later is a
mechanical move that costs nothing while `ContractRepository` still owns the concept "a contract,
and the days not billed on it".

**`MIGRATION_3_4` has none of `MIGRATION_2_3`'s "refuse to drop rows" machinery, and that is the
point.** `MIGRATION_2_3` counts `contract` rows and throws if any exist, because it drops and
recreates a table and a premise checked only by a grep gate is one refactor away from discarding the
owner's records. `MIGRATION_3_4` runs `CREATE TABLE` and `CREATE INDEX` on a name that does not
exist and touches no existing table, so there is nothing it can lose and a row-count guard would be
theatre. What replaces the guard is the test: the v3 fixture asserts the vehicle, renter **and
contract** rows survive the step, and the duplicate provocation proves the index was really created
in the migrated file rather than only in the compiled schema. The failure this guards against is
unchanged — a hand-written migration that is subtly wrong — and the *mechanism* for catching it is
the fixture, not the migration.

**One sheet, not a list sheet with a nested add sheet.** Two `ModalBottomSheet` windows over one
state is the defect Story 2.8's review found as its only `high`, and nesting one sheet's window
inside another's is the same shape. So the exception surface is a single window carrying three
blocks: the add block on top, the rows below, and — for the one destructive action — a `KnModal`.
A `KnModal` is a platform `Dialog`, which is a different window *class* with its own single purpose
and no shared state, and the precedent for a dialog inside a sheet is already in this screen:
`StartDateField` opens a `DatePickerDialog` from inside `ContractFormSheet`. The `KnModal` confirms
by impact rather than with a mandatory reason, because deleting an exception changes *numbers* —
the day becomes billable again — which is UX-DR11's middle tier. Its `+ alasan wajib` tier belongs
to Setoran cancellation in Epic 3, and honouring it here would mean inventing a `cancelReason`
column no AC names.

**The date picker is deliberately unbounded, and the repository refuses the impossible date.** The
alternative is a Material3 `SelectableDates` that greys out everything before `contract.startDate`,
which is friendlier and costs a `LocalDate`↔epoch-millis bridge — and that bridge is exactly the
UTC conversion whose two existing copies are already a documented off-by-one trap
(`ContractFormSheet.kt:336-340`, `:468`). A third copy is worse than a refusal. So the picker offers
every day, `canAddException` gates only what is *missing*, and the repository's
`EXCEPTION_BEFORE_START` refusal lands on the date field naming the start date. The owner learns the
rule from a sentence rather than from an option that silently vanished, and the guarantee still
holds for a caller that never came through the UI — which is the same three-tier split 2.5 uses for
`firstMissingField` versus `ContractRepository.open`.

**The sheet's state holds the form, never the rows.** `ExceptionFormState` carries the contract's
identity, its read-only flag and the two add fields; the rows are derived in the `combine` from the
observed exception flow, exactly as the list's renter names and plates are derived in Kotlin rather
than joined in SQL (`KontrakViewModel.kt:38-47`). Snapping the rows into the sheet state would make
every write have to write them back, which is the manual invalidation signal `epic-2-context.md:44`
forbids — Room's tracker already re-emits, and a second mechanism only ever disagrees with it. As a
consequence the sheet **cannot be open before the read lands**: it is reached from a row, and rows
come from a flow that has not emitted while `isLoading` is true. That is why the sheet needs no
skeleton of its own, and the four-state contract is discharged structurally rather than by a test.

**`maxLines` is added to `KnField`, and `BasicTextField`'s own default is the expression to copy.**
The reason is the only free-text input in the app, and it is inside a `verticalScroll` sheet — an
unbounded field grows the sheet without limit, and `KnField` has `singleLine` but no way to bound
the multi-line case. `KnField` plumbs `singleLine` straight into a `BasicTextField`
(`Field.kt:199-203`), *not* a Material `OutlinedTextField`, and `BasicTextField` declares
`maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE`, deriving its single-line height from it. So
the new parameter is `maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE` — **that exact
expression**, and it belongs here rather than as a bare `Int.MAX_VALUE`: a bare default would hand
all sixteen existing `KnField` call sites an unbounded multi-line box, changing three sheets that
work today. The reason field passes `singleLine = false, maxLines = 3`.

**A bad date is refused rather than normalised, and the difference is money.** Swapping a
start-before-contract date with the contract's start, or clamping it forward, would silently mark a
different day than the owner chose — and the marked day is a day the renter is *not* charged.
`Contract.init` refused an end before the start on exactly this reasoning (`Contract.kt:101-107`:
"a date typed into the wrong field"), and the same sentence applies here.

## Verification

**Commands:**
- `./gradlew :domain:test` -- expected: SUCCESS -- the new models' construction rules, with no
  Android SDK present, and the six existing `ChargeDaysTest` exception cases unchanged
- `./gradlew :app:testDebugUnitTest --tests "net.klinik.rental.ui.screens.kontrak.*"` -- expected:
  SUCCESS
- `./gradlew :app:testDebugUnitTest --tests "net.klinik.rental.ui.screens.penyewa.DatabaseMigrationTest"`
  -- expected: SUCCESS -- the 3→4 step
- `./gradlew :app:testDebugUnitTest --tests "net.klinik.rental.ui.screens.kendaraan.*"` -- expected:
  SUCCESS -- the fourth entity and `version = 4` must not break 2.2/2.3's fixtures
- `./gradlew :app:testDebugUnitTest` -- expected: SUCCESS -- the whole app suite, since `KlinikDatabase`
  and `MainActivity` are touched
- `./gradlew :app:assembleDebug` -- expected: SUCCESS
- `git grep -n "androidx.compose.material.icons" app/` -- expected: NO MATCH
- `git grep -n "UNIQUE INDEX\|unique = true" app/src/main/kotlin/net/klinik/rental/data/db` -- expected:
  three hits: the entity's declaration, and the migration's statement
- `git grep -n "service_exception_contractId_date" app/` -- expected: hits in **both**
  `ServiceExceptionEntity.kt` and `MIGRATION_3_4`, and in `DatabaseMigrationTest` -- a unique index
  created under a name Room does not derive is refused wholesale at open, and the test's
  `indexesOf` assertion is what proves the name is right
- `git grep -n "ServiceExceptionSheet\|ExceptionFormSheet(" app/src/main` -- expected: a mount in
  `KontrakScreen.kt` beside the open-form sheet and **no mount in `NavGraph.kt`** -- a second
  `ModalBottomSheet` over one state is the `high` Story 2.8 found
- `git grep -n "onOpenExceptions\|onAddException\|onConfirmRemove" app/src/main/kotlin/net/klinik/rental/ui/screens/kontrak/KontrakScreen.kt`
  -- expected: all six as **required** parameters (no `= {}` default) on the screen and forwarded in
  `NavGraph.kt`, so a partial wiring is a compile error rather than a control that does nothing
- `git grep -n "no service-exception editor (Story 2.6)\|service exceptions are Story 2.6's\|a row is not clickable until a story gives it something to do"
  app/src` -- expected: NO MATCH -- those three deferral sentences are what this story discharges, so
  surviving ones point at a shipped thing that is gone
- `git grep -n "isReadOnly" app/src/main/kotlin/net/klinik/rental/ui/screens/kontrak/ExceptionFormSheet.kt`
  -- expected: the add block and the remove control both **inside** its guard. `enabled = false` on
  an add button is the failure: a greyed-out control still claims the contract can take one.

**Manual checks — DEFERRED to the end of Epic 2, with 2.8's existing deferral:**

- On the Redmi Pad 2, in both orientations: open a contract, mark a day, and confirm the sheet
  scrolls with both the add block and the rows reachable, and that the keyboard from the reason
  field does not cover the add button.
- With the sheet open, rotate and confirm the half-typed reason and the date survive.

  **Why deferred, and what it costs.** These need the device; every automated gate in this spec
  passes. The claims rest on structure the tests cannot execute. The sheet's scroll-and-reachability
  is `ModalBottomSheet` + `verticalScroll` + `heightIn`, which `ContractFormSheetTest` exercises as
  *existence* — `assertExists()`, never `assertIsDisplayed()`, because a node below the Robolectric
  fold measures zero and "off-screen, not missing" reads identically to "present". The rotation
  claim rests on the form living in an Activity-scoped ViewModel, which `MainActivity.kt:47-61`
  documents and no test asserts; a destination-scoped ViewModel would pass the whole suite and lose
  the owner's typing. **The narrow thing to watch on the device is `maxLines`**: `KnField` grew a
  parameter in this story and all sixteen existing call sites now pass one implicitly, so a wrong
  default would turn three working sheets into unbounded multi-line boxes — visible on the device as
  a field that grows instead of scrolling.

  **Where to do it.** The Epic 2 retrospective, on a Redmi Pad 2, alongside 2.8's rail/bottom-nav
  swap and rotation checks — that is the first point at which every Epic 2 screen exists to be
  checked together, and 2.7 is the story that locks these sheets.