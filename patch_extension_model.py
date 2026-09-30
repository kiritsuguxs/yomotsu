import re

with open('domain/src/main/java/eu/kanade/tachiyomi/extension/model/Extension.kt', 'r') as f:
    content = f.read()

content = content.replace('abstract val isNsfw: Boolean', 'abstract val isNsfw: Boolean\n    abstract val isAnime: Boolean')

content = content.replace('override val isNsfw: Boolean,', 'override val isNsfw: Boolean,\n        override val isAnime: Boolean = false,')
content = content.replace('override val isNsfw: Boolean = false,', 'override val isNsfw: Boolean = false,\n        override val isAnime: Boolean = false,')

with open('domain/src/main/java/eu/kanade/tachiyomi/extension/model/Extension.kt', 'w') as f:
    f.write(content)

