package org.seuffert.panvpx.benchmark;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import org.seuffert.panvpx.core.VpxImage;

/**
 * Generates synthetic I420 frames in native memory without any heap allocation in the hot loop.
 *
 * <p>Luma values are the sum of three sawtooth waves moving in independent directions at different
 * speeds, a fine 4×4 texture layer, and per-pixel temporal noise:
 *
 * <ul>
 *   <li><b>W1</b>: horizontal wave, moves rightward at 5 px/frame (period 64 px).
 *   <li><b>W2</b>: vertical wave, moves upward at 3 px/frame (period 64 px).
 *   <li><b>W3</b>: diagonal wave, moves at 2 px/frame (period 64 px).
 *   <li><b>Detail</b>: 4×4 XOR texture that shifts each frame — exercises small-block DCT coding.
 *   <li><b>Noise</b>: per-pixel LCG hash — forces real residuals even after perfect MV
 *       compensation.
 * </ul>
 *
 * <p>Because the three waves travel in independent directions, no single motion vector can
 * compensate all three simultaneously — the encoder must code genuine inter-frame residuals across
 * the whole frame, not just at quadrant boundaries. The chroma planes carry two-wave colour
 * gradients (not neutral 128) so the encoder also has colour residuals to code on every frame.
 *
 * <p>The same backing {@link MemorySegment} is reused across calls via {@link
 * VpxImage#fromMemorySegment}, so the caller must close the returned {@link VpxImage} before
 * calling {@link #next()} again.
 */
public final class SyntheticFrameSource implements FrameSource {

    private static final int CHROMA_NEUTRAL = 128;
    private static final int BYTE_MASK = 0xFF;
    // LCG multiplicative constants for a fast per-pixel hash (noise layer).
    private static final int HASH_X = 1_664_525;
    private static final int HASH_Y = 22_695_477;
    private static final int HASH_F = 1_013_904_223;

    private final int width;
    private final int height;
    private final Arena arena;
    private final MemorySegment dataSegment;
    private int frameIndex;

    /**
     * Creates a synthetic frame source for the given resolution.
     *
     * @param width frame width in pixels
     * @param height frame height in pixels
     */
    public SyntheticFrameSource(final int width, final int height) {
        this.width = width;
        this.height = height;
        this.arena = Arena.ofShared();
        final int frameSize = width * height * 3 / 2;
        this.dataSegment = arena.allocate(frameSize);
    }

    @Override
    public VpxImage next() {
        // Luma plane — three independent waves + fine texture + temporal noise.
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // W1: horizontal wave, rightward (64 px period).
                final int w1 = (x + frameIndex * 5) & 0x3F;
                // W2: vertical wave, upward (64 px period).
                final int w2 = (y - frameIndex * 3) & 0x3F;
                // W3: diagonal wave (64 px period).
                final int w3 = ((x >> 1) + (y >> 1) + frameIndex * 2) & 0x3F;
                // 4×4 texture: XOR of cell indices shifted each frame (0–56).
                final int detail =
                        (((x >> 2) + frameIndex) & 0x7) ^ (((y >> 2) - frameIndex) & 0x7);
                // Per-pixel noise: top 5 bits of an LCG hash (0–31).
                final int noise = (x * HASH_X ^ y * HASH_Y ^ frameIndex * HASH_F) >>> 27;
                final int luma = (w1 + w2 + w3 + (detail << 2) + noise) & BYTE_MASK;
                dataSegment.setAtIndex(ValueLayout.JAVA_BYTE, (long) y * width + x, (byte) luma);
            }
        }

        // Chroma planes — two-wave colour gradients, range 96–159, centred on CHROMA_NEUTRAL.
        final int chromaOffset = width * height;
        final int chromaW = width / 2;
        final int chromaH = height / 2;
        final int chromaPlaneSize = chromaW * chromaH;
        for (int cy = 0; cy < chromaH; cy++) {
            for (int cx = 0; cx < chromaW; cx++) {
                // Cb: horizontal + vertical drift.
                final int cb = CHROMA_NEUTRAL - 32 + ((cx * 2 + frameIndex * 3) & 0x3F);
                // Cr: diagonal drift, opposite direction.
                final int cr = CHROMA_NEUTRAL - 32 + (((cx + cy) * 2 - frameIndex * 2) & 0x3F);
                final long idx = (long) cy * chromaW + cx;
                dataSegment.setAtIndex(
                        ValueLayout.JAVA_BYTE, chromaOffset + idx, (byte) (cb & BYTE_MASK));
                dataSegment.setAtIndex(
                        ValueLayout.JAVA_BYTE,
                        chromaOffset + chromaPlaneSize + idx,
                        (byte) (cr & BYTE_MASK));
            }
        }

        frameIndex++;
        return VpxImage.fromMemorySegment(dataSegment, width, height);
    }

    @Override
    public void close() {
        arena.close();
    }
}
