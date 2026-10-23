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

    private suspend fun collectAvailableChapters(
        manga: Manga,
        chapters: List<Chapter>,
        preferTranslated: Boolean,
        fallbackChaptersContent: Map<Long, String>,
    ): List<Pair<Chapter, List<String>>> {
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
        return availableChapters
    }

    /**
     * Generates a standard EPUB file from all downloaded (and/or reader-loaded) chapters of a Light Novel,
     * and also saves `.epub`, `.html` (direct phone browser/viewer format), and `.txt` (with UTF-8 BOM)
     * into the Yomotsu downloads folder (`downloads/<Source>/<Novel>/`).
     */
    suspend fun exportNovelToEpub(
        context: Context,
        manga: Manga,
        chapters: List<Chapter>,
        preferTranslated: Boolean = translationPreferences.autoTranslateNovels().get(),
        fallbackChaptersContent: Map<Long, String> = emptyMap(),
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val availableChapters = collectAvailableChapters(
                manga = manga,
                chapters = chapters,
                preferTranslated = preferTranslated,
                fallbackChaptersContent = fallbackChaptersContent,
            )

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

                // 4. Write each chapter XHTML
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

            // Save ONLY the compact translated .epub into the Yomotsu downloads folder
            runCatching {
                val source = sourceManager.get(manga.source)
                if (source != null) {
                    val mangaDir = downloadProvider.getMangaDir(manga.title, source).getOrNull()
                    if (mangaDir != null) {
                        mangaDir.findFile("$safeTitle.html")?.delete()
                        mangaDir.findFile("$safeTitle.txt")?.delete()

                        val epubName = "$safeTitle.epub"
                        mangaDir.findFile(epubName)?.delete()
                        mangaDir.createFile(epubName)?.openOutputStream()?.use { output ->
                            epubFile.inputStream().use { input -> input.copyTo(output) }
                        }
                    }
                }
            }

            epubFile
        }
    }

    /**
     * Generates a single self-contained `.html` E-Book that opens cleanly in any phone's built-in
     * HTML Viewer or Browser without requiring ReadEra or an external EPUB reader.
     */
    suspend fun exportNovelToHtml(
        context: Context,
        manga: Manga,
        chapters: List<Chapter>,
        preferTranslated: Boolean = translationPreferences.autoTranslateNovels().get(),
        fallbackChaptersContent: Map<Long, String> = emptyMap(),
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val availableChapters = collectAvailableChapters(
                manga = manga,
                chapters = chapters,
                preferTranslated = preferTranslated,
                fallbackChaptersContent = fallbackChaptersContent,
            )

            if (availableChapters.isEmpty()) {
                throw IllegalStateException("Nenhum capítulo baixado ou carregado para exportar.")
            }

            val safeTitle = manga.title
                .replace(Regex("[^a-zA-Z0-9À-ÿ _.-]"), "")
                .trim()
                .ifBlank { "novel_${manga.id}" }

            val exportDir = File(context.cacheDir, "epub_exports").apply { mkdirs() }
            val htmlFile = File(exportDir, "$safeTitle.html")
            val bookTitle = escapeXml(manga.title.ifBlank { "Novel" })
            val bookAuthor = escapeXml(manga.author ?: "Desconhecido")
            val htmlContent = buildStandaloneHtmlBook(bookTitle, bookAuthor, availableChapters)
            htmlFile.writeText(htmlContent, Charsets.UTF_8)
            htmlFile
        }
    }

    private fun buildStandaloneHtmlBook(
        bookTitle: String,
        bookAuthor: String,
        availableChapters: List<Pair<Chapter, List<String>>>,
    ): String {
        val tocLinks = availableChapters.mapIndexed { idx, (chap, _) ->
            val num = idx + 1
            "<li><a href=\"#chap-$num\">${escapeXml(chap.name.ifBlank { "Capítulo $num" })}</a></li>"
        }.joinToString("\n")

        val chaptersHtml = availableChapters.mapIndexed { idx, (chap, paragraphs) ->
            val num = idx + 1
            val pTags = paragraphs.joinToString("\n") { "<p>${escapeXml(it)}</p>" }
            """<section id="chap-$num">
<h2>${escapeXml(chap.name.ifBlank { "Capítulo $num" })}</h2>
$pTags
</section><hr/>"""
        }.joinToString("\n")

        return """<!DOCTYPE html>
<html lang="pt-BR">
<head>
<meta charset="UTF-8">
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>$bookTitle</title>
<style>
  body { font-family: Georgia, serif; line-height: 1.75; margin: 0; padding: 20px; background: #141414; color: #e6e6e6; max-width: 760px; margin-left: auto; margin-right: auto; }
  h1, h2 { text-align: center; color: #ffffff; }
  p { text-indent: 1.4em; margin: 0.6em 0; text-align: justify; }
  a { color: #90caf9; text-decoration: none; }
  hr { border: 0; border-top: 1px solid #333; margin: 2em 0; }
  ul { line-height: 1.9; }
</style>
</head>
<body>
<h1>$bookTitle</h1>
<p style="text-align:center;text-indent:0;opacity:0.75;">$bookAuthor</p>
<hr/>
<h2>Sumário</h2>
<ul>
$tocLinks
</ul>
<hr/>
$chaptersHtml
</body>
</html>"""
    }

    private fun buildStandaloneTxtBook(
        title: String,
        availableChapters: List<Pair<Chapter, List<String>>>,
    ): String {
        return buildString {
            append("\uFEFF") // UTF-8 BOM so built-in Android/Xiaomi text viewers never misdetect encoding
            appendLine(title)
            appendLine("=".repeat(title.length.coerceAtLeast(10)))
            appendLine()
            availableChapters.forEachIndexed { idx, (chap, paragraphs) ->
                appendLine("## ${chap.name.ifBlank { "Capítulo ${idx + 1}" }}")
                appendLine()
                paragraphs.forEach { p ->
                    appendLine(p)
                    appendLine()
                }
                appendLine("----------------------------------------")
                appendLine()
            }
        }
    }

    fun shareEpub(context: Context, epubFile: File) {
        shareFile(context, epubFile, "application/epub+zip")
    }

    fun shareHtml(context: Context, htmlFile: File) {
        shareFile(context, htmlFile, "text/html")
    }

    private fun shareFile(context: Context, file: File, mimeType: String) {
        try {
            val uri = file.getUriCompat(context)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, file.name))
        } catch (e: Exception) {
            context.toast(e.message ?: "Erro ao compartilhar arquivo")
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
