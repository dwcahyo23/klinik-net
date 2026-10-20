---
title: 'A restored backup can bypass both future-date guards'
type: 'bugfix'
created: '2026-10-21'
status: 'in-progress'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: '4a2dce8'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `endContract` and `settleThrough` both refuse a date after today, because
`Contract.isOpen` is `endDate == null` and `settledDays` closes every charge day at or before its
marker. `BackupJsonReader.contract` reads `endDate` and `settledThroughDate` straight from JSON
(`:220-221`), so restore is a **third writer** that bypasses both. A backup written by a build before
those guards existed — or on a device whose clock was ahead — carries exactly the rows they now
reject, and restoring it puts the app back into the state the guards prevent: a car reading
`tersedia` for a rental still running, or arrears gone with nobody having paid.

**Approach:** The reader refuses such a file, with its own reason and its own sentence. The refusal
is the reader's existing shape — `RestoreRejected` with a reason the UI turns into a message — and
`asOf` arrives as a parameter, the same discipline `endContract` and `settleThrough` follow.

## Boundaries & Constraints

**Always:**
- **Refuse the file; never repair it.** Dropping the row would restore a database missing a
  contract the owner never deleted. Nulling the future date would be the reader inventing data,
  which the class KDoc forbids outright.
- **`asOf` is a parameter, never `LocalDate.now()` inside the reader.** AD-8, and the refusal
  depends on the day, so a test must be able to pin it.
- **Both dates are checked.** An end date frees the car; a settlement marker hides the arrears.
  Different harms, one rule.
- **Today itself is accepted.** `isAfter`, not `!isBefore`: a contract that ended today is real,
  and a marker of today is the ordinary settlement.

**Never:**
- Do not change `BackupJsonWriter`. It exports whatever the database holds, and the database can
  no longer hold a future date after this change and the two write guards.
- Do not add a version bump. The file shape is unchanged; only which files are accepted is.
- Do not touch `RestorePreview`. The counts are right; the file never gets far enough to be
  previewed.

**Decided at the gate (2026-10-21):**

- **Refuse, and accept that this costs an owner their file.** This is the part worth naming
  plainly. An owner holding a backup written before the guards existed, containing a contract that
  ended in the future, cannot restore it with this build. That is a real loss.

  The alternative is worse, and specifically so: a restored future end date has **no in-app fix**.
  `endContract` only accepts an open contract, and there is no "clear the end date" operation, so
  the app would sit with a car reading available for a rental still running and no way to correct
  it. Silent and unfixable beats refused-and-explained. The message says an earlier backup is the
  way back, because that is the only true advice.
- **One reason, not two.** `REASON_FUTURE_DATE` covers both dates. Two reasons would produce two
  sentences that differ only in which column was wrong — a distinction the owner cannot act on,
  which is the same argument `RStrings` already makes for `NOT_JSON` and `NO_VERSION` sharing a
  sentence.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| END_FUTURE | contract with `endDate` after `asOf` | whole file refused | `RestoreRejected(REASON_FUTURE_DATE, table = "contracts")` |
| SETTLE_FUTURE | contract with `settledThroughDate` after `asOf` | whole file refused | same reason and table |
| BOTH | both dates after `asOf` | refused once, by the end-date check | same |
| TODAY | `endDate == asOf` or marker `== asOf` | accepted; the row restores | N/A |
| PAST | both dates before `asOf` | accepted, unchanged | N/A |
| NULL | both dates null (the ordinary open contract) | accepted, unchanged | N/A |

</frozen-after-approval>

## Code Map

- `app/src/main/kotlin/net/klinik/rental/data/backup/BackupJsonReader.kt` — `REASON_FUTURE_DATE`
  beside the other five reasons; `read(json, expectedVersion, asOf)`; `contract(row, asOf)` checks
  both dates before building the entity, and passes the already-parsed values into it rather than
  parsing twice.
- `app/src/main/kotlin/net/klinik/rental/data/backup/BackupRestorer.kt` — `parse(json, asOf)`
  forwards to the reader. No clock of its own.
- `app/src/main/kotlin/net/klinik/rental/ui/screens/pengaturan/PengaturanViewModel.kt:856` — the
  only production caller. It already holds a nullable `clock`; `LocalDate.now(clock)` is the same
  idiom used at `:1178`. An absent clock reports the file unreadable rather than restoring
  unchecked, because a reader that guessed "is this date in the future" would be worse than one
  that refuses.
- `app/src/main/kotlin/net/klinik/rental/ui/strings/RStrings.kt:690` — the `when` gains a branch.
  Its `else` already catches `NOT_JSON`/`NO_VERSION`; the new reason needs its own arm because its
  next action differs.
- `app/src/main/res/values/strings.xml:1000` — the sentence, beside `pengaturan_restore_error_bad_row`.
- `app/src/test/kotlin/net/klinik/rental/data/backup/BackupJsonReaderTest.kt` — `AS_OF` constant and
  the three new cases; the six existing `read(...)` calls gain `AS_OF`.
- `app/src/test/.../{BackupRestorerTest,BackupLockInterplayTest,ExportRestoreRoundTripTest}.kt` — 18
  `parse(...)` calls gain `AS_OF`; each file already has a companion object.

## Tasks & Acceptance

**Execution:**
- [x] `BackupJsonReader.kt` — `REASON_FUTURE_DATE`, `asOf` on `read`, both checks in `contract`.
- [x] `BackupRestorer.kt` — `parse(json, asOf)`.
- [x] `PengaturanViewModel.kt` — pass the clock's day; absent clock reports unreadable.
- [x] `RStrings.kt` + `strings.xml` — the refusal sentence.
- [x] `BackupJsonReaderTest.kt` — `END_FUTURE`, `SETTLE_FUTURE`, `TODAY`.
- [x] Four test files — `asOf` at every call site.

**Acceptance Criteria:**
- Given a backup whose contract ends after today, when it is chosen, then the preview never
  appears, the banner names the reason, and the database is untouched.
- Given a backup whose contract ends today, when it is chosen, then it restores.
- Given the app builds, then `:domain:test`, `:app:testDebugUnitTest` and `:app:assembleDebug` pass.

## Implementation Notes

## Spec Change Log

## Review Triage Log

## Verification

**Commands:**
- `./gradlew :domain:test :app:testDebugUnitTest :app:assembleDebug` -- expected: SUCCESS
- `git grep -n "BackupJsonReader.read(\|\.parse(" app/src/main app/src/test` -- expected: every
  call site passes `asOf`/`AS_OF`.

**Manual checks (a device is required):**
- Settings → Pulihkan data with a hand-edited file whose `endDate` is next month: the banner
  appears and the account list is unchanged. Not run — no device.
