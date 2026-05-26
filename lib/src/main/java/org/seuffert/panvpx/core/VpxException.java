package org.seuffert.panvpx.core;

/**
 * Exception thrown when a libvpx native call returns a non-OK status.
 *
 * <p>This unchecked exception wraps the native {@code vpx_codec_err_t} error code returned by the
 * underlying C library. It is a {@link RuntimeException} to avoid cluttering method signatures
 * &mdash; most libvpx errors (invalid parameters, memory exhaustion, ABI mismatch) are either
 * configuration mistakes caught at startup or unrecoverable runtime conditions.
 *
 * <p><strong>Example &mdash; inspecting the error code:</strong>
 *
 * <pre>{@code
 * try {
 *     encoder.encode(image, pts, duration, flags);
 * } catch (VpxException e) {
 *     System.err.println("libvpx error code: " + e.code() + " — " + e.getMessage());
 * }
 * }</pre>
 */
public class VpxException extends RuntimeException {
    @java.io.Serial private static final long serialVersionUID = 1L;

    /** The internal libvpx error code. */
    private final int code;

    /**
     * Constructs a new VpxException.
     *
     * @param code The native libvpx error code.
     * @param message A descriptive error message.
     */
    public VpxException(final int code, final String message) {
        super("Vpx Error [" + code + "]: " + message);
        this.code = code;
    }

    /**
     * Returns the native libvpx error code associated with this exception.
     *
     * <p>The value maps directly to the {@code vpx_codec_err_t} C enumeration defined in {@code
     * vpx_codec.h}. Common values include:
     *
     * <ul>
     *   <li>{@code 1} &mdash; {@code VPX_CODEC_ERROR}: unspecified error.
     *   <li>{@code 3} &mdash; {@code VPX_CODEC_INVALID_PARAM}: invalid parameter.
     *   <li>{@code 4} &mdash; {@code VPX_CODEC_MEM_ERROR}: memory allocation failure.
     *   <li>{@code 6} &mdash; {@code VPX_CODEC_ABI_MISMATCH}: ABI version mismatch between the Java
     *       bindings and the installed libvpx.
     * </ul>
     *
     * @return The {@code vpx_codec_err_t} integer error code.
     */
    public int code() {
        return code;
    }
}
