"""Resolve launcher resources in a shrunk APK and verify the packaged pixels.

Usage: python verify_launcher_icons.py APK AAPT2 REPORT_JSON
"""
from pathlib import Path
from zipfile import ZipFile
from io import BytesIO
import hashlib
import json
import re
import subprocess
import sys
from PIL import Image


def verify(apk, aapt, report_path):
    main = Path(__file__).resolve().parents[1] / "app/src/main"
    table = subprocess.check_output([aapt, "dump", "resources", str(apk)], encoding="utf-8")
    report = {"apk": str(apk), "sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
              "size": apk.stat().st_size, "resources": []}
    ids = dict(re.findall(r"resource (0x[\da-f]+) mipmap/(ic_launcher\w*)", table))
    ids = {name: resource_id for resource_id, name in ids.items()}
    with ZipFile(apk) as archive:
        artwork = archive.read("assets/branding/app_icon_source.png")
        assert artwork == (main / "assets/branding/app_icon_source.png").read_bytes()
        report["source_sha256"] = hashlib.sha256(artwork).hexdigest()
        for name in ["ic_launcher", "ic_launcher_round", "ic_launcher_foreground", "ic_launcher_monochrome"]:
            block = re.search(r"resource (0x[\da-f]+) mipmap/" + name + r"\n(.*?)(?=\n\s+resource|\n\s+type)", table, re.S)
            assert block, name
            for density, path in re.findall(r"\((.*?)\) \(file\) (res/\S+) type=PNG", block[2]):
                packaged = Image.open(BytesIO(archive.read(path))).convert("RGBA")
                source = Image.open(main / f"res/mipmap-{density}/{name}.png").convert("RGBA")
                assert packaged.size == source.size
                assert packaged.getchannel("A").tobytes() == source.getchannel("A").tobytes(), path
                # AAPT may zero RGB beneath fully transparent pixels; visible pixels must match.
                background = Image.new("RGBA", source.size, "black")
                assert Image.alpha_composite(background, packaged).tobytes() == Image.alpha_composite(background, source).tobytes(), path
                assert packaged.getchannel("A").getextrema()[1] >= 240
                report["resources"].append({"name": name, "density": density, "apk_path": path, "visible_pixels_and_alpha_match": True})
            for path in re.findall(r"\(file\) (res/\S+) type=XML", block[2]):
                xml = subprocess.check_output([aapt, "dump", "xmltree", str(apk), "--file", path], encoding="utf-8")
                assert "foreground" in xml and "monochrome" in xml
                assert "@" + ids["ic_launcher_foreground"] in xml
                assert "@" + ids["ic_launcher_monochrome"] in xml
        assert len(report["resources"]) == 20
    report_path.write_text(json.dumps(report, indent=2), encoding="utf-8")
    print("Verified original artwork, adaptive references and all 20 launcher PNGs")
    print("SHA256", report["sha256"])


if __name__ == "__main__":
    verify(Path(sys.argv[1]), sys.argv[2], Path(sys.argv[3]))
