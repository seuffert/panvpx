package org.seuffert.panvpx.core;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;

/** Represents a packet of encoded data output by the VPX encoder. */
public class VpxPacket {

    /** Flag indicating that this packet contains a keyframe (start of a GOP). */
    public static final long VPX_FRAME_IS_KEY = 0x1L;

    /**
     * Flag indicating that this frame can be dropped without affecting the stream (no future frame
     * depends on this one).
     */
    public static final long VPX_FRAME_IS_DROPPABLE = 0x2L;

    /** Flag indicating that this frame should be decoded but will not be shown. */
    public static final long VPX_FRAME_IS_INVISIBLE = 0x4L;

    /** Flag indicating that this packet is a fragment of the encoded frame. */
    public static final long VPX_FRAME_IS_FRAGMENT = 0x8L;

    private final MemorySegment dataSegment;
    private final long flags;

    /**
     * Constructs a VpxPacket wrapping native packet memory. This constructor is intended for
     * library-internal use; obtain instances from {@link org.seuffert.panvpx.vp8.Vp8Encoder#encode}
     * or {@link org.seuffert.panvpx.vp8.Vp8Encoder#flush}.
     *
     * <p><strong>Memory contract:</strong> the caller is responsible for ensuring {@code
     * dataSegment} remains valid for as long as this packet is accessed via {@link
     * #asDirectBuffer()}. Use {@link #toByteArray()} for a safe copy that is independent of the
     * segment lifetime.
     *
     * @param dataSegment The memory segment containing the encoded data.
     * @param flags The packet flags (e.g., keyframe).
     */
    public VpxPacket(final MemorySegment dataSegment, final long flags) {
        this.dataSegment = dataSegment;
        this.flags = flags;
    }

    /**
     * Gets the packet flags.
     *
     * @return The flags bitmask.
     */
    public long flags() {
        return flags;
    }

    /**
     * Helper to check if this packet is a keyframe (start of a GOP).
     *
     * @return true if it is a keyframe.
     */
    public boolean isKeyFrame() {
        return (flags & VPX_FRAME_IS_KEY) != 0;
    }

    /**
     * Helper to check if this frame can be dropped without affecting the stream.
     *
     * <p>A droppable frame has no future frames that depend on it, so it may be discarded by the
     * application without corrupting the bitstream.
     *
     * @return true if this frame is droppable.
     */
    public boolean isDroppable() {
        return (flags & VPX_FRAME_IS_DROPPABLE) != 0;
    }

    /**
     * Helper to check if this frame is invisible (decoded but not displayed).
     *
     * @return true if this frame will not be shown.
     */
    public boolean isInvisible() {
        return (flags & VPX_FRAME_IS_INVISIBLE) != 0;
    }

    /**
     * Helper to check if this packet is a fragment of a larger encoded frame.
     *
     * <p>When this flag is set, the packet is one piece of a multi-packet frame. The final fragment
     * will have this flag cleared.
     *
     * @return true if this packet is a fragment.
     */
    public boolean isFragment() {
        return (flags & VPX_FRAME_IS_FRAGMENT) != 0;
    }

    /**
     * Gets the size of the packet data in bytes.
     *
     * @return The size of the packet in bytes.
     */
    public long size() {
        return dataSegment.byteSize();
    }

    /**
     * Zero-copy path: returns a <strong>direct</strong> {@link ByteBuffer} view of the underlying
     * native memory.
     *
     * <p><strong>Lifetime warning:</strong> when a packet is produced by {@link
     * org.seuffert.panvpx.vp8.Vp8Encoder}, the underlying buffer points into libvpx-internal memory
     * that is invalidated by the next call to {@code encode()} or {@code flush()}, or when the
     * encoder is closed. The FFM runtime provides <em>no</em> use-after-free guard for this memory
     * — accessing the returned buffer after any of those events is silent undefined behaviour.
     *
     * <p>Use this method only when you will fully consume the buffer <em>before</em> the next
     * encoder call. For any other use-case, call {@link #toByteArray()} which copies the data into
     * a safe, GC-managed byte array.
     *
     * @return A direct ByteBuffer viewing the packet data. Valid only until the next encoder call.
     */
    public ByteBuffer asDirectBuffer() {
        return dataSegment.asByteBuffer();
    }

    /**
     * Simple path: Copies the native memory data into a new byte array.
     *
     * @return A new byte array containing a copy of the packet data.
     */
    public byte[] toByteArray() {
        return dataSegment.toArray(ValueLayout.JAVA_BYTE);
    }
}
