#!/usr/bin/env python3

import math
import os

from PIL import Image, ImageDraw

S = 4                   
BASE = 512
W = BASE * S

C1 = (0x2E, 0x86, 0xEA)
C2 = (0x00, 0xA3, 0x8B) 
C_MID = (0x1E, 0x9B, 0xC9)

OUT_DIR = "/workspace/icon"
MIPMAPS = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def gradient(w, h):
    small = Image.new("RGB", (w, h))
    px = small.load()
    for y in range(h):
        for x in range(w):
            t = (x + y) / (2.0 * (max(1, w - 1)))
            if t < 0.55:
                c = lerp(C1, C_MID, t / 0.55)
            else:
                c = lerp(C_MID, C2, (t - 0.55) / 0.45)
            px[x, y] = c
    return small.resize((W, W), Image.BILINEAR)


def arc_points(x1, y1, rx, ry, phi, large, sweep, x2, y2, n=96):
    if x1 == x2 and y1 == y2:
        return []
    pr = math.radians(phi)
    dx2, dy2 = (x1 - x2) / 2.0, (y1 - y2) / 2.0
    x1p = math.cos(pr) * dx2 + math.sin(pr) * dy2
    y1p = -math.sin(pr) * dx2 + math.cos(pr) * dy2
    rx, ry = abs(rx), abs(ry)
    lam = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry)
    if lam > 1:
        rx *= math.sqrt(lam)
        ry *= math.sqrt(lam)
    num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p
    den = rx * rx * y1p * y1p + ry * ry * x1p * x1p
    coef = (1 if large != sweep else -1) * math.sqrt(max(0.0, num / den))
    cxp = coef * rx * y1p / ry
    cyp = -coef * ry * x1p / rx
    cx = math.cos(pr) * cxp - math.sin(pr) * cyp + (x1 + x2) / 2.0
    cy = math.sin(pr) * cxp + math.cos(pr) * cyp + (y1 + y2) / 2.0

    def ang(ux, uy, vx, vy):
        d = (ux * vx + uy * vy) / (math.hypot(ux, uy) * math.hypot(vx, vy))
        d = max(-1.0, min(1.0, d))
        a = math.acos(d)
        return -a if (ux * vy - uy * vx) < 0 else a

    t1 = ang(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry)
    dt = ang((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
    if not sweep and dt > 0:
        dt -= 2 * math.pi
    elif sweep and dt < 0:
        dt += 2 * math.pi
    pts = []
    for i in range(n + 1):
        t = t1 + dt * i / n
        x = cx + rx * math.cos(t) * math.cos(pr) - ry * math.sin(t) * math.sin(pr)
        y = cy + rx * math.cos(t) * math.sin(pr) + ry * math.sin(t) * math.cos(pr)
        pts.append((x, y))
    return pts


def folder_points():
    pts = []
    pts += arc_points(72, 314, 22, 22, 0, 0, 1, 94, 292, 32)      
    pts.append((200, 292))
    pts.append((228, 316))                                        
    pts.append((418, 316))
    pts += arc_points(418, 316, 22, 22, 0, 0, 1, 440, 338, 32)   
    pts.append((440, 450))
    pts += arc_points(440, 450, 22, 22, 0, 0, 1, 418, 472, 32)   
    pts.append((94, 472))
    pts += arc_points(94, 472, 22, 22, 0, 0, 1, 72, 450, 32)     
    pts.append((72, 314))
    return [(x * S, y * S) for x, y in pts]


def build():
    os.makedirs(OUT_DIR, exist_ok=True)
    from PIL import ImageChops

    def overlay(base, color, mask):
        lyr = Image.new("RGBA", (W, W), color)
        out = Image.new("RGBA", (W, W), (0, 0, 0, 0))
        out.paste(lyr, (0, 0), mask)
        return Image.alpha_composite(base, out)

    bg_mask = Image.new("L", (W, W), 0)
    ImageDraw.Draw(bg_mask).rounded_rectangle(
        [0, 0, W - 1, W - 1], radius=116 * S, fill=255)
    img = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    img.paste(gradient(W, W), (0, 0), bg_mask)

    hl = bg_mask.copy()
    ImageDraw.Draw(hl).rectangle([0, 148 * S, W, W + 1], fill=0)
    img = overlay(img, (255, 255, 255, 33), hl)

    wifi = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    d = ImageDraw.Draw(wifi)
    r = 17 * S
    d.ellipse([256 * S - r, 228 * S - r, 256 * S + r, 228 * S + r],
              fill=(255, 255, 255, 255))
    for (rad, a, b, op) in ((52, (219.2, 191.2), (292.8, 191.2), 255),
                            (88, (193.8, 165.8), (318.2, 165.8), 184)):
        pts = arc_points(a[0], a[1], rad, rad, 0, 0, 1, b[0], b[1], 96)
        pts = [(x * S, y * S) for x, y in pts]
        wdt = int(25 * S)
        d.line(pts, fill=(255, 255, 255, op), width=wdt, joint="curve")
        for (px, py) in (pts[0], pts[-1]):
            d.ellipse([px - wdt / 2.0, py - wdt / 2.0,
                       px + wdt / 2.0, py + wdt / 2.0], fill=(255, 255, 255, op))
    img = Image.alpha_composite(img, wifi)

    poly = folder_points()
    sh = Image.new("L", (W, W), 0)
    ImageDraw.Draw(sh).polygon([(x, y + 7 * S) for x, y in poly], fill=255)
    img = overlay(img, (0x0B, 0x35, 0x50, 41), sh)

    body = Image.new("L", (W, W), 0)
    ImageDraw.Draw(body).polygon(poly, fill=255)
    img = overlay(img, (255, 255, 255, 255), body)

    master = img.resize((BASE, BASE), Image.LANCZOS)
    master.save(os.path.join(OUT_DIR, "app_icon_512.png"))
    print("输出 app_icon_512.png")

    for name, size in MIPMAPS.items():
        master.resize((size, size), Image.LANCZOS).save(
            os.path.join(OUT_DIR, "ic_launcher_%s.png" % name))
    print("输出 mipmap:", ", ".join("%s(%d)" % (k, v) for k, v in MIPMAPS.items()))

    ascii_preview(master)


def ascii_preview(img):
    w = 34
    h = 34
    small = img.resize((w, h), Image.LANCZOS)
    px = small.load()
    ramp = " .:-=+*#%@"
    print("\n预览(34x34,白=@ 背景较暗):")
    for y in range(h):
        line = ""
        for x in range(w):
            r, g, b, a = px[x, y]
            if a < 40:
                line += " "
                continue
            lum = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0
            line += ramp[min(len(ramp) - 1, int(lum * (len(ramp) - 1) + 0.5))]
        print("  " + line)


build()
