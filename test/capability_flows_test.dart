// coord-main (C20): every capabilities.json enable flow resolves to a
// registered handler at runtime, and the handlers open the right door.
import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/capability_flows.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/servers_screen.dart'
    show ServersRouteRequest, ServersRouteRequestKind;
import 'package:shared_preferences/shared_preferences.dart';

Future<ConnectionController> _controller() async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  return ConnectionController(ProfileStore(prefs: prefs));
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(KitCapabilities.debugReset);
  tearDown(KitCapabilities.debugReset);

  test('every capabilities.json enable flow resolves on Android', () async {
    final controller = await _controller();
    addTearDown(controller.dispose);
    registerCapabilityFlows(
      controller,
      platform: const PlatformCapabilities.android(),
    );
    expect(KitCapabilities.unregisteredFlows, isEmpty);

    final matrix =
        jsonDecode(File('docs/ux-system/capabilities.json').readAsStringSync())
            as Map<String, dynamic>;
    final ids = [
      for (final flow in matrix['enableFlows'] as List)
        (flow as Map<String, dynamic>)['capability'] as String,
    ];
    expect(ids, hasLength(KitEnableFlows.all.length));
    for (final id in ids) {
      expect(KitCapabilities.canEnable(id), isTrue, reason: id);
    }
  });

  test('a computer registers only the flows it can run', () async {
    final controller = await _controller();
    addTearDown(controller.dispose);
    registerCapabilityFlows(
      controller,
      platform: const PlatformCapabilities.linuxDesktop(),
    );
    expect(KitCapabilities.unregisteredFlows.toSet(), {
      KitEnableFlows.phoneSetup,
      KitEnableFlows.addToolClaude,
      KitEnableFlows.voiceModelSetup,
      KitEnableFlows.allowMicrophone,
      KitEnableFlows.allowNotifications,
      KitEnableFlows.allowBackground,
      KitEnableFlows.allowCamera,
      KitEnableFlows.tailscaleSetup,
    });
    // Adding a computer is the same on both.
    expect(KitCapabilities.canEnable('server.any'), isTrue);
    expect(KitCapabilities.canEnable('phone.builtin'), isFalse);
  });

  testWidgets('Turn it on opens Add server at its first step', (tester) async {
    final controller = await _controller();
    addTearDown(controller.dispose);
    registerCapabilityFlows(
      controller,
      platform: const PlatformCapabilities.linuxDesktop(),
    );
    final opened = <RouteSettings>[];
    await tester.pumpWidget(
      MaterialApp(
        theme: AppTheme.light(),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        onGenerateRoute: (settings) {
          if (settings.name != '/') opened.add(settings);
          return MaterialPageRoute<void>(
            settings: settings,
            builder: (_) => const Scaffold(
              body: KitCapabilityExplainer.row(
                capability: 'server.oc2',
                host: KitHost.openCode2,
              ),
            ),
          );
        },
      ),
    );
    await tester.tap(find.text('Connect OpenCode 2').first);
    await tester.pumpAndSettle();
    expect(opened.single.name, '/servers');
    final request = opened.single.arguments! as ServersRouteRequest;
    expect(request.kind, ServersRouteRequestKind.add);
    expect(request.backend, isNull);
  });

  for (final (flow, backend) in [
    (KitEnableFlows.addServer, null),
    (KitEnableFlows.addServerCodex, ServerBackend.codex),
    (KitEnableFlows.addServerPaseo, ServerBackend.paseo),
  ]) {
    testWidgets('$flow carries only its selected backend to Add server', (
      tester,
    ) async {
      final controller = await _controller();
      addTearDown(controller.dispose);
      final handlers = capabilityFlowHandlers(
        controller,
        platform: const PlatformCapabilities.linuxDesktop(),
      );
      final opened = <RouteSettings>[];
      await tester.pumpWidget(
        MaterialApp(
          home: Builder(
            builder: (context) => TextButton(
              onPressed: () => unawaited(
                handlers[flow]!(
                  context,
                  KitEnableRequest(capability: 'server.any', flow: flow),
                ),
              ),
              child: const Text('Open selected server setup'),
            ),
          ),
          onGenerateRoute: (settings) {
            opened.add(settings);
            return MaterialPageRoute<void>(
              settings: settings,
              builder: (_) => const Scaffold(body: Text('Server setup')),
            );
          },
        ),
      );
      await tester.tap(find.text('Open selected server setup'));
      await tester.pumpAndSettle();
      expect(opened.single.name, '/servers');
      final request = opened.single.arguments! as ServersRouteRequest;
      expect(request.kind, ServersRouteRequestKind.add);
      expect(request.backend, backend);
      expect(request.profileID, isNull);
    });
  }
}
