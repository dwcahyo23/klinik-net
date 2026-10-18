---
title: 'Story 4.1 — Daftar tunggakan yang langsung terbaca'
type: 'feature'
created: '2026-10-18'
status: 'done'
followup_review_recommended: false
route: 'dispatch'
review_loop_iteration: 0
baseline_revision: 'a734af3a21289a883d2597c78f8d8d750d592ca9'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-4-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The owner cannot see who owes what. Arrears exist as a correct figure behind a
deposit form and a preview dialog, but there is no screen that answers the only question the
app exists for: *who do I contact today, and for how much*. The PRD calls this the most-opened
screen and nothing in the app is it.

**Approach:** An arrears list built on the domain's existing — and currently uncalled —
`riskOf` and `inArrearsOrder`, over a new cross-contract arrears read. Each row shows the
amount **and** the day count together, coloured by the count through the fixed ladder. In
landscape the list and a per-contract detail sit side by side; in portrait the detail follows.

## Boundaries & Constraints

**Always:**
- **Amount and day count together, never one alone.** Each row reads `Rp 750.000 · 3 hari`.
- **Colour is derived from the day count through `riskOf`**, never chosen. No state exists in
  which the figure says "3 hari" and the badge is green.
- **Ended contracts with a balance stay in the list**, marked finished, until they are settled.
- **Ordering is `inArrearsOrder`** -- largest amount first, then oldest still-open charge day.
- **Every figure comes from one snapshot per contract.** A write landing between two reads
  produces a number that reaches a tenant with no undo behind it.
- **The day count is allocation-derived**, never `amount ÷ rate`. A partly-paid day is open and
  counts as one whole day, so the two disagree.
- **Days already past are counted**, across midnight -- the figure must match online banking.

**Never:**
- **No WhatsApp action, no reminder.** Story 4.2 attaches to a row.
- **No stored arrears column.** A cached figure is one nobody can reconcile.
- **No morning summary, no report, no search.** Stories 4.3, 4.5, 4.6.
- **No re-deriving arrears from deposits in the UI layer.** The repository owns the arithmetic;
  a second derivation is the second thing that can disagree with the first.
- **No colour as the only carrier of meaning** -- the number is always beside it.
- **No new migration.** `settledThroughDate` already exists (Story 3.6), so a settled contract
  already reports zero arrears without this story special-casing it.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| LIST_ONE | Two contracts owing, Rp 750.000/3 days and Rp 250.000/1 day | Both rows, largest first; each shows amount **and** days; badge tone follows `riskOf` | No error expected |
| LIST_ZERO | A contract fully paid or settled | Absent — `inArrearsOrder` filters zero amounts, so a paid tenant never appears as owing | No error expected |
| LIST_ENDED_OWES | An ended contract with a balance | Present, marked finished, ordered among the rest by the same rule | No error expected |
| LIST_RISK_LADDER | Days 0, 1, 2, 3, 6, 7 | LANCAR, RINGAN, RINGAN, PERHATIAN, PERHATIAN, KRITIS — the boundary values pinned | No error expected |
| LIST_TIE | Two contracts owing equal amounts | Older `oldestOpenDay` first | No error expected |
| LIST_MIDNIGHT | A charge day already past, viewed after midnight | Already counted; the figure equals the owner's banking app | No error expected |
| LIST_SETTLED | A contract settled through a date | Days at or before it do not count; a fully settled contract is absent | No error expected |
| LIST_EMPTY | No contract owes anything | Empty state says what to do, not merely that there is nothing | No error expected |

</frozen-after-approval>

## Decisions taken at the gate

- **Scope: the list only.** 4.1 does **not** mount Story 3.4's `requestCancel` or Story 3.5's
  `openEditDeposit`. 4.1's acceptance criteria never mention deposits, and mounting them means a
  second list plus two dialog wirings — a second shippable goal inside a story whose goal is a
  list. Both deferrals stay exactly as Epic 3 left them and are recorded here so the next story
  that builds a deposit history knows they are waiting.
- **`Destination.START` stays on `Kendaraan`.** The PRD calls the arrears list the most-opened
  screen, but changing the launch screen is not in 4.1's criteria and it alters what every
  existing user sees on open. That decision belongs to whoever makes it deliberately.
- **Full spec kept at ~2,400 tokens**, over the 1,600 guideline. The matrix pins every ladder
  boundary and the tie-break, which are the parts an implementer cannot guess; cutting them
  would trade a size warning for a wrong number.

## Code Map

- `domain/.../calculate/Arrears.kt:14-20,74-82,88-91` -- `ArrearsEntry`, `riskOf`,
  `inArrearsOrder`. **The ordering and colour rule already exists, matches the ACs exactly, and
  has no caller in `app/src/main`.** This story is the wiring, as `settledDays` was in Story 3.6.
- `app/.../data/repository/PaymentRepository.kt` -- `currentArrears(contractId, asOf)` is
  **per-contract only**; the cross-contract read is the new work. `previewAllocation`'s
  `chargeDays` already runs through `settledDays`, so a settled contract needs no special case.
- `app/.../data/db/dao/ContractDao.kt:84,161` -- `observeAll()` reads every contract;
  `observeOpen()` would silently drop the ended ones that must stay listed.
- `app/.../ui/shell/Destination.kt:51-74` -- four entries; `AppShell` derives **both** the rail
  and the bottom nav from `entries`, so a fifth is one enum entry plus one `composable()`.
- `app/.../ui/screens/kontrak/KontrakScreen.kt` -- the closest existing screen: rows carrying a
  name, a plate and a derived badge. **No master-detail exists anywhere in `app/src/main`** --
  landscape master-detail is new in this story, for all three screens.
- `app/.../ui/components/Badge.kt` -- `KnBadgeTone`; the number always beside the colour.

## Tasks & Acceptance

**Execution:**
- [ ] `app/.../data/repository/PaymentRepository.kt` -- add a cross-contract arrears read
  returning one complete entry per contract from a **single snapshot** -- joining three
  per-contract reads in the UI is the mixed-state failure.
- [ ] `app/.../ui/screens/tunggakan/TunggakanUiState.kt` -- **new**: rows carrying amount, day
  count, `RiskLevel`, the ended-contract marker and the renter/plate label.
- [ ] `app/.../ui/screens/tunggakan/TunggakanViewModel.kt` -- **new**: combine arrears with
  renters and vehicles, apply `inArrearsOrder`, derive the tone from `riskOf`.
- [ ] `app/.../ui/screens/tunggakan/TunggakanScreen.kt` -- **new**: the list and the landscape
  detail pane.
- [ ] `app/.../ui/shell/Destination.kt`, `NavGraph.kt`, `MainActivity.kt` -- the fifth
  destination; callbacks required, not defaulted.
- [ ] `app/src/test/.../tunggakan/TunggakanArrearsTest.kt` -- **new**: the whole matrix against a
  real database, including every ladder boundary and the tie-break.
- [ ] `app/src/test/.../tunggakan/TunggakanScreenTest.kt` -- **new**: **click-through**, not
  `assertExists` -- this repo has shipped an unwired control twice.

**Acceptance Criteria:**
- Given a contract with arrears, when the list renders, then every row shows amount **and** day
  count, and the badge tone is `riskOf(days)` — never independently chosen
- Given a fully paid or settled contract, when the list renders, then it is absent
- Given an ended contract with a balance, when the list renders, then it is present and marked finished
- Given two contracts owing equal amounts, when the list renders, then the older open day is first
- Given landscape, when two contracts are compared, then list and detail are visible together
- Given the app builds, when tests run, then `:domain:test`, `:app:testDebugUnitTest` and
  `:app:assembleDebug` all pass

## Implementation Notes

**The failure path took three attempts, and the third is the one that is right.** The original
wrote `errorMessage.value` inside `catch`; `errorMessage` was not one of the `combine`'s inputs,
so the write re-rendered nothing and a failed read sat on a skeleton forever. The fix made the
failure a `Source.Failed` value — but the first version still re-threw after emitting, which
terminates the flow, and under `stateIn(WhileSubscribed)` that completes the sharing coroutine
before the emitted value is delivered. Same class of bug one level down: a value that never
arrives. `retryWhen { _, _ -> true }` was rejected as the next answer because a source that
fails immediately fails again immediately, and re-subscribing in a tight loop spins the CPU
while showing a banner. What ships is `catch { emit(Source.Failed) }` — the flow completes
normally — plus an `arrearsRetry` counter that `consumeError` bumps to re-subscribe. A dismissal
is therefore a *retry*: it either recovers and hides the banner, or it fails again and brings the
banner back. There is no state where the banner is gone and no figures behind it.

**KDoc that claimed more than the code did, twice.** `PaymentDao.observeContractIds` claimed the
combine named "the allocation and exception flows" — there was no exception flow, so an owner
marking a service day saw the figure stay stale. `TunggakanViewModel` claimed `asOf` was read
"once per emission" when it was frozen at construction, so an app left open across midnight
showed yesterday's figure. Both were found by two reviewers each, independently.

**`AccessibilityTest` earned its keep on the fifth destination.** Adding a fifth nav item made
the rail's `weight(1f)` column bounded, which compresses children below their own
`minTouchTarget()`: `Tunggakan` measured 38dp. The rail's KDoc had already promised
`overflow-y: auto` — the scroll was simply never written. It is now, and the promise is
load-bearing.

**Rail scroll and the 48dp floor are the same story.** Four destinations fit a landscape tablet;
five do not. Nothing warned of this and no build error mentions it — only a test that measures
what the owner can actually touch.

## Spec Change Log

## Review Triage Log

### 2026-10-18 — Review pass 1
- verdicts: 45 findings — high 3, medium 11, low 14, false 2, maybe-false 15
- findings:
  - `[high]` `[patch]` **service-exception writes never re-emit the arrears list** (edge-case E13; verification-gap G1, independently). `observeArrearsForAllContracts` combines `contractDao.observeAll()`, `paymentDao.observeContractIds()` and `allocationDao.observeContractIds()` — `PaymentRepository.kt:816-826` — but `arrearsEntry` reads excused days through `exceptionDao.forContract`, so `service_exception` is a table the figure depends on and **no query in the combine names it**. The owner marks a day unbilled and the row keeps its old day count and rupiah until they leave and re-enter. `PaymentDao.kt`'s KDoc claims the exception flow is combined; it is not. The existing invalidation test writes the two tables that *are* named, so it passes.
  - `[high]` `[patch]` **`asOf` is frozen at ViewModel construction, not per emission** (edge-case E1; blind-hunter B2). `TunggakanViewModel.kt:104` passes `LocalDate.now(clock)` as an argument while *building* the flow, so an app left open past midnight shows yesterday's figure indefinitely. The KDoc at line 46 claims "read from the Clock once per emission" — the claim is the KDoc's, and it is false. `LIST_MIDNIGHT` unmet.
  - `[high]` `[patch]` **the failure path probably never renders a banner** (edge-case E2, E3; blind-hunter B3). `.catch` sets `errorMessage.value`, emits `Source.Loading`, then rethrows — and `errorMessage` is not an input to the `combine` at line 122, so nothing re-emits to carry it into `uiState`. The one test covering the banner builds the state by hand (`TunggakanScreenTest.kt:262`) rather than making the flow fail, so nothing proves the path is live. A failure therefore reads as a permanent skeleton.
  - `[medium]` `[patch]` **the row's only tap has no visible effect in portrait** (blind-hunter B1; edge-case E12). `onSelect` still fires, the selection is stored, and no pane draws it. This is the control-that-does-nothing defect `Screens.Kontrak`'s KDoc cites shipping twice.
  - `[medium]` `[patch]` **`renterCount` counts rows, not renters** (edge-case E7; blind-hunter B9). The resource's own comment says one renter can hold two contracts with a balance, then the code returns `rows.size` — so the top bar reads "2 penyewa" for one person owing twice.
  - `[medium]` `[patch]` the top bar shows **"0 penyewa" on every load**, because `renterCount` is 0 before the first emission (edge-case E8). The empty-looks-real failure, on the one line that states the screen's own subject.
  - `[medium]` `[patch]` `toRows` silently drops a contract when `byId` misses (`return@mapNotNull null`) (edge-case E10). A contract owing money vanishing is the single failure this screen exists to prevent.
  - `[medium]` `[patch]` **tone is decided by comparing two message strings** (edge-case E5; blind-hunter B4). MainActivity passes the same value on both channels, so severity is inferred from text rather than modelled — and identical text renders Danger on one channel and Success on the other.
  - `[medium]` `[patch]` `onErrorDismiss` defaults to `{}` on `TunggakanScreen` (edge-case E6; verification-gap O1) while its own KDoc and `Screens.Tunggakan`'s forbid it. Production is unaffected, but the screen test passes through the default, so the dismiss wiring is unproven — and `SettleContractSheet` shipped this exact default.
  - `[medium]` `[patch]` one contract's arrears read throwing voids the whole list (`contracts.map { … }` with no per-contract guard) (edge-case E11). One unreadable contract should degrade, not blank the screen the owner opens every morning.
  - `[medium]` `[patch]` **the AC's literal example figure is never reproduced** (intent-alignment I3). `TunggakanScreenTest` asserts `Rp 750.000 · 3 hari` against a **hand-built** fixture; the repository-produced path is only asserted at 14 days. The join — a stored rupiah becoming that string — is proven only in `TunggakanViewModelTest`, and not at the AC's own numbers.
  - `[medium]` `[patch]` **`isEmpty` is true while loading and on error**, and correctness rests entirely on the screen's `when` ordering (edge-case E9; verification-gap O3). Any other caller gets "everyone paid" mid-load.
  - `[low]` `[patch]` two error channels carry one fact: `TunggakanUiState.errorMessage` plus the screen's own parameter (blind-hunter B5; edge-case E15).
  - `[low]` `[patch]` `Screens.Tunggakan.errorMessage` defaults to `null` while its sibling `onErrorDismiss` is required — the parameter deciding *whether* a banner shows is the forgettable one (blind-hunter B7).
  - `[low]` `[patch]` `RStrings` now imports the domain's `RiskLevel` (blind-hunter B15), coupling the string table to an enum it never knew.
  - `[low]` `[patch]` the subtitle resource is a bare `%1$s` carrying only the plate, with a comment claiming it distinguishes two rentals of one renter (blind-hunter B13).
  - `[low]` `[patch]` preview composables pass `onSelect = {}` and no error channel, so the preview cannot exercise the banner path (edge-case E16).
  - `[low]` `[patch]` trailing newlines missing on `TunggakanViewModel.kt`, `TunggakanScreen.kt`, `PaymentDao.kt`, `TunggakanUiState.kt`, `epic-4-context.md` (blind-hunter B14; edge-case E17; verification-gap O2).
  - `[low]` `[defer]` the aggregate recomputes every contract on every emission — O(contracts × deposits) per save, with no benchmark against NFR-1's 50 ms (blind-hunter B11; edge-case E18). Correctness is unquestioned here; the budget is unmeasured.
  - `[low]` `[defer]` `harreatestOpenDay` is landscape-only — in portrait `selectedRow` is `null` by construction and the pane never draws, so the figure the Notes calls the thing the owner is looking for is unreachable on one orientation (intent-alignment I1, I5).
  - `[false]` `[reject]` the tie-break test proves nothing because the ids happen to agree (intent-alignment, credits the diff positively) — already the strongest case in the suite; no finding to record.
  - `[maybe-false]` `[reject]` declining to mount the two Epic 3 deferrals (intent-alignment I4, R-d). The epic context says they "land here"; 4.1's ACs never mention deposits, and mounting them is a second shippable goal. Recorded as a gate decision in the spec, so this is a scope judgment already surfaced rather than a hidden divergence.
  - `[maybe-false]` `[reject]` the AC's `Rp 750.000 · 3 hari` example is internally inconsistent — 3 is days *closed*, the row shows days *open* (intent-alignment I6). The epic context's own rule ("derived from stored allocations", never `amount ÷ rate`) settles it, and the tests document the choice. Recorded so the choice is visible, not because the row is wrong.
  - `[maybe-false]` `[reject]` 12 further low findings about KDoc phrasing and comment accuracy across both new screens and the shell fixtures, all confirmed cosmetic by reading the cited lines.

## Design Notes

**Three of the four rules already exist and are uncalled.** `riskOf`, `inArrearsOrder` and
`ArrearsEntry.oldestOpenDay` were built in Epic 1 with 14 passing tests, and `app/src/main` calls
none of them -- the same shape as `settledDays` before Story 3.6. The genuinely new work is the
**aggregate** read: every arrears method in `PaymentRepository` takes one `contractId`.

**Master-detail is new to this app.** No screen implements it, so `Kendaraan` and `Penyewa` lack
it too despite the design doc listing all three. It is built here for the first time.

## Verification

**Commands:**
- `./gradlew :domain:test` -- expected: SUCCESS
- `./gradlew :app:testDebugUnitTest` -- expected: SUCCESS
- `./gradlew :app:assembleDebug` -- expected: SUCCESS