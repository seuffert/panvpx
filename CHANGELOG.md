# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed
- Replaced all `assertTrue(count > 0, ...)` test assertions with exact `assertEquals(expected, count, ...)`
  to catch missing frames rather than merely confirming non-empty output.
- Replaced single `encoder.flush()` calls with `do { … } while (!flushed.isEmpty())` drain loops
  in all unit/integration tests to fully drain the VP9 lookahead buffer before asserting counts.

## [0.3.0] — 2026-05-26

### Added
- New `:benchmark` Gradle subproject with a CLI benchmark runner for VP8/VP9 encoder
  and decoder throughput measurement.
- `EncoderBenchmark` and `DecoderBenchmark` measure encode/decode throughput in fps;
  flush is included in elapsed time and warmup fully drains the codec lookahead before
  measurement begins.
- `SyntheticFrameSource` generates temporally varied I420 frames using three independent
  interference waves, a 4×4 XOR texture, and LCG noise — resistant to trivial motion
  prediction shortcuts.
- `--synthetic` flag in the benchmark CLI to use generated frames instead of the built-in clip.
- `--help` / no-args shows full usage text with OPTIONS and EXAMPLES.
- Unknown CLI arguments are a hard error (exit 1) instead of being silently ignored.
- `VpxEncoderConfig` extended with `threads`, `preset` (quality/realtime), `cpuUsed`,
  `rowMt`, `tileColumns`, and `tokenPartitions` for benchmark parametrization.
- VP8 decoding and VP9 encoding/decoding examples added to README.

### Changed
- Benchmark defaults: `--frames=25`, `--warmup=3`.

### Removed
- Pipeline benchmark mode: sequential encode→decode adds no information beyond
  the individual encoder and decoder results.

## [0.2.0] — 2026-05-26

### Added
- `AbstractVpxEncoder` and `AbstractVpxDecoder` abstract base classes in `org.seuffert.panvpx.core`,
  containing all shared VP8/VP9 encoder and decoder logic.
- `VpxEncoderConfig` and `VpxDecoderConfig` moved from `org.seuffert.panvpx.vp8` to
  `org.seuffert.panvpx.core` for reuse across codecs.
- `Vp9Encoder` in `org.seuffert.panvpx.vp9` — VP9 video encoder backed by `libvpx`
  `vpx_codec_vp9_cx` interface.
- `Vp9Decoder` in `org.seuffert.panvpx.vp9` — VP9 video decoder backed by `libvpx`
  `vpx_codec_vp9_dx` interface.
- `AbstractVpxEncoder.VPX_EFLAG_FORCE_KF` replaces the former `Vp8Encoder.VPX_EFLAG_FORCE_KF`
  so the constant is accessible for both VP8 and VP9 encoders.
- `Vp9EncoderTest` and `Vp9DecoderTest` with full coverage mirroring the VP8 test suites.
- Additional test coverage verifying `getCodecName()` and exact native libvpx error codes in `VpxException`.
- JPMS module now exports `org.seuffert.panvpx.vp9`.

### Changed
- `Vp8Encoder` and `Vp8Decoder` refactored as thin `final` subclasses of the new abstract bases;
  public API is unchanged.
- `VpxEncoderConfig` and `VpxDecoderConfig` are now in `org.seuffert.panvpx.core` (package change).
- README "Cousin Project" section renamed to "See Also".

### CI
- Added CI pipeline `release` stage: pushing a `vX.Y.Z` tag now automatically creates a GitLab
  Release (with CHANGELOG link and JAR asset) once the `build_and_test` job succeeds.
- Added Latest Release, CI pipeline status, Maven Central, and Java 25+ badges to README.

## [0.1.0] — 2026-05-25

### Added
- VP8 decoder implementation (`Vp8Decoder`) using Project Panama FFM API.
- `VpxDecoderConfig` record for basic decoder configuration.
- `VpxImage.toByteArray()` to efficiently copy the native I420 planes (respecting strides) into a contiguous Java `byte[]`.
- `VpxImage.getPlane()` and `VpxImage.getStride()` for zero-copy access to the strided Y, U, and V memory planes.
- Codec-owned native frame support via `VpxImage.createCodecOwned()` to safely wrap libvpx decoder buffers without freeing them prematurely on close.
- `VpxPacket` now exposes all four libvpx frame-flag constants (`VPX_FRAME_IS_KEY`,
  `VPX_FRAME_IS_DROPPABLE`, `VPX_FRAME_IS_INVISIBLE`, `VPX_FRAME_IS_FRAGMENT`) with
  corresponding helper methods (`isKeyFrame()`, `isDroppable()`, `isInvisible()`, `isFragment()`).
- Extended `Vp8DecoderTest` with 5 additional tests covering multi-frame streaming, the `byte[]`
  decode path, plane/stride accessor correctness, invalid plane index bounds, and cross-thread
  decoder usage (`Arena.ofShared()` contract).

### Fixed
- `VpxImage.getPlane()` and `VpxImage.getStride()` incorrectly accepted plane index 3 (the
  unused native alpha slot in the C struct). Valid range is now restricted to [0, 2] only.
- `Vp8Decoder.decode()` Javadoc (all three overloads) lacked an explicit lifetime warning.
  The returned `VpxImage` instances wrap libvpx-internal buffers that are only valid until the
  next `decode()` call or `close()`; this is now clearly stated.

## [0.0.1] — 2026-05-25

### Added
- Initial VP8 encoder implementation (`Vp8Encoder`) using Project Panama FFM API.
- `VpxEncoderConfig` record with convenience constructors for common encoder configurations.
- `VpxImage` with `fromByteArray()` (copy path) and `fromMemorySegment()` (zero-copy path).
- `VpxPacket` with `toByteArray()` (copy path) and `asDirectBuffer()` (zero-copy path).
- `VpxException` for native libvpx error propagation with the underlying error code.
- `PanVpx` utility class with `isLibVpxAvailable()`, `getVersionString()`, and `getBuildConfigString()`.
- `jextract`-generated FFM bindings for `libvpx` in `org.seuffert.panvpx.ffi` (internal, not exported).
- JPMS module `org.seuffert.panvpx`; internal FFM bindings are not exported.
- Full static analysis enforcement: Spotless (Google Java Format AOSP 1.27.0), ErrorProne 2.x,
  NullAway, Checkstyle, SpotBugs, and PMD — all warnings treated as errors.
- JUnit 5 test suite covering encoder lifecycle, synthetic frame encoding, and edge cases.
- `Vp8Encoder.VPX_EFLAG_FORCE_KF` public constant for requesting forced key frames without
  importing the internal FFM bindings package.

### Fixed
- `VpxImage.fromMemorySegment()` used `Arena.ofConfined()` for the struct arena, which bound
  the `VpxImage` to the creating thread and caused `WrongThreadException` when `close()` was
  called from a different thread (e.g. a pipeline thread). Changed to `Arena.ofShared()`.
- `VpxPacket.asDirectBuffer()` lacked a documented lifetime contract. The returned `ByteBuffer`
  points directly into libvpx-internal memory that is invalidated on the next `encode()` or
  `flush()` call; the Javadoc now carries an explicit use-after-free warning.
- `Vp8Encoder` constructor leaked the `Arena.ofShared()` and all its native allocations when
  libvpx initialization failed (e.g. invalid width/height). Fixed with a `try-finally` / success
  flag that closes the arena on any exception during construction.
- `close()` was not idempotent in `VpxImage` or `Vp8Encoder`. A second `close()` call forwarded
  directly to `vpx_img_free` / `vpx_codec_destroy` on already-freed memory, causing undefined
  behaviour. Both classes now use `AtomicBoolean.compareAndSet` so that subsequent calls are
  silent no-ops.
- `Vp8Encoder.flush()` passed `VPX_DL_REALTIME` (1 ms deadline) to `vpx_codec_encode`, which
  caused libvpx to skip frames still held in its lookahead buffer. Changed to `VPX_DL_BEST_QUALITY`
  (deadline = 0) so all delayed frames are drained completely on flush.

[Unreleased]: https://gitlab.com/org.seuffert/panvpx/-/compare/v0.3.0...HEAD
[0.3.0]: https://gitlab.com/org.seuffert/panvpx/-/compare/v0.2.0...v0.3.0
[0.2.0]: https://gitlab.com/org.seuffert/panvpx/-/compare/v0.1.0...v0.2.0
[0.1.0]: https://gitlab.com/org.seuffert/panvpx/-/compare/v0.0.1...v0.1.0
[0.0.1]: https://gitlab.com/org.seuffert/panvpx/-/releases/tag/v0.0.1
