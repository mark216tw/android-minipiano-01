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
        for directory, count, author, license_marker in [("piano", 60, b"Alexander Holm", b"Attribution 3.0")]:
            prefix = f"assets/{directory}/"
            manifest = json.loads(apk.read(prefix + "instrument.json"))
            entries = manifest["samples"]
            if len(entries) != count:
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
                layers.setdefault(entry.get("layer", "loud"), set()).update(range(entry["lowMidi"], entry["highMidi"] + 1))
            if not layers or any(covered != set(range(21, 109)) for covered in layers.values()):
                raise ValueError("Incomplete piano key coverage")
            if author not in apk.read(prefix + "ATTRIBUTION.txt") or license_marker not in apk.read(prefix + "LICENSE.txt"):
                raise ValueError("Missing sample attribution/license")
            print(f"Verified {directory}: {len(entries)} stereo samples in {len(layers)} velocity layers, all 88 keys per layer, SHA-256, attribution and {manifest['license']}.")
            print(f"WAV assets: {raw / 1024**2:.2f} MiB; stored in APK: {compressed / 1024**2:.2f} MiB.")
            print(f"Decoded 16-bit PCM: {sum(e['frames'] for e in entries) * 4 / 1024**2:.2f} MiB.")
        print(f"APK total: {args.apk.stat().st_size / 1024**2:.2f} MiB.")


if __name__ == "__main__":
    main()
