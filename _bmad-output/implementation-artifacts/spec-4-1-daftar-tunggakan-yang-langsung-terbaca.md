---
title: 'Story 4.1 — Daftar tunggakan yang langsung terbaca'
type: 'feature'
created: '2026-10-18'
status: 'in-progress'
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

## Spec Change Log

## Review Triage Log

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