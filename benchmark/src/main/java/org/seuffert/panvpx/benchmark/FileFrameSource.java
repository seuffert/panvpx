package org.seuffert.panvpx.benchmark;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.seuffert.panvpx.core.VpxImage;

/**
 * Reads raw I420 frames from a planar YUV file. When the file is exhausted it wraps around to the
 * beginning, so any clip length works — the benchmark will loop the content.
 */
public final class FileFrameSource implements FrameSource {

    private final int width;
    private final int height;
    private final int frameSize;
    private final byte[] buffer;
    private final java.io.InputStream stream;
    private final Path path;

    /**
     * Opens the given file and prepares to read I420 frames of the specified dimensions.
     *
     * @param path path to the raw I420 YUV file
     * @param width frame width in pixels
     * @param height frame height in pixels
     */
    public FileFrameSource(final Path path, final int width, final int height) {
        this.width = width;
        this.height = height;
        this.path = path;
        this.frameSize = width * height * 3 / 2;
        this.buffer = new byte[frameSize];
        try {
            this.stream = Files.newInputStream(path);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot open benchmark input: " + path, e);
        }
    }

    @Override
    public VpxImage next() {
        int read = 0;
        try {
            read = stream.readNBytes(buffer, 0, frameSize);
            if (read < frameSize) {
                // Wrap around: reopen and read the remainder from the start.
                stream.close();
                try (java.io.InputStream fresh = Files.newInputStream(path)) {
                    read += fresh.readNBytes(buffer, read, frameSize - read);
                }
                if (read < frameSize) {
                    throw new IllegalStateException(
                            "YUV file is smaller than a single frame ("
                                    + frameSize
                                    + " bytes needed)");
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Error reading benchmark input", e);
        }
        return VpxImage.fromByteArray(buffer, width, height);
    }

    @Override
    public void close() {
        try {
            stream.close();
        } catch (IOException e) {
            throw new UncheckedIOException("Error closing benchmark input", e);
        }
    }
}
