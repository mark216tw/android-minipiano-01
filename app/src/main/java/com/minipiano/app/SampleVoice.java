package com.minipiano.app;

/** One reusable sample cursor per polyphonic voice, optionally crossfading two velocity layers. */
final class SampleVoice {
    /** The soft layer is recorded quieter than the loud layer; lift it so the crossfade stays loudness-continuous. */
    private static final double SOFT_GAIN = 1.78;
    private PianoSample first, second;
    private double firstPos, secondPos, firstStep, secondStep, mix;
    private double envelope, release;
    private int outputRate, age;
    boolean active;
    float left, right;
    void clear() { first = second = null; active = false; left = right = 0; }
    void start(PianoSample sample, int pitch, int outputRate) { start(sample, null, 0, pitch, outputRate); }
    /** Streams the soft layer at blend 0 and the loud layer at blend 1, mixing between. */
    void start(PianoSample soft, PianoSample loud, double blend, int pitch, int outputRate) {
        first = soft != null ? soft : loud;
        second = soft != null && loud != null && soft != loud ? loud : null;
        mix = second == null ? 0 : Math.max(0, Math.min(1, blend));
        this.outputRate = outputRate;
        firstPos = secondPos = 0;
        firstStep = first == null ? 0 : first.rate / (double) outputRate * Math.pow(2, (pitch - first.root) / 12.0);
        secondStep = second == null ? 0 : second.rate / (double) outputRate * Math.pow(2, (pitch - second.root) / 12.0);
        envelope = release = 1; age = 0; left = right = 0;
        active = first != null || second != null;
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
        double soft = second == null ? 1 : SOFT_GAIN;
        if (firstOn && secondOn) { add(first, firstPos, (1 - mix) * soft, gain); add(second, secondPos, mix, gain); }
        else if (firstOn) add(first, firstPos, soft, gain);
        else add(second, secondPos, 1, gain);
        if (firstOn) firstPos += firstStep;
        if (secondOn) secondPos += secondStep;
        envelope *= release;
        active = playing(first, firstPos) || playing(second, secondPos);
    }
    private static boolean playing(PianoSample sample, double position) { return sample != null && position < sample.frames - 1; }
    private void add(PianoSample sample, double position, double weight, double gain) {
        int frame = (int) position, index = frame * sample.channels;
        float fraction = (float) (position - frame);
        double base = PianoSample.SCALE * weight * gain;
        left += (float) ((sample.pcm[index] + fraction * (sample.pcm[index + sample.channels] - sample.pcm[index])) * base);
        if (sample.channels == 2) right += (float) ((sample.pcm[index + 1] + fraction * (sample.pcm[index + 3] - sample.pcm[index + 1])) * base);
        else right = left;
    }
}
