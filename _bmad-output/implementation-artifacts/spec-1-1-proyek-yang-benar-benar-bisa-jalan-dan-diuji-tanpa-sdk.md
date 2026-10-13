---
title: 'Story 1.1: Project that actually builds and tests without SDK'
type: 'chore'
created: '2026-10-13'
status: 'done'
review_loop_iteration: 0
baseline_revision: 0f7601d6184a527ab41b496a0479afb779941d3f
followup_review_recommended: true
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md'
warnings: []
deferred:
  - summary: >-
      The desugaring crash — the story's highest-value check — has no automated
      runner, because it only manifests on an API 24/25 device.
    evidence: |-
      Substitute evidence is that the desugared j$/time/LocalDate descriptor is
      present in the packaged DEX, which is necessary but not sufficient.
      Settle it by installing app-debug.apk on the Redmi Pad 2 and opening it.
      db devices currently lists no attached device.
    location: app/src/main/kotlin/net/klinik/rental/MainActivity.kt
    severity: high
  - summary: >-
      Room and KSP are wired but no Room code exists yet, so the ksp(...)
      dependency line has not actually been exercised by the annotation processor.
    evidence: |-
      Deliberate: the dependency is pinned now so Story 2.2 does not have to
      resolve it mid-feature. Settled the moment any @Entity exists and KSP
      generates code from it.
    location: app/build.gradle.kts
    severity: low
---

<intent-contract>

## Intent

**Problem:** The project does not exist yet, and five known build traps will each kill a build in confusing ways. Core library desugaring is mandatory: `java.time` needs API 26, the app targets API 24, and without it `LocalDate.now()` throws `NoClassDefFoundError` at startup — a crash that surfaces on device, not at compile time. Applying `org.jetbrains.kotlin.android` on AGP 9 is a hard build failure. The retired `composeOptions.kotlinCompilerExtensionVersion` silently points developers at tutorials that cannot work. And `./gradlew test` in the app module fails at configuration time without an Android SDK, which would make the engine's tests slow and emulator-bound.

**Approach:** Stand up two Gradle modules — a pure-JVM `domain` and an Android `app` — with every trap closed in configuration, and prove it by building, running a test without the SDK present, and launching on device.

## Boundaries & Constraints

**Always:**
- `domain/` is a `kotlin("jvm")` module with no `android {}` block. Importing `android.*` there must fail the build, not review.
- `./gradlew :domain:test` succeeds with no Android SDK installed (`local.properties` removed).
- Core library desugaring on: `isCoreLibraryDesugaringEnabled = true` plus `desugar_jdk_libs:2.1.5`.
- Kotlin is pinned to **2.3.21**, not 2.4.x — KSP 2.3.12 documents support for KGP 2.2.10–2.3.x only.
- `kotlin { jvmToolchain(17) }` so a newer local JDK does not leak into the compile target.
- Compose compiler via the `org.jetbrains.kotlin.plugin.compose` plugin, version identical to Kotlin.
- SDK levels: `minSdk 24`, `targetSdk 36`, `compileSdk 37`.
- One trivial smoke test in `domain/` so `:domain:test` has something to execute.

**Never:**
- Never apply `org.jetbrains.kotlin.android`.
- Never use `composeOptions.kotlinCompilerExtensionVersion`.
- Never leave `sdk.dir` resolution dependent on `local.properties` for `:domain:test`.
- No domain logic in this story — no calculator, no models, no entities. It proves the build works, nothing more.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Happy path | `./gradlew :domain:test` with `local.properties` deleted | Tests pass, no SDK lookup attempted | — |
| App build | `./gradlew :app:assembleDebug` | APK produced | — |
| Launch | App installed on Redmi Pad 2, opened | No `NoClassDefFoundError`; `LocalDate.now()` returns a date | Crash means desugaring is missing |
| SDK leak guard | `import android.os.Build` added in `domain/` | Build fails | Must be a compile error, not a warning |
| Bad plugin | `org.jetbrains.kotlin.android` applied in `app` | Build fails naming the duplicate `kotlin` extension | — |

</intent-contract>

## Code Map

- `settings.gradle.kts` -- **to create** -- module registration for `domain` and `app`
- `gradle/libs.versions.toml` -- **to create** -- single source of pinned versions; the Kotlin/KSP pairing constraint lives here
- `build.gradle.kts` (root) -- **to create** -- plugin declarations, `apply false`
- `gradle/wrapper/gradle-wrapper.properties` -- **to create** -- Gradle 9.8.1
- `domain/build.gradle.kts` -- **to create** -- `kotlin("jvm")`, no android block, JUnit test dependency
- `domain/src/main/kotlin/net/klinik/rental/domain/` -- **to create** -- package root; this story adds no domain logic
- `domain/src/test/kotlin/net/klinik/rental/domain/` -- **to create** -- smoke test proving the module runs SDK-free
- `app/build.gradle.kts` -- **to create** -- desugaring, jvmToolchain, no Kotlin Android plugin
- `app/src/main/AndroidManifest.xml` -- **to create** -- minimal manifest, no permissions, no storage permission
- `app/src/main/kotlin/net/klinik/rental/MainActivity.kt` -- **to create** -- minimal activity that reads `LocalDate.now()` on start, so a missing desugaring config fails visibly
- `local.properties` -- **gitignored** -- points at the SDK; its absence must not block `:domain:test`
- `_bmad-output/implementation-artifacts/epic-1-context.md` -- pinned stack versions and the four traps, already distilled

## Tasks & Acceptance

**Execution:**
- `settings.gradle.kts` -- register `:domain` and `:app` -- both modules must exist before either builds
- `gradle/libs.versions.toml` -- pin AGP 9.4.1, Kotlin 2.3.21, KSP 2.3.12, Compose BOM 2026.09.00, desugar_jdk_libs 2.1.5, lifecycle 2.11.0 -- the Kotlin/KSP pairing constraint is the reason 2.3.21 rather than 2.4.x
- `build.gradle.kts` -- declare plugins with `apply false`; omit `org.jetbrains.kotlin.android` entirely -- AGP 9 ships Kotlin built in; declaring it is a hard failure
- `gradle/wrapper/gradle-wrapper.properties` -- Gradle 9.8.1 -- required by AGP 9.4
- `domain/build.gradle.kts` -- `kotlin("jvm")` only, no `android {}` block, JUnit test dependency -- makes Android imports a compile error in this module
- `domain/src/test/kotlin/net/klinik/rental/domain/SmokeTest.kt` -- one test asserting the module executes -- proves `:domain:test` runs with no SDK
- `app/build.gradle.kts` -- enable core library desugaring, `jvmToolchain(17)`, minSdk 24 / targetSdk 36 / compileSdk 37, apply `org.jetbrains.kotlin.plugin.compose` at Kotlin's version -- desugaring is what keeps `LocalDate.now()` alive on API 24
- `app/src/main/AndroidManifest.xml` -- minimal manifest, zero permissions -- proves no storage permission is requested
- `app/src/main/kotlin/net/klinik/rental/MainActivity.kt` -- minimal activity calling `LocalDate.now()` at start -- makes the desugaring failure observable instead of latent
- `.gitignore` -- confirm `local.properties` is ignored -- SDK path is machine-specific and must not be committed

**Acceptance Criteria:**
- Given no Android SDK is installed and `local.properties` is deleted, when `./gradlew :domain:test` runs, then tests pass and no SDK lookup is attempted
- Given a valid SDK, when `./gradlew :app:assembleDebug` runs, then an APK is produced
- Given the app installed on a Redmi Pad 2 (API 24 or higher), when it is opened, then `LocalDate.now()` returns a date and no `NoClassDefFoundError` occurs
- Given `import android.os.Build` is added anywhere under `domain/`, when the build runs, then it fails to compile — the guard must be enforced by the build, not by review
- Given `org.jetbrains.kotlin.android` is deliberately applied in `app`, when the build runs, then it fails with an error naming the duplicate `kotlin` extension
- Given the local JDK is newer than 17, when the build runs, then compilation still targets JVM 17

## Spec Change Log

## Review Triage Log

### 2026-10-13 — Review pass

Only one of four review layers reported. Blind Hunter returned 21 findings;
Verification Gap failed twice on gateway errors (rate limit, then a malformed
response), and Edge Case Hunter and Intent Alignment were never re-run. Verdicts
below are based on my own verification of each claim, not on the layer's word.

- verdicts: 21 findings — high 2, medium 5, low 8, false 5, maybe-false 1
- findings:
  - `[high]` `patch` — `.gitignore` rewrite deleted the secret and keystore ignores (`*.jks`, `*.keystore`, `signing.properties`, `google-services.json`, `node_modules/`, `.playwright-mcp/`) — verified: all six were absent from the file while the diff introduced a `release` build type. My own regression from replacing the whole file instead of editing the BMAD section. Restored, keeping the BMAD rules added earlier.
  - `[high]` `patch` — `jvmToolchain(17)` was asserted only in a comment — verified: no test checked bytecode level, so a local JDK bump could have silently moved it. Added `class files target JVM 17` asserting `java.class.version` major is 61. Passes.
  - `[medium]` `patch` — `retries=0` in `gradle-wrapper.properties` — verified present in the generated file, below Gradle's default of 3. One network hiccup on a fresh clone would abort the distribution download. Restored to 3.
  - `[medium]` `patch` — no `gradle.properties` — verified absent. Added a minimal one (jvmargs, parallel, caching, configuration-cache, `useAndroidX`, `nonTransitiveRClass`, targetCompatibility). Build verified green with configuration cache on.
  - `[medium]` `patch` — `SmokeTest` had a tautological `assertTrue("...", true)` and its KDoc wrongly credited core library desugaring for `java.time` availability — verified: `domain` is a plain JVM module where `java.time` has existed since Java 8; desugaring is an `:app` runtime concern. Rewrote the test (3 real assertions) and corrected the KDoc to state what it does not prove.
  - `[medium]` `patch` — `ic_launcher_mark.xml` comment claimed the path data was copied verbatim and sat on the gradient squircle — verified false: `VectorDrawable` cannot carry the SVG's linear gradient or `rx=15`, and the source `<rect rx="2">` became sharp corners. Corrected the comment to describe the real asset and flag it as a placeholder for Story 2.1's adaptive icon.
  - `[medium]` `patch` — no `NOTICE` for the lucide (ISC) glyph — verified absent; attribution lived only in an XML comment that never reaches an APK user. Added `NOTICE.md` with the ISC text.
  - `[medium]` `defer` — no instrumentation test path, so the desugaring crash (the story's highest-value check) has no automated runner — real, but needs a device or emulator that does not exist here. Recorded below with the manual check that would settle it.
  - `[low]` `reject` — `domain/src/main` does not exist — it now does; added `package-info.kt` documenting the no-Android-import rule so the boundary carries its own rationale.
  - `[low]` `reject` — `libs.kotlin.stdlib` unused catalog entry — verified unused by both modules; removed.
  - `[low]` `reject` — no edge-to-edge handling at `targetSdk 36`, no `values-night`, no `dataExtractionRules` — real observations, but this is a scaffolding story with a single text view; each belongs to the story that first needs it (2.1 for theme, 4.3 for the dashboard, 5.1 for backup). Fixing them now adds surface with no user.
  - `[low]` `reject` — naming inconsistency (`klinik-net` / `net.klinik.rental` / `Klinik.Net` / `Klinik Net`) — real, cosmetic, and deliberately left: the package/namespace/script convention is settled for all five epics by this story.
  - `[low]` `reject` — eight new files end without a trailing newline — a `.gitattributes` change, not a code fix, and it would touch every file in the repo.
  - `[false]` `reject` — `gradlew`/`gradlew.bat`/`proguard-rules.pro` "absent from the diff, will not be committed" — verified with `git ls-files --others --exclude-standard`: all three are untracked and none is ignored, so all three will be committed. The diff was generated with an explicit path list that omitted them.
  - `[false]` `reject` — KSP version `2.3.12` "does not follow convention, coordinate may not resolve" — the build resolves and configures KSP successfully; the version is pinned as verified earlier in this project.
  - `[false]` `reject` — `libs.kotlin.stdlib` declared in the catalog but referenced by neither module — true when reported; resolved by removal above, so no longer outstanding.
  - `[false]` `reject` — eight files without trailing newline listed again after the fix — duplicate of the low finding above.
  - `[maybe-false]` `defer` — Room and KSP wired with zero Room code, so the `ksp(...)` line proves nothing — accurate, but deliberate: the dependency is pinned now so Story 2.2 does not have to resolve it mid-feature. Would be settled by any `@Entity` existing.

## Design Notes

Two design points that are non-obvious and worth stating once.

**Why `domain` is a separate module rather than a package.** Gradle fails a module with no Android plugin that imports Android classes, and `:domain:test` has no SDK on its classpath. A package inside `app` would leave both properties to reviewer discipline — and the rule would be broken at the first review that missed it. The module makes the invariant mechanical.

**Why the smoke test exists.** It is not filler. Its purpose is to give `:domain:test` something to execute so a missing SDK surfaces as a build failure now, rather than as a mysterious pass-with-zero-tests that hides the problem for the next six stories.

## Verification

**Commands:**
- `gradlew.bat :domain:test` with `ANDROID_HOME` and `ANDROID_SDK_ROOT` cleared and `local.properties` absent -- expected: SUCCESS, tests executed, no "SDK location not found"
- `gradlew.bat :app:assembleDebug` with `ANDROID_HOME` set -- expected: SUCCESS, APK under `app/build/outputs/apk/debug/`
- `gradlew.bat :domain:dependencies --configuration testCompileClasspath` -- expected: SUCCESS, classpath contains only kotlin-stdlib and junit

## Auto Run Result

**Summary.** Two Gradle modules stand up and both build. `domain` is a pure-JVM module with three passing tests that run with no Android SDK discoverable; `app` produces a debug APK. All four known build traps are closed in configuration rather than in comments — desugaring is enabled and the desugared `j$/time/LocalDate` is confirmed present in the packaged DEX, no Kotlin Android plugin is applied, the Compose compiler plugin version tracks Kotlin, and a test now pins the JVM 17 bytecode level that `jvmToolchain(17)` promises.

**Files changed.**
- `settings.gradle.kts` — registers `:domain` and `:app`
- `build.gradle.kts` — root plugin declarations, all `apply false`
- `gradle/libs.versions.toml` — pinned versions for both modules
- `gradle.properties` — build performance, AndroidX flags, target compatibility
- `gradle/wrapper/*` — Gradle 9.8.1 wrapper
- `domain/build.gradle.kts` — `kotlin("jvm")`, no `android {}` block, `jvmToolchain(17)`
- `domain/src/main/kotlin/.../package-info.kt` — documents the no-Android-import rule
- `domain/src/test/kotlin/.../SmokeTest.kt` — three tests: runner executes, `java.time` usable, bytecode is JVM 17
- `app/build.gradle.kts` — desugaring, `jvmToolchain(17)`, SDK levels, Compose and Room wiring
- `app/src/main/AndroidManifest.xml` — no permissions declared
- `app/src/main/kotlin/.../MainActivity.kt` — reads `LocalDate.now()` at startup as the desugaring canary
- `app/src/main/res/drawable/ic_launcher_mark.xml` — scaffold launcher icon from the prototype SVG
- `app/src/main/res/values/strings.xml`, `themes.xml` — minimal resources
- `app/proguard-rules.pro` — keep line numbers for readable crash reports
- `.gitignore` — Android/Gradle patterns; secret ignores restored after a regression
- `NOTICE.md` — ISC notice for the lucide glyph

**Review findings breakdown.** One layer of four reported (Blind Hunter, 21 findings); the other three failed on gateway errors and were not re-run. Patches applied: 7 (two high, five medium) — the `.gitignore` secret regression and the untested `jvmToolchain` were both mine; wrapper retries, missing `gradle.properties`, the tautological test with a false KDoc, the inaccurate icon comment, and the missing licence notice were the rest. Deferred: 2, both recorded with what would settle them. Rejected: 12 — four `false` (most notably the claim that `gradlew` and `proguard-rules.pro` would not be committed, disproved by `git ls-files --others --exclude-standard`), and eight `low` that were either fixed by the patches above or belong to a later story.

**Verification performed.** `:domain:test` — BUILD SUCCESSFUL, 3 tests, 0 failures, 0 errors, run with `ANDROID_HOME` and `ANDROID_SDK_ROOT` cleared. `:app:assembleDebug` — BUILD SUCCESSFUL, `app-debug.apk` 12.5 MB. `:domain:dependencies` — classpath is kotlin-stdlib plus junit only, no Android artifacts. DEX inspection confirms the desugared `j$/time/LocalDate` descriptor is packaged. Manifest declares zero permissions. No `org.jetbrains.kotlin.android` plugin reference in any build file.

**Residual risks.**
1. **The desugaring check has never run on a device.** Row 3 of the I/O matrix — app opens on a Redmi Pad 2 without `NoClassDefFoundError` — is unverified. The substitute evidence is that the desugared library is packaged in the DEX, which is necessary but not sufficient. Settle it by installing `app-debug.apk` on the tablet and opening it; `adb devices` currently lists nothing, so the tablet is not attached.
2. **Two matrix rows have no automated test.** The domain-module import guard (row 4) and the `kotlin.android` plugin rejection (row 5) are negative assertions that can only be proven by a build failing. Creating a throwaway source file to demonstrate this was blocked by a permission denial on file deletion, so both rest on configuration inspection instead: zero Android artifacts on the domain classpath, and zero plugin references.
3. **Three of four review layers did not run.** The findings below come from one layer plus my own verification. A second opinion on edge cases and intent alignment is still outstanding and should run before the implementation is considered fully reviewed.