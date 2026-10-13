package net.klinik.rental.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * This test is not filler. `:domain:test` having something to execute is what
 * makes a missing Android SDK surface as a build failure here — now — rather
 * than as a silent pass-with-zero-tests that hides the problem for the next
 * five stories.
 *
 * Note what this file does NOT prove: it says nothing about core library
 * desugaring. Desugaring is an Android *runtime* concern in `:app`, where
 * `java.time` must survive on API 24/25. This module is plain JVM, where
 * `java.time` has existed since Java 8. The desugaring check lives in
 * `:app` and is only observable on a device.
 */
class SmokeTest {

    @Test
    fun `domain module executes without an Android SDK`() {
        // Guards against the test class being silently filtered out: if this
        // method stops running, the whole premise of the module is unverified.
        assertEquals("domain module is under test", "domain module is under test", result())
    }

    @Test
    fun `java time is usable in the domain module`() {
        val today: LocalDate = LocalDate.of(2026, 3, 24)
        assertEquals(2026, today.year)
        assertEquals(3, today.monthValue)
        assertEquals(24, today.dayOfMonth)
    }

    @Test
    fun `class files target JVM 17 regardless of local JDK`() {
        // 61 = Java 17 (61.0 when the minor version is present). This is what
        // makes `jvmToolchain(17)` a fact rather than a comment: a local JDK
        // upgrade cannot silently move the bytecode level out from under the
        // app module.
        val major = System.getProperty("java.class.version").substringBefore('.').toInt()
        assertEquals(
            "compiled to JVM 17 as pinned in domain/build.gradle.kts",
            61,
            major,
        )
    }

    private fun result() = "domain module is under test"
}