package com.minipiano.app;

/** Stereo-linked look-ahead peak limiter. Transparent below the threshold. */
final class Limiter {
    private final float[] delayLeft, delayRight, peaks;
    private final int size;
    private final double threshold, releaseCoefficient;
    private double gain = 1;
    private int index;

    Limiter(int rate, double threshold, double lookaheadSeconds, double releaseSeconds) {
        this.threshold = threshold;
        int lookahead = Math.max(0, (int) Math.round(rate * lookaheadSeconds));
        size = lookahead + 1;
        delayLeft = new float[size];
        delayRight = new float[size];
        peaks = new float[size];
        releaseCoefficient = 1 - Math.exp(-1.0 / (rate * releaseSeconds));
    }

    void reset() {
        java.util.Arrays.fill(delayLeft, 0); java.util.Arrays.fill(delayRight, 0);
        java.util.Arrays.fill(peaks, 0); index = 0; gain = 1;
    }

    void process(float[] buffer, int frames) {
        for (int frame = 0; frame < frames; frame++) {
            float left = buffer[frame * 2], right = buffer[frame * 2 + 1];
            delayLeft[index] = left; delayRight[index] = right;
            peaks[index] = Math.max(Math.abs(left), Math.abs(right));
            double maximum = 0;
            for (int i = 0; i < size; i++) maximum = Math.max(maximum, peaks[i]);
            double target = maximum > threshold ? threshold / maximum : 1;
            if (target < gain) gain = target; else gain += (target - gain) * releaseCoefficient;
            int read = (index + 1) % size;
            buffer[frame * 2] = clamp(delayLeft[read] * gain);
            buffer[frame * 2 + 1] = clamp(delayRight[read] * gain);
            index = read;
        }
    }

    private static float clamp(double value) { return (float) Math.max(-1, Math.min(1, value)); }
}
