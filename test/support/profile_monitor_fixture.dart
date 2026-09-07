import 'dart:async';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';

class MonitorTestGateway implements ServerGateway {
  MonitorTestGateway({
    this.requests = const [],
    this.failure = false,
    this.pending,
  });
  final List<PermissionRequest> requests;
  final bool failure;
  final Completer<List<PermissionRequest>>? pending;
  @override
  bool isClosed = false;
  @override
  String? directory;
  @override
  String? workspace;
  @override
  ServerCapabilities get capabilities => const ServerCapabilities(forms: false);
  @override
  void setLocation({String? directory, String? workspace}) {
    this.directory = directory;
    this.workspace = workspace;
  }

  @override
  void close() {
    isClosed = true;
  }

  @override
  Future<List<PermissionRequest>> pendingPermissions() async {
    if (failure) throw StateError('SENSITIVE fixture error');
    if (pending != null) return pending!.future;
    return requests;
  }

  @override
  Future<Map<String, String>> sessionStatuses() async => {
    'same-session': 'busy',
  };
  @override
  Future<ServerPage<Session>> sessionPage({
    String? cursor,
    int limit = 100,
  }) async => ServerPage(
    items: [
      Session(
        id: 'same-session',
        title: 'Private title',
        directory: directory,
        workspaceID: workspace,
      ),
    ],
  );
  @override
  Future<Session> session(String id) async =>
      Session(id: id, directory: directory, workspaceID: workspace);
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class MonitorTestOperations implements ServerOperationsGateway {
  @override
  void setLocation({String? directory, String? workspace}) {}
  @override
  Future<List<PendingQuestion>> listQuestions() async => [];
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

PermissionRequest request(int id) => PermissionRequest(
  id: 'request-$id',
  sessionID: 'same-session',
  permission: 'edit',
);
Future<ProfileStore> monitorStore({int count = 2}) async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  final store = ProfileStore(prefs: prefs);
  await store.load();
  for (var i = 1; i <= count; i++) {
    await store.upsert(
      ServerProfile(
        id: 'profile-$i',
        name: 'Server $i',
        baseUrl: 'https://server$i.example',
        username: '',
        password: '',
      ),
    );
  }
  return store;
}
