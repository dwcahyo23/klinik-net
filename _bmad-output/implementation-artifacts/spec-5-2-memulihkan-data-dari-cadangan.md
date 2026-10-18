---
title: 'Story 5.2 — Memulihkan data dari cadangan'
type: 'feature'
created: '2026-10-19'
status: 'in-progress'
baseline_revision: '201c9ed9e61a936f2b60f9da2daa14f99086baad'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-5-context.md'
warnings:
  - oversized
---

<intent-contract>

## Intent

**Problem:** Story 5.1 gave the owner a file. This story is the other half of FR-16, and it is the
half that makes the file worth having: *"sampai Epic 5 selesai, aplikasi adalah satu-satunya salinan
catatan uang Andi tanpa jalan keluar"* stops being true only when the file can be read back. A
backup you cannot restore is a receipt, not an exit.

It is also the most dangerous operation this app will ever perform. Every other destructive action
in Epic 3 asks through a dialog and then undoes one row; this one **replaces everything**, and the
failure modes run from "the owner picked last month's file" to "a parse bug silently returns an
empty array and the app deletes his records and says it worked."

**Approach:** Pick a file with `OpenDocument`, parse it strictly, show what is inside **before**
anything is touched, and — only on an explicit confirm — replace the whole database in one
transaction.

## Boundaries & Constraints

**Always:**
- **The preview comes before the confirm, and cancelling changes nothing.** The AC names the
  shape: `18 mobil · 14 penyewa · 312 setoran` and the last period. Picking the wrong backup is
  the *normal* case, not an edge case, and it must cost nothing.
- **Everything is one transaction.** `clearAllTables()` then eight inserts inside one
  `withTransaction`. A half-restored database is worse than an untouched one: it reads like a
  fresh account holding half the owner's records, which is precisely the failure Epic 2's
  four-state rule forbids.
- **Every insert order is derived from the foreign keys, not from the entity list.** vehicle,
  renter, business_account → contract → service_exception → payment → allocation → settings.
  `allocation.contractId` has **no** FK to `contract` — it is a plain column — so it does not
  constrain the order and the reader must not assume it does.
- **A file whose `schemaVersion` is not the app's is refused before anything is written**, with a
  message naming both versions. Refusing is free; the failure mode of guessing is a database
  half-converted into a shape nobody can read.
- **A missing or malformed field is an error, never an empty list.** This is the whole reason the
  reader does not use `optJSONArray` anywhere: a table key that returns an empty array instead of
  throwing turns a corrupt file into a successful wipe. "No vehicles" and "the vehicles key is
  missing" are different facts and only one of them is a decision the app may make.
- **The restore writes `NEVER_EXPORTED` keys to nothing.** The file does not carry them; the
  restore does not invent them. After a restore the app is **not locked**, which is the accepted
  consequence the epic states plainly.

**Never:**
- **No partial restore.** There is no "restore only vehicles" and no per-table checkbox. A
  database restored from half a file is a database whose totals are a lie.
- **No re-derivation of allocations.** `previewAllocation` replays against *today's* cutoff; the
  stored rows are inserted as they stand. This is the cost Story 5.1 accepted and this story pays.
- **No CSV, no import of a partially-hand-edited file, no "merge" mode.** One format, whole file.
- **No attempt to repair a file.** A backup that fails to parse is refused with its reason. What
  the app could repair it *from* is the current database, which the restore is about to replace.
- **No automatic restore on app start**, and no "your data looks incomplete" repair pass. Story
  5.2's own AC calls the empty-looks-like-a-new-account failure out by name; inventing a detector
  for it here would be the failure.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| RESTORE_PICK | "Pulihkan dari cadangan" tapped | `OpenDocument` opens, filtered to JSON | Cancelling writes nothing and shows nothing |
| RESTORE_PARSE | A file this app wrote | Preview: five counts + the last payment date | N/A |
| RESTORE_CANCEL | Preview shown, owner cancels | Nothing changes; every table byte-identical | N/A |
| RESTORE_OK | Preview confirmed | All eight tables replaced in one transaction; success snackbar | N/A |
| RESTORE_NEWER | `schemaVersion` above the app's | Refused, naming both versions; **nothing deleted** | `pengaturan_restore_error_version` |
| RESTORE_OLDER | `schemaVersion` below the app's | Refused the same way — migration is Story 5.6 | Same message |
| RESTORE_MISSING_KEY | A file with `vehicles` absent | Refused; nothing deleted | `pengaturan_restore_error_corrupt` |
| RESTORE_NOT_JSON | A file that is not JSON at all | Refused; nothing deleted | Same |
| RESTORE_GUARD | A row the entity's `init` would reject | Refused naming the table; nothing deleted | Same |
| RESTORE_PIN | A file with a `pinHash` key in `settings` | Ignored on read **and** not written back; app unlocked after | N/A |
| RESTORE_EMPTY | A valid file with empty arrays | Every table becomes empty; the screen shows its empty state, never an error | N/A |
| RESTORE_INTEGRITY | Restore finished, then any screen reads | The stored allocation still sums to its payment's amount | N/A |

</intent-contract>

## Decisions taken at the gate

- **`org.json` for reading, after rejecting it for writing.** The two calls point opposite ways
  and the spec should say why. The writer rejected it because `JSONObject` has no key order (so
  the same database would export two different files) and because `isReturnDefaultValues` makes
  its `put` a silent no-op on a plain JVM test. **Neither applies to reading**: key order is
  irrelevant for input, and Robolectric supplies a real `org.json`. It is already in the framework,
  so it is rung 4 of the ladder — a dependency already installed beats a parser written to read a
  document a hand-written writer produced, which would be a second implementation of the same
  grammar. What it *does* provide is the property this story most needs: `getJSONArray` throws on
  a missing key and `optJSONArray` does not, so the strict/strict boundary is the library's own
  API shape rather than a discipline someone has to maintain. **`optJSONArray` appears nowhere in
  the reader.**
- **`clearAllTables()` rather than eight `DELETE` queries.** The foreign keys are `RESTRICT`, which
  sounds like it makes a wholesale delete impossible — it does not: SQLite only checks a
  constraint for rows actually modified, and `DELETE FROM t` removes them without checking, which
  is exactly why Room's own cascade bookkeeping runs as a separate set of statements. Eight new
  DAO queries "for safety" would be eight methods with a real ordering requirement and no
  requirement at all.
- **The whole restore is `clearAllTables()` plus eight inserts inside one `withTransaction`.**
  Room's `beginTransaction` is re-entrant, so `clearAllTables()`'s own transaction nests inside
  it rather than colliding with it, and the result is one atomic replacement rather than a window
  in which the owner's database is empty.
- **Older files are refused, not migrated.** Story 5.6's AC asks for migration from an older
  schema and no backup older than v8 can exist yet — Story 5.1 is the first thing in this app that
  ever wrote one. Refusing both directions with one message is the honest interim answer; 5.6
  changes the lower half of that comparison and nothing else.
- **The PIN prompt is deferred to Story 5.3, and the mechanism ships here.** 5.3 is what creates
  `pinEnabled`; there is nothing for 5.2 to set to `false`. What 5.2 ships instead is the
  guarantee that a restored `settings` table contains **no** key from `NEVER_EXPORTED` — tested by
  a file that carries one — so that when 5.3 lands there is nothing for it to remember.

## Code Map

**Read-only evidence — do not modify:**
- `app/.../data/backup/BackupJson.kt` — the writer, `NEVER_EXPORTED`, and the eight shapes. **This
  is the format contract.** Its KDoc states two things this story must not contradict: `org.json`
  was rejected *for writing*, and the file's key names are the API.
- `app/.../data/db/KlinikDatabase.kt:87` — `abstract class KlinikDatabase : RoomDatabase()`.
  `clearAllTables()` exists as the inherited member and **is called from nowhere in the repo**;
  this story is its first caller, and that is worth saying in its KDoc rather than leaving a
  reader to wonder whether it is safe.
- `app/.../data/db/entity/*.kt` — the eight `init` blocks. Each is a guard a restored row must
  satisfy, and four of them reject something a plain JSON reader would otherwise pass through:
  `VehicleEntity` refuses an un-normalised plate, `ContractEntity` refuses an `activeVehicleId`
  that disagrees with `endDate`, `PaymentEntity` refuses a cancelled deposit with no reason,
  `RenterEntity` refuses a blank phone.
- `app/.../data/repository/PaymentRepository.kt:978` and `ContractRepository.kt:588` — the
  `inTransaction { database?.withTransaction { it } ?: it() }` idiom, duplicated in five
  repositories. **This story follows it rather than calling `withTransaction` directly**, which is
  also what lets its ViewModel test build the repository with no database handle.
- `app/.../ui/shell/NavGraph.kt:566-589` — `PengaturanRoute`'s launcher parameter and the
  `Screens.Pengaturan` members. **17, none defaulted**; the restore adds four more and a second
  launcher, and the second launcher belongs on the route for the reason the first one does.
- `app/.../ui/components/Feedback.kt:505-516` — `KnModal`, which **unmounts** when not visible and
  renders its footer only when a label *and* a handler are both present.
- `app/.../ui/screens/setoran/CancelDepositDialog.kt:52-72` — the `KnModal` call to copy.

**New files:**
- `app/.../data/backup/BackupJsonReader.kt` — strict `org.json` → the eight entity lists, plus
  `RestorePreview` (the counts and the last period).
- `app/.../data/backup/BackupRestorer.kt` — the transaction, the insert order, the version gate.
- `app/.../ui/screens/pengaturan/RestorePreviewDialog.kt` — preview + confirm, over `KnModal`.
- `app/.../ui/backup/BackupFileReader.kt` — the one `ContentResolver` read, beside
  `BackupFileWriter` because both are the framework seam.

**Modified:**
- `app/.../ui/screens/pengaturan/*` — the restore control, the preview state, five callbacks.
- `app/.../ui/shell/NavGraph.kt`, `MainActivity.kt`, `strings.xml`, `RStrings.kt`.
- `PengaturanScreen.kt`'s backup card gains the restore action and **loses** the "not available yet"
  line it has carried since Story 5.1.

**Gotchas carried over:**
- `KlinikNetTheme` is `staticCompositionLocalOf`; one `setContent` per Compose test.
- `KnModal` unmounts, so `assertDoesNotExist` is the assertion for "the preview is gone".
- A ViewModel test must build its repository with **no** database handle, or `withTransaction`
  throws on `StandardTestDispatcher`'s thread.
- Every new `Screens.*` callback is **required, not defaulted**.

## Tasks & Acceptance

**Execution:**
- [x] `app/.../data/backup/BackupJsonReader.kt` — strict parse of all eight tables, `schemaVersion`,
      `RestorePreview`, `RestoreRejected`.
- [ ] `app/.../data/backup/BackupRestorer.kt` — the version gate, `clearAllTables()`, the FK-ordered
      inserts, one transaction.
- [ ] `app/.../ui/backup/BackupFileReader.kt` — `ContentResolver.openInputStream`.
- [ ] `app/.../ui/screens/pengaturan/RestorePreviewDialog.kt` — counts, last period, confirm.
- [ ] `app/.../ui/screens/pengaturan/*` — the action, the state, the callbacks.
- [ ] `app/.../ui/shell/NavGraph.kt`, `MainActivity.kt`, `strings.xml`, `RStrings.kt`.
- [x] `app/src/test/.../backup/BackupJsonReaderTest.kt` — **new, 13 cases**: every table key required,
      an empty table *not* refused (the two are opposite facts and the suite proves it), newer and
      older versions refused, a malformed row refused naming its table, a row the entity guards
      reject refused rather than repaired, `pinHash` dropped on read.
- [ ] `app/src/test/.../backup/BackupRestorerTest.kt` — **new, against a real Room database**: the
      whole matrix, `Σ allocation == amount` after a restore, the PIN key ignored.
- [ ] `app/src/test/.../backup/ExportRestoreRoundTripTest.kt` — **new**: build a database, export it,
      restore it into a second one, and assert the two are identical. The test that proves the two
      halves of FR-16 agree, which neither can prove alone.
- [ ] `app/src/test/.../pengaturan/PengaturanScreenTest.kt` — the preview, the confirm, the cancel.

**Acceptance Criteria:**
- Given the restore action is tapped, then the system file picker opens, filtered to JSON
- Given a valid file, then a preview shows vehicle, renter, contract and deposit counts and the
  last period **before** anything is written
- Given the preview is cancelled, then every table is byte-identical to what it was
- Given the preview is confirmed, then all eight tables are replaced, including the deposit history
- Given a file whose `schemaVersion` differs from the app's, then it is refused naming both versions
  and nothing is deleted
- Given a file missing a table key, then it is refused and nothing is deleted
- Given a row the entity guards would reject, then it is refused naming the table
- Given a restored database, then every payment's allocations still sum to its amount
- Given a file carrying a `pinHash` setting, then it is neither trusted nor written back
- Given the app builds, when tests run, then `:domain:test`, `:app:testDebugUnitTest` and
  `:app:assembleDebug` all pass

## Spec Change Log

### `org.json` is used for reading after being rejected for writing — and the tests say which is which

`BackupJsonTest` (no runner) and `BackupJsonReaderTest` (`RobolectricTestRunner`) sit beside each
other and look like an inconsistency until the reason is read. It is not one: the reader needs the
framework's `org.json`, and on a plain JVM test the stubs would return **defaults rather than
throwing** under `isReturnDefaultValues = true` — which would make "a missing table is refused"
pass without the real library ever running. That file's KDoc says so, because the asymmetry is
otherwise a mystery someone would "clean up" by deleting the runner.

### The strictness is proven by mutation, not by having been written carefully

`optJSONArray` was substituted for `getJSONArray` and the empty-array fallback, and **two** tests
failed — `a file missing a table key is refused, not read as empty` and `every table key is
required`. Reverted. This is the one property in the story that a future "let me be lenient about
old backups" change would silently undo, and the mutation is what makes the next such change a
test failure rather than a data-loss incident.

### Two test bugs found by their own failure messages

Both fixture failures, both worth recording because the pattern recurs: a `String.replace` on a
multi-line literal stopped matching when `trimIndent()` changed the indentation, so the fixture
**silently changed nothing** and the test asserted something other than what it claimed. The fix is
`emptyTable`/`withoutTable` helpers that **assert they actually modified the text** before
returning it — a fixture that no longer matches its target should fail loudly rather than make a
test vacuous.

The second was worse: the "no last period" test had been built on `withoutTable("payments")`,
which *removes* the key — and the reader correctly refused it. The reader was right and the test
was wrong. The distinction that fixed it is now the point of two separate tests: **an empty array
is a fact about the owner's business; an absent key is a corrupt file.**

### `clearAllTables()` was almost overridden with an empty body

The first attempt documented it by writing `override fun clearAllTables() = super.clearAllTables()`,
which does not compile — the member is abstract on `RoomDatabase`. The second attempt overrode it
with an **empty body**, which compiles and does **nothing**: a restore would report success having
restored nothing, which is the exact failure this story is about. Reverted; the explanation lives
in the spec's Design Notes instead of in a method that must not be called twice.

## Review Triage Log

## Auto Run Result

## Design Notes

**The dangerous failure is not a corrupt file — it is a file the parser reads as empty.** Every
shape of a bad restore that actually costs the owner his records looks like this: the reader
returns zero vehicles, the restore succeeds, and the settings screen shows a brand-new empty
account. That is indistinguishable from a successful first run, which is the one thing Epic 2's
four-state rule was written to prevent. Every design decision below follows from it — the strict
accessors, the preview before the write, one transaction, and the refusal of a version mismatch
rather than a best-effort conversion.

**`RESTRICT` on every foreign key does not make a wholesale delete impossible.** It is worth
writing down because it reads the other way and the instinct is to add per-table deletes in
dependency order. SQLite evaluates a constraint per row actually modified; `DELETE FROM t` never
modifies a row, so nothing is checked — which is the same mechanism Room's own cascade bookkeeping
relies on when it drops orphans after a parent delete. `clearAllTables()` is therefore both
sufficient and the shortest correct thing.

**The round-trip test is the one that matters.** `BackupJsonTest` proves the writer's output is
well-formed; `BackupRestorerTest` proves the reader handles well-formed input. Neither proves they
agree — and that disagreement is a backup file that exports perfectly and restores to nothing. A
test that writes a database, exports it, restores it into a second database, and compares all
eight tables is the only thing in this story that would catch it.

**Verification**

- `./gradlew :domain:test` -- expected: SUCCESS
- `./gradlew :app:testDebugUnitTest` -- expected: SUCCESS
- `./gradlew :app:assembleDebug` -- expected: SUCCESS
- `git grep -c "optJSONArray" app/src/main` -- expected: **0**. The strict/strict boundary is
  load-bearing and a grep is cheaper than a review.