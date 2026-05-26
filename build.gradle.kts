// Register plugins at the root so sibling subprojects share the same classloader instance.
// This prevents Spotless SpotlessTaskService classloader conflicts between lib and benchmark.
plugins {
    alias(libs.plugins.spotless) apply false
}
