package eu.kanade.tachiyomi.data.track.myanimelist

import eu.kanade.tachiyomi.data.database.models.Track

fun Track.toMyAnimeListStatus(isAnime: Boolean = false) = when (status) {
    MyAnimeList.READING -> if (isAnime) "watching" else "reading"
    MyAnimeList.COMPLETED -> "completed"
    MyAnimeList.ON_HOLD -> "on_hold"
    MyAnimeList.DROPPED -> "dropped"
    MyAnimeList.PLAN_TO_READ -> if (isAnime) "plan_to_watch" else "plan_to_read"
    MyAnimeList.REREADING -> if (isAnime) "watching" else "reading"
    else -> null
}

fun getStatus(status: String?) = when (status) {
    "reading", "watching" -> MyAnimeList.READING
    "completed" -> MyAnimeList.COMPLETED
    "on_hold" -> MyAnimeList.ON_HOLD
    "dropped" -> MyAnimeList.DROPPED
    "plan_to_read", "plan_to_watch" -> MyAnimeList.PLAN_TO_READ
    else -> MyAnimeList.READING
}
