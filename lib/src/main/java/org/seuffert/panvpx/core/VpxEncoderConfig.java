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
 * @param width The width of the video frame in pixels.
 * @param height The height of the video frame in pixels.
 * @param targetBitrateKbps The target bitrate in kilobits per second.
 * @param frameDropThreshold Temporal resampling / frame-drop threshold ({@code
 *     rc_dropframe_thresh}). {@code 0} disables frame dropping entirely. Values {@code
 *     1}&ndash;{@code 100} define the notional decoder-buffer fullness level &mdash; as a
 *     percentage of the optimal buffer size &mdash; below which the encoder will start dropping
 *     frames to recoup space. Note: frame dropping via this threshold is only effective in CBR
 *     mode; in VBR mode the value has no effect.
 * @param threads The number of threads to use for encoding. A good starting point is the number of
 *     real CPU cores minus one. Note that with more than one thread, repeated encodes of the same
 *     input may not produce bit-identical output.
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
 *       <li><strong>VP8 with {@link #DEADLINE_GOOD_QUALITY}</strong>: effective range 0–5. {@code
 *           0} produces quality close to {@link #DEADLINE_BEST_QUALITY} at roughly twice the speed;
 *           values 1–2 trade further quality for speed; values 4–5 disable rate-distortion
 *           optimisation for maximum throughput. The default is {@code 0}.
 *       <li><strong>VP8 with {@link #DEADLINE_REALTIME}</strong>: 0–15. Controls the CPU
 *           utilisation target: approximately {@code (100*(16-cpuUsed)/16)}%. The default is {@code
 *           0}.
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
 *     compression efficiency. For small images the default ({@code 0} or {@code 1}) is recommended;
 *     for HD content, {@code 4} or {@code 8} partitions are commonly used. Values other than these
 *     four are invalid.
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
 * @param profile Codec profile. For VP8: profile {@code 0} is recommended for most use cases.
 *     Non-zero profiles increasingly optimise for reduced-complexity decoding on low-power devices
 *     at the expense of encode quality; for example, profile {@code 1} restricts sub-pixel
 *     filtering to bi-linear interpolation and uses a simplified loop filter. Only consider a
 *     non-zero value when targeting very low-power playback hardware. For VP9: controls
 *     colour-space and bit-depth handling. Default is {@code 0}.
 * @param usage Encoder usage hint ({@code g_usage}). {@code 0} is general-purpose encoding;
 *     codec-specific values (e.g.&nbsp;{@code 1} for VP8 real-time) select alternative init tables.
 *     Default is {@code 0}.
 * @param errorResilient Enable error-resilient encoding ({@code g_error_resilient}). When enabled,
 *     the encoder fully resets its context tables not only at key frames but also whenever a Golden
 *     Frame is encoded, allowing the decoder to recover from packet loss without a full key frame.
 *     Only recommended for real-time or lossy-network scenarios (e.g. video conferencing). Default
 *     is {@code false}.
 * @param lagInFrames Number of frames the encoder may buffer before emitting output ({@code
 *     g_lag_in_frames}). {@code 0} disables lookahead (required for real-time use). For VP8
 *     two-pass encoding with alternate-reference frames, a value of {@code 16} is commonly
 *     recommended. VP9 typically benefits from values of {@code 25} for offline encoding. Default
 *     is {@code 0}.
 * @param maxQuantizer Maximum quantiser index ({@code rc_max_quantizer}). Higher values allow more
 *     compression at lower quality. Range: {@code 0}–{@code 63}; default is {@code 63} (libvpx
 *     default). The legacy JNI code used {@code 54}. These are control indices rather than real
 *     quantiser values; together they act as hard quality limits that override all other
 *     rate-control settings.
 * @param minQuantizer Minimum quantiser index ({@code rc_min_quantizer}). Lower values produce
 *     higher quality at the cost of bitrate. Range: {@code 0}–{@code 63}; default is {@code 0}.
 *     Together with {@link #maxQuantizer} this defines a hard quality window that overrides all
 *     other rate-control settings.
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
         * Constant bitrate. The encoder tries to stay within notional decoder-buffer constraints
         * rather than forcing every frame to be exactly the same size (which would harm quality).
         * It may spend slightly more bits on a difficult frame or short section, but cannot sustain
         * a higher-than-average data rate for long before the buffer runs empty. Preferred for
         * live-streaming and WebRTC scenarios where a predictable bitrate is required.
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
     * Fluent builder for {@link VpxEncoderConfig}.
     *
     * <p>All fields are pre-populated with sensible defaults. Only {@code width} and {@code height}
     * are required.
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
         * Creates a builder with the required frame dimensions. All other fields are pre-populated
         * with sensible defaults (256&nbsp;kbps, 1&nbsp;thread, 1/1000&nbsp;ms timebase, real-time
         * deadline, VBR rate control, auto keyframe mode, quantizer range 0–63).
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
