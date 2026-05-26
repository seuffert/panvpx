package org.seuffert.panvpx.core;

/**
 * Configuration options for a VPX Encoder (VP8 or VP9).
 *
 * @param width The width of the video frame.
 * @param height The height of the video frame.
 * @param targetBitrateKbps The target bitrate in kilobits per second.
 * @param frameDropThreshold The threshold for dropping frames to hit the target bitrate.
 * @param threads The number of threads to use for encoding.
 * @param timebaseNumerator The numerator of the timebase fraction.
 * @param timebaseDenominator The denominator of the timebase fraction.
 * @param deadline The per-frame encoding deadline in microseconds. Use one of the {@code
 *     DEADLINE_*} constants: {@link #DEADLINE_REALTIME} for low-latency streaming or {@link
 *     #DEADLINE_GOOD_QUALITY} for throughput benchmarks and general use.
 * @param cpuUsed CPU usage / speed control. The valid range and effect differ by codec and
 *     deadline:
 *     <ul>
 *       <li><strong>VP8</strong>: 0–16. {@code 0} = best quality (slowest); {@code 16} = fastest
 *           encoding. The default is {@code 0}.
 *       <li><strong>VP9 with {@link #DEADLINE_GOOD_QUALITY}</strong>: 0–5. {@code 0} = highest
 *           quality (slowest); {@code 5} = fastest within the deadline. The default is {@code 0}.
 *       <li><strong>VP9 with {@link #DEADLINE_REALTIME}</strong>: 5–8. Values below 5 are not
 *           recommended for real-time use. {@code 5} = best realtime quality; {@code 8} = fastest.
 *     </ul>
 *
 * @param rowMt Enable row-based multithreading (VP9 only). When {@code true} and {@code threads >
 *     1}, libvpx encodes rows in parallel, significantly improving throughput.
 * @param tileColumns Number of VP9 tile columns (VP9 only). Pass the actual column count: {@code 1}
 *     (or {@code 0} for codec default), {@code 2}, {@code 4}, {@code 8}, {@code 16}, {@code 32}, or
 *     {@code 64}. More tile columns improve multi-core utilisation but may slightly reduce
 *     compression efficiency. Values other than these powers of two are invalid.
 * @param tokenPartitions Number of VP8 token partitions / slices (VP8 only). Pass the actual
 *     partition count: {@code 1} (or {@code 0} for codec default), {@code 2}, {@code 4}, or {@code
 *     8}. More partitions allow parallel decoding of a single frame but may slightly reduce
 *     compression efficiency. Values other than these four are invalid.
 */
public record VpxEncoderConfig(
        int width,
        int height,
        int targetBitrateKbps,
        int frameDropThreshold,
        int threads,
        int timebaseNumerator,
        int timebaseDenominator,
        long deadline,
        int cpuUsed,
        boolean rowMt,
        int tileColumns,
        int tokenPartitions) {

    /** Real-time deadline (1 µs): fastest encoding, lowest quality. */
    public static final long DEADLINE_REALTIME = 1L;

    /** Good-quality deadline (1 s): balanced quality and speed; the default. */
    public static final long DEADLINE_GOOD_QUALITY = 1_000_000L;

    /** Best-quality deadline (0 = unlimited): highest quality, slowest encoding. */
    public static final long DEADLINE_BEST_QUALITY = 0L;

    /**
     * Constructs a basic encoder configuration using reasonable defaults. Target bitrate is
     * 256kbps, single threaded, with a 1/1000 millisecond timebase and real-time deadline.
     *
     * @param width The width of the video frame.
     * @param height The height of the video frame.
     */
    public VpxEncoderConfig(final int width, final int height) {
        this(width, height, 256, 0, 1, 1, 1000, DEADLINE_REALTIME, 0, false, 0, 0);
    }

    /**
     * Constructs a basic encoder configuration specifying bitrate and threading. Uses the real-time
     * encoding deadline.
     *
     * @param width The width of the video frame.
     * @param height The height of the video frame.
     * @param targetBitrateKbps The target bitrate in kilobits per second.
     * @param threads The number of threads to use.
     */
    public VpxEncoderConfig(
            final int width, final int height, final int targetBitrateKbps, final int threads) {
        this(
                width,
                height,
                targetBitrateKbps,
                0,
                threads,
                1,
                1000,
                DEADLINE_REALTIME,
                0,
                false,
                0,
                0);
    }

    /**
     * Constructs a basic encoder configuration specifying bitrate, threading, and encoding
     * deadline.
     *
     * @param width The width of the video frame.
     * @param height The height of the video frame.
     * @param targetBitrateKbps The target bitrate in kilobits per second.
     * @param threads The number of threads to use.
     * @param deadline The per-frame encoding deadline in microseconds (see {@link
     *     #DEADLINE_REALTIME}, {@link #DEADLINE_GOOD_QUALITY}).
     */
    public VpxEncoderConfig(
            final int width,
            final int height,
            final int targetBitrateKbps,
            final int threads,
            final long deadline) {
        this(width, height, targetBitrateKbps, 0, threads, 1, 1000, deadline, 0, false, 0, 0);
    }
}
