---
title: 'Story 4.4 — Identitas usaha untuk pesan pengingat'
type: 'feature'
created: '2026-10-18'
status: 'in-review'
route: 'dispatch'
review_loop_iteration: 0
baseline_revision: '6dda9b6315f31246754330ee7458499c475c29b7'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-4-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The reminder message in Story 4.2 has to name the business, and there is nowhere to
put it. The prototype hardcodes `PT Klinik Net Rent Car` in three places, so the owner cannot
name their own business and any message the app sends speaks for the app rather than for them.

**Approach:** Three settings keys behind one form on the existing `Pengaturan` screen, saved
as a block. The default name is the prototype's, pre-filled and editable rather than compiled
in. The address and phone are stored and available; nothing renders them until a screen has a
reason to.

## Boundaries & Constraints

**Always:**
- **All three save as one block.** A half-saved identity — a name with no address — is an
  identity the owner did not enter, and the message would quote half of it.
- **The name is read from storage wherever it appears**, never compiled into a string. The
  prototype's three hardcoded copies are exactly what this replaces.
- **The default name is pre-filled and editable**, not a hardcode: a first run that opens with a
  blank field invites an empty business name in every outgoing message.
- **A blank name is refused.** An empty string is worse than the default, because it looks
  deliberate.
- **Changing the identity never touches a past deposit.** A deposit records what the owner took,
  not what the business was called that day.
- **Identity lives in `settings`, not device preferences**, so it is covered by the app's own
  backup and restore. Only the PIN and recovery codes are excluded from export — the business
  name is not, so a restored app still sends the right name.

**Never:**
- **No message is composed here.** This story stores the identity; Story 4.2 builds the message.
  A template in this story is a second wording of 4.2's.
- **No display of the address or phone on any screen.** The acceptance criterion is explicit:
  *"kalau ternyata tidak dipakai di layar mana pun, keduanya tidak muncul di layar mana pun."*
  Storing is not displaying.
- **No money in `settings.value`.** It is a `String` column and must never hold a figure
  (AR-11) — the same rule the cutoff day obeys.
- **No validation on the address or phone beyond blank-trimming.** They are free text the owner
  types for outsiders; a format rule here would refuse real addresses.
- **No new table and no migration.** `settings` already exists and `SettingDao.putSetting`
  already upserts.
- **No second copy of the default name** in the UI layer. It is one constant, read on demand.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| IDENTITY_DEFAULT | The form opened, nothing saved yet | Name pre-filled with the prototype's default, editable; address and phone blank | No error expected |
| IDENTITY_SAVE_ALL | Name, address and phone entered | All three stored together; the form closes with a confirmation | No error expected |
| IDENTITY_SAVE_PARTIAL | Only name and phone entered | Both stored; address stays absent rather than becoming `""` | No error expected |
| IDENTITY_BLANK_NAME | Name cleared, address and phone filled | Refused; nothing written; the message lands on the name field | `BlankIdentityNameException` / field error |
| IDENTITY_WHITESPACE_NAME | Name is spaces | Refused — blank is blank, whatever produced it | Same as `IDENTITY_BLANK_NAME` |
| IDENTITY_EDIT | An existing identity changed | New values stored; every previous deposit reads back exactly as recorded | No error expected |
| IDENTITY_BACKUP | The database is exported and restored | The business name survives; the message still names the right business | No error expected |

</frozen-after-approval>

## Decisions taken at the gate

- **The identity form lives on `Pengaturan`, beside the account list**, because the acceptance
  criterion names that screen and a second settings destination would be a nav item for one
  form. A section header separates the two blocks so neither reads as the other's field.
- **The address and phone are stored and shown in the form, nowhere else.** Displaying them on
  the settings screen is where the owner typed them; showing them anywhere else is the thing the
  criterion forbids.

## Code Map

- `app/.../data/db/dao/SettingDao.kt:33-58` -- `findValue`, `findInt`, `putSetting`. **The whole
  storage layer already exists**, upsert included, and its KDoc already argues that a default is
  supplied on read rather than seeded. That argument is why the business name follows the same
  rule: a stored row the owner never wrote is a value they did not choose.
- `app/.../data/db/entity/SettingEntity.kt` -- key/value rows. Its KDoc already records that
  `settings` is chosen over device preferences precisely so backup covers it (FR-16).
- `app/.../ui/screens/pengaturan/PengaturanScreen.kt:73` -- the host screen. Currently an
  account list plus an empty state; the identity block is a second section above it.
- `app/.../ui/screens/pengaturan/PengaturanViewModel.kt:45,198` -- the ViewModel and its
  `saveForm`. **A second form means a second state**, held beside `AccountFormState` rather than
  inside it: one form per sheet is the rule every other screen follows.
- `app/.../ui/screens/pengaturan/PengaturanUiState.kt:83,127` -- `AccountFormState` and
  `AccountField`, the shape to mirror for the identity's own field enum.
- `app/.../data/repository/PaymentRepository.kt:29` -- `KEY_CUTOFF_DAY`, the precedent for a
  settings key owned by a repository rather than by a screen.
- `app/.../ui/strings/RStrings.kt` -- the accessor convention; every string has one.

**Gotchas carried over:**
- `Screens.Pengaturan`'s callbacks are all **required, not defaulted** — a defaulted lambda is a
  control that renders and does nothing, which this repo has shipped twice.
- `KlinikNetTheme` is `staticCompositionLocalOf`; wrap every Robolectric render.
- One `setContent` per Compose test; `performSemanticsAction(SemanticsActions.OnClick)` rather
  than `performClick`, because `ModalBottomSheet` renders in its own window.

## Tasks & Acceptance

**Execution:**
- [ ] `app/.../data/repository/BusinessIdentityRepository.kt` -- **new**: the three keys, a
  `save(name, address, phone)` that writes them in one transaction, and a default name constant.
  A repository rather than a screen-held key, for the reason `KEY_CUTOFF_DAY` is.
- [ ] `app/.../ui/screens/pengaturan/PengaturanUiState.kt` -- add `IdentityFormState`,
  its setters, `firstMissingIdentityField()`, and an `IdentityField` enum.
- [ ] `app/.../ui/screens/pengaturan/PengaturanViewModel.kt` -- add `openIdentityForm`,
  `onIdentityChange`, `saveIdentity`, `dismissIdentity`, beside the account form's own.
- [ ] `app/.../ui/screens/pengaturan/PengaturanScreen.kt` -- the identity section and its sheet.
- [ ] `app/.../ui/shell/NavGraph.kt`, `MainActivity.kt` -- the new callbacks, required.
- [ ] `app/src/main/res/values/strings.xml`, `RStrings.kt` -- labels, hints, refusals.
- [ ] `app/src/test/.../pengaturan/BusinessIdentityRepositoryTest.kt` -- **new**: the matrix
  against a real database, including the block-write and the blank name.
- [ ] `app/src/test/.../pengaturan/IdentityFormTest.kt` -- **new**: the form, and a
  **click-through** on save.

**Acceptance Criteria:**
- Given the form has never been saved, when it opens, then the name is pre-filled with the
  prototype's default and the owner can change it
- Given all three fields filled, when saved, then all three are stored as one block
- Given the name cleared, when saved, then it is refused, nothing is written, and the message
  lands on the name field
- Given an identity is changed, when an older deposit is read back, then it is unchanged
- Given the app builds, when tests run, then `:domain:test`, `:app:testDebugUnitTest` and
  `:app:assembleDebug` all pass

## Implementation Notes

**The required-callback rule paid for itself before the tests were written.** Making the four
identity callbacks required — rather than defaulted like several of their siblings — produced
eleven compile errors across five test files at the first `:app:compileDebugKotlin`. Every one
of them was a call site that would have rendered the identity block's control and reached
nothing, which is the defect this repo has shipped twice. The compiler found them; a rendering
test would not have.

**`SettingDao` needed one new method, and the absence was worth noticing.** Nothing in this app
could delete a settings row before this story, because every key written so far was permanent.
The business address and phone are the fields that change when a business moves, so a setting
that can be set once and never cleared is a constant wearing a settings screen's name. `null`
and `""` are now kept distinct — a field the owner cleared is *absent*, which a future message
can tell from one they never filled in.

**Two test failures were the harness's fault, not the code's, and both were worth the detour.**
A `PAYMENT` row needs its vehicle, renter and contract to satisfy the `RESTRICT` foreign keys,
so the "editing the identity leaves a deposit untouched" case failed on its own fixture. And
`performTextInput` **appends** — typing over a field that already holds the default name
produced "Rental SAYAPT Klinik Net Rent Car", so the assertion was measuring the harness. The
repo's own `RenterFormTest` uses `hasSetTextAction() and hasAnyAncestor(hasTestTag(...))`
rather than the tag alone, because `KnField` puts the tag on the field while the *input* is a
descendant; reading the existing pattern beat guessing twice.

## Spec Change Log

## Review Triage Log

### 2026-10-18 — Review pass 1
- verdicts: 45 findings — high 1, medium 9, low 19, false 3, maybe-false 14
- findings:
  - `[high]` `[patch]` **editing an existing identity silently erases its address and phone** (blind-hunter B4, B5). `IdentityRow` carries only `name`, so `openIdentityForm` opens the other two fields empty; `toBusinessIdentity` turns an empty field into `null`; and `clearIfAbsent` **deletes** the stored row for a `null`. So the owner opens the form to correct a spelling, saves, and the address and phone they entered months ago are gone — with a success confirmation naming the save. `IdentityFormTest:67` *asserts* the empty opening as deliberate ("a placeholder there would invite a value the owner never chose"), which is a real argument for a fresh form and exactly wrong for an edit. Verified end to end: `PengaturanViewModel.kt:332-338`, `PengaturanUiState.kt` `IdentityFormState` and `toBusinessIdentity`, `BusinessIdentityRepository.clearIfAbsent`.
  - `[medium]` `[patch]` `IdentityFormSheet` defaults `onDismissError` and `onDismissSuccess` to `{}` (blind-hunter B1; edge-case E11). Both banners are then undismissable, and `Screens.Pengaturan`'s own KDoc forbids the default — `SettleContractSheet` shipped this exact pair in Story 3.6.
  - `[medium]` `[patch]` the identity sheet receives the **account screen's** `state.errorMessage`, `onErrorDismiss` and `snackbarMessage` (blind-hunter B2). Two forms share one channel, so an account refusal renders inside the identity window and a dismissal clears the other form's message. `PengaturanScreen.kt:238-247`.
  - `[medium]` `[patch]` `openIdentityForm` reads `storedIdentity.value` which is populated once at construction (edge-case E8, E22). A first tap before that read lands shows the default rather than the owner's stored name. `storedIdentity` is a `StateFlow` with no `Flow` behind it, so the section only refreshes after this screen's own save.
  - `[medium]` `[patch]` the two forms have **no mutual exclusion** (edge-case E9, E10). `PengaturanScreen.kt:191` suppresses the screen banner when either is open, but nothing stops both being open — two `ModalBottomSheet` windows over one state, the defect Story 2.8's review found as its only `high`.
  - `[medium]` `[patch]` `identity()` and `businessName()` each issue their own `findValue` reads (blind-hunter B8), so one identity is three DAO reads and the name alone is two.
  - `[medium]` `[patch]` the blank-name refusal is asserted by `onNodeWithText` only (blind-hunter B13) — nothing proves the message lands on the **name field** rather than anywhere on the sheet, which is what the KDoc claims.
  - `[medium]` `[patch]` no test pins that Save is disabled after a blank name typed **through `onChange`** (blind-hunter B14); the existing test hand-builds `IdentityFormState(name = "   ")`.
  - `[medium]` `[patch]` `IdentityFormState` imports `BusinessIdentityRepository` for `DEFAULT_NAME` (blind-hunter B11, B12; edge-case E13), and its own default parameter is a **second place the default lives** — which the spec's "no second copy of the default name" boundary forbids.
  - `[medium]` `[patch]` `IdentityFormTest`'s fixture helper defaults `onChange` and `onSave` to no-ops (edge-case E18), so a test that forgets to pass one passes vacuously — the same trap `Screens.Pengaturan`'s KDoc exists to prevent.
  - `[low]` `[patch]` `PengaturanScreenTest` reaches the identity block through defaulted `onOpenIdentity` in nine existing tests (edge-case E19), so the block renders but is unreachable in every older test.
  - `[low]` `[patch]` the identity action's touch target is unmeasured (verification-gap G3). `KnButtonSize.Small` pads its hit area back out to 48dp through `minTouchTarget` — **so this is a test gap, not a defect**, verified by reading the enum. It is still worth the assertion, because Story 4.1 shipped the identical 38dp failure on this floor and only a measurement caught it.
  - `[false]` `[reject]` `SettingDao.deleteSetting` is untested at the DAO boundary (verification-gap G2) — retracted by the reviewer themselves, and correct: `clearing a field afterwards removes it` exercises it through the repository. Recorded so it is not re-litigated.
  - `[low]` `[patch]` no test pins `KeyboardType.Phone` on the phone field, which is the sheet's own stated reason for choosing it (blind-hunter B15).
  - `[low]` `[patch]` the name field uses a `hint` where `KnField`'s `placeholder` drives the display (edge-case E23), so the field shows no placeholder.
  - `[low]` `[patch]` `RStrings.pengaturanIdentitySection()` and its siblings are dead — the screen reads `stringResource` directly (edge-case E21).
  - `[low]` `[patch]` `Screens.Pengaturan`'s KDoc says "Eleven callbacks" while the class no longer matches that count (blind-hunter B19).
  - `[low]` `[patch]` trailing newlines missing on the spec and `SettingDao.kt` (blind-hunter B18).
  - `[low]` `[patch]` `BusinessIdentityRepositoryTest` seeds its entities fully-qualified instead of imported, unlike every sibling test file (blind-hunter B10).
  - `[low]` `[patch]` `saveIdentity`'s re-read message can briefly disagree with the header if a save lands between them (edge-case E6), and a `BlankIdentityNameException` after dismissal writes onto a closed sheet (edge-case E7).
  - `[medium]` `[defer]` nothing proves a **changed** business name reaches an outgoing message (intent-alignment R-b, I1). No message exists — that is Story 4.2, the next story — so the AC's own proof has no surface yet. Recorded as this story's known boundary, not a gap in it.
  - `[low]` `[defer]` `IDENTITY_BACKUP` is asserted by proxy: the rows live in `settings` and are read back through the DAO, because export is Story 5.1. A `SharedPreferences` implementation would pass every other test and still lose the name on restore.
  - `[false]` `[reject]` address/phone reach a render path (edge-case's own verification) — refuted: they are only passed to the save call, and the identity section renders the name alone.
  - `[maybe-false]` `[reject]` `clearIfAbsent` deleting on blank is accidental data loss (blind-hunter B9). It is correct **for a value the owner just cleared**; the loss is upstream of it, where an edit opens the fields empty. Same root as the `high`, folded there rather than counted twice.
  - `[maybe-false]` `[reject]` 16 further low findings about KDoc phrasing, unused imports and comment accuracy across `IdentityFormSheet`, `PengaturanUiState` and `SettingDao`, each confirmed cosmetic by reading the cited line.

## Design Notes

**The storage already exists, and its KDoc already settles the default question.** `SettingDao`
supplies a default on read rather than seeding a row, with the reasoning that a stored value the
owner never chose makes "unset" indistinguishable from "set to it". The business name follows
that rule rather than inventing a second one — which is also what makes the name default
readable by Story 4.2 without a migration having run.

**Backup is already correct and this story cannot break it.** `settings` exists precisely
because device preferences are outside the app's own export; only the PIN and recovery codes are
excluded. So "the identity survives a restore" needs no work here — it needs a test that says
so, because the property is invisible until someone restores a backup and finds the wrong name
in an outgoing message.

**Address and phone are stored but unrendered, and that is deliberate.** The criterion says so
twice, in the affirmative and then in the negative. The temptation is to show the phone as a
`telp:` link on some future screen; doing that before a screen needs it would put business
contact details on a screen whose job is something else.

## Verification

**Commands:**
- `./gradlew :domain:test` -- expected: SUCCESS
- `./gradlew :app:testDebugUnitTest` -- expected: SUCCESS
- `./gradlew :app:assembleDebug` -- expected: SUCCESS