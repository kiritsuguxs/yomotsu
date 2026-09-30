import re

with open('app/src/main/java/eu/kanade/tachiyomi/data/database/models/Chapter.kt', 'r') as f:
    chapter = f.read()

extension_props = """
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
"""

chapter += extension_props

with open('app/src/main/java/eu/kanade/tachiyomi/data/database/models/Chapter.kt', 'w') as f:
    f.write(chapter)

