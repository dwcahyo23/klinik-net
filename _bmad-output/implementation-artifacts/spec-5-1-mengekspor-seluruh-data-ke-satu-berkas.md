---
title: 'Story 5.1 — Mengekspor seluruh data ke satu berkas'
type: 'feature'
created: '2026-10-19'
status: 'done'
baseline_revision: 'f7716d90f23b9bc4c5b602bca6a1fc85ba886826'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-5-context.md'
warnings:
  - oversized
---

<intent-contract>

## Intent

**Problem:** FR-16 says this is *"satu-satunya jalur keluar data dari perangkat"* (the only way data
leaves the device), and until now the app has none. Worse, the epic that was supposed to build that
exit was deliberately scheduled **last**, and until it lands the PRD states plainly: *"sampai Epic 5
selesai, aplikasi adalah satu-satunya salinan catatan uang Andi tanpa jalan keluar."* Every month of
deposits, contracts and payments exists in exactly one SQLite file on one tablet, and a lost or
cracked device takes all of it.

**Approach:** One button in `Pengaturan` that hands the owner the Storage Access Framework's own file
picker — so they choose where the backup lives, and no storage permission is ever requested. What
lands in that file is one JSON document carrying every table, with a `schemaVersion` at its root so
Story 5.2 can refuse a file it does not understand.

## Boundaries & Constraints

**Always:**
- **All eight tables go into the file, not the five the AC names.** The AC lists Kendaraan,
  Penyewa, Kontrak, Setoran and Pengaturan; the database also holds `service_exception` (the owner's
  own "hari tanpa tagihan" marks — Epic 2.6) and `allocation` (Story 1.3's per-day ledger). The
  AC's list is a floor, and Story 5.2's criterion demands *"seluruh data kembali utuh"* — a restore
  that silently dropped the exception marks or the ledger would be exactly the partial recovery
  that criterion forbids.
- **Allocations are copied verbatim, never re-derived.** `saveDeposit` re-derives allocations from
  the deposits *and the current cutoff date*. Restoring through it would replay history under
  whatever cutoff the owner has today, not the one in force when the money arrived — a restored
  backup whose per-day ledger disagrees with the one it was taken from. The backup carries the rows
  as they stand.
- **The PIN hash and the recovery-code hash are excluded by an explicit deny-list, not by memory.**
  Story 5.3 adds them; this story ships the exclusion and the test that proves it, so that story
  cannot forget. NFR-5 and AD-7 both forbid the hash reaching a backup file, and a file the owner
  emails to themselves is precisely where a leaked hash would spread.
- **`schemaVersion` sits at the JSON root and equals `KlinikDatabase`'s version** — one derivation.
  AD-6 and NFR-3 both make this the basis for Story 5.2's "reject a newer file with a message the
  owner can read, not a parse failure that destroys what is already stored."
- **No storage permission, ever.** SAF grants per-file access. The manifest already declares **zero**
  permissions and says why; this story must not change that, and a test asserts it.
- **No network.** NFR-4 and FR-16's "Aplikasi tidak mengirim data ke mana pun". The export is a
  local file write and nothing else.
- **An export that fails says so.** Story 2.8's four-state rule: a control that swallows its
  failure is the defect that repo has shipped most often. Success is a snackbar naming the file
  that was written; failure is the screen's Danger banner.

**Never:**
- **No placeholder "Pulihkan" button.** The prototype's settings screen carries both, and the
  restore is Story 5.2. A disabled restore button is the "renders and is wired to nothing" defect
  in its purest form, and this repo has shipped it repeatedly. The section says the restore is not
  here yet, in words, rather than drawing a control that does nothing.
- **No automatic or scheduled backup.** The epic's own note is explicit: *"keputusan manual, bukan
  otomatis"* — no WorkManager, no daily file the owner may not know exists. Story 5.6 adds the
  reminder instead.
- **No CSV, and no second export format.** The prototype's settings screen has an *"Ekspor Semua
  (CSV)"* button beside its backup button; FR-16 names exactly one file, and Story 5.2 must be able
  to read what this story writes. Two formats would mean two writers and one reader that only
  understands one of them.
- **No counts, no size badge, no "last backup" line.** The prototype shows a database-size badge
  and three entity counters. None of it is asked for, and Story 5.2 needs counts only to *parse*
  a file it is restoring, which it can count from the arrays. Building a summary surface no
  criterion asks for is the speculative layer.
- **No import path, no schema migration of the file format.** Story 5.2.
- **No encryption of the file.** Nothing claims the file is encrypted, and nothing may imply it —
  FR-17 forbids reusing the prototype's *"data tersimpan lokal dan terenkripsi"*.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| EXPORT_PICK | The export action is tapped | The system file picker opens, pre-named `klinik-net-backup-<today>.json` | Cancelling the picker writes nothing and says nothing |
| EXPORT_WRITE | A destination is chosen | One JSON file, all eight tables, written to the chosen location | A failed write raises the Danger banner; no file is claimed to exist |
| EXPORT_VERSIONS | Two exports on different days | Two different filenames | Same-day re-export is SAF's own replace-confirmation, not ours |
| EXPORT_NO_MUTATION | After any export | Every table byte-identical — export only reads | N/A |
| EXPORT_PIN | A `pinHash` / `recoveryHash` row exists | Absent from the file; every other `settings` row present | N/A |
| EXPORT_EMPTY | A fresh install, no rows at all | Eight **empty arrays**, not a missing key — Story 5.2 distinguishes "empty" from "not there" | N/A |
| EXPORT_SPECIALS | A renter named `A "B" \ C`, a note with a newline | Both round-trip exactly through the file | N/A |
| EXPORT_NO_PERMISSION | The manifest is inspected | Zero permissions declared | N/A |
| EXPORT_DOUBLE | The action is tapped twice quickly | The button is disabled while an export is in flight | N/A |
| EXPORT_ROWS | 312 deposits and their allocations | Every one present, cancelled ones with their reason | N/A |

</intent-contract>

## Decisions taken at the gate

- **The JSON writer is hand-written; no serialization dependency is added.** The classpath has
  none, Room entities are not `@Serializable`, and a backup file whose key order is stable is worth
  more to an owner who opens it in a text editor than one whose keys come out in hash order.
  `org.json` was rejected for exactly that reason — plus `isReturnDefaultValues = true` means it
  silently returns defaults on a plain JVM test instead of failing, which would make a
  serialization bug invisible. Story 5.2's **parser** is a different problem and gets its own
  decision; this story only writes.
- **Two new DAO queries, not eight.** Six tables already have a whole-table reader; the two that do
  not — `AllocationDao` and `SettingDao` — get one each. Everything else reuses
  `observeAll().first()`, which is correct because a Room flow emits immediately on subscription.
- **Row order is fixed in the writer, by `id`.** Six of the eight readers have different `ORDER BY`
  clauses and one of them may change for an unrelated feature. Sorting by primary key in the writer
  means the file's content depends on the data alone, so the same database always produces the same
  bytes — which is what makes two backups diffable.
- **Export success reuses the screen's existing snackbar slot; failure reuses its error slot.** The
  screen already has three message slots and its own KDoc complains about them. A fourth pair would
  be a second place the same message could be written, and the existing banner precedence (error
  beats success) is already the right rule for this.
- **The action is a card, placed below the identity block and outside the list's `when`.** The
  screen's own pattern for a labelled section is `IdentitySection`, and its argument applies
  unchanged: whether a backup control is visible must not depend on how many accounts exist.

## Code Map

**Read-only evidence — do not modify:**
- `app/.../data/db/KlinikDatabase.kt:57-70` — eight entities, `version = 8`. **The version becomes a
  named constant this story adds**, because `schemaVersion` in the file and the schema Room migrates
  to must be one number and not two literals that agree today.
- `app/.../data/db/dao/SettingDao.kt:33-77` — `findValue`, `findInt`, `putSetting`, `deleteSetting`.
  **No whole-table read exists at all**, which is why `settings` needs a query here: the AC requires
  "Pengaturan" in the file, and the identity block's three rows are only reachable one key at a time.
- `app/.../data/db/dao/AllocationDao.kt:46-130` — `forContract`, `observeForContract`, `creditRowsFor`,
  `observeContractIds`, `insertAllocation`, `deleteForPayment`, `deleteForContract`. No global reader.
- `app/.../ui/screens/pengaturan/PengaturanScreen.kt:76-104` — 13 parameters, 12 of them required;
  the KDoc at L100-102 states why defaults are banned and names the five test files it has caught.
- `app/.../ui/screens/pengaturan/PengaturanScreen.kt:287-350` — `IdentitySection`, the pattern the
  backup card copies: a private `Surface`, a label heading, and its own action.
- `app/src/main/AndroidManifest.xml:1-27` — zero permissions, and a header comment that already
  states the SAF rationale. **This story must leave it that way, and the test is what says so.**
- `app/.../ui/strings/RStrings.kt:36` and the rule at L540-545 — chrome read in a composable uses
  `stringResource`; only a ViewModel-assembled **message** gets an accessor. The export's result
  message is a message (two ViewModels build it), the section's heading and the action label are
  chrome.

**New files:**
- `app/.../data/backup/BackupJson.kt` — the writer: eight tables to one JSON string, deterministic
  by `id`, with the escaping and the deny-list. Pure JVM, no Android import, so the string can be
  asserted without Robolectric.
- `app/.../data/backup/BackupRepository.kt` — reads the eight DAOs, builds the JSON, returns the
  filename and the text. `suspend`, because it reads.
- `app/.../ui/backup/BackupFileWriter.kt` — the one Android-side function: a `String` and a `Uri` in,
  bytes out. Kept in `ui/` because `data/` must not see `android.net.Uri` (AD-1).

**Modified:**
- `app/.../data/db/dao/AllocationDao.kt`, `SettingDao.kt` — one query each.
- `app/.../data/db/KlinikDatabase.kt` — `SCHEMA_VERSION` as a constant, consumed by the annotation
  and by the writer.
- `app/.../ui/screens/pengaturan/PengaturanScreen.kt`, `PengaturanUiState.kt`,
  `PengaturanViewModel.kt` — the card, `isExporting`, and the three callbacks.
- `app/.../ui/shell/NavGraph.kt`, `MainActivity.kt` — the wiring, required not defaulted.
- `app/src/main/res/values/strings.xml`, `ui/strings/RStrings.kt` — the copy.

**Gotchas carried over:**
- Every new `Screens.*` callback is **required, not defaulted**.
- `KlinikNetTheme` is `staticCompositionLocalOf`; one `setContent` per Compose test.
- Robolectric + `AndroidJUnit4` for UI, plain `RobolectricTestRunner` for data, **no runner** for
  pure functions — `BackupJsonTest` is the last of these and needs none.
- The ViewModel test must not hand the database handle to the code under test; `withTransaction`
  throws on the main thread under `StandardTestDispatcher`.

## Tasks & Acceptance

**Execution:**
- [x] `app/.../data/db/dao/AllocationDao.kt`, `SettingDao.kt` — `all(): List<…>`, one each.
- [x] `app/.../data/db/KlinikDatabase.kt` — `SCHEMA_VERSION` const, used by `@Database` and by the
      writer.
- [x] `app/.../data/backup/BackupJson.kt` — the writer, the deny-list, the escaping, `id` ordering.
- [x] `app/.../data/backup/BackupRepository.kt` — the eight reads, the filename from the `Clock`.
- [x] `app/.../ui/backup/BackupFileWriter.kt` — `ContentResolver.openOutputStream`.
- [x] `app/.../ui/screens/pengaturan/*` — the card, `isExporting`, `onExport`/`onExportErrorDismiss`,
      required.
- [x] `app/.../ui/shell/NavGraph.kt`, `MainActivity.kt`, `strings.xml`, `RStrings.kt` — wiring.
- [x] `app/src/test/.../backup/BackupJsonTest.kt` — **new, no runner**: escaping, empty arrays,
      ordering, root `schemaVersion`, deny-list.
- [x] `app/src/test/.../backup/BackupRepositoryTest.kt` — **new**: eight tables round-trip,
      cancelled rows keep their reason, PIN hash absent, data unchanged after export, filename
      carries the date.
- [x] `app/src/test/.../backup/ExportPermissionTest.kt` — **new**: the merged manifest declares no
      storage permission. Proven to fail by adding one.
- [x] `app/src/test/.../pengaturan/PengaturanScreenTest.kt` — eight new cases: the card, the wiring,
      disabled mid-flight, the failure's own banner and dismissal channel, the touch floor, and
      availability on a fresh install and while loading.
- [x] `app/.../ui/screens/pengaturan/PengaturanScreen.kt` — **the layout fix**, which this story
      did not set out to make: everything scrolls in one `Column`, and the account list is a
      `Column` rather than a `LazyColumn`. See the Spec Change Log.

**Acceptance Criteria:**
- Given the export action is tapped, then the system file picker opens, pre-named with today's date
- Given a destination is chosen, then one JSON file contains all eight tables
- Given two exports on different days, then the filenames differ
- Given an export has completed, then the data in the app is byte-for-byte unchanged
- Given a PIN hash row exists, then it is absent from the file while every other setting is present
- Given a fresh install with no rows, then the file holds eight empty arrays rather than missing keys
- Given a cancelled deposit, then it is present with the owner's reason
- Given an allocation row, then it is copied verbatim rather than re-derived
- Given the manifest is inspected, then no storage permission is declared
- Given an export fails, then the screen says so and claims no file
- Given the app builds, when tests run, then `:domain:test`, `:app:testDebugUnitTest` and
  `:app:assembleDebug` all pass

## Spec Change Log

### The manifest criterion needed no work, only a test — and that is the whole finding

`ExportPermissionTest` was written to *fail if it could not read the manifest*, not merely to
pass. Its first version called `getPackageInfo(GET_PERMISSIONS)` and treated a null result as "no
permissions", which would have made it green on a build where the read failed for any reason. It
was proven instead by **adding `READ_EXTERNAL_STORAGE` to the manifest**, watching it fail, and
removing it again. A permission test that has never failed is a comment about a manifest.

### Two defects the mutation checks caught, and one that only the *existing* tests caught

- **`BackupJson`'s quote escaping was never proven.** The escaping tests were written first and
  passed, which proves nothing about a test that has never failed. Breaking `'"' -> append("\\\"")`
  back to `append(c)` failed `quotes, backslashes and newlines in the owner's text are escaped` —
  and the second assertion in that test (the raw form is *absent*) is the one that caught it, not
  the first.
- **The PIN deny-list was the same shape.** Removing `filterNot { it.key in NEVER_EXPORTED }`
  failed exactly one test, and that test is the only thing standing between Story 5.3 and a hash
  in every backup file the owner has ever made.
- **Neither mutation check found the layout regression.** That one came from
  `PengaturanScreenTest`'s two pre-existing list tests, which is the argument for keeping an
  unrelated suite green rather than only adding new ones.

### The settings screen's layout was wrong before this story and the story exposed it

`IdentitySection` sat above a `Box(Modifier.weight(1f))` holding a `LazyColumn`. One card above a
weighted list is fine. **Two** cards is not: the list got whatever was left, and on Robolectric's
default viewport that was nothing — `a populated list renders the bank and the number` and
`tapping a row reaches onEdit with that row` both failed, with the row composed and off-screen.
`assertExists()` cannot distinguish "off-screen" from "missing" below the Robolectric fold, so
these tests had been green for a layout nobody could scroll.

The fix is one scrolling `Column` holding all four sections, with the account rows as a plain
`Column` rather than a `LazyColumn` — a lazy list cannot live inside an unbounded-height scroll,
and `PengaturanScreen`'s own KDoc calls the contents "a handful of accounts". **This changes the
screen's layout for a story that was supposed to add a card to it**, and that is recorded here
rather than buried: anyone reading the diff sees a `LazyColumn` become a `Column` and needs to
know why.

### `*/*` cannot be written inside a KDoc block

The MIME-type constant's first draft documented itself as `` `*/*` ``. Those last two characters
are a comment terminator, so the KDoc closed early and the compiler reported forty-odd
"Expecting a top level declaration" errors pointing at lines that were perfectly valid. It took
three compile rounds to find, because the error line was nowhere near the cause. The constant's
KDoc now says so in prose, because the next person will otherwise "restore" the literal.

### Two deviations from the Code Map

- **`RStrings` grew a `contextRef` accessor** the Code Map did not anticipate. The export needs a
  `ContentResolver` and the ViewModel that runs it holds no `Context` of its own; adding a second
  `Context` constructor parameter that must equal the first is a fact nothing would check. The
  accessor returns `getApplicationContext()` so the writer cannot leak an Activity.
- **`BackupRepository` is a plain class, not a `suspend` builder on `Settings`.** Its output is
  `BackupDocument(filename, json)` — a pair, because a repository returning a filename from
  anywhere else would let the picker suggest one name and the file record another, which the owner
  finds out by looking for a backup dated the wrong day.

## Review Triage Log

## Auto Run Result

### Files changed

| File | Change |
|---|---|
| `data/backup/BackupJson.kt` | **new** — the writer: eight tables, hand-rolled escaping, `id` ordering, the deny-list. Pure JVM, no Android import |
| `data/backup/BackupRepository.kt` | **new** — the eight reads, the `Clock`, `BackupDocument` |
| `ui/backup/BackupFileWriter.kt` | **new** — the one `ContentResolver` call, in `ui/` because `data/` must not see `Uri` (AD-1) |
| `data/db/dao/AllocationDao.kt`, `SettingDao.kt` | one `all()` each — the two tables with no whole-table reader |
| `data/db/KlinikDatabase.kt` | `SCHEMA_VERSION` as a top-level `const`, used by `@Database` and by the writer |
| `ui/screens/pengaturan/*` | `isExporting`, `exportErrorMessage`, `exportTo`, `consumeExportError`, the `BackupSection` card, and the layout fix |
| `ui/shell/NavGraph.kt` | the `CreateDocument` launcher on the settings route; `Screens.Pengaturan` gains three members |
| `MainActivity.kt` | `createBackupRepository`, the wiring |
| `strings.xml`, `RStrings.kt` | the section's copy, two accessors, `contextRef` |

### Verification

- `./gradlew :domain:test` -- **SUCCESS**
- `./gradlew :app:testDebugUnitTest` -- **SUCCESS**
- `./gradlew :app:assembleDebug` -- **SUCCESS**
- Four mutation checks, each reverted: escaping, the deny-list, the manifest permission, and the
  row-level target of the deposit history (Story 5.1's predecessor). Every one failed the test
  that claims to cover it.

### Residual risks

- **`BackupFileWriter` has no test.** `ContentResolver.openOutputStream` is faked in Robolectric
  via `ShadowContentResolver`, but no such pattern exists in this repo yet and writing the first
  one was judged more risk than the untested line was worth — the function is four lines and its
  failure mode is `false`, which the ViewModel already renders. This is a known gap, not a
  considered omission, and it is the first thing to close when Story 5.2 needs a read side too.
- **The manual check was not run.** Opening the picker on a device, choosing a location, and
  confirming the file exists and parses — none of it happened; there is no device here.

## Design Notes

**The manifest needed no change, which is the point.** It already declares zero permissions and
already carries a comment saying the backup goes through SAF. The AC's *"tidak perlu izin penyimpanan
manual"* is therefore not work but a **property to hold** — and a property nobody checks is a
property that a well-meaning future feature erases. That is what `ExportPermissionTest` is for: it
reads the merged manifest and fails if any storage permission appears, so the cost of keeping the
promise is one test rather than a reviewer's memory.

**The deny-list is written before the rows it excludes exist.** Story 5.3 adds `pinHash` and
`recoveryHash` to the same `settings` table this story exports wholesale. If the exclusion were
implemented in 5.3 as "remember to skip them when the exporter is next written", the two would be
edited by different people months apart and the skip would be added to the *insert* path instead of
the *read* path — which reads as working and leaks. So the deny-list ships now, empty of meaning,
and its test inserts a `pinHash` row **that nothing in the app writes** and asserts it is absent.
The test passes today because the key does not exist, and starts failing the moment someone adds the
row without updating the list.

**Raw allocations, not re-derived ones, is the decision with a real cost.** Copying them means
Story 5.2 has to insert them as-is, which means the restore path has to respect `allocation`'s two
unique indices and its foreign keys in the right order — more work than calling `saveDeposit` per
payment. Re-deriving would be less code and would be **wrong**: `previewAllocation` replays
oldest-day-first over the period the cutoff defines *today*, so a deposit recorded in March and
restored in November would land on different days, and the contract's per-day ledger — the thing
Story 1.3 exists to guarantee never loses a rupiah — would differ from the one that was backed up.
The cheap path trades an audit guarantee for forty lines.

**Two DAO queries, not eight.** Six tables already have whole-table readers, and `observeAll().first()`
on a Room flow is not a workaround — the flow emits on subscription, so it is one query. Adding
`suspend fun all()` to every DAO for symmetry would be eight new methods, six of which nothing
would call, and would put a second whole-table spelling next to the first on six tables. The two
that have no reader get one, and the KDoc on each says why it exists.

**The list cannot be lazy any more, and that is the story's real change to a screen it did not
mean to touch.** The account list was a `LazyColumn` inside a `weight(1f)` box, which is correct
only while the cards above it are fixed chrome. A second card made the box's share of the screen
approach zero, and the two existing list tests failed in a shape that looks like a rendering bug
and is a layout one. The honest fix is the boring one: everything scrolls together, and a handful
of bank accounts is a `Column`. The alternative — keeping the `LazyColumn` and moving the backup
control into a menu or a sheet — would preserve the windowing of a list that does not need it and
put the app's only exit from its data two taps away. `LazyColumn` is the right tool for two
thousand deposits; this screen holds neither.

## Verification

**Commands:**
- `./gradlew :domain:test` -- expected: SUCCESS
- `./gradlew :app:testDebugUnitTest` -- expected: SUCCESS
- `./gradlew :app:assembleDebug` -- expected: SUCCESS
- `git grep -n "MANAGE_EXTERNAL_STORAGE\|READ_EXTERNAL_STORAGE\|WRITE_EXTERNAL_STORAGE" app/` --
  expected: **NO MATCH** — the AC's manifest clause, checked by the reader rather than by trust