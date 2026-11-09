package tachiyomi.domain.merge

import java.util.Collections
import java.util.IdentityHashMap
import kotlin.math.abs

fun <T> stampedReadingOrder(merged: List<T>, restamp: (T, Long) -> T): List<T> =
    merged.mapIndexed { position, chapter -> restamp(chapter, position.toLong()) }

class MergedChapterOrder<T>(
    private val isTitle: (key: Any) -> Boolean = { false },
    private val boundsBackwardMatch: Boolean = false,
    private val boundsForwardMatch: Boolean = false,
    private val keyOf: (T) -> Any?,
) {

    private class Placed<T>(val key: Any?, val item: T)

    private val order = mutableListOf<Placed<T>>()
    private val placedKeys = HashSet<Any>()
    private var cursor = -1
    private var placedBySource = 0
    private var anchored = false
    private val supplied = HashSet<Placed<T>>()
    private val nextNamesake = IdentityHashMap<T, Int>()
    private val deferred = mutableListOf<T>()
    private val copies = mutableListOf<Pair<T, T>>()

    fun startSource(chapters: List<T> = emptyList()) {
        finishSource()
        cursor = -1
        placedBySource = 0
        anchored = false
        supplied.clear()
        nextNamesake.clear()
        if (boundsForwardMatch) {
            val laterIndex = HashMap<Any, Int>()
            for (index in chapters.indices.reversed()) {
                val key = keyOf(chapters[index])?.takeIf(::titled) ?: continue
                laterIndex[key]?.let { nextNamesake[chapters[index]] = it - index }
                laterIndex[key] = index
            }
        }
    }

    fun positionOf(item: T): Int {
        val key = keyOf(item) ?: return -1
        if (key !in placedKeys) return -1
        val ahead = (cursor + 1 until order.size).firstOrNull { order[it].key == key }
        if (ahead != null && !laterNamesakeTakes(item, ahead)) return ahead
        val behind = (cursor downTo 0).firstOrNull { order[it].key == key } ?: return -1
        return if (boundsBackwardMatch && titled(key) && order[behind] in supplied) -1 else behind
    }

    private fun laterNamesakeTakes(item: T, ahead: Int): Boolean {
        val gap = nextNamesake[item] ?: return false
        val key = order[ahead].key
        return abs(cursor + gap - ahead) < ahead - cursor &&
            (ahead + 1 until order.size).none { order[it].key == key }
    }

    fun followTo(index: Int, item: T) {
        if (index >= placedBySource) anchored = true
        val between = index - cursor - 1
        if (deferredRunMatches(between)) {
            deferred.forEachIndexed { offset, held -> copies += held to order[cursor + 1 + offset].item }
            deferred.clear()
            cursor = index
        } else {
            val shift = if (index > cursor) deferred.size else 0
            placeDeferred()
            cursor = index + shift
        }
        copies += item to order[cursor].item
        supplied += order[cursor]
    }

    fun defer(item: T) {
        deferred.add(item)
    }

    fun place(item: T) {
        cursor += 1
        placedBySource += 1
        val key = keyOf(item)
        val placed = Placed(key, item)
        order.add(cursor, placed)
        supplied += placed
        if (key != null) placedKeys.add(key)
    }

    private fun deferredRunMatches(between: Int): Boolean = deferred.size == between &&
        deferred.indices.none { titled(keyOf(deferred[it])) && titled(order[cursor + 1 + it].key) }

    private fun titled(key: Any?): Boolean = key != null && isTitle(key)

    fun result(): Stitched<T> {
        finishSource()
        return Stitched(order.map { it.item }, copies)
    }

    private fun finishSource() {
        placeDeferred()
        if (!anchored) Collections.rotate(order, -placedBySource)
    }

    private fun placeDeferred() {
        deferred.forEach(::place)
        deferred.clear()
    }

    class Stitched<T>(val merged: List<T>, val copies: List<Pair<T, T>>) {

        fun units(id: (T) -> Long): List<ChapterUnit> {
            val positions = merged.withIndex().associate { (position, item) -> id(item) to position }
            val shown = merged.mapIndexed { position, item -> ChapterUnit(id(item), position, 0) }
            val copyOrders = HashMap<Int, Int>()
            return shown + copies.mapNotNull { (copy, of) ->
                positions[id(of)]?.let { unit ->
                    val order = (copyOrders[unit] ?: 0) + 1
                    copyOrders[unit] = order
                    ChapterUnit(id(copy), unit, order)
                }
            }
        }
    }
}
