# panvpx Benchmark

A standalone Gradle subproject that measures VP8 and VP9 encoder/decoder throughput using the panvpx library.

## Running

```sh
# Show usage
./gradlew :benchmark:run

# Full benchmark — all codecs, encoder + decoder
./gradlew :benchmark:run --args="--codec=both --mode=all"
```

## Options

| Option | Default | Description |
|---|---|---|
| `--codec=vp8\|vp9\|both` | `both` | Codec(s) to benchmark |
| `--mode=encoder\|decoder\|all` | `all` | Mode(s) to benchmark, comma-separated |
| `--width=N` | `1920` | Frame width in pixels |
| `--height=N` | `1080` | Frame height in pixels |
| `--bitrate=N` | `4000` | Target bitrate in kbps |
| `--threads=N` | `1` | Encoder and decoder thread count |
| `--warmup=N` | `3` | Warmup frames (excluded from results) |
| `--frames=N` | `25` | Measurement frames |
| `--preset=quality\|realtime` | `quality` | Encoding deadline: `quality` = 1 s, `realtime` = 1 µs |
| `--cpu-used=N` | `0` | CPU speed/quality trade-off (VP8: 0–16; VP9 quality: 0–5; VP9 realtime: 5–8) |
| `--row-mt` | off | Enable VP9 row-based multithreading |
| `--tile-columns=N` | `0` (1 column) | VP9 tile columns — actual count: 1, 2, 4, 8, 16, 32, or 64 |
| `--token-partitions=N` | `0` (1 partition) | VP8 token partitions — actual count: 1, 2, 4, or 8 |
| `--input=/path/to/file.yuv` | — | Raw I420 YUV input file |
| `--synthetic` | off | Use generated frames instead of the built-in clip |

## Input frames

Without `--input` or `--synthetic`, the benchmark uses a **built-in VP9 1080p25 clip** (bundled as a classpath resource). This is the recommended baseline for reproducible comparisons.

`--synthetic` generates I420 frames on the fly using a three-wave interference pattern with per-frame temporal variation. Synthetic frames are more compressor-resistant than simple gradients — no single motion vector can compensate all three waves simultaneously — making them a reasonable stress test when no real content is available.

`--input=/path/to/file.yuv` reads a raw I420 YUV file. `--width` and `--height` must match the file's actual dimensions.

## Output

Each run prints a summary header then a results table:

```
panvpx Benchmark — 1920x1080 @ 4000 kbps  |  1 threads  |  quality  |  25 frames  |  3 warmup  |  built-in clip (1080p25)

Running Encoder/VP8 (3 warmup + 25 measure frames)…
Running Decoder/VP8 (25 measure frames, setup encodes first)…
…

Mode          Codec   Throughput    Out (MB/s)   Avg frame   Runtime
Encoder       VP8     123.4 fps     4.56 MB/s    12345 B     0.203 s
Decoder       VP8     567.8 fps     4.56 (in)    12345 B     0.044 s
…
```

| Column | Encoder | Decoder |
|---|---|---|
| **Throughput** | Encode fps (includes flush) | Decode fps |
| **Out / In (MB/s)** | Compressed output rate | Compressed input rate fed to decoder |
| **Avg frame** | Average compressed packet size | Average compressed packet size |
| **Runtime** | Wall time for all measurement frames + flush | Wall time for all measurement frames |

## Examples

```sh
# VP9 encoder, 8 threads, realtime preset, fastest cpu-used, tile columns
./gradlew :benchmark:run --args="--codec=vp9 --mode=encoder --threads=8 --row-mt --tile-columns=8 --cpu-used=6 --preset=realtime"

# VP8 encoder with 4 token partitions, quality preset
./gradlew :benchmark:run --args="--codec=vp8 --mode=encoder --token-partitions=4 --preset=quality"

# Decoder-only benchmark, 720p, 4 threads, external file
./gradlew :benchmark:run --args="--codec=vp9 --mode=decoder --width=1280 --height=720 --threads=4 --input=/tmp/video.yuv"

# Quick encoder + decoder comparison with more frames
./gradlew :benchmark:run --args="--mode=encoder,decoder --warmup=10 --frames=50"

# Synthetic frames stress test, VP9 only
./gradlew :benchmark:run --args="--codec=vp9 --synthetic --frames=50"
```

## Measurement methodology

- **Warmup**: the first `--warmup` frames are encoded/decoded and discarded. For VP9 in quality mode the 25-frame lookahead is fully drained during warmup so its latency does not inflate the measurement.
- **Flush included**: the timer runs from the first `encode()` call through the end of the flush loop. VP9 quality mode emits most packets during flush, so omitting it would produce misleadingly high fps numbers.
- **Single-threaded by default**: `--threads=1` isolates single-core throughput. Increase with `--threads=N` to measure multi-threaded scaling.
