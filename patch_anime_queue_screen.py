import re

with open('app/src/main/java/eu/kanade/tachiyomi/ui/animedownload/AnimeDownloadQueueScreen.kt', 'r') as f:
    content = f.read()

# Replace MR.strings.label_download_queue with MR.strings.label_anime_download_queue
content = content.replace('MR.strings.label_download_queue', 'MR.strings.label_anime_download_queue')

with open('app/src/main/java/eu/kanade/tachiyomi/ui/animedownload/AnimeDownloadQueueScreen.kt', 'w') as f:
    f.write(content)
