/// True in the F-Droid build: `flutter build apk --dart-define=OC_FDROID=true`
/// (with `-Pocfdroid=true` for Gradle; docs/release/fdroid-2026-10-31.md).
///
/// F-Droid compiles the app from source with upstream Flutter, ships updates
/// itself, and refuses proprietary SDKs. This build therefore has no
/// code-push updater, no in-app APK update and no ML Kit camera scanner.
/// Conditional imports on the same define (`if (OC_FDROID)`) keep those
/// packages out of the compiled program; this constant gates the UI that
/// would otherwise offer them.
const bool kFdroidBuild = bool.fromEnvironment('OC_FDROID');
