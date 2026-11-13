#!/system/bin/sh
# ROM "TV" test builds.
# 1. If Android has not finished starting after 2.5 minutes, shows the errors on the TV (tvdiag.jar)
#    and saves the logs on any FAT32 USB stick, folder TV-diag, every minute.
# 2. Once it has started: whenever a USB stick with a folder named TV-diag is plugged in, saves
#    the logs there (a new subfolder each time, at most once a minute).

save() {   # $1: folder to write into
    D="$1"
    mkdir -p "$D"
    echo "$(date) uptime $(cat /proc/uptime) boot_completed=$(getprop sys.boot_completed) selinux=$(getenforce)" > "$D/resumen.txt"
    logcat -d -v threadtime -b main -b system -b crash -b events > "$D/logcat.txt" 2>&1
    logcat -d -b crash > "$D/crash.txt" 2>&1
    dmesg > "$D/dmesg.txt" 2>&1
    dmesg | grep "avc:" > "$D/selinux.txt" 2>&1
    cat /data/anr/traces.txt > "$D/anr.txt" 2>/dev/null
    cat /data/tombstones/tombstone_0* > "$D/tombstones.txt" 2>/dev/null
    getprop > "$D/props.txt" 2>&1
    ps > "$D/procesos.txt" 2>&1
    dumpsys activity activities > "$D/actividades.txt" 2>&1
    # Crash records Android keeps across restarts (a system crash restarts the box).
    dumpsys dropbox --print > "$D/caidas.txt" 2>&1
    ls -la /data/system/dropbox > "$D/caidas_lista.txt" 2>&1
    cat /sys/fs/pstore/* > "$D/pstore.txt" 2>/dev/null
    cat /dev/tvdiag.log > "$D/pantalla.txt" 2>/dev/null
    sync
}

sleep 150
if [ "$(getprop sys.boot_completed)" != "1" ]; then
    CLASSPATH=/system/framework/tvdiag.jar app_process /system/bin tv.rom.diag.Diag > /dev/tvdiag.log 2>&1 &
    n=0
    while [ "$(getprop sys.boot_completed)" != "1" ] && [ $n -lt 30 ]; do
        for dev in /dev/block/sd[a-z][0-9] /dev/block/sd[a-z]; do
            [ -b "$dev" ] || continue
            mkdir -p /dev/tvusb
            mount -t vfat -o rw,noatime "$dev" /dev/tvusb 2>/dev/null || continue
            save /dev/tvusb/TV-diag/arranque
            umount /dev/tvusb
            break
        done
        n=$((n + 1))
        sleep 60
    done
fi

# Started: a stick with a TV-diag folder, mounted by Android under /mnt/media_rw.
while true; do
    for dir in /mnt/media_rw/*/TV-diag; do
        [ -d "$dir" ] || continue
        stamp=$(date +%Y%m%d-%H%M)
        [ -d "$dir/$stamp" ] && continue
        save "$dir/$stamp"
    done
    sleep 15
done
