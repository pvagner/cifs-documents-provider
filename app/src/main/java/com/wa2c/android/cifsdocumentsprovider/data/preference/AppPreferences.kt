package com.wa2c.android.cifsdocumentsprovider.data.preference

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.wa2c.android.cifsdocumentsprovider.common.utils.logE
import com.wa2c.android.cifsdocumentsprovider.common.values.HostSortType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Preference repository
 */
@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val preferences: SharedPreferences = getPreferences(context)

    /**
     * CIFS settings
     */
    var cifsSettings: List<CifsSetting>
        get() {
            return try {
                Json.decodeFromString(preferences.getString(PREFKEY_CIFS_SETTINGS, "{}")!!)
            } catch(e: Exception) {
                logE(e)
                emptyList()
            }
        }
        set(value) {
            try {
                preferences.edit { putString(PREFKEY_CIFS_SETTINGS, Json.encodeToString(value)) }
            } catch (e: Exception) {
                logE(e)
            }
        }

    /**
     * Host sort type
     */
    var hostSortType: HostSortType
        get() {
            return HostSortType.findByValueOrDefault(preferences.getInt(PREFKEY_HOST_SORT_TYPE, -1))
        }
        set(value) {
            preferences.edit { putInt(PREFKEY_HOST_SORT_TYPE, value.intValue) }
        }

    companion object {
        private const val PREFKEY_CIFS_SETTINGS = "prefkey_cifs_settings"
        private const val PREFKEY_HOST_SORT_TYPE = "prefkey_host_sort_type"

        fun getPreferences(context: Context): SharedPreferences {
            return context.getSharedPreferences("App", Context.MODE_PRIVATE)
        }

    }

}
