---
title: 'Ending a contract on a future date frees the car today'
type: 'bugfix'
created: '2026-10-20'
status: 'in-progress'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: 'c947b1ed417ecf0c192a05db1449573685d86767'
context:
  - '{project-root}/_bmad-output/planning-artifacts/architecture/architecture-spine.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `ContractRepository.open(contract)` (`ContractRepository.kt:152`) writes the contract
through `Contract.toEntity()`, which nulls both active columns only when `contract.isOpen`
(`ContractEntity.kt:189-201`). It guards the rate and the one-active rule but **not** `isOpen` —
so a `Contract` whose `endDate` is already set is written, the row goes in with both active columns
NULL, the unique indexes never see it, and the contract occupies neither the vehicle nor the renter.
That is the structural failure mode the active indexes exist to prevent, reached through the
constructor rather than through a race.

A caller that has a form can only build a `Contract` whose `endDate` is null — `endContract` is
the only writes path that *can* hold an ended `Contract`. So no caller in the app reaches this today.
The smallest fix is a `require(contract.isOpen)` beside the existing rate guard, mirroring the rule
the indexes already encode. The require's failure path is `IllegalArgumentException` (not
`ContractNotOpenException`), because `open`'s precondition is "this is an open contract" — an
argument the caller mis-stated — rather than "an existing open contract would now be violated", which
is what `ContractNotOpenException` reads.

**Approach:** Add the guard; tighten the entity's constructor pattern check that exists but says
nothing about `isOpen`; add one test that reaches the path the constructor would. No new exception
type, no new domain rule — the rule already lives in `isOpen`, the constructor already enforces
`!endDate.isBefore(startDate)`, and the unique indexes already encode the consequence.

## Boundaries & Constraints

**Always:**
- **Mirror the existing rule, do not invent one.** `isOpen` is the one definition of "still running"
  (`Contract.kt:143`); `open` should consult it before the indexes settle it. The require reads the
  same fact the indexes would.
- **Name the field the caller mis-stated.** The require message says which property was wrong on
  which argument, so a debug stack points at the form that built it.
- **Test reaches the path the existing constructor would not.** A builder that has a form cannot
  build an ended `Contract`, so the test runs against the entity constructor directly with an
  ended `Contract` instance, asserting both the require's failure and the success path of a
  separately-prepared open `Contract`.

**Never:**
- Do not introduce `ContractNotOpenException` here. The type reads "this contract has already
  ended", which is the *result* of an open call that another concurrent call ended. The argument
  shape here is "the caller passed an ended `Contract` to `open`", which is what
  `IllegalArgumentException` exists for.
- Do not touch `Contract.toEntity()`'s `if (isOpen) … null` derivation. That is the rule the
  guard above now mirrors at the entry point; rewriting it would make `toEntity()` itself enforce
  what `open` should have failed first.
- Do not touch the unique indexes or the one-active checks. They are correct, and the fix is to
  reach them only through contracts that satisfied `isOpen` from the start.
- Do not change `ContractRepository.endContract()`. That method is the one that *produces* ended
  `Contract` values; if `open` is fixed, the only place an ended `Contract` is built is `endContract`'s
  legitimate write path.

**Decided at the gate (2026-10-20):**
- **`IllegalArgumentException`, not a new exception type.** `ContractNotOpenException` already
  reads "this contract has ended" for the *result* of an open-after-ended sequence. This guard
  reads "the caller passed an ended `Contract` to `open`", which the standard type names correctly
  without a new domain concept.
- **`require` instead of `Contract.init`.** `Contract`'s constructor already enforces
  `!endDate.isBefore(startDate)`; adding a third guarded invariant there is a second place that
  changes which kinds of misuse throw. The guard at `open` is the one method whose job is to
  refuse contracts that should not be open.
- **No migration.** The fix prevents a future bug; existing data carries no rows produced by
  this path.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| OPEN_OPEN | an `open()` call with `Contract.isOpen == true` | the existing path | unchanged |
| OPEN_ENDED | an `open()` call with `Contract.isOpen == false` | refused; nothing written | `IllegalArgumentException("contract ... cannot be opened: endDate is set")` |
| ACTIVE_RACE | two `open()` calls for the same renter or vehicle, both with `isOpen == true`, both passing the new guard | unchanged — the indexes still settle it, and now the indexes can reject a wider set because the new guard caught what would otherwise have slipped through | `ContractAlreadyActiveException` (unchanged) |

</frozen-after-approval>

## Code Map

- `app/src/main/kotlin/net/klinik/rental/data/repository/ContractRepository.kt:152` — `open`. The
  existing `requirePositiveRate(contract.dailyRate)` is on `:153`; add `require(contract.isOpen)`
  immediately after, with a message naming the contract id and the property. Update the
  `@throws` block at `:38-40` to add `IllegalArgumentException` and explain why it isn't
  `ContractNotOpenException`.
- `domain/src/main/kotlin/net/klinik/rental/domain/model/Contract.kt:103-117` — the constructor's
  KDoc on its invariants. Add one sentence naming `isOpen` as the only derivation of "still
  running", with a pointer to `ContractRepository.open`'s new guard.
- `app/src/test/kotlin/net/klinik/rental/ui/screens/kontrak/ContractRepositoryTest.kt:75-90` — the
  suite already uses `@Test fun open(...)` paths; add `OPEN_ENDED` beside `END_BEFORE_START` and
  `ALREADY_ACTIVE`. The contract helper at `:1435+` currently takes `id = CONTRACT_ID, startDate
  = ..., dailyRate = 250_000`, no `endDate`; extend it with an `endDate: LocalDate? = null`
  default and use it in the new test.

## Tasks & Acceptance

**Execution:**
- [ ] `app/.../data/repository/ContractRepository.kt` — `require(contract.isOpen)` beside the rate
      guard, with a `@throws IllegalArgumentException` entry and the rationale why it is not the
      sibling type.
- [ ] `domain/.../model/Contract.kt` — KDoc sentence pointing at `open`'s new guard.
- [ ] `app/src/test/.../ContractRepositoryTest.kt` — new `OPEN_ENDED`: build an ended `Contract`,
      call `open`, assert the require fires and nothing was written; then assert the success
      half (an `isOpen == true` contract inserts cleanly) so a regression that broke both halves
      cannot pass.

**Acceptance Criteria:**
- Given a `Contract` whose `endDate` is not null, when `open` is called, then no row is written.
- Given a `Contract` whose `endDate` is null, when `open` is called, then the existing path runs
  and the rate/unique-index guards are unchanged.
- Given the app builds, then `:domain:test`, `:app:testDebugUnitTest` and `:app:assembleDebug` pass.

## Implementation Notes

## Spec Change Log

## Review Triage Log

## Verification

**Commands:**
- `./gradlew :domain:test :app:testDebugUnitTest :app:assembleDebug` -- expected: SUCCESS

**Manual checks (a device is required):**
- Open Kontrak, save — the form still completes; the rejected path is only reachable through a
  second caller that has a build-your-own-`Contract` path the app does not expose, so a device
  run would never exercise the new throw. A JVM test that constructs an ended `Contract` is the
  whole verification.
