# ROADMAP

## Plan: panvpx FFM Wrapper

Iterative implementation of a Java/JDK25 wrapper for libvpx using Project Panama FFM, prioritizing an "easy direct memory" approach.

**Steps**

1. **Phase 1: Foundation & Bindings** ✅ Completed
   - Set up Gradle project targeting JDK 25 in a `lib` module
   - Generate FFM bindings using `jextract` for `vpx_encoder.h`, `vpx_image.h`, `vp8cx.h`, and `vp8dx.h`
   - Setup JPMS (`module-info.java`) to encapsulate bindings.
2. **Phase 2: Core Memory Abstractions** ✅ Completed
   - Implement `VpxImage` (input) and `VpxPacket` (output) in Java, using `MemorySegment`
3. **Phase 3: VP8 Encoder** ✅ Completed
   - Create `VpxEncoderConfig` record with convenience constructors
   - Implement `Vp8Encoder` with `Arena`-scoped lifecycle, `.encode()`, and `.flush()`
   - JUnit 5 test suite covering synthetic frame encoding and lifecycle edge cases
4. **Phase 4: VP8 Decoder** (*depends on 3*) ✅ In Progress
   - Fix jextract bindings to include `/usr/include/vpx/vpx_decoder.h`
   - Update `VpxImage` with codec-owned frame wrapper, `toByteArray()`, and zero-copy accessors
   - Implement `VpxDecoderConfig` and `Vp8Decoder`
   - Test end-to-end encode and decode in `Vp8DecoderTest`
5. **Phase 5: VP9 & Abstraction** (*depends on 4*)
   - Extract shared logic into `AbstractVpxEncoder` / `AbstractVpxDecoder`
   - Add `Vp9Encoder` and `Vp9Decoder` implementations

**Relevant files**
- `lib/build.gradle.kts` — Project config, JDK 25 setup, static analysis configuration
- `lib/src/main/java/org/seuffert/panvpx/ffi/...` — Generated jextract bindings (internal)
- `lib/src/main/java/org/seuffert/panvpx/core/VpxImage.java` — Core memory abstractions
- `lib/src/main/java/org/seuffert/panvpx/vp8/Vp8Encoder.java` — VP8 encoder
- `lib/src/test/java/org/seuffert/panvpx/vp8/Vp8EncoderTest.java` — VP8 encoder tests

**Decisions**
- Language: Pure Java with JDK 25 (refactored from Kotlin).
- Architecture: Unified library for VP8/VP9, Encoder/Decoder inside `lib` module.
- Memory: "Easy direct memory" — users pass `byte[]` (wrapper handles copy) or `MemorySegment`/`ByteBuffer` (direct native passing, zero-copy).
- Quality: Static analysis enforced on every build (Spotless, ErrorProne, NullAway, Checkstyle, SpotBugs, PMD). All warnings treated as errors.
