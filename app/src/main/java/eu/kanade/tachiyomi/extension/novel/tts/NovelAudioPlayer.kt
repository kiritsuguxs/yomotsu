package eu.kanade.tachiyomi.extension.novel.tts

import android.content.Context
import android.media.MediaPlayer
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class NovelAudioPlayer(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private var mediaPlayer: MediaPlayer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false

    private var wakeLock: PowerManager.WakeLock? = null
    private var playbackJob: Job? = null
    private var sleepTimerJob: Job? = null

    var isPlaying: Boolean = false
        private set

    var currentChunkIndex: Int = 0
        private set

    var currentChunkText: String? = null
        private set

    private var lastRawText: String = ""
    private var currentChunks: List<String> = emptyList()

    var sleepTimerRemainingMinutes: Int = 0
        private set

    var onStateChanged: ((Boolean) -> Unit)? = null
    var onChunkChanged: ((Int, String) -> Unit)? = null
    var onChapterFinished: (() -> Unit)? = null
    var onSleepTimerChanged: ((Int) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    init {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Yomotsu:NovelAudioPlayerWakeLock")
        initNativeTts()
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        sleepTimerRemainingMinutes = minutes.coerceAtLeast(0)
        onSleepTimerChanged?.invoke(sleepTimerRemainingMinutes)
        if (minutes <= 0) return

        sleepTimerJob = scope.launch(Dispatchers.Main) {
            var remaining = minutes
            while (remaining > 0 && isActive) {
                delay(60_000L)
                remaining--
                sleepTimerRemainingMinutes = remaining
                onSleepTimerChanged?.invoke(remaining)
            }
            if (remaining <= 0 && isPlaying) {
                stop()
            }
        }
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(2 * 60 * 60 * 1000L) // 2 hours max
            }
        } catch (e: Throwable) {
            logcat(LogPriority.WARN, e) { "Failed to acquire wake lock" }
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Throwable) {}
    }

    private fun initNativeTts(onReady: (() -> Unit)? = null) {
        if (textToSpeech != null && isTtsInitialized) {
            onReady?.invoke()
            return
        }
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsInitialized = true
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        val idx = utteranceId
                            ?.removePrefix("novel_p_")
                            ?.removeSuffix("_last")
                            ?.toIntOrNull()
                        if (idx != null) {
                            scope.launch(Dispatchers.Main) {
                                currentChunkIndex = idx
                                val chunkStr = currentChunks.getOrNull(idx).orEmpty()
                                currentChunkText = chunkStr
                                onChunkChanged?.invoke(idx, chunkStr)
                            }
                        }
                    }
                    override fun onDone(utteranceId: String?) {
                        if (utteranceId?.endsWith("_last") == true) {
                            scope.launch(Dispatchers.Main) {
                                stop(resetProgress = true)
                                onChapterFinished?.invoke()
                            }
                        }
                    }
                    override fun onError(utteranceId: String?) {
                        scope.launch(Dispatchers.Main) {
                            stop()
                        }
                    }
                })
                textToSpeech?.let { configureNativeVoice(it, Locale("pt", "BR")) }
                onReady?.invoke()
            } else {
                isTtsInitialized = false
            }
        }
    }

    private fun configureNativeVoice(tts: TextToSpeech, targetLocale: Locale, speed: Float = 1.02f) {
        try {
            val availableVoices = tts.voices
            if (!availableVoices.isNullOrEmpty()) {
                val matchingVoices = availableVoices.filter { voice ->
                    voice.locale.language.equals(targetLocale.language, ignoreCase = true)
                }

                if (matchingVoices.isNotEmpty()) {
                    fun isFemale(voice: Voice): Boolean {
                        val nameLower = voice.name.lowercase(Locale.ROOT)
                        val hasFemaleFeature = voice.features?.any { feature ->
                            val fLower = feature.lowercase(Locale.ROOT)
                            fLower == "female" || fLower.contains("female") ||
                                fLower.contains("mulher") || fLower.contains("feminino")
                        } ?: false

                        return hasFemaleFeature ||
                            nameLower.contains("female") ||
                            nameLower.contains("#female") ||
                            nameLower.contains("-f-") ||
                            nameLower.contains("_f_") ||
                            nameLower.contains("feminino") ||
                            nameLower.contains("mulher") ||
                            nameLower.contains("-afs") ||
                            nameLower.contains("-ptd") ||
                            nameLower.contains("-pte")
                    }

                    val offlineVoices = matchingVoices.filter { !it.isNetworkConnectionRequired }
                    val pool = if (offlineVoices.isNotEmpty()) offlineVoices else matchingVoices

                    val bestVoice = pool.maxWithOrNull(
                        compareBy<Voice> { isFemale(it) }
                            .thenBy { it.locale.country.equals(targetLocale.country, ignoreCase = true) }
                            .thenBy { it.quality }
                    )

                    if (bestVoice != null) {
                        tts.voice = bestVoice
                    }
                }
            }
        } catch (_: Throwable) {}

        try {
            tts.setPitch(1.08f)
            tts.setSpeechRate(speed)
        } catch (_: Throwable) {}
    }

    fun play(
        text: String,
        voice: NovelTtsVoice,
        speed: Float,
        isPortuguese: Boolean,
        startChunkIndex: Int? = null,
    ) {
        val resumeIndex = startChunkIndex ?: if (text == lastRawText && currentChunkIndex > 0) currentChunkIndex else 0
        stop(resetProgress = false)
        lastRawText = text
        val sanitized = NovelSpeechSanitizer.sanitize(text)
        if (sanitized.isBlank()) {
            onError?.invoke("Texto vazio para narração.")
            return
        }

        val chunks = splitIntoChunks(sanitized)
        currentChunks = chunks
        if (chunks.isEmpty()) {
            onError?.invoke("Texto vazio para narração.")
            return
        }
        val safeStartIndex = resumeIndex.coerceIn(0, chunks.lastIndex)
        currentChunkIndex = safeStartIndex

        isPlaying = true
        onStateChanged?.invoke(true)
        acquireWakeLock()

        if (voice.isOnline) {
            playWithEdgeTts(chunks, safeStartIndex, voice, speed, isPortuguese)
        } else {
            playWithNativeTts(chunks, safeStartIndex, speed, isPortuguese)
        }
    }

    fun updateVoiceOrSpeed(
        voice: NovelTtsVoice,
        speed: Float,
        isPortuguese: Boolean,
    ) {
        if (isPlaying && lastRawText.isNotBlank()) {
            play(
                text = lastRawText,
                voice = voice,
                speed = speed,
                isPortuguese = isPortuguese,
                startChunkIndex = currentChunkIndex,
            )
        }
    }

    private fun playWithNativeTts(chunks: List<String>, startIndex: Int, speed: Float, isPortuguese: Boolean) {
        initNativeTts {
            val tts = textToSpeech ?: return@initNativeTts
            val targetLocale = if (isPortuguese) Locale("pt", "BR") else Locale.getDefault()
            var effectiveLocale = targetLocale
            var langResult = tts.setLanguage(targetLocale)
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                langResult = tts.setLanguage(Locale("pt"))
                effectiveLocale = Locale("pt")
            }
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.language = Locale.getDefault()
                effectiveLocale = Locale.getDefault()
            }
            configureNativeVoice(tts, effectiveLocale, speed)
            tts.stop()

            for (index in startIndex until chunks.size) {
                val chunk = chunks[index]
                val queueMode = if (index == startIndex) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                val isLast = index == chunks.lastIndex
                val utteranceId = if (isLast) "novel_p_${index}_last" else "novel_p_$index"
                tts.speak(chunk, queueMode, null, utteranceId)
            }
        }
    }

    private fun playWithEdgeTts(
        chunks: List<String>,
        startIndex: Int,
        voice: NovelTtsVoice,
        speed: Float,
        isPortuguese: Boolean,
    ) {
        playbackJob?.cancel()
        playbackJob = scope.launch(Dispatchers.IO) {
            if (chunks.isEmpty()) {
                withContext(Dispatchers.Main) { stop(resetProgress = true) }
                return@launch
            }

            val prefetchMap = ConcurrentHashMap<Int, Deferred<ByteArray?>>()

            fun getChunkDeferred(idx: Int): Deferred<ByteArray?> {
                return prefetchMap.computeIfAbsent(idx) {
                    async(Dispatchers.IO) {
                        if (idx in chunks.indices) {
                            EdgeTtsClient.synthesize(chunks[idx], voice.id, speed)
                        } else null
                    }
                }
            }

            // Immediately launch synthesis for startIndex and prefetch startIndex + 1 in parallel
            getChunkDeferred(startIndex)
            if (startIndex + 1 < chunks.size) {
                getChunkDeferred(startIndex + 1)
            }

            suspend fun playChunk(chunkIdx: Int) {
                if (!isPlaying) return
                if (chunkIdx >= chunks.size) {
                    withContext(Dispatchers.Main) {
                        stop(resetProgress = true)
                        onChapterFinished?.invoke()
                    }
                    return
                }

                withContext(Dispatchers.Main) {
                    currentChunkIndex = chunkIdx
                    val chunkStr = chunks[chunkIdx]
                    currentChunkText = chunkStr
                    onChunkChanged?.invoke(chunkIdx, chunkStr)
                }

                // Prefetch the next chunks in background while current chunk is playing
                if (chunkIdx + 1 < chunks.size) getChunkDeferred(chunkIdx + 1)
                if (chunkIdx + 2 < chunks.size) getChunkDeferred(chunkIdx + 2)

                val deferred = getChunkDeferred(chunkIdx)
                val audioBytes = deferred.await()

                // Free memory for previous completed chunks
                prefetchMap.remove(chunkIdx - 1)

                if (audioBytes == null || audioBytes.isEmpty()) {
                    logcat(LogPriority.INFO) { "Edge TTS unavailable, falling back to Native TTS for remaining chunks from $chunkIdx" }
                    withContext(Dispatchers.Main) {
                        playWithNativeTts(chunks, chunkIdx, speed, isPortuguese)
                    }
                    return
                }

                val tempFile = File(context.cacheDir, "edge_tts_chunk_${chunkIdx % 2}.mp3")
                try {
                    FileOutputStream(tempFile).use { it.write(audioBytes) }
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Failed to write TTS audio cache" }
                    withContext(Dispatchers.Main) {
                        playWithNativeTts(chunks, chunkIdx, speed, isPortuguese)
                    }
                    return
                }

                withContext(Dispatchers.Main) {
                    if (!isPlaying) return@withContext
                    try {
                        mediaPlayer?.stop()
                        mediaPlayer?.release()
                    } catch (_: Throwable) {}

                    mediaPlayer = MediaPlayer().apply {
                        setDataSource(tempFile.absolutePath)
                        setOnCompletionListener {
                            scope.launch(Dispatchers.IO) {
                                playChunk(chunkIdx + 1)
                            }
                        }
                        setOnErrorListener { _, _, _ ->
                            scope.launch(Dispatchers.IO) {
                                playChunk(chunkIdx + 1)
                            }
                            true
                        }
                        prepare()
                        start()
                    }
                }
            }

            playChunk(startIndex)
        }
    }

    fun stop(resetProgress: Boolean = false) {
        playbackJob?.cancel()
        playbackJob = null
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Throwable) {}
        mediaPlayer = null

        try {
            textToSpeech?.stop()
        } catch (_: Throwable) {}

        releaseWakeLock()

        if (resetProgress) {
            currentChunkIndex = 0
            currentChunkText = null
            onChunkChanged?.invoke(-1, "")
        }

        if (isPlaying) {
            isPlaying = false
            onStateChanged?.invoke(false)
        }
    }

    fun release() {
        sleepTimerJob?.cancel()
        stop(resetProgress = true)
        try {
            textToSpeech?.shutdown()
        } catch (_: Throwable) {}
        textToSpeech = null
    }

    internal fun splitIntoChunks(
        text: String,
        initialChunkMax: Int = 260,
        subsequentChunkMax: Int = 850,
    ): List<String> {
        val lines = text.split(Regex("\\r?\\n+")).map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return emptyList()

        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        var isFirstChunk = true

        fun currentMax(): Int = if (isFirstChunk) initialChunkMax else subsequentChunkMax

        fun flushCurrent() {
            if (current.isNotEmpty()) {
                chunks.add(current.toString().trim())
                current.clear()
                isFirstChunk = false
            }
        }

        for (line in lines) {
            val max = currentMax()
            if (current.length + line.length + 1 > max) {
                if (current.isNotEmpty()) {
                    flushCurrent()
                }

                if (line.length > currentMax()) {
                    var remaining = line
                    while (remaining.length > currentMax()) {
                        val limit = currentMax()
                        val splitIdx = remaining.take(limit).lastIndexOfAny(charArrayOf('.', '!', '?', ';', ':', '—', '–', ',', ' '))
                            .takeIf { it > 50 } ?: limit
                        chunks.add(remaining.substring(0, splitIdx).trim())
                        isFirstChunk = false
                        remaining = remaining.substring(splitIdx).trim()
                    }
                    if (remaining.isNotEmpty()) {
                        current.append(remaining)
                    }
                } else {
                    current.append(line)
                }
            } else {
                if (current.isNotEmpty()) current.append(" ")
                current.append(line)
                if (isFirstChunk && current.length >= 100 && (line.endsWith('.') || line.endsWith('!') || line.endsWith('?'))) {
                    flushCurrent()
                }
            }
        }
        flushCurrent()
        return chunks.filter { it.isNotBlank() }
    }
}
