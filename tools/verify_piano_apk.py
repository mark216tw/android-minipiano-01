"""Verify the built APK contains the complete, attributed sample bank."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("apk", nargs="?", type=Path, default=Path("app/build/outputs/apk/debug/app-debug.apk"))
    args = parser.parse_args()
    with zipfile.ZipFile(args.apk) as apk:
        prefix = "assets/piano/"
        manifest = json.loads(apk.read(prefix + "instrument.json"))
        entries = manifest["samples"]
        if len(entries) != 90:
            raise ValueError("Incomplete sample bank")
        raw = compressed = 0
        layers = {}
        for entry in entries:
            data = apk.read(prefix + entry["file"])
            if hashlib.sha256(data).hexdigest() != entry["sha256"]:
                raise ValueError(f"Sample hash mismatch: {entry['file']}")
            info = apk.getinfo(prefix + entry["file"])
            raw += info.file_size
            compressed += info.compress_size
        for entry in entries:
            layers.setdefault(entry.get("layer", "loud"), set()).update(range(entry["lowMidi"], entry["highMidi"] + 1))
        if len(layers) != 3 or any(covered != set(range(21, 109)) for covered in layers.values()):
            raise ValueError("Incomplete piano key coverage")
        if b"Alexander Holm" not in apk.read(prefix + "ATTRIBUTION.txt") or b"Attribution 3.0" not in apk.read(prefix + "LICENSE.txt"):
            raise ValueError("Missing sample attribution/license")
        print(f"Verified piano: {len(entries)} note samples, "
              f"{len(layers)} velocity layers, all 88 keys per layer, SHA-256, attribution and {manifest['license']}.")
        print(f"WAV assets: {raw / 1024**2:.2f} MiB; stored in APK: {compressed / 1024**2:.2f} MiB.")
        print(f"Decoded 16-bit PCM: {sum(e['frames'] * e.get('channels', 2) * 2 for e in entries) / 1024**2:.2f} MiB.")
        print(f"APK total: {args.apk.stat().st_size / 1024**2:.2f} MiB.")


if __name__ == "__main__":
    main()
