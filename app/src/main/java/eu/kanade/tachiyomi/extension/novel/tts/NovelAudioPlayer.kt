package eu.kanade.tachiyomi.extension.novel.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
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
import java.util.concurrent.atomic.AtomicLong

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
    private val playbackGeneration = AtomicLong(0L)

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var pausedByTransientLoss: Boolean = false
    private var lastVoice: NovelTtsVoice? = null
    private var lastSpeed: Float = 1.0f
    private var lastIsPortuguese: Boolean = true

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                pausedByTransientLoss = false
                scope.launch(Dispatchers.Main) {
                    stop(resetProgress = false)
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (isPlaying) {
                    pausedByTransientLoss = true
                    scope.launch(Dispatchers.Main) {
                        stopInternal(resetProgress = false, abandonFocus = false)
                    }
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (pausedByTransientLoss) {
                    pausedByTransientLoss = false
                    val voiceToResume = lastVoice
                    if (voiceToResume != null && lastRawText.isNotBlank()) {
                        scope.launch(Dispatchers.Main) {
                            play(
                                text = lastRawText,
                                voice = voiceToResume,
                                speed = lastSpeed,
                                isPortuguese = lastIsPortuguese,
                                startChunkIndex = currentChunkIndex,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        val am = audioManager ?: return true
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(playbackAttributes)
                    .setOnAudioFocusChangeListener(audioFocusChangeListener)
                    .build()
                audioFocusRequest = request
                am.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    audioFocusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN,
                ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
        } catch (_: Throwable) {
            true
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
                audioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(audioFocusChangeListener)
            }
        } catch (_: Throwable) {}
    }

    @Volatile
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
                        val parsed = parseUtteranceId(utteranceId) ?: return
                        if (parsed.first != playbackGeneration.get() || !isPlaying) return
                        val idx = parsed.second
                        scope.launch(Dispatchers.Main) {
                            if (parsed.first != playbackGeneration.get() || !isPlaying) return@launch
                            currentChunkIndex = idx
                            val chunkStr = currentChunks.getOrNull(idx).orEmpty()
                            currentChunkText = chunkStr
                            onChunkChanged?.invoke(idx, chunkStr)
                        }
                    }
                    override fun onDone(utteranceId: String?) {
                        val parsed = parseUtteranceId(utteranceId) ?: return
                        if (parsed.first != playbackGeneration.get() || !isPlaying) return
                        if (parsed.third) {
                            scope.launch(Dispatchers.Main) {
                                if (parsed.first != playbackGeneration.get() || !isPlaying) return@launch
                                stop(resetProgress = true)
                                onChapterFinished?.invoke()
                            }
                        }
                    }
                    override fun onError(utteranceId: String?) {
                        val parsed = parseUtteranceId(utteranceId)
                        if (parsed != null && parsed.first != playbackGeneration.get()) return
                        scope.launch(Dispatchers.Main) {
                            if (isPlaying) stop()
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

    private fun parseUtteranceId(utteranceId: String?): Triple<Long, Int, Boolean>? {
        if (utteranceId == null || !utteranceId.startsWith("novel_g")) return null
        val isLast = utteranceId.endsWith("_last")
        val clean = utteranceId.removePrefix("novel_g").removeSuffix("_last")
        val parts = clean.split("_p_")
        if (parts.size != 2) return null
        val gen = parts[0].toLongOrNull() ?: return null
        val idx = parts[1].toIntOrNull() ?: return null
        return Triple(gen, idx, isLast)
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
        pausedByTransientLoss = false
        stopInternal(resetProgress = false, abandonFocus = false)
        lastRawText = text
        lastVoice = voice
        lastSpeed = speed
        lastIsPortuguese = isPortuguese

        val sanitized = NovelSpeechSanitizer.sanitize(text)
        if (sanitized.isBlank()) {
            abandonAudioFocus()
            onError?.invoke("Texto vazio para narração.")
            return
        }

        val chunks = splitIntoChunks(sanitized)
        currentChunks = chunks
        if (chunks.isEmpty()) {
            abandonAudioFocus()
            onError?.invoke("Texto vazio para narração.")
            return
        }
        val safeStartIndex = resumeIndex.coerceIn(0, chunks.lastIndex)
        currentChunkIndex = safeStartIndex

        if (!requestAudioFocus()) {
            onError?.invoke("Não foi possível obter foco de áudio.")
            return
        }

        val generation = playbackGeneration.incrementAndGet()
        isPlaying = true
        onStateChanged?.invoke(true)
        acquireWakeLock()

        if (voice.isOnline) {
            playWithEdgeTts(generation, chunks, safeStartIndex, voice, speed, isPortuguese)
        } else {
            playWithNativeTts(generation, chunks, safeStartIndex, speed, isPortuguese)
        }
    }

    fun updateVoiceOrSpeed(
        voice: NovelTtsVoice,
        speed: Float,
        isPortuguese: Boolean,
    ) {
        lastVoice = voice
        lastSpeed = speed
        lastIsPortuguese = isPortuguese
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

    private fun playWithNativeTts(
        generation: Long,
        chunks: List<String>,
        startIndex: Int,
        speed: Float,
        isPortuguese: Boolean,
    ) {
        initNativeTts {
            if (!isPlaying || playbackGeneration.get() != generation) return@initNativeTts
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
                if (!isPlaying || playbackGeneration.get() != generation) break
                val chunk = chunks[index]
                val queueMode = if (index == startIndex) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                val isLast = index == chunks.lastIndex
                val utteranceId = if (isLast) "novel_g${generation}_p_${index}_last" else "novel_g${generation}_p_$index"
                tts.speak(chunk, queueMode, null, utteranceId)
            }
        }
    }

    private fun playWithEdgeTts(
        generation: Long,
        chunks: List<String>,
        startIndex: Int,
        voice: NovelTtsVoice,
        speed: Float,
        isPortuguese: Boolean,
    ) {
        playbackJob?.cancel()
        playbackJob = scope.launch(Dispatchers.IO) {
            val sessionScope = this
            if (chunks.isEmpty()) {
                withContext(Dispatchers.Main) {
                    if (playbackGeneration.get() == generation) stop(resetProgress = true)
                }
                return@launch
            }

            val prefetchMap = ConcurrentHashMap<Int, Deferred<ByteArray?>>()

            fun isCurrentSession(): Boolean = isPlaying && playbackGeneration.get() == generation && sessionScope.isActive

            fun getChunkDeferred(idx: Int): Deferred<ByteArray?> {
                return prefetchMap.computeIfAbsent(idx) {
                    sessionScope.async(Dispatchers.IO) {
                        if (idx in chunks.indices && isCurrentSession()) {
                            EdgeTtsClient.synthesize(chunks[idx], voice.id, speed)
                        } else {
                            null
                        }
                    }
                }
            }

            getChunkDeferred(startIndex)
            if (startIndex + 1 < chunks.size) {
                getChunkDeferred(startIndex + 1)
            }

            suspend fun playChunk(chunkIdx: Int) {
                if (!isCurrentSession()) return
                if (chunkIdx >= chunks.size) {
                    withContext(Dispatchers.Main) {
                        if (playbackGeneration.get() == generation) {
                            stop(resetProgress = true)
                            onChapterFinished?.invoke()
                        }
                    }
                    return
                }

                withContext(Dispatchers.Main) {
                    if (!isCurrentSession()) return@withContext
                    currentChunkIndex = chunkIdx
                    val chunkStr = chunks[chunkIdx]
                    currentChunkText = chunkStr
                    onChunkChanged?.invoke(chunkIdx, chunkStr)
                }

                if (chunkIdx + 1 < chunks.size) getChunkDeferred(chunkIdx + 1)
                if (chunkIdx + 2 < chunks.size) getChunkDeferred(chunkIdx + 2)

                val deferred = getChunkDeferred(chunkIdx)
                val audioBytes = deferred.await()

                prefetchMap.remove(chunkIdx - 1)
                if (!isCurrentSession()) return

                if (audioBytes == null || audioBytes.isEmpty()) {
                    logcat(LogPriority.INFO) { "Edge TTS unavailable, falling back to Native TTS for remaining chunks from $chunkIdx" }
                    withContext(Dispatchers.Main) {
                        if (isCurrentSession()) {
                            playWithNativeTts(generation, chunks, chunkIdx, speed, isPortuguese)
                        }
                    }
                    return
                }

                val tempFile = File(context.cacheDir, "edge_tts_chunk_${generation}_${chunkIdx % 2}.mp3")
                try {
                    FileOutputStream(tempFile).use { it.write(audioBytes) }
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Failed to write TTS audio cache" }
                    withContext(Dispatchers.Main) {
                        if (isCurrentSession()) {
                            playWithNativeTts(generation, chunks, chunkIdx, speed, isPortuguese)
                        }
                    }
                    return
                }

                val chunkCompleted = kotlinx.coroutines.CompletableDeferred<Boolean>()
                withContext(Dispatchers.Main) {
                    if (!isCurrentSession()) {
                        chunkCompleted.complete(false)
                        return@withContext
                    }
                    try {
                        mediaPlayer?.stop()
                        mediaPlayer?.release()
                    } catch (_: Throwable) {}

                    mediaPlayer = MediaPlayer().apply {
                        setDataSource(tempFile.absolutePath)
                        setOnCompletionListener {
                            chunkCompleted.complete(true)
                        }
                        setOnErrorListener { _, _, _ ->
                            chunkCompleted.complete(true)
                            true
                        }
                        prepare()
                        start()
                    }
                }

                try {
                    val shouldAdvance = chunkCompleted.await()
                    if (shouldAdvance && isCurrentSession()) {
                        playChunk(chunkIdx + 1)
                    }
                } finally {
                    runCatching { tempFile.delete() }
                }
            }

            try {
                playChunk(startIndex)
            } finally {
                prefetchMap.values.forEach { it.cancel() }
                prefetchMap.clear()
            }
        }
    }

    fun stop(resetProgress: Boolean = false) {
        pausedByTransientLoss = false
        stopInternal(resetProgress = resetProgress, abandonFocus = true)
    }

    private fun stopInternal(resetProgress: Boolean, abandonFocus: Boolean) {
        playbackGeneration.incrementAndGet()
        playbackJob?.cancel()
        playbackJob = null
        try {
            mediaPlayer?.setOnCompletionListener(null)
            mediaPlayer?.setOnErrorListener(null)
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Throwable) {}
        mediaPlayer = null

        try {
            textToSpeech?.stop()
        } catch (_: Throwable) {}

        releaseWakeLock()
        if (abandonFocus) {
            abandonAudioFocus()
        }

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
