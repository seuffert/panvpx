package org.seuffert.panvpx.core;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.seuffert.panvpx.ffi.VpxFFI;
import org.seuffert.panvpx.ffi.vpx_codec_ctx;
import org.seuffert.panvpx.ffi.vpx_codec_dec_cfg;
import org.seuffert.panvpx.ffi.vpx_image;

/**
 * Abstract base class for VP8 and VP9 decoders using libvpx via Project Panama FFM API.
 *
 * <p>Subclasses supply the codec-specific interface pointer via the constructor; all lifecycle
 * management, frame extraction, and error handling logic is provided here.
 *
 * <p><strong>Thread-safety:</strong> The underlying libvpx codec state is not concurrently
 * thread-safe. External serialization is required if the same instance is shared across threads.
 * The {@link Arena#ofShared()} backing allows the instance to be created, used, and closed on
 * different threads, but not simultaneously.
 */
public abstract class AbstractVpxDecoder implements AutoCloseable {

    private final Arena arena;
    private final MemorySegment codecCtx;
    private final MemorySegment iterPtr;
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * Initializes the decoder with the provided configuration and codec-specific interface pointer.
     *
     * <p>If initialization fails, all native resources are released before the exception propagates
     * — no native memory is leaked.
     *
     * @param config The decoder configuration.
     * @param iface The codec interface pointer (e.g. {@code VpxFFI.vpx_codec_vp8_dx()}).
     */
    protected AbstractVpxDecoder(final VpxDecoderConfig config, final MemorySegment iface) {
        arena = Arena.ofShared();
        MemorySegment tempCodecCtx = MemorySegment.NULL;
        MemorySegment tempIterPtr = MemorySegment.NULL;
        boolean success = false;
        try {
            tempCodecCtx = vpx_codec_ctx.allocate(arena);

            // Apply configuration
            final MemorySegment decCfg = vpx_codec_dec_cfg.allocate(arena);
            vpx_codec_dec_cfg.threads(decCfg, config.threads());
            vpx_codec_dec_cfg.w(decCfg, config.width());
            vpx_codec_dec_cfg.h(decCfg, config.height());

            // Initialize the decoder
            final int res =
                    VpxFFI.vpx_codec_dec_init_ver(
                            tempCodecCtx, iface, decCfg, 0, VpxFFI.VPX_DECODER_ABI_VERSION());
            checkError(tempCodecCtx, res, "Failed to initialize decoder");

            // Allocate iterator pointer for vpx_codec_get_frame
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
     * Returns the name of the codec used by this decoder (e.g. {@code "VP8"} or {@code "VP9"}).
     *
     * @return the codec name string.
     */
    public abstract String getCodecName();

    /**
     * Decodes a packet of encoded video.
     *
     * <p><strong>Lifetime warning:</strong> The returned {@link VpxImage} instances wrap
     * libvpx-internal buffers that are only valid until the next call to any {@code decode()}
     * overload on this instance, or until the decoder is closed. Use {@link VpxImage#toByteArray()}
     * or {@link VpxImage#getPlane(int)} and copy immediately if the data must outlive the next
     * decode call.
     *
     * @param packet The encoded packet.
     * @return A list of decoded images valid until the next {@code decode()} call.
     */
    public List<VpxImage> decode(final VpxPacket packet) {
        return decode(MemorySegment.ofBuffer(packet.asDirectBuffer()));
    }

    /**
     * Decodes encoded video data from a heap byte array. The input data is copied to native memory
     * before decoding.
     *
     * <p><strong>Lifetime warning:</strong> see {@link #decode(VpxPacket)} for the memory-lifetime
     * contract of the returned images.
     *
     * @param data The byte array containing the encoded frame.
     * @return A list of decoded images valid until the next {@code decode()} call.
     */
    public List<VpxImage> decode(final byte[] data) {
        try (Arena tempArena = Arena.ofConfined()) {
            final MemorySegment dataSegment = tempArena.allocate(data.length);
            MemorySegment.copy(data, 0, dataSegment, ValueLayout.JAVA_BYTE, 0, data.length);
            return decode(dataSegment);
        }
    }

    /**
     * Decodes encoded video data from a native MemorySegment (zero-copy input path).
     *
     * <p><strong>Lifetime warning:</strong> see {@link #decode(VpxPacket)} for the memory-lifetime
     * contract of the returned images.
     *
     * @param dataSegment The memory segment containing the encoded frame.
     * @return A list of decoded images valid until the next {@code decode()} call.
     */
    public List<VpxImage> decode(final MemorySegment dataSegment) {
        final int res =
                VpxFFI.vpx_codec_decode(
                        codecCtx, dataSegment, (int) dataSegment.byteSize(), MemorySegment.NULL, 0);
        checkError(codecCtx, res, "Failed to decode frame");
        return extractFrames();
    }

    /**
     * Destroys the native decoder context and releases all associated native memory. Safe to call
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

    private List<VpxImage> extractFrames() {
        final List<VpxImage> frames = new ArrayList<>();

        // Reset iterator pointer to NULL (0)
        iterPtr.set(ValueLayout.ADDRESS, 0, MemorySegment.NULL);

        while (true) {
            final MemorySegment imgPtr = VpxFFI.vpx_codec_get_frame(codecCtx, iterPtr);
            if (imgPtr.address() == 0L) {
                break;
            }

            final int width = vpx_image.d_w(imgPtr);
            final int height = vpx_image.d_h(imgPtr);
            final int format = vpx_image.fmt(imgPtr);

            frames.add(VpxImage.createCodecOwned(imgPtr, width, height, format));
        }

        return frames;
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
