plugins {
    java
    `java-library`
    `maven-publish`
}


java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}


dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-Werror")
    options.compilerArgs.add("-Xlint:all")
    // Suppress warnings from jextract generated code and restricted methods
    options.compilerArgs.add("-Xlint:-restricted")
    options.compilerArgs.add("-Xlint:-preview")
}

tasks.withType<Javadoc> {
    (options as StandardJavadocDocletOptions).apply {
        // -Xdoclint/package:[-]<packages>
        // Note: ".*" suffix means only sub-packages, NOT the package itself.
        // Use both "pkg" and "pkg.*" to cover the package and all sub-packages.
        addBooleanOption(
            "Xdoclint/package:-org.seuffert.panvpx.ffi,-org.seuffert.panvpx.ffi.*",
            true
        )
        // Keep internal out of the generated HTML output
        addStringOption("exclude", "org.seuffert.panvpx.ffi")
    }
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
