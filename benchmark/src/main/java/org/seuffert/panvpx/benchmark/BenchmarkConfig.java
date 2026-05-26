package org.seuffert.panvpx.benchmark;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import org.seuffert.panvpx.core.VpxEncoderConfig;

/** Immutable configuration for a benchmark run. */
public final class BenchmarkConfig {

    /** The codecs to benchmark. */
    public enum Codec {
        VP8,
        VP9,
        BOTH
    }

    /** The benchmark modes to run. */
    public enum Mode {
        ENCODER,
        DECODER
    }

    /** Controls the per-frame encoding deadline passed to libvpx. */
    public enum EncodingPreset {
        /** Prioritises quality and realistic throughput measurement (default). */
        QUALITY(VpxEncoderConfig.DEADLINE_GOOD_QUALITY),
        /** Prioritises throughput: fastest encoding, lower quality. */
        REALTIME(VpxEncoderConfig.DEADLINE_REALTIME);

        private final long deadline;

        EncodingPreset(final long deadline) {
            this.deadline = deadline;
        }

        /** Returns the libvpx deadline value (µs) for this preset. */
        public long deadline() {
            return deadline;
        }
    }

    private final int width;
    private final int height;
    private final int bitrateKbps;
    private final Codec codec;
    private final Set<Mode> modes;
    private final int warmupFrames;
    private final int measureFrames;
    private final int threads;
    private final EncodingPreset preset;
    private final int cpuUsed;
    private final boolean rowMt;
    private final int tileColumns;
    private final int tokenPartitions;
    private final Optional<Path> inputFile;
    private final boolean synthetic;

    private BenchmarkConfig(final Builder b) {
        this.width = b.width;
        this.height = b.height;
        this.bitrateKbps = b.bitrateKbps;
        this.codec = b.codec;
        this.modes = Set.copyOf(b.modes);
        this.warmupFrames = b.warmupFrames;
        this.measureFrames = b.measureFrames;
        this.threads = b.threads;
        this.preset = b.preset;
        this.cpuUsed = b.cpuUsed;
        this.rowMt = b.rowMt;
        this.tileColumns = b.tileColumns;
        this.tokenPartitions = b.tokenPartitions;
        this.inputFile = b.inputFile;
        this.synthetic = b.synthetic;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int bitrateKbps() {
        return bitrateKbps;
    }

    public Codec codec() {
        return codec;
    }

    public Set<Mode> modes() {
        return modes;
    }

    public int warmupFrames() {
        return warmupFrames;
    }

    public int measureFrames() {
        return measureFrames;
    }

    public Optional<Path> inputFile() {
        return inputFile;
    }

    public int threads() {
        return threads;
    }

    public EncodingPreset preset() {
        return preset;
    }

    public int cpuUsed() {
        return cpuUsed;
    }

    public boolean isRowMt() {
        return rowMt;
    }

    public int tileColumns() {
        return tileColumns;
    }

    public int tokenPartitions() {
        return tokenPartitions;
    }

    /** Returns {@code true} when synthetic frames should be used instead of a real clip. */
    public boolean isSynthetic() {
        return synthetic;
    }

    /** Returns a builder pre-populated with defaults: 1920x1080, 4000 kbps, VP8+VP9, all modes. */
    public static Builder defaults() {
        return new Builder();
    }

    /** Mutable builder for {@link BenchmarkConfig}. */
    public static final class Builder {
        private int width = 1920;
        private int height = 1080;
        private int bitrateKbps = 4000;
        private Codec codec = Codec.BOTH;
        private Set<Mode> modes = EnumSet.allOf(Mode.class);
        private int warmupFrames = 3;
        private int measureFrames = 25;
        private int threads = 1;
        private EncodingPreset preset = EncodingPreset.QUALITY;
        private int cpuUsed = 0;
        private boolean rowMt = false;
        private int tileColumns = 0;
        private int tokenPartitions = 0;
        private Optional<Path> inputFile = Optional.empty();
        private boolean synthetic = false;

        private Builder() {}

        public Builder width(final int v) {
            this.width = v;
            return this;
        }

        public Builder height(final int v) {
            this.height = v;
            return this;
        }

        public Builder bitrateKbps(final int v) {
            this.bitrateKbps = v;
            return this;
        }

        public Builder codec(final Codec v) {
            this.codec = v;
            return this;
        }

        public Builder modes(final Set<Mode> v) {
            this.modes = EnumSet.copyOf(v);
            return this;
        }

        public Builder warmupFrames(final int v) {
            this.warmupFrames = v;
            return this;
        }

        public Builder measureFrames(final int v) {
            this.measureFrames = v;
            return this;
        }

        public Builder threads(final int v) {
            this.threads = v;
            return this;
        }

        public Builder preset(final EncodingPreset v) {
            this.preset = v;
            return this;
        }

        public Builder cpuUsed(final int v) {
            this.cpuUsed = v;
            return this;
        }

        public Builder rowMt(final boolean v) {
            this.rowMt = v;
            return this;
        }

        public Builder tileColumns(final int v) {
            this.tileColumns = v;
            return this;
        }

        public Builder tokenPartitions(final int v) {
            this.tokenPartitions = v;
            return this;
        }

        public Builder inputFile(final Path v) {
            this.inputFile = Optional.of(v);
            return this;
        }

        public Builder synthetic(final boolean v) {
            this.synthetic = v;
            return this;
        }

        public BenchmarkConfig build() {
            return new BenchmarkConfig(this);
        }
    }
}
