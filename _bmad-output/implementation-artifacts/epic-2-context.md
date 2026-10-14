# Epic 2 Context: Armada dan orang

<!-- Generated from planning artifacts. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Andi can store and see his whole fleet, his renters, and their contracts — the first data he types, and data that stands on its own. This epic also delivers the design system and every core component, because master data is the first place those components get used and the last cheap moment to build them properly instead of letting accessibility leak through per-screen inline styles.

## Stories

- Story 2.1: Design system and core components used by every later screen
- Story 2.2: Storing the vehicle fleet
- Story 2.3: Seeing fleet availability status
- Story 2.4: Storing renter data
- Story 2.5: Opening a rental contract
- Story 2.6: Marking non-billable days
- Story 2.7: Ending a rental contract
- Story 2.8: Four states on every screen, and the unbreakable principles

## Requirements & Constraints

**Money and input robustness.** Every money value — vehicle daily rate above all — is `Long` in whole rupiah, in the domain and in the database. Zero or negative rates are rejected outright, never silently replaced by a fallback. Bad input is rejected in the data layer, not only in the UI: two screens can both pass the same UI check. Parsing is one function in the lowest layer that returns null and refuses. No hidden defaults anywhere.

**Uniqueness is a data-layer constraint, not a UI check.** Vehicle plate and renter name each need a real unique index. A renter's name is required, non-blank, and unique in the app — it is the key that reconciles against bank mutations. A plate is required and unique *after normalization* (uppercased, spaces squeezed), so `b 1235 tf` and `B 1235 TF` collide. National ID number and address fields are deliberately absent from the renter form — out of scope, and not collected.

**One active contract per vehicle AND per renter**, enforced by a partial unique index. The prototype's `currentRenterId` display convention is rejected: it can drift and prevents nothing. Contract daily rate is copied from the vehicle at open time and stays constant, so a later vehicle rate change cannot silently reprice a running contract; negotiated rates are allowed. A start date in the past is accepted, because contracts that began before the app existed are real.

**Status is display, not truth.** Tersewa and tersedia are *derived* from whether an active contract exists. Servis is a manual marker that never stops billing — the only thing that stops a charge day is a Service Exception recorded on the contract for a specific date, and there must be no second mechanism that silently halts billing. Duplicate exception dates are rejected. Exceptions attach to their original date and do not roll into the next period.

**Ending a contract is one transaction** — status, remaining arrears, and vehicle status may not half-change. Any surplus does not vanish; it becomes credit on that contract. A finished contract cannot be edited (rate, schedule, exceptions), but its deposit history stays readable and its deposits stay cancellable with a mandatory reason. A finished contract with a balance still counts toward active arrears until settled.

**Scale and first run.** Target device is a Redmi Pad 2 with 20 vehicles and 2,000 deposits. The app runs from empty — no accounts, no wizard, no seeded sample data.

## Technical Decisions

**Two Gradle modules.** `domain/` is a pure-JVM `kotlin("jvm")` module with no `android {}` block at all, so Gradle — not review discipline — makes an Android import a build failure, and `./gradlew :domain:test` runs with no Android SDK installed. `app/` holds data and UI. Dependencies point one way: domain → data → ui.

**Data model.** `VEHICLE` (plate unique, `dailyRate` Long > 0, `status` display-only); `RENTER` (name unique, phone, nullable bankAccount, emergencyContact); `CONTRACT` (renterId, vehicleId, startDate, frozen `dailyRate`, `workSchedule` `6days`/`7days`, `offDay` where 0 = Sunday, nullable endDate, nullable settledThroughDate, status); `SERVICE_EXCEPTION` (contractId, date — unique on the pair). Primary keys are `uuid` columns in Room. **Follow the existing precedent for domain ids: `id: String`, not `UUID`** — the domain import surface excludes `java.util.UUID`, so the id arrives as a string from the layer that owns persistence while the database keeps it a uuid column.

**Dates.** `LocalDate` for date-only values, `Instant` only for creation and cancellation timestamps. Stored as ISO `yyyy-MM-dd` TEXT so lexicographic order equals chronological order and the column stays sortable and indexable; Room 2.8 ships no built-in `java.time` support, so `Converters.kt` is required. Do not "optimize" to integers later. "Today" comes from the single `Clock` abstraction in domain — no hardcoded date constants, no `SIM_TODAY`.

**Writes and reads.** Any write touching more than one row — or one row that feeds a calculation — runs as a `@Transaction` on a suspend repository function. The repository never exposes a DAO to `ui/`, and no `Flow` writes. Domain throws specific typed exceptions (`DuplicatePlateException`, `InvalidAmountException`, `ContractAlreadyActiveException`); the data layer turns them into Indonesian messages, never `Result<_, String>` carrying a domain message. On a write or parse failure the app fails loudly — it never resets to defaults, because data that disappears and looks like a fresh account is worse than a crash.

**UI state.** One `UiState` data class per screen exposing a `StateFlow`, built with `combine` + `stateIn` + `distinctUntilChanged()` over Room flows. No manual invalidation signal: Room's invalidation tracker already re-emits, and adding a manual one creates two refresh mechanisms that can disagree. No spinners — loading is a skeleton.

**Presentation.** One `formatRupiah(Long)` used everywhere; money is never assembled by hand in a template. All UI strings are Indonesian and centralized — no hardcoded text in `res/values/strings.xml`. Icons come from Lucide (`com.composables:icons-lucide`); **no Material Icons**. Never log amounts, renter names, PINs, or recovery codes.

**Arrears boundary.** These screens display stored data as-is. They must not compute arrears, overdue days, risk tier, credit, or anything allocation-derived — those come from the domain calculation engine behind a single aggregate query, and the same screens must keep working unchanged once those numbers appear. "Mark settled through date" (`settledThroughDate`) is not built here; it arrives with Epic 3.

## UX & Interaction Patterns

**Tokens.** Brand coral/rose scale `--brand-50 #FFF1F4` → `--brand-500 #FF385C` (primary) → `--brand-700 #C01336`; warm neutrals `--warm-0 #FFFFFF` → `--warm-900 #1A1715` (primary text), page background `--warm-50 #F9F7F4`. Four semantic colors — success `#0E9F6E`, warning `#D97706`, danger `#DC2626`, info `#2563EB` — each with a companion background, and no fifth status color. **Semantic foreground does not change in dark theme; only the background does.** Dark surfaces use literal hex (`#14100F`, `#1F1A19`, `#2A2321`), not the warm scale. Fraunces for display and large figures, Plus Jakarta Sans for body, 14sp as the floor for labels and data. 4dp spacing base; radii 10 / 14 / **20 for cards** / 26 / full-pill. Elevation is three levels of soft warm shadow plus a brand-colored shadow reserved for primary buttons.

**Accessibility is measured, not assumed.** 48dp minimum touch target on every interactive element including badges, chips, and in-row icon buttons — that measurement belongs in a test. A 3dp brand-colored focus ring on every interactive element, with focus order following visual order. Text contrast at or above 4.5:1 across all three text levels, in both themes. Rupiah figures use `tabular-nums` so columns of money can be compared — that comparison is the owner's actual morning job.

**Component inventory.** Button (5 variants — primary, secondary, ghost, danger, success — × 4 sizes, icon variant exactly 48dp pill), Card, KPI tile, Badge, Chip, Field/Input with error text hidden by default, Search bar, Table and list row, Avatar, Modal, Snackbar, Empty state, PIN grid, Nav rail, Bottom nav, Top bar, Numpad. Errors say what is wrong *and* how to fix it, and appear on blur rather than mid-typing.

**Numpad.** Nominal entry uses the on-screen numpad, never the system keyboard — it is used while standing and holding something, so large touch targets beat typing speed. 3×4 large keys, in-progress digits shown large and tabular above the grid, 12-digit cap, save rejects zero or negative with a red flash rather than accepting quietly, and every key fires a haptic.

**Layout.** One Activity, one NavHost, identical graph in both orientations — only the composition differs, never the business logic. Landscape: 88dp nav rail plus master-detail. Portrait: bottom nav plus a single column, with master-detail collapsing to list → detail. Master-detail applies to vehicles and renters. Rail order follows usage frequency. Every screen gets all four states: skeleton loading, an empty state that says *what to do*, an error state that never looks like a new account, and a success snackbar that does not change screen state.

**Quick actions and forms.** A floating action button bottom-right carries a speed dial of three labelled actions. It appears on every screen **except** the home screen, whose four-tile grid is the single entry point to quick actions — two doors to the same action is how one of them goes stale. Forms with many inputs or a tall keyboard use a bottom sheet rather than a modal dialog, because on a tablet a dialog covers the numpad it exists to serve. Three confirmation levels: reversible actions ask nothing, actions that change numbers get a dialog naming the impact, actions that delete data get a dialog plus a mandatory reason.

**Voice.** Indonesian, plain, and honest. Money is written in full (`Rp 250.000`), dates as `23 Sen`, `24 Sel`. Term names match the owner's daily vocabulary rather than being translated. Arrears always carry both a figure and a day count. A gesture is never the only path to an important action.

## Cross-Story Dependencies

Story 2.1 comes first — every other story renders through its components and tokens. Story 2.8 defines the four-state contract the remaining seven screens must satisfy, so it lands with the first screen rather than at the end. Story 2.3's derived status depends on an active contract existing, so 2.5 must exist before status display is meaningful; 2.6's edits lock once 2.7 ends a contract. Epic 3 consumes this epic's renter, vehicle, and contract records plus the numpad; Epic 4's home-screen fleet counts derive from this epic's status rule; Epic 5 exports the schema this epic's tables define. Nothing here is blocked by a later epic.