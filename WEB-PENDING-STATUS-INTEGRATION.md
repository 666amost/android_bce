# Integrasi Status Pending pada Web BCE Express

## Tujuan

Web harus membaca event berikut sebagai status operasional tersendiri:

```json
{
  "status": "pending",
  "location": "Neroktog, Kecamatan Pinang, Banten",
  "reason_code": "rescheduled",
  "reason_note": null,
  "photo_url": "https://...",
  "created_at": "2026-08-29T11:35:59.742081+00:00"
}
```

`pending` tidak boleh dipetakan menjadi `processed`/“Diproses”. Status ini berarti proses antar sudah dicoba atau ditunda dan memerlukan tindak lanjut.

## File web yang perlu diubah

### 1. `lib/db.ts`

Tambahkan `pending` ke tipe status dan field alasan:

```ts
export type ShipmentStatus =
  | "processed"
  | "shipped"
  | "in_transit"
  | "out_for_delivery"
  | "pending"
  | "delivered"
  | "exception"

export interface ShipmentHistory {
  // field lama tetap
  reason_code?: PendingReasonCode | null
  reason_note?: string | null
}

export type PendingReasonCode =
  | "recipient_unavailable"
  | "address_not_found"
  | "recipient_rejected"
  | "rescheduled"
  | "parcel_issue"
  | "other"
```

Tambahkan juga pada `Shipment`:

```ts
pending_reason_code?: PendingReasonCode | null
pending_reason_note?: string | null
pending_at?: string | null
```

### 2. `lib/database.types.ts`

Regenerasi tipe Supabase atau tambahkan field berikut pada `Row`, `Insert`, dan `Update`:

- `shipments`: `pending_reason_code`, `pending_reason_note`, `pending_at`.
- `shipment_history`: `reason_code`, `reason_note`.

Semua field tersebut nullable. `status`/`current_status` tetap string bila tipe ini memang mengikuti hasil generator Supabase.

### 3. `app/api/tracking/route.ts`

Query saat ini sudah memakai `select("*")`, sehingga field alasan ikut terbaca. Pertahankan urutan history:

```ts
.order("created_at", { ascending: false })
```

Pastikan entri manifest buatan (`processed`) tidak menggantikan event `pending` terbaru. Setelah penggabungan history, urutkan berdasarkan waktu seperti sekarang dan tentukan status aktif dari `shipment.current_status`, bukan dari status hasil fallback manifest.

Untuk endpoint publik, jangan mengirim identitas internal yang tidak diperlukan. `notes` seperti `Updated by admin amos` sebaiknya disaring di API atau tidak dirender pada public view. `reason_code` dan `reason_note` tetap dikirim karena itu informasi operasional yang relevan.

### 4. `components/tracking-results.tsx`

Masalah yang sekarang terjadi: `pending` tidak ada di `STATUS_ORDER`. Fungsi `getStepIndex()` lalu mengembalikan indeks `0`, sehingga tampilan terlihat kembali ke “Diproses”.

Jangan menaruh `pending` sebagai tahap progres normal setelah `out_for_delivery`. Jadikan Pending sebagai keadaan cabang/exception, sementara progres terakhir tetap berada di “Antar ke Tujuan”.

```ts
const isPending = currentStatus === "pending" || currentStatus === "shipped"

function getStepIndex(status: string): number {
  if (status === "pending") return STATUS_ORDER.indexOf("out_for_delivery")
  const idx = STATUS_ORDER.indexOf(status as StatusKey)
  return idx === -1 ? 0 : idx
}
```

`shipped` dibaca sebagai legacy Pending selama masa transisi, bukan sebagai “Dalam Perjalanan”. Bila data `shipped` lama di web masih berarti benar-benar dikirim antarcabang, selesaikan migrasi datanya dahulu sebelum mengaktifkan alias ini secara global.

Tambahkan label dan warna:

```ts
const PENDING_REASON_LABEL: Record<PendingReasonCode, string> = {
  recipient_unavailable: "Penerima tidak berada di tempat",
  address_not_found: "Alamat tidak ditemukan",
  recipient_rejected: "Paket ditolak penerima",
  rescheduled: "Pengantaran dijadwalkan ulang",
  parcel_issue: "Paket bermasalah",
  other: "Kendala lainnya",
}
```

- Judul status publik: **Pengantaran Tertunda**.
- Badge: oranye/amber dengan ikon peringatan atau jam.
- Deskripsi utama: label dari `reason_code`.
- Jika `reason_note` ada, tampilkan sebagai keterangan kedua.
- Jangan tampilkan `reason_code` mentah kepada pengguna.
- Foto bukti tetap bisa dibuka.

Pada riwayat, lokasi harus mengutamakan alamat teks:

```ts
const displayLocation = item.location?.trim()
  ? item.location
  : hasCoordinates(item.latitude, item.longitude)
    ? formatCoordinates(item.latitude, item.longitude)
    : null
```

Kode sekarang mengutamakan koordinat sehingga screenshot admin menampilkan `-6.203668, 106.689236` walaupun alamat `Neroktog, Kecamatan Pinang, Banten` tersedia. Koordinat hanya informasi sekunder atau tautan peta, bukan pengganti alamat.

Untuk notes:

```tsx
{!isPublicView && item.notes && <p>{item.notes}</p>}
```

Pada public view, tampilkan alasan Pending yang sudah diterjemahkan, bukan `Updated by ...`.

## Tampilan admin

Pada mode admin (`isPublicView === false`):

- Judul event tetap `Pending`.
- Tampilkan chip alasan, misalnya `Dijadwalkan ulang`.
- Tampilkan `reason_note` bila ada.
- Tampilkan `location` sebagai lokasi utama.
- Koordinat boleh menjadi teks kecil/tombol “Buka peta”.
- Notes internal dan pelaku update boleh tetap terlihat.

Jangan mengubah history lama: event `out_for_delivery` tetap muncul di bawah event `pending`, lalu event `delivered` ditambahkan di atasnya bila pengiriman berhasil.

## Dashboard web kurir

Query lama di `courier-dashboard.tsx` dan `courier-dashboard-with-update.tsx` hanya membaca `out_for_delivery`/`shipped`. Samakan dengan aturan aplikasi Android:

- Assigned: `current_status = out_for_delivery` dan umur assignment kurang dari 24 jam.
- Pending: `current_status IN (pending, shipped)`, ditambah `out_for_delivery` yang umur assignment-nya sudah 24 jam.
- Perhitungan umur memakai timestamp assignment (`updated_at`, fallback `created_at`) sebagai rolling 24 jam, bukan pergantian tanggal kalender.
- Pengelompokan umur hanya untuk tampilan; jangan melakukan update otomatis ke DB.

## Acceptance test web

1. AWB dengan `current_status = pending` tidak tampil sebagai “Diproses”.
2. Progress tetap mencapai “Antar ke Tujuan” dan status aktif menunjukkan “Pengantaran Tertunda”.
3. `rescheduled` tampil sebagai “Pengantaran dijadwalkan ulang”.
4. Alamat teks tampil sebelum koordinat.
5. Notes internal tidak tampil pada pelacakan publik, tetapi masih tampil pada admin.
6. Event `out_for_delivery`, `pending`, dan `delivered` tetap tampil berurutan sesuai `created_at`.
7. `reason_note = null` tidak menghasilkan baris kosong.
8. Foto pada event Pending tetap dapat dibuka.
9. `out_for_delivery` berumur kurang dari 24 jam tetap Assigned; setelah 24 jam tampil Pending tanpa mutasi DB.
