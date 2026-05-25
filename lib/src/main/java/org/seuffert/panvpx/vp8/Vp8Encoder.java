package org.seuffert.panvpx.vp8;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
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

    private final Arena arena;
    private final MemorySegment codecCtx;
    private final MemorySegment iterPtr;
    private final AtomicBoolean closed = new AtomicBoolean();

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
        arena = Arena.ofShared();
        MemorySegment tempCodecCtx = MemorySegment.NULL;
        MemorySegment tempIterPtr = MemorySegment.NULL;
        boolean success = false;
        try {
            tempCodecCtx = vpx_codec_ctx.allocate(arena);

            // 1. Get the VP8 encoder interface
            final MemorySegment iface = VpxFFI.vpx_codec_vp8_cx();

            // 2. Populate default configuration
            final MemorySegment encCfg = vpx_codec_enc_cfg.allocate(arena);
            int res = VpxFFI.vpx_codec_enc_config_default(iface, encCfg, 0);
            checkError(MemorySegment.NULL, res, "Failed to get default encoder configuration");

            // 3. Apply custom configuration
            vpx_codec_enc_cfg.g_w(encCfg, config.width());
            vpx_codec_enc_cfg.g_h(encCfg, config.height());
            vpx_codec_enc_cfg.rc_target_bitrate(encCfg, config.targetBitrateKbps());
            vpx_codec_enc_cfg.rc_dropframe_thresh(encCfg, config.frameDropThreshold());
            vpx_codec_enc_cfg.g_threads(encCfg, config.threads());

            final MemorySegment timebase = vpx_codec_enc_cfg.g_timebase(encCfg);
            vpx_rational.num(timebase, config.timebaseNumerator());
            vpx_rational.den(timebase, config.timebaseDenominator());

            // 4. Initialize the encoder
            res =
                    VpxFFI.vpx_codec_enc_init_ver(
                            tempCodecCtx, iface, encCfg, 0, VpxFFI.VPX_ENCODER_ABI_VERSION());
            checkError(tempCodecCtx, res, "Failed to initialize VP8 encoder");

            // Allocate iterator pointer for vpx_codec_get_cx_data
            tempIterPtr = arena.allocate(ValueLayout.ADDRESS);
            success = true;
        } finally {
            if (!success) {
                arena.close();
            }
        }
        codecCtx = tempCodecCtx;
        iterPtr = tempIterPtr;
    }

    /**
     * Encodes a single frame.
     *
     * @param image The VpxImage containing the uncompressed frame data.
     * @param pts The presentation timestamp of the frame.
     * @param duration The duration to show the frame.
     * @param flags Encoding flags (e.g., {@code VPX_EFLAG_FORCE_KF} for keyframes).
     * @return A list of encoded packets.
     */
    public List<VpxPacket> encode(
            final VpxImage image, final long pts, final long duration, final long flags) {
        final int res =
                VpxFFI.vpx_codec_encode(
                        codecCtx,
                        image.nativeImage(),
                        pts,
                        duration,
                        flags,
                        VpxFFI.VPX_DL_REALTIME());
        checkError(codecCtx, res, "Failed to encode frame");

        return extractPackets();
    }

    /**
     * Flushes the encoder, returning any delayed packets.
     *
     * @return A list of delayed encoded packets.
     */
    public List<VpxPacket> flush() {
        final int res =
                VpxFFI.vpx_codec_encode(
                        codecCtx, MemorySegment.NULL, 0, 0, 0, VpxFFI.VPX_DL_REALTIME());
        checkError(codecCtx, res, "Failed to flush encoder");
        return extractPackets();
    }

    private List<VpxPacket> extractPackets() {
        final List<VpxPacket> packets = new ArrayList<>();

        // Reset iterator pointer to NULL (0)
        iterPtr.set(ValueLayout.ADDRESS, 0, MemorySegment.NULL);

        while (true) {
            final MemorySegment pktPtr = VpxFFI.vpx_codec_get_cx_data(codecCtx, iterPtr);
            if (pktPtr.address() == 0L) {
                break;
            }

            // Check if packet is a frame packet
            final int kind = vpx_codec_cx_pkt.kind(pktPtr);
            if (kind == VpxFFI.VPX_CODEC_CX_FRAME_PKT()) {
                // Get the frame struct inside the union
                final MemorySegment dataLayout = vpx_codec_cx_pkt.data(pktPtr);

                final MemorySegment bufAddress = vpx_codec_cx_pkt.data.frame.buf(dataLayout);
                final long bufSize = vpx_codec_cx_pkt.data.frame.sz(dataLayout);
                final long pktFlags = vpx_codec_cx_pkt.data.frame.flags(dataLayout);

                if (bufAddress.address() != 0L && bufSize > 0) {
                    final MemorySegment dataSegment = bufAddress.reinterpret(bufSize);
                    packets.add(new VpxPacket(dataSegment, pktFlags));
                }
            }
        }

        return packets;
    }

    /**
     * Destroys the native encoder context and releases all associated native memory. Safe to call
     * more than once; subsequent calls are no-ops.
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        VpxFFI.vpx_codec_destroy(codecCtx);
        arena.close();
    }

    private static void checkError(final MemorySegment ctx, final int res, final String message) {
        if (res != VpxFFI.VPX_CODEC_OK()) {
            final String detail;
            if (ctx.address() != 0L) {
                final MemorySegment errDetailPtr = VpxFFI.vpx_codec_error_detail(ctx);
                detail =
                        (errDetailPtr.address() != 0L)
                                ? errDetailPtr.getString(0)
                                : "No detail available";
            } else {
                detail = "No detail available";
            }
            throw new VpxException(res, message + ": " + detail);
        }
    }
}
