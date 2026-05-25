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
 */
public record VpxEncoderConfig(
        int width,
        int height,
        int targetBitrateKbps,
        int frameDropThreshold,
        int threads,
        int timebaseNumerator,
        int timebaseDenominator) {
    /**
     * Constructs a basic encoder configuration using reasonable defaults. Target bitrate is
     * 256kbps, single threaded, with a 1/1000 millisecond timebase.
     *
     * @param width The width of the video frame.
     * @param height The height of the video frame.
     */
    public VpxEncoderConfig(final int width, final int height) {
        this(width, height, 256, 0, 1, 1, 1000);
    }

    /**
     * Constructs a basic encoder configuration specifying bitrate and threading.
     *
     * @param width The width of the video frame.
     * @param height The height of the video frame.
     * @param targetBitrateKbps The target bitrate in kilobits per second.
     * @param threads The number of threads to use.
     */
    public VpxEncoderConfig(
            final int width, final int height, final int targetBitrateKbps, final int threads) {
        this(width, height, targetBitrateKbps, 0, threads, 1, 1000);
    }
}
