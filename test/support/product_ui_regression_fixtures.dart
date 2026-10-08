import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';

class TestApi extends OpenCodeApi {
  TestApi({this.files, this.findFiles, this.contents = const {}})
    : super(baseUrl: 'http://localhost');

  final Future<List<FileNode>> Function(String path)? files;
  final Future<List<String>> Function(String query)? findFiles;
  final Map<String, FileContent> contents;

  @override
  Future<List<FileNode>> listFiles([String path = '']) async =>
      files?.call(path) ?? const [];

  @override
  Future<List<String>> findFile(String query) async =>
      findFiles?.call(query) ?? const [];

  @override
  Future<FileContent> fileContent(String path) async => contents[path]!;

  @override
  Future<List<Session>> sessions() async => const [];

  @override
  Future<Map<String, String>> sessionStatuses() async => const {};
}

class LocationRepository
    implements ProductRepository, LocationAwareProductRepository {
  int _revision = 0;
  int terminalLoads = 0;
  CatalogSnapshot catalog = const CatalogSnapshot(
    providers: [],
    models: [],
    agents: [],
  );

  @override
  int get locationRevision => _revision;

  @override
  void setLocation({String? directory, String? workspace}) {
    _revision++;
  }

  @override
  Future<List<WorkspaceProject>> listProjects() async => const [];

  @override
  Future<List<WorkspaceInfo>> listWorkspaces() async => const [];

  @override
  Future<List<TerminalProcess>> listTerminals() async {
    terminalLoads++;
    return const [];
  }

  @override
  Future<CatalogSnapshot> loadCatalog() async => catalog;

  @override
  Future<List<VersionControlFile>> listFileStatuses() async => const [];

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class SignalController extends ConnectionController {
  SignalController(super.store);

  void signalLocation(ProductRepository value) {
    repository = value;
    locationRevision++;
    notifyListeners();
  }
}

Future<ConnectionController> regressionController({
  OpenCodeApi? api,
  ProductRepository? repository,
}) async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  return ConnectionController(ProfileStore(prefs: prefs))
    ..api = api ?? TestApi()
    ..repository = repository
    ..status = StreamStatus.connected;
}
