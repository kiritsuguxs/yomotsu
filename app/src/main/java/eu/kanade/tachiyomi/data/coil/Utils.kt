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
 * Discards dead/dull/black/white background colors and favors vibrant tones.
 * @author Jays2Kings
 */
fun Palette.getBestColor(): Int? {
    val vibPopulation = vibrantSwatch?.population ?: -1
    val domSat = dominantSwatch?.hsl?.get(1) ?: 0f
    val domLum = dominantSwatch?.hsl?.get(2) ?: -1f
    val mutedPopulation = mutedSwatch?.population ?: -1
    val mutedSat = mutedSwatch?.hsl?.get(1) ?: 0f
    val mutedSatMinAcceptable = if (mutedPopulation > vibPopulation * 3f) 0.1f else 0.25f

    val dominantIsColorful = domSat >= .25f
    val dominantBrightnessJustRight = domLum <= .8f && domLum > .2f
    val vibrantIsConsiderableBigEnough = vibPopulation >= mutedPopulation * 0.75f
    val mutedIsBig = mutedPopulation > vibPopulation * 1.5f
    val mutedIsNotTooBoring = mutedSat > mutedSatMinAcceptable

    return when {
        dominantIsColorful && dominantBrightnessJustRight -> dominantSwatch
        vibrantIsConsiderableBigEnough -> vibrantSwatch
        mutedIsBig && mutedIsNotTooBoring -> mutedSwatch
        else -> listOfNotNull(vibrantSwatch, lightVibrantSwatch, darkVibrantSwatch)
            .maxByOrNull {
                if (it === vibrantSwatch) vibPopulation * 3 else it.population
            }
    }?.rgb
}

