---
title: 'Tiga cacat UI: tombol Cetak mati, badge tertukar, padding dobel'
type: 'bugfix'
created: '2026-10-20'
status: 'ready-for-dev'
route: ''
review_loop_iteration: 0
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
- **Reuse:** CSV (`NavGraph.kt:145-158`) sudah jadi pola tunggal — satu launcher di route, satu handler
  di ViewModel. Setelah Cetak dihapus, `LaporanRoute` tak butuh parameter.

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
- [ ] `app/.../ui/screens/laporan/LaporanScreen.kt` -- hapus tombol, param `onPrint`,
      `TAG_EXPORT_PRINT`, argumen preview, dan KDoc parameternya
- [ ] `app/.../ui/shell/NavGraph.kt` -- hapus param `onExportCsv` dari `LaporanRoute` (`:202`) dan field
      `onPrint` dari `Screens.Laporan` (`:795`)
- [ ] `app/.../MainActivity.kt` -- hapus `onPrint = {}`
- [ ] `app/src/main/res/values/strings.xml` -- hapus `laporan_export_print`
- [ ] `LaporanScreenTest.kt`, `AccessibilityTest.kt`, `AppShellTest.kt`, `BannerScopeTest.kt` -- hapus
      argumen `onPrint`; ubah test dua tombol jadi satu; **tambahkan assertion `TAG_EXPORT_PRINT` tidak ada**
- [ ] `app/.../ui/screens/kendaraan/KendaraanScreen.kt:632-637` -- `Tersedia`→`Info`,
      `Tersewa`→`Success`; perbarui KDoc `:626-631`
- [ ] `KendaraanScreenTest.kt` -- assertion tone per status, bukan hanya label
- [ ] `app/.../ui/components/MasterDetail.kt:54-64,:80-89` -- hapus `.padding(xxl)` dari kedua cabang
- [ ] `app/src/test/kotlin/.../ui/shell/` -- ukur padding terluar di kedua orientasi, assert 24dp

**Acceptance Criteria:**
- Given Laporan dibuka, when top bar diperiksa, then hanya satu tombol aksi dan `laporan-export-print`
  tidak ada di pohon semantik
- Given tombol CSV diketuk di produksi, when callback-nya dipanggil, then `onExportFileChosen` menerima
  Uri dari `CreateDocument`
- Given armada berisi Tersedia/Tersewa/Servis, when barisnya dirender, then tone-nya Info/Success/Warning
- Given tiap master-detail screen di ≥600dp dan <600dp, when padding terluar diukur, then 24dp pada keduanya
- Given `./gradlew :domain:test :app:testDebugUnitTest :app:assembleDebug`, then SUCCESS

## Implementation Notes

## Spec Change Log

## Review Triage Log

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
- `./gradlew :domain:test :app:testDebugUnitTest :app:assembleDebug` -- expected: SUCCESS
- `git grep -n "onPrint\|laporan_export_print\|TAG_EXPORT_PRINT" app/` -- expected: **no match**
- `git grep -n "\.padding(" app/.../ui/components/MasterDetail.kt` -- expected: no outer padding block

**Manual checks (butuh perangkat):**
- Laporan: satu tombol aksi; CSV membuka picker
- Kendaraan: Tersedia biru, Tersewa hijau, Servis kuning
- Ketiga master-detail screen di 711dp portrait dan landscape: jarak tepi 24dp, bukan 48dp