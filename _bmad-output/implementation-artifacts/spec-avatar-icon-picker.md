---
title: 'Renter avatars are a picked icon, not typed emoji'
type: 'feature'
created: '2026-10-21'
status: 'in-progress'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: '68c2129'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/spec-ui-chrome-theme.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The renter form asks the owner to *type* an emoji (`renters.html:342` is the
prototype's own `<input placeholder="Emoji">`), and the first pass at this shipped exactly that: a
free-text field feeding `KnAvatar`'s `Text`. Two consequences, both real:

1. Typing an emoji on a tablet keyboard is a hunt through a picker the OS owns, and the result is
   whatever the system font decides a face looks like — inconsistent with every other icon in an
   app whose whole visual language is a hand-drawn set.
2. A typed emoji is unvalidated free text. A stray character, a multi-codepoint sequence, or an
   empty-looking glyph all reach the disc, and nothing can refuse them without deciding which
   characters count as a face.

**Approach:** Ship the 45-face icon pack as drawables and make the avatar a **pick from a grid**.
The stored value becomes a stable key (`avatar_07`), not display text; `KnAvatar` renders a
`painterResource` when the key is known and falls back to initials when it is not.

The pack exists: `Group 4.png` is a 7×7 sheet of 45 transparent-background faces, already cropped
into `app/src/main/res/drawable-nodpi/avatar_01.png` … `avatar_45.png` at 256×256 (~5 MB total).

## Boundaries & Constraints

**Always:**
- **The stored value is a key, never a glyph.** `avatar_07` survives a font change, an emoji
  revision, and a locale; a typed emoji does not.
- **Unknown or absent keys fall back to initials.** `KnAvatar` must never draw an empty disc or
  crash: a backup restored from a build with a different pack, or a row written before this
  feature, both land here.
- **The column stays nullable and free-text.** `avatar TEXT DEFAULT NULL` (already migrated in
  v8→v9) accepts any key; the picker constrains what the app writes, not what the schema holds.
  A `CHECK` constraint would refuse a future pack's keys and would need another migration.
- **The picker is one screen's grid, not a dialog.** The form is already a bottom sheet; a nested
  modal is the shape `ExceptionFormSheet`'s KDoc argues against.

**Never:**
- Do not keep the text field. A free-text field beside a grid would be two writers for one value,
  and the text one can store something the grid cannot render.
- Do not ship the 5014×5014 sheet. Its bitmap is ~100 MB decoded; the app would OOM on the tablet
  it targets. The cropped drawables exist for this reason.
- Do not add a migration. `avatar` is already there from v8→v9; only its *meaning* changes from
  "an emoji" to "a pack key", and both are strings.

**Decided at the gate (2026-10-21):**

- **Keep the v8→v9 migration and replace the UI.** The emoji work in the working tree is the same
  column and the same plumbing; only the input control and the renderer change.
- **45 drawables, not one sheet.** A single `BitmapPainter(srcOffset, srcSize)` keeps one file but
  holds the whole decoded bitmap for every screen showing an avatar, and one pixel of drift in the
  offsets is a visible seam. Separate files load only what is drawn.
- **No "none" entry in the grid beyond clearing.** A `null` avatar is already the fallback-to-
  initials state, and a dedicated "no avatar" tile would make the absence a choice rather than a
  default. The picker offers the 45 faces; clearing the selection returns to initials.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| PICK | owner taps face 7 in the grid | form holds `avatar_07`; the sheet's preview disc shows it | N/A |
| SAVE | form saved with `avatar_07` | stored verbatim; the list row and detail header draw face 7 | N/A |
| CLEAR | owner taps the selected face again | form holds `""`; the disc falls back to initials; save stores `null` | N/A |
| UNKNOWN_KEY | stored `avatar_99`, or a key from a future pack | initials are drawn; no crash, no empty disc | `KnAvatar` resolves by name and falls back |
| NULL | renter recorded before this feature | initials | N/A |
| RESTORE | a backup carrying `avatar_07` | restores; the row draws face 7 | the reader already reads the column |

</frozen-after-approval>

## Code Map

- `app/src/main/res/drawable-nodpi/avatar_01.png` … `avatar_45.png` — the pack, already cropped.
  256×256, transparent, `nodpi` so one file serves every density.
- `app/src/main/kotlin/net/klinik/rental/ui/components/Row.kt:424-463` — `KnAvatar`. It already
  takes `avatar: String?` and draws it as `Text`. The change is: resolve the key to a drawable and
  `Image` it, falling back to the initials `Text` when the key is unknown or null. `AVATAR_KEYS` and
  `avatarDrawable` live here too, because the pack is a property of the component that draws it.
- `app/src/main/kotlin/net/klinik/rental/ui/screens/penyewa/AvatarPicker.kt` — the grid.
- `app/src/main/kotlin/net/klinik/rental/ui/screens/tunggakan/TunggakanUiState.kt` — `ArrearsRow`
  gains `renterAvatar`, and `toRows` gains a `renterAvatars` lookup. The prototype draws the
  renter's face on the arrears row too (`arrears.html:152`), so leaving it off would show
  initials on the one screen the owner chases money from.
- `app/src/main/kotlin/net/klinik/rental/ui/screens/kontrak/ContractPicker.kt` — a `rowAvatar:
  (T) -> String?` parameter, defaulted to `null`. The picker lists **two** things — renters, who
  have a face, and vehicles, which do not — so the function is per-row rather than one value.
- `app/src/main/kotlin/net/klinik/rental/ui/screens/penyewa/RenterFormSheet.kt` — replace the
  `KnField` at the `TAG_FIELD_AVATAR` block with the picker.
- `app/src/main/kotlin/net/klinik/rental/ui/screens/penyewa/PenyewaUiState.kt:133-150` —
  `RenterFormState.avatar` stays a `String`; its KDoc changes from "an emoji" to "a pack key".
- `app/src/main/kotlin/net/klinik/rental/ui/screens/penyewa/PenyewaViewModel.kt:236-243` and
  `:291-305` — the form↔domain plumbing is already in place; no change beyond the KDoc.
- `app/src/main/res/values/strings.xml` — `penyewa_field_avatar` / `penyewa_avatar_hint` change
  from "type an emoji" to "pick one".
- `app/src/test/kotlin/net/klinik/rental/data/backup/DemoDataset.kt` — the demo's eight emoji
  become eight keys.

## Tasks & Acceptance

**Execution:**
- [x] `Row.kt` — `KnAvatar` resolves `avatar` through a key→drawable map and draws an `Image`;
      unknown/blank/null falls back to the existing initials path. `AVATAR_KEYS` and
      `avatarDrawable` live here, because the pack belongs to the component that draws it.
- [x] `AvatarPicker.kt` (new) — the grid: 45 tappable discs, six across, the selected one ringed,
      a second tap on it clearing the choice.
- [x] `RenterFormSheet.kt` — the avatar `KnField` becomes the picker.
- [x] `TunggakanUiState.kt` + `TunggakanViewModel.kt` + `TunggakanScreen.kt` — the arrears row
      carries and draws the renter's face, as the prototype does.
- [x] `ContractPicker.kt` + `ContractFormSheet.kt` — a per-row `rowAvatar`, supplied by the
      renter picker and defaulted to `null` by the vehicle one.
- [x] `strings.xml` — the label and hint describe picking, not typing.
- [x] `DemoDataset.kt` — the eight prototype renters get eight distinct keys.
- [x] `AvatarPickerTest.kt` (new) — the pack's size and keys, the unknown-key fallback, and the
      pick / clear behaviour.
- [x] The version bump: `SCHEMA_VERSION` 8→9, `MIGRATION_8_9`, and the `avatar` column on
      `RenterEntity`, `Renter`, the repository mapping, and the backup read/write.

**Acceptance Criteria:**
- Given the renter form, when the owner taps a face, then that face shows as selected and saving
  stores its key.
- Given a renter with no avatar, then the disc shows initials — never an empty circle.
- Given a stored key the current pack does not carry, then initials are drawn and nothing throws.
- Given the app builds, then `:domain:test`, `:app:testDebugUnitTest` and `:app:assembleDebug` pass.

## Implementation Notes

**The crop took three attempts, and only the third was right.** `Group 4.png` is a 7×7 sheet of
45 faces on a transparent background, and the first two crops both leaked:

1. *Crop to the cell.* The cell is 716px but a face is ~500px, so a crop that filled the cell
   caught the neighbour's hair — the first attempt's `avatar_20` had a sliver of another face
   down its left edge.
2. *Crop to the face's alpha bounding box, clamped inside the cell.* The sliver survived, because
   the bounding box was *wider* than the face: measuring the row's opaque runs showed the sliver
   was a separate 14px run inside the same cell, and the bounding box spanned both.
3. *Keep only the largest connected component.* The sliver is a separate component, so dropping
   everything but the biggest gumpalan removed it. Verified on the first, a middle, and the last
   cell.

**The version bump broke eight restore tests, which is the point.** `BackupJsonReader` refuses a
`schemaVersion` that is not the app's, in both directions, so every fixture carrying
`"schemaVersion": 8` was refused after the bump to 9. That is the cost the deferred-work entry
names, and the fix here was to make the fixtures read `SCHEMA_VERSION` rather than to loosen the
check. Recorded in `deferred-work.md` as the prerequisite for the **next** bump, with the reason it
is bigger than a range check: a v6 file has no `payment.status` at all, so accepting one means
per-version field defaults.

**`stringResource` cannot be called inside `semantics { }`.** That lambda is not a composable
scope, so the accessibility label is read *before* the modifier chain, not inside it.

**`AVATAR_FACES` had to be declared before `AVATAR_KEYS`.** A top-level `val` is initialised in
source order, and the keys list is sized from the faces list — the other order is a compile
error, not a runtime one.

**The 45 drawables are in `res/drawable-nodpi/`.** `nodpi` because one file then serves every
density: the pack is a fixed 256px set, and letting Android rescale them per density would give
a 44dp disc a different pixel count on each device for no reason.

**The demo dataset's eight renters now carry eight distinct keys** (`avatar_01`, `_08`, `_14`,
`_22`, `_29`, `_35`, `_41`, `_45`) rather than the prototype's emoji, so the generated backup
exercises the pack.

## Spec Change Log

## Review Triage Log

## Verification

**Commands:**
- `./gradlew :domain:test :app:testDebugUnitTest :app:assembleDebug` -- expected: SUCCESS
- `ls app/src/main/res/drawable-nodpi/avatar_*.png | wc -l` -- expected: 45

**Manual checks (a device is required):**
- Open a renter, pick a face, save, and confirm the row and the detail header both draw it.
