import re

with open('data/src/main/java/mihon/data/extension/model/NetworkExtensionStore.kt', 'r') as f:
    content = f.read()

content = content.replace('''        TachiyomiExtension.Available(
            name = extension.name,''',
'''        val isAnime = store.signingKey == "ANIME_REPO" || store.badgeLabel.equals("Anime", ignoreCase = true)
        TachiyomiExtension.Available(
            name = extension.name,
            isAnime = isAnime,''')

with open('data/src/main/java/mihon/data/extension/model/NetworkExtensionStore.kt', 'w') as f:
    f.write(content)
