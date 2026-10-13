---
title: "Klinik.Net — Sistem Rental Mobil Harian"
status: final
created: 2026-10-13
updated: 2026-10-13
---

# PRD: Klinik.Net — Sistem Rental Mobil Harian

> **Nama produk** — "Klinik.Net" adalah nama yang dipakai pada prototipe dan identitas visual. [ASSUMPTION: Klinik.Net adalah nama usaha rental, bukan nama perangkat lunak. Kalau ternyata nama aplikasi, tidak ada dampaknya ke scope.]

---

## 0. Tujuan Dokumen

PRD ini untuk pemilik usaha rental mobil yang sedang membangun aplikasi Android untuk penggunaan pribadi, dan untuk seluruh alur hilir (UX, arsitektur, epics dan stories).

Dokumen ini **membangun di atas prototipe HTML yang sudah ada** di `prototype/` — ia tidak mengulanginya. Prototipe menjadi bukti bentuk layar; PRD ini menjadi sumber kebenaran untuk **apa yang harus dibangun, dan apa yang salah di prototipe**.

Istilah domain di §3 dipakai persis seperti tertulis di seluruh dokumen ini. Asumsi yang belum dikonfirmasi ditandai `[ASSUMPTION: ...]` dan dikumpulkan di §9.

---

## 1. Visi

Klinik.Net adalah buku besar digital untuk usaha rental mobil harian dengan skala di bawah 20 kendaraan. Satu orang — pemiliknya — setiap pagi memeriksa mutasi di internet banking, lalu mencatat setoran satu per satu. Prosesnya lambat, dan yang lebih penting, hasilnya berantakan: tabel yang menumpuk, angka yang tidak bisa dipercaya, dan tidak ada cara cepat melihat siapa yang belum bayar.

Aplikasi ini menggantikan catatan manual itu dengan **satu layar yang sudah dirapikan** — dashboard yang langsung menampilkan pendapatan hari ini, sisa tunggakan, dan siapa yang perlu dikejar. Tidak ada akun, tidak ada server, tidak ada internet yang dibutuhkan. Semua data tinggal di tablet, dan bisa disalin keluar kapan saja sebagai satu berkas.

Ini bukan aplikasi kasir, dan bukan produk untuk penyewa. **Penyewa tidak pernah menyentuh aplikasi ini.** Pembayaran tetap terjadi lewat transfer bank atau tunai seperti sekarang; perannya hanya mencatat, memperbaiki, dan menghitung apa yang sudah terjadi.

Masalah yang sebenarnya bukan lupa mencatat, melainkan data yang berantakan sampai sulit dipakai mengambil keputusan. Karena itu aplikasi ini diukur dari apakah angkanya bisa dipercaya, bukan dari seberapa cepat bisa mengetik.

---

## 2. Pengguna Sasaran

### 2.1 Jobs To Be Done

- **Ketika** paginya tiba dan ada transfer masuk, **maka saya ingin** mencatat setiap setoran dalam langkah singkat, **agar** rekap harian selesai tanpa membuka Excel sama sekali.
- **Ketika** saya perlu mengejar tunggakan, **maka saya ingin** melihat siapa yang menunggak, berapa, dan sudah berapa hari, **agar** saya tahu siapa yang harus dihubungi hari ini.
- **Ketika** ada penyewa yang membayar lebih dari satu hari sekaligus, **maka saya ingin** uang itu otomatis dianggap sebagai pembayaran hari-hari yang belum tertagih, **agar** saya tidak menagih orang yang sudah bayar.
- **Ketika** saya butuh memutuskan apakah bisa membeli mobil baru, **maka saya ingin** melihat posisi kas dan arus uang masuk, **agar** keputusan modal diambil dari angka, bukan perkiraan.
- **Ketika** tablet saya hilang atau rusak, **maka saya ingin** seluruh riwayat bisa dipulihkan dari satu berkas cadangan, **agar** catatan uang tidak hilang permanen.

### 2.2 Bukan Pengguna (v1)

- **Penyewa** — tidak ada akses, tidak ada akun, tidak ada notifikasi dalam aplikasi. Interaksi dengan penyewa tetap lewat WhatsApp.
- **Karyawan atau admin lain** — belum ada konsep multi-pengguna. Kalau nanti dibutuhkan, itu penambahan tersendiri.
- **Akuntan atau pemeriksa** — laporan bersifat internal, bukan dokumen yang diserahkan ke pihak lain.

### 2.3 Perjalanan Pengguna Utama

> Protagonis: **Andi** — pemilik usaha rental, 18 mobil aktif dan 14 penyewa. Nama ini placeholder; ganti dengan nama yang dipakai keseharian.

**UJ-1. Andi mencatat setoran pagi itu.**

- **Persona dan konteks:** Andi, pemilik rental. Pagi buta, di meja depan, sebelum buka warung. Tablet kedua sudah login ke internet banking.
- **Entry state:** baru menyalakan tablet. Aplikasi masih terkunci PIN.
- **Path:** membuka aplikasi, layar Beranda tampil dengan empat angka: pendapatan hari ini, tunggakan aktif, armada aktif, tingkat penagihan. Ia menekan "Catat Setoran", memilih penyewa Budi, lalu memilih mobil yang Budi sewa. Nominal sudah terisi dari tarif harian; ia membiarkannya. Memilih Rekening usaha, menekan Simpan. Ada empat setoran pagi itu, jadi ia mengulangi tiga kali lagi.
- **Climax:** setelah setoran keempat, angka "Pendapatan hari ini" langsung bertambah dan tunggakan Budi berkurang, tanpa memuat ulang dan tanpa menekan apa pun. Di situ Andi tahu rekap pagi sudah selesai.
- **Resolution:** ia membuka daftar Tunggakan dan melihat Pak Budi 3 hari, Pak Agus 1 hari. Ia sudah tahu siapa yang harus dihubungi.
- **Edge case:** hari itu ada transfer masuk atas nama orang lain — misalnya atas nama istri Budi. Dari internet banking ia tahu itu milik Budi, jadi ia memilih Budi dan menambah catatan "atas nama istri". Setoran itu tetap dihitung sebagai pembayaran Budi.

**UJ-2. Andi mengejar tunggakan dan mengirim pengingat.**

- **Persona dan konteks:** Andi, dua hari setelah setoran terakhir Pak Budi. Budi bilang akan bayar hari Senin, tapi hari itu lewat.
- **Entry state:** aplikasi terbuka, sedang di Beranda.
- **Path:** membuka layar Tunggakan. Pak Budi ada di teratas, ditandai "3-6 Hari (Perhatian)", nominal yang tampil sudah mencakup semua hari yang belum dibayar. Ia menekan tombol WhatsApp, muncul pesan siap kirim yang menyebut nama, plat mobil, sisa tunggakan, dan jumlah hari menunggak. Ia menyunting sedikit, lalu menekan "Buka WhatsApp".
- **Climax:** pesan terkirim dalam dua ketukan, dengan angka yang benar. Tidak perlu menghitung, tidak perlu membuka Excel.
- **Resolution:** WhatsApp terbuka dengan pesan terisi. Tunggakan Budi tetap tercatat — tidak ada perubahan status hanya karena pesan dikirim.
- **Edge case:** Andi baru ingat bahwa Pak Budi sebenarnya bayar hari Minggu untuk hari Senin. Ia membuka menu Ubah pada setoran Minggu itu dan mengoreksi tanggalnya menjadi Senin. Tunggakan Budi langsung hilang.

**UJ-3. Andi memindahkan uang untuk modal.**

- **Persona dan konteks:** Andi, akhir bulan. Mobil kedua jatuh tempo, dan ia perlu tahu apakah ada modal untuk Kendaraan tambahan.
- **Entry state:** aplikasi terbuka di Beranda.
- **Path:** membuka Laporan, memilih periode bulan berjalan. Terlihat tiga angka: total setoran masuk bulan ini, total tunggakan akhir periode, jumlah penyewa yang lunas penuh.
- **Climax:** dalam satu layar, ia punya gambaran kas masuk dan Kewajibannya. Keputusan diambil di tempat itu.
- **Resolution:** ia pulang dengan tahu apakah ada yang perlu ditagih minggu depan.
- **Edge case:** ia memutuskan menambah satu mobil baru. Angka ini **tidak** dicatat di aplikasi — pembelian mobil, cicilan, dan biaya servis masih di luar sistem (lihat §6.2).

**UJ-4. Andi memulihkan data.**

- **Persona dan konteks:** Andi, tabletnya hilang tertinggal di angkot. Ia punya satu berkas cadangan di laptop.
- **Entry state:** tablet baru, aplikasi baru terpasang, data kosong.
- **Path:** membuka Pengaturan, memilih "Pulihkan dari cadangan", memilih berkas JSON. Aplikasi menampilkan ringkasan: 18 mobil, 14 penyewa, 312 setoran, periode terakhir 2026-03-24. Ia menekan "Pulihkan".
- **Climax:** seluruh datanya kembali utuh — bukan hanya mobil dan penyewa, tapi juga riwayat setorannya.
- **Resolution:** aplikasi kembali berguna di hari yang sama.
- **Edge case:** ia memilih berkas yang salah, yaitu cadangan bulan lalu. Aplikasi menampilkan pratinjau **sebelum** menimpa, dan menyediakan tombol Batal.

---

## 3. Glosarium

Istilah berikut dipakai persis seperti tertulis di seluruh dokumen ini. Sinonim tidak boleh muncul di FR, UJ, atau SM.

- **Setoran** — satu catatan pembayaran yang diterima dari penyewa, lengkap dengan tanggal, nominal, Metode Pembayaran, dan catatan opsional.
- **Metode Pembayaran** — cara Setoran diterima: Transfer Bank atau Tunai.
- **Transfer Bank** — Setoran yang diterima lewat transfer ke rekening usaha. Diperoperasi melalui internet banking, bukan lewat aplikasi.
- **Tunai** — Setoran yang diterima langsung di tangan. Tidak ada mutasi bank yang bisa diperiksa.
- **Penyewa** — orang yang menyewa Kendaraan. Identitasnya di dunia nyata ditentukan oleh nama yang muncul sebagai nama pengirim di mutasi bank, sehingga nama Penyewa adalah data wajib.
- **Kendaraan** — mobil dalam armada, dengan plat, merek, tipe, tahun, warna, dan tarif harian. Diidentifikasi oleh plat nomor yang wajib unik.
- **Kontrak** — ikatan sewa antara satu Penyewa dengan satu Kendaraan. Memiliki tarif harian sendiri, jadwal kerja, dan daftar pengecualian.
- **Periode cutoff** — rentang tagihan bulanan, dari Tanggal cutoff sampai satu hari sebelum Tanggal cutoff bulan berikutnya. Default: 25 sampai 24.
- **Tanggal cutoff** — tanggal yang menentukan awal periode tagihan. Default 25.
- **Hari kerja** — hari yang ditagih. Untuk Kontrak 6 hari, Minggu tidak ditagih. Untuk Kontrak 7 hari, semua hari ditagih.
- **Pengecualian Servis** — tanggal tertentu yang tidak ditagih pada Kontrak tertentu, misalnya Kendaraan sedang diservis.
- **Hari tagih** — Hari kerja yang jatuh di dalam Periode cutoff, sejak tanggal mulai Kontrak.
- **Kewajiban** — total nilai Hari tagih dalam Periode cutoff berjalan, untuk satu Kontrak.
- **Pool Setoran** — jumlah seluruh Setoran lunas pada satu Kontrak yang masuk Periode cutoff berjalan, dijumlahkan menjadi satu angka.
- **Tunggakan** — selisih antara Kewajiban dan Pool Setoran, minimal nol.
- **Kredit** — sisa Pool Setoran setelah Tunggakan mencapai nol. Kredit mengurangi Kewajiban periode berikutnya, bukan sekadar angka yang tersimpan.
- **Menunggak (hari)** — jumlah Hari tagih yang belum tertutup oleh alokasi Setoran. Ini hitungan hari, bukan pembagian nominal.
- **Kode transaksi** — nomor referensi dari mutasi bank, dicatat bebas sebagai pengingat. Tidak divalidasi terhadap bank.
- **Rekening usaha** — rekening milik usaha yang menerima Setoran. Dikelola di Pengaturan (FR-18), dipilih saat mencatat Setoran dengan Metode Pembayaran Transfer Bank.
- **Nama usaha** — nama badan usaha pemilik, muncul di Pengaturan. Dipakai di pesan pengingat WhatsApp (FR-12).
- **Tunggakan aktif** — total Tunggakan dari seluruh Kontrak yang sedang berjalan maupun yang sudah berakhir dengan sisa tagihan.

---

## 4. Fitur

### 4.1 Setoran

**Deskripsi:** Ini inti aplikasi — satu-satunya tempat Andi memasukkan data setiap hari. Realizes UJ-1.

Andi selalu sudah tahu sebuah Setoran valid **sebelum** membuka aplikasi: ia mencocokkan nama pengirim di internet banking, lalu mengonfirmasi lewat WhatsApp bila transfer atas nama orang lain. Aplikasi tidak perlu, dan tidak boleh, berintegrasi dengan bank.

Untuk **Tunai**, rekonsiliasi itu tidak ada. Tidak ada mutasi bank yang bisa dicek, sehingga kebenaran datang dari kehati-hatian pemilik, bukan dari aplikasi.

Bentuknya harus memungkinkan jalur tercepat untuk kasus paling sering: satu hari, satu hari penuh. Memilih Penyewa mengisi otomatis Kendaraan dan tarif harian; nominal sudah terisi dan bisa diubah untuk pembayaran sebagian.

**Kebutuhan Fungsional:**

#### FR-1: Mencatat Setoran

Andi dapat mencatat satu Setoran dalam satu langkah layar, dengan memilih Penyewa, Kendaraan, tanggal, nominal, Metode Pembayaran, dan catatan opsional. Realizes UJ-1.

**Konsekuensi (teruji):**
- Memilih Penyewa yang memiliki Kontrak aktif mengisi otomatis Kendaraan dan tarif harian. Andi cukup mengubah nominal jika pembayaran sebagian.
- Setelah Simpan, angka Pendapatan hari ini dan Tunggakan terkait berubah tanpa memuat ulang dan tanpa berpindah halaman.
- Kode transaksi dan catatan bersifat opsional. Tanpa keduanya, Setoran tetap valid.
- Setoran dapat dicatat dengan tanggal di masa lalu untuk koreksi.
- Sebelum Simpan, dialog menampilkan pratinjau: Tunggakan sebelum, Tunggakan sesudah, dan Kredit yang tersisa. Menyimpan tanpa melihat efeknya bertentangan dengan tujuan aplikasi, jadi pratinjau ini bukan tambahan opsional.

#### FR-1b: Memilih Metode Pembayaran

Setoran dicatat dengan salah satu dari dua Metode Pembayaran: Transfer Bank atau Tunai. Metode Pembayaran menentukan field mana yang relevan dan apakah input tambahan diperlukan. Realizes UJ-1.

**Konsekuensi (teruji):**
- **Transfer Bank** — Andi memilih Rekening usaha dari daftar yang dikonfigurasi di Pengaturan. Kode transaksi lebih berguna di sini karena bisa disalin langsung dari mutasi bank.
- **Tunai** — tidak ada Rekening usaha dan tidak ada Kode transaksi. Field-nya disembunyikan, bukan diisi kosong. Mengisi kosong menciptakan ilusi data yang padahal tidak ada.
- Daftar Rekening usaha dikelola di Pengaturan, bukan diketik bebas setiap kali. Mengetik ulang nomor rekening setiap hari adalah sumber salah ketik yang tidak perlu.
- Setoran Tunai tidak punya sumber eksternal yang bisa memverifikasinya. Kebenarannya sepenuhnya bergantung pada pencatatan pemilik. Ini konsekuensi yang tidak bisa diatasi dengan desain apa pun.
- Setoran tidak dapat diubah Metode Pembayarannya setelah disimpan. Memperbaiki metode berarti membatalkan dan mencatat ulang — konsistensi data lebih berharga dari kemudahan.

#### FR-2: Membatalkan Setoran

Andi dapat membatalkan Setoran yang sudah tercatat, dengan menyebutkan alasannya. Setoran yang dibatalkan tidak lagi mengurangi Tunggakan, tetapi tetap terlihat pada riwayat. Realizes UJ-2.

**Konsekuensi (teruji):**
- Pembatalan wajib menyertakan alasan. Aplikasi menolak pembatalan tanpa alasan.
- Setelah dibatalkan, Tunggakan untuk Kontrak terkait dihitung ulang dan naik sesuai nilai Setoran yang dibatalkan.
- Setoran yang dibatalkan dapat dipulihkan tanpa mengetik ulang.

#### FR-3: Memperbaiki Setoran yang Salah Input

Andi dapat mengubah tanggal, nominal, Rekening usaha, catatan, dan Kode transaksi pada Setoran yang sudah tercatat. Metode Pembayaran sendiri tidak dapat diubah. Realizes UJ-2.

**Konsekuensi (teruji):**
- Perubahan tanggal atau nominal langsung memengaruhi Tunggakan, dihitung ulang seketika.
- Riwayat perubahan tidak menyimpan versi lama. Satu Setoran hanya punya satu nilai terkini.

**NFR khusus fitur:**
- Penghapusan permanen Setoran tidak disediakan, hanya pembatalan, agar jejaknya tetap ada. [ASSUMPTION: hard delete tidak dibutuhkan; pembatalan dengan alasan sudah cukup untuk kebutuhan audit Andi.]

---

### 4.2 Kendaraan

**Deskripsi:** Kendaraan adalah aset yang Andi sewakan. Setiap Kendaraan punya plat unik, tarif harian, dan status yang mencakup apakah sedang tersewa, tersedia, atau masuk servis. Realizes UJ-3.

**Kebutuhan Fungsional:**

#### FR-4: Kelola Kendaraan

Andi dapat menambah, mengubah, dan melihat Kendaraan beserta plat, merek, tipe, tahun, warna, tarif harian, dan catatan.

**Konsekuensi (teruji):**
- Plat nomor wajib dan unik. Penolakan berlaku setelah normalisasi: huruf besar, spasi dirapatkan.
- Kendaraan yang sedang disewa tidak bisa dihapus. Kontraknya harus diselesaikan lebih dulu.
- Tarif harian harus lebih besar dari nol. Nilai nol atau negatif ditolak, bukan diganti dengan nilai bawaan.
- Mengubah tarif harian Kendaraan tidak mengubah Kontrak yang sudah berjalan. [ASSUMPTION: tarif tidak boleh naik di tengah Kontrak. Inilah alasan Kontrak memiliki salinan tarif sendiri.]

#### FR-5: Status dan Ketersediaan Kendaraan

Andi dapat menandai Kendaraan sebagai tersewa, tersedia, atau servis, dan melihat berapa unit di tiap kategori.

**Konsekuensi (teruji):**
- Menandai Kendaraan sebagai tersewa tidak membuat Kontrak. Kontrak harus dibuat terpisah lewat Penyewa.
- Status adalah informasi tampilan, bukan sumber kebenaran. Yang menentukan adalah Kontrak. [ASSUMPTION: prototipe memakai konvensi ini; implementasi tetap harus mencegah dua Kontrak aktif pada satu Kendaraan.]

---

### 4.3 Penyewa dan Kontrak

**Deskripsi:** Di sinilah hubungannya dengan dunia nyata terjalin. Andi mengidentifikasi Setoran dengan mencocokkan nama pengirim di internet banking — itu sebabnya nama Penyewa adalah data wajib, bukan opsional. Kontraklah yang menentukan berapa yang harus dibayar dan hari mana yang ditagih. Realizes UJ-1, UJ-2.

**Penting:** prototipe belum memiliki operasi tambah atau ubah untuk Penyewa maupun Kontrak — kedua halaman hanya menampilkan. Ini bukan spesifikasi fitur, hanya bentuk layar. Halaman-halaman ini harus dibangun penuh.

**Kebutuhan Fungsional:**

#### FR-6: Kelola Penyewa

Andi dapat menambah, mengubah, dan melihat Penyewa beserta nama, nomor telepon, nomor rekening, dan kontak darurat.

**Konsekuensi (teruji):**
- Nama Penyewa wajib dan tidak boleh kosong. Inilah kunci rekonsiliasi dengan mutasi bank.
- Nomor rekening opsional, boleh diisi nanti.
- Nama Penyewa unik dalam aplikasi agar tidak ambigu saat mencocokkan.
- Data KTP dan alamat tidak dikumpulkan di MVP. [ASSUMPTION: prototipe punya kolom KTP, tapi tidak ada kebutuhan yang menyatakannya. Menghapus kolom ini juga mengurangi data pribadi yang tersimpan.]

#### FR-7: Membuka Kontrak Sewa

Andi dapat mengikat satu Penyewa dengan satu Kendaraan melalui Kontrak, dengan menentukan tanggal mulai, tarif harian, jadwal kerja, dan hari libur. Kontrak yang baru dibuka langsung berstatus aktif. Realizes UJ-1.

**Konsekuensi (teruji):**
- Satu Kendaraan hanya boleh punya satu Kontrak aktif. Satu Penyewa juga tidak boleh punya dua Kontrak aktif — ini aturan bisnis, bukan batasan teknis.
- Tanggal mulai Kontrak boleh lebih awal dari hari ini, untuk mencatat Kontrak yang sudah berjalan sebelum aplikasi dipakai. [ASSUMPTION: pemilik punya beberapa Kontrak aktif yang dimulai sebelum aplikasi pertama dipakai. Kontrak seperti ini akan langsung menunjukkan Tunggakan untuk hari-hari yang sudah lewat.]
- Tarif harian di Kontrak disalin dari Kendaraan saat Kontrak dibuka, dan tidak berubah otomatis.
- Kontrak boleh dibuat dengan tarif yang berbeda dari tarif Kendaraan, untuk harga yang sudah dinegosiasi.

#### FR-8: Jadwal Kerja dan Pengecualian Servis

Andi dapat menentukan apakah Kontrak ditagih 6 hari seminggu dengan hari libur yang dipilih, atau 7 hari, serta menandai tanggal tertentu sebagai Pengecualian Servis yang tidak ditagih.

**Konsekuensi (teruji):**
- Jadwal kerja 6 hari mengeluarkan satu hari dari hitungan. Default-nya Minggu.
- Pengecualian Servis berlaku per Kontrak, bukan per Kendaraan. Satu Kendaraan bisa punya beberapa Kontrak dengan pengecualian berbeda.
- Menambah Pengecualian Servis dengan tanggal yang sudah ada tidak diperbolehkan.

#### FR-9: Mengakhiri Kontrak

Andi dapat mengakhiri Kontrak aktif, sehingga Kendaraan kembali tersedia dan Kontrak berhenti menambah Tunggakan setelah tanggal akhir.

**Konsekuensi (teruji):**
- Saat Kontrak diakhiri, kelebihan Setoran atas Kewajiban periode berjalan tidak hilang, tetapi menjadi Kredit pada Kontrak tersebut.
- Kontrak yang sudah berakhir tidak bisa diedit tarif, jadwal, atau pengecualian.
- Setoran lama pada Kontrak yang sudah berakhir tetap dapat dibaca dan dibatalkan.

#### FR-9b: Menandai Lunas Sampai Tanggal Tertentu

Andi dapat menandai satu Kontrak lunas sampai tanggal tertentu, untuk situasi ketika kewajiban lama sudah tercatat di tempat lain dan tidak akan pernah dimasukkan ke aplikasi.

**Konsekuensi (teruji):**
- Aksi ini menghapus seluruh Tunggakan pada Kontrak untuk semua hari tagih sebelum tanggal yang dipilih, dalam satu operasi.
- **Tidak ada representasi per hari.** Aksi ini untuk menyamakan aplikasi dengan kenyataan, bukan untuk mencatat riwayat pembayaran. Tidak ada Setoran yang dibuat, tidak ada alokasi yang disimpan, dan tidak ada jejak audit selain waktu aksi itu dilakukan.
- Wajib konfirmasi yang menyebutkan Kontrak, Kendaraan, Penyewa, jumlah hari yang dihapus, dan nominal yang hilang dari tunggakan.
- Hanya tersedia untuk Kontrak, bukan untuk Penyewa.
- Jika ada Setoran yang tercatat setelah tanggal tersebut, alokasi normal FR-10 tetap berlaku untuk hari-hari sesudahnya.

> **Alasan ada di MVP.** FR-7 mengizinkan Kontrak dengan tanggal mulai yang sudah lewat, untuk mencatat kontrak yang berjalan sebelum aplikasi dipakai. Tanpa aksi ini, Kontrak seperti itu langsung menampilkan Tunggakan fiktif untuk hari-hari yang sudah dibayar di tempat lain, dan tidak ada jalan keluar lain — tidak ada impor dan tidak ada entri historis massal. Owners akan perlu memasukkan puluhan sampai ratusan Setoran satu per satu, dan hari pertama aplikasi akan terlihat gagal.

---

### 4.4 Tunggakan dan Penagihan

**Deskripsi:** Ini layar yang paling sering dibuka, dan tempat nilai utama aplikasi ini. Tunggakan harus dapat dipercaya tanpa perlu dikoreksi manual. Kalau angkanya salah, Andi akan menagih orang yang sudah bayar, dan seluruh kepercayaan terhadap aplikasi hilang.

Realizes UJ-2.

> **Koreksi terhadap prototipe.** Prototipe mencocokkan pembayaran per tanggal: setoran hari kedua dibandingkan dengan tarif tanggal itu, lalu sisanya dibuang. Akibatnya, (a) pembayaran dua hari sekaligus tidak menutup hari yang telat, dan (b) pembayaran hari Minggu tidak dihitung untuk hari Senin. Keduanya membuat Tunggakan terlihat selalu tertinggal satu hari dari kenyataan. Prototipe sengaja begitu agar layar demo tidak kosong, tapi pola ini **tidak boleh** diterjemahkan mentah ke Kotlin. Model di bawah menggantikannya. Contoh lengkap ada di `addendum.md`.

**Kebutuhan Fungsional:**

#### FR-10: Menghitung Tunggakan dengan Alokasi Kronologis

Sistem menghitung Tunggakan untuk setiap Kontrak aktif **dan setiap Kontrak yang sudah berakhir dengan sisa tagihan**, dengan mengumpulkan seluruh Setoran yang sudah tercatat menjadi satu **Pool Setoran**, lalu mengalokasikannya ke Hari tagih dari yang terlama ke yang terbaru. Sisa yang tidak tertagih adalah Tunggakan. Realizes UJ-2.

**Konsekuensi (teruji):**
- **Pool Setoran** adalah jumlah seluruh Setoran lunas pada Kontrak tersebut **yang masuk Periode cutoff berjalan**, dengan tanggal antara batas bawah Periode (dibatasi oleh tanggal mulai Kontrak) dan hari ini. Setoran dari periode sebelumnya tidak dihitung ulang ke periode ini.
- **Kewajiban** adalah tarif harian dikali jumlah Hari tagih dalam Periode cutoff berjalan, dihitung sejak tanggal mulai Kontrak.
- **Tunggakan** adalah Kewajiban dikurangi Pool Setoran, dengan nilai minimum nol.
- **Kredit** adalah sisa Pool Setoran setelah Tunggakan mencapai nol. Kredit mengurangi Kewajiban periode berikutnya, bukan sekadar angka yang tersimpan.
- **Alokasi setiap Setoran terhadap Hari tagih disimpan, bukan dihitung ulang dari nol.** Ini yang membuat Kredit bertahan lintas periode: karena yang disimpan adalah fakta "Setoran ini menutup hari tersebut", setiap periode berikutnya otomatis melihat hari-hari yang belum tertutup, tanpa perlu menghitung ulang atau menyimpan angka Kredit terpisah.
- Pembayaran beberapa hari sekaligus menutup hari-hari yang tertagih, termasuk yang terlama. Tidak ada sisa yang terbuang.
- Pembayaran pada hari yang tidak ditagih — hari libur, hari Pengecualian Servis, atau hari sebelum Kontrak dimulai — tetap masuk Pool Setoran dan dialokasikan ke Hari tagih terdekat.
- Pembayaran dengan tanggal setelah hari ini tidak dihitung sampai tanggalnya benar-benar jatuh tempo.
- Setoran yang dibatalkan dikeluarkan dari Pool Setoran, alokasi yang dilepas darinya, dan seluruh alokasi dihitung ulang dari Setoran yang tersisa. Pembatalannya menaikkan Tunggakan.
- **Kontrak yang sudah berakhir tetap dihitung Tunggakannya** sampai lunas. Tunggakan atas Kontrak yang sudah berakhir tidak hilang, dan tetap masuk Tunggakan aktif.

#### FR-11: Menentukan Jumlah Hari Menunggak dan Tingkat Risiko

Sistem menyatakan jumlah hari Menunggak dan memberi tingkat risiko yang langsung dibaca.

**Konsekuensi (teruji):**
- **Menunggak** adalah jumlah Hari tagih dalam Periode cutoff berjalan yang belum tertutup oleh alokasi Setoran.
- Karena tarif harian Kontrak konstan, jumlah ini setara dengan Tunggakan dibagi tarif harian, dibulatkan ke atas. Kedua pendekatan menghasilkan angka sama; yang dipakai adalah alokasi karena ia langsung tersedia dari FR-10.
- Tingkat risiko: 0 hari = Lancar; 1 sampai 2 hari = Ringan; 3 sampai 6 hari = Perhatian; 7 hari atau lebih = Kritis.
- Daftar Tunggakan diurutkan dari nominal terbesar. Untuk nominal yang sama, dari Hari tagih tertua yang belum tertutup.
- Nilai Menunggak ini adalah angka yang dikirim ke penyewa lewat WhatsApp (FR-12), sehingga harus benar.

> **Catatan untuk implementasi.** Kesalahan yang ada di prototipe bukan pada pembagian nominal, melainkan pada `Math.min(missedDaysList.length, ...)`: daftar itu hanya memuat hari yang pembayarannya kurang pada tanggal itu, sehingga overpay besar menghapus beberapa hari yang sebenarnya menunggak. Hitungan berbasis alokasi FR-10 menghilangkan masalah itu.

#### FR-12: Menyusun Pesan Pengingat WhatsApp

Andi dapat membuat pesan pengingat yang sudah terisi dari data Tunggakan, lalu mengirimnya lewat WhatsApp. Realizes UJ-2.

**Konsekuensi (teruji):**
- Pesan memuat nama Penyewa, plat Kendaraan, Nama usaha, sisa Tunggakan dalam rupiah, dan jumlah hari menunggak.
- Pesan ditampilkan di kotak yang bisa disunting sebelum dikirim. Tautan WhatsApp dibangun ulang dari teks hasil suntingan.
- Tersedia tombol Salin untuk kasus tanpa tautan.
- Pengiriman pesan tidak mengubah status Tunggakan dan tidak tercatat sebagai riwayat interaksi.

---

### 4.5 Dashboard dan Laporan

**Deskripsi:** Andi membuka aplikasi hampir setiap pagi. Dua layar ini menjawab dua pertanyaan berbeda: hari ini, dan bulan ini. Keduanya untuk monitoring internal, bukan untuk diserahkan ke pihak lain. Realizes UJ-3.

**Kebutuhan Fungsional:**

#### FR-13: Ringkasan Beranda

Beranda menampilkan dalam sekali lihat: Pendapatan hari ini, Tunggakan aktif, armada aktif yang terpecah menjadi tersewa, tersedia, dan servis, serta tingkat penagihan.

**Konsekuensi (teruji):**
- **Pendapatan hari ini** adalah jumlah Setoran lunas dengan tanggal hari ini.
- **Tunggakan aktif** adalah total Tunggakan dari seluruh Kontrak aktif dan yang sudah berakhir dengan sisa tagihan.
- **Tingkat penagihan** adalah Pendapatan Periode dibagi jumlah Pendapatan Periode dan Tunggakan aktif, dalam persen bulat. Bernilai 100 persen jika nol.
- Semua angka dihitung ulang setiap kali ada perubahan data. Tidak ada tombol muat ulang dan tidak ada angka yang basi.
- Empat aksi cepat tersedia: Catat Setoran, Tambah Mobil, Kelola Penyewa, Kirim Pengingat.

#### FR-14: Laporan Periode

Andi dapat melihat rekap satu Periode cutoff: total Setoran masuk, total Tunggakan akhir periode, dan jumlah Penyewa yang lunas.

**Konsekuensi (teruji):**
- Laporan menampilkan Setoran berdasarkan tanggalnya, bukan tanggal pembukuan. Yang terlihat adalah tanggal yang dicatat, karena itulah yang relevan secara kas.
- Periode di masa depan tidak dihitung. Hari setelah hari ini tidak menambah Tunggakan.
- Laporan menampilkan komposisi Tunggakan menurut tingkat risiko, dan tren Setoran tujuh hari terakhir. [ASSUMPTION: grafik dianggap nilai tambah visual. Kalau dependensi chart tidak sepadan, tabel angka sudah memenuhi kebutuhan. Ini kandidat pertama untuk di-drop saat implementasi.]

---

### 4.6 Pengaturan, Keamanan, dan Pencarian

**Deskripsi:** Pengaturan berisi konfigurasi yang jarang diubah, dan yang lebih penting, cara keluar dari data secara aman. Realizes UJ-4.

**Kebutuhan Fungsional:**

#### FR-15: Mengubah Tanggal Cutoff

Andi dapat mengubah Tanggal cutoff. Aplikasi langsung memperhitungkan ulang seluruh Tunggakan dengan Periode baru.

**Konsekuensi (teruji):**
- Tanggal cutoff antara 1 dan 28. [ASSUMPTION: dibatasi 28 agar Periode selalu punya minimal satu hari di bulan terpendek. Konvensi ini mengikuti prototipe.]
- Sebelum diterapkan, aplikasi menampilkan pratinjau: berapa Kontrak dan berapa rupiah Tunggakan yang berubah. Peringatan ini wajib, bukan opsional. Mengganti Tanggal cutoff dapat mengubah angka tagihan secara signifikan.

#### FR-16: Cadangkan dan Pulihkan Data

Andi dapat mengekspor seluruh data ke satu berkas, dan memulihkannya dari berkas itu. Ini satu-satunya jalur keluar data dari perangkat.

**Konsekuensi (teruji):**
- Ekspor menghasilkan satu berkas JSON berisi Kendaraan, Penyewa, Kontrak, Setoran, dan Pengaturan.
- Nama berkas memuat tanggal ekspor, agar beberapa cadangan bisa dibedakan.
- Pemulihan menampilkan pratinjau jumlah entitas sebelum menimpa.
- Ekspor tidak menghapus data saat ini. Pemulihan adalah satu-satunya operasi yang menimpa, dan harus punya konfirmasi eksplisit.
- Aplikasi tidak mengirim data ke mana pun. Tidak ada sinkronisasi dan tidak ada unggahan.

#### FR-17: Kunci Layar dengan PIN

Andi dapat mengaktifkan atau menonaktifkan kunci layar, dan mengganti kodenya.

**Konsekuensi (teruji):**
- Durasi kunci otomatis dapat diatur pada 1, 5, 15, atau 30 menit. Default 5 menit.
- Setelah aplikasi berada di latar belakang selama durasi itu, aplikasi terkunci lagi saat dibuka. Pencacah waktu harus benar-benar berjalan — prototipe punya setelan ini tetapi tidak pernah menjalankannya.
- Kode PIN tepat empat angka, dan tidak disimpan dalam bentuk yang bisa dibaca. Lihat NFR-5.
- **Kunci layar adalah penghalang, bukan keamanan sungguhan.** Tidak ada bedanya antara aplikasi terkunci dan terbuka bagi orang yang memegang tablet. Ini dinyatakan terbuka, dan tidak ada klaim lain. [ASSUMPTION: prototipe menulis "data tersimpan lokal dan terenkripsi di perangkat ini". Kalimat itu tidak akurat dan tidak boleh dipakai.]

#### FR-18: Mengatur Identitas Usaha dan Rekening

Andi dapat mengisi dan mengubah Nama usaha, Alamat usaha, Telepon usaha, dan daftar Rekening usaha yang menerima Setoran. Data ini dipakai di pesan pengingat (FR-12) dan di pilihan Rekening usaha pada FR-1b. Realizes UJ-2, UJ-3.

**Konsekuensi (teruji):**
- Nama usaha, Alamat usaha, dan Telepon usaha tersimpan sebagai satu blok identitas, dan dipakai ulang di mana pun Nama usaha perlu muncul. Tidak ada input Nama usaha yang terpisah di tempat lain.
- Daftar Rekening usaha mendukung lebih dari satu rekening, karena Setoran bisa masuk lewat beberapa bank.
- Setiap rekening memiliki nama bank dan nomor rekening. Nomor rekening divalidasi sebatas formatnya saja, dan tidak perlu diverifikasi terhadap bank.
- Rekening tidak bisa dihapus selama masih ada Setoran yang memakainya.
- Perubahan data usaha tidak mengubah Setoran yang sudah tercatat. Setoran lama tetap seperti saat dicatat.

#### FR-19: Mencari Data

Andi dapat mencari Penyewa, Kendaraan, dan Kontrak dari satu kolom pencarian global yang tersedia di setiap layar. Realizes UJ-1, UJ-2.

**Konsekuensi (teruji):**
- Pencarian mencocokkan nama Penyewa dan plat Kendaraan, sebagian teks, tanpa membedakan huruf besar dan kecil.
- Hasil dikelompokkan per jenis, dan memilih hasil membuka layar yang sesuai.
- Pencarian tidak menelusuri catatan, Kode transaksi, atau nomor Setoran.

---

### 4.7 Persyaratan Non-Fungsional

Bagian ini mengikat seluruh aplikasi. Ini alat internal untuk satu pengguna di satu perangkat, jadi persyaratannya sedikit — tapi yang ada di sini bukan pilihan.

#### NFR-1: Perhitungan Tunggakan yang Selalu Benar

- Setiap perubahan pada Setoran, Kontrak, atau Tanggal cutoff menghitung ulang seluruh Tunggakan secara langsung, sebelum layar selesai digambar. Menyimpan angka tunggakan di storage dan menghitungnya hanya saat pembacaan menghasilkan selisih yang tidak terlihat.
- Pada skala di bawah 20 Kendaraan dan beberapa ratus Setoran, hitungan ulang penuh selesai dalam di bawah 50 milidetik. Batas ini longgar; kalau dipecahkan karena alasan teknis, jangan mengorbankan kebenaran demi kecepatan.

#### NFR-2: Durabilitas Data

- Penulisan bersifat atomik. Aplikasi tidak boleh pernah meninggalkan data dalam keadaan setengah tertulis. Ini bukan detail kecil: prototipe menelan parse error lalu me-reset ke default, yang berarti satu penulisan korup menghapus seluruh riwayat.
- Kerusakan pada data yang tersimpan harus menggagalkan aplikasi dengan pesan yang jelas, bukan dengan data kosong yang terlihat seperti akun baru.
- Tidak ada operasi yang menghapus data tanpa persetujuan eksplisit. Ekspor tidak menghapus. Pemulihan adalah satu-satunya operasi yang menimpa, dan harus punya pratinjau lebih dulu.

#### NFR-3: Skema Berkas

- Berkas ekspor memuat nomor versi skema. Pemulihan dari berkas versi berbeda harus ditolak dengan pesan yang bisa dipahami, bukan dengan kegagalan parsing yang menghapus data yang ada.
- Nama berkas ekspor memuat tanggal ekspor.

#### NFR-4: Perilaku Pertama Kali Dijalankan

- Aplikasi harus bisa dijalankan dan diisi dari nol. Tidak ada akun, tidak ada wizard, tidak ada data contoh yang ikut tersimpan. [ASSUMPTION: aplikasi dimulai dari data kosong sesuai keputusan pemilik.]

#### NFR-5: Kode PIN

- Kode PIN tidak boleh disimpan dalam bentuk yang bisa dibaca, dan tidak boleh muncul di log, di backup yang diekspor, atau di pesan galat.
- Tidak ada klaim keamanan di dalam aplikasi maupun di luar aplikasi yang lebih besar dari kenyataan. PIN adalah penghalang layar.

#### NFR-6: Ketahanan Input

- Nilai yang bisa merusak data harus ditolak di lapisan data, bukan di antarmuka saja. Angka yang bukan angka, nominal nol atau negatif, tarif harian nol atau negatif — semuanya ditolak dengan pesan yang menyebut perbaikannya.
- Tidak ada nilai default diam-diam yang mengubah makna input. Bila tarif harian 0 berarti tidak valid, ia harus ditolak, bukan diganti dengan nilai bawaan.

#### NFR-7: Performa dan Ukuran

- Aplikasi harus tetap responsif pada Redmi Pad 2 dengan 20 Kendaraan dan 2.000 Setoran. Ini batas realistis untuk data beberapa bulan; tidak perlu yang lebih besar.
- Ukuran APK bukan batasan keras. Yang penting: aplikasi tidak besar dan tidak butuh aset besar.

#### NFR-8: Aksesibilitas

Standar ini sudah ada di prototipe dan wajib dipertahankan, bukan mulai dari nol. Tidak ada klaim aksesibilitas yang lebih rendah dari ini.

- Kontras teks utama minimal 4.5:1 pada tema terang dan 7:1 pada tema gelap.
- Target sentuh minimal 48dp pada semua tombol, chip, dan baris tabel.
- Ukuran teks minimal 14sp untuk label dan data.
- Angka rupiah memakai `tabular-nums` agar kolom angka mudah dibandingkan satu sama lain. Ini bukan hiasan — membandingkan nominal tunggakan secara visual adalah inti dari apa yang dilakukan pemilik setiap pagi.
- Focus ring 3px berwarna brand pada setiap elemen interaktif.

#### NFR-9: Orientasi dan Tema

- Tablet Redmi Pad 2 dipakai dalam dua orientasi, dan keduanya harus bisa dipakai penuh. Prototipe sudah merancang dua layout berbeda: navigasi rel dan pola master-detail untuk landscape, navigasi bawah untuk portrait. [ASSUMPTION: pemilik memakai tablet dalam kedua orientasi. Kalau ternyata hanya landscape, separuh pekerjaan navigasi bisa dihapus.]
- Aplikasi mendukung tema terang dan gelap. Prototipe sudah dirancang untuk keduanya.

---

## 5. Non-Tujuan (Eksplisit)

- **Aplikasi ini bukan aplikasi kasir atau titik pembayaran.** Pembayaran selalu terjadi di luar, lewat transfer bank atau tunai. Peran aplikasi adalah mencatat, bukan memproses uang.
- **Aplikasi ini tidak berintegrasi dengan bank.** Tidak ada API, tidak ada impor mutasi, tidak ada scraping. Rekonsiliasi nama pengirim terjadi di m-banking, secara manual, oleh Andi.
- **Penyewa tidak akan pernah memakai aplikasi ini.** Tidak ada akses penyewa, tidak ada portal, tidak ada notifikasi dalam aplikasi.
- **Aplikasi ini tidak menyimpan pengeluaran.** Tidak ada pembelian mobil, cicilan, atau biaya servis di MVP. Lihat §6.2.
- **Aplikasi ini tidak multi-pengguna.** Tidak ada login, tidak ada role, tidak ada sinkronisasi antar-perangkat.
- **Aplikasi ini tidak akan pernah butuh internet.** Tidak ada backend dan tidak ada sinkronisasi cloud. Ini keputusan desain, bukan keterbatasan sementara.

---

## 6. Scope MVP

### 6.1 Termasuk Scope

- Layar Beranda dengan empat metrik hidup dan empat aksi cepat.
- Layar Setoran: catat, ubah, batalkan, dan riwayat, dengan dua Metode Pembayaran.
- Layar Kendaraan: operasi penuh dengan plat unik.
- Layar Penyewa: operasi penuh dengan nama wajib.
- Layar Kontrak: buat, lihat, akhiri, dan tandai lunas sampai tanggal, beserta jadwal kerja, hari libur, dan Pengecualian Servis.
- Perhitungan Tunggakan dengan alokasi kronologis (FR-10).
- Layar Tunggakan dengan pengurutan, tingkat risiko, dan penyusun pesan WhatsApp.
- Layar Laporan per Periode cutoff.
- Pencarian global (FR-19).
- Pengaturan: Tanggal cutoff, identitas usaha, daftar Rekening usaha, dan kunci layar PIN dengan kunci otomatis yang berjalan.
- Ekspor dan pemulihan data sebagai berkas JSON.
- Pratinjau dampak sebelum mengganti Tanggal cutoff.
- Pratinjau konsekuensi sebelum menyimpan Setoran: Tunggakan sebelum, sesudah, dan Kredit.

Aplikasi mulai dari data kosong. Tidak ada fitur impor, dan tidak ada jalur migrasi dari Excel.

### 6.2 Di Luar Scope MVP

| Yang ditunda | Alasan | Kapan |
|---|---|---|
| Catatan pengeluaran pembelian mobil | Sudah diputuskan ditunda oleh pemilik. Arus kas masuk sudah cukup untuk keputusan belanja modal. | MVP berikutnya |
| Cicilan dan angsuran mobil | Masuk kategori pengeluaran, jadi ikut tertunda. | MVP berikutnya |
| Biaya servis mobil sebagai pengeluaran | Pengecualian Servis di FR-8 hanya menandai tidak ditagih ke penyewa, bukan mencatat biaya ke usaha. | MVP berikutnya |
| Ekspor laporan PDF atau Excel | Laporan untuk monitoring internal. [NOTE FOR PM] kalau nanti perlu laporan yang dibagikan, ini yang pertama ditambahkan. | v2 |
| Impor data dari Excel | Aplikasi dimulai dari data kosong. Tidak ada data lama yang perlu dimigrasikan. | Tidak direncanakan |
| Data KTP dan alamat penyewa | Tidak ada kebutuhan yang menyatakannya. Mengurangi data pribadi tersimpan. | Tidak direncanakan |
| Metode pembayaran QRIS | Dilewati. [ASSUMPTION] nama pengirim pada QRIS adalah nama merchant, bukan penyewa, sehingga rekonsiliasi nama tidak berlaku seperti pada Transfer Bank. | Tidak direncanakan |
| Aplikasi untuk penyewa | Tidak ada kebutuhan. Penyewa sudah punya WhatsApp. | Tidak direncanakan |
| Multi-perangkat dan sinkronisasi | Satu pengguna, satu tablet. | Tidak direncanakan |
| Multi-pengguna dan role | Satu pengguna. | Tidak direncanakan |
| Pengingat harian aplikasi | [NOTE FOR PM] "ingatkan saya hari ini masih ada yang menunggak" sangat mungkin diinginkan. Tapi itu fitur baru, bukan hasil MVP. | v2 |

---

## 7. Metrik Sukses

**Konteks:** alat internal untuk satu pengguna. Tidak ada pelacak pengguna. Metrik di bawah diukur dengan cara termurah, yaitu pengamatan, bukan telemetri. [ASSUMPTION: tidak ada permission analytics di aplikasi; metrik dinilai manual.]

**Utama**

- **SM-1** — Nol pencatatan harian di luar aplikasi. *Definisi:* tidak ada Setoran yang tercatat hanya di Excel atau catatan. *Ukur:* pada akhir bulan pertama, aplikasi adalah satu-satunya tempat catatan harian ditulis. Berhasil bila angka di aplikasi cocok dengan kenyataan dan Excel tidak pernah dibuka lagi untuk rekap harian. *Validasi:* FR-1, FR-6, FR-7.
- **SM-2** — Tunggakan yang dapat dipercaya tanpa koreksi. *Definisi:* Tunggakan yang ditampilkan aplikasi selalu sama dengan kenyataan, dan tidak pernah perlu diperbaiki manual. *Ukur:* setiap selisih antara aplikasi dan kenyataan adalah bug, bukan kesalahan setup. Target nol selisih sejak hari pertama. *Validasi:* FR-10, FR-11, FR-3.
- **SM-3** — Jawaban "siapa yang menunggak" dalam hitungan detik. *Definisi:* waktu dari membuka aplikasi sampai melihat daftar Tunggakan dengan nominal dan jumlah hari yang tepat. *Ukur:* di bawah 5 detik. *Validasi:* FR-13, FR-11.

**Sekunder**

- **SM-4** — Rekap satu hari selesai dalam satu sesi duduk. *Definisi:* seluruh Setoran hari itu tercatat tanpa berdiri dan tanpa membuka aplikasi lain. *Ukur:* di bawah 2 menit untuk 5 Setoran. *Validasi:* FR-1.
- **SM-5** — Pemulihan data berhasil dilakukan. *Definisi:* data pulih utuh dari satu berkas cadangan, di perangkat baru. *Ukur:* berhasil sekali, tanpa bantuan. *Validasi:* FR-16.

**Counter-metrics (jangan di|caption)**

- **SM-C1** — Kelengkapan data. *Kenapa jangan:* dorongan menambah kolom seperti KTP, alamat, catatan panjang, atau foto muncul karena terasa lebih lengkap. Tapi setiap kolom baru adalah tempat input salah dan data pribadi yang harus disimpan. Sukses bukan data yang lengkap. Sukses adalah angka yang benar.
- **SM-C2** — Jumlah fitur. *Kenapa jangan:* Setoran sudah cukup untuk menggantikan Excel. Fitur yang ditambahkan tanpa masalah nyata yang menyelesaikannya menambah beban perawatan tanpa mengurangi masalah.

---

## 8. Pertanyaan Terbuka

> ~~Apakah kunci layar otomatis perlu?~~ **Terjawab.** Perlu. Lihat FR-17.
> ~~Apakah data Excel perlu dimigrasikan?~~ **Terjawab.** Tidak. Aplikasi mulai dari data kosong.
> ~~Apakah satu Penyewa bisa punya lebih dari satu Kendaraan?~~ **Terjawab.** Tidak. Satu Penyewa satu Kendaraan adalah aturan bisnis.
> ~~Perlukah Setoran tunai dibedakan?~~ **Terjawab.** Ya. Dua Metode Pembayaran: Transfer Bank dan Tunai. Lihat FR-1b.
> ~~Apakah QRIS dipakai?~~ **Terjawab.** Tidak. Dilewati.
> ~~Apakah tarif harian bisa berubah di tengah Kontrak?~~ **Terjawab.** Tidak. Tarif Kontrak konstan selama masa Kontrak.
> ~~Bagaimana perlakuan atas Setoran berlebih?~~ **Terjawab.** Sebagai Kredit yang mengurangi Kewajiban periode berikutnya.

1. **[Menengah]** Apakah ada yang perlu dicetak? Andi mungkin ingin mencetak daftar Tunggakan untuk dibawa.
2. **[Kecil]** Apakah aplikasi perlu pengingat harian saat dibuka, bahwa masih ada tiga penyewa yang menunggak?
3. **[Kecil]** Apakah satu Penyewa boleh menyewa Kendaraan yang sama lagi setelah Kontrak sebelumnya selesai? Saat ini asumsinya boleh, dan Kontrak baru dimulai dengan Tunggakan nol.
4. **[Kecil]** Untuk Setoran Tunai, adakah catatan yang wajib diisi agar bisa ditelusuri nanti? Tidak ada mutasi bank yang bisa dibandingkan, jadi catatan adalah satu-satunya breadcrumbs yang ada.

---

## 9. Indeks Asumsi

Setiap `[ASSUMPTION]` di dokumen ini, dikumpulkan di bawah. Yang bertanda "Diputuskan pemilik" bukan asumsi, melainkan jawaban yang sudah diberikan.

- **§1** — Klinik.Net adalah nama usaha, bukan nama aplikasi. Kalau ternyata nama aplikasi, tidak ada dampak ke scope.
- **§4.1** — Penghapusan permanen Setoran tidak dibutuhkan. Pembatalan dengan alasan sudah cukup.
- **§4.2** — Tarif harian Kontrak tidak berubah selama masa Kontrak. Mengubah tarif berarti Kontrak baru.
- **§4.2** — Status Kendaraan adalah informasi tampilan, bukan sumber kebenaran. Yang menentukan adalah Kontrak.
- **§4.3** — Data KTP dan alamat Penyewa tidak dikumpulkan di MVP.
- **§4.3** — Tanggal mulai Kontrak boleh lebih awal dari hari ini, untuk mencatat Kontrak yang sudah berjalan sebelum aplikasi dipakai.
- **§4.5** — Grafik adalah nilai tambah visual. Kandidat pertama untuk di-drop saat implementasi.
- **§4.6** — Tanggal cutoff dibatasi 1 sampai 28.
- **§4.6** — Kunci layar adalah penghalang, bukan keamanan sungguhan.
- **§4.7** — Tablet Redmi Pad 2 dipakai dalam dua orientasi.
- **§4.7** — Aplikasi dimulai dari data kosong, tanpa data contoh.
- **§6.2** — QRIS dilewati, tidak masuk scope.

**Diputuskan pemilik (bukan asumsi):**
- **§4.6** — Kunci layar otomatis harus benar-benar bekerja, dengan pencacah waktu yang berjalan.
- **§4.7** — Tidak ada permission analytics. Metrik dinilai manual.
- **§4.3** — Satu Penyewa satu Kendaraan. Dua Kontrak aktif per Penyewa dilarang.
- **§4.1** — Dua Metode Pembayaran: Transfer Bank dan Tunai.
- **§4.6** — Nama usaha adalah PT Klinik Net Rent Car. Dikelola lewat FR-18, bukan hardcode.
- **§6.1** — Tidak ada migrasi data Excel. Aplikasi mulai dari data kosong.
- **§6.2** — Pengeluaran, cicilan, dan biaya servis ditunda ke MVP berikutnya.
- **§8** — Tarif Kontrak konstan selama masa Kontrak.
- **§8** — Setoran berlebih menjadi Kredit yang mengurangi Kewajiban periode berikutnya.

---

*Final — FR-10 dan FR-11 sudah diverifikasi dengan kasus numerik (`verify-fr10.md`). Siap untuk `/bmad-architecture`.*
