import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/pending_auth.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/library_screen.dart';
import 'package:opencode_mobile/ui/widgets/provider_logo.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _Api extends OpenCodeApi {
  _Api() : super(baseUrl: 'https://auth-test.example');
  @override
  ServerCapabilities get capabilities => const ServerCapabilities(
    providerRuntimeRefresh: false,
    integrationCommandAuth: true,
  );
  @override
  Future<Health> health() async => Health(healthy: true, version: 'test');
  @override
  Future<List<Session>> sessions() async => [];
  @override
  Future<Map<String, String>> sessionStatuses() async => {};
  @override
  Future<ProvidersResponse> providers() async =>
      ProvidersResponse(providers: []);
  @override
  Future<List<AgentInfo>> agents() async => [];
  @override
  Future<List<PermissionRequest>> pendingPermissions() async => [];
  @override
  Future<List<PermissionRequest>> pendingPermissionsV2() async => [];
  @override
  Future<List<Map<String, dynamic>>> pendingQuestionsV2() async => [];
}

class _Events extends EventStream {
  _Events({
    required super.api,
    required super.onEvent,
    required super.onStatus,
    super.onError,
  });
  @override
  void start() => onStatus(StreamStatus.connected);
  @override
  Future<void> dispose() async {}
}

class _Repository
    implements
        ProductRepository,
        IntegrationAuthRecoveryGateway,
        IntegrationCommandGateway {
  int starts = 0;
  Completer<IntegrationAuthLaunch>? gate;
  final launch = const IntegrationAuthLaunch(
    attemptID: 'synthetic-attempt',
    url: 'https://provider-auth.example/device',
    instructions: 'Enter code: ABCD-EFGH',
    mode: IntegrationAuthMode.auto,
  );
  @override
  void setLocation({String? directory, String? workspace}) {}
  @override
  Future<List<IntegrationInfo>> listIntegrations() async => const [
    IntegrationInfo(
      id: 'cloud',
      name: 'Cloud Provider',
      connectionCount: 0,
      methods: [
        IntegrationMethodInfo(
          type: 'oauth',
          id: 'oauth',
          label: 'Browser sign-in',
        ),
        IntegrationMethodInfo(
          type: 'command',
          id: 'command',
          label: 'CLI sign-in',
        ),
      ],
    ),
  ];
  @override
  Future<List<McpServerInfo>> listMcpServers() async => [];
  @override
  Future<List<McpResourceInfo>> listMcpResources() async => [];
  @override
  Future<List<WorkspaceProject>> listProjects() async => [];
  @override
  Future<WorkspaceProject?> loadCurrentProject() async => null;
  @override
  Future<List<PendingQuestion>> listQuestions() async => [];
  @override
  Future<CatalogSnapshot> loadCatalog() async =>
      const CatalogSnapshot(providers: [], models: [], agents: []);
  @override
  Future<ChatDefaults> loadChatDefaults() async => const ChatDefaults();
  @override
  Future<IntegrationAuthLaunch> startIntegrationOAuth(
    String id,
    String methodID, {
    Map<String, String> inputs = const {},
    String? label,
  }) {
    starts++;
    return gate?.future ?? Future.value(launch);
  }

  @override
  Future<IntegrationAuthLaunch> startIntegrationCommand(
    String integrationID,
    String methodID, {
    String? label,
  }) {
    starts++;
    return gate?.future ?? Future.value(launch);
  }

  @override
  void restoreIntegrationAuthAttempt({
    required String integrationID,
    required String attemptID,
    required bool command,
    String? directory,
    String? workspace,
  }) {}
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

Future<ConnectionController> _controller(_Repository repository) async {
  SharedPreferences.setMockInitialValues({});
  final store = ProfileStore(prefs: await SharedPreferences.getInstance());
  final profile = ServerProfile(
    id: 'auth-test',
    name: 'Test',
    baseUrl: 'https://auth-test.example',
    username: 'opencode',
    password: 'synthetic-server-password',
  );
  await store.upsert(profile);
  final api = _Api();
  final controller = ConnectionController(
    store,
    apiFactory: (_) => api,
    repositoryFactory: (_) => repository,
    eventStreamFactory:
        ({required api, required onEvent, required onStatus, onError}) =>
            _Events(
              api: api,
              onEvent: onEvent,
              onStatus: onStatus,
              onError: onError,
            ),
  );
  addTearDown(controller.dispose);
  await controller.connect(profile, redetectOnFailure: false);
  return controller;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const storage = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(storage, (_) async => null);
    ProviderLogo.imageProviderOverride = (_) => null;
  });
  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(storage, null);
    ProviderLogo.imageProviderOverride = null;
  });

  for (final kind in PendingAuthKind.values) {
    test(
      'uncertain $kind requires explicit dismissal before another start',
      () async {
        final repository = _Repository()
          ..gate = Completer<IntegrationAuthLaunch>();
        final controller = await _controller(repository);
        Future<IntegrationAuthLaunch> start() => kind == PendingAuthKind.oauth
            ? controller.startRecoverableIntegrationOAuth(
                'cloud',
                'oauth',
                locationRevision: controller.locationRevision,
              )
            : controller.startIntegrationCommand(
                'cloud',
                'command',
                locationRevision: controller.locationRevision,
              );
        final first = start();
        final rejected = expectLater(first, throwsStateError);
        // Drain the local preparation chain until the synthetic server receives it.
        for (var i = 0; i < 20 && repository.starts == 0; i++) {
          await Future<void>.delayed(Duration.zero);
        }
        expect(repository.starts, 1);
        expect(controller.uncertainIntegrationAuth, isEmpty);
        expect(
          () => controller.forgetUncertainIntegrationAuth(
            'cloud',
            kind,
            locationRevision: controller.locationRevision,
          ),
          throwsStateError,
        );
        repository.gate!.completeError(
          const ProductException('Synthetic timeout'),
        );
        await rejected;
        expect(controller.pendingIntegrationAuth, isEmpty);
        expect(controller.uncertainIntegrationAuth.single.kind, kind);
        await expectLater(start(), throwsStateError);
        expect(repository.starts, 1);

        controller.forgetUncertainIntegrationAuth(
          'cloud',
          kind,
          locationRevision: controller.locationRevision,
        );
        expect(controller.uncertainIntegrationAuth, isEmpty);
        expect(repository.starts, 1, reason: 'Forgetting must never dispatch');
        repository.gate = null;
        await start();
        expect(repository.starts, 2);
        expect(controller.pendingIntegrationAuth.single.kind, kind);
        final metadata = controller.store.prefs.getString(
          'oc.pendingAuth.auth-test',
        )!;
        expect(metadata, isNot(contains('ABCD-EFGH')));
        expect(metadata, isNot(contains('/device')));
        expect(metadata, isNot(contains('synthetic-server-password')));
      },
    );
  }

  testWidgets(
    'unknown start can be explicitly forgotten without another request',
    (tester) async {
      final repository = _Repository()
        ..gate = Completer<IntegrationAuthLaunch>();
      final controller = await _controller(repository);
      final pending = controller.startRecoverableIntegrationOAuth(
        'cloud',
        'oauth',
        locationRevision: controller.locationRevision,
      );
      final failure = expectLater(pending, throwsStateError);
      for (var i = 0; i < 20 && repository.starts == 0; i++) {
        await tester.pump();
      }
      repository.gate!.completeError(
        const ProductException('Synthetic timeout'),
      );
      await failure;
      await tester.pumpWidget(
        MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: IntegrationsScreen(
            controller: controller,
            mode: IntegrationsMode.providers,
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Unconfirmed sign-in: cloud'), findsOneWidget);
      await tester.tap(find.text('Forget uncertain start'));
      await tester.pumpAndSettle();
      expect(
        find.textContaining('does not cancel sign-in on the server'),
        findsOneWidget,
      );
      await tester.tap(find.widgetWithText(TextButton, 'Cancel'));
      await tester.pumpAndSettle();
      expect(controller.uncertainIntegrationAuth, hasLength(1));
      await tester.tap(find.text('Forget uncertain start'));
      await tester.pumpAndSettle();
      await tester.tap(
        find.widgetWithText(FilledButton, 'Forget uncertain start'),
      );
      await tester.pumpAndSettle();
      expect(find.text('Unconfirmed sign-in: cloud'), findsNothing);
      expect(controller.uncertainIntegrationAuth, isEmpty);
      expect(repository.starts, 1);
      await tester.pumpWidget(const SizedBox.shrink());
      controller.dispose();
    },
  );

  testWidgets(
    'recoverable browser sign-in shows its device code before launch',
    (tester) async {
      final repository = _Repository();
      final controller = await _controller(repository);
      var browserLaunches = 0;
      await tester.pumpWidget(
        MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: IntegrationsScreen(
            controller: controller,
            mode: IntegrationsMode.providers,
            authorizationLauncher: (_) async {
              browserLaunches++;
              return true;
            },
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Connect'));
      await tester.pumpAndSettle();
      // Providers with several methods first show the method chooser.
      if (find.text('Browser sign-in').evaluate().isNotEmpty) {
        await tester.tap(find.text('Browser sign-in'));
        await tester.pumpAndSettle();
      }
      expect(find.text('Enter code: ABCD-EFGH'), findsOneWidget);
      expect(browserLaunches, 0);
      await tester.tap(find.text('Open browser'));
      await tester.pumpAndSettle();
      expect(find.text('Open external link?'), findsOneWidget);
      await tester.tap(find.text('Open link'));
      await tester.pumpAndSettle();
      expect(browserLaunches, 1);
      expect(controller.pendingIntegrationAuth, hasLength(1));
      expect(
        controller.store.prefs.getString('oc.pendingAuth.auth-test'),
        isNot(contains('ABCD-EFGH')),
      );
      await tester.pumpWidget(const SizedBox.shrink());
      controller.dispose();
    },
  );
}
