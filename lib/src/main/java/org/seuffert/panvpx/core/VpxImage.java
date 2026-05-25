package org.seuffert.panvpx.core;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import org.seuffert.panvpx.ffi.VpxFFI;
import org.seuffert.panvpx.ffi.vpx_image;

/**
 * A wrapper for the native `vpx_image_t` structure. Manages the memory for raw uncompressed image
 * frames.
 */
public final class VpxImage implements AutoCloseable {

    /** Standard VPX Image Format for I420 planar data. */
    public static final int VPX_IMG_FMT_I420 = 0x102; // VPX_IMG_FMT_PLANAR | 2

    private final MemorySegment nativeImage;
    private final Arena dataArena;
    private final int width;
    private final int height;
    private final int format;
    private final boolean isCodecOwned;
    private final AtomicBoolean closed = new AtomicBoolean();

    private VpxImage(
            final MemorySegment nativeImage,
            final Arena dataArena,
            final int width,
            final int height,
            final int format,
            final boolean isCodecOwned) {
        this.nativeImage = nativeImage;
        this.dataArena = dataArena;
        this.width = width;
        this.height = height;
        this.format = format;
        this.isCodecOwned = isCodecOwned;
    }

    /**
     * Gets the underlying native pointer to the {@code vpx_image_t} structure. This is intended for
     * internal FFI use within the library.
     *
     * @return The MemorySegment pointer to the native image struct.
     */
    public MemorySegment nativeImage() {
        return nativeImage;
    }

    /**
     * Gets the width of the image frame.
     *
     * @return The width in pixels.
     */
    public int width() {
        return width;
    }

    /**
     * Gets the height of the image frame.
     *
     * @return The height in pixels.
     */
    public int height() {
        return height;
    }

    /**
     * Gets the raw pixel format of the image.
     *
     * @return The format integer flag.
     */
    public int format() {
        return format;
    }

    /**
     * Simple path: Creates a VpxImage from a Java heap byte array. The data is copied to off-heap
     * native memory.
     *
     * @param data The raw image data in I420 format (Y, U, V planes)
     * @param width The width of the image
     * @param height The height of the image
     * @return A VpxImage that MUST be closed when no longer needed.
     */
    public static VpxImage fromByteArray(final byte[] data, final int width, final int height) {
        final Arena arena = Arena.ofShared();
        boolean success = false;
        try {
            final MemorySegment dataSegment = arena.allocate(data.length);
            MemorySegment.copy(data, 0, dataSegment, ValueLayout.JAVA_BYTE, 0, data.length);
            final VpxImage img = create(dataSegment, arena, width, height, VPX_IMG_FMT_I420);
            success = true;
            return img;
        } finally {
            if (!success) {
                arena.close();
            }
        }
    }

    /**
     * Internal factory for images returned by a decoder. The codec retains ownership of the native
     * memory, so this wrapper will not free it.
     *
     * @param nativeImage The native image pointer returned by the codec.
     * @param width The width of the image.
     * @param height The height of the image.
     * @param format The format of the image.
     * @return A VpxImage that wraps the codec-owned memory.
     */
    @SuppressWarnings("NullAway")
    public static VpxImage createCodecOwned(
            final MemorySegment nativeImage, final int width, final int height, final int format) {
        return new VpxImage(nativeImage, null, width, height, format, true);
    }

    /**
     * "Easy direct memory" path: Creates a VpxImage from an existing MemorySegment. The memory is
     * aliased without copying. This {@code VpxImage} does NOT own or close the passed segment — the
     * caller retains ownership and must keep the segment alive for the lifetime of this image.
     *
     * <p>The returned image may be created on one thread and closed on a different thread, because
     * it uses a shared arena internally.
     *
     * @param segment The memory segment containing the I420 image data.
     * @param width The width of the image
     * @param height The height of the image
     * @return A VpxImage that MUST be closed when no longer needed.
     */
    public static VpxImage fromMemorySegment(
            final MemorySegment segment, final int width, final int height) {
        final Arena structArena = Arena.ofShared();
        boolean success = false;
        try {
            final MemorySegment imageStruct = vpx_image.allocate(structArena);
            final MemorySegment result =
                    VpxFFI.vpx_img_wrap(imageStruct, VPX_IMG_FMT_I420, width, height, 1, segment);
            if (result.address() == 0L) {
                throw new VpxException(-1, "Failed to wrap image memory in vpx_img_wrap");
            }
            final VpxImage img =
                    new VpxImage(imageStruct, structArena, width, height, VPX_IMG_FMT_I420, false);
            success = true;
            return img;
        } finally {
            if (!success) {
                structArena.close();
            }
        }
    }

    private static VpxImage create(
            final MemorySegment dataSegment,
            final Arena arena,
            final int width,
            final int height,
            final int format) {
        // Allocate the vpx_image_t struct itself.
        // We have an arena (meaning we own the data segment), use it.
        final MemorySegment imageStruct = vpx_image.allocate(arena);

        // Call vpx_img_wrap to initialize the struct with our data
        final MemorySegment result =
                VpxFFI.vpx_img_wrap(imageStruct, format, width, height, 1, dataSegment);

        if (result.address() == 0L) {
            throw new VpxException(-1, "Failed to wrap image memory in vpx_img_wrap");
        }

        // We own the data arena, we must keep the struct arena alive until close.
        return new VpxImage(imageStruct, arena, width, height, format, false);
    }

    /**
     * Releases the native image and its associated memory. Safe to call more than once; subsequent
     * calls are no-ops.
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        if (!isCodecOwned) {
            // Freeing the native image structure using vpx_img_free.
            VpxFFI.vpx_img_free(nativeImage);
        }

        // Close the arena managing the data and/or the struct if we own it
        if (dataArena != null) {
            dataArena.close();
        }
    }

    /**
     * Gets the stride (in bytes) of the specified plane.
     *
     * @param planeIndex The index of the plane (0 for Y, 1 for U, 2 for V).
     * @return The stride.
     */
    public int getStride(final int planeIndex) {
        if (planeIndex < 0 || planeIndex > 2) {
            throw new IllegalArgumentException("Invalid plane index: " + planeIndex);
        }
        return vpx_image.stride(nativeImage, planeIndex);
    }

    /**
     * Zero-copy path: returns a <strong>direct</strong> {@link ByteBuffer} view of the specified
     * plane. For I420 format:
     *
     * <ul>
     *   <li>Plane 0 (Y): contains {@code height} rows of {@code getStride(0)} bytes.
     *   <li>Plane 1 (U): contains {@code (height + 1) / 2} rows of {@code getStride(1)} bytes.
     *   <li>Plane 2 (V): contains {@code (height + 1) / 2} rows of {@code getStride(2)} bytes.
     * </ul>
     *
     * <p><strong>Lifetime warning:</strong> For images returned by a decoder, the underlying memory
     * is invalidated by the next call to {@code decode()} or when the decoder is closed. The FFM
     * runtime provides <em>no</em> use-after-free guard — accessing the returned buffer after any
     * of those events is silent undefined behaviour. Use {@link #toByteArray()} for a safe copy.
     *
     * @param planeIndex The index of the plane (0 for Y, 1 for U, 2 for V).
     * @return A direct ByteBuffer viewing the plane data.
     */
    public ByteBuffer getPlane(final int planeIndex) {
        if (planeIndex < 0 || planeIndex > 2) {
            throw new IllegalArgumentException("Invalid plane index: " + planeIndex);
        }
        final int stride = getStride(planeIndex);
        final int h = (planeIndex == 0) ? height : (height + 1) / 2;
        final long size = (long) stride * h;

        final MemorySegment planePtr = vpx_image.planes(nativeImage, planeIndex);
        if (planePtr.address() == 0L) {
            return ByteBuffer.allocateDirect(0);
        }
        return planePtr.reinterpret(size).asByteBuffer();
    }

    /**
     * Simple path: Copies the native image planes into a new contiguous Java byte array in I420
     * format. This safely extracts the valid pixels even if the native image uses strided memory
     * (where row length exceeds image width).
     *
     * @return A new byte array containing the tightly packed I420 image data.
     */
    public byte[] toByteArray() {
        if (format != VPX_IMG_FMT_I420) {
            throw new VpxException(-1, "Only VPX_IMG_FMT_I420 is supported for toByteArray()");
        }

        final int yWidth = width;
        final int yHeight = height;
        final int uvWidth = (width + 1) / 2;
        final int uvHeight = (height + 1) / 2;

        final int ySize = yWidth * yHeight;
        final int uvSize = uvWidth * uvHeight;
        final byte[] data = new byte[ySize + uvSize * 2];
        final MemorySegment dataSegment = MemorySegment.ofArray(data);

        long offset = 0;

        // Plane 0 (Y)
        final int yStride = getStride(0);
        final MemorySegment yPlane = vpx_image.planes(nativeImage, 0);
        for (int r = 0; r < yHeight; r++) {
            MemorySegment.copy(
                    yPlane.reinterpret((long) yStride * yHeight),
                    (long) r * yStride,
                    dataSegment,
                    offset,
                    yWidth);
            offset += yWidth;
        }

        // Plane 1 (U)
        final int uStride = getStride(1);
        final MemorySegment uPlane = vpx_image.planes(nativeImage, 1);
        for (int r = 0; r < uvHeight; r++) {
            MemorySegment.copy(
                    uPlane.reinterpret((long) uStride * uvHeight),
                    (long) r * uStride,
                    dataSegment,
                    offset,
                    uvWidth);
            offset += uvWidth;
        }

        // Plane 2 (V)
        final int vStride = getStride(2);
        final MemorySegment vPlane = vpx_image.planes(nativeImage, 2);
        for (int r = 0; r < uvHeight; r++) {
            MemorySegment.copy(
                    vPlane.reinterpret((long) vStride * uvHeight),
                    (long) r * vStride,
                    dataSegment,
                    offset,
                    uvWidth);
            offset += uvWidth;
        }

        return data;
    }
}
