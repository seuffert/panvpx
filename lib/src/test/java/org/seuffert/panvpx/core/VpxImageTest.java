package org.seuffert.panvpx.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
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
}
