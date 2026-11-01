# flutter-migration-readers

Readers for the data a Flutter build of OpenCode Mobile (2.1.0+57) leaves on Android, plus crash-safe
migration helpers, for slice-0 proof P5 of the native rewrite. Plain Kotlin/JVM, no runtime dependencies.

This is a standalone build: it has its own `settings.gradle.kts` and is not part of `native/` or
`native/build-logic`.

```bash
./gradlew test               # all tests (kotlin.test, JUnit 5 platform)
./gradlew generateFixtures   # synthetic fixture set → build/fixtures
```

Formats, sources, the Android Keystore step, test results and open risks: `docs/native/slice0/P5-readers.md`.
