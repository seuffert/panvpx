plugins {
    java
    application
    alias(libs.plugins.spotless)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

dependencies {
    implementation(project(":lib"))
}

application {
    mainClass.set("org.seuffert.panvpx.benchmark.BenchmarkRunner")
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

spotless {
    java {
        googleJavaFormat("1.27.0").aosp()
    }
}
