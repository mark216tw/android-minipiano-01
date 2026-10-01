"""Offline comparison of the three timbres through the app's render/gain path.

Measures 0.5 s held notes at velocity 90, plus one common phrase/chord passage.
K-weighting uses the BS.1770 48 kHz pre-filter coefficients; results are relative
digital levels, not device SPL or a substitute for phone listening tests.
Requires numpy, scipy, soundfile. Does not modify the licensed sample assets.
"""
import argparse
import json
from pathlib import Path
import numpy as np
from scipy.signal import lfilter
from scipy.optimize import least_squares
import soundfile as sf

RATE = 48000
ROOT = Path(__file__).resolve().parents[1]
NAMES = ["Salamander", "VSCO", "Synthetic"]
TABLE = np.sin(np.arange(2048) * 2 * np.pi / 2048).astype(np.float32)
ANCHORS = [24, 48, 72, 96, 108]


def banks():
    result = []
    for directory in ["piano", "upright"]:
        folder = ROOT / "app/src/main/assets" / directory
        manifest = json.loads((folder / "instrument.json").read_text(encoding="utf-8"))
        entries = {}
        for entry in manifest["samples"]:
            pcm, rate = sf.read(folder / entry["file"], dtype="float32", always_2d=True)
            for pitch in range(entry["lowMidi"], entry["highMidi"] + 1):
                entries[pitch] = (pcm, rate, entry["rootMidi"])
        result.append(entries)
    return result


def wave(phase):
    return TABLE[(phase * 2048).astype(np.int64) & 2047]


def note(bank, sound, pitch, held=.5, duration=.8, velocity=90):
    n = np.arange(round(duration * RATE), dtype=np.float64)
    attack = np.minimum(1, n / (RATE * .002))
    if sound == 2:
        phase = (n * (440 * 2 ** ((pitch - 69) / 12) / RATE)) % 1
        hammer = .99965 ** n
        tone = wave(phase) + .42 * hammer * wave(phase * 2) + .20 * hammer * wave(phase * 3) + .08 * hammer * wave(phase * 5)
        tone *= np.exp(-n / (RATE * (1.4 + (108 - pitch) * .025))) * attack
        pcm = np.repeat(tone[:, None], 2, axis=1) * .16
        release = .045
    else:
        data, sample_rate, root = bank[sound][pitch]
        position = n * sample_rate / RATE * 2 ** ((pitch - root) / 12)
        pcm = np.column_stack([np.interp(position, np.arange(len(data)), data[:, c], left=0, right=0) for c in range(2)])
        pcm *= attack[:, None] * .5
        release = .6 if pitch >= 89 else .10
    pcm *= np.exp(-np.maximum(0, n / RATE - held) / release)[:, None]
    return pcm * (velocity / 127) * 1.5


def output(pcm, gain):
    pcm = pcm * gain
    return pcm / (1 + np.abs(pcm))


def weighted(pcm):
    pcm = lfilter([1.53512485958697, -2.69169618940638, 1.19839281085285], [1, -1.69065929318241, .73248077421585], pcm, axis=0)
    return lfilter([1, -2, 1], [1, -1.99004745483398, .99007225036621], pcm, axis=0)


def level(pcm):
    return float(10 * np.log10(max(1e-16, np.mean(weighted(pcm) ** 2))))


def metrics(pcm):
    return {"kWeightedDb": round(level(pcm), 3), "rmsDbfs": round(float(10 * np.log10(max(1e-16, np.mean(pcm ** 2)))), 3),
            "peak": round(float(np.max(np.abs(pcm))), 6)}


def passage(bank, sound, profile):
    pcm = np.zeros((RATE * 6, 2), dtype=np.float64)
    events = [(0, [48,60,64,67]), (.6, [60]), (.9, [60]), (1.2, [62]), (1.5, [64]),
              (1.8, [53,65,69,72]), (2.4, [72]), (2.7, [72]), (3, [67]), (3.3, [64]),
              (3.6, [55,62,67,71]), (4.2, [60,64,67,72])]
    for start, pitches in events:
        for pitch in pitches:
            gain = 10 ** (np.interp(pitch, ANCHORS, profile) / 20)
            data = note(bank, sound, pitch, held=.35 if len(pitches) == 1 else .55, duration=1.5) * gain
            offset = round(start * RATE); pcm[offset:offset + len(data)] += data
    return pcm


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--report", type=Path)
    parser.add_argument("--gains", default="1,1,1")
    parser.add_argument("--calibrate", action="store_true")
    parser.add_argument("--profile", type=Path, help="Use dbProfiles from a previous calibration report")
    args = parser.parse_args()
    gains = [float(x) for x in args.gains.split(",")]
    profiles = [[20 * np.log10(gain)] * len(ANCHORS) for gain in gains]
    if args.profile:
        profiles = json.loads(args.profile.read_text(encoding="utf-8"))["dbProfiles"]
    bank = banks()
    rendered = [[note(bank, sound, pitch)[:RATE // 2] for pitch in range(21, 109)] for sound in range(3)]
    baseline = [[level(output(pcm, 1)) for pcm in samples] for samples in rendered]
    if args.calibrate:
        # Match each key through the existing limiter, then fit a smooth dB
        # contour rather than peak-normalizing each sample independently.
        for sound in [1, 2]:
            matching = []
            for index in range(88):
                low, high = .125, 16.0
                for _ in range(18):
                    middle = (low + high) / 2
                    if level(output(rendered[sound][index], middle)) < baseline[0][index]: low = middle
                    else: high = middle
                matching.append(20 * np.log10((low + high) / 2))
            fitted = least_squares(lambda points: np.interp(np.arange(21, 109), ANCHORS, points) - matching,
                                   np.zeros(len(ANCHORS)), loss="soft_l1", f_scale=2, bounds=(-30, 24))
            profiles[sound] = [round(float(value), 3) for value in fitted.x]
    result = {"sampleRate": RATE, "velocity": 90, "heldSeconds": .5, "anchorMidi": ANCHORS, "dbProfiles": profiles, "timbres": {}}
    for sound, name in enumerate(NAMES):
        key_gains = 10 ** (np.interp(np.arange(21, 109), ANCHORS, profiles[sound]) / 20)
        levels = [level(output(pcm, gain)) for pcm, gain in zip(rendered[sound], key_gains)]
        differences = np.array(levels) - baseline[0]
        bands = {}
        for band, low, high in [("low", 21, 47), ("middle", 48, 83), ("high", 84, 108)]:
            values = differences[low - 21:high - 20]
            bands[band] = {"medianDifferenceDb": round(float(np.median(values)), 3),
                           "p10DifferenceDb": round(float(np.percentile(values, 10)), 3), "p90DifferenceDb": round(float(np.percentile(values, 90)), 3)}
        phrase = passage(bank, sound, profiles[sound])
        result["timbres"][name] = {"bands": bands, "phrase": metrics(output(phrase, 1)),
                                    "phrasePreLimiterPeak": round(float(np.max(np.abs(phrase))), 6)}
        print(f"{name}: dB anchors {profiles[sound]}; bands {bands}; phrase {result['timbres'][name]['phrase']}; pre-limiter peak {result['timbres'][name]['phrasePreLimiterPeak']}")
    if args.report:
        args.report.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
