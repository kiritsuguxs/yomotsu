package eu.kanade.translation.translator

import eu.kanade.tachiyomi.network.NetworkHelper
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.EOFException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

internal object TranslationNetworkHelper {

    private val retryInterceptor = Interceptor { chain ->
        val request = chain.request()
        var httpTryCount = 0
        var ioTryCount = 0

        while (true) {
            if (chain.call().isCanceled()) {
                throw IOException("Canceled")
            }
            val response: Response = try {
                chain.proceed(request)
            } catch (error: IOException) {
                if (chain.call().isCanceled() || !isTransientTranslationIoFailure(error) || ioTryCount >= 1) {
                    throw error
                }
                ioTryCount++
                sleepWithCancel(chain, 1_000L)
                continue
            }

            if (!response.isSuccessful && (response.code == 408 || response.code == 429 || response.code >= 500) && httpTryCount < 3) {
                httpTryCount++
                response.close()
                sleepWithCancel(chain, 2_000L * httpTryCount)
                continue
            }
            return@Interceptor response
        }
        @Suppress("UNREACHABLE_CODE")
        error("Unreachable")
    }

    private fun sleepWithCancel(chain: Interceptor.Chain, durationMs: Long) {
        val deadlineNs = System.nanoTime() + durationMs * 1_000_000L
        while (System.nanoTime() < deadlineNs) {
            if (chain.call().isCanceled()) {
                throw IOException("Canceled")
            }
            val remainingMs = ((deadlineNs - System.nanoTime()) / 1_000_000L).coerceAtMost(200L)
            if (remainingMs > 0L) {
                Thread.sleep(remainingMs)
            }
        }
    }

    internal fun isTransientTranslationIoFailure(error: IOException): Boolean =
        generateSequence<Throwable>(error) { it.cause }.any { cause ->
            cause is SocketTimeoutException ||
                cause is InterruptedIOException ||
                cause is ConnectException ||
                cause is SocketException ||
                cause is EOFException
        }

    val sharedClient: OkHttpClient by lazy {
        Injekt.get<NetworkHelper>().client.newBuilder()
            .connectTimeout(45, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(retryInterceptor)
            .build()
    }
}
