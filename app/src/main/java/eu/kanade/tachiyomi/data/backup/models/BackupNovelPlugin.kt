package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class BackupNovelPlugin(
    @ProtoNumber(1) val id: String,
    @ProtoNumber(2) val jsContent: String,
    @ProtoNumber(3) val jsonMeta: String,
)
