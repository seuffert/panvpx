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
import org.seuffert.panvpx.ffi.vpx_codec_dec_cfg;
import org.seuffert.panvpx.ffi.vpx_image;

/** VP8 Video Decoder using libvpx via Project Panama FFM API. */
public class Vp8Decoder implements AutoCloseable {

    private final Arena arena;
    private final MemorySegment codecCtx;
    private final MemorySegment iterPtr;
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * Initializes the VP8 Decoder with the provided configuration.
     *
     * @param config The decoder configuration.
     */
    public Vp8Decoder(final VpxDecoderConfig config) {
        arena = Arena.ofShared();
        MemorySegment tempCodecCtx = MemorySegment.NULL;
        MemorySegment tempIterPtr = MemorySegment.NULL;
        boolean success = false;
        try {
            tempCodecCtx = vpx_codec_ctx.allocate(arena);

            // 1. Get the VP8 decoder interface
            final MemorySegment iface = VpxFFI.vpx_codec_vp8_dx();

            // 2. Apply configuration
            final MemorySegment decCfg = vpx_codec_dec_cfg.allocate(arena);
            vpx_codec_dec_cfg.threads(decCfg, config.threads());
            vpx_codec_dec_cfg.w(decCfg, config.width());
            vpx_codec_dec_cfg.h(decCfg, config.height());

            // 3. Initialize the decoder
            final int res =
                    VpxFFI.vpx_codec_dec_init_ver(
                            tempCodecCtx, iface, decCfg, 0, VpxFFI.VPX_DECODER_ABI_VERSION());
            checkError(tempCodecCtx, res, "Failed to initialize VP8 decoder");

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
     * Decodes a packet of encoded video.
     *
     * @param packet The encoded packet.
     * @return A list of decoded images.
     */
    public List<VpxImage> decode(final VpxPacket packet) {
        return decode(MemorySegment.ofBuffer(packet.asDirectBuffer()));
    }

    /**
     * Decodes encoded video data from a heap byte array.
     *
     * @param data The byte array containing the encoded frame.
     * @return A list of decoded images.
     */
    public List<VpxImage> decode(final byte[] data) {
        try (Arena tempArena = Arena.ofConfined()) {
            final MemorySegment dataSegment = tempArena.allocate(data.length);
            MemorySegment.copy(data, 0, dataSegment, ValueLayout.JAVA_BYTE, 0, data.length);
            return decode(dataSegment);
        }
    }

    /**
     * Decodes encoded video data from a native MemorySegment.
     *
     * @param dataSegment The memory segment containing the encoded frame.
     * @return A list of decoded images.
     */
    public List<VpxImage> decode(final MemorySegment dataSegment) {
        final int res =
                VpxFFI.vpx_codec_decode(
                        codecCtx, dataSegment, (int) dataSegment.byteSize(), MemorySegment.NULL, 0);
        checkError(codecCtx, res, "Failed to decode frame");

        return extractFrames();
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

    /** Destroys the native decoder context and releases all associated native memory. */
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
