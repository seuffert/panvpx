# panvpx

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

panvpx is a lightweight, JNI-free Java wrapper for [libvpx](https://www.webmproject.org/code/) (VP8/VP9 video codec). It uses the [Project Panama Foreign Function & Memory (FFM) API](https://openjdk.org/jeps/454) — no JNI glue code required.

Developed by [Oliver Seuffert](https://gitlab.com/org.seuffert).

## Features

- **JNI-free**: Native calls via Project Panama FFM — no JNI glue code, no native compilation step.
- **Simple and advanced paths**: Pass standard `byte[]` arrays for ease of use; pass `MemorySegment` or direct `ByteBuffer` for zero-copy performance.
- **VP8 and VP9**: Single library for encoding and decoding (VP8 encoding and decoding are stable; VP9 support is in progress).
- **Java 25+**: Uses `record` types, `try-with-resources`, and `Arena`-scoped memory for safe native memory management.
- **JPMS Ready**: Fully modularized as `org.seuffert.panvpx`; internal FFM bindings are never exported.
- **Strict Quality**: Every build verified by Spotless, ErrorProne, NullAway, Checkstyle, SpotBugs, and PMD — all warnings treated as errors.

## Status

> **Work in Progress** — VP8 encoding and decoding are complete and stable. VP9 support is upcoming (see [docs/ROADMAP.md](docs/ROADMAP.md)).

## Requirements

- JDK 25 or higher
- **libvpx 1.16.0** — the FFI bindings are generated from `libvpx 1.16.0` headers
  (`VPX_ENCODER_ABI_VERSION = 39`). Using a different ABI version will cause
  `VpxException` at encoder/decoder initialization.
  - Debian/Ubuntu 26.04 (Resolute) or later: `sudo apt install libvpx-dev`
  - macOS (Homebrew): `brew install libvpx` (verify with `pkg-config --modversion vpx`)

## Installation

### Maven

```xml
<dependency>
  <groupId>org.seuffert</groupId>
  <artifactId>panvpx</artifactId>
  <version>0.0.1</version>
</dependency>
```

### Gradle (Kotlin DSL)

```kotlin
implementation("org.seuffert:panvpx:0.0.1")
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
import org.seuffert.panvpx.vp8.VpxEncoderConfig;

int width = 640, height = 480;
VpxEncoderConfig config = new VpxEncoderConfig(width, height, /* bitrateKbps */ 512, /* threads */ 2);

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

## Contributing

Contributions are welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a merge request.

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for a history of notable changes.

## Cousin Project

[PanOpus](https://gitlab.com/org.seuffert/panopus) — a Project Panama FFM wrapper for the Opus audio codec, following the same design principles.

## License

Licensed under the [Apache License, Version 2.0](LICENSE).
