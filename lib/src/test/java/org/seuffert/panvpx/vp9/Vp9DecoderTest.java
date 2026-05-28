package org.seuffert.panvpx.vp9;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.seuffert.panvpx.core.AbstractVpxEncoder;
import org.seuffert.panvpx.core.VpxDecoderConfig;
import org.seuffert.panvpx.core.VpxEncoderConfig;
import org.seuffert.panvpx.core.VpxException;
import org.seuffert.panvpx.core.VpxImage;
import org.seuffert.panvpx.core.VpxPacket;

class Vp9DecoderTest {

    @Test
    void testGetCodecName() {
        try (Vp9Decoder decoder = new Vp9Decoder(new VpxDecoderConfig())) {
            assertEquals("VP9", decoder.getCodecName(), "Codec name should be VP9");
        }
    }

    @Test
    void testEndToEndEncodeDecode() {
        final int width = 320;
        final int height = 240;

        // Size for I420 format (Y plane + U/V planes which are 1/4 size each)
        final int frameSize = width * height * 3 / 2;
        final byte[] dummyData = new byte[frameSize];

        // Fill dummy data with a basic pattern
        for (int i = 0; i < dummyData.length; i++) {
            dummyData[i] = (byte) (i % 255);
        }

        final VpxEncoderConfig encConfig =
                VpxEncoderConfig.builder(VpxEncoderConfig.Codec.VP9, width, height).build();
        final VpxDecoderConfig decConfig = new VpxDecoderConfig();

        int framesDecoded = 0;

        try (Vp9Encoder encoder = new Vp9Encoder(encConfig);
                Vp9Decoder decoder = new Vp9Decoder(decConfig);
                VpxImage image = VpxImage.fromByteArray(dummyData, width, height)) {

            // Encode with FORCE_KF and decode each packet immediately
            for (final VpxPacket pkt :
                    encoder.encode(image, 0, 1000, AbstractVpxEncoder.VPX_EFLAG_FORCE_KF)) {
                final List<VpxImage> decodedImages = decoder.decode(pkt);
                framesDecoded += decodedImages.size();

                for (final VpxImage decodedImg : decodedImages) {
                    assertEquals(width, decodedImg.width(), "Decoded width must match");
                    assertEquals(height, decodedImg.height(), "Decoded height must match");
                    assertEquals(
                            VpxImage.VPX_IMG_FMT_I420,
                            decodedImg.format(),
                            "Decoded format must match");
                    assertEquals(
                            frameSize,
                            decodedImg.toByteArray().length,
                            "Decoded byte array size must match I420 size");
                }
            }

            // Flush encoder in a loop until empty and decode any remaining buffered frames
            List<VpxPacket> flushed;
            do {
                flushed = encoder.flush();
                for (final VpxPacket pkt : flushed) {
                    final List<VpxImage> decodedImages = decoder.decode(pkt);
                    framesDecoded += decodedImages.size();
                    for (final VpxImage decodedImg : decodedImages) {
                        assertEquals(width, decodedImg.width(), "Decoded width must match");
                        assertEquals(height, decodedImg.height(), "Decoded height must match");
                        assertEquals(
                                VpxImage.VPX_IMG_FMT_I420,
                                decodedImg.format(),
                                "Decoded format must match");
                        assertEquals(
                                frameSize,
                                decodedImg.toByteArray().length,
                                "Decoded byte array size must match I420 size");
                    }
                }
            } while (!flushed.isEmpty());
        }

        assertEquals(1, framesDecoded, "Decoder must produce exactly 1 decoded frame");
    }

    @Test
    void testInvalidDataThrowsException() {
        final byte[] badData = {(byte) 0x00, (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04};

        try (Vp9Decoder decoder = new Vp9Decoder(new VpxDecoderConfig())) {
            final VpxException ex =
                    assertThrows(
                            VpxException.class,
                            () -> decoder.decode(badData),
                            "Should throw exception on invalid bitstream data");
            assertTrue(ex.code() != 0, "Error code should be non-zero");
        }
    }

    @Test
    void testDecoderUseAfterCloseThrowsException() {
        final Vp9Decoder decoder = new Vp9Decoder(new VpxDecoderConfig());
        decoder.close();

        final byte[] badData = {(byte) 0x00};
        assertThrows(
                IllegalStateException.class,
                () -> decoder.decode(badData),
                "Should throw IllegalStateException when using closed arena");
    }

    @Test
    void testDoubleCloseIsIdempotent() {
        final Vp9Decoder decoder = new Vp9Decoder(new VpxDecoderConfig());

        // First close must succeed
        decoder.close();

        // Second close must be a silent no-op — no exception, no native crash
        assertDoesNotThrow(decoder::close, "Second close() call must be a no-op");
    }

    /**
     * Encodes and decodes a multi-frame stream to verify that the decoder's internal iterator is
     * properly reset between successive {@code decode()} calls. Also exercises the encode + flush
     * pipeline.
     */
    @Test
    void testMultiFrameEncodeDecodeStream() {
        final int width = 320;
        final int height = 240;
        final int frameSize = width * height * 3 / 2;
        final int frameCount = 5;

        try (Vp9Encoder encoder =
                        new Vp9Encoder(
                                VpxEncoderConfig.builder(VpxEncoderConfig.Codec.VP9, width, height)
                                        .build());
                Vp9Decoder decoder = new Vp9Decoder(new VpxDecoderConfig())) {
            int totalDecoded = 0;

            for (int i = 0; i < frameCount; i++) {
                final byte[] data = new byte[frameSize];
                Arrays.fill(data, 0, width * height, (byte) (i * 50));
                try (VpxImage image = VpxImage.fromByteArray(data, width, height)) {
                    for (final VpxPacket pkt : encoder.encode(image, (long) i * 1000, 1000, 0)) {
                        for (final VpxImage decoded : decoder.decode(pkt)) {
                            assertEquals(width, decoded.width());
                            assertEquals(height, decoded.height());
                            assertEquals(VpxImage.VPX_IMG_FMT_I420, decoded.format());
                            assertEquals(frameSize, decoded.toByteArray().length);
                            totalDecoded++;
                        }
                    }
                }
            }

            // Flush the encoder in a loop until empty and decode any remaining buffered packets
            List<VpxPacket> flushed;
            do {
                flushed = encoder.flush();
                for (final VpxPacket pkt : flushed) {
                    for (final VpxImage decoded : decoder.decode(pkt)) {
                        assertEquals(width, decoded.width());
                        assertEquals(height, decoded.height());
                        totalDecoded++;
                    }
                }
            } while (!flushed.isEmpty());

            assertEquals(frameCount, totalDecoded, "Expected exactly frameCount decoded frames");
        }
    }

    /**
     * Explicitly exercises the {@code decode(byte[])} overload, which copies the input into a
     * temporary confined arena before calling the native decoder.
     */
    @Test
    void testDecodeByteArrayPath() {
        final int width = 320;
        final int height = 240;
        final int frameSize = width * height * 3 / 2;

        try (Vp9Encoder encoder =
                        new Vp9Encoder(
                                VpxEncoderConfig.builder(VpxEncoderConfig.Codec.VP9, width, height)
                                        .build());
                Vp9Decoder decoder = new Vp9Decoder(new VpxDecoderConfig());
                VpxImage image = VpxImage.fromByteArray(new byte[frameSize], width, height)) {

            int decoded = 0;
            // Encode with FORCE_KF and immediately decode via byte[] overload
            for (final VpxPacket pkt :
                    encoder.encode(image, 0, 1000, AbstractVpxEncoder.VPX_EFLAG_FORCE_KF)) {
                // Use pkt.toByteArray() to explicitly invoke the byte[] decode path
                for (final VpxImage img : decoder.decode(pkt.toByteArray())) {
                    assertEquals(width, img.width());
                    assertEquals(height, img.height());
                    decoded++;
                }
            }

            // Flush encoder in a loop until empty and decode any remaining packets via byte[] path
            List<VpxPacket> flushed;
            do {
                flushed = encoder.flush();
                for (final VpxPacket pkt : flushed) {
                    for (final VpxImage img : decoder.decode(pkt.toByteArray())) {
                        assertEquals(width, img.width());
                        assertEquals(height, img.height());
                        decoded++;
                    }
                }
            } while (!flushed.isEmpty());

            assertEquals(1, decoded, "byte[] decode path must produce exactly 1 decoded frame");
        }
    }

    /**
     * Verifies that {@link VpxImage#getPlane(int)} and {@link VpxImage#getStride(int)} return
     * sensible values for a decoded frame. Stride must be at least as large as the plane width, and
     * the buffer capacity must equal {@code stride * planeRows}.
     */
    @Test
    void testDecodedFramePlanesAreAccessible() {
        final int width = 320;
        final int height = 240;
        final int frameSize = width * height * 3 / 2;
        final int uvDim = (width + 1) / 2;
        final int uvHeight = (height + 1) / 2;

        try (Vp9Encoder encoder =
                        new Vp9Encoder(
                                VpxEncoderConfig.builder(VpxEncoderConfig.Codec.VP9, width, height)
                                        .build());
                Vp9Decoder decoder = new Vp9Decoder(new VpxDecoderConfig());
                VpxImage image = VpxImage.fromByteArray(new byte[frameSize], width, height)) {

            int validatedFrames = 0;
            for (final VpxPacket pkt :
                    encoder.encode(image, 0, 1000, AbstractVpxEncoder.VPX_EFLAG_FORCE_KF)) {
                for (final VpxImage decoded : decoder.decode(pkt)) {
                    // Strides must cover at least the plane width
                    assertTrue(decoded.getStride(0) >= width, "Y stride >= width");
                    assertTrue(decoded.getStride(1) >= uvDim, "U stride >= (width+1)/2");
                    assertTrue(decoded.getStride(2) >= uvDim, "V stride >= (width+1)/2");

                    // Plane buffer capacity must equal stride * plane-rows
                    final ByteBuffer yBuf = decoded.getPlane(0);
                    assertEquals(
                            decoded.getStride(0) * height,
                            yBuf.capacity(),
                            "Y plane capacity == stride * height");

                    final ByteBuffer uBuf = decoded.getPlane(1);
                    assertEquals(
                            decoded.getStride(1) * uvHeight,
                            uBuf.capacity(),
                            "U plane capacity == stride * uv-height");

                    final ByteBuffer vBuf = decoded.getPlane(2);
                    assertEquals(
                            decoded.getStride(2) * uvHeight,
                            vBuf.capacity(),
                            "V plane capacity == stride * uv-height");

                    validatedFrames++;
                }
            }

            // Flush encoder in a loop until empty and validate any remaining decoded frames
            List<VpxPacket> flushed;
            do {
                flushed = encoder.flush();
                for (final VpxPacket pkt : flushed) {
                    for (final VpxImage decoded : decoder.decode(pkt)) {
                        assertTrue(decoded.getStride(0) >= width, "Y stride >= width");
                        assertTrue(decoded.getStride(1) >= uvDim, "U stride >= (width+1)/2");
                        assertTrue(decoded.getStride(2) >= uvDim, "V stride >= (width+1)/2");

                        final ByteBuffer yBuf = decoded.getPlane(0);
                        assertEquals(
                                decoded.getStride(0) * height,
                                yBuf.capacity(),
                                "Y plane capacity == stride * height");

                        final ByteBuffer uBuf = decoded.getPlane(1);
                        assertEquals(
                                decoded.getStride(1) * uvHeight,
                                uBuf.capacity(),
                                "U plane capacity == stride * uv-height");

                        final ByteBuffer vBuf = decoded.getPlane(2);
                        assertEquals(
                                decoded.getStride(2) * uvHeight,
                                vBuf.capacity(),
                                "V plane capacity == stride * uv-height");

                        validatedFrames++;
                    }
                }
            } while (!flushed.isEmpty());

            assertEquals(1, validatedFrames, "Expected exactly 1 decoded frame to be validated");
        }
    }

    /**
     * Verifies that {@link VpxImage#getStride(int)} and {@link VpxImage#getPlane(int)} reject
     * out-of-range plane indices with an {@link IllegalArgumentException}.
     */
    @Test
    void testInvalidPlaneIndexThrowsException() {
        try (VpxImage image = VpxImage.fromByteArray(new byte[320 * 240 * 3 / 2], 320, 240)) {
            assertThrows(IllegalArgumentException.class, () -> image.getStride(-1));
            assertThrows(IllegalArgumentException.class, () -> image.getStride(3));
            assertThrows(IllegalArgumentException.class, () -> image.getPlane(-1));
            assertThrows(IllegalArgumentException.class, () -> image.getPlane(3));
        }
    }

    /**
     * Verifies that the decoder can be created on one thread and its {@code decode()} method can be
     * called from a different thread. This exercises the {@code Arena.ofShared()} contract, which
     * removes thread affinity from the long-lived codec context.
     */
    @Test
    void testDecoderCrossThreadUsage() throws InterruptedException {
        final int width = 320;
        final int height = 240;
        final int frameSize = width * height * 3 / 2;

        // Encode on the main thread and copy packet data to heap byte arrays before
        // the encoder (and its internal memory) is closed.
        final List<byte[]> encodedData = new ArrayList<>();
        try (Vp9Encoder encoder =
                        new Vp9Encoder(
                                VpxEncoderConfig.builder(VpxEncoderConfig.Codec.VP9, width, height)
                                        .build());
                VpxImage image = VpxImage.fromByteArray(new byte[frameSize], width, height)) {
            for (final VpxPacket pkt :
                    encoder.encode(image, 0, 1000, AbstractVpxEncoder.VPX_EFLAG_FORCE_KF)) {
                encodedData.add(pkt.toByteArray());
            }
            // Flush encoder in a loop until empty, copying data immediately
            List<VpxPacket> flushed;
            do {
                flushed = encoder.flush();
                for (final VpxPacket pkt : flushed) {
                    encodedData.add(pkt.toByteArray());
                }
            } while (!flushed.isEmpty());
        }
        assertFalse(encodedData.isEmpty(), "Encoder must produce at least one packet");

        // Create decoder on the main thread; decode() runs on a worker thread.
        try (Vp9Decoder decoder = new Vp9Decoder(new VpxDecoderConfig())) {
            final int[] decodedCount = {0};
            final Throwable[] threadError = {null};

            final Thread worker =
                    new Thread(
                            () -> {
                                try {
                                    for (final byte[] data : encodedData) {
                                        decodedCount[0] += decoder.decode(data).size();
                                    }
                                } catch (Throwable t) {
                                    threadError[0] = t;
                                }
                            });

            worker.start();
            worker.join();

            final Throwable error = threadError[0];
            if (error != null) {
                throw new AssertionError("Decoder threw on worker thread", error);
            }
            assertEquals(1, decodedCount[0], "Worker thread must decode exactly 1 frame");
        }
    }
}
