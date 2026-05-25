package org.seuffert.panvpx.vp8;

import org.seuffert.panvpx.core.AbstractVpxEncoder;
import org.seuffert.panvpx.core.VpxEncoderConfig;
import org.seuffert.panvpx.ffi.VpxFFI;

/** VP8 Video Encoder using libvpx via Project Panama FFM API. */
public final class Vp8Encoder extends AbstractVpxEncoder {

    /**
     * Initializes the VP8 Encoder with the provided configuration. The encoder allocates native
     * memory that must be released by calling {@link #close()}.
     *
     * <p>If initialization fails (e.g. invalid configuration), all native resources are released
     * before the exception propagates — no native memory is leaked.
     *
     * @param config The encoder configuration.
     */
    public Vp8Encoder(final VpxEncoderConfig config) {
        super(config, VpxFFI.vpx_codec_vp8_cx());
    }

    @Override
    public String getCodecName() {
        return "VP8";
    }
}
