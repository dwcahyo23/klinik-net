---
title: 'A demo backup so an installed APK opens with a working fleet'
type: 'feature'
created: '2026-10-21'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: 'f49900e'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** An installed APK opens empty — no fleet, no renters, no contracts, no deposits — so
every screen shows its empty state and nothing in the app can be evaluated by using it. The
prototype has always opened with the owner's real-looking business (`prototype/data.js`: 10
vehicles, 8 renters, 8 contracts, three arrears tiers). That data has never reached the app.

**Approach:** Generate `demo-backup.json` at the repo root and restore it through the Settings
restore path that already exists. No build variant, no `BuildConfig` flag, no seeding code in
`app/src/main`.

## Boundaries & Constraints

**Always:**
- **Delivery is a backup file**, per the gate decision in `deferred-work.md`. A seeding `if
  (empty)` in the startup path is correct in a demo build and silently wrong in a release one.
- **Dates shift to relative.** The generator takes the caller's day and shifts every prototype
  date by the same offset, so the far end lands on today and the staged arrears still read as
  arrears. Figures therefore no longer match `data.js` digit for digit.
- **Allocations are computed, never hand-written.** The file is built by driving
  `ContractRepository.open`, `addServiceException`, `PaymentRepository.saveDeposit` and
  `cancelDeposit` — the same write paths the app uses — so `Σ allocation == amount` holds by
  construction rather than by transcription.
- **The generated file must restore.** The generator and its proof are the same run: the test
  writes the file, reads it back through `BackupJsonReader`, and asserts counts, the allocation
  invariant and the three arrears tiers.

**Never:**
- No `productFlavors`, no `assets/`, no demo branch in production code.
- Do not hand-write `AllocationEntity` rows. `AllocationEngine` refuses an unbalanced set, and a
  fixture that disagrees with the engine would show arrears that contradict the deposits.
- Do not transcribe `data.js`'s scattered `MISSED` dates. See the Decision below.

**Decided at the gate (2026-10-21):**

- **Arrears are a tail, and the three behind-on-rent contracts start paying only in the current
  period.** Two measured facts force this, and both were found by running the generator rather
  than by reading it:
  1. `arrearsEntry` re-derives the allocation from **every** confirmed deposit against the current
     period's charge days; it never asks which day a deposit was dated. So a gap in the middle is
     filled by the next deposit, and `data.js`'s `MISSED` dates produce **zero** arrears here.
     Transcribed literally, `cont-5` showed 0 open days where the prototype shows 8.
  2. The re-derivation is all-time, so a contract with months of history can never be behind —
     older deposits pay for the current period. That is why those three contracts begin at the
     current period's start and the other five keep the full history the charts read.
- **`cancelDeposit` is called with today, not the deposit's date.** It deletes every allocation
  row on the contract and re-derives them against `asOf`; passing the deposit's own date caps the
  charge days there and silently drops the allocations of every later deposit. Measured: two
  deposits lost their rows before this was fixed.
- **The file is a build output that is copied in, not a test side effect.** The test writes
  `app/build/demo-backup.json`; the committed copy at the repo root is that file. Regenerating is
  not byte-identical (`createdAt` comes from `Instant.now()`), so the assertions are about
  structure and figures, never bytes.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| RESTORES | the generated text, read at the current schema version | every table restores; 10 vehicles, 8 renters, 8 contracts, 4 accounts, 3 exemptions | N/A |
| ALLOCATION_BALANCED | every confirmed deposit | its allocation rows sum to its amount | the reader accepts; a broken row would fail the test |
| ARREARS_TIERS | the generated database, read at today | `cont-5` ≥ 5 open days, `cont-3` ≥ 2, `cont-6` ≥ 1, `cont-1` 0 | N/A |
| ONE_CANCELLATION | the audit-trail deposit | exactly one `canceled` row, with zero allocation rows | N/A |
| SHIFTED | generated at a later day | every date moves by the same offset; no contract ends after today | the reader's future-date refusal must not fire |

</frozen-after-approval>

## Code Map

- `app/src/test/kotlin/net/klinik/rental/data/backup/DemoDataset.kt` — the tables (transcribed
  from `prototype/data.js`), the payment loop, and `build(db, asOf)`.
- `app/src/test/kotlin/net/klinik/rental/data/backup/DemoDatasetTest.kt` — generates, writes,
  and restores. Uses `Room.inMemoryDatabaseBuilder` like the other backup tests.
- `demo-backup.json` (repo root) — the artifact.
- `app/src/main/kotlin/net/klinik/rental/data/repository/PaymentRepository.kt:115` — `saveDeposit`,
  the only way a deposit and its allocations are written together.
- `app/src/main/kotlin/net/klinik/rental/data/repository/PaymentRepository.kt:392` — `cancelDeposit`,
  which re-derives the whole contract's rows. Its `asOf` must be today.
- `app/src/main/kotlin/net/klinik/rental/data/backup/BackupJson.kt:104` — `build`, the serializer.

## Tasks & Acceptance

**Execution:**
- [x] `DemoDataset.kt` — the prototype's tables, the shift, the payment loop, the one cancellation.
- [x] `DemoDatasetTest.kt` — generate, write, restore, assert counts, invariant and tiers.
- [x] `demo-backup.json` — generated at the repo root.

**Acceptance Criteria:**
- Given `demo-backup.json` and a fresh install, when it is restored from Pengaturan, then every
  screen opens with data and Tunggakan shows three contracts with different arrears.
- Given the file, when read at the current schema version, then no contract ends after today and
  every confirmed deposit's allocations sum to its amount.
- Given the app builds, then `:domain:test`, `:app:testDebugUnitTest` and `:app:assembleDebug` pass.

## Implementation Notes

**Three failures found by running the generator, not by reading it.** Each was measured against
the generated database, and each changed the design:

1. `deposit pay-243 must allocate exactly its amount expected:<250000> but was:<0>` — the deposit
   existed with no allocation rows. Cause: `cancelDeposit` deletes **every** allocation row on the
   contract (`deleteForContract`) and re-derives them against the `asOf` it is given. The generator
   passed the cancelled deposit's own date, which capped the charge days there and silently dropped
   the allocations of every deposit made after it. Fixed by passing today, as the app does.
2. `cont-5 must be the worst: 0` with the prototype's `MISSED` dates transcribed literally — the
   allocator fills oldest-first, so a mid-period gap is covered by the next deposit. Fixed by
   making the unpaid days a tail.
3. `cont-5 must be the worst: 0` again with a tail, because `arrearsEntry` re-derives from
   **all** confirmed deposits against the current period's charge days. Months of history paid for
   the current period no matter what the tail did. Fixed by starting those three contracts at the
   current period's start.

**229 deposits, not the ~380 the ledger guessed.** 52 days × 8 contracts, less rest days, three
service exemptions and the unpaid tails; the three behind-on-rent contracts start later still. The
test's floor is 200 — loose enough for a one-day shift, tight enough to catch a loop that stopped
early.

**`System.getenv` does not reach a Gradle test JVM started by a running daemon.** The first attempt
to write the file through `DEMO_OUT` produced nothing, silently. The test now always writes
`build/demo-backup.json` and `DEMO_OUT` only overrides it.

## Spec Change Log

## Review Triage Log

## Verification

**Commands:**
- `./gradlew :domain:test :app:testDebugUnitTest :app:assembleDebug` -- expected: SUCCESS

**Manual checks (a device is required, NOT RUN — none available):**
- Install the debug APK, copy `demo-backup.json` to the tablet, Pengaturan → Pulihkan data, confirm
  the preview, then check Beranda's KPIs and Tunggakan's three tiers.
