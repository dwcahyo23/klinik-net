# Epic 5 Context: Data yang selamat

<!-- Generated from planning artifacts. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Andi can copy every record out of the app, put it back when the tablet is gone, lock the app so a
found device does not expose his books, change his billing cutoff once and see what it costs, and
be reminded when he has not backed up. Everything Epic 1–4 built is only trustworthy if this epic
ships: until it does, the PRD is blunt that *"sampai Epic 5 selesai, aplikasi adalah satu-satunya
salinan catatan uang Andi tanpa jalan keluar"* — the app is the sole copy of the owner's money
records and has no way out of it.

The epic is deliberately last because FR-16 exports a schema Epic 2 and 3 created. **Before it,
there is nothing to export.** That ordering is correct rather than incidental.

## Stories

- Story 5.1: Exporting the whole database to one file
- Story 5.2: Restoring from a backup file
- Story 5.3: Locking the app with a PIN and a recovery code
- Story 5.4: Changing the cutoff date with its impact shown first
- Story 5.5: Reminding when no backup has been made
- Story 5.6: Verifying restore and PIN together

## Requirements & Constraints

**Export and restore go through the Storage Access Framework, and the app declares no storage
permission at all.** SAF hands back a `Uri` that already carries the grant, so `CreateDocument`
and `OpenDocument` need nothing declared in the manifest. `MANAGE_EXTERNAL_STORAGE`,
`READ_EXTERNAL_STORAGE` and `WRITE_EXTERNAL_STORAGE` are all forbidden — on `targetSdk` 36 the
legacy two are silently ineffective anyway, so asking would be both wrong and pointless.

**A backup is manual, never automatic.** No WorkManager, no nightly file the owner may not know
exists. Story 5.5 adds the reminder instead, because "forgot" is what needs solving and a silent
automated copy solves a different problem at the price of a dependency and a file nobody chose the
location of.

**The PIN hash and the recovery-code hash never reach a backup file.** The exclusion is an
explicit deny-list in the writer, not a memory, and the test inserts a `pinHash` row that nothing
in the app writes to prove it. The consequence is accepted and stated plainly: **after a restore the
app is not locked.** If the hash travelled, it would spread to every copy of the file — which is
exactly the leak NFR-5 forbids.

**A newer file is refused with a message the owner can read, not a parse failure that destroys what
is already stored.** `schemaVersion` sits at the JSON root and equals the Room version; a file from
a future version is rejected *before* anything is written.

**The stored file format carries no version of its own beyond that number.** No envelope, no
checksum, no signature. A checksum would answer "is this file intact", which is a real question —
and one Story 5.2 answers by validating every row against the entity guards on the way in.

**Restore is the only operation in this app that overwrites, and it always confirms first.** Every
other destructive action in Epic 3 asks through a dialog; this one asks through a dialog *and* a
preview, because what it destroys is the running copy of everything.

**PIN, when it exists, is a screen barrier and not security.** Every string and document says so
and says no more. The prototype's *"data tersimpan lokal dan terenkripsi"* is not true of anything
this app writes and **must not be reused** (FR-17). There is no difference between a locked and an
unlocked app for someone holding the tablet.

**Hashing is `PBKDF2WithHmacSHA1` with a random per-setting salt**, available since API 10 with no
library — not bcrypt or Argon2, which are not in the Android SDK without a native dependency and
would slow the owner's unlock far more than they slow an attacker.

**The recovery code has far more entropy than the PIN** — alphanumeric, at least 20 characters,
generated when the PIN is set and shown exactly once. 20 alphanumeric characters is about 119 bits;
a 6-digit code would be a PIN with an extra step and still guessable.

**Five failed PIN attempts lock input with a visible countdown.** That is the correct control for
low-entropy input, where a costly hash buys nothing against someone holding the device.

**Auto-lock is an absolute timestamp taken on entering the background**, not a counter — it has to
survive process death and a device restart, and a counter resets on both.

**Changing the cutoff rewrites no allocation row.** Story 1.5 settled this: an allocation is a
fact and the cutoff only changes which range is *counted*. Story 5.4 changes the setting and shows
what it will cost; it does not touch the ledger.

## Technical Decisions

**The backup file is written by hand, not by a serialization library.** No JSON library is on the
classpath; `org.json` returns defaults under `isReturnDefaultValues = true` and would make a
serialization bug invisible in a test; and `JSONObject` has no key order, so the same database
would produce two different files. `BackupJson` is a pure JVM function of eight lists, which is why
its test needs no runner at all.

**Rows are sorted by primary key in the writer, not left to each DAO's `ORDER BY`.** Six of the
eight readers order differently for their own reasons; a file whose bytes depended on which screen
last changed a DAO is not diffable, and an owner who cannot diff two backups cannot see what
changed between them.

**Allocations are copied verbatim and never re-derived on restore.** `previewAllocation` replays
deposits oldest-day-first over the period the cutoff defines *today*, so a March deposit restored
in November would land on different days and the restored ledger would disagree with the one that
was backed up. The restore inserts the stored rows.

**Restore reads a file, and reading is separate from writing.** A `Uri` in hand is not yet content;
a parse failure must be reportable before anything is deleted. The whole restore is then one
transaction, because a half-restored database is worse than an untouched one — it reads as a new
account holding half the owner's records.

**Two modules, one direction**, unchanged from Epic 3: `domain → data → ui`. `BackupRepository`
builds the document and knows nothing about Android; the one function that touches a
`ContentResolver` lives in `ui/`, because `data/` does not see `Uri`.

## UX & Interaction Patterns

**Every screen has four states, none removable without explanation** — unchanged from Epic 3, and
it lands hardest here. A restore that fails and renders an empty settings screen would read as
total data loss, which is the exact outcome the owner cannot distinguish from success.

**The preview comes before the confirmation, and cancelling it changes nothing.** The owner needs
to see *what is in the file* — counts and the last period — before deciding whether it is the file
they meant to pick. Picking last month's backup by accident is the normal case, and it must cost
nothing.

**The confirmation names what it destroys.** "This replaces everything currently in the app" is
the sentence; the counts are what make it concrete.

**No gesture is ever the only route to an important action**, and a destructive one that takes
seconds rather than milliseconds is a worse candidate for a gesture, not a better one.

**The backup reminder is small and does not block.** It names the last export date when there is
one, explains why it matters in one sentence — this is the only copy — and points at the export
without forcing it.

**Touch targets are at least 48dp with no exception**, and the export and restore buttons are
measured rather than reviewed: they are the app's only route to a copy of the owner's money.

## Cross-Story Dependencies

**Story 5.3 depends on nothing and 5.1 depends on nothing; 5.2 depends on 5.1's file format.**
The format is the contract, and it was written before either consumer existed precisely so that
neither could be built against a format the other had not agreed to. `schemaVersion` and the
deny-list are the two parts of that contract that a later story would otherwise have to infer.

**Story 5.6 is a verification story, not a feature.** It exists because the likeliest bug in this
epic is the interaction between two rules pulling in opposite directions: *"the PIN hash is not
exported"* says a restore must leave the app unlocked, and *"restore brings everything back"* says
it must bring the PIN back too. Both are true and they cannot both hold, and the resolution —
the hash is not in the file — only holds if a test checks the file rather than the behaviour.

**Story 5.4 reads Epic 3.6's settled-through marker and writes `settings`.** It does not re-derive
anything the allocation ledger already knows, which is the same rule Epic 4 followed.