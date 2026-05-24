package org.seuffert.panvpx.vp8;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;
import org.seuffert.panvpx.core.VpxException;
import org.seuffert.panvpx.core.VpxImage;
import org.seuffert.panvpx.core.VpxPacket;
import org.seuffert.panvpx.ffi.VpxFFI;
import org.seuffert.panvpx.ffi.vpx_codec_ctx;
import org.seuffert.panvpx.ffi.vpx_codec_cx_pkt;
import org.seuffert.panvpx.ffi.vpx_codec_enc_cfg;
import org.seuffert.panvpx.ffi.vpx_rational;

/** VP8 Video Encoder using libvpx via Project Panama FFM API. */
public class Vp8Encoder implements AutoCloseable {

    private final Arena arena = Arena.ofShared();
    private final MemorySegment codecCtx;
    private final MemorySegment iterPtr;

    /**
     * Initializes the VP8 Encoder with the provided configuration. The encoder allocates native
     * memory that must be released by calling {@link #close()}.
     *
     * @param config The encoder configuration.
     */
    public Vp8Encoder(VpxEncoderConfig config) {
        codecCtx = vpx_codec_ctx.allocate(arena);

        // 1. Get the VP8 encoder interface
        MemorySegment iface = VpxFFI.vpx_codec_vp8_cx();

        // 2. Populate default configuration
        MemorySegment encCfg = vpx_codec_enc_cfg.allocate(arena);
        int res = VpxFFI.vpx_codec_enc_config_default(iface, encCfg, 0);
        checkError(res, "Failed to get default encoder configuration");

        // 3. Apply custom configuration
        vpx_codec_enc_cfg.g_w(encCfg, config.width());
        vpx_codec_enc_cfg.g_h(encCfg, config.height());
        vpx_codec_enc_cfg.rc_target_bitrate(encCfg, config.targetBitrateKbps());
        vpx_codec_enc_cfg.rc_dropframe_thresh(encCfg, config.frameDropThreshold());
        vpx_codec_enc_cfg.g_threads(encCfg, config.threads());

        MemorySegment timebase = vpx_codec_enc_cfg.g_timebase(encCfg);
        vpx_rational.num(timebase, config.timebaseNumerator());
        vpx_rational.den(timebase, config.timebaseDenominator());

        // 4. Initialize the encoder
        res =
                VpxFFI.vpx_codec_enc_init_ver(
                        codecCtx, iface, encCfg, 0, VpxFFI.VPX_ENCODER_ABI_VERSION());
        checkError(res, "Failed to initialize VP8 encoder");

        // Allocate iterator pointer for vpx_codec_get_cx_data
        iterPtr = arena.allocate(ValueLayout.ADDRESS);
    }

    /**
     * Encodes a single frame.
     *
     * @param image The VpxImage containing the uncompressed frame data.
     * @param pts The presentation timestamp of the frame.
     * @param duration The duration to show the frame.
     * @param flags Encoding flags (e.g., VPX_EFLAG_FORCE_KF for keyframes).
     * @return A list of encoded packets.
     */
    public List<VpxPacket> encode(VpxImage image, long pts, long duration, long flags) {
        int res =
                VpxFFI.vpx_codec_encode(
                        codecCtx,
                        image.getNativeImage(),
                        pts,
                        duration,
                        flags,
                        VpxFFI.VPX_DL_REALTIME());
        checkError(res, "Failed to encode frame");

        return extractPackets();
    }

    /**
     * Flushes the encoder, returning any delayed packets.
     *
     * @return A list of delayed encoded packets.
     */
    public List<VpxPacket> flush() {
        int res =
                VpxFFI.vpx_codec_encode(
                        codecCtx, MemorySegment.NULL, 0, 0, 0, VpxFFI.VPX_DL_REALTIME());
        checkError(res, "Failed to flush encoder");
        return extractPackets();
    }

    private List<VpxPacket> extractPackets() {
        List<VpxPacket> packets = new ArrayList<>();

        // Reset iterator pointer to NULL (0)
        iterPtr.set(ValueLayout.ADDRESS, 0, MemorySegment.NULL);

        while (true) {
            MemorySegment pktPtr = VpxFFI.vpx_codec_get_cx_data(codecCtx, iterPtr);
            if (pktPtr.address() == 0L) {
                break;
            }

            // Check if packet is a frame packet
            int kind = vpx_codec_cx_pkt.kind(pktPtr);
            if (kind == VpxFFI.VPX_CODEC_CX_FRAME_PKT()) {
                // Get the frame struct inside the union
                MemorySegment dataLayout = vpx_codec_cx_pkt.data(pktPtr);

                MemorySegment bufAddress =
                        org.seuffert.panvpx.ffi.vpx_codec_cx_pkt.data.frame.buf(dataLayout);
                long bufSize = org.seuffert.panvpx.ffi.vpx_codec_cx_pkt.data.frame.sz(dataLayout);

                if (bufAddress.address() != 0L && bufSize > 0) {
                    MemorySegment dataSegment = bufAddress.reinterpret(bufSize);
                    packets.add(new VpxPacket(dataSegment));
                }
            }
        }

        return packets;
    }

    /** Destroys the native encoder context and releases all associated native memory. */
    @Override
    public void close() {
        VpxFFI.vpx_codec_destroy(codecCtx);
        arena.close();
    }

    private void checkError(int res, String message) {
        if (res != VpxFFI.VPX_CODEC_OK()) {
            MemorySegment errDetailPtr = VpxFFI.vpx_codec_error_detail(codecCtx);
            String detail =
                    (errDetailPtr.address() != 0L)
                            ? errDetailPtr.getString(0)
                            : "No detail available";
            throw new VpxException(res, message + ": " + detail);
        }
    }
}
