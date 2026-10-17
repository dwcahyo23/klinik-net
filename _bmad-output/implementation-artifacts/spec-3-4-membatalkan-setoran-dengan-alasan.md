---
title: 'Story 3.4 — Membatalkan setoran dengan alasan'
type: 'feature'
created: '2026-10-17'
status: 'in-progress'
review_loop_iteration: 0
followup_review_recommended: false
baseline_revision: '621577ae34a04044f8b5ac4010392d8defb6a912'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-3-context.md'
warnings:
  - oversized
deferred: []
---

<intent-contract>

## Intent

**Problem:** A mistyped deposit cannot be undone. `PaymentDao` has no delete, no update
and no status — Story 3.2 shipped a one-way record — so the only remedy the owner has is
deleting the database, which takes the fleet and the contracts with it. And a mistake is
likely by construction: the amount was typed from memory on purpose, so the mistake is the
feature working, not failing.

**Approach:** Add `status` and `cancelReason` to `PAYMENT`, and a `cancelDeposit` that in one
transaction marks the deposit, deletes **its** allocation rows and re-allocates the
contract from the deposits that survive. The row stays and stays readable; only its effect on
arrears goes away.

## Boundaries & Constraints

**Always:**
- The reason is **mandatory** (`epics.md`: *"Pembatalan wajib menyertakan alasan. Aplikasi
  menolak pembatalan tanpa alasan"*) and lands on the dialog's own field, not a banner.
- The confirmation **names the impact** — the deposit's amount *and* the arrears the contract
  returns to. The AC's own copy is the shape:
  `Batalkan setoran Rp 250.000?` / `Tunggakan Pak Budi akan kembali menjadi Rp 750.000.`
- Releasing the allocations and re-allocating the contract happen in **one transaction**
  (AR-8). A partial cancel — allocations gone, rows not rewritten — would leave the
  contract's days placed by a deposit that no longer counts.
- **`Σ allocation == amount` still holds after cancellation.** Proved by re-running
  `allocatePayments` over the survivors and asserting the sum.
- The cancelled deposit stays **visible and readable** with its reason.
- Arrears **rise** by the cancelled amount, and the open-day count rises with it.

**Never:**
- **No hard delete of the deposit.** The AC is explicit (*"tidak ada hard delete"*), and a
  deleted row cannot be audited, which is the whole reason the reason is mandatory.
- **No history screen.** This story builds the row and proves it survives; Epic 4 builds the
  surface that lists it, because that surface is part of arrears work rather than of
  cancellation.
- **No restore.** The AC's "saya bisa memulihkannya" is Story 3.5's (`mengoreksi setoran
  yang salah input`), and building both at once would leave two paths writing one row.
- No arrears screen, no KPI, no period report — Epic 4's.
- No `settledThroughDate`, no edit of the amount in place: a wrong amount is a cancel plus a
  fresh deposit, so the audit trail keeps both facts.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| CANCEL_OK | An open deposit, reason `salah tanggal` | `status = canceled`, reason stored, its allocation rows deleted, contract re-allocated, arrears rises | No error expected |
| CANCEL_NO_REASON | Reason left empty | Refused; nothing written; the deposit stays live | `deposito_error_cancel_reason_required` on the reason field |
| CANCEL_ALREADY | The same deposit cancelled twice | Refused; reason unchanged | `deposito_error_already_canceled`, on the dialog |
| CANCEL_UNKNOWN | A deposit id nobody holds | Refused | `deposito_error_unexpected` |
| CANCEL_ROW_STAYS | A cancelled deposit read back | The row is returned by the contract's query, with `cancelReason` | No error expected |
| CANCEL_SUM_HOLDS | After cancellation | Every remaining deposit's rows still sum to its amount | No error expected |
| CANCEL_ARREARS_UP | Contract with 2 days closed, a Rp 250.000 deposit cancelled | Arrears rises by Rp 250.000 and the open-day count rises | No error expected |
| CANCEL_LAST | The only deposit on a contract is cancelled | Every day reopens; arrears returns to the whole period | No error expected |
| CANCEL_OTHERS_INTACT | Two deposits, one cancelled | The survivor's rows are unchanged in value, though they may shift day | No error expected |
| MIGRATE_6_7 | A v6 database with deposits and allocations | `payment` gains `status`/`cancelReason`, existing rows read back as `confirmed`, allocations intact | No error expected |

</intent-contract>

## Code Map

**Read-only evidence — do not modify:**
- `domain/.../model/Payment.kt:19-25` — *"There is deliberately no `status` or
  `canceledAt` … carrying the flag here would invite callers to filter instead, and a filter
  somebody forgets to apply is precisely how Rp 13.000.000 disappears at the month
  rollover."* **This story reverses that decision**, with the user, because the AC requires
  a visible trail. See Design Notes for why the risk the KDoc names is now loud instead of
  silent.
- `domain/.../model/Allocation.kt` — `Σ allocation == amount` is AD-3's unconditional rule,
  and `allocatePayments` checks it internally. The re-allocation therefore cannot break it;
  the test asserts it survives cancellation anyway.
- `app/.../data/repository/PaymentRepository.kt` — `previewAllocation`, the one pure function
  from Story 3.3 that both the write and this cancellation will use. **Cancellation is the
  same shape with the deposit excluded**, which is why the extraction was worth doing one
  story early.
- `app/.../data/db/dao/PaymentDao.kt:42-81` — `forContract`, `observeForContract` and
  `insertPayment`, and no delete or update. This story adds `updatePayment` and the status
  columns; **it does not add a delete**, and the KDoc must say so.
- `app/.../data/db/dao/AllocationDao.kt` — `insertAllocation` with no delete. This story adds
  `deleteForPayment`.
- `app/.../data/db/KlinikDatabase.kt` — `version = 6`; this story moves it to `7` with a
  migration that **adds two columns and back-fills them**, which is the first `ALTER` in this
  database and therefore the first where a default clause is load-bearing.
- `app/.../ui/components/Feedback.kt:500` — `KnModal`, whose `onDismissRequest` and `dismissLabel`
  are what a mandatory-reason dialog needs.

**Reuse points:**
- `previewAllocation` — the read-and-allocate half, reused rather than rewritten.
- `KnModal` — the cancellation dialog.
- `KnField` — the mandatory-reason field.
- `formatRupiah` — every figure.

**Gotchas carried over:**
- A ViewModel test must **not** pass the database handle; `withTransaction` throws on the
  main thread under `StandardTestDispatcher`.
- `KlinikNetTheme` is `staticCompositionLocalOf`; wrap every Robolectric render.
- One `setContent` per test.

## Tasks & Acceptance

**Execution:**

*Data*
- `app/.../data/db/entity/PaymentEntity.kt` — `status: String` (`confirmed` | `canceled`)
  and `cancelReason: String?`. **Both nullable-safe in shape**: `status` is non-null with a
  default the migration supplies, `cancelReason` is nullable because a confirmed deposit has
  none. The `init` guard refuses a blank `status`.
- `app/.../data/db/dao/PaymentDao.kt` — `@Update updatePayment`, and `forContract` /
  `observeForContract` unchanged so the history still returns **every** row including
  cancelled ones. A `WHERE status = 'confirmed'` on those two would be the filter the
  `Payment` KDoc warns about.
- `app/.../data/db/dao/AllocationDao.kt` — `deleteForPayment(paymentId)`.
- `app/.../data/db/KlinikDatabase.kt` — `MIGRATION_6_7`: `ALTER TABLE … ADD COLUMN status
  TEXT NOT NULL DEFAULT 'confirmed'` and `ADD COLUMN cancelReason TEXT DEFAULT NULL`.
  **The `DEFAULT` is load-bearing** — without it every existing row reads back `null` and
  fails the entity's non-null guard at the first read.
- `app/.../data/repository/PaymentRepository.kt` — `cancelDeposit(contractId, paymentId,
  reason)`, in one transaction: read the deposit, refuse a blank reason and an
  already-cancelled one, mark it, delete its allocation rows, then re-allocate the contract
  from the survivors **through `previewAllocation`'s read half**. No screenshot, no new
  arithmetic.

*UI*
- `app/.../ui/screens/setoran/SetoranUiState.kt` — a cancellation state carrying the
  deposit, the pre-formatted impact sentence, the reason field and its error.
- `app/.../ui/screens/setoran/SetoranViewModel.kt` — `requestCancel(payment)`,
  `cancelDeposit(reason)`, `dismissCancel()`, reusing the same `saving` guard.
- `app/.../ui/screens/setoran/CancelDepositDialog.kt` — a `KnModal` naming the amount, the
  arrears it returns to, and the mandatory reason field.
- `app/src/main/res/values/strings.xml` + `RStrings.kt` — the AC's copy verbatim,
  parameterised by the two figures and the renter's name.

*Tests*
- `app/src/test/.../setoran/CancelDepositTest.kt` — **new**, covering the whole matrix.
- `app/src/test/.../penyewa/DatabaseMigrationTest.kt` — the 6→7 step and the back-fill.

**Acceptance Criteria:**
- Given an open deposit, when it is cancelled with a reason, then the reason is stored, its
  allocation rows are released, and the contract is re-allocated in **one** transaction
- Given a blank reason, when confirm is pressed, then the write is refused, the deposit stays
  live, and the message lands on the reason field
- Given an already-cancelled deposit, when it is cancelled again, then it is refused and the
  original reason is untouched
- Given a cancelled deposit, when the contract's deposits are read, then the row is **still
  there** with its reason — it has not been deleted
- Given a cancelled deposit, when arrears is recomputed, then it **rises** by that amount and
  the open-day count rises with it
- Given two deposits and one cancelled, when the contract is re-allocated, then every
  surviving deposit's rows still sum to its own amount
- Given a v6 database, when the app opens, then existing deposits read back as `confirmed`
  and every allocation row is intact
- Given the app module, when it builds and its unit tests run, then `:domain:test`,
  `:app:testDebugUnitTest` and `:app:assembleDebug` all pass, and `version` is `7`

## Spec Change Log

## Review Triage Log

## Design Notes

**This story reverses a decision Story 1.3 made deliberately, so the reversal is
recorded rather than quietly executed.** `Payment`'s KDoc says there is "deliberately no
`status`", because carrying one "would invite callers to filter instead, and a filter
somebody forgets to apply is precisely how Rp 13.000.000 disappears at the month rollover."
That reasoning is sound and this story answers it rather than ignoring it: the deposit's
**rows are never filtered** — `forContract` keeps returning every row, and the *allocator*
excludes them by construction because cancellation deletes the rows and re-allocates from
survivors. A caller who forgets must now also forget the `status` filter **and** the
`WHERE` clause, and the schema carries `NOT NULL` with a default so the value cannot be
missing. Forgetting became louder rather than silent.

**Cancellation is the same shape as saving, which is why the Story 3.3 extraction landed
one story early.** `cancelDeposit` is `saveDeposit` with the target deposit excluded from
the payment list before allocating: same read half, same pure function, one transaction,
different final write. An implementation that recomputed the arithmetic here would be a
third derivation of the same question.

**The reason is mandatory because the trail is the product.** Without it the history is a
list of numbers with gaps and the owner cannot answer "why did Budi's arrears jump on the
17th" — which is the audit the AC is protecting. A soft delete with an optional reason
would keep the row and lose the only thing it was kept for.

## Verification

**Commands:**
- `./gradlew :domain:test` -- expected: SUCCESS -- the allocator suites unchanged
- `./gradlew :app:testDebugUnitTest --tests "net.klinik.rental.ui.screens.setoran.*"` -- expected:
  SUCCESS
- `./gradlew :app:testDebugUnitTest --tests "net.klinik.rental.ui.screens.penyewa.DatabaseMigrationTest"`
  -- expected: SUCCESS -- the 6→7 step
- `./gradlew :app:testDebugUnitTest` -- expected: SUCCESS
- `./gradlew :app:assembleDebug` -- expected: SUCCESS
- `git grep -n "version = 7" app/src/main/kotlin/net/klinik/rental/data/db` -- expected: one hit
- `git grep -n "status = 'confirmed'\|status='confirmed'" app/src/main/kotlin/net/klinik/rental/data/db`
  -- expected: the migration's `DEFAULT`, **and nowhere else** — a filter in a query is the
  defect the `Payment` KDoc names
- `git grep -n "DELETE FROM payment" app/` -- expected: NO MATCH — a hard delete is the one
  thing this AC forbids

**Manual checks — DEFERRED to the end of Epic 3:**

- On the Redmi Pad 2: cancel a deposit and confirm the dialog's impact sentence fits without
  wrapping in landscape, and that the mandatory reason field is reachable with the keyboard up.