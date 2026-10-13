// Pure-JVM domain module — no `android {}` block by design.
//
// This is what makes AD-1 mechanical rather than advisory: importing
// android.* from here is a compile error, not a review comment. It is also
// why `:domain:test` runs with no Android SDK installed — `./gradlew test`
// on the app module fails at configuration time with "SDK location not found",
// because AGP resolves the SDK location before any task runs.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    // Gradle may run on a newer local JDK; compilation targets 17 regardless,
    // so a local JDK upgrade cannot silently change the bytecode level.
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("passed", "failed", "skipped")
    }
}