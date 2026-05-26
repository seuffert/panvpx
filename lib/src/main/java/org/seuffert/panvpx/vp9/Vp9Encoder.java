package org.seuffert.panvpx.vp9;

import org.seuffert.panvpx.core.AbstractVpxEncoder;
import org.seuffert.panvpx.core.VpxEncoderConfig;
import org.seuffert.panvpx.ffi.VpxFFI;

/**
 * VP9 video encoder using libvpx via Project Panama FFM API.
 *
 * <p>VP9 is the successor to VP8 developed by Google. It achieves roughly 30&ndash;50&nbsp;% better
 * compression than VP8 at the same visual quality, and is widely used on YouTube, in WebM
 * containers, and in modern web browsers. Choose VP9 when bitrate efficiency matters and the extra
 * encoding CPU cost is acceptable.
 *
 * <p>For real-time encoding, configure {@link VpxEncoderConfig#cpuUsed} between 5 and 8, use {@link
 * VpxEncoderConfig#DEADLINE_REALTIME}, and enable {@link VpxEncoderConfig#rowMt} with multiple
 * threads to maximize throughput.
 *
 * <p>For offline or quality-first encoding, use {@link VpxEncoderConfig#DEADLINE_GOOD_QUALITY} with
 * {@code cpuUsed} 0&ndash;3.
 *
 * <p><strong>Example &mdash; encode a sequence of frames:</strong>
 *
 * <pre>{@code
 * // Simple 720p encoding with default settings
 * try (Vp9Encoder encoder = new Vp9Encoder(1280, 720)) {
 *     long pts = 0;
 *     for (byte[] rawFrame : frameSource) {
 *         try (VpxImage image = VpxImage.fromByteArray(rawFrame, 1280, 720)) {
 *             encoder.encode(image, pts++, 1L, 0L)
 *                    .forEach(p -> send(p.toByteArray()));
 *         }
 *     }
 *     // VP9 buffers frames in a lookahead; drain them with a loop
 *     List<VpxPacket> batch;
 *     do {
 *         batch = encoder.flush();
 *         batch.forEach(p -> send(p.toByteArray()));
 *     } while (!batch.isEmpty());
 * }
 * }</pre>
 *
 * <p><strong>Resource management:</strong> This class allocates native memory. Always use
 * try-with-resources or call {@link #close()} explicitly to avoid native memory leaks.
 *
 * @see VpxEncoderConfig
 * @see org.seuffert.panvpx.vp9.Vp9Decoder
 */
public final class Vp9Encoder extends AbstractVpxEncoder {

    /**
     * Initializes the VP9 encoder with the provided configuration.
     *
     * <p>VP9-specific controls applied here:
     *
     * <ul>
     *   <li>{@link VpxEncoderConfig#rowMt()} &mdash; when {@code true}, enables row-based
     *       multi-threading, which distributes encoding work across rows in parallel. Requires
     *       {@code threads > 1} to have any effect.
     *   <li>{@link VpxEncoderConfig#tileColumns()} &mdash; when &gt; 1, splits the frame into
     *       independent tile columns for parallel encoding and decoding. Improves multi-core
     *       utilization at a slight compression cost.
     * </ul>
     *
     * <p>If initialization fails (e.g. invalid configuration or ABI mismatch), all native resources
     * are released before the exception propagates &mdash; no native memory is leaked.
     *
     * @param config The encoder configuration.
     * @throws VpxException if the native encoder cannot be initialized.
     */
    public Vp9Encoder(final VpxEncoderConfig config) {
        super(config, VpxFFI.vpx_codec_vp9_cx());
        if (config.rowMt()) {
            codecControl(VpxFFI.VP9E_SET_ROW_MT(), 1);
        }
        if (config.tileColumns() > 1) {
            // tileColumns is the actual column count (2, 4, 8, … 64).
            // libvpx expects the log2 of that count (1, 2, 3, … 6 respectively).
            codecControl(
                    VpxFFI.VP9E_SET_TILE_COLUMNS(),
                    Integer.numberOfTrailingZeros(config.tileColumns()));
        }
    }

    /**
     * Convenience constructor: initializes the VP9 encoder with 256&nbsp;kbps target bitrate, a
     * single encoding thread, and a 1/1000&nbsp;ms timebase at real-time deadline.
     *
     * <p>Equivalent to {@code new Vp9Encoder(new VpxEncoderConfig(width, height))}. Suitable for
     * quick prototyping. For production use, construct a {@link VpxEncoderConfig} explicitly to
     * control bitrate, thread count, deadline, and VP9-specific options.
     *
     * @param width The frame width in pixels.
     * @param height The frame height in pixels.
     * @throws VpxException if the native encoder cannot be initialized.
     */
    public Vp9Encoder(final int width, final int height) {
        this(new VpxEncoderConfig(width, height));
    }

    @Override
    public String getCodecName() {
        return "VP9";
    }
}
