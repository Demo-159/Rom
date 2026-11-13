package app.orchard.tv.wallpaper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import app.orchard.tv.R

/**
 * ROM "TV": keeps Aerial alive for a while after the QR code is shown, so the phone page still
 * answers when Aerial is not the home screen and the system would otherwise stop it in the
 * background (a phone, where the same device opens the page). On a TV Aerial is the home screen and
 * always running, so this is skipped there and costs nothing.
 */
class PhonePageKeepAlive : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val stop = Runnable { stopSelf() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.phone_page_channel), NotificationManager.IMPORTANCE_LOW),
            )
        }
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(getString(R.string.phone_page_active))
            .setContentText(getString(R.string.phone_page_active_hint))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification,
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        PhoneServer.listen(this)
        handler.removeCallbacks(stop)
        handler.postDelayed(stop, KEEP_MS)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(stop)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "phone_page"
        private const val NOTIFICATION_ID = 8790
        private const val KEEP_MS = 15 * 60 * 1000L

        /** Starts (or extends) the keep-alive, except on a TV, where Aerial never stops. */
        fun start(context: Context) {
            val pm = context.packageManager
            if (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
                pm.hasSystemFeature("android.hardware.type.television")
            ) return
            runCatching {
                androidx.core.content.ContextCompat.startForegroundService(
                    context, Intent(context, PhonePageKeepAlive::class.java),
                )
            }
        }
    }
}
