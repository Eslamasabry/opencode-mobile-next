import 'package:opencode_mobile/platform/platform_capabilities.dart';

// Test seams for the capture tools in tool/capture/: those files live outside
// test/, so they set the app's @visibleForTesting hooks through here rather
// than silencing the analyzer.

/// Pins the platform the app believes it runs on; null puts it back.
set capturePlatformCapabilities(PlatformCapabilities? value) =>
    debugPlatformCapabilities = value;
