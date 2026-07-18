package app.screensavarr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.service.dreams.DreamService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

internal sealed interface DreamContent {
    data object Brand : DreamContent
    data object Unconfigured : DreamContent
    data class Showcase(val title: String, val backdrop: Bitmap, val logo: Bitmap?) : DreamContent
}

internal class DreamPresenter(service: DreamService) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val store = ConfigurationStore(service.applicationContext)
    private val client = ArrApiClient()
    private val _content = MutableStateFlow<DreamContent>(DreamContent.Brand)
    val content = _content.asStateFlow()

    fun start() = scope.launch {
        delay(2.seconds)
        while (isActive) {
            val config = store.load()
            val items = supervisorScope {
                listOf(
                    async { runCatching { client.loadSonarr(config.sonarr) }.getOrDefault(emptyList()) },
                    async { runCatching { client.loadRadarr(config.radarr) }.getOrDefault(emptyList()) },
                ).flatMap { it.await() }.shuffled(Random.Default)
            }
            if (items.isEmpty()) {
                _content.value = DreamContent.Unconfigured
                delay(60.seconds)
                continue
            }
            for (item in items) {
                if (!isActive) break
                val backdrop = loadImage(item.backdrop, config) ?: continue
                _content.value = DreamContent.Showcase(item.title, backdrop, item.logo?.let { loadImage(it, config) })
                delay(30.seconds)
            }
        }
    }

    fun stop() = scope.cancel()

    private suspend fun loadImage(artwork: ArtworkUrl, config: ScreensavarrConfig): Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                val apiKey = when {
                    !artwork.requiresApiKey -> null
                    artwork.url.startsWith(config.sonarr.baseUrl) -> config.sonarr.apiKey
                    artwork.url.startsWith(config.radarr.baseUrl) -> config.radarr.apiKey
                    else -> null
                }
                val connection = (URL(artwork.url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 20_000
                    if (apiKey != null) setRequestProperty("X-Api-Key", apiKey)
                }
                try {
                    if (connection.responseCode !in 200..299) null
                    else connection.inputStream.use(BitmapFactory::decodeStream)
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
        }
}