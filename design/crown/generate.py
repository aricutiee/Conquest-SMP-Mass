"""Generate legacy flat-crown previews without replacing the deployed 3D crown."""

import json
import struct
import zlib
from pathlib import Path


ROOT = Path(__file__).resolve().parent
HEAD_TRANSLATION_Y = 8.8
HEAD_SCALE = 0.94
HEAD_SCALE_Y = 0.82

COLORS = {
    "outline": "#6A3C00",
    "shadow": "#A76000",
    "bronze": "#D88A00",
    "gold": "#F6B500",
    "sun": "#FFD735",
    "light": "#FFF07A",
    "green": "#55D214",
    "green_light": "#A6F044",
    "clear": "#00000000",
}


def rgba(value):
    if len(value) == 9:
        return tuple(bytes.fromhex(value[1:3] + value[3:5] + value[5:7])) + (int(value[7:9], 16),)
    return tuple(bytes.fromhex(value[1:])) + (255,)


def chunk(name, data):
    return struct.pack(">I", len(data)) + name + data + struct.pack(">I", zlib.crc32(name + data) & 0xFFFFFFFF)


def save_png(path, width, height, pixels):
    raw = bytearray()
    for row in pixels:
        raw.append(0)
        for pixel in row:
            raw.extend(pixel)
    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += chunk(b"IEND", b"")
    path.write_bytes(png)


def face(uv=None):
    return {"uv": uv or [0, 0, 16, 16], "texture": "#crown"}


def panel(frm, to, faces):
    return {
        "from": list(frm),
        "to": list(to),
        "faces": {name: face(uv) for name, uv in faces.items()},
    }


ELEMENTS = [
    panel((0.35, 0.0, 0.35), (15.65, 9.5, 0.55), {"north": [0, 0, 16, 16], "south": [16, 0, 0, 16]}),
    panel((0.35, 0.0, 15.45), (15.65, 9.5, 15.65), {"south": [0, 0, 16, 16], "north": [16, 0, 0, 16]}),
    panel((0.35, 0.0, 0.35), (0.55, 9.5, 15.65), {"west": [0, 0, 16, 16], "east": [16, 0, 0, 16]}),
    panel((15.45, 0.0, 0.35), (15.65, 9.5, 15.65), {"east": [0, 0, 16, 16], "west": [16, 0, 0, 16]}),
]

MODEL = {
    "credit": "Flat pixel crown for testingarii",
    "ambientocclusion": False,
    "textures": {
        "crown": "kingscrown:item/crown_palette",
        "particle": "kingscrown:item/crown_palette",
    },
    "elements": ELEMENTS,
    "display": {
        "head": {"rotation": [0, 0, 0], "translation": [0, HEAD_TRANSLATION_Y, 0], "scale": [HEAD_SCALE, HEAD_SCALE_Y, HEAD_SCALE]},
        "gui": {"rotation": [0, 180, 0], "translation": [0, 1, 0], "scale": [0.9, 0.9, 0.9]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.8, 0.8, 0.8]},
        "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.9, 0.9, 0.9]},
        "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 1, 0], "scale": [0.7, 0.7, 0.7]},
        "firstperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.75, 0.75, 0.75]},
    },
}


def make_texture():
    clear = rgba(COLORS["clear"])
    pixels = [[clear for _ in range(16)] for _ in range(16)]

    def px(x, y, name):
        if 0 <= x < 16 and 0 <= y < 16:
            pixels[y][x] = rgba(COLORS[name])

    def rect(x0, y0, x1, y1, name):
        for y in range(y0, y1):
            for x in range(x0, x1):
                px(x, y, name)

    for x, top in ((0, 1), (6, 0), (13, 1)):
        rect(x, top, x + 3, 6, "gold")
        rect(x, top, x + 1, 6, "sun")
        rect(x, top, x + 3, top + 1, "light")

    # The band reaches both texture edges so it wraps continuously around the head.
    rect(0, 5, 16, 11, "gold")
    rect(0, 5, 16, 6, "light")
    rect(0, 9, 16, 11, "sun")
    rect(0, 11, 16, 12, "bronze")
    rect(0, 12, 16, 13, "outline")

    rect(6, 6, 10, 11, "green")
    rect(7, 5, 9, 6, "green_light")
    rect(6, 6, 7, 10, "green_light")
    px(10, 7, "outline")
    px(10, 8, "outline")
    px(5, 7, "outline")
    px(5, 8, "outline")

    return pixels


def render_front_preview():
    scale = 24
    clear = rgba(COLORS["clear"])
    texture = make_texture()
    preview = [[clear for _ in range(16 * scale)] for _ in range(16 * scale)]
    for ty, row in enumerate(texture):
        for tx, color in enumerate(row):
            for y in range(ty * scale, (ty + 1) * scale):
                for x in range(tx * scale, (tx + 1) * scale):
                    preview[y][x] = color
    return preview


def render_worn_preview():
    scale = 32
    clear = rgba(COLORS["clear"])
    worn = [[clear for _ in range(512)] for _ in range(512)]
    hair = rgba("#4A2200")
    skin = rgba("#FFEBC2")
    eye = rgba("#FFFDF1")
    texture = make_texture()

    for py in range(200, 456):
        for px in range(128, 384):
            is_hair = py < 296 or ((px < 166 or px >= 346) and py < 350)
            worn[py][px] = hair if is_hair else skin
    for py in range(328, 382):
        for px in list(range(178, 210)) + list(range(302, 334)):
            worn[py][px] = eye

    left = 128 + (4 + 0.625 * HEAD_SCALE * (0.35 - 8)) * scale
    top = 200 + (4 - 0.625 * (HEAD_TRANSLATION_Y + HEAD_SCALE_Y * (9.5 - 8))) * scale
    pixel_size_x = 15.3 * 0.625 * HEAD_SCALE * scale / 16
    pixel_size_y = 9.5 * 0.625 * HEAD_SCALE_Y * scale / 16
    front_depth = (8 - 0.35) * 0.625 * HEAD_SCALE
    for ty, row in enumerate(texture):
        for tx, color in enumerate(row):
            if color[3] == 0:
                continue
            for y in range(round(top + ty * pixel_size_y), round(top + (ty + 1) * pixel_size_y)):
                for x in range(round(left + tx * pixel_size_x), round(left + (tx + 1) * pixel_size_x)):
                    if 0 <= x < 512 and 0 <= y < 512:
                        if front_depth <= 4.25 and 120 <= x < 392 and 192 <= y < 464:
                            continue
                        worn[y][x] = color
    return worn


def main():
    (ROOT / "crown_item.json").write_text(
        json.dumps({"model": {"type": "minecraft:model", "model": "kingscrown:item/crown"}}, indent=2) + "\n",
        encoding="utf-8",
    )
    (ROOT / "crown_flat_legacy_model.json").write_text(json.dumps(MODEL, indent=2) + "\n", encoding="utf-8")
    save_png(ROOT / "crown_flat_legacy_palette.png", 16, 16, make_texture())
    save_png(ROOT / "crown_front_preview.png", 384, 384, render_front_preview())
    save_png(ROOT / "crown_angled_preview.png", 384, 384, render_front_preview())
    save_png(ROOT / "crown_worn_preview.png", 512, 512, render_worn_preview())


if __name__ == "__main__":
    main()
