# AI Agent Instructions for PanVPX

## Project Context
- **Language**: Java
- **JDK Version**: JDK 25+
- **Native Interop**: Project Panama (Foreign Function & Memory API - FFM)
- **Target Native Library**: `libvpx`
- **Goal**: Provide an easy-to-use VP8/VP9 encoder/decoder API that avoids zero-copy complexity for beginners (using standard `byte[]`), but provides an "easy direct memory" path (using `MemorySegment`) for advanced users to avoid unnecessary array copies.

## Best Practices & Guidelines

### 1. Memory Management (Critical)
- **Always use `Arena`**: Native memory MUST be allocated using an `Arena`. 
- **Java `try-with-resources`**: Whenever an `Arena.ofConfined()` is opened for temporary scope, wrap it in a `try-with-resources` block to ensure memory is properly cleaned up.
- **Class-bound Arenas**: If an encoder/decoder class holds native state (`vpx_codec_ctx_t`), the class must implement `AutoCloseable`. The class should instantiate its own `Arena` and close it in the `close()` method.
- Never let native memory leak into the Garbage Collector's hands unmanaged.

### 2. Project Panama (FFM API) specific
- Use `MemorySegment.ofBuffer()` for mapping Java direct `ByteBuffer` instances.
- Use `MemorySegment.copy()` for bulk data copying between heap `byte[]` arrays and native memory.
- Generated `jextract` bindings reside in `org.seuffert.panvpx.ffi`. Treat these as internal. Hide all `MemorySegment` pointers, `ValueLayout` constants, and `jextract` structs from the public API.

### 3. Java Idioms
- Favor `record` classes for configuration (e.g., `VpxEncoderConfig`).
- Provide overloaded constructors to simulate default parameters and simplify the API.
- Map native C error codes (`vpx_codec_err_t`) to a custom Java Exception (e.g., `class VpxException extends RuntimeException`).

### 4. Implementation Iteration
- Development starts strictly with the VP8 Encoder before moving to VP8 Decoder, and finally VP9.
- Design base classes (`AbstractVpxEncoder`) with reusability for VP9 in mind.
- The main code resides in the `lib` module.
