/**
 * VP9 video codec implementation.
 *
 * <p>VP9 is the successor to VP8, developed by Google and deployed at scale on YouTube, in WebM
 * containers, and modern web browsers. It achieves roughly 30&ndash;50&nbsp;% better compression
 * than VP8 at the same visual quality at the cost of higher encoder CPU usage. VP9 is the right
 * choice when bitrate efficiency matters and the extra CPU budget is available.
 *
 * <h2>Classes</h2>
 *
 * <ul>
 *   <li>{@link org.seuffert.panvpx.vp9.Vp9Encoder} &mdash; encodes raw I420 frames into VP9
 *       bitstream packets.
 *   <li>{@link org.seuffert.panvpx.vp9.Vp9Decoder} &mdash; decodes VP9 bitstream packets back into
 *       raw I420 frames.
 * </ul>
 *
 * <h2>Encoding example (multi-threaded, good quality)</h2>
 *
 * <pre>{@code
 * int width = 1280, height = 720;
 * byte[] i420Frame = new byte[width * height * 3 / 2];
 * // ... fill i420Frame with pixel data ...
 *
 * VpxEncoderConfig config = new VpxEncoderConfig(
 *         width, height,
 *         1500,    // targetBitrateKbps
 *         0,       // frameDropThreshold — disabled
 *         4,       // threads
 *         1, 1000, // timebase: 1/1000 ms
 *         VpxEncoderConfig.DEADLINE_GOOD_QUALITY,
 *         2,       // cpuUsed
 *         true,    // rowMt — row-level multithreading
 *         2,       // tileColumns
 *         0);      // tokenPartitions (VP8 only)
 *
 * try (Vp9Encoder encoder = new Vp9Encoder(config)) {
 *     long pts = 0;
 *     try (VpxImage image = VpxImage.fromByteArray(i420Frame, width, height)) {
 *         List<VpxPacket> packets = encoder.encode(image, pts++, 1L, 0L);
 *         packets.forEach(p -> send(p.toByteArray()));
 *     }
 *
 *     // Drain lookahead buffer — VP9 may hold several frames; loop until empty
 *     List<VpxPacket> batch;
 *     do {
 *         batch = encoder.flush();
 *         batch.forEach(p -> send(p.toByteArray()));
 *     } while (!batch.isEmpty());
 * }
 * }</pre>
 *
 * <h2>Decoding example</h2>
 *
 * <pre>{@code
 * try (Vp9Decoder decoder = new Vp9Decoder()) {
 *     byte[] vp9Packet = ...; // VP9 bitstream data
 *     for (VpxImage frame : decoder.decode(vp9Packet)) {
 *         byte[] i420 = frame.toByteArray();
 *         int w = frame.width(), h = frame.height();
 *         // display or process raw I420 frame
 *     }
 * }
 * }</pre>
 *
 * @see org.seuffert.panvpx.vp8
 */
package org.seuffert.panvpx.vp9;
