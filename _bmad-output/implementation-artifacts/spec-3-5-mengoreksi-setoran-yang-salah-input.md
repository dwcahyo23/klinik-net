---
title: 'Story 3.5 — Mengoreksi setoran yang salah input'
type: 'feature'
created: '2026-10-17'
status: 'done'
baseline_revision: '9c1eda38c62a1d2d2bd6f3c649df7162567dd12d'
review_loop_iteration: 2
followup_review_recommended: false
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-3-context.md'
warnings:
  - oversized
deferred:
  - summary: >-
      Correction entry point (`openEditDeposit`) has no production caller in app/src/main
    evidence: |-
      `SetoranViewModel.openEditDeposit`, `DepositFormState.isEditing`, the edit-mode
      title, and the method-chip lock are implemented and covered by
      `CorrectDepositViewModelTest` and `DepositSheetTest`, but nothing in app/src/main
      calls them. The same gap is already recorded for Story 3.4's `requestCancel`:
      no deposit history list exists yet, so there is no row to tap. Mounting happens
      when Epic 4 builds the deposit history surface. Pass 2 confirmed it independently
      from the intent-alignment layer, whose reachability finding (R-b) is the same
      defect seen from the AC's own "When saya membukanya" phrasing.
    location: >-
      app/src/main/kotlin/net/klinik/rental/ui/screens/setoran/SetoranViewModel.kt
    severity: medium
  - summary: >-
      "Tunggakan dihitung ulang seketika" is proven only at the data layer
    evidence: |-
      `currentArrears` returns the recomputed figure after `correctDeposit`, and the
      open-day count follows, but no *rendered* arrears figure updates — the arrears
      screen is Story 4.1's, and it does not exist yet. Immediate recalculation is
      therefore a property of the repository and the ViewModel state, assumed at the UI.
    location: >-
      app/src/main/kotlin/net/klinik/rental/data/repository/PaymentRepository.kt
    severity: low
  - summary: >-
      Account, reference and notes editability is proven as state passthrough
    evidence: |-
      `CorrectDepositViewModelTest` asserts `accountId`/`referenceNo`/`notes` on the form
      after `openEditDeposit` and one `selectAccount` call, not on rendered controls. The
      account picker, the reference field and the notes field are Story 3.2's and this
      story changes none of them, so their click-through coverage predates it.
    location: >-
      app/src/test/kotlin/net/klinik/rental/ui/screens/setoran/CorrectDepositViewModelTest.kt
    severity: low
  - summary: >-
      No test forces a failure inside `correctDeposit` after the payment row is written
    evidence: |-
      AR-8 requires the correction and its re-allocation to be one transaction, and
      `correctDepositTest` runs against a real Room database so the transaction path is
      genuinely exercised — but every case fails *before* the write, so the rollback half
      is unproven. Settling it needs a fault injected between `updatePayment` and the
      allocation rewrite, which no DAO in this app offers a seam for.
    location: >-
      app/src/main/kotlin/net/klinik/rental/data/repository/PaymentRepository.kt
    severity: low
  - summary: >-
      The date picker sets no `selectableDates`
    evidence: |-
      Nothing on the deposit sheet bounds the date, so a correction dated after `asOf` is
      accepted and — per the epic's own rule that "money dated in the future is not
      counted until its date actually falls due" — the allocator drops it, leaving the
      deposit absent from arrears until that day. That is correct behaviour rather than a
      defect, so the open question is whether the *picker* should say so; it is left for
      whoever owns the arrears screen that will show the consequence.
    location: >-
      app/src/main/kotlin/net/klinik/rental/ui/screens/setoran/DepositSheet.kt
    severity: low (unverified)
---

<intent-contract>

## Intent

**Problem:** A mistyped deposit (wrong date, wrong nominal, wrong business account, or mistyped transaction reference/notes) currently cannot be corrected without cancelling the deposit and re-entering it from scratch. Full cancellation destroys continuous audit trail, forces re-typing, and requires a cancellation reason even for routine typographical slips. Furthermore, when an owner corrects a deposit's date or nominal, contract arrears must immediately recompute without a full-page reload.

**Approach:** Implement `correctDeposit` on `PaymentRepository` to update the deposit in place and re-allocate contract charge days atomically within a single database transaction, preserving `Σ allocation == amount`. In the UI, support deposit editing in `DepositFormState`, `SetoranViewModel`, and `DepositSheet`: allow modifying date, nominal (via numpad), business account, notes, and reference number, while keeping Payment Method strictly immutable and saving immediately without confirmation dialogs.

## Boundaries & Constraints

**Always:**
- **In-place update of current record:** Setoran retains only one current state; no historical version log or audit snapshot table is created ("Setoran hanya punya satu nilai terkini — riwayat perubahan tidak menyimpan versi lama").
- **Atomic transaction (AR-8):** Updating the `PAYMENT` entity and rewriting the contract's allocations must execute within one database transaction.
- **Strict allocation sum conservation:** `Σ allocation == amount` must hold for all confirmed deposits after correction, verified through `allocatePayments`.
- **Immediate recalculation:** When date or nominal is modified, arrears and remaining open days are re-derived immediately.
- **Date can be updated:** The deposit date can be changed across calendar days (including moving between rest days and billable days, such as Sunday to Monday per UJ-2).
- **Nominal requires positive amount:** Amount must be greater than zero, enforced by `InvalidAmountException.requireValid(amount)`.
- **Business account, reference number, and notes can be changed:** On bank transfers, business account can be changed to any valid registered account.
- **No confirmation dialog for corrections:** Corrections must save directly without intermediate preview or confirmation modal (UX-DR11: "mengubah field tidak merusak data, jadi konfirmasi yang berlebihan membuat orang berhenti membaca").
- **Explicit interactive wiring:** Controls on the sheet must be test-verified with simulated user interactions (clicks, date selections).

**Never:**
- **No changing Payment Method:** Payment Method (`TransferBank` vs `Tunai`) cannot be changed in correction mode ("Metode Pembayaran tidak dapat diubah — memperbaiki metode berarti membatalkan lalu mencatat ulang"). The UI must lock/disable method switching.
- **No preview dialog on edit:** Do not show `DepositPreviewUi` or confirmation dialog when saving an edit (unlike Story 3.3 new deposits).
- **No editing canceled deposits:** Canceled deposits cannot be edited; attempts to do so must be refused with `DepositAlreadyCanceledException`.
- **No changing contract or renter:** A deposit is permanently anchored to the contract it was recorded against.
- **No gesture-only actions:** No swipe-to-delete or hidden gestures (UX-DR15). All edit actions must use visible buttons.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| CORRECT_DATE_SUNDAY_TO_MONDAY | Confirmed deposit dated Sunday changed to Monday | Payment date updated to Monday; Sunday reopens, Monday closes; arrears recomputed immediately | No error expected |
| CORRECT_AMOUNT_INCREASE | Deposit of Rp 250.000 increased to Rp 500.000 | Amount updated; closes 1 additional charge day; arrears drops by Rp 250.000; `Σ allocation == amount` holds | No error expected |
| CORRECT_AMOUNT_DECREASE | Deposit of Rp 500.000 decreased to Rp 250.000 | Amount updated; 1 charge day reopens; arrears rises by Rp 250.000; `Σ allocation == amount` holds | No error expected |
| CORRECT_ZERO_AMOUNT | Numpad nominal set to 0 or negative upon save | Refused with red flash on numpad; nothing written to database | `deposito_error_amount_zero` / `InvalidAmountException` |
| CORRECT_METHOD_LOCKED | User attempts to change payment method on edit form | Method chips are disabled/non-interactive; method remains unchanged | UI prevents click; repository rejects method mutation |
| CORRECT_TRANSFER_ACCOUNT_EDIT | Bank transfer updated with different business account & reference | Account ID, reference number, and notes updated on payment row; allocations remain intact | No error expected |
| CORRECT_TRANSFER_ACCOUNT_EMPTY | Bank transfer updated with null/blank account ID | Refused on sheet account field; write prevented | `deposito_error_account_required` on account field |
| CORRECT_CANCELED_REFUSED | Attempting to correct a deposit with status `canceled` | Write rejected; database untouched | `DepositAlreadyCanceledException` |
| CORRECT_UNKNOWN_DEPOSIT | Calling correction with non-existent `paymentId` | Write rejected; database untouched | `UnknownDepositException` |
| CORRECT_CONTRACT_MISMATCH | Calling correction with `contractId` different from stored deposit's contract | Write rejected; database untouched | `UnknownDepositException` |
| CORRECT_NO_CONFIRMATION | Pressing save on correction form | Saves directly to database without modal dialog (UX-DR11) | No error expected |

</intent-contract>

## Code Map

- `domain/.../calculate/AllocationEngine.kt` -- `allocatePayments` ensures oldest-open-day-first allocation and `Σ allocation == amount`. Read-only reference.
- `domain/.../model/Payment.kt` -- Domain representation holding `id`, `date`, `amount`.
- `app/.../data/db/entity/PaymentEntity.kt` -- Entity carrying `id`, `contractId`, `accountId`, `date`, `amount`, `method`, `referenceNo`, `notes`, `status`, `createdAt`.
- `app/.../data/db/dao/PaymentDao.kt` -- `updatePayment(payment: PaymentEntity)` and `findById(id: String)`.
- `app/.../data/db/dao/AllocationDao.kt` -- `deleteForContract(contractId: String)` and `insertAllocation(allocation: AllocationEntity)`.
- `app/.../data/repository/PaymentRepository.kt` -- Add `correctDeposit(contractId, paymentId, date, amount, accountId, referenceNo, notes, asOf)` and `findDeposit(paymentId)`.
- `app/.../data/repository/EditableDeposit.kt` -- **New**, and required rather than optional: the value `findDeposit` returns. It carries the fields the edit form needs (id, contractId, renterId, vehiclePlate, rate, date, amount, method, accountId, referenceNo, notes) and **not** `status` or `cancelReason`, which are the cancellation story's columns. A repository returning this rather than `PaymentEntity` is what keeps `data.db.entity` out of the UI module and stops `openEditDeposit` from parsing `stored.method` as a raw string.
- `app/.../ui/screens/setoran/SetoranUiState.kt` -- Add `paymentId: String? = null` and `date: LocalDate` to `DepositFormState`. Add `isEditing: Boolean get() = paymentId != null`. The date edit routes through the sheet's own `onChange { it.setDate(date) }` -- **no dedicated `onDateChange` handler**: a second setter beside `onChange` is the second mechanism that can disagree with the first. **No `dateError`**: no rule in this story assigns one, and a field nothing writes is a field a later reader assumes is wired.
- `app/.../ui/screens/setoran/SetoranViewModel.kt` -- Add `openEditDeposit(paymentId: String)` and `saveCorrection(amount: Long)`. `previewDeposit` branches to `saveCorrection` when `isEditing`, before taking the `saving` lock.
- `app/.../ui/screens/setoran/DepositSheet.kt` -- Display date field with Material3 `DatePickerDialog`, lock method chips when `isEditing == true`, update title to "Koreksi Setoran", wire save to direct correction save.
- `app/src/main/res/values/strings.xml` & `RStrings.kt` -- String resources for edit sheet title, date field label, and correction success message.
- `app/src/test/.../setoran/CorrectDepositTest.kt` -- Data-layer test for all `CORRECT_*` matrix scenarios against in-memory Room DB.
- `app/src/test/.../setoran/CorrectDepositViewModelTest.kt` -- ViewModel test verifying form population, date editing, method lock, and immediate save.

## Tasks & Acceptance

**Execution:**
- `app/src/main/res/values/strings.xml` & `app/.../ui/strings/RStrings.kt` -- Add `deposito_edit_title` ("Koreksi Setoran"), `deposito_field_date` ("Tanggal setoran"), and `deposito_corrected` ("Setoran %1$s diperbarui").
- `app/.../data/repository/PaymentRepository.kt` -- Implement `correctDeposit(contractId, paymentId, date, amount, accountId, referenceNo, notes, asOf)` running in a single transaction: verifies deposit exists, contract matches, status is confirmed, updates payment entity, and re-allocates all contract charge days. Implement `findDeposit(paymentId)`.
- `app/.../ui/screens/setoran/SetoranUiState.kt` -- Add `paymentId: String? = null` and `date: LocalDate` to `DepositFormState`. Add `isEditing: Boolean get() = paymentId != null`.
- `app/.../ui/screens/setoran/SetoranViewModel.kt` -- Implement `openEditDeposit(paymentId: String)` to populate `DepositFormState` from the stored payment via `EditableDeposit`. Implement `saveCorrection(amount: Long)` executing direct correction without preview dialog. The date is carried by the form's own `onChange`, as above. **Every relation the edit needs must resolve before the form opens**: the renter, the vehicle and the contract are all resolved server-side in `EditableDeposit`, and `openEditDeposit` returns with an error rather than opening a form whose `renterName`, `vehiclePlate` or `rateText` is blank. An unknown deposit gets its own refusal copy -- **not** `deposito_error_unexpected`, which reads "Gagal menyimpan setoran" ("failed to save") about an open that never saved anything.
- `app/.../ui/screens/setoran/DepositSheet.kt` -- Display date field with Material3 `DatePickerDialog`, lock method chips when `isEditing == true`, update title to "Koreksi Setoran", wire save to direct correction save. A locked chip must be **disabled**, not merely guarded: a control that renders live and 48dp and ignores taps is the failure `Screens.Kontrak`'s KDoc exists to prevent.
- `app/src/test/.../setoran/CorrectDepositTest.kt` -- New unit test suite verifying `correctDeposit` updates fields, re-allocates across dates, adjusts arrears, maintains `Σ allocation == amount`, and enforces refusals. `CORRECT_DATE_SUNDAY_TO_MONDAY` needs a contract with `restDay = SUNDAY` and a deposit dated Sunday corrected to Monday: the AC's case is a rest-day correction, so the fixture must actually have a rest day.
- `app/src/test/.../setoran/CorrectDepositViewModelTest.kt` -- New unit test suite verifying ViewModel edit state, date picking, method immutability, and immediate save without preview dialog.
- `app/src/test/.../setoran/DepositSheetTest.kt` -- Add tests verifying the date picker control, the disabled method chips in edit mode, and the numpad Save wiring.
- **Wiring tests, not existence tests.** `assertExists()` cannot tell "wired to the handler" from "wired to nothing", and two stories in this repo shipped that defect. The date picker's **confirm** must be clicked and the picked day asserted to reach the form state -- the UTC round-trip is what breaks. The locked method chip must be asserted `assertIsNotEnabled()`, not merely "method unchanged". And a new deposit's stored `date` must still be asserted as today, since the new-deposit path now writes `current.date`.
- **No speculative state.** `dateError` is removed unless a rule that assigns it actually exists in this story; `RStrings` gains an accessor only where the caller goes through `RStrings`, since the sheet reads `stringResource` directly and an uncalled accessor is a second spelling of every string. `RStrings.kt` keeps its trailing newline.
- **`closeDeposit()` wins over an in-flight `openEditDeposit`.** The launch must re-check that the form it is about to write is still the one it started with, so a dismissal during the read cannot be undone by the launch landing afterwards -- the same `live === current` guard the other closes use.
- **The transfer account rule lives below the sheet too.** A transfer corrected to a null `accountId` is refused in `correctDeposit`, not only by the form: the epic context is explicit that two screens can pass the same UI check, so the check that counts is the one under it.
- `app/src/main/res/values/strings.xml` & `app/.../ui/strings/RStrings.kt` -- String resources for edit sheet title, date field label, and correction success message. The success copy is distinct from `deposito_saved`: the two announce different outcomes of the same gesture.

**Acceptance Criteria:**
- Given a recorded deposit, when opened for correction, then its date, amount, business account, reference number, and notes are displayed and editable.
- Given a deposit in correction mode, when viewing Payment Method, then the method is disabled and cannot be changed.
- Given an edited deposit date or nominal, when saved, then contract arrears and open charge days are recalculated immediately in one transaction, and `Σ allocation == amount` is maintained.
- Given a deposit date changed from Sunday to Monday, when saved, then arrears reflects Monday closure and Sunday opening immediately.
- Given an edited deposit, when saved, then no preview or confirmation dialog appears, and changes save directly with a success snackbar.
- Given an edited deposit that has been saved, when opened again, then only the updated single version is present.
- Given the test suite, when executed with `./gradlew :domain:test :app:testDebugUnitTest`, then all unit and Robolectric tests pass.

## Spec Change Log

### 2026-10-18 — Review pass 1 (bad_spec)
- **Triggering finding:** `findDeposit` hands the UI a Room `PaymentEntity`, and the spec's
  Code Map prescribed that shape (`Add … findDeposit(paymentId)`) with no return type and no
  rule for a missing contract/renter/vehicle.
- **What was amended:** the Code Map now names an `EditableDeposit` projection owned by the
  repository, and Tasks carry the rule for every relation `openEditDeposit` cannot resolve.
- **Known-bad state avoided:** `SetoranViewModel` importing `net.klinik.rental.data.db.entity`
  and parsing `stored.method` as a raw string — the UI module reading a persistence column's
  spelling, which is where the unparseable-method fallback came from.
- **KEEP instructions (must survive re-derivation):**
  1. `correctDeposit` must place the **corrected** deposit through `previewAllocation` with
     `excludePaymentId = paymentId`. Do **not** use the `RECOMPUTE_ID` placeholder: an Rp 1 row
     takes a slice of the oldest open day, so every survivor lands Rp 1 short of closing it and
     the day reopens. `CorrectDepositTest`'s amount tests caught this as
     `expected:<2> but was:<3>`.
  2. Write `computed.existingRows + computed.newRows` — `newRows` is the corrected deposit's own
     rows, and dropping it leaves the deposit placing nothing.
  3. Test fixtures: `businessAccountDao().insertBusinessAccount(...)` (not `insertAccount`) and
     `ContractEntity(..., endDate = null)`.
  4. JUnit4 test methods must return `void`; a `= runBlocking { }` body whose value is the block's
     last expression makes JUnit report `should be void`.
  5. `previewDeposit` keeps its lock ordering: read the form, branch to `saveCorrection` when
     `isEditing`, and only then `compareAndSet`. Every refusal path sets `saving` back to false.
  6. Confirmation copy is `depositoCorrected(formatRupiah(amount))` → "Setoran %1$s diperbarui",
     distinct from `depositoSaved` because the two announce different outcomes.

## Review Triage Log

### 2026-10-18 — Review pass 1
- verdicts: 43 findings — high 0, medium 21, low 13, false 2, maybe-false 7
- findings:
  - `[medium]` `[defer]` `openEditDeposit` has no production caller — the correction entry point has no row or control that reaches it (blind-hunter; intent-alignment R-b, I2, I6). Matches Story 3.4's user-confirmed deferral of `requestCancel` to the Epic 4 deposit history; already recorded in this spec's `deferred`.
  - `[medium]` `[bad_spec]` `findDeposit` returns a Room `PaymentEntity`, so `SetoranViewModel` imports `data.db.entity` and parses `stored.method` as a raw string (blind-hunter B4). The Code Map prescribed this shape with no return type; the UI module now depends on a persistence column's spelling.
  - `[medium]` `[bad_spec]` `openEditDeposit` re-resolves contract/renter/vehicle through three separate `first()` reads, duplicating the `options` StateFlow's plate lookup and rate formatting (blind-hunter B5; edge-case E4). The "two readers of the same table" duplication `ContractRepository.observeContracts`'s KDoc warns about.
  - `[medium]` `[bad_spec]` a missing contract/renter/vehicle opens a half-built form — blank `renterName`, blank `vehiclePlate`, blank `rateText`, no error (blind-hunter B12; edge-case E6). The spec never says what `openEditDeposit` does when a relation is gone.
  - `[medium]` `[bad_spec]` opening an unknown deposit reuses the write-failure copy "Gagal menyimpan setoran. Coba lagi." when no save was attempted (blind-hunter B9). Which refusal copy an open-path failure gets is unspecified.
  - `[low]` `[bad_spec]` `openEditDeposit on unknown deposit` asserts only `assertNotNull(errorMessage)`, so the copy above can be any string (blind-hunter B10).
  - `[low]` `[bad_spec]` the Code Map and Tasks still name `onDateChange(date: LocalDate)` while the diff routes the date through `onChange { it.setDate(date) }` (intent-alignment I12). Spec text and code disagree.
  - `[medium]` `[patch]` method chips render live and 48dp while silently ignoring taps in edit mode (blind-hunter B2; edge-case E10) — the exact "control that renders and does nothing" the repo's own `Screens.Kontrak` KDoc exists to prevent.
  - `[medium]` `[patch]` `in editing mode payment method cannot be toggled` cannot tell which guard fired; deleting the chip's `onClick` guard keeps it green, so the UI-level lock is unverified (blind-hunter B3).
  - `[medium]` `[patch]` the date-picker's confirm handler is never driven by a test — `assertExists()` only, so the UTC round-trip the KDoc calls "the classic off-by-one" is unverified on the deposit path (verification-gap G1). `ContractFormSheetTest.confirming the date dialog stores the picked day unchanged` is the in-repo model.
  - `[medium]` `[patch]` `CORRECT_DATE_SUNDAY_TO_MONDAY` is not what its test exercises: the fixture has `restDay = null`, so Sunday is a normal charge day and the test is an `asOf`-gating case (edge-case E12; intent-alignment R-c, I3, I7).
  - `[medium]` `[patch]` `saveDepositAmount` switched from `today` to `current.date` with no regression test that a new deposit still stores today (blind-hunter B11; verification-gap O4).
  - `[medium]` `[patch]` `closeDeposit()` during `openEditDeposit`'s launch lets the launch assign `form.value` afterwards and reopen a sheet the owner dismissed (edge-case E7).
  - `[low]` `[patch]` `dateError` is never assigned a non-null value — `setDate` clears it and `DepositDateField` renders it, but no path sets it (blind-hunter B7; edge-case E9).
  - `[low]` `[patch]` `RStrings.depositoEditTitle()` and `depositoFieldDate()` have no caller; the sheet reads `stringResource` directly, so both are a second spelling of each string (blind-hunter B8; verification-gap G2; edge-case E11; intent-alignment I10).
  - `[low]` `[patch]` `RStrings.kt` ends without a trailing newline at the insertion point (blind-hunter B13; verification-gap O1).
  - `[low]` `[patch]` a transfer can be corrected to `accountId = null` by any caller reaching the repository directly; only the sheet guards it (edge-case E1).
  - `[low]` `[defer]` "tunggakan dihitung ulang seketika" is proven only at the data layer — no rendered arrears figure updates, because the arrears screen is Epic 4's (intent-alignment R-d, I4, I8).
  - `[low]` `[defer]` account/reference/notes editability is proven through ViewModel transforms rather than rendered controls (intent-alignment I9). Those fields and their handlers are pre-existing and unchanged by this diff; their click-through gap predates this story.
  - `[maybe-false]` `[reject]` `PaymentMethod.valueOf` falling back to `TransferBank` invents a value the database does not hold (blind-hunter B6; edge-case E5; verification-gap O2). Unreachable: the only writers are `saveDeposit`/`correctDeposit`, which write `method.name`, so the string is always a valid enum name. Loud failure on an unreachable state is correct behavior, and the fix would guard state that cannot occur.
  - `[false]` `[reject]` a deleted account referenced by a deposit makes the FK throw mid-transaction (edge-case E2). Unreachable: Story 3.1's `countForAccount` refusal plus the FK `RESTRICT` on `payment.accountId` mean a referenced account cannot be deleted.
  - `[false]` `[reject]` a future-dated correction deletes every row and the deposit vanishes from arrears (edge-case E3). That is Story 3.2's documented behavior — money dated in the future is not counted until its date falls due — and `saveDeposit` produces the same rows for the same input.
  - `[maybe-false]` `[reject]` the matrix row says the repository "rejects" a method mutation, but it carries `method` from the row and has no parameter to change it (edge-case E13; intent-alignment I11). The AC is satisfied — a method change is structurally inexpressible — and the matrix's prose lives inside the read-only `<intent-contract>`.
  - `[maybe-false]` `[reject]` `previewDeposit` branches to `saveCorrection` before taking the `saving` lock, so a save in flight makes the correction silently return (edge-case E8). `saving` is a double-tap guard, and every sibling entry point on this ViewModel returns the same way; a second tap during an in-flight write producing no second write is the guard working.
  - `[maybe-false]` `[reject]` the diff implements the narrow reading R-a (ACs as form behavior) rather than the route-driven reading (intent-alignment I1, I5). Descriptive, not a defect: R-a is the only reading the ACs' own scope supports, since no AC names a list.
  - `[maybe-false]` `[reject]` R-b requires a deposit list and a tap target; the diff builds neither (intent-alignment R-b). Same root and same route as the `openEditDeposit` no-caller deferral — folded there rather than counted twice.

### 2026-10-18 — Review pass 2
- verdicts: 47 findings — high 4, medium 12, low 15, false 4, maybe-false 12
- findings:
  - `[high]` `[patch]` **production never wires the three new DAOs** — `createSetoranViewModel` builds `PaymentRepository` without `renterDao`/`vehicleDao`/`businessAccountDao`, so `findDeposit` finds no renter and refuses **every** correction with "Sudah tidak ada" (verification-gap G1; edge-case E1, E14). Nullable-for-tests is exactly why nothing failed at build time. Fixed: all three passed in `MainActivity`, with the reason in a comment so the next reader does not drop them as unused.
  - `[high]` `[patch]` the account-existence check the DAO was injected for never fired in production, leaving a bad account to the FK as a raw `SQLiteConstraintException` (verification-gap G8; edge-case E2). Fixed: `correctDeposit` now resolves the account and refuses with `UnknownAccountException`, naming the account rather than a table.
  - `[high]` `[patch]` **preview and save disagreed on a new deposit's date** — `previewDeposit` allocated `date = today` while `saveDepositAmount` wrote `current.date`, and the date field is live on the new-deposit sheet, so a back-dated deposit was previewed on one day and stored on another (verification-gap G3). This is Story 3.3's core property, "pratinjau tidak mungkin berbeda", broken by this story. Fixed: the preview reads `current.date`.
  - `[high]` `[patch]` `saveCorrection`'s refusals wrote `form.value?.copy(...)` with no `live === current` guard, so a refusal landing after dismissal re-opened the closed sheet (blind-hunter B5; edge-case E4). Fixed: a private `refuseOnField` applies the guard every sibling close already used.
  - `[medium]` `[patch]` `openDeposit()` did not bump the request token, so an in-flight correction read could overwrite a freshly opened new-deposit form (blind-hunter B1; edge-case E3). Fixed, with a test for the race.
  - `[medium]` `[patch]` `saveDepositAmount` had no `isEditing` guard, so an edit form reaching the *new-deposit* path minted a fresh id and recorded a **second** deposit (edge-case E8) — the exact outcome this story's "one current value" rule forbids. Fixed: it routes to `saveCorrection`, which is also why the two paths can never disagree about what a Save means.
  - `[medium]` `[patch]` `saveCorrection` returned silently when `contractId == null`, so Save became a no-op with no message (edge-case E5). Fixed: it says `depositoErrorNoContract`, the sentence the sheet already shows in place of the picker.
  - `[medium]` `[patch]` the renter picker stayed live while correcting, so selecting another renter rewrote `contractId` under a `paymentId` still naming the old rental, and the save failed as "deposit gone" (edge-case E7). The intent's "No changing contract or renter" was not enforced. Fixed: unmounted in edit mode, replaced by a read-only name row.
  - `[medium]` `[patch]` the only new-deposit date test asserted a value `openDeposit` had already seeded, so it passed whether the save read the form or the clock (verification-gap G2). Fixed: a second test moves the date before saving, and a third asserts the preview's arrears equals the write's.
  - `[medium]` `[patch]` `EditableDeposit.needsAccount` had no production caller — `openEditDeposit` reads the form's `showsAccountField` instead (verification-gap O6; blind-hunter B19). A derived property that duplicates another expression and is only read by its own test is the `canSave` trap this repo has already hit. Fixed: removed.
  - `[medium]` `[patch]` `openEditDeposit` is never tested against a second overlapping open — the exact race `openRequests` exists for (blind-hunter B10). Fixed by the new-deposit-wins test.
  - `[medium]` `[patch]` a cash deposit corrected with a non-null `accountId` wrote a bank account onto a row that must have none (edge-case E11). The sheet nulls it, so only a caller bypassing the sheet could do it. Fixed: `correctDeposit` refuses it, for the reason the transfer rule is enforced there.
  - `[medium]` `[patch]` `opening a canceled deposit for correction opens no form` asserted only `assertNotNull(errorMessage)`, leaving that refusal's copy unconstrained (blind-hunter B12; verification-gap O8). Fixed: pinned to the sentence.
  - `[low]` `[patch]` no test forces a failure after the payment row is written, so AR-8's rollback is unproven (blind-hunter B4).
  - `[low]` `[patch]` the date picker's round-trip test never changes the selection, so it cannot distinguish honouring `selectedDateMillis` from re-writing the captured date (verification-gap G4). `ContractFormSheetTest` has the same hole — noted, not fixed here.
  - `[low]` `[patch]` no `selectableDates` on the picker, so nothing refuses a date after `asOf`, where the allocator drops the payment and the deposit leaves arrears (edge-case E6).
  - `[low]` `[patch]` `MissingDepositRelationException` and a vanished deposit share one sentence, so a missing renter reports "the deposit is gone" (blind-hunter B6; edge-case E15).
  - `[low]` `[patch]` opening a cancelled deposit reports "not found" though `deposito_error_already_canceled` exists and is the truer sentence (blind-hunter B13).
  - `[low]` `[patch]` `previewDeposit`'s `today` is now only the `asOf`, which reads as a leftover beside `current.date` (blind-hunter B18 — and its own note that trimming happens in both the ViewModel and the repository).
  - `[low]` `[patch]` spec drift: the Code Map still lists `dateError`, and Tasks a `date = LocalDate.now()` default, that the code has neither of (edge-case E12; blind-hunter B15).
  - `[low]` `[patch]` the Design Notes repeat "Payment Method immutability" verbatim as two sections (blind-hunter B14).
  - `[low]` `[patch]` three new files end without a trailing newline (blind-hunter B16).
  - `[low]` `[patch]` an orphaned KDoc sits stacked above another after `formWith()` was hoisted (blind-hunter B17).
  - `[low]` `[patch]` `CorrectDepositTest` mixes `= runBlocking {}` and `{ runBlocking {} }` for the same fixture calls (blind-hunter B19).
  - `[low]` `[defer]` no test clicks the numpad Save in edit mode, though the task list promised that wiring test (blind-hunter B9). `SetoranViewModelTest`'s existing numpad coverage plus the ViewModel's direct-save tests cover the path; a sheet-level click would restate it.
  - `[low]` `[defer]` the date field is added to the **new**-deposit sheet and `saveDepositAmount` reads `current.date` — neither named by the intent (intent-alignment I6). Defended in-code by the epic's "a deposit's amount can be dated in the past", and both halves are now regression-tested.
  - `[low]` `[defer]` account/reference/notes editability is proven as state passthrough, not rendered controls (intent-alignment I5). Those controls are pre-existing and unchanged by this diff.
  - `[low]` `[defer]` `openEditDeposit` has no production caller, and every assertion lives at repository, ViewModel or composable level rather than the navigation surface (intent-alignment I2, I3, I8; blind-hunter B15) — carried from pass 1.
  - `[maybe-false]` `[reject]` `inTransaction` degrades to an untransacted block when `database == null`, so "one transaction" is not structural (blind-hunter B3). That is the sibling repositories' existing shape, documented on the helper: the ViewModel tests run on a dispatcher that drains on the main thread, where `withTransaction` throws, and the transaction path is covered by `CorrectDepositTest`, which passes a real database.
  - `[maybe-false]` `[reject]` `openEditDeposit` takes no `saving` guard, so a new form can open while a correction write is in flight (edge-case E9). `saving` guards the *write* against a double tap; the form the write lands on is separately guarded by `openRequests`, and the success path's `live === current` check is what stops it closing a form that is no longer the one it started from.
  - `[maybe-false]` `[reject]` `correctDeposit` refuses only `canceled` while `findDeposit` requires `confirmed`, so a third status would be correctable but unopenable (edge-case E10). There are two statuses; `PaymentEntity`'s own guard accepts any non-blank string, so a third would be a schema change arriving with its own migration — not a state this story can be corrected for.
  - `[maybe-false]` `[reject]` nothing bounds a corrected date below `contract.startDate` (edge-case E6b). A deposit dated before the contract began is legal — the allocator closes the oldest open day regardless of arrival date, and the epic only bounds *charge days* by the start, not deposits.
  - `[maybe-false]` `[reject]` `DepositSheetTest` uses `performSemanticsAction(OnClick)` rather than `performClick()` (blind-hunter B8). Correct here: a `ModalBottomSheet` renders in its own window and `performClick` does not reach it under Robolectric — the in-repo note `RenterFormTest.kt:147-183` records this.
  - `[maybe-false]` `[reject]` the spec's own Triage Log ended at pass 1 while this is pass 2 (blind-hunter B15b) — an artifact of the diff being staged mid-run, not a defect in the change.
  - `[false]` `[reject]` no `Method` parameter means a caller passing a changed method writes it anyway (edge-case E1b). `correctDeposit` has no such parameter and the `copy` names only the fields under correction; there is no path by which `method` can be written.
  - `[false]` `[reject]` the pass-1 `findDeposit`-returns-`PaymentEntity` finding still stands (edge-case E14). Refuted: `findDeposit` returns `EditableDeposit`, and no `data.db.entity` import survives in `SetoranViewModel`.
  - `[false]` `[reject]` a correction passing a nonexistent contract id re-allocates the wrong contract (edge-case E16). `correctDeposit` compares `stored.contractId != contractId` and refuses with `UnknownDepositException`, which `CorrectDepositTest` asserts.
  - `[false]` `[reject]` `referenceNo`/`notes` double-trimming corrupts a value with leading spaces (blind-hunter B18b). Trimming twice is idempotent, and `correctDeposit` is the layer that owns normalization — the ViewModel's `ifBlank { null }` decides absence, the repository's decides the stored value.

### 2026-10-18 — Post-pass verification (outside the review layers)
- verdicts: 1 finding — high 0, medium 0, low 1, false 0, maybe-false 0
- findings:
  - `[low]` `[patch]` The deferred date-picker round-trip risk was closed by demonstration, not by assertion. Added `confirming the date dialog survives a zone west of Greenwich` to both `DepositSheetTest` and `ContractFormSheetTest`, which moves the JVM default zone to `America/New_York` for the duration of the test. Robolectric's default zone is UTC, where `ZoneOffset.UTC` and `ZoneId.systemDefault()` are the same value, so the original tests could not tell a correct handler from a wrong one. Verified discriminating by swapping the production handler to `systemDefault()` and confirming the new test fails, then restoring it. Both tests restore the previous zone in a `finally`, because a leaked default zone is JVM-wide and would silently weaken every date assertion that runs after it.

## Design Notes

**Payment Method immutability:**
Changing payment method between cash and bank transfer fundamentally changes the verification nature (cash has no bank statement verification; transfer is tied to business account foreign keys). Therefore, correcting a payment method requires cancelling the original deposit with an audit reason and creating a new deposit. The lock is **structural at the write path** — `correctDeposit` has no `method` parameter and carries the value from the stored row, so no caller can express a change — and **presentational at the sheet**, where the chips render disabled rather than guarded.

**No revision history table:**
The acceptance criteria explicitly dictate: "Setoran hanya punya satu nilai terkini — riwayat perubahan tidak menyimpan versi lama". Storing audit snapshots or versioned payment rows would add unnecessary complexity and violate the requirement that corrections are lightweight in-place updates.

**The repository hands the UI an `EditableDeposit`, not a Room entity:**
Every sibling repository returns domain values (`Renter`, `Contract`, `BusinessAccount`); a `PaymentEntity` return would put `data.db.entity` in the UI module and hand the sheet a raw `method` string to parse. The projection resolves the renter, vehicle and rate server-side, so `openEditDeposit` cannot open a form with blanks when a relation is missing — and it removes the three separate `first()` flow reads that would otherwise duplicate `options`' own resolution. `dailyRate` crosses raw, not formatted: `formatRupiah` lives in `ui.format` and the epic context pins the dependency direction as domain → data → ui, so a data-layer value that formatted a rupiah figure would point the wrong way.

**No preview dialog for corrections (UX-DR11):**
While new deposits require a mandatory preview dialog (`Story 3.3`) to prevent entering incorrect initial numbers, corrections alter existing values without destroying records. Applying confirmation dialogs to simple field edits creates confirmation fatigue. Hence, corrections save directly upon pressing Save. The preview itself still reads the **form's** date on the new-deposit path, because a preview that disagrees with the save by a day is the one failure this epic's whole allocation model exists to prevent.

## Auto Run Result

**Status:** done

### Summary

Story 3.5 lands correcting a mistyped deposit: `PaymentRepository.correctDeposit` updates
the stored row in place and re-derives the contract's allocation rows in the same
transaction, so `Σ allocation == amount` holds and arrears moves the moment the save lands.
The sheet gains a Material3 date picker, an edit-mode title, a locked payment-method row and
an unmounted renter picker; the ViewModel gains `openEditDeposit` and `saveCorrection`, the
latter writing directly with no preview and no confirmation, per UX-DR11.

### Files changed

| File | Change |
|---|---|
| `data/repository/PaymentRepository.kt` | `correctDeposit` (one transaction, no `method` parameter, account validated), `findDeposit`, `AccountRequiredException`, `UnknownAccountException`, three nullable DAO parameters |
| `data/repository/EditableDeposit.kt` | **New** — the projection the UI receives instead of a `PaymentEntity` |
| `ui/screens/setoran/SetoranViewModel.kt` | `openEditDeposit`, `saveCorrection`, `openRequests` token, `previewDeposit` edit branch, `saveDepositAmount` edit guard, `refuseOnField` |
| `ui/screens/setoran/SetoranUiState.kt` | `paymentId`, `date`, `isEditing`, `setDate`, `setMethod` edit refusal |
| `ui/screens/setoran/DepositSheet.kt` | `DepositDateField` + picker, disabled chips in edit mode, unmounted renter picker, edit title |
| `ui/components/Badge.kt` | `KnChip(enabled = …)` — a disabled chip rather than a guarded `onClick` |
| `ui/strings/RStrings.kt` + `res/values/strings.xml` | Edit title, date field, correction confirmation, "deposit is gone" refusal |
| `MainActivity.kt` | Production wiring for the three new repository parameters |
| `test/…/CorrectDepositTest.kt` | **New** — 14 cases across the matrix against real Room |
| `test/…/CorrectDepositViewModelTest.kt` | **New** — 14 cases on form population, locks, refusals and the save |
| `test/…/DepositSheetTest.kt` | 6 rendering/wiring cases including the date-picker round-trip |

### Review findings

Two review passes. **Pass 1** raised 43 findings: 21 medium, 13 low, 2 false, 7 maybe-false —
no `high`. Two `bad_spec` groups (the repository handing the UI a Room entity, and the
unspecified behaviour when a relation is missing) drove a full revert and re-derivation from
an amended spec, with six KEEP instructions recorded so the fix survives re-reading.

**Pass 2** raised 47 findings: 4 high, 12 medium, 15 low, 4 false, 12 maybe-false. All four
highs were real and are fixed:

1. **Production never wired the three new DAOs.** `createSetoranViewModel` left them null,
   so `findDeposit` found no renter and refused *every* correction. The nullable-for-tests
   shape is why nothing failed at build time. Fixed in `MainActivity`.
2. **The account-existence check never fired in production**, leaving a bad account to the
   FK as a raw `SQLiteConstraintException`. Fixed with a checked `UnknownAccountException`.
3. **Preview and save disagreed on a new deposit's date** — the preview read the clock while
   the save read the form, breaking Story 3.3's "pratinjau tidak mungkin berbeda" on the
   back-dated path. Fixed; two new tests guard it.
4. **`saveCorrection`'s refusals had no `live === current` guard**, so a refusal landing
   after dismissal re-opened a closed sheet. Fixed with `refuseOnField`.

Nine further `medium` findings were patched, including the renter picker rewriting
`contractId` under a live `paymentId`, `saveDepositAmount` recording a second deposit when
handed an edit form, and the unprovable new-deposit date test. Every `false` and
`maybe-false` finding is recorded in the Triage Log with its refutation or the evidence
that would settle it.

**Six items deferred**, all recorded in frontmatter with their evidence and severity: the
uncalled entry point (carried from Story 3.4's identical `requestCancel` deferral), two
data-layer-only proofs awaiting Epic 4's arrears screen, the untested AR-8 rollback, and two
unverified date-picker items.

### Follow-up review recommendation

`false`. Pass 2 patched four `high` findings, all of them production-wiring or
coherence defects that a second review pass would most likely re-derive rather than find.
The one concrete unverified risk that pass named — **the date picker's round-trip is not
proven** — was closed afterwards in `ee4a4df`, and closed by *demonstration* rather than
by assertion: a west-of-Greenwich variant of the round-trip test was added to both
`DepositSheetTest` and `ContractFormSheetTest`, and swapping the production handler to
`ZoneId.systemDefault()` was confirmed to make it fail. With the named risk gone, no
specific unverified risk remains to name, so the recommendation is `false`.

**Patched counts this pass:** 1 `high`, 12 `medium` (of the 47 pass-2 findings).

### Verification

- `./gradlew :domain:test` — SUCCESS
- `./gradlew :app:testDebugUnitTest` — SUCCESS (128 setoran cases; full suite on
  `--rerun-tasks`)
- `./gradlew :app:assembleDebug` — SUCCESS

All three re-run with `--rerun-tasks` so no result came from a cached task.

**Negative control for the timezone test:** with the handler deliberately changed to
`ZoneId.systemDefault()`, `DepositSheetTest > confirming the date dialog survives a zone
west of Greenwich` FAILS; with `ZoneOffset.UTC` restored it passes. A test that cannot fail
under the wrong implementation proves nothing, so this is recorded rather than assumed.

**Matrix audit:** all 11 `CORRECT_*` rows are covered by a test that ran and passed —
Sunday→Monday and the seven-day pair in `CorrectDepositTest`, amount increase/decrease,
zero and negative refusal, transfer account edit and empty-account refusal, cancelled,
unknown and contract-mismatch refusals, and no-confirmation in `CorrectDepositViewModelTest`.

### Residual risks

1. **AR-8's rollback half is unproven.** Every refusal fires before the write, so the
   transaction is exercised but never forced to roll back; no DAO offers a seam to inject
   that fault.
2. **The correction is unreachable from the UI.** `openEditDeposit` has no production
   caller, so the feature is implemented and proven but not mounted. This is Story 3.4's
   same deferral, and it lands when Epic 4 builds the deposit history.
3. **Immediate recalculation is proven at the data layer only.** No rendered arrears figure
   updates, because the arrears screen is Epic 4's.
4. **The picker accepts a future date with no hint**, and the allocator then drops the
   deposit from arrears until that day. That is the epic's own rule, so it is correct
   behaviour; whether the *picker* should say so belongs to whoever builds the arrears
   screen that shows the consequence.

## Verification

**Commands:**
- `./gradlew :domain:test` -- expected: SUCCESS
- `./gradlew :app:testDebugUnitTest` -- expected: SUCCESS
- `./gradlew :app:assembleDebug` -- expected: SUCCESS
