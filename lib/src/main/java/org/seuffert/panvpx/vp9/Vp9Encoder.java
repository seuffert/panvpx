package org.seuffert.panvpx.vp9;

import org.seuffert.panvpx.core.AbstractVpxEncoder;
import org.seuffert.panvpx.core.VpxEncoderConfig;
import org.seuffert.panvpx.ffi.VpxFFI;

/** VP9 Video Encoder using libvpx via Project Panama FFM API. */
public final class Vp9Encoder extends AbstractVpxEncoder {

    /**
     * Initializes the VP9 Encoder with the provided configuration. The encoder allocates native
     * memory that must be released by calling {@link #close()}.
     *
     * <p>If initialization fails (e.g. invalid configuration), all native resources are released
     * before the exception propagates — no native memory is leaked.
     *
     * @param config The encoder configuration.
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
     * Initializes the VP9 Encoder with a basic configuration using reasonable defaults. Target
     * bitrate is 256 kbps, single-threaded, with a 1/1000 ms timebase.
     *
     * @param width The width of the video frame.
     * @param height The height of the video frame.
     */
    public Vp9Encoder(final int width, final int height) {
        this(new VpxEncoderConfig(width, height));
    }

    @Override
    public String getCodecName() {
        return "VP9";
    }
}
