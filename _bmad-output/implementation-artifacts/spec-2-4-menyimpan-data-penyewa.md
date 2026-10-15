---
title: 'Story 2.4 — Menyimpan data penyewa'
type: 'feature'
created: '2026-10-15'
status: 'in-progress'
review_loop_iteration: 0
followup_review_recommended: false
baseline_revision: 'a601b99653938ef42c1820f15e497614fe4a68ab'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-2-context.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-2-3-melihat-status-ketersediaan-armada.md'
warnings:
  - oversized
---

<intent-contract>

## Intent

**Problem:** The app can store vehicles but has no place to store the people renting them. Every later story — contracts (2.5), arrears (Epic 3), the WhatsApp reminder (4.x) — needs a renter, and the renter's *name* is the key that reconciles against bank mutations. Nothing in the codebase names a renter: no domain model, no entity, no DAO, no repository, no screen.

**Approach:** Add `RENTER` end to end, mirroring the vehicle path that Stories 2.2/2.3 established — domain model, Room entity with a real unique index on `name`, repository raising typed exceptions, and a list + form screen. Name is required and unique; bank account is optional; KTP and address are not collected at all.

## Boundaries & Constraints

**Always:**
- `name` is required, non-blank, and unique — enforced by a real `UNIQUE` index (AR-12), with a pre-check as courtesy only.
- `id` is `String` in the domain, a `uuid` column in the database — the existing precedent, because `domain/` may not import `java.util.UUID`.
- The repository raises typed domain exceptions and never Indonesian text; `RStrings` picks the wording.
- Every string is Indonesian and lives in `strings.xml`.

**Never:**
- No `ktp` or `address` field, anywhere. PRD §6.1 pushes both out of scope (`prd.md:222`); collecting personal data without a reason is the defect this epic exists to remove.
- No `RENTER` → `CONTRACT` foreign key. `ContractEntity` gains `renterId` in Story 2.5 with the partial unique index for one active contract per renter (FR-7).
- No delete affordance *or* delete guard. Unlike Story 2.2, no AC asks for a refusal, and the mandatory-reason audit sink does not exist yet. YAGNI until a story needs it.
- No name normalization. Plate normalization (uppercase, squeeze) is scoped to plates in `epic-2-context.md:24`; names are not plates.
- No Material Icons. Lucide only.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| SAVE_RENTER | Valid name, phone, emergency contact | Row written; renter visible in the list with no manual refresh | No error expected |
| SAVE_RENTER_OPTIONAL_ACCOUNT | Bank account left blank | Saved with `bankAccount = null` | No error expected |
| SAVE_RENTER_BLANK_NAME | `name = "   "` | Refused; nothing written | Field error on `name` |
| SAVE_RENTER_DUPLICATE_NAME | Name already stored by a *different* renter | Refused; nothing written | Field error naming the stored name |
| EDIT_RENTER | Edit renter A, renter B exists | Only A's row changes | No error expected |
| SEARCH_RENTER | Query matches a substring of name, phone, or bank account | List narrows in place; no route change | Empty-search state, not a failure |
| MIGRATE_1_2 | Existing v1 database | `renter` table and its unique index exist; existing `vehicle` rows intact | No error expected |

</intent-contract>

## Code Map

**Read-only evidence — do not modify:**
- `app/.../data/repository/VehicleRepository.kt:103-135` — the ordered save pipeline this mirrors: normalize, reject blank, check amount, pre-check collision, then insert-or-update matched on the primary key. An edit is "same id updates in place"; a different id with a taken key is a duplicate.
- `app/.../data/repository/VehicleRepository.kt:147-157` — `insertNormalized` catches `SQLiteConstraintException` and re-reads the colliding value, so the message names a real row instead of echoing SQLite.
- `app/.../data/db/entity/VehicleEntity.kt:49-93` — `@Entity` shape: lowercase singular `tableName`, no `@ColumnInfo(name=…)` (columns are property names verbatim), `@ColumnInfo(defaultValue = "NULL")` on every nullable column, `OnConflictStrategy.ABORT`, and an `init` block that re-checks the domain rule so a raw `@Insert` cannot land a bad row.
- `app/.../data/db/KlinikDatabase.kt:18-22` — the KDoc promising version `1` for Epic 2 and warning that `fallbackToDestructiveMigration` would make dropping the owner's fleet easy. Neither fallback nor any `Migration` exists today.
- `app/.../ui/strings/RStrings.kt:34` — the `Context`-wrapping accessor class. A ViewModel has no composition, so this is where a typed exception becomes a sentence.
- `ui/theme/`, `ui/format/Rupiah.kt` — every token and the single money-to-text path. This screen shows no money at all.

**Reuse points:**
- `domain/.../validation/DuplicatePlateException.kt:27` — the exact shape a `DuplicateNameException` follows: a typed exception carrying the colliding value, extending `IllegalStateException`.
- `domain/.../model/Vehicle.kt:64-78` — how a domain model refuses on construction. `Renter` uses `require(name.isNotBlank())` and skips the money rule entirely.
- `app/.../ui/screens/kendaraan/KendaraanUiState.kt` — the whole pattern: one `UiState` data class, one `FormState`, pure top-level filter functions so search is testable without a device, and the `isEmptyFleet` / `isEmptySearch` split so an empty *search* never reads like an empty account.
- `app/.../ui/screens/kendaraan/KendaraanViewModel.kt:64-108` — nested `combine` + `stateIn` + `distinctUntilChanged()`; `FleetSnapshot`/`FormSnapshot` exist because the typed `combine` overload stops at five flows.
- `app/.../ui/components/` — `KnTopBar`, `KnSearchBar`, `KnListRow`, `KnEmptyState`, `KnSnackbar`, `KnField`, `KnButton`, `KnAvatar`, `KnBadge`. All exist; nothing needs adding. Note `KnField.error` replaces the hint and stays invisible until non-null, and validation timing is the screen's decision.
- `VehicleFormSheet.kt` — the `ModalBottomSheet` pattern. `KnCheckbox` (added in 2.3) and `KnNumPad` also exist, but neither applies here: a boolean toggle would be an invented field and there is no money on this form.

**Gotchas carried over from Stories 2.1–2.3:**
- `KlinikNetTheme` uses `staticCompositionLocalOf` — a screen composed outside it silently gets the light defaults, so every Robolectric test wraps in the theme.
- `KnListRow.leading` is hard-boxed to 44dp; pass `KnAvatar()` at its default size rather than a 48dp icon button.
- In a test, use `assertExists()` rather than `assertIsDisplayed()` for anything below the Robolectric fold — an off-screen node measures zero, so the displayed claim would be about the harness rather than the screen.
- `KendaraanUiState.errorMessage` exists but `KendaraanScreen` never renders it. Do not copy that dead state: either render this screen's error or do not carry one.

## Tasks & Acceptance

**Execution:**
- `domain/.../domain/model/Renter.kt` -- `data class Renter(id: String, name: String, phone: String, bankAccount: String?, emergencyContact: String)` whose `init` requires a non-blank name -- no Room import; `id` is a `String` per the `Vehicle` precedent
- `domain/.../domain/validation/DuplicateNameException.kt` -- typed exception carrying the stored name that collided -- mirrors `DuplicatePlateException`, so the message points at a row that actually exists
- `domain/src/test/.../model/RenterTest.kt` -- construction refuses a blank or whitespace-only name -- the module that needs no Android SDK
- `app/.../data/db/entity/RenterEntity.kt` -- `@Entity(tableName = "renter")` with `Index(value = ["name"], unique = true)`, `@ColumnInfo(defaultValue = "NULL")` on `bankAccount`, and an `init` re-checking the name rule -- uniqueness as a schema constraint, not a query
- `app/.../data/db/dao/RenterDao.kt` -- `Flow` of all renters ordered by name, `findById`, `findName`, `@Insert(onConflict = ABORT)`, `@Update` -- no validation in the DAO; it moves rows and lets SQLite say no
- `app/.../data/db/KlinikDatabase.kt` -- register `RenterEntity`, add `renterDao()`, bump `version` to `2`, and add `MIGRATION_1_2` creating the table and its unique index -- see the design note on why the version moves inside Epic 2
- `app/.../data/repository/RenterRepository.kt` -- `observeRenters()`, `save(Renter)`, `newRenterId()`; mirrors the `VehicleRepository` save order and translates `SQLiteConstraintException` into `DuplicateNameException`; never exposes the DAO and never returns `Result<_, String>`
- `app/.../ui/screens/penyewa/PenyewaUiState.kt` -- `RenterRow`, `PenyewaUiState` (rows, query, form, errorMessage), `RenterFormState`, and pure `matchesRenterQuery` over name/phone/bankAccount
- `app/.../ui/screens/penyewa/PenyewaViewModel.kt` -- `combine` + `stateIn` + `distinctUntilChanged()` over the Room flow, holds the form state, maps each exception to its message through `RStrings`
- `app/.../ui/screens/penyewa/PenyewaScreen.kt` -- stateless: `KnTopBar` with the renter count, `KnSearchBar`, the `KnListRow` list, `KnEmptyState` for both emptinesses, and the success snackbar
- `app/.../ui/screens/penyewa/RenterFormSheet.kt` -- Material3 `ModalBottomSheet` with four `KnField`s and no `KnNumPad` -- there is no money on this form
- `app/src/main/res/values/strings.xml` -- a `<!-- Story 2.4 — Penyewa -->` block of `penyewa_*` keys, mirroring the `kendaraan_*` naming convention
- `app/.../ui/strings/RStrings.kt` -- `penyewaSaved`, `penyewaErrorDuplicateName`, `penyewaErrorBlankName`, `penyewaErrorUnexpected`
- `app/src/test/.../penyewa/RenterRepositoryTest.kt` -- `SAVE_RENTER`, `SAVE_RENTER_OPTIONAL_ACCOUNT`, `SAVE_RENTER_DUPLICATE_NAME`, `EDIT_RENTER` against in-memory Room, because a `UNIQUE` index is a schema guarantee a fake DAO cannot demonstrate
- `app/src/test/.../penyewa/DatabaseMigrationTest.kt` -- build a v1 database, run `MIGRATION_1_2`, assert the `renter` table and its unique index exist and the pre-existing vehicle row survives -- nothing else catches a wrong migration, and a wrong one crashes every install that already ran 2.2
- `app/src/test/.../penyewa/PenyewaFilterTest.kt` -- the `SEARCH_RENTER` row against the pure function, with no runner and no Android dependency
- `app/src/test/.../penyewa/PenyewaScreenTest.kt` -- Robolectric render: the empty state says what to do, a populated list shows the name, and no interactive node measures below 48dp
- `app/src/test/.../penyewa/RenterFormTest.kt` -- a blank name is rejected with a visible message on the field and nothing written

**Acceptance Criteria:**
- Given an app opened with no data, when the renter screen renders, then the empty state says what to do (`Belum ada Penyewa` / `Tambahkan penyewa pertama untuk mulai mencatat sewa.`) with a `Tambah Penyewa` action — not a bare "no data", and not a screen that looks like an error
- Given a renter saved from the form, when the list is observed, then the renter is visible with no manual refresh — Room's invalidation tracker re-emits, and there is no refresh path at all (AR-12)
- Given the search bar, when the owner types a substring of a name, phone, or bank account, then only matching rows remain and it is still the same list — filtering, never navigation to a second screen
- Given the renter form, when it is opened, then it shows exactly four fields — name, phone, bank account, emergency contact — and no KTP or address field exists to be filled in
- Given a search that matches nothing, when the list renders, then the empty-search state appears, which does not read like a fresh account
- Given the app module, when it builds and its unit tests run, then `:app:testDebugUnitTest` and `:domain:test` both pass, and `git grep -n "androidx.compose.material.icons" app/` returns nothing

## Spec Change Log

**2026-10-15 — review pass (adversarial, edge-case-hunter, verification-gap).** Four production
defects and six coverage gaps closed:

1. `RenterRepository.save` asked "is my id stored?" only when the name was already taken, so an
   edit that *renamed* to an unused value fell through to an INSERT carrying an existing id. SQLite
   refused it as a primary-key conflict and the repository reported it as a duplicate name. Fixed by
   asking both questions unconditionally. **`VehicleRepository.kt:121-122` has the identical shape
   and therefore the identical latent bug for the vehicle path** — out of scope here, recorded for
   the story that touches it.
2. `MIGRATION_1_2` declared `bankAccount` as bare `TEXT` while `RenterEntity` carries
   `@ColumnInfo(defaultValue = "NULL")`. Room's compiled schema records a default of `NULL` and Room
   compares recorded defaults when validating a migrated database, so the migration was refused
   wholesale — crashing every install that had already run 2.2.
3. `DatabaseMigrationTest` passed falsely: the v1 fixture was written to `cacheDir` while Room opens
   `context.getDatabasePath(name)`, so Room created a fresh empty database at v2 and every schema
   assertion passed vacuously.
4. `PenyewaViewModelTest` never subscribed to `uiState`, so `stateIn(WhileSubscribed)` never
   queried Room and `.value` stayed at `initialValue`.

Plus, from the review lenses: blank `phone` / `emergencyContact` are now refused at construction
with typed exceptions carrying *which* field was empty (the spec documented them as required but
nothing enforced it); the name is trimmed in the repository, not only the ViewModel; `catch (e:
Exception)` re-throws `CancellationException`; a second Save tap while a write is in flight is
ignored; the two `KnSnackbar`s are mutually exclusive; `filterByQuery` composes
`matchesRenterQuery` instead of duplicating it; `ORDER BY name COLLATE NOCASE`; `findNameById`
deleted as dead code; and new tests cover the rename-collision branch, trimming, the blank-contact
refusals, the double-tap guard, button-handler invocation, and search through the production
function.

## Review Triage Log

**2026-10-15 — first review pass.** Lenses: adversarial, edge-case-hunter, verification-gap.

| # | Verdict | Route | Note |
|---|---------|-------|------|
| 1 | high | fixed | phone/emergencyContact documented required, never enforced |
| 2 | high | fixed | name trimmed only in the ViewModel, not the repository |
| 3 | med | fixed | stale `current` snapshot discarded keystrokes typed during the write |
| 4 | med | fixed | every `SQLiteConstraintException` translated as a name collision |
| 5 | med | fixed | rename path gave no name to look at; now translated like the insert |
| 6 | high | fixed | rename-onto-taken-name branch had no test |
| 7 | med | **not fixed** | `insertEntity`'s race catch has no test seam; `RenterDao` is a concrete interface with no injectable fake, and the pre-check always wins single-threaded. Needs a seam this story does not have. |
| 8 | — | **not a defect** | `RenterFormSheet` is deliberately unmounted (spec Design Notes, user's decision). Recorded, not changed. |
| 9 | med | documented | `renterCount` is the stored count while the list shows filtered; KDoc now says why this differs from `kendaraan_count` |
| 10 | high | fixed | `filterByQuery` and `matchesRenterQuery` were independent copies; now composed |
| 11 | med | fixed | `catch (e: Exception)` swallowed `CancellationException` |
| 12 | med | fixed | success and error snackbars could overlap; now mutually exclusive |
| 13 | low | fixed | the spec's own `ktp`/`address` grep gate matched its own KDoc; narrowed to declarations |
| 14 | med | documented | v1 fixture lacks `room_master_table`; covers the DDL path, not the identity-hash update |
| 15 | high | fixed | no `performClick`/`performTextInput` anywhere in the penyewa suite; added both |
| 16 | low | **not fixed** | `kotlinx-coroutines-core` is undeclared, so the comment's "matches Room's transitive version" is unenforced. Adding the declaration is a build-wide choice, not this story's. |
| 17 | low | **not fixed** | one rationale restated across six files. Real drift risk, but cutting comments is a judgement call for the reviewer's caller, not a defect. |
| 18 | low | **not fixed** | `penyewa_edit` string unused (`penyewa_edit_description` is used, at `PenyewaScreen.kt:178`). One dead resource. |
| 19 | low | **not fixed** | test named "without a migration" calls `.addMigrations(...)`. Rename-only. |

Edge-case-hunter and verification-gap independently converged on 6 of these, which is the strongest
signal in the set: the rename branch, the duplicated search predicate, the unreachable error path,
the unclickable buttons, and the missing constraint translation were each found by more than one
lens.

## Design Notes

**The schema version moves inside Epic 2, against the database KDoc's stated intent.** `KlinikDatabase.kt:18-22` says version stays `1` for the life of Epic 2 and no migration is declared — with the reason "there is no earlier version to migrate from". Adding `RENTER` makes that reason false: v1 exists, as built by 2.2 and 2.3, and any device that already ran them holds that schema on disk. Leaving the version at 1 with a changed entity set makes Room's stored identity hash disagree with the compiled schema and throw on open. So the version goes to `2` with a real `MIGRATION_1_2`, the KDoc is corrected to say the rule is "bump and migrate whenever the schema changes", and `fallbackToDestructiveMigration` stays unused — the KDoc's own warning about it dropping the owner's fleet is exactly right, and dropping the owner's data is not a direction to be wrong in.

**Name uniqueness is exact, not case-folded.** `epic-2-context.md:24` gives the plate a normalization rule — uppercased, spaces squeezed, so `b 1235 tf` and `B 1235 TF` collide — in the same sentence that requires the renter name to be unique, and does not extend that rule to names. The contrast is deliberate: a plate has one canonical form and a person's name does not, so uppercasing a name would degrade every label, snippet and WhatsApp message built from it. Matching a sender name against a stored renter is a manual step the owner does in m-banking (PRD §5, "Rekonsiliasi nama pengirim terjadi di m-banking, secara manual, oleh Andi"); the app's obligation is that the name is unique so that search resolves to one person. `Budi` and `budi` can coexist, and if the owner finds that ambiguous the fix is a normalization rule applied deliberately — not one inferred here.

**Phone and emergency contact are required; bank account is optional.** `prd.md:219-221` states exactly three tested consequences: the name is required, the bank account is optional, and the name is unique. It says nothing about phone or emergency contact. The epic context's data model (`epic-2-context.md:38`) annotates `bankAccount` as `nullable` and leaves `phone` and `emergencyContact` unannotated, which is the distilled design's way of marking the one optional field. This spec follows that reading: two required text fields and one optional. If the intent was phone-optional, the fix is a `String?` plus one `require` in `Renter.init` — a narrow change, recorded here so the derivation is visible rather than silent.

**Master-detail is deferred to Story 2.8's shell, and the screen is not mounted.** UX-DR13 asks for list and detail side by side in landscape, but AR-18 makes `WindowSizeClass` a `MainActivity` decision, and no `WindowSizeClass`, `NavHost`, or orientation check exists anywhere in `app/src/main` today. `KendaraanScreen.kt:60-61` already defers the same thing to Story 2.8, and Story 2.2's AC — which describes search and a readable list — did not name master-detail at all, while 2.4's does. Building it here would put a layout rule in two screens with different shapes and leave the two master-data screens inconsistent; building the shell instead would pull Story 2.8's NavHost, rail and bottom-nav into a story about storing renter names. So this story ships the screen, fully tested and unreachable, and Story 2.8 wires it. That deferral is a real gap against UX-DR13 and belongs on the record rather than in a comment: the AC's promise is met in 2.8, not here.

## Verification

**Commands:**
- `./gradlew :domain:test` -- expected: SUCCESS -- renter name validation, with no Android SDK present
- `./gradlew :app:testDebugUnitTest --tests "net.klinik.rental.ui.screens.penyewa.*"` -- expected: SUCCESS
- `./gradlew :app:assembleDebug` -- expected: SUCCESS
- `git grep -n "androidx.compose.material.icons" app/` -- expected: NO MATCH
- `git grep -nE "\bktp\b|nik|address" app/src/main` -- expected: NO MATCH, so the out-of-scope personal data has no path back in.
  **Narrowed after the first review:** the wide grep matched the KDoc that explains *why* those
  fields are absent, so it reported a failure on correct code. Scope it to declarations:
  `git grep -nE "val (ktp|nik|address)\b|ColumnInfo\(name ?= ?\"(ktp|nik|address)" app/src/main`
  -- expected: NO MATCH.