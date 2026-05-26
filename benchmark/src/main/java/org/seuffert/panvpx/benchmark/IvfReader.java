package org.seuffert.panvpx.benchmark;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads an IVF container and exposes the encoded video packets as byte arrays.
 *
 * <p>IVF format:
 *
 * <ul>
 *   <li>32-byte file header: magic {@code DKIF}, version (2 bytes LE), header length (2 bytes LE),
 *       FourCC (4 bytes), width (2 bytes LE), height (2 bytes LE), fps numerator (4 bytes LE), fps
 *       denominator (4 bytes LE), frame count (4 bytes LE), unused (4 bytes).
 *   <li>Per-frame: payload size (4 bytes LE u32), PTS (8 bytes LE u64), then the payload bytes.
 * </ul>
 */
public final class IvfReader {

    private static final int FILE_HEADER_SIZE = 32;
    private static final int FRAME_HEADER_SIZE = 12;
    private static final byte[] IVF_MAGIC = {'D', 'K', 'I', 'F'};

    private final int width;
    private final int height;
    private final int fpsNumerator;
    private final int fpsDenominator;
    private final List<byte[]> packets;

    /**
     * Parses an IVF file from the given input stream and reads all packets into memory.
     *
     * @param in stream positioned at byte 0 of the IVF file; the caller is responsible for closing
     *     it
     * @throws IOException if the stream cannot be read or the header is not a valid IVF file
     */
    public IvfReader(final InputStream in) throws IOException {
        final byte[] header = in.readNBytes(FILE_HEADER_SIZE);
        if (header.length < FILE_HEADER_SIZE) {
            throw new IOException("IVF file too short: expected 32-byte file header");
        }
        if (header[0] != IVF_MAGIC[0]
                || header[1] != IVF_MAGIC[1]
                || header[2] != IVF_MAGIC[2]
                || header[3] != IVF_MAGIC[3]) {
            throw new IOException("Not an IVF file: magic bytes do not match 'DKIF'");
        }
        this.width = readU16Le(header, 12);
        this.height = readU16Le(header, 14);
        this.fpsNumerator = readU32Le(header, 16);
        this.fpsDenominator = readU32Le(header, 20);
        final int declaredFrameCount = readU32Le(header, 24);
        final List<byte[]> pkts = new ArrayList<>(Math.max(declaredFrameCount, 16));
        final byte[] frameHeader = new byte[FRAME_HEADER_SIZE];
        while (true) {
            final int read = in.readNBytes(frameHeader, 0, FRAME_HEADER_SIZE);
            if (read == 0) {
                break;
            }
            if (read < FRAME_HEADER_SIZE) {
                throw new IOException("Truncated IVF frame header");
            }
            final int payloadSize = readU32Le(frameHeader, 0);
            // PTS is at bytes 4–11; not used for benchmarking.
            final byte[] payload = in.readNBytes(payloadSize);
            if (payload.length < payloadSize) {
                throw new IOException(
                        "Truncated IVF frame payload: expected "
                                + payloadSize
                                + " bytes, got "
                                + payload.length);
            }
            pkts.add(payload);
        }
        this.packets = List.copyOf(pkts);
    }

    /** Width in pixels, from the IVF file header. */
    public int width() {
        return width;
    }

    /** Height in pixels, from the IVF file header. */
    public int height() {
        return height;
    }

    /** FPS numerator from the IVF file header. */
    public int fpsNumerator() {
        return fpsNumerator;
    }

    /** FPS denominator from the IVF file header. */
    public int fpsDenominator() {
        return fpsDenominator;
    }

    /** All encoded video packets from the IVF file, in presentation order. */
    public List<byte[]> packets() {
        return packets;
    }

    private static int readU16Le(final byte[] buf, final int off) {
        return (buf[off] & 0xFF) | ((buf[off + 1] & 0xFF) << 8);
    }

    private static int readU32Le(final byte[] buf, final int off) {
        return (buf[off] & 0xFF)
                | ((buf[off + 1] & 0xFF) << 8)
                | ((buf[off + 2] & 0xFF) << 16)
                | ((buf[off + 3] & 0xFF) << 24);
    }
}
