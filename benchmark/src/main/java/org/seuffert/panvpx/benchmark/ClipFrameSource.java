package org.seuffert.panvpx.benchmark;

import java.util.List;
import org.seuffert.panvpx.core.VpxImage;

/**
 * Provides raw I420 frames from a pre-decoded in-memory clip. When the list is exhausted the source
 * wraps around to index zero, so it can serve arbitrarily many frames regardless of clip length.
 */
public final class ClipFrameSource implements FrameSource {

    private final List<byte[]> frames;
    private final int width;
    private final int height;
    private int index;

    /**
     * Creates a looping frame source backed by a list of pre-decoded I420 frame byte arrays.
     *
     * @param frames raw I420 frame data; must not be empty
     * @param width frame width in pixels
     * @param height frame height in pixels
     */
    public ClipFrameSource(final List<byte[]> frames, final int width, final int height) {
        if (frames.isEmpty()) {
            throw new IllegalArgumentException("frames list must not be empty");
        }
        this.frames = frames;
        this.width = width;
        this.height = height;
    }

    @Override
    public VpxImage next() {
        final byte[] data = frames.get(index % frames.size());
        index++;
        return VpxImage.fromByteArray(data, width, height);
    }

    @Override
    public void close() {
        // No resources to release — frame data lives on the Java heap.
    }
}
