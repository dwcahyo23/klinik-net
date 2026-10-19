---
title: 'Story 5.4 — Mengubah tanggal cutoff dengan melihat dampaknya'
type: 'feature'
created: '2026-10-19'
status: 'in-progress'
baseline_revision: 'a8bd913ea57b1a248019fa0cf276f6ab66822786'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-5-context.md'
warnings:
  - oversized
---

<intent-contract>

## Intent

**Problem:** The cutoff day decides which days a billing period contains, and therefore what every
arrears figure in the app means. Story 1.5 settled the semantics — *"digeser, bukan diturunkan
ulang"* — and shipped the pure function that computes a shift, but nothing in the app can **change**
the setting. The only way to move it today is to edit the database by hand, which the owner cannot
do and which would skip the one thing the AC insists on: seeing what the change costs **before**
it happens.

**Approach:** A cutoff control in `Pengaturan`, a **mandatory** preview that computes every
contract's arrears under the old and the new day and reports how many contracts and how much
rupiah move, and a confirm that writes **one settings row** — nothing else. No allocation row is
touched, because changing the range must never rewrite the facts.

## Boundaries & Constraints

**Always:**
- **The preview is mandatory and comes before the write.** There is no path from "pick a day" to
  "written" that skips it. Structurally: the ViewModel exposes `requestCutoffChange(day)` and
  `confirmCutoffChange()`, and no public function writes the setting directly.
- **The write is one row.** `setCutoffDay` calls `settingDao.putSetting` inside a transaction and
  nothing else. The AC's *"tidak ada baris alokasi yang dihapus atau ditulis ulang"* is then a
  property of the code rather than of a reviewer's memory — there is no second statement that
  could touch `allocation`.
- **The preview compares the same contracts under two day lists.** Every contract's arrears is
  computed twice, with the old and the new cutoff, over the same stored deposits. Nothing is
  written between the two reads.
- **A contract whose amount does not change is not counted as changed.** The figure the AC asks
  for is *"berapa Kontrak … yang berubah"* — a count of contracts, not of contracts examined.
- **The day is validated at 1–28** through `CutoffPeriod.requireValidCutoffDay`, which already
  exists and already explains why 28 rather than 31: `withDayOfMonth(29)` throws in a common-year
  February, so a larger cutoff would make period construction fail for most of the year.
- **Every arrears read after the write uses the new cutoff with no recomputation step.** That is
  not something this story implements — it is what the architecture already does, because
  `previewAllocation` reads the cutoff from `settings` on every call. Worth stating so nobody adds
  a "recalculate" button later.

**Never:**
- **No recalculation job, no migration, no rewrite pass.** The AC's "semua Tunggakan dihitung
  ulang" is satisfied by the read being cutoff-derived, not by a batch update.
- **No writing an allocation row from this story.** Not to "fix" a shift, not to re-credit a
  surplus. Re-crediting is `allocatePayments`'s job in the period that contains the day, and
  Story 1.5's KDoc says so.
- **No change to `CutoffPeriod`, `chargeDaysIn`, `arrearsOf` or `cutoffShift`.** The domain half of
  this story is already built and tested; this story is the surface that reaches it.
- **No preview that reads from the database twice.** A preview computed from one read and a write
  from another could disagree, and the owner would approve a number the app then did not honour.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| CUTOFF_PICK | The control is opened | Days 1–28 are offered; the stored day is selected | N/A |
| CUTOFF_PREVIEW | 25 → 15 chosen | A dialog: how many contracts change, the rupiah delta, and the two period labels | N/A |
| CUTOFF_CANCEL | Preview shown, owner cancels | Nothing written; every table byte-identical | N/A |
| CUTOFF_CONFIRM | Preview confirmed | `cutoffDay` written in one transaction; success snackbar | N/A |
| CUTOFF_NO_ROWS | A database with no allocations | Preview shows 0 contracts changed and a 0 delta | N/A |
| CUTOFF_SAME_DAY | The stored day is picked again | Preview shows 0 changed; confirming writes the same value | N/A |
| CUTOFF_OUT_OF_RANGE | 0 or 29 requested | Refused before any read | `InvalidCutoffDayException` |
| CUTOFF_NO_ALLOCATIONS_TOUCHED | Any confirm | Every `allocation` row identical, before and after | N/A |
| CUTOFF_DEFAULTS | Nothing stored | The control shows 25 — `DEFAULT_CUTOFF_DAY` | N/A |

</intent-contract>

## Decisions taken at the gate

- **The preview lives in `PaymentRepository`, not in a new class.** Because `previewAllocation` is
  private there, and it is the **one** function that narrows charge days through `settledDays` and
  allocates the stored deposits. A second reader that re-derived arrears for a preview would be a
  second answer to "what does this contract owe", and the two would drift the first time either
  changed. The repository grows a `cutoffDayOverride` parameter instead.
- **`cutoffDayOverride` is a parameter on `previewAllocation` and `arrearsEntry`, defaulting to
  `null`.** `null` means "read storage", which is what every existing caller gets and what keeps
  them unchanged. The override exists for exactly one caller — the preview — and its KDoc says so.
- **The preview reports a count and a delta, not per-contract rows.** The AC asks for *"berapa
  Kontrak dan berapa rupiah"*. A list of fourteen contracts with before/after figures is a screen
  the owner would have to read rather than answer a yes/no question with, and the decision is
  binary: change the cutoff or do not.
- **Confirming the same day is allowed and writes the same value.** A disabled button would need a
  reason on screen, and "nothing changed" is not a refusal — it is a no-op the owner is entitled to
  perform. The dialog says so instead.
- **The two period labels are shown, not just the numbers.** `25 Feb – 24 Mar 2026` →
  `15 Mar – 14 Apr 2026` is what makes the delta intelligible; a bare "Rp 750.000 lebih tinggi"
  tells the owner the figure moved without telling them why.

## Code Map

**Read-only evidence — do not modify:**
- `domain/.../calculate/CutoffShift.kt` — `cutoffShift(contractId, oldChargeDays, newChargeDays,
  allocations, dailyRate)`. **Story 1.5's function, and this story does not call it**: the app's
  arrears read re-derives rows from deposits rather than reading stored ones, so the two day lists
  come from two `previewAllocation` calls. The KDoc's guarantee — no row deleted or rewritten — is
  what this story's write path must preserve, and it does so by writing one settings row.
- `domain/.../calculate/CutoffPeriod.kt:77-95` — `containing(cutoffDay, today)` and
  `requireValidCutoffDay`, with the 1–28 bound and its February explanation.
- `app/.../data/repository/PaymentRepository.kt:228-330` — `previewAllocation`, whose step 2 reads
  the cutoff from `settings`. **The one line this story parameterises.**
- `app/.../data/repository/PaymentRepository.kt:747-785` — `arrearsEntry`, the per-contract read
  that `observeArrearsForAllContracts` maps over. It gains the same override.
- `app/.../data/repository/PaymentRepository.kt:1007-1020` — `KEY_CUTOFF_DAY` and
  `DEFAULT_CUTOFF_DAY = 25`, with the KDoc that says Story 5.4 is what changes it.
- `app/.../ui/screens/pengaturan/PengaturanScreen.kt` — the card pattern (`IdentitySection`,
  `BackupSection`, `SecuritySection`) and the single-banner slot with three failure sources.

**Modified:**
- `app/.../data/repository/PaymentRepository.kt` — `cutoffDayOverride` on the two private reads;
  new `cutoffDay()`, `previewCutoffChange()`, `setCutoffDay()`; `CutoffChangePreview`.
- `app/.../ui/screens/pengaturan/PengaturanUiState.kt` — the cutoff figure and the pending preview.
- `app/.../ui/screens/pengaturan/PengaturanViewModel.kt` — the pick/preview/confirm flow.
- `app/.../ui/screens/pengaturan/PengaturanScreen.kt` — the card and the dialog.
- `app/.../ui/shell/NavGraph.kt`, `MainActivity.kt`, `strings.xml`, `RStrings.kt`.

**Gotchas carried over:**
- `combine` has typed overloads to five; the settings screen already nests two pairs into one.
- Every new `Screens.*` callback is **required, not defaulted**.
- A ViewModel test must build repositories without the database handle under
  `StandardTestDispatcher`.
- `KlinikNetTheme` is `staticCompositionLocalOf`; one `setContent` per Compose test.

## Tasks & Acceptance

**Execution:**
- [ ] `PaymentRepository` — `cutoffDayOverride` threaded through `previewAllocation` and
      `arrearsEntry`; `cutoffDay()`, `previewCutoffChange(newDay, asOf)`, `setCutoffDay(newDay)`;
      `CutoffChangePreview`.
- [ ] `PengaturanUiState` — `cutoff: CutoffSettingsUi?`, `cutoffPreview: CutoffPreviewUi?`.
- [ ] `PengaturanViewModel` — `requestCutoffChange`, `confirmCutoffChange`, `dismissCutoffPreview`.
- [ ] `PengaturanScreen` — the cutoff card and the preview dialog.
- [ ] `NavGraph`, `MainActivity`, `strings.xml`, `RStrings.kt`.
- [ ] `app/src/test/.../setoran/CutoffChangeTest.kt` — **new, against a real Room database**: the
      preview's counts and delta, no allocation row touched, one row written, the same-day no-op,
      out-of-range refused.
- [ ] `app/src/test/.../pengaturan/PengaturanScreenTest.kt` — the card, the preview, confirm, cancel.

**Acceptance Criteria:**
- Given the cutoff control, then days 1–28 are offered and the stored day is selected
- Given a new day, then a preview shows how many contracts change and the rupiah delta **before**
  anything is written
- Given the preview is cancelled, then nothing is written and every allocation row is unchanged
- Given the preview is confirmed, then `cutoffDay` is written in one transaction and no other row
  changes
- Given the write has happened, then every arrears figure reflects the new day with no
  recomputation step
- Given a day outside 1–28, then it is refused before any read
- Given the app builds, when tests run, then `:domain:test`, `:app:testDebugUnitTest` and
  `:app:assembleDebug` all pass

## Spec Change Log

## Review Triage Log

## Auto Run Result

## Design Notes

**The preview cannot use `cutoffShift`, and that is worth stating because the function looks
purpose-built for it.** `cutoffShift` takes the *stored allocation rows* and computes two entries
over one shared row list — which is exactly the property Story 1.5 wanted to prove. But this app's
arrears read does not read stored rows: `previewAllocation` re-derives them from the deposits via
`allocatePayments`, and its KDoc explains why (a stored read would let the preview disagree with
the save the moment history and re-derivation drifted). So the honest preview runs
`previewAllocation` twice with two cutoff days, and the "same deposits, two ranges" property is
preserved by the deposits being the input rather than the rows. The domain function remains the
proof that the *rule* is a shift; the repository is the implementation of that rule over a
re-derivation.

**The write path is one statement, and that is the whole guarantee.** `setCutoffDay` calls
`putSetting` inside `inTransaction` and nothing else. Every other write in this app touches at
least two tables; this one touches one, because the AC's *"tidak ada baris alokasi yang dihapus
atau ditulis ulang"* is satisfied by **not having code that could do it**. A test asserts every
`allocation` row is identical before and after, and the mutation that would break it — adding a
`deleteForContract` call "to be safe" — is not a change anyone would make by accident.

**The count is of contracts that moved, not of contracts examined.** Fourteen contracts where two
change is "2 kontrak berubah", because the owner's question is "what will this do to my books", and
a count of fourteen answers "how many contracts do I have". The delta is the sum over those same
contracts, computed from the same two reads — a second pass that summed differently would be the
second derivation this repo keeps finding.

## Verification

**Commands:**
- `./gradlew :domain:test` -- expected: SUCCESS
- `./gradlew :app:testDebugUnitTest` -- expected: SUCCESS
- `./gradlew :app:assembleDebug` -- expected: SUCCESS
- `git grep -n "deleteForContract\|deleteForPayment" app/src/main/kotlin/net/klinik/rental/data/repository/PaymentRepository.kt`
  -- expected: the two existing Story 3.4 call sites and **no new one** in the cutoff path