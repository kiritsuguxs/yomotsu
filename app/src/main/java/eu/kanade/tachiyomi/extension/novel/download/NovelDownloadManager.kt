package eu.kanade.tachiyomi.extension.novel.download

import android.app.Application
import androidx.core.text.HtmlCompat
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.extension.novel.NovelSourceWrapper
import eu.kanade.tachiyomi.extension.novel.translation.NovelTranslator
import eu.kanade.tachiyomi.source.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import logcat.LogPriority
import org.jsoup.Jsoup
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.domain.translation.TranslationPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object NovelDownloadManager {

    private const val MAX_CONCURRENT_DOWNLOADS = 3
    private val app: Application by lazy { Injekt.get() }
    private val sourceManager: SourceManager by lazy { Injekt.get() }
    private val storageManager: StorageManager by lazy { Injekt.get() }
    private val translationPreferences: TranslationPreferences by lazy { Injekt.get() }
    private val downloadProvider: DownloadProvider by lazy { Injekt.get() }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloadSemaphore = Semaphore(MAX_CONCURRENT_DOWNLOADS)
    private val baseDir by lazy { File(app.filesDir, "novel_downloads").apply { mkdirs() } }
    private val activeJobs = ConcurrentHashMap<Long, Job>()

    private val _downloadedChapters = MutableStateFlow<Set<Long>>(emptySet())
    val downloadedChapters: StateFlow<Set<Long>> = _downloadedChapters.asStateFlow()

    private val _downloadingChapters = MutableStateFlow<Set<Long>>(emptySet())
    val downloadingChapters: StateFlow<Set<Long>> = _downloadingChapters.asStateFlow()

    init {
        refreshDownloadedCache()
    }

    fun isChapterDownloading(chapterId: Long): Boolean = _downloadingChapters.value.contains(chapterId)

    private fun getMangaDir(mangaId: Long): File = File(baseDir, mangaId.toString()).apply { mkdirs() }
    private fun getChapterFile(mangaId: Long, chapterId: Long): File = File(getMangaDir(mangaId), "$chapterId.txt")

    fun getDownloadCount(mangaId: Long): Int {
        val dir = File(baseDir, mangaId.toString())
        if (!dir.exists() || !dir.isDirectory) return 0
        return dir.listFiles()?.count { it.extension == "txt" && it.length() > 0 } ?: 0
    }

    fun refreshDownloadedCache() {
        scope.launch {
            val ids = mutableSetOf<Long>()
            baseDir.listFiles()?.forEach { mangaFolder ->
                if (mangaFolder.isDirectory) {
                    mangaFolder.listFiles()?.forEach { file ->
                        if (file.extension == "txt" && file.length() > 0) {
                            file.nameWithoutExtension.toLongOrNull()?.let { ids.add(it) }
                        }
                    }
                }
            }
            _downloadedChapters.value = ids
        }
    }

    fun isChapterDownloaded(mangaId: Long, chapterId: Long): Boolean {
        val file = getChapterFile(mangaId, chapterId)
        return file.exists() && file.length() > 0
    }

    fun getDownloadedChapterText(mangaId: Long, chapterId: Long): String? {
        val file = getChapterFile(mangaId, chapterId)
        return if (file.exists() && file.length() > 0) file.readText() else null
    }

    /**
     * Strips scripts, styles, ads, and HTML tags from raw novel plugin output,
     * returning clean paragraphs ready for reading, translation, or EPUB export.
     */
    fun cleanHtmlToPlainText(raw: String): String {
        if (raw.isBlank()) return ""
        val stripped = runCatching {
            val doc = Jsoup.parse(raw)
            doc.select(
                "script, style, noscript, iframe, nav, header, footer, svg, canvas, " +
                    "form, button, input, select, textarea, aside, .ads, .ad, .advertisement, " +
                    "[style*='display:none'], [style*='display: none'], [hidden]",
            ).remove()
            doc.select("img").remove()
            doc.body().html()
        }.getOrDefault(raw)

        val withBreaks = stripped
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</(p|div|h[1-6]|li|blockquote|tr|section|article)>"), "\n\n")
            .replace(Regex("(?i)<hr\\s*/?>"), "\n\n* * *\n\n")
            .replace(Regex("<[^>]+>"), "")

        val unescaped = runCatching {
            HtmlCompat.fromHtml(withBreaks, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
        }.getOrElse {
            withBreaks
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
        }

        return unescaped
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("[[IMG:") }
            .joinToString("\n\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    fun getNovelDir(source: Source, mangaTitle: String): UniFile? {
        val novelsRoot = storageManager.getNovelDownloadsDirectory() ?: return null
        val sourceDirName = downloadProvider.getSourceDirName(source)
        val sourceDir = novelsRoot.createDirectory(sourceDirName) ?: return null
        val mangaDirName = downloadProvider.getMangaDirName(mangaTitle)
        return sourceDir.createDirectory(mangaDirName)
    }

    private fun saveToYomotsuFolder(
        source: NovelSourceWrapper,
        manga: Manga,
        chapter: Chapter,
        cleanText: String,
        translatedText: String? = null,
    ) {
        runCatching {
            val mangaDir = getNovelDir(source, manga.title) ?: return
            val chapterDirName = downloadProvider.getChapterDirName(
                chapter.name,
                chapter.scanlator,
                chapter.url,
            )
            val chapterDir = mangaDir.findFile(chapterDirName)
                ?: mangaDir.createDirectory(chapterDirName)
                ?: return

            // Remove legacy duplicate files if present to keep folder compact
            chapterDir.findFile("chapter.html")?.delete()
            chapterDir.findFile("chapter_traduzido.txt")?.delete()

            val finalContent = "\uFEFF" + (translatedText?.takeIf { it.isNotBlank() } ?: cleanText)
            val txtFile = chapterDir.findFile("chapter.txt") ?: chapterDir.createFile("chapter.txt")
            txtFile?.openOutputStream()?.use { out ->
                out.write(finalContent.toByteArray(Charsets.UTF_8))
            }
        }.onFailure { e ->
            logcat(LogPriority.WARN, e) { "Failed to mirror novel chapter into Yomotsu novels folder" }
        }
    }

    suspend fun downloadChapter(manga: Manga, chapter: Chapter): Boolean = withContext(Dispatchers.IO) {
        currentCoroutineContext()[Job]?.let { activeJobs[chapter.id] = it }
        _downloadingChapters.update { it + chapter.id }
        try {
            val source = sourceManager.get(manga.source) as? NovelSourceWrapper
                ?: throw Exception("Fonte não é um plugin de novel")

            val file = getChapterFile(manga.id, chapter.id)

            // Nuvem Telegram: Tenta puxar de volta primeiro
            val telegramCloudManager = Injekt.get<eu.kanade.tachiyomi.data.telegram.TelegramCloudManager>()
            val restored = telegramCloudManager.restoreNovelChapter(manga.title, chapter.name, file)

            currentCoroutineContext().ensureActive()

            val rawContent = if (!restored) {
                val raw = source.getChapterText(manga.url, chapter.url)
                currentCoroutineContext().ensureActive()
                if (raw.isBlank()) return@withContext false
                file.writeText(raw)
                raw
            } else {
                file.readText()
            }

            val cleanText = cleanHtmlToPlainText(rawContent)

            var translatedText: String? = NovelTranslator.getCached(
                chapterId = chapter.id,
                seriesTitle = manga.title,
                originalText = cleanText,
            )
            if (translationPreferences.autoTranslateNovels().get() && translatedText.isNullOrBlank() && cleanText.isNotBlank()) {
                translatedText = runCatching {
                    NovelTranslator.translate(
                        chapterId = chapter.id,
                        text = cleanText,
                        seriesTitle = manga.title,
                    )
                }.getOrNull()
            }

            // Salva também na pasta oficial do Yomotsu (downloads/<Fonte>/<Novel>/<Capítulo>/)
            saveToYomotsuFolder(
                source = source,
                manga = manga,
                chapter = chapter,
                cleanText = cleanText,
                translatedText = translatedText,
            )

            // Upload to Telegram Cloud only after local clean/translation processing is done
            // so deleteLocalAfterUpload cannot delete `file` mid-read.
            if (!restored) {
                telegramCloudManager.uploadNovelChapter(manga, chapter, file)
            }

            _downloadedChapters.update { it + chapter.id }

            true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to download novel chapter ${chapter.name}" }
            false
        } finally {
            activeJobs.remove(chapter.id)
            _downloadingChapters.update { it - chapter.id }
        }
    }

    fun downloadChapters(manga: Manga, chapters: List<Chapter>) {
        chapters.forEach { chapter ->
            if (activeJobs.containsKey(chapter.id)) return@forEach
            _downloadingChapters.update { it + chapter.id }
            val job = scope.launch {
                try {
                    downloadSemaphore.withPermit {
                        currentCoroutineContext().ensureActive()
                        downloadChapter(manga, chapter)
                    }
                } finally {
                    activeJobs.remove(chapter.id)
                    _downloadingChapters.update { it - chapter.id }
                }
            }
            activeJobs[chapter.id] = job
        }
    }

    fun cancelDownload(chapterId: Long) {
        activeJobs.remove(chapterId)?.cancel()
        _downloadingChapters.update { it - chapterId }
    }

    fun deleteChapter(mangaId: Long, chapterId: Long) {
        cancelDownload(chapterId)
        val file = getChapterFile(mangaId, chapterId)
        if (file.exists()) {
            file.delete()
        }
        NovelTranslator.clearCached(chapterId)
        _downloadedChapters.update { it - chapterId }
    }

    fun deleteManga(mangaId: Long) {
        val dir = File(baseDir, mangaId.toString())
        if (dir.exists()) {
            val deletedIds = dir.listFiles()
                ?.mapNotNull { it.nameWithoutExtension.toLongOrNull() }
                ?.toSet()
                .orEmpty()
            deletedIds.forEach {
                activeJobs.remove(it)?.cancel()
                NovelTranslator.clearCached(it)
            }
            dir.deleteRecursively()
            if (deletedIds.isNotEmpty()) {
                _downloadingChapters.update { it - deletedIds }
                _downloadedChapters.update { it - deletedIds }
            }
        }
    }
}
