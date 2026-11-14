#!/vendor/bin/sh
# ROM "TV": speed and memory tuning once Android has started (init: /vendor/etc/init/tv_tune.rc).
# Runs in its own SELinux domain (tvtune, rom/atv9/sepolicy/tvtune.cil). Only shell built-ins,
# and a setting the kernel does not have is skipped.
w() { [ -w "$1" ] && echo "$2" > "$1"; }

# The USB remote (its receiver powered by the box) can only turn the box back on if the USB ports
# keep their power: no deep sleep. The power button still turns the picture off.
w /sys/power/wake_lock tv_rom_usb_remote

# Compressed memory (zram) is on: lean on it before closing apps, and read it page by page.
w /proc/sys/vm/swappiness 100
w /proc/sys/vm/page-cluster 0
# Keep the file cache (app code, libraries) a little longer: apps reopen sooner.
w /proc/sys/vm/vfs_cache_pressure 60

# CPU (interactive governor, set by the vendor): reach full speed sooner after a key press and stay
# there a little longer, so menus and app starts do not wait for the clock to climb.
for d in /sys/devices/system/cpu/cpu0/cpufreq/interactive /sys/devices/system/cpu/cpufreq/interactive; do
    w $d/go_hispeed_load 60
    w $d/above_hispeed_delay 20000
    w $d/min_sample_time 80000
    w $d/timer_rate 20000
    w $d/io_is_busy 1
done

# Internal storage (eMMC): a light scheduler and a larger read-ahead, so apps open sooner.
for q in /sys/block/mmcblk0/queue /sys/block/mmcblk1/queue; do
    w $q/scheduler deadline
    w $q/read_ahead_kb 512
    w $q/iostats 0
    w $q/add_random 0
done
exit 0
