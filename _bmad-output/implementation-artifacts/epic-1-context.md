# Epic 1 Context: Angka yang bisa dipercaya

<!-- Generated from planning artifacts. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Andi punya mesin hitung tunggakan yang terbukti benar lewat test, sebelum aplikasi punya satu pun layar. Murni modul `domain/` pure-JVM — dua modul Gradle dengan jebakan build yang sudah diketahui sudah ditutup, mesin periode cutoff dan hari tagih, mesin alokasi kronologis yang menyimpan fakta "setoran ini menutup hari ini", dan angka hari menunggak yang dikirim ke penyewa lewat WhatsApp. Tujuh kasus numerik dari verifikasi FR-10 adalah **definition of done** — bukan gate yang menggantung, karena di epic ini tidak ada apa pun yang bisa gagal selain angkanya. Budgednya juga menutup dua lubang yang belum diputuskan saat dokumen ditulis: apakah mengubah `cutoffDay` menurunkan ulang alokasi atau cuma menggeser cakupan, dan bukti angka untuk batas performa. `./gradlew :domain:test` jalan tanpa Android SDK sama sekali.

## Stories

- Story 1.1: Proyek yang benar-benar bisa jalan dan diuji tanpa SDK
- Story 1.2: Periode cutoff, hari kerja, dan pengecualian
- Story 1.3: Alokasi dan Kredit yang tidak pernah hilang
- Story 1.4: Hari menunggak dan tingkat risiko
- Story 1.5: Mengubah Tanggal cutoff hanya menggeser cakupan
- Story 1.6: Mengukur performa, bukan menebaknya

## Requirements & Constraints

**Uang.** Semua nilai uang adalah `Long` rupiah penuh tanpa titik desimal — tarif harian, nominal setoran, tunggakan, kredit — di domain maupun di database. Tidak pernah `Double`, `Float`, atau tipe desimal. Format ke layar lewat satu fungsi `formatRupiah()`; parse input lewat satu fungsi di layer terendah yang mengembalikan `null` dan menolak, **tanpa nilai default diam-diam**. Ini persis mode gagal `parseInt(x) || 250000` yang ditemukan di prototipe.

**Alokasi disimpan, bukan dihitung ulang.** Setiap Setoran meninggalkan jejak hari tagih yang ditutupnya (`paymentId`, `chargeDate`, `amount`). Yang disimpan adalah fakta, bukan hasil hitungan — itu yang membuat Kredit bertahan lintas periode tanpa perlu menghitung ulang atau menyimpan angka Kredit terpisah. Ada satu aturan tunggal tanpa pengecualian: **Σ alokasi per Setoran == amount Setoran itu**. Pelanggaran itu adalah bug, bukan kondisi sah. Kolom `chargeDate` boleh `null` sebagai penanda "dana ada, hari belum ditentukan"; baris itulah yang membuat kelebihan tidak hilang diam-diam.

**Periode dan hari tagih.** Periode cutoff berjalan = Tanggal cutoff sampai satu hari sebelum cutoff bulan berikutnya; Tanggal cutoff 1–28, dibatasi supaya periode selalu punya minimal satu hari di bulan terpendek. Hari tagih = hari dalam periode, sejak tanggal mulai Kontrak, bukan hari libur (jadwal 6 hari), bukan Pengecualian Servis, dan tidak setelah hari ini. Pembayaran pada hari yang tidak ditagih — hari libur, pengecualian servis, sebelum kontrak dimulai — **tetap masuk pool** dan dialokasikan ke Hari tagih terdekat.

**Mutasi menurunkan seluruh alokasi.** Pembatalan dan perubahan nominal keduanya menghapus semua baris alokasi milik Setoran itu lalu menghitung ulang seluruh Kontrak dari Setoran yang tersisa, dalam satu transaksi. Penanda "tandai lunas sampai tanggal" **tidak** menulis baris alokasi sama sekali — ia mengubah `settledThroughDate` pada Kontrak, dan mesin hitung memperlakukannya sebagai hari tagih yang sudah tertutup. Ada indeks unik nyata pada `(paymentId, chargeDate)` — itu koreksi kebenaran, bukan performa, karena tanpa itu satu Setoran bisa punya dua baris untuk hari yang sama dan Σ alokasi melebihi amount.

**Mengubah Tanggal cutoff hanya menggeser cakupan.** Tidak ada baris alokasi yang dihapus atau ditulis ulang; hanya rentang `chargeDate` yang masuk hitungan berubah. Ini keputusan yang sudah diambil, bukan yang masih terbuka.

**Hari menunggak** = jumlah Hari tagih dalam periode berjalan yang belum tertutup oleh alokasi. Karena tarif harian Kontrak konstan selama masa kontrak, hitungan ini setara dengan Tunggakan dibagi tarif, dibulatkan ke atas — tapi yang dipakai adalah alokasi karena langsung tersedia dari mesin yang sama. Yang benar-benar salah di prototipe adalah clamp `Math.min(missedDaysList.length, ...)`: daftar itu hanya memuat hari yang pembayarannya kurang pada tanggal itu, sehingga overpay besar menyembunyikan tunggakan sungguhan. Tingkat risiko: 0 = Lancar, 1–2 = Ringan, 3–6 = Perhatian, 7+ = Kritis. Urutan daftar: nominal terbesar dulu, lalu Hari tagih tertua. Kontrak yang sudah berakhir dengan sisa Tunggakan **tetap dihitung dan tetap masuk Tunggakan aktif** sampai lunas.

**Performa sebagai bukti, bukan klaim.** Hitung ulang penuh untuk 20 Kontrak, 2.000 Setoran, 500 hari tagih harus selesai di bawah 50 milidetik, dan angka hasilnya dicatat sebagai baseline. Kalau ambang itu tidak tercapai di perangkat, ambangnya diturunkan atau diabaikan secara eksplisit dan tercatat — **tidak boleh dihapus diam-diam**. Jangan mengorbankan kebenaran demi kecepatan.

**Tujuh kasus numerik yang harus hijau sebelum epic selesai** (acuan: hari ini 2026-03-24, cutoff 25, tarif Rp 250.000, periode 2026-02-25 → 2026-03-24 = 28 hari tagih):
1. Setoran Rp 500.000 → menutup dua hari tertua, tunggakan Rp 6.500.000.
2. Setoran Rp 250.000 bertanggal **Minggu (1 Mar)** pada kontrak 6 hari → tetap dikreditkan, dialokasikan ke Senin 2 Mar; tunggakan turun, bukan naik. Ini regresi bug prototype yang nilainya Rp 1.000.000 error per bulan.
3. Setoran sebagian Rp 100.000 → alokasi parsial, tidak ada hari teralokasi dua kali.
4. Setoran Rp 20.000.000 → 28 hari teralokasi penuh + **satu baris `chargeDate = null` berisi sisa Rp 13.000.000**; 29 baris total. Kredit itu harus terpakai di periode berikutnya (25 Mar → 24 Apr). Regresi temuan verifikasi — tanpa alokasi tersimpan, Rp 13 juta hilang diam-diam.
5. Kontrak backdated tanpa Setoran → `settledThroughDate` menutup semua hari tagih sebelum tanggal itu; tidak ada baris alokasi yang ditulis untuknya.
6. Pembatalan Setoran Rp 250.000 → alokasi dilepas, tunggakan naik Rp 250.000 dan satu hari.
7. Setoran bertanggal reddit (30 Mar saat hari ini 24 Mar) → tidak dihitung.

## Technical Decisions

**Domain adalah modul Gradle terpisah, bukan package di dalam `app`.** `domain/` adalah `kotlin("jvm")` tanpa `android {}` block sama sekali, sehinggaGradle yang menegakkan aturan "tidak ada Android di domain" — impor `android.*` jadi build failure, bukan teguran di review. Ini juga yang membuat `./gradlew :domain:test` jalan tanpa Android SDK terpasang: `./gradlew test` di app gagal saat configuration time dengan "SDK location not found".

**Aturan tanggal.** Semua "hari ini" lewat satu `Clock` abstraction di `domain/` yang default-nya `LocalDate.now()`. Tidak ada konstanta tanggal hardcoded, tidak ada `SIM_TODAY`. Test menyuntik tanggal tetap lewat `Clock`.

**Snapshot tunggal untuk angka yang keluar.** Angka tunggakan yang dikirim ke penyewa harus berasal dari satu pembacaan yang mengembalikan objek `ArrearsSummary` lengkap, bukan dari beberapa query yang digabung di layar. Kalau ada pembatalan yang jalan di antaranya, nomor yang terkirim berasal dari dua keadaan berbeda — dan nomor itu keluar ke pelanggan tanpa ada undo.

**Tanpa sinyal invalidasi manual.** Room sudah punya `InvalidationTracker` yang otomatis mengemasi ulang `Flow`; menambah `invalidate()` manual menghasilkan dua mekanisme refresh yang bisa berbeda. Pakai `combine` + `stateIn` + `distinctUntilChanged()`.

**Jebakan build yang sudah diketahui dan wajib ditutup di Story 1.1.** Core library desugaring wajib aktif (`com.android.tools:desugar_jdk_libs:2.1.5`) — tanpa itu `LocalDate.now()` melempar `NoClassDefFoundError` dan aplikasi crash saat start di minSdk 24. Plugin `org.jetbrains.kotlin.android` **tidak boleh** dipasang pada AGP 9 (hard build failure; Kotlin bawaan AGP 9). `composeOptions.kotlinCompilerExtensionVersion` sudah dihapus — wajib plugin `org.jetbrains.kotlin.plugin.compose` dengan versi sama persis dengan Kotlin. `kotlin { jvmToolchain(17) }` — Gradle boleh jalan di JVM lokal 25, compile harus ke 17. Versi dipin: AGP 9.4.1, Gradle 9.8.1, **Kotlin 2.3.21 (bukan 2.4.x — KSP 2.3.12 hanya mendukung KGP 2.2.10–2.3.x)**, KSP 2.3.12, Compose BOM 2026.09.00, Room 2.8.5, Lifecycle 2.11.0, minSdk 24 · targetSdk 36 · compileSdk 37.

**Exception domain** berupa tipe spesifik yang jelas (`InvalidAmountException`, `DuplicatePlateException`, `ContractAlreadyActiveException`) — tanpa `Result<_, String>` berisi pesan, karena pesan adalah urusan lapisan di atas.

## Cross-Story Dependencies

Story 1.6 bergantung pada Story 1.1 (butuh `./gradlew :domain:test` yang jalan) dan pada ketujuhan Story 1.2–1.5, karena benchmark mengukur mesin yang sudah jadi. Story 1.2 mendahului 1.3 dan 1.4 — semua hitungan tunggakan membaca dari satu sumber tanggal. Kontrak database (`CONTRACT.settledThroughDate`, tabel `ALLOCATION`) didefinisikan di sini karena mesinnya yang membacanya, meski entitasnya baru ditulis di epic lain. Tidak ada dependensi ke epic lain untuk menjalankan epic ini.

## Performance Baseline

**Skala NFR-1:** 20 Kontrak, 2.000 Setoran, 500 hari tagih (25 hari × 20 Kontrak).
Test-nya: `domain/src/test/kotlin/net/klinik/rental/domain/calculate/BenchmarkTest.kt`.

| Nilai | Jumlah | Keterangan |
|---|---|---|
| On-device target (NFR-1) | 50 ms | Redmi Pad 2, mode debug. Tidak pernah di-assert di CI — kondisi perangkat tidak bisa direproduksi di mesin build. |
| CI threshold | 500 ms | Proksi build-machine. `CI_THRESHOLD_MS` di `BenchmarkTest.kt`. Longgar untuk variance mesin, ketat untuk regresi ordo-besar di engine. |
| Baseline terukur (perubahan `c801775`, 2026-10-13) | **14 ms** | Dari log `:domain:test` di mesin `IOT04`: `NFR-1 benchmark: 2000 allocation rows across 20 contracts, 2000 payments, 500 charge days in 14 ms (on-device target: 50 ms)`. Test mencetak angka ini ke stdout tiap run; bagian ini mencatatnya agar bisa dibandingkan antar-commit. Angka ini **bukan** angka on-device — 50 ms adalah target perangkat, 14 ms adalah proksi mesin build di commit saat file ini ditulis. |

**Aturan:** `CI_THRESHOLD_MS` tidak boleh diturunkan atau dihapus secara diam-diam (NFR-1, "tidak boleh dihapus diam-diam"). Perubahan nilai ambang — naik maupun turun — adalah keputusan tingkat spec yang harus dicatat di bagian ini beserta alasannya sebelum perubahan itu dibuat di kode.