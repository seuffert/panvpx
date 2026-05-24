package org.seuffert.panvpx.core;

public class VpxException extends RuntimeException {
    @java.io.Serial
    private static final long serialVersionUID = 1L;

    private final int code;

    public VpxException(int code, String message) {
        super("Vpx Error [" + code + "]: " + message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
