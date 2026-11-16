package eu.kanade.translation.detection

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import android.os.Process
import eu.kanade.translation.model.PageTranslation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FilterInputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.coroutineContext

internal sealed interface AotInpaintResult {
    data class Success(val inpaintedBlocks: Int) : AotInpaintResult
    data class Failure(val reason: String) : AotInpaintResult
}

internal class AotInpaintClient(
    context: Context,
    private val serviceIntent: Intent = Intent(context, AotInpaintService::class.java),
) : AutoCloseable {
    private val context = context.applicationContext
    private val requests = Mutex()
    private val stateLock = Any()
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var modelsReady = false

    private var connection: ServiceConnection? = null
    private var connected: CompletableDeferred<Messenger>? = null
    private var remote: Messenger? = null
    private var pending: CompletableDeferred<AotInpaintResult>? = null
    private var requestId = 0

    private val receiver = Messenger(
        Handler(Looper.getMainLooper()) { message ->
            if (message.what == AotInpaintService.MSG_RESULT) {
                synchronized(stateLock) {
                    if (message.arg1 == requestId) {
                        val bundle = message.data
                        val error = bundle.getString(AotInpaintService.KEY_ERROR)
                        val result = if (error != null) {
                            AotInpaintResult.Failure(error.take(200))
                        } else {
                            val count = bundle.getInt(AotInpaintService.KEY_INPAINTED_COUNT, 0)
                            AotInpaintResult.Success(count)
                        }
                        pending?.complete(result)
                    }
                }
            }
            true
        },
    )

    suspend fun ensureModelsAvailable(onDownloadRequired: () -> Unit = {}) = withContext(Dispatchers.IO) {
        if (modelsReady) return@withContext
        val currentContext = coroutineContext
        DbnetModelStore(
            directory = File(context.filesDir, AotInpaintService.MODEL_RELATIVE_DIR),
            assets = DbnetModelStore.INPAINTER_ASSETS,
        ) { url ->
            val conn = URL(url).openConnection() as HttpsURLConnection
            conn.connectTimeout = 30_000
            conn.readTimeout = 60_000
            conn.instanceFollowRedirects = true
            try {
                object : FilterInputStream(conn.inputStream) {
                    override fun close() {
                        try {
                            super.close()
                        } finally {
                            conn.disconnect()
                        }
                    }
                }
            } catch (error: Throwable) {
                conn.disconnect()
                throw error
            }
        }.ensureAvailable(
            checkCancelled = { currentContext.ensureActive() },
            onDownloadRequired = onDownloadRequired,
        )
        modelsReady = true
    }

    suspend fun inpaintPage(
        imageFile: File,
        page: PageTranslation,
    ): AotInpaintResult = requests.withLock {
        if (page.blocks.none(AotPageInpainter::needsAotInpaint)) {
            return@withLock AotInpaintResult.Success(0)
        }

        val scratchDir = File(context.cacheDir, "aot-inpaint").apply { mkdirs() }
        val reqFile = File.createTempFile("aot-req-", ".json", scratchDir)
        val respFile = File.createTempFile("aot-resp-", ".json", scratchDir)

        val reply = CompletableDeferred<AotInpaintResult>()
        synchronized(stateLock) {
            pending = reply
            requestId++
        }

        try {
            reqFile.writeText(json.encodeToString(page), Charsets.UTF_8)
            val messenger = withTimeoutOrNull(15_000L) { connect().await() }
                ?: return@withLock AotInpaintResult.Failure("Tempo limite ao conectar ao serviço AOT-GAN")
                    .also { close() }

            ParcelFileDescriptor.open(imageFile, ParcelFileDescriptor.MODE_READ_ONLY).use { imgFd ->
                ParcelFileDescriptor.open(reqFile, ParcelFileDescriptor.MODE_READ_ONLY).use { reqFd ->
                    ParcelFileDescriptor.open(
                        respFile,
                        ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_TRUNCATE,
                    ).use { respFd ->
                        val msg = Message.obtain(null, AotInpaintService.MSG_INPAINT).apply {
                            arg1 = synchronized(stateLock) { requestId }
                            replyTo = receiver
                            data = Bundle().apply {
                                putParcelable(AotInpaintService.KEY_IMAGE, imgFd)
                                putParcelable(AotInpaintService.KEY_REQUEST, reqFd)
                                putParcelable(AotInpaintService.KEY_RESPONSE, respFd)
                            }
                        }
                        messenger.send(msg)
                    }
                }
            }

            val result = withTimeoutOrNull(180_000L) { reply.await() }
                ?: AotInpaintResult.Failure("Tempo limite na inferência AOT-GAN")

            when (result) {
                is AotInpaintResult.Failure -> {
                    close()
                    result
                }
                is AotInpaintResult.Success -> {
                    if (result.inpaintedBlocks > 0 && respFile.length() > 0L) {
                        val rawResp = respFile.readText(Charsets.UTF_8)
                        val updated = json.decodeFromString<PageTranslation>(rawResp)
                        if (updated.blocks.size == page.blocks.size) {
                            for (i in page.blocks.indices) {
                                val src = updated.blocks[i]
                                val dst = page.blocks[i]
                                if (!src.aotPatchBase64.isNullOrBlank() && src.aotPatchRegion != null) {
                                    dst.aotPatchBase64 = src.aotPatchBase64
                                    dst.aotPatchRegion = src.aotPatchRegion
                                    dst.backgroundColor = src.backgroundColor
                                    dst.foregroundColor = src.foregroundColor
                                    dst.balloonDetected = src.balloonDetected
                                }
                            }
                        }
                    }
                    result
                }
            }
        } catch (cancelled: CancellationException) {
            close()
            throw cancelled
        } catch (error: Throwable) {
            close()
            AotInpaintResult.Failure("Falha de comunicação AOT-GAN: ${error.javaClass.simpleName}")
        } finally {
            synchronized(stateLock) {
                if (pending === reply) pending = null
            }
            reqFile.delete()
            respFile.delete()
        }
    }

    private fun connect(): CompletableDeferred<Messenger> = synchronized(stateLock) {
        connected?.let { return@synchronized it }
        val ready = CompletableDeferred<Messenger>()
        connected = ready
        val callback = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                synchronized(stateLock) {
                    if (connection !== this) return
                    try {
                        service.linkToDeath({ died() }, 0)
                        val messenger = Messenger(service)
                        remote = messenger
                        ready.complete(messenger)
                    } catch (error: Exception) {
                        ready.completeExceptionally(error)
                        died()
                    }
                }
            }

            private fun died() {
                synchronized(stateLock) {
                    if (connection !== this) return
                    pending?.complete(AotInpaintResult.Failure("Processo AOT-GAN encerrado"))
                    ready.completeExceptionally(IllegalStateException("Processo AOT-GAN encerrado"))
                    remote = null
                }
            }

            override fun onServiceDisconnected(name: ComponentName) = died()
            override fun onBindingDied(name: ComponentName) = died()
            override fun onNullBinding(name: ComponentName) = died()
        }
        connection = callback
        if (!context.bindService(serviceIntent, callback, Context.BIND_AUTO_CREATE)) {
            connection = null
            ready.completeExceptionally(IllegalStateException("Serviço AOT-GAN indisponível"))
        }
        ready
    }

    override fun close() {
        synchronized(stateLock) {
            pending?.complete(AotInpaintResult.Failure("Sessão AOT-GAN encerrada"))
            connected?.cancel()
            connected = null
            try {
                remote?.send(Message.obtain(null, AotInpaintService.MSG_STOP).apply { replyTo = receiver })
            } catch (_: Exception) {
            }
            remote = null
            val bound = connection
            connection = null
            if (bound != null) {
                try {
                    context.unbindService(bound)
                } catch (_: IllegalArgumentException) {
                }
            }
        }
    }

    companion object {
        fun isDeviceSupported(): Boolean =
            Process.is64Bit() && "arm64-v8a" in Build.SUPPORTED_ABIS
    }
}
