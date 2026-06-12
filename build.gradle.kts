import nl.littlerobots.vcu.plugin.resolver.VersionSelectors

// Register plugins at the root so sibling subprojects share the same classloader instance.
// This prevents Spotless SpotlessTaskService classloader conflicts between lib and benchmark.
plugins {
    alias(libs.plugins.spotless) apply false
    // Keeps gradle/libs.versions.toml up to date: `./gradlew versionCatalogUpdate`.
    alias(libs.plugins.version.catalog.update)
}

// Only accept stable releases when updating the catalog, so release candidates,
// alpha/beta builds and snapshots are never pulled in automatically.
versionCatalogUpdate {
    versionSelector(VersionSelectors.STABLE)
}
