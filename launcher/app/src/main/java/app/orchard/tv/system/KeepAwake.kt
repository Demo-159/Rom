package app.orchard.tv.system

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import app.orchard.tv.R
import app.orchard.tv.RomFlags

/**
 * ROM "TV": the power button turns the picture off, but the box does not go into deep sleep.
 *
 * The owner's remote is a USB one (a 2.4 GHz receiver in a USB port, with a microphone). In deep
 * sleep the Amlogic S905W powers the USB ports down, so the receiver had no power and nothing could
 * wake the box but unplugging it. Holding a partial wake lock keeps the kernel from suspending:
 * with the screen off the CPU idles at its lowest clock (well under a watt more), the USB remote
 * stays powered, and its power key wakes the screen as usual.
 *
 * A foreground service, so the system does not stop it while another app is in front.
 */
class KeepAwake : Service() {
    private var lock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.keep_awake_channel), NotificationManager.IMPORTANCE_MIN))
        }
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle(getString(R.string.keep_awake_title))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification,
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        if (lock == null) {
            lock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "aerial:usb-remote")
                .apply { setReferenceCounted(false); acquire() }
            DiagLog.i("keep-awake: deep sleep off (USB remote)")
        }
        return START_STICKY
    }

    override fun onDestroy() {
        lock?.let { if (it.isHeld) it.release() }
        lock = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "keep_awake"
        private const val NOTIFICATION_ID = 8791

        /** Starts it in the ROM only: Aerial installed as a system app on a TV. */
        fun start(context: Context) {
            if (!RomFlags.KEEP_AWAKE) return
            val pm = context.packageManager
            val tv = pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) || pm.hasSystemFeature("android.hardware.type.television")
            val system = (context.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            if (!tv || !system) return
            runCatching {
                androidx.core.content.ContextCompat.startForegroundService(context, Intent(context, KeepAwake::class.java))
            }.onFailure { DiagLog.i("keep-awake: could not start: $it") }
        }
    }
}
