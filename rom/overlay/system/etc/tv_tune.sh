#!/system/bin/sh
# ROM "TV": memory and speed tuning, by the RAM this box has (1 or 2 GB).
# Run by init once Android has started (etc/init/tv_tune.rc).

MEM_KB=0
while read -r key value unit; do
    [ "$key" = "MemTotal:" ] && MEM_KB=$value && break
done < /proc/meminfo

# Low-memory killer: Android writes its own levels while it starts, so these go after.
if [ "$MEM_KB" -lt 1300000 ]; then
    # 1 GB: close background apps sooner.
    echo "12288,15360,18432,21504,24576,30720" > /sys/module/lowmemorykiller/parameters/minfree
else
    # 2 GB: keep more apps around, switch between them faster.
    echo "18432,23040,27648,32256,55296,80640" > /sys/module/lowmemorykiller/parameters/minfree
fi
# Compressed memory (zram) is on: lean on it before killing apps.
echo 100 > /proc/sys/vm/swappiness
echo 0 > /proc/sys/vm/page-cluster

# Only on the first boot: later changes in Settings are kept.
if [ "$(settings get global tv_rom_tuned)" != "1" ]; then
    # Faster animations.
    settings put global window_animation_scale 0.5
    settings put global transition_animation_scale 0.5
    settings put global animator_duration_scale 0.5
    # Location off: Google services stop working out where the box is (the weather has its city).
    settings put secure location_providers_allowed -gps
    settings put secure location_providers_allowed -network
    settings put secure location_mode 0
    # No Wi-Fi or Bluetooth scanning while they are off ("always available" scanning).
    settings put global wifi_scan_always_enabled 0
    settings put global ble_scan_always_enabled 0
    # No Google backup running in the background.
    bmgr enable false
    # Wi-Fi for Chile: the 5 GHz channels the country allows.
    settings put global wifi_country_code CL
    # HDMI-CEC on: the TV turns on and switches to the box with it, and turns off with it.
    settings put global hdmi_control_enabled 1
    settings put global hdmi_control_one_touch_play_enabled 1
    settings put global hdmi_control_auto_device_off_enabled 1
    settings put global hdmi_control_auto_wakeup_enabled 1
    # Standby after 3 hours without use (one of the choices in Settings, so it can be changed there).
    settings put system screen_off_timeout 10800000
    settings put global tv_rom_tuned 1
fi
# The launcher compiled ahead of time, so it opens and moves fast from the first day; again
# whenever a new version of it comes with a ROM update. Low priority, after boot.
AERIAL=$(dumpsys package app.orchard.tv | grep -m1 versionCode | sed 's/.*versionCode=\([0-9]*\).*/\1/')
if [ -n "$AERIAL" ] && [ "$(settings get global tv_rom_aerial_compiled)" != "$AERIAL" ]; then
    nice -n 19 cmd package compile -m speed -f app.orchard.tv > /dev/null 2>&1 && \
        settings put global tv_rom_aerial_compiled "$AERIAL"
fi
exit 0
