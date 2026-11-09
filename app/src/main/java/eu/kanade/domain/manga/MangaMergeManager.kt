package eu.kanade.domain.manga

import eu.kanade.domain.merge.EntryMergeManager
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.merge.MergeGroupRepository
import tachiyomi.domain.merge.model.ContentType

class MangaMergeManager(
    repository: MergeGroupRepository,
    preferences: LibraryPreferences,
    onMerged: suspend (memberIds: List<Long>) -> Unit = {},
    onBeforeDissolve: suspend (memberIds: List<Long>) -> Unit = {},
) : EntryMergeManager(
    ContentType.MANGA,
    repository,
    preferences,
    sameTitlePreference = preferences.autoMergeSameTitle,
    onMerged = onMerged,
    onBeforeDissolve = onBeforeDissolve,
)
