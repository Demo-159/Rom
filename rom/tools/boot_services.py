#!/usr/bin/env python3
"""ROM "TV": switches off factory and vendor services in an init .rc file of the boot ramdisk.

- ddrtest: a factory memory test that loads a module and leaves a logger running on every boot
- imageserver: the daemon of Amlogic's image viewer (removed from the ROM)
- usbpm: a factory USB power test
- root_access / sud: the maker's old root, replaced by Magisk
- installxbmc / xbmc_addons: installers of the Kodi fork removed from the ROM
Services get "disabled"; "start" lines for them become comments. Usage: boot_services.py FILE.rc
"""
import re, sys

DISABLE = {"ddrtest", "imageserver", "usbpm", "root_access", "sud", "installxbmc", "xbmc_addons"}
path = sys.argv[1]
out, changed = [], []
service = None
for line in open(path, encoding="utf-8", errors="surrogateescape").read().split("\n"):
    m = re.match(r"\s*service\s+(\S+)", line)
    if m:
        service = m.group(1)
        out.append(line)
        if service in DISABLE:
            out.append("    disabled  # ROM \"TV\"")
            changed.append("disabled " + service)
        continue
    if re.match(r"\s*on\s", line):
        service = None
    if service in DISABLE and re.match(r"\s*disabled\s*$", line):
        continue  # already added above
    m = re.match(r"(\s*)start\s+(\S+)\s*$", line)
    if m and m.group(2) in DISABLE:
        out.append(m.group(1) + "# ROM \"TV\": " + line.strip())
        changed.append("no start " + m.group(2))
        continue
    out.append(line)
open(path, "w", encoding="utf-8", errors="surrogateescape").write("\n".join(out))
print(path, "->", ", ".join(changed) or "nothing to change")
