// Shared by the AI Team coverage ratchets: a loopback "supervisor" that answers
// every route the team screens read from the family's whole-city payload, and
// the controller + screens that run on the app's real Gas City gateway.
import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/orchestration/adapters/gascity/gascity_gateway.dart';
import 'package:opencode_mobile/state/orchestration.dart';
import 'package:opencode_mobile/state/orchestration_store.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../tool/capture/fixtures.dart';
import 'lists_support.dart' show WireRequest;

const teamCity = 'phone';

/// A supervisor answer with its own status (a refusal), for tests that
/// need one; any other answer is a 200.
class TeamHostReply {
  const TeamHostReply(this.status, this.body);
  final int status;
  final Object? body;
}

/// A loopback supervisor: JSON routes answered from [handler], and the event
/// stream held open with heartbeats, so the team reads as connected.
class TeamHost {
  TeamHost._(this.server) {
    server.listen((request) async {
      final text = await utf8.decoder.bind(request).join();
      final seen = (
        method: request.method,
        path: request.uri.path,
        uri: request.uri,
        body: text,
      );
      requests.add(seen);
      final response = request.response;
      if (request.method == 'GET' &&
          request.uri.path.endsWith('/events/stream')) {
        response.headers.contentType = ContentType('text', 'event-stream');
        response.bufferOutput = false;
        _streams.add(response);
        response.write('event: heartbeat\ndata: {}\n\n');
        await response.flush();
        return;
      }
      final answer = await handler?.call(seen);
      response.headers.contentType = ContentType.json;
      if (answer == null) {
        response.statusCode = HttpStatus.notFound;
        response.write(
          jsonEncode({
            'type': 'about:blank',
            'title': 'Not Found',
            'status': 404,
            'detail': 'no route',
          }),
        );
      } else if (answer is TeamHostReply) {
        response.statusCode = answer.status;
        response.write(jsonEncode(answer.body));
      } else {
        response.write(jsonEncode(answer));
      }
      await response.close();
    });
    _beat = Timer.periodic(const Duration(milliseconds: 300), (_) {
      for (final stream in _streams.toList()) {
        try {
          stream.write('event: heartbeat\ndata: {}\n\n');
        } on Object {
          _streams.remove(stream);
        }
      }
    });
  }

  final HttpServer server;
  final requests = <WireRequest>[];
  final _streams = <HttpResponse>[];
  late final Timer _beat;
  FutureOr<Object?> Function(WireRequest request)? handler;

  static Future<TeamHost> start() async =>
      TeamHost._(await HttpServer.bind(InternetAddress.loopbackIPv4, 0));

  String get baseUrl => 'http://${server.address.host}:${server.port}';

  Future<void> close() async {
    _beat.cancel();
    for (final stream in _streams) {
      try {
        await stream.close();
      } on Object {
        // already gone
      }
    }
    await server.close(force: true);
  }
}

/// The supervisor's answer for [request] from one case's whole-city [city].
Object? supervisorAnswer(Map<String, Object?> city, WireRequest request) {
  final prefix = '/v0/city/$teamCity';
  final path = request.path;
  List<Object?> list(Object? v) => (v as List?)?.cast<Object?>() ?? const [];
  if (path == '/health') return city['supervisor'];
  if (path == '/v0/cities') {
    return {
      'items': list(city['cities']),
      'total': list(city['cities']).length,
    };
  }
  if (!path.startsWith(prefix)) return null;
  final tail = path.substring(prefix.length);
  final q = request.uri.queryParameters;
  Map<String, Object?> page(List<Object?> items, [String key = 'items']) => {
    key: items,
    'total': items.length,
  };
  switch (tail) {
    case '/health':
      return city['health'];
    case '/status':
      return city['status'];
    case '/agents':
      return page(list(city['agents']));
    case '/sessions':
      return page(list(city['sessions']));
    case '/pending':
      return page(list(city['pending']));
    case '/waits':
      return {'waits': list(city['waits']), 'capped': false};
    case '/usage':
      return city['usage'];
    case '/runs':
      return city['runs'];
    case '/convoys':
      return page(list(city['convoys']));
    case '/events':
      return page(const []);
    case '/beads':
      if (q['status'] == 'closed') return page(const []);
      if (q['ready'] == 'true') {
        return page([
          for (final b in list(city['beads']))
            if ((b as Map)['status'] == 'open' && b['is_blocked'] != true) b,
        ]);
      }
      return page(list(city['beads']));
  }
  final detail = RegExp(r'^/session/([^/]+)/pending$').firstMatch(tail);
  if (detail != null) {
    final entry = (city['pendingDetail'] as Map?)?[detail.group(1)];
    return {'supported': true, 'pending': ?entry};
  }
  final bead = RegExp(r'^/bead/([^/]+)$').firstMatch(tail);
  if (bead != null) {
    for (final b in [...list(city['beads']), ...list(city['convoys'])]) {
      if ((b as Map)['id'] == bead.group(1)) return b;
    }
  }
  return null;
}

/// A controller running on the real gateway against [wire], serving [city].
Future<(OrchestrationController, GasCityGateway)> bootTeam(
  TeamHost wire,
  Map<String, Object?> city, {
  bool front = true,
}) async {
  wire.handler = (r) => supervisorAnswer(city, r);
  SharedPreferences.setMockInitialValues({});
  final store = OrchestrationStore(await SharedPreferences.getInstance());
  final gateway = GasCityGateway(
    url: wire.baseUrl,
    city: teamCity,
    front: front,
  );
  final config = OrchestrationConfig(
    provider: OrchestrationProvider.gascity,
    url: wire.baseUrl,
    city: teamCity,
    enabledAt: DateTime.utc(2026, 10, 1),
  );
  final controller = OrchestrationController(
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
  await controller.start();
  return (controller, gateway);
}

Widget teamApp(Widget home) => MaterialApp(
  debugShowCheckedModeBanner: false,
  key: UniqueKey(),
  theme: captureTheme(),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  builder: (context, child) => MediaQuery(
    data: MediaQuery.of(context).copyWith(disableAnimations: true),
    child: child!,
  ),
  home: home,
);

/// Lets the page settle without waiting on animations that never end.
Future<void> teamSettle(WidgetTester tester) async {
  for (var i = 0; i < 12; i++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
}

HttpOverrides realHttp() => _RealHttp();

class _RealHttp extends HttpOverrides {}
