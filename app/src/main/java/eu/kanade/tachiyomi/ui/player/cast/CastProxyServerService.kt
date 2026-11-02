package eu.kanade.tachiyomi.ui.player.cast

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.tachiyomi.util.system.notificationBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.aniyomi.AYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class CastProxyServerService : Service() {

    private var server: CastProxyServer? = null

    private val networkHelper: NetworkHelper = Injekt.get()
    private val playerPreferences: PlayerPreferences = Injekt.get()
    private val json: Json = Injekt.get()

    override fun onBind(p0: Intent?): IBinder? = null

    private val notificationBitmap by lazy {
        BitmapFactory.decodeResource(this.resources, R.mipmap.ic_launcher)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (server?.isAlive == true && isRunning.value) return START_STICKY

        val address = intent?.getStringExtra(EXTRA_ADDRESS) ?: run {
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            server?.stop()
        } catch (_: Exception) {}

        val port = playerPreferences.castProxyPort().get()
        server = CastProxyServer(
            baseClient = networkHelper.client,
            contentResolver = contentResolver,
            ipAddress = address,
            port = port,
            json = json,
        )

        try {
            server?.start()
            logcat(LogPriority.INFO) { "Cast proxy server started on $address:$port" }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to start cast server on port $port" }
            _isRunning.update { false }
            stopSelf()
            return START_NOT_STICKY
        }

        _isRunning.update { _ -> true }

        val stopIntent = Intent(this, CastProxyServerService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStopIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = notificationBuilder(Notifications.CHANNEL_CAST_SERVER) {
            setLargeIcon(notificationBitmap)
            setSmallIcon(R.drawable.ic_cast_24dp)
            addAction(
                R.drawable.ic_pause_24dp,
                "Stop",
                pendingStopIntent,
            )
            setContentText(stringResource(AYMR.strings.pref_cast_server))
            setAutoCancel(false)
            setOngoing(true)
            setUsesChronometer(true)
        }

        try {
            ServiceCompat.startForeground(
                this,
                Notifications.ID_CAST_SERVER,
                builder.build(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    0
                },
            )
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to start foreground for CastProxyServerService" }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        _isRunning.update { _ -> false }
        try {
            server?.stop()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Error stopping cast proxy server" }
        }
        super.onDestroy()
    }

    companion object {
        private const val NAME = "CastProxyServer"
        private val ACTION_STOP = "${BuildConfig.APPLICATION_ID}.$NAME.ACTION_STOP"
        const val EXTRA_ADDRESS = "$NAME.EXTRA.ADDRESS"

        private val _isRunning = MutableStateFlow(false)
        val isRunning = _isRunning.asStateFlow()
    }
}
