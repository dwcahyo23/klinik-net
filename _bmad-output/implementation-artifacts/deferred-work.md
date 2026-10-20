- source_spec: `_bmad-output/implementation-artifacts/spec-ui-chrome-theme.md`
  summary: Renter avatars — an emoji per renter, shown in the list rows and the detail header, editable in the renter form.
  evidence: |
    Split because it needs a schema migration (v8 -> v9, a new nullable `avatar` column on
    `renter`) and a form field, which is a data change rather than chrome. The prototype's own
    `.avatar` / `.avatar-lg` (34px / 64px) are already matched by `KnAvatar`'s sizes — only the
    emoji source is missing, and `KnAvatar(name)` already draws initials today.

    **Blocked on restore compatibility, measured 2026-10-21.** `BackupJsonReader.read` refuses
    `version != expectedVersion` in *both* directions (`BackupJsonReader.kt:164`), and the
    "Story 5.6 teaches the reader to accept an older file" comment there is stale — 5.6 shipped
    and this behaviour remains. So bumping `SCHEMA_VERSION` to 9 for one cosmetic column would
    make **every existing backup unrestorable**. The prerequisite is teaching the reader to
    accept an older file, and that is only safe for additive migrations — `MIGRATION_6_7` and
    `MIGRATION_7_8` are `ALTER TABLE ADD COLUMN` only, so a v7 file read by a v9 reader works by
    construction. Do that first, as its own change; avatars then cost one migration and no
    orphaning. Do not ship the version bump before it.

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
