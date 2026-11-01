# :android:linux

Slice 1 (unit S1-LINUX, plan 11 §0.2 S11-1 and §4.1): the built-in Linux Kotlin of the Flutter app, **copied unchanged**.
Copied from `6627d320` (this repository's `android/app/src/{main,test,androidTest}`); every file and its source commit is
a row in `native/TWINS.md`. The report is `docs/native/units/S1-LINUX.md`.

- `src/main/kotlin`, `src/test/kotlin`, `src/androidTest/**`: twins. Do not edit them here; a Flutter-side fix is
  mirrored through the twin rule (plan 02 §5.6), and refactoring waits for S3-RTSPLIT (plan 11 §5.3).
- `src/main/res/values{,-ar}/strings.xml`: twins, verbatim subsets of the app's `strings.xml` (only the names the copies read).
- `src/shims/kotlin`: the only native-authored code. Package-level stand-ins for Flutter-app classes outside this module
  (`R`, `BuildConfig`, `MainActivity`, `StatusActionReceiver`, `AppLifecycle`, `LocalTerminal`). Each has a TODO naming the
  unit that removes it. `MainActivity` and `R` share FQCNs with `:androidApp`, so this module cannot join the app
  classpath until S3-RTSPLIT removes them.

Checks (T0):

```bash
./gradlew :android:linux:testDebugUnitTest                      # 34 copied JVM test files, 491 tests
./gradlew :android:linux:compileDebugAndroidTestKotlin          # instrumented acceptance classes compile (not run)
```
