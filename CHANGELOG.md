# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- VP8 decoder implementation (`Vp8Decoder`).

## [0.0.1] — Unreleased

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

[Unreleased]: https://gitlab.com/org.seuffert/panvpx/-/compare/v0.0.1...HEAD
[0.0.1]: https://gitlab.com/org.seuffert/panvpx/-/releases/tag/v0.0.1
