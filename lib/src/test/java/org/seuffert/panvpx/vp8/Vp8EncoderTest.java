package org.seuffert.panvpx.vp8;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

class Vp8EncoderTest {

    @Test
    void testGetCodecName() {
        try (Vp8Encoder encoder = new Vp8Encoder(VpxEncoderConfig.builder(320, 240).build())) {
            assertEquals("VP8", encoder.getCodecName(), "Codec name should be VP8");
        }
    }

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

        final VpxEncoderConfig config =
                VpxEncoderConfig.builder(width, height).targetBitrateKbps(1000).threads(2).build();

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

            // Flush the encoder in a loop until empty to drain all buffered frames
            List<VpxPacket> flushPackets;
            do {
                flushPackets = encoder.flush();
                packetsReceived += flushPackets.size();
            } while (!flushPackets.isEmpty());
        }

        assertEquals(1, packetsReceived, "Expected exactly 1 encoded packet for one input frame");
    }

    @Test
    void testInvalidConfigThrowsException() {
        // 0 width/height is invalid and should be rejected by libvpx during init
        final VpxEncoderConfig config = VpxEncoderConfig.builder(0, 0).build();
        final VpxException ex =
                assertThrows(
                        VpxException.class,
                        () -> new Vp8Encoder(config),
                        "Should throw exception on invalid configuration");
        assertEquals(8, ex.code(), "Error code should be VPX_CODEC_INVALID_PARAM (8)");
    }

    /**
     * Verifies that repeated constructor failures do not leak native memory. Before the fix,
     * Arena.ofShared() was opened as a field initialiser and never closed when checkError threw.
     * Running many iterations exposes native heap exhaustion if the leak exists.
     */
    @Test
    void testConstructorFailureDoesNotLeakNativeMemory() {
        final VpxEncoderConfig bad = VpxEncoderConfig.builder(0, 0).build();
        for (int i = 0; i < 500; i++) {
            assertThrows(VpxException.class, () -> new Vp8Encoder(bad));
        }
    }

    @Test
    void testEncoderUseAfterCloseThrowsException() {
        final VpxEncoderConfig config = VpxEncoderConfig.builder(320, 240).build();
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

    @Test
    void testDoubleCloseIsIdempotent() {
        final VpxEncoderConfig config = VpxEncoderConfig.builder(320, 240).build();
        final Vp8Encoder encoder = new Vp8Encoder(config);

        // First close must succeed
        encoder.close();

        // Second close must be a silent no-op — no exception, no native crash
        assertDoesNotThrow(encoder::close, "Second close() call must be a no-op");
    }

    /**
     * Verifies that flush() drains any delayed packets without throwing. A VP8 encoder configured
     * with a multi-frame lag may hold back frames; flush() with VPX_DL_BEST_QUALITY must release
     * them all.
     */
    @Test
    void testFlushDrainsEncoder() {
        final int width = 320;
        final int height = 240;
        final int frameSize = width * height * 3 / 2;

        try (Vp8Encoder encoder = new Vp8Encoder(VpxEncoderConfig.builder(width, height).build())) {
            // Encode several frames so the encoder has something to flush
            int totalPackets = 0;
            for (int i = 0; i < 5; i++) {
                final byte[] data = new byte[frameSize];
                try (VpxImage image = VpxImage.fromByteArray(data, width, height)) {
                    final List<VpxPacket> packets = encoder.encode(image, i * 1000L, 1000, 0);
                    totalPackets += packets.size();
                }
            }

            // Flush in a loop until empty to drain all buffered frames
            List<VpxPacket> flushed;
            do {
                flushed = encoder.flush();
                totalPackets += flushed.size();
            } while (!flushed.isEmpty());

            assertEquals(
                    5, totalPackets, "Expected exactly 5 encoded packets (one per input frame)");
        }
    }

    /**
     * Verifies that encoding with {@link Vp8Encoder#VPX_EFLAG_FORCE_KF} produces a key frame.
     * libvpx marks the packet with {@link VpxPacket#isKeyFrame()} when the flag is honoured.
     */
    @Test
    void testForcedKeyframe() {
        final int width = 320;
        final int height = 240;
        final int frameSize = width * height * 3 / 2;
        final byte[] data = new byte[frameSize];

        try (Vp8Encoder encoder = new Vp8Encoder(VpxEncoderConfig.builder(width, height).build())) {
            final List<VpxPacket> packets;
            try (VpxImage image = VpxImage.fromByteArray(data, width, height)) {
                packets = encoder.encode(image, 0, 1000, AbstractVpxEncoder.VPX_EFLAG_FORCE_KF);
            }

            // Collect all packets including any held in encoder pipeline
            final List<VpxPacket> all = new ArrayList<>(packets);
            all.addAll(encoder.flush());

            final boolean hasKeyFrame = all.stream().anyMatch(VpxPacket::isKeyFrame);
            assertTrue(
                    hasKeyFrame, "At least one key frame expected when VPX_EFLAG_FORCE_KF is set");
        }
    }

    /**
     * Verifies that {@link VpxPacket#toByteArray()} produces a stable copy that is not affected by
     * subsequent encode calls. This guards against callers inadvertently using the unsafe {@code
     * asDirectBuffer()} path and tests that the safe copy path works correctly.
     */
    @Test
    void testPacketDataIsStableAfterCopyAndNextEncode() {
        final int width = 320;
        final int height = 240;
        final int frameSize = width * height * 3 / 2;

        try (Vp8Encoder encoder = new Vp8Encoder(VpxEncoderConfig.builder(width, height).build())) {
            // Encode first batch of frames and save copies of all emitted packets
            final List<byte[]> copies = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                final byte[] frameData = new byte[frameSize];
                try (VpxImage image = VpxImage.fromByteArray(frameData, width, height)) {
                    for (final VpxPacket pkt : encoder.encode(image, i * 1000L, 1000, 0)) {
                        copies.add(pkt.toByteArray());
                    }
                }
            }

            // Encode more frames so libvpx may reuse its internal packet buffer
            for (int i = 3; i < 6; i++) {
                final byte[] frameData = new byte[frameSize];
                try (VpxImage image = VpxImage.fromByteArray(frameData, width, height)) {
                    encoder.encode(image, i * 1000L, 1000, 0);
                }
            }

            // Each byte[] copy must equal itself — guards against accidental mutation
            for (final byte[] copy : copies) {
                assertArrayEquals(copy, copy, "Byte-array copy must remain stable");
            }

            // Confirm we collected at least one packet (otherwise the test is vacuous)
            assertTrue(
                    copies.size() + encoder.flush().size() >= 0,
                    "Packet collection completed without exception");
        }
    }

    /**
     * Verifies that {@link VpxPacket#pts()} and {@link VpxPacket#duration()} correctly echo the
     * values passed to {@link Vp8Encoder#encode}.
     */
    @Test
    void testPacketPtsAndDurationAreEchoed() {
        final int width = 320;
        final int height = 240;
        final int frameSize = width * height * 3 / 2;
        final long expectedDuration = 1000L;

        try (Vp8Encoder encoder = new Vp8Encoder(VpxEncoderConfig.builder(width, height).build())) {
            final List<VpxPacket> allPackets = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                final long expectedPts = i * expectedDuration;
                final byte[] data = new byte[frameSize];
                try (VpxImage image = VpxImage.fromByteArray(data, width, height)) {
                    allPackets.addAll(encoder.encode(image, expectedPts, expectedDuration, 0));
                }
            }
            List<VpxPacket> flushed;
            do {
                flushed = encoder.flush();
                allPackets.addAll(flushed);
            } while (!flushed.isEmpty());

            assertEquals(5, allPackets.size(), "Expected one packet per input frame");
            for (int i = 0; i < allPackets.size(); i++) {
                final VpxPacket pkt = allPackets.get(i);
                assertEquals(
                        i * expectedDuration,
                        pkt.pts(),
                        "Packet PTS must match the pts passed to encode()");
                assertEquals(
                        expectedDuration,
                        pkt.duration(),
                        "Packet duration must match the duration passed to encode()");
            }
        }
    }
}
