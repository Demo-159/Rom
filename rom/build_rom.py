#!/usr/bin/env python3
"""ROM "TV" for the Tanix TX3 Mini (Amlogic S905W, p281): builds the flashable image from stock.

Needs root (loop mount), simg2img/img2simg, e2fsck, setfattr, unzip and Python 3 with Pillow.
Usage: sudo python3 rom/build_rom.py STOCK.img OUT.img

Steps, each one described in docs/DECISIONES.md:
  1. unpack the USB Burning Tool image            (tools/aml_image_unpack.py)
  2. system: remove apps, add Aerial, TV settings,
     TizenTube, TV features, boot animation, build.prop
  3. boot: Magisk (root) plus the memory tuning    (rom/magisk)
  4. logo: black
  5. VERIFY sha1 files, repack the image
"""
import hashlib, os, re, shutil, subprocess, sys, tempfile, urllib.request, zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ROM = os.path.join(ROOT, "rom")
UNPACK = os.path.join(ROOT, "tools", "aml_image_unpack.py")

MAGISK_URL = "https://github.com/topjohnwu/Magisk/releases/download/v27.0/Magisk-v27.0.apk"
TIZENTUBE_URL = "https://github.com/reisxd/TizenTubeCobalt/releases/download/v2.0.3/cobalt-arm.apk"
AERIAL_APK = os.path.join(ROOT, "launcher", "app", "build", "outputs", "apk", "standard", "release", "app-standard-release.apk")
TV_SETTINGS_APK = os.path.join(ROM, "prebuilt", "iTvSettings.apk")
SYSTEM_FILE = "u:object_r:system_file:s0"


def run(*cmd, **kw):
    print("+", " ".join(cmd), flush=True)
    return subprocess.run(cmd, check=True, **kw)


def fetch(url, dest):
    if not os.path.exists(dest):
        print("downloading", url, flush=True)
        urllib.request.urlretrieve(url, dest)
    return dest


def label(path):
    """Owner, mode and SELinux label as the build system gives /system files."""
    for base, dirs, files in os.walk(path):
        for n in [base] + [os.path.join(base, f) for f in files]:
            os.chown(n, 0, 0)
            os.chmod(n, 0o755 if os.path.isdir(n) else 0o644)
            run("setfattr", "-n", "security.selinux", "-v", SYSTEM_FILE, n)


def install_app(system, rel_dir, apk, extract_libs):
    """An APK as a system app, its native libraries unpacked beside it (system apps do not extract)."""
    d = os.path.join(system, rel_dir)
    shutil.rmtree(d, ignore_errors=True)
    os.makedirs(d)
    name = os.path.basename(rel_dir) + ".apk"
    shutil.copy(apk, os.path.join(d, name))
    if extract_libs:
        with zipfile.ZipFile(apk) as z:
            libs = [n for n in z.namelist() if n.startswith("lib/armeabi-v7a/") and n.endswith(".so")]
            if libs:
                os.makedirs(os.path.join(d, "lib", "arm"))
                for n in libs:
                    with z.open(n) as src, open(os.path.join(d, "lib", "arm", os.path.basename(n)), "wb") as out:
                        shutil.copyfileobj(src, out)
    label(d)


def apk_cert(apk):
    out = subprocess.run([os.path.join(os.environ.get("ANDROID_HOME", "/home/user/sdk"), "build-tools", "36.0.0", "apksigner"),
                          "verify", "--print-certs", apk], capture_output=True, text=True)
    m = re.search(r"Signer #1 certificate SHA-256 digest: ([0-9a-f]+)", out.stdout)
    return m.group(1) if out.returncode == 0 and m else None


def install_tv_play_store(system):
    """The Play Store's Android TV build in place of the phone one, only if Google signed it (the
    same certificate as the stock Play Store), it runs on Android 7.1 and it has a TV launcher entry."""
    tv = os.path.join(ROM, "prebuilt", "Phonesky-TV.apk")
    if not os.path.exists(tv):
        print("  (no Phonesky-TV.apk: keeping the phone Play Store)")
        return
    stock = os.path.join(system, "priv-app", "Phonesky", "Phonesky.apk")
    want, got = apk_cert(stock), apk_cert(tv)
    if not want or want != got:
        sys.exit(f"Phonesky-TV.apk is not signed like the stock Play Store ({got} vs {want}): refusing it")
    badging = subprocess.run(["aapt", "dump", "badging", tv], capture_output=True, text=True).stdout
    sdk = int(re.search(r"sdkVersion:'(\d+)'", badging).group(1))
    if "package: name='com.android.vending'" not in badging or sdk > 25 or "leanback-launchable-activity" not in badging:
        sys.exit(f"Phonesky-TV.apk is not a Play Store for Android TV that runs on 7.1 (minSdk {sdk})")
    install_app(system, "priv-app/Phonesky", tv, extract_libs=True)
    print("  Play Store: Android TV build", re.search(r"versionName='([^']+)'", badging).group(1))


def strip_modules(system):
    before = after = 0
    for base, _, files in os.walk(system):
        for f in files:
            if not f.endswith(".ko"):
                continue
            p = os.path.join(base, f)
            before += os.path.getsize(p)
            attr = subprocess.run(["getfattr", "-n", "security.selinux", "--only-values", p], capture_output=True, text=True).stdout
            subprocess.run(["aarch64-linux-gnu-strip", "--strip-debug", p], check=True)
            with open(p, "rb") as fh:
                assert fh.read(4) == b"\x7fELF", p
            if attr:
                run("setfattr", "-n", "security.selinux", "-v", attr.strip("\x00"), p)
            after += os.path.getsize(p)
    print(f"kernel modules: {before // 2**20} MB -> {after // 2**20} MB", flush=True)


def edit_build_prop(path, changes):
    lines = open(path, encoding="utf-8").read().splitlines()
    for raw in open(changes, encoding="utf-8"):
        raw = raw.strip()
        if not raw or raw.startswith("#"):
            continue
        if raw.startswith("-"):
            key = raw[1:]
            lines = [l for l in lines if not l.startswith(key + "=")]
            continue
        key, value = raw.split("=", 1)
        # Every line with the key goes (stock sets some twice, and the last one wins); one stays.
        at = next((i for i, l in enumerate(lines) if l.startswith(key + "=")), None)
        lines = [l for l in lines if not l.startswith(key + "=")]
        lines.insert(at if at is not None else len(lines), f"{key}={value}")
    open(path, "w", encoding="utf-8").write("\n".join(lines) + "\n")


def fsck(raw):
    """e2fsck answers 1 when it fixed something minor (as the padding of a bitmap); only 2+ is trouble."""
    print("+ e2fsck -fy", raw, flush=True)
    rc = subprocess.run(["e2fsck", "-fy", raw]).returncode
    if rc > 1:
        sys.exit(f"e2fsck failed ({rc})")


def build_system(parts, work):
    sparse = os.path.join(parts, "system.PARTITION")
    raw = os.path.join(work, "system.raw")
    run("simg2img", sparse, raw)
    mnt = os.path.join(work, "mnt")
    os.makedirs(mnt, exist_ok=True)
    run("mount", "-o", "loop,rw", raw, mnt)
    try:
        # 1. Debloat.
        for line in open(os.path.join(ROM, "debloat.txt"), encoding="utf-8"):
            p = line.strip()
            if not p or p.startswith("#"):
                continue
            target = os.path.join(mnt, p)
            if os.path.isdir(target):
                shutil.rmtree(target)
            elif os.path.exists(target):
                os.remove(target)
            else:
                print("  (not found)", p)
        # 2. Apps.
        install_app(mnt, "priv-app/Aerial", AERIAL_APK, extract_libs=True)
        install_app(mnt, "priv-app/iTvSettings", TV_SETTINGS_APK, extract_libs=False)
        tt = fetch(TIZENTUBE_URL, os.path.join(work, "TizenTube.apk"))
        install_app(mnt, "app/TizenTube", tt, extract_libs=True)
        # Android TV Play Store, when rom/prebuilt/Phonesky-TV.apk is there (see install_tv_play_store).
        install_tv_play_store(mnt)
        # Stands in for YouTube (same package name) and hands voice search results to TizenTube.
        install_app(mnt, "app/YouTubeBridge", os.path.join(ROM, "prebuilt", "YouTubeBridge.apk"), extract_libs=False)
        # 3. Overlay files (TV features).
        overlay = os.path.join(ROM, "overlay", "system")
        for base, _, files in os.walk(overlay):
            for f in files:
                rel = os.path.relpath(os.path.join(base, f), overlay)
                dst = os.path.join(mnt, rel)
                os.makedirs(os.path.dirname(dst), exist_ok=True)
                shutil.copy(os.path.join(base, f), dst)
                label(dst)
        # 4. Boot animation.
        run(sys.executable, os.path.join(ROM, "tools", "make_bootanim.py"), work)
        shutil.copy(os.path.join(work, "bootanimation.zip"), os.path.join(mnt, "media", "bootanimation.zip"))
        label(os.path.join(mnt, "media", "bootanimation.zip"))
        # 5. Kernel modules without their debug information (152 MB -> 66 MB). The kernel loads code
        # and symbols, which stay; the modules are not signed, so nothing checks the bytes.
        strip_modules(mnt)
        # 6. build.prop.
        edit_build_prop(os.path.join(mnt, "build.prop"), os.path.join(ROM, "build.prop.txt"))
        run("df", "-h", mnt)
    finally:
        run("umount", mnt)
    fsck(raw)
    # Mounting here marks the journal (revoke records); a fresh journal leaves it as stock had it.
    run("tune2fs", "-O", "^has_journal", raw)
    run("tune2fs", "-j", raw)
    fsck(raw)
    # Blocks freed by the removals still hold old data, which the sparse image would carry
    # (1.6 GB for 0.9 GB of files). Zeroed, they become empty chunks.
    run("zerofree", raw)
    run("img2simg", raw, sparse)
    os.remove(raw)


def patch_boot(parts, work):
    """Magisk v27 on boot.img, as its boot_patch.sh does, with the memory tuning in overlay.d."""
    mw = os.path.join(work, "magisk")
    os.makedirs(mw, exist_ok=True)
    apk = fetch(MAGISK_URL, os.path.join(work, "Magisk.apk"))
    with zipfile.ZipFile(apk) as z:
        for src, dst in [("lib/x86_64/libmagiskboot.so", "magiskboot"), ("lib/armeabi-v7a/libmagiskinit.so", "magiskinit"),
                         ("lib/armeabi-v7a/libmagisk32.so", "magisk32"), ("assets/stub.apk", "stub.apk")]:
            with z.open(src) as s, open(os.path.join(mw, dst), "wb") as o:
                shutil.copyfileobj(s, o)
    os.chmod(os.path.join(mw, "magiskboot"), 0o755)
    boot = os.path.join(mw, "boot.img")
    shutil.copy(os.path.join(parts, "boot.PARTITION"), boot)
    env = dict(os.environ, KEEPVERITY="false", KEEPFORCEENCRYPT="false", PATCHVBMETAFLAG="false", RECOVERYMODE="false")
    mb = lambda *a: run("./magiskboot", *a, cwd=mw, env=env)
    mb("unpack", "boot.img")
    sha1 = subprocess.run(["./magiskboot", "sha1", "boot.img"], cwd=mw, capture_output=True, text=True).stdout.strip()
    mb("compress=xz", "magisk32", "magisk32.xz")
    mb("compress=xz", "stub.apk", "stub.xz")
    open(os.path.join(mw, "config"), "w").write(f"KEEPVERITY=false\nKEEPFORCEENCRYPT=false\nRECOVERYMODE=false\nSHA1={sha1}\n")
    shutil.copy(os.path.join(mw, "ramdisk.cpio"), os.path.join(mw, "ramdisk.cpio.orig"))
    ov = os.path.join(ROM, "magisk", "overlay.d")
    mb("cpio", "ramdisk.cpio",
       "add 0750 init magiskinit",
       "mkdir 0750 overlay.d",
       "mkdir 0750 overlay.d/sbin",
       "add 0644 overlay.d/sbin/magisk32.xz magisk32.xz",
       "add 0644 overlay.d/sbin/stub.xz stub.xz",
       f"add 0644 overlay.d/tv_tune.rc {os.path.join(ov, 'tv_tune.rc')}",
       f"add 0755 overlay.d/sbin/tv_tune.sh {os.path.join(ov, 'sbin', 'tv_tune.sh')}",
       "patch",
       "backup ramdisk.cpio.orig",
       "mkdir 000 .backup",
       "add 000 .backup/.magisk config")
    # Factory and vendor services that slow the boot or sit in memory (rom/boot_services.py).
    for rc in ("init.amlogic.rc", "init.amlogic.board.rc"):
        mb("cpio", "ramdisk.cpio", f"extract {rc} {rc}")
        run(sys.executable, os.path.join(ROM, "tools", "boot_services.py"), os.path.join(mw, rc))
        mb("cpio", "ramdisk.cpio", f"add 0750 {rc} {rc}")
    mb("repack", "boot.img")
    shutil.copy(os.path.join(mw, "new-boot.img"), os.path.join(parts, "boot.PARTITION"))


def refresh_verify(parts):
    for name in os.listdir(parts):
        if name.endswith(".VERIFY"):
            data = os.path.join(parts, name[:-len(".VERIFY")] + ".PARTITION")
            h = hashlib.sha1(open(data, "rb").read()).hexdigest()
            open(os.path.join(parts, name), "w").write("sha1sum " + h)


def main():
    if os.geteuid() != 0:
        sys.exit("run as root (the system image is mounted)")
    stock, out = sys.argv[1], sys.argv[2]
    for f in (AERIAL_APK, TV_SETTINGS_APK):
        if not os.path.exists(f):
            sys.exit("missing " + f)
    work = tempfile.mkdtemp(prefix="tvrom-", dir=os.path.dirname(os.path.abspath(out)))
    parts = os.path.join(work, "parts")
    run(sys.executable, UNPACK, "unpack", stock, parts)
    build_system(parts, work)
    patch_boot(parts, work)
    run(sys.executable, os.path.join(ROM, "tools", "logo_black.py"), os.path.join(parts, "logo.PARTITION"))
    refresh_verify(parts)
    run(sys.executable, UNPACK, "repack", parts, out)
    shutil.rmtree(work)
    print("done:", out, os.path.getsize(out) // (1024 * 1024), "MB")


if __name__ == "__main__":
    main()
