package eu.kanade.presentation.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

private val formatter = DecimalFormat(
    "#.###",
    DecimalFormatSymbols(Locale.ROOT),
)

private val SEASON_EPISODE_NAME_REGEX = Regex(
    """(?i)(?:\bs\s*(\d{1,2})[\s._-]*e\s*(\d{1,3})\b|\b(\d{1,2})\s*x\s*(\d{1,3})\b|(?:temporada|season)\s*(\d{1,2})[^0-9]{0,20}ep(?:is[oó]dio|isode)?\.?\s*(\d{1,3})\b)""",
)

fun formatChapterNumber(chapterNumber: Double): String {
    return formatter.format(chapterNumber)
}

private data class DecodedSeasonEpisode(
    val season: Int,
    val episode: Int,
)

private fun tryDecodeSeasonEpisode(
    episodeNumber: Double,
    episodeName: String? = null,
): DecodedSeasonEpisode? {
    if (episodeNumber >= 0.0 && episodeNumber.isFinite()) {
        val season = floor(episodeNumber).toInt()
        val fractional = episodeNumber - season
        if (season in 0..99 && fractional > 0.0005) {
            val scaledThousandths = fractional * 1000.0
            val roundedEpisode = scaledThousandths.roundToInt()

            val nameMatch = episodeName?.let { SEASON_EPISODE_NAME_REGEX.find(it) }
            if (nameMatch != null) {
                val parsedSeason = (
                    nameMatch.groupValues[1].ifEmpty { nameMatch.groupValues[3] }
                        .ifEmpty { nameMatch.groupValues[5] }
                    ).toIntOrNull()
                val parsedEpisode = (
                    nameMatch.groupValues[2].ifEmpty { nameMatch.groupValues[4] }
                        .ifEmpty { nameMatch.groupValues[6] }
                    ).toIntOrNull()
                if (
                    parsedSeason != null &&
                    parsedEpisode != null &&
                    parsedSeason == season &&
                    abs(scaledThousandths - parsedEpisode) < 0.5
                ) {
                    return DecodedSeasonEpisode(parsedSeason, parsedEpisode)
                }
            }

            // Extensions encode season + episode / 1000 (e.g., 1.007 = S1E07, 2.018 = S2E18).
            // Only .001-.099 is treated as an encoded episode automatically so real fractional
            // episodes like 12.5 or 1.25 keep formatting as decimals.
            if (roundedEpisode in 1..99 && abs(scaledThousandths - roundedEpisode) < 0.05) {
                return DecodedSeasonEpisode(season, roundedEpisode)
            }
        }
    }
    return null
}

fun decodeEpisodeNumber(
    episodeNumber: Double,
    episodeName: String? = null,
): Double {
    return tryDecodeSeasonEpisode(episodeNumber, episodeName)?.episode?.toDouble() ?: episodeNumber
}

fun normalizeMergedEpisodeNumber(
    episodeNumber: Double,
    episodeName: String? = null,
): Double {
    val decoded = tryDecodeSeasonEpisode(episodeNumber, episodeName) ?: return episodeNumber
    return when {
        decoded.season == 0 -> episodeNumber
        decoded.season == 1 -> decoded.episode.toDouble()
        else -> decoded.season * 10_000.0 + decoded.episode
    }
}

fun formatEpisodeNumber(
    episodeNumber: Double,
    episodeName: String? = null,
): String {
    val decoded = tryDecodeSeasonEpisode(episodeNumber, episodeName)
    if (decoded != null) {
        return formatDecodedSeasonEpisode(decoded.season, decoded.episode)
    }
    return formatChapterNumber(episodeNumber)
}

private fun formatDecodedSeasonEpisode(season: Int, episode: Int): String {
    return if (season > 1) "$episode (S$season)" else episode.toString()
}
