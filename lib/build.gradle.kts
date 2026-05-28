import net.ltgt.gradle.errorprone.errorprone

plugins {
    java
    `java-library`
    checkstyle
    pmd
    alias(libs.plugins.errorprone)
    alias(libs.plugins.spotbugs)
    alias(libs.plugins.spotless)
    alias(libs.plugins.vanniktech.publish)
}

group = providers.gradleProperty("group").get()
version = providers.gradleProperty("version").get()


java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

checkstyle {
    toolVersion = libs.versions.checkstyle.get()
    configFile = rootProject.file("config/checkstyle/checkstyle.xml")
}

pmd {
    toolVersion = libs.versions.pmd.get()
    ruleSetFiles = files(rootProject.file("config/pmd/pmd-ruleset.xml"))
    ruleSets = emptyList() // Clear defaults to strictly use our ruleset
}

spotbugs {
    toolVersion = libs.versions.spotbugs.tool.get()
    excludeFilter.set(rootProject.file("config/spotbugs/exclude.xml"))
}

spotless {
    java {
        targetExclude("src/main/java/org/seuffert/panvpx/ffi/**", "src/main/java/module-info.java")
        googleJavaFormat("1.27.0").aosp()
    }
}

dependencies {
    errorprone(libs.errorprone.core)
    errorprone(libs.nullaway)

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
    
    // Configure Error Prone & NullAway
    options.errorprone.disableWarningsInGeneratedCode.set(true)
    options.errorprone.excludedPaths.set(".*/ffi/.*|.*/module-info\\.java")
    options.errorprone.errorproneArgs.addAll(
        "-Xep:NullAway:ERROR",
        "-XepOpt:NullAway:AnnotatedPackages=org.seuffert.panvpx"
    )
}

tasks.withType<Pmd>().configureEach {
    exclude("org/seuffert/panvpx/ffi/**", "module-info.java")
}

tasks.withType<Checkstyle>().configureEach {
    exclude("org/seuffert/panvpx/ffi/**", "module-info.java")
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

    // Forward the custom library path property to the test JVM
    if (System.getProperty("panvpx.libvpx.path") != null) {
        systemProperty("panvpx.libvpx.path", System.getProperty("panvpx.libvpx.path"))
    }
}

// ---------------------------------------------------------------------------
// Maven Central publishing
// ---------------------------------------------------------------------------
mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates(group.toString(), "panvpx", version.toString())

    pom {
        name.set("panvpx")
        description.set(
            "High-level Java bindings for libvpx (VP8/VP9) via the JDK Panama FFM API"
        )
        inceptionYear.set("2025")
        url.set("https://gitlab.com/org.seuffert/panvpx")
        licenses {
            license {
                name.set("Apache-2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0")
                distribution.set("https://www.apache.org/licenses/LICENSE-2.0")
            }
        }
        developers {
            developer {
                id.set("seuffert")
                name.set("seuffert")
                url.set("https://gitlab.com/org.seuffert")
            }
        }
        scm {
            url.set("https://gitlab.com/org.seuffert/panvpx")
            connection.set("scm:git:https://gitlab.com/org.seuffert/panvpx.git")
            developerConnection.set("scm:git:https://gitlab.com/org.seuffert/panvpx.git")
        }
    }
}
