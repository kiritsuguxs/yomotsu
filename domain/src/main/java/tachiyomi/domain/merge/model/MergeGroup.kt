package tachiyomi.domain.merge.model

import androidx.compose.runtime.Immutable

@Immutable
data class MergeGroup(
    val id: Long,
    val contentType: ContentType,
    val overrideSourceRanking: Boolean,
)
