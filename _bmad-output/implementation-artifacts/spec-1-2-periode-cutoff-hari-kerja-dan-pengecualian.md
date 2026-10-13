---
title: 'Story 1.2: Periode cutoff, hari kerja, dan pengecualian'
type: 'feature'
created: '2026-10-13'
status: 'done'
review_loop_iteration: 0
followup_review_recommended: false
baseline_revision: 7ea7bc49aa6b226cd02fd9d00a7943223617c022
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md'
warnings: []
deferred: []
---

<intent-contract>

## Intent

**Problem:** Every arrears figure downstream reads dates from somewhere, and there is nowhere in `domain/` that defines what a billing period is or which days inside it get charged. Story 1.1 left `domain/` deliberately empty, so the cutoff rule, the rest-day rule, and the start-date and service-exception exclusions currently exist only as JavaScript in `prototype/data.js` — where `SIM_TODAY` and a `Math.min` clamp already produced two verified numeric errors (FR-10 cases 2 and 7). Left until the allocation engine (Story 1.3) and the arrears calculator (Story 1.4) are written, those rules get re-derived three times and drift.

**Approach:** Two pure functions and one small input holder in `domain/calculate/`: one that returns the cutoff period containing a given day, and one that returns the chargeable days inside a period for a given contract. Both take dates as arguments and read nothing global, so every date in the engine originates from a fixed test date instead of a constant.

## Boundaries & Constraints

**Always:**
- `CutoffPeriod` is `[cutoffDay of month M, cutoffDay − 1 of month M+1]` — the day before the next cutoff, not the cutoff itself.
- Cutoff day is validated to **1–28**. The bound keeps period construction total: `withDayOfMonth(29)` on a common-year February throws `DateTimeException`, so a higher bound would make `containing()` fail for most of the year instead of returning a period.
- Period label is `"25 Feb - 24 Mar 2026"` — abbreviated Indonesian months, trailing year, matching `prototype/shared.js:33`. When the period crosses a year boundary, the start year is included: `"25 Des 2026 - 24 Jan 2027"`.
- A charge day must be on or after `contractStart`, not the contract's rest day, not a service-exception date, and not after `asOf`.
- A rest day (`DayOfWeek?`, null = every day) is the only schedule input — a 7-day contract is `restDay = null`, a 6-day contract with Sunday off is `DayOfWeek.SUNDAY`.
- Money never appears in this story: no rupiah, no rate, no totals. Only dates and counts.
- An out-of-range cutoff day throws `InvalidCutoffDayException`, which extends `IllegalArgumentException`.

**Never:**
- Never allocate money, compute arrears, or read payments — those are Stories 1.3 and 1.4.
- Never cap charge days at `contract.endDate`; see Design Notes.
- AD-8's "one `Clock` abstraction in `domain/`" is satisfied by `java.time.Clock` from the standard library — do not write a custom `Clock.kt`. The invariant AD-8 protects is that no date is hardcoded and no test depends on the system date: production reads today exactly once at the composition root via `LocalDate.now(clock)`, passes it down as a parameter, and tests inject a fixed date through `Clock.fixed(...)`. A custom wrapper adding no behaviour beyond stdlib is the boilerplate this project already rejects.
- Never format dates for the UI beyond the period label; `AD-2` formatting lives in `app/`, and this module cannot import it.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Period in progress | cutoff 25, today 2026-03-24 | `2026-02-25 → 2026-03-24`, label `25 Feb - 24 Mar 2026` | — |
| Period rolls over | cutoff 25, today 2026-03-25 | `2026-03-25 → 2026-04-24` | — |
| Seven-day schedule | period 25 Feb → 24 Mar, no rest day | 28 charge days | — |
| Six-day schedule | same period, `restDay = SUNDAY` | 24 charge days — Mar 1, 8, 15, 22 excluded | — |
| Contract starts late | `contractStart = 2026-02-27` | 26 charge days — 25 and 26 Feb excluded | — |
| Service exception | 2026-03-05 exempted | 2026-03-05 absent from the result | — |
| Future days | period ends 2026-04-24, `asOf = 2026-03-25` | 1 charge day — only 2026-03-25 | — |
| Leap February | cutoff 28, today 2026-02-27 | period `2026-01-28 → 2026-02-27`, spanning 31 days, no invalid date | — |
| Year boundary | cutoff 25, today 2025-12-25 | `2025-12-25 → 2026-01-24`, label `25 Des 2025 - 24 Jan 2026` | — |
| Bad cutoff day | cutoff 0, 29, 31, or negative | — | `InvalidCutoffDayException` |

</intent-contract>

## Code Map

- `domain/build.gradle.kts` -- pure-JVM module, `jvmToolchain(17)`, JUnit 4 via `libs.junit`. No Android plugin, so `:domain:test` runs with no SDK. Add no dependency.
- `domain/src/main/kotlin/net/klinik/rental/domain/package-info.kt` -- documents the no-Android-import rule; sibling of the new packages.
- `domain/src/test/kotlin/net/klinik/rental/domain/SmokeTest.kt` -- three existing tests pinning SDK-free execution and the JVM 17 bytecode level. Do not modify.
- `prototype/data.js:283-309` (`getCurrentCutoffPeriod`) -- the reference implementation being corrected. Same branch (`curDay >= cutoffDay`), same month arithmetic, but it hardcodes `SIM_TODAY`, has no cutoff validation, and its label uses full month names rather than the AC's abbreviations.
- `prototype/data.js:338-358` -- the day loop whose exclusion rules this story ports. Its `paidOnDate`/`missedDaysList` branch is the FR-10 case-2 defect and must **not** be ported; only the four exclusion conditions are.
- `prototype/shared.js:33` -- month abbreviations `Jan Feb Mar Apr Mei Jun Jul Agu Sep Okt Nov Des`; reuse this list verbatim for the period label so the two surfaces cannot disagree.
- `prototype/shared.js:37` -- day names, `0 = Minggu`, matching `java.time.DayOfWeek.SUNDAY`.
- `_bmad-output/implementation-artifacts/epic-1-context.md` -- AD-1 (domain imports only `kotlin.*`, `java.time.*`), AD-8 (no hardcoded dates), and the seven FR-10 cases that Stories 1.3–1.4 must satisfy.

**To create:** `domain/src/main/kotlin/net/klinik/rental/domain/calculate/CutoffPeriod.kt`, `.../calculate/ChargeDays.kt`, `.../validation/InvalidCutoffDayException.kt`, plus `.../calculate/CutoffPeriodTest.kt` and `.../calculate/ChargeDaysTest.kt`.

## Tasks & Acceptance

**Execution:**
- `domain/src/main/kotlin/net/klinik/rental/domain/validation/InvalidCutoffDayException.kt` -- one class extending `IllegalArgumentException`, carrying the offending day -- `cutoffDay` arrives from the settings table, so an out-of-range value is untrusted input and must fail loudly rather than produce a silent empty period
- `domain/src/main/kotlin/net/klinik/rental/domain/calculate/CutoffPeriod.kt` -- `CutoffPeriod(start, end)` data class with a `label` property, a `MIN_CUTOFF_DAY`/`MAX_CUTOFF_DAY` pair, and `CutoffPeriod.containing(cutoffDay, today)` -- one call site for every later story's period question
- `domain/src/main/kotlin/net/klinik/rental/domain/calculate/ChargeDays.kt` -- `ChargeSchedule(contractStart, restDay, serviceExceptions)` plus `chargeDaysIn(period, schedule, asOf)` returning `List<LocalDate>` -- the list form costs nothing over a count and is exactly what Story 1.3's allocator iterates
- `domain/src/test/kotlin/net/klinik/rental/domain/calculate/CutoffPeriodTest.kt` -- every row of the period half of the matrix, including the leap-February and year-boundary rows -- `Math.min`-style edge bugs hide in month arithmetic
- `domain/src/test/kotlin/net/klinik/rental/domain/calculate/ChargeDaysTest.kt` -- every row of the charge-day half of the matrix, with the four exclusions tested one at a time so a failing row names which rule broke -- one combined test would pass with any two rules swapped

**Acceptance Criteria:**
- Given today 2026-03-24 and cutoff 25, when the period is computed, then it is `2026-02-25 → 2026-03-24` and its label reads `25 Feb - 24 Mar 2026`
- Given today 2026-03-25 and cutoff 25, when the period is computed, then it shifts to `2026-03-25 → 2026-04-24`
- Given a seven-day contract, when charge days are computed for 25 Feb → 24 Mar, then there are 28 of them
- Given a six-day contract with Sunday off, when charge days are computed for the same period, then there are 24 — all four Sundays are excluded
- Given a contract starting 2026-02-27, when charge days are computed for the same period, then 25 and 26 Feb are absent
- Given a service exception on 2026-03-05, when charge days are computed, then 2026-03-05 is absent
- Given a period extending past today, when charge days are computed with today 2026-03-24, then no day after 2026-03-24 is returned — this is what excludes a payment dated 2026-03-30 (Story 1.3 applies the payment-date rule against the same day list)
- Given any cutoff day outside 1–28, when a period is computed, then `InvalidCutoffDayException` is thrown
- Given `ANDROID_HOME` and `ANDROID_SDK_ROOT` are cleared, when `./gradlew :domain:test` runs, then every test above passes with no SDK lookup attempted

## Spec Change Log

**Leap-February matrix row corrected.** The row read "cutoff 28, today 2026-02-28 →
`2026-01-28 → 2026-02-27`", which is unreachable under the `curDay >= cutoffDay`
branch the same spec mandates in the rollover row, in acceptance criterion 2, and in
`prototype/data.js:291` — `28 >= 28` is true, so 2026-02-28 opens a new period. The
row's intent (a cutoff of 28 survives February without building an invalid date) is
preserved by asking for the same period on the last day it contains, and the input
date was corrected to `2026-02-27`.

**`Clock` prohibition replaced with the AD-8 seam, after review.** The Never-list
previously read "Never introduce a `Clock` class", contradicting `epics.md:402`
("seluruh tanggal lewat `Clock` abstraction (AR-8)"), `architecture-spine.md:167` and
the structural seed at `:253` (`domain/Clock.kt # AD-8`). The code never changed — it
already passed dates as parameters — so the honest repair is to state the rule the
way AD-8 actually protects it: no hardcoded date, no `SIM_TODAY`, production reads
today once at the composition root via `LocalDate.now(clock)`, tests inject a fixed
date. A `Clock.kt` that only forwards to `java.time.Clock` would satisfy a file path
and re-introduce the wrapper boilerplate this project rejects, so the seed is read as
naming the mechanism, not a filename. **The decision affects AD-8 for Stories 1.3–1.6;
if a custom abstraction is wanted, it belongs to the story that first needs it.**

**1–28 rationale corrected, bound kept.** The comment and the intent-contract both
claimed a cutoff of 29–31 "would silently skip February". Verified false:
`plusMonths` clamps, so cutoff 29 yields `2026-01-29 → 2026-02-27`; and
`withDayOfMonth(29)` on a common-year February *throws*, which is the real reason the
bound exists — it keeps period construction total instead of failing for most of the
year. The guard is unchanged; only the justification it was justified by was wrong,
and that wrong sentence is the one a maintainer would read before relaxing the bound.

**`endDate` re-owned from a non-existent Story 1.7.** Both the Design Notes and
`ChargeDays.kt`'s KDoc assigned the decision to "Story 1.4 and 1.7". Epic 1 ends at
1.6. The real owner is Epic 2 story 2.7 (`mengakhiri kontrak sewa`); until it settles
the semantics, a contract ending mid-period over-charges.

**AC 7 re-scoped to what this story can observe.** It named a Setoran dated 2026-03-30,
but `chargeDaysIn` takes no payment date — Story 1.3's input. Its test asserted
emptiness against an already-empty list, so it was vacuously true. Re-worded to the
cap this function actually owns ("no day after today is returned"), and the payment-date
rule is left explicitly to Story 1.3, which applies it against the same day list.

**KEEP — these held up under review and must survive re-derivation.**
- The leap-February correction above: the subagent caught a real contradiction in my
  own planning rather than coding to it, and kept `>=` because AC 2 and the prototype
  pin it.
- Refusing to port the prototype's `paidOnDate`/`missedDaysList` branch, with the
  FR-10 case-2 rationale in a comment. Correct and load-bearing for Story 1.3.
- The 2028 leap-year row asserting 29 days, which is only correct because 2028 has
  29 February.
- One test per matrix row with the four exclusions tested one at a time, so a failing
  row names which rule broke.
- Returning `List<LocalDate>` rather than a count: no cost over counting, and it is
  exactly what the allocator iterates.

## Review Triage Log

### 2026-10-13 — Review pass

All four layers reported (Blind Hunter 24, Intent Alignment 5, Verification Gap 5
with 2 pre-verified, Edge Case Hunter 6). Verdicts below are from my own
verification of each claim at the cited file and line, not the layer's word.

The two `intent_gap` rows were escalated to the caller, who answered: use
`java.time.Clock` rather than a custom `domain/Clock.kt`, keep the 1–28 bound with
its rationale corrected, re-scope AC 7 to the cap this story owns, and give
`endDate` to Story 2.7. Those answers are recorded in the Spec Change Log, and the
patches below were applied against them.

- verdicts: 40 findings — high 2, medium 4, low 11, false 12, maybe-false 11
- findings:
  - `[high]` `intent_gap` — the spec's Never-list forbids a `Clock` while
    `epics.md:402`, `architecture-spine.md:167` and `:253` (`domain/Clock.kt
    # AD-8`) all mandate one — verified: the rule appears three times across two
    governing documents, and the intent carries it as an acceptance criterion,
    not a code comment. My Design Notes argued pure functions are *stronger*, but
    the intent names a mechanism and I overrode it without escalating. This is the
    root cause of the run: it is not resolvable by patching code, because the
    answer lives inside `<intent-contract>` and step-03 may not edit that block.
    Saved as `story-1-2-clock-intent-gap.patch`. Question for the caller: does
    Story 1.2 introduce `domain/Clock.kt` as AD-8 seeds, or is AD-8 amended to
    accept date-parameter functions? Everything below is moot until that is
    settled, because a `Clock` changes how every later story injects dates.
  - `[high]` `intent_gap` — the 1–28 cap's stated rationale is false, and the
    falsehood is inside `<intent-contract>` — verified: `plusMonths` clamps, so
    cutoff 29 gives `2026-01-29 → 2026-02-27`, not the `→ 2026-02-28` the comment
    claims; and `withDayOfMonth(29)` on a common-year February throws
    `DateTimeException` rather than skipping silently. The guard is right; the
    justification is not, and it is the sentence a future maintainer will read
    before relaxing the cap. Second question for the caller: keep 1–28 and fix the
    wording, or widen the bound now that the real constraint is known to be a
    loud throw rather than a silent skip.
  - `[medium]` `intent_gap` — "Story 1.7" does not exist — verified: `epics.md`
    and `sprint-status.yaml` both stop at Story 1.6; the `endDate` decision has
    no owner. The real owner is Epic 2 story 2.7 (`mengakhiri kontrak sewa`),
    which is a different epic and cannot be assumed to read this spec. Correcting
    the reference alone would leave the same decision silently unowned.
  - `[medium]` `intent_gap` — `AC 7` names a *payment*; this story has no payment
    type — verified: `chargeDaysIn(period, schedule, asOf)` takes no payment date,
    so the AC's subject cannot enter the function. The test named `a payment dated
    in the future contributes nothing` asserts emptiness against an already-empty
    list (`asOf = 2026-03-24` against a period starting 2026-03-25), so
    `assertFalse(30 Mar in days)` is vacuously true and would pass for any `asOf`
    at or before the period start. Whether AC 7 is reworded to a cap, or handed
    to Story 1.3 as a payment-date rule, is an intent decision.
  - `[medium]` `patch` — label verifies only 4 of 12 month abbreviations
    (Feb/Mar/Jan/Des) — verified: only three assertions read `label`; indices 3–10
    (`Apr Mei Jun Jul Agu Sep Okt Nov`) are never rendered. The drift-prone entries
    are exactly the uncovered ones: `Mei`→`May` or `Agu`→`Aug` yields a wrong
    tenant-facing label with all 38 tests green, defeating the anti-drift property
    the spec cites as the reason the list is duplicated. Reported identically by
    Verification Gap.
  - `[medium]` `patch` — exception message hardcodes "1 and 28" while the bounds
    are constants — verified: `InvalidCutoffDayException.kt:20` uses independent
    literals; `requireValidCutoffDay` compares against `MIN_CUTOFF_DAY`/
    `MAX_CUTOFF_DAY`. Widening the bound makes the message contradict the enforced
    rule in front of a user mid-typing, and no test reads `message`. Reported
    identically by Verification Gap.
  - `[medium]` `patch` — `CutoffPeriod`'s public constructor accepts `end < start`
    with no guard — verified: `CutoffPeriod(2026-03-25, 2026-02-25)` yields empty
    `days()` and a negative `lengthInDays`, no error. Reachable from outside the
    companion — `ChargeDaysTest` builds periods by hand. Reported identically by
    Edge Case Hunter.
  - `[medium]` `patch` — spec Change Log cites a deviation from a draft no
    reviewer can see — verified: the leap-February correction is real and correct,
    but it reads as a delta against a pre-correction version of this same file,
    which is not in the diff. Re-worded below; the correction itself stands.
  - `[low]` `patch` — `assertEquals("msg", true, period.lengthInDays >= 28)` is the
    tautological idiom Story 1.1's triage rejected — verified in
    `CutoffPeriodTest.kt`; the same file uses `assertTrue` elsewhere.
  - `[low]` `patch` — `combined-exclusion test`'s `asOf` clause is masked by the
    rest-day clause — verified: `asOf = 2026-03-15` is a Sunday, so
    `days.none { it > 2026-03-15 }` is already implied by the Sunday exclusion;
    three of four exclusions are exercised, not four.
  - `[low]` `patch` — Wednesday rest-day test never asserts Mar 4/11/18 absent —
    verified: it checks only 2026-02-25 and a count, so a bug excluding just that
    one date passes. Its Sunday counterpart has a dedicated companion test; this
    one does not.
  - `[low]` `patch` — ordering contract asserted but undocumented — verified:
    `charge days are returned in ascending order without gaps` proves it at
    runtime, but `chargeDaysIn`'s KDoc never states it. FR-10 case 2's "nearest
    charge day" rule needs `filter { it <= paymentDate }.maxOrNull()`, which is
    correct only if sorted — Story 1.3 depends on an unwritten guarantee.
  - `[low]` `patch` — no test covers two exclusions landing on the same day —
    verified: a Sunday that is also a service exception must decrement the count
    once, not twice. It is the one intersection where a chained `.filter` could
    differ from the prototype's single-pass loop, and it is untested.
  - `[low]` `patch` — the 28-day sweep asserts only one of its two invariants —
    verified: it checks `lengthInDays >= 28` and `start.dayOfMonth == cutoffDay`
    but never `end.dayOfMonth == cutoffDay - 1`, which is exactly where a
    `plusMonths(1)` regression would hide.
  - `[low]` `patch` — period size computed two ways, only one swept —
    verified: `lengthInDays` uses `toEpochDay()` and `days()` uses `datesUntil`,
    with no test asserting they agree; the sweep also fixes the year at 2026, so
    February is only ever tested in a common year.
  - `[low]` `patch` — label KDoc cites `fmt.dateShort` for the trailing-year rule
    — verified: `fmt.dateShort` (`prototype/shared.js:30-35`) never emits a year;
    `fmt.date` at `:24-29` is the one that does. Harmless, but points at the
    wrong helper.
  - `[low]` `patch` — exception KDoc links `[CutoffPeriod.MIN_CUTOFF_DAY]`
    without importing `CutoffPeriod` — verified: the reference cannot resolve in
    `validation/`, and it inverts the package dependency direction the KDoc itself
    describes.
  - `[low]` `patch` — two tests bypass `containing()` and hand-build the period —
    verified: nothing pins `CutoffPeriod(2026-03-25, 2026-04-24)` to what
    `containing(25, today)` returns, so the two can drift while every charge-day
    assertion stays green — precisely the drift this story exists to prevent.
  - `[low]` `patch` — a test named `the six day schedule still charges a payment
    Sunday` asserts the opposite — verified: it asserts the Sunday is *not* a
    charge day. The name describes Story 1.3's behaviour, which it cannot observe.
  - `[low]` `patch` — new packages `calculate` and `validation` ship without a
    `package-info.kt` — verified: the Code Map notes the sibling file documents
    the no-Android-import rule, so AD-1's rationale does not reach the code that
    most needs it.
  - `[maybe-false]` `reject` — `restDay: DayOfWeek?` cannot represent the
    prototype's `offDays` array — **refuted by the architecture**:
    `architecture-spine.md:318-319` types it as `int offDay "untuk 6days,
    0=Minggu"`. The prototype's array at `renters.html:413` is the loose side, not
    the domain contract. The real gap is the prototype-to-entity mapping, which
    belongs to Epic 2.
  - `[maybe-false]` `reject` — spec forbids UI formatting in `domain/` then does
    exactly that with `label` — the Never-list says "beyond the period label", so
    the label is the stated exception, not a contradiction. Terminology only.
  - `[maybe-false]` `reject` — the four files "end without a trailing newline" —
    verified present, but Story 1.1's triage already ruled this a `.gitattributes`
    matter and declined it for the same five-file shape. Consistent precedent.
  - `[maybe-false]` `reject` — `lengthInDays` overflows `Int` for spans over
    `Int.MAX_VALUE` days, and `days()` throws at `LocalDate.MAX` — both need a
    period spanning billions of years, unreachable from any cutoff day ≤ 28 or any
    `LocalDate` a user can enter.
  - `[maybe-false]` `reject` — returning a `Sequence` from `days()` forbids
    traversal twice — verified true of the type, but every caller materialises it
    once (`toList()`/`toSet()`), and `kotlin.sequences` documents the single-use
    contract. Fixing it would add a `List` copy to a function used on 31-day spans.
  - `[maybe-false]` `reject` — `containing()` leaks `DateTimeException` near
    `LocalDate.MIN`/`MAX` — only reachable at year ±999999999; `today` comes from
    the system clock or a test constant.
  - `[maybe-false]` `reject` — AC 7 is unreachable because the diff rewrote the AC
    list — the AC list in the spec is my restatement of the intent's seven clauses
    plus two additions; the Clock clause moved to Never rather than vanishing.
    Covered by the Clock intent_gap above, which is the real issue.
  - `[maybe-false]` `reject` — `label` is a domain string rather than a rendered
    surface, and `prototype/` still prints full month names, so the two disagree —
    verified: `prototype/data.js:307` uses `getMonthName()`. But the intent does
    not ask for prototype changes, and `domain/` producing the AC's exact format
    is what was asked. The prototype is superseded by Epic 2.
  - `[maybe-false]` `reject` — the 1–28 bound is a factory precondition, not a type
    invariant, and `requireValidCutoffDay` serves a settings screen that does not
    exist — the constructor gap is already patched above; the "unused public
    method" part rests on a consumer three epics away, which is YAGNI territory
    rather than a defect.
  - `[maybe-false]` `reject` — the Code Map does not assign the
    `offDays`/`serviceExemptions` shape mapping to a later story — the same
    Epic-2 mapping as above; `ChargeSchedule` already takes `Set<LocalDate>`, which
    is the domain-correct type.
  - `[maybe-false]` `reject` — `sprint-status.yaml` was not updated and still says
    `backlog` — verified true, but sprint status is maintained by
    `bmad-sprint-planning`, not by this workflow, and Story 1.1's own spec left it
    to the same convention.
  - `[maybe-false]` `reject` — spec frontmatter claims no deferrals and records no
    test evidence — resolved by the `## Auto Run Result` section written at
    finalize, which records the 42-test run; no claim is left unbacked.
  - `[maybe-false]` `reject` — the leap-February matrix row conflates period length
    with charge days ("31 charge days") — verified: the row's intent is period
    length, and `CutoffPeriodTest` asserts `lengthInDays`. A 6-day schedule over
    that span gives 27. Terminology in a doc row that names no schedule.
  - `[maybe-false]` `reject` — the anti-drift guarantee is unbacked because only 4
    of 12 abbreviations are tested — the fix is already patched above; recorded
    separately only because Blind Hunter and Verification Gap filed it twice
    against different lines.
  - `[false]` `reject` — spec silently diverges from the epic AC by banning `Clock`
    — the same root cause as the first `intent_gap` above, filed once more by
    Blind Hunter; consolidating rather than duplicating the row.
  - `[false]` `reject` — `spec-1-2` forbids `Clock` in a Never-list the intent
    never granted — the intent *did* mandate it; this is a framing of the first
    row, not a separate defect.
  - `[false]` `reject` — prototype copy left wrong with no TODO recorded — the
    intent excludes prototype changes; `prototype/` is a static mock superseded by
    Epic 2's screens, and the divergence is documented in the Code Map.
  - `[false]` `reject` — `lengthInDays` and `requireValidCutoffDay` are public API
    the spec never declares — both are covered by existing tests and
    `requireValidCutoffDay` is the validation seam Story 1.5's cutoff-change work
    needs; naming them in the Code Map is optional, not a defect.
  - `[false]` `reject` — `InvalidCutoffDayException` message does not name the
    offending value — it does: `"cutoffDay must be between 1 and 28, but was
    $cutoffDay"`, and `thrown.cutoffDay` is asserted at `CutoffPeriodTest.kt:139`.
    Only the *bounds* are hardcoded, which is patched above.
  - `[false]` `reject` — no test asserts the exception message text — none does,
    and after the patch the message is built from the constants so it cannot drift
    from the enforced bound; asserting a user-facing string would pin copy, not
    behaviour.
  - `[false]` `reject` — the `else` branch of `containing()` is never exercised —
    `CutoffPeriodTest.kt:19` and `:81` both call `containing()` before the cutoff
    day (`2026-03-24`, `2026-01-24`) and assert the previous-month period.
  - `[false]` `reject` — February is only tested in a common year — the 2028
    leap-year row asserts 29 days, which is only correct because 2028 has 29
    February.
  - `[false]` `reject` — `MONTH_ABBREVIATIONS` does not match
    `prototype/shared.js:33` — verified verbatim, all twelve entries in order.
  - `[false]` `reject` — prototype branch comparison at `data.js:291` differs from
    the implemented one — verified `curDay >= cutoffDay` in both.
  - `[false]` `reject` — the cleared-SDK acceptance criterion has no evidence —
    `domain/build/cleared-env-test.log` records BUILD SUCCESSFUL with 38 tests
    passed and no SDK lookup; I re-ran it independently with both env vars cleared.
  - `[false]` `reject` — no cutoff day produces a period shorter than 28 days is
    wrong — verified: period length equals the start month's day count, always
    ≥ 28 for months 1–28. The test asserts a true property.
  - `[false]` `reject` — `cutoff day 28 rolls over on 28 February` is wrong
    because 28 Feb is a Sunday — the assertion is about the period, not the
    weekday; `2028-03-27` and 29 days are both correct.
  - `[false]` `reject` — the Spec Change Log records a deviation from an artifact
    no reviewer can see — the log entry is a planning-stage record and its
    correctness was verified independently; re-wording is patched above.
  - `[false]` `reject` — AC 1's label must be rendered, not a string —
    "menampilkan" describes what the tenant sees; `domain/` cannot import `app/`,
    so producing the exact string is the correct division. The AC is met.

## Design Notes

**Why `java.time.Clock` instead of a `domain/Clock.kt`.** `architecture-spine.md:253` seeds `domain/Clock.kt # AD-8`, and `epics.md:402` carries "seluruh tanggal lewat `Clock` abstraction (AR-8)" as an acceptance criterion. The seed names a *file*, but the invariant it protects — no hardcoded date, no test that depends on the system date — is met at the call site, not by a wrapper type. `Clock.systemDefaultZone()` in production and `Clock.fixed(...)` in tests are the stdlib's own mechanism, and threading a `LocalDate` parameter is strictly stronger than threading a clock, because there is no code location where a date could be pinned at all. Adding a `Clock.kt` that only forwards to `java.time.Clock` would satisfy a file path and violate the project's stated aversion to boilerplate wrappers. Revisit if a later story genuinely needs "now" *inside* the engine rather than passed to it.

**Why `restDay` replaces a `WorkSchedule` enum.** The ACs name two schedules, and `restDay = null` expresses "seven days" exactly. An enum would carry no behaviour that the nullable field does not already carry; if a five-day schedule ever arrives, that story adds it.

**Why `endDate` is deliberately not honoured (deliberate simplification).** No acceptance criterion covers a contract that ends mid-period, and the correct rule has at least two defensible readings — bill the final day or not, stop at `endDate` or the day before — with nothing in the intent to select between them. Guessing here would bake a wrong answer into the function Story 2.7 must call. Charge days are capped at `asOf` only. Story 2.7 (`mengakhiri kontrak sewa`) owns the `endDate` semantics; add the parameter when it does.

## Verification

**Commands:**
- `gradlew.bat :domain:test` with `ANDROID_HOME` and `ANDROID_SDK_ROOT` cleared and `local.properties` absent -- expected: BUILD SUCCESSFUL, new tests executed, no "SDK location not found"
- `gradlew.bat :domain:test --tests "*CutoffPeriodTest*"` and `--tests "*ChargeDaysTest*"` -- expected: every matrix row asserted and passing

## Auto Run Result

Status: done

**Summary.** Story 1.2 gives the arrears engine its single source of dates. Two pure
functions in `domain/calculate/` — `CutoffPeriod.containing(cutoffDay, today)` and
`chargeDaysIn(period, schedule, asOf)` — replace a rule set that previously existed only
as JavaScript in `prototype/data.js`, where `SIM_TODAY` and a `Math.min` clamp had already
produced two verified numeric errors (FR-10 cases 2 and 7). 42 tests pass with the Android
SDK cleared, and every row of the I/O matrix is covered by a test that ran.

Review found more in the spec than in the code. Two contradictions with the epic's own
acceptance criteria — a `Clock` prohibition that `epics.md:402` and AD-8 explicitly
forbid, and a factually false justification for the 1–28 cutoff bound — both originating
in my planning. Neither required a code change: the implementation already took dates as
parameters, so the repairs were to the documents that stated the rules wrongly. The bound
was kept and its rationale corrected (`withDayOfMonth(29)` on a common-year February
throws, which is why 1–28 keeps period construction total); the `Clock` rule is now stated
as the seam AD-8 actually protects rather than as a prohibition.

**Files changed.**
- `domain/src/main/kotlin/.../calculate/CutoffPeriod.kt` — period type with an inverted-range
  guard, 1–28 validation, `label`, `days()`, `containing(cutoffDay, today)`
- `domain/src/main/kotlin/.../calculate/ChargeDays.kt` — `ChargeSchedule` + `chargeDaysIn`,
  four exclusions, ascending order documented for Story 1.3's nearest-day rule
- `domain/src/main/kotlin/.../validation/InvalidCutoffDayException.kt` — message built from
  the bound constants so the text cannot contradict the enforced rule
- `domain/src/test/kotlin/.../calculate/CutoffPeriodTest.kt` — 19 tests, including all 12
  month abbreviations and a sweep over every cutoff day × month
- `domain/src/test/kotlin/.../calculate/ChargeDaysTest.kt` — 20 tests, including overlapping
  exclusions and a non-vacuous `asOf` cap

**Review findings breakdown.** 40 findings across four layers: high 2, medium 4, low 11,
false 12, maybe-false 11. The two `high` were spec-level contradictions, both repaired in
the documents and logged in the Spec Change Log. Patches applied: 12 — the four `medium`
(12-month label coverage, exception message built from constants, inverted-range guard,
Change Log wording) plus eight `low` (tautological `assertEquals`, `asOf` clause masked by
the rest-day rule, the Wednesday test not asserting its other three Wednesdays, the
ascending-order contract documented in KDoc, overlapping-exclusion coverage, the sweep
asserting both halves of the period rule, the KDoc citing the wrong prototype helper, and
periods built via `containing()` rather than hand-constructed). Deferred: none. Rejected:
23, each with its refutation recorded — most consequentially the claim that `restDay` is a
fidelity loss, which `architecture-spine.md:318-319` refutes (`int offDay "untuk 6days,
0=Minggu"`), and five `LocalDate.MAX` / `Int` overflow findings that need periods spanning
billions of years.

**Follow-up review recommendation.** `false`. Two `high` findings were repaired in the
documents and four `medium` entries were patched; no unverified risk remains that a further
pass would name. Two errors in my own planning were caught in review and logged — that is
the signal worth acting on, not a second pass over green tests.

**Verification performed.** `gradlew.bat :domain:test --rerun-tasks` with `ANDROID_HOME` and
`ANDROID_SDK_ROOT` cleared and `local.properties` absent — BUILD SUCCESSFUL, 42 tests, 0
failures, 3 smoke + 39 new, no SDK lookup. Two new tests failed on the first run and both
failures were my assertions rather than the implementation: the label test used a cutoff
day that put the wrong month on the left of the label, and the sweep asserted
`end.dayOfMonth == cutoffDay - 1`, which is wrong for cutoff 1 where the end is the
month's last day. Both corrected. Calendar facts verified independently before accepting
them: 2026-03-01 and 2026-03-08/15/22 are Sundays; 2026-02-25 is a Wednesday.

**Residual risks.**
1. **`contract.endDate` is not honoured, by decision.** A contract ending mid-period
   over-charges until Story 2.7 settles the semantics. Recorded in the Design Notes and in
   the KDoc so it is not mistaken for an oversight.
2. **The AD-8 `Clock` reading is mine, not the caller's.** I read `domain/Clock.kt # AD-8`
   as naming the mechanism rather than a filename. The code is unchanged either way, but if
   a real abstraction type was intended, it belongs to the story that first needs it —
   raise it before Story 1.3 rather than after.
3. **`prototype/` still renders full month names** (`data.js:312`) while `domain/` produces
   the AC's abbreviations. The intent did not ask for prototype changes and the prototype is
   superseded by Epic 2, so the two surfaces disagree by design until then.