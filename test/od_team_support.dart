// Look-gate pictures for the OD1 team lane: a screen drawn on a phone-sized
// view and written to build/coverage/od-team-NAME.png for the coordinator.
import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/orchestration/adapters/gascity/gascity_gateway.dart';
import 'package:opencode_mobile/orchestration/adapters/fixture/fixture_gateway.dart';
import 'package:opencode_mobile/state/orchestration.dart';
import 'package:opencode_mobile/state/orchestration_store.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'coverage/lists_support.dart' show WireRequest, withRealHttp;
import 'coverage/paseo_coverage_support.dart' show writeCasePng;
import 'coverage/team_support.dart';

Future<void> odShot(WidgetTester tester, Widget home, String name) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(odApp(home));
  await teamSettle(tester);
  await odCapture(tester, name);
}

/// A real Gas City gateway against a loopback supervisor serving the
/// coverage family's first city, with every mutation recorded.
class OdTeam {
  OdTeam._(this.wire, this.city, this.team, this.gateway);

  final TeamHost wire;
  final Map<String, Object?> city;
  final OrchestrationController team;
  final GasCityGateway gateway;

  /// Every mutation the supervisor received, oldest first.
  final writes = <WireRequest>[];

  /// Answers a mutation; null falls back to `{"status": "ok"}`.
  FutureOr<Object?> Function(WireRequest request)? onWrite;

  static Future<OdTeam> boot({bool front = true}) async {
    final samples =
        jsonDecode(
              File(
                'test/fixtures/coverage/gascity_team_samples.json',
              ).readAsStringSync(),
            )
            as Map;
    final payload = ((samples['cases'] as List).first as Map)['payload'] as Map;
    // A deep copy: tests change the city as the host would.
    final city = jsonDecode(jsonEncode(payload)) as Map<String, Object?>;
    final wire = await TeamHost.start();
    late OdTeam self;
    final (team, gateway) = await withRealHttp(
      () => bootTeam(wire, city, front: front),
    );
    self = OdTeam._(wire, city, team, gateway);
    wire.handler = (request) async {
      if (request.method != 'GET') {
        self.writes.add(request);
        return await self.onWrite?.call(request) ?? {'status': 'ok'};
      }
      return supervisorAnswer(city, request);
    };
    return self;
  }

  Future<void> close() async {
    await team.stop();
    await gateway.close();
    await wire.close();
  }
}

/// The boundary [odApp] draws inside, so [odCapture] can write a picture of
/// whatever the test has on screen.
final odBoundary = GlobalKey();

/// [teamApp] inside the picture boundary.
Widget odApp(Widget home) =>
    RepaintBoundary(key: odBoundary, child: teamApp(home));

/// Writes what the test shows as build/coverage/od-team-NAME.png.
Future<void> odCapture(WidgetTester tester, String name) =>
    writeCasePng(tester, odBoundary, 'od-team-$name');

/// The run fixture behind a real controller: controls are recorded on
/// [gateway] ([FixtureOrchestrationGateway.controlCalls]) and never sent
/// anywhere, so widget taps can be followed to the gateway call.
class OdFixture {
  OdFixture._(this.team, this.gateway);

  final OrchestrationController team;
  final FixtureOrchestrationGateway gateway;

  /// The fixture's one project.
  static const rig = 'ocproof';

  static Future<OdFixture> boot() async {
    SharedPreferences.setMockInitialValues({});
    final store = OrchestrationStore(await SharedPreferences.getInstance());
    final gateway = FixtureOrchestrationGateway(
      fixturePath: 'tool/qa/gascity_fixture',
    );
    final config = OrchestrationConfig(
      provider: OrchestrationProvider.fixture,
      url: 'tool/qa/gascity_fixture',
      city: 'bright-lights',
      enabledAt: DateTime.utc(2026, 9, 10),
    );
    final team = OrchestrationController(
      profile: ServerProfile(
        id: 'srv-1',
        name: 'Development PC',
        baseUrl: 'https://server.example:4096',
        orchestration: config,
      ),
      config: config,
      store: store,
      gatewayFactory: (_, _) => gateway,
      now: () => DateTime.utc(2026, 10, 9, 10),
    );
    await team.start();
    return OdFixture._(team, gateway);
  }

  Future<void> close() async {
    await team.stop();
    team.dispose();
  }
}
