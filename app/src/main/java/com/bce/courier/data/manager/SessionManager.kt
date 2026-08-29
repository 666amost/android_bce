package com.bce.courier.data.manager

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = createPrefs(context)

    companion object {
        private const val TAG = "SessionManager"
        private const val PREF_NAME = "courier_session"
        private const val FALLBACK_PREF_NAME = "courier_session_fallback"
    }

    private fun createPrefs(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREF_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "EncryptedSharedPreferences failed, using fallback", e)
            try {
                context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().clear().apply()
            } catch (_: Exception) {}
            context.getSharedPreferences(FALLBACK_PREF_NAME, Context.MODE_PRIVATE)
        }
    }

    fun saveSession(token: String, email: String, userId: String, fullName: String?, refreshToken: String? = null) {
        prefs.edit().apply {
            putString("access_token", token)
            putString("user_email", email)
            putString("user_id", userId)
            putString("full_name", fullName)
            putLong("session_saved_at", System.currentTimeMillis())
            if (refreshToken != null) putString("refresh_token", refreshToken)
            apply()
        }
    }

    fun saveRememberedCredentials(email: String, password: String, remember: Boolean) {
        prefs.edit().apply {
            putBoolean("remember_me", remember)
            if (remember) {
                putString("remember_email", email)
                putString("remember_password", password)
            } else {
                remove("remember_email")
                remove("remember_password")
            }
            apply()
        }
    }

    fun getRememberedEmail(): String? = prefs.getString("remember_email", null)
    fun getRememberedPassword(): String? = prefs.getString("remember_password", null)
    fun isRememberMeEnabled(): Boolean = prefs.getBoolean("remember_me", false)

    fun getAccessToken(): String? = try { prefs.getString("access_token", null) } catch (_: Exception) { null }
    fun getRefreshToken(): String? = try { prefs.getString("refresh_token", null) } catch (_: Exception) { null }
    fun getUserEmail(): String? = try { prefs.getString("user_email", null) } catch (_: Exception) { null }
    fun getUserId(): String? = try { prefs.getString("user_id", null) } catch (_: Exception) { null }
    fun getFullName(): String? = try { prefs.getString("full_name", null) } catch (_: Exception) { null }

    private fun clean(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }

    private fun emailLocalPart(email: String?): String? {
        val cleanEmail = clean(email) ?: return null
        return cleanEmail.substringBefore("@").trim().takeIf { it.isNotEmpty() }
    }

    fun getBestCourierName(defaultName: String = "Kurir"): String =
        clean(getFullName()) ?: emailLocalPart(getUserEmail()) ?: defaultName

    fun saveFullName(name: String?) {
        val valid = name?.trim()?.takeIf { it.isNotEmpty() } ?: return
        prefs.edit().putString("full_name", valid).apply()
    }

    fun updateAccessToken(newToken: String, newRefreshToken: String? = null) {
        prefs.edit().apply {
            putString("access_token", newToken)
            putLong("session_saved_at", System.currentTimeMillis())
            if (newRefreshToken != null) putString("refresh_token", newRefreshToken)
            apply()
        }
    }

    fun clearSession() {
        prefs.edit().apply {
            remove("access_token")
            remove("refresh_token")
            remove("user_email")
            remove("user_id")
            remove("full_name")
            remove("session_saved_at")
            apply()
        }
    }

    fun getOverduePopupDismissedDate(): String? = prefs.getString("overdue_popup_dismissed_date", null)
    fun getOverduePopupDismissedCount(): Int = prefs.getInt("overdue_popup_dismissed_count", 0)

    fun setOverduePopupDismissed(dateKey: String, overdue14dCount: Int) {
        prefs.edit().apply {
            putString("overdue_popup_dismissed_date", dateKey)
            putInt("overdue_popup_dismissed_count", overdue14dCount)
            apply()
        }
    }

    fun clearRememberedCredentials() {
        prefs.edit().apply {
            remove("remember_me")
            remove("remember_email")
            remove("remember_password")
            apply()
        }
    }

    fun isLoggedIn(): Boolean = getAccessToken() != null && getUserId() != null
}
