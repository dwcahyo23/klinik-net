---
title: "Party Review — Owner (Andi) & Field Operator"
status: review
scope: 'prd.md, epics.md, architecture-spine.md, verify-fr10.md, addendum.md, EXPERIENCE.md'
reviewed: 2026-10-13
---

# Party Review — Dua Sudut, Satu Tujuan: Di Mana Plan Ini Gagal untuk Orang yang Pakai

Dua orang membaca plan yang sama dan mencari hal yang sama: **titik di mana plan ini gagal untuk pengguna akhir.**

---

# BAGIAN 1 — PEMILIK (Andi)

> 18 mobil, 14 penyewa. Sendirian. Duduk di meja. Bayar sewa setiap hari.
> Masalahnya: Excel berantakan dan dia tidak bisa melihat siapa yang masih owes dia.

## 1.1 Senin Pagi Setelah Epic 1 Selesai

Epic 1 selesai persis seperti tertulis di `epics.md`: FR1, FR1b, FR2, FR3, FR4, FR5, FR6, FR7, FR8, FR9, FR10, FR11, FR13, FR19.

| # | Yang dia lakukan | Yang dia temukan |
|---|---|---|
| 1 | Buka aplikasi | Nav rail 7 tujuan: Beranda · Setoran · Kendaraan · Penyewa · **Tunggakan** · **Laporan** · **Pengaturan**. Tiga yang terakhir belum dibangun. |
| 2 | Lihat Beranda | 4 KPI: **semua nol**. Tidak ada satu pun data. |
| 3 | Tekan "Catat Setoran" | Dipilih Penyewa. **Daftar kosong.** |
| 4 | Harus isi 18 mobil dulu (FR-4) | ~126 nilai: plat, merek, tipe, tahun, warna, tarif harian, catatan. Numpad on-screen untuk tarif (UX-DR8). Tidak ada impor. Tidak ada wizard (NFR-4 melarang). Tidak ada indikator progres. |
| 5 | 14 penyewa (FR-6) | ~56 nilai lebih. |
| 6 | 14 kontrak (FR-7) | ~84 nilai lebih. Tanggal mulai dibuat mundur, seperti yang diizinkan FR-7. |
| 7 | Melihat hasilnya | **Setiap kontrak langsung menunjukkan Tunggakan fiktif.** `verify-fr10.md` Kasus 5: satu kontrak saja = **Rp 6.000.000, 28 hari, Kritis**. Untuk 14 kontrak: sekitar Rp 84 juta yang tidak ada. |
| 8 | Cari tombol untuk membersihkan ini | **Tidak ada.** Tombolnya FR-9b "tandai lunas sampai tanggal" — dan FR-9b ada di **Epic 2**. |
| 9 | Coba jalan pintas: catat setoran historis satu per satu | 80–100 setoran. `verify-fr10.md` sendiri menulis: *"Hari pertama aplikasi akan terasa seperti gagal."* |
| 10 | Buka Tunggakan | Tidak ada tombol WhatsApp — itu FR-12, **Epic 2**. |
| 11 | Kembali ke Excel | Excel masih menunjukkan angka yang benar. |

**Hari pertama selesai di titik 11.** Dan bukan karena aplikasi salah — karena plan menaruh tombol yang membuatnya benar di epic yang salah.

## 1.2 Plan Jujur tentang ~14 Penyewa + ~18 Mobil yang Harus Diketik Manual?

**Tidak. Tidak ada satu pun dokumen yang menyebut biayanya.**

- PRD §6.1: *"Aplikasi mulai dari data kosong. Tidak ada fitur impor, dan tidak ada jalur migrasi dari Excel."* — satu kalimat, nol angka.
- PRD §6.2 memberi alasan impor Excel tidak direncanakan: *"Tidak ada data lama yang perlu dimigrasikan."* **Kalimat ini salah untuk Andi.** Dia punya data lama; itu seluruh premis proyeknya (§1: sudah menggunakan spreadsheet Excel). Yang benar: tidak ada data lama yang sudah berbentuk data aplikasi.
- NFR-4 melarang wizard, dan tidak ada FR yang menggantikannya.

**FR mana yang "input first car"?** → **FR-4 (Kelola Kendaraan).** Dan itulah masalahnya. FR-4 adalah kata kerja CRUD — tambah, ubah, lihat. Ia tidak tahu apa yang pertama, tidak tahu urutan, dan tidak tahu bahwa setelah 32 entri ia masih belum bisa menjalankan satu pun aksi yang dia cares about.

**Biaya yang tidak disebut:** 18 kendaraan × 7 field + 14 penyewa × 4 field + 14 kontrak × 6 field ≈ **284 nilai yang diketik**. Dengan numpad on-screen untuk semua uang (UX-DR8), realistis **2–3 jam**. Di spreadsheet itu 30 menit.

## 1.3 Kalau Dia Hanya Pakai Satu Layar, Mana?

**Tunggakan.** PRD §4.4 sendiri: *"Ini layar yang paling sering dibuka, dan tempat nilai utama aplikasi ini."* Dan SM-3 mengukurnya. Masalah yang dia sebutkan adalah "tidak bisa melihat siapa yang owes dia."

**Apakah struktur epic menjadikannya prioritas, atau menguburnya?** Menguburnya — dan itu karena urutan, bukan scope.

| Bagian dari layar Tunggakan | Epic |
|---|---|
| Hitungannya benar (FR-10) | **Epic 1** |
| Urutan + jumlah hari + warna risiko (FR-11) | **Epic 1** |
| Tombol WhatsApp (FR-12) | **Epic 2** |
| Escape hatch untuk kontrak backdated (FR-9b) | **Epic 2** |
| Angka WhatsApp dari snapshot tunggal (AR-9) | **Epic 2** |

**4 dari 5 bagian layar yang dia peduli berada di luar Epic 1.** Yang Epic 1 berikan: angka yang benar, dan tidak ada yang bisa dia lakukan dengan angka itu.

Sebaliknya, **7 dari 14 FR di Epic 1 adalah master data** (FR-4, FR-5, FR-6, FR-7, FR-8, FR-9, sebagian FR-1b) — 32 entri yang harus diketik sebelum layar yang dia pedulikan menyala.

## 1.4 Tiga Alasan Paling Mungkin Dia Kembali ke Excel

### 1. Angka hari pertama tidak bisa dipercaya, dan tombol yang memperbaikinya ada di Epic 2

Dia buat 14 kontrak backdated. Semuanya langsung Kritis. Rp 84 juta yang tidak ada. Satu-satunya jalan keluar adalah FR-9b — dan itu **Epic 2**. Aplikasi tidak salah, tapi ada 32 hari di mana aplikasi terlihat salah, dan orang berhenti membandingkan aplikasi dengan kenyataan selama 32 hari.

**Epic yang harus mencegahnya: Epic 1.** `prd.md` §4.3 sudah menulis alasannya sendiri — FR-9b ada tepat karena FR-7 mengizinkan tanggal mulai mundur. Prasyaratnya di Epic 1, penawarnya di Epic 2, dan resikonya di antaranya.

### 2. Tiga hari setup tanpa papan kemajuan, dan berhenti di tengah terlihat seperti selesai

~284 nilai, nol impor, nol wizard, nol indikator "13 dari 18 mobil". Kalau dia berhenti di mobil ke-9, aplikasi yang tersisa **tidak terlihat seperti setengah jadi** — dia terlihat seperti akun kosong dengan tunggakan Rp 0, yang terbaca "semua orang lunas". Itu kondisi paling berbahaya di aplikasi ini, dan NFR-4 mengirimkannya ke sana.

**Epic yang harus mencegahnya: Epic 1** — dengan FR setup yang jujur, bukan dengan menambah scope. Butuh urutan yang eksplisit (kendaraan → penyewa → kontrak), angka progres, dan kondisi berhenti-di-tengah yang terasa seperti progres.

### 3. Dia tidak bisa mengeluarkan data sampai Epic 3, jadi Excel tetap jadi master

FR-16 (ekspor/pemulihan) ada di **Epic 3, epic terakhir**. UJ-4 di PRD berisi ketakutan eksplisitnya: *"tablet saya hilang tertinggal di angkot."* Selama Epic 1 dan 2 berjalan, aplikasi ini adalah satu-satunya salinan catatan uangnya dan tidak ada jalan keluar. Kalau tablet hilang di minggu ketiga, semuanya hilang — dan Excel, yang ada di laptop, masih utuh. Itu lebih buruk dari Excel, bukan lebih baik.

**Epic yang harus mencegahnya: Epic 3, tapi harus paling depan di dalamnya.** Ekspor JSON adalah operasi kecil di modul yang sudah ada. Meletakkannya sebelum PIN (FR-17) dan cutoff (FR-15) hanya membalik urutan dua fitur yang tidak bisa membuat aplikasi hilang di tangan orang lain.

## 1.5 Yang Epic 1 Buat Lebih Buruk

- **Tombol mati di Beranda.** FR-13 (Epic 1) menjanjikan empat aksi cepat termasuk "Kirim Pengingat" — itu FR-12, **Epic 2**. Setelah Epic 1, satu dari empat tombol membuka layar yang belum ada. UX-DR12 mensyaratkan setiap layar punya empat state; tombol yang membuka apa-apa tidak punya state sama sekali.
- **FR-5 mengulang bug prototipe yang sudah diketahui.** FR-5 membolehkan Andi menandai status Kendaraan manual, lalu menyatakan status itu "informasi tampilan, bukan sumber kebenaran." `addendum.md` §2.1 sudah merekam bug yang sama: `car.status` tidak sinkron dengan Kontrak dan angka dashboard bisa salah.
- **Numpad vs. konteks bertentangan.** `EXPERIENCE.md` §1 menyebut konteks pemakaian "duduk di meja depan warung". `UX-DR8` membenarkan numpad dengan "dipakai sambil berdiri/memegang sesuatu". Dua dokumen bound berbeda tentang cara dia duduk. Numpad 3×4 yang lambat untuk 284 input adalah pilihan yang benar untuk berdiri dan salah untuk duduk.

---

# BAGIAN 2 — FIELD OPERATOR

> Dev kompeten Kotlin/Compose, orang pertama yang menulis ini. Tujuh test dari `verify-fr10.md` adalah gerbang.

## 2.1 Klaim "Satu Loop, File Sama, Memecah Bikin Churn"

Klaimnya di `epics.md`: *"Ini loop tertutup: master data → kontrak → setoran → tunggakan → dashboard. Semuanya menyentuh skema Room, repository, dan design system yang sama."*

**Separuh benar, separuh salah, dan bagian yang salah adalah bagian yang costly.**

### Yang benar-benar berbagi file (inti uang, ~10 FR)

| FR | File yang benar-benar dipakai bersama |
|---|---|
| FR1, FR1b, FR2, FR3 | `domain/calculate/AllocationEngine.kt`, `PaymentEntity.kt`, `AllocationEntity.kt`, `ui/screens/setoran/`, `data/repository/RentalRepository.kt` |
| FR7, FR8, FR9 | `ArrearsCalculator.kt`, `CutoffPeriod.kt`, `ContractEntity.kt`, `ServiceExceptionEntity.kt` — semua lewat charge-day generation |
| FR10, FR11 | `ArrearsCalculator.kt`, `data/db/dao/ArrearsDao.kt`, `domain/model/ArrearsSummary.kt` |
| FR13 | `ui/screens/beranda/`, membaca `ArrearsDao` yang sama |

Klaim ini bertahan di sini. Satu loop, satu allocation engine, satu query snapshot.

### Tiga FR yang tidak berbagi file Kotlin yang sama dengan loop

**FR-6 (Kelola Penyewa).** Miliknya sendiri: `RenterEntity.kt`, `RenterDao`, `ui/screens/penyewa/RenterScreen.kt` + `RenterViewModel.kt`, form composable sendiri. Satu-satunya aturan domain-nya adalah `UNIQUE` pada `name`, dan itu hidup di skema Room (AR-12), bukan di `domain/calculate/`. Nol sentuhan ke `AllocationEngine`, nol ke `ArrearsDao`.

**FR-4 dan FR-5 (Kelola Kendaraan + status).** `VehicleEntity.kt`, `VehicleDao`, `ui/screens/kendaraan/`. Satu-satunya bagian domain-nya adalah validasi — `DuplicatePlateException` dan `dailyRate <= 0` — dan itu exception type di `domain/validation/`, bukan kalkulator. FR-5 sendiri menyatakan status "bukan sumber kebenaran", jadi secara definisi ia tidak menyentuh logika kontrak.

**FR-19 (Pencarian global).** Satu query DAO + satu composable search yang di-inject ke scaffold navigasi. Tidak memodifikasi logika layar mana pun. Nol file bersama dengan inti hitung.

**Artinya:** dari 14 FR di Epic 1, **4 tidak menyentuh file yang sama dengan loop uang** (FR-4, FR-5, FR-6, FR-19) — persis FR yang paling tidak butuh satu sama lain, dan yang energinya habis untuk mendata ulang. Yang benar-benar nyata adalah fraksi 10/14, dan fraksi itu sendiri sudah terpecah jadi dua kelompok yang tidak perlu satu sama lain: **(master data + search)** dan **(kontrak + setoran + hitung)**.

Yang lebih penting: pemecahan Epic 1/2 yang dikritik justru **menghemat di titik yang benar**. FR-12 dan FR-9b menyentuh `ArrearsScreen.kt` dan detail Kontrak — file yang FR-11 dan FR-7 sudah kerjakan di Epic 1. Yang tidak butuh dipecah adalah yang sudah terpisah.

## 2.2 Tujuh Test — Apa yang Tidak Dispesifikasi

Architecture memberikan nama file. `verify-fr10.md` memberikan tujuh skenario numerik. Di antara keduanya ada lubang yang harus diisi sendiri, dan tiga di antaranya menentukan apakah hitungannya benar.

### Yang harus dikarang (risiko rendah)

1. **Seluruh tanda tangan fungsi.** Tidak satu pun tertulis di mana pun. `ArrearsCalculator.arrears(contract, payments, asOf): ArrearsSummary`? Kelas dengan `Clock` di constructor? Saya pilih yang pure dan bisa diuji.
2. **Field `ArrearsSummary`.** Disebut di ERD dan di Structural Seed, tapi field-nya tidak pernah ditulis. Saya harus menentukan isinya, dan menentukan apakah risk tier (`Lancar`/`Ringan`/`Perhatian`/`Kritis`) hidup di `domain/model/` atau di `ui/`. Saya taruh di domain — itu tebakan yang mengikat semua test.
3. **`CutoffPeriod` API.** Batas period jelas dari §3 glossary ([25, 24] inklusif kedua ujung). API-nya tidak: `of(today, cutoffDay)`, `contains(date)`, `chargeDays(contract)`.
4. **Bentuk `DailyCharge`.** Tidak terdefinisi. Saya pilih `data class DailyCharge(val date: LocalDate, val amount: Long)`.
5. **Fixture builder untuk test.** Tidak ada. Saya tulis sendiri untuk 7 test.

### Yang hanya bisa ditebak, dan tebakannya menentukan uang

**6. Bagaimana `chargeDate = null` (Kredit) dikonsumsi antar-periode.** Lubang terbesar di seluruh plan. AD-3 menyatakan baris NULL adalah "dana ada, hari belum ditentukan" dan menyebutnya satu-satunya hal yang membuat Kredit bertahan lintas periode. Tapi **tidak ada dokumen yang menyatakan bagaimana baris itu dikurangi saat periode berikutnya tiba.** Apakah `amount`-nya dimutasi (bertentangan dengan AD-3: yang disimpan adalah fakta, model hitung-ulang-dari-awal dilarang), atau kredit dihitung ulang dari `payments` (persis Opsi A yang `verify-fr10.md` tolak)? Dua aturan AD-3 yang keduanya benar bertemu di sini, dan tidak ada yang menang. **Test 4 tidak bisa ditulis tanpa mengarang ini duluan.**

**7. Apakah hari dengan coverage parsial dihitung "tertutup".** Kasus 3: setoran Rp 100.000 melawan tarif Rp 250.000. `verify-fr10.md` menyimpulkan 28 hari menunggak dari 28 — parsial bukan tertutup. Tapi tidak satu pun dokumen menyatakan mechanismenya: apakah `DailyCharge` punya `coveredAmount`, atau coverage dihitung dengan menjumlahkan allocation per `chargeDate`? Saya akan menjumlahkan allocation dan menyatakan tertutup iff `Σ == charge`. Itu pembacaan natural, dan itu menentukan apakah angka "hari menunggak" yang dikirim ke WhatsApp benar.

**8. Pembayaran sebelum kontrak dimulai — "dialokasikan ke Hari tagih terdekat" (FR-10).** Terdekat ke mana? Pembayaran 1 Januari untuk kontrak yang mulai 15 Januari: ke 15 Januari (maju), atau ke hari tagih terakhir periode sebelumnya (mundur, di luar periode)? Tidak didefinisikan, tidak diuji, dan itu persis kasus yang paling mungkin Andi temui di hari pertama.

### Yang tidak ada test-nya padahal wajib ada

**Kontrak yang sudah berakhir.** FR-10 mewajibkan kontrak berakhir tetap dihitung tunggakannya sampai lunas dan tetap masuk Tunggakan aktif; FR-11 mewajibkan jumlah hari dan risk tier-nya. **Tujuh test tidak punya satu pun kasus kontrak berakhir.**

### Kontradiksi antara dua dokumen yang sama-sama bound

**Definisi Pool Setoran berbeda antara PRD dan addendum.**

- `prd.md` FR-10: Pool = Setoran "yang masuk Periode cutoff berjalan", dan "Setoran dari periode sebelumnya tidak dihitung ulang ke periode ini."
- `addendum.md` §1.1: `pool = Σ setoran_lunas(kontrak, tanggal_mulai ≤ d ≤ hari_ini)` — semua setoran sejak kontrak mulai.

Kontrak backdated dengan setoran Rp 250.000 tanggal 5 Januari, hari ini 24 Maret:
- Versi PRD → Pool = Rp 0 → Tunggakan Rp 7.000.000.
- Versi addendum → Pool = Rp 250.000 → Tunggakan Rp 6.750.000.

Tidak sama. **Tidak satu pun dari tujuh test menyentuhnya** — Kasus 4 memakai pembayaran 25 Feb yang kebetulan jatuh di dalam periode, jadi tidak membedakan. Dan kasusnya persis yang paling mungkin Andi temui di hari pertama.

## 2.3 Hal PERTAMA yang Akan Saya Salah

**Representasi sisa: baris alokasi dengan `chargeDate = null`.**

Bukan karena tidak mengerti — semua yang perlu sudah ada, tapi satu mekanik penentu tidak. Minute pertama kalkulator dijalankan terhadap data nyata adalah minute pertama saya harus memutuskan apa itu sisa yang belum ditempatkan. Setiap kontrak backdated dan setiap setoran yang melebihi kewajiban periode berjalan langsung menghadapinya.

AD-3 memberi dua perintah yang bertabrakan tepat di titik ini. Ia berkata "yang disimpan adalah fakta, bukan hasil hitungan. Model hitung-ulang-dari-awal dilarang." Tapi ia juga said "jumlah allocation per Setoran selalu sama dengan `amount` Setoran itu, tanpa pengecualian." Kalau baris NULL adalah fakta yang kekal, bagaimana dikurangi ketika periode berikutnya membuat charge days baru? Kalau amount-nya divariasikan, aturan pertama dilanggar. Kalau dihitung ulang dari `payments` setiap kali, itu **Opsi A yang `verify-fr10.md` menolak secara eksplisit**, dan saya menyalin pola yang paling mahal tepat di tempat yang paling berbahaya.

Saya akan membangun yang salah lebih dulu dari yang saya kira. Karena test 1, 2, 3, 6, 7 semuanya hijau pada implementasi hitung-ulang-dari-awal — cara termudah yang membuat semua angka terlihat masuk akal. **Test 4 adalah satu-satunya yang menangkapnya**, dan test 4 ada di urutan ketujuh. Jadi saya akan melihat 6 dari 7 hijau, melangkah ke dialog pratinjau, dan men'(\*) write jalur kalkulasi kedua — preview harus pure (data belum disimpan), simpan harus transaksional. Dua implementasi aturan yang sama. Keduanya hijau.

Baru pada FR-15 (ubah cutoff, Epic 3), atau pada pembayaran pertama yang melintasi batas periode, keduanya akan berbeda — dan tidak ada yang bisa menjelaskan kenapa, karena keduanya secara individual terlihat benar.

**Yang harus ada sebelum saya mulai, dan tidak ada di mana pun:** satu kalimat yang menyatakan siapa pemilik konsumsi kredit — apakah baris NULL dimutasi di dalam `@Transaction` yang sama yang membuat baris bertanggal baru, atau kredit dihitung ulang dari Payments setiap kali. Tanpa itu, dua aturan AD-3 itu tidak bisa dua-duanya benar.

---

# RINGKASAN YANG BERLAKU

**Untuk pemilik:** masalahnya bukan fiturnya kurang. Masalahnya **hari-hari pertama aplikasi tidak bisa dipercaya, dan dia tidak punya apa pun untuk dilakukan selama dua dari lima bagian layar yang dia peduli.** Yang harus dipindah ke Epic 1: FR-9b dan FR-12. Yang harus dipindah ke depan Epic 3: FR-16, karena tanpa itu aplikasi adalah perangkap data selama berminggu-minggu.

**Untuk operator:** loop uangnya memang satu loop dan memang sebaiknya tidak dipecah — klaim itu benar untuk ~10 dari 14 FR. Yang tidak ikut loop adalah FR-4, FR-5, FR-6, FR-19, dan mereka justru yang paling bisa dikirim duluan untuk mengurangi 284 input setup. Yang belum dispesifikasi dan harus dikarang sebelum kalkulator pertama ditulis: **bagaimana kredit (`chargeDate = null`) dikonsumsi antar-periode**, dan **apakah hari berscoverage parsial dihitung tertutup**. Plus definisi Pool Setoran yang berbeda antara `prd.md` dan `addendum.md` — dua-duanya bound, dua-duanya dipakai, dan tujuh test tidak menyentuhnya.