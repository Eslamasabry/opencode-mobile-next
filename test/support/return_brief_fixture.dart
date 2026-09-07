import 'package:flutter/material.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/screens/workspace_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class BriefApi extends OpenCodeApi {
  BriefApi() : super(baseUrl: 'http://localhost');
  final formReplies = <String>[];
  @override
  ServerCapabilities get capabilities =>
      const ServerCapabilities(projectManagement: false, forms: true);
  @override
  Future<ServerPage<MessageWithParts>> messagePage(
    String id, {
    String? cursor,
    int limit = 100,
  }) async => const ServerPage(items: []);
  @override
  Future<void> replyForm(
    String sessionID,
    String formID,
    Map<String, dynamic> answer,
  ) async => formReplies.add(formID);
  @override
  Future<void> cancelForm(String sessionID, String formID) async =>
      formReplies.add(formID);
}

class BriefRepository extends ProductRepository
    implements SessionReadStateGateway {
  final views = <String>[];
  @override
  Future<void> viewSession(String id, int idle) async => views.add(id);
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class BriefController extends ConnectionController {
  BriefController(super.store);
  bool known = true;
  bool partial = false;
  @override
  bool get hasMoreSessions => partial;
  Future<void>? wake;
  @override
  bool get supportsSessionReadState => known;
  @override
  Future<ServerGateway?> prepareActionTransport() async {
    await wake;
    return api;
  }

  @override
  Future<ServerOperationsGateway?> prepareActionRepository() async =>
      repository;
  @override
  Future<void> refreshSessions() async {}
  @override
  Future<void> refreshPendingPermissions() async {}
  @override
  Future<void> refreshPendingQuestions() async {}
  void changeProject() {
    directory = '/work/other';
    locationRevision++;
    sessionsById = {};
    permissions = {};
    questions = {};
    forms = {};
    notifyListeners();
  }

  void publish() => notifyListeners();
}

Session briefSession(String id, {int idle = 20}) => Session(
  id: id,
  title: id == 'results'
      ? 'Polish the mobile checkout'
      : 'Review the database migration',
  directory: '/work/shop',
  time: SessionTime(created: 1, updated: idle, idle: idle),
);

Future<BriefController> briefController({bool requests = false}) async {
  final profiles = ProfileStore(prefs: await SharedPreferences.getInstance());
  await profiles.load();
  await profiles.upsert(
    ServerProfile(
      id: 'brief-profile',
      name: 'Workstation',
      baseUrl: 'http://localhost',
    ),
  );
  await profiles.setActiveId('brief-profile');
  final c = BriefController(profiles)
    ..api = BriefApi()
    ..repository = BriefRepository()
    ..directory = '/work/shop'
    ..status = StreamStatus.connected;
  c.sessionsById['results'] = briefSession('results');
  if (requests) {
    c.sessionsById['request'] = briefSession('request');
    c.permissions['p1'] = PermissionRequest(
      id: 'p1',
      sessionID: 'request',
      permission: 'bash',
      patterns: ['git status'],
    );
  }
  return c;
}

Widget briefApp(
  BriefController c, {
  double scale = 1,
  bool dark = false,
  bool rtl = false,
  Widget? home,
  ThemeData? theme,
}) => MaterialApp(
  debugShowCheckedModeBanner: false,
  theme: theme ?? (dark ? AppTheme.dark() : AppTheme.light()),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  builder: (context, child) => MediaQuery(
    data: MediaQuery.of(context).copyWith(textScaler: TextScaler.linear(scale)),
    child: Directionality(
      textDirection: rtl ? TextDirection.rtl : TextDirection.ltr,
      child: child!,
    ),
  ),
  onGenerateRoute: (settings) => MaterialPageRoute<void>(
    settings: settings,
    builder: (_) => Scaffold(
      appBar: AppBar(title: const Text('Conversation')),
      body: Text(settings.name ?? ''),
    ),
  ),
  home: Scaffold(body: home ?? WorkspaceScreen(controller: c)),
);
