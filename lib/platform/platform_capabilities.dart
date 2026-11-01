import 'package:flutter/foundation.dart';

import 'browser_context_io.dart'
    if (dart.library.js_interop) 'browser_context_web.dart'
    as browser_context;

/// The single place that answers "does the platform this build is running on
/// actually have that?".
///
/// This app is primarily an Android client for OpenCode with a Linux desktop
/// target bolted on, and a browser build that runs the same product shell. A
/// large share of its features are Android platform features — a Termux
/// bridge, a foreground service, notification actions, a home-screen widget,
/// Shorebird code push, on-device voice — reached through `MethodChannel`s
/// that simply have no implementation in the Linux runner or in a browser.
/// Left ungated, every one of those surfaces is visible on the other builds
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
///
/// In a browser [defaultTargetPlatform] still reports the *operating system
/// the browser runs on* (Linux, Windows, macOS, Android, iOS), never "web".
/// Every getter here therefore checks [isWeb] first: a Linux browser is not a
/// Linux desktop window, and an Android browser is not the Android app.
@immutable
class PlatformCapabilities {
  const PlatformCapabilities({
    required this.platform,
    this.isWeb = false,
    this.secureContext = true,
  });

  /// The shipping mobile target: everything is available.
  const PlatformCapabilities.android()
    : platform = TargetPlatform.android,
      isWeb = false,
      secureContext = true;

  /// The shipping desktop target.
  const PlatformCapabilities.linuxDesktop()
    : platform = TargetPlatform.linux,
      isWeb = false,
      secureContext = true;

  /// The browser build. [platform] is the operating system under the browser
  /// — a Linux workstation by default — and [secureContext] whether the page
  /// was opened from an origin the browser trusts (see [secureContext]).
  const PlatformCapabilities.browser({
    this.platform = TargetPlatform.linux,
    this.secureContext = true,
  }) : isWeb = true;

  final TargetPlatform platform;
  final bool isWeb;

  /// Whether the browser treats the page's origin as a secure context: an
  /// `https` page, or a plain-`http` page served from `localhost` /
  /// `127.0.0.1`. Always true off the web, where the native stores do not
  /// depend on the origin.
  ///
  /// Two browser APIs the app leans on are gated on this by the browser
  /// itself, not by anything the app can do about it: `crypto.subtle`, which
  /// `flutter_secure_storage_web` needs to keep a server password encrypted,
  /// and `navigator.clipboard.readText`, behind every "Paste" button. On a
  /// plain-`http` page reached over the network — a Tailscale address, a LAN
  /// address — both are absent, so the app has to say so rather than fail
  /// with a raw browser error.
  final bool secureContext;

  bool get isAndroid => !isWeb && platform == TargetPlatform.android;

  /// A native desktop *window*: Linux, macOS, or Windows runner. Owns window
  /// geometry, the taskbar icon, and the GitHub release check. A browser on
  /// the same operating system is deliberately not this — see
  /// [usesDesktopInteractions] for what a desktop browser does share.
  bool get isDesktop =>
      !isWeb &&
      (platform == TargetPlatform.linux ||
          platform == TargetPlatform.macOS ||
          platform == TargetPlatform.windows);

  /// True when the browser build runs on a desktop-class operating system,
  /// where the user has a keyboard and a mouse rather than a touch screen.
  bool get isDesktopBrowser =>
      isWeb &&
      (platform == TargetPlatform.linux ||
          platform == TargetPlatform.macOS ||
          platform == TargetPlatform.windows);

  /// Desktop *input conventions*: the keyboard shortcut layer, right-click
  /// context menus, persistent scrollbars, mouse text selection in the
  /// transcript, and file drops onto the composer.
  ///
  /// This is what `desktopInteractions` in `lib/ui/desktop/` gates on. It is
  /// true for a native desktop window and for a desktop browser alike — a
  /// keyboard is a keyboard — and false for Android, iOS, and a phone
  /// browser, which keep the touch product exactly as shipped.
  bool get usesDesktopInteractions => isDesktop || isDesktopBrowser;

  /// The Termux bridge (`oc/termux`), its guided setup screen, and the
  /// on-device managed OpenCode server. Termux is an Android app; there is no
  /// desktop equivalent and nothing to fall back to.
  bool get supportsTermux => isAndroid;

  /// Explicit handoff to the separately installed official Android VPN app.
  bool get supportsTailscaleHandoff => isAndroid;

  /// On-device speech: the `oc/voice` channel (microphone permission, device
  /// probe) and the recorder that writes into Android-shaped paths. No
  /// desktop capture path exists yet, so desktop reports honestly unavailable
  /// rather than pretending a microphone is present.
  bool get supportsVoice => isAndroid;

  /// Foreground system TTS with installed offline voices; Android only.
  bool get supportsReadAloud => isAndroid;

  /// Read-only raster PDF bridge; native admission requires Android 10+.
  bool get supportsLocalPdf => isAndroid;

  /// Explicit foreground turns using the existing local dictation path.
  bool get supportsVoiceConversation => supportsVoice && supportsReadAloud;
  bool get supportsPromptPhotos => isAndroid;

  /// The `oc/background` foreground service that keeps a live transport alive
  /// while the Activity is backgrounded, plus battery-optimisation exemption.
  bool get supportsBackgroundService => isAndroid;

  /// Privacy-safe coding-alert notifications and their reply/decision
  /// actions, delivered by the same native service.
  bool get supportsNotifications => isAndroid;

  /// The home-screen widget snapshot and its native redraw.
  bool get supportsHomeWidget => isAndroid;

  /// Launcher shortcuts over `oc/shortcut`: the static Connect / New task
  /// entries, and the dynamic pinned-session entries the app publishes
  /// through `ShortcutManagerCompat`. Desktop and iOS have no launcher
  /// long-press menu the app can write to, so nothing is published there.
  bool get supportsLaunchShortcuts => isAndroid;

  /// The Quick Settings tile: a native `TileService` that reads the cached
  /// needs-attention count the Dart side writes. Android only; other
  /// platforms write no cache at all.
  bool get supportsQuickSettingsTile => isAndroid;

  /// Shorebird patches. Desktop builds are not Shorebird-released; they use
  /// the GitHub release check instead ([supportsDesktopReleaseCheck]).
  bool get supportsCodePush => isAndroid;

  /// The GitHub release-tag check that stands in for code push where
  /// Shorebird cannot reach. Deliberately the complement of
  /// [supportsCodePush] over the platforms that ship a release artifact:
  /// Linux and Windows desktop bundles. A browser is served, not installed,
  /// so it has nothing to update.
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

  /// Whether a "Paste" *button* can read the clipboard.
  ///
  /// Native builds always can. A browser can only through
  /// `navigator.clipboard.readText`, which exists solely in a
  /// [secureContext]; elsewhere Flutter's `Clipboard.getData` fails with a
  /// platform error rather than returning empty text. Ordinary keyboard
  /// paste into a text field (Ctrl+V) is handled by the browser itself and
  /// keeps working everywhere, which is what the guidance points at.
  bool get supportsClipboardRead => !isWeb || secureContext;

  /// Whether a server password can be kept between page loads.
  ///
  /// Native builds have a keystore or keyring (a Linux desktop without one
  /// is reported by the store's own probe). The browser build encrypts with
  /// `crypto.subtle`, which the browser withholds outside a [secureContext],
  /// so saving a profile *with* a password fails there; servers without a
  /// password are unaffected.
  bool get supportsSecureCredentialStorage => !isWeb || secureContext;

  /// Whether a Codex or Paseo agent-socket connection can be made at all.
  ///
  /// Both transports open a `dart:io` WebSocket upgrade by hand (Codex sends
  /// an `Authorization` header on it, Paseo a bearer subprotocol); the
  /// browser WebSocket API cannot set request headers, `dart:io` sockets do
  /// not exist on the web, and neither server offers another supported
  /// browser authentication contract. A token in the URL would be logged by
  /// every proxy in between and is not an option. So the browser build does
  /// not offer these connections rather than offer one that cannot succeed.
  /// OpenCode 2 uses HTTP Basic auth over fetch and is unaffected.
  bool get supportsAgentSocketConnections => !isWeb;

  /// Codex alone: the same answer as [supportsAgentSocketConnections].
  bool get supportsCodexConnections => supportsAgentSocketConnections;

  @override
  bool operator ==(Object other) =>
      other is PlatformCapabilities &&
      other.platform == platform &&
      other.isWeb == isWeb &&
      other.secureContext == secureContext;

  @override
  int get hashCode => Object.hash(platform, isWeb, secureContext);

  @override
  String toString() =>
      'PlatformCapabilities($platform, isWeb: $isWeb, '
      'secureContext: $secureContext)';
}

PlatformCapabilities? _override;

/// What this build can do. Read it at the point of use, never cache it — a
/// test may have swapped it since the widget was constructed.
PlatformCapabilities get platformCapabilities =>
    _override ??
    PlatformCapabilities(
      platform: defaultTargetPlatform,
      isWeb: kIsWeb,
      secureContext: browser_context.isSecureContext,
    );

/// The browser's "potentially trustworthy origin" rule, evaluated on the
/// page URL: `https` (and `file`) always; plain `http` only from the loopback
/// names browsers exempt. Exposed for tests; the real build reads
/// `Uri.base`, which is the page location in a browser.
@visibleForTesting
bool pageIsSecureContext(Uri page) => _pageIsSecureContext(page);

bool _pageIsSecureContext(Uri page) {
  switch (page.scheme) {
    case 'https':
    case 'wss':
    case 'file':
      return true;
  }
  final host = page.host.toLowerCase();
  return host == 'localhost' ||
      host.endsWith('.localhost') ||
      host == '127.0.0.1' ||
      host == '::1';
}

/// Pins [platformCapabilities] for a test. Pass `null` to restore the real
/// platform; always do so from `addTearDown` so one test cannot leak its
/// platform into the next.
@visibleForTesting
set debugPlatformCapabilities(PlatformCapabilities? value) => _override = value;

@visibleForTesting
PlatformCapabilities? get debugPlatformCapabilities => _override;
