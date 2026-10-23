package eu.kanade.tachiyomi.extension.novel.epub

import android.content.Context
import android.content.Intent
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.extension.novel.download.NovelDownloadManager
import eu.kanade.tachiyomi.extension.novel.translation.NovelTranslator
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.translation.TranslationPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object NovelEpubExporter {

    private val translationPreferences: TranslationPreferences by lazy { Injekt.get() }
    private val downloadProvider: DownloadProvider by lazy { Injekt.get() }
    private val sourceManager: SourceManager by lazy { Injekt.get() }

    /**
     * Generates a standard EPUB file from all downloaded (and/or reader-loaded) chapters of a Light Novel,
     * saves a copy into the Yomotsu downloads folder (`downloads/<Source>/<Novel>/<Novel>.epub`),
     * and returns the generated [File].
     */
    suspend fun exportNovelToEpub(
        context: Context,
        manga: Manga,
        chapters: List<Chapter>,
        preferTranslated: Boolean = translationPreferences.autoTranslateNovels().get(),
        fallbackChaptersContent: Map<Long, String> = emptyMap(),
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val sortedChapters = chapters.sortedBy {
                if (it.chapterNumber >= 0) it.chapterNumber else it.sourceOrder.toDouble()
            }

            val availableChapters = mutableListOf<Pair<Chapter, List<String>>>()
            for (chapter in sortedChapters) {
                val cachedTranslation = if (preferTranslated) NovelTranslator.getCached(chapter.id) else null
                if (!cachedTranslation.isNullOrBlank()) {
                    val cleanParagraphs = cleanTextToParagraphs(cachedTranslation)
                    if (cleanParagraphs.isNotEmpty()) {
                        availableChapters.add(chapter to cleanParagraphs)
                        continue
                    }
                }

                val rawDownloaded = NovelDownloadManager.getDownloadedChapterText(manga.id, chapter.id)
                    ?: fallbackChaptersContent[chapter.id]

                if (!rawDownloaded.isNullOrBlank()) {
                    val cleanPlain = NovelDownloadManager.cleanHtmlToPlainText(rawDownloaded)
                    if (cleanPlain.isBlank()) continue

                    val finalText = if (preferTranslated) {
                        runCatching {
                            NovelTranslator.translate(
                                chapterId = chapter.id,
                                text = cleanPlain,
                                seriesTitle = manga.title,
                            )
                        }.getOrDefault(cleanPlain)
                    } else {
                        cleanPlain
                    }

                    val paragraphs = cleanTextToParagraphs(finalText)
                    if (paragraphs.isNotEmpty()) {
                        availableChapters.add(chapter to paragraphs)
                    }
                }
            }

            if (availableChapters.isEmpty()) {
                throw IllegalStateException("Nenhum capítulo baixado ou carregado para exportar em EPUB.")
            }

            val safeTitle = manga.title
                .replace(Regex("[^a-zA-Z0-9À-ÿ _.-]"), "")
                .trim()
                .ifBlank { "novel_${manga.id}" }

            val exportDir = File(context.cacheDir, "epub_exports").apply { mkdirs() }
            val epubFile = File(exportDir, "$safeTitle.epub")
            if (epubFile.exists()) epubFile.delete()

            val bookUuid = UUID.nameUUIDFromBytes("yomotsu-novel-${manga.id}".toByteArray()).toString()
            val bookTitle = escapeXml(manga.title.ifBlank { "Novel" })
            val bookAuthor = escapeXml(manga.author ?: "Desconhecido")
            val bookDesc = escapeXml(manga.description ?: "")

            ZipOutputStream(FileOutputStream(epubFile)).use { zos ->
                // 1. mimetype (must be first, uncompressed, and without extra fields per EPUB spec)
                val mimeBytes = "application/epub+zip".toByteArray(Charsets.US_ASCII)
                val mimeEntry = ZipEntry("mimetype").apply {
                    method = ZipEntry.STORED
                    size = mimeBytes.size.toLong()
                    compressedSize = mimeBytes.size.toLong()
                    crc = CRC32().apply { update(mimeBytes) }.value
                    extra = null
                }
                zos.putNextEntry(mimeEntry)
                zos.write(mimeBytes)
                zos.closeEntry()

                // 2. META-INF/container.xml
                writeTextEntry(
                    zos,
                    "META-INF/container.xml",
                    """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>""",
                )

                // 3. OEBPS/style.css
                writeTextEntry(
                    zos,
                    "OEBPS/style.css",
                    """body {
  font-family: serif;
  line-height: 1.65;
  margin: 5%;
  text-align: justify;
}
h1, h2 {
  text-align: center;
  margin-bottom: 1.5em;
  line-height: 1.3;
}
p {
  text-indent: 1.5em;
  margin-top: 0.4em;
  margin-bottom: 0.4em;
}""",
                )

                // 4. Write each chapter XHTML (without external DTD so offline XML parsers never fail)
                availableChapters.forEachIndexed { index, (chapter, paragraphs) ->
                    val chapterFileName = "chapter_${index + 1}.xhtml"
                    val chapterTitle = escapeXml(chapter.name.ifBlank { "Capítulo ${index + 1}" })
                    val bodyHtml = paragraphs.joinToString("\n") { line ->
                        "  <p>${escapeXml(line)}</p>"
                    }

                    val xhtml = """<?xml version="1.0" encoding="utf-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="pt">
<head>
  <meta http-equiv="Content-Type" content="application/xhtml+xml; charset=utf-8" />
  <title>$chapterTitle</title>
  <link rel="stylesheet" type="text/css" href="style.css" />
</head>
<body>
  <h2>$chapterTitle</h2>
$bodyHtml
</body>
</html>"""
                    writeTextEntry(zos, "OEBPS/$chapterFileName", xhtml)
                }

                // 5. OEBPS/toc.ncx
                val navPoints = availableChapters.mapIndexed { index, (chapter, _) ->
                    val num = index + 1
                    val title = escapeXml(chapter.name.ifBlank { "Capítulo $num" })
                    """    <navPoint id="navPoint-$num" playOrder="$num">
      <navLabel><text>$title</text></navLabel>
      <content src="chapter_$num.xhtml"/>
    </navPoint>"""
                }.joinToString("\n")

                val tocNcx = """<?xml version="1.0" encoding="UTF-8"?>
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
  <head>
    <meta name="dtb:uid" content="urn:uuid:$bookUuid"/>
    <meta name="dtb:depth" content="1"/>
    <meta name="dtb:totalPageCount" content="0"/>
    <meta name="dtb:maxPageNumber" content="0"/>
  </head>
  <docTitle><text>$bookTitle</text></docTitle>
  <navMap>
$navPoints
  </navMap>
</ncx>"""
                writeTextEntry(zos, "OEBPS/toc.ncx", tocNcx)

                // 6. OEBPS/content.opf
                val manifestItems = availableChapters.indices.joinToString("\n") { index ->
                    val num = index + 1
                    """    <item id="chap$num" href="chapter_$num.xhtml" media-type="application/xhtml+xml"/>"""
                }
                val spineItems = availableChapters.indices.joinToString("\n") { index ->
                    val num = index + 1
                    """    <itemref idref="chap$num"/>"""
                }

                val contentOpf = """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" unique-identifier="BookId" version="2.0">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
    <dc:title>$bookTitle</dc:title>
    <dc:creator opf:role="aut">$bookAuthor</dc:creator>
    <dc:language>pt</dc:language>
    <dc:description>$bookDesc</dc:description>
    <dc:identifier id="BookId" opf:scheme="UUID">urn:uuid:$bookUuid</dc:identifier>
  </metadata>
  <manifest>
    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
    <item id="style" href="style.css" media-type="text/css"/>
$manifestItems
  </manifest>
  <spine toc="ncx">
$spineItems
  </spine>
</package>"""
                writeTextEntry(zos, "OEBPS/content.opf", contentOpf)
            }

            // Mirror the generated EPUB directly into the Yomotsu downloads directory
            runCatching {
                val source = sourceManager.get(manga.source)
                if (source != null) {
                    val mangaDir = downloadProvider.getMangaDir(manga.title, source).getOrNull()
                    if (mangaDir != null) {
                        val fileName = "$safeTitle.epub"
                        mangaDir.findFile(fileName)?.delete()
                        val targetUniFile = mangaDir.createFile(fileName)
                        targetUniFile?.openOutputStream()?.use { output ->
                            epubFile.inputStream().use { input ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            }

            epubFile
        }
    }

    fun shareEpub(context: Context, epubFile: File) {
        try {
            val uri = epubFile.getUriCompat(context)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/epub+zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, epubFile.name))
        } catch (e: Exception) {
            context.toast(e.message ?: "Erro ao compartilhar EPUB")
        }
    }

    private fun writeTextEntry(zos: ZipOutputStream, path: String, content: String) {
        val entry = ZipEntry(path)
        zos.putNextEntry(entry)
        zos.write(content.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    private fun cleanTextToParagraphs(rawContent: String): List<String> {
        val cleaned = NovelDownloadManager.cleanHtmlToPlainText(rawContent)
        return cleaned.lines()
            .map { line ->
                line.trim()
                    .replace(Regex("\\[\\[IMG:[^]]*]]"), "")
                    .replace(Regex("\\[\\[YOMOTSU_TERM_\\d+]]"), "")
                    .trim()
            }
            .filter { it.isNotEmpty() }
    }

    private fun escapeXml(input: String): String {
        return buildString(input.length) {
            for (ch in input) {
                when (ch) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&apos;")
                    else -> if (ch.code >= 0x20) {
                        append(ch)
                    }
                }
            }
        }
    }
}
