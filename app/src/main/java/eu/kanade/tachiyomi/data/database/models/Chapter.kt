@file:Suppress("PropertyName")

package eu.kanade.tachiyomi.data.database.models

import eu.kanade.tachiyomi.source.model.SChapter
import java.io.Serializable
import tachiyomi.domain.chapter.model.Chapter as DomainChapter

interface Chapter : SChapter, Serializable {

    var id: Long?

    var manga_id: Long?

    var read: Boolean

    var bookmark: Boolean

    var last_page_read: Int

    var date_fetch: Long

    var source_order: Int

    var last_modified: Long

    var version: Long
}

val Chapter.isRecognizedNumber: Boolean
    get() = chapter_number >= 0f

fun Chapter.toDomainChapter(): DomainChapter? {
    if (id == null || manga_id == null) return null
    return DomainChapter(
        id = id!!,
        mangaId = manga_id!!,
        read = read,
        bookmark = bookmark,
        lastPageRead = last_page_read.toLong(),
        dateFetch = date_fetch,
        sourceOrder = source_order.toLong(),
        url = url,
        name = name,
        dateUpload = date_upload,
        chapterNumber = chapter_number.toDouble(),
        scanlator = scanlator,
        lastModifiedAt = last_modified,
        version = version,
        memo = memo,
    )
}

var Chapter.last_second_seen: Long
    get() = last_page_read.toLong()
    set(value) { last_page_read = value.toInt() }

var Chapter.total_seconds: Long
    get() = 0L
    set(value) { }

var Chapter.seen: Boolean
    get() = read
    set(value) { read = value }

var Chapter.fillermark: Boolean
    get() = false
    set(value) { }

var Chapter.episode_number: Float
    get() = chapter_number
    set(value) { chapter_number = value }

