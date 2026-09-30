import re

with open('domain/src/main/java/tachiyomi/domain/chapter/model/Chapter.kt', 'r') as f:
    content = f.read()

# Add extension properties to the end of the class
extension_props = """
    val animeId: Long get() = mangaId
    val episodeNumber: Double get() = chapterNumber
    val seen: Boolean get() = read
    val lastSecondSeen: Long get() = lastPageRead
    val totalSeconds: Long get() = 0L // Tachiyomi chapter doesn't have totalPages in Yomotsu?
"""
# Wait, let's check if Yomotsu Chapter has totalPages.
