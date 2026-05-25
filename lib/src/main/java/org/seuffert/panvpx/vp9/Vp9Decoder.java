package org.seuffert.panvpx.vp9;

import org.seuffert.panvpx.core.AbstractVpxDecoder;
import org.seuffert.panvpx.core.VpxDecoderConfig;
import org.seuffert.panvpx.ffi.VpxFFI;

/** VP9 Video Decoder using libvpx via Project Panama FFM API. */
public final class Vp9Decoder extends AbstractVpxDecoder {

    /**
     * Initializes the VP9 Decoder with the provided configuration.
     *
     * @param config The decoder configuration.
     */
    public Vp9Decoder(final VpxDecoderConfig config) {
        super(config, VpxFFI.vpx_codec_vp9_dx());
    }

    /**
     * Initializes the VP9 Decoder with a basic configuration using single-threaded decoding and
     * auto-detected dimensions.
     */
    public Vp9Decoder() {
        this(new VpxDecoderConfig());
    }

    @Override
    public String getCodecName() {
        return "VP9";
    }
}
