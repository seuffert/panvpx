package org.seuffert.panvpx.vp8;

/**
 * Configuration options for the VP8 Decoder.
 *
 * @param threads The maximum number of threads to use for decoding. Default is 1.
 * @param width The intended width of the video frame, if known in advance. Can be 0.
 * @param height The intended height of the video frame, if known in advance. Can be 0.
 */
public record VpxDecoderConfig(int threads, int width, int height) {
    /**
     * Constructs a basic decoder configuration using single-threaded decoding and auto-detected
     * dimensions.
     */
    public VpxDecoderConfig() {
        this(1, 0, 0);
    }

    /**
     * Constructs a decoder configuration specifying the thread count.
     *
     * @param threads The number of threads to use.
     */
    public VpxDecoderConfig(final int threads) {
        this(threads, 0, 0);
    }
}
