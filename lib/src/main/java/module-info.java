/**
 * The core module for panvpx, a Java Project Panama FFM wrapper for libvpx.
 */
module org.seuffert.panvpx {
    exports org.seuffert.panvpx;
    exports org.seuffert.panvpx.core;
    exports org.seuffert.panvpx.vp8;

    // Do NOT export org.seuffert.panvpx.ffi to keep jextract bindings internal
}
