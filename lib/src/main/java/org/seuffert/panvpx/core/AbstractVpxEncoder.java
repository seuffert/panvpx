package org.seuffert.panvpx.core;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.seuffert.panvpx.ffi.VpxFFI;
import org.seuffert.panvpx.ffi.vpx_codec_ctx;
import org.seuffert.panvpx.ffi.vpx_codec_cx_pkt;
import org.seuffert.panvpx.ffi.vpx_codec_enc_cfg;
import org.seuffert.panvpx.ffi.vpx_rational;

/**
 * Abstract base class for VP8 and VP9 encoders using libvpx via Project Panama FFM API.
 *
 * <p>Concrete subclasses ({@link org.seuffert.panvpx.vp8.Vp8Encoder}, {@link
 * org.seuffert.panvpx.vp9.Vp9Encoder}) supply the codec-specific interface pointer via their
 * constructor. All lifecycle management, configuration application, packet extraction, and error
 * handling logic is centralized here.
 *
 * <h2>Encoding lifecycle</h2>
 *
 * <ol>
 *   <li>Create a concrete encoder subclass with a {@link VpxEncoderConfig}.
 *   <li>For each raw frame, wrap the I420 pixel data in a {@link VpxImage} and call {@link
 *       #encode}. Process the returned {@link VpxPacket} instances <em>before</em> the next {@code
 *       encode} call — they point into codec-internal memory that is invalidated on the next call.
 *   <li>At end-of-stream, call {@link #flush} in a loop until the list is empty to drain any frames
 *       held in the encoder's lookahead buffer.
 *   <li>Call {@link #close} (or use try-with-resources) to release all native memory.
 * </ol>
 *
 * <pre>{@code
 * try (Vp8Encoder encoder = new Vp8Encoder(VpxEncoderConfig.builder(640, 480).targetBitrateKbps(512).threads(2).build())) {
 *     long pts = 0;
 *     for (byte[] rawFrame : frameSource) {
 *         try (VpxImage image = VpxImage.fromByteArray(rawFrame, 640, 480)) {
 *             encoder.encode(image, pts++, 1L, 0L)
 *                    .forEach(p -> send(p.toByteArray()));
 *         }
 *     }
 *     // Drain the lookahead buffer
 *     List<VpxPacket> batch;
 *     do {
 *         batch = encoder.flush();
 *         batch.forEach(p -> send(p.toByteArray()));
 *     } while (!batch.isEmpty());
 * }
 * }</pre>
 *
 * <p><strong>Thread-safety:</strong> The underlying libvpx codec state is not concurrently
 * thread-safe. External serialization is required if the same instance is shared across threads.
 * The {@link Arena#ofShared()} backing allows the instance to be created, used, and closed on
 * different threads, but not simultaneously.
 */
public abstract class AbstractVpxEncoder implements AutoCloseable {

    /**
     * Encoding flag that forces the next frame to be encoded as a key frame (IDR / intra-only).
     * Pass this value as the {@code flags} argument to {@link #encode} when a seek point is
     * required (e.g., at the start of a new segment or after a stream reset).
     */
    public static final long VPX_EFLAG_FORCE_KF = VpxFFI.VPX_EFLAG_FORCE_KF();

    private final Arena arena;
    private final MemorySegment codecCtx;
    private final MemorySegment iterPtr;
    private final long encodingDeadline;
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * Initializes the encoder with the provided configuration and codec-specific interface pointer.
     *
     * <p>If initialization fails (e.g. invalid configuration), all native resources are released
     * before the exception propagates — no native memory is leaked.
     *
     * @param config The encoder configuration.
     * @param iface The codec interface pointer (e.g. {@code VpxFFI.vpx_codec_vp8_cx()}).
     */
    protected AbstractVpxEncoder(final VpxEncoderConfig config, final MemorySegment iface) {
        arena = Arena.ofShared();
        MemorySegment tempCodecCtx = MemorySegment.NULL;
        MemorySegment tempIterPtr = MemorySegment.NULL;
        boolean success = false;
        try {
            tempCodecCtx = vpx_codec_ctx.allocate(arena);

            // 1. Populate default configuration
            final MemorySegment encCfg = vpx_codec_enc_cfg.allocate(arena);
            int res = VpxFFI.vpx_codec_enc_config_default(iface, encCfg, 0);
            checkError(MemorySegment.NULL, res, "Failed to get default encoder configuration");

            // 2. Apply custom configuration
            vpx_codec_enc_cfg.g_w(encCfg, config.width());
            vpx_codec_enc_cfg.g_h(encCfg, config.height());
            vpx_codec_enc_cfg.g_usage(encCfg, config.usage());
            vpx_codec_enc_cfg.g_profile(encCfg, config.profile());
            vpx_codec_enc_cfg.g_error_resilient(encCfg, config.errorResilient() ? 1 : 0);
            vpx_codec_enc_cfg.g_lag_in_frames(encCfg, config.lagInFrames());
            vpx_codec_enc_cfg.g_threads(encCfg, config.threads());
            vpx_codec_enc_cfg.rc_target_bitrate(encCfg, config.targetBitrateKbps());
            vpx_codec_enc_cfg.rc_dropframe_thresh(encCfg, config.frameDropThreshold());
            vpx_codec_enc_cfg.rc_end_usage(encCfg, toNativeRcMode(config.rateControlMode()));
            vpx_codec_enc_cfg.rc_min_quantizer(encCfg, config.minQuantizer());
            vpx_codec_enc_cfg.rc_max_quantizer(encCfg, config.maxQuantizer());
            vpx_codec_enc_cfg.kf_mode(encCfg, toNativeKfMode(config.keyframeMode()));
            if (config.maxKeyframeDistance() > 0) {
                vpx_codec_enc_cfg.kf_max_dist(encCfg, config.maxKeyframeDistance());
            }
            vpx_codec_enc_cfg.kf_min_dist(encCfg, config.minKeyframeDistance());
            vpx_codec_enc_cfg.g_bit_depth(encCfg, toNativeBitDepth(config.bitDepth()));
            vpx_codec_enc_cfg.g_input_bit_depth(encCfg, config.inputBitDepth());
            vpx_codec_enc_cfg.rc_resize_allowed(encCfg, config.resizeAllowed() ? 1 : 0);

            final MemorySegment timebase = vpx_codec_enc_cfg.g_timebase(encCfg);
            vpx_rational.num(timebase, config.timebaseNumerator());
            vpx_rational.den(timebase, config.timebaseDenominator());

            // 3. Initialize the encoder
            res =
                    VpxFFI.vpx_codec_enc_init_ver(
                            tempCodecCtx, iface, encCfg, 0, VpxFFI.VPX_ENCODER_ABI_VERSION());
            checkError(tempCodecCtx, res, "Failed to initialize encoder");

            // 4. Apply cpu_used control (VP8E_SET_CPUUSED is shared between VP8 and VP9)
            res =
                    VpxFFI.vpx_codec_control_
                            .makeInvoker(ValueLayout.JAVA_INT)
                            .apply(tempCodecCtx, VpxFFI.VP8E_SET_CPUUSED(), config.cpuUsed());
            checkError(tempCodecCtx, res, "Failed to set cpu_used");

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
        encodingDeadline = config.deadline();
    }

    /**
     * Returns the name of the codec used by this encoder (e.g. {@code "VP8"} or {@code "VP9"}).
     *
     * @return the codec name string.
     */
    public abstract String getCodecName();

    /**
     * Encodes a single raw video frame and returns any compressed packets produced.
     *
     * <p>Timestamps and durations are expressed in timebase units as configured in {@link
     * VpxEncoderConfig}: with the default 1/1000 timebase, one unit equals one millisecond.
     * Timestamps must be monotonically increasing and non-overlapping across successive calls.
     *
     * <p>The returned list may be <em>empty</em> for a given input frame. VP9 in particular buffers
     * frames in a lookahead window before emitting packets. Call {@link #flush} at end-of-stream to
     * drain those delayed packets.
     *
     * <p><strong>Memory contract:</strong> The returned {@link VpxPacket} instances point into
     * libvpx-internal memory that is invalidated by the next call to {@code encode()} or {@link
     * #flush()}, or when the encoder is closed. Call {@link VpxPacket#toByteArray() toByteArray()}
     * immediately if the data must survive beyond the current call site.
     *
     * @param image The {@link VpxImage} containing the uncompressed I420 frame to encode.
     * @param pts The presentation timestamp of the frame, in timebase units.
     * @param duration The display duration of the frame, in timebase units. Typically {@code 1L}
     *     when the timebase denominator equals the target frame rate.
     * @param flags Encoding flags. Pass {@code 0L} for normal encoding, or {@link
     *     #VPX_EFLAG_FORCE_KF} to force a key frame at this position.
     * @return A (possibly empty) list of encoded packets, valid until the next {@code encode()} or
     *     {@link #flush()} call.
     * @throws VpxException if the underlying {@code vpx_codec_encode} call fails.
     */
    public List<VpxPacket> encode(
            final VpxImage image, final long pts, final long duration, final long flags) {
        final int res =
                VpxFFI.vpx_codec_encode(
                        codecCtx, image.nativeImage(), pts, duration, flags, encodingDeadline);
        checkError(codecCtx, res, "Failed to encode frame");
        return extractPackets();
    }

    /**
     * Flushes one batch of delayed packets from the encoder's lookahead buffer. Uses the same
     * deadline that was supplied at construction time, so the flush respects the configured
     * quality/speed trade-off (e.g. realtime vs. good-quality).
     *
     * <p><strong>Important — call in a loop for VP9:</strong> VP9 encoders configured with a
     * positive {@code g_lag_in_frames} require one {@code flush()} call per buffered frame to fully
     * drain the lookahead. Always call in a loop until the returned list is empty:
     *
     * <pre>{@code
     * List<VpxPacket> batch;
     * do {
     *     batch = encoder.flush();
     *     batch.forEach(pkt -> consume(pkt));
     * } while (!batch.isEmpty());
     * }</pre>
     *
     * <p>Callers must consume the returned packets before the next {@link #encode} or {@link
     * #flush} call — see {@link VpxPacket} for the memory-lifetime contract.
     *
     * @return A list of delayed encoded packets (may be empty when the buffer is fully drained).
     */
    public List<VpxPacket> flush() {
        final int res =
                VpxFFI.vpx_codec_encode(codecCtx, MemorySegment.NULL, 0, 0, 0, encodingDeadline);
        checkError(codecCtx, res, "Failed to flush encoder");
        return extractPackets();
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

    /**
     * Applies an integer codec control to the native encoder context. Call this from subclass
     * constructors (after {@code super()}) to configure codec-specific controls such as row-based
     * multithreading or tile columns.
     *
     * @param ctrlId The control identifier (e.g. {@link VpxFFI#VP9E_SET_ROW_MT()}).
     * @param value The integer value to set.
     */
    protected final void codecControl(final int ctrlId, final int value) {
        final int res =
                VpxFFI.vpx_codec_control_
                        .makeInvoker(ValueLayout.JAVA_INT)
                        .apply(codecCtx, ctrlId, value);
        checkError(codecCtx, res, "Failed to apply codec control " + ctrlId);
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
                final long pktPts = vpx_codec_cx_pkt.data.frame.pts(dataLayout);
                final long pktDuration = vpx_codec_cx_pkt.data.frame.duration(dataLayout);

                if (bufAddress.address() != 0L && bufSize > 0) {
                    final MemorySegment dataSegment = bufAddress.reinterpret(bufSize);
                    packets.add(new VpxPacket(dataSegment, pktFlags, pktPts, pktDuration));
                }
            }
        }

        return packets;
    }

    private static int toNativeRcMode(final VpxEncoderConfig.RateControlMode mode) {
        return switch (mode) {
            case VBR -> VpxFFI.VPX_VBR();
            case CBR -> VpxFFI.VPX_CBR();
            case CQ -> VpxFFI.VPX_CQ();
            case Q -> VpxFFI.VPX_Q();
        };
    }

    private static int toNativeKfMode(final VpxEncoderConfig.KeyframeMode mode) {
        return switch (mode) {
            case AUTO -> VpxFFI.VPX_KF_AUTO();
            case DISABLED -> VpxFFI.VPX_KF_DISABLED();
        };
    }

    private static int toNativeBitDepth(final VpxEncoderConfig.BitDepth depth) {
        return switch (depth) {
            case BITS_8 -> VpxFFI.VPX_BITS_8();
            case BITS_10 -> VpxFFI.VPX_BITS_10();
            case BITS_12 -> VpxFFI.VPX_BITS_12();
        };
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
