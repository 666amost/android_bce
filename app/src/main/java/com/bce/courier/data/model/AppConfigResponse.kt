package com.bce.courier.data.model

import kotlinx.serialization.Serializable

@Serializable
data class AppConfigResponse(
    val app_id: String,
    val enabled: Boolean,
    val banner: BannerConfig,
    val versioning: VersioningConfig,
    val features: FeaturesConfig,
    val pickup: PickupConfig? = null
)

@Serializable
data class BannerConfig(
    val enabled: Boolean,
    val title: String,
    val message: String
)

@Serializable
data class VersioningConfig(
    val min_version_code: Int,
    val force_update: Boolean,
    val latest_version_code: Int? = null,
    val latest_version_name: String? = null,
    val apk_url: String? = null,
    val changelog: String? = null
)

@Serializable
data class FeaturesConfig(
    val scan_awb: Boolean,
    val photo_pod: Boolean,
    val location_tracking: Boolean
)

@Serializable
data class PickupConfig(
    val rev: Int? = null,
    val agent_list_central: List<String>? = null,
    val kota_wilayah_pusat: Map<String, List<String>>? = null,
    val harga_per_kg_map: Map<String, Int>? = null
)
