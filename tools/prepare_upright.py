"""Prepare a compact VSCO 2 CE Upright Piano bank (dyn2, rr1)."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
import math
from pathlib import Path
import urllib.parse

import numpy as np
from scipy.signal import resample_poly
import soundfile as sf
from prepare_piano import download

REVISION = "440300901dfe9275fd84e0b7763af1f8443ae62e"
REPO = "sgossner/VSCO-2-CE"
BASE = f"https://raw.githubusercontent.com/{REPO}/{REVISION}/"
FOLDER = "Keys/Upright Piano/"
RATE = 24000


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--cache", type=Path, required=True)
    args = parser.parse_args()
    args.cache.mkdir(parents=True, exist_ok=True)
    destination = Path(__file__).resolve().parents[1] / "app/src/main/assets/upright"
    destination.mkdir(parents=True, exist_ok=True)
    chart = download(BASE + urllib.parse.quote(FOLDER + "MappingChart.txt"), args.cache / "MappingChart.txt")
    mapping = {int(k): int(v) for line in chart.read_text().splitlines() if "=" in line
               for k, v in [line.split("=", 1)] if k.isdigit()}
    indices = list(range(0, 45, 2))
    roots = [mapping[index] for index in indices]

    def prepare(index):
        root = mapping[index]; source = f"Player_dyn2_rr1_{index:03d}.wav"
        path = download(BASE + urllib.parse.quote(FOLDER + source), args.cache / source)
        data, rate = sf.read(path, dtype="float32", always_2d=True)
        if data.shape[1] == 1:
            data = np.repeat(data, 2, axis=1)
        if data.shape[1] != 2:
            raise ValueError(f"Unsupported source channels: {source}")
        peak = float(np.max(np.abs(data)))
        hits = np.flatnonzero(np.max(np.abs(data[:int(rate * .75)]), axis=1) > max(peak * .005, .00001))
        if not len(hits):
            raise ValueError(f"No onset detected: {source}")
        onset = max(0, int(hits[0]) - int(rate * .002))
        seconds = 10 if root <= 49 else 7 if root <= 77 else 4
        data = data[onset:min(len(data), onset + seconds * rate)]
        divisor = math.gcd(rate, RATE)
        data = resample_poly(data, RATE // divisor, rate // divisor).astype(np.float32)
        fade = min(len(data), int(RATE * .12))
        data[-fade:] *= np.linspace(1, 0, fade, dtype=np.float32)[:, None]
        print(f"Prepared VSCO MIDI {root}: {source}, {len(data) / RATE:.2f}s, onset {onset / rate:.3f}s", flush=True)
        return root, source, path, data, onset, rate

    with ThreadPoolExecutor(max_workers=4) as executor:
        prepared = list(executor.map(prepare, indices))
    gain = min(2.0, .85 / max(float(np.max(np.abs(item[3]))) for item in prepared))
    entries = []
    for i, (root, source, path, data, onset, source_rate) in enumerate(prepared):
        output = destination / f"note_{root:03d}.wav"
        sf.write(output, data * gain, RATE, subtype="PCM_16", format="WAV")
        entries.append({"file": output.name, "rootMidi": root,
                        "lowMidi": 21 if i == 0 else (roots[i - 1] + root) // 2 + 1,
                        "highMidi": 108 if i == len(roots) - 1 else (root + roots[i + 1]) // 2,
                        "frames": len(data), "sourceFile": source, "sourceRate": source_rate, "onsetTrimFrames": onset,
                        "sourceSha256": hashlib.sha256(path.read_bytes()).hexdigest(),
                        "sha256": hashlib.sha256(output.read_bytes()).hexdigest()})
    for file, remote in [("LICENSE.txt", "LICENSE"), ("SOURCE_INFO.txt", FOLDER + "Info.txt"), ("SOURCE_README.txt", "Readme.txt")]:
        path = download(BASE + urllib.parse.quote(remote), args.cache / file)
        (destination / file).write_bytes(path.read_bytes())
    manifest = {"name": "VSCO 2 CE Upright Piano — mini piano mobile edition", "author": "Simon Dalzell / Ivy Audio",
                "publisher": "Versilian Studios", "license": "CC0 1.0", "licenseUrl": "https://creativecommons.org/publicdomain/zero/1.0/",
                "source": f"https://github.com/{REPO}", "revision": REVISION, "sampleRate": RATE, "channels": 2,
                "velocityLayer": 2, "roundRobin": 1, "gainApplied": gain,
                "changes": "Selected dyn2/rr1 and alternate mapping indices (23 samples); onset detection with 2 ms pre-roll; anti-aliased resampling to 24 kHz stereo; common gain; 10/7/4 second tail limits with 120 ms fade; PCM16 WAV conversion; no loops.",
                "samples": entries}
    (destination / "instrument.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    total = sum((destination / e["file"]).stat().st_size for e in entries)
    print(f"VSCO bank: {len(entries)} samples, {total / 1024**2:.2f} MiB PCM16, {sum(e['frames'] for e in entries) * 8 / 1024**2:.2f} MiB float PCM")


if __name__ == "__main__":
    main()
