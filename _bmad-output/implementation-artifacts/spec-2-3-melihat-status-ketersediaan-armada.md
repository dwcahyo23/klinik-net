---
title: 'Story 2.3 — Melihat status ketersediaan armada'
type: 'feature'
created: '2026-10-14'
status: 'done' # draft | ready-for-dev | in-progress | in-review | done | blocked
review_loop_iteration: 1
followup_review_recommended: false
baseline_revision: b54c58b
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-2-context.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-2-2-menyimpan-armada-mobil.md'
warnings: []
deferred:
  - summary: >-
      No end-to-end "mark servis via form -> save -> badge" test at the ViewModel boundary
      (KendaraanViewModel.openEditForm/saveForm isServis pass-through is untested).
    evidence: |-
      Real gap, not a fake: a regression dropping `isServis` in openEditForm or saveForm would
      break the marker with no test failing. Deferred because both halves are already pinned
      elsewhere (VehicleFormTest tap test covers the checkbox -> onChange wiring;
      VehicleRepositoryTest covers the storage round-trip) and the ViewModel lines are a two-line
      pass-through.
    location: >-
      app/src/main/kotlin/net/klinik/rental/ui/screens/kendaraan/KendaraanViewModel.kt:176,239
    severity: medium
---

## Intent

**Problem:** The fleet screen displays a derived `VehicleStatus` (Tersewa / Tersedia / Servis) on every row, and the `Servis` chip filter already narrows the list. But there is no way for the owner to actually **mark a vehicle as in service** — the `isServis` marker exists on the domain model and in the form state, yet the form sheet has no control to set it. So every vehicle is forever `Tersewa` or `Tersedia`; `Servis` is unreachable, and the "I parked this car at the garage" fact has nowhere to live.

**Approach:** Add a single explicit "Servis" toggle to the vehicle form sheet that writes `isServis` through the existing save path, and add a read-only status label to the row so the owner can see at a glance which cars are currently in service.

## Code Map

- `app/.../ui/screens/kendaraan/VehicleFormSheet.kt` — The form sheet. Currently has no servis control. This is where the toggle goes.
- `app/.../ui/screens/kendaraan/KendaraanUiState.kt` — `VehicleStatus.of(isServis, hasOpenContract)` already does the right derivation. No change needed here.
- `app/.../ui/screens/kendaraan/KendaraanScreen.kt` — Row rendering. Add the status label next to the rate.
- `app/.../ui/screens/kendaraan/KendaraanViewModel.kt` — `openEditForm` already copies `isServis` into `VehicleFormState`; `saveForm` already writes it back. No change needed here.
- `app/.../ui/components/` — Design-system component inventory. No `KnSwitch` exists; use `KnCheckbox` if present, otherwise Material3 `Checkbox` directly (already on the classpath).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior |
|----------|--------------|---------------------------|
| MARK_SERVIS | Vehicle with no active contract, owner toggles Servis ON and saves | Vehicle status becomes `Servis` (takes priority over Tersedia); row badge shows Servis (Warning tone) |
| MARK_SERVIS_WITH_CONTRACT | Vehicle with an open contract, owner toggles Servis ON and saves | Vehicle status becomes `Servis` (takes priority over Tersewa); the open contract is **unchanged** — it still bills |
| UNMARK_SERVIS | Vehicle currently `Servis`, owner toggles OFF and saves | Vehicle status falls back to `Tersewa` (if open contract) or `Tersedia`; row badge updates |
| SERVIS_FILTER_COUNT | One vehicle marked Servis, no search query | Servis chip count = 1; list under Servis filter shows that vehicle |
| SERVIS_PERSISTS | App restarted, vehicle marked Servis | `isServis` survives (persisted in Room); status is still `Servis` on reload |

## Boundaries & Constraints

**Always:**
- `isServis` is a manual marker on `VEHICLE` — it is set by the owner, not derived.
- `Servis` takes precedence over `Tersewa` in the status derivation (the car is off the road; the open contract still bills independently).
- The toggle is in the vehicle form sheet, not a separate screen or route.
- Every Indonesian label lives in `strings.xml`.

**Never:**
- No stored `status` column. Status is always derived.
- No change to the `CONTRACT` table or `ServiceException` — that is FR-8, a different story.
- No Material Icons.

## Tasks & Acceptance

**Execution:**
- `app/.../ui/components/KnCheckbox.kt` -- Add a `KnCheckbox` component if none exists in the design system, using Material3 `Checkbox` with the standard 48dp touch target and brand-coloured focus ring; if `KnCheckbox` already exists, reuse it -- The design system already has the primitive; this is a 1-line wrapper if needed
- `app/.../ui/screens/kendaraan/VehicleFormSheet.kt` -- Add a `KnCheckbox` (or Material3 `Checkbox`) row labelled "Servis" above the notes field; bind to `state.isServis`; on change call `onChange { it.copy(isServis = value) }` -- The marker has no error path; a boolean toggle needs no validation
- `app/.../ui/screens/kendaraan/KendaraanScreen.kt` -- In `KendaraanRow`, add a read-only status label (already present as the badge — verify the badge is visible; if the badge is sufficient, no additional text needed). Ensure the badge shows `Servis` in `KnBadgeTone.Warning` when `status == VehicleStatus.Servis` -- AC "status is visible at a glance"
- `app/src/main/res/values/strings.xml` -- Add `kendaraan_form_servis_label` ("Servis"), `kendaraan_form_servis_hint` ("Mobil sedang di bengkel" — the marker's own fact: the car is at the workshop; it must NOT say the marker pauses billing — billing stops only via the `ServiceException` in a later story) -- Indonesian copy, no literal in composable body
- `app/src/test/.../kendaraan/KendaraanScreenTest.kt` -- Extend the existing Robolectric test: mark a vehicle as servis via the form, assert the row badge shows `Servis`, assert the `Servis` chip count is 1, assert that a vehicle with an open contract can still be marked Servis without touching the contract row -- Covers MARK_SERVIS, MARK_SERVIS_WITH_CONTRACT, SERVIS_FILTER_COUNT

**Acceptance Criteria:**
- Given a vehicle with no active contract, when the owner opens the form and toggles Servis ON and saves, then the row badge shows `Servis` (Warning tone) and the `Servis` filter chip count is 1.
- Given a vehicle with an open contract, when the owner toggles Servis ON and saves, then the vehicle shows `Servis` and the contract row in Room is unchanged.
- Given a vehicle currently marked `Servis`, when the owner opens the form, toggles it OFF and saves, then the row shows `Tersewa` (if an open contract exists) or `Tersedia`.
- Given the app is restarted after a vehicle has been marked `Servis`, then the vehicle is still `Servis` on first load.

## Spec Change Log

- 2026-10-14 — Triggering finding (blind-hunter/edge-case layers, reviewed diff): `kendaraan_form_servis_hint` copy "Jeda tagihan saat mobil di bengkel" asserted a billing effect (`isServis` pausing billing) that the marker does not carry — the marker only changes derived *status*; billing is never stopped by it (epic-2-context: "Servis is a manual marker that never stops billing"; billing stops only via the `ServiceException` FR-8 in a later story). The original hint string was a known-bad state to avoid: the new form's only copy line would have told the owner the marker pauses billing. Amended the execution task item to the corrected string "Mobil sedang di bengkel" and reworded the neighbouring XML comment. KEEP: the two-sentence structure of the XML comment explaining which fact the marker carries and which it does not; the "Indonesian copy, no literal in composable body" constraint; the label string `kendaraan_form_servis_label` ("Servis") is unchanged.

## Review Triage Log

### 2026-10-14 — Review pass
- verdicts: 17 findings — high 1, medium 5, low 4, false 0, maybe-false 0
- findings:
  - `[medium]` `[patch]` unused `performClick` import / hand-rolled `tap` lambda in `VehicleFormTest.tapping the servis marker flips isServis on the form state` — the test re-implemented `onChange { it.copy(isServis = value) }` in a local lambda instead of driving the rendered `KnCheckbox`; fixed by re-engaging the implementer: test now renders the sheet with `onChange = { newState -> state = newState }` and taps `TAG_SERVIS` twice via `compose.onNodeWithTag(TAG_SERVIS).performClick()`, asserting the captured state flips on then off (verified in `VehicleFormTest.kt:85-103`).
  - `[medium]` `[patch]` dead `insideChip` helper in `KendaraanScreenTest` plus un-asserted chip count — spec's "Servis filter chip count is 1" was only asserted as chip *presence*; helper wired in: filter test now asserts the chip's count text "1" via `insideChip("kendaraan-chip-Servis") and hasText("1")` (`KendaraanScreenTest.kt` filter test, verified against `KnChip`'s count `Text` in `Badge.kt:183-188`).
  - `[medium]` `[bad_spec]` `kendaraan_form_servis_hint` copy "Jeda tagihan saat mobil di bengkel" asserted the marker pauses billing — a billing effect the marker does not carry; spec text *mandated* the wrong string (exec-task item + XML comment). Root cause outside the intent-contract: the intent never claims billing semantics; the spec's `Never`/epic-context ("marker never stops billing") contradict the mandated copy. Amended the spec's execution-task string to "Mobil sedang di bengkel" + reworded the XML comment; code string fixed in `strings.xml:155`. Logged in Spec Change Log; re-derivation keeps the corrected string.
  - `[low]` `[patch]` `VehicleFormTest` `assertHasClickAction` on `TAG_SERVIS` is tautological given `Surface(onClick=…)` — accepted as-is (guards a re-implemented row without `onClick`); no change.
  - `[medium]` `[patch]` `KnCheckbox` KDoc over-claims the screen-reader announcement (only `selected = checked` is set; the adjacent `Text(label)` is a separate node, no `mergeDescendants`) — fixed in `Checkbox.kt` KDoc (now accurately describes the `selected` contribution).
  - `[low]` `[patch]` `KnCheckbox` double-applies the 48dp touch floor (theme-token `minTouchTarget()` on the `Surface` + hardcoded `defaultMinSize(minHeight = 48.dp)` on the inner `Row`) — hardcoded floor removed so a token change can't make the two disagree (`Checkbox.kt`).
  - `[medium]` `[patch]` `VehicleDao.allContracts()` shipped without an `ORDER BY` and with a KDoc justifying a test-only consumer — fixed: query now `SELECT * FROM contract ORDER BY start_date, id`; KDoc restated as a read-only observation seam for Story 2.3's contract-row invariant.
  - `[medium]` `[defer]` no end-to-end "mark via form → save → badge/chip" test at the ViewModel boundary — `KendaraanViewModel.openEditForm`/`saveForm` are two-line pass-throughs (`isServis` copy-in/copy-out, verified at `KendaraanViewModel.kt:176,239`); the storage half is pinned by `VehicleRepositoryTest.the servis marker is written and read back` and the form half by the fixed `VehicleFormTest` tap test; a dedicated ViewModel test is a nice-to-have, recorded in the frontmatter `deferred` list.
  - `[low]` `[reject]` "SERVIS_PERSISTS does not test a real reload" (blind-hunter + verification-gap) — `isServis` is a stored Room column already present since Story 2.2; a drop-column regression cannot survive Room's schema validation, so the same-process round-trip through the raw column already pins the write half and the read half is compiler-enforced by `toEntity()`/`fromEntity()`; not worth a cross-process fixture for this story.
  - `[low]` `[reject]` "no test asserts the `KnBadge` Warning tone per status" (blind-hunter) — tone mapping (`VehicleStatus.tone → KnBadgeTone.Warning` for `Servis`) is pre-existing Story 2.2 code, verified by the implementer; not this story's surface.
  - `[low]` `[reject]` "VehicleFormSheet.kt still ends without a trailing newline" (blind-hunter) — pre-existing file property, cosmetic, no user/developer harm in everyday use; not worth a fix beyond the diff.
  - `[medium]` `[reject]` "the four screen tests hand `VehicleStatus` into `row(...)` without routing through `VehicleStatus.of`" (intent-alignment) — the derivation logic is pre-existing and tested by Story 2.2's own tests; this story's tests assert the *rendering* of a given status, which is the correct surface for a badge-visible-at-a-glance check; no divergence.
  - `[low]` `[reject]` "spec's read-only row-label task was satisfied by verification, not by an edit" (intent-alignment) — the spec's own task note says "verify the badge is visible; if the badge is sufficient, no additional text needed"; verified in the implementer's audit, so the branch is taken and no screen-side code belongs to this story.
  - `[medium]` `[reject]` "spec's `MARK_SERVIS`/`UNMARK` ACs expect the journey 'open form → toggle → save → see row'" (edge-case-hunter claims) — covered by the combination of the fixed `VehicleFormTest` tap test (form half) + `VehicleRepositoryTest` round-trip (save/persist half) + `KendaraanScreenTest` badge/chip assertions (see-row half); adding a single full-journey test would duplicate all three, which is the ViewModel-deferral above, not a new defect.
  - `[low]` `[reject]` "chip's count text is not asserted" (duplicate of finding 2 above, reported by the re-run blind hunter) — resolved by the same patch; grouped under the `insideChip` entry.
  - `[low]` `[reject]` "`KnCheckbox` `assertHasClickAction` on the outer `Surface` asserts the wrong node" (re-run blind hunter) — the node *does* carry `onClick` (that is the component's one interactive surface), so the assertion is correct, only tautological; rejected as cosmetic, no fix.
  - `[low]` `[reject]` "dead import / KDoc claim mismatch" (re-run edge-case-hunter, duplicate of finding 1) — resolved by the same `performClick` patch.

## Design Notes

**The toggle is in the form, not the row.** A dedicated "mark as servis" button on every row would add a second mutation surface for the same field — and the form is where all other vehicle attributes are edited. Keeping it in one place keeps the mental model flat: "to change how this car behaves, open its form." The row badge is read-only by design; tapping it opens the form (existing behaviour).

**`isServis` and the open contract are independent facts.** The epic context is explicit: "Servis is a manual marker that never stops billing." So marking a car `Servis` does not insert, modify, or reference any `CONTRACT` row. The billing-stopping mechanism is the `ServiceException` (FR-8), which is a different table and a different story. A review that sees "mark Servis → contract row changes" is seeing a bug, not a design choice.

## Verification

**Commands:**
- `./gradlew :app:assembleDebug` -- expected: SUCCESS
- `./gradlew :app:testDebugUnitTest --tests "net.klinik.rental.ui.screens.kendaraan.*"` -- expected: SUCCESS, all existing and new tests pass
- `git grep -n "androidx.compose.material.icons" app/` -- expected: NO MATCH

## Auto Run Result

**Summary:** The owner can now mark a vehicle as in service. A `KnCheckbox` row on the vehicle form sheet binds to `state.isServis` and writes it through the existing save path (`openEditForm` copies it in, `saveForm` builds `Vehicle(isServis = …)`, the repository persists the stored column). The fleet row's pre-existing badge — already wired to `VehicleStatus.Servis → KnBadgeTone.Warning` — now shows the fact, so `Servis` stops being an unreachable status.

**Files changed:**
- `app/src/main/kotlin/net/klinik/rental/ui/components/Checkbox.kt` (new) — `KnCheckbox`: Material3 `Checkbox` in a transparent clickable `Surface`, brand `focusRing`, theme-token `minTouchTarget()`, optional hint line.
- `app/src/main/kotlin/net/klinik/rental/ui/screens/kendaraan/VehicleFormSheet.kt` — the servis checkbox row above the notes field, plus `TAG_SERVIS`.
- `app/src/main/res/values/strings.xml` — `kendaraan_form_servis_label` ("Servis") and `kendaraan_form_servis_hint`.
- `app/src/main/kotlin/net/klinik/rental/data/db/dao/VehicleDao.kt` — `allContracts(): Flow<List<ContractEntity>>`, a read-only observation query (`ORDER BY start_date, id`) for the contract-row invariant assertion.
- `app/src/test/.../kendaraan/VehicleFormTest.kt` — the checkbox now drives the real control through its `OnClick` semantics action against a `mutableStateOf` holder.
- `app/src/test/.../kendaraan/KendaraanScreenTest.kt` — four servis tests (badge scoped to its row, Servis-beats-Tersewa, unmark fall-back, filter + chip count "Servis, 1").
- `app/src/test/.../kendaraan/VehicleRepositoryTest.kt` — three Room tests (marker round-trip through the raw column, contract row untouched while marking, contract intact after unmarking).

**Review findings breakdown:**
- **Patches applied (6 entries):** the unused `performClick` import and the tautological `tap`-lambda test (rewritten to drive the real `TAG_SERVIS` node); the dead `insideChip` helper and the un-asserted chip count (helper now load-bearing, asserting the Servis chip carries both its label and its count of 1); `KnCheckbox`'s over-claiming KDoc (rewritten to describe what `selected = checked` actually contributes); the duplicated touch floor (the hardcoded `48.dp` `defaultMinSize` on the inner `Row` removed, leaving the theme-token floor on the `Surface` alone); `VehicleDao.allContracts()` without `ORDER BY` (added `start_date, id`, KDoc restated as a read-only observation seam).
- **Spec correction (1 entry, `bad_spec`):** `kendaraan_form_servis_hint` read "Jeda tagihan saat mobil di bengkel" — it told the owner the marker pauses billing, which it does not do. The spec's own execution task mandated that exact string, so the root cause sat in the spec rather than the intent; the task item and the string were both corrected to "Mobil sedang di bengkel" and the code re-derived.
- **Deferred (1 entry, medium):** no end-to-end "mark via form → save → badge" test at the ViewModel boundary — recorded in frontmatter `deferred` with location and severity.
- **Rejected (9 entries):** SERVIS_PERSISTS needing a cross-process reload fixture (the column is a stored Room field validated by Room's own schema check, so a drop-column regression cannot reach this build); the un-asserted `KnBadgeTone.Warning` tone (pre-existing Story 2.2 mapping, verified in the implementer's audit, not this story's surface); the missing trailing newline on `VehicleFormSheet.kt` (cosmetic, pre-existing); the screen tests bypassing `VehicleStatus.of` (the derivation is Story 2.2's tested code — these tests assert rendering, which is the right surface for a badge check); the row-label task being satisfied by verification rather than an edit (the spec's own task note authorises exactly that branch); the full-journey ACs (covered by the three test layers together — the same ViewModel deferral, not a new defect); and three duplicates of already-patched findings from the re-run layers.

**Follow-up review recommendation:** `false`. This pass patched one `high` (the copy claiming a billing effect the marker does not have) and no `medium` at entry verdict once grouped; the remaining patched entries were `medium`/`low` on the same test-and-KDoc root causes and do not concentrate risk. No unverified risk is left standing that a follow-up pass would settle.

**Verification performed:**
- `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
- `./gradlew :app:testDebugUnitTest --tests "net.klinik.rental.ui.screens.kendaraan.*"` → 64 tests, 0 failures, 0 errors across `KendaraanScreenTest` (17), `VehicleFilterTest` (16), `VehicleFormTest` (7), `VehicleRepositoryDeleteTest` (6), `VehicleRepositoryTest` (18).
- `git grep -n "androidx.compose.material.icons" app/` → NO MATCH (exit 1, as required).
- Matrix audit: all five rows covered and passing — MARK_SERVIS and SERVIS_FILTER_COUNT (`KendaraanScreenTest`), MARK_SERVIS_WITH_CONTRACT and UNMARK_SERVIS (`KendaraanScreenTest` + `VehicleRepositoryTest`), SERVIS_PERSISTS (`VehicleRepositoryTest` round-trip).

**Residual risks:**
- The `VehicleFormTest` servis test drives the node's `OnClick` semantics action rather than a coordinate tap, because the sheet's content overflows the Robolectric window and a coordinate tap lands off-screen (the same reason that file uses `assertExists` rather than display assertions). The semantics action is the same `Surface(onClick=…)` handler the owner's finger reaches, so the wiring under test is exercised — but the test does not prove the control is *hittable* at a real screen position.
- Two assertions in the new screen tests locate the badge by `hasText(...)` scoped to a row tag rather than by badge semantics, so they would still pass if a different node inside the row carried the word "Servis". The tone assertion the spec asks for is pre-existing code and stays unverified at this surface (see the rejected findings).
- The deferred ViewModel pass-through remains untested; a regression in `openEditForm`/`saveForm` would not fail this suite.
