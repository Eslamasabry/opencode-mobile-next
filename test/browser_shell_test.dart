// The browser build of the product shell: what a desktop browser shares with
// the desktop window (keyboard, mouse), what the browser itself withholds
// (reserved accelerators, clipboard read and credential storage on a
// plain-http origin, the agent-socket WebSocket header), and what stays out
// because there is no phone underneath (Termux, voice, background service).
//
// Everything runs on the VM under a pinned `PlatformCapabilities.browser()`;
// nothing here imports dart:io or touches a real browser. The servers screen
// and the app root in a browser are in `browser_servers_test.dart`. Real
// browser proof is the coordinator's serialized check, recorded in
// docs/verification/web-mode-2026-11-01.md.
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/desktop/context_menu.dart';
import 'package:opencode_mobile/ui/desktop/desktop_interaction.dart';
import 'package:opencode_mobile/ui/desktop/shortcuts.dart';
import 'package:opencode_mobile/ui/screens/settings/browser_mode_screen.dart';
import 'package:opencode_mobile/ui/search/search_index.dart';
import 'package:shared_preferences/shared_preferences.dart';

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

/// Pins the browser build for one test. A Linux workstation browser on a
/// secure origin by default; the phone browser and the plain-http origin are
/// the cases a test opts into.
void inBrowser({
  TargetPlatform platform = TargetPlatform.linux,
  bool secureContext = true,
}) {
  debugPlatformCapabilities = PlatformCapabilities.browser(
    platform: platform,
    secureContext: secureContext,
  );
  addTearDown(() => debugPlatformCapabilities = null);
}

/// The shell shortcut layer around a surface, as `OcApp` installs it.
class _ShellHarness {
  final navigatorKey = GlobalKey<NavigatorState>();
  final signals = AppShortcutSignals();
  int newSessions = 0;
  int settingsOpens = 0;

  Widget app(Widget home) => MaterialApp(
    navigatorKey: navigatorKey,
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    scrollBehavior: const AppScrollBehavior(),
    builder: (context, child) => AppShortcuts(
      navigatorKey: navigatorKey,
      signals: signals,
      handlers: AppShortcutHandlers(
        onNewSession: () => newSessions++,
        onOpenSettings: () => settingsOpens++,
        paletteCommands: (context) => [
          DesktopCommand(
            label: 'New session',
            icon: AppIconography.add,
            keys: newSessionShortcutKeys,
            onInvoke: () => newSessions++,
          ),
        ],
      ),
      child: child ?? const SizedBox.shrink(),
    ),
    home: home,
  );
}

Future<void> _pressWith(
  WidgetTester tester,
  LogicalKeyboardKey modifier,
  LogicalKeyboardKey key,
) async {
  await tester.sendKeyDownEvent(modifier);
  await tester.sendKeyEvent(key);
  await tester.sendKeyUpEvent(modifier);
  await tester.pumpAndSettle();
}

Future<void> _ctrl(WidgetTester tester, LogicalKeyboardKey key) =>
    _pressWith(tester, LogicalKeyboardKey.controlLeft, key);

Future<void> _alt(WidgetTester tester, LogicalKeyboardKey key) =>
    _pressWith(tester, LogicalKeyboardKey.altLeft, key);

/// The intent bound to [key] under exactly these modifiers, or null.
/// `SingleActivator` has no value equality, so the table is walked.
Intent? _bound(
  Map<ShortcutActivator, Intent> bindings,
  LogicalKeyboardKey key, {
  bool control = false,
  bool meta = false,
  bool alt = false,
}) {
  for (final entry in bindings.entries) {
    final activator = entry.key;
    if (activator is SingleActivator &&
        activator.trigger == key &&
        activator.control == control &&
        activator.meta == meta &&
        activator.alt == alt) {
      return entry.value;
    }
  }
  return null;
}

final _en = lookupAppLocalizations(const Locale('en'));

/// The Browser mode page, mounted under the app theme as Settings shows it.
Widget _browserModePage({Locale locale = const Locale('en')}) => MaterialApp(
  theme: AppTheme.light(),
  locale: locale,
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home: const BrowserModeScreen(),
);

Future<ConnectionController> _controller() async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  return ConnectionController(ProfileStore(prefs: prefs));
}

// ---------------------------------------------------------------------------

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    // ProfileStore reaches for secure storage; an unmocked channel hangs.
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });

  group('platform capabilities', () {
    test('a desktop browser has desktop input but no native window', () {
      const browser = PlatformCapabilities.browser();
      expect(browser.isWeb, isTrue);
      expect(browser.isDesktop, isFalse);
      expect(browser.isAndroid, isFalse);
      expect(browser.isDesktopBrowser, isTrue);
      expect(browser.usesDesktopInteractions, isTrue);
      // Native-window concerns stay off: nothing to size, nothing to update.
      expect(browser.supportsDesktopReleaseCheck, isFalse);
      expect(browser.supportsCodePush, isFalse);
    });

    test('a browser offers no phone service and no agent sockets', () {
      const browser = PlatformCapabilities.browser();
      expect(browser.supportsTermux, isFalse);
      expect(browser.supportsTailscaleHandoff, isFalse);
      expect(browser.supportsVoice, isFalse);
      expect(browser.supportsReadAloud, isFalse);
      expect(browser.supportsVoiceConversation, isFalse);
      expect(browser.supportsPromptPhotos, isFalse);
      expect(browser.supportsLocalPdf, isFalse);
      expect(browser.supportsBackgroundService, isFalse);
      expect(browser.supportsNotifications, isFalse);
      expect(browser.supportsHomeWidget, isFalse);
      expect(browser.supportsLaunchShortcuts, isFalse);
      expect(browser.supportsQuickSettingsTile, isFalse);
      expect(browser.supportsUsbHostBridge, isFalse);
      expect(browser.supportsQrPairing, isFalse);
      // Codex and Paseo open a dart:io WebSocket by hand.
      expect(browser.supportsAgentSocketConnections, isFalse);
      expect(browser.supportsCodexConnections, isFalse);
    });

    test('a secure origin keeps clipboard read and credential storage', () {
      const browser = PlatformCapabilities.browser();
      expect(browser.secureContext, isTrue);
      expect(browser.supportsClipboardRead, isTrue);
      expect(browser.supportsSecureCredentialStorage, isTrue);
    });

    test('a plain-http network origin loses both', () {
      const browser = PlatformCapabilities.browser(secureContext: false);
      expect(browser.supportsClipboardRead, isFalse);
      expect(browser.supportsSecureCredentialStorage, isFalse);
      // Everything else is unchanged by the origin.
      expect(browser.usesDesktopInteractions, isTrue);
      expect(browser.supportsAgentSocketConnections, isFalse);
    });

    test('a phone browser keeps the touch product', () {
      const phone = PlatformCapabilities.browser(
        platform: TargetPlatform.android,
      );
      expect(phone.isAndroid, isFalse, reason: 'a browser is not the app');
      expect(phone.isDesktopBrowser, isFalse);
      expect(phone.usesDesktopInteractions, isFalse);
      expect(phone.supportsTermux, isFalse);
      expect(phone.supportsBackgroundService, isFalse);
    });

    test('native builds are unchanged', () {
      const android = PlatformCapabilities.android();
      expect(android.usesDesktopInteractions, isFalse);
      expect(android.supportsClipboardRead, isTrue);
      expect(android.supportsSecureCredentialStorage, isTrue);
      expect(android.supportsAgentSocketConnections, isTrue);
      expect(android.supportsCodexConnections, isTrue);
      expect(android.secureContext, isTrue);

      const linux = PlatformCapabilities.linuxDesktop();
      expect(linux.isDesktop, isTrue);
      expect(linux.isDesktopBrowser, isFalse);
      expect(linux.usesDesktopInteractions, isTrue);
      expect(linux.supportsDesktopReleaseCheck, isTrue);
      expect(linux.supportsClipboardRead, isTrue);
      expect(linux.supportsAgentSocketConnections, isTrue);
    });

    test('the default capabilities on the VM are Android and not web', () {
      expect(platformCapabilities.isWeb, isFalse);
      expect(platformCapabilities.secureContext, isTrue);
      expect(platformCapabilities.usesDesktopInteractions, isFalse);
    });

    test('secure context follows the browser rule for the page origin', () {
      expect(pageIsSecureContext(Uri.parse('https://box.example/')), isTrue);
      expect(pageIsSecureContext(Uri.parse('http://localhost:8789/')), isTrue);
      expect(pageIsSecureContext(Uri.parse('http://LOCALHOST/')), isTrue);
      expect(pageIsSecureContext(Uri.parse('http://app.localhost/')), isTrue);
      expect(pageIsSecureContext(Uri.parse('http://127.0.0.1:8789/')), isTrue);
      expect(pageIsSecureContext(Uri.parse('http://[::1]:8789/')), isTrue);
      expect(pageIsSecureContext(Uri.parse('file:///index.html')), isTrue);
      // A Tailscale address over plain http is not a secure context.
      expect(
        pageIsSecureContext(Uri.parse('http://100.64.0.10:8789/')),
        isFalse,
      );
      expect(pageIsSecureContext(Uri.parse('http://box.lan/')), isFalse);
    });

    test('equality includes the origin', () {
      expect(
        const PlatformCapabilities.browser(),
        isNot(equals(const PlatformCapabilities.browser(secureContext: false))),
      );
      expect(
        const PlatformCapabilities.browser(),
        equals(
          const PlatformCapabilities(
            platform: TargetPlatform.linux,
            isWeb: true,
          ),
        ),
      );
    });
  });

  group('server addresses in a browser', () {
    // dart:io's InternetAddress throws on the web, so the plain-http consent
    // rule reads address literals with core Dart; the verdicts must be the
    // ones the phone gets.
    test('private and public literals are told apart without dart:io', () {
      for (final host in [
        '192.168.1.20',
        '10.1.2.3',
        '172.20.0.5',
        '[fd00::5]',
        'fe80::1%wlan0',
        '::ffff:192.168.1.5',
        'desk.local',
      ]) {
        expect(isPrivateNetworkHost(host), isTrue, reason: host);
      }
      for (final host in [
        '100.64.0.10',
        '8.8.8.8',
        '::ffff:8.8.8.8',
        '2001:db8::1',
        'example.com',
        '10.0.0.1.example.com',
      ]) {
        expect(isPrivateNetworkHost(host), isFalse, reason: host);
      }
    });
  });

  group('desktop interactions', () {
    testWidgets('are on in a desktop browser and off in a phone browser', (
      tester,
    ) async {
      inBrowser();
      expect(desktopInteractions, isTrue);
      debugPlatformCapabilities = const PlatformCapabilities.browser(
        platform: TargetPlatform.android,
      );
      expect(desktopInteractions, isFalse);
      debugPlatformCapabilities = const PlatformCapabilities.browser(
        platform: TargetPlatform.iOS,
      );
      expect(desktopInteractions, isFalse);
    });

    test(
      'the modifier label follows the operating system under the browser',
      () {
        inBrowser();
        expect(shortcutModifierLabel, 'Ctrl');
        expect(shortcutAltLabel, 'Alt');
        debugPlatformCapabilities = const PlatformCapabilities.browser(
          platform: TargetPlatform.macOS,
        );
        expect(shortcutModifierLabel, '⌘');
        expect(shortcutAltLabel, '⌥');
      },
    );
  });

  group('keyboard shortcuts in a browser', () {
    testWidgets('step the browser-reserved accelerators to Alt', (
      tester,
    ) async {
      inBrowser();
      final bindings = appShortcutBindings;
      // Ctrl/Cmd+N opens a browser window and Ctrl/Cmd+W closes the tab; a
      // page cannot intercept either, so neither may be bound.
      for (final activator in bindings.keys.cast<SingleActivator>()) {
        final reserved =
            activator.trigger == LogicalKeyboardKey.keyN ||
            activator.trigger == LogicalKeyboardKey.keyW;
        if (reserved) {
          expect(activator.alt, isTrue, reason: '$activator');
          expect(activator.control || activator.meta, isFalse);
        } else {
          expect(activator.control || activator.meta, isTrue);
        }
      }
      expect(
        _bound(bindings, LogicalKeyboardKey.keyN, alt: true),
        isA<NewSessionIntent>(),
      );
      expect(
        _bound(bindings, LogicalKeyboardKey.keyW, alt: true),
        isA<CloseRouteIntent>(),
      );
      expect(_bound(bindings, LogicalKeyboardKey.keyN, control: true), isNull);
      expect(_bound(bindings, LogicalKeyboardKey.keyW, meta: true), isNull);
      // Eight accelerators x Ctrl and Cmd, plus the two Alt stand-ins.
      expect(bindings.length, 18);
      expect(newSessionShortcutKeys, 'Alt + N');
      expect(closeRouteShortcutKeys, 'Alt + W');
    });

    testWidgets('keep the native desktop table as it shipped', (tester) async {
      debugPlatformCapabilities = const PlatformCapabilities.linuxDesktop();
      addTearDown(() => debugPlatformCapabilities = null);
      final bindings = appShortcutBindings;
      expect(bindings.length, 20);
      expect(
        _bound(bindings, LogicalKeyboardKey.keyN, control: true),
        isA<NewSessionIntent>(),
      );
      expect(
        _bound(bindings, LogicalKeyboardKey.keyW, meta: true),
        isA<CloseRouteIntent>(),
      );
      expect(_bound(bindings, LogicalKeyboardKey.keyN, alt: true), isNull);
      expect(newSessionShortcutKeys, 'Ctrl + N');
      expect(closeRouteShortcutKeys, 'Ctrl + W');
    });

    testWidgets('Alt+N starts a session; Ctrl+N is left to the browser', (
      tester,
    ) async {
      inBrowser();
      final harness = _ShellHarness();
      await tester.pumpWidget(
        harness.app(const Scaffold(body: Text('surface'))),
      );
      await tester.pumpAndSettle();

      await _ctrl(tester, LogicalKeyboardKey.keyN);
      expect(harness.newSessions, 0);

      await _alt(tester, LogicalKeyboardKey.keyN);
      expect(harness.newSessions, 1);
    });

    testWidgets('Ctrl+K and Ctrl+, still reach the shell', (tester) async {
      inBrowser();
      final harness = _ShellHarness();
      await tester.pumpWidget(
        harness.app(const Scaffold(body: Text('surface'))),
      );
      await tester.pumpAndSettle();

      await _ctrl(tester, LogicalKeyboardKey.comma);
      expect(harness.settingsOpens, 1);

      await _ctrl(tester, LogicalKeyboardKey.keyK);
      expect(
        find.byKey(const ValueKey('desktop-command-palette')),
        findsOneWidget,
      );
      // The launcher prints the binding that is actually live here.
      expect(find.text('Alt + N'), findsOneWidget);
      expect(find.text('Ctrl + N'), findsNothing);
    });

    testWidgets('the help sheet prints the browser bindings', (tester) async {
      inBrowser();
      final harness = _ShellHarness();
      await tester.pumpWidget(
        harness.app(const Scaffold(body: Text('surface'))),
      );
      await tester.pumpAndSettle();

      await _ctrl(tester, LogicalKeyboardKey.slash);
      expect(
        find.byKey(const ValueKey('keyboard-shortcuts-sheet')),
        findsOneWidget,
      );
      expect(find.text('Alt + N'), findsOneWidget);
      expect(find.text('Alt + W'), findsOneWidget);
      expect(find.text('Ctrl + N'), findsNothing);
      expect(find.text('Ctrl + W'), findsNothing);
      expect(find.text('Ctrl + K'), findsOneWidget);
    });

    testWidgets('a phone browser installs no shortcut layer', (tester) async {
      inBrowser(platform: TargetPlatform.android);
      final harness = _ShellHarness();
      await tester.pumpWidget(
        harness.app(const Scaffold(body: Text('surface'))),
      );
      await tester.pumpAndSettle();

      await _ctrl(tester, LogicalKeyboardKey.keyK);
      expect(
        find.byKey(const ValueKey('desktop-command-palette')),
        findsNothing,
      );
      await _alt(tester, LogicalKeyboardKey.keyN);
      expect(harness.newSessions, 0);
    });
  });

  group('the browser context menu guard', () {
    test('holds the browser menu off while any region is hovered', () async {
      final calls = <bool>[];
      final guard = BrowserContextMenuGuard(
        setBrowserMenuEnabled: (enabled) async => calls.add(enabled),
      );
      guard.enter();
      guard.enter();
      expect(calls, [false], reason: 'one disable for nested regions');
      guard.leave();
      expect(calls, [false], reason: 'still inside the outer region');
      guard.leave();
      expect(calls, [false, true]);
      guard.leave();
      expect(calls, [false, true], reason: 'a stray leave is a no-op');
      expect(guard.hoveredRegions, 0);
    });

    testWidgets('a region reports hover in a browser only', (tester) async {
      inBrowser();
      final calls = <bool>[];
      debugBrowserContextMenuGuard = BrowserContextMenuGuard(
        setBrowserMenuEnabled: (enabled) async => calls.add(enabled),
      );
      addTearDown(() => debugBrowserContextMenuGuard = null);

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: Center(
              child: ContextMenuRegion(
                actions: () => const [],
                child: const SizedBox(width: 100, height: 100),
              ),
            ),
          ),
        ),
      );
      final region = find.byKey(const ValueKey('browser-context-menu-region'));
      expect(region, findsOneWidget);

      final gesture = await tester.createGesture(kind: PointerDeviceKind.mouse);
      await gesture.addPointer(location: Offset.zero);
      addTearDown(gesture.removePointer);
      await tester.pump();
      expect(calls, isEmpty);

      await gesture.moveTo(tester.getCenter(region));
      await tester.pump();
      expect(calls, [false]);
      expect(browserContextMenuGuard.hoveredRegions, 1);

      await gesture.moveTo(const Offset(1, 1));
      await tester.pump();
      expect(calls, [false, true]);
      expect(browserContextMenuGuard.hoveredRegions, 0);

      // Unmounting a hovered region hands its count back too.
      await gesture.moveTo(tester.getCenter(region));
      await tester.pump();
      expect(calls, [false, true, false]);
      await tester.pumpWidget(const SizedBox.shrink());
      expect(calls, [false, true, false, true]);
      expect(browserContextMenuGuard.hoveredRegions, 0);
    });

    testWidgets('a native desktop window adds no hover region', (tester) async {
      debugPlatformCapabilities = const PlatformCapabilities.linuxDesktop();
      addTearDown(() => debugPlatformCapabilities = null);
      await tester.pumpWidget(
        MaterialApp(
          home: ContextMenuRegion(
            actions: () => const [],
            child: const SizedBox(width: 100, height: 100),
          ),
        ),
      );
      expect(
        find.byKey(const ValueKey('browser-context-menu-region')),
        findsNothing,
      );
    });
  });

  group('Help › Browser mode', () {
    testWidgets('states the secure-origin facts of this page', (tester) async {
      inBrowser();
      await tester.pumpWidget(_browserModePage());
      await tester.pumpAndSettle();

      expect(find.text(_en.browserModeTitle), findsOneWidget);
      expect(
        find.descendant(
          of: find.byKey(const ValueKey('browser-mode-secure-context')),
          matching: find.text(_en.browserModeSecureContextOk),
        ),
        findsOneWidget,
      );
      expect(find.text(_en.browserModeSecureContextMissing), findsNothing);
      // The two facts that hold on every origin.
      expect(
        find.byKey(const ValueKey('browser-mode-mixed-content')),
        findsOneWidget,
      );
      expect(find.byKey(const ValueKey('browser-mode-cors')), findsOneWidget);
    });

    testWidgets('warns on a plain-http origin and says how to lift it', (
      tester,
    ) async {
      inBrowser(secureContext: false);
      await tester.pumpWidget(_browserModePage());
      await tester.pumpAndSettle();

      expect(find.text(_en.browserModeSecureContextMissing), findsOneWidget);
      expect(find.text(_en.browserModeSecureContextOk), findsNothing);
    });

    testWidgets('names what needs the phone app and the agent sockets', (
      tester,
    ) async {
      inBrowser();
      await tester.pumpWidget(_browserModePage());
      await tester.pumpAndSettle();

      for (final key in [
        'browser-mode-termux',
        'browser-mode-voice',
        'browser-mode-background',
        'browser-mode-agent-socket',
      ]) {
        await tester.ensureVisible(find.byKey(ValueKey(key)));
        expect(find.byKey(ValueKey(key)), findsOneWidget, reason: key);
      }
    });

    testWidgets('the keyboard line is a desktop browser fact only', (
      tester,
    ) async {
      inBrowser();
      await tester.pumpWidget(_browserModePage());
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('browser-mode-keyboard')),
        findsOneWidget,
      );

      debugPlatformCapabilities = const PlatformCapabilities.browser(
        platform: TargetPlatform.android,
      );
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pumpWidget(_browserModePage());
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('browser-mode-keyboard')), findsNothing);
    });

    test('About and the guide describe the browser, not a desktop window', () {
      expect(_en.aboutBrowserSummary, isNot(contains('desktop')));
      expect(_en.aboutBrowserSummary, contains('browser'));
      final ar = lookupAppLocalizations(const Locale('ar'));
      expect(ar.aboutBrowserSummary, isNot(_en.aboutBrowserSummary));
      expect(_en.browserEditorInsecureOrigin, contains('HTTPS'));
    });

    testWidgets('reads in Arabic', (tester) async {
      inBrowser();
      await tester.pumpWidget(_browserModePage(locale: const Locale('ar')));
      await tester.pumpAndSettle();
      final ar = lookupAppLocalizations(const Locale('ar'));
      expect(find.text(ar.browserModeTitle), findsOneWidget);
      expect(ar.browserModeTitle, isNot(_en.browserModeTitle));
    });

    testWidgets('the Help group lists it in a browser and not on Android', (
      tester,
    ) async {
      final controller = await _controller();
      addTearDown(controller.dispose);

      List<String> found(PlatformCapabilities platform) => [
        for (final entry in searchEntries(
          _en,
          SearchScope(controller: controller, platform: platform),
          _en.browserModeTitle,
        ))
          entry.id,
      ];

      expect(
        found(const PlatformCapabilities.browser()),
        contains('settings-browser-mode'),
      );
      expect(
        found(const PlatformCapabilities.android()),
        isNot(contains('settings-browser-mode')),
      );
    });
  });
}
