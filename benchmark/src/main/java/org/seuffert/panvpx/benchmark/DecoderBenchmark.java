package org.seuffert.panvpx.benchmark;

import java.util.ArrayList;
import java.util.List;
import org.seuffert.panvpx.core.AbstractVpxDecoder;
import org.seuffert.panvpx.core.AbstractVpxEncoder;
import org.seuffert.panvpx.core.VpxImage;
import org.seuffert.panvpx.core.VpxPacket;

/**
 * Measures decoder throughput: frames decoded per second.
 *
 * <p>Setup (untimed): pre-encodes {@code measureFrames} frames using a throwaway encoder and stores
 * them as heap byte arrays. Warmup then decodes all packets once (discarded) before the timed
 * measurement pass.
 */
public final class DecoderBenchmark {

    private static final long FRAME_DURATION_MS = 33L;

    private DecoderBenchmark() {}

    /**
     * Runs the decoder benchmark.
     *
     * @param codecName label for the result row (e.g. {@code "VP8"})
     * @param setupEncoder throwaway encoder used to produce packets for decoding; caller owns
     *     lifecycle and must close it after this method returns
     * @param decoder the decoder instance to benchmark; caller owns lifecycle
     * @param source frame source used during the setup encoding phase; caller owns lifecycle
     * @param warmupFrames number of decode passes to run before timing begins
     * @param measureFrames number of frames to time
     * @return the measurement result
     */
    public static BenchmarkResult run(
            final String codecName,
            final AbstractVpxEncoder setupEncoder,
            final AbstractVpxDecoder decoder,
            final FrameSource source,
            final int warmupFrames,
            final int measureFrames) {

        // Setup (untimed): encode frames and store packet bytes on the heap.
        final List<byte[]> encodedPackets = preEncode(setupEncoder, source, measureFrames);

        // Warmup: decode all packets once, discard results.
        for (final byte[] data : encodedPackets) {
            decoder.decode(data);
        }
        // Drain decoder after warmup so buffered frames don't bleed into measurement.
        List<VpxImage> warmupDrain;
        do {
            warmupDrain = decoder.flush();
        } while (!warmupDrain.isEmpty());

        // Total compressed bytes fed to the decoder — used to show input packet sizes.
        long totalEncodedBytes = 0;
        for (final byte[] pkt : encodedPackets) {
            totalEncodedBytes += pkt.length;
        }

        // Measurement: decode the same set of packets and time it, including the final drain.
        final long start = System.nanoTime();
        int decodedFrames = 0;
        for (final byte[] data : encodedPackets) {
            final List<VpxImage> frames = decoder.decode(data);
            decodedFrames += frames.size();
        }
        // Drain any frames still buffered in the decoder pipeline.
        List<VpxImage> flushed;
        do {
            flushed = decoder.flush();
            decodedFrames += flushed.size();
        } while (!flushed.isEmpty());
        final long elapsed = System.nanoTime() - start;

        final int expectedFrames = encodedPackets.size();
        if (decodedFrames != expectedFrames) {
            System.err.printf(
                    "[WARN] Decoder/%s: submitted %d packets but decoded %d frames%n",
                    codecName, expectedFrames, decodedFrames);
        }

        return new BenchmarkResult("Decoder", codecName, decodedFrames, elapsed, totalEncodedBytes);
    }

    /**
     * Runs the decoder benchmark using a pre-supplied list of encoded packets.
     *
     * <p>Use this overload when packets come from an external source (e.g. a pre-encoded IVF clip)
     * and no encoder setup step is required. Packets are cycled when {@code measureFrames} exceeds
     * the list size.
     *
     * @param codecName label for the result row (e.g. {@code "VP9"})
     * @param decoder the decoder instance to benchmark; caller owns lifecycle
     * @param encodedPackets pre-encoded packets to decode; cycled if fewer than {@code
     *     measureFrames}
     * @param warmupFrames number of decode iterations to run before timing begins
     * @param measureFrames number of decode iterations to time
     * @return the measurement result
     */
    public static BenchmarkResult run(
            final String codecName,
            final AbstractVpxDecoder decoder,
            final List<byte[]> encodedPackets,
            final int warmupFrames,
            final int measureFrames) {

        // Warmup: decode warmupFrames packets cycling through the list, discard results.
        for (int i = 0; i < warmupFrames; i++) {
            decoder.decode(encodedPackets.get(i % encodedPackets.size()));
        }
        // Drain decoder after warmup so buffered frames don't bleed into measurement.
        List<VpxImage> warmupDrain;
        do {
            warmupDrain = decoder.flush();
        } while (!warmupDrain.isEmpty());

        // Count the bytes that will be decoded during the measurement pass.
        long totalEncodedBytes = 0;
        for (int i = 0; i < measureFrames; i++) {
            totalEncodedBytes += encodedPackets.get(i % encodedPackets.size()).length;
        }

        // Measurement.
        final long start = System.nanoTime();
        int decodedFrames = 0;
        for (int i = 0; i < measureFrames; i++) {
            final byte[] data = encodedPackets.get(i % encodedPackets.size());
            final List<VpxImage> frames = decoder.decode(data);
            decodedFrames += frames.size();
        }
        // Drain any frames still buffered in the decoder pipeline.
        List<VpxImage> flushed;
        do {
            flushed = decoder.flush();
            decodedFrames += flushed.size();
        } while (!flushed.isEmpty());
        final long elapsed = System.nanoTime() - start;

        if (decodedFrames != measureFrames) {
            System.err.printf(
                    "[WARN] Decoder/%s: submitted %d packets but decoded %d frames%n",
                    codecName, measureFrames, decodedFrames);
        }

        return new BenchmarkResult("Decoder", codecName, decodedFrames, elapsed, totalEncodedBytes);
    }

    /**
     * Runs the decoder benchmark using a pre-supplied list of encoded packets.
     *
     * <p>Use this overload when packets come from an external source (e.g. a pre-encoded IVF clip)
     * and no encoder setup step is required. Packets are cycled when {@code measureFrames} exceeds
     * the list size.
     */
    private static List<byte[]> preEncode(
            final AbstractVpxEncoder encoder, final FrameSource source, final int frameCount) {
        final List<byte[]> result = new ArrayList<>(frameCount);
        for (int i = 0; i < frameCount; i++) {
            try (VpxImage img = source.next()) {
                final List<VpxPacket> packets =
                        encoder.encode(img, (long) i * FRAME_DURATION_MS, FRAME_DURATION_MS, 0);
                for (final VpxPacket pkt : packets) {
                    result.add(pkt.toByteArray());
                }
            }
        }
        // Drain remaining packets — VP9 requires multiple flush() calls to empty its lookahead.
        List<VpxPacket> flushed;
        do {
            flushed = encoder.flush();
            for (final VpxPacket pkt : flushed) {
                result.add(pkt.toByteArray());
            }
        } while (!flushed.isEmpty());
        return result;
    }
}
