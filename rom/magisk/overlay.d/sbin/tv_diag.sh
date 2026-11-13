#!/system/bin/sh
# ROM "TV": when Android has not finished starting after 3 minutes, saves what is needed to find out
# why on a USB stick (FAT32), in a folder TV-diag, and again every minute (up to 20 times).
# When the box starts normally it does nothing and ends.
sleep 180
n=0
while [ "$(getprop sys.boot_completed)" != "1" ] && [ $n -lt 20 ]; do
    for dev in /dev/block/sd[a-z][0-9] /dev/block/sd[a-z]; do
        [ -b "$dev" ] || continue
        mkdir -p /dev/tvdiag
        mount -t vfat -o rw,noatime "$dev" /dev/tvdiag 2>/dev/null || continue
        D=/dev/tvdiag/TV-diag
        mkdir -p "$D"
        {
            echo "intento $n, $(date), uptime $(cat /proc/uptime)"
            echo "boot_completed=$(getprop sys.boot_completed) bootanim=$(getprop init.svc.bootanim) zygote=$(getprop init.svc.zygote)"
        } >> "$D/resumen.txt"
        logcat -d -v threadtime -b main -b system -b crash -b events > "$D/logcat.txt" 2>&1
        logcat -d -b crash > "$D/crash.txt" 2>&1
        dmesg > "$D/dmesg.txt" 2>&1
        getprop > "$D/props.txt" 2>&1
        ps > "$D/procesos.txt" 2>&1
        ls -la /data/tombstones /data/anr > "$D/tombstones.txt" 2>&1
        cat /data/tombstones/tombstone_0* > "$D/tombstone_ultimo.txt" 2>/dev/null
        cat /proc/meminfo > "$D/memoria.txt" 2>&1
        cat /proc/last_kmsg > "$D/last_kmsg.txt" 2>/dev/null
        sync
        umount /dev/tvdiag
        break
    done
    n=$((n + 1))
    sleep 60
done
exit 0
