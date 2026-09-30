import os
import shutil
import re

src_dir = '/workspace/anikku/app/src/main/java/eu/kanade/tachiyomi/ui/download'
dst_dir = '/workspace/yomotsu/app/src/main/java/eu/kanade/tachiyomi/ui/animedownload'

if os.path.exists(dst_dir):
    shutil.rmtree(dst_dir)

shutil.copytree(src_dir, dst_dir)

def rename_files(directory):
    for root, dirs, files in os.walk(directory):
        for file in files:
            if file.endswith('.kt') and 'Download' in file:
                old_path = os.path.join(root, file)
                new_file = file.replace('Download', 'AnimeDownload')
                new_path = os.path.join(root, new_file)
                os.rename(old_path, new_path)

rename_files(dst_dir)

def process_file(file_path):
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()

    content = content.replace('eu.kanade.tachiyomi.ui.download', 'eu.kanade.tachiyomi.ui.animedownload')
    content = content.replace('eu.kanade.tachiyomi.data.download', 'eu.kanade.tachiyomi.data.animedownload')
    
    replacements = {
        'DownloadAdapter': 'AnimeDownloadAdapter',
        'DownloadHeaderHolder': 'AnimeDownloadHeaderHolder',
        'DownloadHeaderItem': 'AnimeDownloadHeaderItem',
        'DownloadHolder': 'AnimeDownloadHolder',
        'DownloadItem': 'AnimeDownloadItem',
        'DownloadQueueScreen': 'AnimeDownloadQueueScreen',
        'DownloadQueueViewModel': 'AnimeDownloadQueueViewModel',
        'DownloadManager': 'AnimeDownloadManager',
    }
    
    for old, new in replacements.items():
        content = re.sub(r'\b' + old + r'\b', new, content)

    content = re.sub(r'\bDownload\b(?!\w)', 'AnimeDownload', content)

    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(content)

for root, dirs, files in os.walk(dst_dir):
    for file in files:
        if file.endswith('.kt'):
            process_file(os.path.join(root, file))

