# Epic 4 Context: Tunggakan, pengingat, laporan

<!-- Compiled from planning artifacts. Regenerate with compile-epic-context if planning docs change. -->

## Goal

The owner can see who owes what, for how long, and act on it the same morning. Epic 4 is
where the app stops recording and starts answering: an arrears list that is trustworthy at a
glance, reminders sent with numbers that are correct, and reports the owner can reconcile
against a bank statement. This is the epic that proves SM-2 in the real world — not through a
model, but by correcting a mistyped entry and watching the arrears figure become right again.
It carries the two functions Epic 3 deliberately left unwired, because both needed a list to
hang from.

## Stories

- Story 4.1: Arrears list that reads at a glance
- Story 4.2: Sending a WhatsApp reminder with correct figures
- Story 4.3: Morning summary on the home screen
- Story 4.4: Business identity for the reminder message
- Story 4.5: Monthly period report
- Story 4.6: Global search

## Requirements & Constraints

**Every list figure must be correct, not merely fast.** Recomputation happens on every change
to a deposit, a contract or the cutoff day, before the screen finishes drawing. A stored
arrears number that is only recomputed on read produces a discrepancy nobody can see.

**The overdue-day count is a primary output, not a display detail.** It is what gets sent to
the tenant, so it must be right. The day count is derived from stored allocations, not by
dividing the rupiah figure by the daily rate and rounding — the two agree today, but only the
allocation-based figure stays right as contracts differ in rate and settle through a date.

**Risk level is a fixed four-step ladder on the day count**: 0 days clear, 1–2 light, 3–6
attention, 7+ critical. A prototype shipped a conflicting threshold of 3+ for the critical
badge; the ladder is the decision, and the badge follows it.

**Colour is derived from the number, never chosen.** Two sources of truth that can disagree
are the surest way to make an owner stop trusting the app. If the scale changes, the colour
changes with it — there is no state in which the figure says "3 days" and the badge is green.
The number is the primary signal because it does not depend on colour perception.

**Ordering is urgency, descending**: largest arrears first, and for equal amounts the oldest
still-open charge day first.

**Ended contracts with a balance stay in the list** until they are settled, marked as finished.
Active arrears therefore span open contracts *and* ended contracts that still owe.

**A day past its date is already counted**, including across midnight — the figure on screen
must match what the owner reads from online banking.

**In landscape the list and the detail are visible together**, list left, detail right.
Comparing several renters must not mean paging back and forth between screens; in portrait
the pair collapses to list then detail.

**Any figure that leaves the app is read from one snapshot** that returns a complete object per
contract. Composing it from separate reads — arrears, then days, then the name — can mix two
database states if a write lands in between, and that number would reach a tenant with no undo
and no audit trail behind it.

## Technical Decisions

**Arrears come from stored allocation rows, never from re-summing.** Each deposit records the
charge days it closed. The stored fact is what makes credit survive a period rollover and what
makes arrears reproducible when a rate or cutoff changes; re-deriving from scratch deletes the
table and contradicts the model.

**Money placed on no particular day is never allowed to vanish.** Surplus beyond the current
period's obligation must produce an allocation row with no charge date — the marker for "money
is here, the day is not assigned yet". Those rows are what carry credit across the rollover.

**Every mutation re-derives the whole allocation set.** Cancellation and correction both
delete the affected rows and recompute the contract from the deposits that survive, in one
transaction. Marking a contract settled is the exception: it writes a field on the contract
and no allocation row at all, and the calculation treats those days as closed. The invariant
that every deposit's allocations sum to its own amount holds without exception.

**Writes touching more than one row, or one row feeding a calculation, run in one transaction.**
Repositories never expose a DAO to the UI layer and no flow ever writes. A write failure must
fail loudly; resetting to a default after a parse error is how a corrupt write silently
destroys the history.

**Recalculation scale.** Below 20 vehicles and a few hundred deposits, a full recomputation
completes well inside 50 milliseconds. That budget is loose, and if it has to be relaxed the
relaxation is recorded explicitly — correctness is never traded for speed silently.

**Money is whole rupiah in a 64-bit integer**, end to end, including in configuration storage,
where the value column is text and must never hold a money figure.

## UX & Interaction Patterns

**Every screen has four states and the empty one is an instruction, not an absence.** Loading
shows a skeleton shaped like the content to come. Empty says what to do. Error explains what
went wrong and what to do next, and never shows empty data that reads like a new account —
that failure looks like data loss. Success is a snackbar that does not change screen state, so
the owner carries straight on to the next action.

**Confirmations are graded by consequence, and never overused.** A reversible action with no
side effect asks nothing. An action that changes figures asks through a dialog naming the
impact. An action that deletes data asks through a dialog *plus* a mandatory reason. Over-
confirmation teaches people to stop reading, which is worse than never asking at all.

**No gesture is ever the only route to an important action**, and swipe-to-delete is not used
for deposits — that action has money consequences and needs a reason, which is too important
to put behind a gesture that can be fired by accident.

**Touch targets are at least 48dp with no exception.** Colour is never the only carrier of
meaning: a figure always appears as a number *and* a colour.

**Reminders do not change arrears and are not recorded as interaction history.** This is not a
CRM; the app sends a message and moves on.

## Cross-Story Dependencies

Story 4.1 is the epic's foundation and a hard dependency for everything after it: 4.2 attaches
a WhatsApp action to each arrears row, 4.3's morning summary reads the same figure, and 4.5's
period report reuses the same computation over a whole period. A list built three different ways
is three chances for the numbers to disagree, which is the one thing this epic exists to
prevent.

Story 4.2 depends on 4.4's business identity — the message names the business, so it cannot be
composed until that exists. Build order is therefore 4.1, then 4.4, then 4.2.

**Two carry-overs from Epic 3 land here.** The deposit-cancellation entry point (Story 3.4) and
the deposit-correction entry point (Story 3.5) are both implemented and tested but have no
production caller, because no deposit history list exists. The arrears detail surface is where
they mount. Two deferrals point at this epic, and closing them is part of finishing it rather
than an extra.

Epic 4 consumes Epic 3's stored allocations and Epic 2's contract records, and it reads the
settled-through marker Story 3.6 added rather than re-deriving anything the deposit ledger
already knows.