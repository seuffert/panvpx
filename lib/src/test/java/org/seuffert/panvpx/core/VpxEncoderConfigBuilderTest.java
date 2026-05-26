package org.seuffert.panvpx.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.seuffert.panvpx.vp8.Vp8Encoder;

class VpxEncoderConfigBuilderTest {

    // --- Builder defaults ---

    @Test
    void testBuilderDefaults() {
        final VpxEncoderConfig config = VpxEncoderConfig.builder(320, 240).build();
        assertEquals(320, config.width());
        assertEquals(240, config.height());
        assertEquals(256, config.targetBitrateKbps());
        assertEquals(0, config.frameDropThreshold());
        assertEquals(1, config.threads());
        assertEquals(1, config.timebaseNumerator());
        assertEquals(1_000, config.timebaseDenominator());
        assertEquals(VpxEncoderConfig.DEADLINE_REALTIME, config.deadline());
        assertEquals(0, config.cpuUsed());
        assertFalse(config.rowMt());
        assertEquals(0, config.tileColumns());
        assertEquals(0, config.tokenPartitions());
        assertEquals(VpxEncoderConfig.RateControlMode.VBR, config.rateControlMode());
        assertEquals(0, config.maxKeyframeDistance());
        assertEquals(VpxEncoderConfig.KeyframeMode.AUTO, config.keyframeMode());
        assertEquals(0, config.profile());
        assertEquals(0, config.usage());
        assertFalse(config.errorResilient());
        assertEquals(0, config.lagInFrames());
        assertEquals(63, config.maxQuantizer());
        assertEquals(0, config.minQuantizer());
    }

    // --- All builder setters round-trip through record accessors ---

    @Test
    void testBuilderAllFieldsRoundTrip() {
        final VpxEncoderConfig config =
                VpxEncoderConfig.builder(640, 480)
                        .targetBitrateKbps(2000)
                        .frameDropThreshold(10)
                        .threads(4)
                        .timebaseNumerator(1)
                        .timebaseDenominator(90_000)
                        .deadline(VpxEncoderConfig.DEADLINE_GOOD_QUALITY)
                        .cpuUsed(4)
                        .rowMt(true)
                        .tileColumns(2)
                        .tokenPartitions(4)
                        .rateControlMode(VpxEncoderConfig.RateControlMode.CBR)
                        .maxKeyframeDistance(120)
                        .keyframeMode(VpxEncoderConfig.KeyframeMode.AUTO)
                        .profile(0)
                        .usage(0)
                        .errorResilient(true)
                        .lagInFrames(0)
                        .maxQuantizer(54)
                        .minQuantizer(4)
                        .build();

        assertEquals(640, config.width());
        assertEquals(480, config.height());
        assertEquals(2000, config.targetBitrateKbps());
        assertEquals(10, config.frameDropThreshold());
        assertEquals(4, config.threads());
        assertEquals(1, config.timebaseNumerator());
        assertEquals(90_000, config.timebaseDenominator());
        assertEquals(VpxEncoderConfig.DEADLINE_GOOD_QUALITY, config.deadline());
        assertEquals(4, config.cpuUsed());
        assertTrue(config.rowMt());
        assertEquals(2, config.tileColumns());
        assertEquals(4, config.tokenPartitions());
        assertEquals(VpxEncoderConfig.RateControlMode.CBR, config.rateControlMode());
        assertEquals(120, config.maxKeyframeDistance());
        assertEquals(VpxEncoderConfig.KeyframeMode.AUTO, config.keyframeMode());
        assertEquals(0, config.profile());
        assertEquals(0, config.usage());
        assertTrue(config.errorResilient());
        assertEquals(0, config.lagInFrames());
        assertEquals(54, config.maxQuantizer());
        assertEquals(4, config.minQuantizer());
    }

    // --- Functional: CBR encoding produces output ---

    @Test
    void testCbrEncodingProducesPacket() {
        final int width = 320;
        final int height = 240;
        final int frameSize = width * height * 3 / 2;
        final byte[] frame = new byte[frameSize];

        final VpxEncoderConfig config =
                VpxEncoderConfig.builder(width, height)
                        .targetBitrateKbps(500)
                        .rateControlMode(VpxEncoderConfig.RateControlMode.CBR)
                        .threads(1)
                        .build();

        int totalPackets = 0;
        try (Vp8Encoder encoder = new Vp8Encoder(config);
                VpxImage image = VpxImage.fromByteArray(frame, width, height)) {
            totalPackets += encoder.encode(image, 0, 1000, 0).size();
            List<VpxPacket> flush;
            do {
                flush = encoder.flush();
                totalPackets += flush.size();
            } while (!flush.isEmpty());
        }

        assertEquals(1, totalPackets, "CBR encoder should produce exactly 1 packet for 1 frame");
    }

    // --- Functional: maxKeyframeDistance forces periodic key frames ---

    @Test
    void testMaxKeyframeDistanceInsertsPeriodic() {
        final int width = 320;
        final int height = 240;
        final int frameSize = width * height * 3 / 2;
        final byte[] frame = new byte[frameSize];

        final int kfDistance = 5;
        final int totalFrames = 12; // spans two full keyframe intervals

        final VpxEncoderConfig config =
                VpxEncoderConfig.builder(width, height)
                        .targetBitrateKbps(500)
                        .maxKeyframeDistance(kfDistance)
                        .build();

        final List<Boolean> keyFlags = new ArrayList<>();
        try (Vp8Encoder encoder = new Vp8Encoder(config);
                VpxImage image = VpxImage.fromByteArray(frame, width, height)) {
            for (int i = 0; i < totalFrames; i++) {
                final List<VpxPacket> packets = encoder.encode(image, i * 1000L, 1000, 0);
                for (final VpxPacket pkt : packets) {
                    keyFlags.add(pkt.isKeyFrame());
                }
            }
            List<VpxPacket> flush;
            do {
                flush = encoder.flush();
                for (final VpxPacket pkt : flush) {
                    keyFlags.add(pkt.isKeyFrame());
                }
            } while (!flush.isEmpty());
        }

        assertFalse(keyFlags.isEmpty(), "Encoder should have produced at least one packet");
        assertTrue(keyFlags.get(0), "First packet must always be a key frame");

        // With kfDistance = 5 and 12 frames we expect key frames at positions 0 and 5 (at minimum)
        final long keyFrameCount = keyFlags.stream().filter(Boolean::booleanValue).count();
        assertTrue(
                keyFrameCount >= 2, "Expected at least 2 key frames for 12 frames with kfDist=5");
    }

    // --- Functional: error-resilient flag is accepted without errors ---

    @Test
    void testErrorResilientEncodesWithoutException() {
        final int width = 320;
        final int height = 240;
        final byte[] frame = new byte[width * height * 3 / 2];

        final VpxEncoderConfig config =
                VpxEncoderConfig.builder(width, height).errorResilient(true).build();

        try (Vp8Encoder encoder = new Vp8Encoder(config);
                VpxImage image = VpxImage.fromByteArray(frame, width, height)) {
            final List<VpxPacket> packets = encoder.encode(image, 0, 1000, 0);
            assertNotNull(packets, "Packet list should not be null");
        }
    }

    // --- Functional: custom quantiser range is accepted without errors ---

    @Test
    void testJniQuantiserRangeEncodesWithoutException() {
        final int width = 320;
        final int height = 240;
        final byte[] frame = new byte[width * height * 3 / 2];

        // Replicate the legacy JNI quantiser cap (rc_max_quantizer = 54)
        final VpxEncoderConfig config =
                VpxEncoderConfig.builder(width, height).maxQuantizer(54).build();

        try (Vp8Encoder encoder = new Vp8Encoder(config);
                VpxImage image = VpxImage.fromByteArray(frame, width, height)) {
            final List<VpxPacket> packets = encoder.encode(image, 0, 1000, 0);
            assertNotNull(packets, "Packet list should not be null");
        }
    }
}
