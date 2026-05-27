package org.seuffert.panvpx.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class VpxPacketTest {

    @Test
    void testDirectBufferVsByteArray() {
        try (Arena arena = Arena.ofConfined()) {
            final byte[] testData = {10, 20, 30, 40, 50};
            final MemorySegment segment = arena.allocateFrom(ValueLayout.JAVA_BYTE, testData);

            final VpxPacket packet = new VpxPacket(segment, VpxPacket.VPX_FRAME_IS_KEY, 42L, 1000L);
            assertEquals(5, packet.size(), "Size should be exactly 5 bytes");
            assertTrue(packet.isKeyFrame(), "Packet should be marked as keyframe");
            assertEquals(42L, packet.pts(), "PTS should be echoed back");
            assertEquals(1000L, packet.duration(), "Duration should be echoed back");

            final ByteBuffer directBuffer = packet.asDirectBuffer();
            assertTrue(directBuffer.isDirect(), "Buffer should be a direct ByteBuffer");
            assertEquals(5, directBuffer.capacity(), "Buffer capacity should match data size");

            final byte[] array = packet.toByteArray();
            assertEquals(5, array.length, "Byte array length should match data size");
            assertArrayEquals(testData, array, "Byte array content should match original data");
        }
    }
}
