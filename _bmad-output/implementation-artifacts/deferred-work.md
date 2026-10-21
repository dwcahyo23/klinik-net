
- source_spec: `_bmad-output/implementation-artifacts/spec-demo-dataset.md`
  summary: The demo dataset is delivered as `demo-backup.json` at the repo root and verified by the test that generates it — but it has never been restored on a device.
  evidence: |
    The file exists and restores on the JVM (229 deposits, 10 vehicles, 8 renters, 8 contracts,
    three arrears tiers, one cancelled audit row). What is unrun is the device half: install the
    debug APK, copy the file to the tablet, Pengaturan → Pulihkan data, confirm the preview, then
    check Beranda's KPIs and Tunggakan's three tiers. There is no device, so this stays open —
    and it is the same "restore has never run on a device" gap that `spec-5-6` recorded.

    Regenerating is not byte-identical: `saveDeposit` stamps `createdAt` from `Instant.now()`, so
    a fresh run differs in that column only. Run `DemoDatasetTest` and copy
    `app/build/demo-backup.json` over the committed file when a later day is wanted.

- source_spec: `_bmad-output/implementation-artifacts/spec-avatar-icon-picker.md`
  summary: Teaching the backup reader to accept an older file, so a future `SCHEMA_VERSION` bump does not make every existing backup unrestorable.
  evidence: |
    Bumped to v9 for the renter's `avatar` column. `BackupJsonReader.read` refuses a
    `schemaVersion` that is not the app's, in **both** directions (`BackupJsonReader.kt:164`), so
    every v8 backup is now unrestorable. The only v8 file that existed was the generated demo,
    which is regenerated in the same commit — so nothing was lost this time. The next bump will
    not be so lucky.

    **This is much bigger than a range check.** A v6 file has no `payment.status` at all, and
    the reader reads that column with `getString`, which throws. So accepting an older file
    means per-version field defaults, not a loosened comparison: a v6 file's payments would need
    `status` defaulted to `confirmed` and `cancelReason` to null, and a v7 file's contracts would
    need `settledThroughDate` to null.

    It is safe to do in one step, because every migration since v5→v6 is `ALTER TABLE ADD COLUMN`
    only — `MIGRATION_6_7` adds `payment.status`/`cancelReason`, `MIGRATION_7_8` adds
    `contract.settledThroughDate`, `MIGRATION_8_9` adds `renter.avatar` — so a v6 file read by a
    v9 reader is a row shape the current schema can hold. The work is teaching the reader the
    **defaults**, not teaching it to guess.

    Also worth doing while there: the version check is a hard equality, so a file from a *newer*
    build is refused with a message about the app's version. A file from the future has columns
    this build has no column for, so refusing it is right — but the message should say which
    direction it went.
