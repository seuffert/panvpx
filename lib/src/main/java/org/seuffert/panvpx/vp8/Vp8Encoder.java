package org.seuffert.panvpx.vp8;

import org.seuffert.panvpx.core.AbstractVpxEncoder;
import org.seuffert.panvpx.core.VpxEncoderConfig;
import org.seuffert.panvpx.core.VpxException;
import org.seuffert.panvpx.ffi.VpxFFI;

/**
 * VP8 video encoder using libvpx via Project Panama FFM API.
 *
 * <p>VP8 is a widely-deployed open video codec used in WebRTC, WebM containers, and many streaming
 * applications. It offers good compression and near-universal hardware and software decoder
 * support. Choose VP8 when compatibility across older browsers and devices matters more than
 * maximizing compression efficiency.
 *
 * <p>For better compression at the cost of higher CPU usage, see {@link
 * org.seuffert.panvpx.vp9.Vp9Encoder}.
 *
 * <p><strong>Example &mdash; encode a sequence of frames:</strong>
 *
 * <pre>{@code
 * VpxEncoderConfig config = VpxEncoderConfig.builder(VpxEncoderConfig.Codec.VP8, 640, 480).targetBitrateKbps(512).threads(2).build();
 * try (Vp8Encoder encoder = new Vp8Encoder(config)) {
 *     long pts = 0;
 *     for (byte[] rawFrame : frameSource) {
 *         try (VpxImage image = VpxImage.fromByteArray(rawFrame, 640, 480)) {
 *             List<VpxPacket> packets = encoder.encode(image, pts++, 1L, 0L);
 *             packets.forEach(p -> send(p.toByteArray()));
 *         }
 *     }
 *     // Flush any buffered packets at end of stream
 *     for (VpxPacket p : encoder.flush()) {
 *         send(p.toByteArray());
 *     }
 * }
 * }</pre>
 *
 * <p><strong>Resource management:</strong> This class allocates native memory. Always use
 * try-with-resources or call {@link #close()} explicitly to avoid native memory leaks.
 *
 * @see VpxEncoderConfig
 * @see org.seuffert.panvpx.vp8.Vp8Decoder
 */
public final class Vp8Encoder extends AbstractVpxEncoder {

    /**
     * Initializes the VP8 encoder with the provided configuration.
     *
     * <p>The {@code tokenPartitions} field in {@link VpxEncoderConfig} allows splitting a VP8 frame
     * into multiple independently decodable token partitions (2, 4, or 8). More partitions enable
     * parallel decoding of a single frame on multi-core hardware but may slightly reduce
     * compression efficiency.
     *
     * <p>If initialization fails (e.g. invalid configuration or ABI mismatch), all native resources
     * are released before the exception propagates &mdash; no native memory is leaked.
     *
     * @param config The encoder configuration.
     * @throws VpxException if the native encoder cannot be initialized.
     */
    public Vp8Encoder(final VpxEncoderConfig config) {
        super(config, VpxFFI.vpx_codec_vp8_cx());
        if (config.tokenPartitions() > 1) {
            // tokenPartitions is the actual partition count (2, 4, or 8).
            // libvpx expects the log2 of that count (1, 2, or 3 respectively).
            codecControl(
                    VpxFFI.VP8E_SET_TOKEN_PARTITIONS(),
                    Integer.numberOfTrailingZeros(config.tokenPartitions()));
        }
    }

    @Override
    public String getCodecName() {
        return "VP8";
    }
}
