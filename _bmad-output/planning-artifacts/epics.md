---
status: final
stepsCompleted: [step-01, step-02, step-03, step-04]
inputDocuments:
  - '_bmad-output/planning-artifacts/prds/prd-dev.klinik.net-2026-10-13/prd.md'
  - '_bmad-output/planning-artifacts/prds/prd-dev.klinik.net-2026-10-13/verify-fr10.md'
  - '_bmad-output/planning-artifacts/prds/prd-dev.klinik.net-2026-10-13/addendum.md'
  - '_bmad-output/planning-artifacts/architecture/architecture-dev.klinik.net-2026-10-13/architecture-spine.md'
  - '_bmad-output/planning-artifacts/ux-designs/ux-dev.klinik.net-2026-10-13/DESIGN.md'
  - '_bmad-output/planning-artifacts/ux-designs/ux-dev.klinik.net-2026-10-13/EXPERIENCE.md'
  - 'prototype/'
  - '_bmad-output/party-epic-review.md'
  - '_bmad-output/party-owner-review.md'
---

# dev.klinik.net - Epic Breakdown

## Overview

Dokumen ini memecah PRD, verifikasi numerik, arsitektur, dan kontrak UX untuk Klinik.Net — aplikasi Android tablet offline yang menggantikan rekap Excel manual rental mobil harian. Satu pengguna, satu tablet, tanpa backend.

## Requirements Inventory

### Functional Requirements

FR1: Mencatat satu Setoran dalam satu langkah layar — pilih Penyewa, Kendaraan, tanggal, nominal, Metode Pembayaran, catatan opsional. Memilih Penyewa mengisi otomatis Kendaraan dan tarif harian. Setelah Simpan, angka Pendapatan hari ini dan Tunggakan berubah tanpa muat ulang atau berpindah halaman. **Sebelum Simpan wajib ada pratinjau: Tunggakan sebelum → sesudah, hari yang tertutup, dan Kredit bila ada.**

FR2: Membatalkan Setoran yang tercatat, dengan alasan yang wajib diisi. Setoran yang dibatalkan tidak lagi mengurangi Tunggakan tetapi tetap terlihat pada riwayat, dan dapat dipulihkan tanpa mengetik ulang.

FR3: Memperbaiki Setoran yang salah input — tanggal, nominal, Rekening usaha, catatan, Kode transaksi. **Metode Pembayaran tidak dapat diubah** (harus batal lalu catat ulang). Perubahan harus langsung memengaruhi Tunggakan.

FR4: Menambah, mengubah, dan melihat Kendaraan — plat, merek, tipe, tahun, warna, tarif harian, catatan. Plat wajib dan unik (setelah normalisasi huruf besar + spasi dirapatkan). Kendaraan yang sedang disewa tidak bisa dihapus. **Tarif harian harus lebih besar dari nol — nilai nol atau negatif ditolak, bukan diganti nilai bawaan.**

FR5: Menandai Kendaraan sebagai tersewa, tersedia, atau servis, dan melihat jumlah unit per kategori. Status adalah informasi tampilan, bukan sumber kebenaran — yang menentukan adalah Kontrak.

FR6: Menambah, mengubah, dan melihat Penyewa — nama, nomor telepon, nomor rekening, kontak darurat. **Nama wajib, tidak boleh kosong, dan unik dalam aplikasi.** Nomor rekening opsional. Data KTP dan alamat TIDAK dikumpulkan.

FR7: Membuka Kontrak antara satu Penyewa dan satu Kendaraan — tanggal mulai, tarif harian, jadwal kerja (6 atau 7 hari), hari libur. Kontrak baru langsung aktif. **Satu Kendaraan hanya boleh punya satu Kontrak aktif; satu Penyewa juga tidak boleh punya dua Kontrak aktif.** Tanggal mulai boleh lebih awal dari hari ini. Tarif disalin dari Kendaraan saat dibuka dan tidak berubah otomatis; boleh berbeda jika harga dinegosiasi.

FR8: Menentukan jadwal kerja 6 hari (dengan hari libur, default Minggu) atau 7 hari, serta menandai **Pengecualian Servis** — tanggal tertentu yang tidak ditagih, berlaku per Kontrak. Tanggal duplikat ditolak.

FR9: Mengakhiri Kontrak aktif — Kendaraan kembali tersedia, Kontrak berhenti menambah Tunggakan setelah tanggal akhir. Kelebihan Setoran tidak hilang tapi menjadi Kredit. Kontrak yang sudah berakhir tidak bisa diedit, tapi Setoran lamanya tetap dapat dibaca dan dibatalkan.

FR9b: **Menandai Kontrak lunas sampai tanggal tertentu** — menghapus seluruh Tunggakan untuk semua hari tagih sebelum tanggal itu dalam satu operasi, dengan konfirmasi yang menyebut Kontrak, Kendaraan, Penyewa, jumlah hari yang dihapus, dan nominalnya. Tidak ada representasi per hari, tidak ada Setoran yang dibuat, tidak ada jejak audit.

FR10: **Menghitung Tunggakan dengan alokasi kronologis.** Pool Setoran dijumlahkan menjadi satu angka, lalu dialokasikan ke Hari tagih dari terlama ke terbaru; sisa yang tidak tertagih adalah Tunggakan (min nol). **Kelebihan Pool di atas Kewajiban menjadi Kredit yang mengurangi Kewajiban periode berikutnya** — lewat baris alokasi dengan `chargeDate = null`. Pembayaran pada hari yang tidak ditagih (hari libur, Pengecualian Servis, sebelum kontrak dimulai) tetap masuk pool. Setoran bertanggalreddit tidak dihitung sebelum jatuh tempo. **Kontrak yang sudah berakhir tetap dihitung Tunggakannya sampai lunas, dan masuk Tunggakan aktif.**

FR11: Menyatakan jumlah hari Menunggak dan tingkat risiko — 0 = Lancar, 1–2 = Ringan, 3–6 = Perhatian, 7+ = Kritis. Daftar diurutkan nominal terbesar, lalu hari tertua. **Angka ini dikirim ke penyewa lewat WhatsApp, jadi harus benar.**

FR12: Menyusun pesan pengingat WhatsApp yang sudah terisi dari data Tunggakan — nama Penyewa, plat Kendaraan, Nama usaha, sisa Tunggakan, jumlah hari menunggak. Pesan bisa disunting sebelum dikirim, dan tautan dibangun ulang dari teks hasil suntingan. Tersedia tombol Salin. **Pengiriman tidak mengubah status Tunggakan dan tidak tercatat sebagai riwayat.**

FR13: Beranda menampilkan dalam sekali lihat — Pendapatan hari ini, Tunggakan aktif, armada aktif (tersewa/tersedia/servis), tingkat penagihan. Empat KPI **sama besar, tanpa hierarki.** Semua dihitung ulang setiap kali ada perubahan data; tidak ada tombol muat ulang. Empat aksi cepat: Catat Setoran, Tambah Mobil, Kelola Penyewa, Kirim Pengingat.

FR14: Laporan per Periode cutoff — total Setoran masuk, total Tunggakan akhir periode, jumlah Penyewa lunas. Periode di masa depan tidak dihitung. Menampilkan komposisi Tunggakan menurut tingkat risiko dan tren Setoran tujuh hari.

FR15: Mengubah Tanggal cutoff (1–28), dengan pratinjau wajib: berapa Kontrak dan berapa rupiah Tunggakan yang berubah, sebelum diterapkan.

FR16: Mengekspor seluruh data ke satu berkas JSON (nama berkas memuat tanggal ekspor) dan memulihkannya. **Pemulihan menampilkan pratinjau jumlah entitas sebelum menimpa.** Ekspor tidak menghapus data saat ini. **Hash PIN dan kode pemulihan tidak ikut ekspor.**

FR17: Kunci layar dengan PIN. Durasi kunci otomatis dapat diatur 1/5/15/30 menit (default 5) dan **harus benar-benar berjalan** — aplikasi terkunci lagi setelah berada di latar belakang selama durasi itu. **Saat PIN dibuat, aplikasi membuat satu kode pemulihan berentropi tinggi (alphanumeric, minimal 20 karakter), ditampilkan sekali, dan meminta konfirmasi bahwa sudah dicatat.** Kode itulah yang dipakai untuk mengatur PIN baru saat PIN terlupa. **Kunci layar adalah penghalang, bukan keamanan sungguhan** — tidak ada klaim enkripsi.

FR18: Mengatur Nama usaha, Alamat usaha, Telepon usaha, dan daftar Rekening usaha yang menerima Setoran. Mendukung lebih dari satu rekening. Rekening tidak bisa dihapus selama masih ada Setoran yang memakainya. Perubahan data usaha tidak mengubah Setoran lama.

FR19: Pencarian global dari satu kolom yang tersedia di setiap layar — mencocokkan nama Penyewa dan plat Kendaraan, sebagian teks, case-insensitive, hasil dikelompokkan per jenis. **Tidak menelusuri catatan, Kode transaksi, atau nomor Setoran.**

### NonFunctional Requirements

NFR1: **Perhitungan Tunggakan selalu benar.** Setiap perubahan pada Setoran, Kontrak, atau Tanggal cutoff menghitung ulang seluruh Tunggakan secara langsung, sebelum layar selesai digambar. Menyimpan angka tunggakan di storage dan menghitungnya hanya saat pembacaan menghasilkan selisih yang tidak terlihat. Pada skala di bawah 20 Kendaraan dan beberapa ratus Setoran, hitungan ulang penuh selesai di bawah 50 milidetik — dan kalau ini dipecahkan karena alasan teknis, jangan mengorbankan kebenaran demi kecepatan.

NFR2: **Durabilitas data.** Penulisan bersifat atomik; aplikasi tidak boleh meninggalkan data setengah tertulis. Kerusakan data yang tersimpan harus menggagalkan aplikasi dengan pesan jelas — **bukan me-reset ke default dan terlihat seperti akun baru.** Tidak ada operasi yang menghapus data tanpa persetujuan eksplisit.

NFR3: **Skema berkas.** Berkas ekspor memuat nomor versi skema. Pemulihan dari berkas versi berbeda ditolak dengan pesan yang bisa dipahami, bukan kegagalan parsing yang menghapus data yang ada.

NFR4: **Perilaku pertama kali dijalankan.** Aplikasi bisa dijalankan dan diisi dari nol. Tidak ada akun, wizard, atau data contoh yang ikut tersimpan.

NFR5: **Kode PIN.** Kode PIN tidak disimpan dalam bentuk yang bisa dibaca, dan tidak muncul di log, backup ekspor, atau pesan galat. Tidak ada klaim keamanan yang lebih besar dari kenyataan.

NFR6: **Ketahanan input.** Nilai yang bisa merusak data ditolak di lapisan data, bukan di antarmuka saja — angka bukan angka, nominal nol atau negatif, tarif harian nol atau negatif. **Tidak ada nilai default diam-diam yang mengubah makna input.**

NFR7: **Performa dan ukuran.** Tetap responsif pada Redmi Pad 2 dengan 20 Kendaraan dan 2.000 Setoran.

NFR8: **Aksesibilitas.** Kontras teks utama ≥ 4.5:1 (terang) dan ≥ 7:1 (gelap). Target sentuh minimal 48dp pada semua tombol, chip, dan baris tabel. Ukuran teks minimal 14sp untuk label dan data. Angka rupiah memakai `tabular-nums`. Focus ring 3px berwarna brand pada setiap elemen interaktif.

NFR9: **Orientasi dan tema.** Tablet Redmi Pad 2 dipakai dalam dua orientasi, keduanya bisa dipakai penuh — navigasi rel + master-detail untuk landscape, navigasi bawah untuk portrait. Mendukung tema terang dan gelap.

### Additional Requirements

**Dari Architecture — wajib, bukan opsional:**

AR-1: **Core library desugaring WAJIB aktif.** `java.time` baru tersedia sejak API 26; dengan `minSdk 24`, tanpa `isCoreLibraryDesugaringEnabled = true` dan `desugar_jdk_libs`, `LocalDate.now()` melempar `NoClassDefFoundError` — aplikasi crash saat startup.

AR-2: **Plugin `org.jetbrains.kotlin.android` TIDAK boleh dipasang** pada AGP 9 — hard build failure (`Cannot add extension with name 'kotlin'`). Kotlin bawaan AGP 9. Semua tutorial lama akan salah di sini.

AR-3: **`composeOptions.kotlinCompilerExtensionVersion` sudah dihapus.** Wajib plugin `org.jetbrains.kotlin.plugin.compose`, versinya harus sama persis dengan versi Kotlin.

AR-4: **`kotlin { jvmToolchain(17) }`** — Gradle boleh jalan di JVM 25 (lokal), tapi compile harus ke 17.

AR-5: **Stack terverifikasi:** AGP 9.4.1 · Gradle 9.8.1 · **Kotlin 2.3.21 (bukan 2.4.x — KSP 2.3.12 hanya mendukung KGP 2.2.10–2.3.x)** · Compose BOM 2026.09.00 · Room 2.8.5 · KSP 2.3.12 · Lifecycle 2.11.0 · Navigation Compose 2.10.2 · compileSdk 37 · targetSdk 36 · minSdk 24.

AR-6: **`domain/` adalah modul Gradle pure-JVM terpisah**, bukan package di dalam `app`. Ini menegakkan AD-1 di level build — impor Android jadi build failure — dan membuat `./gradlew :domain:test` jalan **tanpa Android SDK sama sekali.**

AR-7: **Alokasi setiap Setoran disimpan** di tabel `payment_allocation` yang berisi fakta "setoran ini menutup 23 Feb". Kelebihan yang belum ditempatkan punya `chargeDate = null`. **Aturan tunggal tanpa pengecualian: `Σ alokasi per Setoran == amount Setoran itu`.**

AR-8: **Setiap mutasi menurunkan seluruh alokasi.** Pembatalan (FR2) DAN perubahan nominal (FR3) keduanya menghapus semua baris alokasi milik Setoran itu lalu menghitung ulang seluruh Kontrak, dalam satu `@Transaction`. FR-9b tidak menulis baris alokasi — ia mengubah `settledThroughDate` pada Kontrak.

AR-9: **Angka yang dikirim ke penyewa lewat WhatsApp harus dari satu pembacaan snapshot** yang mengembalikan objek `ArrearsSummary` lengkap. Tidak boleh digabung dari tiga query terpisah — kalau cancel berjalan di antaranya, nomor yang terkirim berasal dari dua keadaan berbeda, tanpa ada undo atau jejak audit.

AR-10: **Tanpa sinyal invalidasi manual.** Room sudah punya `InvalidationTracker` yang otomatis mengemisi ulang `Flow`. Manual `invalidate()` di sampingnya menghasilkan dua mekanisme refresh yang bisa berbeda — persis bug angka tidak sinkron yang harus dicegah. Pakai `combine` + `stateIn` + `distinctUntilChanged()`.

AR-11: **Nominal uang adalah `Long` rupiah penuh, tidak pernah `Double`/`Float`** — di domain maupun database. Format ke layar hanya lewat satu fungsi `formatRupiah()`. Parse input juga satu fungsi di layer terendah, **tanpa nilai default diam-diam** (ini persis mode gagal `parseInt(x) || 250000` yang ditemukan di prototipe).

AR-12: **Tanpa sinyal invalidasi manual — Room `InvalidationTracker` sudah otomatis.** Tabel wajib punya indeks unik nyata: `VEHICLE.plate UNIQUE`, `RENTER.name UNIQUE`, `ALLOCATION UNIQUE(paymentId, chargeDate)`, dan partial unique index untuk Kontrak aktif. **Ini koreksi kebenaran, bukan performa** — uniqueness hanya di UI bisa dilewati dua layar.

AR-13: **Ekspor/pemulihan lewat Storage Access Framework** (`CreateDocument` / `OpenDocument`). **Tidak ada `MANAGE_EXTERNAL_STORAGE` atau `READ_EXTERNAL_STORAGE` di manifest.

AR-14: **PIN di-hash dengan PBKDF2WithHmacSHA1 + salt acak**, bukan bcrypt/Argon2 — keduanya tidak ada di Android SDK tanpa native library, dan untuk PIN 4-digit justru memperlambat unlock tanpa menambah keamanan nyata terhadap penyerang offline. Kontrol yang benar untuk PIN berentropi rendah adalah **rate limiting: 5 percobaan gagal beruntun** lalu hitung mundur.

AR-15: **Tidak ada dependency chart.** Tren 7 hari dan donut digambar dengan `Canvas` + `drawArc`/`drawLine` (~60 baris), nol dependency risk, warna otomatis mengikuti token Material 3.

AR-16: **Ikon dari Lucide** via `com.composables:icons-lucide:1.0.0` (1.695 ikon, lisensi ISC, format ImageVector). **Tidak ada Material Icons** — owners memilih Lucide karena Material Icons dianggap terlalu kaku.

AR-17: **Settings (cutoff, auto-lock, identitas usaha, rekening) di Room, bukan DataStore** — supaya ikut tercakup backup/restore. `settings.value` bertipe `String` dan **tidak boleh menyimpan nilai uang.**

AR-18: **Satu Activity, satu NavHost.** `WindowSizeClass` menentukan rail + master-detail atau bottom-nav + single-column. Graph identik di kedua orientasi; tidak ada branch logika bisnis berdasarkan orientasi.

### UX Design Requirements

**Token dan sistem visual (dari DESIGN.md — mendokumentasikan `shared.css` yang sudah final):**

UX-DR1: Implementasikan brand coral/rose `#FF385C` dengan scale lengkap (brand-50 `#FFF1F4` → brand-700 `#C01336`), plus netral hangat (`--warm-0` `#FFFFFF` → `--warm-900` `#1A1715`). Dark theme memakai hex langsung untuk surface (`#14100F`/`#1F1A19`/`#2A2321`), bukan scale warm.

UX-DR2: Empat warna semantik dengan background pendamping — success `#0E9F6E`, warning `#D97706`, danger `#DC2626`, info `#2563EB`. **Foreground semantik tidak berubah di dark theme, hanya background-nya** — konsistensi semantik lebih penting dari palet yang konsisten.

UX-DR3: Tipografi dua wajah — Fraunces (display, untuk judul dan angka besar) dan Plus Jakarta Sans (body). Skala `--fs-xs` 12 → `--fs-3xl` 48, dengan **minimum 14sp untuk label dan data** (batas, bukan pilihan estetika).

UX-DR4: Skala spacing basis 4dp (`--sp-1` 4 → `--sp-16` 64), radius (`--r-sm` 10 / `--r-md` 14 / **`--r-lg` 20 untuk kartu** / `--r-xl` 26 / `--r-full` pill), dan elevation berupa **bayangan hangat lembut** tiga tingkat plus `--el-brand` yang khusus untuk tombol primary.

UX-DR5: **Angka rupiah memakai `tabular-nums`** agar kolom angka mudah dibandingkan satu sama lain. Ini bukan hiasan — membandingkan nominal tunggakan secara visual adalah inti dari apa yang dilakukan pemilik setiap pagi.

UX-DR6: Motion dengan easing tunggal `cubic-bezier(.2,.8,.2,1)`, durasi 140/220/300ms. **Aturan tidak bisa dilanggar: tidak ada motion yang berulang, tidak ada yang lebih dari 300ms.** Stagger saat layar dibuka (sekali), shake 420ms untuk PIN salah (sekali), fade-up. Tidak ada bounce atau overshoot.

UX-DR7: Komponen inti yang harus ada: Button (5 varian × 4 ukuran), Card, KPI Tile, Badge, Chip, Field/Input dengan `.field-error` **hidden by default**, Search bar, **Numpad on-screen untuk input nominal**, Table/List row, Avatar, Modal, Snackbar, Empty state, PIN grid, Nav rail, Bottom nav, Top bar.

UX-DR8: **Numpad wajib untuk nominal, bukan keyboard sistem** — dipakai sambil berdiri/memegang sesuatu, jadi target sentuh besar lebih penting dari kecepatan ketik. Tombol besar layout 3×4, display angka besar di atas, batas 12 digit, tombol simpan menolak nilai ≤ 0 dengan flash merah. Efek getar saat tombol ditekan.

**Perilaku dan alur (dari EXPERIENCE.md):**

UX-DR9: **Kode tunggakan memakai dua encoding: angka dan warna.** Angka adalah signal primer — akurat, tidak bergantung pada persepsi warna; warna adalah scanner cepat. **Warna selalu diturunkan dari angka, tidak pernah dipilih manual** — dua sumber kebenaran yang bisa tidak sinkron adalah cara pasti membuat pengguna berhenti percaya.

UX-DR10: **Dialog pratinjau alokasi sebelum Simpan — ringkas, tanpa tanggal per-hari.** Format: `Tunggakan: Rp 750.000 → Rp 250.000` / `Menutup: 2 hari · Sisa: 1 hari (Rp 250.000)`. Kalau ada kredit: `Tunggakan: Rp 750.000 → Rp 0` / `Menutup: 3 hari` / `Kredit: Rp 250.000 — dipakai otomatis untuk periode berikutnya`. **Pratinjau wajib, bukan opsional** — termasuk kalau owners sudah tahu hasilnya.

UX-DR11: **Tiga level konfirmasi.** Aksi reversible tanpa efek samping (ganti tema, edit catatan) **tidak boleh** minta konfirmasi. Aksi yang mengubah angka → dialog yang menyebut dampaknya. Aksi yang menghapus data → dialog **+ alasan wajib**. Contoh: `Batalkan setoran Rp 250.000? Tunggakan Pak Budi akan kembali menjadi Rp 750.000. Alasan pembatalan (wajib):`

UX-DR12: **Empat state setiap layar, tidak ada yang boleh hilang tanpa penjelasan.** Loading → skeleton dengan bentuk yang sama dengan isi akhirnya. Kosong → menyebut **apa yang harus dilakukan**, bukan hanya "belum ada data". Error → menjelaskan apa yang salah dan apa yang harus dilakukan; **jangan pernah menampilkan data kosong yang terlihat seperti akun baru.** Sukses → snackbar yang **tidak mengubah state layar**, jadi pengguna bisa langsung lanjut.

UX-DR13: **Master-detail di landscape** untuk Kendaraan, Penyewa, dan Tunggakan — daftar di kiri, detail di kanan, keduanya terlihat bersamaan. Owners sering membandingkan beberapa penyewa sekaligus; kalau detail hanya muncul setelah tap, membandingkan berarti bolak-balik. Di portrait, master-detail runtuh menjadi daftar → detail.

UX-DR14: **Micropcopy Bahasa Indonesia, langsung dan jujur.** Nominal selalu penuh (`Rp 250.000`, bukan `250rb`) di layar yang jadi keputusan. Tanggal format `23 Sen`, `24 Sel`. Tunggakan selalu dua kalimat: nominal **dan** hari. Error menyatakan apa yang salah **dan** bagaimana memperbaikinya (`Nominal harus lebih besar dari nol.`). Tidak ada `res/values/strings.xml` yang berisi teks hardcode.

UX-DR15: **Tidak ada gestur sebagai satu-satunya jalan ke aksi penting** — swipe-to-delete **tidak** dipakai untuk Setoran karena punya konsekuensi uang dan butuh alasan.

UX-DR16: **Prinsip yang tidak bisa dilanggar** (dari EXPERIENCE.md §9): (a) angka harus bisa dipercaya; (b) pratinjau sebelum menyimpan setiap aksi yang mengubah angka; (c) data tidak boleh hilang diam-diam.

**Dari prototype/ — referensi implementasi visual, sudah final:**

UX-DR17: **FAB dan speed dial sebagai cara masuk aksi cepat.** `.fab` mengambang di kanan-bawah (min 60px, pill, brand-500, bayangan brand), `.fab-mini` 54px untuk aksi tunggal. Speed dial membuka 3 aksi berlabel (52px tombol + label pill), toggle berputar 45° saat terbuka, aksi muncul stagger dari bawah.

  **Pengecualian Beranda** — grid 4 aksi cepat (FR-13) adalah satu-satunya pintu ke aksi cepat dari Beranda. **Speed dial TIDAK muncul di Beranda.** Alasannya: ada dua jalan ke aksi yang sama hanya menciptakan kemungkinan keduanya tidak sinkron — dan kalau speed dial punya aksi yang tidak ada di grid (atau sebaliknya), pemilik menemukan aksi yang hilang tanpa tahu ke mana harus pergi. Di semua layar selain Beranda, speed dial adalah satu-satunya akses aksi cepat, dan isinya harus sama dengan isi grid Beranda.

UX-DR18: **Bottom sheet, bukan dialog modal, untuk form input.** `.bottom-sheet` menempel di bawah, max 92% tinggi, radius atas 26px (`--r-xl`), grip 42×4.5px di atas, slide-up saat tampil. **Dialog Catat Setoran adalah bottom sheet** — numpad butuh ruang vertikal, dan di tablet keyboard virtual memenuhi sebagian besar layar. Dialog modal akan menutupi numpad itu sendiri.

  Aturan berlaku luas: **form yang memuat input banyak atau keyboard tinggi → bottom sheet.** Form pendek (mis. hanya nama dan telepon) boleh dialog, tapi konsistensi secara keseluruhan condong ke bottom sheet.

UX-DR19: **Logo sebagai aset, bukan SVG inline.** `assets/logo.svg`, `logo-mono.svg`, `logo-mark.svg` sudah tersedia (rounded square 15px, gradient coral `#FF385C` → `#C01336`, ikon mobil stroke putih). Wordmark di-bake oleh `tools/emit_wordmark.py` + `bake_wordmark.py` → `tools/wordmark-800.json`. Pindahkan sebagai drawable, bukan tulis ulang path-nya.

---

## Temuan dari Party Mode — yang mengubah struktur ini

Party mode (2 reviewer adversarial) membongkar struktur 3 epic awal dan menemukan enam hal. Semuanya sudah diperbaiki di bawah:

1. **Acceptance gate mustahil hijau** — test #5 dari `verify-fr10.md` menguji FR-9b yang ada di Epic 2, jadi Epic 1 tidak akan pernah bisa dinyatakan selesai. *Diperbaiki: aturan domain FR-9b (`settledThroughDate` = hari tagih tertutup tanpa baris alokasi) masuk Epic 1; tombolnya ke Epic 3.*
2. **Tombol mati** — FR-13 punya aksi cepat "Kirim Pengingat" (FR-12) yang ada di Epic 2. Saat Epic 1 selesai, Beranda punya tombol yang membuka layar yang belum ada. *Diperbaiki: keduanya satu epic.*
3. **Dropdown tanpa sumber** — FR-1b butuh daftar Rekening usaha dari FR-18, yang ada di epic lain. Dua jalan buruk: hardcode lalu pindah, atau form dibuka dua kali. *Diperbaiki: FR-18a pindah ke epic yang sama dengan FR-1b.*
4. **FR-9b adalah onboarding, bukan migrasi** — hari pertama tanpa itu = Rp 84 juta utang fiktif di 14 kontrak backdated, dan tidak ada jalan keluar lain karena PRD §6.1 tegas tidak ada impor. *Diterima: FR-9b dipindah ke Epic 3.*
5. **Klaim "file churn" salah** — 8 dari 14 FR epic awal adalah master data yang tidak menyentuh aritmetika tunggakan. Empat dari enam entitas Room tidak pernah dibaca `ArrearsCalculator`. *Diterima.*
6. **PRD baris 282 & 286 bertentangan** — "pool dibatasi periode" vs "alokasi tersimpan, jangan hitung ulang", bersebelahan. Yang 282 menghasilkan Rp 7 juta utang fiktif. *Sudah diperbaiki di `prd.md`.*

---

## FR Coverage Map

**Functional Requirements (21/21 terpetakan)**

| FR | Epic | Deliverable |
|---|---|---|
| FR10 | **1** | **Mesin tunggakan — alokasi kronologis, Kredit lintas periode** |
| FR11 | 1, 4 | Hari menunggak + tingkat risiko (rumus di 1, layar di 4) |
| FR15a | 1 | Aturan domain cutoff — re-derive atau geser cakupan (keputusan, bukan layar) |
| FR4 | 2 | CRUD Kendaraan, plat unik, tarif > 0 |
| FR5 | 2 | Status & ketersediaan (keputusan derived-vs-manual diambil sebelum kolom ditulis) |
| FR6 | 2 | CRUD Penyewa, nama wajib + unik |
| FR7 | 2 | Buka Kontrak; satu Kontrak aktif per Kendaraan **dan** per Penyewa |
| FR8 | 2 | Jadwal 6/7 hari + Pengecualian Servis |
| FR9 | 2 | Akhiri Kontrak; kelebihan jadi Kredit |
| FR1 | 3 | Catat setoran satu langkah + pratinjau sebelum simpan |
| FR1b | 3 | Dua Metode Pembayaran; field Tunai disembunyikan |
| FR2 | 3 | Batalkan setoran dengan alasan wajib + pemulihan |
| FR3 | 3 | Koreksi setoran; Metode Pembayaran tidak bisa diubah |
| FR9b | 3 | Tandai lunas sampai tanggal — **jalur keluar hari pertama** |
| FR18a | 3 | Rekening usaha — sumber data dropdown FR-1b |
| FR12 | 4 | Pesan WhatsApp dari snapshot tunggal |
| FR13 | 4 | Beranda: 4 KPI sama besar, 4 aksi cepat |
| FR14 | 4 | Laporan periode cutoff |
| FR18b | 4 | Nama/Alamat/Telepon usaha — dipakai pesan WhatsApp |
| FR19 | 4 | Pencarian global |
| FR15b | 5 | Layar ubah cutoff + pratinjau wajib |
| FR16 | 5 | Ekspor/pemulihan JSON; PIN tidak ikut ekspor |
| FR17 | 5 | PIN + kode pemulihan + kunci otomatis |

> **FR18 dan FR15 dipecah.** Keduanya punya dua bagian yang jelas berbeda tempat: aturan datanya (dipakai epic yang lebih awal, sebagai hard dependency) dan layarnya. FR18a masuk Epic 3 karena FR-1b tidak boleh punya dropdown tanpa sumber; FR18b ke Epic 4 karena FR-12 butuh Nama usaha. FR15a adalah keputusan semantik yang hanya mesin yang bisa jawab — kalau ditunda ke Epic 5, ambiguitas baru ketahuan setelah semua angka FR-13 dipanggil benar.

**NonFunctional (9/9 terpetakan)**

| NFR | Epic | Kenapa di situ |
|---|---|---|
| NFR1 | 1 | Inti mesin — jadi definition of done, bukan gate |
| NFR6 | 1, 2, 3 | Validasi di lapisan data |
| NFR7 | 1, 4 | Performa |
| NFR8 | 2 | Fondasi aksesibilitas — melekat sejak komponen pertama |
| NFR9 | 2 | Satu Activity, dua orientasi |
| NFR2 | 3, 5 | Transaksi atomik di 3; ketahanan berkas di 5 |
| NFR4 | 2 | First-run tanpa data contoh |
| NFR3 | 5 | Skema berkas ekspor |
| NFR5 | 5 | Hash PIN |

**Architecture (18/18 terpetakan)**

| AR | Epic | Catatan |
|---|---|---|
| AR-1 s/d AR-5 | 1 | Stack + desugaring wajib — Story 1 |
| AR-6 | 1 | `domain/` modul pure-JVM — fondasi epic ini |
| AR-7 | 1 | Alokasi disimpan + `chargeDate = null` |
| AR-8 | 1 | Setiap mutasi re-derive alokasi |
| AR-10 | 1 | Tanpa invalidasi manual |
| AR-11 | 1 | Uang = `Long`, tanpa default diam-diam |
| AR-15 | 4 | Grafik via Canvas, nol dependency |
| AR-12 | 2 | Indeks unik = koreksi kebenaran |
| AR-16 | 2 | Ikon Lucide |
| AR-18 | 2 | Satu Activity, satu NavHost |
| AR-9 | 3, 4 | Snapshot tunggal (kontrak di 3, implementasi di 4) |
| AR-13 | 5 | SAF, tanpa storage permission |
| AR-14 | 5 | PBKDF2 + rate limiting |
| AR-17 | 3, 5 | Settings di Room — rekening di 3, sisanya di 5 |

**UX Design (19/19 terpetakan)**

| UX-DR | Epic | Catatan |
|---|---|---|
| UX-DR6 | 1 | Aturan motion melekat di design system |
| UX-DR1 s/d DR5 | 2 | Token: warna, tipografi, spacing, radius, elevation, tabular-nums |
| UX-DR7, DR8 | 2 | Komponen inti + Numpad wajib |
| UX-DR16 | 2 | Prinsip yang tidak bisa dilanggar |
| UX-DR17, DR18, DR19 | 2 | FAB + speed dial, bottom sheet, logo |
| UX-DR9 | 4 | Warna diturunkan dari angka |
| UX-DR10 | 3, 4 | Pratinjau alokasi (format di 3, layar arrears di 4) |
| UX-DR11 | 3 | Tiga level konfirmasi |
| UX-DR12 | 3 | Empat state |
| UX-DR13 | 4 | Master-detail |
| UX-DR14 | 2, 3 | Micropcopy Bahasa Indonesia |
| UX-DR15 | 3 | Tanpa gestur kritis |

---

## Epic List

### Epic 1: Angka yang bisa dipercaya

Andi punya mesin hitung tunggakan yang **terbukti benar lewat test**, sebelum aplikasi punya satu pun layar.

Murni modul `domain/` pure-JVM. Tujuh test dari `verify-fr10.md` adalah **definition of done** — bukan "gate" yang menggantung, karena di epic ini tidak ada apa pun yang bisa gagal selain angkanya. `./gradlew :domain:test` jalan tanpa Android SDK, dalam hitungan detik.

Termasuk keputusan semantik yang belum ada: **ubah `cutoffDay` itu menurunkan ulang alokasi, atau cuma menggeser cakupan?** PRD baris 282 dan 286 bertentangan, dan arsitektur memilih satu sisi. Epic ini menutupnya sebagai test, sebelum jadi test baru di kemudian hari.

**FRs covered:** FR10, FR11, FR15a

### Epic 2: Armada dan orang

Andi bisa menyimpan dan melihat seluruh armada, penyewa, dan kontraknya. Ini data yang dia ketik pertama kali — dan nilainya berdiri sendiri: "ini armada saya, ini orang saya, ini kontrak saya."

Berlaku **sementara mesinnya belum ada**: layar-layar ini menampilkan data apa adanya. Begitu Epic 1 masuk, layar yang sama langsung menampilkan angka sungguhan tanpa satu baris perubahan.

Berisi juga design system dan seluruh komponen inti — karena master data adalah tempat pertama komponen-komponen itu dipakai.

**FRs covered:** FR4, FR5, FR6, FR7, FR8, FR9

### Epic 3: Catat dan koreksi setoran

Andi bisa mencatat setoran pagi itu dalam satu langkah, melihat pratinjau sebelum menyimpan, mengoreksi yang salah input, dan keluar dari kontrak lama tanpa mengetik riwayat.

Serta **satu jalan keluar untuk hari pertama**: FR-9b dan daftar Rekening usaha ikut di sini, bukan di epic berikutnya. Tanpa keduanya, aplikasi pertama kali dibuka akan menampilkan seluruh kontrak lama sebagai "28 hari Kritis" dengan utang fiktif, dan form Setoran punya dropdown yang belum punya isi.

**FRs covered:** FR1, FR1b, FR2, FR3, FR9b, FR18a

### Epic 4: Tunggakan, pengingat, laporan

Andi bisa melihat daftar tunggakan dengan hari dan warna yang langsung dibaca, mengirim pengingat WhatsApp dengan angka benar, membaca laporan bulanan, dan melihat ringkasan pagi di Beranda.

Ini epic yang **membuktikan SM-2 di dunia nyata** — bukan lewat model, tapi lewat koreksi entri yang salah lalu melihat tunggakan kembali benar (FR-3 yang sudah ada di Epic 3).

**FRs covered:** FR11, FR12, FR13, FR14, FR18b, FR19

### Epic 5: Data yang selamat

Andi bisa mencadangkan dan memulihkan data, mengunci aplikasi dengan PIN yang punya jalan keluar kalau lupa, dan mengubah tanggal cutoff dengan melihat dampaknya dulu.

Deliberately last: FR-16 mengekspor skema yang Epic 2 dan 3 ciptakan. **Sebelum itu ada, tidak ada yang bisa diekspor** — jadi lebih benar tersisa di akhir daripada jadi prioritas yang tidak bisa jalan.

Tapi: **sampai Epic 5 selesai, aplikasi adalah satu-satunya salinan catatan uang Andi tanpa jalan keluar.** Itu diketahui dan diterima — tapi kalau hanya satu epic yang bisa dijadwalkan minggu ini, ini yang harus lebih dulu.

**FRs covered:** FR15b, FR16, FR17

---

## Epic 1: Angka yang bisa dipercaya

Andi punya mesin hitung tunggakan yang **terbukti benar lewat test**, sebelum aplikasi punya satu pun layar.

Murni modul `domain/` pure-JVM. Tujuh test dari `verify-fr10.md` adalah **definition of done** — bukan "gate" yang menggantung, karena di epic ini tidak ada apa pun yang bisa gagal selain angkanya. `./gradlew :domain:test` jalan tanpa Android SDK, dalam hitungan detik.

**FRs:** FR10, FR11, FR15a · **NFR:** NFR1, NFR6, NFR7 · **AR:** AR-1…AR-8, AR-10, AR-11 · **Lubang ditutup di sini:** cutoff digeser (Story 1.5), benchmark performa (Story 1.6)

---

### Story 1.1: Proyek yang benar-benar bisa jalan dan diuji tanpa SDK

Sebagai developers, saya ingin dua modul Gradle dengan jebakan build yang sudah diketahui sudah ditutup, sehingga setiap story berikutnya bisa diuji dalam hitungan detik tanpa emulator.

**Acceptance Criteria:**

**Given** mesin ini belum ada sama sekali
**When** saya menjalankan `./gradlew :domain:test` di mesin yang **tidak punya Android SDK terpasang** (setelah `local.properties` dihapus)
**Then** build berhasil dan test berjalan
**And** `./gradlew :app:assembleDebug` juga berhasil
**And** aplikasi yang terpasang di perangkat **tidak crash saat start** — `LocalDate.now()` tidak melempar `NoClassDefFoundError`

**Given** build.gradle sudah ditulis
**When** `org.jetbrains.kotlin.android` sengaja dipasang di modul `app`
**Then** build gagal dengan pesan yang jelas
**And** tidak ada dokumentasi atau template yang menyuruh orang memasangnya

**And jebakan berikut sudah tertutup di konfigurasi:**
- `compileOptions.isCoreLibraryDesugaringEnabled = true` + `coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")` — tanpa ini aplikasi crash saat start (AR-1)
- `plugins { id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" }` — bukan `composeOptions.kotlinCompilerExtensionVersion` (AR-3)
- `kotlin { jvmToolchain(17) }` — JDK lokal 25 boleh jalan, compile ke 17 (AR-4)
- Versi persis: AGP 9.4.1, Gradle 9.8.1, Kotlin 2.3.21, KSP 2.3.12, Compose BOM 2026.09.00, Room 2.8.5 (AR-5)
- **Kotlin 2.3.21, bukan 2.4.x** — KSP 2.3.12 hanya mendukung KGP 2.2.10–2.3.x

**And** modul `domain/` adalah Gradle module `kotlin("jvm")` **tanpa `android {}` block sama sekali** (AR-6)
**And** mengimpor `android.*` atau `androidx.*` di dalam `domain/` menghasilkan **build failure**, bukan teguran di review (AD-1 ditegakkan Gradle)
**And** `minSdk 24` · `targetSdk 36` · `compileSdk 37`

**Notes:** ini sengaja **bukan** "setup database". Tidak ada tabel, tidak ada entity, tidak ada DAO di story ini — hanya dua modul yang bisa dibangun dan diuji. Jebakan di atas semuanya pernah membunuh build di sesi sebelumnya; menambahkannya sebagai AC membuat story berikutnya tidak mengulanginya.

---

### Story 1.2: Periode cutoff, hari kerja, dan pengecualian

Sebagai developers, saya ingin menghitung rentang periode dan hari-hari yang ditagih, sehingga semua hitungan berikutnya punya satu sumber tanggal yang benar.

**Acceptance Criteria:**

**Given** hari ini 2026-03-24 dan Tanggal cutoff 25
**When** Periode cutoff dihitung
**Then** periodenya 2026-02-25 → 2026-03-24
**And** label periode menampilkan "25 Feb - 24 Mar 2026"

**Given** hari ini 2026-03-25 dan Tanggal cutoff 25
**When** Periode cutoff dihitung
**Then** periodenya bergeser ke 2026-03-25 → 2026-04-24

**Given** Kontrak dengan jadwal 7 hari
**When** hari tagih dihitung dalam periode 25 Feb → 24 Mar
**Then** jumlah hari tagih = 28

**Given** Kontrak dengan jadwal 6 hari dan hari libur Minggu
**When** hari tagih dihitung dalam periode yang sama
**Then** jumlah hari tagih = 24 — **empat Minggu dikeluarkan**

**Given** Kontrak dengan tanggal mulai 2026-02-27
**When** hari tagih dihitung dalam periode 25 Feb → 24 Mar
**Then** 25 dan 26 Feb tidak dihitung — hanya hari sejak tanggal mulai

**Given** Kontrak dengan Pengecualian Servis pada 2026-03-05
**When** hari tagih dihitung
**Then** 5 Mar tidak ditagih

**Given** Setoran bertanggal 2026-03-30, saat hari ini 2026-03-24
**When** hari tagih dihitung
**Then** 30 Mar tidak masuk hitungan — hari setelah hari ini tidak menambah Tunggakan

**And** seluruh tanggal lewat `Clock` abstraction (AR-8) — **tidak ada** konstanta tanggal hardcoded, dan tidak ada `SIM_TODAY`
**And** `Periode cutoff` yang dimulai tanggal 1–28 (dibatasi agar selalu punya minimal satu hari di bulan terpendek)
**And** `Hari kerja` dan `Pengecualian Servis` dihitung di `domain/`, tanpa Android — supaya test-nya jalan tanpa SDK

---

### Story 1.3: Alokasi dan Kredit yang tidak pernah hilang

Sebagai developers, saya ingin setiap Setoran meninggalkan jejak hari mana yang ditutupnya, sehingga Kredit bertahan lintas periode dan tidak ada rupiah yang hilang diam-diam.

**Acceptance Criteria:**

**Given** Kontrak tarif Rp 250.000, 7 hari, periode 25 Feb → 24 Mar (28 hari tagih, Kewajiban Rp 7.000.000)
**When** Setoran Rp 500.000 pada 20 Mar dicatat
**Then** alokasi menutup **dua hari tertua** — 25 dan 26 Feb
**And** Tunggakan = Rp 6.500.000
**And** jumlah baris alokasi = 2, dan `Σ alokasi == amount Setoran`

**Given** Setoran Rp 20.000.000 saat Kewajiban periode hanya Rp 7.000.000
**When** alokasi dibuat
**Then** 28 hari tagih teralokasi penuh
**And** **kelebihan Rp 13.000.000 punya satu baris alokasi dengan `chargeDate = null`** — dana ada, hari belum ditentukan
**And** Tunggakan = Rp 0
**And** jumlah baris alokasi = 29 (28 + 1 untuk sisa)

**Given** Setoran Rp 13.000.000 bertanggal 2026-02-10, **sebelum** periode berjalan dimulai 25 Feb
**When** periode berikutnya dihitung (25 Mar → 24 Apr, 31 hari tagih)
**Then** hari tagih periode baru yang sudah tertutup oleh alokasi 10 Feb **tidak ditagih ulang**
**And** Kredit benar-benar terpakai — inilah alasan `chargeDate = null` ada

**Given** Kontrak jadwal 6 hari, hari libur Minggu
**When** Setoran Rp 250.000 bertanggal **2026-03-01** (Minggu) dicatat
**Then** Sunday **tidak ditagih** dan **tetap** dihitung sebagai Setoran
**And** Setoran itu dialokasikan ke Hari tagih terdekat — Senin 2 Mar
**And** Tunggakan turun satu hari, bukan naik

**Given** Setoran Rp 100.000 saat tarif Rp 250.000
**When** alokasi dibuat
**Then** alokasi parsial — hari yang ditutup kurang dari penuh
**And** tidak ada hari yang teralokasi dua kali

**And** aturan tunggal ditegakkan tanpa pengecualian: **`Σ allocation[p] == payment[p].amount` untuk setiap Setoran** (AR-7)
**And** semua nilai uang bertipe `Long` rupiah penuh — tidak pernah `Double` atau `Float` (AR-2)

**Notes:** kasus hari Minggu adalah **regresi bug prototipe yang sudah terbukti** — `paidInWindow` di `data.js` hanya bertambah di dalam cabang yang bisa ditagih, sehingga Rp 250.000 per hari Minggu hilang tanpa jejak. Untuk kontrak 6 hari, itu Rp 1.000.000 error per bulan.

---

### Story 1.4: Hari menunggak dan tingkat risiko

Sebagai developers, saya ingin jumlah hari menunggak dihitung langsung dari alokasi, sehingga angka yang dikirim ke penyewa lewat WhatsApp benar.

**Acceptance Criteria:**

**Given** Kontrak dengan Hari tagih yang belum tertutup
**When** jumlah hari menunggak dihitung
**Then** nilainya = jumlah Hari tagih yang belum tertutup oleh alokasi
**And** **bukan** `ceil(Tunggakan ÷ tarif)` — pendekatan itu keliru, karena Setoran kurang dari tarif harian menghasilkan "satu hari menunggak" terus-menerus meski satu hari sudah lewat

**Given** Tunggakan Rp 1.000.000 pada tarif Rp 500.000
**When** hari menunggak dihitung
**Then** 2 hari

**Given** Kontrak dengan Tunggakan Rp 0
**When** tingkat risiko ditentukan
**Then** 0 hari = **Lancar**

**Given** hari menunggak 1, 2, 3, 6, dan 7
**When** tingkat risiko ditentukan
**Then** masing-masing 1–2 = **Ringan**, 3–6 = **Perhatian**, 7+ = **Kritis**

**Given** Kontrak yang sudah **diakhiri** dengan sisa Tunggakan
**When** hari menunggak dihitung
**Then** Kontrak itu **tetap dihitung** dan masuk Tunggakan aktif sampai lunas

**Given** Kontrak aktif dan Kontrak yang sudah berakhir
**When** daftar Tunggakan diurutkan
**Then** nominal terbesar dulu; untuk nominal sama, Hari tagih tertua dulu

**Notes:** nilai hari menunggak inilah yang dikirim ke penyewa lewat WhatsApp (FR-12). Kalau meleset, pemilik menagih orang yang sudah bayar dan seluruh kepercayaan hilang — ini SM-2, metrik utama produk.

---

### Story 1.5: Mengubah Tanggal cutoff hanya menggeser cakupan

Sebagai developers, saya ingin `cutoffDay` hanya mengubah rentang yang dihitung tanpa menyentuh alokasi, sehingga mengubah cutoff tidak pernah merusak angka yang sudah tercatat.

**Acceptance Criteria:**

**Given** Kontrak dengan alokasi tersimpan, periode berjalan dihitung dengan cutoff 25
**When** Tanggal cutoff diubah menjadi 15 dan Tunggakan dihitung ulang
**Then** **tidak ada baris `allocation` yang dihapus atau ditulis ulang**
**And** hanya rentang `chargeDate` yang masuk hitungan yang berubah

**Given** alokasi yang menutup hari tagih 20 Mar, saat cutoff 25
**When** cutoff diubah menjadi 15
**Then** hari 20 Mar tidak lagi masuk periode berjalan — **dan alokasi itu tetap ada**
**And** saat periode berikutnya dihitung, hari 20 Mar itu teralokasi dan tidak ditagih ulang

**Given** Kontrak dengan `settledThroughDate` terisi (FR-9b, aturan domainnya di epic ini)
**When** hari tagih dihitung
**Then** semua hari tagih sebelum `settledThroughDate` dianggap **sudah tertutup**
**And** **tidak ada baris alokasi yang ditulis** untuk FR-9b — ia mengubah field Kontrak, bukan menyimpan Setoran palsu

**Given** Kontrak aktif dan Kontrak yang sudah berakhir
**When** cutoff diubah
**Then** seluruh Tunggakan dihitung ulang dengan cutoff baru, tanpa data hilang

**Notes:** keputusan ini **digeser, bukan diturunkan ulang** — disepakati pemilik. Alokasi adalah fakta; `cutoffDay` cuma memetakan fakta itu ke periode. Hitung-ulang-dari-awal akan menghapus tabel dan bertentangan dengan AD-3.

### Story 1.6: Mengukur performa, bukan menebaknya

Sebagai developers, saya ingin performa hitung ulang diukur dengan angka nyata, sehingga NFR-1 punya bukti dan tidak bisa gagal diam-diam.

**Acceptance Criteria:**

**Given** 20 Kontrak dengan total 2.000 Setoran dan 500 hari tagih
**When** Tunggakan dihitung ulang untuk seluruh Kontrak
**Then** waktu kalkulasi selesai **di bawah 50 milidetik**
**And** angka hasil dicatat sebagai baseline di dokumen epic

**Given** hasil benchmark melampaui 50ms di perangkat
**When** ambang dievaluasi
**Then** ambang **diturunkan atau diabaikan secara eksplisit dan tercatat** — **tidak boleh dihapus diam-diam** (NFR-1: jangan mengorbankan kebenaran demi kecepatan)

**Given** benchmark dijalankan di perangkat Redmi Pad 2 dalam mode debug
**When** hasilnya dibandingkan antar-commit
**Then** dipakai sebagai **baseline relatif**, bukan klaim performa produksi
**And** test tetap hijau di mesin build tanpa perangkat, dengan ambang lebih longgar yang ditandai eksplisit

**And** Tujuh kasus `verify-fr10.md` **semua hijau** sebelum epic ini dinyatakan selesai

**Notes:** ini harus jadi test, bukan catatan. NFR-1 menyebut "50 milidetik" tanpa test — persis yang party mode temukan: performa bisa gagal tanpa gagal test mana pun. Benchmark di perangkat nyata adalah kondisi yang berbeda dari test CI; keduanya perlu, dan perbedaannya harus dinyatakan, bukan disembunyikan.

---

## Epic 2: Armada dan orang

Andi bisa menyimpan dan melihat seluruh armada, penyewa, dan kontraknya. Ini data yang dia ketik pertama kali — dan nilainya berdiri sendiri: "ini armada saya, ini orang saya, ini kontrak saya."

Berlaku **sementara mesinnya belum ada**: layar-layar ini menampilkan data apa adanya. Begitu Epic 1 masuk, layar yang sama langsung menampilkan angka sungguhan tanpa satu baris perubahan.

Berisi juga design system dan seluruh komponen inti — karena master data adalah tempat pertama komponen-komponen itu dipakai.

**FR:** FR4, FR5, FR6, FR7, FR8, FR9 · **NFR:** NFR4, NFR6, NFR8, NFR9 · **AR:** AR-12, AR-16, AR-18 · **UX-DR:** 1–8, 14, 16–19 · **Lubang ditutup di sini:** status `servis` vs Kontrak aktif

---

### Story 2.1: Design system dan komponen inti yang dipakai semua layar berikutnya

Sebagai developers, saya ingin satu set komponen dan token yang sudah implementing aksesibilitas, sehingga setiap layar berikutnya tidak mengulang-ulang style dan tidak bisa melewatkan kontras atau target sentuh.

**Acceptance Criteria:**

**Given** aplikasi belum punya tema
**When** design system diterapkan
**Then** token warna brand, netral hangat, dan empat warna semantik sesuai `DESIGN.md`
**And** tema terang **dan** gelap keduanya berfungsi
**And** **foreground semantik tidak berubah di tema gelap** — hanya background-nya

**Given** komponen Button, Card, KPI Tile, Badge, Chip, Search bar, Table/List row, Avatar, Modal, Snackbar, Empty state, PIN grid, Nav rail, Bottom nav, dan Top bar (UX-DR7)
**When** varian dan ukurannya diimplementasikan
**Then** 5 varian (primary, secondary, ghost, danger, success) × 4 ukuran (lg, default, sm, icon) tersedia
**And** `.btn-icon` berukuran persis 48dp dengan bentuk pill

**Given** komponen Field
**When** validasi gagal
**Then** pesan error **muncul** dan menyebutkan apa yang salah **dan** bagaimana memperbaikinya (`Nominal harus lebih besar dari nol.`)
**And** pesan error **hidden by default** — form yang selalu menampilkan placeholder error mengajari pengguna untuk mengabaikannya

**Given** komponen Numpad
**When** nominal diketik
**Then** menampilkan angka yang sedang diketik besar dan tabular di atas
**And** tombol simpan **menolak** nilai ≤ 0 dengan flash merah — bukan menerima diam-diam
**And** input dibatasi 12 digit
**And** setiap tombol memicu getar (haptic)

**Given** target sentuh
**When** semua elemen interaktif diukur
**Then** **tidak ada satu pun** di bawah 48dp — termasuk badge, chip, dan tombol ikon di dalam baris tabel
**And** pengukuran ini adalah bagian dari test, bukan pemeriksaan manual

**Given** kontras warna
**When** teks utama, sekunder, dan tersier diukur pada tema terang
**Then** masing-masing ≥ 4.5:1, ≥ 4.5:1, dan ≥ 4.5:1
**And** pada tema gelap, kontras tetap memenuhi standar

**Given** angka rupiah ditampilkan
**When** beberapa nominal ditampilkan berdampingan
**Then** memakai `tabular-nums` sehingga mudah dibandingkan (UX-DR5)

**Given** fokus keyboard atau navigasi
**When** fokus berpindah antar-elemen
**Then** focus ring 3dp berwarna brand terlihat di setiap elemen interaktif
**And** urutan fokus mengikuti urutan visual — tidak melompat-lompat

**Given** animasi
**When** transisi terjadi
**Then** semua memakai easing `cubic-bezier(.2,.8,.2,1)` dan durasi 140/220/300ms
**And** **tidak ada motion yang berulang**, dan tidak ada yang lebih dari 300ms
**And** tidak ada bounce atau overshoot
**And** motion hanya di tiga tempat: stagger saat layar dibuka (sekali), shake 420ms untuk PIN salah (sekali), dan fade-up untuk konten yang masuk — **tidak ada lagi dari itu** (UX-DR6)

**And** ikon memakai `com.composables:icons-lucide` — **tidak ada Material Icons** (AR-16)
**And** logo diambil dari `prototype/assets/logo.svg` sebagai drawable, bukan menulis ulang path-nya (UX-DR19)
**And** semua string Bahasa Indonesia di satu tempat, bukan `strings.xml` berteks hardcode (UX-DR14)

**Notes:** komponen-komponen ini dibangun sekarang, bukan nanti, karena dipakai bersama oleh seluruh layar. Membangunnya sebagai story terpisah di akhir berarti setiap layar interim memakai style inline — dan style inline_iterum adalah tempat aksesibilitas bocor.

**And** token warna brand coral/rose dengan scale lengkap, netral hangat, dan empat warna semantik diimplementasikan sesuai `DESIGN.md` — **foreground semantik tidak berubah di tema gelap, hanya background-nya** (UX-DR1, UX-DR2)
**And** tipografi dua wajah — Fraunces untuk judul dan angka besar, Plus Jakarta Sans untuk body — dengan **minimum 14sp untuk label dan data** (UX-DR3)
**And** skala spacing basis 4dp, radius 20px untuk kartu, pill untuk elemen inline, serta elevation berupa **bayangan hangat lembut** tiga tingkat plus bayangan khusus tombol primary (UX-DR4)

---

### Story 2.8: Empat state setiap layar, dan prinsip yang tidak bisa dilanggar

Sebagai developers, saya ingin setiap layar punya keempat state yang benar dan test yang menjaga, sehingga tidak ada layar yang bisa menampilkan data kosong yang terlihat seperti akun baru.

**Acceptance Criteria:**

**Given** sebuah layar sedang memuat data
**When** state-nya berubah
**Then** tampil **skeleton dengan bentuk yang sama dengan isi akhirnya** — bukan spinner
**And** untuk aplikasi yang offline dan cepat, loading tidak boleh lama; kalau muncul lama, berarti ada yang salah

**Given** sebuah daftar kosong
**When** layar dibuka
**Then** empty state menyebut **apa yang harus dilakukan**, bukan hanya "belum ada data"
```
Belum ada Kendaraan
Tambahkan mobil pertama untuk mulai mencatat sewa.
[Tambah Mobil]
```
**And** empty state adalah tempat terbaik untuk mengajari aplikasi — bukan hanya memberi tahu tidak ada apa-apa (UX-DR12)

**Given** terjadi kegagalan
**When** error ditampilkan
**Then** menjelaskan **apa yang salah dan apa yang harus dilakukan**
**And** **tidak pernah** menampilkan data kosong yang terlihat seperti akun baru
**And** **tidak pernah** me-reset ke default dan menampilkan data kosong — itu menghancurkan data secara diam-diam

**Given** terjadi kegagalan pada data yang tersimpan
**When** aplikasi dijalankan
**Then** aplikasi **gagal dengan pesan yang jelas**, bukan me-reset
**And** pengguna diberi jalan keluar yang nyata — bukan hanya "coba lagi"

**Given** sebuah aksi berhasil
**When** snackbar muncul
**Then** snackbar **tidak mengubah state layar** — pengguna bisa langsung lanjut ke aksi berikutnya tanpa menutup apa pun

**Given** aplikasi sudah berjalan
**When** principle diuji
**Then** tiga prinsip ini **teregakkan dan bisa diuji** (UX-DR16):
1. **Angka harus bisa dipercaya** — kalau Tunggakan salah sekali, orang yang ditagih akan berhenti memakai aplikasi; satu kesalahan menghapus semua pekerjaan di layar lain
2. **Pratinjau sebelum menyimpan** — setiap aksi yang mengubah angka menunjukkan efeknya lebih dulu
3. **Data tidak boleh hilang diam-diam** — kegagalan yang jelas dan jujur lebih baik daripada data yang hilang tanpa penjelasan

**And** keempat state ini **berlaku untuk setiap layar** — bukan hanya layar yang dibangun lebih dulu
**And** tiap state punya **empty state atau skeleton yang sesuai** — tidak ada layar yang bisa tampil tanpa keempatnya

**Notes:** ini gap yang ditemukan validasi, bukan tambahan scope. UX-DR12 dan UX-DR16 ada di requirements tapi tidak terpakai story manapun — padahal UX-DR12 yang melarang data kosong terlihat seperti akun baru adalah **persis** mode gagal yang ditemukan di prototipe: `init()` menelan parse error lalu me-reset ke default, jadi seluruh riwayat hilang dan pengguna mengira ini akun baru. Itu harus jadi test, bukan catatan.

---

### Story 2.2: Menyimpan armada mobil

Sebagai pemilik rental, saya ingin menyimpan mobil saya beserta tarif hariannya, sehingga saya bisa menyewakannya tanpa mencari catatan fisik.

**Acceptance Criteria:**

**Given** belum ada Kendaraan
**When** saya menambah mobil
**Then** saya bisa mengisi plat, merek, tipe, tahun, warna, tarif harian, dan catatan
**And** mobil langsung muncul di daftar

**Given** plat sudah dipakai mobil lain
**When** saya menambah mobil dengan plat yang sama ditulis berbeda
**Then** aplikasi **menolak** dengan pesan yang menyebut plat yang bentrok
**And** pengecekan terjadi **setelah normalisasi** — huruf besar, spasi dirapatkan — sehingga `b 1235 tf` dan `B 1235 TF` dianggap sama

**Given** saya mengetik tarif harian `0` atau angka negatif
**When** saya menyimpan
**Then** aplikasi **menolak**
**And** **tidak ada nilai bawaan yang diam-diam menggantikannya** — bukan `250.000`, bukan apa pun (NFR-6). Ini persis mode gagal `parseInt(x) || 250000` yang ditemukan di prototipe

**Given** ada Kendaraan yang sedang disewa
**When** saya mencoba menghapusnya
**Then** aplikasi menolak dan menyuruh menyelesaikan Kontraknya dulu

**Given** daftar Kendaraan
**When** saya membuka halamannya
**Then** ada pencarian dan filter
**And** plat, merek, tipe, dan tarif tampil terbaca
**And** filter status (tersewa / tersedia / servis) bekerja sebagai filter, **bukan navigasi terpisah**

**Given** aplikasi baru dibuka
**When** belum ada data
**Then** empty state muncul dengan **apa yang harus dilakukan**, bukan hanya "belum ada data" — misal `Belum ada Kendaraan. Tambahkan mobil pertama untuk mulai mencatat sewa.`

**Given** setiap nilai uang
**When** disimpan
**Then** bertipe `Long` rupiah penuh, bukan `Double` (AR-11)
**And** uniqueness plat ditegakkan di **lapisan data** lewat indeks unik, bukan hanya di antarmuka — dua layar bisa melewati pengecekan UI yang sama (AR-12)

---

### Story 2.3: Melihat status ketersediaan armada

Sebagai pemilik rental, saya ingin melihat mobil mana yang tersewa, tersedia, dan di servis, sehingga saya tahu armada saya dalam keadaan apa.

**Acceptance Criteria:**

**Given** Kendaraan punya Kontrak aktif
**When** status ditampilkan
**Then** tampil sebagai **tersewa** — status ini **diturunkan** dari Kontrak, bukan disimpan terpisah

**Given** Kendaraan tidak punya Kontrak aktif
**When** status ditampilkan
**Then** tampil sebagai **tersedia**

**Given** saya menandai Kendaraan sebagai `servis`
**When** status ditampilkan
**Then** tampil sebagai **servis** — dan **tidak ada Kontrak yang ikut berubah**

**Given** Kendaraan ditandai `servis` **dan** punya Kontrak aktif
**When** hari tagih dihitung
**Then** **tagihan tetap berjalan** — status `servis` tidak menghentikan tagihan
**And** **satu-satunya** cara menghentikan tagihan adalah **Pengecualian Servis** pada Kontrak, yang dicatat per tanggal (FR-8)
**And** tidak ada mekanisme kedua yang diam-diam menghentikan tagihan

**Given** Kontrak ditutup
**When** status Kendaraan ditampilkan
**Then** tampil **tersedia** — tidak ada tombol yang perlu ditekan manual untuk Menyinkronkan

**And** status dihitung dari Kontrak, jadi tidak mungkin tidak sinkron (FR-5: status adalah informasi tampilan, bukan sumber kebenaran)
**And** KPI jumlah armada di Beranda — yang termasuk di Epic 4 — menghitung dari aturan yang sama ini, bukan dari kolom terpisah

**Notes:** keputusan ini menjawab lubang party mode. Yang penting dicatat: kalau `servis` ternyata perlu menghentikan tagihan nanti, itu **Pengecualian Servis** yang harus diubah — **bukan** status `servis`. Dua mekanisme untuk hal yang sama akan membuat tagihan berhenti tanpa jejak, dan bulanan itu akan muncul sebagai kesalahan yang tidak bisa dijelaskan.

---

### Story 2.4: Menyimpan data penyewa

Sebagai pemilik rental, saya ingin menyimpan data penyewa beserta nama dan nomor rekeningnya, sehingga saya bisa mencocokkan siapa yang transfer di m-banking.

**Acceptance Criteria:**

**Given** belum ada Penyewa
**When** saya menambah penyewa
**Then** saya bisa mengisi nama, nomor telepon, nomor rekening, dan kontak darurat
**And** penyewa langsung muncul di daftar

**Given** nama dikosongkan
**When** saya menyimpan
**Then** aplikasi menolak — **nama adalah kunci rekonsiliasi dengan mutasi bank, jadi wajib**

**Given** nama sudah dipakai penyewa lain
**When** saya menambah penyewa dengan nama yang sama
**Then** aplikasi menolak — nama unik dalam aplikasi agar tidak ambigu saat mencocokkan

**Given** kolom KTP atau alamat
**When** saya membuka form penyewa
**Then** **tidak ada field itu** — PRD §6.1 melanjutkan data KTP dan alamat ke luar scope, jadi tidak ada data pribadi yang perlu disimpan tanpa alasan

**Given** daftar Penyewa
**When** saya membuka halamannya
**Then** pencarian tersedia
**And** daftar dan detail tampil bersamaan di landscape (master-detail), dan runtuh jadi daftar → detail di portrait (UX-DR13)

**And** uniqueness nama ditegakkan di lapisan data lewat indeks unik (AR-12)

---

### Story 2.5: Membuka kontrak sewa

Sebagai pemilik rental, saya ingin mengikat penyewa dengan mobil dan tarifnya, sehingga saya bisa tahu siapa owes berapa per hari.

**Acceptance Criteria:**

**Given** saya membuka Kontrak baru
**When** saya memilih Penyewa dan Kendaraan
**Then** saya menentukan tanggal mulai, tarif harian, jadwal kerja, dan hari libur
**And** Kontrak langsung berstatus **aktif**

**Given** Kendaraan sudah punya Kontrak aktif
**When** saya mencoba membuka Kontrak untuk Kendaraan itu
**Then** aplikasi menolak
**And** hal yang sama berlaku untuk Penyewa yang sudah punya Kontrak aktif

**Given** saya memilih jadwal 6 hari
**When** saya membuka form
**Then** ada pemilih hari libur, default **Minggu**
**And** hanya satu hari yang bisa dipilih

**Given** tarif harian Kendaraan
**When** Kontrak dibuka
**Then** tarif **disalin** dari Kendaraan, dan tidak berubah otomatis kalau tarif Kendaraan berubah
**And** saya boleh memakai tarif berbeda untuk harga yang sudah dinegosiasi

**Given** Kontrak untuk penyewa yang sedang menyewa sejak sebelum aplikasi dipakai
**When** tanggal mulai saya isi dengan tanggal yang sudah lewat
**Then** aplikasi **menerima** — kontrak seperti ini nyata, dan jalan keluarnya ada di FR-9b (Epic 3)
**And** hari tagih sejak tanggal mulai itu dihitung

**And** hanya Kontrak dengan Kontrak aktif boleh diedit; yang sudah berakhir dikunci (FR-9)
**And** seluruh aturan ini ditegakkan di lapisan data, bukan hanya di antarmuka

**Notes:** konvensi satu-kontrak-aktif-dari-`currentRenterId` yang dipakai prototipe **tidak** dipakai. Kolom itu bisa tidak sinkron dengan `CONTRACT`, dan tidak ada yang mencegah dua Kontrak aktif untuk satu Kendaraan. Partial unique index di database yang menjaganya.

---

### Story 2.6: Menandai hari tanpa tagihan

Sebagai pemilik rental, saya bisa menandai tanggal tertentu yang tidak ditagih ke penyewa, sehingga ketika mobil diservis dan saya mengompensasi, tagihannya otomatis berhenti di tanggal itu.

**Acceptance Criteria:**

**Given** Kontrak dengan jadwal 6 hari
**When** saya membuka daftar hari tanpa tagihan
**Then** tanggal yang sudah ditandai tampil sebagai **pengecualian**, dengan keterangan alasannya
**And** tanggal tersebut **tidak ditagih** — terhitung sebagai hari yang sudah tertutup

**Given** tanggal yang sudah ditandai
**When** saya menambahkan tanggal yang sama
**Then** aplikasi menolak — tanggal duplikat tidak diperbolehkan

**Given** Kontrak dengan pengecualian
**When** periode berikutnya dihitung
**Then** pengecualian **tidak ikut bergeser ke periode berikutnya** — ia melekat pada tanggal asalnya

**Given** Kontrak yang diakhiri
**When** saya membuka daftar pengecualian
**Then** **tidak bisa diedit** — riwayat tetap terbaca

**And** pengecualian disimpan **per Kontrak**, bukan per Kendaraan — satu Kendaraan bisa punya beberapa Kontrak dengan pengecualian berbeda
**And** kolom yang dipakai adalah `SERVICE_EXCEPTION(contractId, date)` dengan unique index pada pasangan itu

**Notes:** ini sisi **pendapatan** dari servis — penyewa dikompensasi sehingga tidak ditagih. Itu berbeda dengan mencatat **biaya** servis, yang sudah di luar scope (PRD §6.2) dan tidak dibangun di epic mana pun.

---

### Story 2.7: Mengakhiri kontrak sewa

Sebagai pemilik rental, saya ingin mengakhiri kontrak ketika penyewa mengembalikan mobil, sehingga tagihan berhenti pada tanggal itu dan mobilnya kembali tersedia.

**Acceptance Criteria:**

**Given** Kontrak aktif
**When** saya mengakhirinya dengan tanggal akhir
**Then** Kontrak berhenti menambah Tunggakan setelah tanggal itu
**And** Kendaraan otomatis tampil sebagai **tersedia** — tidak ada tombol sinkronisasi manual

**Given** Kontrak dengan kelebihan Setoran saat diakhiri
**When** Kontrak ditutup
**Then** kelebihan itu **tidak hilang** — menjadi Kredit pada Kontrak tersebut
**And** Kontrak yang sudah berakhir **tetap dihitung Tunggakannya** sampai lunas, dan masuk Tunggakan aktif

**Given** Kontrak yang sudah berakhir
**When** saya mencoba mengedit tarif, jadwal, atau pengecualian
**Then** semua terkunci

**Given** Setoran lama pada Kontrak yang sudah berakhir
**When** saya membuka riwayatnya
**Then** masih dapat dibaca
**And** masih dapat dibatalkan dengan alasan wajib

**And** Pengakhiran Kontrak ditulis dalam satu transaksi — status Kontrak, Tunggakan, dan status Kendaraan tidak boleh separuh berubah (AD-4)

---

## Epic 3: Catat dan koreksi setoran

Andi bisa mencatat setoran pagi itu dalam satu langkah, melihat pratinjau sebelum menyimpan, mengoreksi yang salah input, dan keluar dari kontrak lama tanpa mengetik riwayat.

Serta **satu jalan keluar untuk hari pertama**: FR-9b dan daftar Rekening usaha ikut di sini, bukan di epic berikutnya. Tanpa keduanya, aplikasi pertama kali dibuka akan menampilkan seluruh kontrak lama sebagai "28 hari Kritis" dengan utang fiktif, dan form Setoran punya dropdown yang belum punya isi.

**FR:** FR1, FR1b, FR2, FR3, FR9b, FR18a · **NFR:** NFR2, NFR6 · **AR:** AR-8, AR-9, AR-11, AR-17 · **UX-DR:** 10, 11, 12, 14, 15

---

### Story 3.1: Daftar rekening usaha yang dipakai saat mencatat setoran

Sebagai pemilik rental, saya ingin mengaktifkan rekening-rekening yang menerima transfer, sehingga saat mencatat setoran saya tinggal memilih dari daftar, bukan mengetik ulang nomor rekening setiap hari.

**Acceptance Criteria:**

**Given** belum ada rekening usaha
**When** saya membuka Pengaturan
**Then** saya bisa menambah rekening: nama bank dan nomor rekening
**And** saya bisa menambah lebih dari satu — Setoran bisa masuk lewat beberapa bank

**Given** saya mencatat Setoran dengan Metode Pembayaran Transfer Bank
**When** saya membuka form
**Then** saya memilih Rekening usaha dari daftar
**And** saya **tidak** mengetik nomor rekening secara bebas

**Given** nomor rekening yang saya masukkan
**When** saya menyimpan
**Then** formatnya divalidasi sebatas format saja
**And** **tidak** diverifikasi terhadap bank — tidak ada yang berubah kalau bank punya nomor berbeda

**Given** ada Setoran yang memakai suatu rekening
**When** saya mencoba menghapus rekening itu
**Then** aplikasi menolak
**And** aturan ini ditegakkan di lapisan data, bukan hanya di antarmuka

**Given** saya mengubah nomor rekening
**When** Setoran lama ditinjau
**Then** Setoran lama **tidak berubah** — ia tetap seperti saat dicatat

**And** rekening disimpan di tabel `settings` bersama konfigurasi lain, **bukan** DataStore — supaya ikut tercakup backup/restore (AR-17)
**And** `settings.value` bertipe `String` dan **tidak dipakai menyimpan nilai uang** (AR-11)

**Notes:** ini FR-18a — bagian "rekening" saja, bukan identitas usaha. Yang ini masuk epic karena FR-1b hard-dependency ke sini: form Setoran dengan dropdown yang belum punya isi harus dibangun dua kali.

---

### Story 3.2: Mencatat setoran dengan numpad

Sebagai pemilik rental, saya ingin mencatat setoran dalam satu langkah tanpa berdiri dan tanpa keyboard, sehingga rekap pagi selesai dalam sekali duduk.

**Acceptance Criteria:**

**Given** saya menekan "Catat Setoran" dari Beranda atau speed dial
**When** form terbuka
**Then** saya memilih Penyewa, dan **Kendaraan beserta tarif hariannya terisi otomatis** dari Kontrak aktif

**Given** saya sudah memilih Penyewa
**When** saya sampai ke nominal
**Then** **numpad terbuka dan wajib dipakai** — nominal selalu diketik di numpad besar, bukan di keyboard sistem
**And** display nominal tampil besar dan tabular di atas numpad
**And** input dibatasi 12 digit

**Given** nominal sudah diketik
**When** nilainya `0` atau negatif
**Then** tombol Simpan **menolak** dengan flash merah
**And** **tidak ada nilai bawaan yang diam-diam menggantikannya** (NFR-6)

**Given** saya memilih Metode Pembayaran Tunai
**When** form tampil
**Then** field Rekening usaha dan Kode transaksi **disembunyikan** — bukan diisi kosong
**And** mengisi kosong menciptakan ilusi data yang padahal tidak ada

**Given** Setoran Tunai
**When** saya menyimpannya
**Then** aplikasi mencatat bahwa tidak ada sumber eksternal yang bisa memverifikasinya
**And** catatan menjadi satu-satunya breadcrumbs yang ada — tidak ada mutasi bank untuk dibandingkan

**Given** form punya banyak input
**When** ia muncul
**Then** tampil sebagai **bottom sheet**, bukan dialog modal
**And** alasannya: numpad butuh ruang vertikal, dan di tablet keyboard virtual memenuhi sebagian besar layar — dialog akan menutupi numpad itu sendiri (UX-DR18)

**And** setiap tombol numpad memicu getar — konfirmasi taktil tanpa harus melihat layar (UX-DR8)
**And** nominal yang tampil memakai `tabular-nums` (AR-11)

**Notes:** numpad wajib untuk **setiap** Setoran, meski pembayarannya penuh dan tarif sudah terisi. Ini keputusan pemilik: mengetik ulang dari nol memaksa pemilik mengingat nominal yang sudah ada di aplikasi, dan itu memunculkan sumber salah ketik yang tidak perlu. Yang hilang bukan kecepatannya — yang hilang adalah kemungkinan salah ketik.

---

### Story 3.3: Melihat pratinjau sebelum menyimpan

Sebagai pemilik rental, saya ingin melihat efek setoran terhadap tunggakan sebelum menyimpannya, sehingga saya percaya pada angka yang muncul setelahnya.

**Acceptance Criteria:**

**Given** semua isian sudah terisi
**When** saya menekan Simpan
**Then** dialog pratinjau muncul **sebelum** data tersimpan
**And** pratinjau ini **wajib** — tidak bisa dilewati, termasuk kalau saya sudah tahu hasilnya (UX-DR10)

**Given** Kontrak dengan Tunggakan Rp 750.000 dan tarif Rp 250.000
**When** saya mencatat Setoran Rp 500.000
**Then** pratinjau menampilkan:
```
Tunggakan: Rp 750.000 → Rp 250.000
Menutup: 2 hari · Sisa: 1 hari (Rp 250.000)
```

**Given** Setoran lebih besar dari Kewajiban periode berjalan
**When** pratinjau dihitung
**Then** menampilkan Kredit beserta penjelasannya:
```
Tunggakan: Rp 750.000 → Rp 0
Menutup: 3 hari
Kredit: Rp 250.000 — dipakai otomatis untuk periode berikutnya
```
**And** Kredit tidak pernah muncul sebagai angka misterius tanpa penjelasan

**Given** pratinjau sudah tampil
**When** saya membacanya
**Then** **tidak ada tanggal per-hari** — hanya nominal, jumlah hari, dan kredit
**And** audit tanggal tersedia di riwayat Setoran, bukan di setiap kali menyimpan

**Given** pratinjau untuk Setoran pada Kontrak baru yang belum punya riwayat
**When** saya menekan Simpan
**Then** Tunggakan dan hari yang tertutup dihitung dari mesin yang sama dengan yang dipakai layar Tunggakan — bukan perhitungan terpisah

**And** pratinjau dihitung dengan mesin yang **tidak menulis apa pun** — dia murni membaca, sehinggakalkulasi pratinjau dan kalkulasi penyimpanan tidak mungkin berbeda

**Notes:** pratinjau yang salah lebih berbahaya daripada tidak ada pratinjau — pemilik akanRlalu percaya pada angka yang tidak pernah dia lihat perubahannya. Karena ituformatnya **digeser** ke yang ringkas supaya masih dibaca setelah dipakai setahun, bukan jadi lantai yang diabaikan.

---

### Story 3.4: Membatalkan setoran dengan alasan

Sebagai pemilik rental, saya ingin membatalkan setoran yang salah catat dengan menyebut alasannya, agar tunggakan kembali benar dan jejaknya tetap ada.

**Acceptance Criteria:**

**Given** ada Setoran yang tercatat
**When** saya membatalkannya
**Then** dialog konfirmasi muncul **dan menyebutkan dampaknya** — bukan hanya "Yakin tidak mau membatalkan?"
```
Batalkan setoran Rp 250.000?
Tunggakan Pak Budi akan kembali menjadi Rp 750.000.
Alasan pembatalan (wajib):
```

**Given** alasan dikosongkan
**When** saya menekan konfirmasi
**Then** aplikasi menolak — pembatalan **wajib** menyertakan alasan

**Given** Setoran sudah dibatalkan
**When** saya membukanya di riwayat
**Then** Setoran **tetap terlihat** — jejaknya tidak hilang
**And** hanya tidak lagi mengurangi Tunggakan

**Given** Setoran dibatalkan
**When** Tunggakan dihitung ulang
**Then** Tunggakan **naik** sesuai nilai Setoran yang dibatalkan
**And** hari menunggak ikut bertambah

**Given** Setoran yang dibatalkan
**When** saya membukanya
**Then** saya bisa memulihkannya tanpa mengetik ulang — satu ketukan

**Given** pembatalan dijalankan
**When** selesai
**Then** alokasi Setoran itu **dilepas**, dan seluruh alokasi Kontrak **dihitung ulang** dari Setoran yang tersisa — dalam satu transaksi
**And** aturan `Σ alokasi == amount` tetap berlaku setelah pembatalan (AR-8)

**And** pembatalan dicatat sebagai perubahan status dengan alasan — **tidak ada hard delete** (FR FR-3, NFR-5)

**Notes:** pembatalan adalah soft delete yang menyimpan alasannya. Ini yang membuat jejaknya tetap bisa dibaca — dan yang membuat pemilik bisa mengaudit "kenapa tunggakan Pak Budi naik hari itu".

---

### Story 3.5: Mengoreksi setoran yang salah input

Sebagai pemilik rental, saya ingin mengoreksi setoran yang salah catat tanpa membatalkan dan mencatat ulang, sehingga riwayat tetap utuh dan tunggakan langsung benar.

**Acceptance Criteria:**

**Given** ada Setoran yang tercatat
**When** saya membukanya untuk diubah
**Then** saya bisa mengubah tanggal, nominal, Rekening usaha, catatan, dan Kode transaksi
**And** **Metode Pembayaran tidak dapat diubah** — memperbaiki metode berarti membatalkan lalu mencatat ulang

**Given** saya mengubah tanggal atau nominal
**When** saya menyimpan
**Then** Tunggakan **dihitung ulang seketika** — tidak perlu memuat ulang atau berpindah halaman
**And** hari menunggak ikut menyesuaikan

**Given** saya mengubah Setoran
**When** perubahan berjalan
**Then** alokasi **diturunkan ulang** — sama seperti pembatalan
**And** alokasi lama milik Setoran itu dihapus, lalu dihitung dari semua Setoran yang tersisa, dalam satu transaksi (AR-8)
**And** `Σ alokasi == amount` tetap berlaku

**Given** saya mengubah tanggal Setoran dari Minggu menjadi Senin
**When** perubahan disimpan
**Then** Tunggakan langsung berubah — contoh dari UJ-2: Budi bayar hari Minggu untuk hari Senin, dikoreksi ke Senin, tunggakan langsung hilang

**Given** Setoran yang sudah diubah
**When** saya membukanya lagi
**Then** Setoran hanya punya satu nilai terkini — riwayat perubahan tidak menyimpan versi lama

**And** konfirmasi untuk perubahan ini **tidak muncul** — mengubah field tidak merusak data, jadi konfirmasi yang berlebihan membuat orang berhenti membaca (UX-DR11)
**And** **tidak ada gestur yang menjadi satu-satunya jalan ke aksi penting** — semua aksi punya tombol yang terlihat, dan **tidak ada swipe gesture untuk menghapus Setoran** karena punya konsekuensi uang dan butuh alasan (UX-DR15)

**Notes:** koreksi adalah operasi yang lebih sering terjadi daripada pembatalan dalam pemakaian nyata — salah ketik di numpad itu hal biasa, sedangkan membatalkan seluruh catatan hanya perlu kalau tanggalnya salah saat itu juga. Karena itu koreksi harus lebih ringan daripada pembatalan.

---

### Story 3.6: Menandai kontrak lunas sampai tanggal tertentu

Sebagai pemilik rental, saya bisa menyatakan sebuah kontrak sudah lunas sampai tanggal tertentu, sehingga hari-hari yang sudah dibayar di tempat lain tidak muncul sebagai utang fiktif.

**Acceptance Criteria:**

**Given** Kontrak yang berjalan sebelum aplikasi pertama dipakai
**When** saya membuka detail Kontrak
**Then** ada aksi "Tandai lunas sampai [tanggal]"

**Given** saya memilih tanggal
**When** konfirmasi muncul
**Then** konfirmasi menyebutkan **Kontrak, Kendaraan, Penyewa, jumlah hari yang dihapus, dan nominalnya**
```
Tandai Kontrak Budi Santoso · B 1235 TF lunas sampai 1 Maret 2026?
Menghapus Rp 7.000.000 tunggakan (28 hari) dari daftar ini.
[Lanjut] [Batal]
```

**Given** tanggal dipilih
**When** aksi dijalankan
**Then** seluruh Tunggakan untuk semua hari tagih sebelum tanggal itu dihapus dalam satu operasi
**And** Tunggakan setelah tanggal itu **tetap dihitung** sesuai alokasi normal

**Given** FR-9b dijalankan
**When** diperiksa di database
**Then** **tidak ada baris alokasi yang ditulis** dan **tidak ada Setoran yang dibuat** — ia mengubah `settledThroughDate` pada Kontrak saja
**And** mesin hitung memperlakukannya sebagai hari tagih yang sudah tertutup

**Given** ada Kontrak lain
**When** FR-9b dijalankan pada satu Kontrak
**Then** Kontrak lain **tidak terpengaruh**

**Given** hari tagih akan melewati `settledThroughDate` di periode berikutnya
**When** periode berganti
**Then** hari-hari itu **tidak ditagih ulang**

**And** FR-9b **tidak punya** representasi per hari, tidak punya jejak audit selain waktu aksi, dan tidak bisa di-undo — ini untuk menyamakan aplikasi dengan kenyataan, bukan mencatat pembayaran (PRD FR-9b)

**Notes:** ini adalah **onboarding, bukan migrasi**. Tanpa FR-9b, aplikasi yang baru dibuka pertama kali akan menampilkan setiap Kontrak lama sebagai "28 hari Kritis" dengan utang Rp 6–7 juta — dan karena PRD §6.1 tegas tidak ada impor Excel dan tidak ada entri historis massal, **FR-9b adalah satu-satunya jalan keluar**. Bukan karena ada data yang perlu dimigrasikan, tetapi justru karena tidak ada.

---

## Epic 4: Tunggakan, pengingat, laporan

Andi bisa melihat daftar tunggakan dengan hari dan warna yang langsung dibaca, mengirim pengingat WhatsApp dengan angka benar, membaca laporan bulanan, dan melihat ringkasan pagi di Beranda.

Ini epic yang **membuktikan SM-2 di dunia nyata** — bukan lewat model, tapi lewat koreksi entri yang salah lalu melihat tunggakan kembali benar (FR-3 yang sudah ada di Epic 3).

**FR:** FR11, FR12, FR13, FR14, FR18b, FR19 · **NFR:** NFR1, NFR7 · **AR:** AR-9, AR-15, AR-17 · **UX-DR:** 9, 10, 13

---

### Story 4.1: Daftar tunggakan yang langsung terbaca

Sebagai pemilik rental, saya ingin melihat siapa yang menunggak, berapa, dan sudah berapa hari, sehingga saya tahu siapa yang harus saya hubungi hari ini.

**Acceptance Criteria:**

**Given** ada Kontrak dengan Tunggakan
**When** saya membuka daftar
**Then** tiap baris menampilkan **nominal dan jumlah hari** — tidak pernah salah satu saja
```
Rp 750.000 · 3 hari
```
**And** warna diturunkan dari angka hari — bukan dipilih manual
**And** syaratnya: kalau skalanya berubah, warnanya ikut. Tidak ada keadaan di mana angka bilang "3 hari" tapi badge hijau (UX-DR9)

**Given** hari menunggak berbeda
**When** tingkat risiko ditampilkan
**Then** 0 = Lancar, 1–2 = Ringan, 3–6 = Perhatian, 7+ = Kritis

**Given** daftar Tunggakan
**When** diurutkan
**Then** nominal terbesar dulu
**And** untuk nominal sama, Hari tagih tertua dulu — itu yang paling mendesak

**Given** ada Kontrak yang **sudah berakhir** dengan sisa Tunggakan
**When** daftar ditampilkan
**Then** Kontrak itu **tetap ada** di daftar sampai lunas — dengan penanda bahwa kontraknya sudah selesai
**And** Tunggakan aktif mencakup Kontrak aktif **dan** Kontrak yang sudah berakhir dengan sisa

**Given** daftar dibuka di landscape
**When** beberapa Kontrak ingin dibandingkan
**Then** daftar dan detail tampil bersamaan — daftar di kiri, detail di kanan
**And** membandingkan tidak berarti bolak-balik antara layar (UX-DR13)

**Given** saat ini tengah malam, lewat tengah malam
**When** daftar dibuka
**Then** hari tagih untuk hari yang sudah lewat **sudah dihitung** — angka di layar sama dengan yang dibaca Andi dari internet banking

**Notes:** detail Kontrak perlu menampilkan **hari tagih tertua yang belum tertutup**, bukan cuma "3 hari". Kalau pak Budi ternyata bayar hari Minggu untuk hari Senin, setiap hari angka itu yang menjelaskan tunggakannya — dan itu yang Anda cari saat membuka aplikasi ini.

---

### Story 4.2: Mengirim pengingat WhatsApp dengan angka yang benar

Sebagai pemilik rental, saya ingin mengirim pengingat ke penyewa yang menunggak dengan angka yang sudah benar, sehingga saya tidak perlu menghitung dan tidak salah tagih.

**Acceptance Criteria:**

**Given** baris tunggakan Pak Budi
**When** saya menekan tombol WhatsApp
**Then** pesan terisi otomatis: nama Penyewa, plat Kendaraan, Nama usaha, sisa Tunggakan, dan jumlah hari menunggak
**And** **tidak ada tanggal per-hari** — pesan untuk manusia, bukan untuk audit

**Given** pesan sudah muncul
**When** saya menyuntingnya
**Then** tautan WhatsApp **dibangun ulang dari teks hasil suntingan** — kalau saya mengubah angka, link-nya ikut berubah
**And** ada tombol Salin untuk kasus tanpa tautan

**Given** pesan sudah dikirim
**When** saya kembali ke aplikasi
**Then** **status Tunggakan tidak berubah** — mengirim pesan bukan berarti lunas
**And** **tidak ada riwayat interaksi** yang dicatat

**Given** tunggakan saya ubah, lalu saya buka dialog WhatsApp
**When** pesan ditampilkan
**Then** angkanya **sama persis** dengan yang ada di baris daftar — bukan hasil hitungan kedua yang mungkin berbeda

**Given** dua sumber angka: daftar Tunggakan dan dialog pesan
**When** keduanya dibaca
**Then** keduanya berasal dari **satu pembacaan yang sama**
**And** kalau ada pembatalan Setoran yang jalan di antaranya, nomor yang terkirim **tidak mungkin** berasal dari dua keadaan berbeda

**And** pesan berbahasa Indonesia yang ringkas dan langsung — bukan template kaku (UX-DR14)

**Notes:** nomor tunggakan yang dikirim ke pelanggan adalah satu-satunya angka yang keluar dari aplikasi ke dunia luar. Kalau itu salah, tidak ada undo dan penyewa bisa marah. Karena itu snapshot-nya wajib satu, bukan tiga query digabung.

---

### Story 4.3: Ringkasan pagi di Beranda

Sebagai pemilik rental, saya ingin membuka aplikasi dan langsung melihat kondisi hari ini, sehingga saya tahu harus mengerjakan apa pagi ini.

**Acceptance Criteria:**

**Given** aplikasi dibuka
**When** Beranda tampil
**Then** empat KPI terlihat tanpa perlu menggulir: Pendapatan hari ini, Tunggakan aktif, armada aktif, tingkat penagihan
**And** **keempatnya sama besar, tanpa hierarki** — tidak ada satu KPI yang dibesarkan

**Given** empat KPI ditampilkan
**When** dihitung
**Then** Pendapatan hari ini = jumlah Setoran lunas dengan tanggal hari ini
**And** Tunggakan aktif = total Tunggakan dari Kontrak aktif **dan** yang sudah berakhir dengan sisa
**And** armada aktif = tersewa / tersedia / servis, dihitung dari aturan Story 2.3
**And** tingkat penagihan = Pendapatan Periode ÷ (Pendapatan Periode + Tunggakan aktif), persen bulat, 100% kalau nol

**Given** ada perubahan data
**When** saya menyimpan Setoran
**Then** KPI berubah **segera** — tanpa muat ulang, tanpa berpindah halaman
**And** **tidak ada tombol muat ulang** dan tidak ada angka yang basi

**Given** aplikasi dibuka untuk kedua kalinya dengan input keyboard
**When** saya mengetik
**Then** **tidak ada** sistem invalidasi manual yang berjalan — StateFlow Room sudah otomatis
**And** satu sumber angka dipakai Beranda dan Laporan — keduanya **tidak punya perhitungan sendiri**

**Given** saya di Beranda
**When** saya melihat aksi cepat
**Then** ada 4 aksi: Catat Setoran, Tambah Mobil, Kelola Penyewa, Kirim Pengingat
**And** **speed dial TIDAK muncul di Beranda** — grid aksi adalah satu-satunya pintu dari sini (UX-DR17)

**Given** di layar mana pun selain Beranda
**When** saya membuka speed dial
**Then** isinya **sama persis** dengan grid aksi Beranda — tidak ada aksi yang ada di satu tempat tapi tidak di tempat lain

**Given** tablet di portrait
**When** Beranda tampil
**Then** navigasi bawah dipakai, dan layout tetap berfungsi penuh

**And** setiap aksi cepat punya tujuan yang **sudah ada** — tidak ada tombol yang membuka layar belum ada

**Notes:** KPI tanpa hierarki itu keputusan pemilik: kalau tidak ada yang pertama, tidak perlu ada yang besar. Kalau nanti Anda jadi sering melihat satu yang tertentu, itu perubahan ukuran — bukan perubahan arsitektur.

---

### Story 4.4: Identitas usaha untuk pesan pengingat

Sebagai pemilik rental, saya ingin mengisi nama, alamat, dan telepon usaha saya, sehingga pesan pengingat yang terkirim terlihat berasal dari usaha saya, bukan dari aplikasi.

**Acceptance Criteria:**

**Given** saya membuka Pengaturan
**When** saya mengisi identitas usaha
**Then** saya bisa mengisi Nama usaha, Alamat usaha, dan Telepon usaha
**And** ketiganya tersimpan sebagai satu blok

**Given** Nama usaha
**When** pesan WhatsApp disusun
**Then** Nama usaha muncul di pesan — dari sini, bukan dari teks hardcode di kode

**Given** Alamat usaha dan Telepon usaha
**When** saya menyimpan
**Then** keduanya tersimpan dan **tersedia** untuk dipakai kapan saja
**And** kalau ternyata tidak dipakai di layar mana pun, keduanya tidak muncul di layar mana pun

**Given** konvensi nama usaha
**When** nilai standarnya sudah diisi
**Then** Standarnya **PT Klinik Net Rent Car** — sudah ada di prototipe, dan sekarang bisa diubah, bukan hardcode

**Given** saya mengubah identitas usaha
**When** Setoran lama ditinjau
**Then** Setoran lama **tidak berubah**

**And** Data ini disimpan bersama rekening usaha di tabel `settings`, dan **tidak ikut ekspor** hanya kalau termasuk PIN — identitas usaha justru **ikut** backup, supaya setelah pemulihan pesan WhatsApp tetap menyebut nama yang benar

---

### Story 4.5: Laporan periode bulanan

Sebagai pemilik rental, saya ingin melihat rekap satu periode bulanan, sehingga saya bisa memutuskan apakah ada yang perlu ditagih minggu depan atau apakah cukup untuk menambah armada.

**Acceptance Criteria:**

**Given** saya membuka Laporan
**When** memilih periode
**Then** saya melihat total Setoran masuk, total Tunggakan akhir periode, dan jumlah Penyewa lunas

**Given** Setoran di luar periode berjalan
**When** periode berganti
**Then** Setoran tanggalnya **ikut** periode itu — yang terlihat adalah tanggal yang dicatat, bukan tanggal pembukuan
**And** hari setelah hari ini tidak menambah Tunggakan — periode di masa depan tidak dihitung

**Given** Laporan menampilkan Tunggakan
**When** periode dipilih berbeda dari Beranda
**Then** angka berasal dari **fungsi yang sama** dengan Beranda, dengan parameter titik waktu berbeda
**And** bukan dua fungsi terpisah yang bisa menyimpang

**Given** Laporan menampilkan tren
**When** 7 hari terakhir ditampilkan
**Then** **grafik garis** digambar dengan `Canvas` — tanpa library chart, tanpa dependency tambahan
**And** warnanya otomatis mengikuti token Material 3

**Given** Laporan menampilkan komposisi Tunggakan
**When** ditampilkan
**Then** **empat angka dengan warna** (Lancar / Ringan / Perhatian / Kritis) — **bukan donut**
**And** justify: empat angka berwarna lebih cepat dibaca daripada diagram, dan tidak butuh dependency

**And** grafik drawn di `Canvas` — nol dependency, ~60 baris, dan tidak bisa "rot" (AR-15)

**Notes:** tren 7 hari dipakai; komposisi risiko memakai angka. Donut diabaikan karena keempat angka berwarna sudah lebih cepat dibaca — dan setiap dependency adalah sesuatu yang bisa rusak sendirian.

---

### Story 4.6: Pencarian global

Sebagai pemilik rental, saya ingin mencari penyewa atau mobil dari mana saja, sehingga saya tidak perlu menggulir daftar untuk menemukan satu orang.

**Acceptance Criteria:**

**Given** saya di layar mana pun
**When** saya mengetik di kolom pencarian
**Then** saya bisa mencari berdasarkan **nama Penyewa** dan **plat Kendaraan**
**And** pencocokan sebagian teks, tidak membedakan huruf besar-kecil

**Given** hasil pencarian
**When** tampil
**Then** dikelompokkan per jenis — Penyewa dan Kendaraan terpisah
**And** memilih hasil membuka layar yang sesuai

**Given** saya mengetik di kolom pencarian
**When** hasilnya tidak ada
**Then** empty state yang menjelaskan — bukan layar kosong

**Given** kolom pencarian
**When** diketik ke dalamnya
**Then** **tidak** mencari di catatan, Kode transaksi, atau nomor Setoran
**And** hasil kosong adalah jawaban yang valid — searches di catatan butuh full-text search dan tidak sebanding dengan yang sederhana

**And** kolom pencarian tersedia dari **setiap** layar, persis seperti di prototipe
**And** pada 14 penyewa dan 18 mobil, daftar pendeknya masih bisa digulir — pencarian adalah kenyamanan, bukan pengganti

**Notes:** FR-19 di PRD menyebut mencari "Penyewa, Kendaraan, **dan Kontrak**". Kontrak tidak punya field nama atau plat sendiri, jadi ia tidak bisa dicari tanpa mencari lewat relasinya — itu lebih lambat dan tidak sebanding dengan nilainya. Kontrak **tidak** menjadi target pencarian; ini penyimpangan yang disengaja dan tercatat.

---

## Epic 5: Data yang selamat

Andi bisa mencadangkan dan memulihkan data, mengunci aplikasi dengan PIN yang punya jalan keluar kalau lupa, dan mengubah tanggal cutoff dengan melihat dampaknya dulu.

Deliberately last: FR-16 mengekspor skema yang Epic 2 dan 3 ciptakan. **Sebelum itu ada, tidak ada yang bisa diekspor** — jadi lebih benar tersisa di akhir daripada jadi prioritas yang tidak bisa jalan.

Tapi: **sampai Epic 5 selesai, aplikasi adalah satu-satunya salinan catatan uang Andi tanpa jalan keluar.** Itu diketahui dan diterima — tapi kalau hanya satu epic yang bisa dijadwalkan minggu ini, ini yang harus lebih dulu.

**FR:** FR15b, FR16, FR17 · **NFR:** NFR2, NFR3, NFR5 · **AR:** AR-13, AR-14, AR-17

---

### Story 5.1: Mengekspor seluruh data ke satu berkas

Sebagai pemilik rental, saya ingin bisa menyalin seluruh data aplikasi ke berkas saya sendiri, sehingga catatan uang saya tidak hanya ada di satu perangkat.

**Acceptance Criteria:**

**Given** saya membuka Pengaturan
**When** saya menekan "Ekspor data"
**Then** sistem file terbuka dan saya memilih lokasi penyimpanan
**And** saya **tidak** perlu izin penyimpanan manual — aplikasi memakai Storage Access Framework
**And** **Tidak ada `MANAGE_EXTERNAL_STORAGE` atau `READ_EXTERNAL_STORAGE` di manifest

**Given** ekspor berjalan
**When** selesai
**Then** menghasilkan **satu** berkas JSON berisi Kendaraan, Penyewa, Kontrak, Setoran, dan Pengaturan
**And** nama berkas memuat tanggal ekspor, agar beberapa cadangan bisa dibedakan
```
klinik-net-backup-2026-10-13.json
```

**Given** sudah ada beberapa cadangan
**When** saya mengekspor lagi
**Then** nama berkas berbeda — tidak ada yang tertimpa diam-diam

**Given** ekspor selesai
**When** saya mengecek
**Then** data di aplikasi **tetap utuh** — ekspor tidak menghapus apa pun

**Given** berkas ekspor dibuka
**When** diperiksa
**Then** memuat nomor versi skema di root — dasar untuk NFR-3

**Given** PIN dan kode pemulihan aktif
**When** ekspor berjalan
**Then** hash PIN dan hash kode pemulihan **tidak ikut** ke berkas
**And** seluruh data lain, termasuk identitas usaha dan rekening, **tetap ikut**

**And** prosesnya tidak butuh internet — tidak ada unggahan ke mana pun (NFR-4)

**Notes:** keputusan **manual, bukan otomatis** — pemilik yang memutuskan kapan. Bukan WorkManager dengan file harian: itu menambah dependency dan menyimpan file tanpa pemilik tahu ada di mana. Story 5.6 ditambah pengingat sebagai gantinya, supaya "terlupa" tidak terjadi tanpaIam biaya otomatis.

---

### Story 5.2: Memulihkan data dari cadangan

Sebagai pemilik rental, saya bisa memulihkan seluruh data dari berkas cadangan, sehingga data saya kembali utuh di perangkat baru.

**Acceptance Criteria:**

**Given** saya membuka Pengaturan
**When** saya memilih "Pulihkan dari cadangan"
**Then** sistem file terbuka dan saya memilih berkas
**And** aplikasi menampilkan **pratinjau sebelum menimpa** — jumlah Kendaraan, Penyewa, Kontrak, Setoran, dan periode terakhir
```
18 mobil · 14 penyewa · 312 setoran
Periode terakhir: 24 Mar 2026
```

**Given** pratinjau sudah tampil
**When** saya membatalkannya
**Then** **tidak ada yang berubah** — data lama tetap utuh

**Given** saya memilih berkas yang salah (misal cadangan bulan lalu)
**When** pratinjau muncul
**Then** saya bisa membatalkan tanpa efek samping
**And** Pemulihan adalah **satu-satunya** operasi yang menimpa, dan selalu punya konfirmasi eksplisit

**Given** pemulihan berjalan
**When** selesai
**Then** seluruh data kembali utuh — bukan hanya Mobil dan Penyewa, tapi juga riwayat Setorannya

**Given** PIN aktif sebelum pemulihan
**When** pemulihan selesai
**Then** `pinEnabled` kembali ke **false** — hash PIN tidak ikut dipulihkan
**And** aplikasi **jelas meminta** mengatur PIN baru, dengan flow yang jelas

**Given** kode pemulihan di luar aplikasi
**When** PIN baru sedang diatur
**Then** kode pemulihan **baru** dibuat dan ditampilkan sekali — kode lama tidak berlaku lagi

**Given** berkas versi skema yang lebih baru dari aplikasi
**When** saya memilihnya
**Then** aplikasi **menolak dengan pesan yang bisa dipahami**
**And** **tidak** gagal parsing yang menghapus data yang sudah ada

**Given** data yang tersimpan rusak
**When** aplikasi dijalankan
**Then** aplikasi **gagal dengan pesan yang jelas**, bukan me-reset ke default
**And** data kosong **tidak pernah** muncul dengan tampilan seperti akun baru

**And** pemulihan memuat **Nomor** versi skema; berkas versi berbeda ditolak, bukan dicoba dipaksa (NFR-3)

**Notes:** keputusan "PIN tidak ikut ekspor" punya konsekuensi yang harus dinyatakan terbuka: setelah pemulihan, aplikasi **tidak** terkunci. Ini dipilih sadar — kalau hash ikut ekspor, ia akan tersebar ke setiap salinan berkas, dan itu persis kebocoran yang NFR-5 larang.

---

### Story 5.3: Mengunci aplikasi dengan PIN dan kode pemulihan

Sebagai pemilik rental, saya ingin mengunci aplikasi dan tetap punya jalan keluar kalau lupa PIN, sehingga catatan uang saya tidak terbuka untuk siapa pun yang menemukan tablet.

**Acceptance Criteria:**

**Given** saya membuat PIN di Pengaturan
**When** PIN dibuat
**Then** aplikasi **membuat satu kode pemulihan** — alphanumeric, **minimal 20 karakter**, bukan angka
**And** kode ditampilkan **sekali**, lalu disembunyikan selamanya

**Given** kode pemulihan sudah ditampilkan
**When** flow PIN selesai
**Then** aplikasi **meminta konfirmasi** bahwa kode sudah dicatat di luar aplikasi
**And** flow tidak dianggap selesai sebelum itu dikonfirmasi

**Given** kode pemulihan saya hilang
**When** saya lupa PIN
**Then** saya bisa memasukkan kode pemulihan untuk mengatur **PIN baru**
**And** kode pemulihan baru dibuat, kode lama tidak berlaku

**Given** PIN salah
**When** sudah 5 kali percobaan gagal beruntun
**Then** kunci layar **menolak input** dan menampilkan hitung mundur
**And** ini adalah kontrol yang benar untuk PIN 4-digit — bukan hash mahal

**Given** PIN sudah dibuat
**When** PIN disimpan
**Then** **tidak disimpan** dalam bentuk yang bisa dibaca
**And** **tidak muncul** di log, backup ekspor, atau pesan galat

**Given** saya berada di aplikasi
**When**durasi kunci otomatis (1/5/15/30 menit, default 5) terlalu
**Then** aplikasi terkunci lagi saat dibuka kembali

**Given** proses aplikasi mati atau perangkat restart
**When** aplikasi dibuka kembali
**Then** kunci otomatis tetap berlaku — dihitung dari **timestamp absolut** saat masuk background, bukan counter yang reset

**Given** aplikasi terkunci
**When** PIN dimasukkan salah
**Then** ada getaran dan Shake 420ms — sekali, hanya saat benar-benar salah

**Given** aplikasi terbuka
**When** PIN diaktifkan
**Then** copy dokumentasi dan UI **menyatakan** PIN sebagai penghalang, bukan keamanan
**And** copy prototipe "data tersimpan lokal dan terenkripsi" **tidak boleh** dipakai ulang

**And** enkripsi dengan **PBKDF2WithHmacSHA1** + salt acak — bukan bcrypt atau Argon2, yang tidak ada di Android SDK tanpa native library (AR-14)

**Notes:** entropi kode pemulihan **harus** jauh di atas PIN. Kalau kodenya 4–6 digit, itu hanya PIN dengan langkah tambahan — dan bisa ditebak. 20 karakter alphanumeric memberi ~119 bit entropi, cukup sehingga bocor ke file backup tidak bisa di-*brute force* di luar aplikasi.

---

### Story 5.4: Mengubah tanggal cutoff dengan melihat dampaknya

Sebagai pemilik rental, saya ingin mengubah tanggal tagihan bulanan, sehingga tagihan saya sesuai dengan cara saya mencatat.

**Acceptance Criteria:**

**Given** saya membuka Pengaturan
**When** saya mengubah Tanggal cutoff
**Then** saya bisa memilih tanggal 1–28
**And** diubah ke **semua** Tunggakan dihitung ulang

**Given** saya mengubah cutoff
**When** dialog pratinjau tampil
**Then** menampilkan berapa Kontrak dan berapa rupiah Tunggakan yang berubah
**And** pratinjau ini **wajib**, bukan opsional — mengganti cutoff dapat mengubah angka tagihan secara signifikan

**Given** cutoff diubah
**When** dihitung ulang
**Then** **tidak ada baris alokasi yang dihapus atau ditulis ulang**
**And** hanya rentang `chargeDate` yang dihitung yang berubah — alokasi tetap fakta (Story 1.5)

**Given** saya berubah pikiran saat melihat pratinjau
**When** saya menekan Batal
**Then** cutoff **tidak** berubah

**And** perubahan cutoff ditulis dalam satu transaksi

---

### Story 5.5: Mengingat kalau backup belum dibuat

Sebagai pemilik rental, saya ingin diingatkan kalau belum mencadangkan data hari ini, sehingga saya tidak lupa dan kehilangan catatan.

**Acceptance Criteria:**

**Given** hari ini sudah lewat dan saya **belum** mengekspor hari ini
**When** aplikasi dibuka
**Then** muncul pengingat di Beranda — kecil, tidak menghalangi
**And** isinya menyebut tanggal ekspor terakhir, kalau ada

**Given** saya sudah mengekspor hari ini
**When** aplikasi dibuka lagi
**Then** pengingat **tidak muncul**

**Given** saya menekan pengingat
**When** dialog konfirmasi tampil
**Then** menjelaskan **mengapa** ini penting: ini satu-satunya salinan data
**And** mengarahkan ke ekspor, tanpa memaksa — saya yang memutuskan

**Given** ekspor selesai
**When** tanggal hari ini tercatat
**Then** tanggal ekspor terakhir diperbarui

**Notes:** ini gantinya **WorkManager dengan ekspor harian otomatis** — yang ditolak karena menambah dependency dan menyimpan file tanpa pemilik tahu ada di mana. Pengingat manual lebih lambat, tapi pemilik tetap memegang kendali.

---

### Story 5.6: Pemulihan dan PIN diuji bersama-sama

Sebagai developers, saya ingin memastikan pemulihan dan kunci PIN tidak saling merusak, karena keduanya menyentuh hal yang sama.

**Acceptance Criteria:**

**Given** aplikasi dengan PIN aktif dan data lengkap
**When** saya ekspor, lalu memulihkan berkas itu
**Then** seluruh data kembali utuh
**And** aplikasi **tidak** terkunci
**And** PIN bisa diatur baru dengan kode pemulihan baru

**Given** berkas ekspor
**When** isinya diperiksa
**Then** **tidak ada** hash PIN, **tidak ada** kode pemulihan, **tidak ada** PIN dalam bentuk apa pun
**And** seluruh data bisnis **ada** — tidak ada yang hilang karena penyaringan

**Given** aplikasi dijalankan
**When** PIN aktif dan durasi kunci otomatis berjalan
**Then** aplikasi terkunci lagi setelah durasi, termasuk setelah proses mati

**Given** pemulihan dari berkas versi skema lebih lama
**When** dipulihkan
**Then** dipulihkan dengan benar, dengan migrasi bila diperlukan
**And** dari versi lebih baru — ditolak dengan pesan jelas

**Notes:** ini story verifikasi lintas-fitur, karena bug paling mungkin di sini adalah interaksi antara "PIN tidak ikut ekspor" dan "pemulihan harus mengembalikan data" — dua aturan yang menarik ke arah berlawanan dan bisa salah diam-diam.
**And** CUSTOMER dengan Kontrak baru untuk Kendaraan yang sama bisa langsung dibuka setelah ini

Sebagai developers, saya ingin performa hitung ulang diukur dengan angka nyata, sehingga NFR-1 punya bukti dan tidak bisa gagal diam-diam.

**Acceptance Criteria:**

**Given** 20 Kontrak dengan total 2.000 Setoran dan 500 hari tagih
**When** Tunggakan dihitung ulang untuk seluruh Kontrak
**Then** waktu kalkulasi selesai **di bawah 50 milidetik**
**And** angka hasil dicatat sebagai baseline di dokumen epic

**Given** hasil benchmark melampaui 50ms di perangkat
**And** ambang dievaluasi
**Then** threshold **diturunkan atau diabaikan secara eksplisit dan tercatat** — **tidak boleh dihapus diam-diam** (NFR-1: jangan mengorbankan kebenaran demi kecepatan)

**Given** perangkat uji Redmi Pad 2 (atau proyektor yang setara)
**When** test performa dijalankan di mode debug
**Then** angka di atas hanya dipakai sebagai **baseline relatif antar-commit**, bukan sebagai klaim performa produksi
**And** test tetap hijau di mesin build tanpa perangkat, dengan target yang lebih longgar dan ditandai eksplisit

**And** Tujuh kasus `verify-fr10.md` **semua hijau** sebelum epic ini dinyatakan selesai

**Notes:** ini harus jadi test, bukan catatan. NFR-1 Cells "50 milidetik" tanpa test adalah MBT-3FRANGKA itu party mode temukan: performa bisa gagal tanpa gagal test mana pun. Benchmark di perangkat nyata adalah kondisi yang berbeda dari test CI — keduanya perlu, dan perbedaannya harus dinyatakan, bukan disembunyikan.

---

## Lubang yang Masih Terbuka

1. ~~**NFR-1 "50 milidetik" tidak punya test.**~~ **SUDAH DIPUTUSKAN.** Masuk Story 1.6 sebagai test benchmark yang mengukur, bukan menebak. Angka target 50ms dipertahankan dari NFR-1 — kalau gagal di perangkat, threshold itu diturunkan bersama, bukan dihapus.

2. ~~**Apakah alokasi diturunkan ulang saat cutoff berubah?**~~ **SUDAH DIPUTUSKAN — DIGESER.** Alokasi adalah fakta. `cutoffDay` hanya mengubah rentang `chargeDate` yang dihitung, **tidak pernah menghapus atau menulis ulang baris `allocation`**. Konsisten dengan AD-3 yang melarang hitung-ulang-dari-awal. Lihat Story 1.5.

3. **Kendaraan `servis` dengan Kontrak aktif — apa yang terjadi?** **MASIH TERBUKA.** Tidak didefinisikan di PRD, arsitektur, maupun prototype. Menentukan apakah Andi ditagih untuk mobil yang di bengkel. Masuk Epic 2 sebagai keputusan yang diambil **sebelum** kolom status ditulis.

