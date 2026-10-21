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
