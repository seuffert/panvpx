package org.seuffert.panvpx.core;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import org.seuffert.panvpx.ffi.VpxFFI;
import org.seuffert.panvpx.ffi.vpx_image;

/**
 * A wrapper for the native {@code vpx_image_t} structure. Manages the memory for raw uncompressed
 * image frames.
 *
 * <p>This class abstracts the complex native memory layout of video frames into a manageable Java
 * object. It primarily supports the I420 planar pixel format commonly used with VP8 and VP9 codecs.
 *
 * <p><strong>Resource management:</strong> Because instances of this class wrap native memory, they
 * MUST be closed when no longer needed using {@link #close()} or a try-with-resources block.
 *
 * <p><strong>Example usage:</strong>
 *
 * <pre>{@code
 * // Creating an image from a byte array for encoding
 * byte[] i420Data = new byte[width * height * 3 / 2]; // Y + U + V planes
 * try (VpxImage image = VpxImage.fromByteArray(i420Data, width, height)) {
 *     encoder.encode(image, pts, duration, flags);
 * }
 * }</pre>
 */
public final class VpxImage implements AutoCloseable {

    /**
     * Standard VPX image format identifier for I420 (YUV 4:2:0 planar) pixel data.
     *
     * <p>I420 is a planar format with three separate, non-interleaved planes:
     *
     * <ul>
     *   <li><strong>Plane 0 &mdash; Y (luma):</strong> full resolution, {@code width × height}
     *       bytes.
     *   <li><strong>Plane 1 &mdash; U / Cb (chroma blue):</strong> half resolution in both
     *       dimensions, {@code ⌈width/2⌉ × ⌈height/2⌉} bytes.
     *   <li><strong>Plane 2 &mdash; V / Cr (chroma red):</strong> same size as the U plane.
     * </ul>
     *
     * <p>Total tightly-packed frame size: {@code width × height × 3 / 2} bytes.
     */
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
     * Returns the raw pixel format of this image.
     *
     * <p>The only format currently produced and consumed by this library is {@link
     * #VPX_IMG_FMT_I420}. Decoder-produced images may theoretically carry a different format code
     * if the bitstream was encoded with a different pixel format, in which case {@link
     * #toByteArray()} will throw a {@link VpxException}.
     *
     * @return The VPX format constant (e.g. {@link #VPX_IMG_FMT_I420}).
     */
    public int format() {
        return format;
    }

    /**
     * Simple path: creates a {@link VpxImage} from a Java heap {@code byte[]} containing
     * tightly-packed I420 pixel data. The array is copied to off-heap native memory owned by the
     * returned image.
     *
     * <p>The expected byte layout for a {@code width × height} I420 frame is:
     *
     * <ul>
     *   <li>Bytes {@code 0} &hellip; {@code width*height - 1}: Y (luma) plane, row by row.
     *   <li>Bytes {@code width*height} &hellip; {@code width*height*5/4 - 1}: U (Cb) plane, at half
     *       resolution in both dimensions, row by row.
     *   <li>Bytes {@code width*height*5/4} &hellip; {@code width*height*3/2 - 1}: V (Cr) plane,
     *       same size as U, row by row.
     * </ul>
     *
     * <p>The minimum required array length is therefore {@code width * height * 3 / 2} bytes.
     *
     * <p>Use {@link #fromMemorySegment(MemorySegment, int, int)} instead when the data already
     * lives in off-heap native memory to avoid an unnecessary copy.
     *
     * @param data The raw, tightly-packed I420 image data.
     * @param width The frame width in pixels.
     * @param height The frame height in pixels.
     * @return A new {@link VpxImage} that MUST be closed when no longer needed.
     * @throws VpxException if the underlying {@code vpx_img_wrap} call fails.
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
     * Advanced path: creates a {@link VpxImage} from an existing off-heap {@link MemorySegment}
     * <em>without</em> copying the pixel data. Use this path when the frame data already resides in
     * native memory (e.g. from a camera driver or an upstream pipeline stage) to eliminate an
     * unnecessary copy.
     *
     * <p>The segment must contain a tightly-packed I420 frame in the layout described by {@link
     * #VPX_IMG_FMT_I420}: {@code width × height × 3 / 2} bytes in Y-U-V order.
     *
     * <p>This {@link VpxImage} does <em>not</em> own or close the passed segment &mdash; the caller
     * retains ownership and must keep the segment alive for the full lifetime of this image.
     *
     * <p>The returned image may be created on one thread and closed on a different thread because
     * it uses a shared arena internally.
     *
     * @param segment The off-heap memory segment containing the I420 image data.
     * @param width The frame width in pixels.
     * @param height The frame height in pixels.
     * @return A new {@link VpxImage} that MUST be closed when no longer needed.
     * @throws VpxException if the underlying {@code vpx_img_wrap} call fails.
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
     * Returns the stride (row pitch, in bytes) of the specified plane.
     *
     * <p>The stride is the number of bytes between the start of one row and the start of the next.
     * It may be <em>larger</em> than the plane's pixel width when the codec or the platform adds
     * alignment padding at the end of each row. Always use the stride, not the image width, when
     * iterating over rows manually:
     *
     * <pre>{@code
     * int stride = image.getStride(0); // Y-plane stride
     * ByteBuffer yPlane = image.getPlane(0);
     * for (int row = 0; row < image.height(); row++) {
     *     yPlane.position(row * stride);
     *     // read image.width() bytes of valid Y data for this row
     * }
     * }</pre>
     *
     * @param planeIndex The plane index: {@code 0} = Y, {@code 1} = U (Cb), {@code 2} = V (Cr).
     * @return The stride of the plane in bytes.
     * @throws IllegalArgumentException if {@code planeIndex} is not in the range [0,&nbsp;2].
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
     * Simple path: copies the native image planes into a new tightly-packed Java {@code byte[]} in
     * I420 format (Y plane, then U plane, then V plane), stripping any alignment padding bytes that
     * may be present in the native strided representation.
     *
     * <p>The returned array has exactly {@code width * height * 3 / 2} bytes and is completely
     * independent of the native buffers, making it safe to use after the next {@code decode()}
     * call.
     *
     * <p>For performance-sensitive paths, consider {@link #getPlane(int)} to access individual
     * planes directly without the copy overhead.
     *
     * @return A new {@code byte[]} of size {@code width * height * 3 / 2} containing tightly-packed
     *     I420 pixel data.
     * @throws VpxException if the image format is not {@link #VPX_IMG_FMT_I420}.
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

    /**
     * Zero-allocation path: copies the native image planes into a user-provided {@link ByteBuffer}
     * in I420 format (Y plane, then U plane, then V plane), stripping any alignment padding bytes
     * that may be present in the native strided representation.
     *
     * <p>This method works with both direct and heap buffers. It requires the destination buffer to
     * have at least {@code width * height * 3 / 2} bytes remaining. The buffer's position will be
     * advanced by the number of bytes written.
     *
     * @param destinationBuffer The buffer to copy the pixel data into.
     * @return The updated {@code destinationBuffer} for method chaining.
     * @throws VpxException if the image format is not {@link #VPX_IMG_FMT_I420}.
     * @throws IllegalArgumentException if the buffer does not have enough remaining capacity.
     */
    public ByteBuffer copyTo(final ByteBuffer destinationBuffer) {
        if (format != VPX_IMG_FMT_I420) {
            throw new VpxException(-1, "Only VPX_IMG_FMT_I420 is supported for copyTo()");
        }

        final int yWidth = width;
        final int yHeight = height;
        final int uvWidth = (width + 1) / 2;
        final int uvHeight = (height + 1) / 2;

        final int ySize = yWidth * yHeight;
        final int uvSize = uvWidth * uvHeight;
        final int requiredSize = ySize + uvSize * 2;

        if (destinationBuffer.remaining() < requiredSize) {
            throw new IllegalArgumentException(
                    "Destination buffer requires at least "
                            + requiredSize
                            + " bytes remaining, but has "
                            + destinationBuffer.remaining());
        }

        final MemorySegment dataSegment = MemorySegment.ofBuffer(destinationBuffer);
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

        destinationBuffer.position(destinationBuffer.position() + requiredSize);
        return destinationBuffer;
    }
}
