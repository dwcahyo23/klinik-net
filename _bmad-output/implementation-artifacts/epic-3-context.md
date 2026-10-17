# Epic 3 Context: Catat dan koreksi setoran

<!-- Generated from planning artifacts. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Andi can record a morning's deposits in one pass, preview the effect before committing it, correct a mistyped entry, and retire an old contract without retyping its history. The epic also carries two pieces that exist so the app is usable on day one rather than merely complete: the business-account list the deposit form selects from, and the settled-through marker. Without either, a first run shows every historic contract as fictional critical arrears, and the deposit form ships with an empty dropdown.

## Stories

- Story 3.1: Business-account list used when recording a deposit
- Story 3.2: Recording a deposit with the numpad
- Story 3.3: Previewing before saving
- Story 3.4: Cancelling a deposit with a reason
- Story 3.5: Correcting a mistyped deposit
- Story 3.6: Marking a contract settled through a given date

## Requirements & Constraints

**Money is `Long` rupiah, whole units, everywhere** — in the domain, in the database, and in every string a screen shows. Zero or negative amounts are refused, never replaced by a fallback figure, and a bad amount is refused in the data layer rather than only in the UI: two screens can pass the same UI check, so the check that counts is the one below the screen.

**A deposit's allocation is stored, not recomputed.** Every deposit records which charge days it closed, in its own allocation rows. That is what makes credit survive a period rollover and what makes arrears a stored fact rather than a replay — a re-derived figure changes itself when a cutoff day or a rate changes, and an owner who trusted yesterday's number cannot reconcile today's.

**Cancelling a deposit is a re-derivation, not a deletion.** A cancelled deposit leaves the pool, releases its allocations, and every remaining allocation is recomputed from the deposits that survive — so cancelling *raises* arrears. The consequence is unavoidable and must be disclosed rather than avoided.

**A deposit's amount can be dated in the past**, for correction. Money dated in the future is not counted until its date actually falls due.

**Business accounts are a real relation, not a settings string.** An account has a bank name and a number. The number is validated for **format only** — never checked against the bank — so nothing changes if the bank's records disagree. A deposit records the account it arrived through, and **editing that account's number later must not alter deposits already recorded**: the deposit stays as it was written, which is why the account is referenced rather than copied-and-mutated.

**An account in use cannot be deleted, and that rule lives in the data layer.** Not in the interface: two screens can pass the same UI check. The refusal names the deposits that hold it.

**Configuration and account data belong in the database, not in device preferences**, so both are covered by backup and restore. PIN and recovery codes are explicitly excluded from export. The configuration store's value column is a string and is **never** used to hold money — the money constraint applies to numeric columns, and overloading a string column is how a figure loses its type.

## Technical Decisions

**Two modules, one direction.** A pure-JVM domain module with no Android block at all, so Gradle rather than review discipline makes an Android import a build failure; and an app module holding data and UI. Dependencies point domain → data → ui. Domain ids are `String`, not `UUID`, because the domain's import surface excludes the UUID package.

**Any write touching more than one row — or one row feeding a calculation — is a transaction** on a suspending repository function. The repository never exposes a DAO to the UI and no flow ever writes. Domain raises specific typed exceptions; the data layer turns them into Indonesian messages. Never a result type carrying a domain message, because a string that has already been rendered into Indonesian cannot be re-checked or re-worded.

**Dates are date-only values**, stored as ISO text so lexicographic order equals chronological order and the column stays sortable and indexable; instants are for creation and cancellation timestamps only. Today comes from a single clock abstraction — no hardcoded dates, no simulated today.

**UI state is one value object per screen**, built by combining observed flows. No manual invalidation signal: the database's change tracker already re-emits, and a second refresh mechanism is a second thing that can disagree with the first.

**Configuration rows are a key/value pair.** The account relation is separate — accounts have a bank name and a number and are referenced by deposits, which is a different shape from configuration and would lose its constraints inside a string column.

## UX & Interaction Patterns

**Every screen has four states, none removable without explanation.** Loading shows a skeleton shaped like the content that will arrive. Empty says *what to do*, not merely that there is nothing. Error explains what went wrong and what to do next, and **never shows empty data that looks like a new account** — that failure reads as data loss. Success is a snackbar that does not change screen state, so the owner can carry straight on.

**Three confirmation levels, and the level is chosen by consequence.** A reversible action with no side effect asks nothing. An action that changes numbers asks through a dialog naming the impact. An action that deletes data asks through a dialog **plus a mandatory reason**.

**No gesture is ever the only route to an important action** — and specifically not for a deposit, which has money consequences and needs a reason.

**A preview before saving is mandatory, not optional**, including when the owner already knows the result: without it, saving blind contradicts the point of the app. It is compact — arrears before, arrears after, days closed, any remaining credit — and never a per-day ledger.

**Correcting a field is not a destructive act.** Changing a value does not damage anything, so it asks for no confirmation: over-confirmation is what teaches people to stop reading.

**Nominal entry uses the on-screen numpad, never the system keyboard** — it is used standing up while holding something, so large targets beat typing speed. Digits show large and in tabular figures above the grid, with a twelve-digit cap; saving rejects zero or negative with a visible flash rather than accepting quietly, and every key gives haptic feedback.

**The deposit form may be a bottom sheet rather than a dialog**, because on a tablet a dialog covers the numpad it exists to serve.

## Cross-Story Dependencies

Story 3.1 comes first and is a **hard dependency for 3.2**: a deposit form whose payment-method dropdown has no source list has to be built twice, once empty and once populated. Nothing else in the epic is blocked from outside it, and 3.2 through 3.5 all consume the account list rather than depending on one another.

Epic 3 consumes Epic 2's renter, vehicle and contract records — the deposit form selects among exactly those contracts Epic 2 opened — and Epic 2's ended contracts supply the historic entries this epic corrects and cancels. Story 3.6's settled-through marker is what stops an old contract from presenting as fictional arrears on first run, which is a day-one concern rather than a later refinement.

The preview in 3.3 renders arrears figures that Epic 4 later displays as a list; the same stored allocation rows serve both, so the preview is a display of an existing fact rather than a second calculation.