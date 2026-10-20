---
title: 'Settling a contract through a future date hides arrears'
type: 'bugfix'
created: '2026-10-20'
status: 'in-review'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: 'c947b1ed417ecf0c192a05db1449573685d86767'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/spec-kontrak-tanggal-akhir-di-masa-depan.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `settleThrough(contractId, date)` refuses only `date < startDate`
(`ContractRepository.kt:433-438`), so it accepts a date after today. That marker is not inert:
`settledDays` filters every charge day `<= settledThroughDate` (`domain/.../Settlement.kt:30`), so
settling through 24 Oct on 17 Oct closes **today's** arrears too and hides rupiah the owner has not
been paid. FR-9b's stated purpose is the opposite — marking obligations *"sudah tercatat di tempat
lain"* (`prd.md:254`) — which cannot be true of days that have not happened. `spec-3-6` dismissed this
as unreachable because "the picker only offers past dates"; that is false in the code: all three
sheets share `ContractDateField`, which binds no `selectableDates`.

**Approach:** Refuse a settlement date after today, the same shape as `endContract`'s guard landed
yesterday: an `asOf` parameter, a named exception, and a message on the date field naming the day it
compared against. The two paths are siblings and should read as such.

## Boundaries & Constraints

**Always:**
- **The refusal names the day it compared against.** Same reason as the end-date guard: the one figure
  the owner cannot otherwise see, and the only way to notice a wrong device clock.
- **A past date stays accepted** — that is FR-9b's whole use case, and several tests rely on it.
- **Nothing is written when it throws**, inside the existing `inTransaction`.
- **The exception's message must not claim `asOf` is today.** The end-date guard's first review
  finding was exactly this: `asOf` is a caller-supplied parameter, so the type cannot know.

**Never:**
- Do not bound `ContractDateField` with `selectableDates`. It feeds the contract **start** date too,
  and `open()` accepts a future start date — a `max(today)` here would make future-start contracts
  unrecordable. The guard belongs in the repository, which is where the end-date guard lives.
- Do not change `chargeDaysIn`'s `it <= asOf` filter or `settledDays`. They are correct; the bug is a
  write that stores a date they cannot honour.
- Do not touch `BackupJsonReader`'s restore path, which has the same gap for `endDate`. It is
  recorded separately in `deferred-work.md` and is a different contract.

**Decided at the gate (2026-10-20):**
- **Refuse; do not clamp.** Clamping to today would silently settle days the owner never named,
  which is the same failure `endContract` refuses to commit.
- **Reuse `ContractEndInFutureException`'s shape, not the type.** The two live in different layers —
  settlement's own exceptions sit in `data/repository/Settlement.kt` beside `SettlementImpact`, not in
  `domain/validation/`. A settlement exception belongs with its siblings.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| TODAY | open contract, `date == asOf` | settles through today | N/A |
| PAST | open contract, `date < asOf` | settles | N/A |
| FUTURE | open contract, `date == asOf + 1 day` | refused; marker unchanged; arrears untouched | `SettlementInFutureException(asOf)` |
| FUTURE_FAR | open contract, `date == asOf + 6 weeks` | same refusal | same |
| BEFORE_START | `date < startDate` (unchanged) | refused | `SettlementBeforeStartException` |
| ALREADY_SETTLED | marker already set and `date` earlier (unchanged) | refused | `SettlementAlreadySettledException` |
| ORDER | future **and** before start | `SettlementBeforeStartException` first | a start date is inert and knowable; "too far ahead" would be a second complaint about one field |
| NOT_STARTED | contract whose start is in the future | refused both ways, by the two checks in order | see the sibling spec's `NOT_STARTED` row — the same impossibility, and for the same reason |

</frozen-after-approval>

## Code Map

- `app/src/main/kotlin/net/klinik/rental/data/repository/ContractRepository.kt:439-470` — `settleThrough`.
  Guard goes beside the before-start check at `:445`, before `dao.update` at `:461`. Update the method's
  `@throws` block and the class KDoc's exception list.
- `app/src/main/kotlin/net/klinik/rental/data/repository/Settlement.kt:5-51` — where the new exception
  belongs, beside `SettlementBeforeStartException` and `SettlementAlreadySettledException`. Copy their
  shape: `IllegalStateException`, the fields the message needs, one sentence.
- `app/src/main/kotlin/net/klinik/rental/domain/calculate/Settlement.kt:25-31` — `settledDays`, the
  filter a future marker abuses. Read it to write the KDoc; **do not change it**.
- `app/src/main/kotlin/net/klinik/rental/ui/screens/kontrak/KontrakViewModel.kt:1125-1175` — the caller.
  Add a catch beside `SettlementBeforeStartException` at `:1153`; it writes `dateError` on the live
  sheet, else the sheet banner. `settleContract` already guards the success path with
  `live === current` at `:1141` — the new catch must use the same identity check, the finding the end-
  date guard's review caught.
- `app/src/main/kotlin/net/klinik/rental/ui/strings/RStrings.kt` — `kontrakErrorSettleBeforeStart`, the
  shape to mirror. It takes a `LocalDate`; use `formatTanggalBulanTahun` (added by the end-date spec),
  not `formatExceptionDate`, which is why the year must be in this message too.
- `app/src/main/res/values/strings.xml` — the `kontrak_error_settle_*` family to extend.
- `app/src/test/kotlin/net/klinik/rental/ui/screens/setoran/SettleContractTest.kt:56` — `asOf` is
  `2026-10-17`. Fourteen `settleThrough` calls; `:101` and `:349` pass **24 Oct**, and `:369`, `:467`,
  `:489` pass **20 Oct** — all after `asOf`. Those fixtures must move, and the two that were testing
  "settle the whole period" need a different end date, not a moved clock.

## Tasks & Acceptance

**Execution:**
- [ ] `app/.../data/repository/Settlement.kt` — new `SettlementInFutureException(contractId, asOf)`.
- [ ] `app/.../data/repository/ContractRepository.kt` — `settleThrough` gains `asOf: LocalDate`, and
      after the before-start check refuses `date.isAfter(asOf)`; `@throws` states which wins.
- [ ] `app/.../ui/screens/kontrak/KontrakViewModel.kt` — pass `asOf = LocalDate.now(clock)`; catch the
      new exception with the `live === current` identity check the success path already uses.
- [ ] `app/.../ui/strings/RStrings.kt` + `res/values/strings.xml` — `kontrakErrorSettleInFuture(asOf)`.
- [ ] `app/src/test/.../SettleContractTest.kt` — add `FUTURE` and `FUTURE_FAR`; move the five fixtures
      that sit after `asOf`, keeping each test's subject intact.
- [ ] `app/src/test/.../KontrakViewModelTest.kt` or a settle-suite equivalent — one case: a future date
      shows the error on the date field, the contract stays unsettled, and arrears are unchanged.

**Acceptance Criteria:**
- Given a contract with open arrears, when a future date is settled, then arrears are unchanged and the
  date field carries a message naming today.
- Given a past date, when settled, then arrears close exactly as before.
- Given a date that is both future and before the start, then the message is still the start-date one.
- Given the app builds, then `:domain:test`, `:app:testDebugUnitTest` and `:app:assembleDebug` pass.

## Implementation Notes

## Spec Change Log

## Review Triage Log

## Verification

**Commands:**
- `./gradlew :domain:test :app:testDebugUnitTest :app:assembleDebug` -- expected: SUCCESS
- `git grep -n "settleThrough(" -- app/src/main app/src/test` -- expected: every call site passes
  `asOf`, and the repository holds no bare `LocalDate.now()`.

**Manual checks (a device is required):**
- Settle sheet: pick tomorrow, confirm, then confirm the arrears figure on Tunggakan is unchanged.
