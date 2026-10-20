---
title: 'Tiga cacat UI: tombol Cetak mati, badge tertukar, padding dobel'
type: 'bugfix'
created: '2026-10-20'
status: 'done'
route: ''
review_loop_iteration: 1
baseline_commit: 'cf5c3d3'
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Tiga cacat dari review aplikasi terhadap prototype-nya. Semuanya presentation-layer, semuanya
terlihat, dan ketiganya lolos dari 1.132 test yang hijau.

1. **Tombol "Cetak / PDF" mati.** `LaporanScreen.kt:119-125` merender `KnButton` dengan
   `onClick = onPrint`, dan `onPrint` di `MainActivity.kt:1029` adalah `{}`. Tidak ada `PrintManager`,
   `WebView`, atau `android.print` di seluruh `main/`. Tombolnya terlihat dan **tidak melakukan apa-apa** —
   kelas defect yang thrice di-ship repo ini, dan KDoc `LaporanScreen.kt:88-90` sendiri menyebutnya.

2. **Badge status armada tertukar.** `prototype/cars.html:177-179`: `rented`→`badge-success`,
   `available`→`badge-info`. App `KendaraanScreen.kt:634-636`: `Tersedia`→`Success`, `Tersewa`→`Brand`.
   Seharusnya `Tersedia`→`Info` dan `Tersewa`→`Success`. `Servis`→`Warning` sudah benar.

3. **Padding master-detail dobel 48dp.** `prototype/shared.css:288` memberi `padding: var(--sp-6)` —
   24px **sekali**. App menumpuk dua lapis: `TunggakanScreen.kt:186`, `KendaraanScreen.kt:160`,
   `PenyewaScreen.kt:145` mem-pad `spacing.xxl`, lalu `MasterDetail.kt:54-64` mem-pad `xxl` lagi.

**Approach:** Hapus tombol beserta seluruh wiring-nya. Tukar dua nilai enum. Hapus padding di
`KnMasterDetail` — bukan di tiga pemanggil — supaya satu tempat yang membiarkannya.

**Decided at the gate (2026-10-20):**
- **Cetak dihapus, bukan diimplementasikan.** Story 4.5 menyatakan *"No PDF, no export, no share"*, dan
  tabel *Deferred* arsitektur menunda *"Ekspor PDF / laporan tercetak"* dengan alasannya sendiri. Ini
  keputusan yang sudah diambil dua kali, bukan fitur yang tertunda karena lupa. Mengimplementasikan
  butuh `PrintManager` + `WebView` + `PrintDocumentAdapter`, dan `PrintSpooler` tidak bisa diuji JVM.
- **Padding dihapus dari komponen, bukan dari pemanggil.** Ketiga pemanggil identik dan sudah mem-pad.
  Satu edit menutup tiga bug; tiga edit meninggalkan komponen yang mengasumsikan pemanggil sudah
  mem-pad — dan pemanggil keempat akan langsung salah.

## Boundaries & Constraints

**Always:**
- **Setiap kontrol yang bisa diklik punya wiring test yang menguji *call*, bukan keberadaan node.**
  `assertExists()` tidak bisa melihat tombol yang tidak melakukan apa-apa — itulah cacat pertama. Test
  yang ada (`LaporanScreenTest.kt:300-321`) mengoper stub sendiri, jadi tidak pernah menyentuh wiring
  produksi.
- **Warna badge diturunkan dari status**, tidak pernah dipilih per call site.
- **Padding luar milik layar.** `KnMasterDetail` memusatkan; pemanggil yang memutuskan ruang.

**Never:**
- **Tidak menambah `PrintManager`, `WebView`, atau `android.print`.**
- **Tidak menambah produk yang tidak diminta.** Tanpa ikon, string, atau enum baru —
  `KnBadgeTone.Info` sudah ada (`Badge.kt:35`).
- **Tidak menyentuh `domain/`, repository, query, atau state.** Ketiganya tidak mengubah satu angka
  atau aturan hitung.
- **Tidak mengubah perilaku atau copy layar lain.** Yang hilang adalah *satu* tombol.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| LAPORAN_BAR | Laporan dibuka | Hanya satu tombol aksi (CSV) | N/A |
| CETAK_HILANG | Pohon semantik Laporan | `laporan-export-print` tidak ada | N/A |
| WIRING_CSV | Ketuk tombol CSV | `CreateDocument` terbuka → `onExportFileChosen` | N/A |
| BADGE_TERSEDIA | Mobil `Tersedia` | `KnBadgeTone.Info` | N/A |
| BADGE_TERSEWA | Mobil `Tersewa` | `KnBadgeTone.Success` | N/A |
| BADGE_SERVIS | Mobil `Servis` | `KnBadgeTone.Warning`, tak berubah | N/A |
| GUTTER_WIDE | Master-detail ≥600dp (Row) | Padding luar 24dp | N/A |
| GUTTER_NARROW | Master-detail <600dp (Column) | Padding luar 24dp | N/A |

</frozen-after-approval>

## Code Map

**1 — tombol mati. Hapus, jangan sambungkan.**
- `app/.../ui/screens/laporan/LaporanScreen.kt` — `:119-125` tombol; `:102` param `onPrint`; `:637`
  `TAG_EXPORT_PRINT`; `:685` argumen preview; `:82-90` KDoc param
- `app/.../MainActivity.kt:1029` — `onPrint = {}`
- `app/.../ui/shell/NavGraph.kt` — `:212` `onPrint = wiring.onPrint`; `:795` field `Screens.Laporan`
- `app/src/main/res/values/strings.xml:1448` — `laporan_export_print`
- Test: `LaporanScreenTest.kt:309,316,350,361`; `AccessibilityTest.kt:844`; `AppShellTest.kt:1192`;
  `BannerScopeTest.kt:276`
- **The CSV export's real wiring, and the trap in it.** `NavGraph.kt:147-153` builds the
  `CreateDocument(MIME_CSV)` launcher and its result calls `screens.laporan.onExportFileChosen(uri)`
  at `:152`. The button's callback comes from `NavGraph.kt:156` —
  `onExportCsv = { exportLauncher.launch(screens.laporan.csvFilename()) }` — passed to
  `LaporanRoute` and straight through to the screen at `:211`.
  **`Screens.Laporan.onExportCsv` (`:793`) is therefore dead**: `LaporanRoute`'s parameter of the
  same name shadows it, no code reads `wiring.onExportCsv`, and its only production writer is
  `MainActivity.kt:1028` — `{}`. Iteration 0 of this spec had this backwards (its task said to
  remove the *route parameter*); doing that would have pointed the button at the `{}` and
  re-created the dead control this spec removes. **Delete the field and its `MainActivity`
  argument; keep the route parameter.**

**2 — badge tertukar. Satu blok.**
- `app/.../ui/screens/kendaraan/KendaraanScreen.kt:632-637` — `VehicleStatus.tone`; KDoc `:626-631`
  **mendokumentasikan pemetaan salah sebagai pilihan sadar**, jadi harus ikut berubah
- `prototype/cars.html:177-179` — acuan. `Badge.kt:35` sudah punya `Info`

**3 — padding dobel. Satu blok.**
- `app/.../ui/components/MasterDetail.kt:54-64` (Row) dan `:80-89` (Column) — `.padding(xxl)`
- Tiga pemanggil yang **sudah** mem-pad: `TunggakanScreen.kt:186`, `KendaraanScreen.kt:160`,
  `PenyewaScreen.kt:145`; KDoc ketiganya sudah mengutip `shared.css:285-289`. Pemanggil benar,
  komponen yang menyimpang.

## Tasks & Acceptance

**Execution:**
- [x] `app/.../ui/screens/laporan/LaporanScreen.kt` -- hapus tombol, param `onPrint`,
      `TAG_EXPORT_PRINT`, argumen preview, dan KDoc parameternya
- [x] `app/.../ui/shell/NavGraph.kt` -- hapus field `onPrint` dari `Screens.Laporan`; **pertahankan**
      parameter `onExportCsv` di `LaporanRoute` (`:202`) — itu yang menjalankan picker
- [x] `app/.../ui/shell/NavGraph.kt` + `MainActivity.kt:1028` -- **hapus field
      `Screens.Laporan.onExportCsv` dan argumennya di `MainActivity`**. Field itu mati: parameter
      route dengan nama sama meninggalkannya, tidak ada kode yang membaca `wiring.onExportCsv`, dan
      writer-produksinya cuma `onExportCsv = {}`. Lihat Code Map bagian 1
- [x] `app/src/main/res/values/strings.xml` -- hapus `laporan_export_print`
- [x] `LaporanScreenTest.kt`, `AccessibilityTest.kt`, `AppShellTest.kt`, `BannerScopeTest.kt` -- hapus
      argumen `onPrint`; ubah test dua tombol jadi satu; **tambahkan assertion `TAG_EXPORT_PRINT` tidak ada**
- [x] **BARU** -- `LaporanExportWiringTest.kt`: render `Destination.Laporan` lewat `AppNavGraph` di
      `createAndroidComposeRule<ComponentActivity>()` (agar `rememberLauncherForActivityResult`
      resolve), ketuk `TAG_EXPORT_CSV`, assert `onExportFileChosen` terpanggil. **AC #2 belum
      pernah punya test sama sekali**
- [x] `app/.../ui/screens/kendaraan/KendaraanScreen.kt:632-637` -- `Tersedia`→`Info`,
      `Tersewa`→`Success`; perbarui KDoc `:626-631` (termasuk angka "four" yang sudah tidak akurat)
- [x] `KendaraanScreenTest.kt` -- assertion tone per status
- [x] `KendaraanScreenTest.kt` -- guard batas koordinat sebelum `getPixel`, dan variant dark-theme
- [x] `app/.../ui/components/MasterDetail.kt:54-64,:80-89` -- hapus `.padding(xxl)` dari kedua cabang
- [x] `MasterDetail.kt:11` -- hapus import yang sudah tidak terpakai
- [x] `MasterDetailGutterTest.kt` -- ukur padding terluar di kedua orientasi, assert 24dp
- [x] `MasterDetailGutterTest.kt` -- tambah assertion **tepi bawah** di kedua orientasi; GUTTER
      dibaca dari `knSpacing.xxl`, bukan literal `24.dp`
- [x] **BARU** -- `KendaraanScreenTest.kt`, `PenyewaScreenTest.kt`, `TunggakanScreenTest.kt`: ukur
      inset baris terhadap window di layar **nyata**, assert 24dp. Test komponen tidak menangkap
      pemanggil yang kehilangan padding-nya — dan setelah patch ini pemanggil adalah satu-satunya
      lapisan padding yang tersisa
- [x] **BARU** (triage #11) -- `MasterDetailGutterTest.kt`: pin `MasterDetailBreakpoint` lewat
      **perilaku**, di 648dp dan 647dp window. Bukan mengulang angkanya — test yang membaca konstanta
      akan lulus di nilai apa pun. Lebar 648dp karena `Body()` mem-pad 24dp per sisi, jadi
      `KnMasterDetail` menerima window − 48dp, yaitu tepat 600dp

**Acceptance Criteria:**
- [x] Given Laporan dibuka, when top bar diperiksa, then hanya satu tombol aksi dan `laporan-export-print`
      tidak ada di pohon semantik
- [ ] **Partial.** Given tombol CSV diketuk **lewat route produksi**, when callback-nya dipanggil, then
      `onExportFileChosen` menerima Uri dari `CreateDocument`. **Tercapai separuh:**
      `LaporanExportWiringTest` membuktikan route memakai lambda miliknya sendiri (bukan `{}`) dan
      memanggil `csvFilename` *saat tap* — jadi `launch(...)` benar-benar jalan. Set
      `NavGraph.kt:156` ke `{}` dan test itu gagal. **Belum tercakup:** `onExportFileChosen`
      menerima Uri asli. `CreateDocument` tidak punya file manager di Robolectric, jadi callback-nya
      tidak pernah fired, dan test yang mengasumsikannya akan gagal karena harness, bukan karena bug.
      Tertutup device-only — lihat Verification
- [x] Given armada berisi Tersedia/Tersewa/Servis, when barisnya dirender, then tone-nya Info/Success/Warning
      (terverifikasi di light **dan** dark)
- [x] Given **tiap** master-detail screen nyata di ≥600dp dan <600dp, when padding terluar diukur
      terhadap window, then 24dp pada keenamnya. **Mutation-verified:** `PenyewaScreen.kt:145`
      di-`0.dp` → 2 test gagal di 0dp sementara `MasterDetailGutterTest` tetap hijau
- [x] Given `./gradlew :domain:test :app:testDebugUnitTest :app:assembleDebug --rerun-tasks`, then SUCCESS
      — app **1.150** + domain 210, 0 failures, 0 skipped

**Mutation-verified, bukan hanya hijau.** Setiap assertion baru diuji dengan mengembalikan defect-nya:

| Mutasi | Hasil |
|---|---|
| `NavGraph.kt:156` → `{}` | wiring test gagal (`expected:<0> but was:<-1>`) |
| `PenyewaScreen.kt:145` → `0.dp` | 2 test gagal di 0dp; `MasterDetailGutterTest` tetap hijau — membuktikan test per-layar yang dibutuhkan |
| `bottom = xxl` di cabang `Row` | hanya test tepi bawah gagal (24dp vs 48dp) |
| badge di-resolve lewat token light | hanya test dark-theme gagal |
| **`MasterDetailBreakpoint` → `700.dp`** | **1.150 test, tepat 1 gagal.** Tanpa test ini, konstanta bisa bergeser dan seluruh suite tetap hijau |

## Spec Change Log

### Iteration 1 — the Code Map's model of the CSV wiring was wrong

**Triggering findings:** triage #1, #2, #5 (found independently by all three layers).

The Code Map asserted *"Setelah Cetak dihapus, `LaporanRoute` tak butuh parameter"*, and the task
list said *"hapus param `onExportCsv` dari `LaporanRoute`"*. Both are false. `NavGraph.kt:156`
supplies the real lambda to the **route parameter**, which shadows `Screens.Laporan.onExportCsv`
(`:793`); that field's only production writer is `MainActivity.kt:1028` — `{}`. So the field is
dead, and **following the task literally would have routed the button to that `{}` and re-created
the exact defect this spec exists to remove.** Iteration 0 did not follow it literally, but marked
the task `[x]` anyway, leaving the misleading claim in place and the dead field in the tree.

**Amended (all outside `<frozen-after-approval>`):**
- `## Code Map` — the **Reuse** bullet now states the real direction: the route parameter **is** the
  wiring; the `Screens` field is the dead one and must be deleted.
- `## Tasks & Acceptance` — task 2 corrected to *"delete the unread `Screens.Laporan.onExportCsv`
  field and its `MainActivity` argument; keep the route parameter"*, plus a task for the
  NavGraph-level CSV wiring test the Always-rule and AC #2 already demanded but no task produced.
- `## Tasks & Acceptance` — the gutter task now names the three real screens, matching AC #4's
  "tiap master-detail screen" instead of the component alone, plus the bottom edge.
- `## Verification` — `git grep` paths expanded from the unrunnable `app/.../` ellipsis form to real
  paths (triage #12).

**Known-bad state avoided:** `Screens.Laporan.onExportCsv` surviving as a required field that
production fills with `{}` and no code reads — a "control wired to nothing" sitting one field away
from the one just deleted.

**KEEP — must survive re-derivation:**
- **Delete the print button; do not implement it.** Story 4.5's Never-list, the architecture's
  Deferred table, and `PrintSpooler` being untestable on the JVM. The reasoning belongs in the
  KDoc so nobody re-adds it.
- **Strip padding in `KnMasterDetail`, not in the three callers.** Callers are correct; the
  component was the deviation.
- **Swap the badge tones and rewrite the KDoc that documented the wrong mapping as deliberate.**
- **Mutation-test every new assertion** — reintroduce each defect, confirm the matching test fails
  with a precise message, revert. This is what caught the first test run being cached.
- **Force `--rerun-tasks`** whenever tests are added; a cached green is not a green.

## Implementation Notes

**AC #2 hanya separuh, dan batasnya nyata.** `CreateDocument` tidak punya file manager di Robolectric,
jadi callback `onExportFileChosen` tidak pernah fired. Test yang mengasumsikannya akan gagal karena
harness, bukan karena bug — lebih buruk daripada tidak ada test, karena ia melatih orang mengabaikan
kegagalan. `LaporanExportWiringTest` memakai `csvFilename` sebagai seam: route memanggilnya **di dalam**
`launch(...)`, jadi filename yang dikembalikan hanya saat tap sedang ditangani membuktikan `launch`
benar-benar jalan, bukan tombol yang memegang referensi basi. Sisa AC — Uri asli sampai ke
`onExportFileChosen` — device-only, dan tercatat sebagai `[ ] Partial`, bukan `[x]`.

**Triage #11 ditutup dengan pasangan 648/647, dan angkanya bukan tebakan.** `Body()` mem-pad
`spacing.xxl` per sisi, jadi `BoxWithConstraints` mengukur **window − 48dp**, bukan window. 648dp
memberi komponen tepat 600dp. Test yang mengulang `MasterDetailBreakpoint` akan lulus di nilai
apa pun; pasangan ini mengapit ambangnya, jadi hanya konstanta yang salah yang bisa membuatnya hijau.

**Catatan coupling yang diketahui.** Dua test breakpoint terikat pada padding 24dp di `Body()`.
Kalau padding harness itu berubah, pasangan 647/648 diam-diam berhenti mengapit ambangnya **tanpa
error**. Aritmetikanya tertulis di KDoc agar coupling-nya bisa ditemukan; mengembalikannya ke
padding lain berarti lebar kedua test harus dihitung ulang.

## Review Triage Log

| # | Source | Verdict | Evidence |
|---|--------|---------|----------|
| 1 | blind-hunter, verification-gap, edge-case (independent) | **high** | **AC #2 is checked but untested.** No test drives the production route. `LaporanScreenTest.kt:308` injects `onExportCsv = { exported++ }` into `LaporanScreen` directly — the same self-supplied stub the spec's own Always-rule (`spec:47-50`) names as why this defect survived 1,132 green tests. Set `NavGraph.kt:156` to `{}` and every test still passes while the shipped APK's CSV button reaches nothing. |
| 2 | blind-hunter, verification-gap, edge-case (independent) | **high** | **`Screens.Laporan.onExportCsv` (`NavGraph.kt:793`) is never read.** `LaporanRoute`'s parameter (`NavGraph.kt:202`) shadows it, and is passed through at `:211`. Its only production writer is `MainActivity.kt:1028` — `onExportCsv = {}`. Production reads as an inert `{}` while the real wiring sits on the route parameter. This is finding #1's hiding place, and it is the exact "control wired to nothing" shape just removed from the screen beside it. |
| 3 | verification-gap, edge-case, blind-hunter (independent) | **medium** | **`MasterDetailGutterTest` measures its own harness, not the three real screens.** `Body()` (`:133-150`) rebuilds the caller shape locally. `git grep KnMasterDetail -- app/src/main` returns exactly three call sites (`KendaraanScreen:176`, `PenyewaScreen:147`, `TunggakanScreen:201`), and all three are now the *only* padding layer. Delete `.padding(spacing.xxl)` from `PenyewaScreen.kt:145` — the apparent duplicate — and that screen renders at 0dp with all three gutter assertions still green. AC #4 says "tiap master-detail screen"; the test covers the component only. |
| 4 | edge-case, blind-hunter (independent) | **medium** | **No bottom-edge assertion.** The defect was four-sided padding at both layers; the tests measure `left`, `right` (wide) and `top` (narrow). Re-add `bottom = spacing.xxl` to the `Row` at `MasterDetail.kt:63` and all three stay green. |
| 5 | blind-hunter, edge-case (independent) | **medium** | **`NavGraph.kt:202` still takes `onExportCsv`**, contradicting the spec task marked `[x]` at `spec:106` ("hapus param `onExportCsv` dari `LaporanRoute`"). The task's intent — that `LaporanRoute` needs no parameter — is unmet, and it is the same root cause as #2. |
| 6 | blind-hunter | **medium** | **`KendaraanScreen.kt:629-630` still reads "the four semantic colours"** while naming three tones, in the very KDoc the spec ordered rewritten on the grounds that stale docs "jadi menghitung kebohongan yang lebih buruk dari bug-nya". Verified: the phrase survives the edit at line 629-630. |
| 7 | edge-case | **low** | **Orphaned import.** `MasterDetail.kt:11` imports `androidx.compose.foundation.layout.padding`; no `.padding(` call remains in the file (verified — only two KDoc mentions). An unused import reading as live padding is the deletion class this repo enforces elsewhere. |
| 8 | edge-case | **low** | **Badge pixel sample is unguarded.** `KendaraanScreenTest.kt:727-728` calls `getPixel(bounds.left.toInt(), bounds.top.toInt())` with no bounds check. A fractional or out-of-window coordinate throws `IllegalArgumentException` instead of failing with the intent's message. |
| 9 | blind-hunter | **low** | **`MasterDetailGutterTest.kt:859` hardcodes `24.dp`** while `KendaraanScreenTest` deliberately reads tokens "a literal would keep passing after the token changed". Two opposite policies, neither reconciled; `GUTTER`'s KDoc asserts it equals `knSpacing.xxl` and nothing verifies it. |
| 10 | blind-hunter | **low** | **Badge test is light-theme only.** `InfoBg`/`SuccessBg`/`WarningBg` read `LightColors.semanticBg.*`. A dark-theme regression in the same mapping ships green here. The class's `theme` helper is light-only. |
| 11 | blind-hunter | **low** → **closed** | **No test pins `MasterDetailBreakpoint` (600dp).** Tests ran at 1280dp and 560dp. Moving the constant to 700dp left every assertion green while inverting tablet layouts. **Closed after triage:** `MasterDetailGutterTest` now pins it by behaviour at 648dp/647dp windows (the harness's own 24dp padding means the component sees window − 48dp, so 648dp hands it exactly 600dp). Mutation-verified — moving the constant to 700dp fails exactly one of 1.150 tests. |
| 12 | blind-hunter | **low** | **Spec's Verification commands are unrunnable as written.** Paths use the `app/.../` ellipsis form (`:150`), which `git grep` cannot resolve. Verified separately with full paths — all three pass — but the documented form does not. |
| 13 | blind-hunter | **false** | **"No other locale checked."** `app/src/main/res/` contains only `drawable`, `font`, `values` — there is no `values-xx/strings.xml` to leave behind. Verified directly. |
| 14 | blind-hunter | **false** | **"`KnBadgeTone.Brand` is now an orphan token."** Still used in 8 places (`Badge.kt:64,225`, `Chart.kt:491,499`, `Feedback.kt:239`, `BerandaScreen:346`, `LaporanScreen:216,601`). Verified. |
| 15 | blind-hunter | **maybe-false** | **"`@GraphicsMode(NATIVE)` has no build change and may fail."** `:app:testDebugUnitTest` passes with `--rerun-tasks`; `each status paints its badge in its own tone` ran at 1.023s in the fresh run. The requirement is met by the existing build. The open part is only whether it is *recorded* — a documentation nit, not a failure. |
| 16 | blind-hunter | **false** | **"Spec sections empty yet tasks checked."** `## Implementation Notes` is empty by the template's own instruction ("Leave empty at planning time; never delete"). Not a defect in the spec. |

## Design Notes

**Kenapa hapus tombol, bukan implementasikan.** Dua alasan saling menguatkan. Story 4.5 sudah
menulis *"No PDF, no export, no share"* di Never-list, dan arsitektur menunda *"Ekspor PDF / laporan
tercetak"* dengan alasan *"ekspor JSON sudah cukup"*. Menambalnya berarti membangun tiga API Android
yang tidak satu pun bisa diuji tanpa perangkat.

**Kenapa KDoc badge ikut berubah.** `KendaraanScreen.kt:626-631` menulis pemetaan yang salah sebagai
*"the four semantic colours of the design system, with no fifth status colour invented"* — dan itu
terdengar seperti pilihan sadar. Kalau hanya kodenya yang ditukar, dokumentasinya jadi menghitung
kebohongan yang lebih buruk dari bug-nya.

## Verification

**Commands:**
- `./gradlew :domain:test :app:testDebugUnitTest :app:assembleDebug --rerun-tasks` -- expected: SUCCESS.
  **`--rerun-tasks` is not optional** — a cached green from a run before the new tests existed is not
  a green.
- `git grep -n "onPrint\|laporan_export_print\|TAG_EXPORT_PRINT" -- app/` -- expected: **no match**
- `git grep -n "PrintManager\|android.print\|WebView" -- app/src/main/` -- expected: **no match**
- `git grep -n "\.padding(" -- app/src/main/kotlin/net/klinik/rental/ui/components/MasterDetail.kt`
  -- expected: **no match**
- `git grep -n "wiring.onExportCsv" -- app/` -- expected: **no match** (proves the dead field is gone)

**Manual checks (butuh perangkat):**
- Laporan: satu tombol aksi; CSV membuka picker
- Kendaraan: Tersedia biru, Tersewa hijau, Servis kuning
- Ketiga master-detail screen di 711dp portrait dan landscape: jarak tepi 24dp, bukan 48dp