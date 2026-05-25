package org.seuffert.panvpx.vp8;

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
import org.seuffert.panvpx.core.VpxException;
import org.seuffert.panvpx.core.VpxImage;
import org.seuffert.panvpx.core.VpxPacket;

class Vp8DecoderTest {

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

        final VpxEncoderConfig encConfig = new VpxEncoderConfig(width, height);
        final VpxDecoderConfig decConfig = new VpxDecoderConfig();

        int framesDecoded = 0;

        try (Vp8Encoder encoder = new Vp8Encoder(encConfig);
                Vp8Decoder decoder = new Vp8Decoder(decConfig);
                VpxImage image = VpxImage.fromByteArray(dummyData, width, height)) {

            final List<VpxPacket> packets =
                    encoder.encode(image, 0, 1000, Vp8Encoder.VPX_EFLAG_FORCE_KF);
            assertFalse(packets.isEmpty(), "Encoder must produce at least one packet");

            for (final VpxPacket pkt : packets) {
                final List<VpxImage> decodedImages = decoder.decode(pkt);
                framesDecoded += decodedImages.size();

                for (final VpxImage decodedImg : decodedImages) {
                    assertEquals(width, decodedImg.width(), "Decoded width must match");
                    assertEquals(height, decodedImg.height(), "Decoded height must match");
                    assertEquals(
                            VpxImage.VPX_IMG_FMT_I420,
                            decodedImg.format(),
                            "Decoded format must match");

                    final byte[] outData = decodedImg.toByteArray();
                    assertEquals(
                            frameSize,
                            outData.length,
                            "Decoded byte array size must match I420 size");
                }
            }
        }

        assertTrue(framesDecoded > 0, "Decoder must have produced at least one frame");
    }

    @Test
    void testInvalidDataThrowsException() {
        final byte[] badData = {(byte) 0x00, (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04};

        try (Vp8Decoder decoder = new Vp8Decoder(new VpxDecoderConfig())) {
            assertThrows(
                    VpxException.class,
                    () -> decoder.decode(badData),
                    "Should throw exception on invalid bitstream data");
        }
    }

    @Test
    void testDecoderUseAfterCloseThrowsException() {
        final Vp8Decoder decoder = new Vp8Decoder(new VpxDecoderConfig());
        decoder.close();

        final byte[] badData = {(byte) 0x00};
        assertThrows(
                IllegalStateException.class,
                () -> decoder.decode(badData),
                "Should throw IllegalStateException when using closed arena");
    }

    @Test
    void testDoubleCloseIsIdempotent() {
        final Vp8Decoder decoder = new Vp8Decoder(new VpxDecoderConfig());

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

        try (Vp8Encoder encoder = new Vp8Encoder(new VpxEncoderConfig(width, height));
                Vp8Decoder decoder = new Vp8Decoder(new VpxDecoderConfig())) {
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

            // Flush the encoder and decode any remaining buffered packets
            for (final VpxPacket pkt : encoder.flush()) {
                for (final VpxImage decoded : decoder.decode(pkt)) {
                    assertEquals(width, decoded.width());
                    assertEquals(height, decoded.height());
                    totalDecoded++;
                }
            }

            assertTrue(totalDecoded > 0, "At least one frame must be decoded from the stream");
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

        try (Vp8Encoder encoder = new Vp8Encoder(new VpxEncoderConfig(width, height));
                Vp8Decoder decoder = new Vp8Decoder(new VpxDecoderConfig());
                VpxImage image = VpxImage.fromByteArray(new byte[frameSize], width, height)) {

            final List<VpxPacket> packets =
                    encoder.encode(image, 0, 1000, Vp8Encoder.VPX_EFLAG_FORCE_KF);
            assertFalse(packets.isEmpty(), "Encoder must produce at least one packet");

            int decoded = 0;
            for (final VpxPacket pkt : packets) {
                // Use pkt.toByteArray() to explicitly invoke the byte[] decode path
                for (final VpxImage img : decoder.decode(pkt.toByteArray())) {
                    assertEquals(width, img.width());
                    assertEquals(height, img.height());
                    decoded++;
                }
            }

            assertTrue(decoded > 0, "byte[] decode path must produce at least one frame");
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

        try (Vp8Encoder encoder = new Vp8Encoder(new VpxEncoderConfig(width, height));
                Vp8Decoder decoder = new Vp8Decoder(new VpxDecoderConfig());
                VpxImage image = VpxImage.fromByteArray(new byte[frameSize], width, height)) {

            final List<VpxPacket> packets =
                    encoder.encode(image, 0, 1000, Vp8Encoder.VPX_EFLAG_FORCE_KF);

            int validatedFrames = 0;
            for (final VpxPacket pkt : packets) {
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

            assertTrue(validatedFrames > 0, "At least one frame must be validated");
        }
    }

    /**
     * Verifies that {@link VpxImage#getStride(int)} and {@link VpxImage#getPlane(int)} reject
     * out-of-range plane indices with an {@link IllegalArgumentException}. This guards the bounds
     * fix that changed the valid range from [0, 3] to [0, 2].
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
        try (Vp8Encoder encoder = new Vp8Encoder(new VpxEncoderConfig(width, height));
                VpxImage image = VpxImage.fromByteArray(new byte[frameSize], width, height)) {
            for (final VpxPacket pkt :
                    encoder.encode(image, 0, 1000, Vp8Encoder.VPX_EFLAG_FORCE_KF)) {
                encodedData.add(pkt.toByteArray());
            }
        }
        assertFalse(encodedData.isEmpty(), "Encoder must produce at least one packet");

        // Create decoder on the main thread; decode() runs on a worker thread.
        try (Vp8Decoder decoder = new Vp8Decoder(new VpxDecoderConfig())) {
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
            assertTrue(decodedCount[0] > 0, "Worker thread must decode at least one frame");
        }
    }
}
