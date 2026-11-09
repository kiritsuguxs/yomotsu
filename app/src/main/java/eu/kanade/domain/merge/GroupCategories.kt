package eu.kanade.domain.merge

open class GroupCategories(
    private val categoriesOf: suspend (id: Long) -> List<Long>,
    private val write: suspend (id: Long, categoryIds: List<Long>) -> Unit,
) {

    suspend fun set(id: Long, categoryIds: List<Long>, mergeManager: EntryMergeManager) {
        groupOf(id, mergeManager).forEach { write(it, categoryIds) }
    }

    suspend fun change(id: Long, add: List<Long>, remove: Collection<Long>, mergeManager: EntryMergeManager) {
        val members = groupOf(id, mergeManager)
        val changed = (members.flatMap { categoriesOf(it) } - remove.toSet() + add).distinct()
        members.forEach { write(it, changed) }
    }

    suspend fun adoptOwnerCategories(memberIds: List<Long>) {
        val ownerId = memberIds.firstOrNull() ?: return
        val owned = categoriesOf(ownerId)
        memberIds.drop(1).forEach { write(it, owned) }
    }

    suspend fun groupCategoriesFor(id: Long, mergeManager: EntryMergeManager): List<Long>? =
        mergeManager.groupLibraryMembers(id).firstOrNull { it != id }?.let { categoriesOf(it) }

    suspend fun fileMigrationTarget(id: Long, carried: List<Long>?, mergeManager: EntryMergeManager) {
        if (carried != null) {
            set(id, carried, mergeManager)
        } else {
            groupCategoriesFor(id, mergeManager)?.let { write(id, it) }
        }
    }

    suspend fun alignEveryGroup(mergeManager: EntryMergeManager) {
        mergeManager.libraryGroups().forEach { adoptOwnerCategories(it) }
    }

    private suspend fun groupOf(id: Long, mergeManager: EntryMergeManager): List<Long> =
        mergeManager.computeRelatedIds(id).toList()
}
