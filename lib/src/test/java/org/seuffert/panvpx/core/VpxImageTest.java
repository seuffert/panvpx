package org.seuffert.panvpx.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class VpxImageTest {

    @Test
    void testFromMemorySegmentLifecycle() {
        final int width = 320;
        final int height = 240;
        final int frameSize = width * height * 3 / 2;

        try (Arena arena = Arena.ofConfined()) {
            final MemorySegment segment = arena.allocate(frameSize);

            try (VpxImage image = VpxImage.fromMemorySegment(segment, width, height)) {
                assertNotNull(image.nativeImage(), "Native image pointer should not be null");
                assertEquals(width, image.width(), "Width should match");
                assertEquals(height, image.height(), "Height should match");
                assertEquals(VpxImage.VPX_IMG_FMT_I420, image.format(), "Format should be I420");
            } // VpxImage closes here. The struct arena should be closed.

            // The data segment's arena (our 'arena') should NOT be closed by VpxImage,
            // because fromMemorySegment doesn't take ownership of it.
            // We can prove it's still alive by accessing it (won't throw IllegalStateException).
            segment.set(ValueLayout.JAVA_BYTE, 0, (byte) 1);
        }
    }

    @Test
    void testFromByteArrayLifecycle() {
        final int width = 320;
        final int height = 240;
        final byte[] data = new byte[width * height * 3 / 2];

        final VpxImage image = VpxImage.fromByteArray(data, width, height);
        assertNotNull(image.nativeImage(), "Native image pointer should not be null");

        // We can access the native image struct because it is alive
        image.nativeImage().get(ValueLayout.JAVA_BYTE, 0);

        image.close();

        // After close, the dataArena is closed, so accessing the struct should throw
        // IllegalStateException
        assertThrows(
                IllegalStateException.class,
                () -> image.nativeImage().get(ValueLayout.JAVA_BYTE, 0),
                "Accessing memory after close should throw IllegalStateException");
    }

    /**
     * Verifies that a VpxImage created by fromMemorySegment can be closed from a thread different
     * from the one that created it. This requires Arena.ofShared() (not Arena.ofConfined()).
     */
    @Test
    void testFromMemorySegmentCanBeClosedFromDifferentThread() throws InterruptedException {
        final int width = 320;
        final int height = 240;
        final int frameSize = width * height * 3 / 2;

        try (Arena dataArena = Arena.ofShared()) {
            final MemorySegment segment = dataArena.allocate(frameSize);

            // Create image on the current thread
            final VpxImage image = VpxImage.fromMemorySegment(segment, width, height);

            final AtomicReference<Throwable> errorOnOtherThread = new AtomicReference<>();
            final Thread closer =
                    new Thread(
                            () -> {
                                try {
                                    // Close on a different thread — must not throw
                                    // WrongThreadException
                                    image.close();
                                } catch (final Throwable t) {
                                    errorOnOtherThread.set(t);
                                }
                            });
            closer.start();
            closer.join();

            if (errorOnOtherThread.get() != null) {
                fail("close() threw on different thread: " + errorOnOtherThread.get().getMessage());
            }
        }
    }

    @Test
    void testDoubleCloseIsIdempotent() {
        final int width = 320;
        final int height = 240;
        final byte[] data = new byte[width * height * 3 / 2];
        final VpxImage image = VpxImage.fromByteArray(data, width, height);

        // First close must succeed
        image.close();

        // Second close must be a silent no-op — no exception, no native crash
        assertDoesNotThrow(image::close, "Second close() call must be a no-op");
    }

    @Test
    void testCopyToByteBuffer() {
        final int width = 320;
        final int height = 240;
        final int frameSize = width * height * 3 / 2;

        // Fill with dummy data
        final byte[] originalData = new byte[frameSize];
        for (int i = 0; i < frameSize; i++) {
            originalData[i] = (byte) (i % 256);
        }

        try (VpxImage image = VpxImage.fromByteArray(originalData, width, height)) {
            final byte[] expectedArray = image.toByteArray();
            assertArrayEquals(
                    originalData, expectedArray, "toByteArray() should match original data");

            // Test Heap Buffer
            final ByteBuffer heapBuffer = ByteBuffer.allocate(frameSize + 10);
            heapBuffer.position(5); // Offset
            final ByteBuffer returnedHeap = image.copyTo(heapBuffer);

            assertEquals(heapBuffer, returnedHeap, "Should return the same instance");
            assertEquals(frameSize + 5, heapBuffer.position(), "Position should advance");
            assertEquals(5, heapBuffer.remaining(), "Remaining should be 5");

            heapBuffer.position(5);
            final byte[] heapResult = new byte[frameSize];
            heapBuffer.get(heapResult);
            assertArrayEquals(
                    expectedArray, heapResult, "Heap buffer copy should match toByteArray()");

            // Test Direct Buffer
            final ByteBuffer directBuffer = ByteBuffer.allocateDirect(frameSize + 10);
            directBuffer.position(5);
            image.copyTo(directBuffer);

            assertEquals(frameSize + 5, directBuffer.position(), "Position should advance");

            directBuffer.position(5);
            final byte[] directResult = new byte[frameSize];
            directBuffer.get(directResult);
            assertArrayEquals(
                    expectedArray, directResult, "Direct buffer copy should match toByteArray()");

            // Test Insufficient Capacity
            final ByteBuffer smallBuffer = ByteBuffer.allocate(frameSize - 1);
            final IllegalArgumentException ex =
                    assertThrows(IllegalArgumentException.class, () -> image.copyTo(smallBuffer));
            final String msg = ex.getMessage();
            assertNotNull(msg, "Exception message should not be null");
            assertTrue(msg.contains("requires at least"), "Should indicate required capacity");
        }
    }
}
