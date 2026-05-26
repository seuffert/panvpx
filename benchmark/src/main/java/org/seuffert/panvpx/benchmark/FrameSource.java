package org.seuffert.panvpx.benchmark;

import org.seuffert.panvpx.core.VpxImage;

/** Provides raw I420 frames to benchmark loops. Implementations must be thread-safe-free. */
public interface FrameSource extends AutoCloseable {

    /** Returns the next frame. The caller is responsible for closing the returned image. */
    VpxImage next();

    @Override
    void close();
}
