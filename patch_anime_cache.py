import re

with open('app/src/main/java/eu/kanade/tachiyomi/data/animedownload/AnimeDownloadCache.kt', 'r') as f:
    content = f.read()

content = content.replace('storageManager.getDownloadsDirectory()', 'storageManager.getDownloadsDirectory()?.createDirectory("anime")')

with open('app/src/main/java/eu/kanade/tachiyomi/data/animedownload/AnimeDownloadCache.kt', 'w') as f:
    f.write(content)
