- source_spec: `_bmad-output/implementation-artifacts/spec-audit-restyle-ui.md`
  summary: Fix the six visual defects that need scrolling or measuring to see — the KPI `FlowRow` weight, the 36sp colliding label, the 16px gutter versus the prototype's 24px, the skeleton/tile height mismatch, the 840dp centred content column, and the stale seven-destination KDoc.
  evidence: Split from the audit because every one of these needs a scroll, a measurement or a grep to notice, while the four in the parent spec are visible on the first screen. None depends on the others; the parent spec's shell, FAB, rail and card work can merge and ship without this.

- source_spec: `_bmad-output/implementation-artifacts/spec-ui-chrome-theme.md`
  summary: Renter avatars — an emoji per renter, shown in the list rows and the detail header, editable in the renter form.
  evidence: Split because it needs a schema migration (v8 -> v9, a new nullable `avatar` column on `renter`) and a form field, which is a data change rather than chrome. It can merge independently of the top-bar and theme work, and the prototype's own `.avatar` / `.avatar-lg` (34px / 64px) is already matched by `KnAvatar`'s sizes — only the emoji source is missing.

- source_spec: `_bmad-output/implementation-artifacts/spec-ui-chrome-theme.md`
  summary: A demo build that seeds the prototype's mock dataset on first install, so an installed APK opens with a working fleet, renters, contracts and payments.
  evidence: Split because it is a build-variant plus a seeding path, not a layout change, and it must not run in a real install. The dataset already exists verbatim in `prototype/data.js` (10 vehicles, 8 renters, 8 contracts, ~65 payments with staged arrears), so the work is the seeding and the flag, not the data.

- source_spec: `_bmad-output/implementation-artifacts/spec-ui-chrome-theme.md`
  summary: KPI tile geometry — the prototype floats the 40dp icon in the tile's top-right corner (`shared.css:421-426`) while `KnKpiTile` stacks it above the label, which makes every tile taller and the label sit lower than the reference.
  evidence: Split because it is a component-internal change that affects one screen (`Beranda`), while the parent spec changes the frame around all eight. Changing both in one pass would make a regression in either unattributable.