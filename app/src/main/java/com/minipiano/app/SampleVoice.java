package com.minipiano.app;

/** One reusable sample cursor per polyphonic voice, optionally crossfading two velocity layers. */
final class SampleVoice {
    private static final double LEGACY_SOFT_GAIN = 1.78;
    private PianoSample first, second;
    private double firstPos, secondPos, firstStep, secondStep, mix, firstGain = 1, secondGain = 1;
    private double envelope, release;
    private int outputRate, age;
    boolean active;
    float left, right;
    void clear() { first = second = null; active = false; left = right = 0; }
    void start(PianoSample sample, int pitch, int outputRate) { start(sample, null, 0, 1, 1, pitch, outputRate); }
    void start(PianoSample soft, PianoSample loud, double blend, int pitch, int outputRate) {
        start(soft, loud, blend, LEGACY_SOFT_GAIN, 1, pitch, outputRate);
    }
    /** Streams two layers; blend 0 selects the first layer and 1 the second. */
    void start(PianoSample first, PianoSample second, double blend, double firstGain, double secondGain, int pitch, int outputRate) {
        this.first = first != null ? first : second;
        this.second = first != null && second != null && second != first ? second : null;
        this.firstGain = first != null ? firstGain : 1;
        this.secondGain = second != null ? secondGain : 1;
        mix = this.second == null ? 0 : Math.max(0, Math.min(1, blend));
        this.outputRate = outputRate;
        firstPos = secondPos = 0;
        firstStep = this.first == null ? 0 : this.first.rate / (double) outputRate * Math.pow(2, (pitch - this.first.root) / 12.0);
        secondStep = this.second == null ? 0 : this.second.rate / (double) outputRate * Math.pow(2, (pitch - this.second.root) / 12.0);
        envelope = release = 1; age = 0; left = right = 0;
        active = this.first != null || this.second != null;
    }
    void seek(double seconds) {
        double time = Math.max(0, seconds);
        firstPos = time * outputRate * firstStep;
        secondPos = time * outputRate * secondStep;
        age = (int) Math.min(Integer.MAX_VALUE, time * outputRate);
        active = playing(first, firstPos) || playing(second, secondPos);
    }
    void release(double seconds) { release = Math.exp(-1.0 / (outputRate * seconds)); }
    void next() {
        left = right = 0;
        if (!active) return;
        boolean firstOn = playing(first, firstPos), secondOn = playing(second, secondPos);
        if (envelope < .0001 || (!firstOn && !secondOn)) { active = false; return; }
        double gain = envelope * Math.min(1, age++ / (outputRate * .002));
        if (firstOn && secondOn) {
            add(first, firstPos, (1 - mix) * firstGain, gain);
            add(second, secondPos, mix * secondGain, gain);
        } else if (firstOn) add(first, firstPos, firstGain, gain);
        else add(second, secondPos, secondGain, gain);
        if (firstOn) firstPos += firstStep;
        if (secondOn) secondPos += secondStep;
        envelope *= release;
        active = playing(first, firstPos) || playing(second, secondPos);
    }
    private static boolean playing(PianoSample sample, double position) { return sample != null && position < sample.frames - 1; }
    private void add(PianoSample sample, double position, double weight, double gain) {
        int frame = (int) position, channels = sample.channels;
        int i0 = Math.max(0, frame - 1), i1 = frame, i2 = Math.min(sample.frames - 1, frame + 1), i3 = Math.min(sample.frames - 1, frame + 2);
        double t = position - frame, base = PianoSample.SCALE * weight * gain;
        double leftSample = cubic(sample.pcm[i0 * channels], sample.pcm[i1 * channels], sample.pcm[i2 * channels], sample.pcm[i3 * channels], t);
        left += (float) (leftSample * base);
        if (channels == 2) {
            double rightSample = cubic(sample.pcm[i0 * channels + 1], sample.pcm[i1 * channels + 1], sample.pcm[i2 * channels + 1], sample.pcm[i3 * channels + 1], t);
            right += (float) (rightSample * base);
        } else right += (float) (leftSample * base);
    }
    private static double cubic(double a, double b, double c, double d, double t) {
        return b + .5 * t * (c - a + t * (2 * a - 5 * b + 4 * c - d + t * (3 * (b - c) + d - a)));
    }
}
