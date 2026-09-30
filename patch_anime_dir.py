import re

with open('app/src/main/java/eu/kanade/tachiyomi/data/animedownload/AnimeDownloadProvider.kt', 'r') as f:
    content = f.read()

# Replace getDownloadsDirectory() with getDownloadsDirectory()?.createDirectory("anime")
content = content.replace('storageManager.getDownloadsDirectory()', 'storageManager.getDownloadsDirectory()?.createDirectory("anime")')

with open('app/src/main/java/eu/kanade/tachiyomi/data/animedownload/AnimeDownloadProvider.kt', 'w') as f:
    f.write(content)
