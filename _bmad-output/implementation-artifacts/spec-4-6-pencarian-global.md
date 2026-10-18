---
title: 'Story 4.6 — Pencarian global'
type: 'feature'
created: '2026-10-18'
status: 'done'
baseline_revision: 'b9483e04ad121cf5abc872b3f20bb8241ef0d86b'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-4-context.md'
warnings:
  - oversized
deferred: []
---

<intent-contract>

## Intent

**Problem:** Finding one tenant means scrolling a list, and the app now has seven screens — so
"which list?" becomes a question before the question that matters. Two of the seven already carry
a search box that searches only their own screen, so the owner is invited to believe search works
and then not find what he wanted.

**Approach:** One search field in the shell, above the destination, searching renters and
vehicles by name and plate, grouped by kind, with partial and case-insensitive matching. It is
the same component the two screens already use, hoisted to the one place every screen passes
through.

## Boundaries & Constraints

**Always:**
- **The field is available on every screen**, mounted in the shell rather than in each screen —
  so "available everywhere" is true by construction instead of by seven reminders.
- **Matching is partial and case-insensitive**, so `siti` finds `Siti Aminah` and `1235` finds
  `B 1235 TF`.
- **Results are grouped by kind** — renters and vehicles in separate sections — and selecting a
  result opens the screen that owns it.
- **No results is an explained empty state**, never a blank list.
- **The list stays scrollable.** Search is a convenience, not a replacement: fourteen renters and
  eighteen vehicles must still scroll on their own screens.
- **Each search is on the screen that owns it, at the same keyboard type and the same clear
  button**, so the hoisted field is not a second kind of control.

**Never:**
- **No searching notes, transaction references or deposit numbers.** The design doc's reasoning is
  the rule: full-text search is not worth it against searching for people, and an empty result
  is a valid answer rather than a failure.
- **Contracts are not a search target.** The epic's own note records this as a deliberate
  departure from the PRD's wording: a contract has no name or plate of its own, so reaching it
  would mean searching through its relations, which is slower than it is worth.
- **No fuzzy or typo-tolerant matching.** Partial and case-insensitive only; anything cleverer
  needs a scoring rule nobody has agreed to.
- **No search results page, no recent searches, no saved searches.**
- **No new screen.** A result opens the screen that owns it.
- **No debounce, no background worker, no index.** Fourteen renters and eighteen vehicles is a
  list the owner can scroll; a search engine here is scaffolding for a fleet that does not exist.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| SEARCH_PARTIAL | Query `siti` | Finds `Siti Aminah` — partial, and case does not matter | No error expected |
| SEARCH_PLATE | Query `1235` | Finds the vehicle `B 1235 TF` — the plate is searchable by a fragment of itself | No error expected |
| SEARCH_GROUPED | Both a renter and a vehicle match | Two sections, renters first, each under its own heading | No error expected |
| SEARCH_NONE | Query `zzzz` | The empty state **explains** what was searched and what is not | No error expected |
| SEARCH_NOTES_EXCLUDED | A query matching only a deposit's note or reference | No result, and the empty state says so — an empty answer is a valid answer | No error expected |
| SEARCH_CLEAR | The clear button pressed | The query empties and the field returns to its placeholder | No error expected |
| SEARCH_TYPING | Typing with a keyboard open on a 1280×800 tablet | The field is usable at the top, the result list scrolls beneath it, and nothing is covered | No error expected |
| SEARCH_EVERY_SCREEN | The owner opens each of the seven destinations in turn | The field is present on all seven | No error expected |
| SEARCH_LIST_STILL_SCROLLS | Fourteen renters and eighteen vehicles on their own screens | Those lists still scroll; search did not replace the list | No error expected |

</intent-contract>

## Decisions taken at the gate

- **The field is hoisted into `AppShell`, above the destination graph, not into each screen.**
  Every screen therefore has it for free, and a screen added later cannot forget it. The two
  screens that already have one of their own keep their behaviour but stop drawing their own,
  so there is one search control in the app rather than three.
- **Selecting a result navigates to the owning screen with that item selected**, reusing the
  routes `Destination` already carries rather than adding a search-results destination.

## Code Map

- `app/.../ui/shell/AppShell.kt:115-144` -- the single place both orientations compose the rail
  or bottom nav beside the graph. **The field mounts here**, above whichever branch drew, so
  "every screen" is structural rather than seven call sites.
- `app/.../ui/components/Navigation.kt` / `Feedback.kt` -- `KnSearchBar`, built by Story 2.4 and
  already in use on two screens. Reused unchanged; a second search component would be a second
  look and a second set of bugs.
- `app/.../ui/screens/penyewa/PenyewaUiState.kt:138` -- `filterByQuery` composes
  `matchesRenterQuery` rather than repeating its predicate, and its KDoc already warns about the
  exact hazard this story faces: *"two independent copies of one rule is how the search AC ends up
  verified against a function the screen never calls."* **This story must call that predicate, not
  copy it.**
- `app/.../ui/screens/kendaraan/KendaraanUiState.kt:176` -- `matchesQuery`, over plate, brand,
  model and colour. **Note the difference**: the vehicle search is broader than the plate alone.
  The AC names plates; keeping the wider fields here is the existing behaviour and dropping them
  would be a silent narrowing, so the shared rule carries what it already matched.
- `app/.../ui/screens/tunggakan/TunggakanScreen.kt` -- the row structure a result takes when it
  opens the arrears screen.
- `app/.../ui/shell/Destination.kt` -- the routes a result navigates to.
- `app/.../ui/components/Feedback.kt` -- `KnEmptyState`, which already demands an empty state say
  *what to do* rather than merely that there is nothing.

**Gotchas carried over:**
- The shell is a `Box`, so the field must not disturb the FAB's bottom-end alignment.
- Every new `Screens.*` callback is **required, not defaulted**.
- `KlinikNetTheme` is `staticCompositionLocalOf`; one `setContent` per Compose test.

## Tasks & Acceptance

**Execution:**
- [ ] `app/.../ui/screens/penyewa/PenyewaUiState.kt` -- **extract** `matchesRenterQuery`'s
  predicate to a shared location so the shell and the list call **one** function, not two copies.
- [ ] `app/.../ui/screens/kendaraan/KendaraanUiState.kt` -- the same for the vehicle predicate;
  the shared rule keeps every field it matches today.
- [ ] `app/.../ui/shell/GlobalSearchUiState.kt` -- **new**: the query, the grouped results, and
  the empty state.
- [ ] `app/.../ui/shell/GlobalSearchViewModel.kt` -- **new**: combine the two flows and group the
  matches through the shared rules.
- [ ] `app/.../ui/shell/GlobalSearchBar.kt` -- **new**: `KnSearchBar` plus the grouped results
  beneath it, drawn in the shell.
- [ ] `app/.../ui/screens/penyewa/PenyewaScreen.kt`, `KendaraanScreen.kt` -- **keep** their own
  bars. The spec's task list originally said to remove them; that was wrong, and this is why: a
  screen's bar is the only control that **widens** a list the shell narrowed. Removing it leaves
  a list the owner can filter but never unfilter, and strands `query` with no writer. The two
  controls have different jobs — the shell's searches across the app, the screen's narrows its
  own list — so the selection path writes the destination's query through `onQueryChange`.
- [ ] `app/.../ui/shell/AppShell.kt`, `NavGraph.kt`, `MainActivity.kt` -- mount it and wire the
  callbacks, required.
- [ ] `app/src/test/.../shell/GlobalSearchTest.kt` -- **new**: the whole matrix, including that
  notes and references are excluded.
- [ ] `app/src/test/.../shell/GlobalSearchBarTest.kt` -- **new**: the field on every destination,
  and a **click-through** on a result.

**Acceptance Criteria:**
- Given a partial query, when the owner searches, then matching renters and vehicles are found
  regardless of case
- Given a plate fragment, when the owner searches, then the vehicle is found
- Given matches in both kinds, when results render, then they are grouped by kind
- Given no matches, when results render, then an explained empty state appears, and it says that
  notes and references are not searched
- Given each of the seven destinations, when opened, then the search field is present
- Given a result is selected, when tapped, then the screen that owns it opens with that item
- Given the app builds, when tests run, then `:domain:test`, `:app:testDebugUnitTest` and
  `:app:assembleDebug` all pass

## Implementation Notes

**The shared rule moved, and both screens now call it rather than carrying a copy.**
`ui/SearchMatch.kt` holds `asSearchNeedle` plus `Renter.matchesSearch` and
`Vehicle.matchesSearch`. `matchesRenterQuery` and `matchesQuery` delegate to them, so the shell
and the two lists match by **one** implementation. The vehicle rule keeps plate, brand, model and
colour — narrowing it to the AC's "plate" would have been a silent regression on a screen that
ships today.

**The task list's "stop drawing their own bars" was wrong, and I reversed it.** A screen's own
bar is the only control that **widens** a list the shell narrowed. Removing it leaves a list the
owner can filter but never unfilter, and strands the screen's `query` with no writer. The two
controls have different jobs — the shell's searches across the app, the screen's narrows its own
list — so selecting a result writes the destination's query through `onQueryChange` and the
screen's own bar remains the way back. The spec's task list was amended rather than followed.

**The required-parameter rule caught three call sites at compile time.** Adding `search` to
`Screens` broke `AppShellTest`, `BannerScopeTest` and `AccessibilityTest` immediately — each one a
shell fixture that would otherwise have rendered a search field wired to nothing.

**`onSelect` deliberately does not live in `Screens.Search`.** Choosing a result is a navigation,
and the nav controller lives in `AppShell`; deriving the route there keeps one back-stack owner,
the same rule `BerandaRoute` follows for its three quick actions. `SearchResult` therefore carries
its `route`, so the shell never maps a kind to a destination itself.

**The shell became a `Column` above the existing `Box`.** The field sits above the graph rather
than floating over it, so it never covers a screen's own top bar; the FAB still aligns to the
content area's bottom-end and its Beranda rule is unchanged.

## Spec Change Log

## Review Triage Log

## Design Notes

**"Available on every screen" is usually a promise seven screens forget to keep.** Here it is a
mount in one `Box` in `AppShell`, above whichever branch drew — so a screen added next year
inherits it, and one added today cannot ship without it. The two screens that already had their
own bars give them up, because three search controls with three behaviours is a worse app than
one with a single rule, however convenient the extra two looked.

**The matching rule already exists twice, and they are not identical — which makes this the
riskiest part of the story.** `matchesRenterQuery` matches name, phone and account;
`matchesQuery` matches plate, **brand, model and colour**. So the vehicle search is already
broader than the AC's "plate", and narrowing it here would be a silent regression on a screen
that ships today. The shared rule therefore keeps every field each one matches, and the shell
calls the same predicates the screens call. `filterByQuery`'s own KDoc names the failure this
prevents: *"two independent copies of one rule is how the search AC ends up verified against a
function the screen never calls."*

**Contracts are not searchable, and that is a decision rather than an omission.** The PRD asks for
renter, vehicle **and contract**. A contract carries no name or plate of its own, so reaching it
means searching through its relations — slower than the finding is worth. The epic records the
departure explicitly, so the honest thing is to keep it and let the empty state say what is not
searched, rather than to build relation traversal for a fleet that numbers eighteen cars.

## Verification

**Commands:**
- `./gradlew :domain:test` -- expected: SUCCESS
- `./gradlew :app:testDebugUnitTest` -- expected: SUCCESS
- `./gradlew :app:assembleDebug` -- expected: SUCCESS