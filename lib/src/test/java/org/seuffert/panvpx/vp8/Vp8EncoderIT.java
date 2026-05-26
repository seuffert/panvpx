package org.seuffert.panvpx.vp8;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.seuffert.panvpx.core.VpxEncoderConfig;
import org.seuffert.panvpx.core.VpxImage;
import org.seuffert.panvpx.core.VpxPacket;

class Vp8EncoderIT {

    private static final String TEST_VIDEO_RESOURCE = "/test_video_320x240_30fps.yuv";
    private static final String MSG_RESOURCE_NOT_FOUND = "Test video resource not found";

    @Test
    void testEncodeRealYuvFile() throws Exception {
        final int width = 320;
        final int height = 240;
        final int fps = 30;

        // Size for I420 format (Y plane + U/V planes which are 1/4 size each)
        final int frameSize = width * height * 3 / 2;
        final byte[] frameBuffer = new byte[frameSize];

        final VpxEncoderConfig config =
                VpxEncoderConfig.builder(width, height).targetBitrateKbps(500).threads(1).build();

        int frameCount = 0;
        long totalEncodedBytes = 0;

        try (InputStream is = Vp8EncoderIT.class.getResourceAsStream(TEST_VIDEO_RESOURCE);
                Vp8Encoder encoder = new Vp8Encoder(config)) {

            assertTrue(is != null, MSG_RESOURCE_NOT_FOUND);

            // Read exactly one frame at a time
            while (is.read(frameBuffer) == frameSize) {
                try (VpxImage image = VpxImage.fromByteArray(frameBuffer, width, height)) {

                    // PTS in our configuration defaults to 1/1000 timebase (milliseconds).
                    // So for 30fps, duration is ~33ms per frame.
                    final long pts = frameCount * (1000L / fps);
                    final long duration = 1000L / fps;

                    final List<VpxPacket> packets = encoder.encode(image, pts, duration, 0);

                    for (final VpxPacket pkt : packets) {
                        assertTrue(pkt.size() > 0, "Packet should contain data");
                        totalEncodedBytes += pkt.size();
                    }
                    frameCount++;
                }
            }

            List<VpxPacket> flushPackets;
            do {
                flushPackets = encoder.flush();
                for (final VpxPacket pkt : flushPackets) {
                    totalEncodedBytes += pkt.size();
                }
            } while (!flushPackets.isEmpty());
        }

        // We know the source video is 3 seconds at 30fps = 90 frames
        assertEquals(90, frameCount, "Should have read and encoded exactly 90 frames");
        assertTrue(totalEncodedBytes > 10_000, "Should have encoded a substantial amount of data");
    }

    @Test
    void testEncodeForcesKeyframe() throws Exception {
        final int width = 320;
        final int height = 240;
        final int fps = 30;

        final int frameSize = width * height * 3 / 2;
        final byte[] frameBuffer = new byte[frameSize];

        final VpxEncoderConfig config =
                VpxEncoderConfig.builder(width, height).targetBitrateKbps(500).threads(1).build();

        try (InputStream is = Vp8EncoderIT.class.getResourceAsStream(TEST_VIDEO_RESOURCE);
                Vp8Encoder encoder = new Vp8Encoder(config)) {

            assertTrue(is != null, MSG_RESOURCE_NOT_FOUND);

            int frameCount = 0;
            int keyframesFound = 0;

            while (is.read(frameBuffer) == frameSize) {
                try (VpxImage image = VpxImage.fromByteArray(frameBuffer, width, height)) {

                    final long pts = frameCount * (1000L / fps);
                    final long duration = 1000L / fps;

                    // Force a keyframe on frame 30 and 60 exactly
                    final long flags =
                            (frameCount == 30 || frameCount == 60)
                                    ? org.seuffert.panvpx.ffi.VpxFFI.VPX_EFLAG_FORCE_KF()
                                    : 0;

                    final List<VpxPacket> packets = encoder.encode(image, pts, duration, flags);

                    for (final VpxPacket pkt : packets) {
                        if (pkt.isKeyFrame()) {
                            keyframesFound++;
                        }
                    }
                    frameCount++;
                }
            }

            // Frame 0 is ALWAYS a keyframe. Frame 30 and 60 were forced.
            // libvpx may naturally insert others, but we expect at least 3.
            assertTrue(keyframesFound >= 3, "Should have found at least 3 keyframes (0, 30, 60)");
        }
    }

    @Test
    void testTargetBitrateChangesOutputSize() throws Exception {
        final int width = 320;
        final int height = 240;

        final long sizeLowBitrate =
                encodeEntireFile(
                        VpxEncoderConfig.builder(width, height)
                                .targetBitrateKbps(100)
                                .threads(1)
                                .build());
        final long sizeHighBitrate =
                encodeEntireFile(
                        VpxEncoderConfig.builder(width, height)
                                .targetBitrateKbps(1000)
                                .threads(1)
                                .build());

        assertTrue(
                sizeHighBitrate > (sizeLowBitrate * 1.5),
                "High bitrate config should produce significantly larger output than low bitrate. "
                        + "Low: "
                        + sizeLowBitrate
                        + ", High: "
                        + sizeHighBitrate);
    }

    @Test
    void testFirstPacketIsKeyframe() throws Exception {
        final int width = 320;
        final int height = 240;
        final int fps = 30;
        final int frameSize = width * height * 3 / 2;
        final byte[] frameBuffer = new byte[frameSize];

        try (InputStream is = Vp8EncoderIT.class.getResourceAsStream(TEST_VIDEO_RESOURCE);
                Vp8Encoder encoder =
                        new Vp8Encoder(
                                VpxEncoderConfig.builder(width, height)
                                        .targetBitrateKbps(500)
                                        .threads(1)
                                        .build())) {
            assertTrue(is != null, MSG_RESOURCE_NOT_FOUND);
            assertTrue(
                    is.readNBytes(frameBuffer, 0, frameSize) == frameSize,
                    "Could not read a complete first frame");
            try (VpxImage image = VpxImage.fromByteArray(frameBuffer, width, height)) {
                final List<VpxPacket> packets = encoder.encode(image, 0, 1000L / fps, 0);
                assertFalse(packets.isEmpty(), "First frame must produce at least one packet");
                assertTrue(packets.get(0).isKeyFrame(), "First encoded packet must be a keyframe");
            }
        }
    }

    @Test
    void testToByteArrayAndDirectBufferAreConsistent() throws Exception {
        final int width = 320;
        final int height = 240;
        final int fps = 30;
        final int frameSize = width * height * 3 / 2;
        final byte[] frameBuffer = new byte[frameSize];

        try (InputStream is = Vp8EncoderIT.class.getResourceAsStream(TEST_VIDEO_RESOURCE);
                Vp8Encoder encoder =
                        new Vp8Encoder(
                                VpxEncoderConfig.builder(width, height)
                                        .targetBitrateKbps(500)
                                        .threads(1)
                                        .build())) {
            assertTrue(is != null, MSG_RESOURCE_NOT_FOUND);
            assertTrue(
                    is.readNBytes(frameBuffer, 0, frameSize) == frameSize,
                    "Could not read a complete first frame");
            try (VpxImage image = VpxImage.fromByteArray(frameBuffer, width, height)) {
                final List<VpxPacket> packets = encoder.encode(image, 0, 1000L / fps, 0);
                assertFalse(packets.isEmpty(), "First frame must produce at least one packet");

                final VpxPacket pkt = packets.get(0);
                // Capture both representations before the next encode() invalidates native memory
                final byte[] viaArray = pkt.toByteArray();
                final ByteBuffer buf = pkt.asDirectBuffer();
                final byte[] viaBuf = new byte[buf.remaining()];
                buf.get(viaBuf);

                assertArrayEquals(
                        viaArray,
                        viaBuf,
                        "toByteArray() and asDirectBuffer() must return identical bytes");
            }
        }
    }

    @Test
    void testFromMemorySegmentProducesSameOutputAsFromByteArray() throws Exception {
        final int width = 320;
        final int height = 240;
        final int fps = 30;
        final int frameSize = width * height * 3 / 2;
        final byte[] frameBuffer = new byte[frameSize];

        try (InputStream is = Vp8EncoderIT.class.getResourceAsStream(TEST_VIDEO_RESOURCE)) {
            assertTrue(is != null, MSG_RESOURCE_NOT_FOUND);
            assertTrue(
                    is.readNBytes(frameBuffer, 0, frameSize) == frameSize,
                    "Could not read a complete first frame");
        }

        // Encode via the simple copy path
        final byte[] outputViaByteArray;
        try (Vp8Encoder encoder =
                        new Vp8Encoder(
                                VpxEncoderConfig.builder(width, height)
                                        .targetBitrateKbps(500)
                                        .threads(1)
                                        .build());
                VpxImage image = VpxImage.fromByteArray(frameBuffer, width, height)) {
            final List<VpxPacket> pkts = encoder.encode(image, 0, 1000L / fps, 0);
            outputViaByteArray = pkts.isEmpty() ? new byte[0] : pkts.get(0).toByteArray();
        }

        // Encode the same frame via the zero-copy MemorySegment path
        final byte[] outputViaSegment;
        try (Arena arena = Arena.ofConfined()) {
            final MemorySegment segment = arena.allocate(frameSize);
            MemorySegment.copy(frameBuffer, 0, segment, ValueLayout.JAVA_BYTE, 0, frameSize);
            try (Vp8Encoder encoder =
                            new Vp8Encoder(
                                    VpxEncoderConfig.builder(width, height)
                                            .targetBitrateKbps(500)
                                            .threads(1)
                                            .build());
                    VpxImage image = VpxImage.fromMemorySegment(segment, width, height)) {
                final List<VpxPacket> pkts = encoder.encode(image, 0, 1000L / fps, 0);
                outputViaSegment = pkts.isEmpty() ? new byte[0] : pkts.get(0).toByteArray();
            }
        }

        assertArrayEquals(
                outputViaByteArray,
                outputViaSegment,
                "fromByteArray() and fromMemorySegment() must produce identical encoded output");
    }

    @Test
    void testOutputSizeIsWithinExpectedBitrateRange() throws Exception {
        final int width = 320;
        final int height = 240;
        // 500 kbps * 3 s = 187,500 bytes theoretical. Allow a generous 25%–400% tolerance
        // to stay robust across libvpx versions and platforms.
        final long expectedMinBytes = 500L * 3 * 1_000 / 8 / 4; // ~46,875
        final long expectedMaxBytes = 500L * 3 * 1_000 / 8 * 4; // ~750,000
        final long actualBytes =
                encodeEntireFile(
                        VpxEncoderConfig.builder(width, height)
                                .targetBitrateKbps(500)
                                .threads(1)
                                .build());

        assertTrue(
                actualBytes >= expectedMinBytes,
                "Output too small for 500 kbps target: " + actualBytes + " bytes");
        assertTrue(
                actualBytes <= expectedMaxBytes,
                "Output too large for 500 kbps target: " + actualBytes + " bytes");
    }

    @Test
    void testEncoderIsUsableAcrossThreads() throws Exception {
        final int width = 320;
        final int height = 240;
        final int fps = 30;
        final int frameSize = width * height * 3 / 2;

        // Encoder created on the main (test) thread — Arena.ofShared() allows cross-thread access
        final Vp8Encoder encoder =
                new Vp8Encoder(
                        VpxEncoderConfig.builder(width, height)
                                .targetBitrateKbps(500)
                                .threads(1)
                                .build());
        final long[] totalBytes = {0};
        final Throwable[] workerError = {null};

        // All encoding runs on a separate worker thread
        final Thread worker =
                new Thread(
                        () -> {
                            try (InputStream is =
                                    Vp8EncoderIT.class.getResourceAsStream(TEST_VIDEO_RESOURCE)) {
                                final byte[] buf = new byte[frameSize];
                                int frameCount = 0;
                                while (is.read(buf) == frameSize) {
                                    try (VpxImage img =
                                            VpxImage.fromByteArray(buf, width, height)) {
                                        final long pts = frameCount * (1000L / fps);
                                        for (final VpxPacket pkt :
                                                encoder.encode(img, pts, 1000L / fps, 0)) {
                                            totalBytes[0] += pkt.size();
                                        }
                                    }
                                    frameCount++;
                                }
                            } catch (final Exception e) {
                                workerError[0] = e;
                            }
                        });
        worker.start();
        worker.join(); // join() establishes happens-before; no extra synchronization needed

        // flush() and close() on the main thread — different from the encode thread
        for (final VpxPacket pkt : encoder.flush()) {
            totalBytes[0] += pkt.size();
        }
        encoder.close();

        assertTrue(workerError[0] == null, "Worker thread threw an exception: " + workerError[0]);
        assertTrue(totalBytes[0] > 10_000, "Cross-thread encoding should produce data");
    }

    private long encodeEntireFile(final VpxEncoderConfig config) throws java.io.IOException {
        final int frameSize = config.width() * config.height() * 3 / 2;
        final byte[] frameBuffer = new byte[frameSize];
        long totalBytes = 0;

        try (InputStream is = Vp8EncoderIT.class.getResourceAsStream(TEST_VIDEO_RESOURCE);
                Vp8Encoder encoder = new Vp8Encoder(config)) {

            int frameCount = 0;
            while (is.read(frameBuffer) == frameSize) {
                try (VpxImage image =
                        VpxImage.fromByteArray(frameBuffer, config.width(), config.height())) {
                    final long pts = frameCount * (1000L / 30);
                    final List<VpxPacket> packets = encoder.encode(image, pts, 1000L / 30, 0);
                    for (final VpxPacket pkt : packets) {
                        totalBytes += pkt.size();
                    }
                    frameCount++;
                }
            }
            for (final VpxPacket pkt : encoder.flush()) {
                totalBytes += pkt.size();
            }
        }
        return totalBytes;
    }
}
