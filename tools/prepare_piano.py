"""Build the offline Salamander mobile bank from a pinned upstream revision.

Two velocity layers are packaged per keyed note: a soft layer (v3) and a loud
layer (v10). Dependencies: numpy, scipy, soundfile. Source FLACs are cached
outside the repo. Run from the workspace root:

    python tools/prepare_piano.py --cache <directory> --rate 32000
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
import math
from pathlib import Path
import time
import urllib.parse
import urllib.request

import numpy as np
from scipy.signal import resample_poly
import soundfile as sf

REVISION = "3382bf9496bba2486f5ab0de55a264d1dfc38404"
REPO = "sfzinstruments/SalamanderGrandPiano"
ROOTS = list(range(21, 109, 3))
OFFSETS = [1788,1382,1152,1008,797,811,663,813,675,775,624,658,660,822,670,
           571,593,702,682,680,593,542,528,773,549,616,770,591,690,632]
# Onset offsets are shared by both layers of the same note (same hammer timing).
LAYERS = [("soft", "v3"), ("loud", "v10")]
SOURCE_RATE = 48000
CROSSFADE = [64, 88]


def download(url, path):
    if path.exists():
        return path
    for attempt in range(3):
        try:
            with urllib.request.urlopen(url, timeout=90) as response:
                data = response.read()
            path.write_bytes(data)
            return path
        except Exception:
            if attempt == 2:
                raise
            time.sleep(attempt + 1)


def name(root, layer):
    notes = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"]
    return f"{notes[root % 12]}{root // 12 - 1}{layer}.flac"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--cache", type=Path, required=True)
    parser.add_argument("--rate", type=int, default=24000)
    args = parser.parse_args()
    args.cache.mkdir(parents=True, exist_ok=True)
    rate_out = args.rate
    divisor = math.gcd(SOURCE_RATE, rate_out)
    up, down = rate_out // divisor, SOURCE_RATE // divisor
    destination = Path(__file__).resolve().parents[1] / "app/src/main/assets/piano"
    destination.mkdir(parents=True, exist_ok=True)

    def prepare(item):
        root, offset = item
        seconds = 12 if root <= 45 else 8 if root <= 72 else 5
        samples = []
        for layer, label in LAYERS:
            source = name(root, label)
            url = f"https://raw.githubusercontent.com/{REPO}/{REVISION}/Samples/{urllib.parse.quote(source)}"
            path = download(url, args.cache / source)
            data, rate = sf.read(path, dtype="float32", always_2d=True)
            if rate != SOURCE_RATE or data.shape[1] != 2:
                raise ValueError(f"Unexpected upstream format: {source}, {rate}, {data.shape}")
            data = data[offset:min(len(data), offset + seconds * rate)]
            data = resample_poly(data, up, down).astype(np.float32)
            # Preserve attack/body; smoothly bring trimmed tails to zero (no looping).
            fade = min(len(data), int(rate_out * .12))
            data[-fade:] *= np.linspace(1, 0, fade, dtype=np.float32)[:, None]
            print(f"Prepared MIDI {root} {layer}: {source}, {len(data) / rate_out:.2f}s", flush=True)
            samples.append((layer, source, path, data))
        return root, samples

    with ThreadPoolExecutor(max_workers=4) as executor:
        prepared = list(executor.map(prepare, zip(ROOTS, OFFSETS)))
    gain = min(2.0, .85 / max(float(np.max(np.abs(s[3]))) for _, samples in prepared for s in samples))
    entries = []
    for old in destination.glob("note_*.wav"):
        old.unlink()
    for root, samples in prepared:
        for layer, source, path, data in samples:
            output = destination / f"note_{root:03d}_{layer}.wav"
            sf.write(output, data * gain, rate_out, subtype="PCM_16", format="WAV")
            entries.append({"file": output.name, "layer": layer, "rootMidi": root,
                            "lowMidi": 21 if root == 21 else root - 1,
                            "highMidi": 108 if root == 108 else root + 1,
                            "frames": len(data), "sourceFile": source,
                            "sourceSha256": hashlib.sha256(path.read_bytes()).hexdigest(),
                            "sha256": hashlib.sha256(output.read_bytes()).hexdigest()})
    license_url = f"https://raw.githubusercontent.com/{REPO}/{REVISION}/LICENSE"
    license_path = download(license_url, args.cache / "LICENSE-upstream.txt")
    (destination / "LICENSE.txt").write_bytes(license_path.read_bytes())
    manifest = {"name": "Salamander Grand Piano v3 — mini piano mobile edition",
                "author": "Alexander Holm", "upstreamMapping": "kinwie",
                "license": "CC BY 3.0", "licenseUrl": "https://creativecommons.org/licenses/by/3.0/",
                "source": f"https://github.com/{REPO}", "revision": REVISION,
                "sampleRate": rate_out, "channels": 2, "velocityLayers": len(LAYERS),
                "velocityCrossfade": CROSSFADE, "gainApplied": gain,
                "changes": f"Selected velocity layers v3 (soft) and v10 (loud); used upstream v10 onset offsets; "
                           f"anti-aliased resampling from 48 kHz to {rate_out // 1000} kHz; common gain across both layers; "
                           f"tails shortened with 120 ms fade; PCM 16-bit WAV conversion. No loops or release/resonance layers.",
                "samples": entries}
    (destination / "instrument.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    total = sum((destination / e["file"]).stat().st_size for e in entries)
    print(f"Bank: {len(entries)} stereo samples ({len(LAYERS)} layers x {len(ROOTS)} roots), {total / 1024**2:.2f} MiB PCM16, {sum(e['frames'] for e in entries) * 4 / 1024**2:.2f} MiB decoded 16-bit, gain {gain:.3f}")


if __name__ == "__main__":
    main()
