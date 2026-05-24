package org.seuffert.panvpx.core;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import org.seuffert.panvpx.ffi.VpxFFI;
import org.seuffert.panvpx.ffi.vpx_image;

/**
 * A wrapper for the native `vpx_image_t` structure. Manages the memory for raw uncompressed image
 * frames.
 */
public class VpxImage implements AutoCloseable {

    /** Standard VPX Image Format for I420 planar data. */
    public static final int VPX_IMG_FMT_I420 =
            0x102; // VPX_IMG_FMT_PLANAR | VPX_IMG_FMT_UV_FLIP | 2

    private final MemorySegment nativeImage;
    private final Arena dataArena;
    private final int width;
    private final int height;
    private final int format;

    private VpxImage(
            MemorySegment nativeImage, Arena dataArena, int width, int height, int format) {
        this.nativeImage = nativeImage;
        this.dataArena = dataArena;
        this.width = width;
        this.height = height;
        this.format = format;
    }

    /**
     * Gets the underlying native pointer to the {@code vpx_image_t} structure. This is intended for
     * internal FFI use within the library.
     *
     * @return The MemorySegment pointer to the native image struct.
     */
    public MemorySegment getNativeImage() {
        return nativeImage;
    }

    /**
     * Gets the width of the image frame.
     *
     * @return The width in pixels.
     */
    public int getWidth() {
        return width;
    }

    /**
     * Gets the height of the image frame.
     *
     * @return The height in pixels.
     */
    public int getHeight() {
        return height;
    }

    /**
     * Gets the raw pixel format of the image.
     *
     * @return The format integer flag.
     */
    public int getFormat() {
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
    public static VpxImage fromByteArray(byte[] data, int width, int height) {
        Arena arena = Arena.ofShared();
        try {
            MemorySegment dataSegment = arena.allocate(data.length);
            MemorySegment.copy(data, 0, dataSegment, ValueLayout.JAVA_BYTE, 0, data.length);
            return create(dataSegment, arena, width, height, VPX_IMG_FMT_I420);
        } catch (Exception e) {
            arena.close();
            throw e;
        }
    }

    /**
     * "Easy direct memory" path: Creates a VpxImage from an existing MemorySegment. The memory is
     * aliased without copying.
     *
     * @param segment The memory segment containing the I420 image data.
     * @param width The width of the image
     * @param height The height of the image
     * @return A VpxImage that does NOT manage the lifecycle of the passed segment.
     */
    public static VpxImage fromMemorySegment(MemorySegment segment, int width, int height) {
        Arena structArena = Arena.ofConfined();
        try {
            MemorySegment imageStruct = vpx_image.allocate(structArena);
            MemorySegment result =
                    VpxFFI.vpx_img_wrap(imageStruct, VPX_IMG_FMT_I420, width, height, 1, segment);
            if (result.address() == 0L) {
                throw new VpxException(-1, "Failed to wrap image memory in vpx_img_wrap");
            }
            return new VpxImage(imageStruct, structArena, width, height, VPX_IMG_FMT_I420);
        } catch (Exception e) {
            structArena.close();
            throw e;
        }
    }

    private static VpxImage create(
            MemorySegment dataSegment, Arena arena, int width, int height, int format) {
        // Allocate the vpx_image_t struct itself.
        // We have an arena (meaning we own the data segment), use it.
        try {
            MemorySegment imageStruct = vpx_image.allocate(arena);

            // Call vpx_img_wrap to initialize the struct with our data
            MemorySegment result =
                    VpxFFI.vpx_img_wrap(imageStruct, format, width, height, 1, dataSegment);

            if (result.address() == 0L) {
                throw new VpxException(-1, "Failed to wrap image memory in vpx_img_wrap");
            }

            // We own the data arena, we must keep the struct arena alive until close.
            return new VpxImage(imageStruct, arena, width, height, format);
        } catch (Exception e) {
            throw e;
        }
    }

    @Override
    public void close() {
        // Freeing the native image structure using vpx_img_free.
        VpxFFI.vpx_img_free(nativeImage);

        // Close the arena managing the data and/or the struct if we own it
        if (dataArena != null) {
            dataArena.close();
        }
    }
}
