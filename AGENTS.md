# AI Agent Instructions for PanVPX

## Project Context
- **Language**: Java
- **JDK Version**: JDK 25+
- **Native Interop**: Project Panama (Foreign Function & Memory API - FFM)
- **Target Native Library**: `libvpx`
- **Goal**: Provide an easy-to-use VP8/VP9 encoder/decoder API that avoids zero-copy complexity for beginners (using standard `byte[]`), but provides an "easy direct memory" path (using `MemorySegment`) for advanced users to avoid unnecessary array copies.

## Best Practices & Guidelines

### 1. Memory Management (Critical)
- **Always use `Arena`**: Native memory MUST be allocated using an `Arena`. Never use `Arena.global()` in library code.
- **Per-call temporary allocations**: Open `Arena.ofConfined()` with a `try-with-resources` block inside the method body. Never use `Arena.ofShared()` for per-call allocations.
- **Long-lived native buffers (Class-bound Arenas)**: If an encoder/decoder spans multiple calls, store the `Arena` as a `private final` field and close it in the `close()` method. Use `Arena.ofShared()` for these long-lived arenas so that the instance can be created, used, and closed on different threads (the common pattern in real-time streaming pipelines).
- **Thread-safety consequence**: `Arena.ofShared()` removes thread affinity from long-lived native buffers, allowing create/use/close to happen on different threads. However, **`libvpx` state is NOT concurrently thread-safe**: calling two methods simultaneously from separate threads on the same instance will silently corrupt encoder/decoder state. External serialization (e.g., a single-thread executor or `synchronized` block) is required if the same instance is shared across threads.

### 2. Project Panama (FFM API) specific
- **Java 25 toolchain**: Use current FFM API idioms (`MemorySegment`, `Arena`, `FunctionDescriptor`, `Linker`, etc.).
- Do not introduce `sun.misc.Unsafe`, JNI, or any other native-access mechanism.
- Use `MemorySegment.ofBuffer()` for mapping Java direct `ByteBuffer` instances.
- Use `MemorySegment.copy()` for bulk data copying between heap `byte[]` arrays and native memory.
- Generated `jextract` bindings reside in `org.seuffert.panvpx.ffi`. Treat these as internal. Hide all `MemorySegment` pointers, `ValueLayout` constants, and `jextract` structs from the public API.

### 3. Java Idioms & JPMS
- Favor `record` classes for configuration (e.g., `VpxEncoderConfig`).
- Provide overloaded constructors to simulate default parameters and simplify the API.
- Map native C error codes (`vpx_codec_err_t`) to a custom Java Exception (e.g., `class VpxException extends RuntimeException`).
- **JPMS Module**: The library is built as a Java Platform Module System (JPMS) module named `org.seuffert.panvpx`. 
- **Internal Bindings**: Only export the top-level, `core`, `vp8`, and `vp9` packages. NEVER export the `org.seuffert.panvpx.ffi` package.

### 4. Implementation Iteration
- Development starts strictly with the VP8 Encoder before moving to VP8 Decoder, and finally VP9.
- Design base classes (`AbstractVpxEncoder`) with reusability for VP9 in mind.
- The main code resides in the `lib` module.

### 5. Roadmap
- The current development plan and iterative phases are tracked in `docs/ROADMAP.md`. 
- Always check `docs/ROADMAP.md` before starting a new phase and update it when a phase is completed.

## Versioning / Release process

To bump the project version (e.g. to `vX.Y.Z`):

1. **Verify the build is green** — run `./gradlew build --rerun-tasks` and confirm
   `BUILD SUCCESSFUL`.
2. **Update `gradle.properties`** — change `version=<old>` to `version=<new>` (no `v` prefix).
3. **Update `README.md`** — update the version in both the Maven `<version>` tag and the
   Gradle `implementation(...)` string.
4. **Update `CHANGELOG.md`**:
   - Retrieve commits since the last tag: `git log <last-tag>..HEAD --oneline` (if the tag
     does not exist locally, just use `git log --oneline | head -20` and find the bump commit).
   - Promote the `[Unreleased]` section to `[X.Y.Z] — YYYY-MM-DD`, adding any commits not
     yet documented (dependency upgrades, CI fixes, etc.).
   - Add a new empty `## [Unreleased]` section above it.
   - Update the comparison links at the bottom of the file.
5. **Check `AGENTS.md`** — update if any new conventions were introduced in this release.
6. **Verify the build is still green** — run `./gradlew build --rerun-tasks` again.
7. **Commit** — `git add -A && git commit -m "bumping version to vX.Y.Z"`.
8. **Tag** (triggers CI release) — `git tag vX.Y.Z && git push --follow-tags`.
