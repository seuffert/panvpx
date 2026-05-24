package org.seuffert.panvpx.vp8;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.seuffert.panvpx.core.VpxImage;
import org.seuffert.panvpx.core.VpxPacket;

class Vp8EncoderIT {

    @Test
    void testEncodeRealYuvFile() throws Exception {
        final int width = 320;
        final int height = 240;
        final int fps = 30;

        // Size for I420 format (Y plane + U/V planes which are 1/4 size each)
        final int frameSize = width * height * 3 / 2;
        final byte[] frameBuffer = new byte[frameSize];

        final VpxEncoderConfig config = new VpxEncoderConfig(width, height, 500, 1);

        int frameCount = 0;
        long totalEncodedBytes = 0;

        try (InputStream is =
                        Vp8EncoderIT.class.getResourceAsStream("/test_video_320x240_30fps.yuv");
                Vp8Encoder encoder = new Vp8Encoder(config)) {

            assertTrue(is != null, "Test video resource not found");

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

            final List<VpxPacket> flushPackets = encoder.flush();
            for (final VpxPacket pkt : flushPackets) {
                totalEncodedBytes += pkt.size();
            }
        }

        // We know the source video is 3 seconds at 30fps = 90 frames
        assertTrue(frameCount > 80, "Should have read and encoded ~90 frames");
        assertTrue(totalEncodedBytes > 10_000, "Should have encoded a substantial amount of data");
    }
}
