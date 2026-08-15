#!/usr/bin/env python3
"""Generate Android launcher icons and in-app logos from project root PNGs.

Keeps transparency (black canvas → alpha) and adds generous safe-zone margin
so adaptive / circular masks do not clip the hexagon.
"""

from __future__ import annotations

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent
RES = ROOT / "app" / "src" / "main" / "res"
DRAWABLE = RES / "drawable"

MARK_SRC = ROOT / "logo-ldapadvisor.png"
TEXT_SRC = ROOT / "logo-ldapadvisor-text.png"

# Adaptive icon safe zone is ~66% of 108dp; use less so hexagon isn't clipped.
ADAPTIVE_FILL = 0.52
LEGACY_FILL = 0.62

written: list[tuple[str, tuple[int, int] | None]] = []
deleted: list[str] = []


def rel(path: Path) -> str:
    try:
        return str(path.relative_to(ROOT)).replace("\\", "/")
    except ValueError:
        return str(path)


def record_write(path: Path, size: tuple[int, int] | None = None) -> None:
    written.append((rel(path), size))


def record_delete(path: Path) -> None:
    if path.exists():
        path.unlink()
        deleted.append(rel(path))


def black_to_transparent(img: Image.Image, threshold: int = 28) -> Image.Image:
    """Make near-black opaque pixels fully transparent (keeps colored logo)."""
    rgba = img.convert("RGBA")
    pixels = rgba.load()
    w, h = rgba.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = pixels[x, y]
            if a == 0:
                continue
            if r <= threshold and g <= threshold and b <= threshold:
                pixels[x, y] = (0, 0, 0, 0)
    return rgba


def trim_transparent(img: Image.Image, pad: int = 4) -> Image.Image:
    rgba = img.convert("RGBA")
    bbox = rgba.getbbox()
    if not bbox:
        return rgba
    min_x, min_y, max_x, max_y = bbox
    w, h = rgba.size
    min_x = max(0, min_x - pad)
    min_y = max(0, min_y - pad)
    max_x = min(w, max_x + pad)
    max_y = min(h, max_y + pad)
    return rgba.crop((min_x, min_y, max_x, max_y))


def fit_centered(logo: Image.Image, canvas_size: int, fill_ratio: float) -> Image.Image:
    """Transparent canvas; logo scaled so max side = fill_ratio * canvas."""
    canvas = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
    target = max(1, int(canvas_size * fill_ratio))
    lw, lh = logo.size
    scale = min(target / lw, target / lh)
    new_w = max(1, int(round(lw * scale)))
    new_h = max(1, int(round(lh * scale)))
    resized = logo.resize((new_w, new_h), Image.Resampling.LANCZOS)
    x = (canvas_size - new_w) // 2
    y = (canvas_size - new_h) // 2
    canvas.alpha_composite(resized, (x, y))
    return canvas


def save_png(img: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path, format="PNG", optimize=True)
    record_write(path, img.size)


def resize_max(img: Image.Image, max_side: int | None = None, max_width: int | None = None) -> Image.Image:
    w, h = img.size
    if max_width is not None and w > max_width:
        scale = max_width / w
        return img.resize((max_width, max(1, int(round(h * scale)))), Image.Resampling.LANCZOS)
    if max_side is not None:
        m = max(w, h)
        if m > max_side:
            scale = max_side / m
            return img.resize(
                (max(1, int(round(w * scale))), max(1, int(round(h * scale)))),
                Image.Resampling.LANCZOS,
            )
    return img


def main() -> None:
    print("Loading source logos...")
    mark = black_to_transparent(Image.open(MARK_SRC))
    text = black_to_transparent(Image.open(TEXT_SRC))
    mark = trim_transparent(mark)
    text = trim_transparent(text)
    print(f"  mark trimmed: {mark.size}")
    print(f"  text trimmed: {text.size}")

    # Adaptive foreground — generous margin, fully transparent canvas
    record_delete(DRAWABLE / "ic_launcher_foreground.xml")
    save_png(fit_centered(mark, 432, ADAPTIVE_FILL), DRAWABLE / "ic_launcher_foreground.png")

    # Transparent adaptive background
    bg_path = DRAWABLE / "ic_launcher_background.xml"
    bg_path.write_text(
        '''<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="@android:color/transparent" />
</shape>
''',
        encoding="utf-8",
    )
    record_write(bg_path, None)

    densities = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }
    for folder, size in densities.items():
        mipmap = RES / folder
        for name in ("ic_launcher", "ic_launcher_round"):
            record_delete(mipmap / f"{name}.webp")
            save_png(fit_centered(mark, size, LEGACY_FILL), mipmap / f"{name}.png")

    # In-app: keep transparency, no black fill
    save_png(resize_max(mark, max_side=512), DRAWABLE / "logo_mark.png")
    save_png(resize_max(text, max_width=768), DRAWABLE / "logo_wordmark.png")

    for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
        path = RES / "mipmap-anydpi-v26" / name
        path.write_text(
            '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
''',
            encoding="utf-8",
        )
        record_write(path, None)

    print("\n=== SUMMARY ===")
    for path, size in written:
        print(f"  + {path}" + (f"  ({size[0]}x{size[1]})" if size else ""))
    for path in deleted:
        print(f"  - {path}")
    print(f"Totals: {len(written)} written, {len(deleted)} deleted")
    print(f"Adaptive fill={ADAPTIVE_FILL}, legacy fill={LEGACY_FILL} (transparent)")


if __name__ == "__main__":
    main()
