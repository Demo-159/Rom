#!/usr/bin/env python3
"""ROM "TV": an original boot animation, calm and modern: four coloured dots on black that appear
one after another, then breathe until Android has started.

Writes bootanimation.zip (stored, as Android requires) and, with --preview, a GIF to look at.
Usage: make_bootanim.py OUT_DIR [--preview]
"""
import io, math, os, sys, zipfile
from PIL import Image, ImageDraw

W, H, FPS = 1280, 720, 30
COLORS = [(79, 139, 255), (255, 92, 108), (255, 193, 69), (61, 220, 151)]
R = 18          # dot radius at rest
GAP = 64        # distance between dot centres
SS = 3          # supersampling for smooth edges


def ease_out_back(t):
    c1, c3 = 1.70158, 2.70158
    t = min(max(t, 0.0), 1.0)
    return 1 + c3 * (t - 1) ** 3 + c1 * (t - 1) ** 2


def frame(radii, offsets):
    img = Image.new("RGB", (W * SS // 2, H * SS // 2), (0, 0, 0))  # drawn at 1.5x, then scaled down
    d = ImageDraw.Draw(img)
    s = SS / 2
    cx, cy = W / 2, H / 2
    for i, (col, r, dy) in enumerate(zip(COLORS, radii, offsets)):
        x = cx + (i - 1.5) * GAP
        y = cy + dy
        if r <= 0.2:
            continue
        d.ellipse([(x - r) * s, (y - r) * s, (x + r) * s, (y + r) * s], fill=col)
    return img.resize((W, H), Image.LANCZOS)


def part0():
    """The dots pop in, one after another (1.2 s)."""
    frames = []
    for f in range(int(FPS * 1.2)):
        t = f / FPS
        radii = [R * ease_out_back((t - i * 0.12) / 0.45) for i in range(4)]
        frames.append(frame(radii, [0] * 4))
    return frames


def part1():
    """A soft wave runs through the dots, looping (1.2 s)."""
    frames = []
    n = int(FPS * 1.2)
    for f in range(n):
        p = f / n * 2 * math.pi
        offs = [-8 * max(0.0, math.sin(p - i * 0.7)) for i in range(4)]
        radii = [R * (1 + 0.06 * max(0.0, math.sin(p - i * 0.7))) for i in range(4)]
        frames.append(frame(radii, offs))
    return frames


def jpeg(img):
    b = io.BytesIO()
    img.save(b, "JPEG", quality=88, optimize=True)
    return b.getvalue()


def main():
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    p0, p1 = part0(), part1()
    path = os.path.join(out, "bootanimation.zip")
    with zipfile.ZipFile(path, "w", zipfile.ZIP_STORED) as z:
        # Part 0 once; part 1 loops until boot completes, finishing its loop ("c").
        z.writestr("desc.txt", f"{W} {H} {FPS}\np 1 0 part0\nc 0 0 part1\n")
        for name, frames in (("part0", p0), ("part1", p1)):
            for i, im in enumerate(frames):
                z.writestr(f"{name}/{i:03d}.jpg", jpeg(im))
    print(path, os.path.getsize(path) // 1024, "KB")
    if "--preview" in sys.argv:
        seq = [im.resize((640, 360)) for im in p0 + p1 + p1]
        seq[0].save(os.path.join(out, "preview.gif"), save_all=True, append_images=seq[1:], duration=1000 // FPS, loop=0)


if __name__ == "__main__":
    main()
