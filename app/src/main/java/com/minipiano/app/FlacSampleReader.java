package com.minipiano.app;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Decodes a bundled FLAC asset into the same 16-bit PCM used by the WAV path. */
final class FlacSampleReader {
    private FlacSampleReader() {}

    static PianoSample read(AssetManager assets, String name, int root) throws IOException {
        try (AssetFileDescriptor descriptor = assets.openFd(name)) {
            MediaExtractor extractor = new MediaExtractor();
            MediaCodec codec = null;
            try {
                extractor.setDataSource(descriptor.getFileDescriptor(), descriptor.getStartOffset(), descriptor.getLength());
                MediaFormat format = null;
                for (int i = 0; i < extractor.getTrackCount(); i++) {
                    MediaFormat candidate = extractor.getTrackFormat(i);
                    String mime = candidate.getString(MediaFormat.KEY_MIME);
                    if (mime != null && mime.startsWith("audio/")) { extractor.selectTrack(i); format = candidate; break; }
                }
                if (format == null) throw new IOException("音色沒有音訊軌：" + name);
                codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
                codec.configure(format, null, null, 0);
                codec.start();
                ShortList pcm = new ShortList();
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                boolean inputDone = false, outputDone = false;
                int rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                int channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                int encoding = AudioFormat.ENCODING_PCM_16BIT;
                while (!outputDone) {
                    if (!inputDone) {
                        int input = codec.dequeueInputBuffer(10000);
                        if (input >= 0) {
                            ByteBuffer buffer = codec.getInputBuffer(input);
                            int size = extractor.readSampleData(buffer, 0);
                            if (size < 0) {
                                codec.queueInputBuffer(input, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                inputDone = true;
                            } else {
                                codec.queueInputBuffer(input, 0, size, extractor.getSampleTime(), 0);
                                extractor.advance();
                            }
                        }
                    }
                    int output = codec.dequeueOutputBuffer(info, 10000);
                    if (output == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        MediaFormat changed = codec.getOutputFormat();
                        rate = changed.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                        channels = changed.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                        if (changed.containsKey(MediaFormat.KEY_PCM_ENCODING)) encoding = changed.getInteger(MediaFormat.KEY_PCM_ENCODING);
                    } else if (output >= 0) {
                        if (info.size > 0) {
                            ByteBuffer buffer = codec.getOutputBuffer(output);
                            if (buffer != null) {
                                buffer.position(info.offset);
                                buffer.limit(info.offset + info.size);
                                append(buffer.order(ByteOrder.nativeOrder()), encoding, pcm);
                            }
                        }
                        codec.releaseOutputBuffer(output, false);
                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true;
                    }
                }
                if (channels < 1 || channels > 2 || pcm.size() < channels || pcm.size() % channels != 0) throw new IOException("音色 PCM 長度無效：" + name);
                return new PianoSample(root, rate, channels, pcm.toArray());
            } finally {
                if (codec != null) { try { codec.stop(); } catch (Exception ignored) {} codec.release(); }
                extractor.release();
            }
        }
    }

    private static void append(ByteBuffer buffer, int encoding, ShortList output) throws IOException {
        if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
            var floats = buffer.asFloatBuffer();
            while (floats.hasRemaining()) output.add((short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(floats.get() * 32767))));
        } else if (encoding == AudioFormat.ENCODING_PCM_8BIT) {
            while (buffer.hasRemaining()) output.add((short) (((buffer.get() & 255) - 128) << 8));
        } else if (encoding == AudioFormat.ENCODING_PCM_24BIT_PACKED) {
            while (buffer.remaining() >= 3) {
                int value = (buffer.get() & 255) | ((buffer.get() & 255) << 8) | (buffer.get() << 16);
                output.add((short) (value >> 8));
            }
        } else {
            var shorts = buffer.asShortBuffer();
            while (shorts.hasRemaining()) output.add(shorts.get());
        }
    }

    private static final class ShortList {
        private short[] values = new short[32768];
        private int size;
        void add(short value) {
            if (size == values.length) values = java.util.Arrays.copyOf(values, values.length * 2);
            values[size++] = value;
        }
        int size() { return size; }
        short[] toArray() { return java.util.Arrays.copyOf(values, size); }
    }
}
