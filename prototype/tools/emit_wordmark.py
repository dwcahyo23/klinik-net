"""Emit the baked 'Klinik Net' wordmark as an SVG <g> ready to paste into a lockup.

Reads tools/wordmark-800.json (written by bake_wordmark.py) and prints one
<g> element whose paths are already positioned, tracked, and flipped to SVG's
y-down space. No live <text> anywhere, so the lockup renders the same whether
the file is inlined, loaded via <img>, or exported to PNG.

Usage: python emit_wordmark.py [json] [--size 30] [--tracking -0.8] [--x 66] [--baseline 38]
"""

import json
import re
import sys
from pathlib import Path

DEFAULTS = Path(__file__).with_name("wordmark-800.json")

# pen output carries full float precision ("357.4068965517241"); 2dp is well
# under a thousandth of the 30px cap height and roughly halves the file size.
_NOISE = re.compile(r"-?\d+\.\d{3,}")


def flag(argv: list[str], name: str, fallback: float) -> float:
    return float(argv[argv.index(name) + 1]) if name in argv else fallback


def main() -> int:
    argv = sys.argv[1:]
    src = next((a for a in argv if a.endswith(".json")), DEFAULTS)

    size = flag(argv, "--size", 30.0)
    tracking = flag(argv, "--tracking", -0.8)  # px per gap, at --size
    x = flag(argv, "--x", 0.0)
    baseline = flag(argv, "--baseline", 38.0)

    data = json.loads(Path(src).read_text(encoding="utf-8"))
    em = size / 1000.0  # outlines were normalised to a 1000-unit em
    track_units = (tracking / size) * 1000.0

    out = [
        f'<g fill="currentColor" fill-rule="nonzero" aria-hidden="true">'
    ]
    cursor = x
    for i, letter in enumerate(data["letters"]):
        if letter["path"]:
            dx = cursor * em  # letters advance in 1000-unit space
            d = _NOISE.sub(lambda m: f"{float(m.group()):.2f}", letter["path"])
            out.append(
                f'  <path transform="translate({dx:.2f} {baseline:.2f}) '
                f'scale({em:.6f} {-em:.6f})" d="{d}"/>'
            )
        cursor += letter["advance"] + (track_units if i < len(data["letters"]) - 1 else 0.0)

    total = (cursor - track_units) * em
    out.append("</g>")
    out.append(f"<!-- width {total:.2f}px at size {size:g}, tracking {tracking:g} -->")

    print("\n".join(out))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())