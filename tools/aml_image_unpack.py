#!/usr/bin/env python3
"""Unpack / repack Amlogic USB Burning Tool images (format v2)."""
import struct, sys, os, zlib, json

HDR = struct.Struct('<IIIQII36x')  # 0x40 bytes          # crc, ver, magic, size, align, n
ITEM = struct.Struct('<IIQQQ256s256sIHH24x')  # id, ftype, curoff, off, size, main, sub, verify, isbak, bakid
MAGIC = 0x27b51956

def unpack(img, out):
    os.makedirs(out, exist_ok=True)
    with open(img, 'rb') as f:
        crc, ver, magic, size, align, n = HDR.unpack(f.read(HDR.size))
        assert magic == MAGIC and ver == 2, (hex(magic), ver)
        items = [ITEM.unpack(f.read(ITEM.size)) for _ in range(n)]
        meta = {'align': align, 'items': []}
        for (iid, ft, cur, off, sz, main, sub, verify, isbak, bakid) in items:
            main = main.split(b'\0')[0].decode(); sub = sub.split(b'\0')[0].decode()
            name = f'{sub}.{main}'
            f.seek(off); data = f.read(sz)
            open(os.path.join(out, name), 'wb').write(data)
            meta['items'].append(dict(id=iid, file_type=ft, main=main, sub=sub, verify=verify,
                                      is_backup=isbak, backup_id=bakid, file=name, size=sz, offset=off))
            print(f'{iid:2d} {ft} {main:8s} {sub:20s} {sz:>12,d}')
        json.dump(meta, open(os.path.join(out, 'image.json'), 'w'), indent=1)

def repack(src, img):
    meta = json.load(open(os.path.join(src, 'image.json')))
    items, align = meta['items'], meta['align']
    n = len(items)
    off = HDR.size + ITEM.size * n
    # Every item the size it had: each goes back where it was, so the image differs from the
    # original only in the bytes that changed (packers differ in how they align small items).
    keep = all('offset' in it and os.path.getsize(os.path.join(src, it['file'])) == it['size'] for it in items)
    with open(img, 'wb') as o:
        o.write(b'\0' * off)
        table, offsets = [], []
        for i, it in enumerate(items):
            data = open(os.path.join(src, it['file']), 'rb').read()
            if it['is_backup']:  # shares data with another item
                ioff = offsets[it['backup_id']]
            elif keep:
                ioff = it['offset']
                o.seek(ioff); o.write(data); off = max(off, ioff + len(data))
            else:
                off = (off + align - 1) // align * align
                o.seek(off); o.write(data); ioff = off; off += len(data)
            offsets.append(ioff)
            table.append(ITEM.pack(i, it['file_type'], 0, ioff, len(data),
                                   it['main'].encode(), it['sub'].encode(), it['verify'],
                                   it['is_backup'], it['backup_id']))
        total = off
        o.seek(HDR.size); o.write(b''.join(table))
        o.seek(4); o.write(struct.pack('<IIQII36x', 2, MAGIC, total, align, n))
    # crc32 over everything after the first 4 bytes
    crc = 0
    with open(img, 'r+b') as o:
        o.seek(4)
        while chunk := o.read(1 << 22):
            crc = zlib.crc32(chunk, crc)
        o.seek(0); o.write(struct.pack('<I', crc ^ 0xffffffff))  # Amlogic: no final xor
    print(f'wrote {img} ({total:,d} bytes, {n} items)')

if __name__ == '__main__':
    {'unpack': unpack, 'repack': repack}[sys.argv[1]](sys.argv[2], sys.argv[3])
