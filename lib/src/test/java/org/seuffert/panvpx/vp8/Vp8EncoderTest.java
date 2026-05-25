package org.seuffert.panvpx.vp8;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.seuffert.panvpx.core.VpxException;
import org.seuffert.panvpx.core.VpxImage;
import org.seuffert.panvpx.core.VpxPacket;

class Vp8EncoderTest {

    @Test
    void testEncodingSyntheticFrame() {
        final int width = 640;
        final int height = 480;

        // Size for I420 format (Y plane + U/V planes which are 1/4 size each)
        final int frameSize = width * height * 3 / 2;
        final byte[] dummyData = new byte[frameSize];

        // Fill dummy data with a basic pattern
        for (int i = 0; i < dummyData.length; i++) {
            dummyData[i] = (byte) (i % 255);
        }

        final VpxEncoderConfig config = new VpxEncoderConfig(width, height, 1000, 2);

        int packetsReceived = 0;

        // Use try-with-resources to manage the lifecycle of native arenas properly
        try (Vp8Encoder encoder = new Vp8Encoder(config);
                VpxImage image = VpxImage.fromByteArray(dummyData, width, height)) {

            // Encode a frame
            final List<VpxPacket> packets = encoder.encode(image, 0, 1000, 0);
            packetsReceived += packets.size();

            // Assert payload extraction works
            for (final VpxPacket pkt : packets) {
                assertTrue(pkt.size() > 0, "Packet should contain data");
                final byte[] bytes = pkt.toByteArray();
                assertTrue(bytes.length > 0, "Packet byte array should not be empty");
            }

            // Flush the encoder
            final List<VpxPacket> flushPackets = encoder.flush();
            packetsReceived += flushPackets.size();
        }

        assertTrue(
                packetsReceived > 0, "Should have received at least one packet from the encoder");
    }

    @Test
    void testInvalidConfigThrowsException() {
        // 0 width/height is invalid and should be rejected by libvpx during init
        final VpxEncoderConfig config = new VpxEncoderConfig(0, 0);
        assertThrows(
                VpxException.class,
                () -> new Vp8Encoder(config),
                "Should throw exception on invalid configuration");
    }

    /**
     * Verifies that repeated constructor failures do not leak native memory. Before the fix,
     * Arena.ofShared() was opened as a field initialiser and never closed when checkError threw.
     * Running many iterations exposes native heap exhaustion if the leak exists.
     */
    @Test
    void testConstructorFailureDoesNotLeakNativeMemory() {
        final VpxEncoderConfig bad = new VpxEncoderConfig(0, 0);
        for (int i = 0; i < 500; i++) {
            assertThrows(VpxException.class, () -> new Vp8Encoder(bad));
        }
    }

    @Test
    void testEncoderUseAfterCloseThrowsException() {
        final VpxEncoderConfig config = new VpxEncoderConfig(320, 240);
        final Vp8Encoder encoder = new Vp8Encoder(config);
        encoder.close();

        final byte[] dummyData = new byte[320 * 240 * 3 / 2];
        try (VpxImage image = VpxImage.fromByteArray(dummyData, 320, 240)) {
            assertThrows(
                    IllegalStateException.class,
                    () -> encoder.encode(image, 0, 1000, 0),
                    "Should throw IllegalStateException when using closed arena");
        }
    }
}
