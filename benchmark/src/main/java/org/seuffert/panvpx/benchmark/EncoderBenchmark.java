package org.seuffert.panvpx.benchmark;

import java.util.List;
import org.seuffert.panvpx.core.AbstractVpxEncoder;
import org.seuffert.panvpx.core.VpxImage;
import org.seuffert.panvpx.core.VpxPacket;

/** Measures encoder throughput: frames encoded per second and output size. */
public final class EncoderBenchmark {

    private static final long FRAME_DURATION_MS = 33L;

    private EncoderBenchmark() {}

    /**
     * Runs the encoder benchmark.
     *
     * @param codecName label for the result row (e.g. {@code "VP8"})
     * @param encoder the encoder instance to benchmark (caller owns lifecycle)
     * @param source the frame source (caller owns lifecycle)
     * @param warmupFrames number of frames to encode before timing begins
     * @param measureFrames number of frames to time
     * @return the measurement result
     */
    public static BenchmarkResult run(
            final String codecName,
            final AbstractVpxEncoder encoder,
            final FrameSource source,
            final int warmupFrames,
            final int measureFrames) {

        // Warmup — encode and discard, no timing.
        for (int i = 0; i < warmupFrames; i++) {
            try (VpxImage img = source.next()) {
                encoder.encode(img, (long) i * FRAME_DURATION_MS, FRAME_DURATION_MS, 0);
            }
        }
        // Flush the codec lookahead after warmup so that warmup frames don't bleed into the
        // timed section (VP9 holds several frames in its lookahead buffer).
        List<VpxPacket> warmupDrain;
        do {
            warmupDrain = encoder.flush();
        } while (!warmupDrain.isEmpty());

        // Measurement.
        long totalEncodedBytes = 0;
        int encodedPackets = 0;
        final long start = System.nanoTime();
        for (int i = 0; i < measureFrames; i++) {
            try (VpxImage img = source.next()) {
                final long pts = (long) (warmupFrames + i) * FRAME_DURATION_MS;
                final List<VpxPacket> packets = encoder.encode(img, pts, FRAME_DURATION_MS, 0);
                for (final VpxPacket pkt : packets) {
                    encodedPackets++;
                    totalEncodedBytes += pkt.size();
                }
            }
        }

        // Drain remaining packets — VP9 needs multiple flush calls to empty its lookahead buffer.
        // Flush is part of the encode pipeline and must be included in the elapsed time.
        List<VpxPacket> flushed;
        do {
            flushed = encoder.flush();
            for (final VpxPacket pkt : flushed) {
                encodedPackets++;
                totalEncodedBytes += pkt.size();
            }
        } while (!flushed.isEmpty());

        final long elapsed = System.nanoTime() - start;

        if (encodedPackets != measureFrames) {
            System.err.printf(
                    "[WARN] Encoder/%s: submitted %d frames but received %d output packets%n",
                    codecName, measureFrames, encodedPackets);
        }

        return new BenchmarkResult("Encoder", codecName, measureFrames, elapsed, totalEncodedBytes);
    }
}
