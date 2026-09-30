import re

with open('app/src/main/java/eu/kanade/tachiyomi/extension/util/ExtensionLoader.kt', 'r') as f:
    content = f.read()

# Add isAnime to ExtensionInfo
content = content.replace(
'''    private data class ExtensionInfo(
        val packageInfo: PackageInfo,
        val isShared: Boolean,
    )''',
'''    private data class ExtensionInfo(
        val packageInfo: PackageInfo,
        val isShared: Boolean,
    ) {
        val isAnime: Boolean
            get() = packageInfo.reqFeatures.orEmpty().any { it.name == ANIME_EXTENSION_FEATURE }
    }'''
)

# Update the metadata keys inside loadExtension
content = re.sub(
    r'val extName = appInfo.metaData.getString\(METADATA_NAME\).*?\n',
    r'val extName = appInfo.metaData.getString(METADATA_NAME)\n            ?: pkgManager.getApplicationLabel(appInfo).toString().substringAfter("Tachiyomi: ")\n',
    content, flags=re.DOTALL
)

# Wait, the METADATA keys:
replacement = '''
        val isAnime = extensionInfo.isAnime
        val classKey = if (isAnime) "tachiyomi.animeextension.class" else METADATA_SOURCE_CLASS
        val factoryKey = if (isAnime) "tachiyomi.animeextension.factory" else METADATA_SOURCE_FACTORY
        val nsfwKey = if (isAnime) "tachiyomi.animeextension.nsfw" else METADATA_NSFW
        
        val classMetaData = appInfo.metaData.getString(classKey) ?: appInfo.metaData.getString(METADATA_SOURCE_CLASS)
        val factoryMetaData = appInfo.metaData.getString(factoryKey) ?: appInfo.metaData.getString(METADATA_SOURCE_FACTORY)
'''
# find the line `val classMetaData = appInfo.metaData.getString(METADATA_SOURCE_CLASS)`
# and replace the relevant block
import sys

# Replace `classMetaData` extraction
content = content.replace('val classMetaData = appInfo.metaData.getString(METADATA_SOURCE_CLASS)', 
'''val isAnime = extensionInfo.isAnime
        val classKey = if (isAnime) "tachiyomi.animeextension.class" else METADATA_SOURCE_CLASS
        val factoryKey = if (isAnime) "tachiyomi.animeextension.factory" else METADATA_SOURCE_FACTORY
        val classMetaData = appInfo.metaData.getString(classKey)''')

content = content.replace('val factoryMetaData = appInfo.metaData.getString(METADATA_SOURCE_FACTORY)',
'''val factoryMetaData = appInfo.metaData.getString(factoryKey)''')

content = content.replace('val isNsfw = appInfo.metaData.getInt(METADATA_CONTENT_WARNING) > 0 ||\n            appInfo.metaData.getInt(METADATA_NSFW) == 1',
'''val nsfwKey = if (isAnime) "tachiyomi.animeextension.nsfw" else METADATA_NSFW
        val isNsfw = appInfo.metaData.getInt(METADATA_CONTENT_WARNING) > 0 ||
            appInfo.metaData.getInt(nsfwKey) == 1''')

# In Extension.Untrusted and Extension.Installed we need to add isAnime
# We'll do that by patching Extension model later. For now, just pass isAnime

with open('app/src/main/java/eu/kanade/tachiyomi/extension/util/ExtensionLoader.kt', 'w') as f:
    f.write(content)

