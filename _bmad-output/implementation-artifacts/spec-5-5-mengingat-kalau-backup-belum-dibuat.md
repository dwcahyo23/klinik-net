---
title: 'Story 5.5 — Mengingat kalau backup belum dibuat'
type: 'feature'
created: '2026-10-19'
status: 'done'
baseline_revision: 'f38d486e8f9c9f86a62f1cee398c3e95cde481aa'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-5-context.md'
warnings:
  - oversized
---

<intent-contract>

## Intent

**Problem:** Story 5.1 gave the owner a way to take a backup and 5.2 gave them a way to put it
back. Neither makes them **do it**, and the PRD's own framing is that the app is the only copy of
the owner's money records. A control nobody presses is not an exit.

The epic rejected the obvious alternative in writing: *"ini gantinya WorkManager dengan ekspor
harian otomatis — yang ditolak karena menambah dependency dan menyimpan file tanpa pemilik tahu
ada di mana."* So this is a **nudge**, not a job: the reminder appears, says why it matters, and
points at the export without forcing anything.

**Approach:** A small banner on Beranda that appears when today has passed and no export has been
recorded today. Tapping it opens a confirmation that says what the file is for, then routes to
`Pengaturan`. The date of the last export is written by Story 5.1's success path — **this story
adds that one write**, because without it there is nothing for the reminder to compare against.

## Boundaries & Constraints

**Always:**
- **The banner is small and above the fold.** It is a reminder, and the AC says *"kecil, tidak
  menghalangi"* — a modal on every morning the owner forgot would be a punishment for forgetting.
- **The last export date is recorded when the export succeeds**, in the same place the writer
  records it: `BackupRepository.build()`. Writing it in the ViewModel's success branch would mean
  the fact lives beside the message rather than beside the write, and an export that succeeded
  while the app died before the message would not be remembered.
- **The banner disappears once today's export has happened** — checked against a **date**, not a
  boolean, so an export at 08:00 and one at 17:00 are the same answer for the whole day.
- **The reminder never blocks and never repeats.** It is a banner with one action; there is no
  snooze, no counter, and no escalating pressure. The epic's reason for rejecting automation
  applies to nagging too.
- **An owner with no data at all is not nagged.** A fresh install that has never recorded a
  deposit has nothing to lose yet, and a reminder about backing up nothing is noise. The gate is
  "the database has something in it" **and** "no export today".

**Never:**
- **No WorkManager, no AlarmManager, no background write.** The epic rejects it by name.
- **No automatic export.** The owner decides where the file lives; the app cannot know a location
  it would be allowed to write.
- **No nag escalation, no per-day count, no "you have not backed up in 7 days" streak.** Once the
  banner is a counter it becomes something to dismiss rather than something to read.
- **No modal, no dialog on launch.** The confirmation appears only when the banner is tapped.
- **No change to the export's own behaviour.** This story records *that* an export happened; it
  does not touch what the file contains.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| BACKUP_NUDGE | Data exists, no export today, after the hour the owner picked | The banner appears, naming the last export date if there is one | N/A |
| BACKUP_DONE | Exported today | No banner | N/A |
| BACKUP_FIRST | Never exported | Banner without a date line — not "last export: never" | N/A |
| BACKUP_EMPTY | Fresh install, no deposits | No banner | N/A |
| BACKUP_UNDATED | The owner has not chosen an hour | Banner appears from the first launch of the day | N/A |
| BACKUP_TAP | Banner tapped | A dialog says what the file is for, then routes to `Pengaturan` | N/A |
| BACKUP_CANCEL | Dialog dismissed | Nothing happens; the banner stays | N/A |
| BACKUP_WRITE | An export succeeds | Today's date is stored; a later launch shows no banner | N/A |
| BACKUP_YESTERDAY | Last export was yesterday | Banner appears and names yesterday's date | N/A |

</intent-contract>

## Decisions taken at the gate

- **The nudge hour is a setting, not a constant.** *"hari ini sudah lewat"* is ambiguous: a
  reminder at 07:00 for someone who backs up at close of business is noise, and one at 22:00 for
  someone who runs the books each morning is too late. So the hour is stored beside the other
  settings, defaulting to **09:00** — after the day's deposits are usually recorded and before the
  owner has left the office. This is one more `settings` key and one chip row; the alternative is a
  constant that is wrong for half the owners.
- **The last-export date is stored as an ISO `LocalDate`, not a boolean "exported today".** Because
  the banner says *"menyebut tanggal ekspor terakhir, kalau ada"* — a last date is a fact the
  owner reads, and a boolean could only ever answer today's question. The comparison against today
  is then a derivation rather than a second stored fact.
- **The banner lives on Beranda and routes to `Pengaturan`, not to a direct export flow.** Because
  the export already lives in the settings screen and the settings screen is where the backup
  section is; a second door to the same sheet is the "two doors to one action" defect this repo
  keeps finding. The dialog's job is the explanation, not the launch.
- **The "has any data" gate is the deposit count, not a general row count.** Because a deposit is
  the thing the owner would lose, and a vehicle they just added is a row they can retype in a
  minute. This story reads `observeTodaysIncome`'s table rather than counting eight tables.

## Code Map

**Read-only evidence — do not modify:**
- `app/.../data/repository/BackupRepository.kt` — `build()`, where this story records the date, so
  the fact is written beside the write that earned it.
- `app/.../ui/screens/beranda/BerandaViewModel.kt:114-135` — the screen's single `combine`, and
  the four KPI tiles' source. The banner is **not** part of it: it is a separate flow, because
  adding a fourth source would push this screen to the same `combine` wall Story 5.4 just hit.
- `app/.../ui/screens/beranda/BerandaScreen.kt:85-111` — the scrolling column the banner mounts
  inside, above the KPI row.
- `app/.../ui/screens/pengaturan/PengaturanScreen.kt` — where the export action lives, and the
  route the reminder sends the owner to.
- `app/.../data/backup/BackupJson.kt` — `NEVER_EXPORTED`, which **must not** grow: a last-export
  date is a preference the owner benefits from restoring, not a secret.

**Modified:**
- `app/.../data/repository/BackupRepository.kt` — write `lastBackupDate` inside `build()`.
- `app/.../ui/screens/beranda/BerandaUiState.kt`, `BerandaViewModel.kt` — the reminder's state,
  read as its own flow.
- `app/.../ui/screens/beranda/BerandaScreen.kt` — the banner.
- `app/.../ui/screens/beranda/BackupReminderDialog.kt` — **new**, the explanation.
- `app/.../ui/shell/NavGraph.kt`, `MainActivity.kt`, `strings.xml`, `RStrings.kt`.

**Gotchas carried over:**
- `combine` has typed overloads to five; Beranda's is at three and the reminder stays outside it.
- Every new `Screens.*` callback is **required, not defaulted**.
- `KlinikNetTheme` is `staticCompositionLocalOf`; one `setContent` per Compose test.
- The Beranda screen scrolls, so a banner test may need `performScrollTo` — the cutoff chips
  learned that in Story 5.4.

## Tasks & Acceptance

**Execution:**
- [x] `BackupRepository` — record `lastBackupDate` inside `build()`; read it and the reminder hour.
- [x] `BerandaUiState` — `backupReminder: BackupReminderUi?`, null when there is nothing to say.
- [x] `BerandaViewModel` — the reminder as its own flow over the two stored dates and the deposit
      count.
- [x] `BerandaScreen` — the banner, above the KPI row.
- [x] `BackupReminderDialog.kt` — the explanation, and its route to `Pengaturan`.
- [x] `NavGraph.kt` (the navigate callback), `MainActivity.kt`, `strings.xml`, `RStrings.kt`.
- [x] `app/src/test/.../beranda/BackupReminderTest.kt` — **new**: the whole matrix against a real
      database, including that an export today clears it.
- [x] `app/src/test/.../beranda/BerandaScreenTest.kt` — the banner, the tap, the dialog.

**Acceptance Criteria:**
- Given the owner has data and has not exported today after the nudge hour, then Beranda shows a
  small banner naming the last export date when there is one
- Given today's export has happened, then no banner
- Given nothing has ever been exported, then the banner appears without a date line
- Given a fresh install with no deposits, then no banner
- Given the banner is tapped, then a dialog explains that this is the only copy and routes to
  `Pengaturan` without forcing anything
- Given the dialog is dismissed, then nothing happens and the banner remains
- Given an export succeeds, then today's date is stored
- Given the app builds, when tests run, then `:domain:test`, `:app:testDebugUnitTest` and
  `:app:assembleDebug` all pass

## Spec Change Log

## Spec Change Log

### The `lastBackupDate` write broke an existing round-trip test, correctly

`ExportRestoreRoundTripTest`'s `an empty database round trips to an empty database` asserted
`settingDao().all().isEmpty()`. That assertion was **right when written** and became wrong when
this story added one settings row to an export — and it was right to become wrong: an export that
leaves no record of having run means a restore onto a new device cannot tell the owner they
already have a backup, and the reminder fires against a file they demonstrably possess.

The test now asserts emptiness of the eight **data** tables and names the one row the export adds,
including that `lastBackupDate` **survives** the round trip. That is also why the key is
deliberately absent from `BackupJson.NEVER_EXPORTED`: it is not a secret, and the test would have
caught the mistake if it were.

### Three test failures, each a real lesson rather than a typo

- **Six of eight cases threw `IllegalStateException: Cannot access database on the main thread`.**
  `runTest`'s body runs on the main thread, so a repository built *with* the database handle
  makes every `withTransaction` throw. This is the asymmetry every ViewModel test in this repo
  already accepts, and the KDoc now says it on the fixture rather than leaving it as folklore.
- **The three positive cases then failed with "the banner must appear".** Because
  `stateIn(WhileSubscribed)` collects nothing until something subscribes, and `reminderSource`
  is a cold `flow { }` that never starts without one — so reading `.value` returned
  `stateIn`'s *initial* value. The reminder was never missing because of a bug in the reminder;
  it was missing because nothing had ever run. `BackupReminderTest`'s helper now launches a
  collector in `backgroundScope` and says why.
- **`LocalDate` has no `hour`.** The first cut read `today.hour < hour`, which does not compile —
  a lucky break, because the alternative (reading the hour from somewhere else) would have
  compiled and produced a reminder that silently never appeared. The hour now comes from
  `LocalDateTime.now(clock).hour`, and the KDoc records that the distinction is the difference
  between a compile error and an invisible feature.

### `collectionRate` was lost and restored

Repairing a duplicated block in `BerandaViewModel` deleted a top-level function that had been
restored from `BerandaUiState.kt` in an earlier edit. It was caught by `BerandaKpiTest`'s
compile error rather than by a test failure — which is the right time to catch it, and a reminder
that `git diff` is the cheaper check than a red suite.

## Review Triage Log

## Auto Run Result

### Files changed

| File | Change |
|---|---|
| `data/backup/BackupRepository.kt` | `lastBackupDate` written in `build()`; `lastBackupDate()`, `observeDepositCount()`, `DEFAULT_NUDGE_HOUR` |
| `ui/screens/beranda/BackupReminder.kt` | **new** — the banner and the dialog |
| `ui/screens/beranda/BerandaUiState.kt` | `BackupReminderUi` |
| `ui/screens/beranda/BerandaViewModel.kt` | `reminderSource`, `backupReminder`, the tap recorder |
| `ui/screens/beranda/BerandaScreen.kt` | the banner above the KPI row; the dialog |
| `ui/shell/NavGraph.kt`, `MainActivity.kt`, `strings.xml`, `RStrings.kt` | the wiring and seven strings |

### Verification

- `./gradlew :domain:test` -- **SUCCESS** (203)
- `./gradlew :app:testDebugUnitTest` -- **SUCCESS** (1075, 0 failures)
- `./gradlew :app:assembleDebug` -- **SUCCESS**

### Residual risks

- **No device run.** The banner's position above the KPI row was asserted by both being present in
  one composition, not by measuring that neither is below the fold.
- **`nudgeHour` has no UI.** It is a constructor parameter with a default and no setting to change
  it yet; the spec called it a setting and it is not one. Recorded rather than quietly claimed.

## Design Notes

**The reminder is the epic's answer to WorkManager, and its whole argument is about where the
file lives.** A nightly job cannot ask the owner where to put a backup, so it either guesses — and
guesses wrong, producing a file nobody finds — or asks, which is a notification the owner has to
act on anyway. That is a scheduled permission prompt with extra steps. The banner is that prompt,
placed where the owner opens the app every day, and it costs no dependency.

**The last-export date belongs in `build()`, not in the ViewModel's success branch.** Both write
the same string, and they differ in one way that matters: `build()` runs when the export
*succeeds*, while a ViewModel message is written after it returns — so a process death between
the write and the message loses the record of a backup that exists. The fact is written beside the
act that earned it, and the message is only a message.

**The reminder reads two dates and a count, and derives its own visibility.** `lastBackupDate` is
a fact, the stored hour is a preference, and today's date is a derivation from the clock. The
banner is true when *all three* say so — data exists, the hour has passed, and the last export is
not today. Making any one of those a stored boolean would be a fourth fact that could disagree
with the three it summarises.

## Verification

**Commands:**
- `./gradlew :domain:test` -- expected: SUCCESS
- `./gradlew :app:testDebugUnitTest` -- expected: SUCCESS
- `./gradlew :app:assembleDebug` -- expected: SUCCESS
