package app.screensavarr

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class ArtworkUrl(
	val url: String,
	val requiresApiKey: Boolean,
)

data class CatalogItem(
	val title: String,
	val backdrop: ArtworkUrl,
	val logo: ArtworkUrl? = null,
)

class ArrApiClient {
	private val json = Json { ignoreUnknownKeys = true }
	private val logoCoverTypes = setOf("clearlogo", "clear_logo", "logo")

	suspend fun testConnection(config: ArrInstanceConfig): Result<Unit> = runCatching {
		request(config, "api/v3/system/status")
	}.map { }

	suspend fun loadSonarr(config: ArrInstanceConfig): List<CatalogItem> = loadItems(config, "api/v3/series")

	suspend fun loadRadarr(config: ArrInstanceConfig): List<CatalogItem> = loadItems(config, "api/v3/movie")

	private suspend fun loadItems(config: ArrInstanceConfig, endpoint: String): List<CatalogItem> = withContext(Dispatchers.IO) {
		if (!config.isConfigured) return@withContext emptyList()
		val response = request(config, endpoint)
		json.parseToJsonElement(response).jsonArray.mapNotNull { entry ->
			val item = entry.jsonObject
			val title = item["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
			val images = item["images"] as? JsonArray ?: return@mapNotNull null
			val backdrop = images.firstOrNull { image ->
				image.jsonObject["coverType"]?.jsonPrimitive?.content == "fanart"
			} ?: images.firstOrNull { image ->
				image.jsonObject["coverType"]?.jsonPrimitive?.content == "banner"
			} ?: return@mapNotNull null
			val logo = images.firstOrNull { image ->
				image.jsonObject["coverType"]?.jsonPrimitive?.contentOrNull
					?.lowercase(Locale.ROOT) in logoCoverTypes
			}?.jsonObject
			CatalogItem(
				title = title,
				backdrop = imageUrl(config, backdrop.jsonObject) ?: return@mapNotNull null,
				logo = logo?.let { imageUrl(config, it) },
			)
		}
	}

	private fun imageUrl(config: ArrInstanceConfig, image: JsonObject): ArtworkUrl? {
		val localPath = image["url"]?.jsonPrimitive?.contentOrNull
		if (!localPath.isNullOrBlank()) {
			return ArtworkUrl("${config.baseUrl.trimEnd('/')}/${localPath.trimStart('/')}", requiresApiKey = true)
		}
		return image["remoteUrl"]?.jsonPrimitive?.contentOrNull?.let { ArtworkUrl(it, requiresApiKey = false) }
	}

	private suspend fun request(config: ArrInstanceConfig, endpoint: String): String = withContext(Dispatchers.IO) {
		val url = URL("${config.baseUrl.trimEnd('/')}/${endpoint.trimStart('/')}")
		val connection = (url.openConnection() as HttpURLConnection).apply {
			requestMethod = "GET"
			connectTimeout = 10_000
			readTimeout = 20_000
			setRequestProperty("X-Api-Key", config.apiKey)
			setRequestProperty("Accept", "application/json")
		}
		try {
			val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
			val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
			check(connection.responseCode in 200..299) { "Request failed with HTTP ${connection.responseCode}: $response" }
			response
		} finally {
			connection.disconnect()
		}
	}
}