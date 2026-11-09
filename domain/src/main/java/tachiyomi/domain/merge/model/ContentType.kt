package tachiyomi.domain.merge.model

enum class ContentType(val id: Long) {
    ALL(-1),
    MANGA(0),
    NOVEL(1),
    ANIME(2);

    companion object {
        fun fromId(id: Long) = entries.find { it.id == id } ?: MANGA
    }
}
