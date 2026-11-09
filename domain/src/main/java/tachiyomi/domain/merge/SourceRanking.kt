package tachiyomi.domain.merge

fun <S> sourcePriority(memberId: Long, sourceId: S?, preferredSourceIds: List<S>, memberRanking: List<Long>): Int {
    val position = if (memberRanking.isNotEmpty()) {
        memberRanking.indexOf(memberId)
    } else {
        sourceId?.let(preferredSourceIds::indexOf) ?: -1
    }
    return position.takeIf { it >= 0 } ?: Int.MAX_VALUE
}

fun <T> trunkOrder(priority: (T) -> Int, chapterCount: (T) -> Long, id: (T) -> Long): Comparator<T> =
    compareBy(priority).thenByDescending(chapterCount).thenBy(id)

fun <T, S> libraryRanking(
    members: List<T>,
    memberRanking: List<Long>,
    preferredSourceIds: List<S>,
    id: (T) -> Long,
    sourceId: (T) -> S?,
    chapterCount: (T) -> Long,
): List<T> = members.sortedWith(
    trunkOrder({ sourcePriority(id(it), sourceId(it), preferredSourceIds, memberRanking) }, chapterCount, id),
)

fun <T, K> coverFallbacks(shown: K, members: List<T>, key: (T) -> K): List<T> = members.filter { key(it) != shown }

fun <T> stitchOrder(ranked: List<T>, hasChapters: (T) -> Boolean): List<T> = ranked.sortedBy { !hasChapters(it) }

class RankedMember<S>(val groupId: Long, val memberId: Long, val sourceId: S, val overridden: Boolean)

fun <S> rankingStamps(members: List<RankedMember<S>>, preferredSourceIds: List<S>): Map<Long, String> =
    members.groupBy { it.groupId }.mapValues { (_, group) ->
        val memberRanking = if (group.first().overridden) group.map { it.memberId } else emptyList()
        group
            .groupBy({ sourcePriority(it.memberId, it.sourceId, preferredSourceIds, memberRanking) }, { it.memberId })
            .toSortedMap()
            .values
            .joinToString(";") { tier -> tier.sorted().joinToString(",") }
    }
