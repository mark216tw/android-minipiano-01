package com.minipiano.app;

/** One reusable sample cursor per polyphonic voice. No render-time allocation. */
final class SampleVoice {
    private PianoSample sample;
    private double position, step, envelope, release;
    private int outputRate, age;
    boolean active;
    float left, right;
    void clear() { sample = null; active = false; left = right = 0; }
    void start(PianoSample sample, int pitch, int outputRate) {
        this.sample = sample; this.outputRate = outputRate;
        step = sample.rate / (double) outputRate * Math.pow(2, (pitch - sample.root) / 12.0);
        position = 0; envelope = release = 1; age = 0; active = true; left = right = 0;
    }
    void seek(double seconds) {
        position = Math.max(0, seconds) * outputRate * step; age = (int) Math.min(Integer.MAX_VALUE, Math.max(0, seconds) * outputRate);
        active = position < sample.frames - 1;
    }
    void release(double seconds) { release = Math.exp(-1.0 / (outputRate * seconds)); }
    void next() {
        left = right = 0;
        if (!active) return;
        int frame = (int) position;
        if (frame >= sample.frames - 1 || envelope < .0001) { active = false; return; }
        int index = frame * sample.channels; float fraction = (float) (position - frame);
        double gain = envelope * Math.min(1, age++ / (outputRate * .002));
        left = (float) ((sample.pcm[index] + fraction * (sample.pcm[index + sample.channels] - sample.pcm[index])) * gain);
        if (sample.channels == 2) right = (float) ((sample.pcm[index + 1] + fraction * (sample.pcm[index + 3] - sample.pcm[index + 1])) * gain);
        else right = left;
        position += step; envelope *= release;
    }
}
