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
| MSG_UNKNOWN_CONTRACT | The row's contract vanished between render and tap | Refused; nothing is opened | `UnknownContractException` |

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