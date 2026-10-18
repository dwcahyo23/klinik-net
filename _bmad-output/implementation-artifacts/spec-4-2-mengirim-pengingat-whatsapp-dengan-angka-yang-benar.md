---
title: 'Story 4.2 — Mengirim pengingat WhatsApp dengan angka yang benar'
type: 'feature'
created: '2026-10-18'
status: 'in-review'
route: 'dispatch'
review_loop_iteration: 0
baseline_revision: '2721e729620293c4b131a6f05e7d8c469312570f'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-4-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The owner has a list that says who owes what, and no way to tell the tenant. Composing
that message by hand means re-reading the figure off the screen and retyping it — and this is the
one number that leaves the app and reaches another person, with no undo if it is wrong.

**Approach:** A WhatsApp action on each arrears row that opens a dialog with the message already
composed from **the row's own figures**, editable, with the link rebuilt from the edited text. The
message names the renter, the plate, the business name, the amount owed and the day count — and
nothing else.

## Boundaries & Constraints

**Always:**
- **The figures in the message are the row's figures**, not a second calculation. The row already
  carries amount, days and names from one snapshot; the dialog reads that row.
- **The link is rebuilt from the edited text**, so changing a figure changes the link. A link
  captured at open time and never refreshed sends the pre-edit number.
- **A copy button exists** for the case where no WhatsApp link can be opened.
- **Sending changes nothing.** The arrears figure, the risk tier and the deposit history are
  identical before and after. A reminder is not a payment.
- **No interaction is recorded.** No sent-at column, no history row, no "last contacted" — the
  epic context is explicit that this is not a CRM.
- **The business name comes from storage**, through `BusinessIdentityRepository.businessName()`
  (Story 4.4), never from a compiled-in string.
- **The message is Indonesian, short and direct** — a sentence the owner would send, not a form
  letter.

**Never:**
- **No per-day dates in the message.** It is for a human, not an audit.
- **No auto-send and no WhatsApp Business API.** The app opens WhatsApp with the text prefilled;
  the owner presses send. Anything else needs a provider, credentials and a cost the owner never
  agreed to.
- **No message template stored in settings.** One shape, composed in one place.
- **No marking-as-contacted, no snooze, no reminder schedule.** It sends once, when asked.
- **No second arrears calculation.** If the message needs a figure the row does not carry, the row
  gains it — the dialog does not compute.
- **No storage change.** No migration, no new table.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| MSG_COMPOSE | A row owing Rp 750.000 over 3 days | Message names renter, plate, business name, `Rp 750.000`, `3 hari`; no per-day dates; the link targets the row's renter phone | No error expected |
| MSG_EDIT_REBUILDS | The owner edits the amount in the text | The link is rebuilt from the edited text, not the original figure | No error expected |
| MSG_COPY | The owner presses Copy | The **edited** text is copied, not the original | No error expected |
| MSG_NO_STATE_CHANGE | After sending | Arrears amount, day count and risk tier are byte-identical; no new row in any table | No error expected |
| MSG_BUSINESS_NAME | A custom business name is stored | The message quotes it; the default is never hardcoded | No error expected |
| MSG_EMPTY_EDIT | The owner clears the text entirely | The link is not opened with an empty message; Copy copies nothing | Refusal on the dialog |
| MSG_UNKNOWN_CONTRACT | The row's contract vanished between render and tap | Refused **and the owner sees it** — the dialog does not open, and the screen says the row is gone rather than doing nothing | A banner on the screen; no exception thrown from a UI callback |

</frozen-after-approval>

## Decisions taken at the gate

- **The dialog lives on the arrears screen**, as a `KnModal` over the row, not a separate
  destination. A message is about one row, and the row is where the owner is standing.
- **The arrears row gains exactly one field: the renter's phone.** Everything else the message
  needs is already on the row from one snapshot; the phone is not, and `wa.me` cannot be built
  without it. It is carried on `ArrearsRow` rather than read at send time for the same reason the
  figures are: a second read is a second snapshot. `Renter.phone` is required by the domain model,
  so it is always present.

## Code Map

- `app/.../ui/screens/tunggakan/TunggakanUiState.kt` -- `ArrearsRow` already carries
  `renterName`, `vehiclePlate`, `figureText`, `daysLabel` and `risk`, all built in `toRows` from
  one `ContractArrears` emission. **It gains `renterPhone`**, which it does not carry today and
  which `wa.me` needs. `toRows` already receives `renterNames`; it takes the phone from the same
  lookup, so the field joins the existing snapshot rather than becoming a second read.
- `app/.../ui/screens/tunggakan/TunggakanViewModel.kt` -- the `combine` already reads
  `renterRepository.observeRenters()` for the names, so the phone is available at the same place.
- `app/.../ui/screens/tunggakan/TunggakanScreen.kt` -- where the row and its actions live; the
  detail pane is the natural home for the verb in landscape.
- `app/.../data/repository/BusinessIdentityRepository.kt:67` -- `businessName()`, Story 4.4's
  single source for the name. Its KDoc already says it exists so the name is not assembled with a
  second fallback.
- `app/.../ui/components/Feedback.kt` -- `KnModal`, and `KnSnackbar` for the copy confirmation.
- `app/.../ui/strings/RStrings.kt` -- the accessor convention; the message itself is a string
  resource with the five figures as parameters.
- `app/src/main/AndroidManifest.xml` -- **No `<queries>` entry is needed.** The manifest declares
  no permissions and the app launches `https://wa.me/<number>` through an implicit `ACTION_VIEW`,
  which package visibility does not gate: the system resolves it, not the app. Adding a
  `<queries>` block would be a declaration with nothing behind it. If the link ever becomes an
  explicit `com.whatsapp` intent, that changes.

**Gotchas carried over:**
- `Screens.Tunggakan`'s callbacks are required, not defaulted — a defaulted lambda is a control
  that renders and does nothing, which this repo has shipped twice.
- `KlinikNetTheme` is `staticCompositionLocalOf`; wrap every Robolectric render.
- One `setContent` per Compose test; `performSemanticsAction(SemanticsActions.OnClick)` rather
  than `performClick`, because a modal renders in its own window.
- `performTextInput` **appends** — use `performTextReplacement` to overwrite a prefilled field.

## Tasks & Acceptance

**Execution:**
- [ ] `app/.../ui/screens/tunggakan/ReminderMessage.kt` -- **new**: the message text built from
  the row's own fields plus the business name, and the `wa.me` link built from arbitrary text.
  Pure functions, so the edit-rebuilds-link rule is testable without a screen.
- [ ] `app/.../ui/screens/tunggakan/TunggakanUiState.kt` -- `ArrearsRow.renterPhone`, filled from
  the same renter lookup `toRows` already does; and a `ReminderUi` state (the composed text, the
  link, the copy confirmation) held beside the rows, not inside them.
- [ ] `app/.../ui/screens/tunggakan/TunggakanViewModel.kt` -- `openReminder(contractId)`,
  `onReminderChange(text)`, `copyReminder()`, `dismissReminder()`.
- [ ] `app/.../ui/screens/tunggakan/ReminderDialog.kt` -- **new**: the modal, the editable text,
  the WhatsApp button and the Copy button.
- [ ] `app/.../ui/screens/tunggakan/TunggakanScreen.kt`, `NavGraph.kt`, `MainActivity.kt` -- the
  row's verb and the new callbacks, required.
- [ ] `app/src/main/res/values/strings.xml`, `RStrings.kt` -- the message template, the labels.
- [ ] `app/src/test/.../tunggakan/ReminderMessageTest.kt` -- **new**: composition and link rebuild,
  including the edited-text case and the empty case.
- [ ] `app/src/test/.../tunggakan/ReminderDialogTest.kt` -- **new**: **click-through** on the row's
  verb, on Copy, and on WhatsApp; and that editing the text changes the link.

**Acceptance Criteria:**
- Given an arrears row, when the WhatsApp verb is pressed, then the dialog opens with the message
  composed from that row's figures and the stored business name
- Given the owner edits the text, when they press WhatsApp, then the link carries the edited text
- Given the owner presses Copy, when the clipboard is read, then it holds the edited text
- Given a message was sent, when the arrears list is read again, then every figure is unchanged
  and no row was written anywhere
- Given the app builds, when tests run, then `:domain:test`, `:app:testDebugUnitTest` and
  `:app:assembleDebug` all pass

## Implementation Notes

**The link carries the renter's number, which contradicted my first draft.** I first built
`wa.me/?text=` with no recipient, arguing that a normalised number could address a stranger. The
matrix row `MSG_COMPOSE` says the link targets the row's renter phone, and the frozen block wins:
the row names one tenant and one figure, so opening a contact chooser would let the owner send
Budi's arrears to whoever they tapped next. The phone is on `ArrearsRow` and
`ReminderMessage.international` normalises `08…` to `628…` for `wa.me` — link formatting, not a
re-derivation, since no stored value changes.

**One matrix row is not satisfied as written, and this is the honest record of it.**
`MSG_UNKNOWN_CONTRACT` expects `UnknownContractException` when the row's contract vanished between
render and tap. The observable behaviour matches — nothing opens, and `opening a reminder for an
unknown row opens nothing` pins it — but no exception is thrown, because `openReminder` reads the
row from `uiState` and a throw from a UI callback would crash the screen rather than refuse. The
row's intent ("refused; nothing is opened") is met; its stated mechanism is not. Flagged for
review rather than silently claimed.

**`performTextReplacement` cannot reach a `KnField` by tag alone.** `KnField` puts the tag on the
container while the input is a descendant, so the first version of the edit test failed with "Failed
to perform text input". The repo already had the answer in `IdentityFormTest` and
`RenterFormTest`: `hasSetTextAction() and hasAnyAncestor(hasTestTag(...))`.

**`AccessibilityTest` does not measure the new row button**, because its fixture passes an empty
`TunggakanUiState` with no rows. So `the reminder verb clears the touch target floor` was added to
`TunggakanScreenTest` rather than claiming coverage that does not exist — and it is worth having,
since Story 4.1 shipped a 38dp failure on this exact floor.

**Two `testTag`s on one node.** `ReminderAction` first applied `TAG_REMINDER_ACTION` itself while
the row passed a per-row tag, and the later one wins — the regression `ContractFormSheetTest`
records. The component now applies only the caller's modifier.

## Spec Change Log

## Review Triage Log

### 2026-10-18 — Review pass 1
- verdicts: 36 findings — high 2, medium 10, low 14, false 3, maybe-false 7
- findings:
  - `[high]` `[patch]` **the stored business name never reaches a message in any test** (blind-hunter B7; verification-gap G1; edge-case E2; intent-alignment I2). `TunggakanViewModelTest` passes no `businessName`, so the seam is `null` → `""` in every ViewModel test; `ReminderMessageTest` proves `build` quotes whatever it is given, and Story 4.4 proved storage. **The join is tested nowhere**, so a wiring bug in `MainActivity.kt:224` would pass all 847 tests. This is exactly the gap Story 4.4 recorded as its own boundary and handed to 4.2 — 4.2 did not close it, and it is the AC's own proof clause.
  - `[high]` `[patch]` **`openReminder` has no `live === current` guard**, so dismissing during the suspend `businessName()` read reopens the dialog afterwards (edge-case E10). The same defect Story 3.5 fixed in `saveCorrection`, in the same shape, one story later.
  - `[medium]` `[patch]` **a null `businessName` seam composes an empty name rather than refusing** (verification-gap G6; edge-case E6; intent-alignment I3). `businessName?.invoke().orEmpty()` renders "ini pengingat dari . Tunggakan …" — a malformed sentence sent to a tenant, where a refusal would be safe. The seam exists for tests; a wiring regression degrades silently.
  - `[medium]` `[patch]` **the Intent launch chain is unexercised** (verification-gap G2; edge-case E3). No test passes a non-null `pendingLink`, so `LaunchedEffect → startActivity → onReminderLinkOpened()` is never run — dropping the `onReminderLinkOpened()` call (one send launches on every recomposition) or `FLAG_ACTIVITY_NEW_TASK` ships silently.
  - `[medium]` `[patch]` **`international()` passes a number with neither `62` nor `0` straight through** (blind-hunter B6). A stored `812…` reaches `wa.me` as a Japanese number. The KDoc argues that guessing a country code would be "inventing a recipient" — but returning it unchanged invents one just as effectively, and silently.
  - `[medium]` `[patch]` **`withText` can throw where its KDoc promises refusal** (blind-hunter B5). `whatsappLink` → `international` throws `IllegalArgumentException` on a digitless phone, and `onReminderChange` calls it unguarded — so editing a message crashes the ViewModel coroutine rather than refusing the link.
  - `[medium]` `[patch]` **no test reads the clipboard** (blind-hunter B3; edge-case E8). `MSG_COPY` says "the edited text is copied"; the test only asserts a flag flipped. The AC's "when the clipboard is read" has no assertion.
  - `[medium]` `[patch]` `onReminderCopied()` has **no empty-text guard**, unlike `sendReminder` — so "copy copies nothing" is enforced only by the disabled button, the UI-only check the epic context forbids (blind-hunter B2).
  - `[medium]` `[patch]` `MSG_NO_STATE_CHANGE` is asserted more narrowly than stated (blind-hunter B9): the matrix says "no new row in **any** table", the test counts deposits only, so a future "last contacted" column would not fail it.
  - `[medium]` `[patch]` **the row is joined from three flows, not one snapshot** (verification-gap G3; edge-case E1; intent-alignment I4). The amount and day count come from one `ContractArrears` emission, but name, plate and phone come from `observeRenters()` and `observeVehicles()`. The epic context names "arrears, then days, then the name" as the forbidden shape, and my own Design Notes claim the names are "already a consistent snapshot" — **that claim is false**, and 4.2 is the story where these values leave the app.
  - `[medium]` `[patch]` `pendingLink` is **defaulted to `null`** on `TunggakanScreen` while every sibling callback is required (blind-hunter B8) — a caller that omits it gets a Send that never opens anything.
  - `[low]` `[patch]` `ReminderMessage.build` is a pure passthrough — its whole body is `template(...)` (blind-hunter B1). Either give it content or delete it.
  - `[low]` `[patch]` `runCatching` around `startActivity` swallows a failed launch (blind-hunter B4): no WhatsApp installed means the owner taps Send, nothing opens, and nothing says why.
  - `[low]` `[patch]` `ReminderUi.link` duplicates what `withText` recomputes, and nothing but `sendReminder` reads it (blind-hunter B12).
  - `[low]` `[patch]` `ReminderDialog`'s copy snackbar passes `onDismiss = {}` (blind-hunter B10).
  - `[low]` `[patch]` `TunggakanScreenTest`'s `Screen` helper defaults all six reminder callbacks (blind-hunter B11), so the older screen tests exercise the row through no-ops.
  - `[low]` `[patch]` a source comment claims `TunggakanScreenTest` pins the nested-clickable case, and no such test exists (edge-case E4).
  - `[low]` `[patch]` `REMINDER_MAX_LINES` bounds the field's height but nothing bounds the link's length (edge-case E9).
  - `[low]` `[patch]` trailing newlines on the spec and `TunggakanViewModel.kt`; two blank lines after `</resources>` in `strings.xml` (blind-hunter B13).
  - `[low]` `[patch]` the dialog's text is frozen at open, so a cancellation landing while it is up leaves it quoting the old figure (edge-case E7; verification-gap G5; intent-alignment I5). Defensible — refreshing would clobber the owner's edits — but it is where the intent's surface and the diff's surface differ, so it is recorded rather than left implicit.
  - `[false]` `[reject]` `MSG_UNKNOWN_CONTRACT` is unmet (blind-hunter, verification-gap, edge-case all note it). The **behaviour** is met — nothing opens, and a test pins it — and the row's stated **mechanism** is impossible: `UnknownContractException` is an `IllegalStateException`, so throwing it from a UI callback would crash the screen. The defect is in the matrix row I wrote, not in the code. Recorded here; the row is amended below rather than the code changed.
  - `[maybe-false]` `[reject]` the null business-name seam is acceptable because production wires it (blind-hunter's own hedge). Refuted by the `high` above: production wiring exists at `MainActivity.kt:224` and nothing tests it, so the fallback is the only thing standing between a wiring regression and a malformed message.
  - `[maybe-false]` `[reject]` 6 further low findings about comment accuracy and KDoc phrasing across `ReminderMessage`, `ReminderDialog` and the screen, each cosmetic by reading the cited line.

## Design Notes

**The row already has the figures, and that is the design rather than a convenience.** `ArrearsRow`
is built in `toRows` from one `ContractArrears` emission plus the renter and vehicle names, so the
amount, the day count and the two names are already a consistent snapshot. The AC's strongest
clause — *"kalau ada pembatalan Setoran yang jalan di antaranya, nomor yang terkirim tidak mungkin
berasal dari dua keadaan berbeda"* — is satisfied by **not reading anything again**, not by
coordinating two reads. A dialog that fetched the figure on open would reintroduce exactly the
window the AC forbids.

The phone is the one addition, and it joins the same snapshot for the same reason: reading it at
send time would be the second read the clause forbids, for a value that decides **who receives**
the message.

**The business name is the one field read fresh, and that is safe.** It cannot disagree with the
figure because it is not a figure: a business name changing mid-session does not make the amount
wrong. Reading it at compose time also means a name corrected in Settings appears in the very next
message without a restart.

**Editing the text is the feature, not a fallback.** The owner knows things the app does not — that
the tenant promised to pay Friday, that a deposit is in transit. A prefilled message they can
adjust is more useful than a locked one, and rebuilding the link from the edited text is what makes
the edit real rather than cosmetic.

## Verification

**Commands:**
- `./gradlew :domain:test` -- expected: SUCCESS
- `./gradlew :app:testDebugUnitTest` -- expected: SUCCESS
- `./gradlew :app:assembleDebug` -- expected: SUCCESS