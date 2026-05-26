package org.seuffert.panvpx.benchmark;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import org.seuffert.panvpx.benchmark.BenchmarkConfig.Codec;
import org.seuffert.panvpx.benchmark.BenchmarkConfig.EncodingPreset;
import org.seuffert.panvpx.benchmark.BenchmarkConfig.Mode;
import org.seuffert.panvpx.core.AbstractVpxDecoder;
import org.seuffert.panvpx.core.VpxDecoderConfig;
import org.seuffert.panvpx.core.VpxEncoderConfig;
import org.seuffert.panvpx.core.VpxImage;
import org.seuffert.panvpx.vp8.Vp8Decoder;
import org.seuffert.panvpx.vp8.Vp8Encoder;
import org.seuffert.panvpx.vp9.Vp9Decoder;
import org.seuffert.panvpx.vp9.Vp9Encoder;

/**
 * Entry point for the panvpx benchmark suite.
 *
 * <p>Run with defaults (1920x1080 synthetic frames, VP8+VP9, all modes):
 *
 * <pre>{@code
 * ./gradlew :benchmark:run
 * }</pre>
 *
 * <p>Customise via {@code --args}:
 *
 * <pre>{@code
 * ./gradlew :benchmark:run --args="--width=1280 --height=720 --bitrate=2000 --codec=vp8 --mode=encoder --frames=120 --warmup=30"
 * ./gradlew :benchmark:run --args="--input=/path/to/video.yuv --width=1920 --height=1080 --codec=vp8 --mode=encoder"
 * }</pre>
 *
 * <p>Available arguments:
 *
 * <ul>
 *   <li>{@code --width=N} — frame width (default 1920)
 *   <li>{@code --height=N} — frame height (default 1080)
 *   <li>{@code --bitrate=N} — target bitrate in kbps (default 4000)
 *   <li>{@code --codec=vp8|vp9|both} — codec(s) to benchmark (default both)
 *   <li>{@code --mode=encoder|decoder|all} — benchmark mode(s), comma-separated (default all)
 *   <li>{@code --warmup=N} — warmup frames (default 60)
 *   <li>{@code --frames=N} — measurement frames (default 300)
 *   <li>{@code --threads=N} — encoder and decoder thread count (default 1)
 *   <li>{@code --preset=quality|realtime} — encoding deadline preset: {@code quality} (default)
 *       uses a 1-second deadline for realistic throughput; {@code realtime} uses a 1 µs deadline
 *       for low-latency-focused measurements
 *   <li>{@code --cpu-used=N} — CPU usage / speed control (default 0 = best quality within
 *       deadline). For VP9 good-quality: 0–5; for VP9 realtime: 5–8; for VP8: 0–16.
 *   <li>{@code --row-mt} — enable VP9 row-based multithreading (default off)
 *   <li>{@code --tile-columns=N} — VP9 tile columns: actual column count — 1 (default), 2, 4, 8,
 *       16, 32, or 64
 *   <li>{@code --token-partitions=N} — VP8 token partitions / slices: actual partition count — 1
 *       (default), 2, 4, or 8
 *   <li>{@code --input=/path/to/file.yuv} — use a raw I420 YUV file instead of synthetic frames
 * </ul>
 */
public final class BenchmarkRunner {

    private BenchmarkRunner() {}

    /** Entry point. */
    public static void main(final String[] args) {
        // Show usage when invoked with no arguments or an explicit --help flag.
        if (args.length == 0 || java.util.Arrays.asList(args).contains("--help")) {
            printUsage();
            return;
        }

        final BenchmarkConfig config;
        try {
            config = parseArgs(args);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
            return;
        }

        // When no external input file is provided, load the built-in VP9 clip and decode it
        // into raw I420 frames for use by all benchmark modes.
        final List<byte[]> clipPackets;
        final List<byte[]> clipFrames;
        if (config.inputFile().isEmpty() && !config.isSynthetic()) {
            System.out.println("Loading built-in VP9 clip\u2026");
            final IvfReader clip = loadBuiltInClip();
            clipPackets = clip.packets();
            clipFrames = decodeClipFrames(clipPackets, config.threads());
            System.out.printf(
                    "  \u2192 %d frames decoded (%dx%d)%n",
                    clipFrames.size(), clip.width(), clip.height());
        } else {
            clipPackets = List.of();
            clipFrames = List.of();
        }

        printHeader(config);

        final List<BenchmarkResult> results = new ArrayList<>();
        final List<String> codecs = resolveCodecs(config.codec());

        for (final String codec : codecs) {
            if (config.modes().contains(Mode.ENCODER)) {
                results.add(runEncoder(codec, config, clipFrames));
            }
            if (config.modes().contains(Mode.DECODER)) {
                results.add(runDecoder(codec, config, clipPackets, clipFrames));
            }
        }

        printResults(results);
    }

    // ── Benchmark runners ──────────────────────────────────────────────────────

    private static BenchmarkResult runEncoder(
            final String codec, final BenchmarkConfig config, final List<byte[]> clipFrames) {
        System.out.printf(
                "Running Encoder/%s (%d warmup + %d measure frames)\u2026%n",
                codec, config.warmupFrames(), config.measureFrames());
        try (AutoCloseable enc = createEncoder(codec, config);
                FrameSource src = createSource(config, clipFrames)) {
            return EncoderBenchmark.run(
                    codec,
                    (org.seuffert.panvpx.core.AbstractVpxEncoder) enc,
                    src,
                    config.warmupFrames(),
                    config.measureFrames());
        } catch (Exception e) {
            throw new RuntimeException("Encoder benchmark failed for " + codec, e);
        }
    }

    private static BenchmarkResult runDecoder(
            final String codec,
            final BenchmarkConfig config,
            final List<byte[]> clipPackets,
            final List<byte[]> clipFrames) {
        if (!clipPackets.isEmpty() && "VP9".equals(codec)) {
            // For VP9 with the built-in clip, feed the original IVF packets directly to the
            // decoder — no re-encoding step needed.
            System.out.printf(
                    "Running Decoder/%s (%d warmup + %d measure frames, clip packets)\u2026%n",
                    codec, config.warmupFrames(), config.measureFrames());
            try (AutoCloseable dec = createDecoder(codec, config)) {
                return DecoderBenchmark.run(
                        codec,
                        (AbstractVpxDecoder) dec,
                        clipPackets,
                        config.warmupFrames(),
                        config.measureFrames());
            } catch (Exception e) {
                throw new RuntimeException("Decoder benchmark failed for " + codec, e);
            }
        }
        // VP8, or an external file source: pre-encode frames first.
        System.out.printf(
                "Running Decoder/%s (%d measure frames, setup encodes first)\u2026%n",
                codec, config.measureFrames());
        try (AutoCloseable setupEnc = createEncoder(codec, config);
                AutoCloseable dec = createDecoder(codec, config);
                FrameSource src = createSource(config, clipFrames)) {
            return DecoderBenchmark.run(
                    codec,
                    (org.seuffert.panvpx.core.AbstractVpxEncoder) setupEnc,
                    (org.seuffert.panvpx.core.AbstractVpxDecoder) dec,
                    src,
                    config.warmupFrames(),
                    config.measureFrames());
        } catch (Exception e) {
            throw new RuntimeException("Decoder benchmark failed for " + codec, e);
        }
    }

    // ── Factory helpers ────────────────────────────────────────────────────────

    private static AutoCloseable createEncoder(final String codec, final BenchmarkConfig config) {
        final VpxEncoderConfig encCfg =
                VpxEncoderConfig.builder(config.width(), config.height())
                        .targetBitrateKbps(config.bitrateKbps())
                        .threads(config.threads())
                        .deadline(config.preset().deadline())
                        .cpuUsed(config.cpuUsed())
                        .rowMt(config.isRowMt())
                        .tileColumns(config.tileColumns())
                        .tokenPartitions(config.tokenPartitions())
                        .build();
        return "VP8".equals(codec) ? new Vp8Encoder(encCfg) : new Vp9Encoder(encCfg);
    }

    private static AutoCloseable createDecoder(final String codec, final BenchmarkConfig config) {
        final VpxDecoderConfig decCfg = new VpxDecoderConfig(config.threads());
        return "VP8".equals(codec) ? new Vp8Decoder(decCfg) : new Vp9Decoder(decCfg);
    }

    private static FrameSource createSource(
            final BenchmarkConfig config, final List<byte[]> clipFrames) {
        if (!clipFrames.isEmpty()) {
            return new ClipFrameSource(clipFrames, config.width(), config.height());
        }
        return config.inputFile()
                .map(
                        path ->
                                (FrameSource)
                                        new FileFrameSource(path, config.width(), config.height()))
                .orElseGet(() -> new SyntheticFrameSource(config.width(), config.height()));
    }

    private static List<String> resolveCodecs(final Codec codec) {
        final List<String> codecs = new ArrayList<>();
        if (codec == Codec.VP8 || codec == Codec.BOTH) {
            codecs.add("VP8");
        }
        if (codec == Codec.VP9 || codec == Codec.BOTH) {
            codecs.add("VP9");
        }
        return codecs;
    }

    // ── Output ─────────────────────────────────────────────────────────────────

    private static void printHeader(final BenchmarkConfig config) {
        final String source =
                config.inputFile()
                        .map(p -> "file: " + p.getFileName())
                        .orElse(
                                config.isSynthetic()
                                        ? "synthetic frames"
                                        : "built-in clip (1080p25)");
        final List<String> extras = new ArrayList<>();
        if (config.cpuUsed() != 0) {
            extras.add("cpu-used=" + config.cpuUsed());
        }
        if (config.isRowMt()) {
            extras.add("row-mt");
        }
        if (config.tileColumns() != 0) {
            extras.add("tile-cols=" + config.tileColumns());
        }
        if (config.tokenPartitions() != 0) {
            extras.add("token-parts=" + config.tokenPartitions());
        }
        final String extraStr = extras.isEmpty() ? "" : "  |  " + String.join("  |  ", extras);
        System.out.println();
        System.out.printf(
                "panvpx Benchmark — %dx%d @ %d kbps  |  %d threads  |  %s%s  |  %d frames  |  %d warmup  |  %s%n",
                config.width(),
                config.height(),
                config.bitrateKbps(),
                config.threads(),
                config.preset().name().toLowerCase(Locale.ROOT).replace('_', ' '),
                extraStr,
                config.measureFrames(),
                config.warmupFrames(),
                source);
        System.out.println();
    }

    private static void printResults(final List<BenchmarkResult> results) {
        System.out.println();
        System.out.println(BenchmarkResult.tableHeader());
        for (final BenchmarkResult r : results) {
            // For decoder rows, label the bitstream columns as input so the user
            // can see the average compressed-packet size fed to the decoder.
            if ("Decoder".equals(r.mode())) {
                System.out.println(r.tableRowWithLabel("in"));
            } else {
                System.out.println(r.tableRow());
            }
        }
        System.out.println();
    }

    // ── Clip loading ───────────────────────────────────────────────────────────

    private static IvfReader loadBuiltInClip() {
        try (InputStream in =
                BenchmarkRunner.class.getResourceAsStream("/clip_vp9_1080p25_2s.ivf")) {
            if (in == null) {
                throw new IllegalStateException(
                        "Built-in VP9 clip not found in classpath"
                                + " (expected /clip_vp9_1080p25_2s.ivf)");
            }
            return new IvfReader(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load built-in VP9 clip", e);
        }
    }

    private static List<byte[]> decodeClipFrames(final List<byte[]> packets, final int threads) {
        final List<byte[]> frames = new ArrayList<>(packets.size());
        try (Vp9Decoder dec = new Vp9Decoder(new VpxDecoderConfig(threads))) {
            for (final byte[] packet : packets) {
                for (final VpxImage img : dec.decode(packet)) {
                    frames.add(img.toByteArray());
                }
            }
        }
        if (frames.isEmpty()) {
            throw new IllegalStateException("Built-in VP9 clip decoded zero frames");
        }
        return List.copyOf(frames);
    }

    // ── Argument parsing ───────────────────────────────────────────────────────

    private static BenchmarkConfig parseArgs(final String[] args) {
        final BenchmarkConfig.Builder b = BenchmarkConfig.defaults();
        for (final String arg : args) {
            if (arg.startsWith("--width=")) {
                b.width(parseInt(arg, "--width="));
            } else if (arg.startsWith("--height=")) {
                b.height(parseInt(arg, "--height="));
            } else if (arg.startsWith("--bitrate=")) {
                b.bitrateKbps(parseInt(arg, "--bitrate="));
            } else if (arg.startsWith("--warmup=")) {
                b.warmupFrames(parseInt(arg, "--warmup="));
            } else if (arg.startsWith("--frames=")) {
                b.measureFrames(parseInt(arg, "--frames="));
            } else if (arg.startsWith("--threads=")) {
                b.threads(parseInt(arg, "--threads="));
            } else if (arg.startsWith("--cpu-used=")) {
                b.cpuUsed(parseInt(arg, "--cpu-used="));
            } else if (arg.equals("--row-mt")) {
                b.rowMt(true);
            } else if (arg.startsWith("--tile-columns=")) {
                b.tileColumns(parseInt(arg, "--tile-columns="));
            } else if (arg.startsWith("--token-partitions=")) {
                b.tokenPartitions(parseInt(arg, "--token-partitions="));
            } else if (arg.startsWith("--preset=")) {
                b.preset(parsePreset(arg.substring("--preset=".length())));
            } else if (arg.startsWith("--codec=")) {
                b.codec(parseCodec(arg.substring("--codec=".length())));
            } else if (arg.startsWith("--mode=")) {
                b.modes(parseModes(arg.substring("--mode=".length())));
            } else if (arg.startsWith("--input=")) {
                b.inputFile(Path.of(arg.substring("--input=".length())));
            } else if (arg.equals("--synthetic")) {
                b.synthetic(true);
            } else {
                throw new IllegalArgumentException(
                        "Unknown argument: '" + arg + "'. Run without arguments to see usage.");
            }
        }
        return b.build();
    }

    private static int parseInt(final String arg, final String prefix) {
        try {
            return Integer.parseInt(arg.substring(prefix.length()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid numeric argument: " + arg, e);
        }
    }

    private static Codec parseCodec(final String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "vp8" -> Codec.VP8;
            case "vp9" -> Codec.VP9;
            case "both" -> Codec.BOTH;
            default ->
                    throw new IllegalArgumentException(
                            "Unknown codec '" + value + "'. Use vp8, vp9, or both.");
        };
    }

    private static EncodingPreset parsePreset(final String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "quality" -> EncodingPreset.QUALITY;
            case "realtime" -> EncodingPreset.REALTIME;
            default ->
                    throw new IllegalArgumentException(
                            "Unknown preset '" + value + "'. Use quality or realtime.");
        };
    }

    private static EnumSet<Mode> parseModes(final String value) {
        final EnumSet<Mode> modes = EnumSet.noneOf(Mode.class);
        for (final String part : value.split(",")) {
            switch (part.trim().toLowerCase(Locale.ROOT)) {
                case "encoder" -> modes.add(Mode.ENCODER);
                case "decoder" -> modes.add(Mode.DECODER);
                case "all" -> {
                    return EnumSet.allOf(Mode.class);
                }
                default ->
                        throw new IllegalArgumentException(
                                "Unknown mode '" + part + "'. Use encoder, decoder, or all.");
            }
        }
        if (modes.isEmpty()) {
            throw new IllegalArgumentException("--mode must specify at least one mode.");
        }
        return modes;
    }

    private static void printUsage() {
        System.out.println(
                """
                panvpx Benchmark

                USAGE
                  ./gradlew :benchmark:run --args="[OPTIONS]"

                OPTIONS
                  --codec=vp8|vp9|both          Codec(s) to benchmark (default: both)
                  --mode=encoder|decoder|all     Benchmark mode(s), comma-separated
                                          (default: all)
                  --width=N                     Frame width  (default: 1920)
                  --height=N                    Frame height (default: 1080)
                  --bitrate=N                   Target bitrate in kbps (default: 4000)
                  --threads=N                   Encoder and decoder threads (default: 1)
                  --warmup=N                    Warmup frames (default: 3)
                  --frames=N                    Measurement frames (default: 25)
                  --preset=quality|realtime     Encoding deadline preset
                                                  quality   — 1 s deadline (default)
                                                  realtime  — 1 µs deadline
                  --cpu-used=N                  CPU speed/quality trade-off (default: 0)
                                                  VP8:  0–16  (0 = best quality, 16 = fastest)
                                                  VP9 quality:  0–5  (0 = best, 5 = fastest)
                                                  VP9 realtime: 5–8  (5 = best, 8 = fastest)
                  --row-mt                      Enable VP9 row-based multithreading
                  --tile-columns=N              VP9 tile columns — actual count: 1 (default),
                                                  2, 4, 8, 16, 32, or 64
                  --token-partitions=N          VP8 token partitions — actual count: 1 (default),
                                                  2, 4, or 8
                  --input=/path/to/file.yuv     Raw I420 YUV input file (default: built-in clip)
                  --synthetic                   Use generated frames instead of the built-in clip
                  --help                        Show this message

                EXAMPLES
                  # Full benchmark (all codecs, all modes) using the built-in 1080p25 clip:
                  ./gradlew :benchmark:run --args="--codec=both --mode=all"

                  # VP9 encoder only, 8 threads, realtime preset, cpu-used=6:
                  ./gradlew :benchmark:run --args="--codec=vp9 --mode=encoder --threads=8 --row-mt --tile-columns=8 --cpu-used=6 --preset=realtime"

                  # VP8 encoder with 4 token partitions, quality preset:
                  ./gradlew :benchmark:run --args="--codec=vp8 --mode=encoder --token-partitions=4 --preset=quality"

                  # Decoder-only benchmark, 720p, 4 threads, custom input:
                  ./gradlew :benchmark:run --args="--codec=vp9 --mode=decoder --width=1280 --height=720 --threads=4 --input=/tmp/video.yuv"

                  # Quick encoder + decoder comparison, short run:
                  ./gradlew :benchmark:run --args="--mode=encoder,decoder --warmup=10 --frames=50"
                """);
    }
}
