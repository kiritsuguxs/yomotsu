import re

with open('domain/src/main/java/tachiyomi/domain/manga/model/Manga.kt', 'r') as f:
    manga = f.read()

constants = """
        const val EPISODE_SORT_DESC = 0x00000000L
        const val EPISODE_SORT_ASC = 0x00000001L
        const val EPISODE_SORT_DIR_MASK = 0x00000001L

        const val EPISODE_SHOW_UNSEEN = 0x00000002L
        const val EPISODE_SHOW_SEEN = 0x00000004L
        const val EPISODE_UNSEEN_MASK = 0x00000006L

        const val EPISODE_SHOW_DOWNLOADED = 0x00000008L
        const val EPISODE_SHOW_NOT_DOWNLOADED = 0x00000010L
        const val EPISODE_DOWNLOADED_MASK = 0x00000018L

        const val EPISODE_SHOW_BOOKMARKED = 0x00000020L
        const val EPISODE_SHOW_NOT_BOOKMARKED = 0x00000040L
        const val EPISODE_BOOKMARKED_MASK = 0x00000060L

        const val EPISODE_SHOW_FILLERMARKED = 0x00000080L
        const val EPISODE_SHOW_NOT_FILLERMARKED = 0x00000100L
        const val EPISODE_FILLERMARKED_MASK = 0x00000180L

        const val EPISODE_SORTING_SOURCE = 0x00000000L
        const val EPISODE_SORTING_NUMBER = 0x00000200L
        const val EPISODE_SORTING_UPLOAD_DATE = 0x00000400L
        const val EPISODE_SORTING_ALPHABET = 0x00000600L
        const val EPISODE_SORTING_MASK = 0x00000600L

        const val EPISODE_SHOW_PREVIEWS = 0x00000000L
        const val EPISODE_SHOW_NOT_PREVIEWS = 0x00000800L
        const val EPISODE_PREVIEWS_MASK = 0x00000800L

        const val EPISODE_SHOW_SUMMARIES = 0x00000000L
        const val EPISODE_SHOW_NOT_SUMMARIES = 0x00001000L
        const val EPISODE_SUMMARIES_MASK = 0x00001000L

        const val EPISODE_DISPLAY_NAME = 0x00000000L
        const val EPISODE_DISPLAY_NUMBER = 0x00100000L
        const val EPISODE_DISPLAY_MASK = 0x00100000L

        const val ANIME_INTRO_MASK = 0x0000000000000FFL
        const val ANIME_AIRING_EPISODE_MASK = 0x000000000FFFF00L
        const val ANIME_AIRING_TIME_MASK = 0x0FFFFFFFF000000L
        const val ANIME_INTRO_DISABLE_MASK = 0x100000000000000L
"""

manga = manga.replace('companion object {', 'companion object {\n' + constants)

with open('domain/src/main/java/tachiyomi/domain/manga/model/Manga.kt', 'w') as f:
    f.write(manga)
