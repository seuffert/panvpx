/**
 * Codec-agnostic core types shared by both the VP8 and VP9 implementations.
 *
 * <h2>Data types</h2>
 *
 * <ul>
 *   <li>{@link org.seuffert.panvpx.core.VpxImage} &mdash; wraps a raw, uncompressed I420 video
 *       frame backed by off-heap native memory. Obtain instances via its static factory methods
 *       ({@link org.seuffert.panvpx.core.VpxImage#fromByteArray fromByteArray}, {@link
 *       org.seuffert.panvpx.core.VpxImage#fromMemorySegment fromMemorySegment}) and always close in
 *       a try-with-resources block.
 *   <li>{@link org.seuffert.panvpx.core.VpxPacket} &mdash; wraps a compressed bitstream packet
 *       produced by an encoder. Call {@link org.seuffert.panvpx.core.VpxPacket#toByteArray()
 *       toByteArray()} for a safe copy or {@link
 *       org.seuffert.panvpx.core.VpxPacket#asDirectBuffer() asDirectBuffer()} for a zero-copy view
 *       (see the lifetime warning on that method).
 * </ul>
 *
 * <h2>Configuration</h2>
 *
 * <ul>
 *   <li>{@link org.seuffert.panvpx.core.VpxEncoderConfig} &mdash; immutable configuration for a VP8
 *       or VP9 encoder: resolution, target bitrate, thread count, encoding deadline, CPU-speed
 *       trade-off, and codec-specific options.
 *   <li>{@link org.seuffert.panvpx.core.VpxDecoderConfig} &mdash; immutable configuration for a VP8
 *       or VP9 decoder: thread count and optional pre-declared frame dimensions.
 * </ul>
 *
 * <h2>Abstract base classes</h2>
 *
 * <ul>
 *   <li>{@link org.seuffert.panvpx.core.AbstractVpxEncoder} &mdash; base encoder that manages the
 *       native codec context, frame encoding, packet extraction, and teardown. Concrete subclasses:
 *       {@link org.seuffert.panvpx.vp8.Vp8Encoder}, {@link org.seuffert.panvpx.vp9.Vp9Encoder}.
 *   <li>{@link org.seuffert.panvpx.core.AbstractVpxDecoder} &mdash; base decoder that manages the
 *       native codec context, frame decoding, image extraction, and teardown. Concrete subclasses:
 *       {@link org.seuffert.panvpx.vp8.Vp8Decoder}, {@link org.seuffert.panvpx.vp9.Vp9Decoder}.
 * </ul>
 *
 * <h2>Error handling</h2>
 *
 * <p>{@link org.seuffert.panvpx.core.VpxException} is a {@link RuntimeException} thrown whenever a
 * libvpx native call returns a non-OK status. Its {@link
 * org.seuffert.panvpx.core.VpxException#code() code()} method exposes the raw {@code
 * vpx_codec_err_t} value from the C library for diagnostic purposes.
 */
package org.seuffert.panvpx.core;
