"""Klinik Net — bake the 'Klinik Net' wordmark into SVG paths.

Live <text> in an SVG loaded via <img> never sees the page's webfont, so the
lockup silently fell back to system-ui. Converting to outlines makes the
wordmark render identically everywhere, offline, with no font dependency.

Usage: python bake_wordmark.py <ttf-path> <out-json-path> [--text "Klinik Net"]
"""

import json
import sys

from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

EMU_PER_UNIT = 1000  # we scale font units to a 1000-unit em below


def main() -> int:
    src = sys.argv[1]
    dest = sys.argv[2]
    text = "Klinik Net"
    if "--text" in sys.argv:
        text = sys.argv[sys.argv.index("--text") + 1]

    font = TTFont(src)

    # Pin the variable axis at ExtraBold (800) so the baked outlines match the
    # weight the CSS asks for.
    if "fvar" in font:
        axes = {a.axisTag: a.defaultValue for a in font["fvar"].axes}
        if "wght" in axes:
            font = instantiateVariableFont(font, {"wght": 800}, inplace=True)

    upem = font["head"].unitsPerEm
    scale = EMU_PER_UNIT / upem
    glyph_set = font.getGlyphSet()
    cmap = font.getBestCmap()
    hmtx = font["hmtx"]

    letters = []
    advance_x = 0.0
    for ch in text:
        glyph_name = cmap.get(ord(ch))
        if glyph_name is None:
            raise SystemExit(f"no glyph for {ch!r} (U+{ord(ch):04X})")
        pen = SVGPathPen(glyph_set)
        glyph_set[glyph_name].draw(pen)
        letters.append(
            {
                "char": ch,
                "glyph": glyph_name,
                "advance": hmtx[glyph_name][0] * scale,
                "path": pen.getCommands(),
            }
        )
        advance_x += hmtx[glyph_name][0] * scale

    payload = {
        "text": text,
        "upem": upem,
        "scale": scale,
        "width": advance_x,
        "letters": letters,
    }
    with open(dest, "w", encoding="utf-8") as fh:
        json.dump(payload, fh, ensure_ascii=False)

    print(f"{text!r}: width={advance_x:.2f} units, {len(letters)} glyphs -> {dest}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())