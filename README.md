# PanVPX

PanVPX is a lightweight, JNI-free wrapper for `libvpx` (VP8/VP9 Video Codec), written in pure Java and utilizing Java's Project Panama (FFM API) on JDK 25+.

Developed by [Oliver Seuffert](https://gitlab.com/org.seuffert).

## Features
- **Project Panama FFM API**: Native calls without the overhead and complexity of JNI.
- **Easy Direct Memory**: Designed with an easy-to-use API that accepts standard `byte[]` arrays, while also natively supporting Java `MemorySegment` and direct `ByteBuffer`s to eliminate unnecessary memory copying ("zero-copy" friendly).
- **Unified Architecture**: Single library for VP8 and VP9 encoding and decoding.
- **Java 25+**: Uses modern Java features like `record` classes and `try-with-resources` to guarantee native memory safety with FFM `Arena`s.
- **JPMS Ready**: Fully modularized (`org.seuffert.panvpx`), keeping internal FFM bindings safely encapsulated.

## Cousin Project
This library follows similar design principles to [PanOpus](https://gitlab.com/org.seuffert/panopus), a Project Panama wrapper for the Opus audio codec.

## Status
🚧 **Work in Progress**: Iterative development currently focused on VP8 Encoding.

## Usage
(API Documentation coming soon as the encoder matures)

## Requirements
- JDK 25 or higher
- System `libvpx` installed (e.g., `apt install libvpx-dev` or `brew install libvpx`)

## License
Open Source.
