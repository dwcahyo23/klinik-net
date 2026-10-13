---
title: "Klinik.Net — Design System"
status: final
created: 2026-10-13
updated: 2026-10-13
---

# DESIGN.md: Klinik.Net

Identitas visual untuk aplikasi tablet Android. Dokumen ini **mendokumentasikan** design system yang sudah ada di `prototype/shared.css` — bukan menentukan dari nol.

`EXPERIENCE.md` merujuk token di sini dengan `{colors.brand-500}`.

---

## 1. Brand & Style

**Arah:** *warm editorial utility* —Combining a warm-neutral foundation with a single saturated accent and a serif display face.

- **Nuansa:** hangat, tenang, dan tepercaya. Bukan gelap atau tajam seperti aplikasiSPECTIVITY, bukan playful seperti aplikasi consumer.
- **Fondasi:** netral hangat (bukan abu-abu biru), seperti kertas. Ini yang membuat angka rupiah dan teks panjang terasa enak dibaca.
- **Aksen:** satu warna coral/rose yang kuat, dipakai hemat — hanya pada aksi utama, status merkbrand, dan grafik accent.
- **Tipografi:** serif display (Fraunces) untuk judul dan angka besar, sans (Plus Jakarta Sans) untuk segalanya yang lain. Kombinasi ini yang memberikan rasa "editorial" dan membedakan dari dashboard generik.
- **Untuk siapa:** satu orang, sendiri, sepanjang hari. Ketenangan lebih bernilai daripada density.

---

## 2. Colors

### Brand — Coral / Rose

| Token | Hex | Pakai untuk |
|---|---|---|
| `--brand-50` | `#FFF1F4` | Background badge brand, background lighten KPI icon |
| `--brand-100` | `#FFE1E8` | Background avatar brand |
| `--brand-200` | `#FFC7D4` | — |
| `--brand-300` | `#FF9FB5` | Gradient stage, dekoratif |
| `--brand-400` | `#FF6C8E` | — |
| `--brand-500` | `#FF385C` | **Primary.** Tombol primary, focus ring, aksen |
| `--brand-600` | `#E81E45` | Teks pada background `--brand-50` |
| `--brand-700` | `#C01336` | Gradient splash logo |

**Dark theme override:** `--brand-50: #2A1519` (menjadi deep burgundy, bukan pink terang).

### Netral Hangat

| Token | Hex | Pakai untuk |
|---|---|---|
| `--warm-0` | `#FFFFFF` | Background kartu, rail, teks inverse |
| `--warm-25` | `#FDFCFB` | — |
| `--warm-50` | `#F9F7F4` | **Background halaman** |
| `--warm-100` | `#F4F1EC` | Background sunken, secondary button |
| `--warm-200` | `#E8E3DB` | Border |
| `--warm-300` | `#D6CFC4` | Border kuat, scrollbar thumb |
| `--warm-400` | `#A8A096` | — |
| `--warm-500` | `#79726A` | **Teks tersier** (5.2:1 on white ✓) |
| `--warm-600` | `#57514B` | **Teks sekunder** (7.4:1 on white ✓) |
| `--warm-700` | `#3D3833` | — |
| `--warm-800` | `#262220` | — |
| `--warm-900` | `#1A1715` | **Teks primer** |

### Semantic

| Token | Light | `-bg` light | Dark `-bg` |
|---|---|---|---|
| `--success` | `#0E9F6E` | `#E7F6F0` | `#10291F` |
| `--warning` | `#D97706` | `#FEF3C7` | `#2E2411` |
| `--danger` | `#DC2626` | `#FEE2E2` | `#2E1414` |
| `--info` | `#2563EB` | `#DBEAFE` | `#14203A` |

Warna foreground semantic **tidak** berubah di dark theme — hanya background-nya. Ini disengaja: konsistensi semantic lebih penting dari palet yang konsisten.

### Surfaces & Text

| Token | Light | Dark |
|---|---|---|
| `--bg-page` | `--warm-50` | `#14100F` |
| `--bg-card` | `--warm-0` | `#1F1A19` |
| `--bg-sunken` | `--warm-100` | `#2A2321` |
| `--bg-rail` | `--warm-0` | `#1A1615` |
| `--border` | `--warm-200` | `#38302E` |
| `--border-strong` | `--warm-300` | `#4A3F3C` |
| `--text-primary` | `--warm-900` | `#F7F3F0` |
| `--text-secondary` | `--warm-600` | `#C9BFB9` |
| `--text-tertiary` | `--warm-500` | `#A29892` |

Background gelap memakai **hex langsung, bukan scale warm** — netral gelap yang hangat tanpa warna coklat yang tidak deadlines.

### Angka uang

Angka rupiah memakai `{typography.tabular-nums}` — bukan untuk estetika, tapi karena membandingkan nominal tunggakan secara visual adalah inti dari apa yang dilakukan pemilik setiap pagi.

---

## 3. Typography

**Body:** `"Plus Jakarta Sans", -apple-system, "Segoe UI", sans-serif`
**Display:** `"Fraunces", "Plus Jakarta Sans", Georgia, serif`

### Skala

| Token | px | dp (setara) | Pakai untuk |
|---|---|---|---|
| `--fs-xs` | 12 | 12sp | Label, meta, caption — **bukan** body |
| `--fs-sm` | 14 | 14sp | **Minimum body**, label form |
| `--fs-md` | 16 | 16sp | Body default |
| `--fs-lg` | 20 | 20sp | `.page-title` |
| `--fs-xl` | 28 | 28sp | — |
| `--fs-2xl` | 36 | 36sp | — |
| `--fs-3xl` | 48 | 48sp | Hero headline, KPI value |

**Minimum 14sp untuk data dan label** — bukan pilihan estetika, ini batas yang membuat angka tetap terbaca setelah seharian dipakai.

### Weight

`400` body · `500` meta & subtitle · `600` kpi-meta · `700` card-title, field-label, kpi-label, badge · `800` angka besar & tombol.

### Perilaku khas

- `.kpi-label` — uppercase, `letter-spacing: .8px`, `--fs-xs`, weight 700. Label KPI selalu uppercase agar cepat dipindai.
- `.kpi-value` — serif display, besar, tabular-nums. Angka adalah elemen visual utama, bukan label.
- `.page-title` — serif display, weight 700, `letter-spacing: -.3px`.
- **Line-height 1.5** global.

---

## 4. Layout & Spacing

**Skala spacing (basis 4dp):**

`--sp-1: 4` · `--sp-2: 8` · `--sp-3: 12` · `--sp-4: 16` · `--sp-5: 20` · `--sp-6: 24` · `--sp-8: 32` · `--sp-10: 40` · `--sp-12: 48` · `--sp-16: 64`

### Ukuran tetap

| Token | Nilai | Arti |
|---|---|---|
| `--rail-w` | 88px | Lebar nav rail (landscape) |
| `--rail-w-compact` | 72px | Rail mode ringkas |
| `--topbar-h` | 72px | Tinggi top bar |
| `--tap-min` | 48px | **Target sentuh minimum** |

### Grid

- `.kpi-grid` — grid responsif, min 200-260px per kolom, `auto-fit`. Jumlah kolom ditentukan lebar layar, bukan breakpoint tetap.
- `.page-body` — area konten dengan padding `--sp-6`, scrollable.

### Pola master-detail

Landscape memakai pola **master-detail**: daftar di kiri, detail di kanan. Detail selalu terlihat tanpa navigasi — penting karena所有者 sering membandingkan beberapa penyewa sekaligus.

---

## 5. Elevation & Depth

Elevation berupa **bayangan hangat lembut**, bukan bayangan hitam. `--el-1` sampai `--el-3` naik dalam dua lapis (tight + spread):

| Token | Nilai | Pakai untuk |
|---|---|---|
| `--el-1` | `0 1px 2px rgba(38,34,32,.05), 0 2px 8px rgba(38,34,32,.04)` | Rest, button secondary |
| `--el-2` | `0 2px 4px rgba(38,34,32,.05), 0 8px 24px rgba(38,34,32,.07)` | Card hover, modal |
| `--el-3` | `0 8px 16px rgba(38,34,32,.08), 0 20px 48px rgba(38,34,32,.10)` | Overlay berat |
| `--el-brand` | `0 6px 20px rgba(255,56,92,.30)` | **Khusus** button primary |

`--el-brand` — bayangan brand-colored hanya untuk primary button. Efeknya tombol utama terlihat "terangkat" dan glowing lembut; tidak ada tombol lain yang punya efek ini, jadi primacy terbaca tanpa perlu warna tambahan.

**Dark theme:** bayangan jadi lebih gelap dan lebih kuat (`rgba(0,0,0,.4)` → `.55`) karena bayangan ringan hampir tak terlihat di atas background gelap. `--el-brand` tidak di-override.

---

## 6. Shapes

| Token | Nilai | Pakai untuk |
|---|---|---|
| `--r-sm` | 10px | Focus ring, input kecil |
| `--r-md` | 14px | Input, button |
| `--r-lg` | 20px | **Kartu** |
| `--r-xl` | 26px | Card besar, splash |
| `--r-full` | 999px | **Pill** — badge, chip, button icon, avatar, scrollbar |

Kartu memakai radius besar (20px) dengan bayangan halus — ini yang membuat setiap kartu terasa seperti kartu fisik, bukan sekadar kotak. Pill penuh dipakai untuk semua yang kecil dan inline.

**Logo:** rounded square 15px radius dengan gradient coral (`#FF385C` → `#C01336`) dan ikon mobil putih stroke.

---

## 7. Components

### Button

`.btn` (base) · varian: `.btn-primary` `.btn-secondary` `.btn-ghost` `.btn-danger` `.btn-success`
Ukuran: `.btn-lg` (min 58px) · default · `.btn-sm` (min 44px) · `.btn-icon` (48×48, pill) · `.btn-block`

Primary: background `--brand-500`, teks putih, `--el-brand`. Secondary: background card, `--el-1`, border. Ghost: transparan, teks sekunder — untuk aksi tersier.

### Card

`.card` · `.card-outlined` (border, tanpa bayangan) · `.card-pad-lg` (24px) · `.card-pad-xl` (32px) · `.card-hover` · `.card-header` + `.card-title` + `.card-sub`

### KPI Tile

`.kpi-grid` > `.kpi` · `.kpi-label` `.kpi-value` `.kpi-meta` `.kpi-icon`

Icon di pojok kanan atas, background `{colors.semantic-*-bg}`, warna foreground `{colors.semantic-*}`. Nilai serif, tabular-nums.

### Badge

`.badge` + varian: `.badge-success` `.badge-warning` `.badge-danger` `.badge-info` `.badge-brand` `.badge-neutral`

Background `-bg`, foreground solid. Digunakan untuk status tunggakan (lihat EXPERIENCE.md).

### Chip

`.chips` > `.chip` — filter dan pilihan. `.chip` aktif memakai warna brand.

### Field & Input

`.field` > `.field-label` `.field-hint` `.field-error`
`.input` `.select` `textarea.input` · `.input-affix` (ikon di dalam) · `.input-split` (gabungan dua input)
`.switch` > `.switch-track` + `.switch-thumb`
`.segmented` — pilihan mutually exclusive

`.field-error` **hidden by default**, muncul hanya saat error. Ini penting: form yang selalu menampilkan placeholder error akan mengajari pengguna untuk mengabaikannya.

### Search

`.search-bar` — ikon + input, tersedia di setiap halaman.

### Numpad

`.numpad-shell` · `.numpad-display` · `.numpad-grid` > `.numpad-key`

Komponen kunci untuk input nominal. Memakai tombol besar, bukan keyboard sistem — dipakai sambil berdiri atau memegang sesuatu, jadi target sentuh besar lebih penting daripada kecepatan ketik.

### Table & List

`.table-wrap` (overflow-x) · `.list-row` > `.lr-main` `.lr-title` `.lr-sub` `.lr-right`
`.avatar` + `.avatar-sm` (34px) `.avatar-lg` (64px)

`.list-row` dipakai untuk daftar mobile-friendly; `.table-wrap` untuk data dense.

### Navigation

`.nav-rail` (88px, landscape) — ikon + label vertikal, aktif ditandai.
`.bottom-nav` (portrait) — `display: none` di landscape, muncul hanya di portrait.

### Modal, Toast, Empty

`.modal` > `.modal-header` `.modal-body` `.modal-footer`
`.snackbar` — notifikasi transient
`.empty-state` — pesan "belum ada data" dengan aksi

### PIN

`.pin-screen` · `.pin-dots` > `.pin-dot` (`.filled`) · `.pin-grid` (3 kolom, 84px) > `.pin-key` · `.pin-error`

### Tabs & Toolbar

`.top-bar` (72px) > `.top-bar-header` + `.page-title` `.page-subtitle` `.top-bar-actions`

---

## 8. Motion

Semua easing `cubic-bezier(.2,.8,.2,1)` — mulai cepat, melambat halus. Tidak ada bounce atau overshoot.

| Token | Durasi | Pakai untuk |
|---|---|---|
| `--t-fast` | 140ms | Hover, press |
| `--t-base` | 220ms | Transisi standar, masuk |
| `--t-slow` | 300ms | Modal, layar penuh |

Keyframes: `shake` (420ms) untuk PIN error · `fadeUp` untuk masuknya konten · `stagger` untuk KPI muncul berurutan.

**Stagger** pada KPI grid memberi kesan data "mengalir masuk" saat layar dibuka. Ini satu-satunya motion dekoratif di sistem — dipakai sekali per layar, bukan pada setiap interaksi.

---

## 9. Accessibility Floor

Standar visual yang wajib dijaga. Di `EXPERIENCE.md` ada versi perilaku.

- **Kontras teks:** `--text-primary` ≥ 15:1, `--text-secondary` 7.4:1 pada putih, `--text-tertiary` 5.2:1 pada putih. Ketiganya di atas WCAG AA.
- **Dark theme:** background `#14100F` dengan teks `#F7F3F0` — kontras jauh di atas minimum.
- **Target sentuh:** `--tap-min: 48px` adalah minimum absolut, bukan rata-rata. Tidak ada elemen interaktif di bawah ini.
- **Ukuran teks:** minimum `--fs-sm` (14sp) untuk label dan data. `--fs-xs` (12sp) hanya untuk caption yang bukan data.
- **Focus ring:** `3px solid var(--brand-500)` dengan `outline-offset: 2px` dan `border-radius: var(--r-sm)`. Berlaku global via `:focus-visible`.
- **Numerals:** `font-variant-numeric: tabular-nums` pada semua angka rupiah.

---

## 10. Do's and Don'ts

**Do:**
- Pakai `--brand-500` hanya untuk aksi utama dan fokus. Kalau sesuatu warna brand tapi bukan primary, itu membingungkan.
- Pakai angka serif tabular untuk semua nilai nominal.
- Pertahankan rasio kontras yang tercatat di §9 saat menambah token baru.
- Gunakan scale warm untuk netral — jangan biru-abu-abu, karena akan terasa dingin dan tidak cocok dengan brand coral.
- Biarkan `{elevation.el-brand}` hanya untuk tombol primary.

**Don't:**
- Jangan tambahkan warna status kelima. Empat semantic sudah menutup semua kebutuhan di PRD.
- Jangan kurangi `--tap-min` karena "layar tablet sudah besar". 48dp tetap 48dp.
- Jangan pakai warna sebagai satu-satunya pembawa makna. Tunggakan selalu dismissed dengan angka **dan** warna — lihat EXPERIENCE.md.
- Jangan pakai `--fs-xs` untuk angka rupiah.
- Jangan menambahkan motion yang lebih dari 300ms, dan jangan pernah pakai motion yang berulang.

---

*Spines win on conflict: jika mock atau wireframe berbeda dari dokumen ini, dokumen ini yang benar.*