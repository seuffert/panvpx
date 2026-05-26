package org.seuffert.panvpx.benchmark;

/** Immutable result of a single benchmark measurement. */
public final class BenchmarkResult {

    private static final double NANOS_PER_SEC = 1_000_000_000.0;
    private static final double BYTES_PER_MB = 1_048_576.0;

    private final String mode;
    private final String codec;
    private final int frames;
    private final long elapsedNanos;
    private final long totalEncodedBytes;

    BenchmarkResult(
            final String mode,
            final String codec,
            final int frames,
            final long elapsedNanos,
            final long totalEncodedBytes) {
        this.mode = mode;
        this.codec = codec;
        this.frames = frames;
        this.elapsedNanos = elapsedNanos;
        this.totalEncodedBytes = totalEncodedBytes;
    }

    public String mode() {
        return mode;
    }

    public String codec() {
        return codec;
    }

    public int frames() {
        return frames;
    }

    public long elapsedNanos() {
        return elapsedNanos;
    }

    public long totalEncodedBytes() {
        return totalEncodedBytes;
    }

    public double fps() {
        return frames / (elapsedNanos / NANOS_PER_SEC);
    }

    public double encodedMBperSec() {
        if (totalEncodedBytes == 0) {
            return 0.0;
        }
        return (totalEncodedBytes / BYTES_PER_MB) / (elapsedNanos / NANOS_PER_SEC);
    }

    public long avgFrameBytes() {
        if (totalEncodedBytes == 0 || frames == 0) {
            return 0;
        }
        return totalEncodedBytes / frames;
    }

    /**
     * Returns a fixed-width table row. The header row can be printed separately via {@link
     * #tableHeader()}.
     */
    public String tableRow() {
        final String encoded =
                totalEncodedBytes > 0
                        ? String.format("%-11s  %-10s", formatMBps(), formatAvgFrame())
                        : String.format("%-11s  %-10s", "—", "—");
        return String.format(
                "%-12s  %-6s  %-12s  %s  %-8s", mode, codec, formatFps(), encoded, formatRuntime());
    }

    /**
     * Returns a fixed-width table row with a label appended to the mode column in parentheses. Used
     * by the decoder row to indicate that the bitstream columns reflect the <em>input</em> (encoded
     * packets fed to the decoder) rather than encoder output.
     *
     * @param modeLabel extra label to append, e.g. {@code "in"}.
     * @return formatted table row string.
     */
    public String tableRowWithLabel(final String modeLabel) {
        final String modeField = mode + " (" + modeLabel + ")";
        final String encoded =
                totalEncodedBytes > 0
                        ? String.format("%-11s  %-10s", formatMBps(), formatAvgFrame())
                        : String.format("%-11s  %-10s", "—", "—");
        return String.format(
                "%-12s  %-6s  %-12s  %s  %-8s",
                modeField, codec, formatFps(), encoded, formatRuntime());
    }

    /** Returns the column header matching the format of {@link #tableRow()}. */
    public static String tableHeader() {
        return String.format(
                        "%-12s  %-6s  %-12s  %-11s  %-10s  %-8s",
                        "Mode", "Codec", "Throughput", "Encoded", "Avg Frame", "Runtime")
                + System.lineSeparator()
                + "─".repeat(73);
    }

    private String formatFps() {
        return String.format("%.1f fps", fps());
    }

    private String formatMBps() {
        final double mbps = encodedMBperSec();
        if (mbps < 0.1) {
            return String.format("%.0f kB/s", mbps * 1024.0);
        }
        return String.format("%.1f MB/s", mbps);
    }

    private String formatRuntime() {
        final double secs = elapsedNanos / NANOS_PER_SEC;
        if (secs < 1.0) {
            return String.format("%.0fms", secs * 1000.0);
        }
        return String.format("%.2fs", secs);
    }

    private String formatAvgFrame() {
        final long bytes = avgFrameBytes();
        if (bytes == 0) {
            return "—";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        return String.format("%.1f kB", bytes / 1024.0);
    }
}
