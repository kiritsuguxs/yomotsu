package eu.kanade.tachiyomi.data.coil

import androidx.palette.graphics.Palette
import coil3.Extras
import coil3.getExtra
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.size.Dimension
import coil3.size.Scale
import coil3.size.Size
import coil3.size.isOriginal
import coil3.size.pxOrElse

internal inline fun Size.widthPx(scale: Scale, original: () -> Int): Int {
    return if (isOriginal) original() else width.toPx(scale)
}

internal inline fun Size.heightPx(scale: Scale, original: () -> Int): Int {
    return if (isOriginal) original() else height.toPx(scale)
}

internal fun Dimension.toPx(scale: Scale): Int = pxOrElse {
    when (scale) {
        Scale.FILL -> Int.MIN_VALUE
        Scale.FIT -> Int.MAX_VALUE
    }
}

fun ImageRequest.Builder.cropBorders(enable: Boolean) = apply {
    extras[cropBordersKey] = enable
}

val Options.cropBorders: Boolean
    get() = getExtra(cropBordersKey)

private val cropBordersKey = Extras.Key(default = false)

fun ImageRequest.Builder.customDecoder(enable: Boolean) = apply {
    extras[customDecoderKey] = enable
}

val Options.customDecoder: Boolean
    get() = getExtra(customDecoderKey)

private val customDecoderKey = Extras.Key(default = false)

fun ImageRequest.Builder.webtoonStrips(enable: Boolean) = apply {
    extras[webtoonStripsKey] = enable
}

val Options.webtoonStrips: Boolean
    get() = getExtra(webtoonStripsKey)

private val webtoonStripsKey = Extras.Key(default = false)

/**
 * Calculate the best [Palette.Swatch] from [Palette]
 * Scores swatches by visual presence (population) and color vibrancy (saturation),
 * avoiding dead blacks, washed-out grays, and pure whites while keeping bright gold/yellow,
 * rich reds, greens, blues and purples faithful to the cover.
 */
fun Palette.getBestColor(): Int? {
    if (swatches.isEmpty()) return null

    // Discard near-black, near-white, and desaturated grays
    val candidates = swatches.filter { swatch ->
        val hsl = swatch.hsl
        val sat = hsl[1]
        val lum = hsl[2]
        lum in 0.08f..0.92f && sat >= 0.16f
    }

    if (candidates.isEmpty()) {
        return vibrantSwatch?.rgb ?: dominantSwatch?.rgb
    }

    // Score: population * saturation^2 * lumBalance
    // Favors large colorful areas (like golden wheat, colorful hair/costumes)
    // while giving a strong bonus to vibrant saturation.
    return candidates.maxByOrNull { swatch ->
        val sat = swatch.hsl[1]
        val lum = swatch.hsl[2]
        val lumBalance = 1.0f - kotlin.math.abs(lum - 0.5f) * 0.4f
        swatch.population.toFloat() * sat * sat * lumBalance
    }?.rgb
}

