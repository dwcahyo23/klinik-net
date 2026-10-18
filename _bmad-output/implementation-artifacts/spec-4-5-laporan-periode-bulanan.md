---
title: 'Story 4.5 — Laporan periode bulanan'
type: 'feature'
created: '2026-10-18'
status: 'done'
baseline_revision: '780e2c86b348d1dd61945aed0f7dddcf8717f5b7'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-4-context.md'
warnings:
  - oversized
deferred: []
---

<intent-contract>

## Intent

**Problem:** The owner decides twice a month whether to chase debtors or buy another car, and
today that decision is made by hand: open the deposit history, add up a month, compare it with
what is still owed. The arithmetic is not hard; the discipline of doing it the same way every
month is what fails, and two months computed two ways disagree without anyone noticing.

**Approach:** A period report built on the **same** repository functions Beranda reads, with the
period's date range as the only parameter — one function, two call sites, differing only in
`asOf`. The period is a `CutoffPeriod`, already the app's answer to "which days am I charging
for", and the seven-day trend is a line drawn on `Canvas`.

## Boundaries & Constraints

**Always:**
- **Every figure comes from the function Beranda uses**, with a different date range. There is no
  report-only arithmetic, and the test proves it by computing the same period twice — once
  through the report and once through Beranda's own read — and comparing.
- **Income is attributed by the date the deposit was recorded**, never by a booking date. A
  deposit dated 20 March and viewed from April's period moves with March.
- **A future period contributes no arrears.** Days after today are not charged, so a report for a
  period still ahead of the owner shows the days that exist and nothing more.
- **The trend is a line on `Canvas`.** No chart library, no new dependency; roughly sixty lines.
- **The trend's colour comes from a Material 3 token**, so dark theme follows without a second
  value.
- **The risk breakdown is four coloured numbers**, not a donut. Four figures read faster than a
  chart and need nothing installed.
- **The period is a `CutoffPeriod`**, the app's existing billing boundary, so the report's range
  and the arrears engine's range cannot disagree.

**Never:**
- **No second arrears function.** A report that summarises by querying separately is the
  second-derivation defect this repo keeps catching, and here it would be the worst instance: the
  owner compares this figure against Beranda's and neither knows which is right.
- **No donut, pie, or any circular chart.** Decided and argued in the epic's own notes.
- **No PDF, no export, no share.** Story 5.1 owns every exit from the app.
- **No comparison to last month.** Tempting and out of scope; the AC asks for one period.
- **No stored report row, no cached total, no refresh control.**
- **No touch target or colour that carries a figure alone.** The risk numbers are numbers *and*
  colours, per the same rule every other screen follows.
- **No hardcoded rupiah formatter here.** `formatRupiah` is called at the call site, so a figure
  is spelled one way in the app.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| REPORT_PERIOD_TOTAL | Deposits dated 26 Sep – 24 Oct, period 25 Sep – 24 Oct | Income counts only those; the ones dated 25 Sep and 24 Okt are inside, 25 Okt is not | No error expected |
| REPORT_SAME_FUNCTION | The same period computed through the report and through Beranda's read | The arrears figures are equal, asserted rather than assumed | No error expected |
| REPORT_DEPOSIT_MOVES | A deposit dated 20 Mar, viewed from the March period and from April's | It counts in March and not in April — the recorded date decides | No error expected |
| REPORT_FUTURE | The next period, which has not started | No days after today contribute arrears; the period is offered but not invented | No error expected |
| REPORT_NO_DEPOSITS | A period with nothing in it | Zero income, and the risk breakdown shows only the levels that have contracts | No error expected |
| REPORT_TREND_7 | Deposits across the last seven days | A line is drawn, one point per day, days with nothing reading as zero | No error expected |
| REPORT_DARK | Dark theme active | The trend line uses the theme's token, with no second colour value | No error expected |
| REPORT_RISK_SPLIT | Contracts at 0, 2, 5 and 9 days open | Four numbers: Lancar, Ringan, Perhatian, Kritis, counted by `riskOf` | No error expected |
| REPORT_ZERO_RATE | Income and arrears both zero | Collection rate reads `100%`, the same rule Beranda uses | No error expected |

</intent-contract>

## Decisions taken at the gate

- **The report is a fifth destination, not a tab inside Beranda.** Beranda answers "what today",
  the report answers "what this month", and the design doc's screen map gives Laporan a rail slot
  of its own. Setoran, Tunggakan and Laporan are the three the owner uses daily, and putting the
  report anywhere else would hide it behind a scroll.
- **The period picker offers the current period and the ones before it.** A future period is
  allowed and shows nothing, because the AC says a future period must not add arrears — that is
  a statement about behaviour, not a prohibition on looking.

## Code Map

- `app/.../data/repository/PaymentRepository.kt` -- `observeTodaysIncome(clock)` and
  `observeArrearsForAllContracts(clock)` are **Beranda's own reads**. The report calls both with a
  different `asOf`, which is the whole "same function, different parameter" clause made
  mechanical. `observeIncomeByDate()` already returns a total grouped by date, so a period's income
  is a filter over that grouping rather than a new query.
- `domain/.../calculate/CutoffPeriod.kt:18-48,77-85` -- `CutoffPeriod(cutoffDay, today)` takes
  today **as a parameter**, so a past period is the same call with a different date. `label` and
  `days()` already exist.
- `domain/.../calculate/Arrears.kt:74-82` -- `riskOf`, unchanged, for the four-number breakdown.
  The report does not decide a level of its own.
- `app/.../ui/screens/beranda/BerandaViewModel.kt` -- the pattern for combining the two flows into
  four figures; the report's ViewModel is the same shape with a period.
- `app/.../ui/screens/tunggakan/TunggakanUiState.kt` -- `ArrearsRow` and `risk`, so the breakdown
  counts rows rather than re-deriving a level.
- `app/.../ui/components/Card.kt` -- `KnKpiTile` and `KnBadgeTone`, for the four risk numbers.
  `accent = false` on all of them, for the same reason Beranda's are flat.
- `app/.../ui/shell/Destination.kt` -- **five** entries after Story 4.3. This adds Laporan.

**Gotchas carried over:**
- Room's first emission is an empty list, so "no rows" cannot mean "still loading"; `isLoading`
  is its own field.
- Every new `Screens.*` callback is **required, not defaulted**.
- One `setContent` per Compose test; `performSemanticsAction(SemanticsActions.OnClick)` rather
  than `performClick`, because a modal renders in its own window.

## Tasks & Acceptance

**Execution:**
- [ ] `app/.../data/repository/PaymentRepository.kt` -- `incomeBetween(start, end, asOf)` over the
  **existing** `observeIncomeByDate()` grouping, and **give `observeArrearsForAllContracts` an
  `asOf` parameter** so a past period is reachable. That is a defaulted parameter read *inside*
  the `combine`'s lambda — where `val asOf = LocalDate.now(clock)` currently sits — not a new
  function. Two overloads of the same body would be the second derivation this story forbids;
  one function taking the date is the AC's clause made mechanical.
- [ ] `app/.../ui/screens/laporan/LaporanUiState.kt` -- **new**: the period, the figures, the
  seven trend points, and the four risk counts.
- [ ] `app/.../ui/screens/laporan/LaporanViewModel.kt` -- **new**: combine the two repository
  reads over the selected period, count the risk levels through `riskOf`, build the trend.
- [ ] `app/.../ui/screens/laporan/TrendLine.kt` -- **new**: the `Canvas` line, ~60 lines, colour
  from the theme.
- [ ] `app/.../ui/screens/laporan/LaporanScreen.kt` -- **new**: the period picker, the tiles, the
  trend and the four risk numbers.
- [ ] `app/.../ui/shell/Destination.kt`, `NavGraph.kt`, `MainActivity.kt` -- `Destination.Laporan`
  and its wiring, required callbacks.
- [ ] `app/src/test/.../laporan/LaporanPeriodTest.kt` -- **new**: the whole matrix against a real
  database, including the same-function comparison against Beranda's read.
- [ ] `app/src/test/.../laporan/LaporanScreenTest.kt` -- **new**: the four risk numbers, four
  equal tiles, and a **click-through** on the period picker.

**Acceptance Criteria:**
- Given a period, when the report renders, then income, closing arrears and settled-renter count
  all come from the repository functions Beranda reads, with the period as the only parameter
- Given the same period computed twice, when both figures are compared, then they are equal
- Given a deposit dated 20 Mar, when March's and April's periods are viewed, then it counts in
  March only
- Given a future period, when it is viewed, then no day after today contributes arrears
- Given the trend, when seven days are drawn, then the line is rendered on `Canvas` and its colour
  comes from the theme
- Given the risk breakdown, when it renders, then four coloured numbers appear and no chart
- Given the app builds, when tests run, then `:domain:test`, `:app:testDebugUnitTest` and
  `:app:assembleDebug` all pass

## Implementation Notes

**The "same function" clause is proved by one body and two call shapes.**
`observeArrearsForAllContracts(clock, asOf: () -> LocalDate = { LocalDate.now(clock) })` — a
defaulted parameter read inside the `combine`'s lambda, so the default path stays byte-identical
to what Beranda runs. Two overloads with similar bodies would look right today and diverge the
day one is edited; this cannot.

**And the test proves both halves, which the first version got backwards.** It compared a report
period's *end* against *today* and expected equality: 24 October charges a full month and 17
October charges three weeks, so they differ by seven days. It now passes the **same** date through
two call shapes and expects identical results, then moves the date and expects the figure to shift.
The first half is the claim; the second proves the parameter is live rather than decorative.

**Two more tests were wrong rather than the code**, and both were corrected the same way — by
reading what the criterion says instead of what the first draft assumed. `REPORT_FUTURE` passed a
*future period end* as the time point and expected zero, which is the opposite: the criterion is
about days after **today**, so a contract starting after `asOf` is what owes nothing.

**Seven destinations on a 360dp phone leave each tab 44dp.** `weight(1f)` constrains a child in a
`Row` whether `minTouchTarget` comes before or after it, because the minimum is measured *inside*
an already-fixed share — ordering the modifiers changes nothing, which was tried first. The row
therefore scrolls, the same trade the landscape rail made in Story 4.1: a destination hard to
reach beats one that cannot be hit.

**Two hierarchies the criteria forbid, caught by measuring rather than assuming.** The trend
tile had an icon and the other three did not, and the icon made it taller — which is exactly the
"no tile is enlarged" rule, caught only because the test compares the four measured heights.
And `riskTone(count)` mapped a *contract count* onto the day ladder, so three contracts would
render as "Perhatian" when that is the Kritik level. Both now go through the arrears list's own
`toneFor`, so the report and the list cannot colour a level differently.

**The rail's UX-DR17 order is complete and still unsorted.** All seven destinations now exist,
so the frequency ordering could be applied — but Setoran is still not one of them (it is a
quick action, not a destination), and reordering here would touch the KDoc that explains the
ordering rule for no remaining story to benefit. It is Epic 4's closing piece, not this one's.

## Spec Change Log

## Review Triage Log

## Design Notes

**"Same function, different parameter" is testable, and that is what makes it worth writing
down.** The AC's strongest clause — *"bukan dua fungsi terpisah yang bisa menyimpang"* — is
usually an architectural note nobody checks. Here it is checkable: compute the same period twice,
once through the report's path and once through Beranda's own read, and compare. A report with its
own arithmetic fails that assertion the day it is written; a report that reuses the repository
passes it forever. That is worth more than the AC's phrasing, because it turns a rule into a test.

The implementation must therefore be a **defaulted parameter**, not a second body. The arrears
read currently reads `LocalDate.now(clock)` inside its `combine` lambda; giving it an `asOf: … =
LocalDate.now(clock)` leaves the default path byte-identical to what Beranda already runs, so
"same function" is true in the strict sense — one body, two arguments — rather than true only in
the sense that two callers happen to agree today.

**The date is what makes a period, not the month.** The app's billing boundary is
`CutoffPeriod`, and `containing(cutoffDay, today)` already takes today as an argument — so last
month's arrears is the same call with a different date, not a second function that knows how to
walk backwards. The report inherits the boundary rule the allocation engine already obeys, which
is why the two cannot disagree about which days a month contains.

**Four numbers beat a donut, and the reason is in the notes rather than in taste.** A donut needs
a chart library to be legible, and this repo has declined every dependency for a screen it could
have drawn itself. Four numbers with four colours are read in one pass, need nothing installed,
and cannot go stale in a dependency. The line chart gets the same treatment for the same reason:
`Canvas` is in the platform.

## Verification

**Commands:**
- `./gradlew :domain:test` -- expected: SUCCESS
- `./gradlew :app:testDebugUnitTest` -- expected: SUCCESS
- `./gradlew :app:assembleDebug` -- expected: SUCCESS