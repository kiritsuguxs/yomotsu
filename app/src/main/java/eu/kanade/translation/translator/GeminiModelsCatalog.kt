package eu.kanade.translation.translator

import eu.kanade.tachiyomi.network.await
import okhttp3.CacheControl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class GeminiModelInfo(
    val id: String,
    val name: String,
    val contextLength: Int,
    val supportsVision: Boolean,
    val isRecommended: Boolean = false,
)

internal const val DEFAULT_GEMINI_MODEL_ID = "gemini-3.8-flash"

internal object GeminiModelsCatalog {

    private const val MODELS_URL = "https://generativelanguage.googleapis.com/v1beta/models"
    private const val MAX_PAGES = 5

    val curatedDefaults: List<GeminiModelInfo> = listOf(
        GeminiModelInfo(
            id = "gemini-3.8-flash",
            name = "Gemini 3.8 Flash (Recomendado • Visão + Texto)",
            contextLength = 1_048_576,
            supportsVision = true,
            isRecommended = true,
        ),
        GeminiModelInfo(
            id = "gemini-3.6-flash",
            name = "Gemini 3.6 Flash (Rápido • Visão + Texto)",
            contextLength = 1_048_576,
            supportsVision = true,
        ),
        GeminiModelInfo(
            id = "gemini-3.1-flash-lite",
            name = "Gemini 3.1 Flash-Lite (Econômico • Visão + Texto)",
            contextLength = 1_048_576,
            supportsVision = true,
        ),
        GeminiModelInfo(
            id = "gemini-3.1-pro-preview",
            name = "Gemini 3.1 Pro Preview (Avançado • Visão + Texto)",
            contextLength = 1_048_576,
            supportsVision = true,
        ),
        GeminiModelInfo(
            id = "gemini-2.5-flash-lite",
            name = "Gemini 2.5 Flash-Lite (Estável • Visão + Texto)",
            contextLength = 1_048_576,
            supportsVision = true,
        ),
    )

    private val curatedById = curatedDefaults.associateBy { it.id.lowercase() }

    internal fun buildModelsRequest(
        cleanApiKey: String,
        pageToken: String? = null,
    ): Request {
        val urlBuilder = MODELS_URL.toHttpUrl().newBuilder()
            .addQueryParameter("pageSize", "100")
        pageToken?.takeIf { it.isNotBlank() }?.let {
            urlBuilder.addQueryParameter("pageToken", it)
        }
        return Request.Builder()
            .url(urlBuilder.build())
            .cacheControl(CacheControl.FORCE_NETWORK)
            .header("x-goog-api-key", cleanApiKey)
            .header("Accept", "application/json")
            .get()
            .build()
    }

    suspend fun fetchModels(
        apiKey: String,
        client: OkHttpClient = TranslationNetworkHelper.sharedClient,
    ): List<GeminiModelInfo> {
        val cleanKey = sanitizeGeminiApiKey(apiKey)
        if (cleanKey.isEmpty()) {
            return curatedDefaults
        }

        val collected = LinkedHashMap<String, GeminiModelInfo>()
        var pageToken: String? = null
        var pagesFetched = 0

        do {
            val request = buildModelsRequest(cleanKey, pageToken)
            val responseBody = client.newCall(request).await().use { response ->
                val body = response.body.string()
                check(response.isSuccessful) {
                    buildGeminiHttpDiagnostic(response.code, body, cleanKey)
                }
                body
            }
            parseGeminiModels(responseBody).forEach { model ->
                collected.putIfAbsent(model.id, model)
            }
            pageToken = JSONObject(responseBody).optString("nextPageToken").trim().ifEmpty { null }
            pagesFetched++
        } while (pageToken != null && pagesFetched < MAX_PAGES)

        return sortGeminiModels(collected.values).ifEmpty { curatedDefaults }
    }

    internal fun enrichModel(
        id: String,
        displayName: String,
        rawInputTokenLimit: Int,
    ): GeminiModelInfo {
        val curated = curatedById[id.lowercase()]
        val contextWindow = if (rawInputTokenLimit > 0) {
            rawInputTokenLimit
        } else {
            curated?.contextLength ?: 0
        }
        return GeminiModelInfo(
            id = id,
            name = curated?.name ?: displayName.trim().ifBlank { id },
            contextLength = contextWindow,
            supportsVision = curated?.supportsVision ?: detectsGeminiVisionSupport(id),
            isRecommended = id.equals(DEFAULT_GEMINI_MODEL_ID, ignoreCase = true),
        )
    }

    internal fun sortGeminiModels(models: Iterable<GeminiModelInfo>): List<GeminiModelInfo> {
        val curatedOrder = curatedDefaults.mapIndexed { idx, model ->
            model.id.lowercase() to idx
        }.toMap()

        return models.sortedWith(
            compareByDescending<GeminiModelInfo> { it.isRecommended }
                .thenBy { curatedOrder[it.id.lowercase()] ?: Int.MAX_VALUE }
                .thenByDescending { it.id.startsWith("gemini", ignoreCase = true) }
                .thenByDescending { it.supportsVision }
                .thenByDescending { it.contextLength }
                .thenBy { it.name.lowercase() }
                .thenBy { it.id.lowercase() },
        )
    }
}

internal fun sanitizeGeminiApiKey(rawKey: String): String =
    rawKey.trim().filter { ch -> ch in '!'..'~' }

internal fun parseGeminiModels(jsonBody: String): List<GeminiModelInfo> {
    val root = JSONObject(jsonBody)
    val modelsArray = root.optJSONArray("models") ?: return emptyList()
    val byId = LinkedHashMap<String, GeminiModelInfo>()

    for (index in 0 until modelsArray.length()) {
        val item = modelsArray.optJSONObject(index) ?: continue
        val rawName = item.optString("name").trim()
        val id = rawName.removePrefix("models/").trim()
        if (id.isEmpty()) continue
        if (!supportsGenerateContent(item)) continue
        if (!isGeminiTranslationCandidate(id)) continue

        val displayName = item.optString("displayName").trim()
        val inputTokenLimit = item.optInt("inputTokenLimit", 0).coerceAtLeast(0)
        byId.putIfAbsent(
            id,
            GeminiModelsCatalog.enrichModel(
                id = id,
                displayName = displayName,
                rawInputTokenLimit = inputTokenLimit,
            ),
        )
    }

    return GeminiModelsCatalog.sortGeminiModels(byId.values)
}

private fun supportsGenerateContent(item: JSONObject): Boolean {
    val methods = item.optJSONArray("supportedGenerationMethods") ?: return false
    for (i in 0 until methods.length()) {
        if (methods.optString(i).equals("generateContent", ignoreCase = true)) {
            return true
        }
    }
    return false
}

internal fun isGeminiTranslationCandidate(modelId: String): Boolean {
    val lower = modelId.lowercase()
    return !lower.contains("embed") &&
        !lower.contains("aqa") &&
        !lower.contains("imagen") &&
        !lower.contains("veo") &&
        !lower.contains("-tts") &&
        !lower.startsWith("tts-") &&
        !lower.contains("-image") &&
        !lower.contains("nano-banana") &&
        !lower.contains("lyria") &&
        !lower.contains("robotics") &&
        !lower.contains("-live") &&
        !lower.startsWith("live-") &&
        !lower.contains("transcribe") &&
        !lower.contains("computer-use")
}

private fun detectsGeminiVisionSupport(modelId: String): Boolean {
    val lower = modelId.lowercase()
    return !lower.contains("1.0-pro") &&
        !lower.contains("pro-001") &&
        !lower.contains("gemma-2")
}
