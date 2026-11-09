package eu.kanade.domain.merge

open class GroupChapterSettings<E>(
    private val flagsOf: (E) -> Long,
    private val withFlags: E.(Long) -> E,
    private val load: suspend (id: Long) -> E?,
    private val write: suspend (ids: List<Long>, flags: Long) -> Unit,
) {

    suspend fun shown(opened: E, memberIds: List<Long>): E {
        val ownerId = memberIds.firstOrNull() ?: return opened
        return opened.withOwnerChapterFlags(listOfNotNull(load(ownerId)), flagsOf, withFlags)
    }

    suspend fun change(openedId: Long, memberIds: List<Long>, change: suspend () -> Unit) {
        change()
        spread(openedId, memberIds)
    }

    suspend fun adoptOwnerSetting(memberIds: List<Long>) {
        memberIds.firstOrNull()?.let { spread(it, memberIds) }
    }

    suspend fun <T> rejoin(id: Long, mergeManager: EntryMergeManager, add: suspend () -> T): T {
        val before = mergeManager.groupLibraryMembers(id)
        val ownerId = before.firstOrNull()?.takeIf { id !in before }
        val flags = ownerId?.let { load(it) }?.let(flagsOf) ?: return add()
        return add().also {
            val after = mergeManager.groupLibraryMembers(id)
            if (id in after) write(after, flags)
        }
    }

    private suspend fun spread(fromId: Long, memberIds: List<Long>) {
        val others = memberIds.filter { it != fromId }
        if (others.isEmpty()) return
        val flags = load(fromId)?.let(flagsOf) ?: return
        write(others, flags)
    }
}

inline fun <E> E.withOwnerChapterFlags(members: Collection<E>, flagsOf: (E) -> Long, withFlags: E.(Long) -> E): E {
    val owner = members.firstOrNull() ?: return this
    val flags = flagsOf(owner)
    return if (flags == flagsOf(this)) this else withFlags(flags)
}
