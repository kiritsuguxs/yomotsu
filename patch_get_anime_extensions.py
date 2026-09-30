import re

with open('app/src/main/java/eu/kanade/domain/extension/interactor/GetAnimeExtensionsByType.kt', 'r') as f:
    content = f.read()

content = content.replace('GetExtensionsByType', 'GetAnimeExtensionsByType')
content = content.replace('!it.isAnime', 'it.isAnime')
content = content.replace('!extension.isAnime', 'extension.isAnime')

with open('app/src/main/java/eu/kanade/domain/extension/interactor/GetAnimeExtensionsByType.kt', 'w') as f:
    f.write(content)

