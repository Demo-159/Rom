#!/system/bin/sh
# ROM "TV": memory and speed tuning, by the RAM this box has (1 or 2 GB).
# Run by init through Magisk (overlay.d/tv_tune.rc): "early" before Android starts, "boot" once it has.
MAGISKTMP="$2"
resetprop() { "$MAGISKTMP/magisk" resetprop "$@"; }

MEM_KB=0
while read -r key value unit; do
    [ "$key" = "MemTotal:" ] && MEM_KB=$value && break
done < /proc/meminfo

case "$1" in
early)
    if [ "$MEM_KB" -lt 1300000 ]; then
        # 1 GB: close background apps sooner, smaller app heaps.
        echo "12288,15360,18432,21504,24576,30720" > /sys/module/lowmemorykiller/parameters/minfree
        resetprop dalvik.vm.heapstartsize 8m
        resetprop dalvik.vm.heapgrowthlimit 128m
        resetprop dalvik.vm.heapsize 256m
        resetprop dalvik.vm.heapmaxfree 4m
    else
        # 2 GB: keep more apps around, switch between them faster.
        echo "18432,23040,27648,32256,55296,80640" > /sys/module/lowmemorykiller/parameters/minfree
    fi
    # Compressed memory (zram) is on: lean on it before killing apps.
    echo 100 > /proc/sys/vm/swappiness
    echo 0 > /proc/sys/vm/page-cluster
    ;;
boot)
    # Faster animations, only on the first boot: later changes in Settings are kept.
    if [ "$(settings get global tv_rom_tuned)" != "1" ]; then
        settings put global window_animation_scale 0.5
        settings put global transition_animation_scale 0.5
        settings put global animator_duration_scale 0.5
        settings put global tv_rom_tuned 1
    fi
    ;;
esac
exit 0
