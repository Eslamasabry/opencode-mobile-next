import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/termux_running_server.dart';

// Test seams for the capture tools in tool/capture/: those files live outside
// test/, so they set the app's @visibleForTesting hooks through here rather
// than silencing the analyzer.

/// Pins the platform the app believes it runs on; null puts it back.
set capturePlatformCapabilities(PlatformCapabilities? value) =>
    debugPlatformCapabilities = value;

/// The loopback check a running Termux server answers.
TermuxRunningServerProbe get captureTermuxRunningServerProbe =>
    termuxRunningServerProbe;

set captureTermuxRunningServerProbe(TermuxRunningServerProbe value) =>
    termuxRunningServerProbe = value;
