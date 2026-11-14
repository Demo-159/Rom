#!/usr/bin/env python3
"""ROM "TV" on Aidan's ROM v9 (Android TV 9 for the TX3 Mini, S905W): builds the flashable image.

The base is a real Android TV 9 (Google's TV Play Store, Play services, setup, voice search and
TV Settings), so only what makes it this ROM goes on top: Aerial as the only home screen,
TizenTube as YouTube, less bloat, white label "TV", the dots boot animation, a black logo and the
memory tuning. Verified boot is off in its vbmeta and its fstab has no verity, so the system and
vendor images can change; the boot image is left as it is.

Needs root (loop mount), e2fsck, tune2fs, zerofree, setfattr and Python 3 with Pillow.
Usage: sudo python3 rom/build_rom_atv9.py AIDAN.img OUT.img
"""
import os, shutil, subprocess, sys, tempfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_rom as b  # noqa: E402  (the stock-based build's helpers)

ATV9 = os.path.join(b.ROM, "atv9")


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


def update_remote_service(sysdir):
    """Android TV Remote Service 5.2 (rom/prebuilt/AtvRemoteService.apk) in place of the base's 4.1:
    the Google TV app on a phone, the owner's only remote, does not find a box with 4.x. Only if
    Google signed it like the one in the base."""
    apk = os.path.join(b.ROM, "prebuilt", "AtvRemoteService.apk")
    target = os.path.join(sysdir, "priv-app", "AtvRemoteService", "AtvRemoteService.apk")
    want, got = b.stock_cert(target), b.apk_cert(apk)
    if not want or got != want:
        sys.exit(f"AtvRemoteService.apk is not signed like the base's ({got} vs {want}): refusing it")
    b.install_app(sysdir, "priv-app/AtvRemoteService", apk, extract_libs=False)
    print("  Android TV Remote Service updated", flush=True)


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
        update_remote_service(sysdir)
        tt = b.fetch(b.TIZENTUBE_URL, os.path.join(work, "TizenTube.apk"))
        b.install_app(sysdir, "app/TizenTube", tt, extract_libs=True)
        # Stands in for YouTube (same package name; no YouTube in this base) and hands voice search
        # results to TizenTube.
        b.install_app(sysdir, "app/YouTubeBridge", os.path.join(b.ROM, "prebuilt", "YouTubeBridge.apk"), extract_libs=False)
        copy_tree(os.path.join(ATV9, "overlay"), root)
        b.run(sys.executable, os.path.join(b.ROM, "tools", "make_bootanim.py"), work)
        anim = os.path.join(sysdir, "media", "bootanimation.zip")
        shutil.copy(os.path.join(work, "bootanimation.zip"), anim)
        b.label(anim)
        b.edit_build_prop(os.path.join(sysdir, "build.prop"), os.path.join(ATV9, "build.prop.txt"))
        # Telemetry hosts blocked (rom/atv9/hosts.txt); the file keeps its owner, mode and label.
        with open(os.path.join(sysdir, "etc", "hosts"), "a", encoding="utf-8") as f:
            f.write("\n" + open(os.path.join(ATV9, "hosts.txt"), encoding="utf-8").read())
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
        add_tuning(mnt)
        check_policy(parts, work, mnt)
    finally:
        b.run("umount", mnt)
    finish_ext4(raw)


def put(src, dst, mode, gid, context):
    shutil.copy(src, dst)
    os.chown(dst, 0, gid)
    os.chmod(dst, mode)
    b.run("setfattr", "-n", "security.selinux", "-v", context, dst)


def add_tuning(vendor):
    """The speed and memory tuning: its script and init file, and its SELinux domain (tvtune).
    A user build with SELinux enforcing: init itself may not write most of these settings, and a
    script needs a domain of its own (rom/atv9/sepolicy/tvtune.cil)."""
    src = os.path.join(ATV9, "vendor")
    put(os.path.join(src, "bin", "tv_tune.sh"), os.path.join(vendor, "bin", "tv_tune.sh"),
        0o755, 2000, "u:object_r:tvtune_exec:s0")
    put(os.path.join(src, "etc", "init", "tv_tune.rc"), os.path.join(vendor, "etc", "init", "tv_tune.rc"),
        0o644, 0, "u:object_r:vendor_configs_file:s0")
    cil = os.path.join(vendor, "etc", "selinux", "vendor_sepolicy.cil")
    with open(cil, "a", encoding="utf-8") as f:
        f.write("\n" + open(os.path.join(ATV9, "sepolicy", "tvtune.cil"), encoding="utf-8").read())
    fc = os.path.join(vendor, "etc", "selinux", "vendor_file_contexts")
    with open(fc, "a", encoding="utf-8") as f:
        f.write("/vendor/bin/tv_tune\\.sh\t\tu:object_r:tvtune_exec:s0\n")


def check_policy(parts, work, vendor):
    """Compiles the SELinux policy the way init does at every boot on this base (it has no
    precompiled policy): a policy that does not compile would leave the box unable to start."""
    sysmnt = os.path.join(work, "system-ro")
    os.makedirs(sysmnt, exist_ok=True)
    b.run("mount", "-o", "loop,ro", os.path.join(parts, "system.PARTITION"), sysmnt)
    try:
        sel = os.path.join(sysmnt, "system", "etc", "selinux")
        args = ["secilc", os.path.join(sel, "plat_sepolicy.cil"), "-m", "-M", "true", "-G", "-N", "-c", "30",
                os.path.join(sel, "mapping", "28.0.cil"),
                os.path.join(vendor, "etc", "selinux", "plat_pub_versioned.cil"),
                os.path.join(vendor, "etc", "selinux", "vendor_sepolicy.cil"),
                "-o", os.path.join(work, "policy.bin"), "-f", "/dev/null"]
        if subprocess.run(args).returncode != 0:
            sys.exit("the SELinux policy with the tvtune domain does not compile: not building this image")
        print("  SELinux policy compiles with the tvtune domain", flush=True)
    finally:
        b.run("umount", sysmnt)


def main():
    if os.geteuid() != 0:
        sys.exit("run as root (the images are mounted)")
    base, out = sys.argv[1], sys.argv[2]
    if not os.path.exists(b.AERIAL_APK):
        sys.exit("missing " + b.AERIAL_APK)
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
