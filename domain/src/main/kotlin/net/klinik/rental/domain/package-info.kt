package net.klinik.rental.domain

/**
 * Package root for the pure-JVM domain layer.
 *
 * This module exists so the arrears engine can be tested without an Android
 * SDK or an emulator. The directory is deliberately present and empty of
 * production types: Story 1.1's job was to prove the build works, and the
 * first real domain type arrives in Story 1.2.
 *
 * Nothing here may import `android.*` — the module has no Android plugin, so
 * such an import is a compile error rather than a review comment.
 */