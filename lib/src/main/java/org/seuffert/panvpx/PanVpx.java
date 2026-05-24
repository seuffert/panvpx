package org.seuffert.panvpx;

import java.lang.foreign.MemorySegment;
import org.seuffert.panvpx.ffi.VpxFFI;

/** Global utility entry point for the panvpx library. */
public final class PanVpx {

    private static boolean checked;
    private static boolean available;

    private PanVpx() {
        // Prevent instantiation
    }

    /**
     * Checks if the libvpx native library is successfully loaded and available on the system. This
     * method safely attempts to load the native library without throwing exceptions or segfaulting.
     * Safe to call multiple times.
     *
     * @return true if the native library is available, false otherwise.
     */
    public static boolean isLibVpxAvailable() {
        synchronized (PanVpx.class) {
            if (checked) {
                return available;
            }
            try {
                // Attempt to resolve the FFI class which triggers the static native library load
                Class.forName("org.seuffert.panvpx.ffi.VpxFFI");
                // Make a harmless call to verify it's functional
                final MemorySegment versionStr = VpxFFI.vpx_codec_version_str();
                available = (versionStr != null && versionStr.address() != 0);
            } catch (final LinkageError | RuntimeException | ClassNotFoundException t) {
                // Catch all LinkageError, ExceptionInInitializerError, UnsatisfiedLinkError, etc.
                available = false;
            }
            checked = true;
            return available;
        }
    }

    /**
     * Gets the version of the underlying libvpx library.
     *
     * @return The version string (e.g., "v1.13.0"), or "N/A" if the library is not available.
     */
    public static String getVersionString() {
        if (!isLibVpxAvailable()) {
            return "N/A";
        }
        final MemorySegment versionSegment = VpxFFI.vpx_codec_version_str();
        if (versionSegment != null && versionSegment.address() != 0) {
            return versionSegment.getString(0);
        }
        return "Unknown";
    }

    /**
     * Gets the build configuration of the underlying libvpx library.
     *
     * @return The build configuration string, or "N/A" if not available.
     */
    public static String getBuildConfigString() {
        if (!isLibVpxAvailable()) {
            return "N/A";
        }
        final MemorySegment buildConfigSegment = VpxFFI.vpx_codec_build_config();
        if (buildConfigSegment != null && buildConfigSegment.address() != 0) {
            return buildConfigSegment.getString(0);
        }
        return "Unknown";
    }
}
