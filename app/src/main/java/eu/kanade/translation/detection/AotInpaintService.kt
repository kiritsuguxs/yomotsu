package eu.kanade.translation.detection

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import android.os.Process
import eu.kanade.translation.model.PageTranslation
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/** Non-exported, separate-process AOT-GAN neural inpainter. */
class AotInpaintService : Service() {
    private lateinit var thread: HandlerThread
    private lateinit var worker: Handler
    private var inpainter: Inpainter? = null
    private var busy = false
    private val lease = DbnetLease<IBinder>()
    private val main = Handler(Looper.getMainLooper())
    private val json = Json { ignoreUnknownKeys = true }

    private val messenger = Messenger(
        Handler(Looper.getMainLooper()) { message ->
            when (message.what) {
                MSG_STOP -> if (lease.ownedBy(message.replyTo?.binder)) terminate()
                MSG_INPAINT -> {
                    @Suppress("DEPRECATION")
                    val imageFd = message.data.getParcelable<ParcelFileDescriptor>(KEY_IMAGE)
                    @Suppress("DEPRECATION")
                    val reqFd = message.data.getParcelable<ParcelFileDescriptor>(KEY_REQUEST)
                    @Suppress("DEPRECATION")
                    val respFd = message.data.getParcelable<ParcelFileDescriptor>(KEY_RESPONSE)
                    val replyTo = message.replyTo
                    val id = message.arg1

                    if (imageFd == null || reqFd == null || respFd == null ||
                        replyTo == null || busy || !lease.claim(replyTo.binder)
                    ) {
                        imageFd?.close()
                        reqFd?.close()
                        respFd?.close()
                        replyTo?.let { replyError(it, id, "Solicitação AOT-GAN inválida ou ocupada") }
                    } else {
                        busy = true
                        worker.post {
                            var errorMsg: String? = null
                            var inpaintedCount = 0
                            try {
                                imageFd.use { img ->
                                    reqFd.use { req ->
                                        respFd.use { resp ->
                                            inpaintedCount = processRequest(img, req, resp)
                                        }
                                    }
                                }
                            } catch (error: Throwable) {
                                errorMsg = "AOT-GAN: ${error.javaClass.simpleName}: ${error.message.orEmpty()}".take(200)
                            }
                            main.post {
                                busy = false
                                if (errorMsg != null) {
                                    replyError(replyTo, id, errorMsg)
                                } else {
                                    replySuccess(replyTo, id, inpaintedCount)
                                }
                            }
                        }
                    }
                }
            }
            true
        },
    )

    override fun onCreate() {
        super.onCreate()
        check(DbnetProcess.isInpaintWorker(this)) { "AOT-GAN must run in :inpaint worker process" }
        thread = HandlerThread("AOT-GAN serial inference").apply { start() }
        worker = Handler(thread.looper)
    }

    override fun onBind(intent: Intent): IBinder = messenger.binder

    private fun processRequest(
        imageFd: ParcelFileDescriptor,
        reqFd: ParcelFileDescriptor,
        respFd: ParcelFileDescriptor,
    ): Int {
        val rawJson = FileInputStream(reqFd.fileDescriptor)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        val page = json.decodeFromString<PageTranslation>(rawJson)
        if (page.blocks.none(AotPageInpainter::needsAotInpaint)) return 0

        val engine = ensureEngine()
        val count = AotPageInpainter(engine, AotPageInpainter.AOT_MODEL_TILE_SIZE)
            .inpaintPage(imageFd, page)

        if (count > 0) {
            val encoded = json.encodeToString(page).toByteArray(Charsets.UTF_8)
            FileOutputStream(respFd.fileDescriptor).use { out ->
                out.write(encoded)
                out.flush()
            }
        }
        return count
    }

    private fun ensureEngine(): Inpainter {
        inpainter?.takeIf { it.isModelLoaded }?.let { return it }
        inpainter?.close()
        val modelsDir = File(filesDir, MODEL_RELATIVE_DIR)
        val paramFile = File(modelsDir, MODEL_PARAM_NAME)
        val instance = Inpainter(
            modelPath = paramFile.absolutePath,
            cfg = InpainterConfig(
                method = InpainterConfig.METHOD_AOT,
                tileSize = AotPageInpainter.AOT_MODEL_TILE_SIZE,
            ),
            backend = DbnetNativeBackend,
        )
        check(instance.isModelLoaded) { "Falha ao carregar modelo nativo AOT-GAN" }
        instance.warmUp()
        inpainter = instance
        return instance
    }

    private fun replySuccess(to: Messenger, id: Int, inpaintedCount: Int) {
        val response = Message.obtain(null, MSG_RESULT).apply {
            arg1 = id
            data = Bundle().apply {
                putInt(KEY_INPAINTED_COUNT, inpaintedCount)
            }
        }
        try {
            to.send(response)
        } catch (_: Exception) {
            terminate()
        }
    }

    private fun replyError(to: Messenger, id: Int, reason: String) {
        val response = Message.obtain(null, MSG_RESULT).apply {
            arg1 = id
            data = Bundle().apply {
                putString(KEY_ERROR, reason.take(200))
            }
        }
        try {
            to.send(response)
        } catch (_: Exception) {
            terminate()
        }
    }

    private fun terminate() {
        if (DbnetProcess.isInpaintWorker(this)) Process.killProcess(Process.myPid())
    }

    override fun onDestroy() {
        super.onDestroy()
        terminate()
    }

    companion object {
        const val MSG_INPAINT = 1
        const val MSG_RESULT = 2
        const val MSG_STOP = 3

        const val KEY_IMAGE = "image"
        const val KEY_REQUEST = "request"
        const val KEY_RESPONSE = "response"
        const val KEY_INPAINTED_COUNT = "inpaintedCount"
        const val KEY_ERROR = "error"

        const val MODEL_RELATIVE_DIR = "models/inpainter-v2"
        const val MODEL_PARAM_NAME = "mit_aot_fixed512.ncnn.param"
    }
}
