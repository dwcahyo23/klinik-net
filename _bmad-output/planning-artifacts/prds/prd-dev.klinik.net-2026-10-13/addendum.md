# Addendum: Klinik.Net — Sistem Rental Mobil Harian

Dokumen ini menyimpan kedalaman yang **tidak layak masuk PRD** tapi dibutuhkan oleh hilir (arsitektur, epics, stories). PRD tetap sumber kebenaran untuk *apa* yang dibangun; addendum ini menyimpan *bagaimana* dan *kenapa alternatifnya ditolak*.

---

## 1. Model Perhitungan Tunggakan — Model yang Dipilih dan yang Ditolak

Ini keputusan teknis paling penting di proyek ini, karena salah hitung berarti menagih uang yang sudah dibayar.

### 1.1 Model yang dipilih — Alokasi Kronologis (PRD FR-10)

```
pool = Σ setoran_lunas(kontrak, tanggal_mulai ≤ d ≤ hari_ini)
kewajiban = tarif_harian × |hari_tagih dalam periode_running|
tunggakan = max(0, kewajiban − pool)
kredit    = max(0, pool − kewajiban)
```

`hari_tagih` = hari dalam periode cutoff, sejak `tanggal_mulai`, yang bukan hari libur (untuk kontrak 6 hari) dan bukan pengecualian servis.

Alokasi bersifat kronologis: pool Dialokasikan ke hari tagih **dari terlama ke terbaru**. Konsekuensinya, shortfall selalu jatuh di hari-hari terbaru — yang persis yang ingin dilihat pemilik saat mengejar tunggakan.

**O(1) per kontrak.** Tidak ada iterasi per hari untuk menghitung *set*tunggakan; iterasi per hari hanya perlu untuk menghitung *kewajiban*. Pada skala <20 mobil, ini bahkan tidakyecto jadi tempatkun optimasi.

### 1.2 Model yang ditolak — Pencocokan Per-Tanggal (implementasi prototipe)

```js
// prototype/data.js calculateArrears()
paidOnDate = Σ setoran dengan tanggal === dStr
paidInWindow += paidOnDate          // ← hanya masuk di dalam cabang yang bisa ditagih
if (paidOnDate < tarif) missedDays.push(dStr)
```

Tiga mode kegagalan, semuanya Producing tunggakan palsu:

| Skenario nyata | Perilaku prototipe | Perilaku model baru |
|---|---|---|
| Bayar 2 hari sekaligus, 500rb @ 250rb/hari | 250rb "hilang", 1 hari tetap tercatat menunggak | Kedua hari lunas |
| Bayar hari Minggu untuk hari Senin | Setoran tidak masuk `paidInWindow` (Minggu bukan hari tagih), Senin tetap menunggak | Alokasi ke Senin, lunas |
| Setoran 560rb (2 hari) di hari yang sama | `missedDays` kosong, `overdueDays = min(0, ceil(...)) = 0` — **tunggakan tersembunyi** | Tunggakan 0 dengan benar |

Baris ketiga yang paling berbahaya: overpay besar bisa **menutup** beberapa hari yang menunggak. Di tangan pemilik, itu berarti daftar "tunggakan" kosong padahal ada uang yang belum masuk.

**Mengapa prototipe begitu?** Agar layar demo tidak pernah kosong — dengan 8 kontrak aktif dan sebagian tidak bayar, model yang benar pun akan menampilkan angka. Ini keputusan demo, bukan keputusan domain, dan tidak boleh diterjemahkan.

### 1.3 Pertanyaan yang masih terbuka

- **Tarif harian berubah di tengah periode.**_MODEL_ saat ini mengasumsikan tarif Kontrak konstan selama masa Kontrak (PRD §9 asumsi ini). Kalau tarif bisa naik di tengah jalan, `kewajiban` harus dihitung per-segmen. **Belum diputuskan** — lihat PRD §8.1.
- **Kelebihan Setoran yang sangat besar** (misal bayar 3 bulan di awal). Model ini memperlakukannya sebagai `kredit` yang transparan, tapi belum ada UI untuk melihat atau mencairkan kredit tersebut. **Belum diputuskan** — lihat PRD §8.2.

---

## 2. Temuan Audit Prototipe

Hasil pembacaan menyeluruh atas `prototype/data.js`, `shared.js`, dan `settings.html`. Hanya yang **berdampak ke implementasi**; sisanya hanya relevan kalau ada yang.prototipe itu diterjemahkan mentah.

### 2.1 Jalur uang — wajib diperbaiki di implementasi

| Temuan | Lokasi | Dampak |
|---|---|---|
| `dailyRate: parseInt(x) \|\| 250000` | `addCar` | Input `0` atau tidak valid diam-diam jadi **Rp 250.000**. Pemilik mengira ia mengetik Rp 0, dan mencatat tarif yang salah. |
| `addPayment` tidak memvalidasi `amount` | `addPayment` | `parseInt("abc")` → `NaN` tersimpan ke state. UI numpad menolak `<= 0`, tapi itu proteksi layer presentasi, bukan data. |
| Tidak ada validasi Kontrak | — | `addContract`/`updateContract` **tidak ada sama sekali** di store. Prototipe hanya bisa menampilkan. |
| Tidak ada proteksi Kontrak aktif ganda | — | Tidak ada yang mencegah satu Kendaraan punya dua Kontrak aktif. Hanya konvensi tampilan (`car.currentRenterId`) yang menjaga. |
| `status` Kendaraan tidak sinkron dengan Kontrak | — | Menutup Kontrak tidak mengubah `car.status` ke `available`. Angka dashboard bisa salah. |

**Rekomendasi:** validasi di lapisan data (bukan di UI), dan `dailyRate` ditolak eksplisit bila ≤ 0 — bukan di-fallback.

### 2.2 Integrity dan recovery

| Temuan | Dampak |
|---|---|
| `KlinikStore.init()` menelan parse error dan reset ke default | **Korupnya satu penulisan JSON = seluruh data hilang tanpa jejak.** Ini skenario yang paling mungkin terjadi pada storage yang penuh. |
| Satu blob `localStorage` (~5 MB), ditulis ulang penuh setiap `save()` | Untuk <20 mobil cukup, tapi cukup rapuh: satu operasi gagal = data korup. |
| Tidak ada versi skema pada berkas ekspor | Pemulihan berkas versi lama bisa gagal diam-diam atau merusak data. |
| `lastAuditTrail` diinisialisasi tapi tidak pernah diisi | Padahal `cancelPayment` sudah meminta alasan — jejaknya dibuang. |
| Copy UI menyebut "SQLite/Room" padahal tidak ada SQLite | Klaim tidak akurat di UI, harus dihapus. |

### 2.3 Yang tidak diimplementasikan meski ada di UI

Tidak akan dibangun ulang apa adanya:

- `currencySymbol` — disimpan di settings tapi tidak pernah dibaca; `fmt.rp` hardcode `"Rp "`.
- `workDaysMode` dan `defaultOffDay` - disimpan di pengaturan, tapi per-Kontrak `workSchedule` dan `offDays` yang dipakai. Setelan global tidak pernah dibaca.
- `autoLockMinutes` - setelan ada, tapi pencacah waktunya tidak ada.
- Switch biometrik — hardcoded `checked`, tanpa handler, tanpa key di settings.
- UI ganti PIN — tidak ada; PIN hardcoded `"1234"` di state.

---

## 3. Keputusan yang Sudah Diambil (Rejected Alternatives)

| Keputusan | Yang ditolak | Alasan |
|---|---|---|
| Alokasi kronologis untuk Tunggakan | Pencocokan per-tanghal (prototipe) | Producing tunggakan palsu; lihat §1.2 |
| Tidak ada integrasi bank | API statement, impor CSV mutasi, scraping | Rekonsiliasi nama pengirim sudah cheap di m-banking. Integrasi menambah risiko dan dan tidak mengurangi satu pun langkah yang dilakukan pemilik. |
| Tidak ada impor dari Excel | Ekspor CSV per tabel + parser + layar validasi impor | Pemilik sudah memutuskan mulai dari data kosong. |
| Tidak ada penghapusan permanen Setoran | Hard delete | Pembatalan dengan alasan sudah cukup, dan menjaga jejaknya. |
| Tidak ada data KTP/alamat | Kolom KTP ada di prototipe | Tidak ada kebutuhan yang menyatakannya; mengurangi data pribadi tersimpan. |
| Ekspor/pulihkan JSON | Room + backup cloud | {project-root}/_bmad belum punya backend; JSON cukup dan bisa dipindah lewat mana saja. |
| PIN tanpa klaim enkripsi | "Data tersimpan lokal & terenkripsi" (copy prototipe) | Tidak akurat. localStorage tidak terenkripsi; PIN adalah screen detterent, bukan keamanan. |

---

## 4. Catatan untuk Arsitektur

Yang perlu diputuskan di tahap arsitektur, bukan di PRD:

1. **Basis data lokal.** Room (SQLite) adalah default wajar untuk Android, dan memberi transaksi atomik — yang dibutuhkan agar setoran dan perubahan Tunggakan tidak bisa setengah jadi. Tapi ada alternatif yang lebih malas: tetap `localStorage`/file JSON dengan satu file sebagai sumber kebenaran. **Rekomendasi: Room**, karena menulis ulang satu file JSON penuh setiap kali data berubah adalah sumber data korup yang, dan itu persis skenario yang paling mahal di sini.

2. **Tanggal referensi.** Prototipe memakai `SIM_TODAY` — konstanta simulasi. Implementasi **wajib** memakai `LocalDate.now()`.

3. **Batas `autoLockMinutes`.** BukanPengaturan ini bukan preferensi; harus tahan proses mati dan restart perangkat. Kalau `autoLockMinutes` dihormati begitu aplikasi buka dari keadaan mati juga, logikanya cukup: catat timestamp saat masuk background, bandingkan saat resume.

4. **Tablet — orientasi.** Prototipe merancang dua layout (nav rail + master-detail di landscape, bottom nav di portrait). Kalau hanya dipakai di satu orientasi, desainnya bisa disederhanakan drastis. **Belum diputuskan.**

5. **Ekspor berkas.** Ekspor JSON butuh akses file yang bisa dilihat pemilik (Documents, atau share sheet). Kalau cukup lewat share sheet Android, tidak perluIzinkan storage permission manual sama sekali.

---

*Lampiran untuk PRD `prd-dev.klinik.net-2026-10-13`.*
