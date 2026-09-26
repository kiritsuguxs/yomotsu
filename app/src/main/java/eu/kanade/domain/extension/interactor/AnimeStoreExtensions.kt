package eu.kanade.domain.extension.interactor

import mihon.domain.extension.model.ExtensionStore

fun ExtensionStore?.isAnimeStore(): Boolean {
    if (this == null) return false
    return badgeLabel.equals("Anime", ignoreCase = true) ||
        signingKey.equals("ANIME_REPO", ignoreCase = true) ||
        indexUrl.contains("anime", ignoreCase = true)
}
