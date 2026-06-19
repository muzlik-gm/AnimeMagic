#!/usr/bin/env python3
"""
AnimeMagic Resource Pack Generator v1.0.0-alpha — Cinematic Edition
=========================================================

MAJOR UPGRADES over v3:
  - 32x32 logical pixel grid (was 16x16) — 4x more detail per texture
  - 128x128 final PNG output (was 64x64) — crisp at all zoom levels
  - Sub-pixel anti-aliasing for smooth curves
  - Multi-layer shading: base + highlight + shadow + rim light + ambient occlusion
  - Procedural noise/dithering for texture (fire, earth, metal surfaces)
  - 48 textures total (was 28) — includes all 20 new spells from v2.5

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

# 32x32 logical pixels, upscaled 4x to 128x128 for crisp pixel art
LOGICAL = 32
SCALE = 4
SIZE = LOGICAL * SCALE  # 128


# === PixelCanvas ============================================================
# A 32x32 logical-pixel canvas with advanced primitives.

class PixelCanvas:
    """32x32 logical canvas. Final PNG is 128x128 nearest-neighbor upscale."""

    def __init__(self, size: int = LOGICAL):
        self.size = size
        self.pixels = [[(0, 0, 0, 0)] * size for _ in range(size)]

    def set(self, x: int, y: int, color):
        if 0 <= x < self.size and 0 <= y < self.size and color is not None:
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

    def rect_outline(self, x: int, y: int, w: int, h: int, color):
        for dx in range(w):
            self.set(x + dx, y, color)
            self.set(x + dx, y + h - 1, color)
        for dy in range(h):
            self.set(x, y + dy, color)
            self.set(x + w - 1, y + dy, color)

    def circle(self, cx: float, cy: float, r: float, color, fill_color=None):
        """Filled circle with optional outline ring."""
        r2 = r * r
        for y in range(self.size):
            for x in range(self.size):
                d2 = (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2
                if d2 <= r2:
                    self.set(x, y, fill_color if fill_color is not None else color)
                elif d2 <= (r + 1) ** 2 and fill_color is not None:
                    self.set(x, y, color)

    def circle_aa(self, cx: float, cy: float, r: float, color):
        """Anti-aliased filled circle — smooth edges."""
        for y in range(self.size):
            for x in range(self.size):
                d = math.sqrt((x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2)
                if d <= r - 0.5:
                    self.set(x, y, color)
                elif d <= r + 0.5:
                    # Edge anti-aliasing
                    coverage = max(0.0, min(1.0, r + 0.5 - d))
                    blended = (color[0], color[1], color[2], int(color[3] * coverage) if len(color) == 4 else int(255 * coverage))
                    self.set(x, y, blended)

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
            if e2 > -dy: err -= dy; x += sx
            if e2 < dx: err += dx; y += sy

    def plot(self, points, color):
        for x, y in points:
            self.set(x, y, color)

    def mirror_h(self):
        for y in range(self.size):
            self.pixels[y] = list(reversed(self.pixels[y]))

    def stamp(self, other: 'PixelCanvas', ox: int = 0, oy: int = 0):
        for y in range(other.size):
            for x in range(other.size):
                col = other.pixels[y][x]
                if col[3] > 0:
                    self.set(ox + x, oy + y, col)

    def to_image(self) -> Image.Image:
        img = Image.new("RGBA", (self.size, self.size), (0, 0, 0, 0))
        for y in range(self.size):
            for x in range(self.size):
                img.putpixel((x, y), self.pixels[y][x])
        return img.resize((self.size * SCALE, self.size * SCALE), Image.NEAREST)

    def save(self, path: Path):
        path.parent.mkdir(parents=True, exist_ok=True)
        self.to_image().save(path, "PNG")


def _blend(bottom, top):
    br, bg, bb, ba = bottom
    tr, tg, tb, ta = top
    if ta == 0: return bottom
    if ba == 0: return (tr, tg, tb, ta)
    a = ta / 255.0
    ba_f = ba / 255.0
    out_a = a + ba_f * (1 - a)
    if out_a == 0: return (0, 0, 0, 0)
    out_r = int((tr * a + br * ba_f * (1 - a)) / out_a)
    out_g = int((tg * a + bg * ba_f * (1 - a)) / out_a)
    out_b = int((tb * a + bb * ba_f * (1 - a)) / out_a)
    return (out_r, out_g, out_b, int(out_a * 255))


def c(r, g, b, a=255): return (r, g, b, a)
def darken(col, amt=0.7): return (int(col[0]*amt), int(col[1]*amt), int(col[2]*amt), col[3])
def lighten(col, amt=0.3): return (int(col[0]+(255-col[0])*amt), int(col[1]+(255-col[1])*amt), int(col[2]+(255-col[2])*amt), col[3])


# === Palettes (expanded with 5+ shades per hue) =============================

PAL = {
    # Fire (5 shades + smoke)
    "fire_white":  c(255, 252, 230), "fire_pale":   c(255, 235, 130),
    "fire_yellow": c(255, 200, 60),  "fire_orange": c(255, 130, 30),
    "fire_red":    c(220, 60, 20),   "fire_dark":   c(140, 30, 10),
    "fire_smoke":  c(80, 30, 15),
    # Lightning
    "light_core":  c(255, 255, 255), "light_white": c(220, 240, 255),
    "light_pale":  c(170, 220, 255), "light_blue":  c(120, 200, 255),
    "light_deep":  c(40, 100, 200),  "light_glow":  c(180, 220, 255, 200),
    # Rasengan (blue sphere)
    "rasengan_white": c(245, 252, 255), "rasengan_pale": c(200, 235, 252),
    "rasengan_light": c(140, 210, 245), "rasengan_mid":   c(80, 160, 230),
    "rasengan_dark":  c(30, 90, 170),  "rasengan_ring":  c(15, 50, 110),
    # Slime
    "slime_shine": c(252, 232, 255), "slime_pale":  c(210, 170, 235),
    "slime_light": c(170, 120, 210), "slime_mid":   c(120, 80, 180),
    "slime_dark":  c(75, 45, 135),   "slime_outline": c(40, 20, 80),
    # Leaf (Naruto orange)
    "leaf_pale":   c(255, 225, 140), "leaf_light": c(255, 185, 65),
    "leaf_mid":    c(240, 145, 35),  "leaf_dark":  c(180, 85, 25),
    "leaf_outline": c(80, 30, 10),
    # Runes (Mushoku blue)
    "rune_pale":   c(190, 235, 255), "rune_light": c(120, 195, 245),
    "rune_mid":    c(55, 120, 205),  "rune_dark":  c(20, 55, 125),
    "rune_outline": c(10, 25, 70),
    # Earth
    "earth_light": c(185, 155, 105), "earth_mid":  c(135, 105, 75),
    "earth_dark":  c(85, 65, 45),    "earth_shadow": c(45, 35, 25),
    "earth_crack": c(15, 10, 5),
    # Water
    "water_shine": c(245, 252, 255), "water_pale": c(185, 235, 255),
    "water_light": c(105, 185, 245), "water_mid":  c(55, 125, 205),
    "water_dark":  c(20, 65, 135),   "water_outline": c(10, 30, 90),
    # Magicule (Tensura purple)
    "magicule_shine": c(255, 205, 255), "magicule_pale": c(225, 145, 255),
    "magicule_light": c(175, 85, 225), "magicule_mid":  c(125, 55, 185),
    "magicule_dark":  c(75, 30, 125), "magicule_outline": c(35, 10, 60),
    # Steel
    "steel_shine": c(252, 252, 255), "steel_pale":  c(205, 220, 235),
    "steel_light": c(145, 165, 185), "steel_mid":   c(85, 105, 125),
    "steel_dark":  c(45, 60, 75),    "steel_outline": c(15, 25, 35),
    "blood_red":   c(180, 25, 30),   "blood_dark":  c(110, 10, 15),
    # Maw (Gluttony)
    "maw_pink":    c(225, 105, 135), "maw_red":     c(165, 35, 55),
    "maw_dark_red": c(95, 18, 33),   "maw_black":   c(20, 5, 12),
    "tooth_pale":  c(252, 242, 222), "tooth_shadow": c(180, 165, 140),
    # Gold (Conqueror's crown)
    "gold_shine":  c(255, 252, 205), "gold_pale":   c(255, 232, 115),
    "gold_light":  c(232, 182, 45),  "gold_mid":    c(182, 132, 22),
    "gold_dark":   c(112, 77, 12),   "gold_outline": c(60, 40, 5),
    "ruby_red":    c(225, 45, 55),   "ruby_dark":   c(145, 18, 28),
    "sapphire_blue": c(55, 95, 225), "sapphire_dark": c(22, 42, 132),
    # Armament Haki (black)
    "arma_pale":   c(85, 85, 105),   "arma_light":  c(55, 55, 75),
    "arma_mid":    c(28, 28, 45),    "arma_dark":   c(12, 12, 25),
    "arma_outline": c(0, 0, 8),      "arma_steam":  c(170, 170, 190, 180),
    # Gomu (red rubber)
    "gomu_shine":  c(255, 185, 185), "gomu_pale":   c(255, 115, 115),
    "gomu_light":  c(225, 65, 65),   "gomu_mid":    c(170, 32, 32),
    "gomu_dark":   c(112, 17, 17),   "gomu_outline": c(55, 5, 5),
    # Shadow clone
    "shadow_pale": c(95, 95, 125),   "shadow_mid":  c(55, 55, 85),
    "shadow_dark": c(22, 22, 42),    "shadow_outline": c(8, 8, 20),
    "eye_red":     c(255, 65, 65),   "eye_glow":    c(255, 35, 35, 200),
    # Straw hat
    "straw_shine": c(255, 247, 185), "straw_pale":  c(252, 222, 115),
    "straw_light": c(225, 185, 65),  "straw_mid":   c(175, 135, 32),
    "straw_dark":  c(115, 85, 18),   "straw_outline": c(50, 35, 5),
    "band_red":    c(195, 45, 45),   "band_dark":   c(125, 22, 22),
    # Skull
    "skull_pale":  c(248, 248, 238), "skull_light": c(215, 215, 200),
    "skull_mid":   c(165, 165, 150), "skull_shadow": c(115, 115, 100),
    "skull_outline": c(40, 40, 30),
    # Rainbow
    "rainbow_red":    c(225, 65, 65), "rainbow_orange": c(245, 145, 35),
    "rainbow_yellow": c(245, 225, 65), "rainbow_green":  c(85, 205, 85),
    "rainbow_blue":   c(65, 145, 225), "rainbow_purple": c(145, 85, 225),
    # Generic UI
    "white":       c(248, 248, 252), "black":       c(20, 20, 30),
    "ui_outline":  c(15, 15, 25),
    "green_arrow": c(85, 225, 85),   "green_dark":  c(40, 125, 40),
    "red_x":       c(225, 65, 65),   "red_dark":    c(125, 22, 22),
    # NEW for v2.5 ultimates
    # Sage Mode (gold + orange aura)
    "sage_pale":   c(255, 245, 180), "sage_light":  c(255, 215, 90),
    "sage_mid":    c(245, 175, 40),  "sage_dark":   c(180, 115, 20),
    # Six Paths (white + gold)
    "sixpaths_pale": c(255, 255, 255), "sixpaths_light": c(245, 245, 220),
    "sixpaths_gold": c(235, 200, 80),
    # True Dragon (black + purple)
    "dragon_pale": c(180, 100, 200), "dragon_mid":  c(110, 50, 150),
    "dragon_dark": c(50, 20, 80),    "dragon_black": c(15, 5, 25),
    # Gravity (purple + black swirl)
    "gravity_pale": c(180, 120, 220), "gravity_mid": c(110, 60, 170),
    "gravity_dark": c(50, 20, 90),
    # Time Warp (pink + clock)
    "time_pale":   c(255, 200, 240), "time_mid":    c(220, 130, 200),
    "time_dark":   c(150, 60, 130),
    # Atomic Flare (white-hot + orange)
    "atomic_core": c(255, 255, 240), "atomic_pale": c(255, 240, 180),
    "atomic_yellow": c(255, 210, 60), "atomic_orange": c(255, 120, 20),
    # Storm (gray + cyan)
    "storm_pale":  c(220, 230, 240), "storm_mid":   c(140, 160, 180),
    "storm_dark":  c(70, 90, 110),
    # Gear Fourth (black + red)
    "gear4_pale":  c(80, 50, 50),    "gear4_mid":   c(40, 20, 20),
    "gear4_dark":  c(15, 5, 5),
    # Voice of All Things (white + rainbow)
    "voice_pale":  c(255, 255, 250), "voice_shine": c(255, 250, 200),
}


# === Filter button textures (32x32) =========================================

def tex_filter_all(cv: PixelCanvas):
    """Grimoire book with rainbow star — represents all schools."""
    # Book cover (dark brown)
    cv.fill(2, 4, 28, 26, PAL["straw_dark"])
    # Pages (cream) on right edge
    cv.fill(28, 5, 3, 24, PAL["skull_pale"])
    cv.fill(29, 5, 2, 24, PAL["skull_light"])
    # Cover highlights
    cv.fill(2, 4, 26, 2, PAL["straw_mid"])
    cv.fill(2, 4, 2, 26, PAL["straw_mid"])
    # Spine (gold)
    cv.fill(2, 4, 2, 26, PAL["gold_light"])
    cv.set(2, 4, PAL["gold_shine"]); cv.set(2, 29, PAL["gold_dark"])
    # Bookmark ribbon (red)
    cv.fill(22, 4, 3, 8, PAL["band_red"])
    cv.fill(22, 12, 3, 3, PAL["band_dark"])
    # 5-pointed star with rainbow points
    cx, cy = 15.5, 16.5
    star_colors = [PAL["rainbow_red"], PAL["rainbow_orange"], PAL["rainbow_yellow"],
                   PAL["rainbow_green"], PAL["rainbow_blue"]]
    # Draw star as 5 triangles from center
    for i in range(5):
        angle1 = -math.pi / 2 + i * 2 * math.pi / 5
        angle2 = angle1 + 2 * math.pi / 5
        # Fill triangle: center, point1, point2 (midpoint between star points)
        p1 = (cx + 8 * math.cos(angle1), cy + 8 * math.sin(angle1))
        p2 = (cx + 3 * math.cos(angle1 + math.pi / 5), cy + 3 * math.sin(angle1 + math.pi / 5))
        p3 = (cx + 8 * math.cos(angle2), cy + 8 * math.sin(angle2))
        # Use barycentric fill
        min_x = int(min(cx, p1[0], p2[0], p3[0])) - 1
        max_x = int(max(cx, p1[0], p2[0], p3[0])) + 2
        min_y = int(min(cy, p1[1], p2[1], p3[1])) - 1
        max_y = int(max(cy, p1[1], p2[1], p3[1])) + 2
        for y in range(max(0, min_y), min(32, max_y)):
            for x in range(max(0, min_x), min(32, max_x)):
                if point_in_triangle(x + 0.5, y + 0.5, cx, cy, p1[0], p1[1], p2[0], p2[1]):
                    cv.set(x, y, star_colors[i])
                if point_in_triangle(x + 0.5, y + 0.5, cx, cy, p2[0], p2[1], p3[0], p3[1]):
                    cv.set(x, y, star_colors[i])
    # Center white dot
    cv.circle(cx, cy, 2, PAL["white"], fill_color=PAL["white"])
    # Outline
    cv.rect_outline(1, 3, 30, 28, PAL["straw_outline"])


def point_in_triangle(px, py, x1, y1, x2, y2, x3, y3):
    d1 = (px - x2) * (y1 - y2) - (x1 - x2) * (py - y2)
    d2 = (px - x3) * (y2 - y3) - (x2 - x3) * (py - y3)
    d3 = (px - x1) * (y3 - y1) - (x3 - x1) * (py - y1)
    has_neg = (d1 < 0) or (d2 < 0) or (d3 < 0)
    has_pos = (d1 > 0) or (d2 > 0) or (d3 > 0)
    return not (has_neg and has_pos)


def tex_filter_naruto(cv: PixelCanvas):
    """Hidden Leaf headband with engraved spiral — 32x32."""
    # Headband cloth (navy blue)
    cv.fill(0, 10, 32, 14, PAL["rune_dark"])
    cv.fill(0, 10, 32, 2, PAL["rune_mid"])  # top edge
    cv.fill(0, 22, 32, 2, PAL["rune_outline"])  # bottom shadow
    # Cloth folds
    for i in range(0, 32, 6):
        cv.line(i, 12, i + 2, 14, PAL["rune_dark"], 1)
    # Metal plate (silver, centered)
    cv.fill(6, 12, 20, 10, PAL["steel_pale"])
    cv.fill(6, 12, 20, 2, PAL["steel_shine"])  # top highlight
    cv.fill(6, 20, 20, 2, PAL["steel_mid"])    # bottom shadow
    # Plate corners (rivets)
    cv.set(7, 13, PAL["steel_dark"]); cv.set(24, 13, PAL["steel_dark"])
    cv.set(7, 20, PAL["steel_dark"]); cv.set(24, 20, PAL["steel_dark"])
    # Konoha spiral (engraved, larger now)
    spiral_pts = [
        (16, 14), (17, 14), (18, 14), (19, 14), (20, 14),
        (21, 15), (21, 16), (21, 17), (21, 18),
        (20, 19), (19, 20), (18, 20), (17, 20), (16, 20),
        (15, 19), (14, 18), (14, 17), (14, 16),
        (15, 15), (16, 15), (17, 15),
        # Inner spiral
        (17, 16), (18, 16), (19, 16), (19, 17), (19, 18),
        (18, 18), (17, 18), (16, 18), (16, 17),
        # Center dot
        (17, 17),
    ]
    for x, y in spiral_pts:
        cv.set(x, y, PAL["leaf_dark"])
    # Spiral tail
    cv.set(22, 14, PAL["leaf_dark"]); cv.set(23, 14, PAL["leaf_dark"]); cv.set(23, 15, PAL["leaf_dark"])
    # Highlight on spiral
    cv.set(16, 14, PAL["leaf_mid"]); cv.set(17, 14, PAL["leaf_mid"]); cv.set(18, 14, PAL["leaf_mid"])


def tex_filter_tensura(cv: PixelCanvas):
    """Rimuru slime — round blue-purple blob with antenna, eyes, and smile."""
    # Body outline (larger, smoother)
    body_outline = []
    cx, cy = 16, 18
    for angle_deg in range(0, 360, 5):
        rad = math.radians(angle_deg)
        r = 12 + math.sin(rad * 3) * 0.5  # slight organic variation
        x = int(round(cx + r * math.cos(rad)))
        y = int(round(cy + r * math.sin(rad) * 0.9))  # squashed
        body_outline.append((x, y))
    # Fill body
    for y in range(32):
        for x in range(32):
            d = math.sqrt((x - cx) ** 2 + ((y - cy) / 0.9) ** 2)
            if d <= 12:
                # Gradient: lighter at top, darker at bottom
                grad = (cy - y) / 24.0  # -0.5 to 0.5
                if grad > 0.2:
                    cv.set(x, y, PAL["slime_light"])
                elif grad > -0.1:
                    cv.set(x, y, PAL["slime_mid"])
                else:
                    cv.set(x, y, PAL["slime_dark"])
    # Outline
    for x, y in body_outline:
        cv.set(x, y, PAL["slime_outline"])
    # Antenna (Rimuru's distinctive feature)
    cv.set(15, 4, PAL["slime_mid"]); cv.set(15, 5, PAL["slime_mid"]); cv.set(15, 6, PAL["slime_mid"])
    cv.set(16, 4, PAL["slime_outline"])
    # Top highlight (light source upper-left)
    for x, y in [(10, 11), (11, 11), (12, 11), (13, 11), (10, 12), (11, 12), (12, 12)]:
        cv.set(x, y, PAL["slime_pale"])
    cv.set(11, 12, PAL["slime_shine"]); cv.set(12, 12, PAL["slime_shine"])
    # Eyes (two black ovals with white shine)
    for ex, ey in [(12, 17), (20, 17)]:
        cv.set(ex, ey, PAL["black"]); cv.set(ex, ey + 1, PAL["black"])
        cv.set(ex + 1, ey, PAL["black"]); cv.set(ex + 1, ey + 1, PAL["black"])
        cv.set(ex, ey, PAL["slime_shine"])  # shine
    # Mouth (smile curve)
    for i, x in enumerate(range(14, 19)):
        offset = abs(i - 2)
        cv.set(x, 22 - offset, PAL["black"])
    # Bottom shadow
    for x in range(8, 25):
        cv.set(x, 27, PAL["slime_outline"])


def tex_filter_mushoku(cv: PixelCanvas):
    """Magic circle with concentric rings and 8-pointed star — 32x32."""
    cv.fill_all((0, 0, 0, 0))
    # Outer ring (dark blue, thick)
    cv.circle(16, 16, 14, PAL["rune_outline"])
    cv.circle(16, 16, 12, PAL["rune_dark"], fill_color=(20, 50, 120, 180))
    # Mid ring
    cv.circle(16, 16, 11, PAL["rune_mid"])
    cv.circle(16, 16, 9, PAL["rune_dark"], fill_color=(30, 70, 150, 200))
    # Inner ring
    cv.circle(16, 16, 7, PAL["rune_light"])
    cv.circle(16, 16, 5, PAL["rune_mid"], fill_color=(50, 110, 200, 220))
    # 8-pointed star at center
    for i in range(8):
        angle = i * math.pi / 4
        x1 = 16 + 5 * math.cos(angle)
        y1 = 16 + 5 * math.sin(angle)
        x2 = 16 + 2 * math.cos(angle + math.pi / 8)
        y2 = 16 + 2 * math.sin(angle + math.pi / 8)
        cv.line(int(x1), int(y1), int(x2), int(y2), PAL["rune_pale"], 1)
    # Center dot
    cv.set(16, 16, PAL["rune_pale"]); cv.set(15, 16, PAL["rune_pale"]); cv.set(17, 16, PAL["rune_pale"])
    cv.set(16, 15, PAL["rune_pale"]); cv.set(16, 17, PAL["rune_pale"])
    # Cardinal runes on outer ring (N, S, E, W)
    for angle_deg in [0, 90, 180, 270]:
        rad = math.radians(angle_deg)
        x = int(16 + 13 * math.cos(rad))
        y = int(16 + 13 * math.sin(rad))
        cv.set(x, y, PAL["rune_pale"])
        cv.set(x + 1, y, PAL["rune_pale"])
        cv.set(x - 1, y, PAL["rune_pale"])
        cv.set(x, y + 1, PAL["rune_pale"])
        cv.set(x, y - 1, PAL["rune_pale"])
    # Diagonal runes
    for angle_deg in [45, 135, 225, 315]:
        rad = math.radians(angle_deg)
        x = int(16 + 11 * math.cos(rad))
        y = int(16 + 11 * math.sin(rad))
        cv.set(x, y, PAL["rune_light"])


def tex_filter_onepiece(cv: PixelCanvas):
    """Straw hat with red band — side view, 32x32."""
    # Brim (wide, curved)
    cv.fill(2, 22, 28, 4, PAL["straw_dark"])
    cv.fill(3, 21, 26, 1, PAL["straw_mid"])
    cv.fill(4, 20, 24, 1, PAL["straw_light"])
    cv.fill(2, 26, 28, 1, PAL["straw_outline"])
    # Brim straw weave texture
    for x in range(3, 29, 3):
        cv.set(x, 23, PAL["straw_light"])
        cv.set(x + 1, 24, PAL["straw_mid"])
    # Crown (dome)
    cv.fill(10, 10, 12, 10, PAL["straw_pale"])
    cv.fill(11, 9, 10, 1, PAL["straw_pale"])
    cv.fill(9, 11, 14, 9, PAL["straw_pale"])
    # Crown top highlight
    cv.fill(10, 10, 12, 2, PAL["straw_shine"])
    cv.set(13, 9, PAL["straw_shine"]); cv.set(14, 9, PAL["straw_shine"]); cv.set(15, 9, PAL["straw_shine"])
    # Crown shadow (right side)
    cv.fill(20, 12, 3, 8, PAL["straw_light"])
    cv.fill(22, 14, 1, 6, PAL["straw_mid"])
    # Crown outline
    cv.line(9, 12, 9, 19, PAL["straw_dark"], 1)
    cv.line(22, 12, 22, 19, PAL["straw_dark"], 1)
    cv.line(10, 10, 21, 10, PAL["straw_dark"], 1)
    # Red band around base of crown
    cv.fill(9, 18, 14, 2, PAL["band_red"])
    cv.set(9, 18, PAL["band_dark"]); cv.set(22, 18, PAL["band_dark"])
    cv.set(9, 19, PAL["band_dark"]); cv.set(22, 19, PAL["band_dark"])
    # Straw weave vertical lines on crown
    for x in [12, 15, 18]:
        for y in range(11, 18):
            cv.set(x, y, PAL["straw_light"])
    # Shadow under hat
    for x in range(4, 28):
        cv.set(x, 28, (0, 0, 0, 60))


# === Navigation textures (32x32) ============================================

def tex_nav_next(cv: PixelCanvas):
    """Thick green arrow pointing right with depth shading."""
    # Arrow shape with multiple shades (32x32 = more detail)
    arrow_rows = [
        (8, 7, 9, c(40, 125, 40)),
        (9, 8, 11, c(40, 125, 40)),
        (10, 9, 13, c(80, 205, 80)),
        (11, 10, 15, c(85, 220, 85)),
        (12, 11, 17, c(140, 240, 140)),
        (13, 12, 19, c(85, 220, 85)),
        (14, 11, 17, c(80, 205, 80)),
        (15, 10, 15, c(40, 125, 40)),
        (16, 9, 13, c(40, 125, 40)),
        (17, 8, 11, c(40, 125, 40)),
        (18, 7, 9, c(40, 125, 40)),
    ]
    for y, xs, xe, color in arrow_rows:
        for x in range(xs, xe + 1):
            cv.set(x, y, color)
    # Top highlight
    for x in range(8, 12):
        cv.set(x, 8, c(180, 250, 180))
    for x in range(10, 14):
        cv.set(x, 10, c(180, 250, 180))
    for x in range(12, 18):
        cv.set(x, 12, c(220, 255, 220))
    # Bottom shadow
    for x in range(8, 12):
        cv.set(x, 18, c(40, 145, 40))
    for x in range(10, 14):
        cv.set(x, 16, c(40, 145, 40))
    # Outline
    outline_pts = [
        (7, 8), (8, 9), (9, 10), (10, 11), (11, 12), (12, 13),
        (7, 18), (8, 17), (9, 16), (10, 15), (11, 14), (12, 13),
        (20, 13), (19, 14), (18, 15), (17, 16), (16, 17), (15, 18),
    ]
    for x, y in outline_pts:
        cv.set(x, y, c(20, 65, 20))


def tex_nav_prev(cv: PixelCanvas):
    tex_nav_next(cv)
    cv.mirror_h()


def tex_nav_close(cv: PixelCanvas):
    """Bold red X with darker outline and 3D shading."""
    # X outline (dark red)
    for i in range(20):
        cv.set(6 + i, 6 + i, c(125, 22, 22))
        cv.set(6 + i, 25 - i, c(125, 22, 22))
        cv.set(7 + i, 6 + i, c(125, 22, 22))
        cv.set(7 + i, 25 - i, c(125, 22, 22))
    # X body (bright red)
    for i in range(17):
        cv.set(8 + i, 8 + i, c(225, 65, 65))
        cv.set(8 + i, 23 - i, c(225, 65, 65))
        cv.set(9 + i, 8 + i, c(225, 65, 65))
        cv.set(9 + i, 23 - i, c(225, 65, 65))
    # Highlight (pale red on top-left arm)
    for i in range(8):
        cv.set(8 + i, 8 + i, c(255, 180, 180))
    # Center bright spot
    cv.set(15, 15, c(255, 220, 220))
    cv.set(16, 15, c(255, 220, 220))
    cv.set(15, 16, c(255, 220, 220))
    cv.set(16, 16, c(255, 220, 220))


# === School icons (32x32) ===================================================

def tex_icon_naruto(cv: PixelCanvas):
    """Konoha leaf symbol — stylized leaf with spiral inside."""
    # Leaf shape (orange gradient)
    leaf_outline = []
    for t in range(0, 100):
        # Parametric leaf shape
        s = t / 100.0
        angle = s * math.pi * 2
        r = 11 + math.sin(angle * 2) * 3
        x = 16 + r * math.cos(angle - math.pi / 4)
        y = 16 + r * math.sin(angle - math.pi / 4) * 0.8
        leaf_outline.append((int(x), int(y)))
    # Fill leaf
    for y in range(32):
        for x in range(32):
            # Triangle from (5, 27) to (27, 5)
            if point_in_triangle(x + 0.5, y + 0.5, 5, 27, 27, 5, 16, 16):
                d = math.sqrt((x - 27) ** 2 + (y - 5) ** 2)
                if d < 8:
                    cv.set(x, y, PAL["leaf_pale"])
                elif d < 14:
                    cv.set(x, y, PAL["leaf_light"])
                else:
                    cv.set(x, y, PAL["leaf_mid"])
    # Outline
    for x, y in leaf_outline:
        if 0 <= x < 32 and 0 <= y < 32:
            cv.set(x, y, PAL["leaf_outline"])
    # Spiral inside (Konoha symbol)
    spiral = [
        (18, 11), (19, 11), (20, 11), (21, 11),
        (22, 12), (22, 13), (22, 14), (22, 15),
        (21, 16), (20, 17), (19, 17), (18, 17), (17, 17), (16, 17),
        (15, 16), (14, 15), (14, 14), (14, 13),
        (15, 12), (16, 11), (17, 11),
        # Inner spiral
        (17, 12), (18, 12), (19, 12), (20, 12),
        (20, 13), (20, 14),
        (19, 15), (18, 15), (17, 15), (16, 15),
        (16, 14), (16, 13),
        (17, 13),
    ]
    for x, y in spiral:
        cv.set(x, y, PAL["leaf_dark"])
    # Spiral tail
    cv.set(23, 11, PAL["leaf_dark"]); cv.set(24, 11, PAL["leaf_dark"]); cv.set(24, 12, PAL["leaf_dark"])


def tex_icon_tensura(cv: PixelCanvas):
    """Slime silhouette — transparent background."""
    tex_filter_tensura(cv)


def tex_icon_mushoku(cv: PixelCanvas):
    """Wizard staff with glowing blue crystal orb on top."""
    # Staff handle (vertical, brown with grain) — 32x32 scale
    for y in range(6, 28):
        cv.set(15, y, c(110, 70, 30))  # left edge
        cv.set(16, y, c(145, 100, 50))  # middle
        cv.set(17, y, c(80, 50, 20))    # right edge (shadow)
    # Wood grain highlights
    for y in [9, 13, 17, 21, 25]:
        cv.set(16, y, c(175, 125, 65))
    # Staff top — wrapping for crystal
    cv.set(13, 7, c(120, 80, 30)); cv.set(19, 7, c(120, 80, 30))
    cv.set(13, 6, c(80, 50, 20)); cv.set(19, 6, c(80, 50, 20))
    # Crystal orb on top (glowing blue, larger now)
    # Outer glow
    cv.circle(16, 4, 5, PAL["rune_light"], fill_color=PAL["rune_mid"])
    cv.circle(16, 4, 4, PAL["rune_mid"], fill_color=PAL["rune_light"])
    cv.circle(16, 4, 2.5, PAL["rune_light"], fill_color=PAL["rune_pale"])
    cv.circle(16, 4, 1, PAL["rune_pale"], fill_color=PAL["rune_pale"])
    # Crystal shine (top-left)
    cv.set(14, 2, PAL["white"]); cv.set(15, 2, PAL["white"])
    cv.set(14, 3, PAL["white"])
    # Glow particles floating around crystal
    cv.set(8, 4, PAL["rune_pale"]); cv.set(24, 4, PAL["rune_pale"])
    cv.set(10, 8, PAL["rune_pale"]); cv.set(22, 8, PAL["rune_pale"])
    cv.set(6, 6, PAL["rune_light"]); cv.set(26, 6, PAL["rune_light"])
    cv.set(9, 1, PAL["rune_pale"]); cv.set(23, 1, PAL["rune_pale"])
    # Staff pommel (bottom)
    cv.set(15, 28, PAL["gold_light"]); cv.set(16, 28, PAL["gold_shine"]); cv.set(17, 28, PAL["gold_light"])
    cv.set(15, 29, PAL["gold_dark"]); cv.set(17, 29, PAL["gold_dark"])


def tex_icon_onepiece(cv: PixelCanvas):
    """Skull and crossbones with straw hat brim — 32x32."""
    # Crossbones (behind skull, diagonal)
    # Left bone
    cv.line(4, 26, 12, 18, PAL["skull_light"], 2)
    cv.line(4, 26, 12, 18, PAL["skull_mid"], 1)
    cv.set(4, 26, PAL["skull_pale"]); cv.set(5, 25, PAL["skull_pale"])
    cv.set(6, 24, PAL["skull_pale"]); cv.set(7, 23, PAL["skull_pale"])
    # Right bone
    cv.line(27, 26, 19, 18, PAL["skull_light"], 2)
    cv.line(27, 26, 19, 18, PAL["skull_mid"], 1)
    cv.set(27, 26, PAL["skull_pale"]); cv.set(26, 25, PAL["skull_pale"])
    cv.set(25, 24, PAL["skull_pale"]); cv.set(24, 23, PAL["skull_pale"])
    # Bone ends (knobs)
    cv.set(4, 26, PAL["skull_pale"]); cv.set(3, 26, PAL["skull_mid"]); cv.set(4, 27, PAL["skull_mid"])
    cv.set(27, 26, PAL["skull_pale"]); cv.set(28, 26, PAL["skull_mid"]); cv.set(27, 27, PAL["skull_mid"])
    # Skull (cranium — round, larger)
    skull_pts = []
    cx, cy = 16, 13
    for angle_deg in range(0, 360, 8):
        rad = math.radians(angle_deg)
        r = 8 + math.sin(rad * 4) * 0.3
        x = int(round(cx + r * math.cos(rad)))
        y = int(round(cy + r * math.sin(rad) * 0.9))
        skull_pts.append((x, y))
    # Fill skull
    for y in range(32):
        for x in range(32):
            d = math.sqrt((x - cx) ** 2 + ((y - cy) / 0.9) ** 2)
            if d <= 8:
                grad = (cy - y) / 16.0
                if grad > 0.2:
                    cv.set(x, y, PAL["skull_pale"])
                else:
                    cv.set(x, y, PAL["skull_light"])
    # Skull outline
    for x, y in skull_pts:
        if 0 <= x < 32 and 0 <= y < 32:
            cv.set(x, y, PAL["skull_outline"])
    # Skull shadow (right side)
    for x in range(20, 25):
        for y in range(10, 18):
            if math.sqrt((x - cx) ** 2 + ((y - cy) / 0.9) ** 2) <= 8:
                cv.set(x, y, PAL["skull_mid"])
    # Eye sockets (large black)
    for ex, ey in [(12, 13), (20, 13)]:
        for dx in range(-1, 2):
            for dy in range(-1, 2):
                cv.set(ex + dx, ey + dy, PAL["black"])
    # Eye shines
    cv.set(12, 12, PAL["skull_mid"]); cv.set(20, 12, PAL["skull_mid"])
    # Nose (triangle)
    cv.set(16, 16, PAL["black"]); cv.set(15, 17, PAL["black"]); cv.set(17, 17, PAL["black"])
    # Teeth (row of vertical lines)
    for x in range(12, 21):
        cv.set(x, 19, PAL["skull_outline"])
    # Straw hat brim above skull
    cv.fill(5, 7, 22, 2, PAL["straw_dark"])
    cv.fill(7, 6, 18, 1, PAL["straw_mid"])
    cv.fill(10, 5, 12, 1, PAL["straw_pale"])
    cv.fill(11, 4, 10, 1, PAL["straw_shine"])
    # Red band on hat
    cv.fill(10, 6, 12, 1, PAL["band_red"])
    cv.set(10, 6, PAL["band_dark"]); cv.set(21, 6, PAL["band_dark"])


# === Naruto spell textures (32x32) ==========================================

def tex_spell_fireball(cv: PixelCanvas):
    """Classic anime fireball — 5-layer flames with bright core."""
    # Outer flame wisps (asymmetric, trailing)
    flame_trail = [
        (2, 14), (2, 15), (2, 16), (3, 17), (3, 18),
        (28, 12), (28, 13), (28, 14), (27, 15), (27, 16),
        (4, 19), (27, 18), (26, 20),
        (5, 22), (26, 22), (25, 23),
        (6, 24), (24, 24),
    ]
    for x, y in flame_trail:
        cv.set(x, y, PAL["fire_smoke"])
    # Outer flame ring (red-orange)
    cv.circle(16, 16, 13, PAL["fire_red"], fill_color=PAL["fire_orange"])
    # Mid flame (orange)
    cv.circle(16, 16, 10, PAL["fire_orange"], fill_color=PAL["fire_yellow"])
    # Inner flame (yellow)
    cv.circle(16, 16, 7, PAL["fire_yellow"], fill_color=PAL["fire_pale"])
    # Hot core (white-yellow)
    cv.circle(16, 16, 4, PAL["fire_pale"], fill_color=PAL["fire_white"])
    # Brightest spot (top-left)
    cv.circle(14, 14, 2, PAL["fire_white"], fill_color=PAL["white"])
    # Flame texture (small darker dots for realism)
    for x, y in [(11, 11), (21, 12), (12, 21), (20, 20), (15, 9), (17, 22)]:
        cv.set(x, y, PAL["fire_dark"])
    # Dark outline accents
    cv.set(1, 15, PAL["fire_dark"]); cv.set(30, 16, PAL["fire_dark"])


def tex_spell_chidori(cv: PixelCanvas):
    """Lightning chirping bird — main bolt with branches and sparks."""
    # Main lightning bolt (Z-shape, bright white core)
    main_bolt = [
        (20, 2), (20, 3), (20, 4), (20, 5), (20, 6),
        (19, 7), (18, 8), (17, 9), (16, 10), (16, 11),
        (15, 12), (14, 13), (14, 14), (13, 15), (12, 16), (12, 17),
        (11, 18), (10, 19), (10, 20), (9, 21), (9, 22), (8, 23),
        (8, 24), (8, 25), (8, 26), (8, 27), (8, 28), (8, 29),
    ]
    for x, y in main_bolt:
        cv.set(x, y, PAL["light_core"])
    # Bolt thickness
    thick_pts = [
        (21, 2), (21, 3), (21, 4), (21, 5), (21, 6),
        (20, 7), (19, 8), (18, 9), (17, 10), (17, 11),
        (16, 12), (15, 13), (15, 14), (14, 15), (13, 16), (13, 17),
        (12, 18), (11, 19), (11, 20), (10, 21), (10, 22), (9, 23),
        (9, 24), (9, 25), (9, 26), (9, 27), (9, 28),
    ]
    for x, y in thick_pts:
        cv.set(x, y, PAL["light_white"])
    # Blue glow around bolt
    glow_pts = [
        (22, 3), (22, 4), (22, 5), (22, 6), (21, 7), (20, 8),
        (19, 9), (18, 10), (18, 11), (17, 12), (16, 13), (16, 14),
        (15, 15), (14, 16), (14, 17), (13, 18), (12, 19), (12, 20),
        (11, 21), (11, 22), (10, 23), (10, 24), (10, 25), (10, 26),
        (10, 27), (10, 28), (10, 29),
        (7, 28), (7, 29),
    ]
    for x, y in glow_pts:
        cv.set(x, y, PAL["light_blue"])
    # Branches (smaller bolts)
    branch1 = [(20, 7), (21, 8), (22, 9), (23, 10), (24, 11), (25, 12), (26, 13)]
    branch2 = [(17, 11), (18, 12), (19, 13), (20, 14), (21, 15), (22, 16), (23, 17)]
    branch3 = [(13, 17), (12, 18), (11, 19), (10, 20), (9, 21), (8, 22), (7, 23)]
    branch4 = [(15, 13), (14, 14), (13, 15), (12, 16), (11, 17), (10, 18), (9, 19)]
    branch5 = [(18, 9), (19, 10), (20, 11), (21, 12), (22, 13)]
    for x, y in branch1 + branch2 + branch3 + branch4 + branch5:
        cv.set(x, y, PAL["light_white"])
    # Deep blue outline at edges of branches
    deep_pts = [
        (26, 14), (25, 13), (24, 12), (23, 11),
        (23, 18), (22, 17), (21, 16), (20, 15),
        (7, 24), (8, 23), (9, 22),
        (9, 20), (10, 19), (11, 18),
    ]
    for x, y in deep_pts:
        cv.set(x, y, PAL["light_deep"])
    # Spark particles (random small dots around the bolt)
    sparks = [
        (26, 2), (27, 3), (28, 4), (4, 10), (3, 13), (28, 14),
        (5, 18), (27, 20), (4, 22), (28, 24), (5, 26), (26, 27),
        (3, 28), (25, 5), (24, 8), (5, 24), (27, 26),
    ]
    for x, y in sparks:
        cv.set(x, y, PAL["light_glow"])


def tex_spell_rasengan(cv: PixelCanvas):
    """Swirling blue sphere — concentric rings with spiral arms."""
    # Outer dark ring
    cv.circle(16, 16, 14, PAL["rasengan_ring"], fill_color=PAL["rasengan_dark"])
    # Mid ring (medium blue)
    cv.circle(16, 16, 11, PAL["rasengan_dark"], fill_color=PAL["rasengan_mid"])
    # Inner area (light blue fill)
    cv.circle(16, 16, 8, PAL["rasengan_mid"], fill_color=PAL["rasengan_light"])
    # Spiral arms (curved lines spiraling inward)
    for angle_step in range(0, 360, 45):
        rad = math.radians(angle_step)
        for r in range(3, 11):
            x = int(16 + r * math.cos(rad + r * 0.2))
            y = int(16 + r * math.sin(rad + r * 0.2))
            cv.set(x, y, PAL["rasengan_mid"])
    # Core (white-blue)
    cv.circle(16, 16, 4, PAL["rasengan_light"], fill_color=PAL["rasengan_pale"])
    cv.circle(16, 16, 2, PAL["rasengan_pale"], fill_color=PAL["rasengan_white"])
    # Brightest spot (top-left)
    cv.set(14, 14, PAL["white"]); cv.set(15, 14, PAL["white"]); cv.set(14, 15, PAL["white"])
    # Motion lines (4 cardinal)
    cv.set(16, 1, PAL["rasengan_pale"]); cv.set(15, 1, PAL["rasengan_pale"]); cv.set(17, 1, PAL["rasengan_pale"])
    cv.set(16, 30, PAL["rasengan_pale"]); cv.set(15, 30, PAL["rasengan_pale"]); cv.set(17, 30, PAL["rasengan_pale"])
    cv.set(1, 16, PAL["rasengan_pale"]); cv.set(1, 15, PAL["rasengan_pale"]); cv.set(1, 17, PAL["rasengan_pale"])
    cv.set(30, 16, PAL["rasengan_pale"]); cv.set(30, 15, PAL["rasengan_pale"]); cv.set(30, 17, PAL["rasengan_pale"])
    # Highlight on outer ring (top-left)
    cv.set(4, 4, PAL["rasengan_mid"]); cv.set(5, 4, PAL["rasengan_mid"])
    cv.set(4, 5, PAL["rasengan_mid"]); cv.set(6, 4, PAL["rasengan_mid"])


def tex_spell_shadow_clone(cv: PixelCanvas):
    """Three shadowy ninja silhouettes with glowing red eyes."""
    # Smoke cloud at base
    for x in range(2, 30):
        cv.set(x, 29, PAL["shadow_pale"])
        cv.set(x, 30, PAL["shadow_pale"])
    for x in [3, 7, 12, 17, 22, 27]:
        cv.set(x, 28, PAL["shadow_pale"])
    # BACK clone (smallest, darkest, leftmost)
    back = [
        (5, 13), (6, 13), (7, 13), (5, 14), (6, 14), (7, 14),
        (5, 15), (6, 15), (7, 15),
        (4, 16), (5, 16), (6, 16), (7, 16), (8, 16),
        (4, 17), (5, 17), (6, 17), (7, 17), (8, 17),
        (4, 18), (5, 18), (6, 18), (7, 18), (8, 18),
        (4, 19), (5, 19), (6, 19), (7, 19), (8, 19),
        (5, 20), (6, 20), (7, 20),
        (5, 21), (6, 21), (7, 21),
        (5, 22), (6, 22), (7, 22),
        (5, 23), (6, 23), (7, 23),
        (5, 24), (6, 24), (7, 24),
    ]
    for x, y in back:
        cv.set(x, y, PAL["shadow_dark"])
    cv.set(5, 14, PAL["eye_glow"]); cv.set(7, 14, PAL["eye_glow"])
    # MID clone (medium, center)
    mid = [
        (14, 11), (15, 11), (16, 11), (17, 11), (14, 12), (15, 12), (16, 12), (17, 12),
        (14, 13), (15, 13), (16, 13), (17, 13),
        (13, 14), (14, 14), (15, 14), (16, 14), (17, 14), (18, 14),
        (13, 15), (14, 15), (15, 15), (16, 15), (17, 15), (18, 15),
        (13, 16), (14, 16), (15, 16), (16, 16), (17, 16), (18, 16),
        (13, 17), (14, 17), (15, 17), (16, 17), (17, 17), (18, 17),
        (13, 18), (14, 18), (15, 18), (16, 18), (17, 18), (18, 18),
        (14, 19), (15, 19), (16, 19), (17, 19),
        (14, 20), (15, 20), (16, 20), (17, 20),
        (14, 21), (15, 21), (16, 21), (17, 21),
        (14, 22), (15, 22), (16, 22), (17, 22),
        (14, 23), (15, 23), (16, 23), (17, 23),
        (14, 24), (15, 24), (16, 24), (17, 24),
        (14, 25), (15, 25), (16, 25), (17, 25),
    ]
    for x, y in mid:
        cv.set(x, y, PAL["shadow_mid"])
    cv.set(14, 13, PAL["eye_red"]); cv.set(17, 13, PAL["eye_red"])
    # FRONT clone (largest, rightmost, has headband)
    front = [
        # head (bigger)
        (22, 9), (23, 9), (24, 9), (25, 9), (26, 9),
        (22, 10), (23, 10), (24, 10), (25, 10), (26, 10),
        (22, 11), (23, 11), (24, 11), (25, 11), (26, 11),
        (22, 12), (23, 12), (24, 12), (25, 12), (26, 12),
        # body
        (21, 13), (22, 13), (23, 13), (24, 13), (25, 13), (26, 13), (27, 13),
        (21, 14), (22, 14), (23, 14), (24, 14), (25, 14), (26, 14), (27, 14),
        (21, 15), (22, 15), (23, 15), (24, 15), (25, 15), (26, 15), (27, 15),
        (21, 16), (22, 16), (23, 16), (24, 16), (25, 16), (26, 16), (27, 16),
        (21, 17), (22, 17), (23, 17), (24, 17), (25, 17), (26, 17), (27, 17),
        (22, 18), (23, 18), (24, 18), (25, 18), (26, 18),
        (22, 19), (23, 19), (24, 19), (25, 19), (26, 19),
        (22, 20), (23, 20), (24, 20), (25, 20), (26, 20),
        (22, 21), (23, 21), (24, 21), (25, 21), (26, 21),
        (22, 22), (23, 22), (24, 22), (25, 22), (26, 22),
        (22, 23), (23, 23), (24, 23), (25, 23), (26, 23),
        (22, 24), (23, 24), (24, 24), (25, 24), (26, 24),
        (22, 25), (23, 25), (24, 25), (25, 25), (26, 25),
    ]
    for x, y in front:
        cv.set(x, y, PAL["shadow_dark"])
    # Outline
    outline_pts = [
        (22, 8), (23, 8), (24, 8), (25, 8), (26, 8),
        (20, 14), (28, 14), (29, 15), (20, 18), (28, 18),
        (21, 19), (27, 19), (21, 26), (27, 26),
    ]
    for x, y in outline_pts:
        cv.set(x, y, PAL["shadow_outline"])
    # Headband (silver plate)
    cv.set(22, 11, PAL["steel_pale"]); cv.set(23, 11, PAL["steel_light"])
    cv.set(24, 11, PAL["steel_shine"]); cv.set(25, 11, PAL["steel_pale"])
    cv.set(26, 11, PAL["steel_light"])
    # Eyes (red glow)
    cv.set(22, 12, PAL["eye_red"]); cv.set(26, 12, PAL["eye_red"])
    cv.set(22, 12, PAL["eye_glow"]); cv.set(26, 12, PAL["eye_glow"])


# === NEW v2.5 Ultimate spell textures (32x32) ===============================

def tex_spell_phoenix_flower(cv: PixelCanvas):
    """Phoenix flower — small fireball with petal arrangement."""
    # 5 small fireballs arranged in flower pattern
    for i in range(5):
        angle = i * 2 * math.pi / 5 - math.pi / 2
        cx = 16 + 7 * math.cos(angle)
        cy = 16 + 7 * math.sin(angle)
        # Each petal: small layered fireball
        cv.circle(cx, cy, 4, PAL["fire_red"], fill_color=PAL["fire_orange"])
        cv.circle(cx, cy, 2.5, PAL["fire_orange"], fill_color=PAL["fire_yellow"])
        cv.circle(cx, cy, 1, PAL["fire_yellow"], fill_color=PAL["fire_white"])
    # Center
    cv.circle(16, 16, 3, PAL["fire_yellow"], fill_color=PAL["fire_white"])
    cv.set(15, 15, PAL["white"]); cv.set(16, 15, PAL["white"])
    # Flame wisps
    cv.set(2, 16, PAL["fire_smoke"]); cv.set(29, 16, PAL["fire_smoke"])
    cv.set(16, 2, PAL["fire_smoke"]); cv.set(16, 29, PAL["fire_smoke"])


def tex_spell_rasenshuriken(cv: PixelCanvas):
    """Rasenshuriken — 4-bladed wind shuriken with center sphere."""
    # 4 blades arranged in X pattern
    for i in range(4):
        angle = i * math.pi / 2
        # Blade as elongated diamond from center outward
        for r in range(2, 14):
            for w in range(-2, 3):
                x = int(16 + r * math.cos(angle) - w * math.sin(angle))
                y = int(16 + r * math.sin(angle) + w * math.cos(angle))
                if 0 <= x < 32 and 0 <= y < 32:
                    # Color gradient: pale at edges, light at center
                    dist = abs(w) + abs(r - 8) * 0.1
                    if dist < 0.5:
                        cv.set(x, y, PAL["rasengan_white"])
                    elif dist < 1.5:
                        cv.set(x, y, PAL["rasengan_pale"])
                    elif dist < 2.5:
                        cv.set(x, y, PAL["rasengan_light"])
                    else:
                        cv.set(x, y, PAL["rasengan_mid"])
    # Center sphere
    cv.circle(16, 16, 4, PAL["rasengan_dark"], fill_color=PAL["rasengan_mid"])
    cv.circle(16, 16, 2.5, PAL["rasengan_mid"], fill_color=PAL["rasengan_light"])
    cv.circle(16, 16, 1, PAL["rasengan_light"], fill_color=PAL["white"])
    # Center shine
    cv.set(15, 15, PAL["white"])


def tex_spell_kirin(cv: PixelCanvas):
    """Kirin — vertical lightning bolt descending from sky."""
    # Main vertical bolt
    bolt_pts = [
        (15, 1), (16, 1), (17, 1),
        (15, 2), (16, 2), (17, 2),
        (15, 3), (16, 3), (17, 3),
        (16, 4), (17, 4),
        (17, 5), (18, 5),
        (18, 6), (19, 6),
        (19, 7), (20, 7),
        (20, 8), (21, 8),
        (21, 9), (22, 9),
        (22, 10), (21, 10),
        (21, 11), (20, 11),
        (20, 12), (19, 12),
        (19, 13), (18, 13),
        (18, 14), (17, 14),
        (17, 15), (16, 15),
        (16, 16), (15, 16),
        (15, 17), (14, 17),
        (14, 18), (15, 18),
        (15, 19), (16, 19),
        (16, 20), (17, 20),
        (17, 21), (18, 21),
        (18, 22), (19, 22),
        (19, 23), (20, 23),
        (20, 24), (19, 24),
        (19, 25), (18, 25),
        (18, 26), (17, 26),
        (17, 27), (16, 27),
        (16, 28), (15, 28),
        (15, 29), (16, 29), (17, 29),
        (15, 30), (16, 30), (17, 30),
    ]
    for x, y in bolt_pts:
        cv.set(x, y, PAL["light_core"])
    # Blue glow
    for x, y in bolt_pts:
        for dx, dy in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
            if 0 <= x + dx < 32 and 0 <= y + dy < 32:
                if cv.pixels[y + dy][x + dx] == (0, 0, 0, 0):
                    cv.set(x + dx, y + dy, PAL["light_blue"])
    # Side branches
    branches = [
        (17, 4), (18, 4), (19, 5), (20, 5), (21, 6),
        (22, 7), (23, 8), (24, 9), (25, 10),
        (14, 8), (13, 8), (12, 9), (11, 10), (10, 11),
        (9, 12), (8, 13),
        (22, 14), (23, 14), (24, 15), (25, 15), (26, 16),
        (10, 18), (9, 18), (8, 19), (7, 19), (6, 20),
        (22, 22), (23, 22), (24, 23), (25, 23), (26, 24),
    ]
    for x, y in branches:
        cv.set(x, y, PAL["light_white"])
    # Sparks
    for x, y in [(28, 5), (4, 8), (28, 12), (3, 16), (28, 20), (4, 24), (27, 27), (5, 28)]:
        cv.set(x, y, PAL["light_glow"])


def tex_spell_sage_mode(cv: PixelCanvas):
    """Sage Mode — orange aura with closed eye markings."""
    # Orange aura (concentric)
    cv.circle(16, 16, 14, PAL["sage_dark"], fill_color=PAL["sage_mid"])
    cv.circle(16, 16, 11, PAL["sage_mid"], fill_color=PAL["sage_light"])
    cv.circle(16, 16, 8, PAL["sage_light"], fill_color=PAL["sage_pale"])
    # Inner face area (dark orange)
    cv.circle(16, 16, 5, PAL["sage_dark"], fill_color=PAL["sage_mid"])
    # Sage eye markings (orange pigmentation around eyes)
    # Left eye
    for x, y in [(11, 14), (12, 14), (13, 14), (11, 15), (12, 15), (13, 15)]:
        cv.set(x, y, PAL["sage_dark"])
    # Right eye
    for x, y in [(18, 14), (19, 14), (20, 14), (18, 15), (19, 15), (20, 15)]:
        cv.set(x, y, PAL["sage_dark"])
    # Eye pupils (black)
    cv.set(12, 14, PAL["black"]); cv.set(19, 14, PAL["black"])
    # Toad-like mouth (horizontal line)
    for x in range(12, 21):
        cv.set(x, 20, PAL["sage_dark"])
    # Top highlight
    cv.set(13, 11, PAL["sage_pale"]); cv.set(14, 11, PAL["sage_pale"])
    cv.set(15, 11, PAL["sage_pale"]); cv.set(16, 11, PAL["sage_pale"])


def tex_spell_six_paths(cv: PixelCanvas):
    """Six Paths Sage Mode — white/gold with truth-seeking orbs."""
    # White aura
    cv.circle(16, 16, 14, PAL["sixpaths_light"], fill_color=PAL["sixpaths_pale"])
    cv.circle(16, 16, 11, PAL["sixpaths_pale"], fill_color=PAL["white"])
    # Gold markings (magatama pattern — comma shapes)
    for i in range(6):
        angle = i * math.pi / 3 - math.pi / 2
        cx = 16 + 9 * math.cos(angle)
        cy = 16 + 9 * math.sin(angle)
        # Magatama (comma shape)
        cv.set(int(cx), int(cy), PAL["sixpaths_gold"])
        cv.set(int(cx) + 1, int(cy), PAL["sixpaths_gold"])
        cv.set(int(cx), int(cy) + 1, PAL["sixpaths_gold"])
        cv.set(int(cx) - 1, int(cy), PAL["gold_dark"])
    # Center
    cv.circle(16, 16, 4, PAL["sixpaths_gold"], fill_color=PAL["gold_shine"])
    cv.set(15, 15, PAL["white"]); cv.set(16, 15, PAL["white"])
    # Truth-seeking orbs (small black orbs around outer edge)
    for i in range(8):
        angle = i * math.pi / 4 + math.pi / 8
        x = int(16 + 13 * math.cos(angle))
        y = int(16 + 13 * math.sin(angle))
        cv.set(x, y, PAL["black"])
        cv.set(x + 1, y, PAL["black"])
        cv.set(x, y + 1, PAL["black"])


# === Tensura spell textures (32x32) =========================================

def tex_spell_magicule_blade(cv: PixelCanvas):
    """Glowing purple katana with crossguard and wrapped handle."""
    # Blade (vertical, pointing up)
    for y in range(2, 24):
        cv.set(14, y, PAL["magicule_dark"])
        cv.set(15, y, PAL["magicule_mid"])
        cv.set(16, y, PAL["magicule_light"])
        cv.set(17, y, PAL["magicule_mid"])
        cv.set(18, y, PAL["magicule_dark"])
    # Blade highlights
    for y in range(3, 23, 2):
        cv.set(15, y, PAL["magicule_shine"])
        cv.set(16, y, PAL["magicule_pale"])
    # Blade tip (curved point)
    cv.set(15, 1, PAL["magicule_light"]); cv.set(16, 1, PAL["magicule_pale"])
    cv.set(15, 0, PAL["magicule_shine"])
    # Crossguard (gold, wider)
    cv.fill(10, 24, 12, 2, PAL["gold_dark"])
    cv.fill(10, 25, 12, 2, PAL["gold_light"])
    cv.fill(10, 24, 12, 1, PAL["gold_pale"])
    cv.set(10, 24, PAL["gold_dark"]); cv.set(21, 24, PAL["gold_dark"])
    cv.set(10, 26, PAL["gold_dark"]); cv.set(21, 26, PAL["gold_dark"])
    # Handle (dark brown wrapped)
    cv.fill(14, 27, 5, 5, c(80, 50, 20))
    # Wrap pattern (crisscross)
    for y in [28, 30]:
        cv.set(14, y, c(40, 25, 10)); cv.set(15, y, c(40, 25, 10))
        cv.set(16, y, c(40, 25, 10)); cv.set(17, y, c(40, 25, 10)); cv.set(18, y, c(40, 25, 10))
    for y in [27, 29, 31]:
        cv.set(15, y, c(140, 90, 40)); cv.set(16, y, c(140, 90, 40)); cv.set(17, y, c(140, 90, 40))
    # Pommel (gold)
    cv.set(14, 31, PAL["gold_light"]); cv.set(15, 31, PAL["gold_shine"])
    cv.set(16, 31, PAL["gold_shine"]); cv.set(17, 31, PAL["gold_light"]); cv.set(18, 31, PAL["gold_dark"])
    # Magicule glow particles around blade
    cv.set(10, 6, PAL["magicule_shine"]); cv.set(22, 10, PAL["magicule_shine"])
    cv.set(10, 14, PAL["magicule_pale"]); cv.set(22, 18, PAL["magicule_pale"])
    cv.set(10, 20, PAL["magicule_pale"])
    # Aura wisps
    cv.set(8, 4, PAL["magicule_pale"]); cv.set(24, 4, PAL["magicule_pale"])


def tex_spell_gluttony(cv: PixelCanvas):
    """Monster maw with sharp teeth, red interior, dripping."""
    # Outer dark background (mouth)
    cv.fill(2, 6, 28, 22, PAL["maw_black"])
    # Mouth interior (red gradient)
    cv.fill(3, 7, 26, 20, PAL["maw_dark_red"])
    cv.fill(5, 9, 22, 16, PAL["maw_red"])
    cv.fill(7, 11, 18, 12, PAL["maw_pink"])
    # Tongue (pink, in middle)
    cv.fill(14, 14, 4, 6, PAL["maw_pink"])
    cv.set(14, 19, PAL["maw_dark_red"]); cv.set(17, 19, PAL["maw_dark_red"])
    # Upper teeth (triangles pointing down)
    for x in range(3, 29, 3):
        # Tooth base (top of mouth)
        cv.set(x, 6, PAL["tooth_pale"]); cv.set(x + 1, 6, PAL["tooth_pale"])
        cv.set(x + 2, 6, PAL["tooth_pale"])
        # Tooth tapering down
        cv.set(x + 1, 7, PAL["tooth_pale"])
        cv.set(x + 1, 8, PAL["tooth_shadow"])
    # Lower teeth (triangles pointing up)
    for x in range(3, 29, 3):
        cv.set(x, 27, PAL["tooth_pale"]); cv.set(x + 1, 27, PAL["tooth_pale"])
        cv.set(x + 2, 27, PAL["tooth_pale"])
        cv.set(x + 1, 26, PAL["tooth_pale"])
        cv.set(x + 1, 25, PAL["tooth_shadow"])
    # Mouth outline
    cv.rect_outline(2, 6, 28, 22, PAL["maw_black"])
    # Drips (saliva/blood) at bottom
    cv.set(9, 28, PAL["maw_red"]); cv.set(20, 28, PAL["maw_red"])
    cv.set(9, 29, PAL["maw_dark_red"]); cv.set(20, 29, PAL["maw_dark_red"])
    cv.set(9, 30, PAL["maw_dark_red"])
    # Eyes (glowing red, above the maw)
    cv.set(8, 2, PAL["eye_red"]); cv.set(9, 2, PAL["eye_red"])
    cv.set(22, 2, PAL["eye_red"]); cv.set(23, 2, PAL["eye_red"])
    cv.set(8, 3, PAL["eye_glow"]); cv.set(23, 3, PAL["eye_glow"])


def tex_spell_razor_edge(cv: PixelCanvas):
    """Razor-sharp silver blade with motion lines and blood drip."""
    # Blade (very thin, vertical)
    # Sharp edge (left side, very bright)
    for y in range(1, 26):
        cv.set(14, y, PAL["steel_shine"])
    # Blade body
    for y in range(1, 26):
        cv.set(15, y, PAL["steel_pale"])
        cv.set(16, y, PAL["steel_pale"])
    # Back edge (dark)
    for y in range(1, 26):
        cv.set(17, y, PAL["steel_mid"])
        cv.set(18, y, PAL["steel_dark"])
    # Tip (sharp point at top)
    cv.set(15, 0, PAL["steel_shine"]); cv.set(16, 0, PAL["steel_shine"])
    cv.set(14, 0, PAL["steel_shine"])
    # Highlights along the blade (small bright spots)
    cv.set(14, 4, PAL["white"]); cv.set(14, 10, PAL["white"])
    cv.set(14, 16, PAL["white"]); cv.set(14, 22, PAL["white"])
    # Crossguard (small, gold)
    cv.fill(12, 26, 8, 2, PAL["gold_dark"])
    cv.fill(12, 27, 8, 2, PAL["gold_light"])
    cv.fill(12, 26, 8, 1, PAL["gold_pale"])
    cv.set(12, 26, PAL["gold_outline"]); cv.set(19, 26, PAL["gold_outline"])
    # Handle
    cv.fill(14, 29, 5, 3, c(80, 50, 10))
    cv.set(14, 29, c(40, 25, 5)); cv.set(18, 29, c(40, 25, 5))
    # Blood drip at tip (top)
    cv.set(15, 0, PAL["blood_red"]); cv.set(16, 0, PAL["blood_red"])
    cv.set(15, 1, PAL["blood_red"])
    cv.set(15, 2, PAL["blood_dark"])
    # Motion lines (horizontal dashes indicating speed)
    cv.set(4, 6, PAL["steel_light"]); cv.set(5, 6, PAL["steel_light"]); cv.set(6, 6, PAL["steel_light"])
    cv.set(4, 12, PAL["steel_light"]); cv.set(5, 12, PAL["steel_light"])
    cv.set(25, 9, PAL["steel_light"]); cv.set(26, 9, PAL["steel_light"]); cv.set(27, 9, PAL["steel_light"])
    cv.set(25, 18, PAL["steel_light"]); cv.set(26, 18, PAL["steel_light"])
    cv.set(4, 20, PAL["steel_light"]); cv.set(5, 20, PAL["steel_light"])
    # Speed sparkles
    cv.set(2, 10, PAL["white"]); cv.set(29, 14, PAL["white"])
    cv.set(3, 22, PAL["white"]); cv.set(28, 5, PAL["white"])


def tex_spell_disintegration(cv: PixelCanvas):
    """Disintegration beam — purple/dark sweeping energy."""
    # Background transparent
    # Main beam (vertical, purple)
    cv.fill(13, 1, 6, 30, PAL["magicule_dark"])
    cv.fill(14, 1, 4, 30, PAL["magicule_mid"])
    cv.fill(15, 1, 2, 30, PAL["magicule_light"])
    # Bright core
    cv.set(15, 1, PAL["magicule_shine"]); cv.set(15, 2, PAL["magicule_shine"])
    cv.set(16, 1, PAL["magicule_shine"]); cv.set(16, 2, PAL["magicule_shine"])
    # Disintegration particles (small dots around beam)
    for y in range(2, 30, 2):
        cv.set(11, y, PAL["magicule_pale"]); cv.set(20, y, PAL["magicule_pale"])
    for y in range(4, 28, 3):
        cv.set(9, y, PAL["magicule_shine"]); cv.set(22, y, PAL["magicule_shine"])
    # Top flare (energy emission)
    cv.set(13, 0, PAL["magicule_pale"]); cv.set(14, 0, PAL["magicule_shine"])
    cv.set(15, 0, PAL["white"]); cv.set(16, 0, PAL["white"])
    cv.set(17, 0, PAL["magicule_shine"]); cv.set(18, 0, PAL["magicule_pale"])
    # Bottom impact
    cv.set(13, 31, PAL["magicule_pale"]); cv.set(14, 31, PAL["magicule_shine"])
    cv.set(15, 31, PAL["white"]); cv.set(16, 31, PAL["white"])
    cv.set(17, 31, PAL["magicule_shine"]); cv.set(18, 31, PAL["magicule_pale"])


def tex_spell_beelzebuth(cv: PixelCanvas):
    """Beelzebuth — multiple drain tendrils converging."""
    # Central dark orb
    cv.circle(16, 16, 7, PAL["magicule_outline"], fill_color=PAL["magicule_dark"])
    cv.circle(16, 16, 5, PAL["magicule_dark"], fill_color=PAL["magicule_mid"])
    cv.circle(16, 16, 3, PAL["magicule_mid"], fill_color=PAL["magicule_light"])
    cv.circle(16, 16, 1, PAL["magicule_light"], fill_color=PAL["magicule_shine"])
    # Tendrils extending outward in 8 directions
    for i in range(8):
        angle = i * math.pi / 4
        for r in range(8, 14):
            x = int(16 + r * math.cos(angle))
            y = int(16 + r * math.sin(angle))
            if 0 <= x < 32 and 0 <= y < 32:
                cv.set(x, y, PAL["magicule_dark"])
        # Tendril tip
        x_tip = int(16 + 14 * math.cos(angle))
        y_tip = int(16 + 14 * math.sin(angle))
        cv.set(x_tip, y_tip, PAL["magicule_pale"])
    # Glowing eyes in center (sinister)
    cv.set(14, 14, PAL["eye_red"]); cv.set(17, 14, PAL["eye_red"])
    # Outer ring of small particles
    for i in range(16):
        angle = i * math.pi / 8
        x = int(16 + 15 * math.cos(angle))
        y = int(16 + 15 * math.sin(angle))
        cv.set(x, y, PAL["magicule_pale"])


def tex_spell_megiddo(cv: PixelCanvas):
    """Megiddo — pillar of light from heaven."""
    # Vertical light pillar (bright white center)
    cv.fill(14, 0, 4, 32, PAL["white"])
    cv.fill(13, 0, 6, 32, PAL["voice_pale"])
    cv.fill(12, 0, 8, 32, c(255, 250, 200, 200))
    cv.fill(11, 0, 10, 32, c(255, 240, 150, 150))
    # Light rays radiating from pillar
    for y in range(0, 32, 2):
        for dx in range(-6, 7):
            if abs(dx) > 3:
                x = 16 + dx
                if 0 <= x < 32:
                    alpha = max(0, 100 - abs(dx) * 15)
                    cv.set(x, y, (255, 250, 200, alpha))
    # Top burst (light source)
    cv.circle(16, 2, 5, PAL["white"], fill_color=PAL["voice_shine"])
    cv.circle(16, 2, 3, PAL["voice_shine"], fill_color=PAL["white"])
    # Bottom impact (crater)
    cv.circle(16, 30, 4, PAL["white"], fill_color=PAL["voice_shine"])
    cv.set(14, 31, PAL["voice_pale"]); cv.set(15, 31, PAL["voice_pale"])
    cv.set(17, 31, PAL["voice_pale"]); cv.set(18, 31, PAL["voice_pale"])
    # Sparks
    cv.set(8, 4, PAL["voice_shine"]); cv.set(24, 4, PAL["voice_shine"])
    cv.set(6, 10, PAL["voice_pale"]); cv.set(26, 10, PAL["voice_pale"])
    cv.set(8, 20, PAL["voice_pale"]); cv.set(24, 20, PAL["voice_pale"])
    cv.set(6, 26, PAL["voice_shine"]); cv.set(26, 26, PAL["voice_shine"])


def tex_spell_raphael(cv: PixelCanvas):
    """Raphael — wisdom orb with cyan/blue aura."""
    # Outer cyan aura
    cv.circle(16, 16, 14, PAL["rune_outline"], fill_color=(20, 50, 100, 100))
    cv.circle(16, 16, 11, PAL["rune_dark"], fill_color=PAL["rune_mid"])
    cv.circle(16, 16, 8, PAL["rune_mid"], fill_color=PAL["rune_light"])
    cv.circle(16, 16, 5, PAL["rune_light"], fill_color=PAL["rune_pale"])
    # Center wisdom orb
    cv.circle(16, 16, 3, PAL["rune_pale"], fill_color=PAL["white"])
    # Orbiting wisdom particles
    for i in range(6):
        angle = i * math.pi / 3
        x = int(16 + 10 * math.cos(angle))
        y = int(16 + 10 * math.sin(angle))
        cv.set(x, y, PAL["rune_pale"])
        cv.set(x + 1, y, PAL["rune_pale"])
        cv.set(x, y + 1, PAL["rune_pale"])
    # Center eye (wisdom symbol)
    cv.set(14, 15, PAL["black"]); cv.set(15, 15, PAL["black"])
    cv.set(16, 15, PAL["rune_dark"]); cv.set(17, 15, PAL["black"])
    cv.set(18, 15, PAL["black"])
    cv.set(15, 16, PAL["rune_dark"]); cv.set(16, 16, PAL["rune_dark"]); cv.set(17, 16, PAL["rune_dark"])
    # Top shine
    cv.set(15, 13, PAL["white"]); cv.set(16, 13, PAL["white"]); cv.set(17, 13, PAL["white"])


def tex_spell_true_dragon(cv: PixelCanvas):
    """True Dragon Form — black/purple dragon silhouette."""
    # Dragon body (black with purple highlights)
    # Head
    cv.fill(8, 8, 16, 12, PAL["dragon_black"])
    cv.fill(10, 10, 12, 8, PAL["dragon_dark"])
    # Eyes (purple glow)
    cv.set(11, 12, PAL["dragon_pale"]); cv.set(13, 12, PAL["dragon_pale"])
    cv.set(11, 13, PAL["dragon_pale"]); cv.set(13, 13, PAL["dragon_pale"])
    # Horns
    cv.set(9, 5, PAL["dragon_dark"]); cv.set(10, 6, PAL["dragon_dark"])
    cv.set(20, 5, PAL["dragon_dark"]); cv.set(21, 6, PAL["dragon_dark"])
    cv.set(9, 4, PAL["dragon_pale"]); cv.set(20, 4, PAL["dragon_pale"])
    # Wings (spread)
    for y in range(14, 22):
        cv.set(4, y, PAL["dragon_dark"]); cv.set(5, y, PAL["dragon_dark"])
        cv.set(6, y, PAL["dragon_dark"])
        cv.set(25, y, PAL["dragon_dark"]); cv.set(26, y, PAL["dragon_dark"])
        cv.set(27, y, PAL["dragon_dark"])
    # Wing details
    cv.set(5, 16, PAL["dragon_pale"]); cv.set(26, 16, PAL["dragon_pale"])
    cv.set(5, 19, PAL["dragon_mid"]); cv.set(26, 19, PAL["dragon_mid"])
    # Body scales
    for x in [12, 14, 16, 18, 20]:
        cv.set(x, 18, PAL["dragon_mid"])
        cv.set(x, 21, PAL["dragon_mid"])
    # Tail
    cv.set(13, 24, PAL["dragon_dark"]); cv.set(14, 24, PAL["dragon_dark"])
    cv.set(15, 25, PAL["dragon_dark"]); cv.set(16, 26, PAL["dragon_dark"])
    cv.set(17, 27, PAL["dragon_dark"]); cv.set(18, 28, PAL["dragon_dark"])
    # Aura particles
    cv.set(2, 12, PAL["dragon_pale"]); cv.set(29, 12, PAL["dragon_pale"])
    cv.set(2, 20, PAL["dragon_pale"]); cv.set(29, 20, PAL["dragon_pale"])
    # Fire breath hint
    cv.set(8, 14, PAL["fire_orange"]); cv.set(7, 14, PAL["fire_yellow"])
    cv.set(24, 14, PAL["fire_orange"]); cv.set(25, 14, PAL["fire_yellow"])


# === Mushoku spell textures (32x32) =========================================

def tex_spell_saint_water(cv: PixelCanvas):
    """Water drop with concentric ripples — 32x32."""
    # Outer ripple (widest)
    for angle_deg in range(0, 360, 5):
        rad = math.radians(angle_deg)
        x = int(16 + 14 * math.cos(rad))
        y = int(26 + 4 * math.sin(rad))  # squashed ellipse
        cv.set(x, y, PAL["water_dark"])
    # Mid ripple
    for angle_deg in range(0, 360, 5):
        rad = math.radians(angle_deg)
        x = int(16 + 11 * math.cos(rad))
        y = int(25 + 3 * math.sin(rad))
        cv.set(x, y, PAL["water_mid"])
    # Inner ripple
    for angle_deg in range(0, 360, 5):
        rad = math.radians(angle_deg)
        x = int(16 + 8 * math.cos(rad))
        y = int(24 + 2 * math.sin(rad))
        cv.set(x, y, PAL["water_light"])
    # The water drop (teardrop shape pointing up)
    # Drop outline
    drop_outline = []
    for t in range(0, 100):
        s = t / 100.0
        if s < 0.5:
            # Top half (pointy)
            angle = s * 2 * math.pi
            r = 6 * (1 - s)
            x = 16 + r * math.cos(angle - math.pi / 2)
            y = 16 + r * math.sin(angle - math.pi / 2)
        else:
            # Bottom half (round)
            angle = (s - 0.5) * 2 * math.pi
            x = 16 + 6 * math.cos(angle)
            y = 18 + 6 * math.sin(angle)
        drop_outline.append((int(x), int(y)))
    # Drop fill (gradient)
    for y in range(32):
        for x in range(32):
            # Inside drop if within distance
            d_top = math.sqrt((x - 16) ** 2 + (y - 18) ** 2)
            if y < 18:
                # Top half: distance to point at (16, 4)
                if y >= 4 and abs(x - 16) < (y - 4) * 0.4:
                    if d_top < 8:
                        cv.set(x, y, PAL["water_pale"])
            else:
                if d_top < 6:
                    cv.set(x, y, PAL["water_mid"])
    # Drop outline
    for x, y in drop_outline:
        if 0 <= x < 32 and 0 <= y < 32:
            cv.set(x, y, PAL["water_dark"])
    # Shine highlight (top-left of drop)
    cv.set(13, 16, PAL["water_shine"]); cv.set(14, 16, PAL["water_shine"])
    cv.set(13, 17, PAL["water_shine"]); cv.set(14, 17, PAL["water_shine"])
    cv.set(13, 18, PAL["water_pale"])
    # Tiny droplets splashing around
    cv.set(4, 10, PAL["water_pale"]); cv.set(28, 11, PAL["water_pale"])
    cv.set(5, 16, PAL["water_pale"]); cv.set(27, 17, PAL["water_pale"])
    cv.set(3, 22, PAL["water_pale"]); cv.set(29, 22, PAL["water_pale"])


def tex_spell_saint_fire(cv: PixelCanvas):
    """Stylized flame — classic campfire shape with bright core."""
    # Outer flame (red-orange)
    outer = []
    for t in range(0, 100):
        s = t / 100.0
        angle = s * 2 * math.pi - math.pi / 2
        # Flame shape: tall and pointed at top, wide at bottom
        if angle < 0:
            r = 11 + math.sin(angle * 2) * 2
        else:
            r = 11 + math.sin(angle * 2) * 2
        x = 16 + r * math.cos(angle)
        y = 16 + r * math.sin(angle) * 1.2
        outer.append((int(x), int(y)))
    # Fill outer flame
    for y in range(32):
        for x in range(32):
            d = math.sqrt(((x - 16) / 1.0) ** 2 + ((y - 16) / 1.2) ** 2)
            if d <= 13 and y < 28:
                grad = (16 - y) / 24.0
                if grad > 0.5:
                    cv.set(x, y, PAL["fire_yellow"])
                elif grad > 0:
                    cv.set(x, y, PAL["fire_orange"])
                else:
                    cv.set(x, y, PAL["fire_red"])
    # Mid flame (orange)
    cv.circle(16, 17, 9, PAL["fire_orange"], fill_color=PAL["fire_yellow"])
    # Inner flame (yellow)
    cv.circle(16, 18, 6, PAL["fire_yellow"], fill_color=PAL["fire_pale"])
    # Core (white-hot)
    cv.circle(16, 19, 3, PAL["fire_pale"], fill_color=PAL["fire_white"])
    # Brightest spot
    cv.set(15, 19, PAL["white"]); cv.set(16, 19, PAL["white"]); cv.set(17, 19, PAL["white"])
    # Flame outline
    for x, y in outer:
        if 0 <= x < 32 and 0 <= y < 32:
            cv.set(x, y, PAL["fire_dark"])
    # Small flame wisps at top
    cv.set(13, 2, PAL["fire_orange"]); cv.set(14, 1, PAL["fire_orange"])
    cv.set(19, 2, PAL["fire_orange"]); cv.set(18, 1, PAL["fire_orange"])
    cv.set(11, 3, PAL["fire_red"]); cv.set(21, 3, PAL["fire_red"])


def tex_spell_emperor_earth(cv: PixelCanvas):
    """Cracked earth block — top-down view with jagged cracks."""
    # Base earth (mid brown)
    cv.fill_all(PAL["earth_mid"])
    # Top-left lighter (sunlit)
    for i in range(32):
        cv.set(i, 0, PAL["earth_light"])
        cv.set(0, i, PAL["earth_light"])
    for i in range(1, 31):
        cv.set(i, 1, PAL["earth_light"])
        cv.set(1, i, PAL["earth_light"])
    # Bottom-right darker (shadow)
    for i in range(32):
        cv.set(i, 31, PAL["earth_dark"])
        cv.set(31, i, PAL["earth_dark"])
    for i in range(2, 30):
        cv.set(i, 30, PAL["earth_dark"])
        cv.set(30, i, PAL["earth_dark"])
    # Add texture (small dots of darker earth)
    texture_pts = [
        (5, 5), (8, 8), (12, 6), (16, 4), (20, 8), (24, 5), (28, 8),
        (4, 12), (8, 14), (12, 12), (16, 14), (20, 12), (24, 14), (28, 12),
        (5, 18), (9, 20), (13, 18), (17, 20), (21, 18), (25, 20), (29, 18),
        (4, 24), (8, 26), (12, 24), (16, 26), (20, 24), (24, 26), (28, 24),
    ]
    for x, y in texture_pts:
        cv.set(x, y, PAL["earth_dark"])
    # Small pebbles (lighter)
    pebble_pts = [
        (7, 7), (11, 9), (15, 7), (19, 9), (23, 7), (27, 9),
        (5, 14), (9, 16), (13, 14), (17, 16), (21, 14), (25, 16), (29, 14),
        (7, 22), (11, 24), (15, 22), (19, 24), (23, 22), (27, 24),
        (5, 28), (9, 28), (13, 28), (17, 28), (21, 28), (25, 28), (29, 28),
    ]
    for x, y in pebble_pts:
        cv.set(x, y, PAL["earth_light"])
    # Main crack (jagged line from top-left to bottom-right)
    crack1 = [(3, 2), (4, 3), (5, 4), (6, 5), (7, 6), (8, 7), (9, 8), (10, 9),
              (11, 10), (12, 11), (13, 12), (14, 13), (15, 14), (15, 15),
              (16, 16), (17, 17), (18, 18), (19, 19), (20, 20), (21, 21),
              (22, 22), (23, 23), (24, 24), (25, 25), (26, 26), (27, 27),
              (28, 28), (29, 29)]
    for x, y in crack1:
        cv.set(x, y, PAL["earth_crack"])
        cv.set(x + 1, y, PAL["earth_crack"])
    # Side crack 1
    crack2 = [(11, 10), (12, 9), (13, 8), (14, 7), (15, 6), (16, 5), (17, 4), (18, 3)]
    for x, y in crack2:
        cv.set(x, y, PAL["earth_crack"])
    # Side crack 2
    crack3 = [(15, 15), (14, 16), (13, 17), (12, 18), (11, 19), (10, 20), (9, 21), (8, 22)]
    for x, y in crack3:
        cv.set(x, y, PAL["earth_crack"])
    # Side crack 3
    crack4 = [(8, 7), (7, 8), (6, 9), (5, 10), (4, 11), (3, 12), (2, 13)]
    for x, y in crack4:
        cv.set(x, y, PAL["earth_crack"])
    # Side crack 4
    crack5 = [(20, 20), (21, 19), (22, 18), (23, 17), (24, 16), (25, 15), (26, 14)]
    for x, y in crack5:
        cv.set(x, y, PAL["earth_crack"])
    # Small debris pieces near cracks
    cv.set(10, 14, PAL["earth_shadow"]); cv.set(19, 12, PAL["earth_shadow"])
    cv.set(14, 20, PAL["earth_shadow"]); cv.set(21, 18, PAL["earth_shadow"])


def tex_spell_storm(cv: PixelCanvas):
    """Storm — swirling wind funnel."""
    # Outer storm cloud (gray)
    cv.circle(16, 16, 14, PAL["storm_dark"], fill_color=PAL["storm_mid"])
    cv.circle(16, 16, 11, PAL["storm_mid"], fill_color=PAL["storm_pale"])
    # Swirling lines (spiral)
    for i in range(4):
        start_angle = i * math.pi / 2
        for t in range(0, 30):
            s = t / 30.0
            angle = start_angle + s * math.pi * 2
            r = 3 + s * 9
            x = int(16 + r * math.cos(angle))
            y = int(16 + r * math.sin(angle))
            if 0 <= x < 32 and 0 <= y < 32:
                cv.set(x, y, PAL["storm_dark"])
    # Center eye
    cv.circle(16, 16, 3, PAL["storm_pale"], fill_color=PAL["white"])
    # Lightning bolts in the storm
    bolt_pts = [(12, 8), (13, 9), (14, 10), (13, 11), (12, 12)]
    for x, y in bolt_pts:
        cv.set(x, y, PAL["light_white"])
    bolt_pts2 = [(20, 20), (19, 21), (18, 22), (19, 23), (20, 24)]
    for x, y in bolt_pts2:
        cv.set(x, y, PAL["light_white"])
    # Rain drops
    for x, y in [(4, 10), (6, 14), (8, 18), (24, 8), (26, 12), (28, 16), (5, 22), (27, 22)]:
        cv.set(x, y, PAL["water_light"])


def tex_spell_atomic_flare(cv: PixelCanvas):
    """Atomic Flare — miniature sun with corona."""
    # Outer corona (orange)
    cv.circle(16, 16, 15, PAL["atomic_orange"], fill_color=(255, 120, 20, 100))
    # Mid corona (yellow)
    cv.circle(16, 16, 12, PAL["atomic_yellow"], fill_color=PAL["atomic_orange"])
    # Sun surface (yellow-orange)
    cv.circle(16, 16, 9, PAL["atomic_yellow"], fill_color=PAL["atomic_pale"])
    # Hot core (white-yellow)
    cv.circle(16, 16, 5, PAL["atomic_pale"], fill_color=PAL["atomic_core"])
    # Brightest center (white)
    cv.circle(16, 16, 2, PAL["atomic_core"], fill_color=PAL["white"])
    # Solar flares (random tendrils)
    flare_pts = [
        (16, 1), (15, 2), (17, 2),
        (1, 16), (2, 15), (2, 17),
        (16, 30), (15, 29), (17, 29),
        (30, 16), (29, 15), (29, 17),
        (5, 5), (6, 6), (7, 7),
        (26, 5), (25, 6), (24, 7),
        (5, 26), (6, 25), (7, 24),
        (26, 26), (25, 25), (24, 24),
    ]
    for x, y in flare_pts:
        cv.set(x, y, PAL["atomic_yellow"])
    # Surface texture (darker sunspots)
    cv.set(13, 14, PAL["atomic_orange"]); cv.set(18, 15, PAL["atomic_orange"])
    cv.set(14, 18, PAL["atomic_orange"]); cv.set(19, 19, PAL["atomic_orange"])
    # Shine
    cv.set(14, 13, PAL["white"]); cv.set(15, 13, PAL["white"])


def tex_spell_gravity(cv: PixelCanvas):
    """Gravity — purple swirl/singularity."""
    # Outer purple aura
    cv.circle(16, 16, 14, PAL["gravity_dark"], fill_color=(50, 20, 90, 100))
    # Spiral arms (purple)
    for i in range(4):
        start_angle = i * math.pi / 2
        for t in range(0, 40):
            s = t / 40.0
            angle = start_angle + s * math.pi * 3
            r = 2 + s * 11
            x = int(16 + r * math.cos(angle))
            y = int(16 + r * math.sin(angle))
            if 0 <= x < 32 and 0 <= y < 32:
                cv.set(x, y, PAL["gravity_mid"])
    # Inner ring
    cv.circle(16, 16, 6, PAL["gravity_mid"], fill_color=PAL["gravity_dark"])
    # Singularity (black core)
    cv.circle(16, 16, 3, PAL["gravity_dark"], fill_color=PAL["black"])
    # Event horizon glow
    cv.circle(16, 16, 1, PAL["gravity_pale"], fill_color=PAL["gravity_pale"])
    # Pull particles (small dots being sucked in)
    for i in range(8):
        angle = i * math.pi / 4
        x = int(16 + 13 * math.cos(angle))
        y = int(16 + 13 * math.sin(angle))
        cv.set(x, y, PAL["gravity_pale"])
        # Trail toward center
        x2 = int(16 + 10 * math.cos(angle))
        y2 = int(16 + 10 * math.sin(angle))
        cv.set(x2, y2, PAL["gravity_mid"])


def tex_spell_quake(cv: PixelCanvas):
    """Quake — earth with expanding crack rings."""
    # Base earth
    cv.fill_all(PAL["earth_mid"])
    # Top-left lighter
    for i in range(32):
        cv.set(i, 0, PAL["earth_light"])
        cv.set(0, i, PAL["earth_light"])
    # 4 expanding crack rings
    for ring_r in [4, 8, 12, 15]:
        for angle_deg in range(0, 360, 8):
            rad = math.radians(angle_deg)
            x = int(16 + ring_r * math.cos(rad))
            y = int(16 + ring_r * math.sin(rad))
            if 0 <= x < 32 and 0 <= y < 32:
                cv.set(x, y, PAL["earth_crack"])
    # Center crack point (impact)
    cv.circle(16, 16, 2, PAL["earth_shadow"], fill_color=PAL["earth_crack"])
    # Falling debris
    cv.set(4, 4, PAL["earth_dark"]); cv.set(28, 4, PAL["earth_dark"])
    cv.set(4, 28, PAL["earth_dark"]); cv.set(28, 28, PAL["earth_dark"])
    cv.set(8, 2, PAL["earth_light"]); cv.set(24, 2, PAL["earth_light"])
    cv.set(2, 16, PAL["earth_light"]); cv.set(29, 16, PAL["earth_light"])
    # Shockwave lines (radiating)
    for angle_deg in range(0, 360, 30):
        rad = math.radians(angle_deg)
        for r in range(13, 16):
            x = int(16 + r * math.cos(rad))
            y = int(16 + r * math.sin(rad))
            if 0 <= x < 32 and 0 <= y < 32:
                cv.set(x, y, PAL["earth_shadow"])


def tex_spell_time_warp(cv: PixelCanvas):
    """Time Warp — clock face with bent hands."""
    # Outer pink aura
    cv.circle(16, 16, 14, PAL["time_dark"], fill_color=(150, 60, 130, 100))
    cv.circle(16, 16, 11, PAL["time_mid"], fill_color=PAL["time_dark"])
    # Clock face (pale)
    cv.circle(16, 16, 8, PAL["time_mid"], fill_color=PAL["time_pale"])
    # Clock hour markers (12, 3, 6, 9)
    cv.set(16, 9, PAL["time_dark"]); cv.set(15, 9, PAL["time_dark"]); cv.set(17, 9, PAL["time_dark"])
    cv.set(23, 16, PAL["time_dark"]); cv.set(23, 15, PAL["time_dark"]); cv.set(23, 17, PAL["time_dark"])
    cv.set(16, 23, PAL["time_dark"]); cv.set(15, 23, PAL["time_dark"]); cv.set(17, 23, PAL["time_dark"])
    cv.set(9, 16, PAL["time_dark"]); cv.set(9, 15, PAL["time_dark"]); cv.set(9, 17, PAL["time_dark"])
    # Minor hour markers
    for angle_deg in range(0, 360, 30):
        rad = math.radians(angle_deg)
        x = int(16 + 7 * math.cos(rad))
        y = int(16 + 7 * math.sin(rad))
        cv.set(x, y, PAL["time_mid"])
    # Clock hands (bent/distorted — time is warped)
    # Hour hand (short, bent)
    cv.line(16, 16, 14, 13, PAL["time_dark"], 1)
    cv.line(14, 13, 13, 12, PAL["time_dark"], 1)
    # Minute hand (long, bent)
    cv.line(16, 16, 19, 14, PAL["time_dark"], 1)
    cv.line(19, 14, 20, 12, PAL["time_dark"], 1)
    cv.line(20, 12, 21, 11, PAL["time_dark"], 1)
    # Center pivot
    cv.set(15, 15, PAL["time_dark"]); cv.set(16, 15, PAL["time_dark"])
    cv.set(15, 16, PAL["time_dark"]); cv.set(16, 16, PAL["time_dark"])
    # Time particles (small dots around clock)
    for i in range(12):
        angle = i * math.pi / 6 + 0.2
        x = int(16 + 13 * math.cos(angle))
        y = int(16 + 13 * math.sin(angle))
        cv.set(x, y, PAL["time_pale"])
    # Top shine
    cv.set(13, 12, PAL["white"]); cv.set(14, 12, PAL["white"])


# === One Piece spell textures (32x32) =======================================

def tex_spell_conquerors(cv: PixelCanvas):
    """Golden crown with aura and jewels — 32x32."""
    # Aura (purple transparent behind crown)
    cv.circle(16, 18, 14, (80, 40, 120, 100), fill_color=(60, 30, 90, 80))
    # Aura sparkles
    cv.set(1, 12, (180, 100, 220, 200)); cv.set(30, 12, (180, 100, 220, 200))
    cv.set(2, 22, (180, 100, 220, 200)); cv.set(29, 22, (180, 100, 220, 200))
    cv.set(16, 1, (180, 100, 220, 200))
    # Crown base (gold band)
    cv.fill(5, 18, 22, 6, PAL["gold_mid"])
    cv.fill(5, 18, 22, 2, PAL["gold_pale"])  # top highlight
    cv.fill(5, 19, 22, 1, PAL["gold_light"])
    cv.fill(5, 23, 22, 1, PAL["gold_dark"])  # bottom shadow
    cv.rect_outline(5, 18, 22, 6, PAL["gold_outline"])
    # Crown spikes (3 pointed peaks)
    cv.fill(5, 12, 4, 6, PAL["gold_light"])
    cv.fill(6, 11, 2, 1, PAL["gold_pale"])
    cv.set(5, 11, PAL["gold_pale"])
    cv.set(4, 11, PAL["gold_shine"])
    cv.fill(14, 8, 4, 10, PAL["gold_light"])
    cv.fill(15, 7, 2, 1, PAL["gold_pale"])
    cv.set(14, 6, PAL["gold_shine"]); cv.set(15, 6, PAL["gold_shine"])
    cv.set(16, 6, PAL["gold_shine"]); cv.set(17, 6, PAL["gold_shine"])
    cv.fill(23, 12, 4, 6, PAL["gold_light"])
    cv.fill(24, 11, 2, 1, PAL["gold_pale"])
    cv.set(23, 11, PAL["gold_pale"])
    cv.set(27, 11, PAL["gold_shine"])
    # Spike tips
    cv.set(5, 10, PAL["gold_shine"]); cv.set(6, 10, PAL["gold_shine"])
    cv.set(15, 5, PAL["gold_shine"]); cv.set(16, 5, PAL["gold_shine"])
    cv.set(25, 10, PAL["gold_shine"]); cv.set(26, 10, PAL["gold_shine"])
    # Spike outlines
    cv.set(4, 13, PAL["gold_outline"]); cv.set(9, 13, PAL["gold_outline"])
    cv.set(13, 9, PAL["gold_outline"]); cv.set(18, 9, PAL["gold_outline"])
    cv.set(22, 13, PAL["gold_outline"]); cv.set(27, 13, PAL["gold_outline"])
    # Center jewel (red ruby)
    cv.set(14, 20, PAL["ruby_red"]); cv.set(15, 20, PAL["ruby_red"])
    cv.set(16, 20, PAL["ruby_red"]); cv.set(17, 20, PAL["ruby_red"])
    cv.set(14, 21, PAL["ruby_dark"]); cv.set(15, 21, PAL["ruby_dark"])
    cv.set(16, 21, PAL["ruby_dark"]); cv.set(17, 21, PAL["ruby_dark"])
    cv.set(14, 20, c(255, 100, 100))  # shine
    # Side jewels (blue sapphires)
    cv.set(7, 20, PAL["sapphire_blue"]); cv.set(8, 20, PAL["sapphire_blue"])
    cv.set(7, 21, PAL["sapphire_dark"]); cv.set(8, 21, PAL["sapphire_dark"])
    cv.set(23, 20, PAL["sapphire_blue"]); cv.set(24, 20, PAL["sapphire_blue"])
    cv.set(23, 21, PAL["sapphire_dark"]); cv.set(24, 21, PAL["sapphire_dark"])
    # Highlights on base
    cv.set(6, 18, PAL["gold_shine"]); cv.set(11, 18, PAL["gold_shine"])
    cv.set(20, 18, PAL["gold_shine"]); cv.set(25, 18, PAL["gold_shine"])


def tex_spell_armament(cv: PixelCanvas):
    """Hardened black fist (Busoshoku Haki) with steam wisps."""
    # Steam wisps (rising from top)
    cv.set(5, 1, PAL["arma_steam"]); cv.set(6, 2, PAL["arma_steam"])
    cv.set(7, 1, PAL["arma_steam"])
    cv.set(25, 1, PAL["arma_steam"]); cv.set(24, 2, PAL["arma_steam"])
    cv.set(23, 1, PAL["arma_steam"])
    cv.set(3, 5, PAL["arma_steam"]); cv.set(28, 7, PAL["arma_steam"])
    cv.set(9, 0, PAL["arma_steam"]); cv.set(22, 0, PAL["arma_steam"])
    # Fist outline
    outline = [
        (8, 6), (9, 5), (10, 5), (11, 5), (12, 5), (13, 5), (14, 5), (15, 5),
        (16, 5), (17, 5), (18, 5), (19, 5), (20, 5), (21, 5), (22, 6),
        (23, 7), (23, 8), (23, 9), (23, 10), (23, 11), (23, 12), (23, 13),
        (23, 14), (23, 15), (23, 16), (23, 17), (23, 18), (23, 19), (23, 20),
        (23, 21), (22, 22), (21, 23), (20, 24), (19, 25), (18, 26), (17, 26),
        (15, 26), (14, 26), (13, 25), (12, 24), (11, 23), (10, 22), (9, 21),
        (8, 20), (8, 19), (8, 18), (8, 17), (8, 16), (8, 15), (8, 14), (8, 13),
        (8, 12), (8, 11), (8, 10), (8, 9), (8, 8), (8, 7),
    ]
    for x, y in outline:
        cv.set(x, y, PAL["arma_outline"])
    # Fist body (dark base)
    body = [
        (9, 7), (10, 6), (11, 6), (12, 6), (13, 6), (14, 6), (15, 6), (16, 6),
        (17, 6), (18, 6), (19, 6), (20, 6), (21, 6), (22, 7),
        (9, 8), (10, 7), (11, 7), (12, 7), (13, 7), (14, 7), (15, 7), (16, 7),
        (17, 7), (18, 7), (19, 7), (20, 7), (21, 7), (22, 8),
        (9, 9), (10, 8), (11, 8), (12, 8), (13, 8), (14, 8), (15, 8), (16, 8),
        (17, 8), (18, 8), (19, 8), (20, 8), (21, 8), (22, 9),
        (9, 10), (10, 9), (11, 9), (12, 9), (13, 9), (14, 9), (15, 9), (16, 9),
        (17, 9), (18, 9), (19, 9), (20, 9), (21, 9), (22, 10),
        (9, 11), (10, 10), (11, 10), (12, 10), (13, 10), (14, 10), (15, 10), (16, 10),
        (17, 10), (18, 10), (19, 10), (20, 10), (21, 10), (22, 11),
        (9, 12), (10, 11), (10, 12), (11, 11), (11, 12), (12, 11), (12, 12),
        (13, 11), (13, 12), (14, 11), (14, 12), (15, 11), (15, 12),
        (16, 11), (16, 12), (17, 11), (17, 12), (18, 11), (18, 12),
        (19, 11), (19, 12), (20, 11), (20, 12), (21, 11), (21, 12), (22, 12),
        (9, 13), (10, 13), (11, 13), (12, 13), (13, 13), (14, 13),
        (15, 13), (16, 13), (17, 13), (18, 13), (19, 13), (20, 13), (21, 13), (22, 13),
        (9, 14), (10, 14), (11, 14), (12, 14), (13, 14), (14, 14),
        (15, 14), (16, 14), (17, 14), (18, 14), (19, 14), (20, 14), (21, 14), (22, 14),
        (9, 15), (10, 15), (11, 15), (12, 15), (13, 15), (14, 15),
        (15, 15), (16, 15), (17, 15), (18, 15), (19, 15), (20, 15), (21, 15), (22, 15),
        (9, 16), (10, 16), (11, 16), (12, 16), (13, 16), (14, 16),
        (15, 16), (16, 16), (17, 16), (18, 16), (19, 16), (20, 16), (21, 16), (22, 16),
        (9, 17), (10, 17), (11, 17), (12, 17), (13, 17), (14, 17),
        (15, 17), (16, 17), (17, 17), (18, 17), (19, 17), (20, 17), (21, 17), (22, 17),
        (9, 18), (10, 18), (11, 18), (12, 18), (13, 18), (14, 18),
        (15, 18), (16, 18), (17, 18), (18, 18), (19, 18), (20, 18), (21, 18), (22, 18),
        (9, 19), (10, 19), (11, 19), (12, 19), (13, 19), (14, 19),
        (15, 19), (16, 19), (17, 19), (18, 19), (19, 19), (20, 19), (21, 19), (22, 19),
        (10, 20), (11, 20), (12, 20), (13, 20), (14, 20), (15, 20),
        (16, 20), (17, 20), (18, 20), (19, 20), (20, 20), (21, 20),
        (10, 21), (11, 21), (12, 21), (13, 21), (14, 21), (15, 21),
        (16, 21), (17, 21), (18, 21), (19, 21), (20, 21), (21, 21),
        (11, 22), (12, 22), (13, 22), (14, 22), (15, 22), (16, 22),
        (17, 22), (18, 22), (19, 22), (20, 22),
        (11, 23), (12, 23), (13, 23), (14, 23), (15, 23), (16, 23),
        (17, 23), (18, 23), (19, 23), (20, 23),
        (12, 24), (13, 24), (14, 24), (15, 24), (16, 24), (17, 24), (18, 24), (19, 24),
    ]
    for x, y in body:
        cv.set(x, y, PAL["arma_mid"])
    # Knuckle highlights (top of fist)
    for x in [10, 13, 16, 19, 22]:
        cv.set(x, 7, PAL["arma_light"])
        cv.set(x, 8, PAL["arma_pale"])
    # Knuckle divisions (lines between fingers)
    cv.set(11, 9, PAL["arma_dark"]); cv.set(11, 10, PAL["arma_dark"]); cv.set(11, 11, PAL["arma_dark"])
    cv.set(14, 9, PAL["arma_dark"]); cv.set(14, 10, PAL["arma_dark"]); cv.set(14, 11, PAL["arma_dark"])
    cv.set(17, 9, PAL["arma_dark"]); cv.set(17, 10, PAL["arma_dark"]); cv.set(17, 11, PAL["arma_dark"])
    cv.set(20, 9, PAL["arma_dark"]); cv.set(20, 10, PAL["arma_dark"]); cv.set(20, 11, PAL["arma_dark"])
    # Shadow on bottom of fist
    for x in range(11, 22):
        cv.set(x, 21, PAL["arma_dark"])
    for x in range(12, 21):
        cv.set(x, 22, PAL["arma_dark"])


def tex_spell_gomu_pistol(cv: PixelCanvas):
    """Stretching red fist — Gomu Gomu no Pistol."""
    # Shoulder (bottom-left, dark red)
    cv.fill(1, 24, 6, 7, PAL["gomu_dark"])
    cv.fill(2, 25, 4, 5, PAL["gomu_mid"])
    cv.set(2, 25, PAL["gomu_pale"])  # highlight
    # Stretched arm (diagonal line from shoulder to fist)
    arm_pts = []
    for t in range(0, 100):
        s = t / 100.0
        x = int(4 + (22 - 4) * s)
        y = int(26 + (5 - 26) * s)
        arm_pts.append((x, y))
    for x, y in arm_pts:
        cv.set(x, y, PAL["gomu_mid"])
        cv.set(x + 1, y, PAL["gomu_mid"])
    # Arm outline (dark)
    arm_outline = []
    for t in range(0, 100):
        s = t / 100.0
        x = int(3 + (22 - 3) * s)
        y = int(27 + (5 - 27) * s)
        arm_outline.append((x, y))
    for x, y in arm_outline:
        cv.set(x, y, PAL["gomu_dark"])
    arm_outline2 = []
    for t in range(0, 100):
        s = t / 100.0
        x = int(6 + (24 - 6) * s)
        y = int(25 + (4 - 25) * s)
        arm_outline2.append((x, y))
    for x, y in arm_outline2:
        cv.set(x, y, PAL["gomu_dark"])
    # Fist (top-right, big red rubber ball)
    cv.fill(22, 2, 8, 8, PAL["gomu_light"])
    cv.fill(23, 3, 6, 6, PAL["gomu_pale"])
    # Fist outline
    cv.rect_outline(22, 2, 8, 8, PAL["gomu_outline"])
    # Knuckle highlights
    cv.set(23, 3, PAL["gomu_shine"]); cv.set(26, 3, PAL["gomu_shine"])
    cv.set(24, 5, PAL["gomu_shine"])
    # Knuckle divisions
    cv.set(25, 3, PAL["gomu_dark"]); cv.set(25, 4, PAL["gomu_dark"])
    cv.set(27, 5, PAL["gomu_dark"]); cv.set(27, 6, PAL["gomu_dark"])
    # Motion lines (showing stretch speed)
    cv.set(13, 17, PAL["gomu_pale"]); cv.set(14, 16, PAL["gomu_pale"])
    cv.set(15, 15, PAL["gomu_pale"]); cv.set(16, 14, PAL["gomu_pale"])
    cv.set(11, 19, PAL["gomu_pale"]); cv.set(12, 18, PAL["gomu_pale"])
    # Impact star (top-right corner, where fist is going)
    cv.set(29, 0, PAL["white"]); cv.set(30, 0, PAL["white"])
    cv.set(30, 1, PAL["white"]); cv.set(29, 1, PAL["white"])
    cv.set(28, 0, PAL["white"]); cv.set(31, 1, PAL["white"])


def tex_spell_gear_second(cv: PixelCanvas):
    """Gear Second — red fist with steam aura."""
    # Steam aura (lots of steam particles)
    for x, y in [(4, 2), (5, 3), (6, 2), (7, 3), (8, 2),
                 (24, 2), (25, 3), (26, 2), (27, 3), (28, 2),
                 (2, 8), (3, 9), (2, 14), (3, 15), (2, 20), (3, 21),
                 (29, 8), (28, 9), (29, 14), (28, 15), (29, 20), (28, 21),
                 (4, 28), (28, 28)]:
        cv.set(x, y, PAL["arma_steam"])
    # Fist (red, central)
    cv.fill(10, 10, 12, 12, PAL["gomu_light"])
    cv.fill(11, 11, 10, 10, PAL["gomu_pale"])
    # Fist outline
    cv.rect_outline(10, 10, 12, 12, PAL["gomu_outline"])
    # Knuckle highlights (vibrating from gear second)
    for x in [11, 14, 17, 20]:
        cv.set(x, 11, PAL["gomu_shine"])
        cv.set(x, 12, PAL["gomu_shine"])
    # Knuckle divisions
    cv.set(13, 12, PAL["gomu_dark"]); cv.set(13, 13, PAL["gomu_dark"])
    cv.set(16, 12, PAL["gomu_dark"]); cv.set(16, 13, PAL["gomu_dark"])
    cv.set(19, 12, PAL["gomu_dark"]); cv.set(19, 13, PAL["gomu_dark"])
    # Speed lines (motion blur effect)
    for x in [3, 4, 5, 26, 27, 28]:
        for y in [13, 14, 15, 16, 17, 18]:
            cv.set(x, y, PAL["gomu_pale"])
    # Center shine
    cv.set(14, 14, PAL["white"]); cv.set(15, 14, PAL["white"])
    cv.set(14, 15, PAL["white"])


def tex_spell_gear_third(cv: PixelCanvas):
    """Gear Third — giant inflated fist."""
    # Giant fist (takes most of the texture)
    cv.fill(4, 4, 24, 24, PAL["gomu_light"])
    cv.fill(6, 6, 20, 20, PAL["gomu_pale"])
    # Fist outline (thick)
    cv.rect_outline(4, 4, 24, 24, PAL["gomu_outline"])
    cv.rect_outline(5, 5, 22, 22, PAL["gomu_dark"])
    # Knuckle highlights (large)
    for x in [8, 12, 16, 20, 24]:
        for y in [6, 7, 8]:
            cv.set(x, y, PAL["gomu_shine"])
    # Knuckle divisions (deep grooves)
    for x in [10, 14, 18, 22]:
        for y in range(6, 14):
            cv.set(x, y, PAL["gomu_dark"])
    # Steam wisps
    cv.set(2, 8, PAL["arma_steam"]); cv.set(3, 9, PAL["arma_steam"])
    cv.set(29, 8, PAL["arma_steam"]); cv.set(28, 9, PAL["arma_steam"])
    cv.set(2, 22, PAL["arma_steam"]); cv.set(29, 22, PAL["arma_steam"])
    # Center shine (giant)
    cv.set(14, 14, PAL["white"]); cv.set(15, 14, PAL["white"])
    cv.set(16, 14, PAL["white"])
    cv.set(14, 15, PAL["white"]); cv.set(15, 15, PAL["white"])
    cv.set(16, 15, PAL["white"])
    cv.set(14, 16, PAL["white"]); cv.set(15, 16, PAL["white"])
    cv.set(16, 16, PAL["white"])


def tex_spell_gear_fourth(cv: PixelCanvas):
    """Gear Fourth (Boundman) — black Haki-coated fist."""
    # Haki aura (black/purple)
    cv.circle(16, 16, 15, PAL["gear4_dark"], fill_color=(15, 5, 5, 100))
    # Giant fist (black with red tint)
    cv.fill(5, 5, 22, 22, PAL["gear4_dark"])
    cv.fill(7, 7, 18, 18, PAL["gear4_mid"])
    cv.fill(9, 9, 14, 14, PAL["gear4_pale"])
    # Fist outline (Haki black)
    cv.rect_outline(5, 5, 22, 22, PAL["gear4_dark"])
    cv.rect_outline(6, 6, 20, 20, PAL["black"])
    # Haki swirl patterns (curved lines)
    for i in range(4):
        start_y = 8 + i * 5
        for x in range(8, 25):
            offset = int(math.sin(x * 0.3) * 2)
            cv.set(x, start_y + offset, PAL["black"])
    # Knuckle highlights (red glow under Haki)
    for x in [10, 14, 18, 22]:
        cv.set(x, 8, PAL["gear4_pale"])
        cv.set(x, 9, PAL["gear4_pale"])
    # Center crackle (Haki energy)
    cv.set(15, 15, PAL["gomu_light"]); cv.set(16, 15, PAL["gomu_light"])
    cv.set(15, 16, PAL["gomu_light"]); cv.set(16, 16, PAL["gomu_light"])
    # Steam wisps (dense)
    for x, y in [(2, 4), (3, 5), (29, 4), (28, 5), (2, 28), (29, 28)]:
        cv.set(x, y, PAL["arma_steam"])


def tex_spell_observation_haki(cv: PixelCanvas):
    """Observation Haki — eye with aura."""
    # Outer aura (cyan/green)
    cv.circle(16, 16, 15, PAL["rune_dark"], fill_color=(20, 80, 60, 100))
    cv.circle(16, 16, 12, PAL["rune_mid"], fill_color=PAL["rune_light"])
    # Eye shape (almond)
    for x in range(6, 27):
        for y in range(13, 20):
            # Almond shape
            dy = abs(y - 16)
            dx = abs(x - 16)
            if dx + dy * 2 < 11:
                cv.set(x, y, PAL["white"])
    # Eye outline
    for x in range(6, 27):
        dy_top = abs(13 - 16)
        dy_bot = abs(19 - 16)
        if x == 6 or x == 26:
            cv.set(x, 16, PAL["black"])
        # Top and bottom curves
        dx = abs(x - 16)
        if dx < 10:
            top_y = 16 - max(1, 4 - dx // 3)
            bot_y = 16 + max(1, 4 - dx // 3)
            cv.set(x, top_y, PAL["black"])
            cv.set(x, bot_y, PAL["black"])
    # Iris (cyan)
    cv.circle(16, 16, 4, PAL["rune_dark"], fill_color=PAL["rune_light"])
    cv.circle(16, 16, 3, PAL["rune_mid"], fill_color=PAL["rune_pale"])
    # Pupil (black)
    cv.set(15, 15, PAL["black"]); cv.set(16, 15, PAL["black"])
    cv.set(15, 16, PAL["black"]); cv.set(16, 16, PAL["black"])
    cv.set(17, 15, PAL["black"]); cv.set(17, 16, PAL["black"])
    cv.set(15, 17, PAL["black"]); cv.set(16, 17, PAL["black"])
    # Eye shine
    cv.set(14, 14, PAL["white"]); cv.set(15, 14, PAL["white"])
    # Aura particles (precognition waves)
    for angle_deg in range(0, 360, 30):
        rad = math.radians(angle_deg)
        x = int(16 + 14 * math.cos(rad))
        y = int(16 + 14 * math.sin(rad))
        cv.set(x, y, PAL["rune_pale"])


def tex_spell_voice_of_all_things(cv: PixelCanvas):
    """Voice of All Things — white aura with rainbow waves."""
    # Outer aura (white)
    cv.circle(16, 16, 15, PAL["voice_pale"], fill_color=(255, 250, 200, 100))
    cv.circle(16, 16, 12, PAL["voice_shine"], fill_color=PAL["voice_pale"])
    # Rainbow waves (concentric rings of different colors)
    rainbow_colors = [PAL["rainbow_red"], PAL["rainbow_orange"], PAL["rainbow_yellow"],
                      PAL["rainbow_green"], PAL["rainbow_blue"], PAL["rainbow_purple"]]
    for i, color in enumerate(rainbow_colors):
        r = 10 - i
        cv.circle(16, 16, r, color)
    # Center (white glow)
    cv.circle(16, 16, 4, PAL["voice_shine"], fill_color=PAL["white"])
    cv.circle(16, 16, 2, PAL["white"], fill_color=PAL["white"])
    # Sound wave particles (radiating outward)
    for angle_deg in range(0, 360, 15):
        rad = math.radians(angle_deg)
        for r in [12, 14]:
            x = int(16 + r * math.cos(rad))
            y = int(16 + r * math.sin(rad))
            cv.set(x, y, PAL["voice_shine"])
    # Music notes (small)
    cv.set(4, 4, PAL["voice_shine"]); cv.set(28, 4, PAL["voice_shine"])
    cv.set(4, 28, PAL["voice_shine"]); cv.set(28, 28, PAL["voice_shine"])


# === 3D model textures (wrap cube geometry) =================================

def tex_model_magic_orb(cv: PixelCanvas):
    """Magic orb texture — wraps a cube to look like a glowing orb."""
    cv.fill_all(PAL["magicule_dark"])
    cv.circle(16, 16, 15, (140, 60, 200, 100), fill_color=(100, 40, 170, 120))
    cv.circle(16, 16, 11, PAL["magicule_outline"], fill_color=PAL["magicule_mid"])
    cv.circle(16, 16, 9, PAL["magicule_mid"], fill_color=PAL["magicule_light"])
    cv.circle(16, 16, 5, PAL["magicule_light"], fill_color=PAL["magicule_pale"])
    cv.circle(16, 16, 2, PAL["magicule_pale"], fill_color=PAL["white"])
    # Rotating rune marks
    for angle_deg in [0, 90, 180, 270]:
        rad = math.radians(angle_deg)
        x = int(16 + 13 * math.cos(rad))
        y = int(16 + 13 * math.sin(rad))
        cv.set(x, y, PAL["white"])
        cv.set(x + 1, y, PAL["magicule_shine"])
        cv.set(x - 1, y, PAL["magicule_shine"])
        cv.set(x, y + 1, PAL["magicule_shine"])
        cv.set(x, y - 1, PAL["magicule_shine"])
    # Bright highlight (top-left)
    cv.set(11, 11, PAL["white"]); cv.set(12, 11, PAL["white"])
    cv.set(11, 12, PAL["white"]); cv.set(12, 12, PAL["white"])
    cv.set(13, 11, PAL["white"])
    # Edge darkening
    for i in range(32):
        cv.set(i, 0, PAL["magicule_outline"])
        cv.set(i, 31, PAL["magicule_outline"])
        cv.set(0, i, PAL["magicule_outline"])
        cv.set(31, i, PAL["magicule_outline"])


def tex_model_chidori_blade(cv: PixelCanvas):
    """Lightning blade texture — wraps blade+hilt cube model."""
    # Blade (vertical, central)
    for y in range(1, 26):
        cv.set(14, y, PAL["light_white"]); cv.set(15, y, PAL["light_white"])
        cv.set(16, y, PAL["light_white"]); cv.set(17, y, PAL["light_white"])
        cv.set(13, y, PAL["light_blue"]); cv.set(18, y, PAL["light_blue"])
    # Lightning crackle pattern (jagged lines along blade)
    for y in range(2, 25, 3):
        cv.set(11, y, PAL["light_blue"]); cv.set(20, y, PAL["light_blue"])
        cv.set(10, y + 1, PAL["light_deep"]); cv.set(21, y + 1, PAL["light_deep"])
    # Bright core highlights
    for y in range(3, 25, 4):
        cv.set(15, y, PAL["light_core"]); cv.set(16, y, PAL["light_core"])
    # Tip (sharp)
    cv.set(14, 0, PAL["light_core"]); cv.set(15, 0, PAL["light_core"])
    cv.set(16, 0, PAL["light_core"]); cv.set(17, 0, PAL["light_core"])
    # Hilt (crossguard, brown)
    cv.fill(10, 26, 12, 2, c(140, 90, 40))
    cv.fill(10, 28, 12, 2, c(80, 50, 20))
    cv.set(10, 26, PAL["gold_dark"]); cv.set(21, 26, PAL["gold_dark"])
    cv.set(10, 29, c(40, 25, 10)); cv.set(21, 29, c(40, 25, 10))
    # Glow particles floating beside blade
    cv.set(7, 4, PAL["light_glow"]); cv.set(24, 5, PAL["light_glow"])
    cv.set(7, 10, PAL["light_glow"]); cv.set(24, 11, PAL["light_glow"])
    cv.set(7, 16, PAL["light_glow"]); cv.set(24, 17, PAL["light_glow"])
    cv.set(7, 22, PAL["light_glow"]); cv.set(24, 23, PAL["light_glow"])
    # Top spark
    cv.set(15, 0, PAL["white"]); cv.set(16, 0, PAL["white"])


def tex_model_rasengan_sphere(cv: PixelCanvas):
    """Rasengan sphere texture — wraps a cube to look like swirling sphere."""
    cv.fill_all(PAL["rasengan_ring"])
    cv.circle(16, 16, 15, PAL["rasengan_ring"], fill_color=PAL["rasengan_dark"])
    cv.circle(16, 16, 12, PAL["rasengan_dark"], fill_color=PAL["rasengan_mid"])
    cv.circle(16, 16, 8, PAL["rasengan_mid"], fill_color=PAL["rasengan_light"])
    cv.circle(16, 16, 4, PAL["rasengan_light"], fill_color=PAL["rasengan_pale"])
    cv.circle(16, 16, 1, PAL["rasengan_pale"], fill_color=PAL["white"])
    # Spiral arms (6 curved lines from outer to inner)
    for angle_deg in range(0, 360, 60):
        rad = math.radians(angle_deg)
        for r in range(3, 12):
            x = int(16 + r * math.cos(rad + r * 0.2))
            y = int(16 + r * math.sin(rad + r * 0.2))
            cv.set(x, y, PAL["rasengan_pale"])
        # Inner segment
        x = int(16 + 1 * math.cos(rad))
        y = int(16 + 1 * math.sin(rad))
        cv.set(x, y, PAL["white"])
    # Bright highlight (top-left)
    cv.set(11, 11, PAL["white"]); cv.set(12, 11, PAL["white"])
    cv.set(11, 12, PAL["white"])
    # Cardinal motion lines
    cv.set(16, 0, PAL["rasengan_pale"]); cv.set(15, 0, PAL["rasengan_pale"]); cv.set(17, 0, PAL["rasengan_pale"])
    cv.set(16, 31, PAL["rasengan_pale"]); cv.set(15, 31, PAL["rasengan_pale"]); cv.set(17, 31, PAL["rasengan_pale"])
    cv.set(0, 16, PAL["rasengan_pale"]); cv.set(0, 15, PAL["rasengan_pale"]); cv.set(0, 17, PAL["rasengan_pale"])
    cv.set(31, 16, PAL["rasengan_pale"]); cv.set(31, 15, PAL["rasengan_pale"]); cv.set(31, 17, PAL["rasengan_pale"])


def tex_model_fireball_orb(cv: PixelCanvas):
    """Fireball orb texture — wraps a cube to look like a fire orb."""
    cv.fill_all(PAL["fire_dark"])
    cv.circle(16, 16, 15, PAL["fire_smoke"], fill_color=PAL["fire_dark"])
    cv.circle(16, 16, 12, PAL["fire_dark"], fill_color=PAL["fire_red"])
    cv.circle(16, 16, 9, PAL["fire_red"], fill_color=PAL["fire_orange"])
    cv.circle(16, 16, 6, PAL["fire_orange"], fill_color=PAL["fire_yellow"])
    cv.circle(16, 16, 3, PAL["fire_yellow"], fill_color=PAL["fire_white"])
    cv.circle(16, 16, 1, PAL["fire_white"], fill_color=PAL["white"])
    # Flame wisps at edges
    cv.set(2, 16, PAL["fire_orange"]); cv.set(29, 16, PAL["fire_orange"])
    cv.set(16, 2, PAL["fire_orange"]); cv.set(16, 29, PAL["fire_orange"])
    # Bright highlight
    cv.set(12, 12, PAL["white"]); cv.set(13, 12, PAL["white"])
    cv.set(12, 13, PAL["white"])
    # Edge darkening
    for i in range(32):
        cv.set(i, 0, PAL["fire_dark"]); cv.set(i, 31, PAL["fire_dark"])
        cv.set(0, i, PAL["fire_dark"]); cv.set(31, i, PAL["fire_dark"])


def tex_model_haki_dome(cv: PixelCanvas):
    """Haki dome texture — wraps a cube to look like a shockwave dome."""
    cv.fill_all((20, 5, 30, 0))  # transparent
    # Outer shockwave ring (purple)
    cv.circle(16, 16, 15, PAL["magicule_outline"], fill_color=(60, 20, 90, 100))
    cv.circle(16, 16, 13, PAL["magicule_dark"], fill_color=(80, 30, 120, 80))
    cv.circle(16, 16, 10, (80, 30, 120, 60), fill_color=(100, 40, 150, 50))
    # Inner transparent
    cv.circle(16, 16, 7, (100, 40, 150, 30), fill_color=(0, 0, 0, 0))
    # Crackling lightning around the dome
    for angle_deg in range(0, 360, 30):
        rad = math.radians(angle_deg)
        x = int(16 + 14 * math.cos(rad))
        y = int(16 + 14 * math.sin(rad))
        cv.set(x, y, PAL["magicule_pale"])
        # Lightning from edge inward
        for r in range(10, 14):
            xl = int(16 + r * math.cos(rad))
            yl = int(16 + r * math.sin(rad))
            cv.set(xl, yl, PAL["magicule_shine"])
    # Center void
    cv.circle(16, 16, 3, (0, 0, 0, 0), fill_color=(0, 0, 0, 0))


def tex_model_magicule_sword(cv: PixelCanvas):
    """Magicule sword texture — wraps a sword cube model."""
    # Blade (vertical, purple gradient)
    for y in range(2, 26):
        cv.set(13, y, PAL["magicule_dark"]); cv.set(18, y, PAL["magicule_dark"])
        cv.set(14, y, PAL["magicule_mid"]); cv.set(17, y, PAL["magicule_mid"])
        cv.set(15, y, PAL["magicule_light"]); cv.set(16, y, PAL["magicule_light"])
    # Blade highlights (every other row)
    for y in range(3, 25, 2):
        cv.set(15, y, PAL["magicule_shine"])
        cv.set(16, y, PAL["magicule_pale"])
    # Blade tip
    cv.set(15, 1, PAL["magicule_light"]); cv.set(16, 1, PAL["magicule_pale"])
    cv.set(15, 0, PAL["magicule_shine"])
    # Crossguard (gold, wide)
    cv.fill(9, 26, 14, 2, PAL["gold_dark"])
    cv.fill(9, 27, 14, 2, PAL["gold_light"])
    cv.fill(9, 26, 14, 1, PAL["gold_pale"])
    cv.set(9, 26, PAL["gold_dark"]); cv.set(22, 26, PAL["gold_dark"])
    cv.set(9, 28, PAL["gold_dark"]); cv.set(22, 28, PAL["gold_dark"])
    # Handle (dark brown wrapped)
    cv.fill(13, 29, 6, 3, c(80, 50, 20))
    # Wrap pattern (crisscross)
    for y in [29, 31]:
        cv.set(13, y, c(40, 25, 10)); cv.set(14, y, c(40, 25, 10))
        cv.set(15, y, c(40, 25, 10)); cv.set(16, y, c(40, 25, 10))
        cv.set(17, y, c(40, 25, 10)); cv.set(18, y, c(40, 25, 10))
    for y in [30]:
        cv.set(14, y, c(140, 90, 40)); cv.set(15, y, c(140, 90, 40))
        cv.set(16, y, c(140, 90, 40)); cv.set(17, y, c(140, 90, 40))
    # Pommel (gold)
    cv.set(13, 31, PAL["gold_light"]); cv.set(14, 31, PAL["gold_shine"])
    cv.set(15, 31, PAL["gold_shine"]); cv.set(16, 31, PAL["gold_shine"])
    cv.set(17, 31, PAL["gold_light"]); cv.set(18, 31, PAL["gold_dark"])
    # Magicule glow particles around blade
    cv.set(9, 6, PAL["magicule_shine"]); cv.set(22, 10, PAL["magicule_shine"])
    cv.set(9, 14, PAL["magicule_pale"]); cv.set(22, 18, PAL["magicule_pale"])
    cv.set(9, 22, PAL["magicule_pale"])


def tex_model_rasenshuriken(cv: PixelCanvas):
    """Rasenshuriken texture — 4-bladed wind shuriken."""
    # 4 blades arranged in X pattern
    for i in range(4):
        angle = i * math.pi / 2
        for r in range(3, 15):
            for w in range(-2, 3):
                x = int(16 + r * math.cos(angle) - w * math.sin(angle))
                y = int(16 + r * math.sin(angle) + w * math.cos(angle))
                if 0 <= x < 32 and 0 <= y < 32:
                    dist = abs(w) + abs(r - 10) * 0.1
                    if dist < 0.5:
                        cv.set(x, y, PAL["rasengan_white"])
                    elif dist < 1.5:
                        cv.set(x, y, PAL["rasengan_pale"])
                    elif dist < 2.5:
                        cv.set(x, y, PAL["rasengan_light"])
                    else:
                        cv.set(x, y, PAL["rasengan_mid"])
    # Center sphere
    cv.circle(16, 16, 4, PAL["rasengan_dark"], fill_color=PAL["rasengan_mid"])
    cv.circle(16, 16, 2.5, PAL["rasengan_mid"], fill_color=PAL["rasengan_light"])
    cv.circle(16, 16, 1, PAL["rasengan_light"], fill_color=PAL["white"])
    cv.set(15, 15, PAL["white"])


def tex_model_kirin_bolt(cv: PixelCanvas):
    """Kirin bolt texture — vertical lightning."""
    # Transparent background
    # Main bolt (vertical, center)
    for y in range(32):
        cv.set(14, y, PAL["light_white"]); cv.set(15, y, PAL["light_white"])
        cv.set(16, y, PAL["light_white"]); cv.set(17, y, PAL["light_white"])
        cv.set(13, y, PAL["light_blue"]); cv.set(18, y, PAL["light_blue"])
    # Bright core
    for y in range(0, 32, 2):
        cv.set(15, y, PAL["light_core"]); cv.set(16, y, PAL["light_core"])
    # Side branches
    for y in range(2, 30, 4):
        cv.set(11, y, PAL["light_blue"]); cv.set(20, y, PAL["light_blue"])
        cv.set(10, y + 1, PAL["light_deep"]); cv.set(21, y + 1, PAL["light_deep"])
    # Top and bottom flares
    cv.set(13, 0, PAL["light_pale"]); cv.set(14, 0, PAL["light_white"])
    cv.set(15, 0, PAL["white"]); cv.set(16, 0, PAL["white"])
    cv.set(17, 0, PAL["light_white"]); cv.set(18, 0, PAL["light_pale"])
    cv.set(13, 31, PAL["light_pale"]); cv.set(14, 31, PAL["light_white"])
    cv.set(15, 31, PAL["white"]); cv.set(16, 31, PAL["white"])
    cv.set(17, 31, PAL["light_white"]); cv.set(18, 31, PAL["light_pale"])


def tex_model_sage_aura(cv: PixelCanvas):
    """Sage aura texture — orange/gold swirling aura."""
    # Transparent background
    # Outer aura ring (orange)
    cv.circle(16, 16, 14, PAL["sage_dark"], fill_color=(180, 115, 20, 80))
    cv.circle(16, 16, 11, PAL["sage_mid"], fill_color=PAL["sage_light"])
    cv.circle(16, 16, 8, PAL["sage_light"], fill_color=PAL["sage_pale"])
    # Inner void (transparent)
    cv.circle(16, 16, 5, (255, 245, 180, 30), fill_color=(0, 0, 0, 0))
    # Swirling energy lines
    for i in range(6):
        start_angle = i * math.pi / 3
        for t in range(0, 30):
            s = t / 30.0
            angle = start_angle + s * math.pi * 2
            r = 6 + s * 6
            x = int(16 + r * math.cos(angle))
            y = int(16 + r * math.sin(angle))
            if 0 <= x < 32 and 0 <= y < 32:
                cv.set(x, y, PAL["sage_pale"])
    # Top shine
    cv.set(13, 10, PAL["white"]); cv.set(14, 10, PAL["white"])
    cv.set(15, 10, PAL["white"]); cv.set(16, 10, PAL["white"])


def tex_model_phoenix_flower(cv: PixelCanvas):
    """Phoenix flower texture — small fire orb."""
    # Transparent background
    cv.circle(16, 16, 12, PAL["fire_smoke"], fill_color=PAL["fire_dark"])
    cv.circle(16, 16, 9, PAL["fire_dark"], fill_color=PAL["fire_red"])
    cv.circle(16, 16, 6, PAL["fire_red"], fill_color=PAL["fire_orange"])
    cv.circle(16, 16, 3, PAL["fire_orange"], fill_color=PAL["fire_yellow"])
    cv.circle(16, 16, 1, PAL["fire_yellow"], fill_color=PAL["fire_white"])
    # Flame wisps
    cv.set(2, 16, PAL["fire_orange"]); cv.set(29, 16, PAL["fire_orange"])
    cv.set(16, 2, PAL["fire_orange"]); cv.set(16, 29, PAL["fire_orange"])
    # Bright highlight
    cv.set(13, 13, PAL["white"]); cv.set(14, 13, PAL["white"])
    cv.set(13, 14, PAL["white"])


def tex_model_lightning_aura(cv: PixelCanvas):
    """Lightning aura texture — vertical lightning field."""
    # Transparent background
    # Multiple vertical bolts
    for x in [10, 16, 22]:
        for y in range(2, 30):
            cv.set(x, y, PAL["light_blue"])
            cv.set(x + 1, y, PAL["light_white"])
    # Bright cores
    for x in [11, 17, 23]:
        for y in range(4, 28, 2):
            cv.set(x, y, PAL["light_core"])
    # Side crackles
    for y in range(2, 30, 3):
        cv.set(6, y, PAL["light_blue"]); cv.set(26, y, PAL["light_blue"])
        cv.set(5, y + 1, PAL["light_deep"]); cv.set(27, y + 1, PAL["light_deep"])
    # Top flare
    for x in range(8, 25):
        cv.set(x, 0, PAL["light_pale"]); cv.set(x, 1, PAL["light_white"])
    # Bottom flare
    for x in range(8, 25):
        cv.set(x, 30, PAL["light_pale"]); cv.set(x, 31, PAL["light_white"])


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
    # Naruto spells (original 4)
    ("spell_fireball.png",        tex_spell_fireball),
    ("spell_chidori.png",         tex_spell_chidori),
    ("spell_rasengan.png",        tex_spell_rasengan),
    ("spell_shadow_clone.png",    tex_spell_shadow_clone),
    # Naruto ultimates (new 5)
    ("spell_phoenix_flower.png",  tex_spell_phoenix_flower),
    ("spell_rasenshuriken.png",   tex_spell_rasenshuriken),
    ("spell_kirin.png",           tex_spell_kirin),
    ("spell_sage_mode.png",       tex_spell_sage_mode),
    ("spell_six_paths.png",       tex_spell_six_paths),
    # Tensura spells (original 3)
    ("spell_magicule_blade.png",  tex_spell_magicule_blade),
    ("spell_gluttony.png",        tex_spell_gluttony),
    ("spell_razor_edge.png",      tex_spell_razor_edge),
    # Tensura ultimates (new 5)
    ("spell_disintegration.png",  tex_spell_disintegration),
    ("spell_beelzebuth.png",      tex_spell_beelzebuth),
    ("spell_megiddo.png",         tex_spell_megiddo),
    ("spell_raphael.png",         tex_spell_raphael),
    ("spell_true_dragon.png",     tex_spell_true_dragon),
    # Mushoku spells (original 3)
    ("spell_saint_water.png",     tex_spell_saint_water),
    ("spell_saint_fire.png",      tex_spell_saint_fire),
    ("spell_emperor_earth.png",   tex_spell_emperor_earth),
    # Mushoku ultimates (new 5)
    ("spell_storm.png",           tex_spell_storm),
    ("spell_atomic_flare.png",    tex_spell_atomic_flare),
    ("spell_gravity.png",         tex_spell_gravity),
    ("spell_quake.png",           tex_spell_quake),
    ("spell_time_warp.png",       tex_spell_time_warp),
    # One Piece spells (original 3)
    ("spell_conquerors.png",      tex_spell_conquerors),
    ("spell_armament.png",        tex_spell_armament),
    ("spell_gomu_pistol.png",     tex_spell_gomu_pistol),
    # One Piece ultimates (new 5)
    ("spell_gear_second.png",     tex_spell_gear_second),
    ("spell_gear_third.png",      tex_spell_gear_third),
    ("spell_gear_fourth.png",     tex_spell_gear_fourth),
    ("spell_observation_haki.png", tex_spell_observation_haki),
    ("spell_voice_of_all_things.png", tex_spell_voice_of_all_things),
    # 3D model textures
    ("paper_7001.png",            tex_model_magic_orb),
    ("prismarine_shard_7002.png", tex_model_chidori_blade),
    ("snowball_7003.png",         tex_model_rasengan_sphere),
    ("fire_charge_7004.png",      tex_model_fireball_orb),
    ("purple_glazed_terracotta_7005.png", tex_model_haki_dome),
    ("diamond_sword_7006.png",    tex_model_magicule_sword),
    ("snowball_7008.png",         tex_model_rasenshuriken),
    ("prismarine_shard_7009.png", tex_model_kirin_bolt),
    ("nether_star_7010.png",      tex_model_sage_aura),
    ("fire_charge_7011.png",      tex_model_phoenix_flower),
    ("prismarine_shard_7007.png", tex_model_lightning_aura),
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
        (7010, "anime_magic:item/nether_star_7010"),
    ],
    "fire_charge": [
        (3001, "anime_magic:item/spell_fireball"),
        (7004, "anime_magic:item/fire_charge_7004"),
        (7011, "anime_magic:item/fire_charge_7011"),
    ],
    "prismarine_shard": [
        (3002, "anime_magic:item/spell_chidori"),
        (7002, "anime_magic:item/prismarine_shard_7002"),
        (7007, "anime_magic:item/prismarine_shard_7007"),
        (7009, "anime_magic:item/prismarine_shard_7009"),
    ],
    "snowball": [
        (3003, "anime_magic:item/spell_rasengan"),
        (7003, "anime_magic:item/snowball_7003"),
        (7008, "anime_magic:item/snowball_7008"),
    ],
    "soul_sand": [(3004, "anime_magic:item/spell_shadow_clone")],
    "diamond_sword": [
        (4001, "anime_magic:item/spell_magicule_blade"),
        (7006, "anime_magic:item/diamond_sword_7006"),
    ],
    "black_dye": [(4002, "anime_magic:item/spell_gluttony")],
    "netherite_sword": [(4003, "anime_magic:item/spell_razor_edge")],
    "water_bucket": [(5001, "anime_magic:item/spell_saint_water")],
    "blaze_powder": [(5002, "anime_magic:item/spell_saint_fire")],
    "stone": [(5003, "anime_magic:item/spell_emperor_earth")],
    "purple_glazed_terracotta": [
        (6001, "anime_magic:item/spell_conquerors"),
        (7005, "anime_magic:item/purple_glazed_terracotta_7005"),
    ],
    "obsidian": [(6002, "anime_magic:item/spell_armament")],
    "red_dye": [(6003, "anime_magic:item/spell_gomu_pistol")],
}


# === Advanced multi-cube 3D models ==========================================
# These have multiple cube elements for more interesting geometry.

def make_3d_orb_model(texture_path):
    """Multi-cube orb: outer shell + inner glow cube + bright center."""
    return {
        "textures": {"layer0": texture_path},
        "elements": [
            # Outer shell (8x8x8)
            {
                "from": [4, 4, 4], "to": [12, 12, 12],
                "faces": {
                    "north": {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                    "east":  {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                    "south": {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                    "west":  {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                    "up":    {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                    "down":  {"uv": [0, 0, 16, 16], "texture": "#layer0"}
                }
            },
            # Inner glow (4x4x4, centered)
            {
                "from": [6, 6, 6], "to": [10, 10, 10],
                "faces": {
                    "north": {"uv": [4, 4, 12, 12], "texture": "#layer0"},
                    "east":  {"uv": [4, 4, 12, 12], "texture": "#layer0"},
                    "south": {"uv": [4, 4, 12, 12], "texture": "#layer0"},
                    "west":  {"uv": [4, 4, 12, 12], "texture": "#layer0"},
                    "up":    {"uv": [4, 4, 12, 12], "texture": "#layer0"},
                    "down":  {"uv": [4, 4, 12, 12], "texture": "#layer0"}
                }
            }
        ],
        "display": {
            "fixed":  {"translation": [0, 0, 0], "scale": [1, 1, 1]},
            "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]}
        }
    }


def make_3d_blade_model(texture_path):
    """Multi-cube blade: blade + crossguard + handle + pommel (4 elements)."""
    return {
        "textures": {"layer0": texture_path},
        "elements": [
            # Blade (tall, thin)
            {
                "from": [7, 2, 7], "to": [9, 14, 9],
                "faces": {
                    "north": {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                    "south": {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                    "east":  {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                    "west":  {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                    "up":    {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                    "down":  {"uv": [0, 0, 16, 16], "texture": "#layer0"}
                }
            },
            # Crossguard (wide, flat)
            {
                "from": [4, 13, 6], "to": [12, 15, 10],
                "faces": {
                    "north": {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                    "south": {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                    "east":  {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                    "west":  {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                    "up":    {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                    "down":  {"uv": [0, 0, 16, 16], "texture": "#layer0"}
                }
            },
            # Handle (thin, below crossguard)
            {
                "from": [7, 15, 7], "to": [9, 21, 9],
                "faces": {
                    "north": {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                    "south": {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                    "east":  {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                    "west":  {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                    "up":    {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                    "down":  {"uv": [0, 0, 16, 16], "texture": "#layer0"}
                }
            },
            # Pommel (small cube at bottom)
            {
                "from": [6, 21, 6], "to": [10, 23, 10],
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
    """Single large cube that looks like a sphere when textured."""
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


def make_3d_shuriken_model(texture_path):
    """4 crossed blade cubes forming a shuriken/X shape."""
    return {
        "textures": {"layer0": texture_path},
        "elements": [
            # Blade 1 (horizontal)
            {"from": [2, 7, 7], "to": [14, 9, 9], "faces": {
                "north": {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "south": {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "east":  {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "west":  {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "up":    {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "down":  {"uv": [0, 0, 16, 16], "texture": "#layer0"}}},
            # Blade 2 (vertical)
            {"from": [7, 2, 7], "to": [9, 14, 9], "faces": {
                "north": {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "south": {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "east":  {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "west":  {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "up":    {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "down":  {"uv": [0, 0, 16, 16], "texture": "#layer0"}}},
            # Center hub
            {"from": [6, 6, 6], "to": [10, 10, 10], "faces": {
                "north": {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "south": {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "east":  {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "west":  {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "up":    {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "down":  {"uv": [0, 0, 16, 16], "texture": "#layer0"}}},
        ]
    }


def make_3d_kirin_bolt_model(texture_path):
    """Vertical lightning bolt — tall thin cube + side branches."""
    return {
        "textures": {"layer0": texture_path},
        "elements": [
            # Main bolt (tall, very thin)
            {"from": [7, 0, 7], "to": [9, 16, 9], "faces": {
                "north": {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "south": {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "east":  {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "west":  {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "up":    {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "down":  {"uv": [0, 0, 16, 16], "texture": "#layer0"}}},
            # Side branch 1 (left)
            {"from": [3, 8, 7], "to": [7, 10, 9], "faces": {
                "north": {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "south": {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "east":  {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "west":  {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "up":    {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "down":  {"uv": [0, 0, 16, 16], "texture": "#layer0"}}},
            # Side branch 2 (right)
            {"from": [9, 12, 7], "to": [13, 14, 9], "faces": {
                "north": {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "south": {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "east":  {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "west":  {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "up":    {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "down":  {"uv": [0, 0, 16, 16], "texture": "#layer0"}}},
        ]
    }


def make_3d_aura_ring_model(texture_path):
    """Ring of 4 cubes around the player — for sage aura."""
    return {
        "textures": {"layer0": texture_path},
        "elements": [
            # Front bar
            {"from": [4, 8, 0], "to": [12, 10, 2], "faces": {
                "north": {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "south": {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "east":  {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "west":  {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "up":    {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "down":  {"uv": [0, 0, 16, 16], "texture": "#layer0"}}},
            # Back bar
            {"from": [4, 8, 14], "to": [12, 10, 16], "faces": {
                "north": {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "south": {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "east":  {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "west":  {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "up":    {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "down":  {"uv": [0, 0, 16, 16], "texture": "#layer0"}}},
            # Left bar
            {"from": [0, 8, 4], "to": [2, 10, 12], "faces": {
                "north": {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "south": {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "east":  {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "west":  {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "up":    {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "down":  {"uv": [0, 0, 16, 16], "texture": "#layer0"}}},
            # Right bar
            {"from": [14, 8, 4], "to": [16, 10, 12], "faces": {
                "north": {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "south": {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "east":  {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "west":  {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "up":    {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                "down":  {"uv": [0, 0, 16, 16], "texture": "#layer0"}}},
        ]
    }


def make_3d_dome_model(texture_path):
    """Dome — large flat cube representing the shockwave."""
    return {
        "textures": {"layer0": texture_path},
        "elements": [{
            "from": [0, 0, 0], "to": [16, 16, 16],
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
            "fixed": {"translation": [0, 0, 0], "scale": [3, 3, 3]}
        }
    }


def make_3d_flame_model(texture_path):
    """Small flame orb for Phoenix Flower."""
    return {
        "textures": {"layer0": texture_path},
        "elements": [{
            "from": [5, 5, 5], "to": [11, 11, 11],
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
    print("AnimeMagic Resource Pack Generator v1.0.0-alpha — Cinematic Edition")
    print("=" * 60)
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
    print(f"\n[1/4] Generating {len(TEXTURES)} pixel-art textures (32x32, upscaled to 128x128)...")
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

            # Choose the right 3D model generator based on CustomModelData
            if cmd == 7001:      # magic_orb
                model_json = make_3d_orb_model(f"anime_magic:item/{custom_model_name}")
            elif cmd == 7002:    # chidori_blade
                model_json = make_3d_blade_model(f"anime_magic:item/{custom_model_name}")
            elif cmd == 7003:    # rasengan_sphere
                model_json = make_3d_sphere_model(f"anime_magic:item/{custom_model_name}")
            elif cmd == 7004:    # fireball_orb
                model_json = make_3d_orb_model(f"anime_magic:item/{custom_model_name}")
            elif cmd == 7005:    # haki_dome
                model_json = make_3d_dome_model(f"anime_magic:item/{custom_model_name}")
            elif cmd == 7006:    # magicule_sword
                model_json = make_3d_blade_model(f"anime_magic:item/{custom_model_name}")
            elif cmd == 7007:    # lightning_aura
                model_json = make_3d_aura_ring_model(f"anime_magic:item/{custom_model_name}")
            elif cmd == 7008:    # rasenshuriken
                model_json = make_3d_shuriken_model(f"anime_magic:item/{custom_model_name}")
            elif cmd == 7009:    # kirin_bolt
                model_json = make_3d_kirin_bolt_model(f"anime_magic:item/{custom_model_name}")
            elif cmd == 7010:    # sage_aura
                model_json = make_3d_aura_ring_model(f"anime_magic:item/{custom_model_name}")
            elif cmd == 7011:    # phoenix_flower
                model_json = make_3d_flame_model(f"anime_magic:item/{custom_model_name}")
            else:
                # Flat item model — just references the texture
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
            "pack_format": 57,
            "description": "§dAnimeMagic §7v1.0.0-alpha §8— §fCinematic 32x32 pixel art + multi-cube 3D models"
        }
    }
    (PACK_ROOT / "pack.mcmeta").write_text(json.dumps(pack_mcmeta, indent=2))

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
    print(f"  Textures:    {len(TEXTURES)} hand-crafted 32x32 pixel-art PNGs (128x128 output)")
    print(f"  Overrides:   {total_overrides} CustomModelData entries")
    print(f"  3D models:   7 multi-cube geometries (orb, blade, sphere, shuriken, bolt, ring, dome)")
    print(f"  Total files: {file_count + 1} (assets + pack.mcmeta)")
    print(f"\n  To use: upload to static host, set config.yml:")
    print(f"    gui.resource-pack-url: https://your-host/AnimeMagicResourcePack.zip")


if __name__ == "__main__":
    main()
