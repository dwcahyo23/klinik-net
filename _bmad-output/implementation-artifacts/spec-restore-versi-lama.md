---
title: 'A v8 backup still restores'
type: 'feature'
created: '2026-10-21'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: 'bbce6c3'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `BackupJsonReader.read` refuses a `schemaVersion` that is not the app's, in **both**
directions (`BackupJsonReader.kt:164`). The `avatar` column bumped the schema to v9, so every v8
backup became unrestorable — including the `demo-backup.json` this repo ships, until it was
regenerated in the same commit. The next column will do it again, and each time the owner loses
the backups they took before the upgrade.

**Approach:** Accept any file from the current version **down to the oldest version this app has
ever written**, and give each older version the defaults its shape implies.

## Boundaries & Constraints

**Always:**
- **The oldest supported version is a fact about the file format, not a guess.** It is the version
  the *export* shipped at — and the export shipped at v8, so v8 is the floor. A v6 file cannot
  exist, because nothing ever wrote one.
- **A file from the future is still refused.** A future build may have written columns this one
  has no column for, so reading it would silently drop data. The refusal is right; its *message*
  is not, because it does not say which direction it went.
- **An older file's missing columns get the value the schema would have given them.** A v8 renter
  has no avatar, so it restores with `avatar = null` — the same value a v9 renter who never
  picked one has. Not a fabricated one.

**Never:**
- Do not accept a version below the export's. If the export ever shipped at v6, that becomes the
  floor; until then it is v8, and a floor set lower would have to invent rows for columns that
  never existed.
- Do not loosen the *shape* checks. A v9 file with a missing `payments` table is still refused —
  the version only decides which **columns** may be absent, never which **tables** must be there.
- Do not change the writer. It writes the current version, which is the whole point.

**Decided at the gate (2026-10-21):**

- **The floor is v8, and the ledger's premise was wrong.** The deferred entry said "a v6 file has
  no `payment.status` at all, so accepting one means per-version defaults". That is a fact about
  the *database* migration, not about the *file* format — and the file format never carried a v6.
  `git log -S "appendField"` on the writer shows exactly one commit, `201c9ed`, which introduced
  the whole format at `SCHEMA_VERSION = 8`; the two later commits that touched the file added
  only the `NEVER_EXPORTED` list. So the entire history of backup files is v8, and v8 differs from
  v9 by **one column**: `renter.avatar`.
- **One column, so the mechanism is one optional field, not a table of defaults.** The reader
  already has `nullableString`, which returns `null` for a JSON `null`; it needs one that also
  returns `null` for an **absent** key, and that is the whole change. A per-version default
  table would be a second source of truth about the file's shape, and the shape is already
  written down by the writer it pairs with.
- **The version check becomes a range, and the message names the direction.** `version >
  expected` is a future file and is refused as un-restorable — "the app that wrote it is newer
  than this one". `version < floor` is a file too old to read, refused as such. Between the two
  is accepted.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| CURRENT | a v9 file | restores, every field read as written | N/A |
| ONE_VERSION_OLD | a v8 file — no `renter.avatar` key | restores; every renter gets `avatar = null` and their disc falls back to initials | N/A |
| FUTURE | a v10 file | refused, and the message says the file is **newer** than the app | `RestoreRejected(REASON_VERSION_TOO_NEW)` |
| TOO_OLD | a v7 file — one this app never wrote | refused, and the message says the file is **older** than the app can read | `RestoreRejected(REASON_VERSION_TOO_OLD)` |
| NO_VERSION | a file with no `schemaVersion` at all | refused | `RestoreRejected(REASON_NO_VERSION)` |
| SHAPE_STILL_ENFORCED | a v8 file missing the `payments` table | refused — a version decides which *columns* may be absent, never which *tables* must be | `RestoreRejected(REASON_MISSING_TABLE)` |
| MISSING_COLUMN_IN_CURRENT | a v9 file with no `renter.avatar` key | refused — a *current* file that omits a column it must carry is a corrupt file | `RestoreRejected(REASON_BAD_ROW)` |

</frozen-after-approval>

## Code Map

- `app/src/main/kotlin/net/klinik/rental/data/backup/BackupJsonReader.kt:164` — the version check.
  Becomes a range check against a floor, with two distinct reasons.
- `app/src/main/kotlin/net/klinik/rental/data/backup/BackupJsonReader.kt:328` — `nullableString`.
  Needs one sibling that treats an **absent** key the same as a JSON `null`, used only for the
  columns a version may lack.
- `app/src/main/kotlin/net/klinik/rental/data/backup/BackupJsonReader.kt:238` — `renter`, the only
  reader with a column that a later version added.
- `app/src/main/kotlin/net/klinik/rental/ui/strings/RStrings.kt:679` — the `when` over reasons; it
  gains an arm per new reason, and the version message splits by direction.
- `app/src/main/res/values/strings.xml:997-1001` — the refusal sentences.
- `app/src/test/kotlin/net/klinik/rental/data/backup/BackupJsonReaderTest.kt` — the version tests.

## Tasks & Acceptance

**Execution:**
- [x] `BackupJsonReader.kt` — the version range check, the two reasons, and the "absent means
      null" reader for the one column a version may lack.
- [x] `RStrings.kt` + `strings.xml` — one sentence per direction, so the owner knows whether to
      upgrade the app or to re-take the backup.
- [x] Tests — a v8 file restores; a v9 file with a missing column is still refused; a future file
      and a too-old file each get their own reason.

**Acceptance Criteria:**
- Given a v8 backup, when it is restored, then every renter, contract, deposit and figure
  restores, and the renters' discs show initials.
- Given a v9 backup with a column missing, when it is restored, then it is refused — an older
  version may lack a column, a current one may not.
- Given the app builds, then `:domain:test`, `:app:testDebugUnitTest` and `:app:assembleDebug` pass.

## Implementation Notes

- `BackupJsonReader` now accepts files with `schemaVersion` between `OLDEST_READABLE_VERSION` (8) and `expectedVersion` (9).
- Versions `> expectedVersion` fail with `REASON_VERSION_TOO_NEW`.
- Versions `< OLDEST_READABLE_VERSION` fail with `REASON_VERSION_TOO_OLD`.
- Added `requiredNullableString` on `JSONObject` which ensures the key exists (allowing `null` as JSON null value) so current schema versions cannot omit columns, while older versions can default absent columns (e.g. `avatar = null` for v8).
- `BackupJsonReaderTest` verifies with an actual v8 fixture from git history (`git show 4299bba:demo-backup.json`) that v8 restores completely and renters get `avatar = null`.
- `BackupLockInterplayTest` updated to test `REASON_VERSION_TOO_NEW` and `REASON_VERSION_TOO_OLD`.

## Spec Change Log

## Review Triage Log

## Verification

**Commands:**
- `./gradlew :domain:test :app:testDebugUnitTest :app:assembleDebug` -- expected: SUCCESS
- `git grep -n "SCHEMA_VERSION = " app/src/main/kotlin/net/klinik/rental/data/db/KlinikDatabase.kt`
  -- expected: 9

**Manual checks (a device is required):**
- Restore the v8 `demo-backup.json` from the repository's history on a device. Not run — no device.
