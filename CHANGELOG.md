# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- VP8 decoder implementation (`Vp8Decoder`).

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

[Unreleased]: https://gitlab.com/org.seuffert/panvpx/-/compare/v0.0.1...HEAD
[0.0.1]: https://gitlab.com/org.seuffert/panvpx/-/releases/tag/v0.0.1
