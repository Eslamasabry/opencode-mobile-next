import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';

class _HistoryRepository
    implements ServerOperationsGateway, HistoryMigrationGateway {
  _HistoryRepository(this.status);
  final HistoryMigrationStatus? status;
  @override
  Future<HistoryMigrationStatus?> historyMigrationStatus() async => status;
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _CapableGateway implements ServerGateway {
  @override
  void close() {}

  @override
  ServerCapabilities get capabilities =>
      const ServerCapabilities(phoneTeamExecution: true);
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _Store extends ProfileStore {
  _Store(SharedPreferences prefs) : super(prefs: prefs);
  ServerProfile? saved;
  @override
  Future<void> upsert(ServerProfile profile) async {
    saved = profile;
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  Map<String, dynamic> legacy({
    String? flavor,
    String? backend,
    String baseUrl = 'http://127.0.0.1:4096',
  }) => {
    'id': 'phone',
    'name': 'This phone',
    'baseUrl': baseUrl,
    'flavor': ?flavor,
    'backend': ?backend,
  };

  test('old managed metadata retains migration requirement through saves', () {
    for (final oldFlavor in [null, 'v1', 'unknown']) {
      final profile = ServerProfile.fromJson(legacy(flavor: oldFlavor));
      expect(profile.flavor, ServerFlavor.v2);
      expect(profile.requiresOpenCode2Migration, isTrue);
      final restored = ServerProfile.fromJson(profile.toJson());
      expect(restored.requiresOpenCode2Migration, isTrue);
      expect(restored.id, 'phone');
      expect(restored.baseUrl, 'http://127.0.0.1:4096');
    }
  });

  test('completed migration and supported profiles remain completed', () {
    final profile = ServerProfile.fromJson(legacy(flavor: 'v1'));
    profile.requiresOpenCode2Migration = false;
    expect(
      ServerProfile.fromJson(profile.toJson()).requiresOpenCode2Migration,
      isFalse,
    );
    expect(
      ServerProfile.fromJson(legacy(flavor: 'v2')).requiresOpenCode2Migration,
      isFalse,
    );
  });

  test('agent profiles never acquire an OpenCode migration requirement', () {
    for (final backend in ['codex', 'paseo']) {
      final profile = ServerProfile.fromJson(legacy(backend: backend));
      expect(profile.requiresOpenCode2Migration, isFalse);
      expect(profile.usesAgentSocket, isTrue);
    }
  });

  test('fresh profiles default to the supported protocol', () {
    final profile = ServerProfile(
      id: 'new',
      name: 'Server',
      baseUrl: 'https://example.com',
    );
    expect(profile.flavor, ServerFlavor.v2);
    expect(profile.requiresOpenCode2Migration, isFalse);
  });
  test('copied history remains pending after process restart', () {
    final profile = ServerProfile.fromJson(legacy(flavor: 'v1'))
      ..requiresOpenCode2Migration = false
      ..openCode2HistoryPending = true;
    final restored = ServerProfile.fromJson(profile.toJson());
    expect(restored.id, profile.id);
    expect(restored.requiresOpenCode2Migration, isFalse);
    expect(restored.openCode2HistoryPending, isTrue);
  });

  for (final phase in [
    null,
    HistoryMigrationPhase.error,
    HistoryMigrationPhase.running,
    HistoryMigrationPhase.completed,
  ]) {
    test(
      'history confirmation $phase only unlocks completed history',
      () async {
        SharedPreferences.setMockInitialValues({});
        final store = _Store(await SharedPreferences.getInstance());
        final controller = ConnectionController(store, isIsolated: true);
        addTearDown(controller.dispose);
        final profile = ServerProfile.fromJson(legacy(flavor: 'v2'))
          ..openCode2HistoryPending = true;
        controller.adoptConnectedProfileForTesting(profile);
        controller.repository = _HistoryRepository(
          phase == null ? null : HistoryMigrationStatus(phase),
        );
        final completed = await controller.confirmManagedOpenCode2Migration(
          timeout: const Duration(milliseconds: 10),
          pollInterval: const Duration(milliseconds: 1),
        );
        expect(completed, phase == HistoryMigrationPhase.completed);
        expect(profile.openCode2HistoryPending, !completed);
        expect(store.saved != null, completed);
      },
    );
  }
  for (final pendingImport in [false, true]) {
    for (final connected in [false, true]) {
      test(
        'unfinished managed upgrade gates setup pending=$pendingImport live=$connected',
        () async {
          SharedPreferences.setMockInitialValues({});
          final store = _Store(await SharedPreferences.getInstance());
          final controller = ConnectionController(store, isIsolated: true);
          addTearDown(controller.dispose);
          // The in-app server: the one the app upgrades on the phone.
          final profile =
              ServerProfile.fromJson(
                  legacy(flavor: 'v2', baseUrl: 'http://127.0.0.1:4097'),
                )
                ..requiresOpenCode2Migration = !pendingImport
                ..openCode2HistoryPending = pendingImport;
          if (connected) {
            controller.adoptConnectedProfileForTesting(profile);
            controller.api = _CapableGateway();
          }
          expect(
            controller.serverCapabilitiesFor(profile).phoneTeamExecution,
            isFalse,
          );
          profile
            ..requiresOpenCode2Migration = false
            ..openCode2HistoryPending = false;
          expect(
            controller.serverCapabilitiesFor(profile).phoneTeamExecution,
            isTrue,
          );
        },
      );
    }
  }
}
