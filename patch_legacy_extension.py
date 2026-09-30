import re

with open('data/src/main/java/mihon/data/extension/model/NetworkLegacyExtension.kt', 'r') as f:
    content = f.read()

content = content.replace('''    fun toAvailableExtension(store: ExtensionStore, storeBaseUrl: String): Extension.Available {
        return Extension.Available(
            name = name.substringAfter("Tachiyomi: "),''',
'''    fun toAvailableExtension(store: ExtensionStore, storeBaseUrl: String): Extension.Available {
        val isAnime = store.signingKey == "ANIME_REPO" || store.badgeLabel.equals("Anime", ignoreCase = true)
        return Extension.Available(
            name = name.substringAfter("Tachiyomi: "),
            isAnime = isAnime,''')

with open('data/src/main/java/mihon/data/extension/model/NetworkLegacyExtension.kt', 'w') as f:
    f.write(content)
