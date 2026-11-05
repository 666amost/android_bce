package com.bce.courier.data.local

import android.content.Context
import com.bce.courier.data.model.PickupFormState
import com.bce.courier.data.model.ManifestPayload
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PickupDraftStore(context: Context, courierId: String) {
    private val prefs = context.applicationContext.getSharedPreferences("pickup_drafts", Context.MODE_PRIVATE)
    private val key = "draft_$courierId"

    fun load(): PickupFormState = runCatching {
        val saved = prefs.getString(key, null) ?: return@runCatching PickupFormState()
        Json.decodeFromString<PickupFormState>(saved).withCurrentDate()
    }.getOrDefault(PickupFormState())

    fun save(form: PickupFormState) {
        prefs.edit().putString(key, Json.encodeToString(form)).apply()
    }

    fun clear() { prefs.edit().remove(key).apply() }

    fun markSubmission(payload: ManifestPayload) {
        val form = load().copy(awbNo = payload.awbNo, awbDate = payload.awbDate, submissionAttempted = true)
        check(prefs.edit().putString(key, Json.encodeToString(form)).commit()) {
            "Draf belum dapat disimpan. Silakan coba kembali."
        }
    }
}
