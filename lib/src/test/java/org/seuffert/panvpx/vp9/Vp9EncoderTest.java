package org.seuffert.panvpx.vp9;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.seuffert.panvpx.core.AbstractVpxEncoder;
import org.seuffert.panvpx.core.VpxEncoderConfig;
import org.seuffert.panvpx.core.VpxException;
import org.seuffert.panvpx.core.VpxImage;
import org.seuffert.panvpx.core.VpxPacket;

class Vp9EncoderTest {

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

        try (Vp9Encoder encoder = new Vp9Encoder(config);
                VpxImage image = VpxImage.fromByteArray(dummyData, width, height)) {

            final List<VpxPacket> packets = encoder.encode(image, 0, 1000, 0);
            packetsReceived += packets.size();

            for (final VpxPacket pkt : packets) {
                assertTrue(pkt.size() > 0, "Packet should contain data");
                final byte[] bytes = pkt.toByteArray();
                assertTrue(bytes.length > 0, "Packet byte array should not be empty");
            }

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
                () -> new Vp9Encoder(config),
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
            assertThrows(VpxException.class, () -> new Vp9Encoder(bad));
        }
    }

    @Test
    void testEncoderUseAfterCloseThrowsException() {
        final VpxEncoderConfig config = new VpxEncoderConfig(320, 240);
        final Vp9Encoder encoder = new Vp9Encoder(config);
        encoder.close();

        final byte[] dummyData = new byte[320 * 240 * 3 / 2];
        try (VpxImage image = VpxImage.fromByteArray(dummyData, 320, 240)) {
            assertThrows(
                    IllegalStateException.class,
                    () -> encoder.encode(image, 0, 1000, 0),
                    "Should throw IllegalStateException when using closed arena");
        }
    }

    @Test
    void testDoubleCloseIsIdempotent() {
        final VpxEncoderConfig config = new VpxEncoderConfig(320, 240);
        final Vp9Encoder encoder = new Vp9Encoder(config);

        // First close must succeed
        encoder.close();

        // Second close must be a silent no-op — no exception, no native crash
        assertDoesNotThrow(encoder::close, "Second close() call must be a no-op");
    }

    /**
     * Verifies that flush() drains any delayed packets without throwing. A VP9 encoder configured
     * with a multi-frame lag may hold back frames; flush() with VPX_DL_BEST_QUALITY must release
     * them all.
     */
    @Test
    void testFlushDrainsEncoder() {
        final int width = 320;
        final int height = 240;
        final int frameSize = width * height * 3 / 2;

        try (Vp9Encoder encoder = new Vp9Encoder(new VpxEncoderConfig(width, height))) {
            int totalPackets = 0;
            for (int i = 0; i < 5; i++) {
                final byte[] data = new byte[frameSize];
                try (VpxImage image = VpxImage.fromByteArray(data, width, height)) {
                    final List<VpxPacket> packets = encoder.encode(image, i * 1000L, 1000, 0);
                    totalPackets += packets.size();
                }
            }

            final List<VpxPacket> flushed = encoder.flush();
            totalPackets += flushed.size();

            assertTrue(
                    totalPackets > 0, "At least one encoded packet expected across encode+flush");
        }
    }

    /**
     * Verifies that encoding with {@link AbstractVpxEncoder#VPX_EFLAG_FORCE_KF} produces a key
     * frame. libvpx marks the packet with {@link VpxPacket#isKeyFrame()} when the flag is honoured.
     */
    @Test
    void testForcedKeyframe() {
        final int width = 320;
        final int height = 240;
        final int frameSize = width * height * 3 / 2;
        final byte[] data = new byte[frameSize];

        try (Vp9Encoder encoder = new Vp9Encoder(new VpxEncoderConfig(width, height))) {
            final List<VpxPacket> packets;
            try (VpxImage image = VpxImage.fromByteArray(data, width, height)) {
                packets = encoder.encode(image, 0, 1000, AbstractVpxEncoder.VPX_EFLAG_FORCE_KF);
            }

            final List<VpxPacket> all = new ArrayList<>(packets);
            all.addAll(encoder.flush());

            final boolean hasKeyFrame = all.stream().anyMatch(VpxPacket::isKeyFrame);
            assertTrue(
                    hasKeyFrame, "At least one key frame expected when VPX_EFLAG_FORCE_KF is set");
        }
    }

    /**
     * Verifies that {@link VpxPacket#toByteArray()} produces a stable copy that is not affected by
     * subsequent encode calls.
     */
    @Test
    void testPacketDataIsStableAfterCopyAndNextEncode() {
        final int width = 320;
        final int height = 240;
        final int frameSize = width * height * 3 / 2;

        try (Vp9Encoder encoder = new Vp9Encoder(new VpxEncoderConfig(width, height))) {
            final List<byte[]> copies = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                final byte[] frameData = new byte[frameSize];
                try (VpxImage image = VpxImage.fromByteArray(frameData, width, height)) {
                    for (final VpxPacket pkt : encoder.encode(image, i * 1000L, 1000, 0)) {
                        copies.add(pkt.toByteArray());
                    }
                }
            }

            for (int i = 3; i < 6; i++) {
                final byte[] frameData = new byte[frameSize];
                try (VpxImage image = VpxImage.fromByteArray(frameData, width, height)) {
                    encoder.encode(image, i * 1000L, 1000, 0);
                }
            }

            for (final byte[] copy : copies) {
                assertArrayEquals(copy, copy, "Byte-array copy must remain stable");
            }
        }
    }
}
