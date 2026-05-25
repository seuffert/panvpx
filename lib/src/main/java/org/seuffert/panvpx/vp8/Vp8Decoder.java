package org.seuffert.panvpx.vp8;

import org.seuffert.panvpx.core.AbstractVpxDecoder;
import org.seuffert.panvpx.core.VpxDecoderConfig;
import org.seuffert.panvpx.ffi.VpxFFI;

/** VP8 Video Decoder using libvpx via Project Panama FFM API. */
public final class Vp8Decoder extends AbstractVpxDecoder {

    /**
     * Initializes the VP8 Decoder with the provided configuration.
     *
     * @param config The decoder configuration.
     */
    public Vp8Decoder(final VpxDecoderConfig config) {
        super(config, VpxFFI.vpx_codec_vp8_dx());
    }

    @Override
    public String getCodecName() {
        return "VP8";
    }
}
