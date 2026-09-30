#!/bin/bash
FILE="app/src/main/java/eu/kanade/tachiyomi/extension/util/ExtensionLoader.kt"

# Add Anime constants
sed -i '/private const val EXTENSION_FEATURE = "tachiyomi.extension"/a \    private const val ANIME_EXTENSION_FEATURE = "tachiyomi.animeextension"' $FILE

# Update isPackageAnExtension
sed -i 's/return pkgInfo.reqFeatures.orEmpty().any { it.name == EXTENSION_FEATURE }/return pkgInfo.reqFeatures.orEmpty().any { it.name == EXTENSION_FEATURE || it.name == ANIME_EXTENSION_FEATURE }/g' $FILE

# ExtensionInfo class update
# Wait, ExtensionInfo is private data class. Let's find it.
