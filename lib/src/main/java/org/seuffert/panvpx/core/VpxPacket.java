package org.seuffert.panvpx.core;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;

/**
 * Represents a packet of encoded data output by the VPX encoder.
 */
public class VpxPacket {
    private final MemorySegment dataSegment;

    public VpxPacket(MemorySegment dataSegment) {
        this.dataSegment = dataSegment;
    }

    /**
     * Gets the size of the packet data in bytes.
     */
    public long getSize() {
        return dataSegment.byteSize();
    }

    /**
     * "Easy direct memory" path: Returns a view of the underlying native memory as a direct ByteBuffer.
     * No data is copied. The buffer is only valid as long as the parent encoder context has not extracted
     * the next packet or been closed.
     */
    public ByteBuffer asDirectBuffer() {
        return dataSegment.asByteBuffer();
    }

    /**
     * Simple path: Copies the native memory data into a new byte array.
     */
    public byte[] toByteArray() {
        return dataSegment.toArray(ValueLayout.JAVA_BYTE);
    }
}
