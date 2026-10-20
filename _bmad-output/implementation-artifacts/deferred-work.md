- source_spec: `_bmad-output/implementation-artifacts/spec-ui-chrome-theme.md`
  summary: Renter avatars — an emoji per renter, shown in the list rows and the detail header, editable in the renter form.
  evidence: Split because it needs a schema migration (v8 -> v9, a new nullable `avatar` column on `renter`) and a form field, which is a data change rather than chrome. It can merge independently of the top-bar and theme work, and the prototype's own `.avatar` / `.avatar-lg` (34px / 64px) is already matched by `KnAvatar`'s sizes — only the emoji source is missing.

- source_spec: `_bmad-output/implementation-artifacts/spec-ui-chrome-theme.md`
  summary: A demo build that seeds the prototype's mock dataset on first install, so an installed APK opens with a working fleet, renters, contracts and payments.
  evidence: Split because it is a build-variant plus a seeding path, not a layout change, and it must not run in a real install. The dataset already exists verbatim in `prototype/data.js` (10 vehicles, 8 renters, 8 contracts, ~65 payments with staged arrears), so the work is the seeding and the flag, not the data.


- source_spec: `_bmad-output/implementation-artifacts/spec-tiga-cacat-ui.md`
  summary: A demo dataset — the prototype's 10 vehicles, 8 renters, 8 contracts and ~380 payments, delivered as a backup JSON file restorable through the existing Settings restore, so an installed APK opens with a working app.
  evidence: Split from the three-UI-bug spec because it needs a device to verify (restore has never run on one) while the three bugs are JVM-verifiable, and a data fixture would hold a one-line badge fix hostage. The data already exists verbatim in `prototype/data.js`; the work is the translation, not the data.

  Decided at the gate (2026-10-20), do not re-litigate:
  - **Delivery is a backup JSON file, not a build variant.** No `productFlavors`, no `BuildConfig` flag, no `assets/`, no seeding path in `app/src/main`. A seeding `if (empty)` in the startup path is correct in a demo build and silently wrong in a release one, and no review distinguishes them. The restore path already exists, is already tested, and refuses rubbish at five points — so the fixture doubles as the first real test of Story 5.1/5.2 on a device.
  - **Dates shift to relative.** `data.js` anchors at `SIM_TODAY = "2026-03-24"`; today is 2026-10-20. Restored literally, the staged arrears from `MISSED` (`data.js:69-73`) vanish — every contract shows months of accrual and the demo stops demonstrating anything. The generator takes an offset and shifts the whole dataset so the far end lands on "today". Figures will no longer match `prototype/data.js` digit for digit; that is the accepted cost.
  - **Allocations are computed, never hand-written.** `AllocationEngine.kt:129-135` enforces `Σ allocation == amount` per payment with `check()`. Call `allocatePayments(...)` (public, not `internal`, reachable from `app/src/test`) and wrap the domain `Allocation` into `AllocationEntity` — the domain type has no `contractId` by design. Hand-written allocations violate the invariant, and the symptom — arrears that disagree with deposits — is exactly what AD-3 exists to prevent. `chargeDate = null` for the remainder emerges automatically (`:122-127`).

  Translation hazards already mapped (do not re-derive):
  - `carId` → `vehicleId` (`ContractEntity.kt:131`)
  - `workSchedule` + `offDays:[0]` → `restDay = DayOfWeek.SUNDAY`, and **`DayOfWeek.SUNDAY.value` is 7, not 0** — JS `getDay()` uses 0. Passing `0` throws `DateTimeException` → surfaces as `RestoreRejected("bad-row")` with no hint which contract failed. `workSchedule:"7days"` → `restDay = null`.
  - Every contract is `status:"active"` in `data.js`, so `endDate=null` **and** `activeVehicleId=vehicleId` **and** `activeRenterId=renterId` are mandatory — omitting them trips `ContractEntity.kt:193-200`.
  - `serviceExemptions` becomes 3 `ServiceExceptionEntity` rows, each needing a non-blank `reason` that `data.js` does not carry — invent the text.
  - `status:"maintenance"` → `isServis = true` (only car-10). `rented`/`available` are **derived** from contract existence, not stored — car-9 and car-10 both have no contract, so both read `Tersedia`.
  - `banks[]` (`data.js:62`) is 5 combined strings of which `"Tunai Kasir"` is a **method, not an account** — only 4 real `BusinessAccountEntity` rows, and `method` must be `"TransferBank"` or `"Tunai"`.
  - `createdAt` in `data.js` is `"2026-03-22 08:45:10"`; `Instant.parse` needs `"2026-03-22T08:45:10Z"`.
  - The single `canceled` row (`data.js:117-129`) must have its allocations **dropped** — keeping both double-counts arrears (`PaymentEntity.kt:120-122`).
  - Drop entirely: `ktp`, `address`, `avatar`, `joinDate`, `currentRenterId` — no columns. String ids `car-1`/`cont-1` are fine; `@PrimaryKey val id: String` has no UUID guard.