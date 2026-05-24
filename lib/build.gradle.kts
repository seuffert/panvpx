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

tasks.test {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
