package eu.kanade.translation.model

import kotlin.math.abs

/** Final automatic-translation gate; rejected blocks remain manual candidates. */
internal object TranslationRecoveryPolicy {

    private val watermarkRegex = Regex(
        pattern = """(?i)\b(rtmth|scanlations?|(?:realm|flame|asura|void|reaper|luminous|leviatans?|reset|drake|immortal)\s*[-_]?\s*scans?|discord(?:\.gg)?/[a-z0-9_]+|www\.\S+\.(?:com|net|org|io|me|gg)|https?://\S+)\b""",
    )

    private val urlPattern = Regex(
        """(?i)\b(https?://|www\.|discord\.gg/|[a-z0-9_-]+\.(com|net|org|xyz|io|to|me|gg|tv|site|online|top))\b""",
    )

    private val scanlatorKeywords = listOf(
        "translated by",
        "translated and edited",
        "proofreader",
        "typesetter",
        "cleaner & typesetter",
        "cleaner and typesetter",
        "cleaner / typesetter",
        "raw provider",
        "scanlation",
        "scanlations",
        "read first on",
        "read first at",
        "join our discord",
        "discord server",
        "to be continued",
        "end of chapter",
        "next chapter",
        "all rights reserved",
        "do not repost",
        "support the author",
        "support the creator",
        "author's note",
        "authors note",
    )

    fun isWatermarkOrCredit(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed.equals("rtmth", ignoreCase = true) || trimmed.contains("rtmth", ignoreCase = true)) return true
        if (watermarkRegex.containsMatchIn(trimmed) || urlPattern.containsMatchIn(trimmed)) return true
        val lower = trimmed.lowercase()
        if (scanlatorKeywords.any { lower.contains(it) }) return true
        return false
    }

    fun isTranslatable(block: TranslationBlock): Boolean {
        if (isWatermarkOrCredit(block.text)) return false
        val layout = block.layoutRegion ?: block.defaultLayoutRegion()
        val widthToHeight = layout.width / layout.height.coerceAtLeast(1f)
        val compact = block.text.filterNot(Char::isWhitespace)
        val letters = compact.count(Char::isLetter)
        val words = block.text.lowercase().split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }

        if (words.isEmpty() || words.all { it in soundEffects } ||
            words.any { word -> word.length >= 4 && word.toSet().size <= 2 }
        ) {
            return false
        }

        if (widthToHeight >= 0.38f) {
            if (block.balloonDetected) {
                return letters >= 2
            }
            if (block.backgroundColor != null) {
                return letters >= 4 || words.size >= 2
            }
            return letters >= 2
        }
        return hasStrongNarrowDialogueEvidence(block, layout)
    }

    private fun hasStrongNarrowDialogueEvidence(block: TranslationBlock, layout: TranslationRegion): Boolean {
        if (isWatermarkOrCredit(block.text)) return false
        val compact = block.text.filterNot(Char::isWhitespace)
        val letters = compact.count(Char::isLetter)
        val words = block.text.lowercase().split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }
        if (letters < 3 || words.isEmpty()) return false
        if (words.all { it in soundEffects } || words.any { word -> word.length >= 4 && word.toSet().size <= 2 }) {
            return false
        }
        if (!block.balloonDetected || block.backgroundColor == null ||
            block.geometryVersion < CURRENT_TRANSLATION_GEOMETRY_VERSION ||
            !block.angle.isFinite() || abs(block.angle) > 12f ||
            !block.width.isFinite() || !block.height.isFinite() || block.width <= 0f || block.height <= 0f ||
            layout.width / layout.height !in 0.25f..0.38f
        ) return false
        val cleanup = block.cleanupRegion ?: return false
        val source = block.sourceRegion()
        // Containment alone accepts giant boxes; IoU also demands a close glyph eraser.
        if (source.overlapFraction(layout) < 0.98f || source.overlapFraction(cleanup) < 0.98f ||
            cleanup.overlapFraction(layout) < 0.98f || source.intersectionOverUnion(cleanup) < 0.55f
        ) return false

        val lines = block.text.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return false
        val lineHeight = block.height / lines.size
        if (!block.symHeight.isFinite() || block.symHeight !in lineHeight * 0.4f..lineHeight * 1.4f ||
            !block.symWidth.isFinite() || block.symWidth <= 0f || block.symWidth > block.width * 0.6f
        ) return false

        return true
    }

    private val soundEffects = setOf(
        "bam", "bang", "boom", "crash", "thud", "thump", "rumble", "whoosh", "swish", "crack",
        "smash", "pow", "bzzz", "buzz", "clang", "clank", "splash", "tap", "ha", "ah",
        "burrak", "burr", "wham", "gasp", "pant", "snort", "sigh",
    )
}
