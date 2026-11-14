package app.orchard.tv.system

import android.content.Context
import android.provider.Settings

/**
 * ROM "TV": the system settings the ROM starts with, set once, on Home's first start after a
 * flash. Later changes in Settings are kept. Aerial is a privileged system app in the ROM, so it
 * holds WRITE_SECURE_SETTINGS; outside the ROM (a plain install) the writes fail and are skipped.
 *
 * Done here rather than by a boot script: the Android TV 9 base is a "user" build with SELinux
 * enforcing, where a script started by init has no domain to run in.
 */
object RomSetup {
    private const val DONE = "tv_rom_tuned"

    fun firstBoot(context: Context) {
        val cr = context.contentResolver
        if (Settings.Global.getString(cr, DONE) == "1") return
        fun global(key: String, value: String) = runCatching { Settings.Global.putString(cr, key, value) }
        fun secure(key: String, value: String) = runCatching { Settings.Secure.putString(cr, key, value) }
        fun system(key: String, value: String) = runCatching { Settings.System.putString(cr, key, value) }
        // Faster animations.
        global(Settings.Global.WINDOW_ANIMATION_SCALE, "0.5")
        global(Settings.Global.TRANSITION_ANIMATION_SCALE, "0.5")
        global(Settings.Global.ANIMATOR_DURATION_SCALE, "0.5")
        // Location off: Google services stop working out where the box is (the weather has its city).
        secure(Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF.toString())
        // No Wi-Fi or Bluetooth scanning while they are off ("always available" scanning).
        global("wifi_scan_always_enabled", "0")
        global("ble_scan_always_enabled", "0")
        // No Google backup running in the background.
        secure("backup_enabled", "0")
        // Wi-Fi for Chile: the 5 GHz channels the country allows.
        global("wifi_country_code", "CL")
        // Standby after 3 hours without use (one of the choices in Settings, so it can be changed there).
        system(Settings.System.SCREEN_OFF_TIMEOUT, "10800000")
        if (global(DONE, "1").getOrDefault(false)) DiagLog.i("ROM first-boot settings applied")
    }
}
