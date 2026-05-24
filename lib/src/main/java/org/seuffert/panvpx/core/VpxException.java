package org.seuffert.panvpx.core;

/** Exception thrown when a libvpx native call fails. */
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
     * Gets the native libvpx error code associated with this exception.
     *
     * @return The error code.
     */
    public int code() {
        return code;
    }
}
