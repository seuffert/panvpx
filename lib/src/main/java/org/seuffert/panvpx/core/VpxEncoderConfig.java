package org.seuffert.panvpx.core;

import java.util.Objects;

/**
 * Configuration options for a VPX Encoder (VP8 or VP9).
 *
 * <p>This immutable record contains all necessary parameters to initialize a video encoder,
 * balancing quality, speed, and target bitrate.
 *
 * <p>For anything beyond a simple width/height or bitrate/threads setup, use the fluent {@link
 * Builder} returned by {@link #builder(Codec, int, int)}:
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
 * <p><strong>Relationship to libvpx native defaults</strong> — The {@link Builder} defaults are
 * designed to work reasonably for both VP8 ({@code vp8_cx_iface.c}) and VP9 ({@code
 * vp9_cx_iface.c}), both at {@code VPX_ENCODER_ABI_VERSION = 39}. Intentional divergences from the
 * raw libvpx defaults:
 *
 * <ul>
 *   <li>{@link #minQuantizer} — builder default {@code 0}; VP8 native default {@code 4}, VP9 native
 *       default {@code 0}.
 *   <li>{@link #timebaseNumerator}/{@link #timebaseDenominator} — builder default {@code {1, 1000}}
 *       (millisecond timestamps); native default {@code {1, 30}} for both codecs.
 *   <li>{@link #threads} — builder default {@code 1}; VP8 native default {@code 0} (codec treats as
 *       1 thread), VP9 native default {@code 8}.
 *   <li>{@link #maxKeyframeDistance} — builder default {@code 0} (delegates to the codec); native
 *       default {@code 128} for both codecs.
 *   <li>{@link #lagInFrames} — builder default {@code 0}; VP8 native default {@code 0}, VP9 native
 *       default {@code 25}.
 * </ul>
 *
 * <p>All other exposed fields ({@link #targetBitrateKbps}, {@link #rateControlMode}, {@link
 * #maxQuantizer}, {@link #frameDropThreshold}, {@link #keyframeMode}, etc.) match the libvpx
 * defaults for both codecs.
 *
 * @param codec The target codec type (VP8 or VP9).
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
 * @param bitDepth Codec internal bit depth ({@code g_bit_depth}). Controls the precision used in
 *     internal transforms. VP8 supports only {@link BitDepth#BITS_8}. VP9 additionally supports
 *     {@link BitDepth#BITS_10} and {@link BitDepth#BITS_12}, which require a matching codec profile
 *     (profile&nbsp;2 for 10-bit 4:2:0, profile&nbsp;3 for 12-bit or 4:4:4 content). Default:
 *     {@link BitDepth#BITS_8}.
 * @param inputBitDepth Bit depth of the raw input frames ({@code g_input_bit_depth}). Must match
 *     the pixel format of the {@code VpxImage} frames passed to the encoder. For standard 8-bit YUV
 *     content use {@code 8} (the default). When encoding high-bit-depth VP9 streams, set this to
 *     {@code 10} or {@code 12} together with a matching {@link #bitDepth} and codec profile.
 *     Default: {@code 8}. Libvpx default: {@code 8}.
 * @param resizeAllowed Enable dynamic spatial resampling ({@code rc_resize_allowed}). When {@code
 *     true}, the encoder may scale the frame dimensions down (and back up) at run-time to stay
 *     within bitrate constraints. The resize thresholds and target dimensions ({@code
 *     rc_resize_down_thresh}, {@code rc_resize_up_thresh}, {@code rc_scaled_width}, {@code
 *     rc_scaled_height}) are not yet exposed; libvpx codec defaults apply when this is enabled.
 *     Only useful in CBR mode with {@link #frameDropThreshold} also set. Default: {@code false}.
 *     Libvpx default: {@code 0} (disabled).
 * @param minKeyframeDistance Minimum number of frames between automatically placed key frames
 *     ({@code kf_min_dist}). {@code 0} imposes no minimum (the codec default). Setting this to a
 *     positive value prevents the encoder from inserting a key frame more frequently than the
 *     specified interval even at scene cuts. Default: {@code 0}. Libvpx default: {@code 0}.
 */
public record VpxEncoderConfig(
        Codec codec,
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
        int minQuantizer,
        BitDepth bitDepth,
        int inputBitDepth,
        boolean resizeAllowed,
        int minKeyframeDistance) {

    /** Real-time deadline (1 µs): fastest encoding, lowest quality. */
    public static final long DEADLINE_REALTIME = 1L;

    /** Good-quality deadline (1 s): balanced quality and speed; the default. */
    public static final long DEADLINE_GOOD_QUALITY = 1_000_000L;

    /** Best-quality deadline (0 = unlimited): highest quality, slowest encoding. */
    public static final long DEADLINE_BEST_QUALITY = 0L;

    /** The target codec type. */
    public enum Codec {
        /** VP8 Codec. */
        VP8,
        /** VP9 Codec. */
        VP9
    }

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
     * Codec internal bit depth ({@code g_bit_depth}).
     *
     * <p>Controls the precision at which the codec performs its internal transforms and
     * rate-distortion optimisation. VP8 supports only {@link #BITS_8}. VP9 additionally supports
     * {@link #BITS_10} and {@link #BITS_12}, but these require a matching {@link
     * VpxEncoderConfig#profile}: profile&nbsp;2 for 10-bit and profile&nbsp;3 for 12-bit 4:2:0, or
     * profiles&nbsp;2/3 for 4:4:4 content.
     *
     * <p>Corresponds to the native {@code vpx_bit_depth_t} enum ({@code g_bit_depth}).
     */
    public enum BitDepth {
        /**
         * 8-bit depth ({@code VPX_BITS_8}). Supported by both VP8 and VP9. The default for all
         * standard 8-bit YUV content.
         */
        BITS_8,

        /**
         * 10-bit depth ({@code VPX_BITS_10}). VP9 only. Requires profile 2 (4:2:0) or profile 3
         * (4:4:4) and a 10-bit input {@code VpxImage}. Not supported by VP8.
         */
        BITS_10,

        /**
         * 12-bit depth ({@code VPX_BITS_12}). VP9 only. Requires profile 2 (4:2:0) or profile 3
         * (4:4:4) and a 12-bit input {@code VpxImage}. Not supported by VP8.
         */
        BITS_12
    }

    /**
     * Creates a new {@link Builder} pre-populated with all defaults and requiring only the frame
     * dimensions.
     *
     * @param codec The target codec type (VP8 or VP9).
     * @param width The frame width in pixels.
     * @param height The frame height in pixels.
     * @return a new {@link Builder} instance.
     */
    public static Builder builder(final Codec codec, final int width, final int height) {
        return new Builder(codec, width, height);
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

        private final Codec codec;
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
        private BitDepth bitDepth = BitDepth.BITS_8;
        private int inputBitDepth = 8;
        private boolean resizeAllowed;
        private int minKeyframeDistance;

        /**
         * Creates a builder with the required frame dimensions. All other fields are pre-populated
         * with sensible defaults (256&nbsp;kbps, 1&nbsp;thread, 1/1000&nbsp;ms timebase, real-time
         * deadline, VBR rate control, auto keyframe mode, quantizer range 0–63).
         *
         * @param codec The target codec type (VP8 or VP9).
         * @param width The frame width in pixels.
         * @param height The frame height in pixels.
         */
        public Builder(final Codec codec, final int width, final int height) {
            this.codec = Objects.requireNonNull(codec, "codec must not be null");
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("Width and height must be strictly positive");
            }
            this.width = width;
            this.height = height;
        }

        /**
         * Sets the target bitrate in kilobits per second. Default: {@code 256}. Libvpx default:
         * {@code 256} ({@code rc_target_bitrate}).
         *
         * @param value target bitrate in kbps.
         * @return this builder.
         */
        public Builder targetBitrateKbps(final int value) {
            if (value < 0) {
                throw new IllegalArgumentException("targetBitrateKbps must be non-negative");
            }
            this.targetBitrateKbps = value;
            return this;
        }

        /**
         * Sets the frame-drop threshold. {@code 0} disables frame dropping; {@code 1}–{@code 100}
         * is the under-shoot percentage at which the encoder may drop frames. Default: {@code 0}.
         * Libvpx default: {@code 0} ({@code rc_dropframe_thresh}).
         *
         * @param value frame-drop threshold.
         * @return this builder.
         */
        public Builder frameDropThreshold(final int value) {
            if (value < 0 || value > 100) {
                throw new IllegalArgumentException("frameDropThreshold must be between 0 and 100");
            }
            this.frameDropThreshold = value;
            return this;
        }

        /**
         * Sets the number of encoding threads. Default: {@code 1}. VP8 libvpx default: {@code 0}
         * ({@code g_threads}; the VP8 codec treats {@code 0} as 1 thread internally). VP9 libvpx
         * default: {@code 8}.
         *
         * @param value thread count.
         * @return this builder.
         */
        public Builder threads(final int value) {
            if (value < 0) {
                throw new IllegalArgumentException("threads must be non-negative");
            }
            this.threads = value;
            return this;
        }

        /**
         * Sets the timebase numerator. Default: {@code 1}. Libvpx default: {@code 1} ({@code
         * g_timebase.num}).
         *
         * @param value timebase numerator.
         * @return this builder.
         */
        public Builder timebaseNumerator(final int value) {
            if (value <= 0) {
                throw new IllegalArgumentException("timebaseNumerator must be positive");
            }
            this.timebaseNumerator = value;
            return this;
        }

        /**
         * Sets the timebase denominator. Default: {@code 1000} (millisecond timestamps). Libvpx
         * default: {@code 30} ({@code g_timebase.den}).
         *
         * @param value timebase denominator.
         * @return this builder.
         */
        public Builder timebaseDenominator(final int value) {
            if (value <= 0) {
                throw new IllegalArgumentException("timebaseDenominator must be positive");
            }
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
            if (value < 0) {
                throw new IllegalArgumentException("deadline must be non-negative");
            }
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
            if (value < 0) {
                throw new IllegalArgumentException("cpuUsed must be non-negative");
            }
            if (codec == Codec.VP8 && value > 16) {
                throw new IllegalArgumentException("VP8 cpuUsed must be <= 16");
            } else if (codec == Codec.VP9 && value > 8) {
                throw new IllegalArgumentException("VP9 cpuUsed must be <= 8");
            }
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
            if (value && codec != Codec.VP9) {
                throw new IllegalArgumentException("rowMt is only supported by VP9");
            }
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
            if (value != 0 && codec != Codec.VP9) {
                throw new IllegalArgumentException("tileColumns is only supported by VP9");
            }
            if (value < 0 || (value > 0 && (value & (value - 1)) != 0) || value > 64) {
                throw new IllegalArgumentException(
                        "tileColumns must be 0 or a power of two up to 64");
            }
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
            if (value != 0 && codec != Codec.VP8) {
                throw new IllegalArgumentException("tokenPartitions is only supported by VP8");
            }
            if (value != 0 && value != 1 && value != 2 && value != 4 && value != 8) {
                throw new IllegalArgumentException("tokenPartitions must be 0, 1, 2, 4, or 8");
            }
            this.tokenPartitions = value;
            return this;
        }

        /**
         * Sets the rate-control mode. Default: {@link RateControlMode#VBR}. Libvpx default: {@code
         * VPX_VBR} ({@code rc_end_usage}).
         *
         * @param value the {@link RateControlMode} to use.
         * @return this builder.
         */
        public Builder rateControlMode(final RateControlMode value) {
            this.rateControlMode =
                    Objects.requireNonNull(value, "rateControlMode must not be null");
            return this;
        }

        /**
         * Sets the maximum distance between automatically placed key frames. {@code 0} leaves the
         * codec default unchanged. Default: {@code 0}. Libvpx default: {@code 128} ({@code
         * kf_max_dist}).
         *
         * @param value max key-frame distance in frames.
         * @return this builder.
         */
        public Builder maxKeyframeDistance(final int value) {
            if (value < 0) {
                throw new IllegalArgumentException("maxKeyframeDistance must be non-negative");
            }
            this.maxKeyframeDistance = value;
            return this;
        }

        /**
         * Sets the key-frame placement mode. Default: {@link KeyframeMode#AUTO}. Libvpx default:
         * {@code VPX_KF_AUTO} ({@code kf_mode}).
         *
         * @param value the {@link KeyframeMode} to use.
         * @return this builder.
         */
        public Builder keyframeMode(final KeyframeMode value) {
            this.keyframeMode = Objects.requireNonNull(value, "keyframeMode must not be null");
            return this;
        }

        /**
         * Sets the codec profile. For VP8: {@code 0}–{@code 3} (typically {@code 0}). For VP9:
         * controls colour-space/bit-depth handling. Default: {@code 0}. Libvpx default: {@code 0}
         * ({@code g_profile}).
         *
         * @param value codec profile index.
         * @return this builder.
         */
        public Builder profile(final int value) {
            if (value < 0 || value > 3) {
                throw new IllegalArgumentException("profile must be between 0 and 3");
            }
            this.profile = value;
            return this;
        }

        /**
         * Sets the encoder usage hint ({@code g_usage}). {@code 0} is general-purpose encoding;
         * {@code 1} selects the real-time preset for VP8. Default: {@code 0}. Libvpx default:
         * {@code 0}.
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
         * partially decoded in the presence of packet loss. Default: {@code false}. Libvpx default:
         * {@code false} ({@code g_error_resilient}).
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
         * lookahead and is required for real-time streaming. Default: {@code 0}. VP8 libvpx
         * default: {@code 0}. VP9 libvpx default: {@code 25}.
         *
         * @param value lag-in-frames value.
         * @return this builder.
         */
        public Builder lagInFrames(final int value) {
            if (value < 0) {
                throw new IllegalArgumentException("lagInFrames must be non-negative");
            }
            this.lagInFrames = value;
            return this;
        }

        /**
         * Sets the maximum quantiser index ({@code rc_max_quantizer}). Range: {@code 0}–{@code 63}.
         * Higher values allow more compression at lower quality. Default: {@code 63}. Libvpx
         * default: {@code 63}. The legacy JNI encoder used {@code 54}.
         *
         * @param value maximum quantiser index.
         * @return this builder.
         */
        public Builder maxQuantizer(final int value) {
            if (value < 0 || value > 63) {
                throw new IllegalArgumentException("maxQuantizer must be between 0 and 63");
            }
            this.maxQuantizer = value;
            return this;
        }

        /**
         * Sets the minimum quantiser index ({@code rc_min_quantizer}). Range: {@code 0}–{@code 63}.
         * Lower values produce higher quality at the cost of bitrate. Default: {@code 0}. VP8
         * libvpx default: {@code 4} ({@code rc_min_quantizer}). VP9 libvpx default: {@code 0}.
         *
         * @param value minimum quantiser index.
         * @return this builder.
         */
        public Builder minQuantizer(final int value) {
            if (value < 0 || value > 63) {
                throw new IllegalArgumentException("minQuantizer must be between 0 and 63");
            }
            this.minQuantizer = value;
            return this;
        }

        /**
         * Sets the codec internal bit depth ({@code g_bit_depth}). Default: {@link
         * BitDepth#BITS_8}. VP8 supports only {@link BitDepth#BITS_8}. VP9 additionally supports
         * {@link BitDepth#BITS_10} and {@link BitDepth#BITS_12} with matching codec profiles.
         * Libvpx default: {@code VPX_BITS_8}.
         *
         * @param value the {@link BitDepth} to use.
         * @return this builder.
         */
        public Builder bitDepth(final BitDepth value) {
            this.bitDepth = Objects.requireNonNull(value, "bitDepth must not be null");
            if (codec == Codec.VP8 && value != BitDepth.BITS_8) {
                throw new IllegalArgumentException("VP8 only supports BITS_8 bitDepth");
            }
            return this;
        }

        /**
         * Sets the bit depth of the raw input frames ({@code g_input_bit_depth}). Must match the
         * pixel format of the {@code VpxImage} frames supplied to the encoder. For standard 8-bit
         * YUV content use {@code 8} (the default). Default: {@code 8}. Libvpx default: {@code 8}.
         *
         * @param value input bit depth (typically {@code 8}, {@code 10}, or {@code 12}).
         * @return this builder.
         */
        public Builder inputBitDepth(final int value) {
            if (codec == Codec.VP8 && value != 8) {
                throw new IllegalArgumentException("VP8 only supports an inputBitDepth of 8");
            }
            if (value != 8 && value != 10 && value != 12) {
                throw new IllegalArgumentException("inputBitDepth must be 8, 10, or 12");
            }
            this.inputBitDepth = value;
            return this;
        }

        /**
         * Enables or disables dynamic spatial resampling ({@code rc_resize_allowed}). When {@code
         * true}, the encoder may scale the frame dimensions down and back up at run-time to stay
         * within bitrate constraints. The related resize thresholds and target dimensions are not
         * yet exposed; libvpx defaults apply. Default: {@code false}. Libvpx default: {@code 0}
         * (disabled).
         *
         * @param value {@code true} to enable dynamic resize.
         * @return this builder.
         */
        public Builder resizeAllowed(final boolean value) {
            this.resizeAllowed = value;
            return this;
        }

        /**
         * Sets the minimum distance between automatically placed key frames ({@code kf_min_dist}).
         * {@code 0} imposes no minimum (the codec default). A positive value prevents the encoder
         * from inserting a key frame more frequently than the specified number of frames even at
         * scene cuts. Default: {@code 0}. Libvpx default: {@code 0}.
         *
         * @param value minimum key-frame interval in frames.
         * @return this builder.
         */
        public Builder minKeyframeDistance(final int value) {
            if (value < 0) {
                throw new IllegalArgumentException("minKeyframeDistance must be non-negative");
            }
            this.minKeyframeDistance = value;
            return this;
        }

        // =====================================================================
        // UNEXPOSED libvpx vpx_codec_enc_cfg_t FIELDS
        // Sources: vp8_cx_iface.c and vp9_cx_iface.c
        //          (VPX_ENCODER_ABI_VERSION = 39, libvpx 1.16.0)
        //          VP8 defaults shown; VP9 differences annotated as (VP9: ...)
        // =====================================================================
        // These fields exist in the native config struct but are not yet
        // surfaced by VpxEncoderConfig.  Update this table as new parameters
        // are added to the Builder above.
        //
        // ---- Multi-pass (2-pass is rarely used for VP8) ----
        // g_pass                   VPX_RC_ONE_PASS
        // rc_twopass_stats_in      {NULL, 0}
        // rc_firstpass_mb_stats_in {NULL, 0}
        // rc_two_pass_vbrbias      50
        // rc_two_pass_vbrmin_section      0
        // rc_two_pass_vbrmax_section      400  (VP9: 2000)
        // rc_2pass_vbr_corpus_complexity  0  (VP9 only)
        //
        // ---- Spatial resampling / dynamic resize ----
        // rc_scaled_width          1    (VP9: 0)
        // rc_scaled_height         1    (VP9: 0)
        // rc_resize_down_thresh    60
        // rc_resize_up_thresh      30
        //
        // ---- Rate-control buffer model ----
        // rc_undershoot_pct        100  (VP9: 25)
        // rc_overshoot_pct         100  (VP9: 25)
        // rc_max_buffer_size       6000  (ms)
        // rc_buffer_initial_size   4000  (ms)
        // rc_buffer_optimal_size   5000  (ms)
        //
        // ---- Scalable video coding (SVC / temporal layers) ----
        // ss_number_layers         VPX_SS_DEFAULT_LAYERS
        // ss_target_bitrate[]      {0}
        // ts_number_layers         1
        // ts_target_bitrate[]      {0}
        // ts_rate_decimator[]      {0}
        // ts_periodicity           0
        // ts_layer_id[]            {0}
        // layer_target_bitrate[]   {0}
        // temporal_layering_mode   0
        //
        // ---- Experimental Vizier RC (internal; unlikely to be needed) ----
        // use_vizier_rc_params     0  (+ ~12 associated *_factor fields, all {1,1})
        // =====================================================================

        /**
         * Builds and returns the immutable {@link VpxEncoderConfig}.
         *
         * @return a new {@link VpxEncoderConfig} reflecting all settings applied to this builder.
         * @throws IllegalArgumentException if invalid parameter combinations are found.
         */
        public VpxEncoderConfig build() {
            if (minQuantizer > maxQuantizer) {
                throw new IllegalArgumentException(
                        "minQuantizer cannot be greater than maxQuantizer");
            }
            if (bitDepth != BitDepth.BITS_8 && profile < 2) {
                throw new IllegalArgumentException(
                        "10-bit or 12-bit encoding requires profile 2 or 3");
            }
            return new VpxEncoderConfig(
                    codec,
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
                    minQuantizer,
                    bitDepth,
                    inputBitDepth,
                    resizeAllowed,
                    minKeyframeDistance);
        }
    }
}
