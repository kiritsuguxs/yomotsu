package eu.kanade.translation.translator

import eu.kanade.tachiyomi.network.await
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class GroqModelInfo(
    val id: String,
    val name: String,
    val contextLength: Int,
    val supportsVision: Boolean,
    val isRecommended: Boolean = false,
)

internal const val DEFAULT_GROQ_MODEL_ID = "qwen/qwen3.8-27b"

internal object GroqModelsCatalog {

    private const val MODELS_URL = "https://api.groq.com/openai/v1/models"

    val curatedDefaults: List<GroqModelInfo> = listOf(
        GroqModelInfo(
            id = "qwen/qwen3.8-27b",
            name = "Qwen 3.8 27B (Recomendado • Visão + Texto)",
            contextLength = 131_072,
            supportsVision = true,
            isRecommended = true,
        ),
        GroqModelInfo(
            id = "openai/gpt-oss-120b",
            name = "GPT-OSS 120B (Texto • Produção)",
            contextLength = 131_072,
            supportsVision = false,
        ),
        GroqModelInfo(
            id = "openai/gpt-oss-20b",
            name = "GPT-OSS 20B (Texto • Rápido)",
            contextLength = 131_072,
            supportsVision = false,
        ),
    )

    private val curatedById = curatedDefaults.associateBy { it.id.lowercase() }

    suspend fun fetchModels(
        apiKey: String,
        client: OkHttpClient = TranslationNetworkHelper.sharedClient,
    ): List<GroqModelInfo> {
        val cleanKey = apiKey.trim()
        if (cleanKey.isEmpty()) {
            return curatedDefaults
        }

        val request = Request.Builder()
            .url(MODELS_URL)
            .header("Authorization", "Bearer $cleanKey")
            .header("Accept", "application/json")
            .get()
            .build()

        val responseBody = client.newCall(request).await().use { response ->
            val body = response.body.string()
            check(response.isSuccessful) { "Groq HTTP ${response.code}" }
            body
        }

        val models = parseGroqModels(responseBody)
        return models.ifEmpty { curatedDefaults }
    }

    internal fun enrichModel(id: String, rawContextLength: Int): GroqModelInfo {
        val curated = curatedById[id.lowercase()]
        val contextWindow = if (rawContextLength > 0) {
            rawContextLength
        } else {
            curated?.contextLength ?: 0
        }
        return GroqModelInfo(
            id = id,
            name = curated?.name ?: formatDisplayName(id),
            contextLength = contextWindow,
            supportsVision = curated?.supportsVision ?: detectsGroqVisionSupport(id),
            isRecommended = id.equals(DEFAULT_GROQ_MODEL_ID, ignoreCase = true),
        )
    }

    internal fun formatContextLength(contextLength: Int): String? {
        if (contextLength <= 0) return null
        return when {
            contextLength >= 1_000_000 -> "${contextLength / 1_000_000}M ctx"
            contextLength >= 1_000 -> "${contextLength / 1_000}k ctx"
            else -> "$contextLength ctx"
        }
    }

    private fun formatDisplayName(id: String): String =
        id.substringAfter('/').ifBlank { id }
}

internal fun parseGroqModels(jsonBody: String): List<GroqModelInfo> {
    val root = JSONObject(jsonBody)
    val data = root.optJSONArray("data") ?: return emptyList()
    val byId = LinkedHashMap<String, GroqModelInfo>()

    for (index in 0 until data.length()) {
        val item = data.optJSONObject(index) ?: continue
        val id = item.optString("id").trim()
        if (id.isEmpty()) continue
        if (item.has("active") && !item.optBoolean("active", true)) continue
        if (!isGroqTranslationCandidate(id)) continue

        val contextWindow = item.optInt("context_window", 0)
            .takeIf { it > 0 }
            ?: item.optInt("context_length", 0).coerceAtLeast(0)

        val base = GroqModelsCatalog.enrichModel(id = id, rawContextLength = contextWindow)
        val supportsVision = base.supportsVision || detectsModalitiesVision(item)

        byId.putIfAbsent(
            id,
            base.copy(supportsVision = supportsVision),
        )
    }

    val curatedOrder = GroqModelsCatalog.curatedDefaults.mapIndexed { idx, model ->
        model.id.lowercase() to idx
    }.toMap()

    return byId.values.sortedWith(
        compareByDescending<GroqModelInfo> { it.isRecommended }
            .thenBy { curatedOrder[it.id.lowercase()] ?: Int.MAX_VALUE }
            .thenByDescending { it.supportsVision }
            .thenByDescending { it.contextLength }
            .thenBy { it.name.lowercase() }
            .thenBy { it.id.lowercase() },
    )
}

internal fun isGroqTranslationCandidate(modelId: String): Boolean {
    val lower = modelId.lowercase()
    return !lower.contains("whisper") &&
        !lower.contains("orpheus") &&
        !lower.contains("prompt-guard") &&
        !lower.contains("llama-guard") &&
        !lower.contains("safeguard") &&
        !lower.contains("-tts") &&
        !lower.startsWith("tts-") &&
        !lower.contains("embedding")
}

private fun detectsGroqVisionSupport(modelId: String): Boolean {
    val lower = modelId.lowercase()
    return lower.contains("vision") ||
        lower.contains("qwen3.8") ||
        lower.contains("qwen2.5-vl") ||
        lower.contains("llama-4-scout") ||
        lower.contains("llama-4-maverick")
}

private fun detectsModalitiesVision(item: JSONObject): Boolean {
    val architecture = item.optJSONObject("architecture") ?: return false
    val modalities = architecture.optJSONArray("input_modalities")
    if (modalities != null) {
        for (i in 0 until modalities.length()) {
            if (modalities.optString(i).equals("image", ignoreCase = true)) {
                return true
            }
        }
    }
    return architecture.optString("modality").contains("image", ignoreCase = true)
}
