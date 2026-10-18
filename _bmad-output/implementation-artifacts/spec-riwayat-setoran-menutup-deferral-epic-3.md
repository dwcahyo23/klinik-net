---
title: 'Riwayat Setoran — menutup deferral Epic 3'
type: 'feature'
created: '2026-10-18'
status: 'in-progress'
baseline_revision: '426b63cb729e48b913600d534be4d20f29e14b7a'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-3-context.md'
warnings:
  - oversized
deferred: []
---

<intent-contract>

## Intent

**Problem:** Two features from Epic 3 are finished, tested, and unreachable. Cancelling a
mistyped deposit and correcting one both work perfectly against a caller that does not exist,
because there is no screen that lists a contract's deposits — so no row to tap. Epic 3 recorded
both as deferrals pointing here, and Epic 4 built six stories without ever building the surface
they wait for.

**Approach:** A deposit history list on the contract detail screen, showing what each deposit
closed and what it says — with a verb per row that mounts the cancellation dialog and the
correction form Story 3.4 and 3.5 already built.

## Boundaries & Constraints

**Always:**
- **The list shows every deposit, cancelled ones included**, each marked as cancelled. The epic's
  rule is that a cancelled deposit keeps its row so the audit trail stays readable; hiding it here
  would undo that decision one screen later.
- **The read is the existing `observeForContract`.** It already returns every row, cancelled
  included, precisely so history can show them.
- **Each row carries the reason a deposit exists**: its date, amount, method and account — and,
  for a cancelled one, the owner's own reason for cancelling it.
- **Two verbs per row, both visible buttons, no gesture.** Cancelling changes money and needs a
  reason; correcting is a field edit. Neither may hide behind a swipe, and the epic forbids it
  explicitly.
- **A cancelled deposit's correction verb is absent**, not disabled-by-a-flag: `correctDeposit`
  already refuses it with `DepositAlreadyCanceledException`, and a control that renders live and
  ignores the tap is the failure this repo has shipped three times.
- **The correction form opens pre-filled with every stored field** — the row carries them, so
  Story 3.5's `openEditDeposit` reads from the same place it always did.

**Never:**
- **No summary, no totals, no chart.** The arrears list (4.1) and the report (4.5) carry figures;
  this screen carries facts.
- **No filter, no search, no date range.** Fourteen deposits is a list that scrolls.
- **No deposit row is editable in place.** Correction goes through Story 3.5's form, which
  rebuilds the allocations; an inline edit would be a second path to one row.
- **No delete, no swipe-to-delete, no bulk action.** Cancellation is the destructive verb and it
  is Story 3.4's dialog, mandatory reason and all.
- **No change to the repository.** Every write this screen triggers already exists and is tested;
  this story mounts entry points and reads a flow.
- **No arrears figure per row.** The day count a deposit closed is not the same as the contract's
  arrears, and conflating them is the second-derivation defect.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| HIST_LIST | Three confirmed deposits, oldest first | All three listed with date, amount and method | No error expected |
| HIST_CANCELLED_SHOWN | One cancelled deposit with a reason | Still listed, marked cancelled, **with the owner's reason visible** | No error expected |
| HIST_CANCEL_VERB | Tapping cancel on a confirmed row | Story 3.4's dialog opens, pre-filled with the amount and arrears impact | No error expected |
| HIST_EDIT_VERB | Tapping correct on a confirmed row | Story 3.5's form opens **pre-filled with every stored field** | No error expected |
| HIST_CANCELLED_NO_VERBS | A cancelled row | **No** cancel verb and **no** correct verb — it cannot be changed again | No error expected |
| HIST_CANCEL_NOTHING | A contract with no deposits | The empty state says what to do, not merely that there is nothing | No error expected |
| HIST_UPDATE | A deposit cancelled from this screen | The list shows it cancelled immediately, with no reload | No error expected |
| HIST_NO_GESTURES | Every row | Two visible buttons, each clearing 48dp; nothing reachable only by swipe | No error expected |

</intent-contract>

## Decisions taken at the gate

- **The list mounts on the contract detail surface, not the arrears screen.** A deposit belongs to
  a contract, and `KontrakScreen` already owns contract detail with a master-detail pane in
  landscape. The arrears screen lists *contracts*; this lists *one contract's deposits*, and
  putting it there would mean the owner reaches a contract's history by first opening that
  contract's arrears row — a second hop for no reason.
- **The list mounts as a second block inside the existing contract sheet, not a third window.**
  `KontrakScreen` already mounts `ExceptionFormSheet` from itself and `KontrakRoute` mounts
  nothing, precisely because two sheets over one state is the defect Story 2.8's review found as
  its only `high`. The deposit rows therefore sit inside that same sheet, beside the exception
  list — one more block, not one more window.
- **Both verbs are visible buttons on the row**, and the row's own tap does not double-fire the
  verb beside it — the nested-clickable case Story 4.3 and 4.5 have each had asserted.
- **This story is not an epic story**, and carries no story number. It closes two recorded
  deferrals rather than adding a new requirement, so it is tracked by what it closes.

## Code Map

- `app/.../data/db/dao/PaymentDao.kt` -- `observeForContract(contractId)` already returns **every**
  row, cancelled ones included, and its KDoc says so: the status filter belongs in the allocator,
  not in a query. **This story reads that flow and adds no read.**
- `app/.../data/repository/PaymentRepository.kt` -- `paymentsFor(contractId)` and
  `observePayments(contractId)` project entities to domain `Payment`. The rows need more than the
  domain value carries — method, account, cancel reason — so this story projects the entity
  itself through a new mapping beside the repository, the same shape
  `BusinessIdentityRepository` established for settings.
- `app/.../ui/screens/setoran/SetoranViewModel.kt` -- `requestCancel(paymentId)` and
  `openEditDeposit(paymentId)` are **already implemented and tested**, and both read the contract
  from `form.value`. Neither has a production caller: that is the whole of this story.
- `app/.../ui/screens/setoran/CancelDepositDialog.kt` -- built by Story 3.4, with the mandatory
  reason field and the impact sentence. Mounted here.
- `app/.../ui/screens/kontrak/KontrakScreen.kt:415-425` -- the exception sheet mounts from
  **this** file beside the open form and never from `KontrakRoute`, because two sheets over one
  state is the defect Story 2.8's review found as its only `high`. The deposit history mounts
  beside them the same way, as a second block inside the same sheet rather than a third window.
- `app/.../ui/screens/kontrak/ExceptionFormSheet.kt` -- the precedent for a *list with rows and
  verbs* inside a contract sheet, including the empty state and the 48dp floor.
- `domain/.../model/Payment.kt` -- carries id, date and amount only, and says why. The row's extra
  fields are the owner's, not the arithmetic's, so they do not belong on it.

**Gotchas carried over:**
- `SetoranViewModel` is a **separate ViewModel** whose form state is what both entry points read.
  Mounting them from `KontrakScreen` means the deposit sheet's ViewModel must be reachable there,
  which it currently is not — the sheet is mounted by the shell.
- Every new `Screens.Kontrak` callback is **required, not defaulted**.
- `KlinikNetTheme` is `staticCompositionLocalOf`; one `setContent` per Compose test.
- A `ModalBottomSheet` renders in its own window, so `performSemanticsAction` rather than
  `performClick`.

## Tasks & Acceptance

**Execution:**
- [ ] `app/.../data/repository/DepositHistoryRepository.kt` -- **new**: the projection the list
  reads, over the existing `observeForContract`. It carries what the owner reads — date, amount,
  method, account, notes, and the cancellation status and reason — and nothing the allocator
  needs.
- [ ] `app/.../ui/screens/setoran/SetoranViewModel.kt` -- add the history flow and an
  `openHistory(contractId)` that the two existing entry points already expect a form for.
- [ ] `app/.../ui/screens/kontrak/DepositHistoryBlock.kt` -- **new**: the rows, the two verbs,
  the cancelled marker and the empty state.
- [ ] `app/.../ui/screens/kontrak/KontrakScreen.kt` -- mount the block in the detail pane.
- [ ] `app/.../ui/shell/NavGraph.kt`, `MainActivity.kt` -- the callbacks, required, and the
  deposit ViewModel's reach into contract detail.
- [ ] `app/src/test/.../kontrak/DepositHistoryTest.kt` -- **new**: the whole matrix, including
  that a cancelled row carries its reason and offers neither verb.
- [ ] `app/src/test/.../kontrak/DepositHistoryWiringTest.kt` -- **new**: the two verbs **reach the
  handlers** — the assertion Story 3.4 and 3.5's deferrals have been waiting for.

**Acceptance Criteria:**
- Given a contract with deposits, when its detail is opened, then every deposit is listed with
  date, amount and method
- Given a cancelled deposit, when it is listed, then it is still shown, marked cancelled, with the
  owner's reason visible
- Given a cancelled deposit, when its row is inspected, then it offers neither the cancel nor the
  correct verb
- Given a confirmed deposit, when its cancel verb is tapped, then Story 3.4's dialog opens
- Given a confirmed deposit, when its correct verb is tapped, then Story 3.5's form opens
  pre-filled with the stored values
- Given a deposit is cancelled from this screen, when the list re-emits, then it shows cancelled
  with no reload
- Given the app builds, when tests run, then `:domain:test`, `:app:testDebugUnitTest` and
  `:app:assembleDebug` all pass

## Spec Change Log

## Review Triage Log

## Design Notes

**The read already exists, and its KDoc already answered the question this story asks.**
`observeForContract` returns every row including cancelled ones, and says so — the status filter
belongs in the allocator, not in a query. That decision was made in Story 3.4 precisely so an
audit trail could be rendered later, and this is that later. Adding a query here would have been a
second reader of the same table and would have quietly reintroduced the filter Story 3.4 refused.

**The two verbs were built to be mounted, not to be reimplemented.** `requestCancel` and
`openEditDeposit` were completed, tested and left unreachable. Their tests already prove the
behaviour; what was missing was a row with a button on it. The honest measure of this story's
success is therefore small: two controls that reach handlers which already work.

**A cancelled row offering a correct verb is the failure this story must not ship.**
`correctDeposit` refuses a cancelled deposit with `DepositAlreadyCanceledException`, and it would
keep refusing — so the control would render, accept a tap, and do nothing. That is the
"renders and is wired to nothing" defect this repo has shipped three times, and the epic's own
KDoc on `Screens.Kontrak` is the rule that forbids it.

## Verification

**Commands:**
- `./gradlew :domain:test` -- expected: SUCCESS
- `./gradlew :app:testDebugUnitTest` -- expected: SUCCESS
- `./gradlew :app:assembleDebug` -- expected: SUCCESS