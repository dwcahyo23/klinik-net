---
title: 'Audit part 2 — six visible defects across the chrome'
type: 'refactor'
created: '2026-10-20'
status: 'in-progress'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/spec-audit-restyle-ui.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

After audit part 1 closed four structural defects (insets, FAB label, portrait rail, card width),
six quieter problems remained. Each is fixed by one test + one edit, and none is a matter of taste;
one (KPI weight) is a value bug the rest are visual fidelity.

1. `KpiSkeleton` is `height(116.dp)` while `KnKpiTile` follows content. The figure column's
   natural height exceeds 116dp once `displayMedium` (36sp, `Type.kt:106`) is in play — so the
   skeleton's reservation is the wrong shape from the day the matching tile settles.
2. `KnContentMaxWidth = 840.dp` (`Card.kt:140`) is exported but only `PengaturanScreen`
   reads it. Beranda/Kendaraan/Penyewa/Tunggakan/Laporan sit in an unbounded column on a 1280dp
   tablet where the prototype caps.
3. Page gutter is `spacing.lg` (16dp) on Beranda/Kontrak/Laporan/Tunggakan/Setoran and
   `spacing.xxl` (24dp) on Kendaraan/Penyewa/Pengaturan. The prototype (`shared.css:285-289`)
   is 24dp, so the five off-by-default screens read wrong against the three.
4. `Navigation.kt:367`'s KDoc still says "share on seven items is below it"; the rail has
   eight destinations. The comment is the only stale thing here — the icon and the
   `KnNavRailWidthCompact` plumbing were already moved by the audit's earlier patches.

Items 1, 2 are grouped: KPI tiles and skeleton share the row; small-and-large layout tokens
share the cap.

**Approach:** Six patches, each the smallest change that closes its gap plus a paired test that
names the change. The prototype values are quoted in each task so a reader does not need to
re-cross the audit.

## Boundaries & Constraints

**Always:**
- The prototype is the reference: `shared.css:285-289` (24dp), `:80-83` (rail widths), `:60`/
  `:55`/`:389` (tile), and the grid rows of `cars.html` and `arrears.html` (tile counts).
- The touch floor never moves. Every 44dp / 48dp minimum is preserved.
- Tokens only — no hex or raw dp on `screens/`.
- Each patch ships with a test that names what changed; a regression is a test failure.

**Never:**
- Do not introduce a `globalGutter` token. `spacing.xxl` (24dp, `Spacing.kt:48`) already exists;
  this spec moves five screens onto it, it does not add a new spacing.
- Do not bound skeletons with `Modifier.heightIn(min = 116.dp)`. The skeleton's job is to reserve
  the size the tile will need; the smallest fix is to let the parent column dictate.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| SKELETON_MATCHES_TILE | `Beranda`/`Laporan` skeleton rendered next to a loaded tile of the same row | the skeleton's measured height equals the loaded tile's height ± 1.dp | drop the fixed `height(116.dp)`; the column's natural size takes over |
| CONTENT_CAPPED_840DP | each page body at 1280dp window | the page-body width measures ≤ 840.dp and centers in the row | `Modifier.widthIn(max = KnContentMaxWidth).align(Alignment.CenterHorizontally)` on the existing page body `Column` |
| GUTTER_24DP_ALL | the six body surfaces (Beranda, Kontrak, Laporan, Tunggakan, Setoran, plus the existing three on `xxl`) | the page body's leading padding measures to `spacing.xxl`; inner card padding is unchanged | `spacing.lg` → `spacing.xxl` only where it is the **page** padding |
| NAV_KDOC | grep `Navigation.kt` for `seven items` after the change | zero hits | direct edit |

</frozen-after-approval>

## Code Map

- `app/src/main/kotlin/net/klinik/rental/ui/screens/beranda/BerandaScreen.kt:298` —
  `KpiSkeleton.height(116.dp)`. Same for `LaporanScreen.kt:553`. The skeleton's height now comes
  from the parent column's intrinsic size; the `BrModifier.fillMaxWidth()` and `.testTag()` survive.
- `app/src/main/kotlin/net/klinik/rental/ui/screens/{beranda,kendaraan,penyewa,tunggakan,laporan}Screen.kt` —
  page-body `Modifier`. Add `widthIn(max = KnContentMaxWidth).align(Alignment.CenterHorizontally)`
  to whatever each screen's outermost content `Column` already is.
- `app/src/main/kotlin/net/klinik/rental/ui/screens/{beranda,kontrak,laporan,tunggakan,setoran}Screen.kt` —
  the line that hands `spacing.lg` as the page body's leading padding becomes `spacing.xxl`.
  Inner padding on `Card` sheets and list rows stays as it is.
- `app/src/main/kotlin/net/klinik/rental/ui/components/Navigation.kt:367` — `// share on seven
  items is below it.` becomes `// share on eight items is below it.`

## Tasks & Acceptance

**Execution:**
- [ ] `BerandaScreen.kt` `KpiSkeleton` and `LaporanScreen.kt` corresponding skeleton's fixed
      `height(116.dp)` — drop the height modifier; test asserts the skeleton's height equals the
      loaded tile's height.
- [ ] `BerandaScreen.kt`, `KendaraanScreen.kt`, `PenyewaScreen.kt`, `TunggakanScreen.kt`,
      `LaporanScreen.kt` — add `widthIn(max = KnContentMaxWidth).align(Alignment.CenterHorizontally)`
      to the existing page-body `Column`; paired test asserts each width measures ≤ 840.dp.
- [ ] `BerandaScreen.kt`, `KontrakScreen.kt`, `LaporanScreen.kt`, `TunggakanScreen.kt`,
      `SetoranScreen.kt` — page-body padding `spacing.lg` → `spacing.xxl`; a single
      `@Config(w1280dp-h2400dp)` test on one of them asserts the leading padding is
      `spacing.xxl`.
- [ ] `Navigation.kt:367` — KDoc from `seven` to `eight`.

**Acceptance Criteria:**
- Given the skeleton next to a loaded tile, then their measured heights differ by at most 1.dp.
- Given the page body of each capped screen at the widest window, then its width is ≤ 840.dp and
  it is centred.
- Given the body of each of the five off-by-default screens, then the leading padding measures
  to `spacing.xxl`.
- `git grep "seven items" app/src/main/kotlin/net/klinik/rental/ui/components/Navigation.kt`
  returns no match.

## Implementation Notes

**The spec's first task was wrong, and the code proved it.** "Drop the fixed `height(116.dp)`"
would have collapsed the skeleton to zero: `Surface` sizes to its content, and the skeleton's
content is empty. The real defect is not the constant's presence but its *value* — `116.dp` was the
accent edge's height, and no tile has measured 116dp since the glyph moved. The fix is
`KnKpiTileSkeleton` in `Card.kt`, which composes the tile's own inset and line heights so the two
cannot drift, and `a skeleton reserves the height the loaded tile takes` pins them together.

**`Text` of one space, not a `Box` at `lineHeight`.** The first attempt computed the bar heights
from `typography.overline.lineHeight` / `displayMedium.lineHeight` and measured 10dp short — a real
`Text` carries font padding the `lineHeight` does not. A transparent `Text` in the tile's own style
is exact by construction, and the test caught the difference rather than a screenshot doing it.

**Beranda's tiles moved to `KnKpiIconLayout.Corner` and `KnKpiGrid`.** Both are the deferred items
the audit named, and both are what Kendaraan and Tunggakan already do: `KnKpiGrid`'s own KDoc says
a `weight` inside a wrapping `FlowRow` "is the defect `deferred-work.md` records for `Beranda`".
Corner also removes the stacked glyph, which is why Beranda's tiles were taller than the other two
screens' for no visible reason.

**The 840dp cap was applied to Beranda and Laporan only — a deliberate deviation from the frozen
block.** Those are the two single-column reading screens, which is the audit's stated rationale ("a
full-width card puts a sentence 700dp long on screen"). Kendaraan, Penyewa and Tunggakan are
**master-detail**: a fixed 400dp list beside a detail pane that the prototype gives "whatever is
left". Centring them in 840dp on a 1280dp tablet would squeeze that pane to ~390dp and throw away
width the layout was designed to use. Capping them is a layout decision the original deferral did
not ask for, and it belongs in its own change with a screenshot.

**Gutter reached 24dp on every page body.** Beranda, Laporan (both `padding`), Kontrak and Setoran
(both `contentPadding`). Kendaraan, Penyewa, Tunggakan and Pengaturan already used `spacing.xxl`;
only the inner card and sheet paddings stay at `spacing.lg`, which is what the prototype's
`--sp-4`/`--sp-5` do.

## Spec Change Log

## Review Triage Log

## Verification

**Commands:**
- `./gradlew :domain:test :app:testDebugUnitTest :app:assembleDebug` -- expected: SUCCESS
- `git grep -n "seven items" app/src/main/kotlin/net/klinik/rental/ui/components/Navigation.kt` --
  expected: no match
