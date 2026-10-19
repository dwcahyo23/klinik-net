---
title: 'Chrome: search in the top bar, light/dark, 24dp gutter'
type: 'refactor'
created: '2026-10-19'
status: 'draft'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/spec-audit-restyle-ui.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** After audit batch 1 (insets, FAB label, portrait rail, card width) the app still does
not read like `prototype/`, and the owner named three gaps. (1) **The global search blocks**: it is
a full-width row *above* the shell (`AppShell.kt:248-269`), so every screen is pushed down by 64dp
and the owner sees two search fields stacked — the shell's and the screen's own. The prototype puts
one field *inside* the 72dp top bar, 280px wide, beside a theme button (`index.html:92-97`).
(2) **There is no light/dark choice.** The prototype ships both (`shared.css:93-108`) and toggles
between them (`shared.js:142-152`); this app hard-wires `isSystemInDarkTheme()`
(`MainActivity.kt:221`), so the dark palette is written, tested and unreachable.
(3) **The page gutter is 16dp** on every screen; the prototype's is 24dp (`shared.css:288`).

**Approach:** Move the search into the top bar as the prototype's capped pill, with its results in a
dropdown anchored under it; add the prototype's circular theme button beside it and persist the
choice; widen the gutter to 24dp. The frame around every screen then matches the prototype in both
themes.

## Boundaries & Constraints

**Always:**
- **The prototype is the reference**; every divergence is named here so it is visible.
- **The touch floor never moves** — every control still clears 48dp.
- **Tokens only.** No hex or raw dp in `screens/`; the two icon glyphs come from Lucide.
- **Each screen keeps its own search bar.** It is the only control that *widens* a list the global
  search narrowed (`AppShell.narrowTo`), so removing it would strand the query with no writer.

**Decided at the gate (2026-10-19):**

- **The theme default stays `system`.** An unwritten setting follows the OS, so today's behaviour is
  preserved and the first launch on a dark tablet is not a surprise; the button then writes an
  explicit `light`/`dark`. The prototype defaults to light — this is the one deliberate divergence,
  because system-following already ships and is not what the owner asked to lose.
- **The search reaches the top bar through a `CompositionLocal`,** so all eight screens' `KnTopBar`
  call sites stay untouched and a screen rendered alone in a test still draws no field.
- **Results are a `DropdownMenu`**, not a hand-positioned overlay: it already anchors to the field,
  dismisses on outside tap and on Back, and lives in its own window so it cannot push the
  destination down.

**Never:**
- **No new dependency**, and **no framework change** — Compose stays; the Flutter remark is recorded
  as an opinion, not an instruction.
- **No change to matching.** `SearchMatch.kt` and `GlobalSearchViewModel` are untouched.
- **No avatars, no mock data, no schema change.** Both are separate specs (see deferred-work.md).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| SEARCH_IN_BAR | Any screen | The field is inside the 72dp bar; nothing occupies a row above the graph | N/A |
| SEARCH_RESULTS | A query is typed | A dropdown under the pill; the destination stays visible behind it | Outside tap or Back dismisses and clears |
| SEARCH_PICK | A result is tapped | Same navigation and `narrowTo` as today, overlay closed | N/A |
| THEME_DEFAULT | Fresh install | Resolves from the OS | N/A |
| THEME_TOGGLE | Button tapped | Whole app switches; choice survives a restart | N/A |
| THEME_ICON | Light / dark | Sun / Moon, as `dashboard.html:147` | N/A |
| GUTTER | Any screen | 24dp horizontal page padding | N/A |
| NARROW | 360dp wide | The pill shrinks to its share; the title truncates | Bar never overflows |

</frozen-after-approval>

## Code Map

- `app/.../ui/shell/AppShell.kt:248-269` — the `Column` and the `GlobalSearchBar` row to delete; the
  graph moves up into the existing `Box`. `narrowTo`, `dismissOverlayOnNavigate` and the
  `onSelect` navigation body stay exactly as they are.
- `app/.../ui/shell/GlobalSearchBar.kt` — keeps its signature and its four tags; becomes the pill
  (`widthIn(max = 280.dp)`) plus a `DropdownMenu` anchored in a `Box`.
- `app/.../ui/components/Navigation.kt:425` — `KnTopBar`. The actions `Row` is drawn unconditionally;
  the search slot first, the screen's `actions` next, the theme button last. `:450`
  `padding(horizontal = spacing.xl)` → `spacing.xxl`.
- `app/.../ui/theme/Theme.kt:24` — already takes `darkTheme`; unchanged. The new
  `LocalThemeControl` lives in a small `ui/theme/ThemeControl.kt` beside it.
- `app/.../MainActivity.kt:221` — `isSystemInDarkTheme()` becomes the resolved mode; the repository
  is built once from the database singleton, like `pinLock` at `:201`.
- `app/.../data/db/dao/SettingDao.kt` — one added `@Query(...): Flow<String?>`; Room's invalidation
  tracker re-emits for `settings`, which is the table the query names.
- `app/.../data/repository/ThemeRepository.kt` — new, small: read, observe, write one key.
- Body padding in the eight screens (`beranda`, `kendaraan`, `kontrak`, `laporan`, `pengaturan`,
  `penyewa`, `setoran`, `tunggakan`): horizontal `spacing.lg` → `spacing.xxl`.
- **Do not change:** `ui/components/SearchBar.kt` (`KnSearchBar` is already the prototype's pill),
  `ui/shell/GlobalSearchViewModel.kt`, `ui/SearchMatch.kt`, `theme/Color.kt`.

## Tasks & Acceptance

**Execution:**
- [ ] `data/repository/ThemeRepository.kt` — `observeMode(): Flow<ThemeMode>`, `setMode(ThemeMode)`,
      `ThemeMode { System, Light, Dark }` with `resolve(systemDark: Boolean): Boolean`. — one reader
      for the one stored key.
- [ ] `data/db/dao/SettingDao.kt` — add the observing query. — a `Flow` is what lets the toggle
      repaint without restarting the Activity.
- [ ] `ui/theme/ThemeControl.kt` — `LocalThemeControl` (nullable, `compositionLocalOf`) carrying
      `dark: Boolean` and `onToggle`. — the top bar is rendered by eight screens; a parameter would
      be eight edits.
- [ ] `ui/components/Navigation.kt` — draw the actions `Row` always; render the search slot, the
      screen's actions, then `ThemeToggleButton`; widen the bar's gutter to 24dp. — matches
      `index.html:92-97`.
- [ ] `ui/shell/GlobalSearchBar.kt` — cap the pill at 280dp and move the results into a
      `DropdownMenu`. — stops it occupying a row.
- [ ] `ui/shell/AppShell.kt` — delete the `Column` and the search row; provide the slot around the
      graph; drop the now-redundant `BackHandler`. — one bar, one row.
- [ ] `MainActivity.kt` — collect the mode, resolve it, provide `LocalThemeControl`, pass
      `darkTheme` to `KlinikNetTheme`.
- [ ] The eight screens — horizontal padding to `spacing.xxl`. — the prototype's `--sp-6`.

**Acceptance Criteria:**
- Given any screen, when it is drawn, then the search field is inside the top bar and no row sits
  above the graph
- Given a typed query, when results appear, then they are anchored under the pill and the
  destination remains visible
- Given a fresh install on a dark device, then the app opens dark; given the button is tapped, then
  it switches and the choice survives a restart
- Given any screen, then its horizontal page padding is 24dp
- Given the build, then `:domain:test`, `:app:testDebugUnitTest` and `:app:assembleDebug` pass

## Implementation Notes

## Spec Change Log

## Review Triage Log

## Verification

**Commands:**
- `./gradlew :domain:test :app:testDebugUnitTest :app:assembleDebug` -- expected: SUCCESS
- `git grep -n "isSystemInDarkTheme" app/src/main` -- expected: one hit, inside the mode resolver

**Manual checks (device):**
- Portrait 711dp: the search pill sits in the bar beside the theme button; no second row above the
  content; tapping the button flips the whole app, including the rail and the cards.
- 360dp: the bar still fits, the title truncates rather than the pill overflowing.
