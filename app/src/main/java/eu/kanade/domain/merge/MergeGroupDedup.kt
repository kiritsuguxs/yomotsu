package eu.kanade.domain.merge

import tachiyomi.core.common.util.lang.withIOContext

fun <T, K> List<T>.dedupeByMergeGroup(membership: Map<K, Long>, id: (T) -> K): List<T> {
    if (membership.isEmpty()) return this
    val seenGroups = HashSet<Long>()
    return filter { item -> membership[id(item)]?.let(seenGroups::add) ?: true }
}

suspend fun <T> EntryMergeManager.onePerSeries(items: List<T>, id: (T) -> Long): List<T> {
    val rows = items.distinctBy(id)
    return rows.dedupeByMergeGroup(withIOContext { groupIdsFor(rows.map(id)) }, id)
}

class MergeBucket<T>(
    val groupId: Long?,
    val members: List<T>,
)

fun <T, K> List<T>.bucketByMergeGroup(
    membership: Map<K, Long>,
    mergingEnabled: Boolean,
    id: (T) -> K,
): List<MergeBucket<T>> {
    if (!mergingEnabled || size <= 1) return map { MergeBucket(null, listOf(it)) }
    val buckets = LinkedHashMap<BucketKey<K>, MutableList<T>>()
    forEach { item ->
        val key = membership[id(item)]?.let { BucketKey.Group(it) } ?: BucketKey.Standalone(id(item))
        buckets.getOrPut(key) { mutableListOf() }.add(item)
    }
    return buckets.map { (key, members) ->
        MergeBucket((key as? BucketKey.Group)?.groupId?.takeIf { members.size > 1 }, members)
    }
}

private sealed interface BucketKey<out K> {
    data class Group(val groupId: Long) : BucketKey<Nothing>
    data class Standalone<K>(val id: K) : BucketKey<K>
}

fun groupedSourceIdsOf(ids: List<Long>, membersOf: (Long) -> List<Long>): Set<Long> =
    ids.flatMapTo(HashSet()) { id -> membersOf(id).takeIf { it.size > 1 }.orEmpty() }
