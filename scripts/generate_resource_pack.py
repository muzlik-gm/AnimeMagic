#!/usr/bin/env python3
"""
AnimeMagic Resource Pack Generator v3 — Pixel-Art Edition
=========================================================

Each texture is procedurally drawn as a 16x16 pixel grid with serious attention
to iconography, shading, outlines, and highlights. No more solid color squares
with letters — every texture is a unique piece of pixel art.

Improvements over v2:
  - Pre-extended color palettes per theme (3-5 shades per hue for proper gradients)
  - Proper outlines on every icon (darker shade of the base color)
  - Highlights on top-left (light source convention)
  - Shadows on bottom-right
  - Anti-aliased curves via sub-pixel distance
  - Layered composition: background → midground → foreground → highlight
  - Thematic accuracy: each icon represents its spell/school visually

Output: resource_pack/build/AnimeMagicResourcePack.zip
"""

import json
import math
import shutil
import zipfile
from pathlib import Path
from PIL import Image

# === Paths ==================================================================

ROOT = Path(__file__).resolve().parent.parent
PACK_ROOT = ROOT / "resource_pack"
ASSETS_DIR = PACK_ROOT / "assets" / "anime_magic"
VANILLA_ASSETS = PACK_ROOT / "assets" / "minecraft"
BUILD_DIR = PACK_ROOT / "build"
PACK_PATH = BUILD_DIR / "AnimeMagicResourcePack.zip"

LOGICAL = 16
SCALE = 4  # final PNG is 64x64, nearest-neighbor upscaled from 16x16


# === PixelCanvas ============================================================
# A 16x16 canvas with primitives. Every coordinate is integer (pixel-perfect).
# Optional sub-pixel distance-based anti-aliasing for curves.

class PixelCanvas:
    def __init__(self, size: int = LOGICAL):
        self.size = size
        # 2D array of RGBA tuples; (0,0,0,0) = transparent
        self.pixels = [[(0, 0, 0, 0)] * size for _ in range(size)]

    def set(self, x: int, y: int, color):
        if 0 <= x < self.size and 0 <= y < self.size:
            if color is None: return
            # Alpha-blend onto existing pixel if new color has alpha < 255
            if len(color) == 4 and color[3] < 255 and self.pixels[y][x][3] > 0:
                self.pixels[y][x] = _blend(self.pixels[y][x], color)
            else:
                self.pixels[y][x] = color

    def fill(self, x: int, y: int, w: int, h: int, color):
        for dy in range(h):
            for dx in range(w):
                self.set(x + dx, y + dy, color)

    def fill_all(self, color):
        for y in range(self.size):
            for x in range(self.size):
                self.set(x, y, color)

    def clear(self):
        for y in range(self.size):
            for x in range(self.size):
                self.pixels[y][x] = (0, 0, 0, 0)

    def rect_outline(self, x: int, y: int, w: int, h: int, color):
        for dx in range(w):
            self.set(x + dx, y, color)
            self.set(x + dx, y + h - 1, color)
        for dy in range(h):
            self.set(x, y + dy, color)
            self.set(x + w - 1, y + dy, color)

    def circle(self, cx: float, cy: float, r: float, color, fill_color=None, aa: bool = False):
        """Filled circle with optional outline. Anti-aliasing optional."""
        r2 = r * r
        for y in range(self.size):
            for x in range(self.size):
                # Sample at pixel center
                d2 = (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2
                if d2 <= r2:
                    self.set(x, y, fill_color if fill_color is not None else color)
                elif d2 <= (r + 1) ** 2 and fill_color is not None:
                    # Outline ring
                    if d2 > (r - 1) ** 2 or not aa:
                        self.set(x, y, color)

    def line(self, x1, y1, x2, y2, color, thickness=1):
        dx = abs(x2 - x1); dy = abs(y2 - y1)
        sx = 1 if x1 < x2 else -1
        sy = 1 if y1 < y2 else -1
        err = dx - dy
        x, y = x1, y1
        while True:
            for ox in range(-(thickness // 2), thickness // 2 + 1):
                for oy in range(-(thickness // 2), thickness // 2 + 1):
                    self.set(x + ox, y + oy, color)
            if x == x2 and y == y2: break
            e2 = 2 * err
            if e2 > -dy:
                err -= dy; x += sx
            if e2 < dx:
                err += dx; y += sy

    def plot(self, points, color):
        """Plot a list of (x,y) tuples as single pixels."""
        for x, y in points:
            self.set(x, y, color)

    def mirror_h(self):
        for y in range(self.size):
            self.pixels[y] = list(reversed(self.pixels[y]))

    def to_image(self) -> Image.Image:
        img = Image.new("RGBA", (self.size, self.size), (0, 0, 0, 0))
        for y in range(self.size):
            for x in range(self.size):
                img.putpixel((x, y), self.pixels[y][x])
        return img.resize((self.size * SCALE, self.size * SCALE), Image.NEAREST)

    def save(self, path: Path):
        path.parent.mkdir(parents=True, exist_ok=True)
        self.to_image().save(path, "PNG")

    def stamp(self, other: 'PixelCanvas', ox: int = 0, oy: int = 0):
        """Composite another canvas onto this one at (ox, oy)."""
        for y in range(other.size):
            for x in range(other.size):
                col = other.pixels[y][x]
                if col[3] > 0:
                    self.set(ox + x, oy + y, col)


def _blend(bottom, top):
    """Alpha-composite top onto bottom. Both are (r,g,b,a) tuples."""
    br, bg, bb, ba = bottom
    tr, tg, tb, ta = top
    if ta == 0: return bottom
    if ba == 0:
        return (tr, tg, tb, ta)
    a = ta / 255.0
    ba_f = ba / 255.0
    out_a = a + ba_f * (1 - a)
    if out_a == 0: return (0, 0, 0, 0)
    out_r = int((tr * a + br * ba_f * (1 - a)) / out_a)
    out_g = int((tg * a + bg * ba_f * (1 - a)) / out_a)
    out_b = int((tb * a + bb * ba_f * (1 - a)) / out_a)
    return (out_r, out_g, out_b, int(out_a * 255))


def c(r, g, b, a=255):
    return (r, g, b, a)


def darken(color, amount=0.7):
    """Darken a color by a multiplier."""
    r, g, b, a = color
    return (int(r * amount), int(g * amount), int(b * amount), a)


def lighten(color, amount=0.3):
    """Lighten a color toward white by amount (0=none, 1=full white)."""
    r, g, b, a = color
    return (int(r + (255 - r) * amount), int(g + (255 - g) * amount),
            int(b + (255 - b) * amount), a)


# === Palettes ===============================================================
# Each spell gets a coherent palette with 4-5 shades for proper shading.

PAL = {
    # --- Fire (Naruto Fireball, Mushoku Saint Fire) ---
    "fire_white":  c(255, 250, 220),
    "fire_yellow": c(255, 220, 80),
    "fire_orange": c(255, 140, 30),
    "fire_red":    c(220, 60, 20),
    "fire_dark":   c(140, 30, 10),
    "fire_smoke":  c(80, 30, 15),

    # --- Lightning (Chidori) ---
    "light_core":  c(255, 255, 255),
    "light_white": c(220, 240, 255),
    "light_blue":  c(120, 200, 255),
    "light_deep":  c(40, 100, 200),
    "light_glow":  c(180, 220, 255, 200),

    # --- Rasengan (blue sphere) ---
    "rasengan_white": c(240, 250, 255),
    "rasengan_pale":  c(180, 230, 250),
    "rasengan_light": c(120, 200, 240),
    "rasengan_mid":   c(60, 140, 220),
    "rasengan_dark":  c(30, 80, 160),
    "rasengan_ring":  c(20, 50, 110),

    # --- Slime (Tensura) ---
    "slime_shine": c(250, 230, 255),
    "slime_pale":  c(200, 160, 230),
    "slime_light": c(150, 100, 200),
    "slime_mid":   c(110, 70, 170),
    "slime_dark":  c(70, 40, 130),
    "slime_outline": c(40, 20, 80),

    # --- Naruto leaf (orange/gold) ---
    "leaf_pale":   c(255, 220, 130),
    "leaf_light":  c(255, 180, 60),
    "leaf_mid":    c(240, 140, 30),
    "leaf_dark":   c(180, 80, 20),
    "leaf_outline": c(80, 30, 10),

    # --- Magic circle / Mushoku blue ---
    "rune_pale":   c(180, 230, 255),
    "rune_light":  c(110, 180, 240),
    "rune_mid":    c(50, 110, 200),
    "rune_dark":   c(20, 50, 120),
    "rune_outline": c(10, 25, 70),

    # --- Earth (Mushoku Emperor Earth) ---
    "earth_light": c(180, 150, 100),
    "earth_mid":   c(130, 100, 70),
    "earth_dark":  c(80, 60, 40),
    "earth_shadow": c(40, 30, 20),
    "earth_crack": c(15, 10, 5),

    # --- Water (Mushoku Saint Water) ---
    "water_shine": c(240, 250, 255),
    "water_pale":  c(180, 230, 255),
    "water_light": c(100, 180, 240),
    "water_mid":   c(50, 120, 200),
    "water_dark":  c(20, 60, 130),
    "water_outline": c(10, 30, 90),

    # --- Magicule / Tensura purple ---
    "magicule_shine": c(255, 200, 255),
    "magicule_pale":  c(220, 140, 255),
    "magicule_light": c(170, 80, 220),
    "magicule_mid":   c(120, 50, 180),
    "magicule_dark":  c(70, 25, 120),
    "magicule_outline": c(35, 10, 60),

    # --- Steel / Razor Edge ---
    "steel_shine": c(250, 252, 255),
    "steel_pale":  c(200, 215, 230),
    "steel_light": c(140, 160, 180),
    "steel_mid":   c(80, 100, 120),
    "steel_dark":  c(40, 55, 70),
    "steel_outline": c(15, 25, 35),
    "blood_red":   c(180, 20, 25),
    "blood_dark":  c(110, 10, 15),

    # --- Gluttony / dark maw ---
    "maw_pink":    c(220, 100, 130),
    "maw_red":     c(160, 30, 50),
    "maw_dark_red": c(90, 15, 30),
    "maw_black":   c(20, 5, 12),
    "tooth_pale":  c(250, 240, 220),
    "tooth_shadow": c(180, 165, 140),

    # --- Gold / Crown (Conqueror's Haki) ---
    "gold_shine":  c(255, 250, 200),
    "gold_pale":   c(255, 230, 110),
    "gold_light":  c(230, 180, 40),
    "gold_mid":    c(180, 130, 20),
    "gold_dark":   c(110, 75, 10),
    "gold_outline": c(60, 40, 5),
    "ruby_red":    c(220, 40, 50),
    "ruby_dark":   c(140, 15, 25),
    "sapphire_blue": c(50, 90, 220),
    "sapphire_dark": c(20, 40, 130),

    # --- Armament Haki (black) ---
    "arma_pale":   c(80, 80, 100),
    "arma_light":  c(50, 50, 70),
    "arma_mid":    c(25, 25, 40),
    "arma_dark":   c(10, 10, 22),
    "arma_outline": c(0, 0, 8),
    "arma_steam":  c(160, 160, 180, 180),

    # --- Gomu (red rubber) ---
    "gomu_shine":  c(255, 180, 180),
    "gomu_pale":   c(255, 110, 110),
    "gomu_light":  c(225, 60, 60),
    "gomu_mid":    c(170, 30, 30),
    "gomu_dark":   c(110, 15, 15),
    "gomu_outline": c(55, 5, 5),

    # --- Shadow clone ---
    "shadow_pale": c(90, 90, 120),
    "shadow_mid":  c(50, 50, 80),
    "shadow_dark": c(20, 20, 40),
    "shadow_outline": c(8, 8, 20),
    "eye_red":     c(255, 60, 60),
    "eye_glow":    c(255, 30, 30, 200),

    # --- Generic UI ---
    "white":       c(245, 245, 250),
    "black":       c(20, 20, 30),
    "ui_outline":  c(15, 15, 25),
    "ui_shadow":   c(15, 15, 25, 180),

    # --- Filter rainbow ---
    "rainbow_red":    c(220, 60, 60),
    "rainbow_orange": c(240, 140, 30),
    "rainbow_yellow": c(240, 220, 60),
    "rainbow_green":  c(80, 200, 80),
    "rainbow_blue":   c(60, 140, 220),
    "rainbow_purple": c(140, 80, 220),

    # --- Straw hat (One Piece) ---
    "straw_shine": c(255, 245, 180),
    "straw_pale":  c(250, 220, 110),
    "straw_light": c(220, 180, 60),
    "straw_mid":   c(170, 130, 30),
    "straw_dark":  c(110, 80, 15),
    "straw_outline": c(50, 35, 5),
    "band_red":    c(190, 40, 40),
    "band_dark":   c(120, 20, 20),

    # --- Skull ---
    "skull_pale":  c(245, 245, 235),
    "skull_light": c(210, 210, 195),
    "skull_mid":   c(160, 160, 145),
    "skull_shadow": c(110, 110, 95),
    "skull_outline": c(40, 40, 30),
}


# === Texture drawing functions =============================================
# Each function takes a PixelCanvas and draws on the 16x16 grid.
# Convention: light source is top-left. Outlines are darker shades.

# --- FILTER BUTTONS ---

def tex_filter_all(cv: PixelCanvas):
    """Grimoire book with 5-color star — represents the unified spellbook."""
    # Book background (dark brown cover)
    cv.fill(1, 2, 14, 13, PAL["straw_dark"])
    # Pages (cream) on right edge
    cv.fill(14, 3, 1, 11, PAL["skull_pale"])
    cv.fill(15, 3, 1, 11, PAL["skull_light"])
    # Cover highlight (top-left lighter)
    cv.fill(1, 2, 13, 1, PAL["straw_mid"])
    cv.fill(1, 2, 1, 13, PAL["straw_mid"])
    # Spine (gold)
    cv.fill(1, 2, 1, 13, PAL["gold_light"])
    cv.set(1, 2, PAL["gold_shine"])
    cv.set(1, 14, PAL["gold_dark"])
    # Bookmark ribbon (red)
    cv.fill(11, 2, 1, 4, PAL["band_red"])
    cv.fill(11, 6, 1, 2, PAL["band_dark"])

    # 5-pointed star in center of cover
    star_pts = []
    cx, cy = 7.5, 8.5
    for i in range(10):
        angle = -math.pi / 2 + i * math.pi / 5
        r = 4 if i % 2 == 0 else 1.5
        x = cx + r * math.cos(angle)
        y = cy + r * math.sin(angle)
        star_pts.append((int(round(x)), int(round(y))))

    # Star colors: 5 colors, one per point
    star_colors = [PAL["rainbow_red"], PAL["rainbow_orange"], PAL["rainbow_yellow"],
                   PAL["rainbow_green"], PAL["rainbow_blue"], PAL["rainbow_purple"]]

    # Draw star outline first
    for i in range(len(star_pts)):
        x1, y1 = star_pts[i]
        x2, y2 = star_pts[(i + 1) % len(star_pts)]
        cv.line(x1, y1, x2, y2, PAL["straw_outline"])

    # Fill star points with their colors (manual scan)
    for y in range(4, 13):
        for x in range(3, 13):
            # Distance to center determines if inside star
            d = math.sqrt((x - cx) ** 2 + (y - cy) ** 2)
            if d < 4.2:
                # Angle from center, choose color based on which point we're in
                angle = math.atan2(y - cy, x - cx) + math.pi / 2  # rotate so 0 = top
                if angle < 0: angle += 2 * math.pi
                point_idx = int((angle / (2 * math.pi)) * 5) % 5
                cv.set(x, y, star_colors[point_idx])

    # Center white dot
    cv.set(7, 8, PAL["white"])
    cv.set(8, 8, PAL["white"])

    # Outline
    cv.rect_outline(0, 1, 16, 15, PAL["straw_outline"])


def tex_filter_naruto(cv: PixelCanvas):
    """Hidden Leaf headband with engraved spiral."""
    # Headband background (navy blue cloth)
    cv.fill(0, 5, 16, 7, PAL["rune_dark"])
    cv.fill(0, 5, 16, 1, PAL["rune_mid"])  # top edge highlight
    cv.fill(0, 11, 16, 1, PAL["rune_outline"])  # bottom shadow

    # Cloth folds (diagonal lines)
    for i in range(0, 16, 4):
        cv.set(i, 6, PAL["rune_dark"])
        cv.set(i + 1, 7, PAL["rune_dark"])

    # Metal plate (silver rectangle in center)
    cv.fill(3, 6, 10, 5, PAL["steel_pale"])
    cv.fill(3, 6, 10, 1, PAL["steel_shine"])  # top highlight
    cv.fill(3, 10, 10, 1, PAL["steel_mid"])   # bottom shadow
    cv.set(3, 6, PAL["steel_shine"])
    cv.set(12, 6, PAL["steel_shine"])
    # Plate outline (rivets in corners)
    cv.set(4, 7, PAL["steel_dark"])
    cv.set(11, 7, PAL["steel_dark"])
    cv.set(4, 9, PAL["steel_dark"])
    cv.set(11, 9, PAL["steel_dark"])

    # Konoha spiral (engraved) — proper spiral shape
    spiral_pts = [
        # Outer ring
        (8, 7), (9, 7), (10, 7), (10, 8), (10, 9), (9, 10), (8, 10),
        (7, 10), (6, 9), (6, 8), (7, 7),
        # Inner spiral
        (8, 8), (9, 8), (9, 9), (8, 9), (7, 9), (7, 8),
        # Center dot
        (8, 8),
    ]
    for x, y in spiral_pts:
        cv.set(x, y, PAL["leaf_dark"])

    # Spiral tail (the distinctive comma-like extension)
    cv.set(11, 7, PAL["leaf_dark"])
    cv.set(11, 8, PAL["leaf_dark"])

    # Highlight on spiral (top-left)
    cv.set(8, 7, PAL["leaf_mid"])
    cv.set(9, 7, PAL["leaf_mid"])


def tex_filter_tensura(cv: PixelCanvas):
    """Rimuru slime — round blue-purple blob with antenna and face."""
    # Slime body — proper round blob shape (slightly squashed)
    # Outline first
    slime_outline_pts = [
        # Top
        (6, 3), (7, 3), (8, 3), (9, 3),
        (5, 4), (10, 4),
        (4, 5), (11, 5),
        (3, 6), (12, 6),
        (3, 7), (12, 7),
        (3, 8), (12, 8),
        (3, 9), (12, 9),
        (4, 10), (11, 10),
        (5, 11), (6, 12), (7, 12), (8, 12), (9, 12), (10, 11),
    ]
    # Body fill
    body_fill = [
        (6, 4), (7, 4), (8, 4), (9, 4),
        (5, 5), (6, 5), (7, 5), (8, 5), (9, 5), (10, 5),
        (4, 6), (5, 6), (6, 6), (7, 6), (8, 6), (9, 6), (10, 6), (11, 6),
        (4, 7), (5, 7), (6, 7), (7, 7), (8, 7), (9, 7), (10, 7), (11, 7),
        (4, 8), (5, 8), (6, 8), (7, 8), (8, 8), (9, 8), (10, 8), (11, 8),
        (4, 9), (5, 9), (6, 9), (7, 9), (8, 9), (9, 9), (10, 9), (11, 9),
        (5, 10), (6, 10), (7, 10), (8, 10), (9, 10), (10, 10),
        (6, 11), (7, 11), (8, 11), (9, 11),
    ]
    for x, y in body_fill:
        cv.set(x, y, PAL["slime_light"])

    # Apply outline
    for x, y in slime_outline_pts:
        cv.set(x, y, PAL["slime_outline"])

    # Antenna/horn on top (Rimuru's distinctive feature)
    cv.set(7, 2, PAL["slime_mid"])
    cv.set(7, 3, PAL["slime_mid"])
    cv.set(7, 4, PAL["slime_mid"])
    cv.set(8, 2, PAL["slime_outline"])

    # Top highlight (light source upper-left)
    cv.set(5, 5, PAL["slime_pale"])
    cv.set(6, 5, PAL["slime_pale"])
    cv.set(5, 6, PAL["slime_pale"])
    cv.set(6, 6, PAL["slime_shine"])
    cv.set(7, 6, PAL["slime_shine"])

    # Eyes (two black dots)
    cv.set(6, 8, PAL["black"])
    cv.set(9, 8, PAL["black"])
    cv.set(6, 9, PAL["black"])
    cv.set(9, 9, PAL["black"])

    # Mouth (small smile)
    cv.set(7, 11, PAL["black"])
    cv.set(8, 11, PAL["black"])

    # Bottom shadow (under the slime)
    cv.set(5, 13, PAL["slime_outline"])
    cv.set(6, 13, PAL["slime_outline"])
    cv.set(7, 13, PAL["slime_outline"])
    cv.set(8, 13, PAL["slime_outline"])
    cv.set(9, 13, PAL["slime_outline"])
    cv.set(10, 13, PAL["slime_outline"])


def tex_filter_mushoku(cv: PixelCanvas):
    """Magic circle with runes — concentric rings with cardinal markers."""
    # Outer dark background (transparent dark blue)
    cv.fill_all((0, 0, 0, 0))

    # Outer ring (dark blue, thick)
    outer_ring = [
        (3, 3), (4, 3), (5, 3), (6, 3), (7, 3), (8, 3), (9, 3), (10, 3), (11, 3), (12, 3),
        (2, 4), (2, 5), (2, 6), (2, 7), (2, 8), (2, 9), (2, 10), (2, 11),
        (13, 4), (13, 5), (13, 6), (13, 7), (13, 8), (13, 9), (13, 10), (13, 11),
        (3, 12), (4, 12), (5, 12), (6, 12), (7, 12), (8, 12), (9, 12), (10, 12), (11, 12), (12, 12),
        # Corners
        (3, 4), (12, 4), (3, 11), (12, 11),
    ]
    for x, y in outer_ring:
        cv.set(x, y, PAL["rune_outline"])

    # Mid ring (lighter blue)
    mid_ring = [
        (4, 4), (5, 4), (6, 4), (7, 4), (8, 4), (9, 4), (10, 4), (11, 4),
        (4, 5), (11, 5),
        (4, 6), (11, 6),
        (4, 7), (11, 7),
        (4, 8), (11, 8),
        (4, 9), (11, 9),
        (4, 10), (11, 10),
        (4, 11), (5, 11), (6, 11), (7, 11), (8, 11), (9, 11), (10, 11), (11, 11),
    ]
    for x, y in mid_ring:
        cv.set(x, y, PAL["rune_dark"])

    # Inner area (filled with mid-blue, slightly transparent)
    for y in range(5, 11):
        for x in range(5, 11):
            cv.set(x, y, (20, 50, 120, 180))

    # Inner ring (light blue)
    inner_ring = [
        (6, 5), (7, 5), (8, 5), (9, 5),
        (5, 6), (10, 6),
        (5, 7), (10, 7),
        (5, 8), (10, 8),
        (5, 9), (10, 9),
        (6, 10), (7, 10), (8, 10), (9, 10),
    ]
    for x, y in inner_ring:
        cv.set(x, y, PAL["rune_light"])

    # Center 8-pointed star
    cv.set(7, 7, PAL["rune_pale"])
    cv.set(8, 7, PAL["rune_pale"])
    cv.set(7, 8, PAL["rune_pale"])
    cv.set(8, 8, PAL["rune_pale"])
    # Star points (cardinal)
    cv.set(7, 6, PAL["rune_pale"])
    cv.set(8, 6, PAL["rune_pale"])
    cv.set(7, 9, PAL["rune_pale"])
    cv.set(8, 9, PAL["rune_pale"])
    cv.set(6, 7, PAL["rune_pale"])
    cv.set(6, 8, PAL["rune_pale"])
    cv.set(9, 7, PAL["rune_pale"])
    cv.set(9, 8, PAL["rune_pale"])
    # Diagonal points
    cv.set(6, 6, PAL["rune_light"])
    cv.set(9, 6, PAL["rune_light"])
    cv.set(6, 9, PAL["rune_light"])
    cv.set(9, 9, PAL["rune_light"])

    # Cardinal runes on outer ring (N, S, E, W markers)
    cv.set(7, 2, PAL["rune_pale"])
    cv.set(8, 2, PAL["rune_pale"])
    cv.set(7, 13, PAL["rune_pale"])
    cv.set(8, 13, PAL["rune_pale"])
    cv.set(1, 7, PAL["rune_pale"])
    cv.set(1, 8, PAL["rune_pale"])
    cv.set(14, 7, PAL["rune_pale"])
    cv.set(14, 8, PAL["rune_pale"])

    # Corner runes (4 diagonal)
    cv.set(3, 3, PAL["rune_light"])
    cv.set(12, 3, PAL["rune_light"])
    cv.set(3, 12, PAL["rune_light"])
    cv.set(12, 12, PAL["rune_light"])


def tex_filter_onepiece(cv: PixelCanvas):
    """Straw hat — Luffy's iconic hat with red band, side view."""
    # Brim (wide, slightly curved)
    cv.fill(1, 11, 14, 2, PAL["straw_dark"])
    cv.fill(2, 10, 12, 1, PAL["straw_mid"])
    cv.fill(1, 13, 14, 1, PAL["straw_outline"])

    # Brim highlights (straw weave texture — small lighter dashes)
    for x in range(2, 14, 2):
        cv.set(x, 11, PAL["straw_light"])
    for x in range(3, 13, 2):
        cv.set(x, 12, PAL["straw_mid"])

    # Crown (dome on top)
    cv.fill(5, 5, 6, 5, PAL["straw_pale"])
    cv.fill(4, 6, 8, 4, PAL["straw_pale"])
    # Crown top highlight
    cv.fill(5, 5, 6, 1, PAL["straw_shine"])
    cv.set(6, 5, PAL["straw_shine"])
    cv.set(7, 5, PAL["straw_shine"])
    # Crown shadow (right side)
    cv.fill(10, 6, 2, 4, PAL["straw_light"])
    cv.fill(11, 7, 1, 3, PAL["straw_mid"])
    # Crown outline
    cv.set(4, 6, PAL["straw_dark"])
    cv.set(4, 9, PAL["straw_dark"])
    cv.set(11, 6, PAL["straw_dark"])
    cv.set(11, 9, PAL["straw_dark"])

    # Red band around base of crown
    cv.fill(4, 9, 8, 1, PAL["band_red"])
    cv.set(4, 9, PAL["band_dark"])
    cv.set(11, 9, PAL["band_dark"])

    # Straw weave detail lines on crown (vertical)
    cv.set(6, 6, PAL["straw_light"])
    cv.set(7, 7, PAL["straw_light"])
    cv.set(8, 6, PAL["straw_light"])
    cv.set(9, 7, PAL["straw_light"])

    # Tiny shadow under hat
    cv.fill(2, 14, 12, 1, (0, 0, 0, 60))


# --- NAVIGATION ---

def tex_nav_next(cv: PixelCanvas):
    """Thick green arrow pointing right, with highlight and shadow."""
    # Build arrow shape with multiple shades for depth
    # Arrow body (right-pointing chevron, thickness 4)
    arrow_rows = [
        # (y, x_start, x_end, color)
        (3, 3, 4, PAL["green_dark"] if False else c(40, 120, 40)),
        (4, 4, 6, c(40, 120, 40)),
        (5, 5, 8, c(80, 200, 80)),
        (6, 6, 10, c(80, 220, 80)),
        (7, 7, 12, c(140, 240, 140)),
        (8, 6, 10, c(80, 220, 80)),
        (9, 5, 8, c(80, 200, 80)),
        (10, 4, 6, c(40, 120, 40)),
        (11, 3, 4, c(40, 120, 40)),
    ]
    for y, xs, xe, color in arrow_rows:
        for x in range(xs, xe + 1):
            cv.set(x, y, color)

    # Highlight on top edge
    cv.set(4, 4, c(180, 250, 180))
    cv.set(5, 5, c(180, 250, 180))
    cv.set(6, 6, c(180, 250, 180))
    cv.set(7, 7, c(220, 255, 220))

    # Shadow on bottom edge
    cv.set(8, 7, c(40, 140, 40))
    cv.set(9, 6, c(40, 140, 40))
    cv.set(10, 5, c(40, 140, 40))

    # Outline (dark green)
    outline_pts = [
        (3, 3), (4, 4), (5, 5), (6, 6), (7, 7),
        (3, 11), (4, 10), (5, 9), (6, 8), (7, 7),
        (12, 7), (11, 8), (10, 9), (9, 10), (8, 11),
    ]
    for x, y in outline_pts:
        cv.set(x, y, c(20, 60, 20))


def tex_nav_prev(cv: PixelCanvas):
    """Mirror of nav_next."""
    tex_nav_next(cv)
    cv.mirror_h()


def tex_nav_close(cv: PixelCanvas):
    """Bold red X with darker outline and small shadow."""
    # X outline (dark red)
    for i in range(11):
        cv.set(3 + i, 3 + i, c(120, 20, 20))
        cv.set(3 + i, 13 - i, c(120, 20, 20))
        cv.set(4 + i, 3 + i, c(120, 20, 20))
        cv.set(4 + i, 13 - i, c(120, 20, 20))

    # X body (bright red)
    for i in range(9):
        cv.set(4 + i, 4 + i, c(220, 60, 60))
        cv.set(4 + i, 12 - i, c(220, 60, 60))
        cv.set(5 + i, 4 + i, c(220, 60, 60))
        cv.set(5 + i, 12 - i, c(220, 60, 60))

    # Highlight (pale red on top-left arm)
    for i in range(5):
        cv.set(4 + i, 4 + i, c(255, 180, 180))

    # Center bright spot
    cv.set(8, 8, c(255, 220, 220))
    cv.set(7, 8, c(255, 220, 220))


# --- SCHOOL ICONS ---

def tex_icon_naruto(cv: PixelCanvas):
    """Konoha leaf symbol — stylized leaf with spiral inside."""
    # Leaf shape (triangle pointing up-right) - orange gradient
    leaf_pts = [
        # Outer outline
        (3, 13), (4, 12), (5, 11), (6, 10), (7, 9), (8, 8), (9, 7), (10, 6),
        (11, 5), (12, 4), (13, 3),
        (12, 3), (11, 4), (10, 5), (9, 6), (8, 7), (7, 8), (6, 9), (5, 10),
        (4, 11), (3, 12),
    ]
    # Fill leaf with orange gradient (lighter at top, darker at bottom)
    leaf_fill_light = [(12, 3), (11, 4), (10, 5), (9, 6), (8, 7), (12, 4), (11, 5), (10, 6), (9, 7), (11, 3)]
    leaf_fill_mid = [(8, 7), (7, 8), (6, 9), (5, 10), (10, 6), (9, 7), (8, 8), (7, 9), (6, 10), (5, 11)]
    leaf_fill_dark = [(4, 11), (3, 12), (4, 12), (5, 11), (4, 10), (3, 11)]

    for x, y in leaf_fill_light: cv.set(x, y, PAL["leaf_pale"])
    for x, y in leaf_fill_mid: cv.set(x, y, PAL["leaf_light"])
    for x, y in leaf_fill_dark: cv.set(x, y, PAL["leaf_mid"])

    # Outline
    for x, y in leaf_pts:
        cv.set(x, y, PAL["leaf_outline"])

    # Spiral inside (the Konoha symbol)
    spiral = [
        (9, 5), (10, 5), (10, 6),
        (10, 7), (9, 8), (8, 8), (7, 8),
        (6, 7), (6, 6), (7, 5), (8, 5),
    ]
    for x, y in spiral:
        cv.set(x, y, PAL["leaf_dark"])
    cv.set(8, 6, PAL["leaf_dark"])

    # Spiral tail (extending out)
    cv.set(11, 5, PAL["leaf_dark"])
    cv.set(12, 5, PAL["leaf_dark"])


def tex_icon_tensura(cv: PixelCanvas):
    """Slime silhouette — transparent background version."""
    tex_filter_tensura(cv)
    # No border (transparent bg)


def tex_icon_mushoku(cv: PixelCanvas):
    """Wizard staff with glowing blue crystal."""
    # Staff handle (vertical, brown with grain)
    for y in range(3, 14):
        cv.set(7, y, c(110, 70, 30))  # left edge
        cv.set(8, y, c(140, 95, 45))  # middle
        cv.set(9, y, c(80, 50, 20))   # right edge (shadow)

    # Wood grain highlights
    cv.set(8, 5, c(170, 120, 60))
    cv.set(8, 8, c(170, 120, 60))
    cv.set(8, 11, c(170, 120, 60))

    # Staff top — wrapping for crystal
    cv.set(6, 4, c(120, 80, 30))
    cv.set(10, 4, c(120, 80, 30))
    cv.set(6, 3, c(80, 50, 20))
    cv.set(10, 3, c(80, 50, 20))

    # Crystal orb on top (glowing blue, with shine)
    # Outer glow
    cv.set(6, 2, PAL["rune_light"])
    cv.set(10, 2, PAL["rune_light"])
    cv.set(6, 3, PAL["rune_mid"])
    cv.set(10, 3, PAL["rune_mid"])
    # Crystal body
    cv.set(7, 1, PAL["rune_light"])
    cv.set(8, 1, PAL["rune_pale"])
    cv.set(9, 1, PAL["rune_light"])
    cv.set(7, 2, PAL["rune_pale"])
    cv.set(8, 2, PAL["rune_pale"])
    cv.set(9, 2, PAL["rune_pale"])
    cv.set(7, 3, PAL["rune_light"])
    cv.set(8, 3, PAL["rune_pale"])
    cv.set(9, 3, PAL["rune_light"])
    # Crystal shine (top-left)
    cv.set(7, 1, PAL["white"])
    cv.set(7, 2, PAL["white"])

    # Glow particles floating around crystal
    cv.set(4, 2, PAL["rune_pale"])
    cv.set(12, 2, PAL["rune_pale"])
    cv.set(5, 5, PAL["rune_pale"])
    cv.set(11, 5, PAL["rune_pale"])
    cv.set(3, 3, PAL["rune_light"])
    cv.set(13, 3, PAL["rune_light"])

    # Staff pommel (bottom)
    cv.set(7, 14, c(180, 130, 20))
    cv.set(8, 14, c(220, 170, 40))
    cv.set(9, 14, c(180, 130, 20))


def tex_icon_onepiece(cv: PixelCanvas):
    """Skull and crossbones with straw hat brim."""
    # Crossbones (behind skull, diagonal)
    # Left bone
    cv.line(2, 13, 6, 9, PAL["skull_light"], 1)
    cv.line(2, 13, 6, 9, PAL["skull_mid"], 1)
    cv.set(2, 13, PAL["skull_pale"])
    cv.set(3, 12, PAL["skull_pale"])
    cv.set(4, 11, PAL["skull_pale"])
    cv.set(5, 10, PAL["skull_pale"])
    # Right bone
    cv.line(13, 13, 9, 9, PAL["skull_light"], 1)
    cv.line(13, 13, 9, 9, PAL["skull_mid"], 1)
    cv.set(13, 13, PAL["skull_pale"])
    cv.set(12, 12, PAL["skull_pale"])
    cv.set(11, 11, PAL["skull_pale"])
    cv.set(10, 10, PAL["skull_pale"])

    # Bone ends (knobs)
    cv.set(2, 13, PAL["skull_pale"])
    cv.set(1, 13, PAL["skull_mid"])
    cv.set(13, 13, PAL["skull_pale"])
    cv.set(14, 13, PAL["skull_mid"])

    # Skull (cranium — round)
    skull_pts = [
        (5, 6), (6, 5), (7, 5), (8, 5), (9, 5), (10, 6),
        (11, 6), (11, 7), (11, 8), (10, 9), (9, 9), (7, 9), (6, 9),
        (5, 8), (5, 7),
    ]
    for x, y in skull_pts:
        cv.set(x, y, PAL["skull_pale"])

    # Skull shadow (right side)
    cv.set(10, 6, PAL["skull_light"])
    cv.set(11, 7, PAL["skull_light"])
    cv.set(10, 8, PAL["skull_light"])

    # Skull outline
    outline_pts = [
        (5, 6), (6, 5), (7, 5), (8, 5), (9, 5), (10, 5), (11, 6),
        (11, 8), (10, 9), (6, 9), (5, 8),
    ]
    for x, y in outline_pts:
        cv.set(x, y, PAL["skull_outline"])

    # Eye sockets (large black)
    cv.set(6, 7, PAL["black"])
    cv.set(7, 7, PAL["black"])
    cv.set(6, 8, PAL["black"])
    cv.set(7, 8, PAL["black"])
    cv.set(9, 7, PAL["black"])
    cv.set(10, 7, PAL["black"])
    cv.set(9, 8, PAL["black"])
    cv.set(10, 8, PAL["black"])

    # Nose (triangle)
    cv.set(8, 9, PAL["black"])

    # Teeth (row of small vertical lines)
    for x in range(6, 11):
        cv.set(x, 10, PAL["skull_outline"])

    # Straw hat brim above skull (iconic Luffy look)
    cv.fill(3, 4, 10, 1, PAL["straw_dark"])
    cv.fill(4, 3, 8, 1, PAL["straw_mid"])
    cv.fill(5, 2, 6, 1, PAL["straw_pale"])
    cv.fill(5, 1, 6, 1, PAL["straw_shine"])
    # Red band on hat
    cv.fill(5, 3, 6, 1, PAL["band_red"])


# --- NARUTO SPELLS ---

def tex_spell_fireball(cv: PixelCanvas):
    """Classic anime fireball — layered flames with bright core."""
    # Outer flame wisps (asymmetric, trailing bottom-right)
    flame_trail = [
        (1, 8), (1, 9), (2, 10), (14, 6), (14, 7), (13, 8),
        (2, 11), (13, 9), (12, 11), (3, 12), (12, 12),
    ]
    for x, y in flame_trail:
        cv.set(x, y, PAL["fire_smoke"])

    # Outer flame ring (red-orange)
    outer = [
        (4, 2), (5, 2), (6, 2), (7, 2), (8, 2), (9, 2), (10, 2), (11, 2),
        (3, 3), (12, 3),
        (2, 4), (13, 4),
        (2, 5), (13, 5),
        (1, 6), (14, 6),
        (1, 7), (14, 7),
        (1, 8), (14, 8),
        (1, 9), (14, 9),
        (2, 10), (13, 10),
        (2, 11), (13, 11),
        (3, 12), (4, 13), (5, 13), (6, 13), (7, 13), (8, 13), (9, 13), (10, 13), (11, 12),
    ]
    for x, y in outer:
        cv.set(x, y, PAL["fire_red"])

    # Mid flame (orange)
    mid = [
        (4, 3), (5, 3), (6, 3), (7, 3), (8, 3), (9, 3), (10, 3), (11, 3),
        (3, 4), (12, 4),
        (3, 5), (12, 5),
        (2, 6), (13, 6),
        (2, 7), (13, 7),
        (2, 8), (13, 8),
        (2, 9), (13, 9),
        (3, 10), (12, 10),
        (3, 11), (4, 12), (5, 12), (6, 12), (7, 12), (8, 12), (9, 12), (10, 12), (11, 11),
    ]
    for x, y in mid:
        cv.set(x, y, PAL["fire_orange"])

    # Inner flame (yellow)
    inner = [
        (5, 4), (6, 4), (7, 4), (8, 4), (9, 4), (10, 4),
        (4, 5), (5, 5), (6, 5), (7, 5), (8, 5), (9, 5), (10, 5), (11, 5),
        (4, 6), (5, 6), (6, 6), (7, 6), (8, 6), (9, 6), (10, 6), (11, 6),
        (3, 7), (4, 7), (5, 7), (10, 7), (11, 7),
        (3, 8), (4, 8), (5, 8), (10, 8), (11, 8),
        (4, 9), (5, 9), (10, 9), (11, 9),
        (5, 10), (6, 10), (9, 10), (10, 10),
        (6, 11), (7, 11), (8, 11), (9, 11),
    ]
    for x, y in inner:
        cv.set(x, y, PAL["fire_yellow"])

    # Hot core (white-yellow)
    core = [
        (6, 5), (7, 5), (8, 5), (9, 5),
        (5, 6), (6, 6), (7, 6), (8, 6), (9, 6), (10, 6),
        (5, 7), (6, 7), (7, 7), (8, 7), (9, 7), (10, 7),
        (6, 8), (7, 8), (8, 8), (9, 8),
        (7, 9), (8, 9),
    ]
    for x, y in core:
        cv.set(x, y, PAL["fire_white"])

    # Brightest spot (top-left)
    cv.set(6, 6, PAL["white"])
    cv.set(7, 6, PAL["white"])
    cv.set(6, 7, PAL["white"])

    # Dark outline at edges (some)
    cv.set(1, 7, PAL["fire_dark"])
    cv.set(14, 8, PAL["fire_dark"])


def tex_spell_chidori(cv: PixelCanvas):
    """Lightning chirping bird — main bolt with branches and sparks."""
    # Main lightning bolt (Z-shape, bright white core)
    main_bolt = [
        (10, 1), (10, 2), (10, 3), (9, 4), (8, 5), (8, 6),
        (7, 7), (6, 8), (6, 9), (5, 10), (5, 11), (4, 12), (4, 13), (4, 14),
    ]
    for x, y in main_bolt:
        cv.set(x, y, PAL["light_core"])

    # Bolt thickness (add adjacent pixels)
    thick_pts = [
        (11, 1), (11, 2), (11, 3),
        (9, 5), (8, 7),
        (7, 8), (6, 10),
        (5, 12), (5, 13), (5, 14),
    ]
    for x, y in thick_pts:
        cv.set(x, y, PAL["light_white"])

    # Blue glow around bolt
    glow_pts = [
        (12, 2), (12, 3), (11, 4), (10, 4),
        (9, 6), (8, 8), (7, 9),
        (6, 11), (5, 11), (4, 11),
        (3, 12), (3, 13), (3, 14),
        (6, 14), (6, 13),
    ]
    for x, y in glow_pts:
        cv.set(x, y, PAL["light_blue"])

    # Branches (smaller bolts)
    branch1 = [(10, 4), (11, 5), (12, 6), (13, 7)]
    branch2 = [(8, 7), (9, 8), (10, 9), (11, 10)]
    branch3 = [(6, 9), (5, 8), (4, 7)]
    branch4 = [(5, 12), (6, 13), (7, 14)]
    for x, y in branch1 + branch2 + branch3 + branch4:
        cv.set(x, y, PAL["light_white"])

    # Deep blue outline at edges of branches
    deep_pts = [
        (13, 8), (12, 7), (11, 6),
        (11, 11), (10, 10),
        (4, 8), (3, 9),
        (7, 14), (7, 13),
    ]
    for x, y in deep_pts:
        cv.set(x, y, PAL["light_deep"])

    # Spark particles (random small dots around the bolt)
    sparks = [
        (13, 1), (14, 2), (2, 5), (1, 8), (14, 10),
        (2, 11), (13, 13), (1, 14), (12, 5), (3, 6),
    ]
    for x, y in sparks:
        cv.set(x, y, PAL["light_glow"])


def tex_spell_rasengan(cv: PixelCanvas):
    """Swirling blue sphere — concentric rings with spiral arms."""
    # Outer dark ring
    outer = [
        (4, 4), (5, 4), (6, 4), (7, 4), (8, 4), (9, 4), (10, 4), (11, 4),
        (3, 5), (12, 5),
        (3, 6), (12, 6),
        (3, 7), (12, 7),
        (3, 8), (12, 8),
        (3, 9), (12, 9),
        (3, 10), (12, 10),
        (3, 11), (12, 11),
        (4, 12), (5, 12), (6, 12), (7, 12), (8, 12), (9, 12), (10, 12), (11, 12),
        (4, 3), (5, 3), (6, 3), (7, 3), (8, 3), (9, 3), (10, 3), (11, 3),
        (4, 13), (5, 13), (6, 13), (7, 13), (8, 13), (9, 13), (10, 13), (11, 13),
    ]
    for x, y in outer:
        cv.set(x, y, PAL["rasengan_ring"])

    # Mid ring (medium blue)
    mid = [
        (5, 5), (6, 5), (7, 5), (8, 5), (9, 5), (10, 5),
        (4, 6), (11, 6),
        (4, 7), (11, 7),
        (4, 8), (11, 8),
        (4, 9), (11, 9),
        (4, 10), (11, 10),
        (5, 11), (6, 11), (7, 11), (8, 11), (9, 11), (10, 11),
    ]
    for x, y in mid:
        cv.set(x, y, PAL["rasengan_dark"])

    # Inner area (light blue fill)
    inner = [
        (5, 6), (6, 6), (7, 6), (8, 6), (9, 6), (10, 6),
        (5, 7), (6, 7), (7, 7), (8, 7), (9, 7), (10, 7),
        (5, 8), (6, 8), (7, 8), (8, 8), (9, 8), (10, 8),
        (5, 9), (6, 9), (7, 9), (8, 9), (9, 9), (10, 9),
        (6, 10), (7, 10), (8, 10), (9, 10),
    ]
    for x, y in inner:
        cv.set(x, y, PAL["rasengan_light"])

    # Spiral arms (curved lines spiraling inward)
    # Arm 1: top → right → bottom
    arm1 = [(7, 5), (8, 5), (9, 5), (10, 6), (10, 7), (9, 8), (8, 9), (7, 9)]
    # Arm 2: left → top → right
    arm2 = [(5, 7), (5, 8), (6, 9), (7, 10), (8, 10), (9, 10), (10, 9)]
    for x, y in arm1 + arm2:
        cv.set(x, y, PAL["rasengan_mid"])

    # Core (white-blue)
    core = [(7, 7), (8, 7), (7, 8), (8, 8)]
    for x, y in core:
        cv.set(x, y, PAL["rasengan_white"])

    # Brightest spot (top-left)
    cv.set(7, 7, PAL["white"])

    # Motion lines (4 cardinal)
    cv.set(8, 2, PAL["rasengan_pale"])
    cv.set(8, 14, PAL["rasengan_pale"])
    cv.set(2, 8, PAL["rasengan_pale"])
    cv.set(14, 8, PAL["rasengan_pale"])

    # Highlight on outer ring (top-left)
    cv.set(4, 4, PAL["rasengan_mid"])
    cv.set(5, 4, PAL["rasengan_mid"])
    cv.set(4, 5, PAL["rasengan_mid"])


def tex_spell_shadow_clone(cv: PixelCanvas):
    """Three shadowy ninja silhouettes with glowing red eyes."""
    # Smoke cloud at base
    smoke = [
        (1, 14), (2, 14), (3, 14), (4, 14), (5, 14), (6, 14), (7, 14),
        (8, 14), (9, 14), (10, 14), (11, 14), (12, 14), (13, 14), (14, 14),
        (1, 13), (2, 13), (13, 13), (14, 13),
    ]
    for x, y in smoke:
        cv.set(x, y, PAL["shadow_pale"])

    # Three silhouettes (back, mid, front) — different sizes for depth
    # BACK clone (smallest, darkest, leftmost)
    back = [
        # head
        (3, 7), (4, 7), (3, 8), (4, 8),
        # body
        (2, 9), (3, 9), (4, 9), (5, 9),
        (2, 10), (3, 10), (4, 10), (5, 10),
        (2, 11), (3, 11), (4, 11), (5, 11),
        (3, 12), (4, 12),
    ]
    for x, y in back:
        cv.set(x, y, PAL["shadow_dark"])
    # Eyes (red glow)
    cv.set(3, 7, PAL["eye_glow"])
    cv.set(4, 7, PAL["eye_glow"])

    # MID clone (medium, center)
    mid = [
        # head
        (7, 6), (8, 6), (7, 7), (8, 7), (7, 8), (8, 8),
        # body
        (6, 9), (7, 9), (8, 9), (9, 9),
        (6, 10), (7, 10), (8, 10), (9, 10),
        (6, 11), (7, 11), (8, 11), (9, 11),
        (6, 12), (7, 12), (8, 12), (9, 12),
        (7, 13), (8, 13),
    ]
    for x, y in mid:
        cv.set(x, y, PAL["shadow_mid"])
    # Eyes (red)
    cv.set(7, 7, PAL["eye_red"])
    cv.set(8, 7, PAL["eye_red"])

    # FRONT clone (largest, rightmost, has headband)
    front = [
        # head (bigger)
        (11, 5), (12, 5), (13, 5),
        (11, 6), (12, 6), (13, 6),
        (11, 7), (12, 7), (13, 7),
        # body
        (10, 8), (11, 8), (12, 8), (13, 8), (14, 8),
        (10, 9), (11, 9), (12, 9), (13, 9), (14, 9),
        (10, 10), (11, 10), (12, 10), (13, 10), (14, 10),
        (10, 11), (11, 11), (12, 11), (13, 11), (14, 11),
        (11, 12), (12, 12), (13, 12),
        (11, 13), (12, 13), (13, 13),
    ]
    for x, y in front:
        cv.set(x, y, PAL["shadow_dark"])
    # Outline
    outline_pts = [
        (11, 4), (12, 4), (13, 4),
        (10, 8), (14, 8), (15, 9), (10, 11), (14, 11),
        (10, 12), (15, 12),
    ]
    for x, y in outline_pts:
        cv.set(x, y, PAL["shadow_outline"])
    # Headband (silver plate)
    cv.set(11, 6, PAL["steel_pale"])
    cv.set(12, 6, PAL["steel_light"])
    cv.set(13, 6, PAL["steel_pale"])
    # Eyes (red glow)
    cv.set(11, 7, PAL["eye_red"])
    cv.set(13, 7, PAL["eye_red"])


# --- TENSURA SPELLS ---

def tex_spell_magicule_blade(cv: PixelCanvas):
    """Glowing purple katana-style sword."""
    # Blade (vertical, pointing up)
    # Outer edges (dark purple)
    for y in range(2, 12):
        cv.set(6, y, PAL["magicule_dark"])
        cv.set(9, y, PAL["magicule_dark"])
    # Blade body (mid purple)
    for y in range(2, 12):
        cv.set(7, y, PAL["magicule_mid"])
        cv.set(8, y, PAL["magicule_mid"])
    # Blade highlights (light purple)
    for y in range(2, 12):
        cv.set(7, y, PAL["magicule_light"])
    # Brightest edge (shine)
    for y in range(3, 11, 2):
        cv.set(7, y, PAL["magicule_shine"])

    # Blade tip (curved point)
    cv.set(7, 1, PAL["magicule_light"])
    cv.set(8, 1, PAL["magicule_light"])
    cv.set(7, 0, PAL["magicule_shine"])

    # Crossguard (gold)
    cv.fill(4, 11, 8, 1, PAL["gold_dark"])
    cv.fill(4, 12, 8, 1, PAL["gold_light"])
    cv.fill(4, 11, 8, 1, PAL["gold_mid"])
    cv.set(4, 11, PAL["gold_dark"])
    cv.set(11, 11, PAL["gold_dark"])
    cv.set(4, 12, PAL["gold_shine"])
    cv.set(5, 12, PAL["gold_shine"])
    cv.set(11, 12, PAL["gold_dark"])

    # Handle (dark brown wrapped)
    cv.fill(7, 13, 2, 3, c(80, 50, 20))
    # Wrap pattern (crisscross)
    cv.set(7, 13, c(40, 25, 10))
    cv.set(8, 13, c(40, 25, 10))
    cv.set(7, 14, c(140, 90, 40))
    cv.set(8, 14, c(140, 90, 40))
    cv.set(7, 15, c(40, 25, 10))

    # Pommel (gold)
    cv.set(7, 15, PAL["gold_light"])
    cv.set(8, 15, PAL["gold_shine"])

    # Magicule glow particles around blade
    cv.set(5, 4, PAL["magicule_shine"])
    cv.set(10, 6, PAL["magicule_shine"])
    cv.set(5, 8, PAL["magicule_pale"])
    cv.set(10, 9, PAL["magicule_pale"])
    cv.set(5, 10, PAL["magicule_pale"])


def tex_spell_gluttony(cv: PixelCanvas):
    """Monster maw with sharp teeth, red interior, dripping."""
    # Outer dark background (mouth)
    cv.fill(1, 3, 14, 11, PAL["maw_black"])

    # Mouth interior (red)
    cv.fill(2, 4, 12, 9, PAL["maw_dark_red"])
    cv.fill(3, 5, 10, 7, PAL["maw_red"])

    # Tongue (pink, in middle)
    cv.fill(7, 7, 2, 3, PAL["maw_pink"])
    cv.set(7, 9, PAL["maw_dark_red"])
    cv.set(8, 9, PAL["maw_dark_red"])

    # Upper teeth (triangles pointing down)
    for x in range(2, 14, 2):
        # Tooth base (top of mouth)
        cv.set(x, 3, PAL["tooth_pale"])
        cv.set(x + 1, 3, PAL["tooth_pale"])
        # Tooth tip (pointing down)
        cv.set(x, 4, PAL["tooth_pale"])
        cv.set(x + 1, 4, PAL["tooth_shadow"])
        cv.set(x, 5, PAL["tooth_shadow"])

    # Lower teeth (triangles pointing up)
    for x in range(2, 14, 2):
        cv.set(x, 13, PAL["tooth_pale"])
        cv.set(x + 1, 13, PAL["tooth_pale"])
        cv.set(x, 12, PAL["tooth_pale"])
        cv.set(x + 1, 12, PAL["tooth_shadow"])
        cv.set(x, 11, PAL["tooth_shadow"])

    # Mouth outline
    cv.rect_outline(1, 3, 14, 11, PAL["maw_black"])

    # Drips (saliva/blood) at bottom
    cv.set(5, 14, PAL["maw_red"])
    cv.set(10, 14, PAL["maw_red"])
    cv.set(5, 15, PAL["maw_dark_red"])

    # Eyes (glowing red, above the maw)
    cv.set(4, 1, PAL["eye_red"])
    cv.set(11, 1, PAL["eye_red"])
    cv.set(4, 2, PAL["eye_glow"])
    cv.set(11, 2, PAL["eye_glow"])


def tex_spell_razor_edge(cv: PixelCanvas):
    """Razor-sharp silver blade with motion lines and blood drip."""
    # Blade (very thin, vertical)
    # Sharp edge (left side, very bright)
    cv.set(7, 1, PAL["steel_shine"])
    for y in range(2, 13):
        cv.set(7, y, PAL["steel_shine"])

    # Blade body
    cv.set(8, 1, PAL["steel_pale"])
    for y in range(2, 13):
        cv.set(8, y, PAL["steel_pale"])

    # Back edge (dark)
    cv.set(9, 1, PAL["steel_mid"])
    for y in range(2, 13):
        cv.set(9, y, PAL["steel_mid"])

    # Shadow edge (rightmost)
    for y in range(2, 13):
        cv.set(10, y, PAL["steel_dark"])

    # Tip (sharp point at top)
    cv.set(8, 0, PAL["steel_shine"])
    cv.set(7, 0, PAL["steel_shine"])

    # Highlights along the blade (small bright spots)
    cv.set(7, 3, PAL["white"])
    cv.set(7, 6, PAL["white"])
    cv.set(7, 9, PAL["white"])

    # Crossguard (small, gold)
    cv.fill(6, 12, 4, 1, PAL["gold_dark"])
    cv.fill(6, 13, 4, 1, PAL["gold_light"])
    cv.set(6, 12, PAL["gold_outline"])
    cv.set(9, 12, PAL["gold_outline"])

    # Handle
    cv.fill(7, 14, 2, 2, c(80, 50, 10))
    cv.set(7, 14, c(40, 25, 5))
    cv.set(8, 14, c(40, 25, 5))

    # Blood drip at tip (top)
    cv.set(8, 0, PAL["blood_red"])
    cv.set(8, 1, PAL["blood_red"])
    cv.set(8, 2, PAL["blood_dark"])

    # Motion lines (small horizontal dashes indicating speed)
    cv.set(2, 4, PAL["steel_light"])
    cv.set(3, 4, PAL["steel_light"])
    cv.set(2, 7, PAL["steel_light"])
    cv.set(3, 7, PAL["steel_light"])
    cv.set(12, 5, PAL["steel_light"])
    cv.set(13, 5, PAL["steel_light"])
    cv.set(12, 9, PAL["steel_light"])
    cv.set(13, 9, PAL["steel_light"])

    # Speed sparkles
    cv.set(1, 6, PAL["white"])
    cv.set(14, 8, PAL["white"])


# --- MUSHOKU SPELLS ---

def tex_spell_saint_water(cv: PixelCanvas):
    """Water drop with concentric ripples."""
    # Outer ripple (widest)
    outer_ripple = [
        (2, 13), (3, 13), (4, 13), (5, 13), (6, 13), (7, 13),
        (8, 13), (9, 13), (10, 13), (11, 13), (12, 13), (13, 13),
        (1, 12), (14, 12),
        (1, 14), (14, 14),
    ]
    for x, y in outer_ripple:
        cv.set(x, y, PAL["water_dark"])

    # Mid ripple
    mid_ripple = [
        (3, 12), (4, 12), (5, 12), (6, 12), (7, 12),
        (8, 12), (9, 12), (10, 12), (11, 12), (12, 12),
        (2, 11), (13, 11),
    ]
    for x, y in mid_ripple:
        cv.set(x, y, PAL["water_mid"])

    # Inner ripple
    inner_ripple = [
        (4, 11), (5, 11), (6, 11), (7, 11), (8, 11), (9, 11), (10, 11), (11, 11),
        (3, 10), (12, 10),
    ]
    for x, y in inner_ripple:
        cv.set(x, y, PAL["water_light"])

    # The water drop (teardrop shape pointing up)
    # Drop outline
    drop_outline = [
        (8, 1),
        (7, 2), (9, 2),
        (6, 3), (10, 3),
        (5, 4), (11, 4),
        (5, 5), (11, 5),
        (4, 6), (12, 6),
        (4, 7), (12, 7),
        (4, 8), (12, 8),
        (4, 9), (12, 9),
        (5, 10), (6, 10), (7, 10), (8, 10), (9, 10), (10, 10), (11, 10),
    ]
    for x, y in drop_outline:
        cv.set(x, y, PAL["water_dark"])

    # Drop body (gradient: light at top, mid at bottom)
    drop_top = [(7, 2), (8, 2), (6, 3), (7, 3), (8, 3), (9, 3)]
    for x, y in drop_top:
        cv.set(x, y, PAL["water_pale"])

    drop_mid = [
        (6, 4), (7, 4), (8, 4), (9, 4), (10, 4),
        (5, 5), (6, 5), (7, 5), (8, 5), (9, 5), (10, 5), (11, 5),
    ]
    for x, y in drop_mid:
        cv.set(x, y, PAL["water_light"])

    drop_bot = [
        (5, 6), (6, 6), (7, 6), (8, 6), (9, 6), (10, 6), (11, 6),
        (5, 7), (6, 7), (7, 7), (8, 7), (9, 7), (10, 7), (11, 7),
        (5, 8), (6, 8), (7, 8), (8, 8), (9, 8), (10, 8), (11, 8),
        (5, 9), (6, 9), (7, 9), (8, 9), (9, 9), (10, 9), (11, 9),
    ]
    for x, y in drop_bot:
        cv.set(x, y, PAL["water_mid"])

    # Shine highlight (top-left of drop)
    cv.set(6, 4, PAL["water_shine"])
    cv.set(7, 4, PAL["water_shine"])
    cv.set(6, 5, PAL["water_shine"])
    cv.set(7, 5, PAL["water_shine"])
    cv.set(6, 6, PAL["water_pale"])
    cv.set(7, 6, PAL["water_pale"])

    # Tiny droplets splashing around
    cv.set(2, 5, PAL["water_pale"])
    cv.set(14, 6, PAL["water_pale"])
    cv.set(3, 8, PAL["water_pale"])
    cv.set(13, 9, PAL["water_pale"])


def tex_spell_saint_fire(cv: PixelCanvas):
    """Stylized flame — classic campfire shape with bright core."""
    # Outer flame (red-orange)
    outer = [
        # Top point
        (8, 0),
        # Upper slopes
        (7, 1), (9, 1),
        (6, 2), (10, 2),
        (5, 3), (11, 3),
        (5, 4), (11, 4),
        # Widest part
        (4, 5), (12, 5),
        (4, 6), (12, 6),
        (4, 7), (12, 7),
        (4, 8), (12, 8),
        (5, 9), (11, 9),
        (5, 10), (11, 10),
        (6, 11), (10, 11),
        (7, 12), (8, 12), (9, 12),
    ]
    for x, y in outer:
        cv.set(x, y, PAL["fire_red"])

    # Mid flame (orange)
    mid = [
        (8, 1),
        (7, 2), (8, 2), (9, 2),
        (6, 3), (7, 3), (8, 3), (9, 3), (10, 3),
        (6, 4), (7, 4), (8, 4), (9, 4), (10, 4),
        (5, 5), (6, 5), (10, 5), (11, 5),
        (5, 6), (11, 6),
        (5, 7), (11, 7),
        (5, 8), (11, 8),
        (6, 9), (10, 9),
        (6, 10), (10, 10),
        (7, 11), (8, 11), (9, 11),
    ]
    for x, y in mid:
        cv.set(x, y, PAL["fire_orange"])

    # Inner flame (yellow)
    inner = [
        (8, 2),
        (7, 3), (8, 3), (9, 3),
        (7, 4), (8, 4), (9, 4),
        (6, 5), (7, 5), (8, 5), (9, 5), (10, 5),
        (6, 6), (7, 6), (8, 6), (9, 6), (10, 6),
        (6, 7), (7, 7), (8, 7), (9, 7), (10, 7),
        (6, 8), (7, 8), (8, 8), (9, 8), (10, 8),
        (7, 9), (8, 9), (9, 9),
        (7, 10), (8, 10), (9, 10),
    ]
    for x, y in inner:
        cv.set(x, y, PAL["fire_yellow"])

    # Core (white-hot)
    core = [
        (7, 5), (8, 5), (9, 5),
        (7, 6), (8, 6), (9, 6),
        (7, 7), (8, 7), (9, 7),
        (8, 8),
    ]
    for x, y in core:
        cv.set(x, y, PAL["fire_white"])

    # Brightest spot
    cv.set(8, 6, PAL["white"])

    # Small flame wisps at top
    cv.set(6, 1, PAL["fire_orange"])
    cv.set(10, 1, PAL["fire_orange"])
    cv.set(5, 2, PAL["fire_red"])
    cv.set(11, 2, PAL["fire_red"])


def tex_spell_emperor_earth(cv: PixelCanvas):
    """Cracked earth block — top-down view with jagged cracks."""
    # Base earth (mid brown)
    cv.fill_all(PAL["earth_mid"])

    # Top-left lighter (sunlit)
    for i in range(16):
        cv.set(i, 0, PAL["earth_light"])
        cv.set(0, i, PAL["earth_light"])
    for i in range(1, 15):
        cv.set(i, 1, PAL["earth_light"])

    # Bottom-right darker (shadow)
    for i in range(16):
        cv.set(i, 15, PAL["earth_dark"])
        cv.set(15, i, PAL["earth_dark"])
    for i in range(2, 14):
        cv.set(i, 14, PAL["earth_dark"])
        cv.set(14, i, PAL["earth_dark"])

    # Add some texture (small dots of darker earth)
    texture_pts = [
        (3, 3), (5, 5), (8, 3), (11, 5), (3, 8), (10, 10),
        (5, 12), (12, 8), (2, 11), (13, 3), (7, 13), (4, 7),
    ]
    for x, y in texture_pts:
        cv.set(x, y, PAL["earth_dark"])
    # Small pebbles (lighter)
    pebble_pts = [
        (4, 4), (7, 6), (10, 4), (3, 10), (12, 11), (9, 12), (5, 2), (11, 2),
    ]
    for x, y in pebble_pts:
        cv.set(x, y, PAL["earth_light"])

    # Main crack (jagged line from top-left to bottom-right)
    crack1 = [(2, 1), (3, 2), (4, 3), (5, 4), (6, 5), (7, 6), (7, 7),
              (8, 8), (9, 9), (10, 10), (11, 11), (12, 12), (13, 13), (14, 14)]
    for x, y in crack1:
        cv.set(x, y, PAL["earth_crack"])
        cv.set(x + 1, y, PAL["earth_crack"])

    # Side crack 1 (from main crack going up-right)
    crack2 = [(7, 6), (8, 5), (9, 4), (10, 3), (11, 2)]
    for x, y in crack2:
        cv.set(x, y, PAL["earth_crack"])

    # Side crack 2 (from main crack going down-left)
    crack3 = [(8, 8), (7, 9), (6, 10), (5, 11), (4, 12)]
    for x, y in crack3:
        cv.set(x, y, PAL["earth_crack"])

    # Side crack 3 (from main crack going up-left)
    crack4 = [(5, 4), (4, 5), (3, 6), (2, 7), (1, 8)]
    for x, y in crack4:
        cv.set(x, y, PAL["earth_crack"])

    # Side crack 4 (from main crack going down-right)
    crack5 = [(10, 10), (11, 9), (12, 8), (13, 7), (14, 6)]
    for x, y in crack5:
        cv.set(x, y, PAL["earth_crack"])

    # Small debris pieces near cracks
    cv.set(8, 7, PAL["earth_shadow"])
    cv.set(9, 8, PAL["earth_shadow"])
    cv.set(6, 6, PAL["earth_shadow"])
    cv.set(11, 10, PAL["earth_shadow"])


# --- ONE PIECE SPELLS ---

def tex_spell_conquerors(cv: PixelCanvas):
    """Golden crown with aura and jewels."""
    # Aura (purple transparent behind crown)
    cv.circle(8, 9, 7, (80, 40, 120, 80), fill_color=(60, 30, 90, 60))
    # Aura sparkles
    cv.set(1, 6, (180, 100, 220, 200))
    cv.set(14, 6, (180, 100, 220, 200))
    cv.set(2, 11, (180, 100, 220, 200))
    cv.set(13, 11, (180, 100, 220, 200))
    cv.set(8, 1, (180, 100, 220, 200))

    # Crown base (gold band)
    cv.fill(3, 9, 10, 4, PAL["gold_mid"])
    # Top highlight
    cv.fill(3, 9, 10, 1, PAL["gold_pale"])
    cv.fill(3, 10, 10, 1, PAL["gold_light"])
    # Bottom shadow
    cv.fill(3, 12, 10, 1, PAL["gold_dark"])
    # Outline
    cv.rect_outline(3, 9, 10, 4, PAL["gold_outline"])

    # Crown spikes (3 pointed peaks)
    # Left spike
    cv.fill(3, 6, 2, 3, PAL["gold_light"])
    cv.set(3, 5, PAL["gold_pale"])
    cv.set(4, 5, PAL["gold_pale"])
    cv.set(3, 4, PAL["gold_shine"])
    # Center spike (tallest)
    cv.fill(7, 4, 2, 5, PAL["gold_light"])
    cv.set(7, 3, PAL["gold_pale"])
    cv.set(8, 3, PAL["gold_pale"])
    cv.set(7, 2, PAL["gold_shine"])
    cv.set(8, 2, PAL["gold_shine"])
    # Right spike
    cv.fill(11, 6, 2, 3, PAL["gold_light"])
    cv.set(11, 5, PAL["gold_pale"])
    cv.set(12, 5, PAL["gold_pale"])
    cv.set(12, 4, PAL["gold_shine"])

    # Spike outlines
    cv.set(2, 7, PAL["gold_outline"])
    cv.set(5, 7, PAL["gold_outline"])
    cv.set(6, 5, PAL["gold_outline"])
    cv.set(9, 5, PAL["gold_outline"])
    cv.set(10, 7, PAL["gold_outline"])
    cv.set(13, 7, PAL["gold_outline"])

    # Center jewel (red ruby)
    cv.set(7, 10, PAL["ruby_red"])
    cv.set(8, 10, PAL["ruby_red"])
    cv.set(7, 11, PAL["ruby_dark"])
    cv.set(8, 11, PAL["ruby_dark"])
    cv.set(7, 10, c(255, 100, 100))  # shine

    # Side jewels (blue sapphires)
    cv.set(4, 10, PAL["sapphire_blue"])
    cv.set(4, 11, PAL["sapphire_dark"])
    cv.set(11, 10, PAL["sapphire_blue"])
    cv.set(11, 11, PAL["sapphire_dark"])

    # Highlights on base
    cv.set(4, 9, PAL["gold_shine"])
    cv.set(9, 9, PAL["gold_shine"])


def tex_spell_armament(cv: PixelCanvas):
    """Hardened black fist (Busoshoku Haki) with steam wisps."""
    # Steam wisps (rising from top)
    cv.set(3, 1, PAL["arma_steam"])
    cv.set(4, 2, PAL["arma_steam"])
    cv.set(12, 1, PAL["arma_steam"])
    cv.set(11, 2, PAL["arma_steam"])
    cv.set(2, 4, PAL["arma_steam"])
    cv.set(13, 5, PAL["arma_steam"])
    cv.set(5, 0, PAL["arma_steam"])
    cv.set(10, 0, PAL["arma_steam"])

    # Fist outline
    outline = [
        (4, 3), (5, 2), (6, 2), (7, 2), (8, 2), (9, 2), (10, 2), (11, 3),
        (12, 4), (12, 5), (12, 6), (12, 7), (12, 8), (12, 9), (12, 10),
        (12, 11), (11, 12), (10, 13), (9, 13), (7, 13), (6, 13), (5, 12),
        (4, 11), (3, 10), (3, 9), (3, 8), (3, 7), (3, 6), (3, 5), (3, 4),
    ]
    for x, y in outline:
        cv.set(x, y, PAL["arma_outline"])

    # Fist body (dark base)
    body = [
        (4, 4), (5, 3), (6, 3), (7, 3), (8, 3), (9, 3), (10, 3), (11, 4),
        (4, 5), (5, 4), (6, 4), (7, 4), (8, 4), (9, 4), (10, 4), (11, 5),
        (4, 6), (5, 5), (6, 5), (7, 5), (8, 5), (9, 5), (10, 5), (11, 6),
        (4, 7), (5, 6), (6, 6), (7, 6), (8, 6), (9, 6), (10, 6), (11, 7),
        (4, 8), (5, 7), (5, 8), (6, 7), (7, 7), (8, 7), (9, 7), (10, 7), (10, 8), (11, 8),
        (4, 9), (5, 9), (6, 8), (6, 9), (7, 8), (8, 8), (9, 8), (9, 9), (10, 9), (11, 9),
        (4, 10), (5, 10), (6, 10), (7, 9), (7, 10), (8, 9), (8, 10), (9, 10), (10, 10), (11, 10),
        (4, 11), (5, 11), (6, 11), (7, 11), (8, 11), (9, 11), (10, 11), (11, 11),
        (5, 12), (6, 12), (7, 12), (8, 12), (9, 12), (10, 12),
    ]
    for x, y in body:
        cv.set(x, y, PAL["arma_mid"])

    # Knuckle highlights (top of fist)
    for x in [5, 7, 9, 11]:
        cv.set(x, 3, PAL["arma_light"])
        cv.set(x, 4, PAL["arma_pale"])

    # Knuckle divisions (lines between fingers)
    cv.set(6, 5, PAL["arma_dark"])
    cv.set(6, 6, PAL["arma_dark"])
    cv.set(8, 5, PAL["arma_dark"])
    cv.set(8, 6, PAL["arma_dark"])
    cv.set(10, 5, PAL["arma_dark"])
    cv.set(10, 6, PAL["arma_dark"])

    # Shadow on bottom of fist
    for x in range(5, 11):
        cv.set(x, 11, PAL["arma_dark"])
    for x in range(6, 10):
        cv.set(x, 12, PAL["arma_dark"])


def tex_spell_gomu_pistol(cv: PixelCanvas):
    """Stretching rubber fist — Gomu Gomu no Pistol."""
    # Shoulder (bottom-left, dark red)
    cv.fill(1, 12, 3, 3, PAL["gomu_dark"])
    cv.fill(2, 13, 2, 1, PAL["gomu_mid"])
    cv.set(2, 13, PAL["gomu_pale"])  # highlight

    # Stretched arm (diagonal line from shoulder to fist)
    arm_pts = [
        (3, 11), (4, 10), (5, 9), (6, 8), (7, 7), (8, 6), (9, 5), (10, 4),
        (4, 11), (5, 10), (6, 9), (7, 8), (8, 7), (9, 6), (10, 5), (11, 4),
    ]
    for x, y in arm_pts:
        cv.set(x, y, PAL["gomu_mid"])
    # Arm outline (dark)
    arm_outline = [
        (2, 12), (3, 11), (4, 10), (5, 9), (6, 8), (7, 7), (8, 6), (9, 5), (10, 4), (11, 3),
        (4, 12), (5, 11), (6, 10), (7, 9), (8, 8), (9, 7), (10, 6), (11, 5), (12, 4),
    ]
    for x, y in arm_outline:
        cv.set(x, y, PAL["gomu_dark"])

    # Fist (top-right, big red rubber ball)
    fist = [
        (11, 1), (12, 1), (13, 1), (14, 1),
        (10, 2), (11, 2), (12, 2), (13, 2), (14, 2),
        (10, 3), (11, 3), (12, 3), (13, 3), (14, 3),
        (10, 4), (11, 4), (12, 4), (13, 4), (14, 4),
        (11, 5), (12, 5), (13, 5),
    ]
    for x, y in fist:
        cv.set(x, y, PAL["gomu_light"])

    # Fist outline
    fist_outline = [
        (11, 0), (12, 0), (13, 0),
        (10, 1), (14, 1),
        (9, 2), (15, 2),
        (9, 3), (15, 3),
        (9, 4), (15, 4),
        (10, 5), (14, 5),
        (11, 6), (12, 6), (13, 6),
    ]
    for x, y in fist_outline:
        cv.set(x, y, PAL["gomu_outline"])

    # Knuckle highlights
    cv.set(11, 2, PAL["gomu_pale"])
    cv.set(13, 2, PAL["gomu_pale"])
    cv.set(12, 3, PAL["gomu_shine"])

    # Motion lines (showing stretch speed)
    cv.set(7, 9, PAL["gomu_pale"])
    cv.set(8, 8, PAL["gomu_pale"])
    cv.set(6, 10, PAL["gomu_pale"])
    cv.set(5, 11, PAL["gomu_pale"])

    # Impact star (top-right corner, where fist is going)
    cv.set(14, 0, PAL["white"])
    cv.set(15, 0, PAL["white"])
    cv.set(15, 1, PAL["white"])
    cv.set(13, 0, PAL["white"])


# --- 3D MODEL TEXTURES (wrap cube geometry) ---

def tex_model_magic_orb(cv: PixelCanvas):
    """Magic orb texture — wraps a cube to look like a glowing orb."""
    # Dark purple background (so cube edges blend)
    cv.fill_all(PAL["magicule_dark"])

    # Outer glow (large soft circle)
    cv.circle(8, 8, 7.5, (140, 60, 200, 100), fill_color=(100, 40, 170, 120))

    # Orb body
    cv.circle(8, 8, 5, PAL["magicule_outline"], fill_color=PAL["magicule_mid"])
    cv.circle(8, 8, 4, PAL["magicule_mid"], fill_color=PAL["magicule_light"])
    cv.circle(8, 8, 2.5, PAL["magicule_light"], fill_color=PAL["magicule_pale"])
    cv.circle(8, 8, 1, PAL["magicule_pale"], fill_color=PAL["white"])

    # Rotating rune marks (4 cardinal points)
    cv.set(8, 1, PAL["white"])
    cv.set(7, 1, PAL["magicule_shine"])
    cv.set(9, 1, PAL["magicule_shine"])
    cv.set(8, 14, PAL["white"])
    cv.set(7, 14, PAL["magicule_shine"])
    cv.set(9, 14, PAL["magicule_shine"])
    cv.set(1, 8, PAL["white"])
    cv.set(1, 7, PAL["magicule_shine"])
    cv.set(1, 9, PAL["magicule_shine"])
    cv.set(14, 8, PAL["white"])
    cv.set(14, 7, PAL["magicule_shine"])
    cv.set(14, 9, PAL["magicule_shine"])

    # Bright highlight (top-left)
    cv.set(6, 6, PAL["white"])
    cv.set(7, 6, PAL["white"])
    cv.set(6, 7, PAL["white"])

    # Edge darkening (for cube edge blending)
    for i in range(16):
        cv.set(i, 0, PAL["magicule_outline"])
        cv.set(i, 15, PAL["magicule_outline"])
        cv.set(0, i, PAL["magicule_outline"])
        cv.set(15, i, PAL["magicule_outline"])


def tex_model_chidori_blade(cv: PixelCanvas):
    """Lightning blade texture — wraps blade+hilt cube model."""
    # Background (transparent)
    # Blade (vertical, central)
    for y in range(1, 13):
        cv.set(7, y, PAL["light_white"])
        cv.set(8, y, PAL["light_white"])
        cv.set(6, y, PAL["light_blue"])
        cv.set(9, y, PAL["light_blue"])

    # Lightning crackle pattern (jagged lines along blade)
    crackle_y = [2, 4, 6, 8, 10]
    for y in crackle_y:
        cv.set(5, y, PAL["light_blue"])
        cv.set(10, y, PAL["light_blue"])
        cv.set(4, y + 1, PAL["light_deep"])
        cv.set(11, y + 1, PAL["light_deep"])

    # Bright core highlights
    cv.set(7, 3, PAL["light_core"])
    cv.set(8, 5, PAL["light_core"])
    cv.set(7, 7, PAL["light_core"])
    cv.set(8, 9, PAL["light_core"])
    cv.set(7, 11, PAL["light_core"])

    # Tip (sharp)
    cv.set(7, 0, PAL["light_core"])
    cv.set(8, 0, PAL["light_core"])
    cv.set(6, 1, PAL["light_white"])
    cv.set(9, 1, PAL["light_white"])

    # Hilt (crossguard, brown)
    cv.fill(5, 13, 6, 1, c(140, 90, 40))
    cv.fill(5, 14, 6, 1, c(80, 50, 20))
    cv.set(5, 13, PAL["gold_dark"])
    cv.set(10, 13, PAL["gold_dark"])
    cv.set(5, 14, c(40, 25, 10))
    cv.set(10, 14, c(40, 25, 10))

    # Glow particles floating beside blade
    cv.set(3, 4, PAL["light_glow"])
    cv.set(12, 5, PAL["light_glow"])
    cv.set(3, 8, PAL["light_glow"])
    cv.set(12, 9, PAL["light_glow"])
    cv.set(3, 11, PAL["light_glow"])
    cv.set(12, 2, PAL["light_glow"])

    # Top spark
    cv.set(7, 0, PAL["white"])
    cv.set(8, 0, PAL["white"])


def tex_model_rasengan_sphere(cv: PixelCanvas):
    """Rasengan sphere texture — wraps a cube to look like swirling sphere."""
    # Outer dark blue background (cube edges blend)
    cv.fill_all(PAL["rasengan_ring"])

    # Outer ring (darkest)
    cv.circle(8, 8, 7, PAL["rasengan_ring"], fill_color=PAL["rasengan_dark"])

    # Mid layer (medium blue)
    cv.circle(8, 8, 5.5, PAL["rasengan_dark"], fill_color=PAL["rasengan_mid"])

    # Inner layer (light blue)
    cv.circle(8, 8, 3.5, PAL["rasengan_mid"], fill_color=PAL["rasengan_light"])

    # Core (pale)
    cv.circle(8, 8, 1.5, PAL["rasengan_light"], fill_color=PAL["rasengan_pale"])
    cv.circle(8, 8, 0.5, PAL["rasengan_pale"], fill_color=PAL["white"])

    # Spiral arms (6 curved lines from outer to inner)
    for angle_deg in range(0, 360, 60):
        rad = math.radians(angle_deg)
        for r in range(2, 6):
            x = int(8 + r * math.cos(rad))
            y = int(8 + r * math.sin(rad))
            cv.set(x, y, PAL["rasengan_pale"])
        # Inner segment
        x = int(8 + 1 * math.cos(rad))
        y = int(8 + 1 * math.sin(rad))
        cv.set(x, y, PAL["white"])

    # Bright highlight (top-left)
    cv.set(6, 6, PAL["white"])
    cv.set(7, 6, PAL["white"])
    cv.set(6, 7, PAL["white"])

    # Cardinal motion lines
    cv.set(8, 0, PAL["rasengan_pale"])
    cv.set(8, 15, PAL["rasengan_pale"])
    cv.set(0, 8, PAL["rasengan_pale"])
    cv.set(15, 8, PAL["rasengan_pale"])


# === Texture registry =======================================================

TEXTURES = [
    # Filter buttons
    ("filter_all.png",            tex_filter_all),
    ("filter_naruto.png",         tex_filter_naruto),
    ("filter_tensura.png",        tex_filter_tensura),
    ("filter_mushoku.png",        tex_filter_mushoku),
    ("filter_onepiece.png",       tex_filter_onepiece),
    # Navigation
    ("nav_next.png",              tex_nav_next),
    ("nav_prev.png",              tex_nav_prev),
    ("nav_close.png",             tex_nav_close),
    # School icons
    ("icon_naruto.png",           tex_icon_naruto),
    ("icon_tensura.png",          tex_icon_tensura),
    ("icon_mushoku.png",          tex_icon_mushoku),
    ("icon_onepiece.png",         tex_icon_onepiece),
    # Naruto spells
    ("spell_fireball.png",        tex_spell_fireball),
    ("spell_chidori.png",         tex_spell_chidori),
    ("spell_rasengan.png",        tex_spell_rasengan),
    ("spell_shadow_clone.png",    tex_spell_shadow_clone),
    # Tensura spells
    ("spell_magicule_blade.png",  tex_spell_magicule_blade),
    ("spell_gluttony.png",        tex_spell_gluttony),
    ("spell_razor_edge.png",      tex_spell_razor_edge),
    # Mushoku spells
    ("spell_saint_water.png",     tex_spell_saint_water),
    ("spell_saint_fire.png",      tex_spell_saint_fire),
    ("spell_emperor_earth.png",   tex_spell_emperor_earth),
    # One Piece spells
    ("spell_conquerors.png",      tex_spell_conquerors),
    ("spell_armament.png",        tex_spell_armament),
    ("spell_gomu_pistol.png",     tex_spell_gomu_pistol),
    # 3D model textures
    ("paper_7001.png",            tex_model_magic_orb),
    ("prismarine_shard_7002.png", tex_model_chidori_blade),
    ("snowball_7003.png",         tex_model_rasengan_sphere),
]


# === Item model overrides ===================================================

OVERRIDES = {
    "paper": [
        (1001, "anime_magic:item/filter_all"),
        (1002, "anime_magic:item/filter_naruto"),
        (1003, "anime_magic:item/filter_tensura"),
        (1004, "anime_magic:item/filter_mushoku"),
        (1005, "anime_magic:item/filter_onepiece"),
        (1010, "anime_magic:item/nav_next"),
        (1011, "anime_magic:item/nav_prev"),
        (1012, "anime_magic:item/nav_close"),
        (7001, "anime_magic:item/paper_7001"),
    ],
    "nether_star": [
        (2001, "anime_magic:item/icon_naruto"),
        (2002, "anime_magic:item/icon_tensura"),
        (2003, "anime_magic:item/icon_mushoku"),
        (2004, "anime_magic:item/icon_onepiece"),
    ],
    "fire_charge": [(3001, "anime_magic:item/spell_fireball")],
    "prismarine_shard": [
        (3002, "anime_magic:item/spell_chidori"),
        (7002, "anime_magic:item/prismarine_shard_7002"),
    ],
    "snowball": [
        (3003, "anime_magic:item/spell_rasengan"),
        (7003, "anime_magic:item/snowball_7003"),
    ],
    "soul_sand": [(3004, "anime_magic:item/spell_shadow_clone")],
    "diamond_sword": [(4001, "anime_magic:item/spell_magicule_blade")],
    "black_dye": [(4002, "anime_magic:item/spell_gluttony")],
    "netherite_sword": [(4003, "anime_magic:item/spell_razor_edge")],
    "water_bucket": [(5001, "anime_magic:item/spell_saint_water")],
    "blaze_powder": [(5002, "anime_magic:item/spell_saint_fire")],
    "stone": [(5003, "anime_magic:item/spell_emperor_earth")],
    "purple_glazed_terracotta": [(6001, "anime_magic:item/spell_conquerors")],
    "obsidian": [(6002, "anime_magic:item/spell_armament")],
    "red_dye": [(6003, "anime_magic:item/spell_gomu_pistol")],
}


# === 3D cube-based models ===================================================

def make_3d_orb_model(texture_path):
    return {
        "textures": {"layer0": texture_path},
        "elements": [{
            "from": [4, 4, 4], "to": [12, 12, 12],
            "faces": {
                "north": {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "east":  {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "south": {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "west":  {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "up":    {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "down":  {"uv": [0, 0, 16, 16], "texture": "#layer0"}
            }
        }],
        "display": {
            "fixed":  {"translation": [0, 0, 0], "scale": [1, 1, 1]},
            "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]}
        }
    }


def make_3d_blade_model(texture_path):
    return {
        "textures": {"layer0": texture_path},
        "elements": [
            {
                "from": [7, 0, 7], "to": [9, 14, 9],
                "faces": {
                    "north": {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                    "south": {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                    "east":  {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                    "west":  {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                    "up":    {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                    "down":  {"uv": [0, 0, 16, 16], "texture": "#layer0"}
                }
            },
            {
                "from": [5, 13, 6], "to": [11, 15, 10],
                "faces": {
                    "north": {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                    "south": {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                    "east":  {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                    "west":  {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                    "up":    {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                    "down":  {"uv": [0, 0, 16, 16], "texture": "#layer0"}
                }
            }
        ]
    }


def make_3d_sphere_model(texture_path):
    return {
        "textures": {"layer0": texture_path},
        "elements": [{
            "from": [3, 3, 3], "to": [13, 13, 13],
            "faces": {
                "north": {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "east":  {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "south": {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "west":  {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "up":    {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "down":  {"uv": [0, 0, 16, 16], "texture": "#layer0"}
            }
        }]
    }


# === Main ===================================================================

def main():
    print(f"AnimeMagic Resource Pack Generator v3 — Pixel-Art Edition")
    print(f"=" * 60)
    BUILD_DIR.mkdir(parents=True, exist_ok=True)

    # Clean previous artifacts
    if ASSETS_DIR.exists():
        shutil.rmtree(ASSETS_DIR)
    if VANILLA_ASSETS.exists():
        shutil.rmtree(VANILLA_ASSETS)

    textures_dir = ASSETS_DIR / "textures" / "item"
    textures_dir.mkdir(parents=True, exist_ok=True)
    models_dir = ASSETS_DIR / "models" / "item"
    models_dir.mkdir(parents=True, exist_ok=True)
    vanilla_models_dir = VANILLA_ASSETS / "models" / "item"
    vanilla_models_dir.mkdir(parents=True, exist_ok=True)

    # 1) Generate all pixel-art textures
    print(f"\n[1/4] Generating {len(TEXTURES)} pixel-art textures (16x16, upscaled to 64x64)...")
    for fname, draw_fn in TEXTURES:
        cv = PixelCanvas()
        draw_fn(cv)
        cv.save(textures_dir / fname)
    print(f"      OK — {len(TEXTURES)} textures written to assets/anime_magic/textures/item/")

    # 2) Generate item model JSONs
    print(f"\n[2/4] Generating item model JSONs for {len(OVERRIDES)} vanilla items...")
    for vanilla_item, overrides in OVERRIDES.items():
        override_list = []
        for cmd, _ in overrides:
            custom_model_name = f"{vanilla_item}_{cmd}"
            custom_model_path = f"anime_magic:item/{custom_model_name}"

            if cmd == 7001:
                model_json = make_3d_orb_model(f"anime_magic:item/{custom_model_name}")
            elif cmd == 7002:
                model_json = make_3d_blade_model(f"anime_magic:item/{custom_model_name}")
            elif cmd == 7003:
                model_json = make_3d_sphere_model(f"anime_magic:item/{custom_model_name}")
            else:
                model_json = {
                    "parent": "minecraft:item/generated",
                    "textures": {"layer0": f"anime_magic:item/{custom_model_name}"}
                }

            (models_dir / f"{custom_model_name}.json").write_text(
                json.dumps(model_json, indent=2))

            override_list.append({
                "predicate": {"custom_model_data": cmd},
                "model": custom_model_path
            })

        vanilla_model = {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": f"minecraft:item/{vanilla_item}"},
            "overrides": override_list
        }
        (vanilla_models_dir / f"{vanilla_item}.json").write_text(
            json.dumps(vanilla_model, indent=2))

    total_overrides = sum(len(o) for o in OVERRIDES.values())
    print(f"      OK — {total_overrides} overrides across {len(OVERRIDES)} items")

    # 3) pack.mcmeta
    pack_mcmeta = {
        "pack": {
            "pack_format": 22,
            "description": "§dAnimeMagic §7v3 §8— §fhand-crafted pixel-art textures + 3D models"
        }
    }
    (PACK_ROOT / "pack.mcmeta").write_text(json.dumps(pack_mcmeta, indent=2))
    print(f"\n[3/4] Wrote pack.mcmeta (pack_format 22 = MC 1.20.2+)")

    # 4) Zip it
    print(f"\n[4/4] Packaging into {PACK_PATH.name}...")
    if PACK_PATH.exists():
        PACK_PATH.unlink()
    with zipfile.ZipFile(PACK_PATH, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as zf:
        zf.write(PACK_ROOT / "pack.mcmeta", "pack.mcmeta")
        for path in (PACK_ROOT / "assets").rglob("*"):
            if path.is_file():
                arc = path.relative_to(PACK_ROOT)
                zf.write(path, arc)

    # Stats
    file_count = sum(1 for _ in (PACK_ROOT / "assets").rglob("*") if _.is_file())
    size_kb = PACK_PATH.stat().st_size / 1024
    print(f"\n" + "=" * 60)
    print(f"DONE!")
    print(f"  Pack:        {PACK_PATH.relative_to(ROOT)}")
    print(f"  Size:        {size_kb:.1f} KB")
    print(f"  Textures:    {len(TEXTURES)} hand-crafted pixel-art PNGs")
    print(f"  Overrides:   {total_overrides} CustomModelData entries")
    print(f"  3D models:   3 (orb, blade, sphere) with cube geometry")
    print(f"  Total files: {file_count + 1} (assets + pack.mcmeta)")
    print(f"\n  To use: upload to static host, set config.yml:")
    print(f"    gui.resource-pack-url: https://your-host/AnimeMagicResourcePack.zip")


if __name__ == "__main__":
    main()
