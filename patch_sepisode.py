import re

with open('source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/model/SEpisode.kt', 'r') as f:
    content = f.read()

content = content.replace('interface SEpisode : Serializable {', 'interface SEpisode : eu.kanade.tachiyomi.source.model.SChapter, Serializable {')

with open('source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/model/SEpisode.kt', 'w') as f:
    f.write(content)
