/**
 * VP8 video codec implementation.
 *
 * <p>VP8 is an open video codec developed by Google, widely used in WebRTC, WebM containers, and
 * streaming applications. It offers good compression efficiency and broad hardware and software
 * decoder support, making it the right choice when maximum compatibility is required.
 *
 * <h2>Classes</h2>
 *
 * <ul>
 *   <li>{@link org.seuffert.panvpx.vp8.Vp8Encoder} &mdash; encodes raw I420 frames into VP8
 *       bitstream packets.
 *   <li>{@link org.seuffert.panvpx.vp8.Vp8Decoder} &mdash; decodes VP8 bitstream packets back into
 *       raw I420 frames.
 * </ul>
 *
 * <h2>Encoding example</h2>
 *
 * <pre>{@code
 * int width = 640, height = 480;
 * byte[] i420Frame = new byte[width * height * 3 / 2];
 * // ... fill i420Frame with pixel data ...
 *
 * VpxEncoderConfig config = VpxEncoderConfig.builder(width, height).targetBitrateKbps(512).threads(2).build();
 * try (Vp8Encoder encoder = new Vp8Encoder(config)) {
 *     long pts = 0;
 *     try (VpxImage image = VpxImage.fromByteArray(i420Frame, width, height)) {
 *         List<VpxPacket> packets = encoder.encode(image, pts++, 1L, 0L);
 *         packets.forEach(p -> send(p.toByteArray()));
 *     }
 *
 *     // Drain any buffered packets at end of stream
 *     for (VpxPacket p : encoder.flush()) {
 *         send(p.toByteArray());
 *     }
 * }
 * }</pre>
 *
 * <h2>Decoding example</h2>
 *
 * <pre>{@code
 * try (Vp8Decoder decoder = new Vp8Decoder(new VpxDecoderConfig())) {
 *     byte[] vp8Packet = ...; // VP8 bitstream data
 *     for (VpxImage frame : decoder.decode(vp8Packet)) {
 *         byte[] i420 = frame.toByteArray();
 *         int w = frame.width(), h = frame.height();
 *         // display or process raw I420 frame
 *     }
 * }
 * }</pre>
 *
 * @see org.seuffert.panvpx.vp9
 */
package org.seuffert.panvpx.vp8;
