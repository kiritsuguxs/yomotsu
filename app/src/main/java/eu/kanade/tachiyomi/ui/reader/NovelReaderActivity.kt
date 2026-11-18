package eu.kanade.tachiyomi.ui.reader

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import eu.kanade.tachiyomi.extension.novel.tts.NovelAudioPlayer
import eu.kanade.tachiyomi.extension.novel.tts.NovelTtsVoice
import androidx.lifecycle.lifecycleScope
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.DisposableEffect
import eu.kanade.tachiyomi.util.system.toast
import java.util.Locale
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jsoup.parser.Parser
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import tachiyomi.i18n.MR
import tachiyomi.i18n.at.ATMR
import tachiyomi.presentation.core.i18n.stringResource
import androidx.core.view.WindowInsetsControllerCompat
import eu.kanade.tachiyomi.extension.novel.NovelSourceWrapper
import eu.kanade.tachiyomi.extension.novel.download.NovelDownloadManager
import eu.kanade.tachiyomi.extension.novel.translation.NovelTranslator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.domain.chapter.interactor.GetChapter
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.translation.TranslationPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.domain.history.interactor.UpsertHistory
import tachiyomi.domain.history.model.HistoryUpdate
import eu.kanade.domain.track.interactor.TrackChapter
import android.view.KeyEvent
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import coil3.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow
import org.jsoup.Jsoup
import java.net.URI
import java.util.Date
import kotlin.math.roundToInt
import android.app.Application

class NovelReaderActivity : ComponentActivity() {

    private val exitSaveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val volumeScrollChannel = Channel<Int>(Channel.BUFFERED)
    private var volumeKeysScrollEnabled: Boolean = false

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (volumeKeysScrollEnabled) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    volumeScrollChannel.trySend(-1)
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    volumeScrollChannel.trySend(1)
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    companion object {
        private val NOVEL_HTML_TAG_REGEX = Regex(
            "(?i)<\\s*/?\\s*(p|div|br|span|img|a|b|strong|i|em|u|s|hr|h[1-6]|ul|ol|li|blockquote|" +
                "table|thead|tbody|tr|td|th|section|article|main|figure|figcaption|script|style|" +
                "noscript|iframe|nav|header|footer|aside|ruby|rt|rp|sup|sub|font|center|pre|code|" +
                "small|big|dl|dt|dd)\\b[^>]*>",
        )

        fun newIntent(context: Context, mangaId: Long, chapterId: Long): Intent {
            return Intent(context, NovelReaderActivity::class.java).apply {
                putExtra("manga_id", mangaId)
                putExtra("chapter_id", chapterId)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }
    }

    class ChapterItemState(
        chapter: Chapter,
        originalText: String? = null,
        translatedText: String? = null,
        isLoading: Boolean = true,
        isTranslating: Boolean = false,
        error: String? = null,
    ) {
        var chapter by mutableStateOf(chapter)
        var originalText by mutableStateOf(originalText)
        var translatedText by mutableStateOf(translatedText)
        var originalBlocks by mutableStateOf<List<String>>(emptyList())
        var translatedBlocks by mutableStateOf<List<String>>(emptyList())
        var isLoading by mutableStateOf(isLoading)
        var isTranslating by mutableStateOf(isTranslating)
        var error by mutableStateOf(error)
    }

    sealed interface NovelRenderEntry {
        val key: String
        val contentType: String
        val chapterIndex: Int
        val chapterState: ChapterItemState

        data class Header(
            override val key: String,
            override val chapterIndex: Int,
            override val chapterState: ChapterItemState,
            val showTopDivider: Boolean,
        ) : NovelRenderEntry {
            override val contentType: String get() = "header"
        }

        data class Status(
            override val key: String,
            override val chapterIndex: Int,
            override val chapterState: ChapterItemState,
        ) : NovelRenderEntry {
            override val contentType: String get() = "status"
        }

        data class Block(
            override val key: String,
            override val chapterIndex: Int,
            override val chapterState: ChapterItemState,
            val blockIndex: Int,
            val totalBlocks: Int,
            val text: String,
            val isImage: Boolean,
        ) : NovelRenderEntry {
            override val contentType: String get() = if (isImage) "image_block" else "text_block"
        }
    }

    private fun splitNovelBlocks(text: String?): List<String> {
        if (text.isNullOrBlank()) return emptyList()
        return text.split("\n\n").map { it.trim() }.filter { it.isNotEmpty() }
    }

    enum class ReaderTheme(val title: String, val bg: Color, val text: Color) {
        DEFAULT("Sistema", Color.Unspecified, Color.Unspecified),
        LIGHT("Claro", Color(0xFFFFFFFF), Color(0xFF1C1B1F)),
        SEPIA("Sépia", Color(0xFFFBF0D9), Color(0xFF4A3525)),
        AMOLED("Preto Puro", Color(0xFF000000), Color(0xFFDCDCDC)),
    }

    enum class NovelFontFamily(val label: String, val family: FontFamily) {
        SANS("Sem Serifa", FontFamily.SansSerif),
        SERIF("Literária (Serifa)", FontFamily.Serif),
        MONO("Monoespaçada", FontFamily.Monospace),
    }

    private fun formatNovelText(raw: String, baseUrl: String? = null): String {
        if (raw.isBlank()) return ""
        val withoutBreaks = if (NOVEL_HTML_TAG_REGEX.containsMatchIn(raw)) {
            val withImageTokens = runCatching {
                val doc = if (baseUrl != null) Jsoup.parse(raw, baseUrl) else Jsoup.parse(raw)
                doc.outputSettings().prettyPrint(false)
                doc.select(
                    "script, style, noscript, iframe, nav, header, footer, svg, canvas, " +
                        "form, button, input, select, textarea, aside, .ads, .ad, .advertisement, " +
                        "[style*='display:none'], [style*='display: none'], [hidden]",
                ).remove()
                doc.select("img").forEach { img ->
                    val rawSrc = img.attr("abs:src").ifBlank {
                        img.attr("src").ifBlank {
                            img.attr("data-src").ifBlank { img.attr("data-original") }
                        }
                    }.trim()
                    if (rawSrc.isNotBlank()) {
                        val resolved = if (baseUrl != null && !rawSrc.startsWith("http", ignoreCase = true) && !rawSrc.startsWith("data:image/", ignoreCase = true)) {
                            runCatching { URI(baseUrl).resolve(rawSrc).toString() }.getOrDefault(rawSrc)
                        } else {
                            rawSrc
                        }
                        val isAllowedScheme = resolved.startsWith("http://", ignoreCase = true) ||
                            resolved.startsWith("https://", ignoreCase = true) ||
                            resolved.startsWith("data:image/", ignoreCase = true)
                        if (isAllowedScheme) {
                            img.before("\n\n[[IMG:$resolved]]\n\n")
                        }
                    }
                    img.remove()
                }
                doc.body().html()
            }.getOrDefault(raw)

            withImageTokens
                .replace(Regex("(?i)<br\\s*/?>"), "\n")
                .replace(Regex("(?i)</(p|div|h[1-6]|li|blockquote|tr|section|article|pre|figure|figcaption)>"), "\n\n")
                .replace(Regex("(?i)<hr\\s*/?>"), "\n\n* * *\n\n")
                .replace(Regex("(?i)<(p|div)[^>]*>"), "")
                .replace(Regex("<[^>]+>"), "")
        } else {
            raw
        }

        val unescaped = runCatching {
            Parser.unescapeEntities(withoutBreaks, false)
        }.getOrElse {
            withoutBreaks
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
        }

        return unescaped
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace('\u00A0', ' ')
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n\n")
            .trim()
    }

    private fun stripImageTokensForTts(text: String): String {
        return text
            .lineSequence()
            .filterNot { it.trim().startsWith("[[IMG:") }
            .joinToString("\n")
    }

    private var audioPlayer: NovelAudioPlayer? = null

    override fun onDestroy() {
        audioPlayer?.release()
        audioPlayer = null
        super.onDestroy()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        audioPlayer = NovelAudioPlayer(this, lifecycleScope).apply {
            onError = { msg -> runOnUiThread { toast(msg) } }
        }


        val mangaId = intent.getLongExtra("manga_id", -1L)
        val initialChapterId = intent.getLongExtra("chapter_id", -1L)

        val getManga: GetManga = Injekt.get()
        val getChapter: GetChapter = Injekt.get()
        val getChaptersByMangaId: GetChaptersByMangaId = Injekt.get()
        val updateChapter: UpdateChapter = Injekt.get()
        val sourceManager: SourceManager = Injekt.get()
        val translationPreferences: TranslationPreferences = Injekt.get()
        val upsertHistory: UpsertHistory = Injekt.get()
        val trackChapter: TrackChapter = Injekt.get()
        val downloadPreferences: tachiyomi.domain.download.service.DownloadPreferences = Injekt.get()
        val mergedChapterProvider: eu.kanade.domain.manga.MergedChapterProvider = Injekt.get()
        val getTracks: tachiyomi.domain.track.interactor.GetTracks = Injekt.get()
        val appContext: Application = Injekt.get()

        val prefs = getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE)

        setContent {
            val scope = rememberCoroutineScope()
            var manga by remember { mutableStateOf<Manga?>(null) }
            var mangaById by remember { mutableStateOf<Map<Long, Manga>>(emptyMap()) }
            var currentStitch by remember { mutableStateOf<List<tachiyomi.domain.merge.ChapterUnit>>(emptyList()) }
            var allChapters by remember { mutableStateOf<List<Chapter>>(emptyList()) }
            val loadedChapters = remember { mutableStateListOf<ChapterItemState>() }
            var isTranslated by remember { mutableStateOf(translationPreferences.autoTranslateNovels().get()) }
            var isTtsPlaying by remember { mutableStateOf(false) }
            var showSettingsDialog by remember { mutableStateOf(false) }
            var showAudioDialog by remember { mutableStateOf(false) }
            var selectedVoice by remember {
                mutableStateOf(NovelTtsVoice.fromId(prefs.getString("novel_tts_voice", NovelTtsVoice.EDGE_FRANCISCA.id)))
            }
            var speechSpeed by remember {
                mutableFloatStateOf(prefs.getFloat("novel_tts_speed", 1.0f))
            }
            var autoAdvanceTts by remember {
                mutableStateOf(prefs.getBoolean("novel_tts_auto_advance", true))
            }
            var sleepTimerMinutes by remember {
                mutableIntStateOf(0)
            }
            var activeTtsSnippet by remember {
                mutableStateOf("")
            }

            var menuVisible by remember { mutableStateOf(false) }

            var fontSize by remember { mutableFloatStateOf(prefs.getFloat("font_size", 17f)) }
            var lineHeightMultiplier by remember { mutableFloatStateOf(prefs.getFloat("line_height_mult", 1.6f)) }
            var paragraphSpacingDp by remember { mutableFloatStateOf(prefs.getFloat("paragraph_spacing", 14f)) }
            var horizontalMarginDp by remember { mutableFloatStateOf(prefs.getFloat("horizontal_margin", 20f)) }
            var justifyText by remember { mutableStateOf(prefs.getBoolean("justify_text", false)) }
            var fontFamilyChoice by remember {
                val savedFont = prefs.getString("font_family", NovelFontFamily.SANS.name)
                mutableStateOf(runCatching { NovelFontFamily.valueOf(savedFont ?: "") }.getOrDefault(NovelFontFamily.SANS))
            }
            var volumeScrollPref by remember {
                val enabled = prefs.getBoolean("volume_keys_scroll", false)
                volumeKeysScrollEnabled = enabled
                mutableStateOf(enabled)
            }
            val savedThemeName = prefs.getString("theme_name", null)
            var readerTheme by remember {
                mutableStateOf(
                    savedThemeName?.let { name ->
                        if (name == "DARK") ReaderTheme.AMOLED
                        else runCatching { ReaderTheme.valueOf(name) }.getOrNull()
                    } ?: run {
                        val oldIdx = prefs.getInt("theme_index", 0)
                        if (oldIdx >= 3) ReaderTheme.AMOLED
                        else ReaderTheme.entries.getOrElse(oldIdx) { ReaderTheme.DEFAULT }
                    },
                )
            }

            val lazyListState = rememberLazyListState()
            var pendingScrollRestoreChapterId by remember(initialChapterId) { mutableStateOf<Long?>(initialChapterId) }
            var pendingRatioRestore by remember { mutableStateOf<Pair<Long, Float>?>(null) }

            val renderEntries by remember {
                derivedStateOf {
                    buildList {
                        loadedChapters.forEachIndexed { chapterIdx, item ->
                            add(
                                NovelRenderEntry.Header(
                                    key = "hdr_${item.chapter.id}",
                                    chapterIndex = chapterIdx,
                                    chapterState = item,
                                    showTopDivider = chapterIdx > 0,
                                ),
                            )
                            if (item.isLoading || item.isTranslating || item.error != null) {
                                add(
                                    NovelRenderEntry.Status(
                                        key = "sts_${item.chapter.id}",
                                        chapterIndex = chapterIdx,
                                        chapterState = item,
                                    ),
                                )
                            } else {
                                val blocks = if (isTranslated && item.translatedBlocks.isNotEmpty()) {
                                    item.translatedBlocks
                                } else {
                                    item.originalBlocks
                                }
                                val total = blocks.size
                                blocks.forEachIndexed { blockIdx, blockText ->
                                    add(
                                        NovelRenderEntry.Block(
                                            key = "blk_${item.chapter.id}_$blockIdx",
                                            chapterIndex = chapterIdx,
                                            chapterState = item,
                                            blockIndex = blockIdx,
                                            totalBlocks = total,
                                            text = blockText,
                                            isImage = blockText.startsWith("[[IMG:") && blockText.endsWith("]]"),
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            val currentVisibleChapter by remember {
                derivedStateOf {
                    renderEntries.getOrNull(lazyListState.firstVisibleItemIndex)?.chapterState?.chapter
                        ?: loadedChapters.firstOrNull()?.chapter
                }
            }

            fun activeChapterIndex(): Int {
                return renderEntries.getOrNull(lazyListState.firstVisibleItemIndex)?.chapterIndex
                    ?: (loadedChapters.size - 1).coerceAtLeast(0)
            }

            LaunchedEffect(Unit) {
                volumeScrollChannel.receiveAsFlow().collect { direction ->
                    val viewportHeight = lazyListState.layoutInfo.viewportSize.height.takeIf { it > 0 } ?: 800
                    val delta = (viewportHeight * 0.75f).toInt() * direction
                    lazyListState.scrollBy(delta.toFloat())
                }
            }

            // Immersive system bars handling
            val insetsController = remember(this) {
                WindowCompat.getInsetsController(window, window.decorView)
            }

            // Function to load a specific chapter's text
            fun loadChapterContent(state: ChapterItemState, autoPlayTtsWhenReady: Boolean = false) {
                scope.launch(Dispatchers.IO) {
                    try {
                        val m = mangaById[state.chapter.mangaId]
                            ?: getManga.await(state.chapter.mangaId)
                            ?: manga
                            ?: getManga.await(mangaId)
                        val novelSource = m?.let { sourceManager.get(it.source) as? NovelSourceWrapper }
                        val localText = NovelDownloadManager.getDownloadedChapterText(state.chapter.mangaId, state.chapter.id)
                        val rawText = if (localText != null) {
                            localText
                        } else {
                            if (m == null || novelSource == null) {
                                throw Exception("Fonte não é um plugin de novel")
                            }
                            novelSource.getChapterText(m.url, state.chapter.url)
                        }

                        val cleanText = formatNovelText(rawText, novelSource?.baseUrl)
                        val origBlocks = splitNovelBlocks(cleanText)
                        val seriesTitle = m?.title.orEmpty()

                        // Check cached translation BEFORE clearing isLoading so translated chapters never flash original text or reset scroll
                        val cached = NovelTranslator.getCached(
                            chapterId = state.chapter.id,
                            seriesTitle = seriesTitle,
                            originalText = cleanText,
                        )
                        if (cached != null) {
                            val cleanTranslated = formatNovelText(cached, novelSource?.baseUrl)
                            val transBlocks = splitNovelBlocks(cleanTranslated)
                            withContext(Dispatchers.Main) {
                                state.originalText = cleanText
                                state.originalBlocks = origBlocks
                                state.translatedText = cleanTranslated
                                state.translatedBlocks = transBlocks
                                state.isLoading = false
                            }
                        } else if (isTranslated) {
                            withContext(Dispatchers.Main) {
                                state.originalText = cleanText
                                state.originalBlocks = origBlocks
                                state.isTranslating = true
                                state.isLoading = false
                            }
                            try {
                                val translated = NovelTranslator.translate(
                                    chapterId = state.chapter.id,
                                    text = cleanText,
                                    seriesTitle = seriesTitle,
                                )
                                val cleanTranslated = formatNovelText(translated, novelSource?.baseUrl)
                                val transBlocks = splitNovelBlocks(cleanTranslated)
                                withContext(Dispatchers.Main) {
                                    state.translatedText = cleanTranslated
                                    state.translatedBlocks = transBlocks
                                    state.isTranslating = false
                                }
                            } catch (_: Exception) {
                                withContext(Dispatchers.Main) {
                                    state.isTranslating = false
                                }
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                state.originalText = cleanText
                                state.originalBlocks = origBlocks
                                state.isLoading = false
                            }
                        }

                        if (autoPlayTtsWhenReady) {
                            withContext(Dispatchers.Main) {
                                val textToSpeak = if (isTranslated && !state.translatedText.isNullOrBlank()) {
                                    state.translatedText
                                } else {
                                    state.originalText
                                }
                                if (!textToSpeak.isNullOrBlank()) {
                                    audioPlayer?.play(
                                        text = stripImageTokensForTts(textToSpeak),
                                        voice = selectedVoice,
                                        speed = speechSpeed,
                                        isPortuguese = isTranslated,
                                        startChunkIndex = 0,
                                    )
                                }
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            state.error = e.message ?: "Erro ao carregar texto"
                            state.isTranslating = false
                            state.isLoading = false
                        }
                    }
                }
            }

            fun navigateToChapter(targetChapter: Chapter, autoStartTts: Boolean = false) {
                audioPlayer?.stop(resetProgress = true)
                val existingIndex = loadedChapters.indexOfFirst { it.chapter.id == targetChapter.id }
                if (existingIndex != -1) {
                    pendingScrollRestoreChapterId = null
                    pendingRatioRestore = null
                    val headerEntryIdx = renderEntries.indexOfFirst {
                        it is NovelRenderEntry.Header && it.chapterState.chapter.id == targetChapter.id
                    }
                    if (headerEntryIdx != -1) {
                        scope.launch { lazyListState.animateScrollToItem(headerEntryIdx) }
                    }
                    if (autoStartTts) {
                        val existing = loadedChapters[existingIndex]
                        val textToSpeak = if (isTranslated && !existing.translatedText.isNullOrBlank()) {
                            existing.translatedText
                        } else {
                            existing.originalText
                        }
                        if (!textToSpeak.isNullOrBlank()) {
                            audioPlayer?.play(
                                text = stripImageTokensForTts(textToSpeak),
                                voice = selectedVoice,
                                speed = speechSpeed,
                                isPortuguese = isTranslated,
                                startChunkIndex = 0,
                            )
                        }
                    }
                } else {
                    pendingRatioRestore = null
                    pendingScrollRestoreChapterId = targetChapter.id
                    loadedChapters.clear()
                    val newItem = ChapterItemState(chapter = targetChapter)
                    loadedChapters.add(newItem)
                    loadChapterContent(newItem, autoPlayTtsWhenReady = autoStartTts)
                    scope.launch { lazyListState.scrollToItem(0) }
                }
            }

            fun toggleTtsPlayback() {
                val player = audioPlayer ?: return
                if (player.isPlaying) {
                    player.stop(resetProgress = false)
                } else {
                    val activeChapter = loadedChapters.getOrNull(activeChapterIndex()) ?: loadedChapters.firstOrNull()
                    val textToSpeak = if (isTranslated && !activeChapter?.translatedText.isNullOrBlank()) {
                        activeChapter?.translatedText
                    } else {
                        activeChapter?.originalText
                    }
                    if (!textToSpeak.isNullOrBlank()) {
                        player.play(
                            text = stripImageTokensForTts(textToSpeak),
                            voice = selectedVoice,
                            speed = speechSpeed,
                            isPortuguese = isTranslated,
                        )
                    } else {
                        toast(ATMR.strings.novel_tts_wait_chapter)
                    }
                }
            }

            DisposableEffect(audioPlayer, autoAdvanceTts, allChapters) {
                audioPlayer?.onStateChanged = { playing ->
                    isTtsPlaying = playing
                    if (!playing) activeTtsSnippet = ""
                }
                audioPlayer?.onChunkChanged = { _, chunkText ->
                    activeTtsSnippet = chunkText.take(64).trim()
                }
                audioPlayer?.onSleepTimerChanged = { mins ->
                    sleepTimerMinutes = mins
                }
                audioPlayer?.onChapterFinished = {
                    if (autoAdvanceTts && allChapters.isNotEmpty()) {
                        val activeChapter = loadedChapters.getOrNull(activeChapterIndex())?.chapter
                            ?: loadedChapters.lastOrNull()?.chapter
                        val allIdx = allChapters.indexOfFirst { it.id == activeChapter?.id }
                        if (allIdx != -1 && allIdx + 1 < allChapters.size) {
                            navigateToChapter(allChapters[allIdx + 1], autoStartTts = true)
                        }
                    }
                }
                onDispose {
                    audioPlayer?.onStateChanged = null
                    audioPlayer?.onChunkChanged = null
                    audioPlayer?.onSleepTimerChanged = null
                    audioPlayer?.onChapterFinished = null
                }
            }

            // Initial load of chapters and target chapter
            LaunchedEffect(mangaId, initialChapterId) {
                val initial = withContext(Dispatchers.IO) {
                    val loadedManga = getManga.await(mangaId)
                    manga = loadedManga
                    val chapters = if (loadedManga != null) {
                        val group = mergedChapterProvider.load(loadedManga)
                        mangaById = group.mangaById
                        currentStitch = group.stitch
                        val orderedGroupChapters = if (group.isMerged) {
                            group.chapters
                        } else {
                            group.chapters.sortedBy { it.sourceOrder }
                        }
                        when {
                            orderedGroupChapters.any { it.id == initialChapterId } -> orderedGroupChapters
                            else -> {
                                val requested = group.pooledChapters.firstOrNull { it.id == initialChapterId }
                                if (requested != null) {
                                    val unitOfRequested = group.stitch.firstOrNull { it.chapterId == initialChapterId }?.unit
                                    if (unitOfRequested != null) {
                                        val sameUnitIds = group.stitch
                                            .filter { it.unit == unitOfRequested }
                                            .mapTo(HashSet()) { it.chapterId }
                                        orderedGroupChapters.map {
                                            if (it.id in sameUnitIds) requested.copy(sourceOrder = it.sourceOrder) else it
                                        }.let { list -> if (list.any { it.id == initialChapterId }) list else list + requested }
                                    } else {
                                        orderedGroupChapters + requested
                                    }
                                } else {
                                    orderedGroupChapters
                                }
                            }
                        }
                    } else {
                        getChaptersByMangaId.await(mangaId).sortedBy { it.sourceOrder }
                    }
                    allChapters = chapters

                    val target = chapters.firstOrNull { it.id == initialChapterId }
                        ?: getChapter.await(initialChapterId)

                    if (target != null) {
                        upsertHistory.await(HistoryUpdate(target.id, Date(), 0L))
                    }
                    target
                }

                if (initial != null) {
                    val firstItem = ChapterItemState(chapter = initial)
                    loadedChapters.add(firstItem)
                    loadChapterContent(firstItem)
                }
            }

            // Deferred scroll restoration: runs ONLY after chapter loading AND translation finish and blocks are materialized
            LaunchedEffect(renderEntries, pendingScrollRestoreChapterId, pendingRatioRestore) {
                val ratioTarget = pendingRatioRestore
                if (ratioTarget != null) {
                    val (targetChapterId, targetRatio) = ratioTarget
                    val targetItem = loadedChapters.firstOrNull { it.chapter.id == targetChapterId } ?: return@LaunchedEffect
                    if (targetItem.isLoading || targetItem.isTranslating) return@LaunchedEffect
                    if (targetItem.error != null) {
                        pendingRatioRestore = null
                        return@LaunchedEffect
                    }
                    val firstBlockEntryIdx = renderEntries.indexOfFirst {
                        it is NovelRenderEntry.Block && it.chapterState.chapter.id == targetChapterId
                    }
                    if (firstBlockEntryIdx != -1) {
                        val firstBlock = renderEntries[firstBlockEntryIdx] as NovelRenderEntry.Block
                        val blockOffset = (targetRatio * (firstBlock.totalBlocks - 1).coerceAtLeast(0))
                            .roundToInt()
                            .coerceIn(0, (firstBlock.totalBlocks - 1).coerceAtLeast(0))
                        lazyListState.scrollToItem(firstBlockEntryIdx + blockOffset, 0)
                    }
                    pendingRatioRestore = null
                    return@LaunchedEffect
                }

                val restoreChapterId = pendingScrollRestoreChapterId ?: return@LaunchedEffect
                val targetItem = loadedChapters.firstOrNull { it.chapter.id == restoreChapterId } ?: return@LaunchedEffect
                if (targetItem.isLoading || targetItem.isTranslating) return@LaunchedEffect
                if (targetItem.error != null) {
                    pendingScrollRestoreChapterId = null
                    return@LaunchedEffect
                }

                val firstBlockEntryIdx = renderEntries.indexOfFirst {
                    it is NovelRenderEntry.Block && it.chapterState.chapter.id == restoreChapterId
                }
                if (firstBlockEntryIdx == -1) {
                    pendingScrollRestoreChapterId = null
                    return@LaunchedEffect
                }

                val firstBlock = renderEntries[firstBlockEntryIdx] as NovelRenderEntry.Block
                val savedRaw = prefs.getString("pos_$restoreChapterId", null)
                val nowWallMs = System.currentTimeMillis()
                var restoreRatio: Float? = null
                var restoreOffsetPx = 0

                if (!savedRaw.isNullOrBlank()) {
                    val parts = savedRaw.split("|")
                    val parsedRatio = parts.getOrNull(0)?.toFloatOrNull()?.coerceIn(0f, 1f)
                    val parsedOffset = parts.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                    val savedAtMs = parts.getOrNull(2)?.toLongOrNull() ?: 0L
                    val recentlySaved = savedAtMs > 0L && (nowWallMs - savedAtMs) in 0L..10_000L
                    if (parsedRatio != null && (!targetItem.chapter.read && targetItem.chapter.lastPageRead > 0L || recentlySaved)) {
                        restoreRatio = parsedRatio
                        restoreOffsetPx = parsedOffset
                    } else if (targetItem.chapter.read || targetItem.chapter.lastPageRead <= 0L) {
                        prefs.edit().remove("pos_$restoreChapterId").apply()
                    }
                } else if (!targetItem.chapter.read && targetItem.chapter.lastPageRead > 0L) {
                    restoreRatio = if (targetItem.chapter.lastPageRead in 1L..99L) {
                        targetItem.chapter.lastPageRead / 100f
                    } else {
                        (targetItem.chapter.lastPageRead.toFloat() / 8000f).coerceIn(0.02f, 0.95f)
                    }
                }

                if (restoreRatio != null && (restoreRatio > 0f || restoreOffsetPx > 0)) {
                    val blockOffset = (restoreRatio * (firstBlock.totalBlocks - 1).coerceAtLeast(0))
                        .roundToInt()
                        .coerceIn(0, (firstBlock.totalBlocks - 1).coerceAtLeast(0))
                    lazyListState.scrollToItem(firstBlockEntryIdx + blockOffset, restoreOffsetPx)
                }
                pendingScrollRestoreChapterId = null
            }

            // Infinite Scroll: seamlessly prefetch and append next chapter before user reaches bottom
            LaunchedEffect(lazyListState) {
                snapshotFlow {
                    val layoutInfo = lazyListState.layoutInfo
                    val total = layoutInfo.totalItemsCount
                    val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
                    val canScrollMore = lazyListState.canScrollForward
                    val firstIdx = lazyListState.firstVisibleItemIndex
                    val firstOffset = lazyListState.firstVisibleItemScrollOffset
                    val entries = renderEntries
                    val chaptersCount = allChapters.size
                    val loadedCount = loadedChapters.size
                    val lastItem = loadedChapters.lastOrNull()
                    val translatingMode = isTranslated
                    val restorePending = pendingScrollRestoreChapterId != null || pendingRatioRestore != null

                    if (restorePending || chaptersCount <= 1 || lastItem == null) {
                        null
                    } else if (lastItem.isLoading || lastItem.isTranslating || lastItem.error != null) {
                        null
                    } else {
                        val lastChapterBlocks = if (translatingMode && lastItem.translatedBlocks.isNotEmpty()) {
                            lastItem.translatedBlocks.size
                        } else {
                            lastItem.originalBlocks.size
                        }
                        val hasRenderedBlocks = lastChapterBlocks > 0 &&
                            entries.any { it is NovelRenderEntry.Block && it.chapterState.chapter.id == lastItem.chapter.id }

                        if (!hasRenderedBlocks || total <= 0 || lastVisibleItem == null) {
                            null
                        } else {
                            val viewportHeight = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).coerceAtLeast(600)
                            val nearLastItemBottom = lastVisibleItem.index >= total - 1 &&
                                ((lastVisibleItem.offset + lastVisibleItem.size) - layoutInfo.viewportEndOffset) <= viewportHeight
                            val prefetchAheadParagraphs = if (translatingMode && lastChapterBlocks >= 16) 10 else 6
                            val nearLastParagraphs = lastChapterBlocks >= 8 &&
                                lastVisibleItem.index >= total - prefetchAheadParagraphs
                            val nearBottom = !canScrollMore || nearLastItemBottom || nearLastParagraphs
                            val untouchedAtTop = firstIdx == 0 && firstOffset == 0
                            val idleCapOk = !untouchedAtTop || loadedCount < 2

                            if (nearBottom && idleCapOk) lastItem.chapter.id else null
                        }
                    }
                }
                    .distinctUntilChanged()
                    .collect { triggerLastChapterId ->
                        if (triggerLastChapterId != null && loadedChapters.isNotEmpty() && allChapters.isNotEmpty()) {
                            val lastLoaded = loadedChapters.last().chapter
                            if (lastLoaded.id != triggerLastChapterId) return@collect
                            val currentIndex = allChapters.indexOfFirst { it.id == lastLoaded.id }
                            if (currentIndex != -1 && currentIndex + 1 < allChapters.size) {
                                val nextChapter = allChapters[currentIndex + 1]
                                if (loadedChapters.none { it.chapter.id == nextChapter.id }) {
                                    val newItem = ChapterItemState(chapter = nextChapter)
                                    loadedChapters.add(newItem)
                                    loadChapterContent(newItem)
                                }
                            }
                        }
                    }
            }

            // Save scroll offset & mark read when user scrolls through a chapter
            LaunchedEffect(lazyListState) {
                var lastHistoryTickMs = android.os.SystemClock.elapsedRealtime()
                snapshotFlow {
                    val visible = lazyListState.layoutInfo.visibleItemsInfo
                    val lastVisibleIdx = visible.lastOrNull()?.index ?: lazyListState.firstVisibleItemIndex
                    listOf(
                        lazyListState.firstVisibleItemIndex,
                        lazyListState.firstVisibleItemScrollOffset,
                        lastVisibleIdx,
                        if (!lazyListState.canScrollForward) 1 else 0,
                        renderEntries.size,
                    )
                }
                .distinctUntilChanged()
                .debounce(250L)
                .collectLatest { snapshot ->
                    val firstVisibleEntryIdx = snapshot[0]
                    val scrollOffset = snapshot[1].coerceAtLeast(0)
                    val lastVisibleEntryIdx = snapshot[2]
                    val reachedEnd = snapshot[3] == 1
                    val nowMs = android.os.SystemClock.elapsedRealtime()
                    val elapsedDeltaMs = (nowMs - lastHistoryTickMs).coerceIn(0L, 60_000L)
                    lastHistoryTickMs = nowMs

                    val firstEntry = renderEntries.getOrNull(firstVisibleEntryIdx) ?: return@collectLatest
                    val lastEntry = renderEntries.getOrNull(lastVisibleEntryIdx) ?: firstEntry
                    val currentChapterIdx = firstEntry.chapterIndex
                    val lastChapterIdx = lastEntry.chapterIndex
                    val currentItem = firstEntry.chapterState

                    // Never overwrite saved scroll while loading, translating, or waiting for initial restoration
                    if (pendingScrollRestoreChapterId != null ||
                        pendingRatioRestore != null ||
                        currentItem.isLoading ||
                        currentItem.isTranslating ||
                        currentItem.error != null
                    ) {
                        return@collectLatest
                    }

                    val userHasScrolled = firstVisibleEntryIdx > 0 || scrollOffset >= 40
                    val finishedBeforeIdx = if (reachedEnd && userHasScrolled && lastChapterIdx > currentChapterIdx) {
                        lastChapterIdx
                    } else {
                        currentChapterIdx
                    }

                    val newlyFinishedItems = buildList {
                        for (idx in 0 until finishedBeforeIdx) {
                            val past = loadedChapters.getOrNull(idx) ?: continue
                            if (!past.chapter.read && !past.isLoading && !past.isTranslating && past.error == null && past.originalBlocks.isNotEmpty()) {
                                add(past)
                            }
                        }
                        if (reachedEnd && lastChapterIdx in loadedChapters.indices) {
                            val lastItem = loadedChapters[lastChapterIdx]
                            val isAtLastParagraph = lastEntry is NovelRenderEntry.Block &&
                                lastEntry.blockIndex >= (lastEntry.totalBlocks - 1).coerceAtLeast(0)
                            if (isAtLastParagraph &&
                                !lastItem.chapter.read &&
                                !lastItem.isLoading &&
                                !lastItem.isTranslating &&
                                lastItem.error == null &&
                                lastItem.originalBlocks.isNotEmpty()
                            ) {
                                add(lastItem)
                            }
                        }
                    }.distinctBy { it.chapter.id }

                    // Optimistically flip in-memory read flag before IO suspension so rapid scroll ticks never double-fire trackers
                    newlyFinishedItems.forEach { finished ->
                        finished.chapter = finished.chapter.copy(read = true, lastPageRead = 0L)
                        prefs.edit().remove("pos_${finished.chapter.id}").apply()
                    }

                    val isCurrentAtBottom = reachedEnd &&
                        currentChapterIdx == lastChapterIdx &&
                        lastEntry is NovelRenderEntry.Block &&
                        lastEntry.blockIndex >= (lastEntry.totalBlocks - 1).coerceAtLeast(0)

                    val (currentRatio, progressPercent) = when {
                        isCurrentAtBottom || currentItem.chapter.read -> 0f to 0L
                        firstEntry is NovelRenderEntry.Block -> {
                            val denom = (firstEntry.totalBlocks - 1).coerceAtLeast(1)
                            val r = (firstEntry.blockIndex.toFloat() / denom.toFloat()).coerceIn(0f, 1f)
                            val pct = if (firstEntry.blockIndex == 0 && scrollOffset < 40) {
                                0L
                            } else {
                                (r * 100f).roundToInt().coerceIn(1, 99).toLong()
                            }
                            r to pct
                        }
                        else -> 0f to 0L
                    }

                    if (!currentItem.chapter.read && newlyFinishedItems.none { it.chapter.id == currentItem.chapter.id }) {
                        currentItem.chapter = currentItem.chapter.copy(lastPageRead = progressPercent)
                        if (progressPercent == 0L) {
                            prefs.edit().remove("pos_${currentItem.chapter.id}").apply()
                        } else {
                            prefs.edit()
                                .putString("pos_${currentItem.chapter.id}", "$currentRatio|$scrollOffset|${System.currentTimeMillis()}")
                                .apply()
                        }
                    }

                    withContext(Dispatchers.IO) {
                        newlyFinishedItems.forEach { finished ->
                            val finishedChapter = finished.chapter
                            updateChapter.await(
                                ChapterUpdate(
                                    id = finishedChapter.id,
                                    read = true,
                                    lastPageRead = 0L,
                                ),
                            )
                            if (currentStitch.isNotEmpty()) {
                                val siblingIds = eu.kanade.domain.merge.expandToUnits(setOf(finishedChapter.id), currentStitch) - finishedChapter.id
                                if (siblingIds.isNotEmpty()) {
                                    updateChapter.awaitAll(siblingIds.map { ChapterUpdate(id = it, read = true, lastPageRead = 0L) })
                                }
                            }
                            val targetTrackIds = if (mangaById.size > 1) {
                                mangaById.keys.filter { id -> getTracks.await(id).isNotEmpty() }
                                    .ifEmpty { listOf(mangaId) }
                            } else {
                                listOf(mangaId)
                            }
                            targetTrackIds.forEach { targetId ->
                                trackChapter.await(appContext, targetId, finishedChapter.chapterNumber.toDouble())
                            }

                            val removeSlots = downloadPreferences.removeAfterReadSlots.get()
                            if (removeSlots == 0) {
                                NovelDownloadManager.deleteChapter(finishedChapter.mangaId, finishedChapter.id)
                            } else if (removeSlots > 0 && allChapters.isNotEmpty()) {
                                val finishedIdx = allChapters.indexOfFirst { it.id == finishedChapter.id }
                                val targetDeleteIdx = finishedIdx - removeSlots
                                if (finishedIdx != -1 && targetDeleteIdx >= 0) {
                                    val chapterToDelete = allChapters[targetDeleteIdx]
                                    NovelDownloadManager.deleteChapter(chapterToDelete.mangaId, chapterToDelete.id)
                                }
                            }
                        }

                        if (newlyFinishedItems.isNotEmpty()) {
                            eu.kanade.tachiyomi.data.profile.ProfileChecker.checkAchievements(appContext)
                        }

                        if (newlyFinishedItems.none { it.chapter.id == currentItem.chapter.id }) {
                            updateChapter.await(
                                ChapterUpdate(
                                    id = currentItem.chapter.id,
                                    read = currentItem.chapter.read,
                                    lastPageRead = progressPercent,
                                ),
                            )
                        }

                        upsertHistory.await(HistoryUpdate(currentItem.chapter.id, Date(), elapsedDeltaMs))

                        val downloadAheadAmount = downloadPreferences.autoDownloadWhileReading.get()
                        if (downloadAheadAmount > 0 && allChapters.isNotEmpty()) {
                            val currAllIdx = allChapters.indexOfFirst { it.id == currentItem.chapter.id }
                            if (currAllIdx != -1) {
                                val seenUnits = HashSet<Int>()
                                currentStitch.firstOrNull { it.chapterId == currentItem.chapter.id }?.unit?.let(seenUnits::add)
                                val toDownload = allChapters.drop(currAllIdx + 1)
                                    .filter { ch ->
                                        val unit = currentStitch.firstOrNull { it.chapterId == ch.id }?.unit
                                        val unitOk = unit == null || seenUnits.add(unit)
                                        unitOk &&
                                            !ch.read &&
                                            !NovelDownloadManager.isChapterDownloaded(ch.mangaId, ch.id) &&
                                            !NovelDownloadManager.isChapterDownloading(ch.id)
                                    }
                                    .take(downloadAheadAmount)
                                toDownload.groupBy { it.mangaId }.forEach { (targetMangaId, targetChapters) ->
                                    val targetManga = mangaById[targetMangaId] ?: manga
                                    if (targetManga != null) {
                                        NovelDownloadManager.downloadChapters(targetManga, targetChapters)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Persist exact scroll position immediately on exit even if user presses Back before debounce fires
            DisposableEffect(Unit) {
                onDispose {
                    val firstIdx = lazyListState.firstVisibleItemIndex
                    val scrollOffset = lazyListState.firstVisibleItemScrollOffset.coerceAtLeast(0)
                    val firstEntry = renderEntries.getOrNull(firstIdx) ?: return@onDispose
                    val item = firstEntry.chapterState
                    if (pendingScrollRestoreChapterId != null ||
                        pendingRatioRestore != null ||
                        item.isLoading ||
                        item.isTranslating ||
                        item.error != null ||
                        item.chapter.read
                    ) {
                        return@onDispose
                    }
                    val (ratio, percent) = when (firstEntry) {
                        is NovelRenderEntry.Block -> {
                            val denom = (firstEntry.totalBlocks - 1).coerceAtLeast(1)
                            val r = (firstEntry.blockIndex.toFloat() / denom.toFloat()).coerceIn(0f, 1f)
                            val pct = if (firstEntry.blockIndex == 0 && scrollOffset < 40) {
                                0L
                            } else {
                                (r * 100f).roundToInt().coerceIn(1, 99).toLong()
                            }
                            r to pct
                        }
                        else -> 0f to 0L
                    }
                    if (percent == 0L) {
                        prefs.edit().remove("pos_${item.chapter.id}").apply()
                    } else {
                        prefs.edit()
                            .putString("pos_${item.chapter.id}", "$ratio|$scrollOffset|${System.currentTimeMillis()}")
                            .apply()
                    }
                    val chapterId = item.chapter.id
                    val isRead = item.chapter.read
                    exitSaveScope.launch {
                        runCatching {
                            updateChapter.await(
                                ChapterUpdate(
                                    id = chapterId,
                                    read = isRead,
                                    lastPageRead = percent,
                                ),
                            )
                        }
                    }
                }
            }

            // Color scheme resolution based on ReaderTheme
            val isDark = isSystemInDarkTheme()
            val isUiDark = when (readerTheme) {
                ReaderTheme.DEFAULT -> isDark
                ReaderTheme.LIGHT -> false
                ReaderTheme.SEPIA -> false
                ReaderTheme.AMOLED -> true
            }

            val resolvedBg = when (readerTheme) {
                ReaderTheme.DEFAULT -> if (isDark) Color(0xFF121212) else Color(0xFFFFFFFF)
                ReaderTheme.LIGHT -> Color(0xFFFFFFFF)
                ReaderTheme.SEPIA -> Color(0xFFFBF0D9)
                ReaderTheme.AMOLED -> Color(0xFF000000)
            }
            val resolvedTextColor = when (readerTheme) {
                ReaderTheme.DEFAULT -> if (isDark) Color(0xFFE0E0E0) else Color(0xFF1C1B1F)
                ReaderTheme.LIGHT -> Color(0xFF1C1B1F)
                ReaderTheme.SEPIA -> Color(0xFF4A3525)
                ReaderTheme.AMOLED -> Color(0xFFDCDCDC)
            }

            val uiBgColor = when (readerTheme) {
                ReaderTheme.DEFAULT -> if (isDark) Color(0xF5181818) else Color(0xF8FFFFFF)
                ReaderTheme.LIGHT -> Color(0xF8FFFFFF)
                ReaderTheme.SEPIA -> Color(0xF8FBF0D9)
                ReaderTheme.AMOLED -> Color(0xF8000000)
            }
            val uiSurfaceColor = when (readerTheme) {
                ReaderTheme.DEFAULT -> if (isDark) Color(0xFF222222) else Color(0xFFFFFFFF)
                ReaderTheme.LIGHT -> Color(0xFFFFFFFF)
                ReaderTheme.SEPIA -> Color(0xFFF7EEDB)
                ReaderTheme.AMOLED -> Color(0xFF161616)
            }
            val uiTextColor = when (readerTheme) {
                ReaderTheme.DEFAULT -> if (isDark) Color(0xFFEDEDED) else Color(0xFF1C1B1F)
                ReaderTheme.LIGHT -> Color(0xFF1C1B1F)
                ReaderTheme.SEPIA -> Color(0xFF4A3525)
                ReaderTheme.AMOLED -> Color(0xFFEDEDED)
            }
            val uiSubtextColor = when (readerTheme) {
                ReaderTheme.DEFAULT -> if (isDark) Color(0xFFA0A0A0) else Color(0xFF666666)
                ReaderTheme.LIGHT -> Color(0xFF666666)
                ReaderTheme.SEPIA -> Color(0xFF7A604D)
                ReaderTheme.AMOLED -> Color(0xFFA0A0A0)
            }
            val uiIconTint = when (readerTheme) {
                ReaderTheme.DEFAULT -> if (isDark) Color.White else Color(0xFF1C1B1F)
                ReaderTheme.LIGHT -> Color(0xFF1C1B1F)
                ReaderTheme.SEPIA -> Color(0xFF4A3525)
                ReaderTheme.AMOLED -> Color.White
            }

            LaunchedEffect(menuVisible, isUiDark) {
                if (menuVisible) {
                    insetsController.show(WindowInsetsCompat.Type.systemBars())
                    insetsController.isAppearanceLightStatusBars = !isUiDark
                    insetsController.isAppearanceLightNavigationBars = !isUiDark
                } else {
                    insetsController.hide(WindowInsetsCompat.Type.systemBars())
                    insetsController.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(resolvedBg),
            ) {
                // Reading content (Edge to Edge)
                if (renderEntries.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                } else {
                    val baseBodyLarge = MaterialTheme.typography.bodyLarge
                    val paragraphTextStyle = remember(baseBodyLarge, fontSize, lineHeightMultiplier, fontFamilyChoice, justifyText) {
                        baseBodyLarge.copy(
                            fontSize = fontSize.sp,
                            lineHeight = (fontSize * lineHeightMultiplier).sp,
                            fontFamily = fontFamilyChoice.family,
                            textAlign = if (justifyText) TextAlign.Justify else TextAlign.Start,
                        )
                    }
                    val activeSnippetPrefix = remember(isTtsPlaying, activeTtsSnippet) {
                        if (isTtsPlaying && activeTtsSnippet.length >= 12) activeTtsSnippet.take(28) else ""
                    }

                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                menuVisible = !menuVisible
                            },
                        contentPadding = PaddingValues(
                            top = 48.dp,
                            bottom = 96.dp,
                        ),
                    ) {
                        items(
                            items = renderEntries,
                            key = { it.key },
                            contentType = { it.contentType },
                        ) { entry ->
                            when (entry) {
                                is NovelRenderEntry.Header -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = horizontalMarginDp.dp)
                                            .padding(top = 16.dp, bottom = 20.dp),
                                    ) {
                                        if (entry.showTopDivider) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(vertical = 32.dp),
                                                color = resolvedTextColor.copy(alpha = 0.2f),
                                            )
                                        }
                                        Text(
                                            text = entry.chapterState.chapter.name,
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = fontFamilyChoice.family,
                                            ),
                                            color = if (readerTheme == ReaderTheme.DEFAULT) MaterialTheme.colorScheme.primary else resolvedTextColor,
                                            modifier = Modifier.fillMaxWidth(),
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }

                                is NovelRenderEntry.Status -> {
                                    val item = entry.chapterState
                                    when {
                                        item.isLoading || item.isTranslating -> {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = horizontalMarginDp.dp, vertical = 40.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                            ) {
                                                CircularProgressIndicator()
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Text(
                                                    text = if (item.isTranslating) {
                                                        stringResource(ATMR.strings.novel_translating_chapter)
                                                    } else {
                                                        stringResource(ATMR.strings.novel_loading_chapter)
                                                    },
                                                    color = resolvedTextColor,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                )
                                            }
                                        }
                                        item.error != null -> {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = horizontalMarginDp.dp, vertical = 24.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                            ) {
                                                Text(
                                                    text = "Erro: ${item.error}",
                                                    color = MaterialTheme.colorScheme.error,
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Button(onClick = {
                                                    item.isLoading = true
                                                    item.error = null
                                                    loadChapterContent(item)
                                                }) {
                                                    Text(stringResource(MR.strings.action_retry))
                                                }
                                            }
                                        }
                                    }
                                }

                                is NovelRenderEntry.Block -> {
                                    val blockText = entry.text
                                    if (entry.isImage) {
                                        val imgUrl = blockText.removePrefix("[[IMG:").removeSuffix("]]").trim()
                                        AsyncImage(
                                            model = imgUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.FillWidth,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(
                                                    horizontal = horizontalMarginDp.dp,
                                                    vertical = (paragraphSpacingDp / 2f).dp,
                                                )
                                                .clip(RoundedCornerShape(8.dp)),
                                        )
                                    } else {
                                        val isTtsHighlighted = activeSnippetPrefix.isNotEmpty() &&
                                            blockText.contains(activeSnippetPrefix, ignoreCase = true)
                                        val bottomPad = if (entry.blockIndex == entry.totalBlocks - 1) {
                                            (paragraphSpacingDp + 16f).dp
                                        } else {
                                            paragraphSpacingDp.dp
                                        }
                                        Text(
                                            text = blockText,
                                            style = paragraphTextStyle,
                                            color = resolvedTextColor,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = horizontalMarginDp.dp)
                                                .then(
                                                    if (isTtsHighlighted) {
                                                        Modifier
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                                                            .padding(horizontal = 6.dp, vertical = 4.dp)
                                                    } else {
                                                        Modifier
                                                    },
                                                )
                                                .padding(bottom = bottomPad),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Manhwa-style Top App Bar (Slides in/out on tap)
                AnimatedVisibility(
                    visible = menuVisible,
                    enter = slideInVertically { -it } + fadeIn(),
                    exit = slideOutVertically { -it } + fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter),
                ) {
                    TopAppBar(
                        modifier = Modifier.statusBarsPadding(),
                        title = {
                            Column {
                                Text(
                                    text = currentVisibleChapter?.name ?: manga?.title ?: "Novel",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = uiTextColor,
                                )
                                manga?.title?.let {
                                    Text(
                                        text = it,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = uiSubtextColor,
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { finish() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = stringResource(MR.strings.action_bar_up_description),
                                    tint = uiIconTint,
                                )
                            }
                        },
                        actions = {
                            // Font / Theme Settings Button
                            IconButton(onClick = { showSettingsDialog = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.FormatSize,
                                    contentDescription = stringResource(ATMR.strings.novel_customize_reading),
                                    tint = uiIconTint,
                                )
                            }

                            // Audio Narrator (TTS)
                            IconButton(onClick = { toggleTtsPlayback() }) {
                                Icon(
                                    imageVector = if (isTtsPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (isTtsPlaying) stringResource(ATMR.strings.novel_tts_pause) else stringResource(ATMR.strings.novel_tts_listen),
                                    tint = if (isTtsPlaying) MaterialTheme.colorScheme.primary else uiIconTint,
                                )
                            }

                            // Translation Toggle
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(end = 8.dp),
                            ) {
                                IconButton(onClick = {
                                    val firstEntry = renderEntries.getOrNull(lazyListState.firstVisibleItemIndex)
                                    if (firstEntry is NovelRenderEntry.Block) {
                                        val denom = (firstEntry.totalBlocks - 1).coerceAtLeast(1)
                                        val ratio = (firstEntry.blockIndex.toFloat() / denom.toFloat()).coerceIn(0f, 1f)
                                        pendingRatioRestore = firstEntry.chapterState.chapter.id to ratio
                                    }
                                    isTranslated = !isTranslated
                                    if (isTranslated) {
                                        loadedChapters.forEach { item ->
                                            if (item.translatedBlocks.isEmpty() && item.originalText != null && !item.isTranslating) {
                                                val itemManga = mangaById[item.chapter.mangaId] ?: manga
                                                val seriesTitle = itemManga?.title.orEmpty()
                                                val novelSource = itemManga?.let { sourceManager.get(it.source) as? NovelSourceWrapper }
                                                val orig = item.originalText!!
                                                scope.launch(Dispatchers.IO) {
                                                    val cached = NovelTranslator.getCached(
                                                        chapterId = item.chapter.id,
                                                        seriesTitle = seriesTitle,
                                                        originalText = orig,
                                                    )
                                                    if (cached != null) {
                                                        val cleanTranslated = formatNovelText(cached, novelSource?.baseUrl)
                                                        val transBlocks = splitNovelBlocks(cleanTranslated)
                                                        withContext(Dispatchers.Main) {
                                                            item.translatedText = cleanTranslated
                                                            item.translatedBlocks = transBlocks
                                                        }
                                                    } else {
                                                        withContext(Dispatchers.Main) {
                                                            item.isTranslating = true
                                                        }
                                                        try {
                                                            val translated = NovelTranslator.translate(
                                                                chapterId = item.chapter.id,
                                                                text = orig,
                                                                seriesTitle = seriesTitle,
                                                            )
                                                            val cleanTranslated = formatNovelText(translated, novelSource?.baseUrl)
                                                            val transBlocks = splitNovelBlocks(cleanTranslated)
                                                            withContext(Dispatchers.Main) {
                                                                item.translatedText = cleanTranslated
                                                                item.translatedBlocks = transBlocks
                                                                item.isTranslating = false
                                                            }
                                                        } catch (e: Exception) {
                                                            withContext(Dispatchers.Main) {
                                                                item.isTranslating = false
                                                                toast(e.message ?: "Erro ao traduzir capítulo")
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Translate,
                                        contentDescription = stringResource(ATMR.strings.manga_translate),
                                        tint = if (isTranslated) MaterialTheme.colorScheme.primary else uiSubtextColor,
                                    )
                                }
                                Text(
                                    text = if (isTranslated) "PT" else "ORIG",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isTranslated) MaterialTheme.colorScheme.primary else uiSubtextColor,
                                    modifier = Modifier.padding(end = 4.dp),
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = uiBgColor,
                            titleContentColor = uiTextColor,
                            navigationIconContentColor = uiIconTint,
                            actionIconContentColor = uiIconTint,
                        ),
                    )
                }

                // Manhwa-style Bottom Bar with Chapter Navigator (Slides in/out on tap)
                AnimatedVisibility(
                    visible = menuVisible,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    Surface(
                        color = uiBgColor,
                        tonalElevation = 6.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            val currentChapter = currentVisibleChapter
                            val currentIdx = allChapters.indexOfFirst { it.id == currentChapter?.id }

                            // Previous / Next chapter buttons row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                IconButton(
                                    onClick = {
                                        if (currentIdx > 0) {
                                            navigateToChapter(allChapters[currentIdx - 1])
                                        }
                                    },
                                    enabled = currentIdx > 0,
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.SkipPrevious,
                                        contentDescription = stringResource(MR.strings.action_previous_chapter),
                                        tint = if (currentIdx > 0) uiIconTint else uiSubtextColor.copy(alpha = 0.4f),
                                    )
                                }

                                Text(
                                    text = if (allChapters.isNotEmpty() && currentIdx != -1) {
                                        "${currentIdx + 1} / ${allChapters.size}"
                                    } else {
                                        currentChapter?.name ?: ""
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = uiTextColor,
                                )

                                IconButton(
                                    onClick = {
                                        if (currentIdx != -1 && currentIdx + 1 < allChapters.size) {
                                            navigateToChapter(allChapters[currentIdx + 1])
                                        }
                                    },
                                    enabled = currentIdx != -1 && currentIdx + 1 < allChapters.size,
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.SkipNext,
                                        contentDescription = stringResource(MR.strings.action_next_chapter),
                                        tint = if (currentIdx != -1 && currentIdx + 1 < allChapters.size) uiIconTint else uiSubtextColor.copy(alpha = 0.4f),
                                    )
                                }
                            }

                            // Chapter Scrubbing Slider
                            if (allChapters.size > 1) {
                                val safeIdx = currentIdx.coerceAtLeast(0)
                                var sliderValue by remember(safeIdx) { mutableFloatStateOf(safeIdx.toFloat()) }

                                Slider(
                                    value = sliderValue,
                                    onValueChange = { sliderValue = it },
                                    onValueChangeFinished = {
                                        val targetIdx = sliderValue.toInt().coerceIn(0, allChapters.size - 1)
                                        navigateToChapter(allChapters[targetIdx])
                                    },
                                    valueRange = 0f..(allChapters.size - 1).toFloat(),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                        inactiveTrackColor = uiSubtextColor.copy(alpha = 0.3f),
                                    ),
                                )
                            }

                            HorizontalDivider(
                                color = uiSubtextColor.copy(alpha = 0.15f),
                                modifier = Modifier.padding(vertical = 6.dp),
                            )

                            // Novel TTS Quick Bar in Bottom Sheet
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(uiSubtextColor.copy(alpha = 0.12f))
                                        .clickable { showAudioDialog = true }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Settings,
                                        contentDescription = stringResource(ATMR.strings.novel_tts_title),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${selectedVoice.title.substringBefore(" ")} • ${speechSpeed}x",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = uiTextColor,
                                    )
                                }

                                Button(
                                    onClick = { toggleTtsPlayback() },
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                ) {
                                    Icon(
                                        imageVector = if (isTtsPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = if (isTtsPlaying) stringResource(ATMR.strings.novel_tts_pause) else stringResource(ATMR.strings.novel_tts_listen),
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isTtsPlaying) stringResource(ATMR.strings.novel_tts_pause_short) else stringResource(ATMR.strings.novel_tts_listen_short),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Reader Customization Dialog (Kindle / E-Book Style)
            if (showSettingsDialog) {
                AlertDialog(
                    onDismissRequest = { showSettingsDialog = false },
                    containerColor = uiSurfaceColor,
                    titleContentColor = uiTextColor,
                    textContentColor = uiTextColor,
                    title = {
                        Text(
                            text = stringResource(ATMR.strings.novel_reader_settings),
                            color = uiTextColor,
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                        ) {
                            // Font Size Adjustment
                            Text(
                                text = stringResource(ATMR.strings.novel_font_size, fontSize.toInt()),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = uiTextColor,
                            )
                            Slider(
                                value = fontSize,
                                onValueChange = {
                                    fontSize = it
                                    prefs.edit().putFloat("font_size", it).apply()
                                },
                                valueRange = 13f..28f,
                                steps = 15,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = uiSubtextColor.copy(alpha = 0.3f),
                                ),
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Font Family Selection
                            Text(
                                text = "Tipografia / Fonte",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = uiTextColor,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                NovelFontFamily.entries.forEach { familyOption ->
                                    FilterChip(
                                        selected = fontFamilyChoice == familyOption,
                                        onClick = {
                                            fontFamilyChoice = familyOption
                                            prefs.edit().putString("font_family", familyOption.name).apply()
                                        },
                                        label = { Text(familyOption.label, style = MaterialTheme.typography.labelSmall) },
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Line Height & Paragraph Spacing & Margins
                            Text(
                                text = "Altura da linha (${"%.1f".format(Locale.US, lineHeightMultiplier)}x)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = uiTextColor,
                            )
                            Slider(
                                value = lineHeightMultiplier,
                                onValueChange = {
                                    lineHeightMultiplier = it
                                    prefs.edit().putFloat("line_height_mult", it).apply()
                                },
                                valueRange = 1.2f..2.2f,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = uiSubtextColor.copy(alpha = 0.3f),
                                ),
                            )

                            Text(
                                text = "Espaçamento entre parágrafos (${paragraphSpacingDp.toInt()}dp)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = uiTextColor,
                            )
                            Slider(
                                value = paragraphSpacingDp,
                                onValueChange = {
                                    paragraphSpacingDp = it
                                    prefs.edit().putFloat("paragraph_spacing", it).apply()
                                },
                                valueRange = 6f..28f,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = uiSubtextColor.copy(alpha = 0.3f),
                                ),
                            )

                            Text(
                                text = "Margens laterais (${horizontalMarginDp.toInt()}dp)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = uiTextColor,
                            )
                            Slider(
                                value = horizontalMarginDp,
                                onValueChange = {
                                    horizontalMarginDp = it
                                    prefs.edit().putFloat("horizontal_margin", it).apply()
                                },
                                valueRange = 10f..40f,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = uiSubtextColor.copy(alpha = 0.3f),
                                ),
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Texto Justificado",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = uiTextColor,
                                )
                                Switch(
                                    checked = justifyText,
                                    onCheckedChange = {
                                        justifyText = it
                                        prefs.edit().putBoolean("justify_text", it).apply()
                                    },
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Rolar com botões de volume",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = uiTextColor,
                                )
                                Switch(
                                    checked = volumeScrollPref,
                                    onCheckedChange = {
                                        volumeScrollPref = it
                                        volumeKeysScrollEnabled = it
                                        prefs.edit().putBoolean("volume_keys_scroll", it).apply()
                                    },
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Theme Selection
                            Text(
                                text = stringResource(ATMR.strings.novel_background_color),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = uiTextColor,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                ReaderTheme.entries.forEach { theme ->
                                    val isSelected = readerTheme == theme
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                readerTheme = theme
                                                prefs.edit()
                                                    .putString("theme_name", theme.name)
                                                    .putInt("theme_index", theme.ordinal)
                                                    .apply()
                                            }
                                            .padding(6.dp),
                                    ) {
                                        val circleBg = when (theme) {
                                            ReaderTheme.DEFAULT -> if (isDark) Color(0xFF1E1E1E) else Color(0xFFFFFFFF)
                                            ReaderTheme.LIGHT -> Color(0xFFFFFFFF)
                                            ReaderTheme.SEPIA -> Color(0xFFFBF0D9)
                                            ReaderTheme.AMOLED -> Color(0xFF000000)
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(CircleShape)
                                                .background(circleBg)
                                                .border(
                                                    width = if (isSelected) 2.5.dp else 1.dp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else uiSubtextColor.copy(alpha = 0.4f),
                                                    shape = CircleShape,
                                                ),
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = theme.title,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else uiSubtextColor,
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = uiSubtextColor.copy(alpha = 0.2f))
                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = stringResource(ATMR.strings.novel_voice_and_narration),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = uiTextColor,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    showSettingsDialog = false
                                    showAudioDialog = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Settings,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(ATMR.strings.novel_configure_voice, selectedVoice.title.substringBefore(" "), speechSpeed))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            androidx.compose.material3.OutlinedButton(
                                onClick = {
                                    val currentManga = manga ?: return@OutlinedButton
                                    showSettingsDialog = false
                                    toast("Gerando EPUB...")
                                    val fallbackMap = loadedChapters.mapNotNull { item ->
                                        val content = if (isTranslated && !item.translatedText.isNullOrBlank()) {
                                            item.translatedText
                                        } else {
                                            item.originalText
                                        }
                                        if (!content.isNullOrBlank()) item.chapter.id to content else null
                                    }.toMap()
                                    scope.launch {
                                        val result = eu.kanade.tachiyomi.extension.novel.epub.NovelEpubExporter.exportNovelToEpub(
                                            context = this@NovelReaderActivity,
                                            manga = currentManga,
                                            chapters = allChapters,
                                            preferTranslated = isTranslated,
                                            fallbackChaptersContent = fallbackMap,
                                        )
                                        result.onSuccess { epubFile ->
                                            toast("EPUB salvo na pasta novels do Yomotsu!")
                                            eu.kanade.tachiyomi.extension.novel.epub.NovelEpubExporter.shareEpub(this@NovelReaderActivity, epubFile)
                                        }.onFailure { err ->
                                            toast(err.message ?: "Erro ao exportar EPUB")
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Exportar capítulos baixados (.EPUB)")
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showSettingsDialog = false }) {
                            Text(
                                text = stringResource(MR.strings.action_close),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                )
            }

            // TTS Voice and Audio Configuration Bottom Sheet
            if (showAudioDialog) {
                val audioSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ModalBottomSheet(
                    onDismissRequest = { showAudioDialog = false },
                    sheetState = audioSheetState,
                    containerColor = uiSurfaceColor,
                    contentColor = uiTextColor,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 32.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(ATMR.strings.novel_tts_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = uiTextColor,
                            )
                            IconButton(onClick = { showAudioDialog = false }) {
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = stringResource(MR.strings.action_close),
                                    tint = uiSubtextColor,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = stringResource(ATMR.strings.novel_tts_narrator_voice),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        NovelTtsVoice.entries.forEach { voice ->
                            val isSelected = selectedVoice == voice
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        selectedVoice = voice
                                        prefs.edit().putString("novel_tts_voice", voice.id).apply()
                                        if (isTtsPlaying) {
                                            audioPlayer?.updateVoiceOrSpeed(voice, speechSpeed, isTranslated)
                                        }
                                    }
                                    .padding(vertical = 4.dp, horizontal = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        selectedVoice = voice
                                        prefs.edit().putString("novel_tts_voice", voice.id).apply()
                                        if (isTtsPlaying) {
                                            audioPlayer?.updateVoiceOrSpeed(voice, speechSpeed, isTranslated)
                                        }
                                    },
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = voice.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = uiTextColor,
                                    )
                                    Text(
                                        text = voice.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = uiSubtextColor,
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = uiSubtextColor.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = stringResource(ATMR.strings.novel_tts_reading_speed, speechSpeed),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            listOf(0.8f, 1.0f, 1.2f, 1.4f, 1.6f).forEach { speedOption ->
                                val isSpeedSelected = kotlin.math.abs(speechSpeed - speedOption) < 0.05f
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isSpeedSelected) MaterialTheme.colorScheme.primary
                                            else uiSubtextColor.copy(alpha = 0.12f)
                                        )
                                        .clickable {
                                            speechSpeed = speedOption
                                            prefs.edit().putFloat("novel_tts_speed", speedOption).apply()
                                            if (isTtsPlaying) {
                                                audioPlayer?.updateVoiceOrSpeed(selectedVoice, speedOption, isTranslated)
                                            }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "${speedOption}x",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSpeedSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSpeedSelected) MaterialTheme.colorScheme.onPrimary else uiTextColor,
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = uiSubtextColor.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Avançar cap. automaticamente",
                                style = MaterialTheme.typography.bodyMedium,
                                color = uiTextColor,
                            )
                            Switch(
                                checked = autoAdvanceTts,
                                onCheckedChange = {
                                    autoAdvanceTts = it
                                    prefs.edit().putBoolean("novel_tts_auto_advance", it).apply()
                                },
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (sleepTimerMinutes > 0) "Sleep Timer (${sleepTimerMinutes} min restantes)" else "Sleep Timer (Desligar narração)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            listOf(0, 15, 30, 45, 60).forEach { mins ->
                                val isTimerSelected = sleepTimerMinutes == mins
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isTimerSelected) MaterialTheme.colorScheme.primary
                                            else uiSubtextColor.copy(alpha = 0.12f),
                                        )
                                        .clickable {
                                            sleepTimerMinutes = mins
                                            audioPlayer?.setSleepTimer(mins)
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = if (mins == 0) "Off" else "${mins}m",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isTimerSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isTimerSelected) MaterialTheme.colorScheme.onPrimary else uiTextColor,
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = stringResource(ATMR.strings.novel_tts_background_notice),
                            style = MaterialTheme.typography.bodySmall,
                            color = uiSubtextColor.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        }
    }
}
