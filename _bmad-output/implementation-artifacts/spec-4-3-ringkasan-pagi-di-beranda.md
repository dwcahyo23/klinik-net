---
title: 'Story 4.3 — Ringkasan pagi di Beranda'
type: 'feature'
created: '2026-10-18'
status: 'in-progress'
baseline_revision: '3dfd46c8b8e55416663b4d985b9593fac5b7898a'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-4-context.md'
warnings:
  - oversized
deferred: []
---

<intent-contract>

## Intent

**Problem:** The app opens on the fleet list. The owner's first question each morning is not
"what cars do I have" but "what do I have to do today" — and answering it means opening four
screens and adding up what they say. Nothing on screen says it at all.

**Approach:** A Beranda screen with four equally-sized KPIs computed from the **existing** reads —
today's deposits, active arrears, fleet split by the Story 2.3 status rules, and collection rate —
plus a four-tile quick-action grid that becomes the only door to those actions from here.

## Boundaries & Constraints

**Always:**
- **All four KPIs are the same size.** No tile is enlarged, no tile leads. This is an owner's
  decision: if nothing is first, nothing is big.
- **All four fit without scrolling** at the app's primary orientation.
- **Today's income is the sum of confirmed deposits dated today**, in whole rupiah.
- **Active arrears covers open contracts *and* ended contracts that still owe** — the same
  population Story 4.1's list draws.
- **Fleet counts come from `VehicleStatus.of`**, the Story 2.3 derivation, never from a count
  column. The manual service marker wins over an open contract, and the arrears engine keeps
  billing either way.
- **Collection rate is period income ÷ (period income + active arrears)**, a whole percentage,
  and **100% when the denominator is zero** — an owner with nothing owing is fully collected, not
  unmeasured.
- **The KPIs change on their own after a write.** No refresh button exists, and none may be added.
- **Quick-action targets all exist.** No tile opens a screen this app does not have.
- **The FAB is hidden on Beranda.** UX-DR17: *"speed dial TIDAK muncul di Beranda"* — two doors
  to one action is how one of them goes stale. `DepositFab`'s KDoc already promises this.

**Never:**
- **No Setoran list, no arrears list, no report preview on Beranda.** It is a summary; the lists
  live where they live, one tap away.
- **No second calculation.** Beranda reads the same `observeArrearsForAllContracts` and the same
  deposit flow the other screens read. A dashboard with its own arithmetic is a second answer to
  the same question.
- **No stored KPI column, no cached total, no refresh gesture.** A figure nobody can reconcile is
  worse than a slow one.
- **No date picker on Beranda.** "Today" is today; the period-scoped figures are Story 4.5's.
- **No hierarchy between the KPIs** added later without the owner's say-so — the note says a
  change of size is not a change of architecture, but the default stays flat.
- **No milestone, no celebration, no confetti.** Nothing here congratulates the owner.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| HOME_TODAY | Deposits dated today and older | Today shows only today's confirmed deposits; older deposits are excluded | No error expected |
| HOME_ZERO_RATE | No income and no arrears | Collection rate reads `100%`, not `0%` and not a dash | No error expected |
| HOME_RATE | Income Rp 750.000, arrears Rp 250.000 | Rate reads `75%` — whole percent, no decimal | No error expected |
| HOME_ENDED_OWES | An ended contract with a balance | Its arrears count in the total, exactly as Story 4.1's list shows | No error expected |
| HOME_SERVIS_WINS | A vehicle marked Servis *and* under an open contract | Counted once, as Servis — the Story 2.3 rule, not a re-derivation | No error expected |
| HOME_UPDATES | A deposit saved while Beranda is open | KPIs move with no reload and no navigation | No error expected |
| HOME_NO_FAB | Beranda visible | The deposit FAB is absent; the grid's tile is the door | No error expected |
| HOME_LOADING | Room has not emitted | A skeleton **per KPI tile**, not an empty grid — four zeros read as a real morning | No error expected |

</intent-contract>

## Decisions taken at the gate

- **`Destination.START` moves to `Beranda`.** This is the story that exists to answer "what do I
  do today", so opening anywhere else makes it a screen the owner must go *to*. The prototype's
  own rail is ordered by frequency with the dashboard first, and the design doc names it the app's
  opening map. This is the story where the change is deliberate rather than a side effect.
- **The four quick-action tiles all target screens that exist.** Catat Setoran opens the deposit
  sheet, Tambah Mobil the vehicle form, Kelola Penyewa the renters screen, Kirim Pengingat the
  arrears screen. No tile opens a screen this app does not have — Story 4.3 does not get to invent
  a destination to satisfy a grid.
- **The rail is not restructured here.** UX-DR17's seven-item order (Beranda · Setoran ·
  Kendaraan · Penyewa · Tunggakan · Laporan · Pengaturan) needs Setoran, Tunggakan and Laporan as
  destinations, and they arrive in 4.2, 4.1 and 4.5. This story adds Beranda and leaves the
  order alone; reordering the rail is a mechanical change once the entries exist, and doing it
  three times would churn the KDoc that explains the ordering rule.

## Code Map

- `domain/.../calculate/Arrears.kt:88` -- `inArrearsOrder` already filters zero amounts, so
  "active arrears" is the sum over exactly the population Story 4.1 shows. **No new query.**
- `app/.../data/repository/PaymentRepository.kt` -- `observeArrearsForAllContracts(clock)` is
  the flow Beranda sums. Reused unchanged; a dashboard read of its own is the second answer.
- `app/.../data/repository/PaymentRepository.kt` -- `observePayments(contractId)` is **per
  contract**, so today's income needs a cross-contract read of `payment`. New, and the same
  snapshot discipline as 4.1: the invalidation triggers must include every table the figure
  depends on.
- `app/.../ui/screens/kendaraan/KendaraanUiState.kt:12-38` -- `VehicleStatus.of(isServis,
  hasOpenContract)`, the Story 2.3 derivation, with `Servis` winning. Beranda counts through it.
- `app/.../ui/components/Card.kt:146` -- `KnKpiTile`, built by Story 2.1 for exactly this. **It
  has no size parameter**, which matters: its `accent: Boolean` is the hierarchy control the AC
  forbids — an accented tile draws a 116dp edge and is visibly taller than its neighbours. So
  "all four the same size" is `accent = false` four times, and the test measures the four heights
  rather than trusting the call site to be uniform.
- `app/.../ui/shell/Destination.kt:51` -- **four** entries today. UX-DR17 specifies a rail of
  **seven**, ordered by frequency: Beranda · Setoran · Kendaraan · Penyewa · Tunggakan · Laporan ·
  Pengaturan. This story adds Beranda; Setoran, Tunggakan and Laporan arrive in 4.2/4.1/4.5.
- `app/.../ui/shell/DepositFab.kt:32-37` -- the KDoc **already promises** the FAB hides on
  Beranda and records that the condition is "then one line". This story adds that line.
- `app/.../ui/shell/AppShell.kt:145` -- where the FAB's `visible` is computed.

**Gotchas carried over:**
- `Destination.START` is `Kendaraan`; this story is the point where it becomes a decision.
- Room's first emission is an empty list, so "no rows" cannot mean "still loading" — the same
  reason `KendaraanUiState.isLoading` is its own field.
- Every new `Screens.*` callback is **required, not defaulted**.
- `KlinikNetTheme` is `staticCompositionLocalOf`; one `setContent` per Compose test.

## Tasks & Acceptance

**Execution:**
- [ ] `app/.../data/repository/PaymentRepository.kt` -- `observeTodaysIncome(clock): Flow<Long>`
  over every confirmed deposit dated today, summing in whole rupiah, with the invalidation
  triggers its figure depends on. Cancelled deposits are excluded; the status filter belongs here
  beside the allocator's, not in the ViewModel.
- [ ] `app/.../ui/screens/beranda/BerandaUiState.kt` -- **new**: four KPI values, each
  pre-formatted, plus the fleet split and `isLoading`.
- [ ] `app/.../ui/screens/beranda/BerandaViewModel.kt` -- **new**: combine the arrears flow, the
  income flow and the vehicle flow; sum and derive. No arithmetic that another screen also does.
- [ ] `app/.../ui/screens/beranda/BerandaScreen.kt` -- **new**: four `KnKpiTile` at one size and
  the four-tile quick-action grid, both above the fold.
- [ ] `app/.../ui/screens/beranda/QuickActionGrid.kt` -- **new**: four tiles whose destinations
  all exist. Every action is one the app already has.
- [ ] `app/.../ui/shell/Destination.kt`, `NavGraph.kt`, `MainActivity.kt`, `AppShell.kt` --
  `Destination.Beranda`, its route, its wiring, and the FAB's visibility rule.
- [ ] `app/src/test/.../beranda/BerandaKpiTest.kt` -- **new**: the whole matrix against a real
  database, including the zero-denominator rate and the Servis-wins case.
- [ ] `app/src/test/.../beranda/BerandaScreenTest.kt` -- **new**: four equal tiles (height measured,
  not assumed), no scrolling past the fold, the FAB absent, and a **click-through** on a grid
  tile.

**Acceptance Criteria:**
- Given the app opens on Beranda, when it renders, then four KPIs are visible without scrolling
  and **none is larger than another** -- the tile heights are measured, not assumed, because
  `KnKpiTile`'s `accent` flag is the hierarchy control the AC forbids
- Given income and arrears, when the rate is computed, then it is a whole percentage, and 100%
  when both are zero
- Given a Servis vehicle under an open contract, when the fleet is counted, then it is counted
  once as Servis
- Given an ended contract that still owes, when active arrears are totalled, then it is included
- Given a deposit is saved while Beranda is open, when the write lands, then the KPIs change with
  no reload and no navigation
- Given Beranda is visible, when it draws, then the deposit FAB is absent
- Given the app builds, when tests run, then `:domain:test`, `:app:testDebugUnitTest` and
  `:app:assembleDebug` all pass

## Spec Change Log

## Review Triage Log

## Design Notes

**Every rule this screen needs already exists; none of it is a dashboard's own arithmetic.**
Active arrears is the sum over `inArrearsOrder`'s output, which already filters zero amounts —
so the population is Story 4.1's by construction rather than by a second filter. The fleet split
goes through `VehicleStatus.of`, whose `Servis`-wins rule is already argued in `KendaraanUiState`.
The collection rate is the only figure that is genuinely new, and it is one division over two
figures that both already exist.

That is what keeps this story from becoming a fifth thing that can disagree. A dashboard that
summarises by querying separately is the second-derivation defect this repo keeps catching, and
on this screen it would be the worst instance yet — the owner reads four numbers at once and has
no way to see which one disagrees with the list they just opened.

**The FAB's promise predates its fulfilment.** `DepositFab`'s KDoc has said since Story 3.2 that
it would disappear on Beranda and that the condition would be "one line". That is the shape a good
constraint takes here: the rule is written down years before there is anything to hide, and the
reason for it — *two doors to one action, so one goes stale* — is recorded where the button is,
not where the grid is.

**"No refresh button" is a design statement, not a missing feature.** The owner's rule is that
every figure recomputes before the screen finishes drawing, so a refresh control would be a second
mechanism — and a second mechanism is always something to disagree with the first.

## Verification

**Commands:**
- `./gradlew :domain:test` -- expected: SUCCESS
- `./gradlew :app:testDebugUnitTest` -- expected: SUCCESS
- `./gradlew :app:assembleDebug` -- expected: SUCCESS