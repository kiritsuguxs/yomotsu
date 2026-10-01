package eu.kanade.domain.episode.model

import eu.kanade.domain.chapter.model.toDbChapter
import eu.kanade.domain.chapter.model.toSChapter
import tachiyomi.domain.episode.model.Episode

fun Episode.toSEpisode(): eu.kanade.tachiyomi.animesource.model.SEpisode = eu.kanade.tachiyomi.animesource.model.SEpisode.create().also {
    it.url = this.url
    it.name = this.name
    it.date_upload = this.dateUpload
    it.episode_number = this.chapterNumber.toFloat()
    it.scanlator = this.scanlator
}
fun Episode.toDbEpisode() = toDbChapter()
