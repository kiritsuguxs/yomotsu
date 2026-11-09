package tachiyomi.domain.merge.model

data class MemberCover<S>(
    val memberId: Long,
    val groupId: Long,
    val sourceId: S,
    val url: String?,
    val lastModified: Long,
)
