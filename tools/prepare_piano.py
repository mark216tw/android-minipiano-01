"""Build the offline Salamander mobile bank from a pinned upstream revision.

Three velocity layers are packaged per keyed note. Low and middle roots use
32 kHz, high roots use 48 kHz. Release and pedal noises are not included.
Dependencies: numpy, scipy, soundfile. Source FLACs are cached outside the repo.
Run from the workspace root:

    python tools/prepare_piano.py --cache <directory>
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
LAYERS = [("soft", "v3", 1, 50), ("medium", "v10", 51, 95), ("loud", "v16", 96, 127)]
SOURCE_RATE = 48000
LOW_RATE = 32000
HIGH_RATE = 48000
DURATION_LOW = 7.5
DURATION_MIDDLE = 4.5
DURATION_HIGH = 3.0
ATTRIBUTION = """Salamander Grand Piano v3 — mini 鋼琴手機精簡版

Original piano recordings: Alexander Holm (Yamaha C5).
Upstream FLAC/SFZ mapping: kinwie, sfzinstruments/SalamanderGrandPiano.
Source: https://github.com/sfzinstruments/SalamanderGrandPiano
Original collection: https://archive.org/details/SalamanderGrandPianoV3
License: Creative Commons Attribution 3.0 Unported (CC BY 3.0).
License URL: https://creativecommons.org/licenses/by/3.0/
Full license: LICENSE.txt in this directory.

This mobile sound bank is derived from the above instrument.
Changes: select v3, v10 and v16 velocity layers; trim using upstream onset
offsets; 32 kHz low/middle and 48 kHz high stereo resampling; common gain
adjustment; per-key RMS calibration; shorten tails with a 120 ms fade; and
convert to 16-bit PCM WAV. No release noises, pedal-action noises, sustain
loops or sympathetic resonance layers are included.
The exact source revision and source/output SHA-256 hashes are in instrument.json.

These samples are distributed under CC BY 3.0. The original authors do not
endorse this application. This notice and the sample license do not apply
the CC BY license to unrelated application source code.
"""


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


def output_rate(root):
    if root <= 45:
        return LOW_RATE
    if root <= 72:
        return LOW_RATE
    return HIGH_RATE


def duration(root):
    if root <= 45:
        return DURATION_LOW
    if root <= 72:
        return DURATION_MIDDLE
    return DURATION_HIGH


def resample(data, rate_out):
    divisor = math.gcd(SOURCE_RATE, rate_out)
    return resample_poly(data, rate_out // divisor, SOURCE_RATE // divisor).astype(np.float32)


def rms(data):
    return float(np.sqrt(np.mean(np.square(data[:min(len(data), 16000)]))))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--cache", type=Path, required=True)
    args = parser.parse_args()
    args.cache.mkdir(parents=True, exist_ok=True)
    destination = Path(__file__).resolve().parents[1] / "app/src/main/assets/piano"
    destination.mkdir(parents=True, exist_ok=True)

    def prepare(item):
        root, offset = item
        seconds = duration(root)
        rate_out = output_rate(root)
        samples = []
        for layer, label, low, high in LAYERS:
            source = name(root, label)
            url = f"https://raw.githubusercontent.com/{REPO}/{REVISION}/Samples/{urllib.parse.quote(source)}"
            path = download(url, args.cache / source)
            data, rate = sf.read(path, dtype="float32", always_2d=True)
            if rate != SOURCE_RATE or data.shape[1] != 2:
                raise ValueError(f"Unexpected upstream format: {source}, {rate}, {data.shape}")
            data = data[offset:min(len(data), offset + int(seconds * rate))]
            data = resample(data, rate_out)
            fade = min(len(data), int(rate_out * .12))
            data[-fade:] *= np.linspace(1, 0, fade, dtype=np.float32)[:, None]
            print(f"Prepared MIDI {root} {layer}: {source}, {len(data) / rate_out:.2f}s", flush=True)
            samples.append((layer, low, high, source, path, data, rate_out))
        return root, samples

    with ThreadPoolExecutor(max_workers=4) as executor:
        prepared = list(executor.map(prepare, zip(ROOTS, OFFSETS)))
    common_gain = min(2.0, .85 / max(float(np.max(np.abs(s[5]))) for _, samples in prepared for s in samples))
    for _, samples in prepared:
        for _, _, _, _, _, data, _ in samples:
            data *= common_gain
    layer_rms = {}
    for layer, _, _, _ in LAYERS:
        values = [rms(s[5]) for _, samples in prepared for s in samples if s[0] == layer]
        layer_rms[layer] = float(np.median(values))

    for old in destination.glob("note_*.wav"):
        old.unlink()
    for old in destination.glob("note_*.flac"):
        old.unlink()
    for old in destination.glob("rel_*.flac"):
        old.unlink()
    for old in destination.glob("rel_*.wav"):
        old.unlink()
    for old in destination.glob("pedal_*.flac"):
        old.unlink()
    for old in destination.glob("pedal_*.wav"):
        old.unlink()

    entries = []
    for root, samples in prepared:
        for layer, low, high, source, path, data, rate_out in samples:
            output = destination / f"note_{root:03d}_{layer}.wav"
            sf.write(output, data, rate_out, subtype="PCM_16", format="WAV")
            gain = layer_rms[layer] / max(1e-9, rms(data))
            entries.append({"file": output.name, "layer": layer, "rootMidi": root,
                            "lowMidi": 21 if root == 21 else root - 1,
                            "highMidi": 108 if root == 108 else root + 1,
                            "rate": rate_out, "channels": 2, "frames": len(data),
                            "gain": round(float(gain), 6), "sourceFile": source,
                            "sourceSha256": hashlib.sha256(path.read_bytes()).hexdigest(),
                            "sha256": hashlib.sha256(output.read_bytes()).hexdigest()})

    license_url = f"https://raw.githubusercontent.com/{REPO}/{REVISION}/LICENSE"
    license_path = download(license_url, args.cache / "LICENSE-upstream.txt")
    (destination / "LICENSE.txt").write_bytes(license_path.read_bytes())
    (destination / "ATTRIBUTION.txt").write_text(ATTRIBUTION, encoding="utf-8", newline="\n")
    manifest = {"version": 2,
                "format": "wav",
                "name": "Salamander Grand Piano v3 — mini piano mobile edition",
                "author": "Alexander Holm", "upstreamMapping": "kinwie",
                "license": "CC BY 3.0", "licenseUrl": "https://creativecommons.org/licenses/by/3.0/",
                "source": f"https://github.com/{REPO}", "revision": REVISION,
                "sampleRate": HIGH_RATE, "channels": 2, "velocityLayers": len(LAYERS),
                "layers": [{"name": layer, "low": low, "high": high} for layer, _, low, high in LAYERS],
                "gainApplied": common_gain, "gainCalibrated": True,
                "changes": "Selected velocity layers v3, v10 and v16; mixed 32 kHz low/middle and 48 kHz high roots; "
                           "used upstream onset offsets; per-key RMS calibration; 120 ms tail fade; PCM16 WAV. "
                           "No release or pedal-action noise layers.",
                "samples": entries}
    (destination / "instrument.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    total = sum((destination / e["file"]).stat().st_size for e in entries)
    decoded = sum(e["frames"] * e["channels"] * 2 for e in entries)
    print(f"Bank: {len(entries)} note samples, {total / 1024**2:.2f} MiB files, {decoded / 1024**2:.2f} MiB decoded")


if __name__ == "__main__":
    main()
