package org.seuffert.panvpx.core;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;

/** Represents a packet of encoded data output by the VPX encoder. */
public class VpxPacket {

    /** Flag indicating that this packet contains a keyframe. */
    public static final long VPX_FRAME_IS_KEY = 1L;

    private final MemorySegment dataSegment;
    private final long flags;

    /**
     * Constructs a VpxPacket wrapping the native packet memory.
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
     * Helper to check if this packet is a keyframe.
     *
     * @return true if it is a keyframe.
     */
    public boolean isKeyFrame() {
        return (flags & VPX_FRAME_IS_KEY) != 0;
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
     * "Easy direct memory" path: Returns a view of the underlying native memory as a direct
     * ByteBuffer. No data is copied. The buffer is only valid as long as the parent encoder context
     * has not extracted the next packet or been closed.
     *
     * @return A direct ByteBuffer viewing the packet data.
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
