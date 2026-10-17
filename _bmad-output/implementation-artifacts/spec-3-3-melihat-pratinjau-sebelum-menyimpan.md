---
title: 'Story 3.3 — Melihat pratinjau sebelum menyimpan'
type: 'feature'
created: '2026-10-17'
status: 'done'
review_loop_iteration: 0
followup_review_recommended: false
baseline_revision: 'bf9f52600c055e702c6f66eca3bbc3e5133c441f'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-3-context.md'
warnings:
  - oversized
deferred: []
---

<intent-contract>

## Intent

**Problem:** The owner trusts a number they never watched change. Story 3.2 records a deposit
in one tap; there is no step where the effect on arrears is shown before it is committed, so
a figure that surprises them can only be discovered afterwards — when the money is already
counted and the fix is a cancellation (Story 3.4, which itself raises arrears again).

**Approach:** Split today's single `saveDeposit` into a **preview** and a **confirm**, both
calling the *same* pure domain function. `PaymentRepository` gains `previewDeposit`, which
computes what the deposit would close **without writing anything**, and the sheet's Save
opens a mandatory `KnModal` that shows the before/after figures. The write path keeps its
own call to the same function, so the preview and the write cannot disagree.

## Boundaries & Constraints

**Always:**
- The preview is **mandatory** (UX-DR10: *"Pratinjau wajib, bukan opsional — termasuk kalau
  owner sudah tahu hasilnya"*). There is no "save without previewing" affordance, and the
  dialog's dismiss does **not** save.
- The preview is computed by the **same function** the save uses. This is the AC's central
  claim — *"dihitung dengan mesin yang tidak menulis apa pun … sehingga kalkulasi pratinjau
  dan kalkulasi penyimpanan tidak mungkin berbeda"* — and it is structural: one pure function
  called from both paths, not two implementations kept in step by hand.
- The format is **exact** (UX-DR10), and **never** a per-day ledger:
  `Tunggakan: Rp 750.000 → Rp 250.000` / `Menutup: 2 hari · Sisa: 1 hari (Rp 250.000)`.
  With credit: `Kredit: Rp 250.000 — dipakai otomatis untuk periode berikutnya`.
- **Credit never appears as a mystery number.** When it appears it carries its explanation,
  because the AC says so and because a figure with no explanation is the one thing the owner
  cannot check.
- All figures come from `formatRupiah`, pre-rendered in the ViewModel.
- The preview is shown **before** anything is written; nothing in the app can save without it.

**Never:**
- **No per-day dates** in the preview (the AC forbids it) and **no deposit-history screen** —
  the per-day audit trail is where 3.4 and Epic 4 read from.
- **No arrears, credit or risk display outside this dialog.** This story shows one preview,
  not a balance. `chargeDaysIn` and `arrearsOf` already exist for Epic 4's list.
- No edit, no correction, no cancel from here — 3.4's subject.
- No `settledThroughDate`, no `Payment` status, no new column or migration: `version` stays `6`.
- No re-derivation of the charge-day list in the UI. The preview asks the repository, which
  already owns that computation from 3.2.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| PREVIEW_EXACT | Arrears Rp 750.000 (3 open days), rate Rp 250.000, deposit Rp 500.000 | `Tunggakan: Rp 750.000 → Rp 250.000` and `Menutup: 2 hari · Sisa: 1 hari (Rp 250.000)` | No error expected |
| PREVIEW_CREDIT | Arrears Rp 750.000, deposit Rp 1.000.000 | `Tunggakan: Rp 750.000 → Rp 0`, `Menutup: 3 hari`, `Kredit: Rp 250.000 — dipakai otomatis untuk periode berikutnya` | No error expected |
| PREVIEW_NO_CREDIT | A deposit that does not exceed the obligation | **No** `Kredit:` line at all — the AC forbids a zero credit being shown | No error expected |
| PREVIEW_NO_ARREARS | Arrears already Rp 0 | `Tunggakan: Rp 0 → Rp 0`, `Menutup: 0 hari`; the whole deposit becomes credit | No error expected |
| PREVIEW_NEW_CONTRACT | A contract with no deposit history | Preview works — computed by the same machine as the arrears screen | No error expected |
| PREVIEW_WRITES_NOTHING | Preview then dismiss | Zero rows in `payment` and `allocation` | No error expected |
| PREVIEW_MATCHES_SAVE | Preview, confirm, re-read the rows | The preview's figures equal what the write produced | No error expected |
| PREVIEW_MANDATORY | Save pressed with a complete form | Dialog appears; dismissing it does not save | No error expected |
| PREVIEW_NO_DATES | Any preview | No per-day date appears anywhere in the dialog | No error expected |

</intent-contract>

## Code Map

**Read-only evidence — do not modify:**
- `domain/.../calculate/Arrears.kt:14-67` — `ArrearsEntry(contractId, days, amount,
  oldestOpenDay)` and `arrearsOf(...)`, which computes `amount = max(0, days × rate − Σ rows)`
  and `days = ` how many charge days are still underpaid. **Both preview figures come from
  here** — the AC's "same machine as the arrears screen" is this function.
- `domain/.../calculate/AllocationEngine.kt:61-137` — `allocatePayments`, already pure and
  already exercised by ~30 tests. The preview calls it; it never writes.
- `app/.../data/repository/PaymentRepository.kt:99-161` — the existing `saveDeposit`: it
  reads the contract, the cutoff day, the exceptions and the stored deposits, then calls
  `allocatePayments`. **The preview is this function's first four steps with step 5 removed** —
  which is why the extraction below is a refactor of existing code rather than a new
  computation.
- `app/.../ui/components/Feedback.kt:500-518` — `KnModal(visible, onDismissRequest, title,
  confirmLabel, onConfirm, dismissLabel, onDismiss, content)`, which **unmounts** when
  hidden rather than emptying it — the right default for a mandatory dialog.
- `app/.../ui/screens/setoran/SetoranUiState.kt` — `DepositFormState.canSave`, whose contract
  (a contract and, for a transfer, an account) is unchanged; the preview only *gates* the save.
- `app/.../ui/format/Rupiah.kt` — `formatRupiah`, the only money-to-text path.
- `app/.../ui/screens/setoran/DepositSheet.kt` — where the Save button and its sheet live.

**Reuse points:**
- `KnModal` — the preview dialog, with its `Primary` confirm and `Ghost` dismiss.
- `formatRupiah` — every figure, pre-rendered by the ViewModel.
- `PaymentRepository.saveDeposit` — unchanged; it keeps calling the extracted function.

**Gotchas carried over:**
- `KlinikNetTheme` is `staticCompositionLocalOf`; every Robolectric test wraps in it.
- A ViewModel test must **not** pass the database handle — `withTransaction` throws on the
  main thread and `StandardTestDispatcher` drains on it. The preview's pure path is the one
  place this does not bite, which is part of why it is a separate method.
- One `setContent` per test.
- Required callbacks, never defaulted.

## Tasks & Acceptance

**Execution:**

*Domain*
- **No new domain file.** `allocatePayments` and `arrearsOf` already exist and already say
  what they do. Adding a `preview` function to `domain/` would be a second way to ask the
  same question — and the AC's whole point is that there is only one.

*Data*
- `app/.../data/repository/PaymentRepository.kt` — extract the read-and-allocate half of
  [the existing method](PaymentRepository.kt:99-161) into a private
  `previewAllocation(contractId, payment, asOf)`, returning the computed `chargeDays` and the
  **new** deposit's rows. `saveDeposit` calls it and then writes; `previewDeposit` calls it
  and then returns. This is the change that makes the AC's claim structural rather than
  aspirational, and it is a **refactor of existing code** — the save's behaviour must not
  change.
- `app/.../data/repository/PaymentRepository.kt` — add
  `previewDeposit(contractId, payment, asOf): DepositPreview`, returning the before/after
  arrears, the days closed, the days remaining and the credit. **Pure**: it opens no
  transaction and writes nothing, and that is asserted.

*UI*
- `app/.../ui/screens/setoran/SetoranUiState.kt` — add `preview: DepositPreviewUi?`, and the
  pure formatting helpers that turn it into the exact UX-DR10 strings. Formatting lives in
  the ViewModel so the dialog renders text, never figures.
- `app/.../ui/screens/setoran/SetoranViewModel.kt` — split the save into
  `previewDeposit()` (computes and sets `preview`) and `confirmDeposit()` (writes, then
  closes both). Reuse `saveDepositAmount`'s in-flight guard for the confirm.
- `app/.../ui/screens/setoran/DepositSheet.kt` — mount a `KnModal` when `preview != null`.
  Dismiss discards it; Confirm saves. **No skip affordance.**
- `app/src/main/res/values/strings.xml` — a Story 3.3 block with the exact UX-DR10 sentences,
  parameterised by figure and count so there is one wording per shape.
- `app/.../ui/strings/RStrings.kt` — the accessors for those, each returning the sentence
  its scenario needs.

*Tests*
- `app/src/test/.../setoran/DepositPreviewTest.kt` — **new**, the domain-level proof that the
  preview and the write agree: for a set of figures, preview, then save, then read the rows
  back, and assert the preview's numbers equal the stored ones. Plus
  `PREVIEW_WRITES_NOTHING`.
- `app/src/test/.../setoran/SetoranViewModelTest.kt` — extend: `previewDeposit` populates the
  state and writes nothing; `confirmDeposit` writes and clears the preview; dismissing the
  preview writes nothing.
- `app/src/test/.../setoran/DepositSheetTest.kt` — extend: the dialog appears, its exact text
  for the AC's two worked examples, no `Kredit:` line when there is no credit, and **no
  per-day date** anywhere in it.
- `app/src/test/.../setoran/DepositSheetTest.kt` — the dismiss path: the dialog's dismiss
  does not save.

**Acceptance Criteria:**
- Given a complete form, when Save is pressed, then the preview dialog appears **before**
  anything is written, and it cannot be skipped or saved past
- Given arrears Rp 750.000 at Rp 250.000/day and a Rp 500.000 deposit, when the preview is
  shown, then it reads `Tunggakan: Rp 750.000 → Rp 250.000` and
  `Menutup: 2 hari · Sisa: 1 hari (Rp 250.000)`
- Given a deposit larger than the period's obligation, when the preview is shown, then a
  `Kredit:` line appears carrying its explanation, and **no** credit line appears at all when
  there is no credit
- Given any preview, when it is read, then it contains no per-day date
- Given a preview that is then dismissed, when the rows are read back, then `payment` and
  `allocation` are both empty
- Given a preview that is then confirmed, when the rows are read back, then they match the
  preview exactly — because one pure function produced both
- Given the app module, when it builds and its unit tests run, then `:domain:test`,
  `:app:testDebugUnitTest` and `:app:assembleDebug` all pass, `version` is still `6`, and
  no Material Icons appear

## Spec Change Log

## Review Triage Log

### 2026-10-17 — Review pass
- verdicts: 6 findings — high 0, medium 2, low 2, false 1, maybe-false 1
- findings:
  - `[medium]` `[patch]` **The preview tests were unreachable dead code inside a closed class
    body.** A 200-line block spliced into `SetoranViewModelTest` at a line-number index
    split `closing the sheet clears it` in half; everything after it landed below a `}`.
    The file still compiled, still ran 12 tests, and still **passed** — the new tests simply
    never executed. Caught only by comparing the `fun` count (19) against the run's count
    (12). Fixed by `git stash` to recover the clean base and rewriting them as
    `DepositPreviewViewModelTest` — where they belonged anyway, since they are UI-state
    claims rather than arithmetic ones.
  - `[medium]` `[patch]` **The matrix audit's first pass reported three false gaps** because
    the map named tests that do not exist. The tests were there under different names. The
    lesson is recorded in the audit itself: a coverage audit whose names are typed from
    memory measures the map, not the code.
  - `[low]` `[patch]` The repo-level grep for a per-day ledger hit four lines — all of them
    the KDoc *explaining* the rule. Re-run against the code rather than the comments, so
    the gate is worth running again in 3.4.
  - `[low]` `[reject]` The FAB's label placement was left as a documented choice rather than
    reworked. Rejected for the same reason as in 3.2: it is a design pass's call, and the
    FAB is a shell component whose surface 4.3 will revisit when Beranda arrives.
  - `[false]` `[reject]` The dismiss was flagged as reading "Batal", which would tell the
    owner they lost their work. Refuted: the *dialog's* dismiss reads "Cek dulu" and the
    *sheet's* close keeps "Batal" — two different controls, both correct. The first draft
    of that test asserted no "Batal" anywhere and failed, which is what surfaced the
    distinction.
  - `[maybe-false]` `[defer]` Whether the preview's `→` line wraps on a narrow landscape
    sheet. Robolectric measures nodes, not line breaks, so this is a device fact. Already
    deferred by this spec's Verification section.

## Auto Run Result

Status: done

### Summary

Story 3.3 shipped the mandatory preview, and met its central AC *structurally*: UX-DR10
says the preview and the save are computed by a machine that *"tidak menulis apa pun —
sehingga kalkulasi pratinjau dan kalkulasi penyimpanan tidak mungkin berbeda"*. "Tidak mungkin
berbeda" — *cannot* differ — is not a behaviour, so `PaymentRepository` now has one private
`previewAllocation` and the two public paths diverge only at the last line: one writes, one
returns.

The preview is **un-skippable by construction**, not by wiring discipline:
`SetoranViewModel.confirmDeposit` returns without writing when no dialog is up, so a caller
that skipped `previewDeposit` gets a refusal rather than a silent save.

### Files changed

**Data**
- `app/.../data/repository/PaymentRepository.kt` — `previewDeposit` (pure, opens no
  transaction), the extracted private `previewAllocation` shared by both paths, and
  `DepositPreview` with a field for each figure and **none** for a date.

**UI**
- `SetoranUiState.kt` — `preview: DepositPreviewUi?` and `isPreviewing`, plus the
  already-formatted `DepositPreviewUi`.
- `SetoranViewModel.kt` — `previewDeposit` / `dismissPreview` / `confirmDeposit`, the dialog's
  own `previewState` flow, and the exact UX-DR10 sentence assembly.
- `DepositSheet.kt` — the `KnModal`, mounted **outside** the sheet's own `ModalBottomSheet`
  so it composes in a separate window above rather than nesting two windows in one composable.
- `NavGraph.kt` / `MainActivity.kt` — the two required handlers.
- `strings.xml` / `RStrings.kt` — UX-DR10's format, one parameterised sentence per shape.

**Tests** — `DepositPreviewTest` (7), `DepositPreviewViewModelTest` (8), `DepositSheetTest`
(+8).

### Follow-up review recommendation

`false`. No patched `high`; both `medium` patches were caught by comparing expected to
actual rather than by a failing test.

### Verification performed

- `./gradlew :domain:test`, `:app:testDebugUnitTest`, `:app:assembleDebug` — **SUCCESS**.
  **834 tests, 0 failures, 0 skipped.**
- No Material Icons; `version` still `6`; `previewAllocation(` has exactly two call sites
  plus its own declaration — a third would be the drift the AC forbids.
- Matrix audit: **all 9 rows** covered by tests that ran and passed.

### Residual risks

One, device-side: whether the `→` line wraps on a narrow landscape sheet. Deferred to the
Epic 3 retrospective.

### A note on the near-miss

The spliced test block compiled, ran and passed while containing seven tests that never
executed. A green suite is not evidence that the tests in it ran — it is evidence that the
tests that ran passed. The only thing that caught it was comparing the `fun` count against
the run count, and that check is now part of how this story's audit is written.

## Design Notes

**The AC's demand is structural, not a behaviour to test.** *"Pratinjau dihitung dengan mesin
yang tidak menulis apa pun — dia murni membaca, sehingga kalkulasi pratinjau dan kalkulasi
penyimpanan tidak mungkin berbeda"* can be satisfied two ways: by writing a preview test that
compares the two outputs, or by making them *the same call*. The second is chosen because a
comparison test can only catch a difference at the moment it is written, whereas one function
cannot express a difference at all. So `previewDeposit` and `saveDeposit` share
`previewAllocation`, and the test that proves it (`PREVIEW_MATCHES_SAVE`) is a regression
net under an invariant, not the invariant itself.

**The dialog's dismiss does not save, and that is the whole reason it is mandatory.** If
dismiss saved, the preview would be a confirmation the owner could wave through — and a
preview that is waved through has taught them nothing. `KnModal`'s unmount-when-hidden
default is already right for this: no content, no window, no way to be dismissed *into* a
save.

**The format is specified by UX-DR10 down to the arrow, and the arrow is the point.** `Rp
750.000 → Rp 250.000` puts two numbers side by side so the *change* is legible without
arithmetic. This is also why the preview carries no per-day ledger: the owner's question is
"does this figure look right", and a table of dates is a thing they must verify rather than
one they can read. The per-day audit still exists — in the deposit history, which this
dialog deliberately points away from.

## Verification

**Commands:**
- `./gradlew :domain:test` -- expected: SUCCESS -- the allocator and arrears suites unchanged
- `./gradlew :app:testDebugUnitTest --tests "net.klinik.rental.ui.screens.setoran.*"` -- expected:
  SUCCESS
- `./gradlew :app:testDebugUnitTest` -- expected: SUCCESS
- `./gradlew :app:assembleDebug` -- expected: SUCCESS
- `git grep -n "version = 6" app/src/main/kotlin/net/klinik/rental/data/db` -- expected:
  one hit; this story adds no column
- `git grep -n "androidx.compose.material.icons" app/` -- expected: NO MATCH
- `git grep -n "allocatePayments\|arrearsOf" app/src/main/kotlin/net/klinik/rental/data/repository/PaymentRepository.kt`
  -- expected: **two** call sites of the same function, one per path -- a third, or a second
  implementation, is exactly the drift the AC forbids
- `git grep -n "preview" app/src/main/kotlin/net/klinik/rental/ui/screens/setoran/DepositSheet.kt`
  -- expected: the dialog is mounted **only** when the preview is non-null, and there is no
  save-without-preview path

**Manual checks — DEFERRED to the end of Epic 3:**

- On the Redmi Pad 2: record a deposit and confirm the preview dialog's two figures fit
  without wrapping, in both orientations. The format is fixed by UX-DR10, so the only open
  question is whether the `→` line breaks on a narrow landscape sheet — a layout fact no
  Robolectric test can answer, since it measures nodes, not line breaks.
