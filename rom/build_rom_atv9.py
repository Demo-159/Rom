#!/usr/bin/env python3
"""ROM "TV" on Aidan's ROM v9 (Android TV 9 for the TX3 Mini, S905W): builds the flashable image.

The base is a real Android TV 9 (Google's TV Play Store, Play services, setup, voice search and
TV Settings), so only what makes it this ROM goes on top: Aerial as the only home screen,
TizenTube as YouTube, the "TV en vivo" m3u8 player, less bloat, white label "TV", the dots boot animation, a black logo and the
memory tuning. Verified boot is off in its vbmeta and its fstab has no verity, so the system and
vendor images can change; the boot image is left as it is.

Needs root (loop mount), e2fsck, tune2fs, zerofree, setfattr and Python 3 with Pillow.
Usage: sudo python3 rom/build_rom_atv9.py AIDAN.img OUT.img
"""
import os, shutil, subprocess, sys, tempfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_rom as b  # noqa: E402  (the stock-based build's helpers)

ATV9 = os.path.join(b.ROM, "atv9")
# "TV en vivo": the m3u8 player (tvlive/), MEGA as its first channel.
TVLIVE_APK = os.path.join(b.ROOT, "tvlive", "app", "build", "outputs", "apk", "release", "app-release.apk")


def debloat(root):
    for line in open(os.path.join(ATV9, "debloat.txt"), encoding="utf-8"):
        p = line.strip()
        if not p or p.startswith("#"):
            continue
        target = os.path.join(root, p)
        if os.path.isdir(target):
            print("  removing", p)
            shutil.rmtree(target)
        elif os.path.exists(target):
            print("  removing", p)
            os.remove(target)
        else:
            print("  (not found)", p)


def copy_tree(src, dst_root):
    for base, _, files in os.walk(src):
        for f in files:
            rel = os.path.relpath(os.path.join(base, f), src)
            dst = os.path.join(dst_root, rel)
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            shutil.copy(os.path.join(base, f), dst)
            b.label(dst)


def finish_ext4(raw):
    b.fsck(raw)
    # Mounting marks the journal (revoke records); a fresh journal leaves it as the base had it.
    b.run("tune2fs", "-O", "^has_journal", raw)
    b.run("tune2fs", "-j", raw)
    b.fsck(raw)
    # Freed blocks to zero: the image is raw, so this only helps the zip, not the flash.
    b.run("zerofree", raw)


def build_system(parts, work):
    raw = os.path.join(parts, "system.PARTITION")  # raw ext4, system-as-root
    mnt = os.path.join(work, "system")
    os.makedirs(mnt)
    b.run("mount", "-o", "loop,rw", raw, mnt)
    try:
        root = mnt
        sysdir = os.path.join(mnt, "system")
        debloat(root)
        b.install_app(sysdir, "priv-app/Aerial", b.AERIAL_APK, extract_libs=True)
        tt = b.fetch(b.TIZENTUBE_URL, os.path.join(work, "TizenTube.apk"))
        b.install_app(sysdir, "app/TizenTube", tt, extract_libs=True)
        # Stands in for YouTube (same package name; no YouTube in this base) and hands voice search
        # results to TizenTube.
        b.install_app(sysdir, "app/YouTubeBridge", os.path.join(b.ROM, "prebuilt", "YouTubeBridge.apk"), extract_libs=False)
        b.install_app(sysdir, "app/TvEnVivo", TVLIVE_APK, extract_libs=False)
        copy_tree(os.path.join(ATV9, "overlay"), root)
        b.run(sys.executable, os.path.join(b.ROM, "tools", "make_bootanim.py"), work)
        anim = os.path.join(sysdir, "media", "bootanimation.zip")
        shutil.copy(os.path.join(work, "bootanimation.zip"), anim)
        b.label(anim)
        b.edit_build_prop(os.path.join(sysdir, "build.prop"), os.path.join(ATV9, "build.prop.txt"))
        b.run("df", "-h", mnt)
    finally:
        b.run("umount", mnt)
    finish_ext4(raw)


def build_vendor(parts, work):
    raw = os.path.join(parts, "vendor.PARTITION")
    mnt = os.path.join(work, "vendor")
    os.makedirs(mnt)
    b.run("mount", "-o", "loop,rw", raw, mnt)
    try:
        prop = os.path.join(mnt, "build.prop")
        text = open(prop, encoding="utf-8").read()
        text = text.replace("ro.product.vendor.model=TX3 Mini", "ro.product.vendor.model=TV")
        open(prop, "w", encoding="utf-8").write(text)
        # Compressed memory: 512 MB instead of 256 MB (2 GB box), as the stock firmware had.
        fstab = os.path.join(mnt, "etc", "fstab.amlogic")
        text = open(fstab, encoding="utf-8").read()
        assert "zramsize=268435456" in text, "zram line not as expected"
        open(fstab, "w", encoding="utf-8").write(text.replace("zramsize=268435456", "zramsize=536870912"))
    finally:
        b.run("umount", mnt)
    finish_ext4(raw)


def main():
    if os.geteuid() != 0:
        sys.exit("run as root (the images are mounted)")
    base, out = sys.argv[1], sys.argv[2]
    for apk in (b.AERIAL_APK, TVLIVE_APK):
        if not os.path.exists(apk):
            sys.exit("missing " + apk)
    work = tempfile.mkdtemp(prefix="tvrom9-", dir=os.path.dirname(os.path.abspath(out)))
    parts = os.path.join(work, "parts")
    b.run(sys.executable, b.UNPACK, "unpack", base, parts)
    build_system(parts, work)
    build_vendor(parts, work)
    b.run(sys.executable, os.path.join(b.ROM, "tools", "logo_black.py"), os.path.join(parts, "logo.PARTITION"))
    b.refresh_verify(parts)
    b.run(sys.executable, b.UNPACK, "repack", parts, out)
    shutil.rmtree(work)
    print("done:", out, os.path.getsize(out) // (1024 * 1024), "MB")


if __name__ == "__main__":
    main()
