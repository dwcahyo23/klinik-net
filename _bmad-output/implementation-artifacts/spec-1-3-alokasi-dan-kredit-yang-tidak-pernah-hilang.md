---
title: 'Story 1.3: Alokasi dan Kredit yang tidak pernah hilang'
type: 'feature'
created: '2026-10-13'
status: 'done'
baseline_revision: 7a6399b6798b1d066f35d45da0373627d5819040
review_loop_iteration: 0
followup_review_recommended: false
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md'
warnings:
  - oversized
deferred:
  - summary: >-
      SQLite unique indexes treat NULLs as distinct, so `ALLOCATION UNIQUE(paymentId,
      chargeDate)` will not stop two `chargeDate = null` rows existing for one Setoran —
      exactly the surplus rows this story is about.
    evidence: |-
      Correct at the schema level, in a table this story does not create. The engine's own
      `Σ allocation == amount` check guarantees the rows are right in memory, but the
      database-level safety net `architecture-spine.md:362` relies on has a hole for null
      charge dates. Surfaced by two review layers independently. Epic 3 owns the entity and
      the DAO; a partial unique index (`WHERE chargeDate IS NOT NULL`) plus a rule for the
      remainder case would close it.
    location: >-
      _bmad-output/planning-artifacts/architecture/architecture-dev.klinik.net-2026-10-13/architecture-spine.md:362
    severity: medium
---

<intent-contract>

## Intent

**Problem:** Arrears today comes from summing payments inside one cutoff window, so money that lands on a non-charge day, arrives before the period opens, or exceeds the period's liability is silently dropped — `verify-fr10.md` case 4 measured Rp 13.000.000 vanishing at the month rollover. AD-3 requires the opposite: each payment stores *which* charge days it closed, so credit is a consequence of stored facts rather than a separately tracked number.

**Approach:** One pure function in `domain/calculate/` that replays a contract's payments in date order across its charge days, oldest day first, emitting one allocation row per (payment, charge day) plus a nullable-`chargeDate` row for whatever is left over.

## Boundaries & Constraints

**Always:**
- **Σ allocation == payment.amount, for every payment, no exception** (AR-7). A violation is a bug, not a valid state.
- Allocation is chronological, oldest open charge day first — `verify-fr10.md` case 1 pins Rp 500.000 on 20 Mar to 25 + 26 Feb.
- Every leftover gets exactly one row with `chargeDate = null`. A payment that fits exactly produces no such row.
- No day is allocated twice per payment; no two rows share `(paymentId, chargeDate)`.
- A payment dated after `asOf` contributes nothing yet, and is picked up once the period rolls (FR-10 case 7).
- Money is `Long` rupiah, never `Double`/`Float` (AR-2).
- Output is deterministic: payments sorted by `date` ascending, ties broken by `id`; rows per payment in ascending `chargeDate` with the `null` remainder last.
- Ids are `String` — AD-1 permits only `kotlin.*`, `java.time.*`, `kotlinx.coroutines.*` in `domain/`, so `java.util.UUID` is not importable.

**Never:**
- Never compute arrears, missed days, or risk level — that is Story 1.4, and it reads these rows.
- Never read payments by *period*. Payments are gated by `asOf` only; the PRD's "payments inside the current period" wording is the bug `verify-fr10.md` case 4 measured.
- Never model `settledThroughDate` — FR-9b writes no allocation rows and belongs to Story 1.5.
- Never touch the database, Room, or `@Transaction`; Epic 3 owns when this runs and what it persists.
- Never introduce a `Contract` type — this story needs a rate and a schedule, and `ChargeSchedule` already carries the schedule.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Two days at once | 28 days @ 250k, Rp 500.000 on 20 Mar | rows 25 Feb + 26 Feb, Σ == 500.000 | — |
| Whole period covered | 28 days @ 250k, Rp 7.000.000 | 28 full rows, no null row | — |
| Overpay into credit | 28 days @ 250k, Rp 20.000.000 | 28 full rows + 1 null row of 13.000.000 = 29 rows | — |
| Credit next period | same payment, replayed over 31 days (25 Mar → 24 Apr) | 31 full rows, 12.250.000 null remainder, arrears 0 | — |
| Partial payment | rate 250k, Rp 100.000 | one row of 100.000 against the oldest day | — |
| Rest-day payment | 6-day contract, Rp 250.000 on Sun 1 Mar | still credited; closes the oldest open charge day | — |
| Off-day = contract's first day | contract starts Mon 2 Mar, payment Sun 1 Mar | closes 2 Mar — the nearest charge day | — |
| Payment before period | 25 Feb payment, period opens 25 Feb | credited; no "future payment" drop | — |
| Future payment | Rp 250.000 dated 30 Mar, asOf 24 Mar | ignored entirely; no rows | — |
| No payments | 28 days, empty payment list | empty allocation list | — |
| Non-positive amount | amount 0 or negative | — | `InvalidAmountException` |

</intent-contract>

## Code Map

- `domain/src/main/kotlin/net/klinik/rental/domain/calculate/CutoffPeriod.kt` -- `CutoffPeriod(start, end)`, `containing(cutoffDay, today)`, `MIN/MAX_CUTOFF_DAY`. Supplies the period the charge days come from. **Read-only.**
- `domain/src/main/kotlin/net/klinik/rental/domain/calculate/ChargeDays.kt` -- `ChargeSchedule(contractStart, restDay, serviceExceptions)` and `chargeDaysIn(period, schedule, asOf)`. **The allocator's input**, already ascending and gap-free. Its KDoc lines 34-37 assert the allocator uses a "nearest charge day" rule (`filter { it <= paymentDate }.maxOrNull()`); that is wrong — see the Spec Change Log.
- `domain/src/main/kotlin/net/klinik/rental/domain/validation/InvalidCutoffDayException.kt` -- the exception convention to follow: one class, message built from the constants that enforce the rule. **Read-only.**
- `domain/src/test/kotlin/net/klinik/rental/domain/calculate/CutoffPeriodTest.kt`, `ChargeDaysTest.kt` -- the conventions to imitate: JUnit 4, one rule per test, backtick Indonesian test names, class KDoc explaining why the matrix is shaped that way, sweeps with assertion messages.
- `domain/src/main/kotlin/net/klinik/rental/domain/package-info.kt` -- stale; still says the first real domain type arrives in Story 1.2.
- `domain/build.gradle.kts` -- pure-JVM, `jvmToolchain(17)`, JUnit 4 only. **Add no dependency.**
- `_bmad-output/planning-artifacts/prds/prd-dev.klinik.net-2026-10-13/verify-fr10.md` -- the numerical authority for cases 1-7; case 4 rejects the `creditBalance` column and mandates stored allocations.
- `_bmad-output/planning-artifacts/architecture/architecture-dev.klinik.net-2026-10-13/architecture-spine.md:94-107,342-366` -- AD-3 and the `ALLOCATION` schema: nullable `chargeDate`, amount always > 0, `UK(paymentId, chargeDate)`.

**To create:** `.../model/Payment.kt`, `.../model/Allocation.kt`, `.../calculate/AllocationEngine.kt`, `.../validation/InvalidAmountException.kt`, `.../calculate/AllocationEngineTest.kt`.

## Tasks & Acceptance

**Execution:**
- `domain/src/main/kotlin/net/klinik/rental/domain/validation/InvalidAmountException.kt` -- follows the `InvalidCutoffDayException` shape; created first because [Payment] depends on it
- `domain/src/main/kotlin/net/klinik/rental/domain/model/Payment.kt` -- `Payment(id: String, date: LocalDate, amount: Long)` with `require(amount > 0)`; the amount check lives on construction so an invalid Setoran cannot enter the queue at all
- `domain/src/main/kotlin/net/klinik/rental/domain/model/Allocation.kt` -- `Allocation(paymentId: String, chargeDate: LocalDate?, amount: Long)`; one stored row, matching `ALLOCATION` minus the `contractId` the caller already has from the payment
- `domain/src/main/kotlin/net/klinik/rental/domain/calculate/AllocationEngine.kt` -- `allocatePayments(payments, chargeDays, dailyRate, asOf): List<Allocation>`; sorts payments by `(date, id)`, drops any dated after `asOf`, walks charge days once filling each up to `dailyRate` until a payment's money runs out, and emits the remainder as one `chargeDate = null` row. Include a `check` that each payment's rows sum to its amount -- the invariant is cheap to assert here and expensive to discover downstream.
- `domain/src/main/kotlin/net/klinik/rental/domain/calculate/ChargeDays.kt` -- correct the KDoc's "nearest charge day" claim; **spec change log explains why**
- `domain/src/main/kotlin/net/klinik/rental/domain/package-info.kt` -- drop the now-false "first real domain type arrives in Story 1.2"
- `domain/src/test/kotlin/net/klinik/rental/domain/calculate/AllocationEngineTest.kt` -- one test per matrix row, plus the two property sweeps below

**Acceptance Criteria:**
- Given a contract of 28 charge days at Rp 250.000 and a Rp 500.000 payment dated 20 Mar, when allocation runs, then rows close 25 and 26 Feb, there are 2 of them, and their sum is Rp 500.000
- Given that same contract and a Rp 20.000.000 payment, when allocation runs, then 28 rows are fully allocated, one extra row carries `chargeDate = null` and Rp 13.000.000, there are 29 rows in total, and period liability minus allocated is Rp 0
- Given the Rp 20.000.000 payment replayed over the next period's 31 charge days (25 Mar → 24 Apr), when allocation runs, then every one of those 31 days is allocated and none is left open for billing again -- the credit is genuinely spent, which is the whole reason the null row exists
- **Prefix stability:** given allocation over this period's 28 days and again over those same 28 days plus the next period's 31, when the two results are compared, then every row whose `chargeDate` falls in the first period is identical in both. This is what makes storing the credit safe: the period rolls, and the earlier facts are extended rather than rewritten.
- Given a 6-day contract with Sunday off and a Rp 250.000 payment dated Sunday 2026-03-01, when allocation runs, then Sunday is not charged, the payment is still allocated, and the period's allocated total rises by Rp 250.000 -- arrears falls by one day
- Given that payment on a contract whose first charge day is Monday 2026-03-02, when allocation runs, then it is allocated to 2026-03-02, the nearest charge day
- Given a rate of Rp 250.000 and a Rp 100.000 payment, when allocation runs, then the single row is Rp 100.000 -- partial coverage, not a full day
- Given any payments and charge days, when allocation returns, then for every payment the rows sum to that payment's amount, and no two rows share a `(paymentId, chargeDate)` pair
- Given a payment dated after `asOf`, when allocation runs, then it produces no rows and the other payments are unaffected
- Given `ANDROID_HOME` and `ANDROID_SDK_ROOT` cleared and `local.properties` absent, when `gradlew.bat :domain:test` runs, then every test above passes with no SDK lookup

## Spec Change Log

**"Exactly one `chargeDate = null` row" re-scoped from "per allocation run" to "per
payment".** The Always-list said "Every leftover gets exactly one row with
`chargeDate = null`", read against the whole result. Implemented literally, that drops
money: when the day list runs out with several deposits still holding cash, only the
payment the loop happened to stop on gets a row and the tail vanishes -- the same failure
as the Rp 13.000.000 case, in a smaller denomination. This was not a hypothetical; the
`Σ allocation == amount` `check` the Tasks section asks for caught it on the first test run
of the prefix-stability row, and the fix is to emit one remainder row *per payment that
still holds cash*. A payment that fits exactly still produces none, which is what the
sentence was reaching for.

**`chargeDays` gained a strictly-ascending precondition, and duplicate payment ids with it.**
The Design Notes claim the returned list "cannot contain a duplicate
`(paymentId, chargeDate)`". That holds only for two inputs the spec never constrained: the
day list must be ascending and duplicate-free, and no two counted payments may share an
`id`. `chargeDaysIn` happens to satisfy the first today, and persistence happens to
guaranteed the second, so asserting neither would leave the claim resting on the discipline
of callers rather than on the function. Both are now `check`s with named tests. This is
enforcing what the spec already promised, not adding a rule.

**`ChargeDays.kt`'s "nearest charge day" KDoc corrected, and why it was wrong.** Lines
34-37 claimed the allocator uses `filter { it <= paymentDate }.maxOrNull()` and justified
the ascending contract by that rule. `verify-fr10.md` case 1 refutes it directly: Rp
500.000 received 20 Mar closes 25 + 26 Feb, the two *oldest* days, not the two nearest the
payment date. The corrected KDoc states that there is no proximity rule and explains the
sort order as what it actually is -- the precondition the chronological walk depends on. A
maintainer reading the old text would have had grounds to "fix" the allocator toward
nearest-day, which is the Rp 1.000.000-per-month prototype bug returning from the other
direction.

## Review Triage Log

### 2026-10-13 — Review pass

Four layers reported: Blind Hunter 14, Verification Gap 2 gap findings + 1 other, Edge Case
Hunter 6, Intent Alignment 1. Verdicts below are from my own verification of each claim at
the cited file and line, not the layer's word.

- verdicts: 11 findings — high 0, medium 2, low 3, false 0, maybe-false 6
- findings:
  - `[medium]` `patch` **no test pins the inclusive `asOf` boundary** — verified at
    `AllocationEngine.kt:80`: the gate is `!it.date.isAfter(asOf)`, so a payment dated
    *exactly* today counts, but no `Payment` in the test file is dated `2026-03-24` or
    `2026-04-24` — grep returns only the fixture field declarations. Flipping to
    `it.date.isBefore(asOf)` passes all 30 tests, because the two asOf cases are dated
    30 Mar, a week clear of the edge. In production `asOf` is today, so a deposit recorded
    today would be excluded and the arrears figure overstated until the next recompute.
    Patched: two tests, one at the edge and one alongside a counted payment.
  - `[medium]` `patch` **the `remaining` vs `queue[i].amount` arm of the remainder loop is
    untested** — verified at `AllocationEngine.kt:123` by tracing both tests that reach the
    loop: `each payment holding cash keeps its own remainder row` and `a payment on a
    contract with no charge days yet` both arrive with `remaining == queue[index].amount`,
    so the arms are equal. The day loop can stop mid-payment (`unfilled > 0` while
    `remaining < dailyRate`), and replacing `remaining` with `queue[i].amount`
    unconditionally would over-report surplus with all tests green. Patched: two tests that
    exhaust the day list part-way through a Rp 1.000.000 deposit, across one and two days.
  - `[low]` `patch` **no test covers a payment dated strictly before the period opens** —
    `epics.md:427` names exactly that shape (Rp 13.000.000 dated 2026-02-10 against a period
    opening 25 Feb) and the suite only ever uses payments on or after the period start, where
    "is it before the period" is indistinguishable from a plain date comparison. This is the
    `verify-fr10.md` case-4 regression guard — the PRD's "payments inside the current
    period" wording — so it is worth pinning even though no current bug exists. Patched.
  - `[low]` `patch` **`ChargeDaysTest.kt:287` asserts a rule the new engine denies** —
    verified: the comment reads "Story 1.3 allocates it to Mon 2 Mar", while
    `AllocationEngine.kt:238-243` states there is no proximity rule and the money closes the
    oldest open day (25 Feb, on that fixture). Left uncorrected, a maintainer reading the
    test would have grounds to "correct" the allocator back. Patched: comment reworded.
  - `[maybe-false]` `reject` **the nearest-charge-day rule diverges from `epics.md:435`** —
    the Intent Alignment layer reads this as a rule conflict. Verified against the two
    binding sources and refuted: `verify-fr10.md:29` (the numerical authority, cited as such
    in the Code Map) fixes case 1 at "2 hari tertua (25–26 Feb)", and `epics.md:435`'s "Senin
    2 Mar" is only reachable on a contract whose first charge day is 2 Mar. The two readings
    are reconciled by the fixture, which the spec's Design Notes state and the test file
    pins. `ChargeDays.kt`'s corrected KDoc says exactly this, so the rule is documented in
    the code rather than left to be re-derived.
  - `[maybe-false]` `reject` **`contractId` is absent from `Payment`, so two contracts'
    payments could be merged** — verified: `Allocation.kt:33-37` argues the omission, and the
    allocator is documented as replaying one contract at a time. But the argument given is
    about not *storing* a field that cannot differ, and it does not address *detecting* a
    caller that concatenates two contracts. That is a caller precondition, and a `Payment`
    carrying a `contractId` would add a field this story cannot validate the uniqueness of —
    the check would belong to the layer that owns contracts. Not this story's gap.
  - `[maybe-false]` `reject` **`settledThroughDate` is a hand-off no one subtracts** —
    verified: `epics.md:501-504` (Story 1.5) states the rule in full — all charge days
    before it count as closed and no allocation row is written. It is explicitly assigned,
    not orphaned. Recorded in the residual risks because Story 1.5's implementer must know
    the day list arrives pre-filtered.
  - `[maybe-false]` `reject` **`UNIQUE(paymentId, chargeDate)` does not constrain NULL rows
    in SQLite** — true of the engine and true of SQLite, which treats NULLs as distinct in
    unique indexes, so two `chargeDate = null` rows could exist for one payment at the DB
    level. The domain cannot fix it: this story has no database, and the constraint belongs
    to the `ALLOCATION` table that Epic 3 creates. The engine's own invariant check is what
    makes the rows correct in memory. Worth carrying to Epic 3; not this story's defect.
    Deferred rather than dropped — recorded below.
  - `[maybe-false]` `reject` **the `Σ allocation == amount` check only covers counted
    payments, so the Always-list's "for every payment" is false as written** — verified: the
    check runs over `queue`, which is post-`asOf`-filter. A future payment is deliberately
    excluded; the Always-list sentence means every payment that takes part, and the
    Spec Change Log already re-scoped one Always-list sentence in exactly this direction.
    Terminology, not behaviour.
  - `[maybe-false]` `reject` **`chargeDays` running past `asOf` is unguarded** — the layer
    proposes `check(chargeDays.all { !it.isAfter(asOf) })`. Not needed: `chargeDaysIn` caps at
    `asOf` as its fourth exclusion, and a day list extending past today would mean a caller
    built it by hand. The same argument as the ascending-order `check`, which was added
    because the uniqueness guarantee depends on it; this one guards nothing.
  - `[maybe-false]` `reject` **a duplicate payment id that is *also* dated after `asOf`
    throws on the next rollover run** — verified `AllocationEngine.kt:86-93`: the id check
    runs on `queue`, i.e. after the `asOf` filter, and
    `a future payment sharing an id with a counted one is not a collision` pins that. When
    the future payment becomes countable, its id is genuinely in the queue then, and throwing
    is correct. No fix wanted.

## Design Notes

**Why the leftover is a regenerated row, not a stored balance.** AD-3 calls the stored rows facts and forbids recomputing from payments, while also requiring `Σ allocation == amount` per payment. Those collide only if the `chargeDate = null` row is permanent. It is not: oldest-first allocation is **prefix-stable** -- appending later charge days can never change which day an earlier rupiah closed, so a longer day list only *fills* the leftover and adds rows. Earlier rows come back byte-identical; the acceptance criteria test that directly. This is `verify-fr10.md`'s Option B, whose price -- "pembatalan harus membuka alokasi lalu menghitung ulang" -- Epic 3 pays in its `@Transaction`, not here.

**AC 4's "Senin 2 Mar" vs chronological allocation.** AC 4 says a Sunday payment lands on the nearest charge day; AC 1 and `verify-fr10.md` case 1 say oldest-open-first. They conflict only if AC 4's contract has earlier unpaid days, and AC 4 never fixes its start date. Anchoring that contract at Monday 2026-03-02 makes 2 Mar its oldest charge day, so both rules hold at once and the sentence is literally true. Its substantive clauses -- Sunday is not charged, the money is credited, arrears falls a day -- hold either way, which is why this is a fixture choice, not a coin flip. The matrix pins both variants so a future rule change breaks a named test.

**Charging a day twice.** The UK at `architecture-spine.md:362` is enforced here, not in the database: the allocator walks each day once per payment and never revisits it, so the returned list cannot contain a duplicate `(paymentId, chargeDate)`. Asserted on the returned value, where a regression is cheap to see.

## Verification

**Commands:**
- `gradlew.bat :domain:test --rerun-tasks` with `ANDROID_HOME` and `ANDROID_SDK_ROOT` cleared and `local.properties` absent -- expected: BUILD SUCCESSFUL, new tests executed, no "SDK location not found"
- `gradlew.bat :domain:test --tests "*AllocationEngineTest*"` -- expected: every matrix row and both property sweeps asserted and passing

**Performed.** Both commands above, re-run after the review patches: BUILD SUCCESSFUL,
77 tests, 0 failures -- 35 in `AllocationEngineTest` plus the 42 from Stories 1.1 and 1.2,
none touched. No SDK lookup: `local.properties` is absent from the repository and both
variables were cleared in the shell that ran Gradle. I re-ran the suite myself rather than
relying on the implementer's report, and read the staged diff of `domain/` to confirm the
engine file carries neither of the mutations it was briefly edited with.

**Matrix audit.** All 11 rows of the I/O matrix are covered by a test that ran and passed in
the run above -- verified by name, not by inspection of the file. The two rows whose tests
are new since implementation (`Payment before period`, `Non-positive amount`) are pinned by
tests added in the review pass.

**Mutation checks, because green tests on a new engine prove nothing on their own.** Six
injected regressions were each caught by name, not by a blanket failure:
- allocating charge days newest-first -- caught by 10 tests, including both prefix-stability
  rows and case 1;
- dropping the `chargeDate = null` remainder row -- caught by 9, including both property
  sweeps and both credit rows;
- gating payments by period membership instead of `asOf` -- caught by 5, which is the bug
  `verify-fr10.md` case 4 was written to prevent;
- `!isAfter(asOf)` → `isBefore(asOf)` -- caught by both new boundary tests (2 of 35);
- `if (i == index) remaining else …` → `queue[i].amount` -- caught by both new remainder
  tests (10 of 35 total).

**One real bug found this way.** The first test run failed on the prefix-stability row with
`allocation rows for payment p3 sum to 0, not its amount 125000` -- thrown by the engine's
own `check`, not by a test assertion. Cause and fix are in the Spec Change Log; the point
worth keeping is that the invariant the spec asked to be asserted is what surfaced the bug,
and no single matrix row would have.

**Residual risks.**
1. **`contract.endDate` is still not honoured** (inherited from Story 1.2, owned by Epic 2
   story 2.7). A contract ending mid-period over-charges; allocation makes that worse,
   because the over-charge now produces stored rows rather than a wrong number on screen.
2. **Two payments sharing an id is rejected, not merged.** The `check` fires rather than
   silently treating two rows as one Setoran. Persistence owns the primary key, so this
   should be unreachable -- but if a caller ever does hand the same id twice, it now throws
   instead of the previous behaviour of emitting two payments' rows under one id.
3. **The allocator's day loop is `O(payments + days)`, untimed.** Story 1.6 owns the
   benchmark; this story claims nothing about it.
4. **`asOf` is a parameter, not a `Clock`.** Story 1.2's Change Log left AD-8 open on
   whether a real `Clock` abstraction is wanted. This story inherits that decision unchanged
   -- it takes `asOf` the same way `chargeDaysIn` does.
5. **`settledThroughDate` is not applied here, by Story 1.5's design.** Days closed by
   FR-9b must be excluded from `chargeDays` *before* this function sees them -- the engine
   has no notion of them, and giving it one would mean writing allocation rows for a rule
   that explicitly writes none. Story 1.5 owns the subtraction
   (`epics.md:501-504`); its implementer needs to know the day list arrives pre-filtered,
   because an unfiltered list makes a settled contract look overdue.
6. **The `NULL` gap in `UNIQUE(paymentId, chargeDate)`.** SQLite treats NULLs as distinct, so
   two `chargeDate = null` rows for one Setoran are possible at the database level. Recorded
   in `deferred` for Epic 3; the domain's invariant check is what keeps the rows correct
   until then.

## Auto Run Result

Status: done

**Summary.** Story 1.3 gives AD-3 its engine: one pure function,
`allocatePayments(payments, chargeDays, dailyRate, asOf)`, replays a contract's deposits
across its charge days oldest-first and returns the rows that say which rupiah closed which
day. Credit is no longer a separate number to remember — it is the `chargeDate = null` row,
and the acceptance criteria pin that extending the day list never rewrites an earlier row.
77 tests pass with the Android SDK cleared, and every row of the I/O matrix is covered by a
test that ran.

The engine's own `Σ allocation == amount` check earned its keep on the first test run: it
caught the day list running out while several deposits still held cash, where a single
remainder row would have silently dropped the tail — the Rp 13.000.000 failure again, in a
smaller denomination. No matrix row would have caught it.

**Files changed.**
- `domain/src/main/kotlin/.../model/Payment.kt` — `Payment(id, date, amount)`, amount checked
  on construction; no `status`, because a cancelled Setoran never reaches the allocator
- `domain/src/main/kotlin/.../model/Allocation.kt` — `Allocation(paymentId, chargeDate?,
  amount)`, one `ALLOCATION` row minus the `contractId` the caller already holds
- `domain/src/main/kotlin/.../calculate/AllocationEngine.kt` — `allocatePayments`; chronological
  walk, `asOf` gating, per-payment remainder rows, ascending-day and unique-id `check`s
- `domain/src/main/kotlin/.../validation/InvalidAmountException.kt` — amount guard, message
  built from `MIN_AMOUNT`
- `domain/src/main/kotlin/.../calculate/ChargeDays.kt` — the stale "nearest charge day" KDoc
  replaced with the chronological rule and the ascending precondition it actually rests on
- `domain/src/main/kotlin/.../package-info.kt` — dropped the stale "first real domain type
  arrives in Story 1.2"
- `domain/src/test/kotlin/.../calculate/AllocationEngineTest.kt` — 35 tests: one per matrix row,
  both prefix-stability rows, both property sweeps over 8 scenarios
- `domain/src/test/kotlin/.../calculate/ChargeDaysTest.kt` — one comment corrected to stop
  asserting a rule the allocator denies

**Review findings breakdown.** 11 findings across four layers: high 0, medium 2, low 3,
false 0, maybe-false 6. Patches applied: 4 (both medium, both low that needed code) — the
inclusive `asOf` boundary had no test at all, which in production is the *common* case
because `asOf` is today; the remainder loop's `remaining` arm was unreachable in every
existing test; plus a pre-period payment test and the stale comment. Deferred: 1 (the SQLite
`NULL` uniqueness gap, Epic 3's). Rejected: 6, each with its refutation recorded —
most consequentially the claim that the nearest-charge-day rule conflicts with chronological
allocation, which `verify-fr10.md:29` and `epics.md:435` together refute, and the claim that
`Payment` needs a `contractId` to detect cross-contract misuse, which would validate a
uniqueness this layer cannot check.

**Follow-up review recommendation.** `false`. Four entries were patched (medium 2, low 2) and
none was high; the deferred item belongs to a different epic. The remaining risk is recorded
in the residual risks above rather than in an unverified gap.

**Residual risks.** Listed above — `contract.endDate`, the duplicate-id `check`, the untimed
loop, the inherited AD-8 `Clock` reading, the `settledThroughDate` hand-off to Story 1.5,
and the SQLite `NULL` uniqueness gap.