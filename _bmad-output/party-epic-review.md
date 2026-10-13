# Party Review — Struktur 3 Epic (dev.klinik.net)

> Tanggal: 2026-10-13
> Dibandingkan: `epics.md` (3 epic) vs `prd.md` (21 FR) vs `architecture-spine.md` (AD-1..AD-9)
> Posisi: **review adversarial.** Tujuan dokumen ini bukan melegitimasi struktur yang ada, tapi mencari tempat salah sebelum cerita ditulis.

---

## 0. Verdict

**Struktur 3 epic salah pada batas yang memuat FR-10.** Bukan salah jumlah — tapi batas Epic 1 memuat dua hal dengan profil risiko berlawanan (mesin hitung pure-JVM vs 12 layar CRUD), dan batas Epic 3 memisahkan FR-15 dari mesin yang FR-15 harus panggil. Rekomendasi: **5 epic**, dengan mesin tunggakan sebagai epic pertama yang berdiri sendiri.

---

## 1. Klaim "semua menyentuh skema Room yang sama, jadi satu epic" — adalah rationalization, bukan fakta

### 1.1 Hitungan layar, bukan hitungan repository

`FR-4..FR-9` + `FR-19` + `FR-13` = **8 dari 14 FR Epic 1 tidak punya satu baris pun hubungannya dengan aritmetika tunggakan.**

| FR | Layar | Butuh `ArrearsCalculator`? |
|---|---|---|
| FR-4 CRUD Kendaraan | `cars.html` | Tidak |
| FR-5 Status Kendaraan | `cars.html` | Tidak |
| FR-6 CRUD Penyewa | `renters.html` | Tidak |
| FR-7 Buka Kontrak | `renters.html` | Tidak — hanya menulis baris `CONTRACT` |
| FR-8 Jadwal 6/7 + Servis | dialog Kontrak | Tidak — hanya menulis `SERVICE_EXCEPTION` |
| FR-9 Akhiri Kontrak | dialog Kontrak | Tidak — hanya menulis `endDate` |
| FR-13 Beranda | `dashboard.html` | **Ya** — 2 dari 4 KPI |
| FR-19 Pencarian | top bar, semua layar | Tidak |

"Repository yang sama" bukan alasan epic. Repository adalah **layer**, bukan nilai. Empat dari *enam* entitas Room (`VEHICLE`, `RENTER`, `SERVICE_EXCEPTION`, `CONTRACT` sebagian) tidak punya satu baris pun yang dibaca `ArrearsCalculator`. Yang dibaca kalkulator hanya `PAYMENT` + `ALLOCATION` + kolom tarif/jadwal dari `CONTRACT`. Jadi: satu Payment punya `contractId` FK — batas repository yang **memang ada**, dan justru memotong FR-4..FR-9 dari FR-10.

### 1.2 Bukti bahwa tidak ada FR yang benar-benar terikat

Tidak ada FR di Epic 1 yang **wajib** ada di epic yang sama dengan FR manapun juga. Yang ada hanya urutan (dependensi), dan dependensi bukan epic boundary. Satu epic dengan 14 FR dan 19 UX-DR + 18 AR + 9 NFR terikat padanya berarti **tidak ada titik di mana owner bisa bilang "cukup, ini jalan"** sampai semuanya rampung. Itu bukan iterasi yang aman, itu satu sprint yang tidak pernah selesai.

### 1.3 Test-driven, bukan fear-driven

Arsitektur sudah memberikan alat yang justru **membantah** klaim ini: `domain/` adalah modul Gradle pure-JVM (AR-6, AD-1). `./gradlew :domain:test` jalan tanpa Android SDK. Artinya `FR-10` + `FR-11` bisa dibangun, diverifikasi, dan **dinyatakan selesai sebelum satu pun baris Compose ditulis** — dan tujuh test yang jadi acceptance gate jalannya dalam **detik**, bukan menit dengan emulator.

Klaim "splitting menyebabkan churn" justru membalik ini: yang tidak bisa dipecah adalah *layer*, dan layer itu sudah dipisah oleh Gradle. Yang dipisah adalah *nilai*.

---

## 2. Acceptance gate Epic 1 tidak bisa hijau — FR-9b ada di Epic 2

Ini temuan paling keras, dan bukan opinion.

`architecture-spine.md` §Test Strategy, baris 5:

```
| 5 | Kontrak backdated + tandai lunas | FR-9b menutup semua hari lewat |
```

Test #5 dari 7 test yang menjadi **acceptance gate Epic 1** (`epics.md` baris 259) secara eksplisit menguji **FR-9b**. Tapi FR-9b di-**map ke Epic 2** (`epics.md` baris 194, 265).

Konsekuensinya: **Epic 1 tidak bisa dinyatakan selesai sebelum Epic 2 dikerjakan.** Risk boundary yang ditulis "kalau test itu belum hijau, epic ini belum selesai" adalah kondisi yang Epic 1 tidak pernah penuhi sendiri.

Yang benar: FR-9b **terbelah dua** dan saat ini dipotong di tempat yang salah.
- **Aturan domain** (`settledThroughDate` diperlakukan sebagai hari tagih tertutup, tanpa menulis baris alokasi — AD-3, AR-8) → ini **mesin**, milik Epic mesin.
- **Tombol + dialog konfirmasi** → ini UI, milik epic CRUD Kontrak/Setoran.

Sekarang FR-9b utuh di Epic 2, sementara *engine*-nya diam-diam sudah jadi bagian Epic 1 lewat AR-8. Cuts ini perlu dibuat eksplisit.

### 2.1 Dan FR-9b adalah syarat hari pertama, bukan alat cleanup

epics.md baris 263 menulis: *"Plus satu cara merapikan kontrak lama tanpa/input manual."* — kalimatnya **rusak**, dan **salah menggambarkan FR-9b**.

PRD baris 263 menyatakan alasan sebenarnya: FR-7 mengizinkan Kontrak dengan `startDate` lampau. Tanpa FR-9b, Kontrak seperti itu **hari pertama aplikasi** langsung menampilkan Tunggakan fiktif Rp 6.000.000–7.000.000 (verify-fr10.md Kasus 5: *"hari pertama aplikasi akan terasa seperti WTA yang gagal"*). Itu **onboarding**, bukan migrasi dan bukan cleanup.

PRD §6.1 tegas: **tidak ada impor, aplikasi mulai dari data kosong.** Justru itu yang membuat FR-9b **wajib**, bukan opsional — tanpa impor dan tanpa entri historis massal, FR-9b adalah satu-satunya jalan keluar dari Kontrak lampau. Argumen "kalau tidak ada migrasi, siapa yang pakai?" salah premise: **FR-9b ada justru karena migrasi tidak ada.**

Konsekuensi ke urutan: kalau FR-9b menunggu Epic 2, maka hasil ship-nya Epic 1 adalah aplikasi yang **hari pertama** menampilkan semua Kontrak Andi sebagai Kritis dengan utang fiktif. Itu kerusakan yang secara psikologis lebih merusak daripada bug Rp 1 juta/bulan di verify-fr10.md Kasus 2 — yang setidaknya menghasilkan angka yang **terlihat** salah.

---

## 3. FR-15 salah epic, dan ia menyembunyikan kontradiksi PRD ↔ arsitektur

Ini menjawab "apakah ada FR di mana PRD dan spine berbeda pendapat" — **ada, dan FR-15 adalah tempatnya.**

PRD FR-10 baris 282 masih menulis Pool Setoran secara **periode**:

> "Pool Setoran adalah jumlah seluruh Setoran lunas pada Kontrak tersebut **yang masuk Periode cutoff berjalan**, dengan tanggal antara batas bawah Periode … dan hari ini. Setoran dari periode sebelumnya **tidak dihitung ulang ke periode ini**."

`epics.md` FR-10 baris 44 sudah menulis model yang **berlawanan**: alokasi tersimpan, kelebihan jadi baris `chargeDate = null`. `addendum.md` §1 dan verify-fr10.md Kasus 4 sudah memutuskan: **opsi B (simpan alokasi), bukan A (simpan `creditBalance`)**. Architecture AD-3 mengikat opsi B. Jadi **PRD baris 282 adalah sisa dari model yang sudah ditolak dan tidak pernah dikoreksi.**

Bukti kontradiktifnya — Kontrak mulai 2026-01-01, tarif Rp 250.000, 7 hari, cutoff 25, periode 25 Feb → 24 Mar (28 hari). Satu Setoran Rp 13.000.000 tanggal **2026-02-10**:

| Model | Pool | Tunggakan | Kenyataan |
|---|---|---|---|
| PRD baris 282 (pool per periode) | Rp 0 — 10 Feb < 25 Feb | **Rp 7.000.000** | Rp 13.000.000 masuk bank |
| AD-3 / epics.md (alokasi tersimpan) | 28 hari teralokasi + sisa `chargeDate=null` | **Rp 0** | ✓ |

Selisihnya **Rp 7.000.000** dan muncul langsung di KPI "Tunggakan aktif" (FR-13).

**Mengapa ini penting untuk batas epic:** `FR-15` (ubah cutoff) adalah **satu-satunya FR yang menggeser `cutoffDay`**, dan ia sekarang di Epic 3 — dua epic jauh dari mesin yang mengimplementasikannya. Loket pratinjau FR-15 ("berapa Kontrak dan berapa rupiah Tunggakan yang berubah") secara teknis adalah **menjalankan kalkulator dua kali dengan `cutoffDay` berbeda**. Itu job mesin, bukan job form Pengaturan.

Yang lebih halus: NFR-1 menyatakan perubahan cutoff "menghitung ulang seluruh Tunggakan", tapi **AR-8 tidak mencantumkan perubahan cutoff** sebagai mutasi yang menurunkan alokasi. Dua bacaan yang mungkin, dan keduanya menghasilkan angka berbeda:
- Alokasi adalah fakta → cutoff change **tidak** menurunkan alokasi, hanya menggeser cakupan `chargeDate` → angka berubah ringan.
- Alokasi diturunkan ulang → cutoff change memusnahkan seluruh tabel `ALLOCATION` dan menghitung dari nol → bertentangan dengan AD-3 ("Model hitung-ulang-dari-awal dilarang").

Belum ada yang memutuskan. Epic yang mengimplementasikan mesin tidak pernah menyelesaikan ini, dan Epic 3 baru ketemu saat story ditulis — setelah semua angka FR-13 diverifikasi sebagai benar.

### 3.1 FR-15/16/17 vs AD-6/AD-7 — checklist disagreement

Ditanya langsung: apakah PRD dan spine berbeda pendapat soal settings/backup/PIN? Ya, di dua titik, dan keduanya **tidak tercatat di `epics.md` sebagai risiko**.

**a) AR-17 bilang settings ikut backup, AD-7 bilang PIN tidak ikut ekspor — dan tidak ada yang menjembatani keduanya.** AR-17 (baris 187): *"Cutoff day, auto-lock, identitas usaha, rekening usaha — semua di Room … supaya ikut tercakup backup/restore. PIN dan kode pemulihan **tidak** ikut ekspor (AD-7)."* Tapi `SETTINGS` adalah tabel key-value generik (`string key PK, string value`) — **tidak ada kolom penanda**. Artinya keputusan ekspor-t atau-tidak diambil di serializer berdasarkan daftar nama kunci, dan tidak ada FR, AD, atau AR yang menyebut daftar itu.
 Konsekuensi kalau saringan itu lupa satu baris: **hash PIN dan hash kode pemulihan ikut keluar ke berkas JSON** — persis kebocoran yang NFR-5 larang. Karena hash PBKDF2 butuh ribuan iterasi, kode pemulihan 20 karakter yang bocor bisa di-*brute force* di luar aplikasi dalam hitungan detik. Ini satu-satunya kontrol yang membuat AD-7 benar-benar bekerja, dan ia tidak punya pemilik cerita.

**b) AD-7 mensyaratkan: setelah pemulihan, `pinEnabled` kembali ke `false` dan aplikasi meminta pemilik mengatur PIN baru.** Ini **tidak ada di PRD sama sekali** — FR-17 tidak menyebutnya, NFR-5 tidak menyebutnya, `epics.md` FR-17 juga tidak. UJ-4 tidak membahas interaksi PIN dengan pemulihan. Ini perilaku yang terlihat penuh dengan flow UI sendiri (dialog, alur setup PIN setelah restore) tapi tidak terpetakan ke FR mana pun. Coverage map mengklaim "20/20 terpetakan" — ini hal ke-21 yang lolos.

Akibatnya: FR-16 dan FR-17 bukan dua FR independen di Epic 3. Ada **tiga** perilaku di sana yang tidak tertulis di requirement: (1) saringan ekspor per-kunci di serializer, (2) reset `pinEnabled` setelah restore, (3) flow "atur PIN baru" pasca-restore. Ketiganya punya UI atau serializer, dan ketiganya tidak punya acceptance criteria.


---

## 4. Dependensi lintas epic yang tersembunyi di balik "20/21 terpetakan"

Coverage map terlihat bersih karena hanya memetakan FR→epic. Dependencies sebenarnya:

```
FR-1b (Epic 1) -- pilih Rekening usaha --> FR-18 (Epic 3)   [pecah, tak dipetakan]
FR-12 (Epic 2) -- sebut Nama usaha       --> FR-18 (Epic 3)   [pecah, tak dipetakan]
FR-12 (Epic 2) -- butuh ArrearsSummary --> FR-10 (Epic 1)   [tertera, benar]
FR-13 (Epic 1) -- aksi "Kirim Pengingat" --> FR-12 (Epic 2) [UI rusak di Epic 1]
```

**FR-13 → FR-12 membuat UI Epic 1 rusak saat rampung.** FR-13 mewajibkan **empat** aksi cepat di Beranda, salah satunya "Kirim Pengingat" (PRD baris 333; `epics.md` baris 50). FR-12 ada di Epic 2. Artinya ketika Epic 1 selesai, Beranda punya tombol yang menunjuk layar yang belum ada — kelas bug yang UX-DR16 ("data tidak boleh hilang diam-diam") ada untuk cegat, tapi lebih parah: bukan data hilang, navigasi ke kekosongan. Prototipe sudah benar: `dashboard.html` hanya memasang satu `.fab` ke `payments.html`, bukan empat aksi. Memindahkan FR-13 ke epic yang sama dengan FR-12 menyelesaikan ini tanpa tambahan pekerjaan.

Tambahan: FR-13 menghitung "armada aktif" dari status Kendaraan (FR-5), yang §5 tunjukkan tidak punya aturan rekonsiliasi. Jadi KPI ketiga Beranda tidak punya sumber yang benar.

**FR-1b → FR-18 adalah yang paling berbahaya.** PRD FR-1b: *"Andi memilih Rekening usaha dari daftar yang dikonfigurasi di Pengaturan."* Epic 1 membangun form Setoran dengan dropdown rekening yang **belum punya sumber data** — FR-18 ada di Epic 3. Ada dua jalan buruk dan keduanya salah: (a) hardcode daftar rekening di Epic 1, lalu FR-18 harus memindahkannya — persis jenis churn yang Epic 1 klaim tidak ada; (b) form Setoran dibangun tanpa dropdown, lalu dibuka dua kali.

`PAYMENT.accountId` nullable di ERD memang mengizinkan null, tapi null berarti **Tunai** — dan untuk Tunai field rekening memang disembunyikan. Jadi null-for-Tunai bukan jalan keluar untuk Transfer Bank.

**FR-12 → FR-18** lebih ringan (cuma `Nama usaha` untuk teks pesan), tapi sama-sama salah: Epic 2 mengirim pesan yang menyebut Nama usaha kosong.

---

## 5. FR-5 tidak mengerjakan apa-apa, dan FR-13 bergantung padanya

PRD FR-5 baris 202: *"Status adalah informasi tampilan, bukan sumber kebenaran. Yang menentukan adalah Kontrak."*

Kalau Kontrak yang menentukan, maka:
- **"tersewa"** = punya Kontrak aktif → **derived**, bukan kolom.
- **"tersedia"** = tidak punya Kontrak aktif dan tidak `servis` → **derived**.
- **"servis"** = satu-satunya status yang benar-benar manual.

`addendum.md` §2.1 sudah menyebut persis bug ini sebagai temuan: *"`status` Kendaraan tidak sinkron dengan Kontrak — Menutup Kontrak tidak mengubah `car.status` ke `available`. Angka dashboard bisa salah."* Dan prototipe sendiri sudah mintanya: `cars.html` baris 114 — `Status "Disewa" biasanya follows kontrak`.

**Yang tidak didefinisikan di mana pun: Kendaraan ditandai `servis` tapi punya Kontrak aktif.** Apakah hari tagih tetap jalan? Apakah "tersewa" atau "servis" yang tampil? Ini bukan detail — menentukan apakah Andi ditagih untuk mobil yang di bengkel.

FR-13 menghitung KPI "armada aktif (tersewa/tersedia/servis)" dari kolom yang **setengah derived, setengah manual, tanpa aturan rekonsiliasi**. Aturan warna UX-DR9 ("warna selalu diturunkan dari angka") sudah ada untuk badge tunggakan; tidak ada aturan setara untuk status kendaraan.

---

## 6. Success Metrics — Epic 1 mengklaim SM-2 tapi tidak bisa membuktikannya

PRD §7:

| Metrik | Validasi FR | Epic sekarang | Status |
|---|---|---|---|
| SM-1 nol pencatatan di luar app | FR-1, FR-6, FR-7 | 1 | ✓ bisa dibuktikan |
| **SM-2 tunggakan dipercaya** | **FR-10, FR-11, FR-3** | 1 | **FR-3 pecah** |
| SM-3 "siapa menunggak" < 5 detik | FR-13, FR-11 | 1 | ✓ bisa dibuktikan |
| SM-4 5 setoran < 2 menit | FR-1 | 1 | ✓ bisa dibuktikan |
| SM-5 pemulihan berhasil sekali | FR-16 | 3 | ✓ |
| **SM-C2 jumlah fitur** (counter) | — | — | **tidak ada epic** |

**SM-2 adalah metrik utama** ("Tunggakan dapat dipercaya tanpa koreksi", target nol selisih sejak hari pertama). Validasi FR-nya: FR-10, FR-11, **FR-3**. FR-3 ada di Epic 1 secara formal, tapi **Epic 1 tidak bisa membuktikan SM-2 tanpa FR-9b juga** (Kasus 5) dan tanpa FR-2/FR-3 teruji di dunia nyata. Aturan yang diuji di `:domain:test` adalah **modelnya**; SM-2 yang diukur adalah **keluarannya**. Satu-satunya cara membuktikan SM-2 adalah mengoreksi entri yang salah lalu melihat tunggakan kembali benar — itu FR-3.

**SM-C2 ("jumlah fitur — jangan kejar") tidak punya pemilik sama sekali.** Tidak ada epic yang mengimplementasikan pemeriksaan ini. Struktur 3 epic sekarang mengirim 21 FR ke 3 epic; tidak ada yang bertanggung jawab atas pertanyaan "apakah FR ini benar-benar dibutuhkan?". Itu counter-metric yang paling mudah diabaikan justru paling gampang dilupakan — karena tidak ada FR yang mengharuskannya.

---

## 7. Pertanyaan "kalau harus ship Jumat" — ranking 21 FR

### Load-bearing (tanpa ini pemilik **tidak bisa menjalankan usahanya**)

| # | FR | Alasan |
|---|---|---|
| 1 | FR-10 | Mesin tunggakan. Tanpa ini semua angka lain fiksi. |
| 2 | FR-7 | Tanpa Kontrak tidak ada Hari tagih → tidak ada Tunggakan. |
| 3 | FR-6 | Tidak bisa memilih Penyewa → tidak bisa catat Setoran. |
| 4 | FR-4 | Tidak ada tarif harian → Kontrak tidak punya Kewajiban. |
| 5 | FR-1 | **Job hariannya.** Catat setoran pagi. |
| 6 | FR-11 | Tanpa hari + tingkat risiko, daftar Tunggakan tidak berurutan dan WhatsApp tidak punya angka. |
| 7 | FR-16 | Kerusakan data berarti aplikasi mati. Ini asuransinya. |
| 8 | FR-3 | Salah input pasti terjadi. Tanpa koreksi, tiap ketikan salah permanen. |
| 9 | FR-1b | Metode Pembayaran + rekening mengubah bentuk form. |
| 10 | FR-9b | Tanpa ini hari pertama menampilkan utang fiktif. |

### Penting tapi bukan loop pagi

| # | FR | Alasan |
|---|---|---|
| 11 | FR-8 | Dibutuhkan kalau ada Kontrak 6 hari — itu error Rp 1 juta/bulan (Kasus 2). Kalau semua 7 hari, bisa ditunda. |
| 12 | FR-13 | Bernilai tinggi harian, tapi murni **baca** dari data yang sudah ada. |
| 13 | FR-2 | Jarang dibanding FR-3; FR-3 menutup lebih banyak kasus. |
| 14 | FR-9 | Sekali per rental, bukan per hari. |
| 15 | FR-18 | Hanya dibutuhkan FR-1b (rekening) dan FR-12 (Nama usaha). |
| 16 | FR-12 | **Shortcut, bukan kemampuan baru** — dia bisa buka WhatsApp dan ketik manual hari ini. |

### Convenience

| # | FR | Alasan |
|---|---|---|
| 17 | FR-17 | PIN = penghalang layar, dipakai PRD sendiri. Tidak mencegah kehilangan data. |
| 18 | FR-19 | 18 mobil, 14 penyewa. Bisa di-scroll. |
| 19 | FR-5 | Turunan dari Kontrak; bit manual satu-satunya adalah `servis`. |
| 20 | FR-15 | Default 25 jalan. Pindahkan setelah dipakai sebulan. |
| 21 | FR-14 | Bulanan, dan PRD sendiri menandai grafiknya "kandidat pertama untuk di-drop" (assumption, §4.5). |

### Potongan Jumat

**8 FR:** FR-10, FR-11, FR-4, FR-6, FR-7, FR-1, FR-9b, FR-16 *(separuh ekspor saja)*.

Epic 1 sekarang 14 FR — hampir dua kali lipat. Yang dipotong tanpa pengorbanan nyata: FR-13 (dashboard), FR-19 (search), FR-2 (cancel), FR-3 (edit), FR-5 (status), FR-8 (jadwal), FR-9 (akhiri) — tujuh FR yang masing-masing **satu layar**.

---

## 8. Catatan tambahan

**FR-19 dipersempit tanpa jejak.** PRD FR-19 baris 394 mencari "Penyewa, Kendaraan, **dan Kontrak**". `epics.md` FR-19 baris 62 hanya "nama Penyewa dan plat Kendaraan", hasil "dikelompokkan per jenis" — dua jenis. Tidak ada catatan bahwa Kontrak dikeluarkan. Kontrak memang **tidak punya** field nama/plat sendiri, jadi versi PRD memang tidak dapat dicapai lewat Kontrak. Epics diam-diam memperbaiki PRD. Itu perbaikan yang benar, tapi harus tercatat sebagai koreksi, bukan diam-diam.

**FR-18 vs FR-1b** — lihat §4. Yang perlu ditegaskan: `Rekening tidak bisa dihapus selama masih ada Setoran yang memakainya` (PRD FR-18) adalah aturan yang **hanya bisa diuji kalau FR-1b sudah ada**. Dalam struktur sekarang FR-1b ada di Epic 1 dan FR-18 di Epic 3 — jadi aturan itu tidak punya epic yang testing-nya utuh.

**NFR-1 "50 milidetik" tidak ada acceptance gate yang mengukurnya.** Tujuh test hanya menguji kebenaran, tidak menguji performa. Tidak ada story yang menjalankan benchmark 2.000 Setoran. NFR-7 (Redmi Pad 2, 2.000 Setoran) dipetakan ke Epic 1 tapi tidak punya test yang mengukurnya — dan ini adalah performa yang bisa gagal **tanpa gagal test mana pun**.

**Kalimat rusak di epics.md baris 263:** "Plus satu cara merapikan kontrak lama tanpa/input manual." — harus diperbaiki saat struktur ditulis ulang.

---

## 9. Struktur yang direkomendasikan — **5 epic**

Pindah yang penting: **buang kerangka "layer = epic"**, ganti dengan **"nyawa dulu, lalu tubuh"**. Arsitektur sudah menyediakan persis alatnya (`domain/` = modul Gradle pure-JVM, `./gradlew :domain:test` jalan tanpa Android SDK), sehingga mesin tunggakan bisa jadi **epic pertama yang berdiri sendiri, selesai sebelum ada satu baris Compose pun** — dan diverifikasi dalam hitungan detik. Itu persis leverage yang dibuang oleh klaim "satu loop, file yang sama".

> **Epic 1 — "Angka yang bisa dipercaya" (3 FR: FR-10, FR-11, + aturan domain FR-15).** Murni `domain/`: `ArrearsCalculator`, `AllocationEngine`, `CutoffPeriod`, `Clock`. Tujuh test jadi **definition of done**, bukan "gate" — karena di epic ini memang tidak ada apa pun selain angka yang bisa salah. Checkout hari ini **tidak butuh FR-9b** utuh: aturannya (`settledThroughDate` = hari tagih tertutup, tanpa baris alokasi — AD-3) ikut ke sini, tombolnya menyusul ke Epic 3. Sekalian **memutuskan ambiguitas cutoff** (§3): apakah `cutoffDay` menurunkan ulang alokasi atau hanya menggeser cakupan `chargeDate`, dan menulis keputusannya sebagai test. Layar pratinjau FR-15 ikut ke sini, karena secara teknis dia kalkulator dua kali — bukan form Pengaturan.
>
> **Epic 2 — "Armada dan orang" (6 FR: FR-4, FR-5, FR-6, FR-7, FR-8, FR-9).** Schema + repository + CRUD Kendaraan, Penyewa, Kontrak. FR-5 di sini karena keputusan "derived vs manual" harus diambil sebelum kolom ditulis — dan aturan rekonsiliasi `servis` vs Kontrak aktif (§5) harus eksplisit. Ini **nilai mandiri**: "ini armada saya, ini penyewa saya, ini kontrak saya" — persis yang diketik hari pertama. Dan karena Epic 1 sudah jadi, layar-layar ini bisa menampilkan **angka sungguhan**, bukan placeholder.
>
> **Epic 3 — "Catat dan koreksi setoran" (6 FR: FR-1, FR-1b, FR-2, FR-3, FR-9b, FR-18).** Loop harian. **FR-18 pindah ke sini** — daftar Rekening usaha adalah hard dependency FR-1b (§4), dan Epic ini tidak boleh punya form Setoran yang dropdown-nya belum ada sumber. Ini sekaligus menyelesaikan FR-18 → FR-12: FR-12 (Epic 4) butuh Nama usaha, dan Epic 3 sudah lebih dulu. Tombol FR-9b + dialog konfirmasi ikut di sini karena tanpa itu hari pertama menampilkan utang fiktif (§2.1).
>
> **Epic 4 — "Tunggakan, pengingat, laporan" (4 FR: FR-12, FR-13, FR-14, FR-19).** Layar Tunggakan dengan hari/risiko/warna, susun pesan WhatsApp dari snapshot `ArrearsSummary` tunggal (AR-9), Laporan periode, Beranda 4 KPI, dan pencarian global. Menyatukan FR-13 dan FR-12 menutup satu dependensi (§4): aksi cepat "Kirim Pengingat" di Beranda akan selalu menunjuk layar yang ada. Ini epik yang **membuktikan SM-2 di dunia nyata** — lewat FR-3 yang sudah ada di Epic 3.
>
> **Epic 5 — "Data yang selamat" (3 FR: layar FR-15, FR-16, FR-17).** Backup/pemulihan, kunci PIN + kode pemulihan, dan layar Pengaturan untuk cutoff. Yang **tidak** masuk ke sini: keputusan semantik cutoff (§3) — itu sudah selesai di Epic 1 sebagai test. Ini satu-satunya epik yang tidak menyentuh jalur uang pada waktu ship, jadi harus **tidak** jadi yang pertama: FR-16 mengekspor `VEHICLE`/`RENTER`/`CONTRACT`/`PAYMENT`/`SETTINGS` — **sebelum Epic 2 dan 3 ada, tidak ada yang bisa diekspor**, dan `schemaVersion` (NFR-3) hanya bermakna terhadap skema yang sudah final. Saringan ekspor per-kunci dan reset `pinEnabled` (§3.1) adalah acceptance criteria wajib di sini, bukan detail implementasi.

Total: **5 epic, 21 FR** (3 + 6 + 6 + 4 + 3, dengan FR-15 dipecah antara aturan domain-nya di Epic 1 dan layarnya di Epic 5). Setiap epic punya "ini yang harus bisa dilihat atau terbukti" yang jelas, dan **Epic 1 selesai sebelum aplikasi punya satu pun layar** — artinya ada titik di mana bisa bilang "cukup, angka ini benar", sesuatu yang tidak pernah terjadi pada struktur sekarang.

---

*Ditulis sebagai masukan adversarial sebelum `create-epics-and-stories` membaca `epics.md`. Tidak mengubah `epics.md`.*
