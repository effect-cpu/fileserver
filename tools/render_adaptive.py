#!/usr/bin/env python3

import math
import os

from PIL import Image, ImageDraw

S = 4
BASE = 512
W = BASE * S


K = 0.86
CX, CY = 256.0, 309.0        

OUT = "/workspace/icon"
SIZES = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}


def fit(x, y):
    return (CX + (x - CX) * K, 256.0 + (y - CY) * K)


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
        pts.append((cx + rx * math.cos(t) * math.cos(pr) - ry * math.sin(t) * math.sin(pr),
                    cy + rx * math.cos(t) * math.sin(pr) + ry * math.sin(t) * math.cos(pr)))
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
    return [fit(x, y) for x, y in pts]


def build():
    os.makedirs(OUT, exist_ok=True)
    img = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    
    r = 17 * K
    cx, cy = fit(256, 228)
    d.ellipse([cx * S - r * S, cy * S - r * S, cx * S + r * S, cy * S + r * S],
              fill=(255, 255, 255, 255))
    for (rad, a, b, op) in ((52, (219.2, 191.2), (292.8, 191.2), 255),
                            (88, (193.8, 165.8), (318.2, 165.8), 184)):
        pts = arc_points(a[0], a[1], rad, rad, 0, 0, 1, b[0], b[1], 96)
        pts = [fit(x, y) for x, y in pts]
        pts = [(x * S, y * S) for x, y in pts]
        wdt = int(25 * K * S)
        d.line(pts, fill=(255, 255, 255, op), width=wdt, joint="curve")
        for (px, py) in (pts[0], pts[-1]):
            d.ellipse([px - wdt / 2.0, py - wdt / 2.0, px + wdt / 2.0, py + wdt / 2.0],
                      fill=(255, 255, 255, op))

    
    body = Image.new("L", (W, W), 0)
    ImageDraw.Draw(body).polygon([(x * S, y * S) for x, y in folder_points()], fill=255)
    img.paste(Image.new("RGBA", (W, W), (255, 255, 255, 255)), (0, 0), body)

    master = img.resize((BASE, BASE), Image.LANCZOS)
    master.save(os.path.join(OUT, "ic_launcher_foreground_512.png"))
    for name, size in SIZES.items():
        master.resize((size, size), Image.LANCZOS).save(
            os.path.join(OUT, "ic_launcher_foreground_%s.png" % name))
    print("前景层:", ", ".join("%s(%d)" % (k, v) for k, v in SIZES.items()))

    
    bbox = master.getbbox()
    print("内容外接框:", bbox, "→ 占画布 %.1f%% x %.1f%%"
          % (100.0 * (bbox[2] - bbox[0]) / BASE, 100.0 * (bbox[3] - bbox[1]) / BASE))


build()
