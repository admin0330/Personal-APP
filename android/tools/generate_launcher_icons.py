"""Package the approved square artwork as Android launcher layers (requires Pillow).

Run from any directory: python android/tools/generate_launcher_icons.py
The source is checked into assets/branding; never read Downloads during builds.
"""
from pathlib import Path
from PIL import Image, ImageOps

MAIN = Path(__file__).resolve().parents[1] / "app/src/main"
SOURCE = MAIN / "assets/branding/app_icon_source.png"
DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}


def generate():
    original = Image.open(SOURCE).convert("RGB")
    # Trim only empty margins, preserving every part of the approved character.
    luminance = ImageOps.grayscale(original)
    bounds = luminance.point(lambda value: 255 if value > 12 else 0).getbbox()
    assert bounds, "The icon source has no visible artwork"
    character = luminance.crop(bounds)
    for density, scale in DENSITIES.items():
        side, safe = round(108 * scale), round(66 * scale)
        artwork = ImageOps.contain(character, (safe, safe), Image.Resampling.LANCZOS)
        alpha = Image.new("L", (side, side), 0)
        alpha.paste(artwork, ((side - artwork.width) // 2, (side - artwork.height) // 2))
        # White RGB with coverage in alpha, not dim RGB multiplied by dim alpha.
        foreground = Image.new("RGBA", (side, side), "white")
        foreground.putalpha(alpha)
        directory = MAIN / "res" / f"mipmap-{density}"
        directory.mkdir(parents=True, exist_ok=True)
        foreground.save(directory / "ic_launcher_foreground.png")
        foreground.save(directory / "ic_launcher_monochrome.png")
        composited = Image.alpha_composite(Image.new("RGBA", (side, side), "black"), foreground)
        inset = round(18 * scale)
        legacy = composited.crop((inset, inset, side - inset, side - inset)).resize(
            (round(48 * scale), round(48 * scale)), Image.Resampling.LANCZOS
        ).convert("RGB")
        # Platform controls the outer mask. No baked-in circle or border.
        legacy.save(directory / "ic_launcher.png")
        legacy.save(directory / "ic_launcher_round.png")
        assert alpha.getextrema()[1] >= 240, f"Invisible {density} foreground"
        assert alpha.getpixel((0, 0)) == 0
        assert legacy.getextrema()[0][1] >= 200, f"Invisible {density} legacy icon"
        print(f"{density}: {side}px foreground; alpha max={alpha.getextrema()[1]}")


if __name__ == "__main__":
    generate()
