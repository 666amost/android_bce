-- ============================================
-- UPDATE Config Pickup (Kota/Wilayah + Harga)
-- ============================================
-- Jalankan query ini untuk update master data pickup tanpa update APK.

ALTER TABLE public.config
  ADD COLUMN IF NOT EXISTS pickup jsonb NOT NULL DEFAULT '{}'::jsonb;

UPDATE public.config
SET pickup = jsonb_build_object(
  'rev', COALESCE((pickup->>'rev')::int, 0) + 1,
  'agent_list_central', COALESCE(pickup->'agent_list_central', '[]'::jsonb),
  'kota_wilayah_pusat', '{
    "bangka": ["Pangkal Pinang","Sungailiat","Belinyu","Jebus","Koba","Toboali","Mentok"],
    "kalimantan barat": ["Pontianak","Sungai Pinyuh","Mempawah","Singkawang","Pemangkat","Sambas"],
    "belitung": ["Tj Pandan"],
    "bali": ["Denpasar"]
  }'::jsonb,
  'harga_per_kg_map', '{
    "Pangkal Pinang": 35000,
    "Sungailiat": 37000,
    "Koba": 38000,
    "Toboali": 39000,
    "Belinyu": 35000,
    "Jebus": 35000,
    "Mentok": 39000,
    "Tj Pandan": 28000,
    "Pontianak": 32000,
    "Sungai Pinyuh": 35000,
    "Mempawah": 35000,
    "Singkawang": 35000,
    "Pemangkat": 37000,
    "Sambas": 39000,
    "Denpasar": 30000
  }'::jsonb
),
updated_at = CURRENT_TIMESTAMP
WHERE app_id = 'bce_courier_app';

-- Verifikasi
SELECT
  app_id,
  jsonb_pretty(pickup) as pickup,
  updated_at
FROM public.config
WHERE app_id = 'bce_courier_app';

