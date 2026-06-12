# panvpx

[![Latest Release](https://img.shields.io/gitlab/v/release/org.seuffert%2Fpanvpx?label=Latest%20Release)](https://gitlab.com/org.seuffert/panvpx/-/releases)
[![CI](https://img.shields.io/gitlab/pipeline-status/org.seuffert%2Fpanvpx?branch=main&label=CI)](https://gitlab.com/org.seuffert/panvpx/-/pipelines)
[![Maven Central](https://img.shields.io/maven-central/v/org.seuffert/panvpx)](https://central.sonatype.com/artifact/org.seuffert/panvpx)
[![Java](https://img.shields.io/badge/Java-25%2B-orange?logo=openjdk)](https://openjdk.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Javadoc](https://javadoc.io/badge2/org.seuffert/panvpx/javadoc.svg)](https://javadoc.io/doc/org.seuffert/panvpx)

panvpx is a lightweight, JNI-free Java wrapper for [libvpx](https://www.webmproject.org/code/) (VP8/VP9 video codec). It uses the [Project Panama Foreign Function & Memory (FFM) API](https://openjdk.org/jeps/454) — no JNI glue code required.

Developed by [Oliver Seuffert](https://gitlab.com/org.seuffert).

## Features

- **JNI-free**: Native calls via Project Panama FFM — no JNI glue code, no native compilation step.
- **Simple and advanced paths**: Pass standard `byte[]` arrays for ease of use; pass `MemorySegment` or direct `ByteBuffer` for zero-copy performance.
- **VP8 and VP9**: Full encoding and decoding support for both VP8 and VP9 via a shared `AbstractVpxEncoder` / `AbstractVpxDecoder` design.
- **Java 25+**: Uses `record` types, `try-with-resources`, and `Arena`-scoped memory for safe native memory management.
- **JPMS Ready**: Fully modularized as `org.seuffert.panvpx`; internal FFM bindings are never exported.
- **Strict Quality**: Every build verified by Spotless, ErrorProne, NullAway, Checkstyle, SpotBugs, and PMD — all warnings treated as errors.

## Status

> VP8 and VP9 encoding and decoding are implemented and stable. See [docs/ROADMAP.md](docs/ROADMAP.md) for future plans.

## Requirements

- JDK 25 or higher
- **libvpx 1.16.0** — the FFI bindings are generated from `libvpx 1.16.0` headers
  (`VPX_ENCODER_ABI_VERSION = 39`). Using a different ABI version will cause
  `VpxException` at encoder/decoder initialization.
  - Debian/Ubuntu 26.04 (Resolute) or later: `sudo apt install libvpx-dev`
  - macOS (Homebrew): `brew install libvpx` (verify with `pkg-config --modversion vpx`)

### Custom library path

panvpx loads `libvpx` via the Panama FFM `SymbolLookup.libraryLookup()` call, which delegates directly to the OS dynamic linker (`dlopen` on Linux/macOS). The JVM flag `-Djava.library.path` has no effect here — it only applies to `System.loadLibrary()`.

**Option 1 — JVM system property (recommended)**

Set `panvpx.libvpx.path` to the full path of the shared library file. This is the cleanest, purely Java approach and works on all platforms:

```
java -Dpanvpx.libvpx.path=/opt/myapp/lib/libvpx.so \
     --enable-native-access=org.seuffert.panvpx -jar myapp.jar
```

```
# macOS
java -Dpanvpx.libvpx.path=/opt/myapp/lib/libvpx.dylib \
     --enable-native-access=org.seuffert.panvpx -jar myapp.jar
```

When this property is set, the OS-level library search is bypassed entirely — the library at the given path is loaded directly, even if a different `libvpx` is installed system-wide.

**Option 2 — OS-level mechanisms**

Use these when you cannot control the JVM command line (e.g. inside an application server) or when you need the override to apply process-wide.

**Linux**

```
# Prepend the directory that contains your custom libvpx.so
LD_LIBRARY_PATH=/opt/myapp/lib:$LD_LIBRARY_PATH java \
    --enable-native-access=org.seuffert.panvpx -jar myapp.jar

# Or preload the exact file — takes precedence even over the linker cache
LD_PRELOAD=/opt/myapp/lib/libvpx.so java \
    --enable-native-access=org.seuffert.panvpx -jar myapp.jar
```

**macOS**

```
DYLD_LIBRARY_PATH=/opt/myapp/lib:$DYLD_LIBRARY_PATH java \
    --enable-native-access=org.seuffert.panvpx -jar myapp.jar
```

> **Note (macOS):** `DYLD_LIBRARY_PATH` is silently ignored for processes protected by System Integrity Protection (SIP). If that is a concern, use the `panvpx.libvpx.path` property instead.

## Installation

### Maven

```xml
<dependency>
  <groupId>org.seuffert</groupId>
  <artifactId>panvpx</artifactId>
  <version>0.3.7</version>
</dependency>
```

### Gradle (Kotlin DSL)

```kotlin
implementation("org.seuffert:panvpx:0.3.7")
```

### JVM flag

panvpx uses the FFM API to call native code. Add the following JVM argument to your application:

```
--enable-native-access=org.seuffert.panvpx
```

If your application is not yet fully modularized, use `ALL-UNNAMED` instead:

```
--enable-native-access=ALL-UNNAMED
```

## Quick Start

### Check availability

```java
import org.seuffert.panvpx.PanVpx;

if (!PanVpx.isLibVpxAvailable()) {
    throw new IllegalStateException("libvpx not found on this system");
}
System.out.println("libvpx version: " + PanVpx.getVersionString());
```

### VP8 encoding — simple path (`byte[]`)

```java
import org.seuffert.panvpx.core.VpxImage;
import org.seuffert.panvpx.core.VpxPacket;
import org.seuffert.panvpx.vp8.Vp8Encoder;
import org.seuffert.panvpx.core.VpxEncoderConfig;

int width = 640, height = 480;
VpxEncoderConfig config = VpxEncoderConfig.builder(VpxEncoderConfig.Codec.VP8, width, height)
        .targetBitrateKbps(512)
        .threads(2)
        .build();

try (Vp8Encoder encoder = new Vp8Encoder(config)) {
    // Raw I420 frame: Y plane (width*height) + U plane (width*height/4) + V plane (width*height/4)
    byte[] i420Frame = new byte[width * height * 3 / 2];
    // ... fill i420Frame with pixel data ...

    try (VpxImage image = VpxImage.fromByteArray(i420Frame, width, height)) {
        List<VpxPacket> packets = encoder.encode(image, /* pts */ 0L, /* duration */ 1L, /* flags */ 0L);
        for (VpxPacket packet : packets) {
            byte[] encoded = packet.toByteArray();
            boolean isKey = packet.isKeyFrame();
            // send or store encoded bytes...
        }
    }

    // Flush any delayed frames at end of stream
    for (VpxPacket packet : encoder.flush()) {
        byte[] encoded = packet.toByteArray();
    }
}
```

### VP8 encoding — advanced path (zero-copy with `MemorySegment`)

```java
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

// nativeBuffer is an already-allocated off-heap MemorySegment (no copy)
MemorySegment nativeBuffer = ...;

try (VpxImage image = VpxImage.fromMemorySegment(nativeBuffer, width, height)) {
    List<VpxPacket> packets = encoder.encode(image, pts, duration, 0L);
    for (VpxPacket packet : packets) {
        // Zero-copy view — valid until the next encode/flush call
        ByteBuffer direct = packet.asDirectBuffer();
        // consume direct buffer...
    }
}
```

### VP8 decoding — simple path (`byte[]`)

```java
import java.util.List;
import org.seuffert.panvpx.core.VpxDecoderConfig;
import org.seuffert.panvpx.core.VpxImage;
import org.seuffert.panvpx.vp8.Vp8Decoder;

try (Vp8Decoder decoder = new Vp8Decoder(new VpxDecoderConfig())) {
    byte[] encoded = ...; // VP8 bitstream packet
    List<VpxImage> frames = decoder.decode(encoded);
    for (VpxImage frame : frames) {
        // Tightly-packed I420 copy — safe to use after the next decode() call
        byte[] i420 = frame.toByteArray();
        int w = frame.width();
        int h = frame.height();
        // use i420...
    }
}
```

### VP9 encoding — simple path (`byte[]`)

`Vp9Encoder` accepts the same `VpxEncoderConfig` as `Vp8Encoder` and exposes an identical API. A convenience constructor is also available:

```java
import java.util.List;
import org.seuffert.panvpx.core.VpxImage;
import org.seuffert.panvpx.core.VpxPacket;
import org.seuffert.panvpx.vp9.Vp9Encoder;

int width = 640, height = 480;

try (Vp9Encoder encoder = new Vp9Encoder(width, height)) {
    byte[] i420Frame = new byte[width * height * 3 / 2];
    // ... fill i420Frame with pixel data ...

    try (VpxImage image = VpxImage.fromByteArray(i420Frame, width, height)) {
        List<VpxPacket> packets = encoder.encode(image, /* pts */ 0L, /* duration */ 1L, /* flags */ 0L);
        for (VpxPacket packet : packets) {
            byte[] encoded = packet.toByteArray();
            // send or store encoded bytes...
        }
    }

    // Flush any delayed frames at end of stream
    for (VpxPacket packet : encoder.flush()) {
        byte[] encoded = packet.toByteArray();
    }
}
```

### VP9 decoding — simple path (`byte[]`)

`Vp9Decoder` also provides a no-argument constructor for single-threaded decoding with auto-detected dimensions:

```java
import java.util.List;
import org.seuffert.panvpx.core.VpxImage;
import org.seuffert.panvpx.vp9.Vp9Decoder;

try (Vp9Decoder decoder = new Vp9Decoder()) {
    byte[] encoded = ...; // VP9 bitstream packet
    List<VpxImage> frames = decoder.decode(encoded);
    for (VpxImage frame : frames) {
        byte[] i420 = frame.toByteArray();
        // use i420...
    }
}
```

## Building from Source

```bash
git clone https://gitlab.com/org.seuffert/panvpx.git
cd panvpx
./gradlew build
```

All static analysis checks run as part of `build`. To regenerate the jextract FFM bindings from the system libvpx headers (Fish shell):

```fish
rm -rf lib/src/main/java/org/seuffert/panvpx/ffi/*; and \
./jextract/bin/jextract -t org.seuffert.panvpx.ffi --output lib/src/main/java -l vpx --header-class-name VpxFFI \
  /usr/include/vpx/vp8cx.h /usr/include/vpx/vp8dx.h /usr/include/vpx/vpx_decoder.h
```

To check for (stable-only) dependency updates in `gradle/libs.versions.toml`, use the
[version-catalog-update](https://github.com/littlerobots/version-catalog-update-plugin) plugin
(release candidates and snapshots are never selected automatically):

```fish
# Report available updates without modifying the file
./gradlew versionCatalogUpdate --check --no-configuration-cache
# Update the catalog in place
./gradlew versionCatalogUpdate --no-configuration-cache
```

## Contributing

Contributions are welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a merge request.

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for a history of notable changes.

## See Also

[PanOpus](https://gitlab.com/org.seuffert/panopus) — a Project Panama FFM wrapper for the Opus audio codec, following the same design principles.

## License

Licensed under the [Apache License, Version 2.0](LICENSE).
