# Verifikasi Numerik FR-10 / FR-11

Tanggal acuan: **hari ini = 2026-03-24**, cutoff 25, periode = **2026-02-25 → 2026-03-24** (28 hari).
Tarif harian Rp 250.000, jadwal 7 hari (semua hari ditagih).

Kalender: Feb 2026 mulai Minggu → Minggu di periode: Mar 1, 8, 15, 22 (4 hari).

## Ringkasan

| # | Kasus | Model lama | Model baru | Status |
|---|---|---|---|---|
| 1 | Bayar 2 hari sekaligus | 2 hari tetap menunggak | 2 hari tertutup | ✅ benar |
| 2 | Bayar hari Minggu (off-day) | **Rp 250.000 hilang** | masuk sebagai 1 hari | ✅ benar |
| 3 | Bayar sebagian | - | - | ✅ benar |
| 4 | Bayar buat 2 bulan | - | Kredit Rp 13.000.000 | ⚠️ **Kredit tidak terdefinisi** |
| 5 | Kontrak backdated | - | Tunggakan Rp 6.000.000 fiktif | ⚠️ **tidak ada jalan keluar** |
| 6 | Pembatalan | - | tunggakan naik 1 hari | ✅ benar |
| 7 | Pembayaran tanggal.reddit | - | tidak dihitung | ✅ benar |

## Kasus 1 — Bayar 2 hari sekaligus

Kontrak A: mulai 2026-01-01, tarif 250.000, 7 hari.

Kewajiban = 28 hari × 250.000 = **Rp 7.000.000**
Pool = 1 setoran Rp 500.000 (20 Mar)
Tunggakan = 7.000.000 − 500.000 = **Rp 6.500.000**
Hari menunggak = **26 hari** (Kritis)

Alokasi kronologis: Rp 500.000 menutup 2 hari tertua (25–26 Feb), menyisakan 27 Feb → 24 Mar.

**Model lama:** `missedDaysList` hanya mencatat hari yang pembayarannya kurang *pada tanggal itu*. Hari dengan overpay tidak masuk daftar, jadi `min(missedDaysList.length, ...)` salah hitung. Bug lama ada di clamp `Math.min`, bukan di pembagian.

## Kasus 2 — Bayar hari Minggu untuk hari Senin (Paling penting)

Kontrak B: mulai 2026-01-01, tarif 250.000, **jadwal 6 hari, hari libur Minggu**.

Minggu di periode: 4 hari (1, 8, 15, 22 Mar)
Hari tagih = 28 − 4 = 24 hari
Kewajiban = 24 × 250.000 = **Rp 6.000.000**

Setoran Rp 250.000 tanggal **1 Mar (Minggu)**.
Pool = Rp 250.000 → Tunggakan = **Rp 5.750.000**, 23 hari menunggak.

**Model lama:** `paidInWindow` hanya bertambah di dalam cabang yang bisa ditagih (`if (!isOffDay && !isServiceExempt)`). Minggu bukan hari tagih, jadi Rp 250.000 **tidak pernah masuk hitungan sama sekali**. Tunggakan tampil Rp 6.000.000.

> Selisihnya Rp 250.000 per hari Minggu. Untuk kontrak 6 hari, 4 Minggu sebulan → **Rp 1.000.000 error per bulan**, berulang tiap bulan.

## Kasus 3 — Bayar sebagian

Setoran Rp 100.000, tarif Rp 250.000.
Tunggakan = 6.900.000. Hari = 28 − floor(100.000/250.000) = **28 hari**.

Dua pendekatan menghasilkan angka yang identik:
- Pembagian nominal: ceil(6.900.000 / 250.000) = 28
- Alokasi ketat: 0 hari tertutup penuh → 28

## KOREKSI TERHADAP KLAIM DI PRD FR-11

PRD menulis bahwa "pembagian nominal adalah pendekatan yang salah". **Itu tidak tepat.**

Dengan tarif harian konstan (keputusan §8), `ceil(tunggakan / tarif)` dan "hitung hari yang belum tertutup" **selalu menghasilkan angka sama**. Matematis: `28 − floor(pool/250.000)` di keduanya.

Konsekuensinya:
- Klaim di FR-11 perlu dilunakkan. Perubahan sebenarnya ada di FR-10 (Pool + alokasi), bukan di cara menghitung hari.
- Contoh yang saya tulis di FR-11 ("Rp 1 juta pada tarif Rp 500.000 berarti dua hari") **benar** dan tidak membuktikan apa-apa salah.
- Yang benar-benar salah di prototipe adalah `Math.min(missedDaysList.length, ...)`, yang meng-*mask* tunggakan saat ada overpay besar.

**Rumus tidak berubah. Justifikasinya berubah.**

## Kasus 4 — Bayar buat 2 bulan ⚠️ TEMUAN BARU

Kontrak A, setoran Rp 20.000.000 tanggal 25 Feb.

Kewajiban periode berjalan (28 hari) = 7.000.000
Tunggakan = max(0, 7.000.000 − 20.000.000) = **Rp 0**
Kredit = **Rp 13.000.000**

**Masalahnya:** periode berikutnya (25 Mar → 24 Apr, 31 hari) punya
`Kewajiban = 31 × 250.000 = Rp 7.750.000`.

Rumus FR-10 untuk periode berikutnya menghitung Pool dari Setoran **yang tanggalnya berada di dalam periode itu**. Setoran 25 Feb tidak termasuk. Jadi:

> **Rp 13.000.000 hilang.**

PRD menulis "Kredit mengurangi Kewajiban periode berikutnya" tapi rumusnya tidak punya input Kredit. Tidak ada yang menyatakan Kredit **disimpan** dan dibawa ke periode berikutnya.

### Dua cara menutup ini

| Cara | Bagaimana | Konsekuensi |
|---|---|---|
| **A. Simpan Kredit** | Kolom `creditBalance` di Kontrak, di-update saat Setoran, Pembatalan, atau Tanggal cutoff berubah | Mengubah Tanggal cutoff jadi kasus yang harus ditangani khusus |
| **B. Simpan alokasi** | Simpan WHICH hari yang ditutup setiap Setoran | Periode berapa pun = "hari tagih yang belum tertutup". Kredit jadi konsekuensi, bukan konsep terpisah |

**Rekomendasi: B.** Lebih benar secara konseptual dan menghindari kasus tepi — ubah cutoff, ubah tarif, alokasi tetap konsisten karena yang disimpan adalah faktanya (pembayaran ini menutup hari ini), bukan hasil hitungan.

Biaya B: pembatalan harus membuka alokasi lalu menghitung ulang. Tidak sulit, tapi harus eksplisit.

## Kasus 5 — Kontrak backdated ⚠️ TEMUAN OPERASIONAL

Pemilik punya kontrak yang berjalan sebelum aplikasi dipakai (FR-7 mengizinkan). Misal Kontrak baru: mulai 1 Jan, tarif 250.000, 7 hari, **tidak ada Setoran tercatat**.

Hari tagih dalam periode = 28
Kewajiban = **Rp 7.000.000**
Tunggakan langsung tampil **Rp 7.000.000, 28 hari, Kritis**

Prencatanan real: mungkin saja sudah lunas 100% tapi aplikasi tidak punya bukti.

PRD membMedal ini ("akan langsung menunjukkan Tunggakan untuk hari-hari yang sudah lewat") tapi **tidak memberi jalan keluar**. Tidak ada impor, tidak ada entri historis massal.

 firsthand: pemilik harus masukkan mungkin 80–100 Setoran satu per satu lewat numpad untuk 14 penyewa. **Hari pertama aplikasi akan terasa seperti WTA yang gagal.**

### Solusi minimal

Satu FR kecil, bukan sistem impor:
> "Tandai lunas sampai [tanggal]" — satu tombol di detail Kontrak yang menghapus seluruh Tunggakan sebelum tanggal tertentu, dengan konfirmasi.

Menutup semua hari yang sudah lewat dalam satu tap. Penuh, bukan parsial. Untuk data yang sudah terlanjur tercatat di tempat lain, justru lebih mudah dicatat di aplikasi daripada di Excel.

## Kasus 6 — Pembatalan

Setoran Rp 250.000 (1 hari) dibatalkan.
Pool turun ke 0, Tunggakan naik dari 6.750.000 ke 7.000.000, hari dari 27 ke 28. ✅

## Kasus 7 — Setoran bertanggal reddit

Setoran 30 Mar saat hari ini 24 Mar. Tidak dihitung (di luar periode). Akan dihitung saat periode berganti. ✅

---

# Kesimpulan

**Rumus intinya benar.** Kasus 1 dan 2 — yang palingchemicals uang — sudah menghasilkan angka benar. Bug per-tanggal di prototipe memang hilang.

**Dua hal perlu diperbaiki sebelum arsitektur:**

1. **FR-10 harus menyatakan bahwa alokasi disimpan.** Tanpa ini, Kredit Rp 13.000.000 di Kasus 4 hilang diam-diam. Ini yang paling penting.

2. **FR-11 perlu dilunakkan.** Klaim "pembagian nominal keliru" tidak tepat — keduanya identik secara matematis. Yang keliru di prototipe adalah clamp `Math.min`.

**Satu tambahan yang layak:**
3. **FR "tandai lunas sampai tanggal"** untuk Kontrak backdated. Tanpa ini, hari pertama aplikasi terasa gagal.

Risiko/data processing: ketiga poin di atas bukan perubahan yang mahal, tapi ketiganya bagus diketahui sekarang — bukan setelah arsitektur dan story ditulis.
