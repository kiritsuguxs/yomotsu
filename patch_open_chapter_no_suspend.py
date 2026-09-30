import re

with open('app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaScreen.kt', 'r') as f:
    content = f.read()

content = content.replace('eu.kanade.tachiyomi.ui.main.MainActivity.startPlayerActivity(context, chapter.mangaId, chapter.id, extPlayer = false)',
'context.startActivity(eu.kanade.tachiyomi.ui.player.PlayerActivity.newIntent(context, chapter.mangaId, chapter.id))')

with open('app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaScreen.kt', 'w') as f:
    f.write(content)
