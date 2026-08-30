import 'package:flutter/foundation.dart';

/// The single place that answers "does the platform this build is running on
/// actually have that?".
///
/// This app is primarily an Android client for OpenCode with a Linux desktop
/// target bolted on. A large share of its features are Android platform
/// features — a Termux bridge, a foreground service, notification actions, a
/// home-screen widget, Shorebird code push, on-device voice — reached through
/// `MethodChannel`s that simply have no implementation in the Linux runner.
/// Left ungated, every one of those surfaces is visible on the desktop build
/// and does nothing, or worse, claims to have worked.
///
/// The gates therefore route through one small object rather than through
/// `Platform.isAndroid` scattered across the widget tree, for three reasons:
///
///  * `dart:io`'s `Platform` is invisible to widget tests — it reports the
///    *host* (Linux), so an Android-only branch guarded that way is never
///    exercised by the suite, which is how the untruths got in.
///  * The booleans are named after the *capability*, not the OS. When iOS or
///    a Windows runner arrives, the answer changes in one file.
///  * Tests can pump both platforms: [debugPlatformCapabilities] swaps the
///    whole object out.
///
/// It is deliberately derived from [defaultTargetPlatform] rather than
/// `Platform`: `defaultTargetPlatform` honours
/// `debugDefaultTargetPlatformOverride`, is the value the Flutter framework
/// itself branches on, and reports `TargetPlatform.android` inside
/// `flutter_test` — so the Android paths stay the tested default and desktop
/// is the case a test opts into.
@immutable
class PlatformCapabilities {
  const PlatformCapabilities({required this.platform, this.isWeb = false});

  /// The shipping mobile target: everything is available.
  const PlatformCapabilities.android()
    : platform = TargetPlatform.android,
      isWeb = false;

  /// The shipping desktop target.
  const PlatformCapabilities.linuxDesktop()
    : platform = TargetPlatform.linux,
      isWeb = false;

  final TargetPlatform platform;
  final bool isWeb;

  bool get isAndroid => !isWeb && platform == TargetPlatform.android;

  bool get isDesktop =>
      !isWeb &&
      (platform == TargetPlatform.linux ||
          platform == TargetPlatform.macOS ||
          platform == TargetPlatform.windows);

  /// The Termux bridge (`oc/termux`), its guided setup screen, and the
  /// on-device managed OpenCode server. Termux is an Android app; there is no
  /// desktop equivalent and nothing to fall back to.
  bool get supportsTermux => isAndroid;

  /// On-device speech: the `oc/voice` channel (microphone permission, device
  /// probe) and the recorder that writes into Android-shaped paths. No
  /// desktop capture path exists yet, so desktop reports honestly unavailable
  /// rather than pretending a microphone is present.
  bool get supportsVoice => isAndroid;

  /// The `oc/background` foreground service that keeps a live transport alive
  /// while the Activity is backgrounded, plus battery-optimisation exemption.
  bool get supportsBackgroundService => isAndroid;

  /// Privacy-safe coding-alert notifications and their reply/decision
  /// actions, delivered by the same native service.
  bool get supportsNotifications => isAndroid;

  /// The home-screen widget snapshot and its native redraw.
  bool get supportsHomeWidget => isAndroid;

  /// Shorebird patches. Desktop builds are not Shorebird-released; they use
  /// the GitHub release check instead ([supportsDesktopReleaseCheck]).
  bool get supportsCodePush => isAndroid;

  /// The GitHub release-tag check that stands in for code push where
  /// Shorebird cannot reach. Deliberately the complement of
  /// [supportsCodePush] over the platforms that ship a release artifact:
  /// Linux and Windows desktop bundles.
  bool get supportsDesktopReleaseCheck =>
      !isWeb &&
      (platform == TargetPlatform.linux || platform == TargetPlatform.windows);

  /// Host-setup copy that only makes sense from a phone plugged into the
  /// machine running the server — `adb reverse`, USB bridging.
  bool get supportsUsbHostBridge => isAndroid;

  /// Scanning the QR that `opencode2 pair` prints: the camera permission, the
  /// `oc/camera` channel, and the `mobile_scanner` preview.
  ///
  /// Android only. `mobile_scanner` ships no Linux implementation, and a
  /// desktop user reading a QR off the same machine that printed it would be
  /// pointing a webcam at their own screen — pasting is simply better there.
  /// Desktop therefore renders no scan affordance at all rather than one that
  /// opens and fails.
  bool get supportsQrPairing => isAndroid;

  @override
  bool operator ==(Object other) =>
      other is PlatformCapabilities &&
      other.platform == platform &&
      other.isWeb == isWeb;

  @override
  int get hashCode => Object.hash(platform, isWeb);

  @override
  String toString() => 'PlatformCapabilities($platform, isWeb: $isWeb)';
}

PlatformCapabilities? _override;

/// What this build can do. Read it at the point of use, never cache it — a
/// test may have swapped it since the widget was constructed.
PlatformCapabilities get platformCapabilities =>
    _override ??
    PlatformCapabilities(platform: defaultTargetPlatform, isWeb: kIsWeb);

/// Pins [platformCapabilities] for a test. Pass `null` to restore the real
/// platform; always do so from `addTearDown` so one test cannot leak its
/// platform into the next.
@visibleForTesting
set debugPlatformCapabilities(PlatformCapabilities? value) => _override = value;

@visibleForTesting
PlatformCapabilities? get debugPlatformCapabilities => _override;
