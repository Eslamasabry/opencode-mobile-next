// The servers area's coverage ratchets (data and action gates) share this:
// a profile store and connection that touch no network or secure storage,
// the app shell the Servers screen needs, and a scripted Paseo daemon.
//
// The wire cases are served to the app's REAL code: the connection probe
// reads them over a Dio adapter, the Paseo probe over the scripted daemon's
// socket, so the words on screen come from the same mappers a phone uses.
import 'dart:async';
import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/paseo/transport.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profile_monitor.dart'
    show MonitorGatewayFactory;
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/servers_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../tool/capture/fixtures.dart';

/// Saved servers held in memory; saves and removals are recorded.
class ServersStore extends ProfileStore {
  ServersStore({required super.prefs, List<ServerProfile> seeded = const []})
    : saved = [...seeded];

  final List<ServerProfile> saved;
  final removed = <String>[];

  @override
  List<ServerProfile> get profiles => List.unmodifiable(saved);

  /// With [selectFirst] the first saved server is the selected one; without
  /// it none is.
  bool selectFirst = true;

  @override
  String? get activeId =>
      selectFirst && saved.isNotEmpty ? saved.first.id : null;

  @override
  Future<String?> secureStorageProblem() async => null;

  @override
  Future<void> remove(String id) async {
    removed.add(id);
    saved.removeWhere((p) => p.id == id);
  }

  @override
  Future<void> upsert(ServerProfile profile) async {
    saved
      ..removeWhere((p) => p.id == profile.id)
      ..add(profile);
  }
}

/// Records connect attempts; a successful one installs a transport so the
/// Servers screen sees an established connection.
class ServersConnection extends ConnectionController {
  ServersConnection(super.store, {super.monitorGatewayFactory});

  final connects = <ServerProfile>[];

  @override
  Future<void> connect(
    ServerProfile profile, {
    bool redetectOnFailure = true,
  }) async {
    connects.add(profile);
    api = OpenCodeApi(baseUrl: profile.baseUrl);
  }
}

Future<(ServersStore, ServersConnection)> serversState({
  List<ServerProfile> profiles = const [],
  MonitorGatewayFactory? monitorGatewayFactory,
}) async {
  SharedPreferences.setMockInitialValues({});
  final store = ServersStore(
    prefs: await SharedPreferences.getInstance(),
    seeded: profiles,
  );
  return (
    store,
    ServersConnection(store, monitorGatewayFactory: monitorGatewayFactory),
  );
}

/// The Servers screen inside the app shell, under [boundary].
Widget serversApp(
  GlobalKey boundary,
  ServersStore store,
  ConnectionController controller, {
  Widget home = const ServersScreen(),
  Map<String, WidgetBuilder> routes = const {},
}) => captureApp(
  home: home,
  boundaryKey: boundary,
  controller: controller,
  store: store,
  routes: {
    '/home': (_) => const Scaffold(body: Text('home-route')),
    '/guide': (_) => const Scaffold(body: Text('guide-route')),
    ...routes,
  },
);

/// The Termux bridge answers "not installed", as on a phone without it.
void mockNoTermux() {
  TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
      .setMockMethodCallHandler(
        const MethodChannel('oc/termux'),
        (call) async => {'installed': false},
      );
  addTearDown(
    () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(const MethodChannel('oc/termux'), null),
  );
}

/// Serves canned HTTP answers per path, as the connection probe sees them.
class WireAdapter implements HttpClientAdapter {
  WireAdapter(this.handler);

  final ResponseBody Function(RequestOptions options) handler;
  final requests = <RequestOptions>[];

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(options);
    return handler(options);
  }

  @override
  void close({bool force = false}) {}
}

ResponseBody wireJson(Object body, [int status = 200]) =>
    ResponseBody.fromString(
      jsonEncode(body),
      status,
      headers: {
        Headers.contentTypeHeader: ['application/json'],
      },
    );

ResponseBody wireEmpty(int status) => ResponseBody.fromString('', status);

/// A scripted Paseo daemon: answers `hello` with the case's server_info and
/// replies to requests by type.
class WireDaemon implements PaseoSocket {
  WireDaemon(this.serverInfo);

  final Map<String, dynamic> serverInfo;
  final _incoming = StreamController<Object?>();
  final handlers =
      <
        String,
        (String, Map<String, dynamic>)? Function(Map<String, dynamic>)
      >{};
  bool closed = false;

  @override
  int? closeCode;

  @override
  Stream<Object?> get messages => _incoming.stream;

  @override
  void send(String message) {
    final json = jsonDecode(message) as Map<String, dynamic>;
    if (json['type'] == 'hello') {
      push('status', serverInfo);
      return;
    }
    if (json['type'] == 'ping') return;
    final inner = json['message'] as Map<String, dynamic>;
    final reply = handlers[inner['type']]?.call(inner);
    if (reply != null) {
      push(reply.$1, {...reply.$2, 'requestId': inner['requestId']});
    }
  }

  void push(String type, Map<String, dynamic> payload) {
    scheduleMicrotask(() {
      if (closed) return;
      _incoming.add(
        jsonEncode({
          'type': 'session',
          'message': {'type': type, 'payload': payload},
        }),
      );
    });
  }

  @override
  Future<void> close() async {
    closed = true;
    await _incoming.close();
  }
}

Future<void> frames(WidgetTester tester, [int count = 8]) async {
  for (var i = 0; i < count; i++) {
    await tester.pump(const Duration(milliseconds: 120));
  }
}
