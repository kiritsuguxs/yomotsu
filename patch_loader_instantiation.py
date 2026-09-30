import re

with open('app/src/main/java/eu/kanade/tachiyomi/extension/util/ExtensionLoader.kt', 'r') as f:
    content = f.read()

content = content.replace('''            val extension = Extension.Untrusted(
                extName,
                pkgName,
                versionName,
                versionCode,
                libVersion,
                signatures.last(),
            )''', '''            val extension = Extension.Untrusted(
                extName,
                pkgName,
                versionName,
                versionCode,
                libVersion,
                signatures.last(),
                isAnime = isAnime,
            )''')

content = content.replace('''        val extension = Extension.Installed(
            extName,
            pkgName,
            versionName,
            versionCode,
            libVersion,
            lang,
            isNsfw,
            pkgFactory,
            sources,
            icon,
            isShared = extensionInfo.isShared,
        )''', '''        val extension = Extension.Installed(
            extName,
            pkgName,
            versionName,
            versionCode,
            libVersion,
            lang,
            isNsfw,
            isAnime = isAnime,
            pkgFactory = pkgFactory,
            sources = sources,
            icon = icon,
            isShared = extensionInfo.isShared,
        )''')

with open('app/src/main/java/eu/kanade/tachiyomi/extension/util/ExtensionLoader.kt', 'w') as f:
    f.write(content)
