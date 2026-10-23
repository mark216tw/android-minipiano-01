package com.minipiano.app;

/** Small Freeverb-style stereo room. State belongs to the audio thread. */
final class Reverb {
    private static final int[] COMB = {1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617};
    private static final int[] ALLPASS = {556, 441, 341, 225};
    private static final int SPREAD = 23;
    private static final float INPUT_GAIN = .015f;
    private final Comb[] combLeft = new Comb[COMB.length], combRight = new Comb[COMB.length];
    private final Allpass[] allpassLeft = new Allpass[ALLPASS.length], allpassRight = new Allpass[ALLPASS.length];
    private final float[] preLeft, preRight;
    private final int preDelay;
    private final long tailFrames;
    private final double highpassCoefficient;
    private int preIndex;
    private float hpLeft, hpRight, previousLeft, previousRight;
    private double mix, targetMix, wetLeft, wetRight;
    private long tail;

    Reverb(int rate) {
        for (int i = 0; i < COMB.length; i++) {
            combLeft[i] = new Comb(Math.round(COMB[i] * rate / 44100f));
            combRight[i] = new Comb(Math.round((COMB[i] + SPREAD) * rate / 44100f));
        }
        for (int i = 0; i < ALLPASS.length; i++) {
            allpassLeft[i] = new Allpass(Math.round(ALLPASS[i] * rate / 44100f));
            allpassRight[i] = new Allpass(Math.round((ALLPASS[i] + SPREAD) * rate / 44100f));
        }
        preDelay = Math.max(1, Math.round(rate * .015f));
        preLeft = new float[preDelay]; preRight = new float[preDelay];
        tailFrames = rate * 2L;
        highpassCoefficient = Math.exp(-2 * Math.PI * 110 / rate);
    }

    void configure(boolean enabled, double level) {
        targetMix = enabled ? Math.max(0, Math.min(.6, level)) : 0;
        if (targetMix == 0) tail = Math.max(tail, 1);
    }

    void clear() {
        for (Comb comb : combLeft) comb.clear();
        for (Comb comb : combRight) comb.clear();
        for (Allpass allpass : allpassLeft) allpass.clear();
        for (Allpass allpass : allpassRight) allpass.clear();
        java.util.Arrays.fill(preLeft, 0); java.util.Arrays.fill(preRight, 0);
        preIndex = 0; hpLeft = hpRight = previousLeft = previousRight = 0; wetLeft = wetRight = 0; tail = 0; mix = targetMix;
    }

    boolean active() { return tail > 0; }

    void process(float[] buffer, int frames) {
        if (mix == 0 && targetMix == 0) {
            if (tail > 0) tail = 0;
            return;
        }
        double step = 1.0 / Math.max(1, frames);
        for (int frame = 0; frame < frames; frame++) {
            mix += (targetMix - mix) * Math.min(1, step);
            float left = buffer[frame * 2], right = buffer[frame * 2 + 1];
            float inputLeft = (float) (highpassCoefficient * (left - previousLeft + hpLeft));
            float inputRight = (float) (highpassCoefficient * (right - previousRight + hpRight));
            previousLeft = left; previousRight = right; hpLeft = inputLeft; hpRight = inputRight;
            preLeft[preIndex] = inputLeft; preRight[preIndex] = inputRight;
            int read = (preIndex + 1) % preDelay;
            float delayedLeft = preLeft[read], delayedRight = preRight[read];
            preIndex = read;
            float wetL = 0, wetR = 0;
            for (Comb comb : combLeft) wetL += comb.process(delayedLeft * INPUT_GAIN);
            for (Comb comb : combRight) wetR += comb.process(delayedRight * INPUT_GAIN);
            wetL *= .125f; wetR *= .125f;
            for (Allpass allpass : allpassLeft) wetL = allpass.process(wetL);
            for (Allpass allpass : allpassRight) wetR = allpass.process(wetR);
            wetLeft = wetLeft * .98 + wetL * .02;
            wetRight = wetRight * .98 + wetR * .02;
            float wetGain = (float) (mix * .9), dryGain = (float) (1 - mix * .25);
            buffer[frame * 2] = left * dryGain + (float) wetLeft * wetGain;
            buffer[frame * 2 + 1] = right * dryGain + (float) wetRight * wetGain;
            if (Math.abs(left) > 1e-5 || Math.abs(right) > 1e-5) tail = tailFrames;
            else if (tail > 0) tail--;
        }
    }

    private static final class Comb {
        private final float[] buffer; private int index; private float store;
        Comb(int size) { buffer = new float[Math.max(1, size)]; }
        void clear() { java.util.Arrays.fill(buffer, 0); index = 0; store = 0; }
        float process(float input) {
            float output = buffer[index];
            store = output * .65f + store * .35f;
            buffer[index] = input + store * .84f;
            index = (index + 1) % buffer.length;
            return output;
        }
    }

    private static final class Allpass {
        private final float[] buffer; private int index;
        Allpass(int size) { buffer = new float[Math.max(1, size)]; }
        void clear() { java.util.Arrays.fill(buffer, 0); index = 0; }
        float process(float input) {
            float output = buffer[index];
            float value = input + output * .5f;
            buffer[index] = value;
            index = (index + 1) % buffer.length;
            return output - input;
        }
    }
}
