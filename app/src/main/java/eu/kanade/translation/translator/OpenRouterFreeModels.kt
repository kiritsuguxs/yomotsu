package eu.kanade.translation.translator

import eu.kanade.tachiyomi.network.await
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class OpenRouterFreeModel(
    val id: String,
    val name: String,
    val contextLength: Int,
    val supportsVision: Boolean,
)

internal const val OPENROUTER_FREE_ROUTER_ID = "openrouter/free"

internal object OpenRouterFreeModelsCatalog {

    private const val MODELS_URL = "https://openrouter.ai/api/v1/models"

    suspend fun fetchLiveFreeModels(
        client: OkHttpClient = TranslationNetworkHelper.sharedClient,
    ): List<OpenRouterFreeModel> {
        val request = Request.Builder()
            .url(MODELS_URL)
            .header("Accept", "application/json")
            .get()
            .build()

        val responseBody = client.newCall(request).await().use { response ->
            val body = response.body.string()
            check(response.isSuccessful) { "OpenRouter HTTP ${response.code}" }
            body
        }

        val models = parseOpenRouterFreeModels(responseBody)
        check(models.isNotEmpty()) { "No free OpenRouter models returned" }
        return models
    }

    internal fun formatContextLength(contextLength: Int): String? {
        if (contextLength <= 0) return null
        return when {
            contextLength >= 1_000_000 -> "${contextLength / 1_000_000}M ctx"
            contextLength >= 1_000 -> "${contextLength / 1_000}k ctx"
            else -> "$contextLength ctx"
        }
    }
}

internal fun parseOpenRouterFreeModels(jsonBody: String): List<OpenRouterFreeModel> {
    val root = JSONObject(jsonBody)
    val data = root.optJSONArray("data") ?: return emptyList()
    val byId = LinkedHashMap<String, OpenRouterFreeModel>()

    for (index in 0 until data.length()) {
        val item = data.optJSONObject(index) ?: continue
        val id = item.optString("id").trim()
        if (id.isEmpty()) continue

        val pricing = item.optJSONObject("pricing")
        if (!isFreePricing(id = id, pricing = pricing)) continue

        val name = item.optString("name").trim().ifEmpty { id }
        val contextLength = item.optInt("context_length", 0).coerceAtLeast(0)
        val architecture = item.optJSONObject("architecture")
        val supportsVision = detectsVisionSupport(architecture)

        byId.putIfAbsent(
            id,
            OpenRouterFreeModel(
                id = id,
                name = name,
                contextLength = contextLength,
                supportsVision = supportsVision,
            ),
        )
    }

    return byId.values.sortedWith(
        compareByDescending<OpenRouterFreeModel> { it.id == "openrouter/free" }
            .thenByDescending { it.supportsVision }
            .thenByDescending { it.contextLength }
            .thenBy { it.name.lowercase() }
            .thenBy { it.id.lowercase() },
    )
}

private fun isFreePricing(id: String, pricing: JSONObject?): Boolean {
    if (id == "openrouter/free") return true
    if (pricing == null) return id.endsWith(":free", ignoreCase = true)

    val promptZero = isZeroPriceValue(pricing.opt("prompt"))
    val completionZero = isZeroPriceValue(pricing.opt("completion"))
    if (promptZero != null && completionZero != null) {
        val imageZero = if (pricing.has("image") && !pricing.isNull("image")) {
            isZeroPriceValue(pricing.opt("image")) == true
        } else {
            true
        }
        return promptZero && completionZero && imageZero
    }
    return id.endsWith(":free", ignoreCase = true)
}

private fun isZeroPriceValue(raw: Any?): Boolean? {
    if (raw == null || raw == JSONObject.NULL) return null
    val parsed = when (raw) {
        is Number -> raw.toDouble()
        is String -> raw.trim().toDoubleOrNull()
        else -> null
    } ?: return null
    if (!parsed.isFinite() || parsed < 0.0) return false
    return parsed == 0.0
}

private fun detectsVisionSupport(architecture: JSONObject?): Boolean {
    if (architecture == null) return false
    val modalities = architecture.optJSONArray("input_modalities")
    if (modalities != null) {
        for (i in 0 until modalities.length()) {
            if (modalities.optString(i).equals("image", ignoreCase = true)) {
                return true
            }
        }
    }
    val modality = architecture.optString("modality")
    return modality.contains("image", ignoreCase = true)
}
