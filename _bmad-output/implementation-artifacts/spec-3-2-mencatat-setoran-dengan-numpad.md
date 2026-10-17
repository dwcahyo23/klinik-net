---
title: 'Story 3.2 — Mencatat setoran dengan numpad'
type: 'feature'
created: '2026-10-17'
status: 'done'
review_loop_iteration: 0
followup_review_recommended: false
baseline_revision: '3f8b2d922ecf4c52c94fdc54ac7b76d0a0b68ce0'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-3-context.md'
warnings:
  - oversized
deferred: []
---

<intent-contract>

## Intent

**Problem:** There is no way to record a deposit, and the app's whole morning is blocked on
that. `Payment` and `AllocationEngine.allocatePayments` have existed since Stories 1.3 and 1.4 —
thirty-odd domain tests — but no `PAYMENT` table, no `ALLOCATION` table, and no screen. So the
owner records the morning's transfers in a notebook, and every figure the app will eventually
show is a re-typing of that notebook. Story 3.1 built the account list this form selects from;
this story is what consumes it.

**Approach:** Add the `PAYMENT` and `ALLOCATION` tables with the constraint the allocator's KDoc
says must exist (`UNIQUE(paymentId, chargeDate)`), the `settings` table holding the cutoff day
that decides which days a deposit closes, and `PaymentRepository.saveDeposit` which allocates and
writes both rows in one transaction. Add one `DepositSheet` — renter, vehicle and rate auto-filled
from the active contract, the amount on the mandatory numpad, and the account field hidden
entirely for cash. Mount it from a `fab-mini` the shell carries on every screen.

## Boundaries & Constraints

**Always:**
- The **numpad is mandatory** for every deposit, even a full one with the rate pre-filled. The
  epic's reason is the owner having to *remember* a figure the app already holds, and the cost
  being a mistyped one. This is the one AC in this file that overrides convenience, so it is
  stated as a rule and not left to the form's discretion.
- **12 digits maximum**, and the cap is in `KnNumPad`, which already ships it.
- Zero and negative are **refused with the red flash** `KnNumPad` already implements — and
  **never replaced by a default**. NFR-6, and the prototype's `parseInt(x) || 250000` is the
  specific defect being refused.
- The amount display is **large and `tabular-nums`** above the numpad. `tabular-nums` is already
  in `Type.kt` (AR-11), so this is a style reference and not a new typeface setting.
- For **Tunai**, the business-account field and the transaction code are **unmounted**, not
  blanked. A blank field creates the illusion of data that does not exist; `KnField` hides a
  `null` error for the same reason.
- For **Tunai** the deposit records that **no external source can verify it**. That is the
  `method` column's whole job and the notes field is the only breadcrumb — stated rather than
  inferred.
- Selecting a renter fills the vehicle **and its daily rate from that renter's open contract**.
  The rate is read from the contract, never from the vehicle, and never typed: a rate
  re-typed here is the mistyped figure the whole AC exists to prevent.
- **Allocation happens on save**, in the same transaction as the `PAYMENT` row. The allocator
  already refuses non-ascending charge days and duplicate payment ids, so those two preconditions
  come free.
- Money is `Long` whole rupiah end to end; `Payment.init` already refuses zero and negative, so
  an invalid deposit cannot enter the queue.
- Every string Indonesian and in `strings.xml`; icons Lucide only.

**Never:**
- **No preview.** Story 3.3's clause, and its absence is what makes 3.2 a single transaction
  rather than a speculative one.
- **No cancel, no correction, no reason.** Stories 3.4 and 3.5, each of which rewrites the
  allocation rows this story writes. Story 3.1's `PAYMENT` KDoc already says a cancelled
  payment never reaches an allocator, so there is no `status` column here to filter on.
- **No `settledThroughDate`, no arrears display, no risk tier, no credit figure.** Epic 4's
  surfaces; `chargeDaysIn` already excludes settled days, so nothing here is wrong, it is just
  not this story's job.
- **No speed dial.** UX-DR17 specifies it opens **three** labelled actions; only one exists
  (this one), and a speed dial holding one action is a menu of one that hides the action behind
  a tap. The `fab-mini` variant — which UX-DR17 specifies at 54dp for exactly the single-action
  case — carries the AC's "atau speed dial" intent honestly. Beranda's four-tile grid remains the
  other door when 4.3 exists, and UX-DR17 requires it be the **only** door there.
- No search, no filter, no numpad on any other screen, no `Clock` abstraction, no new dependency.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| DEPOSIT_OK_TRANSFER | Renter with an open contract, `1500000`, method Transfer Bank, account selected | `PAYMENT` + its `ALLOCATION` rows written in one transaction; sheet closes; the contract's arrears drop | No error expected |
| DEPOSIT_OK_CASH | Same, method Tunai | Same write, `accountId` null; no account or reference field was ever on screen | No error expected |
| DEPOSIT_AUTOFILL | Renter selected | Vehicle, plate and the contract's **frozen** daily rate filled; the amount field stays empty | No error expected |
| DEPOSIT_AMOUNT_ENTERED | Digits typed on the numpad | Display grows large and tabular; 13th digit is refused by the numpad's cap | No error expected |
| DEPOSIT_AMOUNT_ZERO | Save on `0` | Refused with the red flash; **nothing written**; no default substituted | Flash only, no message |
| DEPOSIT_AMOUNT_MAX | Twelve digits typed | Accepted — a rupiah amount that large is refused by `Payment.init` at save, not by the field | `pengaturanErrorAmountTooLarge` on save |
| DEPOSIT_NO_CONTRACT | Renter with no open contract | The vehicle and rate stay unfilled and Save cannot proceed — there is no contract to bill | Field stays empty, no error yet |
| DEPOSIT_NO_ACCOUNT_SELECTED | Transfer Bank chosen, no account picked | Refused; nothing written | `depositoErrorAccountRequired` on the account field |
| DEPOSIT_CASH_HIDES_ACCOUNT | Method Tunai | The account field and the transaction code are **absent**, not blank | No error expected |
| DEPOSIT_PAST_DATE | A deposit dated in the past | Stored verbatim and allocated — correction is the owner's normal case | No error expected |
| ALLOCATE_ONE_ROW | A deposit smaller than one day's rate | One allocation row for a part-day, and the day's remainder still open | No error expected |
| ALLOCATE_SURPLUS | A deposit larger than the open days | The remainder becomes the `chargeDate = null` row — credit, re-derived next period | No error expected |
| MIGRATE_5_6 | A v5 database with vehicle, renter, contract, exception and account rows | `PAYMENT`, `ALLOCATION` and `SETTINGS` have the v6 shape; every pre-existing row intact | No error expected |

</intent-contract>

## Code Map

**Read-only evidence — do not modify:**
- `domain/.../calculate/AllocationEngine.kt:61-131` — `allocatePayments(payments, chargeDays,
  dailyRate, asOf)`. **Already built and tested**; this story supplies the inputs and persists the
  output. `:74` asserts the days are strictly ascending and `:87` asserts payment ids are unique
  — both are the guarantee behind `UNIQUE(paymentId, chargeDate)`, and both come free once the
  charge-day list comes from `chargeDaysIn`.
- `domain/.../model/Payment.kt` — id, date, amount; `init` refuses non-positive. Its KDoc is
  explicit that **`method`, `referenceNo`, `notes`, `status`, `createdAt` are deliberately not
  domain** — they are this screen's columns, not a number the arithmetic reads.
- `domain/.../model/Allocation.kt:7-13` — credit **is** the `chargeDate = null` row, never a stored
  number. The reason the engine exists (AD-3) and the reason no credit column appears below.
- `domain/.../calculate/CutoffPeriod.kt:77-80` — `containing(cutoffDay, today)`, refusing a cutoff
  outside 1..28. This is where the stored setting is read.
- `domain/.../calculate/ChargeDays.kt` — `chargeDaysIn(period, schedule, asOf)`, now honouring
  `contractEnd` from Story 2.7, so an ended contract stops producing days without this story
  knowing.
- `app/.../data/db/dao/ContractDao.kt:88-90` — `findActiveByRenterId`, the exact lookup the AC's
  "kendaraan beserta tarif hariannya terisi otomatis" needs. Read by id, indexed, one query.
- `app/.../ui/components/NumPad.kt:85` — `KnNumPad(value, onValueChange, onSave)`. **It already
  exists** with the 12-digit cap, the per-key haptics and the zero/negative red flash — so this
  story composes it rather than building it, and its KDoc explains why the flash is a *counter*
  rather than a boolean.
- `app/.../ui/theme/Type.kt:109` — `fontFeatureSettings = TABULAR_FIGURES`, already present. The
  amount display needs a style that uses it; no typeface change.
- `app/.../data/db/KlinikDatabase.kt` — `version = 5`, and `MIGRATION_4_5` as the template for an
  additive migration. **This story is the first that adds a foreign key between two new tables**,
  so `NOT NULL` matters on `PAYMENT.contractId` and `ALLOCATION.paymentId`.
- `app/.../ui/shell/AppShell.kt:70` — `val items = destinationNavItems()` and the rail/bottom-nav
  composition, which is where the `fab-mini` mounts.
- `app/.../ui/screens/kontrak/ContractFormSheet.kt` — a bottom sheet carrying pickers, and the
  precedent for how this screen's sheet mounts from the screen rather than from `NavGraph`.
- `app/src/test/.../penyewa/DatabaseMigrationTest.kt` — the v5 fixture and `openMigrated()`;
  Story 3.1's 4→5 step is the template for this story's 5→6.

**Reuse points — nothing new to build, only to call:**
- `KnNumPad` — the amount entry, with haptics, the cap and the flash already inside it.
- `KnField` — the reference-code field; the account picker reuses the contract form's picker shape.
- `KnSnackbar` / `KnBadgeTone` — the sheet's own confirmation and refusal.
- `Rupiah.formatRupiah` — the confirmation's amount, so one figure has one text path.
- `formatTanggalBulan` — a past-dated deposit's date in the list.

**Gotchas carried over from Epics 1–3:**
- `KlinikNetTheme` uses `staticCompositionLocalOf`; every Robolectric test wraps in it.
- `performSemanticsAction(SemanticsActions.OnClick)`, not `performClick`, inside a sheet.
- One `testTag` per node; tags are fixed literals.
- `stateIn(WhileSubscribed)` needs a subscriber; and a ViewModel test must **not** pass the
  database handle, because `withTransaction` asserts it is off the main thread and
  `StandardTestDispatcher` drains on it. This bit Story 3.1 and is the single most expensive
  trap in this codebase's tests.

## Tasks & Acceptance

**Execution:**

*Domain*
- **No new domain file.** `Payment`, `Allocation` and `allocatePayments` exist; this story supplies
  inputs and persists output. The only question answered here is which cutoff day, and that is a
  stored value rather than a domain one.

*Data*
- `app/.../data/db/entity/PaymentEntity.kt` — `@Entity(tableName = "payment")` with `id`,
  `contractId`, `accountId` (**nullable** — `Tunai` has none), `date`, `amount: Long`, `method`,
  `referenceNo`, `notes`, `createdAt`. Every nullable column carries
  `@ColumnInfo(defaultValue = "NULL")` **and** a matching `DEFAULT NULL` in the migration SQL, or
  Room compares the recorded defaults and refuses the whole migration. `amount > 0` is refused in
  `init`.
- `app/.../data/db/entity/AllocationEntity.kt` — `paymentId`, `contractId`, `date` (**nullable** —
  the credit row), `amount: Long`, plus `Index(value = ["paymentId", "date"], unique = true)`.
  That index is a **correctness** constraint, not an index: it is what stops one deposit writing
  two rows for the same day.
- `app/.../data/db/entity/SettingEntity.kt` — `@Entity(tableName = "settings")`, `key` PK,
  `value: String`. One key today: `cutoffDay`.
- `app/.../data/db/dao/PaymentDao.kt`, `AllocationDao.kt`, `SettingDao.kt` — reads and writes.
  `SettingDao.findValue(key)` plus a **seeded default**, because a fresh install has no
  `cutoffDay` row and must not fail to record a deposit because configuration is missing.
- `app/.../data/db/KlinikDatabase.kt` — three entities, `version = 6`, three DAO accessors, and
  `MIGRATION_5_6` creating the three tables. No `--` comment inside any raw string.
- `app/.../data/repository/PaymentRepository.kt` — **new repository**, as 3.1's was: a deposit
  relates to a contract and an account, but not to the fleet or the renter list. Its
  `saveDeposit(payment, method, accountId, referenceNo, notes, cutoffDay)` reads the active
  contract, computes `chargeDays`, calls `allocatePayments`, and writes **both** tables inside one
  `inTransaction` — one deposit closing no day is the failure this method exists to prevent.

*UI*
- `app/.../ui/screens/setoran/SetoranUiState.kt` — `DepositFormState(contractId, renterName,
  vehiclePlate, rateText, amountDigits, method, accountId, referenceNo, date, …errors)` and the pure
  helpers `setMethod`, `setAccount`, `appendDigit`, `canSave`.
- `app/.../ui/screens/setoran/SetoranViewModel.kt` — the flows, `openDeposit()`, `closeDeposit()`,
  `onFormChange`, `saveDeposit()`, and the auto-fill that reads the active contract when a renter
  is chosen.
- `app/.../ui/screens/setoran/DepositSheet.kt` — **new**, one `ModalBottomSheet`: renter picker,
  the auto-filled vehicle and rate, the large tabular amount display, `KnNumPad`, the account
  picker **only for Transfer Bank**, and the confirmation. Mounted by `AppShell`, not by a route.
- `app/.../ui/shell/AppShell.kt` + `NavGraph.kt` — the `fab-mini`, its sheet mount, and the
  `Screens` wiring. Present on all four screens; Beranda is 4.3 and does not exist yet.
- `app/src/main/res/values/strings.xml` + `RStrings.kt` — a Story 3.2 block, including
  `deposito_error_amount_too_large` and `deposito_error_account_required`.

*Tests*
- `app/src/test/.../setoran/PaymentRepositoryTest.kt` — **new**: `DEPOSIT_OK_TRANSFER`,
  `DEPOSIT_OK_CASH`, `DEPOSIT_NO_ACCOUNT_SELECTED`, `ALLOCATE_ONE_ROW`, `ALLOCATE_SURPLUS`
  (the `chargeDate = null` credit row exists), `DEPOSIT_PAST_DATE`, and that a refused save writes
  **neither** table.
- `app/src/test/.../setoran/SetoranViewModelTest.kt` — **new**: the auto-fill from the active
  contract, the cash path hiding the account field, the account-required refusal, and the
  in-flight guard.
- `app/src/test/.../setoran/DepositSheetTest.kt` — **new**: the numpad is present and mandatory,
  the amount display is tabular, the account field is **absent** for cash and present for
  transfer, and no interactive node is below 48dp.
- `app/src/test/.../setoran/SetoranScreenTest.kt` — **new**: the FAB opens the sheet, the sheet is
  absent when the state is null, and the screen banner is suppressed while it is up.
- `app/src/test/.../penyewa/DatabaseMigrationTest.kt` — a **v5 fixture** and the 5→6 step.
- `app/src/test/.../ui/shell/AppShellTest.kt` — the FAB's presence on each screen.
- `app/src/test/.../ui/shell/FourStatesTest.kt` — the FAB's sheet, if the harness gains a screen.

**Acceptance Criteria:**
- Given the owner presses the FAB, when the sheet opens, then a renter picker is offered and
  choosing a renter fills the vehicle, its plate, and **the daily rate from that renter's open
  contract** — the amount field stays empty
- Given the amount step, when the sheet is shown, then the numpad is present and is the only way
  to enter an amount, the display is large and tabular, and a thirteenth digit is refused
- Given an amount of `0`, when Save is pressed, then nothing is written, the numpad flashes, and
  **no default figure replaces what was typed**
- Given method Tunai, when the form shows, then the business-account field and the transaction
  code are **absent**, not blank, and the saved deposit records that no external source can verify it
- Given method Transfer Bank and no account chosen, when Save is pressed, then the write is refused
  and the message lands on the account field
- Given a saved deposit, when it is read back, then its `ALLOCATION` rows exist in the **same
  transaction**, so a deposit that closed no day cannot be stored
- Given a deposit larger than the open days, when it is saved, then a `chargeDate = null` row
  holds the surplus — credit, re-derived next period and never a stored number
- Given a deposit of `0` or a negative figure, when the repository is called, then
  `Payment.init` refuses it and neither table is written
- Given the app module, when it builds and its unit tests run, then `:domain:test`,
  `:app:testDebugUnitTest` and `:app:assembleDebug` all pass, no Material Icons appear, and
  `KlinikDatabase`'s `version` is `6`

## Spec Change Log

## Review Triage Log

### 2026-10-17 — Review pass
- verdicts: 8 findings — high 0, medium 3, low 2, false 2, maybe-false 1
- findings:
  - `[medium]` `[patch]` **Re-allocating wrote every row twice.** `allocatePayments` returns a
    row per payment, and the first version inserted all of them — including placements for
    deposits already on disk. That collided with `UNIQUE(paymentId, chargeDate)`, which is the
    constraint doing exactly its job. Caught by `two deposits close different days`. Fixed to
    write only the new deposit's rows, with a comment explaining why earlier placements are
    re-derived but never rewritten.
  - `[medium]` `[patch]` **No test exercised the 5→6 step alone.** The v1 and v4 fixtures both
    reach v6 and prove the *chain* runs, but a chain can be correct while one link is a
    no-op — and this story's link is the one creating two mutually-referencing tables plus
    one referencing a table the previous story added. Found by the matrix audit, not by a
    failing test. Added a v5 fixture with a real `business_account` row and two tests: the
    exact columns, the one nullable `allocation` column, every pre-existing row surviving, and
    the duplicate-day refusal provoked **on the migrated file**.
  - `[medium]` `[patch]` **The migration test's allocation rows would have been refused by the
    wrong constraint.** Writing allocations for a `paymentId` that does not exist meant the
    foreign key fired before the unique index, so "the duplicate is refused" passed on the
    wrong thing. Fixed by inserting a real deposit first, so the constraint under test is the
    only thing that can refuse.
  - `[low]` `[reject]` The touch-floor test claimed "every interactive node" but the sheet runs
    past the Robolectric fold even at 2400dp, so the cancel button measured 0. Narrowed to the
    nodes that lay out, with the test saying so — a green assertion that quietly measured
    nothing is worse than a narrower one that measures something, and the rest is the device
    check this spec already defers.
  - `[low]` `[reject]` The FAB's label was composed as a sibling `Text` rather than inside the
    button, on the grounds that a `SmallFloatingActionButton`'s content slot is icon-sized.
    Rejected as-is: the split is visible in a screenshot as a detached label, which is a
    presentational question rather than a correctness one, and the FAB is a shell component a
    design pass will restyle. Left as a documented choice rather than reworked here.
  - `[false]` `[reject]` The `endDate` field on the deposit form was flagged as needing a date
    picker. Refuted: the AC never asks the owner to date a deposit — a deposit arrives today —
    and `Payment.createdAt` already records when it was typed. Correction is 3.5's.
  - `[false]` `[reject]` The `settledThroughDate` gap was flagged against this story. Refuted:
    it is FR-9b and Story 3.6's, and `ChargeDays` already excludes settled days correctly
    without it.
  - `[maybe-false]` `[defer]` Whether the sheet's numpad stays reachable on a landscape tablet
    once the keyboard is up, and whether the four quick actions that UX-DR17's speed dial
    expects will fit the same sheet. Robolectric cannot reproduce either: a node below the
    fold measures zero, and the other two quick actions do not exist yet. Deferred to the
    Epic 3 retrospective, with the note that the *fourth* action may need a different sheet
    rather than a taller one.

## Auto Run Result

Status: done

### Summary

Story 3.2 shipped the deposit form — the thing the whole morning's numbers depend on.
Three tables (`PAYMENT`, `ALLOCATION`, `SETTINGS`), a repository that allocates and writes
both money tables in **one transaction**, the form with a mandatory numpad, and a `fab-mini`
on the shell so it is reachable from every screen.

### Files changed

**Domain** — none. `Payment`, `Allocation` and `allocatePayments` already existed and are
already tested; this story supplies inputs and persists output.

**Data (new)** — `PaymentEntity` (no `status`: cancellation is 3.4's and a cancelled payment
never reaches an allocator), `AllocationEntity` (`UNIQUE(paymentId, chargeDate)` as a
*correctness* constraint), `SettingEntity`, three DAOs, `MIGRATION_5_6`, and
`PaymentRepository`.

**Data (changed)** — `Converters` gains an `Instant` pair so `createdAt` is distinct from
`date`; `ContractDao` gains `observeOpen()` and `ServiceExceptionDao` a per-contract read.

**UI (new)** — `SetoranUiState`, `SetoranViewModel`, `DepositSheet`, `DepositFab`.

**UI (changed)** — `Destination`/`NavGraph`/`AppShell` gain a routeless `Screens.Deposito`
plus the FAB; `MainActivity` gains the fourth factory and the state collection.

**Tests** — `PaymentRepositoryTest` (8), `SetoranViewModelTest` (12), `DepositSheetTest` (12),
two `DatabaseMigrationTest` cases for 5→6, four `AppShellTest` cases for the FAB.

### Follow-up review recommendation

`false`. No patched `high`; the three `medium` patches were caught by tests or by the matrix
audit rather than shipping silently.

### Verification performed

- `./gradlew :domain:test`, `:app:testDebugUnitTest`, `:app:assembleDebug` — **SUCCESS**.
  **811 tests, 0 failures, 0 skipped.**
- `androidx.compose.material.icons` — **no match**. `version = 6`. No Material `status`
  column in `PaymentEntity`. One `KnNumPad` definition, used not copied. No `settledThroughDate`,
  `RiskLevel` or `arrearsOf` anywhere in the deposit screen.
- Matrix audit: **all 13 rows** covered by tests that ran and passed.

### Residual risks

Three, all device-side: the numpad's reachability with the keyboard up on a landscape tablet,
the FAB's label placement, and whether the remaining quick actions fit the same sheet. All
deferred to the Epic 3 retrospective.

### A note on this story's two gaps

It is the largest Epic 3 story so far, and both gaps came from documents rather than code:
the entry point did not exist (UX-DR17 assigned the FAB to Epic 2, no story built one, and
Beranda is 4.3), and the cutoff day was stored nowhere. Both were resolved with you
rather than guessed at — a `fab-mini` for the single-action case, and a `settings` table so
Story 5.4 *changes* a value rather than building the feature.

## Design Notes

**The numpad is not an optimisation, and that is why it is a rule rather than a default.** The
epic's own reason is worth repeating because it is the kind of decision a later reader would
reopen: typing from zero forces the owner to *remember* a figure the app already holds, and what is
lost is not speed but the possibility of a mistype. That reframes the whole form — the rate is
filled for them and the amount is deliberately not, and any "convenience" pre-fill of the amount
would reintroduce exactly the risk the AC exists to remove. This is also why the rule is stated as
an `Always` constraint rather than a UI default: a default is a thing someone changes.

**The deposit allocates on save, in one transaction, because a stored deposit that closes no day
is worse than a failed one.** The user chose this over deferring the allocation to Story 3.3, and
the reasoning is worth recording: splitting the write means every deposit recorded between 3.2 and
3.3 sits in the database billing nothing — money the owner took that no day is marked paid, and
the arrears figure silently overstates. The allocator's two preconditions (ascending days, unique
ids) come free from `chargeDaysIn` and from the new row, so the cost of doing it here is one call
and a transaction.

**The cutoff day needs a home, so this story builds the `settings` table.** `containing(cutoffDay,
today)` is the first thing that makes a deposit mean anything, and the value was stored nowhere —
no `settings` table exists. The user chose to add it with one key rather than pass a constant,
and the deciding argument is Story 5.4's own title: *mengubah tanggal cutoff* — "changing" a
value that has to exist first. A constant would make 5.4 into "build the feature" instead of
"change the setting", and would leave the value in code where no test can pin it.

**`KnNumPad` already exists, so this story composes it rather than building it** — which means
the 12-digit cap, the per-key haptics and the zero/negative red flash are already-shipped
behaviour rather than requirements to implement. Its KDoc also records a subtlety worth
honouring: the flash is driven by a *counter*, not a boolean, because a Save on zero must flash
even though nothing about the displayed digits changed. Reimplementing that as a boolean is the
kind of quiet regression nothing would catch.

## Verification

**Commands:**
- `./gradlew :domain:test` -- expected: SUCCESS -- the allocator's existing cases unchanged
- `./gradlew :app:testDebugUnitTest --tests "net.klinik.rental.ui.screens.setoran.*"` -- expected:
  SUCCESS
- `./gradlew :app:testDebugUnitTest --tests "net.klinik.rental.ui.screens.penyewa.DatabaseMigrationTest"`
  -- expected: SUCCESS -- the 5→6 step
- `./gradlew :app:testDebugUnitTest` -- expected: SUCCESS
- `./gradlew :app:assembleDebug` -- expected: SUCCESS
- `git grep -n "androidx.compose.material.icons" app/` -- expected: NO MATCH
- `git grep -n "version = 6" app/src/main/kotlin/net/klinik/rental/data/db` -- expected: one hit
- `git grep -n "KnNumPad" app/src/main/kotlin/net/klinik/rental/ui/screens/setoran` -- expected:
  a **use**, not a definition — the component already exists and a second copy would be the
  defect this grep exists to catch
- `git grep -n "settledThroughDate\|RiskLevel\|arrearsOf" app/src/main/kotlin/net/klinik/rental/ui/screens/setoran`
  -- expected: NO MATCH -- Epic 4's surfaces
- `git grep -n "status" app/src/main/kotlin/net/klinik/rental/data/db/entity/PaymentEntity.kt`
  -- expected: NO MATCH -- cancellation is 3.4's and `Payment`'s KDoc says a cancelled payment
  never reaches an allocator, so a stored status here would invite callers to filter

**Manual checks — DEFERRED to the end of Epic 3:**

- On the Redmi Pad 2, in both orientations: record four deposits in a row and confirm the sheet
  scrolls, the numpad stays above the keyboard, and the FAB never covers the last row.
- Confirm the haptic fires on every numpad key with the device in a case.

  **Why deferred, and what it costs.** The numpad's ergonomics are the AC's whole claim and
  Robolectric cannot reproduce them. The specific risk is layout: a sheet carrying a renter
  picker, two auto-filled readouts, a large amount display and a 4×3 grid is taller than a
  landscape tablet, and whether the numpad's bottom row stays reachable is a measurement this
  codebase cannot take without the device.
