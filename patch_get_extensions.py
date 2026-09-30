import re

with open('app/src/main/java/eu/kanade/domain/extension/interactor/GetExtensionsByType.kt', 'r') as f:
    content = f.read()

content = content.replace('.filter { (showNsfwSources || !it.isNsfw) }', '.filter { (showNsfwSources || !it.isNsfw) && !it.isAnime }')
content = content.replace('val untrusted = _untrusted', 'val untrusted = _untrusted.filter { !it.isAnime }')
content = content.replace('(showNsfwSources || !extension.isNsfw)', '(showNsfwSources || !extension.isNsfw) && !extension.isAnime')

with open('app/src/main/java/eu/kanade/domain/extension/interactor/GetExtensionsByType.kt', 'w') as f:
    f.write(content)

