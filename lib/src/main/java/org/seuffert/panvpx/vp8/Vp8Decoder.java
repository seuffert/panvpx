package org.seuffert.panvpx.vp8;

import org.seuffert.panvpx.core.AbstractVpxDecoder;
import org.seuffert.panvpx.core.VpxDecoderConfig;
import org.seuffert.panvpx.core.VpxException;
import org.seuffert.panvpx.ffi.VpxFFI;

/**
 * VP8 video decoder using libvpx via Project Panama FFM API.
 *
 * <p>Decodes VP8 compressed bitstream packets into raw I420 video frames. VP8 is widely supported
 * across browsers, mobile devices, and hardware decoders; this class is the right choice when
 * decoding VP8 streams from WebRTC, WebM, or other VP8 sources.
 *
 * <p><strong>Example &mdash; decode a stream:</strong>
 *
 * <pre>{@code
 * try (Vp8Decoder decoder = new Vp8Decoder(new VpxDecoderConfig())) {
 *     for (byte[] vp8Packet : packetSource) {
 *         List<VpxImage> frames = decoder.decode(vp8Packet);
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
 * @see org.seuffert.panvpx.vp8.Vp8Encoder
 */
public final class Vp8Decoder extends AbstractVpxDecoder {

    /**
     * Initializes the VP8 decoder with the provided configuration.
     *
     * @param config The decoder configuration (thread count, optional frame dimensions).
     * @throws VpxException if the native decoder cannot be initialized.
     */
    public Vp8Decoder(final VpxDecoderConfig config) {
        super(config, VpxFFI.vpx_codec_vp8_dx());
    }

    @Override
    public String getCodecName() {
        return "VP8";
    }
}
