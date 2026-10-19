---
title: 'UI audit part 1 — inset, FAB label, rail portrait, card width'
type: 'refactor'
created: '2026-10-19'
status: 'in-progress'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: 'a12422d1f5997e7abd1d354a0d4032f572b5a632'
context:
  - '{project-root}/_bmad-output/planning-artifacts/ux-designs/ux-dev.klinik.net-2026-10-13/DESIGN.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The app was audited on a real device (Xiaomi tablet, Android 16, **711dp × 1138dp
portrait**) and four structural defects showed. They are **not** a matter of taste, and **no JVM
test in this repo can catch any of them** — which is the finding:

1. **Zero `WindowInsets` handling in the entire app.** `enableEdgeToEdge()` at
   `MainActivity.kt:211` is consumed by nothing: a repo-wide grep for `WindowInsets | statusBars |
   systemBars | safeDrawing | windowInsetsPadding` returns **0 hits in `app/src/main/kotlin/**`.
   The search bar collides with the status bar; the FAB sits under the gesture bar.
2. **The FAB's label is a sibling, not a child.** `DepositFab.kt:71` emits a bare `Text` beside the
   `SmallFloatingActionButton`, so the `align(BottomEnd)` at `AppShell.kt:286` binds only to the
   button. The label lands at that `Box`'s **top-left** — which is exactly why "Catat Setoran"
   overlaps "Pengaturan" on screen. The KDoc at `:68-70` claims it "sits beside it"; it does not.
3. **Portrait shows a bottom nav; the prototype has none.** `shared.css:292` is
   `.bottom-nav { display: none; }` — unconditionally — and `shared.css:175` makes portrait
   `flex-direction: row`, keeping the **rail** at `--rail-w-compact` 72px with
   `.rail-item span { display: none }` (`shared.css:1030-1032`). Our `AppShell.kt:237` branches on
   *orientation* and does the opposite, so eight destinations share a bar and their labels collide.
   `KnNavRailWidthCompact` (72dp, `Navigation.kt:490`) exists and **is never passed**.
4. **Cards have no `fillMaxWidth()`.** The four sections in `PengaturanScreen.kt:428,519,610,701`
   hand-roll a `Surface` sized to its longest child, so Identitas usaha renders ~45% wide beside
   Data & cadangan at ~100%. None uses `KnCard`, which *does* fill (`Card.kt:65`) — and none carries
   its elevation either.

**Approach:** Fix the four roots, not the symptoms: consume insets once at the shell, make the label
a child of the FAB, branch rail-vs-bottom-nav on **width** per the prototype's own rule, and route
the four cards through `KnCard` inside an 840dp centred column.

## Boundaries & Constraints

**Always:**
- **The prototype is the design reference**, and every divergence is cited so it is visible rather
  than accidental.
- **Insets are consumed once, at the shell.** Every screen below inherits the clearance and none
  has to know about the status bar.
- **The touch floor never moves.** Every control still clears `KnAccessibility.minTouchTarget`
  (48dp). The compact rail hides labels; it never shrinks targets.

**Decided at the gate (2026-10-19):**

- **Nav: rail at ≥600dp, bottom nav below.** The prototype hides `.bottom-nav` outright, but it
  only ever previewed a 1280×800 tablet frame — so a bottom bar at 360dp contradicts nothing in
  it. The breakpoint is **width, not orientation**: 711dp portrait gets the compact rail (72px,
  labels hidden), and an 800dp landscape phone gets the bottom nav rather than an 88px rail eating
  25% of the screen.
- **Content column capped at 840dp, centred.** The prototype has no max-width only because its
  frame is fixed; at 711dp a full-width card puts a sentence 700dp long on screen.
- **`Lucide.Settings` for the rail's Pengaturan.** Verified present in the installed AAR. The four
  `Lucide.Landmark` sites *inside* `PengaturanScreen` keep it — they are bank accounts, and a bank
  building is the right noun there.

**Never:**
- **No new dependency.** `androidx.compose.foundation` already ships `WindowInsets`; the lucide
  AAR already contains `Settings` (1520 classes verified in the jar).
- **No hardcoded hex or dp in `screens/`.** The audit found **0** `Color(0x…)` outside `theme/`
  and exactly one literal per screen (a sheet's max height). That discipline holds.
- **No change to any screen's behaviour or copy.** This is layout, not content.
- **No `Scaffold` migration.** The shell's own `Box`/`Column` stays; it just consumes insets.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| INSET_TOP | Any screen | Content clears the status bar | N/A |
| INSET_BOTTOM | Any screen | Nav bar and FAB clear the gesture bar | N/A |
| RAIL_PORTRAIT | 711dp portrait | Rail, 72dp, labels hidden | N/A |
| NAV_PHONE | 360dp portrait | Bottom nav, 8 scrollable tabs | N/A |
| NAV_LANDSCAPE_PHONE | 800dp landscape | Bottom nav — the rail would eat 25% | N/A |
| FAB_LABEL | Any screen with the FAB | Label adjacent to the button and nowhere else | N/A |
| CARD_WIDTH | Pengaturan | Four cards, one width, 24px gutter, ≤840dp centred | N/A |
| ICON_SETTINGS | Rail | A gear; the bank button inside Pengaturan keeps the bank | N/A |

</frozen-after-approval>

## Code Map

**Roots, each verified on device:**

- `app/.../MainActivity.kt:211` — `enableEdgeToEdge()` with no consumer anywhere.
- `app/.../ui/shell/AppShell.kt:188` `Box(fillMaxSize)` → `:213` `Column(fillMaxSize)` →
  `:214` `GlobalSearchBar` at y=0. `GlobalSearchBar.kt:80` has only *content* padding
  (`vertical = spacing.sm`, 8dp), which cannot clear a 24-32dp status bar.
- `app/.../ui/shell/AppShell.kt:236` `Box(Modifier.weight(1f))` holds the graph, the FAB and the
  sheet; `DepositFab.kt:71`'s bare `Text` is a child of *this* Box → top-left.
- `app/.../ui/shell/AppShell.kt:237` branches on `isLandscape`; `:305` defines it from
  `Configuration.ORIENTATION_LANDSCAPE`. `KnNavRailWidthCompact` at `Navigation.kt:490` is dead code —
  `AppShell.kt:239-244` omits `compact = true`; the parameter defaults false at `Navigation.kt:103`.
- `app/.../ui/screens/pengaturan/PengaturanScreen.kt:428,519,610,701` — the four sections' modifier
  chains, no `fillMaxWidth()`. All four hand-roll `Surface` + `RoundedCornerShape(knRadii.lg)` and
  skip `KnCard`'s `knShadow` (`Card.kt:67`).

**Prototype reference:** rail 88px `shared.css:80`, compact 72px `:81`, top bar 72px `:82`,
tap-min 48px `:83`, card radius 20px `:60`, card padding 20px `:55` / `.card-pad-lg` 24px `:389`,
**page gutter `--sp-6` = 24px** `:285-289`, top-bar gutter 24px `:279`.

**Icons:** the lucide artifact is an **AAR** at
`~/.gradle/caches/modules-2/files-2.1/com.composables/icons-lucide-android-debug/1.0.0/*/icons-lucide-debug.aar`;
its `classes.jar` holds 1520 `com/composables/icons/lucide/*Kt.class`. Confirmed: `SettingsKt`
(→ `Lucide.Settings`), plus `CogKt`, `SlidersHorizontalKt`, `WrenchKt`.

**Tokens (complete, no gaps):** `theme/` holds `Color.kt`, `Type.kt`, `Spacing.kt` (xs4 sm8 md12 lg16
xl20 xxl24 xxxl32 huge40 giant48 colossal64), `Shape.kt`, `Elevation.kt`, `Motion.kt`,
`Accessibility.kt`; all exposed via `MaterialTheme.kn*` from `Theme.kt:32-45`.

**Do not change:** the `theme/` token *definitions* (already correct); `KnButton`, `KnModal`,
`KnPinGrid`, `KnNumPad` — all verified against the prototype by Story 2.1's review.

## Tasks & Acceptance

**Execution:**
- [ ] `app/.../ui/shell/AppShell.kt` -- the outer `Box` takes `Modifier.windowInsetsPadding(safeDrawing)`
      -- every screen below inherits the clearance.
- [ ] `app/.../ui/shell/DepositFab.kt` -- the label `Text` moves **inside** a `Row` with the button,
      and its KDoc stops claiming a placement the code never had.
- [ ] `app/.../ui/shell/AppShell.kt` -- pass `compact = true`; branch rail-vs-nav on
      `maxWidth >= 600.dp` rather than on orientation.
- [ ] `app/.../ui/components/Navigation.kt` -- `KnNavRail`'s `compact` plumbing, and the stale
      seven-destination KDoc at `:289-299`, which now describes eight.
- [ ] `app/.../ui/components/Card.kt` -- `KnCard` gains `fillMaxWidth()` as a default.
- [ ] `app/.../ui/screens/pengaturan/PengaturanScreen.kt` -- the four sections become `KnCard`
      inside an 840dp centred column.
- [ ] `app/.../ui/shell/Destination.kt` -- `Pengaturan` → `Lucide.Settings`.

**Acceptance Criteria:**
- Given any screen on a device with a status bar, then no content is drawn under it
- Given the FAB is visible, then its label is adjacent to the button and nowhere else
- Given a 711dp portrait tablet, then the rail is shown at 72dp with labels hidden
- Given a 360dp portrait phone, then the bottom nav is shown and scrolls
- Given Pengaturan, then all four cards are the same width, reach the 24px gutter, and the column
  is centred at no more than 840dp
- Given the rail, then Pengaturan's icon is a gear and its bank-account button still uses the bank
- Given the app builds, then `:domain:test`, `:app:testDebugUnitTest` and `:app:assembleDebug` pass

## Implementation Notes

## Spec Change Log

## Review Triage Log

## Design Notes

**Every one of these is invisible to the test suite, and that is the finding.** The repo has 971
tests, all measuring a node in a composition nobody has ever looked at. `assertExists()` cannot
see that a label sits 40dp above the button it belongs to; `assertHeightIsAtLeast` cannot see
that what it measured is underneath the clock. The device run is not a nicety at the end of a
story — it is the only measurement that could have caught any of this.

## Verification

**Commands:**
- `./gradlew :domain:test :app:testDebugUnitTest :app:assembleDebug` -- expected: SUCCESS
- `git grep -c "WindowInsets" app/src/main` -- expected: >0; it was **0** before this change
- `git grep -n "Landmark" app/src/main/kotlin/net/klinik/rental/ui/shell/Destination.kt` --
  expected: no match

**Manual checks (a device is required):**
- Portrait 711dp: nothing under the status bar; rail left, 72dp, no labels; FAB label beside the
  button; four equal cards; gear in the rail.
- Portrait 360dp: bottom nav present and scrollable; nothing under the gesture bar.
- Landscape: rail at 88dp **with** labels; the same clearance.
- Every screen scrolled to the bottom: the gesture bar clears the last control.