package org.seuffert.panvpx.core;

/**
 * Configuration options for a VPX Encoder (VP8 or VP9).
 *
 * <p>This immutable record contains all necessary parameters to initialize a video encoder,
 * balancing quality, speed, and target bitrate.
 *
 * <p>For anything beyond a simple width/height or bitrate/threads setup, use the fluent {@link
 * Builder} returned by {@link #builder(int, int)}:
 *
 * <pre>{@code
 * VpxEncoderConfig config = VpxEncoderConfig.builder(640, 480)
 *         .targetBitrateKbps(1500)
 *         .rateControlMode(VpxEncoderConfig.RateControlMode.CBR)
 *         .threads(4)
 *         .maxKeyframeDistance(60)
 *         .errorResilient(true)
 *         .build();
 * try (Vp8Encoder encoder = new Vp8Encoder(config)) {
 *     // Encode frames...
 * }
 * }</pre>
 *
 * <p>Simple cases are still covered by the convenience constructors:
 *
 * <pre>{@code
 * // 720p video at 1.5 Mbps, using 4 threads
 * VpxEncoderConfig config = new VpxEncoderConfig(1280, 720, 1500, 4);
 * }</pre>
 *
 * @param width The width of the video frame in pixels.
 * @param height The height of the video frame in pixels.
 * @param targetBitrateKbps The target bitrate in kilobits per second.
 * @param frameDropThreshold Controls frame dropping to hit the target bitrate. {@code 0} disables
 *     frame dropping entirely. Values {@code 1}&ndash;{@code 100} represent the percentage of the
 *     data-rate undershoot below which a frame may be dropped; e.g. {@code 30} allows the encoder
 *     to drop frames when it is more than 30&nbsp;% below the target rate.
 * @param threads The number of threads to use for encoding.
 * @param timebaseNumerator The numerator of the timebase fraction. Together with {@link
 *     #timebaseDenominator} this defines the unit for the {@code pts} and {@code duration}
 *     arguments passed to {@link org.seuffert.panvpx.core.AbstractVpxEncoder#encode encode()}. For
 *     example, {@code 1/1000} means timestamps are in milliseconds; {@code 1/90000} matches the
 *     standard MPEG 90&nbsp;kHz clock.
 * @param timebaseDenominator The denominator of the timebase fraction. The default value of {@code
 *     1000} gives millisecond-resolution timestamps.
 * @param deadline The per-frame encoding deadline in microseconds. Use one of the {@code
 *     DEADLINE_*} constants: {@link #DEADLINE_REALTIME} for low-latency streaming or {@link
 *     #DEADLINE_GOOD_QUALITY} for throughput benchmarks and general use.
 * @param cpuUsed CPU usage / speed control. The valid range and effect differ by codec and
 *     deadline:
 *     <ul>
 *       <li><strong>VP8</strong>: 0–16. {@code 0} = best quality (slowest); {@code 16} = fastest
 *           encoding. The default is {@code 0}.
 *       <li><strong>VP9 with {@link #DEADLINE_GOOD_QUALITY}</strong>: 0–5. {@code 0} = highest
 *           quality (slowest); {@code 5} = fastest within the deadline. The default is {@code 0}.
 *       <li><strong>VP9 with {@link #DEADLINE_REALTIME}</strong>: 5–8. Values below 5 are not
 *           recommended for real-time use. {@code 5} = best realtime quality; {@code 8} = fastest.
 *     </ul>
 *
 * @param rowMt Enable row-based multithreading (VP9 only). When {@code true} and {@code threads >
 *     1}, libvpx encodes rows in parallel, significantly improving throughput.
 * @param tileColumns Number of VP9 tile columns (VP9 only). Pass the actual column count: {@code 1}
 *     (or {@code 0} for codec default), {@code 2}, {@code 4}, {@code 8}, {@code 16}, {@code 32}, or
 *     {@code 64}. More tile columns improve multi-core utilisation but may slightly reduce
 *     compression efficiency. Values other than these powers of two are invalid.
 * @param tokenPartitions Number of VP8 token partitions / slices (VP8 only). Pass the actual
 *     partition count: {@code 1} (or {@code 0} for codec default), {@code 2}, {@code 4}, or {@code
 *     8}. More partitions allow parallel decoding of a single frame but may slightly reduce
 *     compression efficiency. Values other than these four are invalid.
 * @param rateControlMode The rate-control algorithm. {@link RateControlMode#VBR} (variable bitrate)
 *     targets the bitrate on average; {@link RateControlMode#CBR} (constant bitrate) attempts to
 *     hit it every frame. Default is {@link RateControlMode#VBR}.
 * @param maxKeyframeDistance Maximum distance between automatically placed key frames ({@code
 *     g_lag_in_frames} must be {@code 0} for keyframe placement to take effect immediately). {@code
 *     0} leaves the codec default unchanged (typically unlimited). The legacy JNI code used a
 *     typical value of {@code 60}.
 * @param keyframeMode Whether the encoder places key frames automatically ({@link
 *     KeyframeMode#AUTO}) or only on demand via {@link AbstractVpxEncoder#VPX_EFLAG_FORCE_KF}
 *     ({@link KeyframeMode#DISABLED}). Default is {@link KeyframeMode#AUTO}.
 * @param profile Codec profile. For VP8 this is {@code 0}–{@code 3} (typically {@code 0}); for VP9
 *     this controls colour-space/bit-depth handling. Default is {@code 0}.
 * @param usage Encoder usage hint ({@code g_usage}). {@code 0} is general-purpose encoding;
 *     codec-specific values (e.g.&nbsp;{@code 1} for VP8 real-time) select alternative init tables.
 *     Default is {@code 0}.
 * @param errorResilient Enable error-resilient encoding ({@code g_error_resilient}). Produces a
 *     stream that can be partially decoded in the presence of packet loss at the cost of slightly
 *     reduced compression efficiency. Default is {@code false}.
 * @param lagInFrames Number of frames the encoder may buffer before emitting output ({@code
 *     g_lag_in_frames}). {@code 0} disables lookahead (required for real-time use). VP9 typically
 *     benefits from values of {@code 25} for offline encoding. Default is {@code 0}.
 * @param maxQuantizer Maximum quantiser index ({@code rc_max_quantizer}). Higher values allow more
 *     compression at lower quality. Range: {@code 0}–{@code 63}; default is {@code 63} (libvpx
 *     default). The legacy JNI code used {@code 54}.
 * @param minQuantizer Minimum quantiser index ({@code rc_min_quantizer}). Lower values produce
 *     higher quality at the cost of bitrate. Range: {@code 0}–{@code 63}; default is {@code 0}.
 */
public record VpxEncoderConfig(
        int width,
        int height,
        int targetBitrateKbps,
        int frameDropThreshold,
        int threads,
        int timebaseNumerator,
        int timebaseDenominator,
        long deadline,
        int cpuUsed,
        boolean rowMt,
        int tileColumns,
        int tokenPartitions,
        RateControlMode rateControlMode,
        int maxKeyframeDistance,
        KeyframeMode keyframeMode,
        int profile,
        int usage,
        boolean errorResilient,
        int lagInFrames,
        int maxQuantizer,
        int minQuantizer) {

    /** Real-time deadline (1 µs): fastest encoding, lowest quality. */
    public static final long DEADLINE_REALTIME = 1L;

    /** Good-quality deadline (1 s): balanced quality and speed; the default. */
    public static final long DEADLINE_GOOD_QUALITY = 1_000_000L;

    /** Best-quality deadline (0 = unlimited): highest quality, slowest encoding. */
    public static final long DEADLINE_BEST_QUALITY = 0L;

    /**
     * Rate-control algorithm for the encoder.
     *
     * <p>Corresponds to the native {@code vpx_rc_mode} enum ({@code rc_end_usage}).
     */
    public enum RateControlMode {
        /**
         * Variable bitrate. The encoder targets the configured bitrate on average across frames,
         * allowing individual frames to use more or fewer bits as needed for quality. This is the
         * libvpx default and is suitable for most use cases.
         */
        VBR,

        /**
         * Constant bitrate. The encoder attempts to hit the configured bitrate every frame.
         * Preferred for live-streaming and WebRTC scenarios where a predictable bitrate is
         * required.
         */
        CBR,

        /**
         * Constrained quality. Encodes at the best possible quality subject to the configured
         * target bitrate as an upper bound. Requires VP9; not recommended for VP8.
         */
        CQ,

        /**
         * Constant quality (no bitrate target). Encodes at a fixed quantiser level. Requires VP9;
         * not recommended for VP8.
         */
        Q
    }

    /**
     * Controls how the encoder places key frames in the output stream.
     *
     * <p>Corresponds to the native {@code vpx_kf_mode} enum ({@code kf_mode}).
     */
    public enum KeyframeMode {
        /**
         * Automatic key-frame placement. The encoder inserts key frames at scene cuts and at
         * regular intervals according to {@link VpxEncoderConfig#maxKeyframeDistance}. This is the
         * libvpx default and is suitable for most use cases.
         */
        AUTO,

        /**
         * Disabled automatic key-frame placement. Key frames are only produced when explicitly
         * requested via {@link AbstractVpxEncoder#VPX_EFLAG_FORCE_KF}. Use this when the
         * application controls the key-frame schedule entirely (e.g. for adaptive-bitrate
         * segmenting).
         */
        DISABLED
    }

    /**
     * Creates a new {@link Builder} pre-populated with all defaults and requiring only the frame
     * dimensions.
     *
     * @param width The frame width in pixels.
     * @param height The frame height in pixels.
     * @return a new {@link Builder} instance.
     */
    public static Builder builder(final int width, final int height) {
        return new Builder(width, height);
    }

    /**
     * Constructs a basic encoder configuration using reasonable defaults. Target bitrate is
     * 256&nbsp;kbps, single threaded, with a 1/1000&nbsp;ms timebase and real-time deadline.
     *
     * @param width The width of the video frame.
     * @param height The height of the video frame.
     */
    public VpxEncoderConfig(final int width, final int height) {
        this(
                width,
                height,
                256,
                0,
                1,
                1,
                1000,
                DEADLINE_REALTIME,
                0,
                false,
                0,
                0,
                RateControlMode.VBR,
                0,
                KeyframeMode.AUTO,
                0,
                0,
                false,
                0,
                63,
                0);
    }

    /**
     * Constructs a basic encoder configuration specifying bitrate and threading. Uses the real-time
     * encoding deadline.
     *
     * @param width The width of the video frame.
     * @param height The height of the video frame.
     * @param targetBitrateKbps The target bitrate in kilobits per second.
     * @param threads The number of threads to use.
     */
    public VpxEncoderConfig(
            final int width, final int height, final int targetBitrateKbps, final int threads) {
        this(
                width,
                height,
                targetBitrateKbps,
                0,
                threads,
                1,
                1000,
                DEADLINE_REALTIME,
                0,
                false,
                0,
                0,
                RateControlMode.VBR,
                0,
                KeyframeMode.AUTO,
                0,
                0,
                false,
                0,
                63,
                0);
    }

    /**
     * Constructs a basic encoder configuration specifying bitrate, threading, and encoding
     * deadline.
     *
     * @param width The width of the video frame.
     * @param height The height of the video frame.
     * @param targetBitrateKbps The target bitrate in kilobits per second.
     * @param threads The number of threads to use.
     * @param deadline The per-frame encoding deadline in microseconds (see {@link
     *     #DEADLINE_REALTIME}, {@link #DEADLINE_GOOD_QUALITY}).
     */
    public VpxEncoderConfig(
            final int width,
            final int height,
            final int targetBitrateKbps,
            final int threads,
            final long deadline) {
        this(
                width,
                height,
                targetBitrateKbps,
                0,
                threads,
                1,
                1000,
                deadline,
                0,
                false,
                0,
                0,
                RateControlMode.VBR,
                0,
                KeyframeMode.AUTO,
                0,
                0,
                false,
                0,
                63,
                0);
    }

    /**
     * Fluent builder for {@link VpxEncoderConfig}.
     *
     * <p>All fields default to the same values used by the convenience constructors. Only {@code
     * width} and {@code height} are required.
     *
     * <p>Example:
     *
     * <pre>{@code
     * VpxEncoderConfig config = VpxEncoderConfig.builder(640, 480)
     *         .targetBitrateKbps(1500)
     *         .rateControlMode(RateControlMode.CBR)
     *         .threads(4)
     *         .maxKeyframeDistance(60)
     *         .errorResilient(true)
     *         .build();
     * }</pre>
     */
    public static final class Builder {

        private final int width;
        private final int height;
        private int targetBitrateKbps = 256;
        private int frameDropThreshold;
        private int threads = 1;
        private int timebaseNumerator = 1;
        private int timebaseDenominator = 1000;
        private long deadline = DEADLINE_REALTIME;
        private int cpuUsed;
        private boolean rowMt;
        private int tileColumns;
        private int tokenPartitions;
        private RateControlMode rateControlMode = RateControlMode.VBR;
        private int maxKeyframeDistance;
        private KeyframeMode keyframeMode = KeyframeMode.AUTO;
        private int profile;
        private int usage;
        private boolean errorResilient;
        private int lagInFrames;
        private int maxQuantizer = 63;
        private int minQuantizer;

        /**
         * Creates a builder with the required frame dimensions. All other fields default to the
         * same values used by {@link VpxEncoderConfig#VpxEncoderConfig(int, int)}.
         *
         * @param width The frame width in pixels.
         * @param height The frame height in pixels.
         */
        public Builder(final int width, final int height) {
            this.width = width;
            this.height = height;
        }

        /**
         * Sets the target bitrate in kilobits per second. Default: {@code 256}.
         *
         * @param value target bitrate in kbps.
         * @return this builder.
         */
        public Builder targetBitrateKbps(final int value) {
            this.targetBitrateKbps = value;
            return this;
        }

        /**
         * Sets the frame-drop threshold. {@code 0} disables frame dropping; {@code 1}–{@code 100}
         * is the under-shoot percentage at which the encoder may drop frames. Default: {@code 0}.
         *
         * @param value frame-drop threshold.
         * @return this builder.
         */
        public Builder frameDropThreshold(final int value) {
            this.frameDropThreshold = value;
            return this;
        }

        /**
         * Sets the number of encoding threads. Default: {@code 1}.
         *
         * @param value thread count.
         * @return this builder.
         */
        public Builder threads(final int value) {
            this.threads = value;
            return this;
        }

        /**
         * Sets the timebase numerator. Default: {@code 1}.
         *
         * @param value timebase numerator.
         * @return this builder.
         */
        public Builder timebaseNumerator(final int value) {
            this.timebaseNumerator = value;
            return this;
        }

        /**
         * Sets the timebase denominator. Default: {@code 1000} (millisecond timestamps).
         *
         * @param value timebase denominator.
         * @return this builder.
         */
        public Builder timebaseDenominator(final int value) {
            this.timebaseDenominator = value;
            return this;
        }

        /**
         * Sets the per-frame encoding deadline in microseconds. Use {@link
         * VpxEncoderConfig#DEADLINE_REALTIME} or {@link VpxEncoderConfig#DEADLINE_GOOD_QUALITY}.
         * Default: {@link VpxEncoderConfig#DEADLINE_REALTIME}.
         *
         * @param value deadline in microseconds.
         * @return this builder.
         */
        public Builder deadline(final long value) {
            this.deadline = value;
            return this;
        }

        /**
         * Sets the CPU-usage / speed control ({@code VP8E_SET_CPUUSED}). For VP8 the range is 0–16;
         * for VP9 0–8 depending on the deadline. Default: {@code 0}.
         *
         * @param value cpu-used value.
         * @return this builder.
         */
        public Builder cpuUsed(final int value) {
            this.cpuUsed = value;
            return this;
        }

        /**
         * Enables or disables row-based multithreading (VP9 only). Default: {@code false}.
         *
         * @param value {@code true} to enable row-MT.
         * @return this builder.
         */
        public Builder rowMt(final boolean value) {
            this.rowMt = value;
            return this;
        }

        /**
         * Sets the number of VP9 tile columns. Pass the actual column count (power of two up to
         * 64). {@code 0} or {@code 1} leave the codec default. Default: {@code 0}.
         *
         * @param value tile-column count.
         * @return this builder.
         */
        public Builder tileColumns(final int value) {
            this.tileColumns = value;
            return this;
        }

        /**
         * Sets the number of VP8 token partitions. Pass the actual partition count ({@code 1},
         * {@code 2}, {@code 4}, or {@code 8}). {@code 0} or {@code 1} leave the codec default.
         * Default: {@code 0}.
         *
         * @param value token-partition count.
         * @return this builder.
         */
        public Builder tokenPartitions(final int value) {
            this.tokenPartitions = value;
            return this;
        }

        /**
         * Sets the rate-control mode. Default: {@link RateControlMode#VBR}.
         *
         * @param value the {@link RateControlMode} to use.
         * @return this builder.
         */
        public Builder rateControlMode(final RateControlMode value) {
            this.rateControlMode = value;
            return this;
        }

        /**
         * Sets the maximum distance between automatically placed key frames. {@code 0} leaves the
         * codec default unchanged (typically unlimited). Default: {@code 0}.
         *
         * @param value max key-frame distance in frames.
         * @return this builder.
         */
        public Builder maxKeyframeDistance(final int value) {
            this.maxKeyframeDistance = value;
            return this;
        }

        /**
         * Sets the key-frame placement mode. Default: {@link KeyframeMode#AUTO}.
         *
         * @param value the {@link KeyframeMode} to use.
         * @return this builder.
         */
        public Builder keyframeMode(final KeyframeMode value) {
            this.keyframeMode = value;
            return this;
        }

        /**
         * Sets the codec profile. For VP8: {@code 0}–{@code 3} (typically {@code 0}). For VP9:
         * controls colour-space/bit-depth handling. Default: {@code 0}.
         *
         * @param value codec profile index.
         * @return this builder.
         */
        public Builder profile(final int value) {
            this.profile = value;
            return this;
        }

        /**
         * Sets the encoder usage hint ({@code g_usage}). {@code 0} is general-purpose encoding;
         * {@code 1} selects the real-time preset for VP8. Default: {@code 0}.
         *
         * @param value usage hint value.
         * @return this builder.
         */
        public Builder usage(final int value) {
            this.usage = value;
            return this;
        }

        /**
         * Enables or disables error-resilient encoding. When {@code true} the stream can be
         * partially decoded in the presence of packet loss. Default: {@code false}.
         *
         * @param value {@code true} to enable error resilience.
         * @return this builder.
         */
        public Builder errorResilient(final boolean value) {
            this.errorResilient = value;
            return this;
        }

        /**
         * Sets the lookahead depth in frames ({@code g_lag_in_frames}). {@code 0} disables
         * lookahead and is required for real-time streaming. Default: {@code 0}.
         *
         * @param value lag-in-frames value.
         * @return this builder.
         */
        public Builder lagInFrames(final int value) {
            this.lagInFrames = value;
            return this;
        }

        /**
         * Sets the maximum quantiser index ({@code rc_max_quantizer}). Range: {@code 0}–{@code 63}.
         * Higher values allow more compression at lower quality. Default: {@code 63} (libvpx
         * default). The legacy JNI encoder used {@code 54}.
         *
         * @param value maximum quantiser index.
         * @return this builder.
         */
        public Builder maxQuantizer(final int value) {
            this.maxQuantizer = value;
            return this;
        }

        /**
         * Sets the minimum quantiser index ({@code rc_min_quantizer}). Range: {@code 0}–{@code 63}.
         * Lower values produce higher quality at the cost of bitrate. Default: {@code 0}.
         *
         * @param value minimum quantiser index.
         * @return this builder.
         */
        public Builder minQuantizer(final int value) {
            this.minQuantizer = value;
            return this;
        }

        /**
         * Builds and returns the immutable {@link VpxEncoderConfig}.
         *
         * @return a new {@link VpxEncoderConfig} reflecting all settings applied to this builder.
         */
        public VpxEncoderConfig build() {
            return new VpxEncoderConfig(
                    width,
                    height,
                    targetBitrateKbps,
                    frameDropThreshold,
                    threads,
                    timebaseNumerator,
                    timebaseDenominator,
                    deadline,
                    cpuUsed,
                    rowMt,
                    tileColumns,
                    tokenPartitions,
                    rateControlMode,
                    maxKeyframeDistance,
                    keyframeMode,
                    profile,
                    usage,
                    errorResilient,
                    lagInFrames,
                    maxQuantizer,
                    minQuantizer);
        }
    }
}
