#!/usr/bin/env python3
"""ROM "TV": blacks out the boot logo ("bootup") in an Amlogic AML_RES logo partition, in place.

The picture keeps its size, format and position, so the partition's layout does not change; only
its pixels become black and the header checksum is computed again.
Usage: logo_black.py logo.PARTITION
"""
import struct, sys, zlib

path = sys.argv[1]
d = bytearray(open(path, "rb").read())
assert d[8:16] == b"AML_RES!", "not an AML_RES partition"
img_size, count = struct.unpack_from("<II", d, 16)
off = 0x40
for _ in range(count):
    magic, _hcrc, size, start, _end, nxt, _dcrc = struct.unpack_from("<7I", d, off)
    assert magic == 0x27051956, "bad item"
    name = bytes(d[off + 32:off + 64]).split(b"\0")[0].decode()
    if name == "bootup":
        assert d[start:start + 2] == b"BM", "bootup is not a BMP"
        pixels = struct.unpack_from("<I", d, start + 10)[0]  # offset of the pixel data
        d[start + pixels:start + size] = bytes(size - pixels)
        print(f"bootup: {size - pixels} bytes of pixels set to black")
        break
    if not nxt:
        sys.exit("no bootup picture found")
    off = nxt
# Same checksum as the burn image's: CRC32 of everything after it, without the final xor.
crc = zlib.crc32(bytes(d[4:img_size])) ^ 0xFFFFFFFF
struct.pack_into("<I", d, 0, crc)
open(path, "wb").write(d)
