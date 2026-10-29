#!/usr/bin/env python3
"""Draws Stormz TV's original neon-anime wallpapers (no outside artwork, so no licence questions).

Four night scenes in the style of sci-fi anime backgrounds: a neon skyline, a synthwave sunset, a
cherry-blossom night and a rainy neon street. 1920x1080 JPEGs, dark where the launcher puts its text.

Usage: python3 tools/neon_art.py <output folder>
Needs Pillow and numpy.
"""
import math
import os
import random
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

W, H = 1920, 1080
PINK = (255, 61, 203)
CYAN = (0, 229, 255)
VIOLET = (139, 92, 255)
AMBER = (255, 176, 32)


def gradient(stops):
    """Vertical gradient from (position 0..1, rgb) stops."""
    ys = np.linspace(0, 1, H)[:, None]
    img = np.zeros((H, W, 3), np.float32)
    for c in range(3):
        img[:, :, c] = np.interp(ys, [s[0] for s in stops], [s[1][c] for s in stops])
    return img


def to_img(a):
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), "RGB")


def glow_layer(draw_fn, blur, strength=1.0):
    """Draws onto a black layer, blurs it, returns it as float array to add (light)."""
    layer = Image.new("RGB", (W, H), (0, 0, 0))
    draw_fn(ImageDraw.Draw(layer))
    sharp = np.asarray(layer, np.float32)
    soft = np.asarray(layer.filter(ImageFilter.GaussianBlur(blur)), np.float32)
    return sharp + soft * 2.2 * strength


def add(base, light):
    return base + light


def stars(rng, base, n, top=0.0, bottom=0.6):
    for _ in range(n):
        x = rng.randrange(W)
        y = rng.randrange(int(H * top), int(H * bottom))
        b = rng.uniform(80, 255)
        r = 1 if rng.random() < 0.9 else 2
        base[max(0, y - r + 1):y + r, max(0, x - r + 1):x + r] += b * np.array([0.85, 0.9, 1.0])
    return base


def skyline(rng, draw, horizon, layers):
    """layers: list of (min_h, max_h, colour, window_prob)."""
    for min_h, max_h, colour, win in layers:
        x = -20
        while x < W:
            w = rng.randrange(40, 140)
            h = rng.randrange(min_h, max_h)
            top = horizon - h
            draw.rectangle([x, top, x + w, horizon + 400], fill=colour)
            if rng.random() < 0.25:  # antenna
                draw.line([x + w // 2, top, x + w // 2, top - rng.randrange(20, 60)], fill=colour, width=3)
            x += w + rng.randrange(0, 12)


def windows(rng, draw, horizon, count, height, colours):
    for _ in range(count):
        x = rng.randrange(W)
        y = rng.randrange(horizon - height, horizon)
        c = rng.choice(colours)
        draw.rectangle([x, y, x + 3, y + 5], fill=c)


def neon_skyline(rng):
    sky = gradient([(0, (8, 4, 28)), (0.45, (48, 14, 78)), (0.66, (150, 40, 130)), (0.72, (40, 12, 50)), (1, (6, 4, 16))])
    sky = stars(rng, sky, 500, 0, 0.5)
    img = to_img(sky)
    d = ImageDraw.Draw(img)
    horizon = int(H * 0.72)
    # moon
    moon = glow_layer(lambda g: g.ellipse([1380, 120, 1600, 340], fill=(255, 220, 245)), 40, 0.7)
    img = to_img(np.asarray(img, np.float32) + moon)
    d = ImageDraw.Draw(img)
    skyline(rng, d, horizon, [(160, 420, (34, 16, 60), 0)])
    skyline(rng, d, horizon + 20, [(90, 300, (16, 8, 32), 0)])
    windows(rng, d, horizon + 20, 650, 260, [(255, 210, 120), (120, 230, 255), (255, 120, 220)])
    # neon signs
    def signs(g):
        for _ in range(16):
            x = rng.randrange(60, W - 120)
            y = rng.randrange(horizon - 260, horizon - 40)
            c = rng.choice([PINK, CYAN, VIOLET, AMBER])
            if rng.random() < 0.5:
                g.rectangle([x, y, x + rng.randrange(40, 120), y + 10], fill=c)
            else:
                g.rectangle([x, y, x + 10, y + rng.randrange(40, 110)], fill=c)
    img = to_img(np.asarray(img, np.float32) + glow_layer(signs, 14, 0.9))
    # haze and water
    a = np.asarray(img, np.float32)
    a[horizon + 20:] *= 0.55
    fog = gradient([(0, (0, 0, 0)), (0.64, (0, 0, 0)), (0.72, (90, 30, 90)), (0.8, (0, 0, 0)), (1, (0, 0, 0))])
    return to_img(a + fog * 0.6)


def synth_sunset(rng):
    sky = gradient([(0, (6, 2, 24)), (0.35, (40, 8, 70)), (0.55, (200, 40, 120)), (0.62, (255, 120, 90)), (0.62001, (10, 2, 22)), (1, (4, 0, 14))])
    sky = stars(rng, sky, 300, 0, 0.4)
    img = to_img(sky)
    horizon = int(H * 0.62)
    # striped sun
    sun = Image.new("RGB", (W, H), (0, 0, 0))
    sd = ImageDraw.Draw(sun)
    cx, cy, r = W // 2, horizon - 40, 260
    for y in range(cy - r, horizon):
        t = (y - (cy - r)) / (2 * r)
        col = (255, int(220 - 160 * t), int(90 + 80 * t))
        half = int(math.sqrt(max(0, r * r - (y - cy) ** 2)))
        if y > cy - r * 0.35 and (y // 16) % 2 == 0:
            continue
        sd.line([cx - half, y, cx + half, y], fill=col)
    sun_a = np.asarray(sun, np.float32)
    soft = np.asarray(sun.filter(ImageFilter.GaussianBlur(50)), np.float32)
    img = to_img(np.asarray(img, np.float32) + sun_a + soft * 1.4)
    d = ImageDraw.Draw(img)
    # mountains
    for layer, (col, amp, base) in enumerate([((60, 14, 90), 160, horizon), ((26, 6, 44), 110, horizon)]):
        pts = [(0, base)]
        for x in range(0, W + 60, 60):
            pts.append((x, base - rng.randrange(20, amp)))
        pts.append((W, base))
        d.polygon(pts, fill=col)
    # neon grid floor
    def grid(g):
        vx = W // 2
        for i in range(-30, 31):
            g.line([vx + i * 12, horizon, vx + i * 260, H], fill=PINK, width=2)
        y, step = horizon + 4, 6
        while y < H:
            g.line([0, y, W, y], fill=PINK, width=2)
            step *= 1.28
            y += int(step)
    img = to_img(np.asarray(img, np.float32) + glow_layer(grid, 8, 0.8))
    a = np.asarray(img, np.float32)
    a[horizon:] *= np.linspace(0.75, 0.35, H - horizon)[:, None, None]
    return to_img(a)


def sakura_night(rng):
    sky = gradient([(0, (4, 6, 26)), (0.5, (22, 18, 70)), (0.8, (60, 30, 90)), (1, (10, 6, 22))])
    sky = stars(rng, sky, 700, 0, 0.7)
    a = sky + glow_layer(lambda g: g.ellipse([300, 140, 560, 400], fill=(255, 240, 250)), 60, 0.8)
    img = to_img(a)
    d = ImageDraw.Draw(img)
    ground = int(H * 0.82)
    # far hills and a gate silhouette
    d.polygon([(0, ground), (0, ground - 90), (500, ground - 160), (1100, ground - 70), (1920, ground - 140), (1920, ground)], fill=(14, 10, 34))
    gx = 1380
    d.rectangle([gx, ground - 330, gx + 22, ground], fill=(10, 6, 20))
    d.rectangle([gx + 260, ground - 330, gx + 282, ground], fill=(10, 6, 20))
    d.polygon([(gx - 60, ground - 350), (gx + 342, ground - 350), (gx + 320, ground - 320), (gx - 38, ground - 320)], fill=(10, 6, 20))
    d.rectangle([gx - 20, ground - 290, gx + 302, ground - 275], fill=(10, 6, 20))
    d.rectangle([0, ground, W, H], fill=(8, 5, 18))

    # cherry tree: branching from the left edge
    def branch(x, y, ang, length, width, depth, g, blossoms):
        if depth == 0 or length < 8:
            blossoms.append((x, y))
            return
        x2 = x + math.cos(ang) * length
        y2 = y - math.sin(ang) * length
        g.line([x, y, x2, y2], fill=(16, 8, 22), width=max(1, int(width)))
        for _ in range(2 if depth > 2 else 3):
            branch(x2, y2, ang + rng.uniform(-0.6, 0.6), length * rng.uniform(0.62, 0.8), width * 0.68, depth - 1, g, blossoms)
    blossoms = []
    branch(-40, ground + 40, math.radians(55), 260, 34, 8, d, blossoms)
    branch(-60, 300, math.radians(10), 220, 22, 7, d, blossoms)

    def petals(g):
        for (x, y) in blossoms:
            for _ in range(5):
                px, py = x + rng.uniform(-22, 22), y + rng.uniform(-22, 22)
                rr = rng.uniform(3, 7)
                c = rng.choice([(255, 150, 210), (255, 190, 230), PINK])
                g.ellipse([px - rr, py - rr, px + rr, py + rr], fill=c)
        for _ in range(160):  # falling
            px, py = rng.uniform(0, W), rng.uniform(0, H)
            g.ellipse([px - 3, py - 2, px + 3, py + 2], fill=(255, 170, 220))
    img = to_img(np.asarray(img, np.float32) + glow_layer(petals, 10, 0.45))
    # lanterns
    def lanterns(g):
        for i in range(5):
            x = 760 + i * 120
            g.ellipse([x, ground - 120, x + 30, ground - 80], fill=AMBER)
    return to_img(np.asarray(img, np.float32) + glow_layer(lanterns, 20, 0.8))


def cyber_rain(rng):
    sky = gradient([(0, (2, 4, 14)), (0.5, (10, 14, 40)), (1, (4, 2, 12))])
    img = to_img(sky)
    d = ImageDraw.Draw(img)
    vx, vy = W // 2, int(H * 0.46)
    # buildings on both sides in perspective
    for side in (-1, 1):
        for i in range(9):
            x0 = vx + side * (60 + i * i * 14)
            x1 = vx + side * (60 + (i + 1) * (i + 1) * 14)
            top = vy - 80 - i * 70
            bot = vy + 40 + i * 50
            col = (12 + i * 2, 10 + i, 30 + i * 3)
            d.polygon([(x0, top), (x1, top - 40), (x1, H), (x0, H)], fill=col)
    def signs(g):
        for side in (-1, 1):
            for i in range(1, 9):
                x = vx + side * (70 + i * i * 14)
                y = vy - 40 - i * 40 + rng.randrange(-30, 60)
                c = rng.choice([PINK, CYAN, VIOLET, AMBER])
                g.rectangle([min(x, x + side * 18 * i), y, max(x, x + side * 18 * i), y + 8 + i * 6], outline=c, width=3)
                g.rectangle([x - 4, y + 30, x + 4, y + 30 + 20 * i], fill=c)
        # street glow
        g.polygon([(vx - 10, vy + 40), (vx + 10, vy + 40), (vx + 700, H), (vx - 700, H)], fill=(30, 10, 40))
    img = to_img(np.asarray(img, np.float32) + glow_layer(signs, 18, 1.0))
    a = np.asarray(img, np.float32)
    # reflections on the wet street
    street = a[vy + 40:H].copy()
    refl = a[max(0, 2 * (vy + 40) - H):vy + 40][::-1]
    n = min(len(street), len(refl))
    a[vy + 40:vy + 40 + n] = street[:n] * 0.7 + refl[:n] * 0.35
    img = to_img(a)
    rain = Image.new("RGB", (W, H), (0, 0, 0))
    rd = ImageDraw.Draw(rain)
    for _ in range(1600):
        x, y = rng.uniform(0, W), rng.uniform(0, H)
        L = rng.uniform(20, 60)
        rd.line([x, y, x - L * 0.18, y + L], fill=(120, 160, 220), width=1)
    return to_img(np.asarray(img, np.float32) + np.asarray(rain, np.float32) * 0.35)


SCENES = [
    ("01_neon_skyline", neon_skyline),
    ("02_synth_sunset", synth_sunset),
    ("03_sakura_night", sakura_night),
    ("04_cyber_rain", cyber_rain),
]


def vignette(img):
    a = np.asarray(img, np.float32)
    ys, xs = np.mgrid[0:H, 0:W]
    d = np.sqrt(((xs - W / 2) / (W / 2)) ** 2 + ((ys - H / 2) / (H / 2)) ** 2)
    a *= np.clip(1.15 - 0.45 * d, 0.45, 1)[:, :, None]
    # darker bottom band for the dock and the shelf text
    a *= np.clip(np.linspace(1.05, 0.55, H), 0, 1)[:, None, None]
    return to_img(a)


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "neon"
    os.makedirs(out, exist_ok=True)
    for i, (name, fn) in enumerate(SCENES):
        rng = random.Random(1000 + i)
        img = vignette(fn(rng))
        path = os.path.join(out, name + ".jpg")
        img.save(path, quality=84, optimize=True, progressive=True)
        print(path, os.path.getsize(path) // 1024, "KB")


if __name__ == "__main__":
    main()
