package org.seuffert.panvpx;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PanVpxTest {

    @Test
    void testLibVpxAvailability() {
        // Since we are running on a machine where libvpx is installed, this should be true.
        // But more importantly, it shouldn't crash the JVM.
        final boolean available = PanVpx.isLibVpxAvailable();
        assertTrue(available, "libvpx should be available on this test system");

        final String version = PanVpx.getVersionString();
        assertNotNull(version);
        assertFalse(
                version.equals("N/A") || version.equals("Unknown"),
                "Version string should be valid");

        System.out.println("Loaded libvpx version: " + version);
        System.out.println("Build config: " + PanVpx.getBuildConfigString());
    }
}
