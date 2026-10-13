---
title: "Klinik.Net — Experience Design"
status: final
created: 2026-10-13
updated: 2026-10-13
---

# EXPERIENCE.md: Klinik.Net

Arsitektur informasi, perilaku, state, interaksi, aksesibilitas, dan alur. Identitas visual ada di `DESIGN.md`.

> **Spines win on conflict.** Jika mock, wireframe, atau prototipe berbeda dari dokumen ini, dokumen ini yang benar.

---

## 1. Foundation

### Form-factor

**Tablet Android — Redmi Pad 2, 2560×1600, 90Hz.** Bukan ponsel, bukan web. Ini yang menentukan hampir semua keputusan di bawah.

Dua orientasi, keduanya **penuh** — bukan degraded:

| Orientasi | Navigasi | Pola |
|---|---|---|
| **Landscape** (utama) | Nav rail 88px, kiri | Master-detail |
| **Portrait** | Bottom nav | Single-column, daftar → detail |

Landscape adalah orientasi kerja utama. Portrait adalah yang benar, bukan Citizen-class.

### Sistem UI

**Compose Material 3**, dengan design tokens di `DESIGN.md` sebagai override. Material 3 dipilih karena:

- Komponennya sudah menyelesaikan masalah yang sudah umum — dialog, snackbar, list, text field.
- Typography dan spacing scale-nya bisa di-override tanpa menulis komponen dari nol.
- Van Android-native, jadi tidak terasa "web di dalam app".

Yang **tidak** diambil dari Material: bentuk kartu (radius 20px + bayangan hangat), tipografi Fraunces, dan palet coral. Ini identitas Klinik.Net, bukan identitas Material.

### Konteks pemakaian

**Duduk di meja depan warung, sambil menunggu penyewa.** Bukan sambil jalan. Konsekuensinya:

- Density boleh moderate-tinggi. Layar besar dan tidak dipakai dalam kondisi terburu-buru.
- **Tidak ada keyboard sebagai input utama.** Semua input numerik lewat numpad on-screen.
- Motion boleh ada, tapi tidak boleh berulang. Lihat §6.
- Ini aplikasi **uang** — kontras tinggi dan angka yang bisa dipercaya bukan fitur tambahan.

### Bahasa

Semua teks dalam Bahasa Indonesia. Istilah domain memakai kata yang sama dengan yang dipakai pemilik sehari-hari: *setoran*, *tunggakan*, *penyewa*, *numpad*. Tidak ada terjemahan yang terdengar asing.

---

## 2. Information Architecture

### Peta layar

```
Beranda ──┬── Setoran ──┬── Tunggakan
          │             │
          ├── Kendaraan ─── Kontrak ──┐
          │                          │
          ├── Penyewa ───────────────┤
          │                          │
          ├── Laporan                │
          │                          │
          └── Pengaturan ────────────┘

Pintasan: pencarian global, pesan WhatsApp
```

Delapan layar. Semua dapat dicapai dari nav rail atau bottom nav dalam satu tap.

### Urutan navigasi

Nav rail urutan: **Beranda · Setoran · Kendaraan · Penyewa · Tunggakan · Laporan · Pengaturan**

Urutannya mengikuti frekuensi, bukan struktur data. Setoran dan Tunggakan adalah dua yang dipakai setiap hari — keduanya di posisi atas, bersebelahan, karena sering dipakai berurutan dalam sesi yang sama.

### Master-detail (landscape)

Digunakan di **Kendaraan**, **Penyewa**, dan **Tunggakan**. Daftar di kiri, detail di kanan, keduanya terlihat bersamaan.

Alasannya: pemilik sering membandingkan beberapa penyewa sekaligus. Kalau detail hanya muncul setelah tap, membandingkan berarti bolak-balik. Dengan master-detail, daftar lengkap dan detail satu penyewa ada di layar yang sama.

Di portrait, master-detail runtuh menjadi daftar → detail (navigasi mundur).

### Pencarian global

Satu kolom cari, **tersedia di setiap layar** — persis seperti di prototipe. Mencocokkan nama Penyewa dan plat Kendaraan, sebagian teks, case-insensitive. Hasil dikelompokkan per jenis.

Tidak menelusuri catatan, Kode transaksi, atau nomor Setoran. Itu keputusan sadar: pencarian di dalam catatan butuh full-text search dan tidak sebanding dengan yang sederhana, sementara mencari penyewa adalah kebutuhan harian.

### Alur keluar data

Ada **satu** jalan keluar dari aplikasi: ekspor JSON di Pengaturan. Tidak ada sinkronisasi, tidak ada backend. Lihat §8.

---

## 3. Voice and Tone

### Suara

**Praktis dan tenang.** Aplikasi ini dipakai setiap hari, bukan sesekali. Suara yang terlalu berapi-api akan terasa melelahkan di minggu kedua.

- **Langsung.** "Simpan", "Batal", "Hapus alasan", bukan "Simpan Perubahan Anda".
- **Rapi.** Tagalog pendek, tanpa formalitas berlebihan. "Nama Penyewa" bukan "Nama Lengkap Penyewa".
- **Jujur.** Kalau ada yang tidak bisa dilakukan aplikasi ini, katakan apa adanya. Lihat §7 tentang PIN.

### Microcopy

**Nominal rupiah** selalu ditulis penuh: `Rp 250.000`. Tidak pernah `250rb` di layar yang formal. Ringkas boleh muncul di badge ringkas, tapi angka yang jadi keputusan harus penuh.

**Tanggal** format `23 Sen`, `24 Sel`, `25 Rab` — hari dan bulan. Tahun penuh hanya muncul di ekspor dan laporan periode.

**Tunggakan selalu dua kalimat:** nominal **dan** hari. Tidak pernah satu saja:

```
Rp 750.000 · 3 hari
```

**Placeholder** menjelaskan format, bukan mengulang label: `Contoh: 0812-3456-7890`, bukan "Masukkan nomor telepon".

**Error** menyatakan apa yang salah **dan** bagaimana memperbaikinya:

```
Nominal harus lebih besar dari nol.
```
bukan `Input invalid`.

**Konfirmasi destruktif** selalu menyebut konsekuensinya, bukan hanya nama aksinya:

```
Batalkan setoran Rp 250.000?
Tunggakan Pak Budi akan kembali menjadi Rp 750.000.
Alasan pembatalan (wajib):
```

Yang terakhir adalah yang paling penting. Pratinjau yang menjelaskan dampaknya — bukan hanya "Yakin tidak mau membatalkan?" — membuat orang berhenti berpikir sejenak sebelum menekan.

**Kredit** selalu dijelaskan, tidak pernah muncul sebagai angka misterius:

```
Kredit: Rp 250.000
Dipakai otomatis untuk periode berikutnya.
```

### Istilah

Pakai kata yang sama seperti PRD §3. Kalau ada kata baru yang perlu muncul, tambahkan ke Glossary — jangan diciptakan sineara di layar.

---

## 4. Component Patterns

Perilaku komponen. Visual ada di `DESIGN.md` §7.

### Numpad

Input nominal **wajib** lewat numpad on-screen, bukan keyboard sistem.

- Tombol besar, layout 3×4.
- Display di atas, angka yang sedang diketik terlihat besar dan tabular.
- Nol di posisi yang mudah dijangkau ibu jari.
- Batas 12 digit.
- Tombol simpan menolak nilai ≤ 0 dengan flash merah, **tidak** diam-diam menerima.
- Efek getar/haptic saat tombol ditekan — memberi konfirmasi taktil tanpa harus lihat layar.

### Form

- Validasi **saat kehilangan fokus**, bukan saat mengetik. Mengetik "250000" tidak boleh menampilkan error di tengah jalan.
- `.field-error` hanya muncul setelah ada usaha, lalu tidak pernah hilang tanpa perbaikan.
- Label selalu di atas field, tidak sebagai placeholder.

### Dialog simpan Setoran

Ini interaksi paling penting di aplikasi. Setelah semua input, sebelum Simpan:

```
┌─────────────────────────────────────┐
│  Tunggakan  Rp 750.000 → Rp 250.000 │
│  Menutup: 2 hari · Sisa: 1 hari     │
│                          (Rp 250.000)│
│                                     │
│  [ Batal ]              [ Simpan ]   │
└─────────────────────────────────────┘
```

Kalau ada kredit:

```
│  Tunggakan  Rp 750.000 → Rp 0        │
│  Menutup: 3 hari                     │
│  Kredit: Rp 250.000 — dipakai otomatis│
│          untuk periode berikutnya    │
```

**Ringkas, tanpa tanggal per-hari.** Pratinjau ini dibaca setiap hari selama setahun; padat membuat orang berhenti membacanya. Yang perlu diketahui adalah "berapa yang tersisa dan berapa hari yang tertutup", bukan tanggal masing-masing. Audit tanggal ada di riwayat Setoran.

Pratinjau **wajib**, bukan opsional — termasuk kalau Andi sudah tahu hasilnya. Menyimpan tanpa melihat efeknya melanggar tujuan aplikasi.

### Layar Tunggakan

**Kode tunggakan dipakai dua kali: angka dan warna.** Angka adalah signal primer — akurat dan tidak bergantung pada persepsi warna. Warna adalah scanner cepat.

| Kondisi | Angka | Warna | Badge |
|---|---|---|---|
| Lunas | 0 hari | `{colors.success}` | Lancar |
| 1–2 hari | `2 hari` | `{colors.warning}` | 2 Hari (Ringan) |
| 3–6 hari | `5 hari` | `{colors.danger}` | 5 Hari (Perhatian) |
| 7+ hari | `9 hari` | `{colors.danger}` | 9 Hari (Kritis) |

**Warna selalu diturunkan dari angka, tidak pernah dipilih manual.** Dua sumber kebenaran yang bisa tidak sinkron adalah cara pasti membuat user berhenti percaya pada aplikasi. Kalau skalanya berubah, warnanya ikut — tidak ada keadaan di mana angka bilang "3 hari" tapi badge hijau.

Urutannya: nominal terbesar dulu. Untuk nominal sama, hari tertua dulu — itu yang paling mendesak.

Setiap baris punya tombol WhatsApp. Membuka dialog dengan pesan yang sudah terisi, bisa disunting, lalu tombol "Buka WhatsApp" atau "Salin".

### Tombol WhatsApp

Teks pesan memuat: nama Penyewa, plat Kendaraan, Nama usaha, sisa Tunggakan, jumlah hari menunggak. Kotak bisa disunting — **tautan dibangun ulang dari teks hasil suntingan**, jadi kalau Andi mengubah angka, link-nya ikut berubah.

Mengirim pesan **tidak** mengubah status Tunggakan dan tidak dicatat sebagai riwayat interaksi. Ini bukan CRM.

---

## 5. State Patterns

Setiap layar punya empat state. Tidak ada yang boleh hilang tanpa})$.

### Loading

Skeleton dengan bentuk yang sama dengan isi akhirnya. Untuk aplikasi yang öffline dan cepat, loading tidak boleh lama — kalau muncul, berarti ada yang salah.

### Kosong

Setiap daftar punya empty state yang menyebut **apa yang harus dilakukan**, bukan hanya "belum ada data":

```
Belum ada Kendaraan
Mambahkan mobil pertama untuk mulai mencatat sewa.
[Tambah Mobil]
```

Empty state adalah tempat terbaik untuk mengajari aplikasi — bukan hanya memberi tahu tidak ada apa-apa.

### Error

Error menjelaskan apa yang salah dan apa yang harus dilakukan. Jangan pernah menampilkan data kosong yang terlihat seperti akun baru - itu cara paling buruk untuk membuat orang salah mengira dan mencatat data dari nol.

```
Data tidak bisa dibaca.
Aplikasi dihentikan agar riwayat tidak hilang.
[Pulihkan dari cadangan]
```

**Jangan me-reset ke default.** Prototipe melakukan ini: parse gagal → data kosong → user mengira ini akun baru dan mulai mencatat lagi. Itu kehancuran data, bukan penanganan error.

### Sukses

Snackbar, bukan dialog. Dan **tidak mengubah state layar** — user bisa langsung lanjut ke aksi berikutnya tanpa menutup apa pun.

---

## 6. Interaction Primitives

### Motion

Semua easing `cubic-bezier(.2,.8,.2,1)`. Durasi maks 300ms (`{motion.t-slow}`).

**Aturan yang tidak bisa dilanggar:** tidak ada motion yang berulang, dan tidak ada motion yang lebih dari 300ms. Aplikasi ini dibuka sepanjang hari — animasi yang sama berulang setiap kali membuka akan terasa meriah di hari pertama dan melelahkan di hari kedua.

Yang boleh beranimasi:
- **Stagger saat layar dibuka** — sekali, untuk memberi kesan data mengalir masuk.
- **Shake untuk PIN salah** — 420ms, satu kali, hanya saat benar-benar salah.
- **Fade-up untuk konten yang masuk** — sekali.

Yang tidak boleh: transisi yang berulang, bounce, overshoot, atau animasi yang lebih lambat dari 300ms.

### Gestur

Tablet — jadi multi-touch dan drag yang natural. Tapi gestur **tidak pernah** menjadi satu-satunya jalan ke aksi penting: semua aksi punya tombol yang terlihat.

Swipe-to-delete **tidak** dipakai untuk Setoran. Aksi itu punya konsekuensi uang dan butuh alasan — terlalu penting untuk jadi gestur yang bisa salah.trigger.

### Input

Tap dan ketik. Numpad untuk angka. Keyboard sistem untuk teks.

**Input untuk tablet tanpa keyboard fisik adalah yang utama.** Semua field harus bisa diisi dengan keyboard on-screen tanpa layout yang sempit.

### Konfirmasi

Tiga level:

| Level | Kapan | Wie |
|---|---|---|
| Tidak ada | Aksi reversible, tanpa efek samping | Langsung jalan |
| Dialog | Aksi yang mengubah angka | Menyebut dampaknya |
| Dialog + alasan wajib | Aksi yang menghapus data | Wajib diisi alasannya |

Aksi reversible tanpa efek samping - mengedit catatan, mengganti tema - **tidak boleh** meminta konfirmasi. Konfirmasi yang berlebihan membuat orang berhenti menekan tanpa membaca, dan itu lebih berbahaya daripada tidak meminta sama sekali.

---

## 7. Accessibility Floor

Visual ada di `DESIGN.md` §9. Ini perilaku yang menyertainya.

### Kontras

- Teks utama ≥ 15:1, sekunder 7.4:1, tersier 5.2:1 pada putih.
- Dark theme: `#14100F` dengan teks `#F7F3F0`.
- **Warna tidak pernah satu-satunya pembawa makna** — tunggakan selalu angka **dan** warna.

### Target sentuh

**48dp minimum absolut.** Tidak ada pengecualian karena "tablet sudah besar".

Ini tempat yang paling sering dilanggar: badge, chip kecil, tombol ikon di dalam baris tabel. Semua harus bisa ditekan dengan jari yang tired, di layar yang berdebu, sambil memegangaquality hal lain.。

### Ukuran teks

Minimum 14sp untuk label dan data. 12sp hanya untuk caption yang bukan data.

### Focus

Focus ring 3px brand dengan offset 2px, global via `:focus-visible`. Di tablet dengan keyboard on-screen, focus order harus mengikuti urutan visual — tidak melompat-lompat.

### Gerak

Tidak ada animasi yang berulang. Jika animasi tidak dekoratif, dihapus saja - tidak ada yang hilang.

---

## 8. Alur Kunci

### Alur 1 — Setoran pagi (Andi, 15 detik per setoran)

1. Buka aplikasi → **Beranda**, empat KPI terlihat tanpa scroll.
2. Tekan "Catat Setoran" → pilih Penyewa.
3. Kontrak aktif terisi otomatis: Kendaraan, tarif harian, tanggal.
4. Nominal sudah terisi. **Tidak diubah** kalau pembayaran penuh.
5. Pilih Metode Pembayaran: Transfer Bank atau Tunai.
   - Transfer Bank → pilih Rekening usaha, Kode transaksi opsional.
   - Tunai → field rekening dan kode **disembunyikan**, bukan diisi kosong.
6. Dialog pratinjau: tunggakan sebelum → sesudah, hari yang tertutup, kredit bila ada.
7. Simpan.
8. **KPI di Beranda langsung berubah.** Tidak ada muat ulang, tidak berpindah halaman.

*Climax: empat setteran selesai tanpa berdiri, tanpa membuka Excel, tanpa berpindah halaman.*

### Alur 2 — Mengejar tunggakan

1. Dari Beranda atau rail → **Tunggakan**.
2. Daftar terurut nominal terbesar, color-coded, dengan angka hari.
3. Baris teratas: Pak Budi, Rp 750.000, 3 hari.
4. Tekan WhatsApp → pesan terisi otomatis.
5. Sunting sedikit kalau perlu.
6. "Buka WhatsApp" → WhatsApp terbuka.
7. Kembali. **Tunggakan tidak berubah.**

*Climax: pesan terkirim dalam dua ketukan, dengan angka yang benar.*

### Alur 3 — Memulihkan data

1. Pengaturan → "Pulihkan dari cadangan".
2. Pilih berkas JSON.
3. **Pratinjau dulu**: jumlah Kendaraan, Penyewa, Setoran, periode terakhir.
4. Konfirmasi.
5. Data kembali utuh.

*Edge case: pilih berkas yang salah → pratinjau menunjukinya, tombol Batal ada.*

### Alur 4 — Mengoreksi setoran yang salah

1. Buka riwayat Setoran → pilih yang salah.
2. Ubah tanggal atau nominal.
3. Tunggakan dihitung ulang seketika.
4. Simpan.

*Edge case: terbayar hari Minggu untuk hari Senin — ubah tanggalnya ke Senin, tunggakan langsung hilang.*

---

## 9. Prinsip yang Tidak Bisa Dilanggar

Tiga hal yang kalau dilanggar, aplikasi gagal — bukan cuma buruk:

**1. Angka harus bisa dipercaya.** Kalau Tunggakan salah sekali, orang yang menagih akan berhenti memakai aplikasi ini. Satu kesalahan menghapus semua pekerjaan di layar lain. Ini alasan utama koreksi prototipe di PRD FR-10.

**2. Pratinjau sebelum menyimpan.** Setiap aksi yang mengubah angka harus menunjukkan efeknya lebih dulu. Menyimpan tanpa melihat dampaknya membuat user percaya pada angka yang belum pernah dia lihat perubahannya.

**3. Data tidak boleh hilang diam-diam.** Kalau ada yang gagal, aplikasi berhenti dengan pesan jelas. Ia tidak boleh me-reset ke default dan terlihat seperti akun baru.

---

*Spines win on conflict. Rujukan visual: `DESIGN.md`.*
