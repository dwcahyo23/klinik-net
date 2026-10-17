---
title: 'Story 3.1 — Daftar rekening usaha yang dipakai saat mencatat setoran'
type: 'feature'
created: '2026-10-17'
status: 'done'
review_loop_iteration: 0
followup_review_recommended: false
baseline_revision: 'd497192ff3746addf05ab67f4b5ead500026558f'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-3-context.md'
warnings:
  - oversized
deferred: []
---

<intent-contract>

## Intent

**Problem:** There is no `Pengaturan` destination, no account table, and no way to record
which bank a transfer arrived at. Story 3.2's deposit form has a payment-method selector
whose "Transfer Bank" branch must pick from a list — and `epics.md:881` is explicit that
building that form against an empty dropdown means building it twice. So this story exists
to make the list exist before the form that reads it.

**Approach:** Add a `BUSINESS_ACCOUNT` table (its own table, **not** `settings` rows — see
Design Notes for the planning-document contradiction this resolves), its domain model and
typed refusals, a `BusinessAccountRepository`, and a fourth navigation destination
`Pengaturan` whose screen lists accounts and opens one sheet to add or edit one. No
deposit form, no `PAYMENT` table, no delete affordance.

## Boundaries & Constraints

**Always:**
- `bankName` and `number` are both **required and non-blank**, refused at the domain
  constructor *and* at the entity's `init`, the way `Vehicle` refuses a blank plate. A
  screen-level check alone is not the rule — two screens can pass the same check.
- The number is validated **for format only**: digits, optionally separated by spaces or
  dashes, length-bounded. **Never** checked against a bank, never a checksum, never a
  routing lookup. `epics.md:900` is explicit: *"tidak diverifikasi terhadap bank — tidak
  ada yang berubah kalau bank punya nomor berbeda."* The stored value is the owner's own
  text, normalised the way `normalizePlate` normalises a plate — spaces squeezed — so
  `1234 5678` and `12345678` are one account, not two.
- The bank name is **free text**, not an enum. A closed list of Indonesian banks would
  need a schema change for the next one, and the owner's own spelling (`BCA`, `Bank BCA`,
  `bca`) is data they recognise. No normalisation beyond trimming: a bank name is not an
  identifier here — the number is.
- **No uniqueness constraint on either column.** Two accounts at one bank are legitimate,
  and the same number at two banks is the owner's business. The AC asks for a list, not
  for deduplication; inventing a unique index would refuse a real setup.
- Every string Indonesian and in `strings.xml`; icons Lucide only.

**Never:**
- **No delete affordance, and no delete method.** The AC's fourth clause ("ada Setoran
  yang memakai suatu rekening → aplikasi menolak, ditegakkan di lapisan data") cannot be
  honoured in this story, because the `PAYMENT` table that would reference an account is
  **Story 3.2's subject and does not exist**. With no referencing table, every account is
  trivially deletable, so a delete shipped now would be a control whose guard is
  vacuously true — the exact "exists but guards nothing" shape this project's review
  process keeps catching. The guard lands with the FK in 3.2, and this spec records that
  rather than shipping the appearance of the rule. See Design Notes.
- **No `settings` table, no key/value config store.** This story needs an account
  *relation* — a row with an identity — and a `settings.value` string cannot give it one.
  The config store is Epic 5's and is a different shape. See Design Notes.
- **No `PAYMENT`, `ALLOCATION` or deposit table of any kind.** Those are 3.2 through 3.5.
- No deposit form, no numpad, no preview. Nothing in this story records money.
- No account ordering control, no "default account" flag, no active/inactive toggle. The
  AC asks for a list the owner adds to; nothing asks for a state on an account.
- No migration machinery beyond one `CREATE TABLE`. `version` goes 4 → 5 because a table
  is added, and the migration **only adds** — it cannot lose a row, so it needs none of
  `MIGRATION_2_3`'s row-count guard.
- No Material Icons, no new dependency, no `Clock` (nothing here is date-dependent).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| ACCOUNT_ADD | Empty list, bank `BCA`, number `1234567890` | Row stored; list shows bank and number; screen unchanged otherwise | No error expected |
| ACCOUNT_ADD_SECOND | One account stored, a second added at the same bank | Two rows — a second account at one bank is legitimate | No error expected |
| ACCOUNT_ADD_SAME_NUMBER | Two accounts, the same number at different banks | Two rows — the number is not unique across banks | No error expected |
| ACCOUNT_EDIT | Stored account, number changed to `999` | Row updated in place; the id is unchanged; the list re-emits with no refresh | No error expected |
| ACCOUNT_EDIT_NUMBER_NORMALISED | Number `1234 5678` edited to `1234-5678` | Stored as one normalised form, so the two spellings are one account | No error expected |
| ACCOUNT_BLANK_BANK | Save tapped with an empty bank name | Refused; nothing written | `pengaturanErrorBlankBank`, on the bank field |
| ACCOUNT_BLANK_NUMBER | Save tapped with an empty number | Refused; nothing written | `pengaturanErrorBlankNumber`, on the number field |
| ACCOUNT_BAD_NUMBER_FORMAT | Number `12ab#` | Refused; nothing written | `pengaturanErrorNumberFormat`, on the number field |
| ACCOUNT_NUMBER_TOO_LONG | A 41-digit number | Refused — the bound exists so a paste of a whole statement cannot land | `pengaturanErrorNumberFormat`, on the number field |
| ACCOUNT_LIST_EMPTY | No accounts stored | `KnEmptyState` saying what to do, not merely that the list is empty | No error expected |
| ACCOUNT_LIST_LOADING | First frame, no emission yet | Skeleton, and **not** the empty state — an empty-looking list is the "fresh account" failure | No error expected |
| ACCOUNT_LIST_ERROR | The read flow fails | An error state that does not look like a new account | `pengaturanErrorRead` |
| ACCOUNT_SPACES_TRIMMED | Bank `" BCA "` | Stored as `BCA` | No error expected |
| MIGRATE_4_5 | An existing v4 database with vehicle, renter, contract and exception rows | `business_account` has the v5 shape; every pre-existing row intact and readable | No error expected |
| ACCOUNT_NAV | The app is open on any screen | `Pengaturan` is reachable and is the **fourth** destination; the other three still work | No error expected |

</intent-contract>

## Code Map

**Read-only evidence — do not modify:**
- `app/.../data/db/KlinikDatabase.kt:49-55` — the `@Database` entity list and `version = 4`.
  This story adds a fifth entity and moves the version to `5`. `:316` is `MIGRATION_3_4`,
  the style template: two `CREATE` statements, no `DROP`, no row-count guard, and **no
  `--` comment inside either raw string** (a SQL line comment reaches SQLite verbatim and
  swallows the statement — `:167-169` records the defect).
- `app/.../data/db/KlinikDatabase.kt:359` — `.addMigrations(MIGRATION_1_2, MIGRATION_2_3,
  MIGRATION_3_4)`; `MIGRATION_4_5` joins it. A missing migration fails loudly at open,
  which is the intended direction.
- `app/.../data/db/entity/VehicleEntity.kt:49-72` — the entity shape to copy: `@Entity`
  with `tableName`, `val id: String` as `@PrimaryKey`, `@ColumnInfo(defaultValue = "NULL")`
  on every **nullable** column. **This story's table has no nullable column**, so it needs
  no `defaultValue` — the same position `ServiceExceptionEntity` is in, and its KDoc
  explains why a nullable column would force a matching `DEFAULT NULL` in the migration SQL
  or Room refuses the whole migration.
- `app/.../data/db/entity/VehicleEntity.kt:86` — `val normalized = normalizePlate(plate)`,
  the pattern the number's normalisation copies: the stored column holds the normalised
  form and the raw form is not kept.
- `domain/.../parse/Plate.kt:41` — `normalizePlate(raw: String): String?`, the precedent
  for a pure normaliser that returns `null` on unusable input rather than throwing. The
  account number's normaliser is its sibling, in the same package, for the same reason:
  parsing is one function in the lowest layer that returns null and refuses.
- `app/.../data/repository/VehicleRepository.kt:103-167,212,245` — the master-data
  repository to copy: `save` choosing insert-vs-update, a private `insertNormalized` /
  `updateNormalized` pair, and `inTransaction` with a nullable database. Note `:212`
  `delete` — **this story deliberately has no counterpart**, see the `Never` list.
- `app/.../ui/shell/Destination.kt:50-62` — the `enum class Destination(route, labelRes)`
  and its `START`. `Pengaturan` is added here, and `NavGraph.kt:65-67`'s three
  `composable(...)` calls gain a fourth.
- `app/.../ui/shell/NavGraph.kt:92-152` — the `Screens` container and its per-destination
  `class`es. A fourth destination means a fourth class, and `:213-231` explains why
  `KontrakScreen` mounts its own sheets while `KendaraanRoute`/`PenyewaRoute` do not — this
  screen has no sheet host of its own beyond its form, so follow whichever the form shape
  needs and say so.
- `app/.../ui/screens/kendaraan/VehicleFormSheet.kt` — the **closest precedent for this
  story's sheet**: master-data add/edit in a `ModalBottomSheet`, with the required-callback
  rule and the conditional-close-on-success rule. Read it before writing
  `AccountFormSheet`.
- `app/.../ui/screens/kendaraan/KendaraanViewModel.kt` — the four-state + skeleton
  pattern, `stateIn(WhileSubscribed)`, and the `AtomicBoolean` save guard.
- `app/src/test/.../penyewa/DatabaseMigrationTest.kt` — the v4 fixture and
  `openMigrated()`; extend with a v4 fixture and the 4→5 step. Its `columnsOf`,
  `notNullColumnsOf`, `nullableColumnsOf` and `indexesOf` PRAGMA helpers are what the shape
  assertions use, and the duplicate-provocation technique at `:601-631` is how a
  constraint is proven to exist **in the migrated file** rather than only in the compiled
  schema.
- `app/src/test/.../kendaraan/VehicleRepositoryTest.kt` — the repository-test shape to
  copy, including the in-memory Room harness.
- `app/src/test/.../ui/shell/FourStatesTest.kt` — walks every destination's four states;
  a fourth destination must be added to whatever list it iterates, or this story's screen
  is the one screen the four-state contract does not cover.
- `app/src/test/.../ui/AccessibilityTest.kt` — walks every interactive node against the
  48dp floor. The account sheet's controls are new nodes.

**Reuse points — nothing new to build, only to call:**
- `KnField` (`Field.kt`) — the bank name and number fields. `error: String?` means
  "validation has not run" and renders nothing; a blank string would draw an empty error row.
- `KnButton` / `KnButtonVariant` — the sheet's save and the empty state's action.
- `KnListRow` (`Row.kt`) — one account per row, with the number as the title and the bank
  as the subtitle (or the reverse; see Design Notes).
- `KnEmptyState` (`Feedback.kt:261`) — says **what to do**, per `epic-3-context.md`.
- `KnSkeletonList` — the loading state, shaped like the rows that will arrive.
- `KnSnackbar` / `KnBadgeTone` — the screen's banner and the sheet's own banner.
- `KnModal` (`Feedback.kt:500`) — not needed here (nothing is destructive), and its
  absence is deliberate.
- `RStrings` — every refusal resolved through `getString`, never assembled in the UI.

**Gotchas carried over from Epics 1–2:**
- `KlinikNetTheme` uses `staticCompositionLocalOf`; every Robolectric test wraps in it.
- `performSemanticsAction(SemanticsActions.OnClick)`, not `performClick`, inside a sheet.
- `assertExists()`, never `assertIsDisplayed()`, below the Robolectric fold.
- One `setContent` per test; hold `var state by mutableStateOf(...)` outside.
- One `testTag` per node; tags are fixed literals, never built from `stringResource`.
- `stateIn(WhileSubscribed)` needs a subscriber or `.value` stays at its initial value.
- Every number and date arrives **pre-formatted in the ViewModel**; no formatting in a
  composable.
- A required parameter, not a defaulted lambda, for every callback a screen forwards — the
  rule has caught unwired call sites in five test files across two stories.

## Tasks & Acceptance

**Execution:**

*Domain*
- `domain/.../domain/model/BusinessAccount.kt` — `data class BusinessAccount(id: String,
  bankName: String, number: String)`. `init` requires all three non-blank. **No Room
  import**, `id: String` not `UUID` per AD-1. Its KDoc states the two facts the data layer
  must not let drift: the number is **format-validated only, never verified against a
  bank**, and the bank name is free text because the number is the identifier.
- `domain/.../domain/parse/AccountNumber.kt` — `normalizeAccountNumber(raw: String):
  String?`, the sibling of `normalizePlate`: strips spaces and dashes, refuses anything
  that is not digits, and returns `null` rather than throwing. It bounds the length
  (a paste of a whole statement must not land) and states why the bound exists. Its KDoc
  must say plainly that this is **format validation and nothing more** — no checksum, no
  bank lookup — because that is the sentence a future reader would otherwise "improve".
- `domain/.../domain/validation/BlankAccountFieldException.kt` — a **`sealed class` with
  one subclass per field**, mirroring `BlankRenterFieldException` exactly (read it first):
  `sealed class BlankAccountFieldException(val field: String, message: String) :
  IllegalArgumentException(message)`, with `class BlankBankNameException(bankName: String)`
  and `class BlankNumberException(number: String)`. Each subclass carries the offending
  value and names its field in the message. **`IllegalArgumentException` subtypes**, so a
  caller catching the supertype keeps working, and the field is a `String` matching the
  column name rather than an enum — that is the precedent, and matching it means the two
  master-data screens route refusals the same way.
- `domain/.../domain/validation/InvalidAccountNumberException.kt` — `(val raw: String)`,
  the format refusal. Separate from the blank one because a blank field and a malformed
  one are different fixes: one asks the owner to type something, the other to type
  something *different*.
- `domain/src/test/.../model/BusinessAccountTest.kt` — construction guards with
  `assertThrows` per guard, the way `ContractTest` does, plus the reflection negative test
  asserting `BusinessAccount` declares no `isDefault`, no `isActive`, no `status` — the
  three fields a later story would reach for, and the AC asks for none of them.
- `domain/src/test/.../parse/AccountNumberTest.kt` — the normaliser: spaces and dashes
  stripped, non-digits refused, empty and blank refused, the length bound at both ends,
  and the two spellings of one number producing the same value.

*Data*
- `app/.../data/db/entity/BusinessAccountEntity.kt` — `@Entity(tableName =
  "business_account")`, columns `id: String` `@PrimaryKey`, `bankName: String`,
  `number: String` — **all three `NOT NULL`, so the table has no nullable column and needs
  no `@ColumnInfo(defaultValue)`.** No indices: the AC asks for a list and no lookup is
  keyed on either column, and a unique index would refuse a legitimate setup (see the
  `Always` list). `init` requires all three non-blank. Its KDoc must state why there is no
  unique index — that is the line a later reader would add.
- `app/.../data/db/dao/BusinessAccountDao.kt` — `observeAll(): Flow<List<BusinessAccountEntity>>`
  `ORDER BY bankName ASC, number ASC` (**ascending by bank**, because the owner scans for
  *which bank* first and the list is short); `findById(id)`; `@Insert(onConflict = ABORT)`
  named `insertBusinessAccount`; `@Update` named `updateBusinessAccount`. **No delete
  method** — its absence is the point (see the `Never` list), and a comment says so, so a
  later reader does not add one "for symmetry". Named methods rather than bare
  `insert`/`update`, so "the only writers of this table" stays one greppable fact.
- `app/.../data/db/KlinikDatabase.kt` — add the entity to `@Database(entities = …)`, bump
  `version` to `5`, add `abstract fun businessAccountDao(): BusinessAccountDao`, add
  `MIGRATION_4_5` (one `CREATE TABLE`; **no index to create**, which is what the absent
  unique constraint buys), and register it in `.addMigrations(...)`. No `--` comment
  inside the raw string.
- `app/.../data/repository/BusinessAccountRepository.kt` — **new repository**, not an
  extension: unlike `ContractRepository`, this owns a table with no relationship to any
  other, so extending a contracts repository with it would put two unrelated concepts in
  one class. `observeAccounts(): Flow<List<BusinessAccount>>`, `save(account)` choosing
  insert-vs-update on `findById`, and `newAccountId()` mirroring `newContractId`. Refuses
  a blank field and a malformed number with the typed exceptions **before** any write, and
  normalises the number on the way in.

*UI*
- `app/.../ui/screens/pengaturan/PengaturanUiState.kt` — `AccountRow(account, numberText)`
  with the number pre-formatted in the ViewModel, `PengaturanUiState(rows, form, errorMessage,
  isLoading)` with the four-state helpers, `AccountFormState(id: String?, bankName, number,
  bankError, numberError)` where a **null `id` means "adding"** and a non-null one means
  "editing that row", plus the pure helpers `setBankName`, `setNumber` (each clearing its
  own error), `firstMissingAccountField()`, `canSaveAccount`, and `toBusinessAccount(id)`.
- `app/.../ui/screens/pengaturan/PengaturanViewModel.kt` — the screen's flows, `openAddForm()`,
  `openEditForm(row)`, `closeForm()`, `onFormChange(transform)`, `saveForm()`, and the
  error/confirmation consumers. Catch chain: **one `catch (e: BlankAccountFieldException)`**
  routed by `e.field` through a `when` to the named field's error slot, then
  `catch (e: InvalidAccountNumberException)` to the number field, then
  `CancellationException { throw e }`, then `Exception` → the banner. That is
  `PenyewaViewModel.kt:233-241`'s shape verbatim: routing on the field the exception names
  means adding a required field later is one branch here and nothing else. An `AtomicBoolean`
  save guard, and the conditional close (`if (form.value === submitted)`) so a sheet the
  owner dismissed and reopened is left alone.
- `app/.../ui/screens/pengaturan/PengaturanScreen.kt` — the top bar with an add action, the
  list of `KnListRow`s, the four states (skeleton / empty-that-says-what-to-do / error-that-
  does-not-look-new / snackbar-that-does-not-change-state), the banner, and the form sheet
  mounted from **this** file. Five required callbacks. One `testTag` per node.
- `app/.../ui/screens/pengaturan/AccountFormSheet.kt` — **new**, one `ModalBottomSheet`
  with two `KnField`s (bank name, number with a numeric keyboard), a save button whose
  `enabled = canSaveAccount`, the sheet's own banner, and a ghost close. No delete control
  — see the `Never` list.
- `app/.../ui/shell/Destination.kt` — add `Pengaturan(route = "pengaturan", labelRes =
  R.string.nav_pengaturan)` as the **fourth** destination, after `Kontrak`.
- `app/.../ui/shell/NavGraph.kt` — a fourth `class Pengaturan` in `Screens`, a fourth
  `composable(...)`, and a `PengaturanRoute`. `AppShell`'s rail/bottom-nav item list must
  pick it up from the enum rather than a hand-written list, or the new destination is
  reachable by code and invisible in the UI.
- `app/src/main/res/values/strings.xml` — a `<!-- Story 3.1 — Rekening usaha -->` block
  after 2.8's: `nav_pengaturan`, `pengaturan_title`, `pengaturan_empty_title`,
  `pengaturan_empty_body`, `pengaturan_add_action`, `pengaturan_account_field_bank`,
  `pengaturan_account_field_number`, `pengaturan_account_bank_hint`,
  `pengaturan_account_number_hint`, `pengaturan_account_save`, `pengaturan_account_saved`,
  and the refusals `pengaturan_error_blank_bank`, `pengaturan_error_blank_number`,
  `pengaturan_error_number_format`, `pengaturan_error_unexpected`, `pengaturan_error_read`.
- `app/.../ui/strings/RStrings.kt` — a `// ---- Story 3.1` section for each, with the
  number's refusal naming the format it accepts rather than only that the input is wrong.
- `app/.../MainActivity.kt` — collect the new screen's state and add its `Screens.Pengaturan`
  block. `createKontrakViewModel` and every existing factory are unchanged.

*Tests*
- `app/src/test/.../pengaturan/BusinessAccountRepositoryTest.kt` — **new**: `ACCOUNT_ADD`,
  `ACCOUNT_ADD_SECOND`, `ACCOUNT_ADD_SAME_NUMBER`, `ACCOUNT_EDIT` (the id survives, the row
  count does not move), `ACCOUNT_SPACES_TRIMMED`, `ACCOUNT_EDIT_NUMBER_NORMALISED`,
  `ACCOUNT_BLANK_BANK`, `ACCOUNT_BLANK_NUMBER`, `ACCOUNT_BAD_NUMBER_FORMAT`,
  `ACCOUNT_NUMBER_TOO_LONG`, and the insert-vs-update choice. Against a real in-memory Room
  database, because the normalisation is a **schema-adjacent** fact: a repository that
  normalised only on the way out would pass a fake and fail here.
- `app/src/test/.../penyewa/DatabaseMigrationTest.kt` — add a **v4 fixture** and the 4→5
  step; extend `openMigrated()` to register `MIGRATION_4_5` and assert `version == 5`;
  assert `columnsOf(sql, "business_account")` is exactly `listOf("id", "bankName",
  "number")`, that **no column is nullable**, that the table has **no indices** (which is
  what proves no unique constraint was invented), and that the pre-existing vehicle,
  renter, contract and exception rows survive.
- `app/src/test/.../pengaturan/PengaturanScreenTest.kt` — **new**: the empty state says what
  to do; a populated list renders each account's bank and number; the skeleton renders while
  loading and the empty state does not; the form sheet mounts when its state is open; the
  add action reaches `onOpenAdd`; the row's tap reaches `onEdit` **with that row**; a
  refusal renders **inside** the sheet; no interactive node measures below 48dp.
- `app/src/test/.../pengaturan/AccountFormSheetTest.kt` — **new**: the two fields and the
  save button exist; save is `enabled` per `canSaveAccount`; typing reaches the state; a
  field refusal renders on its own field; **no delete control exists anywhere in the sheet**
  (the assertion that pins this story's deliberate scope); no interactive node below 48dp.
- `app/src/test/.../pengaturan/PengaturanViewModelTest.kt` — **new**: a saved account appears
  with no manual refresh; add and edit take different paths (`id == null` vs not); a blank
  field lands on its own field; a malformed number lands on the number field; a second save
  while one is in flight is ignored; closing clears the form and its error; `isLoading` is
  true before the first emission and false after.
- `app/src/test/.../ui/shell/FourStatesTest.kt` — extend to the fourth destination, so this
  screen is covered by the four-state contract rather than being the one it misses.
- `app/src/test/.../ui/AccessibilityTest.kt` — extend to the new screen's controls.
- `app/src/test/.../ui/shell/AppShellTest.kt` + `BannerScopeTest.kt` — extend for the fourth
  destination's wiring.

**Acceptance Criteria:**
- Given no accounts, when the owner opens `Pengaturan`, then the screen says what to do
  rather than only that the list is empty, and offers the add action
- Given the add sheet, when a bank name and a number are entered and saved, then the account
  appears in the list **with no manual refresh**, and it is the fourth destination the app
  gained rather than a second entry point to an existing screen
- Given a stored account, when the owner edits its number, then the row is updated in place,
  the account keeps its identity, and any list already on screen re-emits on its own
- Given a number written `1234 5678` and the same number written `1234-5678`, when each is
  saved, then both are stored as one normalised form
- Given an account whose number is edited, when the change is saved, then **no money record
  is consulted or touched** — nothing in this story records or reads a deposit, because the
  deposit table does not exist yet
- Given a blank bank name or a blank number, when save is tapped, then the write is refused
  and the message lands on **that field**, naming what to supply
- Given a number containing anything but digits, spaces and dashes, or one longer than the
  bound, when save is tapped, then the write is refused with a message that says which
  format is accepted
- Given a number the owner's bank would not recognise, when it is saved, then it is
  **accepted** — validation is format only and nothing here checks it against a bank
- Given two accounts at the same bank, or the same number at two banks, when both are saved,
  then both rows exist — neither column is unique, because the AC asks for a list
- Given the account screen, when it renders, then it has all four states — skeleton, an empty
  state that says what to do, an error state that does not look like a new account, and a
  confirmation that does not change screen state
- Given the account screen, when every interactive node is measured, then each clears 48dp,
  and the sheet carries **no delete control** — the delete refusal is Story 3.2's, because
  the table that would enforce it does not exist yet
- Given a v4 database holding vehicle, renter, contract and exception rows, when the app
  opens, then `business_account` has the v5 shape, has **no indices**, and every
  pre-existing row is intact and readable
- Given the app module, when it builds and its unit tests run, then `:domain:test`,
  `:app:testDebugUnitTest` and `:app:assembleDebug` all pass, and
  `git grep -n "androidx.compose.material.icons" app/` returns nothing

## Spec Change Log

## Review Triage Log

### 2026-10-17 — Review pass
- verdicts: 7 findings — high 0, medium 3, low 2, false 2, maybe-false 0
- findings:
  - `[medium]` `[patch]` **The typed blank-field exceptions were unreachable.** `BusinessAccount.init`
    used `require`, which throws a plain `IllegalArgumentException` *before* the repository's typed
    check could run — so `BlankAccountFieldException` would have been dead code, and the ViewModel
    would have had no field to blame. Caught by `BusinessAccountRepositoryTest`, which asserted the
    typed type and got the plain one. Fixed by moving the typed throws into the model's `init` and
    deleting the now-dead repository checks; the import stayed for the KDoc link only.
  - `[medium]` `[patch]` **The skeleton count was 3 where every other screen draws 6.** `FourStatesTest`
    asserts one skeleton row count across all screens, so a screen choosing its own number is the
    one the cross-cutting contract cannot cover. Caught by adding the fourth entry to `SCREENS`.
    Fixed to 6, with the reasoning recorded — the count is a shell-wide fact, not a per-screen taste.
  - `[medium]` `[patch]` **A ViewModel test passed the database handle**, which makes `withTransaction`
    throw on the main thread under `StandardTestDispatcher`. The exception was swallowed into the
    generic banner, so five tests failed as "the write did not happen" rather than as a harness
    problem. The repo already documents this asymmetry; fixed by dropping the handle in the
    ViewModel test and saying why, so it is not "fixed" back.
  - `[low]` `[patch]` `ACCOUNT_NUMBER_FIELD_LIMIT` was exported and unused, and a field cap is a
    **truncating** control: a pasted statement line would land as a plausible-looking 40-digit
    prefix. Removed entirely, with the reasoning moved onto `setNumber` where the next author
    would look.
  - `[low]` `[patch]` `requireNotNull` was used as a guard with a `return` inside it, which reads
    as an assertion but behaves as control flow. Replaced with an explicit null check that
    reports through the banner — a disagreement between `firstMissingAccountField` and
    `toBusinessAccount` is a defect and must not read as a successful save.
  - `[false]` `[reject]` The verification-gap layer wanted the `PAYMENT` foreign key and the
    delete refusal shipped now. Refuted: the `PAYMENT` table is Story 3.2's subject and does not
    exist, so the guard's condition would be vacuously true — the exact "control wired to
    nothing" shape. Deferred deliberately, recorded in the DAO's KDoc.
  - `[false]` `[reject]` The blind-hunter layer reported that `settings` should hold the accounts
    because the epics file says so. Refuted: the ACs are unimplementable in a key/value table —
    "an account in use cannot be deleted" needs a foreign key, and "editing the number must not
    change old deposits" needs a reference. The ERD is the more specific artifact and the
    contradiction is recorded in Design Notes.

## Auto Run Result

Status: done

### Summary

Story 3.1 shipped the business-account list Story 3.2's deposit form selects from. It adds
`BUSINESS_ACCOUNT` as its own table (v4 → v5), the domain model with **format-only** number
validation, a repository of its own, and a fourth navigation destination `Pengaturan` whose screen
lists accounts and opens one sheet to add or edit one.

**The delete AC is deliberately deferred to Story 3.2**, with the user's agreement. The AC says an
account in use cannot be deleted, enforced in the data layer — but the `PAYMENT` table that would
reference one is 3.2's subject and does not exist, so a delete now would carry a guard whose
condition is vacuously true. 3.1 ships no delete method, no delete control, and a KDoc explaining
why; the FK and its refusal land with `PAYMENT.accountId`.

**The storage contradiction was resolved with the user**, not silently: `epics.md` says accounts
live in the `settings` table, the architecture ERD defines a separate `BUSINESS_ACCOUNT` entity
with a relation to `PAYMENT`. The ERD won because the ACs are unimplementable without row identity.

### Files changed

**Domain**
- `domain/.../model/BusinessAccount.kt` — id/bank/number; typed blank-field throws in `init`;
  no `isDefault`, `isActive` or `status`.
- `domain/.../parse/AccountNumber.kt` — the normaliser: strips separators, refuses non-digits,
  bounds length as a **paste guard**. Its KDoc forbids tightening it into a bank rule.
- `domain/.../validation/BlankAccountFieldException.kt` — sealed class, one subclass per field.
- `domain/.../validation/InvalidAccountNumberException.kt` — deliberately **not** an
  `IllegalArgumentException`, so a blank-field catch cannot swallow it.

**Data**
- `app/.../data/db/entity/BusinessAccountEntity.kt` — no nullable column, **no indices**.
- `app/.../data/db/dao/BusinessAccountDao.kt` — `observeAll` ordered by bank then number; named
  writers; **no delete**, with the argument in its KDoc.
- `app/.../data/db/KlinikDatabase.kt` — version 5, `MIGRATION_4_5` (one `CREATE TABLE`).
- `app/.../data/repository/BusinessAccountRepository.kt` — `save` deciding insert-vs-update from
  the stored row, normalising the number on the way in.

**UI**
- `app/.../ui/screens/pengaturan/` — `PengaturanUiState`, `PengaturanViewModel`,
  `PengaturanScreen` (four states), `AccountFormSheet`.
- `app/.../ui/shell/Destination.kt` — the fourth destination and its icon; the compiler forced the
  exhaustive `when`, which is what made the icon unforgettable.
- `app/.../ui/shell/NavGraph.kt` + `MainActivity.kt` — `Screens.Pengaturan`, a route, and a factory.

### Tests

| File | Covers |
|---|---|
| `BusinessAccountRepositoryTest.kt` (new, 16) | add, second at one bank, same number at two banks, edit-in-place, trim, normalisation, blank/format/length refusals, ordering |
| `DatabaseMigrationTest.kt` (+4) | the v4 fixture, exact columns, no nullable column, **no declared indices**, surviving rows, a post-migration write |
| `PengaturanScreenTest.kt` (new, 12) | the four states as mutually exclusive branches, both add actions, row payload, sheet mount/absence, banner suppression, touch floor |
| `AccountFormSheetTest.kt` (new, 12) | fields, add-vs-edit title, save's enabled rule, input wiring, field refusals, both banners, **no delete control**, touch floor |
| `AccountFormTest.kt` (new, 13) | the pure rules, including "a malformed number still allows save" |
| `PengaturanViewModelTest.kt` (new, 10) | no manual refresh, add-vs-edit paths, field routing, in-flight guard, close clears, loading flag |
| `BusinessAccountTest.kt` / `AccountNumberTest.kt` (new, 18) | construction guards, the three absent fields by reflection, the normaliser from both sides |
| `FourStatesTest.kt` (+1 screen) | the cross-cutting four-state contract now covers all four destinations |

### Follow-up review recommendation

`false`. No patched `high`, and the three `medium` patches were each caught by an existing test
or by reading the diff rather than shipping silently.

### Verification performed

- `./gradlew :domain:test` — **SUCCESS**.
- `./gradlew :app:testDebugUnitTest` — **SUCCESS**.
- `./gradlew :app:assembleDebug` — **SUCCESS**. **773 tests, 0 failures, 0 skipped.**
- `androidx.compose.material.icons` under `app/` — **no match**.
- `version = 5` — one hit; `unique = true`/`Index(` in `BusinessAccountEntity` — **no match**.
- `@Delete` / `fun delete` in the new DAO and repository — **no match** (KDoc only).
- `PAYMENT|payment|deposit` in the new data files — **KDoc only**, no code.
- `Destination` entry, `composable(...)` and `Screens.Pengaturan` — all three present.
- The screen's callbacks — **required**, no `= {}` default.

### Residual risks

The delete AC is unmet until 3.2 ships the FK — by design, and the only unmet AC in this story.
Device checks deferred to the Epic 3 retrospective: pasting an account number from a real banking
app, and confirming the fourth destination renders in both the rail and the bottom nav.

## Design Notes

**The account is its own table, and the planning documents contradict each other on that.**
`epics.md:906` says accounts go in the `settings` table alongside other configuration
(*"rekening disimpan di tabel `settings` bersama konfigurasi lain, bukan DataStore"*), and
AR-17's row reads *"Settings di Room — rekening di 3, sisanya di 5."* But
`architecture-spine.md:349` defines `BUSINESS_ACCOUNT { uuid id PK, string bankName, string
number }` as its own entity, with `:293` drawing `BUSINESS_ACCOUNT ||--o{ PAYMENT :
"diterima via"` — a relation, which a key/value row cannot participate in.

The ERD wins, and not because it is newer: because the ACs are **unimplementable** without
row identity. "Ada Setoran yang memakai suatu rekening → aplikasi menolak, ditegakkan di
lapisan data" needs a foreign key, and a `settings(key, value)` pair has no key a `PAYMENT`
row could reference. The same clause also needs the *edit* half to work: "saya mengubah
nomor rekening → Setoran lama tidak berubah" means a deposit references an account rather
than copying its number, which is again a relation. AR-17's *"rekening di 3"* is best read
as **epic assignment** — which epic owns accounts — rather than a table choice, and the
architecture's own warning at `:364` (*"`SETTINGS.value` bertipe `String`, jadi tidak boleh
dipakai menyimpan nilai uang"*) shows the string column was never meant to carry structured
data. The contradiction is recorded here rather than silently resolved, because the next
story to touch configuration (5.4) will read both documents and reach the same fork.

**The delete AC is deferred to Story 3.2, deliberately, and that is a scope cut rather than
an oversight.** The AC asks that an account in use cannot be deleted, enforced in the data
layer. The table that would be "in use" — `PAYMENT` — is **Story 3.2's subject and does not
exist**. With nothing referencing an account, every account is trivially unused, so a delete
shipped now would carry a guard whose condition is vacuously true: it would pass every test,
look complete, and refuse nothing. That is precisely the "the control exists but is wired to
nothing" failure this project's reviews have caught twice. So 3.1 ships **no delete method
and no delete control**, the DAO's KDoc says why, and the FK plus its refusal land with
`PAYMENT.accountId` in 3.2. The alternative — building `PAYMENT` now to make the guard real —
would front-run the next story's schema and subject, which is the same trade in the other
direction.

**No unique index on either column, and that is a decision rather than an omission.** The
tempting index is `UNIQUE(number)` by analogy with `Vehicle.plate`. It would be wrong twice
over: two accounts at one bank are legitimate (a business account and a payroll account),
and the same number held at two banks is the owner's business rather than a data error. The
AC asks for a *list* the owner adds to, and nothing in it asks for deduplication. So the
table has **no indices at all**, the migration creates none, and
`DatabaseMigrationTest`'s `indexesOf` assertion is what keeps a later reader from adding one
"for consistency with vehicle".

**The number is normalised on the way in, and the raw form is not kept.** `normalizePlate`'s
pattern: strip the separators a human types, store one canonical form, and let the same
number written two ways be one account. The alternative — storing what was typed — makes
`1234 5678` and `1234-5678` two rows for one account, and the owner's own list would show
the same account twice with no way to tell which is current. The bound on length exists for
a specific reason worth stating: this field is filled by pasting from a banking app or a
statement, and without a bound a paste of a whole page lands silently as an "account
number".

**The screen is a fourth destination rather than a sheet off an existing one.** The AC's
own first clause is *"saya membuka Pengaturan"*, and the app has no such screen. Adding it
as the fourth `Destination` rather than hiding accounts behind, say, a top-bar action on the
fleet screen matters for two reasons: Epic 5's configuration story needs the same
destination, so this is the first tenant of a shared place rather than a one-off; and
`AppShell` derives both the rail and the bottom nav from the enum — `Destination.indexOf`
at `AppShell.kt:69` and `destinationNavItems()` at `:70` — so a destination added to the
enum appears in **both** orientations with no second edit. That is worth stating because it
is the opposite of the usual trap: there is no hand-written nav list to forget, and the
verification gate below still greps for all three of the enum entry, the `composable(...)`
and the `Screens` class, because a destination declared but not composed is reachable in the
nav and lands on nothing.

## Verification

**Commands:**
- `./gradlew :domain:test` -- expected: SUCCESS -- the model's construction rules and the
  normaliser, with no Android SDK present
- `./gradlew :app:testDebugUnitTest --tests "net.klinik.rental.ui.screens.pengaturan.*"` --
  expected: SUCCESS
- `./gradlew :app:testDebugUnitTest --tests "net.klinik.rental.ui.screens.penyewa.DatabaseMigrationTest"`
  -- expected: SUCCESS -- the 4→5 step
- `./gradlew :app:testDebugUnitTest` -- expected: SUCCESS -- the whole suite, since
  `KlinikDatabase`, `Destination` and `AppShell` are touched
- `./gradlew :app:assembleDebug` -- expected: SUCCESS
- `git grep -n "androidx.compose.material.icons" app/` -- expected: NO MATCH
- `git grep -n "version = 5" app/src/main/kotlin/net/klinik/rental/data/db` -- expected: one
  hit -- the version moved because a table was added, and nothing else
- `git grep -n "UNIQUE INDEX\|unique = true" app/src/main/kotlin/net/klinik/rental/data/db/entity/BusinessAccountEntity.kt`
  -- expected: NO MATCH -- the absent unique index is the decision, and this is the cheapest
  assertion of it
- `git grep -n "delete" app/src/main/kotlin/net/klinik/rental/data/db/dao/BusinessAccountDao.kt app/src/main/kotlin/net/klinik/rental/data/repository/BusinessAccountRepository.kt`
  -- expected: only the KDoc lines saying why there is no delete -- a real `@Delete` or
  `suspend fun delete` here means the story shipped a guard it cannot honour
- `git grep -n "PAYMENT\|payment\|deposit\|setoran" app/src/main/kotlin/net/klinik/rental/data/db`
  -- expected: NO MATCH in the new files -- nothing in this story records money, and the
  deposit table is Story 3.2's
- `git grep -n "Pengaturan\|pengaturan" app/src/main/kotlin/net/klinik/rental/ui/shell`
  -- expected: a `Destination` entry **and** a `composable(...)` and a `Screens` class -- a
  destination declared but not composed is unreachable, and one composed but not in the enum
  is invisible in the nav
- `git grep -n "onOpenAdd\|onEdit\|onSaveAccount" app/src/main/kotlin/net/klinik/rental/ui/screens/pengaturan/PengaturanScreen.kt`
  -- expected: all **required** (no `= {}` default) and forwarded in `NavGraph.kt`, so a
  partial wiring is a compile error rather than a control that does nothing

**Manual checks — DEFERRED to the end of Epic 3, with Epic 2's existing deferrals:**

- On the Redmi Pad 2, in both orientations: open `Pengaturan`, add two accounts, edit one,
  and confirm the fourth destination appears in the rail (landscape) and the bottom nav
  (portrait) with the other three.
- Add an account with a number pasted from a banking app and confirm the paste is bounded
  and the stored value reads as one number.

  **Why deferred, and what it costs.** These need the device; every automated gate passes.
  The paste-bounding claim is the one that matters: `AccountNumber`'s length bound is
  asserted in a unit test, but whether a real banking app's paste arrives as one string or
  several, and whether the field's numeric keyboard accepts it, is not something Robolectric
  reproduces. The nav claim is *not* a risk here — `AppShell` derives both orientations from
  the `Destination` enum, which is asserted in `AppShellTest` — so this is a confirmation
  rather than a gap.

  **Where to do it.** The Epic 3 retrospective, on a Redmi Pad 2, alongside Epic 2's
  outstanding device checks if those have not yet been done.