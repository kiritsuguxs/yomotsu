package eu.kanade.tachiyomi.data.telegram

import android.content.Context
import com.hippo.unifile.UniFile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import org.json.JSONArray
import org.json.JSONObject
import eu.kanade.tachiyomi.util.storage.DiskUtil
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.storage.service.StorageManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import tachiyomi.domain.manga.interactor.GetLibraryManga
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody

data class PendingUpload(
    val file: UniFile,
    val manga: Manga? = null,
    val chapter: Chapter? = null,
    val tempFile: File? = null,
    val isNovel: Boolean = false,
)

data class CloudChapter(
    val name: String,
    val messageId: Long,
    val fileId: Int,
    val remoteFileId: String,
    val date: Long = System.currentTimeMillis(),
)

data class CloudManga(
    val title: String,
    val description: String = "",
    val coverUrl: String = "",
    val chapters: MutableList<CloudChapter> = mutableListOf(),
    val isNovel: Boolean = false,
)

class TelegramCloudManager(
    private val context: Context,
    private val preferences: tachiyomi.domain.telegram.TelegramPreferences = Injekt.get()
) : Client.ResultHandler {

    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tdClient: Client? = null
    private var activeBotToken: String = ""
    private var remoteIndexUploadJob: Job? = null
    val isAuthReady = MutableStateFlow(false)
    var lastDownloadError: String? = null

    // Usando as chaves publicas do Telegram Desktop (Open Source) para evitar API_ID_INVALID
    private val apiId = 2040
    private val apiHash = "b18441a1ff607e10a989891a5462e627"

    private val pendingUploads = ConcurrentHashMap<Long, PendingUpload>()
    private val pendingDownloads = ConcurrentHashMap<Int, CompletableDeferred<String>>()
    private val indexMutex = Mutex()

    companion object {
        private val uploadMutex = Mutex()
        private val BOT_TOKEN_REGEX = Regex("""\b\d{6,12}:[A-Za-z0-9_-]{30,}\b""")
        private const val INDEX_TAG = "#YomotsuIndex"
        private const val INDEX_FILENAME = "telegram_cloud_index.json"
    }

    init {
        if (preferences.enableTelegramCloud.get()) {
            initializeTdlib()
        }
        observePreferenceChanges()
    }

    private fun normalizedBotToken(): String {
        val raw = preferences.botToken.get().trim()
        return if (raw.startsWith("bot", ignoreCase = true)) raw.substring(3).trim() else raw
    }

    private fun redactSecrets(text: String?): String {
        if (text.isNullOrEmpty()) return ""
        val rawToken = preferences.botToken.get().trim()
        val normToken = normalizedBotToken()
        var redacted = BOT_TOKEN_REGEX.replace(text, "[REDACTED_TOKEN]")
        if (rawToken.length >= 8) redacted = redacted.replace(rawToken, "[REDACTED_TOKEN]")
        if (normToken.length >= 8) redacted = redacted.replace(normToken, "[REDACTED_TOKEN]")
        return redacted
    }

    private fun observePreferenceChanges() {
        runCatching {
            managerScope.launch {
                preferences.enableTelegramCloud.changes().drop(1).collectLatest { enabled ->
                    if (enabled) {
                        initializeTdlib()
                    }
                }
            }
            managerScope.launch {
                preferences.botToken.changes().drop(1).collectLatest {
                    val newToken = normalizedBotToken()
                    if (!preferences.enableTelegramCloud.get() || newToken.isBlank()) return@collectLatest
                    if (tdClient == null) {
                        initializeTdlib()
                    } else if (!isAuthReady.value) {
                        authenticateBotToken(newToken)
                    } else if (activeBotToken.isNotBlank() && activeBotToken != newToken) {
                        isAuthReady.value = false
                        tdClient?.send(TdApi.LogOut()) {}
                    }
                }
            }
        }
    }

    private fun authenticateBotToken(token: String) {
        if (token.isBlank()) return
        activeBotToken = token
        tdClient?.send(TdApi.CheckAuthenticationBotToken(token)) { authResult ->
            if (authResult is TdApi.Error) {
                val safeMsg = redactSecrets(authResult.message)
                logcat(LogPriority.ERROR) { "Erro na autenticacao do Bot: $safeMsg" }
                showNotification("Nuvem Telegram", "Erro de Token: $safeMsg", ongoing = false)
            }
        }
    }

    @Synchronized
    fun initializeTdlib() {
        if (tdClient != null) return
        tdClient = Client.create(this, null, null)
    }

    fun releaseDownloadedFile(path: String, fileId: Int = 0) {
        if (path.isBlank()) return
        val file = File(path)
        if (file.absolutePath.startsWith(context.cacheDir.absolutePath)) {
            file.delete()
        } else if (fileId != 0) {
            tdClient?.send(TdApi.DeleteFile(fileId)) {}
        }
    }

    fun showNotification(
        title: String,
        text: String,
        progress: Int = 0,
        max: Int = 0,
        ongoing: Boolean = false,
        autoDismiss: Boolean = false
    ) {
        val notificationManager = androidx.core.app.NotificationManagerCompat.from(context)
        val builder = androidx.core.app.NotificationCompat.Builder(context, "downloader_progress_channel")
            .setContentTitle(title)
            .setContentText(redactSecrets(text))
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)

        if (max > 0) {
            builder.setProgress(max, progress, progress == 0)
        } else {
            builder.setProgress(0, 0, false)
        }

        if (autoDismiss) {
            builder.setTimeoutAfter(3000)
        }

        try {
            notificationManager.notify(889911, builder.build())
        } catch (e: SecurityException) {
            logcat(LogPriority.ERROR) { "Sem permissao de notificacao para a Nuvem Telegram" }
        }
    }

    override fun onResult(update: TdApi.Object?) {
        when (update) {
            is TdApi.UpdateAuthorizationState -> {
                when (update.authorizationState) {
                    is TdApi.AuthorizationStateWaitTdlibParameters -> {
                        val parameters = TdApi.SetTdlibParameters().apply {
                            databaseDirectory = File(context.filesDir, "tdlib_v2").absolutePath
                            useMessageDatabase = true
                            useChatInfoDatabase = true
                            useFileDatabase = true
                            useSecretChats = false
                            apiId = this@TelegramCloudManager.apiId
                            apiHash = this@TelegramCloudManager.apiHash
                            systemLanguageCode = "pt"
                            deviceModel = "Android"
                            applicationVersion = "Yomotsu-Cloud-1.0"
                        }
                        tdClient?.send(parameters) { result ->
                            if (result is TdApi.Error) {
                                logcat(LogPriority.ERROR) { "Erro SetTdlibParameters: ${redactSecrets(result.message)}" }
                            }
                        }
                    }
                    is TdApi.AuthorizationStateWaitPhoneNumber -> {
                        val botToken = normalizedBotToken()
                        if (botToken.isNotBlank()) {
                            authenticateBotToken(botToken)
                        } else {
                            logcat(LogPriority.ERROR) { "Bot Token nao configurado!" }
                        }
                    }
                    is TdApi.AuthorizationStateReady -> {
                        logcat(LogPriority.INFO) { "TDLib Ready! Autenticado com sucesso." }
                        isAuthReady.value = true

                        val chatIdString = preferences.chatId.get()
                        val targetChatId = chatIdString.toLongOrNull()
                        if (targetChatId != null) {
                            tdClient?.send(TdApi.OpenChat(targetChatId)) { openRes ->
                                logcat(LogPriority.INFO) { "Chat aberto no TDLib: $openRes" }
                            }
                        }
                    }
                    is TdApi.AuthorizationStateClosed -> {
                        synchronized(this) {
                            tdClient = null
                        }
                        isAuthReady.value = false
                        if (preferences.enableTelegramCloud.get() && normalizedBotToken().isNotBlank()) {
                            initializeTdlib()
                        }
                    }
                }
            }
            is TdApi.UpdateFile -> {
                val file = update.file
                if (file.local.isDownloadingCompleted && file.local.path.isNotBlank()) {
                    val f = File(file.local.path)
                    if (f.exists() && f.length() > 0L) {
                        val completer = pendingDownloads.remove(file.id)
                        completer?.complete(file.local.path)
                    }
                }
            }
            is TdApi.UpdateMessageSendSucceeded -> {
                val oldMessageId = update.oldMessageId
                val pending = pendingUploads.remove(oldMessageId)
                pending?.tempFile?.delete()
                val realMessage = update.message
                val realMessageId = realMessage.id
                val doc = (realMessage.content as? TdApi.MessageDocument)?.document?.document
                val realFileId = doc?.id ?: 0
                val realRemoteId = doc?.remote?.id ?: ""
                val hasValidRemoteDocument = doc != null && (doc.size > 0 || doc.expectedSize > 0) && realRemoteId.isNotBlank()

                logcat(LogPriority.INFO) { "Upload finalizado pelo Telegram com SUCESSO. Real ID: $realMessageId" }
                showNotification("Nuvem Telegram", "Upload concluído!", autoDismiss = true)

                if (pending?.manga != null && pending.chapter != null) {
                    managerScope.launch {
                        registerInIndex(
                            mangaTitle = pending.manga.title,
                            description = pending.manga.description ?: "",
                            coverUrl = pending.manga.thumbnailUrl ?: "",
                            chapterName = pending.chapter.name,
                            messageId = realMessageId,
                            fileId = realFileId,
                            remoteFileId = realRemoteId,
                            isNovel = pending.isNovel,
                        )
                    }
                } else {
                    managerScope.launch {
                        updateMessageIdInIndex(oldMessageId, realMessageId, realFileId, realRemoteId)
                    }
                }

                if (pending != null && preferences.deleteLocalAfterUpload.get() && hasValidRemoteDocument) {
                    val deleted = pending.file.delete()
                    logcat(LogPriority.INFO) { "Arquivo local apagado apos upload: $deleted" }
                    if (!pending.isNovel && pending.manga != null && pending.chapter != null) {
                        managerScope.launch {
                            try {
                                val downloadCache = Injekt.get<eu.kanade.tachiyomi.data.download.DownloadCache>()
                                downloadCache.removeChapter(pending.chapter, pending.manga)
                            } catch (e: Exception) {
                                logcat(LogPriority.ERROR, e) { "Erro ao atualizar cache de download" }
                            }
                        }
                    }
                }
            }
            is TdApi.UpdateMessageSendFailed -> {
                val messageId = update.oldMessageId
                val pending = pendingUploads.remove(messageId)
                pending?.tempFile?.delete()
                logcat(LogPriority.ERROR) { "Falha confirmada pelo Telegram no envio da mensagem." }
                showNotification("Nuvem Telegram", "Erro no upload", autoDismiss = true)
            }
        }
    }

    // ==========================================
    // INDICE LOCAL DE OBRAS SALVAS NA NUVEM
    // ==========================================

    private fun getIndexFile(): File = File(context.filesDir, INDEX_FILENAME)
    private fun getBackupIndexFile(): File = File(context.filesDir, "$INDEX_FILENAME.bak")

    fun getCloudIndex(): List<CloudManga> {
        val primaryFile = getIndexFile()
        val backupFile = getBackupIndexFile()

        val parsed = parseIndexFile(primaryFile)
        if (parsed != null) return parsed

        if (backupFile.exists()) {
            logcat(LogPriority.WARN) { "Índice primário inacessível ou corrompido; recuperando do backup .bak" }
            val backupParsed = parseIndexFile(backupFile)
            if (backupParsed != null) return backupParsed
        }

        return emptyList()
    }

    private fun parseIndexFile(file: File): List<CloudManga>? {
        if (!file.exists() || file.length() == 0L) return null
        return try {
            val jsonString = file.readText()
            val array = JSONArray(jsonString)
            val list = mutableListOf<CloudManga>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val chaptersArray = obj.getJSONArray("chapters")
                val chapters = mutableListOf<CloudChapter>()
                for (j in 0 until chaptersArray.length()) {
                    val cObj = chaptersArray.getJSONObject(j)
                    chapters.add(
                        CloudChapter(
                            name = cObj.getString("name"),
                            messageId = cObj.optLong("messageId", 0L),
                            fileId = cObj.optInt("fileId", 0),
                            remoteFileId = cObj.optString("remoteFileId", ""),
                            date = cObj.optLong("date", 0L),
                        ),
                    )
                }
                list.add(
                    CloudManga(
                        title = obj.getString("title"),
                        description = obj.optString("description", ""),
                        coverUrl = obj.optString("coverUrl", ""),
                        chapters = chapters,
                        isNovel = obj.optBoolean("isNovel", false),
                    ),
                )
            }
            list
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Erro ao ler arquivo de índice: ${file.name}" }
            null
        }
    }

    private fun internalSaveCloudIndex(list: List<CloudManga>, syncRemote: Boolean = true) {
        try {
            val array = JSONArray()
            for (manga in list) {
                val obj = JSONObject().apply {
                    put("title", manga.title)
                    put("description", manga.description)
                    put("coverUrl", manga.coverUrl)
                    put("isNovel", manga.isNovel)
                    val cArray = JSONArray()
                    for (chap in manga.chapters) {
                        cArray.put(
                            JSONObject().apply {
                                put("name", chap.name)
                                put("messageId", chap.messageId)
                                put("fileId", chap.fileId)
                                put("remoteFileId", chap.remoteFileId)
                                put("date", chap.date)
                            },
                        )
                    }
                    put("chapters", cArray)
                }
                array.put(obj)
            }

            val targetFile = getIndexFile()
            val backupFile = getBackupIndexFile()
            val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")

            tempFile.writeText(array.toString(2))

            if (targetFile.exists()) {
                targetFile.copyTo(backupFile, overwrite = true)
            }

            if (!tempFile.renameTo(targetFile)) {
                targetFile.delete()
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
            }

            if (syncRemote && list.isNotEmpty()) {
                scheduleRemoteIndexUpload()
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Erro ao salvar telegram_cloud_index.json atomicamente" }
        }
    }

    private fun scheduleRemoteIndexUpload() {
        remoteIndexUploadJob?.cancel()
        remoteIndexUploadJob = managerScope.launch {
            delay(4_000L)
            uploadRemoteIndexSnapshot()
        }
    }

    private suspend fun uploadRemoteIndexSnapshot() = withContext(Dispatchers.IO) {
        val botToken = normalizedBotToken()
        val chatId = preferences.chatId.get().trim()
        val indexFile = getIndexFile()
        if (!preferences.enableTelegramCloud.get() || botToken.isBlank() || chatId.isBlank() || !indexFile.exists() || indexFile.length() == 0L) {
            return@withContext
        }
        try {
            val client = Injekt.get<NetworkHelper>().client
            val mediaType = "application/json".toMediaType()
            val existingMsgId = preferences.lastIndexMessageId.get()

            if (existingMsgId > 0L) {
                val mediaJson = JSONObject()
                    .put("type", "document")
                    .put("media", "attach://index_doc")
                    .put("caption", INDEX_TAG)
                    .toString()
                val editBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("chat_id", chatId)
                    .addFormDataPart("message_id", existingMsgId.toString())
                    .addFormDataPart("media", mediaJson)
                    .addFormDataPart("index_doc", INDEX_FILENAME, indexFile.asRequestBody(mediaType))
                    .build()
                val editReq = Request.Builder()
                    .url("https://api.telegram.org/bot$botToken/editMessageMedia")
                    .post(editBody)
                    .build()
                client.newCall(editReq).execute().use { res ->
                    if (res.isSuccessful) {
                        val resJson = runCatching { JSONObject(res.body.string()) }.getOrNull()
                        if (resJson?.optBoolean("ok") == true) {
                            return@withContext
                        }
                    }
                }
            }

            val sendBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", chatId)
                .addFormDataPart("caption", INDEX_TAG)
                .addFormDataPart("disable_notification", "true")
                .addFormDataPart("document", INDEX_FILENAME, indexFile.asRequestBody(mediaType))
                .build()
            val sendReq = Request.Builder()
                .url("https://api.telegram.org/bot$botToken/sendDocument")
                .post(sendBody)
                .build()
            client.newCall(sendReq).execute().use { res ->
                if (!res.isSuccessful) return@withContext
                val resJson = JSONObject(res.body.string())
                if (!resJson.optBoolean("ok")) return@withContext
                val newMsgId = resJson.optJSONObject("result")?.optLong("message_id", 0L) ?: 0L
                if (newMsgId > 0L) {
                    preferences.lastIndexMessageId.set(newMsgId)
                    val pinReq = Request.Builder()
                        .url("https://api.telegram.org/bot$botToken/pinChatMessage?chat_id=$chatId&message_id=$newMsgId&disable_notification=true")
                        .get()
                        .build()
                    runCatching { client.newCall(pinReq).execute().close() }
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.WARN) { "Falha ao atualizar snapshot remoto do índice: ${redactSecrets(e.message)}" }
        }
    }

    private suspend fun fetchRemoteIndexSnapshot(targetChatId: Long): List<CloudManga>? = withContext(Dispatchers.IO) {
        val botToken = normalizedBotToken()
        if (botToken.isBlank() || targetChatId == 0L) return@withContext null
        try {
            val client = Injekt.get<NetworkHelper>().client
            val req = Request.Builder()
                .url("https://api.telegram.org/bot$botToken/getChat?chat_id=$targetChatId")
                .get()
                .build()
            val bodyString = client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@withContext null
                res.body.string()
            }
            val json = JSONObject(bodyString)
            if (!json.optBoolean("ok")) return@withContext null
            val pinned = json.optJSONObject("result")?.optJSONObject("pinned_message") ?: return@withContext null
            val pinnedCaption = pinned.optString("caption", "")
            val docObj = pinned.optJSONObject("document") ?: return@withContext null
            val fileName = docObj.optString("file_name", "")
            if (!pinnedCaption.contains(INDEX_TAG, ignoreCase = true) && !fileName.equals(INDEX_FILENAME, ignoreCase = true)) {
                return@withContext null
            }
            val remoteFileId = docObj.optString("file_id", "")
            if (remoteFileId.isBlank()) return@withContext null
            val pinnedMsgId = pinned.optLong("message_id", 0L)
            if (pinnedMsgId > 0L) {
                preferences.lastIndexMessageId.set(pinnedMsgId)
            }
            val tempIndex = File(context.cacheDir, "tg_remote_index_${System.currentTimeMillis()}.json")
            try {
                if (downloadViaBotApi(remoteFileId, tempIndex)) {
                    return@withContext parseIndexFile(tempIndex)
                }
            } finally {
                tempIndex.delete()
            }
            null
        } catch (e: Exception) {
            logcat(LogPriority.WARN) { "Falha ao buscar índice remoto no Telegram: ${redactSecrets(e.message)}" }
            null
        }
    }

    suspend fun saveCloudIndex(list: List<CloudManga>, syncRemote: Boolean = true) = indexMutex.withLock {
        withContext(Dispatchers.IO) {
            internalSaveCloudIndex(list, syncRemote = syncRemote)
        }
    }

    suspend fun clearCloudIndex() = indexMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val file = getIndexFile()
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Erro ao limpar índice da nuvem" }
            }
        }
    }

    suspend fun removeChapterFromIndex(mangaTitle: String, chapterName: String) = indexMutex.withLock {
        withContext(Dispatchers.IO) {
            val current = getCloudIndex().toMutableList()
            val manga = current.find { it.title.equals(mangaTitle, ignoreCase = true) } ?: return@withContext
            manga.chapters.removeAll { it.name.equals(chapterName, ignoreCase = true) }
            if (manga.chapters.isEmpty()) {
                current.remove(manga)
            }
            internalSaveCloudIndex(current)
        }
    }

    suspend fun updateMessageIdInIndex(
        oldMessageId: Long,
        realMessageId: Long,
        fileId: Int,
        remoteFileId: String
    ) = indexMutex.withLock {
        withContext(Dispatchers.IO) {
            val current = getCloudIndex().toMutableList()
            var modified = false
            for (manga in current) {
                for (i in manga.chapters.indices) {
                    val ch = manga.chapters[i]
                    if (ch.messageId == oldMessageId) {
                        manga.chapters[i] = ch.copy(
                            messageId = realMessageId,
                            fileId = if (fileId != 0) fileId else ch.fileId,
                            remoteFileId = if (remoteFileId.isNotBlank()) remoteFileId else ch.remoteFileId,
                            date = System.currentTimeMillis()
                        )
                        modified = true
                    }
                }
            }
            if (modified) {
                internalSaveCloudIndex(current)
            }
        }
    }

    suspend fun registerInIndex(
        mangaTitle: String,
        description: String,
        coverUrl: String,
        chapterName: String,
        messageId: Long,
        fileId: Int,
        remoteFileId: String,
        isNovel: Boolean = false,
    ) = indexMutex.withLock {
        withContext(Dispatchers.IO) {
            val current = getCloudIndex().toMutableList()
            var manga = current.find { it.title.equals(mangaTitle, ignoreCase = true) }
            if (manga == null) {
                manga = CloudManga(
                    title = mangaTitle,
                    description = description,
                    coverUrl = coverUrl,
                    isNovel = isNovel,
                )
                current.add(manga)
            } else {
                var mIdx = current.indexOf(manga)
                if (manga.description.isBlank() && description.isNotBlank()) {
                    manga = manga.copy(description = description)
                    current[mIdx] = manga
                }
                if (manga.coverUrl.isBlank() && coverUrl.isNotBlank()) {
                    mIdx = current.indexOf(manga)
                    manga = manga.copy(coverUrl = coverUrl)
                    current[mIdx] = manga
                }
                if (!manga.isNovel && isNovel) {
                    mIdx = current.indexOf(manga)
                    manga = manga.copy(isNovel = true)
                    current[mIdx] = manga
                }
            }
            val existingIndex = manga.chapters.indexOfFirst { it.name.equals(chapterName, ignoreCase = true) }
            val chapterObj = CloudChapter(
                name = chapterName,
                messageId = messageId,
                fileId = fileId,
                remoteFileId = remoteFileId,
                date = System.currentTimeMillis()
            )
            if (existingIndex >= 0) {
                val old = manga.chapters[existingIndex]
                manga.chapters[existingIndex] = CloudChapter(
                    name = chapterName,
                    messageId = if (messageId != 0L) messageId else old.messageId,
                    fileId = if (fileId != 0) fileId else old.fileId,
                    remoteFileId = if (remoteFileId.isNotBlank()) remoteFileId else old.remoteFileId,
                    date = System.currentTimeMillis()
                )
            } else {
                manga.chapters.add(chapterObj)
            }

            manga.chapters.sortBy { chap ->
                val numStr = chap.name.replace(Regex("""[^0-9.]"""), "")
                numStr.toFloatOrNull() ?: 999999f
            }

            internalSaveCloudIndex(current)
        }
    }

    suspend fun deleteChapter(mangaTitle: String, chapter: CloudChapter): Boolean = withContext(Dispatchers.IO) {
        val chatId = preferences.chatId.get().toLongOrNull() ?: 0L
        if (chatId != 0L && chapter.messageId != 0L) {
            val targetId = if (chapter.messageId in 1..1048575L) chapter.messageId shl 20 else chapter.messageId
            try {
                tdClient?.send(TdApi.DeleteMessages(chatId, longArrayOf(targetId), true)) { res ->
                    logcat(LogPriority.INFO) { "DeleteMessages resultado: $res" }
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Erro ao deletar mensagem no Telegram" }
            }
        }
        removeChapterFromIndex(mangaTitle, chapter.name)
        true
    }

    suspend fun deleteManga(mangaTitle: String): Boolean = indexMutex.withLock {
        withContext(Dispatchers.IO) {
            val chatId = preferences.chatId.get().toLongOrNull() ?: 0L
            val current = getCloudIndex().toMutableList()
            val manga = current.find { it.title.equals(mangaTitle, ignoreCase = true) } ?: return@withContext false

            if (chatId != 0L) {
                val msgIds = manga.chapters.map { chap ->
                    if (chap.messageId in 1..1048575L) chap.messageId shl 20 else chap.messageId
                }.filter { it != 0L }.toLongArray()

                if (msgIds.isNotEmpty()) {
                    try {
                        tdClient?.send(TdApi.DeleteMessages(chatId, msgIds, true)) { res ->
                            logcat(LogPriority.INFO) { "DeleteMessages (manga completo) resultado: $res" }
                        }
                    } catch (e: Exception) {
                        logcat(LogPriority.ERROR, e) { "Erro ao deletar mensagens no Telegram" }
                    }
                }
            }

            current.remove(manga)
            internalSaveCloudIndex(current)
            true
        }
    }

    // ==========================================
    // UPLOAD DE CAPITULO (MANGA)
    // ==========================================

    suspend fun uploadChapter(manga: Manga, chapter: Chapter, cbzFile: UniFile) {
        withContext(Dispatchers.IO) {
            if (!preferences.enableTelegramCloud.get()) return@withContext
            if (tdClient == null) initializeTdlib()

            val ready = withTimeoutOrNull(10000) {
                isAuthReady.first { it }
                true
            } ?: false

            if (!ready) {
                logcat(LogPriority.ERROR) { "TDLib nao inicializou a tempo para o upload." }
                showNotification("Nuvem Telegram", "Erro: Cliente nao inicializado", ongoing = false)
                return@withContext
            }

            val chatIdString = preferences.chatId.get()
            if (chatIdString.isBlank()) {
                logcat(LogPriority.ERROR) { "Chat ID vazio." }
                return@withContext
            }

            val targetChatId = chatIdString.toLongOrNull() ?: return@withContext

            uploadMutex.withLock {
                var fileToUpload: File? = null
                var isTemp = false
                var dispatched = false
                try {
                    val uniquePrefix = "tg_upload_${manga.id}_${chapter.id}_${System.currentTimeMillis()}"
                    fileToUpload = if (cbzFile.isDirectory) {
                        val tempZip = File(context.cacheDir, "$uniquePrefix.cbz")
                        val sortedFiles = cbzFile.listFiles()
                            ?.filter { it.isFile && !it.name.orEmpty().startsWith('.') }
                            ?.sortedWith { a, b -> a.name.orEmpty().compareToCaseInsensitiveNaturalOrder(b.name.orEmpty()) }
                            .orEmpty()
                        ZipOutputStream(tempZip.outputStream().buffered()).use { zipOut ->
                            sortedFiles.forEach { file ->
                                zipOut.putNextEntry(ZipEntry(file.name.orEmpty()))
                                file.openInputStream().use { it.copyTo(zipOut) }
                                zipOut.closeEntry()
                            }
                        }
                        tempZip
                    } else if (cbzFile.uri.scheme == "file") {
                        File(cbzFile.uri.path!!)
                    } else {
                        val temp = File(context.cacheDir, "${uniquePrefix}_${DiskUtil.buildValidFilename(cbzFile.name ?: "capitulo.cbz")}")
                        cbzFile.openInputStream().use { input -> temp.outputStream().use { output -> input.copyTo(output) } }
                        temp
                    }

                    isTemp = fileToUpload.absolutePath.startsWith(context.cacheDir.absolutePath)
                    val fileSizeMb = fileToUpload.length() / (1024 * 1024)
                    if (fileSizeMb > 1999L) {
                        logcat(LogPriority.WARN) { "Arquivo excede 2 GB!" }
                        return@withLock
                    }

                    showNotification("Nuvem Telegram", "Enviando: ${manga.title} - ${chapter.name}...", progress = 0, max = 100, ongoing = true)

                    val description = manga.description?.let {
                        if (it.length > 500) it.take(497) + "..." else it
                    } ?: "Sem sinopse disponivel."

                    val textCaption = "#Yomotsu\n\n📖 Obra: ${manga.title}\n📄 Capítulo: ${chapter.name}\n\n📝 Sinopse: $description"
                    val caption = TdApi.FormattedText(textCaption, emptyArray())

                    val coverCache = Injekt.get<eu.kanade.tachiyomi.data.cache.CoverCache>()
                    val coverFile = coverCache.getCoverFile(manga.thumbnailUrl)
                    val thumbnail = if (coverFile != null && coverFile.exists()) {
                        TdApi.InputThumbnail(TdApi.InputFileLocal(coverFile.absolutePath), 0, 0)
                    } else {
                        null
                    }

                    val inputFile = TdApi.InputFileLocal(fileToUpload.absolutePath)
                    val document = TdApi.InputMessageDocument(inputFile, thumbnail, false, caption)

                    val sendMessageRequest = TdApi.SendMessage(
                        targetChatId,
                        null,
                        null,
                        null,
                        null,
                        document
                    )

                    tdClient?.send(sendMessageRequest) { result ->
                        if (result is TdApi.Message) {
                            pendingUploads[result.id] = PendingUpload(cbzFile, manga, chapter, if (isTemp) fileToUpload else null)
                            val doc = (result.content as? TdApi.MessageDocument)?.document?.document
                            val fileId = doc?.id ?: 0
                            val remoteId = doc?.remote?.id ?: ""
                            managerScope.launch {
                                registerInIndex(
                                    mangaTitle = manga.title,
                                    description = manga.description ?: "",
                                    coverUrl = manga.thumbnailUrl ?: "",
                                    chapterName = chapter.name,
                                    messageId = result.id,
                                    fileId = fileId,
                                    remoteFileId = remoteId,
                                )
                            }
                            logcat(LogPriority.INFO) { "Mensagem despachada pro TDLib. ID = ${result.id}" }
                        } else if (result is TdApi.Error) {
                            if (isTemp) fileToUpload.delete()
                            val safeMsg = redactSecrets(result.message)
                            logcat(LogPriority.ERROR) { "Erro ao empurrar pra TDLib: $safeMsg" }
                            showNotification("Nuvem Telegram", "Erro: $safeMsg", autoDismiss = true)
                        }
                    }
                    dispatched = true

                    delay(3000)

                } catch (e: Exception) {
                    logcat(LogPriority.ERROR) { "Erro no fluxo de preparo da TDLib: ${redactSecrets(e.message)}" }
                    showNotification("Nuvem Telegram", "Falha interna no upload", autoDismiss = true)
                } finally {
                    if (!dispatched && isTemp) {
                        fileToUpload?.delete()
                    }
                }
            }
        }
    }

    // ==========================================
    // DOWNLOAD VIA TELEGRAM BOT API (HTTP DIRETO)
    // ==========================================

    private suspend fun downloadViaBotApi(remoteFileId: String, destFile: File): Boolean = withContext(Dispatchers.IO) {
        val botToken = normalizedBotToken()
        if (botToken.isBlank() || remoteFileId.isBlank()) return@withContext false

        try {
            val client = Injekt.get<NetworkHelper>().client
            val getFileUrl = "https://api.telegram.org/bot$botToken/getFile?file_id=$remoteFileId"
            val req1 = Request.Builder().url(getFileUrl).get().build()
            val bodyString = client.newCall(req1).execute().use { res1 ->
                val rawBody = res1.body.string()
                if (!res1.isSuccessful) {
                    logcat(LogPriority.WARN) { "Bot API getFile HTTP falhou (${res1.code}): ${redactSecrets(rawBody)}" }
                    return@withContext false
                }
                rawBody
            }

            val json = JSONObject(bodyString)
            if (!json.optBoolean("ok")) {
                val desc = redactSecrets(json.optString("description", ""))
                logcat(LogPriority.WARN) { "Bot API getFile retorno ok=false: $desc" }
                return@withContext false
            }

            val resultObj = json.optJSONObject("result") ?: return@withContext false
            val filePath = resultObj.optString("file_path")
            if (filePath.isBlank()) return@withContext false

            val downloadUrl = "https://api.telegram.org/file/bot$botToken/$filePath"
            val req2 = Request.Builder().url(downloadUrl).get().build()
            client.newCall(req2).execute().use { res2 ->
                if (!res2.isSuccessful) {
                    logcat(LogPriority.WARN) { "Bot API download HTTP falhou (${res2.code})" }
                    return@withContext false
                }
                res2.body.byteStream().use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            return@withContext destFile.exists() && destFile.length() > 0L
        } catch (e: Exception) {
            logcat(LogPriority.WARN) { "Excecao no download via Bot API HTTP: ${redactSecrets(e.message)}" }
            false
        }
    }

    private fun extractCoverFromChapter(chapterFile: UniFile, mangaDir: UniFile): Boolean {
        val existingCover = mangaDir.findFile("cover.jpg") ?: mangaDir.findFile("cover.png")
        if (existingCover != null && existingCover.length() > 0L) return true
        return try {
            chapterFile.openInputStream().use { inputStream ->
                ZipInputStream(inputStream).use { zipInput ->
                    var entry = zipInput.nextEntry
                    while (entry != null) {
                        val name = entry.name.lowercase()
                        if (!entry.isDirectory && !name.contains("__macosx") &&
                            (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp"))
                        ) {
                            val target = mangaDir.createFile("cover.jpg") ?: return false
                            target.openOutputStream().use { out ->
                                zipInput.copyTo(out)
                            }
                            logcat(LogPriority.INFO) { "Capa extraida com sucesso do capitulo ${chapterFile.name}" }
                            return true
                        }
                        entry = zipInput.nextEntry
                    }
                }
            }
            false
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Erro ao extrair capa do capitulo ${chapterFile.name}" }
            false
        }
    }

    // ==========================================
    // DOWNLOAD DE ARQUIVO DA TDLIB / BOT API
    // ==========================================

    suspend fun downloadFileFromTelegram(fileId: Int, remoteFileId: String = ""): String? {
        if (remoteFileId.isNotBlank()) {
            val cacheDir = File(context.cacheDir, "tg_downloads").apply { mkdirs() }
            val tempFile = File(cacheDir, "${System.currentTimeMillis()}_$fileId.tmp")
            val httpSuccess = downloadViaBotApi(remoteFileId, tempFile)
            if (httpSuccess && tempFile.exists() && tempFile.length() > 0L) {
                return tempFile.absolutePath
            } else {
                tempFile.delete()
            }
        }

        if (tdClient == null) initializeTdlib()
        val ready = withTimeoutOrNull(15000) { isAuthReady.first { it }; true } ?: false
        if (!ready || fileId == 0) return null

        val completer = CompletableDeferred<String>()
        pendingDownloads[fileId] = completer

        tdClient?.send(TdApi.DownloadFile(fileId, 32, 0, 0, false)) { res ->
            if (res is TdApi.File && res.local.isDownloadingCompleted && res.local.path.isNotBlank()) {
                val f = File(res.local.path)
                if (f.exists() && f.length() > 0L) {
                    completer.complete(res.local.path)
                }
            } else if (res is TdApi.Error) {
                logcat(LogPriority.ERROR) { "Erro DownloadFile: ${res.message}" }
                completer.completeExceptionally(Exception(res.message))
            }
        }

        return try {
            withTimeoutOrNull(180_000) { completer.await() }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Excecao aguardando download do Telegram" }
            null
        } finally {
            pendingDownloads.remove(fileId)
        }
    }

    suspend fun findMessageInChat(chatId: Long, mangaTitle: String, chapterName: String): TdApi.Message? {
        var fromMessageId = 0L
        var iterations = 0
        val cleanChapterName = chapterName.removeSuffix(".cbz").removeSuffix(".zip").trim()
        val chapterNumOnly = cleanChapterName.replace(Regex("""(?i)cap[íi]tulo\s*"""), "").trim()

        while (iterations < 50) {
            val messages = suspendCancellableCoroutine<Array<TdApi.Message>> { cont ->
                tdClient?.send(TdApi.GetChatHistory(chatId, fromMessageId, 0, 100, false)) { result ->
                    if (result is TdApi.Messages) cont.resume(result.messages)
                    else cont.resume(emptyArray())
                }
            }
            if (messages.isEmpty()) break

            val found = messages.firstOrNull { msg ->
                val content = msg.content
                if (content is TdApi.MessageDocument) {
                    val caption = content.caption.text
                    val fn = content.document.fileName ?: ""
                    val combined = "$caption $fn"
                    val matchesTitle = combined.contains(mangaTitle, ignoreCase = true)
                    val matchesCap = combined.contains(cleanChapterName, ignoreCase = true) ||
                        (chapterNumOnly.isNotBlank() && Regex("""(?i)\bcap(?:[íi]tulo)?\s*0*$chapterNumOnly\b""").containsMatchIn(combined))
                    matchesTitle && matchesCap
                } else false
            }
            if (found != null) return found
            val lastId = messages.last().id
            if (lastId == fromMessageId) break
            fromMessageId = lastId
            iterations++
        }
        return null
    }

    suspend fun downloadChapterFile(
        mangaTitle: String,
        chapter: CloudChapter,
        chatId: Long
    ): String? {
        lastDownloadError = null

        // 1. Estrategia Bot API HTTP direto (rapido, confiavel, sem dependencia de sessao MTProto)
        if (chapter.remoteFileId.isNotBlank()) {
            val cacheDir = File(context.cacheDir, "tg_downloads").apply { mkdirs() }
            val tempFile = File(cacheDir, "${System.currentTimeMillis()}_${DiskUtil.buildValidFilename(chapter.name)}.cbz")
            val httpSuccess = downloadViaBotApi(chapter.remoteFileId, tempFile)
            if (httpSuccess && tempFile.exists() && tempFile.length() > 0L) {
                logcat(LogPriority.INFO) { "Capitulo baixado via Bot API HTTP: ${tempFile.length()} bytes" }
                return tempFile.absolutePath
            } else {
                tempFile.delete()
            }
        }

        // 2. Estrategia TDLib (MTProto) para arquivos grandes (> 20MB) ou fallback
        if (tdClient == null) initializeTdlib()
        val ready = withTimeoutOrNull(15000) {
            isAuthReady.first { it }
            true
        } ?: false

        if (!ready) {
            lastDownloadError = "Conexão com Telegram não autenticada."
            logcat(LogPriority.ERROR) { "TDLib nao autenticado para download" }
            return null
        }

        var message: TdApi.Message? = null

        val targetMessageId = if (chapter.messageId in 1..1048575L) {
            chapter.messageId shl 20
        } else {
            chapter.messageId
        }

        // Tenta obter mensagem via GetMessages (busca ativa do servidor do Telegram)
        if (targetMessageId != 0L && chatId != 0L) {
            message = suspendCancellableCoroutine { cont ->
                tdClient?.send(TdApi.GetMessages(chatId, longArrayOf(targetMessageId))) { res ->
                    if (res is TdApi.Messages && res.messages.isNotEmpty() && res.messages[0] != null) {
                        cont.resume(res.messages[0])
                    } else {
                        tdClient?.send(TdApi.GetMessage(chatId, targetMessageId)) { res2 ->
                            if (res2 is TdApi.Message) cont.resume(res2)
                            else cont.resume(null)
                        }
                    }
                }
            }
        }

        // Se falhar ou messageId for 0, varre o historico recente do canal
        if (message == null && chatId != 0L) {
            message = findMessageInChat(chatId, mangaTitle, chapter.name)
            if (message != null) {
                val foundDoc = (message.content as? TdApi.MessageDocument)?.document?.document
                val foundFileId = foundDoc?.id ?: 0
                val foundRemoteId = foundDoc?.remote?.id ?: ""
                CoroutineScope(Dispatchers.IO).launch {
                    val current = getCloudIndex().toMutableList()
                    val m = current.find { it.title.equals(mangaTitle, ignoreCase = true) }
                    val ch = m?.chapters?.find { it.name.equals(chapter.name, ignoreCase = true) }
                    if (ch != null) {
                        val updated = ch.copy(
                            messageId = message.id,
                            fileId = if (foundFileId != 0) foundFileId else ch.fileId,
                            remoteFileId = if (foundRemoteId.isNotBlank()) foundRemoteId else ch.remoteFileId
                        )
                        val idx = m.chapters.indexOf(ch)
                        m.chapters[idx] = updated
                        saveCloudIndex(current)
                    }
                }
            }
        }

        var fileToDownload: TdApi.File? = null
        val doc = (message?.content as? TdApi.MessageDocument)?.document
        if (doc != null) {
            fileToDownload = doc.document
        } else if (chapter.remoteFileId.isNotBlank()) {
            fileToDownload = suspendCancellableCoroutine { cont ->
                tdClient?.send(TdApi.GetRemoteFile(chapter.remoteFileId, TdApi.FileTypeDocument())) { res ->
                    if (res is TdApi.File) cont.resume(res)
                    else cont.resume(null)
                }
            }
        }

        if (fileToDownload == null) {
            lastDownloadError = "Capítulo não encontrado no canal (removido da lista)."
            logcat(LogPriority.WARN) { "Capítulo $mangaTitle - ${chapter.name} não encontrado no canal. Removendo do índice." }
            removeChapterFromIndex(mangaTitle, chapter.name)
            return null
        }

        // Se ja baixado e valido no cache local do TDLib
        if (fileToDownload.local.isDownloadingCompleted && fileToDownload.local.path.isNotBlank()) {
            val f = File(fileToDownload.local.path)
            if (f.exists() && f.length() > 0L) {
                return fileToDownload.local.path
            }
        }

        // Solicita download com prioridade maxima na sessao atual
        val completer = CompletableDeferred<String>()
        pendingDownloads[fileToDownload.id] = completer

        tdClient?.send(TdApi.DownloadFile(fileToDownload.id, 32, 0, 0, false)) { res ->
            if (res is TdApi.File && res.local.isDownloadingCompleted && res.local.path.isNotBlank()) {
                val f = File(res.local.path)
                if (f.exists() && f.length() > 0L) {
                    completer.complete(res.local.path)
                }
            } else if (res is TdApi.Error) {
                logcat(LogPriority.ERROR) { "Erro DownloadFile: ${res.message}" }
                completer.completeExceptionally(Exception(res.message))
            }
        }

        return try {
            withTimeoutOrNull(180_000) { completer.await() }
        } catch (e: Exception) {
            lastDownloadError = "Timeout ou erro no download: ${e.message}"
            logcat(LogPriority.ERROR, e) { "Excecao aguardando download do Telegram" }
            null
        } finally {
            pendingDownloads.remove(fileToDownload.id)
        }
    }

    // ==========================================
    // METADADOS E CAPA DA FONTE LOCAL
    // ==========================================

    private fun String.escapeXml(): String {
        return this.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private suspend fun setupLocalSourceMetadata(
        mangaDir: UniFile,
        mangaTitle: String,
        cloudManga: CloudManga
    ) {
        try {
            val libraryManga = try {
                Injekt.get<GetLibraryManga>().await()
                    .find { it.manga.title.equals(mangaTitle, ignoreCase = true) }?.manga
            } catch (e: Throwable) {
                null
            }

            // 1. Salvar Capa (cover.jpg)
            val coverFile = mangaDir.findFile("cover.jpg") ?: mangaDir.findFile("cover.png")
            if (coverFile == null || coverFile.length() == 0L) {
                var coverSaved = false

                // Tenta extrair a capa de qualquer capitulo ja presente na pasta
                val existingChapterFile = mangaDir.listFiles()?.firstOrNull { file ->
                    val fn = file.name ?: ""
                    file.isFile && (fn.endsWith(".cbz", ignoreCase = true) || fn.endsWith(".zip", ignoreCase = true))
                }
                if (existingChapterFile != null) {
                    coverSaved = extractCoverFromChapter(existingChapterFile, mangaDir)
                }

                if (!coverSaved) {
                    val coverCache = Injekt.get<CoverCache>()
                    val thumbUrl = libraryManga?.thumbnailUrl ?: cloudManga.coverUrl
                    if (!thumbUrl.isNullOrBlank()) {
                        val cached = coverCache.getCoverFile(thumbUrl)
                        if (cached != null && cached.exists() && cached.length() > 0L) {
                            coverFile?.delete()
                            val target = mangaDir.createFile("cover.jpg")
                            if (target != null) {
                                cached.inputStream().use { inp ->
                                    target.openOutputStream().use { out -> inp.copyTo(out) }
                                }
                                coverSaved = true
                            }
                        }
                    }
                }

                if (!coverSaved) {
                    val chatId = preferences.chatId.get().toLongOrNull() ?: 0L
                    val firstChap = cloudManga.chapters.firstOrNull()
                    if (chatId != 0L && firstChap != null && firstChap.messageId != 0L) {
                        val msg = suspendCancellableCoroutine<TdApi.Message?> { cont ->
                            tdClient?.send(TdApi.GetMessages(chatId, longArrayOf(firstChap.messageId))) { res ->
                                if (res is TdApi.Messages && res.messages.isNotEmpty() && res.messages[0] != null) {
                                    cont.resume(res.messages[0])
                                } else {
                                    tdClient?.send(TdApi.GetMessage(chatId, firstChap.messageId)) { res2 ->
                                        if (res2 is TdApi.Message) cont.resume(res2)
                                        else cont.resume(null)
                                    }
                                }
                            }
                        }
                        val thumb = (msg?.content as? TdApi.MessageDocument)?.document?.thumbnail?.file
                        if (thumb != null) {
                            val thumbPath = if (thumb.local.isDownloadingCompleted && thumb.local.path.isNotBlank()) {
                                thumb.local.path
                            } else {
                                val c = CompletableDeferred<String>()
                                pendingDownloads[thumb.id] = c
                                tdClient?.send(TdApi.DownloadFile(thumb.id, 32, 0, 0, false)) { res ->
                                    if (res is TdApi.File && res.local.isDownloadingCompleted && res.local.path.isNotBlank()) {
                                        c.complete(res.local.path)
                                    }
                                }
                                val p = withTimeoutOrNull(10000) { c.await() }
                                pendingDownloads.remove(thumb.id)
                                p
                            }
                            if (thumbPath != null) {
                                val tf = File(thumbPath)
                                if (tf.exists() && tf.length() > 0L) {
                                    val target = mangaDir.createFile("cover.jpg")
                                    if (target != null) {
                                        tf.inputStream().use { inp ->
                                            target.openOutputStream().use { out -> inp.copyTo(out) }
                                        }
                                        coverSaved = true
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. Salvar Metadados (ComicInfo.xml)
            val comicInfoFile = mangaDir.findFile("ComicInfo.xml")
            val shouldWriteXml = if (comicInfoFile == null || comicInfoFile.length() == 0L) {
                true
            } else {
                val existingText = try {
                    comicInfoFile.openInputStream().bufferedReader().use { it.readText() }
                } catch (e: Exception) {
                    ""
                }
                !existingText.contains("<Series>") ||
                    (existingText.contains("<Summary></Summary>") && cloudManga.description.isNotBlank()) ||
                    (!existingText.contains("<Summary>") && cloudManga.description.isNotBlank())
            }

            if (shouldWriteXml) {
                val title = libraryManga?.title ?: cloudManga.title
                val desc = libraryManga?.description ?: cloudManga.description
                val author = libraryManga?.author ?: ""
                val artist = libraryManga?.artist ?: ""
                val genre = libraryManga?.genre?.joinToString(", ") ?: ""

                val xmlContent = buildString {
                    appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
                    appendLine("<ComicInfo>")
                    appendLine("    <Series>${title.escapeXml()}</Series>")
                    appendLine("    <Title>${title.escapeXml()}</Title>")
                    if (desc.isNotBlank()) appendLine("    <Summary>${desc.escapeXml()}</Summary>")
                    if (author.isNotBlank()) appendLine("    <Writer>${author.escapeXml()}</Writer>")
                    if (artist.isNotBlank()) appendLine("    <Penciller>${artist.escapeXml()}</Penciller>")
                    if (genre.isNotBlank()) appendLine("    <Genre>${genre.escapeXml()}</Genre>")
                    appendLine("</ComicInfo>")
                }

                comicInfoFile?.delete()
                val targetXml = mangaDir.createFile("ComicInfo.xml")
                targetXml?.openOutputStream()?.use { it.write(xmlContent.toByteArray()) }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Erro ao configurar metadados da Fonte Local" }
        }
    }

    // ==========================================
    // PUXAR PARA A FONTE LOCAL (POR OBRA)
    // ==========================================

    suspend fun downloadSingleChapterToLocalSource(mangaTitle: String, chapter: CloudChapter): Boolean = withContext(Dispatchers.IO) {
        val storageManager = Injekt.get<StorageManager>()
        val localSourceDir = storageManager.getLocalSourceDirectory() ?: return@withContext false

        val sanitizedTitle = DiskUtil.buildValidFilename(mangaTitle)
        val mangaDir = localSourceDir.findFile(sanitizedTitle) ?: localSourceDir.createDirectory(sanitizedTitle) ?: return@withContext false

        val chatIdString = preferences.chatId.get()
        val targetChatId = chatIdString.toLongOrNull() ?: 0L

        val cloudManga = getCloudIndex().find { it.title.equals(mangaTitle, ignoreCase = true) }
            ?: CloudManga(title = mangaTitle)
        setupLocalSourceMetadata(mangaDir, mangaTitle, cloudManga)

        val cleanName = chapter.name.removeSuffix(".cbz").removeSuffix(".zip")
        val chapterFilename = DiskUtil.buildValidFilename(cleanName) + ".cbz"

        val downloadedPath = downloadChapterFile(mangaTitle, chapter, targetChatId)
        if (downloadedPath.isNullOrBlank()) return@withContext false

        val sourceFile = File(downloadedPath)
        if (sourceFile.exists() && sourceFile.length() > 0L) {
            val existing = mangaDir.findFile(chapterFilename)
            existing?.delete()
            val targetFile = mangaDir.createFile(chapterFilename) ?: return@withContext false
            sourceFile.inputStream().use { input ->
                targetFile.openOutputStream().use { output ->
                    input.copyTo(output)
                }
            }
            releaseDownloadedFile(downloadedPath, chapter.fileId)
            // Extrai capa do capitulo baixado para que cover.jpg exista imediatamente
            extractCoverFromChapter(targetFile, mangaDir)
            DiskUtil.createNoMediaFile(mangaDir, context)
            return@withContext true
        }
        false
    }

    suspend fun downloadMangaToLocalSource(
        mangaTitle: String,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): Boolean = withContext(Dispatchers.IO) {
        val storageManager = Injekt.get<StorageManager>()
        val localSourceDir = storageManager.getLocalSourceDirectory() ?: return@withContext false

        val sanitizedTitle = DiskUtil.buildValidFilename(mangaTitle)
        val mangaDir = localSourceDir.findFile(sanitizedTitle) ?: localSourceDir.createDirectory(sanitizedTitle) ?: return@withContext false

        val chatIdString = preferences.chatId.get()
        val targetChatId = chatIdString.toLongOrNull() ?: 0L

        var cloudManga = getCloudIndex().find { it.title.equals(mangaTitle, ignoreCase = true) }
        if (cloudManga == null || cloudManga.chapters.isEmpty()) {
            val synced = syncFromTelegram()
            cloudManga = synced.find { it.title.equals(mangaTitle, ignoreCase = true) }
        }

        val chapters = cloudManga?.chapters ?: return@withContext false
        if (chapters.isEmpty()) return@withContext false

        showNotification("Nuvem Telegram", "Baixando $mangaTitle para Fonte Local...", ongoing = true)

        var downloadedCount = 0
        try {
            setupLocalSourceMetadata(mangaDir, mangaTitle, cloudManga)

            for ((index, chapter) in chapters.withIndex()) {
                onProgress(index + 1, chapters.size)
                val cleanName = chapter.name.removeSuffix(".cbz").removeSuffix(".zip")
                val chapterFilename = DiskUtil.buildValidFilename(cleanName) + ".cbz"
                val existingFile = mangaDir.findFile(chapterFilename)
                if (existingFile != null && existingFile.length() > 0L) {
                    downloadedCount++
                    continue
                }

                val downloadedPath = downloadChapterFile(mangaTitle, chapter, targetChatId) ?: continue
                val sourceFile = File(downloadedPath)
                if (sourceFile.exists() && sourceFile.length() > 0L) {
                    existingFile?.delete()
                    val targetFile = mangaDir.createFile(chapterFilename)
                    if (targetFile != null) {
                        sourceFile.inputStream().use { input ->
                            targetFile.openOutputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        releaseDownloadedFile(downloadedPath, chapter.fileId)
                        downloadedCount++
                        // Extrai capa do capitulo baixado se ainda nao tiver
                        extractCoverFromChapter(targetFile, mangaDir)
                    }
                }
            }

            // Garante que a capa existe
            val coverFile = mangaDir.findFile("cover.jpg") ?: mangaDir.findFile("cover.png")
            if (coverFile == null || coverFile.length() == 0L) {
                val firstChapterFile = mangaDir.listFiles()?.firstOrNull { file ->
                    val fn = file.name ?: ""
                    file.isFile && (fn.endsWith(".cbz", ignoreCase = true) || fn.endsWith(".zip", ignoreCase = true))
                }
                if (firstChapterFile != null) {
                    extractCoverFromChapter(firstChapterFile, mangaDir)
                }
            }

            DiskUtil.createNoMediaFile(mangaDir, context)

            showNotification("Nuvem Telegram", "$mangaTitle salvo na Fonte Local! ($downloadedCount/${chapters.size})", autoDismiss = true)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Erro ao baixar manga $mangaTitle" }
            showNotification("Nuvem Telegram", "Erro ao baixar: $mangaTitle", autoDismiss = true)
        } finally {
            val notificationManager = androidx.core.app.NotificationManagerCompat.from(context)
            notificationManager.cancel(889911)
        }
        
        downloadedCount > 0
    }

    // ==========================================
    // RESTORE DE CAPITULO PARA DOWNLOADER
    // ==========================================

    suspend fun restoreChapterFromTelegram(
        mangaTitle: String,
        chapterName: String,
        chapterDirname: String = "",
        localSourceMangaDir: UniFile
    ): Boolean {
        if (!preferences.enableTelegramCloud.get()) return false

        return withContext(Dispatchers.IO) {
            val chatIdString = preferences.chatId.get()
            val targetChatId = chatIdString.toLongOrNull() ?: return@withContext false

            val cloudManga = getCloudIndex().find { it.title.equals(mangaTitle, ignoreCase = true) }
            val cloudChapter = cloudManga?.chapters?.find {
                it.name.equals(chapterName, ignoreCase = true) ||
                it.name.equals(chapterDirname, ignoreCase = true)
            } ?: CloudChapter(name = chapterName, messageId = 0L, fileId = 0, remoteFileId = "")

            showNotification("Nuvem Telegram", "Restaurando da Nuvem: $mangaTitle - $chapterName", ongoing = true)
            var success = false
            try {
                val downloadedPath = downloadChapterFile(mangaTitle, cloudChapter, targetChatId)
                if (downloadedPath.isNullOrBlank()) {
                    showNotification("Nuvem Telegram", "Falha ao restaurar: $mangaTitle - $chapterName", autoDismiss = true)
                    return@withContext false
                }

                val sourceFile = File(downloadedPath)
                if (!sourceFile.exists() || sourceFile.length() == 0L) {
                    showNotification("Nuvem Telegram", "Arquivo inválido ou vazio: $chapterName", autoDismiss = true)
                    return@withContext false
                }

                val cleanName = (if (chapterDirname.isNotBlank()) chapterDirname else chapterName)
                    .removeSuffix(".cbz").removeSuffix(".zip")
                val targetFileName = DiskUtil.buildValidFilename(cleanName) + ".cbz"

                val existing = localSourceMangaDir.findFile(targetFileName)
                existing?.delete()

                val targetFile = localSourceMangaDir.createFile(targetFileName)
                if (targetFile == null) {
                    showNotification("Nuvem Telegram", "Erro ao criar arquivo: $chapterName", autoDismiss = true)
                    return@withContext false
                }

                sourceFile.inputStream().use { input ->
                    targetFile.openOutputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                releaseDownloadedFile(downloadedPath, cloudChapter.fileId)

                extractCoverFromChapter(targetFile, localSourceMangaDir)
                DiskUtil.createNoMediaFile(localSourceMangaDir, context)
                showNotification("Nuvem Telegram", "Capitulo $chapterName restaurado!", autoDismiss = true)
                success = true
                true
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Erro ao restaurar capitulo $chapterName" }
                showNotification("Nuvem Telegram", "Erro fatal ao restaurar: $chapterName", autoDismiss = true)
                false
            } finally {
                if (!success) {
                    val notificationManager = androidx.core.app.NotificationManagerCompat.from(context)
                    notificationManager.cancel(889911)
                }
            }
        }
    }

    // ==========================================
    // SINCRONIZAR TUDO DO CANAL TELEGRAM PRO INDICE
    // ==========================================

    suspend fun syncFromTelegram(): List<CloudManga> = withContext(Dispatchers.IO) {
        val chatIdString = preferences.chatId.get()
        val targetChatId = chatIdString.toLongOrNull() ?: return@withContext getCloudIndex()

        val currentIndex = getCloudIndex().map { it.copy(chapters = it.chapters.toMutableList()) }.toMutableList()

        // 0. RECUPERAR SNAPSHOT REMOTO DO ÍNDICE (essencial em instalações novas, pois Bots não recebem histórico antigo via GetChatHistory)
        fetchRemoteIndexSnapshot(targetChatId)?.forEach { remoteManga ->
            var localManga = currentIndex.find { it.title.equals(remoteManga.title, ignoreCase = true) }
            if (localManga == null) {
                localManga = remoteManga.copy(chapters = remoteManga.chapters.toMutableList())
                currentIndex.add(localManga)
            } else {
                val mIdx = currentIndex.indexOf(localManga)
                val mergedManga = localManga.copy(
                    description = localManga.description.ifBlank { remoteManga.description },
                    coverUrl = localManga.coverUrl.ifBlank { remoteManga.coverUrl },
                    isNovel = localManga.isNovel || remoteManga.isNovel,
                )
                currentIndex[mIdx] = mergedManga
                localManga = mergedManga
                for (remoteChap in remoteManga.chapters) {
                    val existingIdx = localManga.chapters.indexOfFirst { it.name.equals(remoteChap.name, ignoreCase = true) }
                    if (existingIdx < 0) {
                        localManga.chapters.add(remoteChap)
                    } else {
                        val existing = localManga.chapters[existingIdx]
                        if (existing.remoteFileId.isBlank() && remoteChap.remoteFileId.isNotBlank()) {
                            localManga.chapters[existingIdx] = existing.copy(
                                messageId = if (existing.messageId != 0L) existing.messageId else remoteChap.messageId,
                                remoteFileId = remoteChap.remoteFileId,
                            )
                        }
                    }
                }
            }
        }

        if (tdClient == null) initializeTdlib()
        val ready = withTimeoutOrNull(15000) {
            isAuthReady.first { it }
            true
        } ?: false

        if (!ready) {
            logcat(LogPriority.ERROR) { "TDLib nao autenticado para syncFromTelegram; salvando índice recuperado via Bot API" }
            if (currentIndex.isNotEmpty()) {
                saveCloudIndex(currentIndex, syncRemote = false)
            }
            return@withContext currentIndex
        }

        // Garante que o chat está carregado e aberto no TDLib
        suspendCancellableCoroutine<Unit> { cont ->
            tdClient?.send(TdApi.GetChat(targetChatId)) { _ ->
                tdClient?.send(TdApi.OpenChat(targetChatId)) { _ ->
                    cont.resume(Unit)
                }
            }
        }

        // 1. VERIFICAR CAPÍTULOS EXISTENTES NO ÍNDICE CONTRA O TELEGRAM
        // Se a mensagem foi confirmada como apagada no Telegram, removemos do índice ("sumir só o que não estiver mais lá")
        val chaptersToCheck = mutableListOf<Pair<CloudManga, CloudChapter>>()
        for (manga in currentIndex) {
            for (chapter in manga.chapters) {
                if (chapter.messageId != 0L) {
                    chaptersToCheck.add(manga to chapter)
                }
            }
        }

        // Checa em lotes de até 100 mensagens
        val deadChapters = mutableSetOf<Pair<String, String>>()
        for (chunk in chaptersToCheck.chunked(100)) {
            val ids = chunk.map { (_, chap) ->
                if (chap.messageId in 1..1048575L) chap.messageId shl 20 else chap.messageId
            }.toLongArray()

            val res = suspendCancellableCoroutine<TdApi.Messages?> { cont ->
                tdClient?.send(TdApi.GetMessages(targetChatId, ids)) { result ->
                    if (result is TdApi.Messages) cont.resume(result)
                    else cont.resume(null)
                }
            }

            if (res != null) {
                for ((index, msg) in res.messages.withIndex()) {
                    if (index < chunk.size) {
                        val (manga, chap) = chunk[index]
                        if (msg == null || msg.id == 0L || msg.content !is TdApi.MessageDocument) {
                            deadChapters.add(manga.title to chap.name)
                            logcat(LogPriority.INFO) { "Capítulo confirmado como apagado no Telegram: ${manga.title} - ${chap.name}" }
                        } else {
                            val doc = (msg.content as TdApi.MessageDocument).document.document
                            val updatedChap = chap.copy(
                                messageId = msg.id,
                                fileId = if (doc.id != 0) doc.id else chap.fileId,
                                remoteFileId = if (doc.remote.id.isNotBlank()) doc.remote.id else chap.remoteFileId
                            )
                            val cIdx = manga.chapters.indexOf(chap)
                            if (cIdx >= 0) {
                                manga.chapters[cIdx] = updatedChap
                            }
                        }
                    }
                }
            }
        }

        // Remove os capítulos mortos
        for ((mTitle, cName) in deadChapters) {
            val m = currentIndex.find { it.title.equals(mTitle, ignoreCase = true) }
            m?.chapters?.removeAll { it.name.equals(cName, ignoreCase = true) }
        }
        // Remove obras que ficaram sem capítulos
        currentIndex.removeAll { it.chapters.isEmpty() }

        // 2. ESCANEAR HISTÓRICO DO TELEGRAM PARA DESCOBRIR NOVOS CAPÍTULOS
        val existingCovers = currentIndex.associate { it.title.lowercase() to it.coverUrl }
        val existingDescs = currentIndex.associate { it.title.lowercase() to it.description }
        val libraryMangas = try {
            Injekt.get<GetLibraryManga>().await().associate { it.manga.title.lowercase() to (it.manga.thumbnailUrl ?: "") }
        } catch (e: Exception) {
            emptyMap()
        }

        val obraRegex = """(?i)(?:📖\s*)?(?:Obra|Título|Manga|Manhwa|Novel)\s*:\s*(.+)""".toRegex()
        val capRegex = """(?i)(?:📄\s*)?(?:Cap[íi]tulo|Cap|Ep|Episode)\s*:\s*(.+)""".toRegex()
        val sinopseRegex = """(?i)(?:📝\s*)?(?:Sinopse|Description|Desc)\s*:\s*(.+)""".toRegex()

        var fromMessageId = 0L
        var iterations = 0

        while (iterations < 100) {
            val messages = suspendCancellableCoroutine<Array<TdApi.Message>> { cont ->
                tdClient?.send(TdApi.GetChatHistory(targetChatId, fromMessageId, 0, 100, false)) { result ->
                    if (result is TdApi.Messages) cont.resume(result.messages)
                    else {
                        logcat(LogPriority.WARN) { "GetChatHistory retornou: $result" }
                        cont.resume(emptyArray())
                    }
                }
            }
            if (messages.isEmpty()) break

            for (msg in messages) {
                if (msg.id == fromMessageId && iterations > 0) continue
                val content = msg.content
                if (content is TdApi.MessageDocument) {
                    val caption = content.caption.text
                    val docName = content.document.fileName ?: ""
                    if (caption.contains(INDEX_TAG, ignoreCase = true) || docName.equals(INDEX_FILENAME, ignoreCase = true)) {
                        continue
                    }
                    val isNovelMsg = caption.contains("#YomotsuNovel", ignoreCase = true) || docName.endsWith(".txt", ignoreCase = true)

                    var titleMatch: String? = null
                    var capMatch: String? = null
                    var sinopseMatch: String? = null

                    val oMatch = obraRegex.find(caption)?.groupValues?.get(1)?.trim()
                    val cMatch = capRegex.find(caption)?.groupValues?.get(1)?.trim()
                    val sMatch = sinopseRegex.find(caption)?.groupValues?.get(1)?.trim()

                    if (!oMatch.isNullOrBlank()) titleMatch = oMatch
                    if (!cMatch.isNullOrBlank()) capMatch = cMatch
                    if (!sMatch.isNullOrBlank()) sinopseMatch = sMatch

                    if (titleMatch.isNullOrBlank() || capMatch.isNullOrBlank()) {
                        val lines = caption.lines().map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("#") }
                        if (lines.size >= 2) {
                            if (titleMatch.isNullOrBlank()) titleMatch = lines[0].removePrefix("📖").trim()
                            if (capMatch.isNullOrBlank()) capMatch = lines[1].removePrefix("📄").trim()
                        }
                    }

                    if (titleMatch.isNullOrBlank() || capMatch.isNullOrBlank()) {
                        if (docName.endsWith(".cbz", ignoreCase = true) || docName.endsWith(".zip", ignoreCase = true)) {
                            val cleanDocName = docName.removeSuffix(".cbz").removeSuffix(".zip")
                            if (cleanDocName.contains(" - ")) {
                                val parts = cleanDocName.split(" - ", limit = 2)
                                if (titleMatch.isNullOrBlank()) titleMatch = parts[0].trim()
                                if (capMatch.isNullOrBlank()) capMatch = parts[1].trim()
                            }
                        }
                    }

                    if (!titleMatch.isNullOrBlank() && !capMatch.isNullOrBlank()) {
                        var manga = currentIndex.find { it.title.equals(titleMatch, ignoreCase = true) }
                        if (manga == null) {
                            val fallbackCover = existingCovers[titleMatch.lowercase()] ?: libraryMangas[titleMatch.lowercase()] ?: ""
                            val fallbackDesc = sinopseMatch ?: existingDescs[titleMatch.lowercase()] ?: ""
                            manga = CloudManga(
                                title = titleMatch,
                                description = fallbackDesc,
                                coverUrl = fallbackCover,
                                isNovel = isNovelMsg,
                            )
                            currentIndex.add(manga)
                        } else if (manga.description.isBlank() && !sinopseMatch.isNullOrBlank()) {
                            val updatedManga = manga.copy(
                                description = sinopseMatch,
                                isNovel = manga.isNovel || isNovelMsg,
                            )
                            val mIdx = currentIndex.indexOf(manga)
                            currentIndex[mIdx] = updatedManga
                            manga = updatedManga
                        }

                        val existingCapIndex = manga.chapters.indexOfFirst { it.name.equals(capMatch, ignoreCase = true) }
                        val cloudCap = CloudChapter(
                            name = capMatch,
                            messageId = msg.id,
                            fileId = content.document.document.id,
                            remoteFileId = content.document.document.remote.id
                        )
                        if (existingCapIndex >= 0) {
                            manga.chapters[existingCapIndex] = cloudCap
                        } else {
                            manga.chapters.add(cloudCap)
                        }
                    }
                }
            }
            val lastId = messages.last().id
            if (lastId == fromMessageId) break
            fromMessageId = lastId
            iterations++
        }

        // Ordena capítulos numericamente
        currentIndex.forEach { manga ->
            manga.chapters.sortBy { chap ->
                val numStr = chap.name.replace(Regex("""[^0-9.]"""), "")
                numStr.toFloatOrNull() ?: 999999f
            }
        }

        saveCloudIndex(currentIndex)
        currentIndex
    }

    // ==========================================
    // NOVELS
    // ==========================================

    suspend fun uploadNovelChapter(manga: Manga, chapter: Chapter, txtFile: File) {
        withContext(Dispatchers.IO) {
            if (!preferences.enableTelegramCloud.get() || txtFile.length() == 0L) return@withContext
            if (tdClient == null) initializeTdlib()

            val ready = withTimeoutOrNull(10000) {
                isAuthReady.first { it }
                true
            } ?: false

            if (!ready) return@withContext

            val chatIdString = preferences.chatId.get()
            val targetChatId = chatIdString.toLongOrNull() ?: return@withContext

            uploadMutex.withLock {
                try {
                    val description = manga.description?.let {
                        if (it.length > 500) it.take(497) + "..." else it
                    } ?: "Sem sinopse disponivel."

                    val textCaption = "#YomotsuNovel\n\n📖 Obra: ${manga.title}\n📄 Capítulo: ${chapter.name}\n\n📝 Sinopse: $description"
                    val caption = TdApi.FormattedText(textCaption, emptyArray())

                    val coverCache = Injekt.get<eu.kanade.tachiyomi.data.cache.CoverCache>()
                    val coverFile = coverCache.getCoverFile(manga.thumbnailUrl)
                    val thumbnail = if (coverFile != null && coverFile.exists()) {
                        TdApi.InputThumbnail(TdApi.InputFileLocal(coverFile.absolutePath), 0, 0)
                    } else null

                    val inputFile = TdApi.InputFileLocal(txtFile.absolutePath)
                    val document = TdApi.InputMessageDocument(inputFile, thumbnail, false, caption)
                    val sendMessageRequest = TdApi.SendMessage(targetChatId, null, null, null, null, document)

                    showNotification("Nuvem Telegram", "Enviando Novel: ${manga.title}...", progress = 0, max = 100, ongoing = true)

                    tdClient?.send(sendMessageRequest) { result ->
                        if (result is TdApi.Message) {
                            val uFile = UniFile.fromFile(txtFile)
                            if (uFile != null) {
                                pendingUploads[result.id] = PendingUpload(uFile, manga, chapter, isNovel = true)
                            }
                            val doc = (result.content as? TdApi.MessageDocument)?.document?.document
                            val fileId = doc?.id ?: 0
                            val remoteId = doc?.remote?.id ?: ""
                            managerScope.launch {
                                registerInIndex(
                                    mangaTitle = manga.title,
                                    description = manga.description ?: "",
                                    coverUrl = manga.thumbnailUrl ?: "",
                                    chapterName = chapter.name,
                                    messageId = result.id,
                                    fileId = fileId,
                                    remoteFileId = remoteId,
                                    isNovel = true,
                                )
                            }
                        } else if (result is TdApi.Error) {
                            val safeMsg = redactSecrets(result.message)
                            logcat(LogPriority.ERROR) { "Erro ao empurrar novel pra TDLib: $safeMsg" }
                            showNotification("Nuvem Telegram", "Erro: $safeMsg", autoDismiss = true)
                        }
                    }
                    delay(3000)
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR) { "Erro ao enviar novel: ${redactSecrets(e.message)}" }
                    showNotification("Nuvem Telegram", "Erro fatal ao enviar novel", autoDismiss = true)
                }
            }
        }
    }

    suspend fun restoreNovelChapter(mangaTitle: String, chapterName: String, localTargetFile: File): Boolean {
        if (!preferences.enableTelegramCloud.get()) return false
        return withContext(Dispatchers.IO) {
            if (tdClient == null) initializeTdlib()
            val ready = withTimeoutOrNull(10000) { isAuthReady.first { it }; true } ?: false
            if (!ready) return@withContext false

            val chatIdString = preferences.chatId.get()
            val targetChatId = chatIdString.toLongOrNull() ?: return@withContext false

            // 1. Tenta localizar instantaneamente via índice local em cache
            val indexedChapter = getCloudIndex()
                .firstOrNull { it.title.equals(mangaTitle, ignoreCase = true) }
                ?.chapters
                ?.firstOrNull { it.name.equals(chapterName, ignoreCase = true) }

            if (indexedChapter != null && (indexedChapter.fileId != 0 || indexedChapter.remoteFileId.isNotBlank())) {
                val indexedPath = downloadFileFromTelegram(indexedChapter.fileId, indexedChapter.remoteFileId)
                if (indexedPath != null) {
                    val sFile = File(indexedPath)
                    if (sFile.exists()) {
                        sFile.copyTo(localTargetFile, overwrite = true)
                        releaseDownloadedFile(indexedPath, indexedChapter.fileId)
                        return@withContext true
                    }
                }
            }

            // 2. Fallback: escaneia histórico recente do canal se não estiver no índice
            var fromMessageId = 0L
            var iterations = 0
            var foundMsg: TdApi.Message? = null

            while (iterations < 30) {
                val messages = kotlin.coroutines.suspendCoroutine<Array<TdApi.Message>> { cont ->
                    tdClient?.send(TdApi.GetChatHistory(targetChatId, fromMessageId, 0, 100, false)) { result ->
                        if (result is TdApi.Messages) cont.resumeWith(Result.success(result.messages))
                        else cont.resumeWith(Result.success(emptyArray()))
                    }
                }
                if (messages.isEmpty()) break
                foundMsg = messages.firstOrNull { msg ->
                    val c = msg.content
                    if (c is TdApi.MessageDocument) {
                        val cap = c.caption.text
                        cap.contains(mangaTitle, ignoreCase = true) && cap.contains(chapterName, ignoreCase = true)
                    } else false
                }
                if (foundMsg != null) break
                fromMessageId = messages.last().id
                iterations++
            }

            if (foundMsg == null) return@withContext false
            val c = foundMsg.content as? TdApi.MessageDocument ?: return@withContext false
            val fileId = c.document.document.id
            val downloadedPath = downloadFileFromTelegram(fileId, c.document.document.remote.id) ?: return@withContext false
            val sFile = File(downloadedPath)
            if (sFile.exists()) {
                sFile.copyTo(localTargetFile, overwrite = true)
                releaseDownloadedFile(downloadedPath, fileId)
                return@withContext true
            }
            false
        }
    }
}
