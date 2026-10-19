---
title: 'Layout prototype: master-detail + KPI cards on Mobil, Penyewa, Tunggakan'
type: 'refactor'
created: '2026-10-19'
status: 'draft'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/spec-ui-chrome-theme.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The prototype's layout is final and approved by every user, and three screens do not
match it. `cars.html:38-58`, `renters.html:39-44` and `arrears.html:39-46` share one page shape —
optional KPI row, optional filter row, then a **two-pane master-detail**: a 400dp list on the left,
a detail card on the right. The app draws a single list on all three. Tunggakan has a master-detail
in landscape only (`TunggakanScreen.kt:167`), which is itself a departure: the prototype's own
portrait rule **stacks** the two panes (`shared.css:1033`) rather than dropping the detail. Mobil and
Penyewa have no selection at all — their row taps open the edit sheet (`KendaraanScreen.kt:315`,
`PenyewaScreen.kt:248`), so there is nowhere for a detail pane to live.

Two of the three also carry cards the app is missing: Mobil a four-tile status summary
(`cars.html:42`), Tunggakan a four-tile risk summary (`arrears.html:41`).

**Approach:** Give all three screens the prototype's page shape: an optional KPI row, then a
master-detail whose left pane lists and whose right pane shows the selected item. Selection is a
property of the screen — a selected row highlights, the detail follows it, and the row's old verb
moves into the detail pane where the prototype puts it. On a narrow window the two panes stack, as
`shared.css:1033` does, rather than one being dropped.

## Boundaries & Constraints

**Always:**
- **The prototype's page shape is the specification.** `shared.css:285-289` page gutter 24dp;
  `:1018-1026` a 400dp list beside a flexible detail with a 20dp gap; `:1033` stacked when narrow;
  `:397-427` the KPI tile (label, value, meta, 40dp icon pinned top-right).
- **One figure, one source.** Every number a detail pane shows is computed by the same repository
  read that feeds the list; a pane that re-derived an amount would be a second answer to one bill.
- **Selection is derived, not carried twice.** When nothing is selected or the selection falls out
  of the filtered list, the first visible row is shown — `arrears.html:148` does exactly this.
- **The touch floor never moves.**

**Decided at the gate (2026-10-19):**
- **The KPI row uses the prototype's tile geometry for these two screens only.** Its icon floats
  top-right and its value is 36sp. `Beranda` and `Laporan` keep today's tile — their tests assert
  four equal heights precisely because *those* screens refuse a hierarchy, and the prototype's tile
  is the same height on all four anyway. Moving every caller is a separate change.
- **Penyewa gets no KPI row**, because `renters.html` has none: search in the bar, then the panes.
- **Tunggakan's chips move to the top bar** (`arrears.html:29-35`) and its list becomes the
  master-detail's left pane in both orientations.

**Never:**
- **No schema change and no new dependency.**
- **No change to what a write does.** Editing, ending, settling and reminding keep their existing
  sheets and ViewModel verbs; only the controls that invoke them move.
- **No mock data, no avatars** — separate specs (see deferred-work.md).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| NO_SELECTION | Screen opens with rows | The first row is selected and the pane shows it | N/A |
| SELECT | A row is tapped | It highlights and the pane switches to it | N/A |
| FILTERED_OUT | The selected row is filtered away | The first row of the new list is selected | N/A |
| EMPTY | The list has no rows | The detail pane is blank — not an empty state of its own | N/A |
| STACKED | Window narrower than the rail breakpoint | Panes stack; the list above, the detail below | N/A |
| LOADING | First read has not landed | No KPI figures and no pane — never four zeros | Skeleton in the list area |
| FAILED | The arrears read failed | No KPI figures, no rows, banner | The existing banner |

</frozen-after-approval>

## Code Map

- **Shared first.** `ui/components/Card.kt:186-250` — `KnKpiTile` gains the prototype's icon-in-the-
  corner shape behind a `layout` parameter (`Stacked` today, `Corner` for the new callers).
  `ui/components/MasterDetail.kt` (new) — the two-pane row that stacks below 600dp, the same
  breakpoint `AppShell.kt:377` uses.
- `ui/screens/kendaraan/KendaraanUiState.kt` — add `selectedId`, the four summary figures, and the
  open-contract facts a pane needs (`VehicleDetail`). `:178 matchesQuery`, `:213 countByStatus` stay.
- `ui/screens/kendaraan/KendaraanViewModel.kt:82` — one more `combine` input (`observeContracts()`);
  `select(id)` and `editSelected()`.
- `ui/screens/kendaraan/KendaraanScreen.kt:101-229` — rebuild the body: `KnKpiGrid` → chips →
  `MasterDetail(list, detail)`. `KendaraanRow:270` gains `selected` and a `select` click;
  `onEdit` moves to the pane's header button. Tags `TAG_ADD`, `TAG_EMPTY_FLEET`,
  `TAG_EMPTY_SEARCH`, `TAG_BANNER`, `TAG_ROW_PREFIX` all survive.
- `ui/screens/penyewa/PenyewaUiState.kt` / `ViewModel.kt:75` / `Screen.kt:87-205` — same shape, no
  chips and no KPI row; the pane shows the renter and their open contract.
- `ui/screens/tunggakan/TunggakanUiState.kt` / `ViewModel.kt:216` / `Screen.kt:144-180` — the KPI
  row, chips into `KnTopBar`, the master-detail in both orientations (`:145` `isLandscape` goes).
- `ui/model/ContractArrears` is the existing per-contract figure — reuse it for the summary counts.
- **Do not change:** `KontrakScreen` (the prototype has no contracts page), `Beranda`, `Laporan`,
  `Setoran`, `Pengaturan`, and every form sheet.

## Tasks & Acceptance

**Execution:**
- [ ] `MasterDetail.kt` + `KnMasterDetailPane` — a `BoxWithConstraints` that draws two panes at/above
      the breakpoint and one stacked column below. — one place for the prototype's rule.
- [ ] `KnKpiTile(layout = Corner)` — icon in the corner, value at `--fs-2xl`. — `cars.html:156-167`.
- [ ] Kendaraan: state, ViewModel, screen. — the first screen to the new shape.
- [ ] Penyewa: state, ViewModel, screen. — same shape, no summary.
- [ ] Tunggakan: summary, chips into the bar, panes in both orientations.
- [ ] Wiring in `MainActivity` for the added callbacks.

**Acceptance Criteria:**
- Given 711dp portrait, then each of the three screens draws its list above its detail, both
  reachable by scrolling
- Given 1280dp landscape, then the list is 400dp wide, the detail fills the rest, and the gap is 20dp
- Given Mobil, then four summary cards sit above the chips
- Given Tunggakan, then four summary cards sit above the panes and the risk chips are in the top bar
- Given Penyewa, then there is no KPI row and the search is in the top bar
- Given any of the three, then tapping a row switches the pane and the edit action lives in the pane
- Given the build, then `:domain:test`, `:app:testDebugUnitTest`, `:app:assembleDebug` pass

## Implementation Notes

## Spec Change Log

## Review Triage Log

## Verification

**Commands:**
- `./gradlew :domain:test :app:testDebugUnitTest :app:assembleDebug` -- expected: SUCCESS
- `git grep -n "ORIENTATION_LANDSCAPE" app/src/main/kotlin/net/klinik/rental/ui/screens` --
  expected: no match in kendaraan, penyewa or tunggakan

**Manual checks (device):**
- Portrait 711dp and landscape: the three screens match `cars.html` / `renters.html` /
  `arrears.html` pane for pane, including the selected-row highlight.
