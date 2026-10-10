package tachiyomi.domain.category.model

import java.io.Serializable

data class Category(
    val id: Long,
    val name: String,
    val order: Long,
    val flags: Long,
) : Serializable {

    val isSystemCategory: Boolean = id == UNCATEGORIZED_ID

    enum class MediaType(override val flag: Long) : tachiyomi.domain.library.model.FlagWithMask {
        ALL(0L),
        MANGA(0x01000000L),
        ANIME(0x02000000L),
        NOVEL(0x04000000L);

        override val mask: Long = 0x07000000L
    }

    val mediaType: MediaType
        get() = MediaType.entries.find { this.flags and it.mask == it.flag && it != MediaType.ALL } ?: MediaType.MANGA

    companion object {
        const val UNCATEGORIZED_ID = 0L
    }
}
