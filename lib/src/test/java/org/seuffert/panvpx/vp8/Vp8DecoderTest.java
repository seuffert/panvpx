package org.seuffert.panvpx.vp8;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
