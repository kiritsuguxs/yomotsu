import re

with open('source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/model/SAnime.kt', 'r') as f:
    content = f.read()

content = content.replace('interface SAnime : Serializable {', 'interface SAnime : eu.kanade.tachiyomi.source.model.SManga, Serializable {')

with open('source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/model/SAnime.kt', 'w') as f:
    f.write(content)
