package net.klinik.rental.domain

/**
 * Package root for the pure-JVM domain layer.
 *
 * This module exists so the arrears engine can be tested without an Android
 * SDK or an emulator. Story 1.1's job was to prove the build works; the first real
 * domain types arrived with Story 1.2's `CutoffPeriod` and `ChargeSchedule`, and
 * Story 1.3 adds `Payment` and `Allocation`. This package root still holds no
 * production type, and stays because the module's only standing rule belongs where
 * it is easiest to find.
 *
 * Nothing here may import `android.*` — the module has no Android plugin, so
 * such an import is a compile error rather than a review comment.
 */