package org.seuffert.panvpx.vp8;

public record VpxEncoderConfig(
        int width,
        int height,
        int targetBitrateKbps,
        int frameDropThreshold,
        int threads,
        int timebaseNumerator,
        int timebaseDenominator
) {
    public VpxEncoderConfig(int width, int height) {
        this(width, height, 256, 0, 1, 1, 1000);
    }
    
    public VpxEncoderConfig(int width, int height, int targetBitrateKbps, int threads) {
        this(width, height, targetBitrateKbps, 0, threads, 1, 1000);
    }
}
