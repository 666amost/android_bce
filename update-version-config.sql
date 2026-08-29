-- ============================================
-- UPDATE Config untuk Versi Baru
-- ============================================
-- Jalankan query ini setiap kali upload APK versi baru ke Supabase
-- GANTI nilai latest_version_code, latest_version_name, apk_url sesuai versi baru

UPDATE config
SET versioning = versioning || jsonb_build_object(
    'latest_version_code', 3,
    'latest_version_name', 'v1.0.2',
    'apk_url', '<SUPABASE_APK_URL>',
    'changelog', 'Perbaikan install flow, optimasi UI'
),
updated_at = CURRENT_TIMESTAMP
WHERE app_id = 'bce_courier_app';

-- Verifikasi hasil update
SELECT
    app_id,
    jsonb_pretty(versioning) as versioning,
    updated_at
FROM config
WHERE app_id = 'bce_courier_app';

