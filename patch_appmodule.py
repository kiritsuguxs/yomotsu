import re

with open('app/src/main/java/eu/kanade/tachiyomi/di/AppModule.kt', 'r') as f:
    content = f.read()

content = content.replace('import eu.kanade.tachiyomi.data.download.DownloadProvider', '''import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.data.animedownload.AnimeDownloadCache
import eu.kanade.tachiyomi.data.animedownload.AnimeDownloadManager
import eu.kanade.tachiyomi.data.animedownload.AnimeDownloadProvider''')

content = content.replace('addSingletonFactory { DownloadCache(app) }', '''addSingletonFactory { DownloadCache(app) }
        
        addSingletonFactory { AnimeDownloadProvider(app) }
        addSingletonFactory { AnimeDownloadManager(app) }
        addSingletonFactory { AnimeDownloadCache(app) }''')

content = content.replace('get<DownloadManager>()\n        }', '''get<DownloadManager>()
            get<AnimeDownloadManager>()
        }''')

with open('app/src/main/java/eu/kanade/tachiyomi/di/AppModule.kt', 'w') as f:
    f.write(content)
