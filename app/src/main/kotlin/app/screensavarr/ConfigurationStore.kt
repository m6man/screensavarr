package app.screensavarr

import android.content.Context
import androidx.core.content.edit

data class ArrInstanceConfig(
    val baseUrl: String = "",
    val apiKey: String = "",
) {
    val isConfigured get() = baseUrl.isNotBlank() && apiKey.isNotBlank()
}

data class ScreensavarrConfig(
    val sonarr: ArrInstanceConfig = ArrInstanceConfig(),
    val radarr: ArrInstanceConfig = ArrInstanceConfig(),
)

class ConfigurationStore(context: Context) {
    private val preferences = context.getSharedPreferences("screensavarr", Context.MODE_PRIVATE)

    fun load() = ScreensavarrConfig(
        sonarr = ArrInstanceConfig(
            baseUrl = preferences.getString("sonarr_url", "").orEmpty(),
            apiKey = preferences.getString("sonarr_api_key", "").orEmpty(),
        ),
        radarr = ArrInstanceConfig(
            baseUrl = preferences.getString("radarr_url", "").orEmpty(),
            apiKey = preferences.getString("radarr_api_key", "").orEmpty(),
        ),
    )

    fun save(config: ScreensavarrConfig) {
        preferences.edit {
            putString("sonarr_url", config.sonarr.baseUrl.trim().trimEnd('/'))
            putString("sonarr_api_key", config.sonarr.apiKey.trim())
            putString("radarr_url", config.radarr.baseUrl.trim().trimEnd('/'))
            putString("radarr_api_key", config.radarr.apiKey.trim())
        }
    }
}