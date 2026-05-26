package org.seuffert.panvpx.core;

/**
 * Configuration options for a VPX Decoder (VP8 or VP9).
 *
 * <p>This immutable record specifies how the decoder should be initialized. In the most common
 * case, {@code new VpxDecoderConfig()} is sufficient: the decoder uses a single thread and
 * auto-detects frame dimensions from the bitstream header.
 *
 * <p><strong>Multi-threaded decoding example:</strong>
 *
 * <pre>{@code
 * // 4-thread VP9 decoding
 * VpxDecoderConfig config = new VpxDecoderConfig(4);
 * try (Vp9Decoder decoder = new Vp9Decoder(config)) {
 *     // Decode packets...
 * }
 * }</pre>
 *
 * @param threads The maximum number of threads the decoder may use. {@code 1} (the default) uses a
 *     single thread; higher values can significantly reduce decode latency for high-resolution VP9
 *     streams.
 * @param width Hint for the expected frame width in pixels. Pass {@code 0} (recommended) to let the
 *     decoder determine the width from the bitstream header. A non-zero value may allow the codec
 *     to pre-allocate buffers but does not constrain decoding if the actual bitstream dimensions
 *     differ.
 * @param height Hint for the expected frame height in pixels. Same semantics as {@code width}; pass
 *     {@code 0} unless you have a reliable out-of-band frame height.
 */
public record VpxDecoderConfig(int threads, int width, int height) {
    /**
     * Constructs a decoder configuration using single-threaded decoding and auto-detected
     * dimensions ({@code width = 0}, {@code height = 0}).
     */
    public VpxDecoderConfig() {
        this(1, 0, 0);
    }

    /**
     * Constructs a multi-threaded decoder configuration with auto-detected frame dimensions.
     *
     * @param threads The number of decoding threads. Must be &gt;= 1.
     */
    public VpxDecoderConfig(final int threads) {
        this(threads, 0, 0);
    }
}
