package org.seuffert.panvpx.vp9;

import org.seuffert.panvpx.core.AbstractVpxDecoder;
import org.seuffert.panvpx.core.VpxDecoderConfig;
import org.seuffert.panvpx.core.VpxException;
import org.seuffert.panvpx.ffi.VpxFFI;

/**
 * VP9 video decoder using libvpx via Project Panama FFM API.
 *
 * <p>Decodes VP9 compressed bitstream packets into raw I420 video frames. VP9 is used extensively
 * on YouTube, in WebM containers, and in modern web browsers. It provides better compression than
 * VP8 at the same quality level.
 *
 * <p>The simplest way to create a decoder is the no-argument constructor {@link #Vp9Decoder()},
 * which uses a single thread and auto-detects frame dimensions from the bitstream.
 *
 * <p><strong>Example &mdash; decode a stream:</strong>
 *
 * <pre>{@code
 * try (Vp9Decoder decoder = new Vp9Decoder()) {
 *     for (byte[] vp9Packet : packetSource) {
 *         List<VpxImage> frames = decoder.decode(vp9Packet);
 *         for (VpxImage frame : frames) {
 *             byte[] i420 = frame.toByteArray(); // safe copy
 *             display(i420, frame.width(), frame.height());
 *         }
 *     }
 * }
 * }</pre>
 *
 * <p><strong>Resource management:</strong> This class allocates native memory. Always use
 * try-with-resources or call {@link #close()} explicitly to avoid native memory leaks.
 *
 * @see VpxDecoderConfig
 * @see org.seuffert.panvpx.vp9.Vp9Encoder
 */
public final class Vp9Decoder extends AbstractVpxDecoder {

    /**
     * Initializes the VP9 decoder with the provided configuration.
     *
     * @param config The decoder configuration (thread count, optional frame dimensions).
     * @throws VpxException if the native decoder cannot be initialized.
     */
    public Vp9Decoder(final VpxDecoderConfig config) {
        super(config, VpxFFI.vpx_codec_vp9_dx());
    }

    /**
     * Convenience constructor: initializes a single-threaded VP9 decoder with auto-detected frame
     * dimensions.
     *
     * <p>Equivalent to {@code new Vp9Decoder(new VpxDecoderConfig())}. Sufficient for most
     * use-cases. For multi-threaded decoding of high-resolution streams, construct a {@link
     * VpxDecoderConfig} with a thread count greater than 1.
     *
     * @throws VpxException if the native decoder cannot be initialized.
     */
    public Vp9Decoder() {
        this(new VpxDecoderConfig());
    }

    @Override
    public String getCodecName() {
        return "VP9";
    }
}
