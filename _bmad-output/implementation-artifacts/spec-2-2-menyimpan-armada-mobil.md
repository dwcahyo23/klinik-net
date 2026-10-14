---
title: 'Story 2.2 — Menyimpan armada mobil'
type: 'feature'
created: '2026-10-14'
status: 'done'
review_loop_iteration: 0
baseline_revision: '397e5fe'
followup_review_recommended: false
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-2-context.md'
warnings:
  - oversized
deferred: []
---

<intent-contract>

## Intent

**Problem:** Andi cannot store his fleet, so every rental starts by hunting for a paper note. The prototype at `prototype/cars.html` shows the intended screen, but its store layer contains exactly the failures this app must not ship: `dailyRate: parseInt(x) || 250000` turns `0`, `"abc"` and negatives into Rp 250.000; plate uniqueness is a `JSON.parse(localStorage).some()` with no index, so two writes can both pass; `updateCar` persists the rate as a raw DOM string, which silently turns the fleet KPI into concatenated digits; and `status` is a stored column that drifts from the contract it is supposed to describe. There is no Room database in `app/` at all yet.

**Approach:** Add the `VEHICLE` table plus the minimum of `CONTRACT` needed to make the delete refusal real, put plate normalisation and rupiah parsing in `domain/` as pure functions that refuse rather than default, and rebuild the screen on Story 2.1's components.

## Boundaries & Constraints

**Always:**
- `dailyRate` is `Long` whole rupiah end to end. `0` and negatives are rejected at the data layer, not only in the form.
- Plate is required, non-blank, and unique **after normalisation** (uppercased, whitespace squeezed). The unique index is on the normalised value, so `b 1235 tf` and `B 1235 TF` collide in the database, not just in a UI check.
- One `parseRupiahInput(String): Long?` in `domain/`, returning `null` on bad input. No silent default anywhere — not in the repository, not in the form, not in a fallback branch.
- Every rupiah figure renders through the existing `formatRupiah(Long)`. No hand-assembled money string.
- Status is derived, never stored: `servis` from the manual marker on the vehicle, `tersewa` from the existence of an active contract (`endDate IS NULL`), `tersedia` otherwise.
- All Indonesian strings in `res/values/strings.xml`; no literal in a composable body.
- `domain/` stays pure-JVM — no `android.*`, no Room.

**Never:**
- No `status` column on `VEHICLE` and no `currentRenterId`. The prototype's both are display conventions that drift and prevent nothing.
- **No delete button, no delete dialog, and no audit table this story.** The guard ships; the affordance does not. See Design Notes — the reason has nowhere to go yet, and a dialog that collects one and discards it is the prototype's defect, not a port of it.
- No seeded or sample data, no `SIM_TODAY`, no `Date.now()`-derived id. Ids arrive as `String` from Room.
- No plate-format regex. The spec requires required-and-unique-after-normalisation, not a shape; the prototype's `^[A-Z]{1,3}\s?\d{1,4}\s?[A-Z]{0,3}$` disagrees with its own normaliser and is not being ported.
- No year range check. Nothing in the intent states one.
- No KPI tile grid, nav rail, bottom nav, skeleton, error state or success snackbar on this screen — those belong to Story 2.8 and Story 4.3, and a card of four figures nobody asked for is not this story.
- No Material Icons; Lucide only.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| PARSE_RATE | `"250000"` | `250000L` | Returns the value |
| PARSE_RATE | `"0"`, `"-1"`, `"abc"`, `""`, `"2.5"` | `null` | Null, never a fallback figure |
| NORMALIZE_PLATE | `"  b   1235  tf "` | `"B 1235 TF"` | Squeezes whitespace, uppercases, trims |
| NORMALIZE_PLATE | `"   "` | `null` | Blank plate has no normalised form |
| SAVE_VEHICLE | valid plate, unique, rate `250000` | Row inserted, appears in list | No error |
| SAVE_VEHICLE | plate collides with a stored vehicle | Nothing written | `DuplicatePlateException` naming the stored plate |
| SAVE_VEHICLE | rate `0` or negative | Nothing written | `InvalidAmountException`, never defaulted |
| DELETE_VEHICLE | vehicle has a contract with `endDate IS NULL` | Nothing removed | `VehicleHasActiveContractException` — the guard ships; the delete button does not |
| SEARCH_QUERY | `"agya"`, `"AGYA"`, `"b 123"` | Matching rows only | Substring, case-insensitive, over plate/brand/model/color |
| STATUS_FILTER | `tersewa` / `tersedia` / `servis` / `semua` | Matching rows only | A filter over one list, never a separate destination |

</intent-contract>

## Code Map

**Read-only evidence — do not modify:**
- `app/src/main/kotlin/net/klinik/rental/MainActivity.kt:34` — the `LocalDate.now()` tripwire proving core-library desugaring is live. Keep it.
- `app/src/main/kotlin/net/klinik/rental/ui/theme/` — every colour, type, radius, spacing and motion value. Screens read `MaterialTheme.kn*`; they never hardcode a dp or hex.
- `app/src/main/kotlin/net/klinik/rental/ui/format/Rupiah.kt:28` — `formatRupiah(Long)`, the only money-to-text path.
- `app/build.gradle.kts:75-77` — Room runtime/ktx/compiler already wired through KSP. Nothing to add. `testOptions.unitTests.isIncludeAndroidResources = true` at line 46 is what lets Robolectric render these screens.
- `domain/build.gradle.kts:1-10` — pure-JVM module; an `android.*` import here is a build failure, which is the point.

**Reuse points:**
- `ui/components/`: `KnTopBar`, `KnSearchBar`, `KnChip`, `KnListRow`, `KnTableCell`/`KnTableRow`, `KnEmptyState`, `KnButton`, `KnField`, `KnIconButton`, `KnNumPad`, `KnFieldError`, `KnBadge`/`KnBadgeTone`, `KnModal`.
- `domain/validation/InvalidAmountException.kt:40` — `requireValid(Long)` is already the reject-not-default helper `Vehicle` reuses.
- `domain/validation/InvalidCutoffDayException.kt` — the pattern a new typed exception follows.

**Gotchas carried over from Story 2.1's review:**
- `KnListRow.leading` is hard-boxed to 44dp — pass `KnAvatar()` at its default size, not a 48dp icon button.
- `KnNumPad` re-syncs from `value` via `LaunchedEffect`; echo back what it emits or its digits are discarded.
- `KnField.error` replaces `hint` and stays invisible until non-null; validation timing is the screen's, not the component's.
- `KlinikNetTheme` uses `staticCompositionLocalOf` — a screen composed outside it silently gets the light defaults.
- Story 2.1 recorded that `SnackbarMaxWidth` contradicts its own KDoc and that `KnNavRail` has no scrolling; neither is this screen's problem to fix.

## Tasks & Acceptance

**Execution:**
- `domain/.../domain/parse/Rupiah.kt` -- `parseRupiahInput(String): Long?`, digits-and-spaces only, returning `null` on anything else, plus `requirePositiveRate(Long)` delegating to `InvalidAmountException.requireValid` -- NFR-6; the single parse path NFR-6 requires both sides to share
- `domain/.../domain/parse/Plate.kt` -- `normalizePlate(String): String?` (trim, uppercase, squeeze internal whitespace; `null` when blank) -- the value the unique index is built on
- `domain/.../domain/model/Vehicle.kt` -- pure data class `Vehicle(id: String, plate: String, brand: String, model: String, year: Int, color: String?, dailyRate: Long, notes: String?, isServis: Boolean)` whose `init` requires a normalised non-blank plate and calls `InvalidAmountException.requireValid(dailyRate)` -- mirrors `Payment`/`Allocation`
- `domain/.../domain/validation/VehicleHasActiveContractException.kt` -- typed exception carrying the blocking vehicle's plate -- the refusal the delete path raises
- `domain/src/test/.../parse/RupiahTest.kt` and `PlateTest.kt` -- the `PARSE_RATE` and `NORMALIZE_PLATE` matrix rows, in the module that needs no Android SDK
- `domain/src/test/.../model/VehicleTest.kt` -- construction refuses a blank plate and a rate of `0`
- `app/.../data/db/entity/VehicleEntity.kt` -- `@Entity` with `@PrimaryKey id: String`, normalised `plate` under `unique = true`, and the `CONTRACT` foreign key declared on the child -- uniqueness as a schema constraint, not a query
- `app/.../data/db/entity/ContractEntity.kt` -- the minimum contract that makes the delete refusal reachable: `id`, `vehicleId` with `onDelete = ForeignKey.RESTRICT`, `startDate`, nullable `endDate`. Widened by Story 2.5; nothing here depends on columns it lacks
- `app/.../data/db/Converters.kt` -- `LocalDate` to ISO `yyyy-MM-dd` TEXT, so lexicographic order equals chronological order
- `app/.../data/db/KlinikDatabase.kt` -- `@Database` over both entities
- `app/.../data/db/dao/VehicleDao.kt` -- a `Flow` of every vehicle, an insert that lets the unique index raise, a delete by id that the foreign key can refuse, and the two queries status derivation needs: does this vehicle have an open contract, and does this plate already exist
- `app/.../data/repository/VehicleRepository.kt` -- suspend functions mapping DAO outcomes to the three typed domain exceptions and to Indonesian messages; never exposes a DAO and never returns `Result<_, String>` carrying a domain message. Its delete is called only by tests this story: the screen exposes no delete affordance
- `app/.../ui/screens/kendaraan/KendaraanUiState.kt` -- one `UiState` data class plus the two pure filters (`matchesQuery`, `filterByStatus`) and the status derivation -- pure functions so the search and filter ACs are testable without a device
- `app/.../ui/screens/kendaraan/KendaraanViewModel.kt` -- builds the `UiState` with `combine` + `stateIn` + `distinctUntilChanged()` over the Room flow, holds the form state, and translates each domain exception into its message; no manual invalidation signal
- `app/.../ui/screens/kendaraan/KendaraanScreen.kt` -- top bar with the fleet count, `KnSearchBar`, four `KnChip` filters with counts, the `KnListRow` list, and `KnEmptyState`; every figure via `formatRupiah`; Lucide icons only
- `app/.../ui/screens/kendaraan/VehicleFormSheet.kt` -- Material3 `ModalBottomSheet` carrying the seven fields, with `KnNumPad` for the daily rate rather than a system keyboard -- the sheet rather than a dialog, because a dialog covers the numpad it exists to serve
- `app/src/main/res/values/strings.xml` -- every Indonesian string this screen shows
- `app/src/main/kotlin/net/klinik/rental/MainActivity.kt` -- replace the placeholder `Text` with the vehicle screen, keeping the desugaring tripwire
- `app/src/test/.../kendaraan/VehicleFilterTest.kt` -- the `SEARCH_QUERY` and `STATUS_FILTER` matrix rows against the pure functions
- `app/src/test/.../kendaraan/VehicleRepositoryDeleteTest.kt` -- the `DELETE_VEHICLE` matrix row: a vehicle with an open contract survives a delete and raises `VehicleHasActiveContractException`
- `app/src/test/.../kendaraan/KendaraanScreenTest.kt` -- Robolectric render: the empty state appears with the "what to do" copy, a populated list renders plate and `formatRupiah` output, and no interactive node measures below 48dp
- `app/src/test/.../kendaraan/VehicleRepositoryTest.kt` -- the `SAVE_VEHICLE` matrix rows: a valid vehicle is written, a colliding plate writes nothing and raises `DuplicatePlateException` naming the stored plate, and a rate of `0` writes nothing and raises `InvalidAmountException`
- `app/src/test/.../kendaraan/VehicleFormTest.kt` -- zero and negative rates are rejected with a visible message on screen and no write

**Acceptance Criteria:**
- Given an app opened with no data, when the fleet screen renders, then the empty state says what to do (`Belum ada Kendaraan` / `Tambahkan mobil pertama untuk mulai mencatat sewa.`) with a `Tambah Mobil` action -- not a bare "no data" and not a screen that looks like an error
- Given two vehicles stored, when one is edited, then the other is unchanged and the edited vehicle's `dailyRate` is still a `Long` reaching the database -- the prototype's string-rate corruption has no path here
- Given the fleet screen, when the owner types into the search bar, then only matching rows remain and the same list is still the list -- filtering, never navigation to a second screen
- Given any status chip, when it is tapped, then the list narrows to that status and the other chips remain reachable; there is no route change
- Given a save attempt, when it succeeds, then the new vehicle is visible in the list without a manual refresh
- Given a vehicle with an open contract, when the repository's delete is called, then the row is still there and `VehicleHasActiveContractException` is raised naming the vehicle -- the AC's guarantee holds at the data layer, and no delete control appears on the screen because nothing yet persists a deletion reason
- Given the app module, when it builds and its unit tests run, then `:app:testDebugUnitTest` and `:domain:test` both pass, and `git grep -n "androidx.compose.material.icons" app/` returns nothing

## Design Notes

**The delete affordance is deferred; the guard is not.** The story's AC asks that deleting a rented vehicle be *refused* — never that a delete button exist — so the guarantee is satisfied where it actually holds: `ContractEntity.vehicleId` carries `onDelete = ForeignKey.RESTRICT`, SQLite refuses the delete, and `VehicleRepository.delete` turns that into `VehicleHasActiveContractException`. `DELETE_VEHICLE` is therefore a real, asserted behaviour with no UI attached.

Shipping the button would have required a destination for the mandatory reason, and none exists yet. The prototype's answer was to collect the reason and drop it (`cars.html:329` passes no reason to `store.deleteCar`; `UI.lastConfirmReason` has no reader; `lastAuditTrail` has no writer) while the dialog's own subtitle promised an audit trail — the false promise this epic forbids. The alternative, an `AUDIT` table, would put a new table into `Epic 2`'s schema and hand `Epic 5` an export obligation for one field in one dialog. So the button waits for the story that owns the audit sink. If deletion ships there as a hard delete rather than an archive, that story's design note must say what happens to the allocations and payments pointing at the vehicle — arrears is computed from stored allocation rows, so an orphan here is a silently wrong number on another screen.

**Because `CONTRACT` exists, status is derived rather than approximated.** `VehicleStatus` resolves to `SERVIS` when the manual marker is set, `TERSEWA` when an open contract exists, and `TERSEDIA` otherwise. That is Story 2.3's rule arriving early, and it is the right order: a stored status column would have to be deleted later, whereas a derivation gets its rules extended rather than replaced. What Story 2.3 keeps is everything about what the statuses *mean* — that `servis` never stops billing, that the only thing that stops a charge day is a service exception, and that the home-screen counts read the same rule.

**`parseRupiahInput` lives in `domain/`, not in `data/`.** The architecture says the lowest layer, and `domain/` is lower and pure-JVM, so it returns `null` for `"abc"`, `"2.5"`, `""` and `"-1"` alike. The form shows the message; the repository rejects the same input with the same rule. Two screens can therefore both pass the same UI check, which is the entire point of NFR-6. The numpad already produces clean digit strings, so this parser guards the paths that do not come from `KnNumPad`.

**The form is a bottom sheet because of the numpad.** Seven fields plus an on-screen numpad is exactly the "many inputs or a tall keyboard" case the epic context names, and a `KnModal` would cover the numpad it exists to serve. Material3's `ModalBottomSheet` is already on the classpath, so the sheet is the native component rather than a new one in the design system.

**Search matches four fields and stops there.** Plate, brand, model and colour — the prototype's behaviour. Year, notes, daily rate, status and renter name are not searched because an owner looking for "Agya" is not looking for a document containing the word "nilai".

## Verification

**Commands:**
- `./gradlew :domain:test` -- expected: SUCCESS -- plate normalisation and rupiah parsing, with no Android SDK present
- `./gradlew :app:testDebugUnitTest` -- expected: SUCCESS -- every row of the I/O matrix covered by a test that ran
- `./gradlew :app:assembleDebug` -- expected: SUCCESS -- KSP generates the Room implementation and `Converters` resolves
- `git grep -n "androidx.compose.material.icons" app/` -- expected: NO MATCH
- `git grep -n 'text\s*=\s*"' -- 'app/src/main/kotlin/net/klinik/rental/ui/screens/'` -- expected: NO MATCH -- no user-facing literal in a screen body