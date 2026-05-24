# ROADMAP

## Plan: panvpx FFM Wrapper

Iterative implementation of a Java/JDK25 wrapper for libvpx using Project Panama FFM, prioritizing an "easy direct memory" approach.

**Steps**
1. **Phase 1: Foundation & Bindings** (Completed)
   - Set up Gradle project targeting JDK 25 in a `lib` module
   - Generate FFM bindings using `jextract` for `vpx_encoder.h`, `vpx_image.h`, and `vp8cx.h`
   - Setup JPMS (`module-info.java`) to encapsulate bindings.
2. **Phase 2: Core Memory Abstractions** (Completed)
   - Implement `VpxImage` (input) and `VpxPacket` (output) in Java, using `MemorySegment`
3. **Phase 3: VP8 Encoder** (Completed)
   - Create `VpxEncoderConfig` wrapper (Java `record`)
   - Implement `Vp8Encoder` with `Arena` scoped lifecycle and `.encode()` loop
4. **Phase 4: VP8 Decoder** (*depends on 3*)
   - Add decoder FFM bindings
   - Implement `Vp8Decoder`
5. **Phase 5: VP9 & Abstraction** (*depends on 4*)
   - Extract shared logic into `AbstractVpxEncoder/Decoder`
   - Add VP9 bindings and implementation

**Relevant files**
- `lib/build.gradle.kts` — Project config, JDK 25 setup
- `lib/src/main/java/org/seuffert/panvpx/ffi/...` — Generated bindings
- `lib/src/main/java/org/seuffert/panvpx/core/VpxImage.java` — Core memory wrappers
- `lib/src/main/java/org/seuffert/panvpx/vp8/Vp8Encoder.java` — First implementation target

**Verification**
1. Verify `jextract` generates correct bindings against native `libvpx` headers (Completed)
2. Write unit tests encoding a dummy/synthetic YUV frame and asserting output payload exists (Completed)
3. Ensure no memory leaks by checking `Arena` scopes (Completed)

**Decisions**
- Language: Pure Java with JDK 25 (Refactored from Kotlin).
- Architecture: Unified library for VP8/VP9, Encoder/Decoder inside `lib` module.
- Memory: "Easy direct memory" - users pass `byte[]` (wrapper handles copy) or `MemorySegment`/`ByteBuffer` (direct native passing).
- Quality: Static analysis enforced on every build (Spotless, ErrorProne, NullAway, Checkstyle, SpotBugs, PMD). All warnings treated as errors.
