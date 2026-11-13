#!/system/bin/sh
# ROM "TV" test builds: when Android has not finished starting after 2.5 minutes, shows the errors
# on the TV (tvdiag.jar) and saves the logs on a FAT32 USB stick, folder TV-diag, every minute.
sleep 150
[ "$(getprop sys.boot_completed)" = "1" ] && exit 0
CLASSPATH=/system/framework/tvdiag.jar app_process /system/bin tv.rom.diag.Diag > /dev/tvdiag.log 2>&1 &
n=0
while [ "$(getprop sys.boot_completed)" != "1" ] && [ $n -lt 30 ]; do
    for dev in /dev/block/sd[a-z][0-9] /dev/block/sd[a-z]; do
        [ -b "$dev" ] || continue
        mkdir -p /dev/tvusb
        mount -t vfat -o rw,noatime "$dev" /dev/tvusb 2>/dev/null || continue
        D=/dev/tvusb/TV-diag
        mkdir -p "$D"
        echo "intento $n $(date) uptime $(cat /proc/uptime) zygote=$(getprop init.svc.zygote) selinux=$(getenforce)" >> "$D/resumen.txt"
        logcat -d -v threadtime -b main -b system -b crash -b events > "$D/logcat.txt" 2>&1
        logcat -d -b crash > "$D/crash.txt" 2>&1
        dmesg > "$D/dmesg.txt" 2>&1
        getprop > "$D/props.txt" 2>&1
        ps > "$D/procesos.txt" 2>&1
        cat /data/tombstones/tombstone_0* > "$D/tombstones.txt" 2>/dev/null
        cat /dev/tvdiag.log > "$D/pantalla.txt" 2>/dev/null
        sync
        umount /dev/tvusb
        break
    done
    n=$((n + 1))
    sleep 60
done
exit 0
