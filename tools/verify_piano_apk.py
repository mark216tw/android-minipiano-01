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
        for directory, count, author, license_marker in [("piano", 30, b"Alexander Holm", b"Attribution 3.0"), ("upright", 23, b"Simon Dalzell", b"CC0 1.0 Universal")]:
            prefix = f"assets/{directory}/"
            manifest = json.loads(apk.read(prefix + "instrument.json"))
            entries = manifest["samples"]
            if len(entries) != count:
                raise ValueError("Incomplete sample bank")
            raw = compressed = 0
            covered = set()
            for entry in entries:
                data = apk.read(prefix + entry["file"])
                if hashlib.sha256(data).hexdigest() != entry["sha256"]:
                    raise ValueError(f"Sample hash mismatch: {entry['file']}")
                info = apk.getinfo(prefix + entry["file"])
                raw += info.file_size
                compressed += info.compress_size
                covered.update(range(entry["lowMidi"], entry["highMidi"] + 1))
            if covered != set(range(21, 109)):
                raise ValueError("Incomplete piano key coverage")
            if author not in apk.read(prefix + "ATTRIBUTION.txt") or license_marker not in apk.read(prefix + "LICENSE.txt"):
                raise ValueError("Missing sample attribution/license")
            print(f"Verified {directory}: {count} stereo samples, all 88 keys, SHA-256, attribution and {manifest['license']}.")
            print(f"WAV assets: {raw / 1024**2:.2f} MiB; stored in APK: {compressed / 1024**2:.2f} MiB.")
            print(f"Decoded float PCM: {sum(e['frames'] for e in entries) * 8 / 1024**2:.2f} MiB.")
        print(f"APK total: {args.apk.stat().st_size / 1024**2:.2f} MiB.")


if __name__ == "__main__":
    main()
