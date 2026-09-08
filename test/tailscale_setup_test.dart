import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/platform/tailscale.dart';
import 'package:opencode_mobile/state/tailscale_address.dart';
import 'package:opencode_mobile/ui/screens/tailscale_setup_screen.dart';

class _Bridge extends TailscaleBridge {
  TailscaleAppState state = TailscaleAppState.installed;
  int checks = 0, opens = 0;
  bool opensSuccessfully = true;
  Completer<TailscaleAppState>? pending;
  @override
  Future<TailscaleAppState> check() async {
    checks++;
    return pending?.future ?? state;
  }

  @override
  Future<bool> open() async {
    opens++;
    return opensSuccessfully;
  }
}

Future<void> _show(
  WidgetTester tester,
  _Bridge bridge, {
  ValueChanged<String?>? result,
}) async {
  await tester.pumpWidget(
    MaterialApp(
      home: Builder(
        builder: (context) => Scaffold(
          body: TextButton(
            onPressed: () async {
              final value = await Navigator.of(context).push<String>(
                MaterialPageRoute(
                  builder: (_) => TailscaleSetupScreen(bridge: bridge),
                ),
              );
              result?.call(value);
            },
            child: const Text('Start'),
          ),
        ),
      ),
    ),
  );
  await tester.tap(find.text('Start'));
  await tester.pumpAndSettle();
}

Future<void> _tap(WidgetTester tester, String text) async {
  final finder = find.text(text);
  await tester.ensureVisible(finder);
  await tester.pumpAndSettle();
  await tester.tap(finder);
  await tester.pumpAndSettle();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  test(
    'review accepts HTTPS origins and bare MagicDNS without loosening policy',
    () {
      expect(
        normalizeTailscaleAddress(' computer.example.ts.net '),
        'https://computer.example.ts.net',
      );
      for (final value in [
        'computer.example.ts.net',
        'https://computer.example.ts.net:8443',
        'https://private.example',
      ]) {
        expect(isValidTailscaleAddress(value), isTrue, reason: value);
      }
      for (final value in [
        'http://100.64.0.1:4096',
        'http://localhost:4096',
        'https://a:0',
        'https://a:65536',
        'https://a:bad',
        'https://a/path',
        'https://user:secret@a',
        'https://a?token=secret',
        'https://a#token',
        'javascript:alert(1)',
        '',
      ]) {
        expect(isValidTailscaleAddress(value), isFalse, reason: value);
      }
    },
  );

  test(
    'native bridge maps only explicit package evidence and fails closed',
    () async {
      const channel = MethodChannel('oc/tailscale');
      final messenger =
          TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
      final methods = <String>[];
      Object? result = 'installed';
      messenger.setMockMethodCallHandler(channel, (call) async {
        methods.add(call.method);
        return result;
      });
      addTearDown(() => messenger.setMockMethodCallHandler(channel, null));
      const bridge = TailscaleBridge();
      expect(await bridge.check(), TailscaleAppState.installed);
      result = 'connected';
      expect(await bridge.check(), TailscaleAppState.unavailable);
      result = 'missing';
      expect(await bridge.check(), TailscaleAppState.missing);
      result = true;
      expect(await bridge.open(), isTrue);
      result = false;
      expect(await bridge.open(), isFalse);
      debugPlatformCapabilities = const PlatformCapabilities.linuxDesktop();
      addTearDown(() => debugPlatformCapabilities = null);
      expect(await bridge.check(), TailscaleAppState.unsupported);
      expect(await bridge.open(), isFalse);
      expect(methods, ['check', 'check', 'check', 'open', 'open']);
    },
  );

  testWidgets(
    'installed is unverified; open and resume preserve the typed address',
    (tester) async {
      final bridge = _Bridge();
      await _show(tester, bridge);
      expect(
        find.text('Tailscale is installed. VPN connection is unverified.'),
        findsOneWidget,
      );
      expect(bridge.opens, 0);
      await tester.enterText(find.byType(TextField), 'work.example.ts.net');
      await _tap(tester, 'Open Tailscale');
      expect(bridge.opens, 1);
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
      await tester.pumpAndSettle();
      expect(bridge.checks, greaterThan(1));
      expect(
        tester.widget<TextField>(find.byType(TextField)).controller!.text,
        'work.example.ts.net',
      );
      expect(find.textContaining('Welcome back.'), findsOneWidget);
    },
  );

  testWidgets(
    'missing app offers official install with external host review and recheck',
    (tester) async {
      final bridge = _Bridge()..state = TailscaleAppState.missing;
      await _show(tester, bridge);
      expect(find.textContaining('not installed'), findsOneWidget);
      await _tap(tester, 'Get official Android app');
      expect(find.text('Open external link?'), findsOneWidget);
      expect(find.text('play.google.com'), findsOneWidget);
      await tester.tap(find.text('Cancel'));
      await tester.pumpAndSettle();
      bridge.state = TailscaleAppState.installed;
      await _tap(tester, 'Check app again');
      expect(find.text('Open Tailscale'), findsOneWidget);
      expect(bridge.opens, 0);
    },
  );

  testWidgets('failed launch gives recovery and review never probes or saves', (
    tester,
  ) async {
    final bridge = _Bridge()..opensSuccessfully = false;
    String? result;
    await _show(tester, bridge, result: (value) => result = value);
    await _tap(tester, 'Open Tailscale');
    expect(find.textContaining('could not open'), findsOneWidget);
    await tester.enterText(find.byType(TextField), 'http://100.64.0.1:4096');
    await _tap(tester, 'Continue to authentication');
    expect(result, isNull);
    expect(find.textContaining('valid port'), findsOneWidget);
    await tester.enterText(find.byType(TextField), 'work.example.ts.net');
    await _tap(tester, 'Continue to authentication');
    expect(result, 'https://work.example.ts.net');
  });

  testWidgets('late package check after dismissal is ignored', (tester) async {
    final bridge = _Bridge();
    await _show(tester, bridge);
    bridge.pending = Completer<TailscaleAppState>();
    await tester.ensureVisible(find.text('Check app again'));
    await tester.tap(find.text('Check app again'));
    await tester.pump();
    await tester.pageBack();
    await tester.pumpAndSettle();
    bridge.pending!.complete(TailscaleAppState.installed);
    await tester.pump();
    expect(tester.takeException(), isNull);
  });
}
