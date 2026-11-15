package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class BackupMergeGroup(
    @ProtoNumber(1) var contentType: Long = 0,
    @ProtoNumber(2) var overrideSourceRanking: Boolean = false,
    @ProtoNumber(3) var members: List<BackupMergeMember> = emptyList(),
)

@Serializable
data class BackupMergeMember(
    @ProtoNumber(1) var source: Long,
    @ProtoNumber(2) var url: String,
    @ProtoNumber(3) var priority: Long = 0,
)
