/**
 * Top-level package of the panvpx library &mdash; a lightweight, JNI-free Java wrapper for <a
 * href="https://www.webmproject.org/code/">libvpx</a> (VP8/VP9 video codec).
 *
 * <p>panvpx uses the Project Panama Foreign Function &amp; Memory (FFM) API (JDK&nbsp;25+) to call
 * native libvpx functions directly from Java without any JNI glue code or native compilation step.
 *
 * <h2>Library structure</h2>
 *
 * <ul>
 *   <li>{@link org.seuffert.panvpx.PanVpx} &mdash; utility entry-point: check native library
 *       availability and query version information.
 *   <li>{@link org.seuffert.panvpx.core} &mdash; codec-agnostic data types ({@link
 *       org.seuffert.panvpx.core.VpxImage}, {@link org.seuffert.panvpx.core.VpxPacket}),
 *       configuration records, abstract base classes, and {@link
 *       org.seuffert.panvpx.core.VpxException}.
 *   <li>{@link org.seuffert.panvpx.vp8} &mdash; VP8 {@link org.seuffert.panvpx.vp8.Vp8Encoder} and
 *       {@link org.seuffert.panvpx.vp8.Vp8Decoder}.
 *   <li>{@link org.seuffert.panvpx.vp9} &mdash; VP9 {@link org.seuffert.panvpx.vp9.Vp9Encoder} and
 *       {@link org.seuffert.panvpx.vp9.Vp9Decoder}.
 * </ul>
 *
 * <h2>Choosing between VP8 and VP9</h2>
 *
 * <p>Use <strong>VP8</strong> for maximum compatibility (e.g. WebRTC, older browsers, broad
 * hardware decoder support). Use <strong>VP9</strong> for roughly 30&ndash;50&nbsp;% better
 * compression at the same visual quality, at the cost of higher CPU usage.
 *
 * <h2>Quick start</h2>
 *
 * <pre>{@code
 * // Verify native library availability
 * if (!PanVpx.isLibVpxAvailable()) {
 *     throw new IllegalStateException("libvpx not found on this system");
 * }
 * System.out.println("Using: " + PanVpx.getVersionString());
 *
 * // Encode one 640x480 frame with VP8
 * int width = 640, height = 480;
 * byte[] i420Frame = new byte[width * height * 3 / 2];
 * // ... fill i420Frame with pixel data ...
 *
 * VpxEncoderConfig cfg = VpxEncoderConfig.builder(VpxEncoderConfig.Codec.VP8, width, height).targetBitrateKbps(512).threads(2).build();
 * try (Vp8Encoder encoder = new Vp8Encoder(cfg);
 *      VpxImage image = VpxImage.fromByteArray(i420Frame, width, height)) {
 *     List<VpxPacket> packets = encoder.encode(image, 0L, 1L, 0L);
 *     packets.forEach(p -> store(p.toByteArray()));
 * }
 * }</pre>
 *
 * <h2>Required JVM flag</h2>
 *
 * <p>The FFM API requires the following JVM argument when running your application:
 *
 * <pre>{@code
 * --enable-native-access=org.seuffert.panvpx
 * }</pre>
 *
 * <p>If your application is not yet fully modularized, use {@code ALL-UNNAMED} instead.
 *
 * @see org.seuffert.panvpx.PanVpx
 */
package org.seuffert.panvpx;
