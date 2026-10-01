package com.minipiano.app;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Immutable, shared interleaved PCM. Loading and decoding never run on the audio thread. */
final class PianoSample {
    final int root, rate, channels, frames;
    final float[] pcm;
    PianoSample(int root, int rate, int channels, float[] pcm) {
        this.root = root; this.rate = rate; this.channels = channels; this.pcm = pcm; frames = pcm.length / channels;
    }
    static PianoSample read(InputStream input, int root) throws IOException {
        byte[] header = bytes(input, 12);
        if (!tag(header, 0).equals("RIFF") || !tag(header, 8).equals("WAVE")) throw new IOException("音色不是 RIFF WAV");
        int channels = 0, rate = 0;
        for (;;) {
            byte[] chunk = bytes(input, 8); int size = le32(chunk, 4);
            if (size < 0 || size > 4 * 1024 * 1024) throw new IOException("音色區塊大小無效");
            String name = tag(chunk, 0);
            if (name.equals("fmt ")) {
                if (size < 16 || size > 65536) throw new IOException("音色 fmt 無效");
                byte[] format = bytes(input, size);
                channels = le16(format, 2); rate = le32(format, 4);
                if (le16(format, 0) != 1 || le16(format, 14) != 16 || (channels != 1 && channels != 2)
                        || rate < 8000 || rate > 96000 || le16(format, 12) != channels * 2) throw new IOException("音色須為 16-bit PCM WAV");
            } else if (name.equals("data")) {
                if (channels == 0 || size < channels * 4 || size % (channels * 2) != 0) throw new IOException("音色 PCM 長度無效");
                byte[] data = bytes(input, size); float[] pcm = new float[size / 2];
                for (int i = 0; i < pcm.length; i++) pcm[i] = (short) le16(data, i * 2) / 32768f;
                return new PianoSample(root, rate, channels, pcm);
            } else skip(input, size);
            if ((size & 1) != 0) skip(input, 1);
        }
    }
    private static String tag(byte[] data, int offset) { return new String(data, offset, 4, StandardCharsets.US_ASCII); }
    private static int le16(byte[] b, int offset) { return (b[offset] & 255) | ((b[offset + 1] & 255) << 8); }
    private static int le32(byte[] b, int offset) { return le16(b, offset) | (le16(b, offset + 2) << 16); }
    private static byte[] bytes(InputStream input, int size) throws IOException {
        byte[] bytes = new byte[size]; int offset = 0;
        while (offset < size) { int count = input.read(bytes, offset, size - offset); if (count < 0) throw new EOFException("音色檔案不完整"); if (count == 0) { int value = input.read(); if (value < 0) throw new EOFException("音色檔案不完整"); bytes[offset++] = (byte) value; } else offset += count; }
        return bytes;
    }
    private static void skip(InputStream input, int size) throws IOException {
        while (size > 0) { long count = input.skip(size); if (count == 0) { if (input.read() < 0) throw new EOFException("音色檔案不完整"); size--; } else size -= (int) count; }
    }
}
