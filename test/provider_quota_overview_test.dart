import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/provider_quota.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/draft_attachments.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/prompt_photos.dart';
import 'package:opencode_mobile/state/provider_quota_overview.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'provider_quota_test.dart' show providerQuotaFixture;

final _now = DateTime.utc(2026, 9, 6, 12);
const _password = 'fixture-only-overview-password';

class _Gateway implements ProviderQuotaGateway {
  final result = Completer<ProviderQuotaSnapshot>();
  int reads = 0;
  int closes = 0;
  bool failClose = false;

  @override
  Future<ProviderQuotaSnapshot> readSnapshot() {
    reads++;
    return result.future;
  }

  @override
  void close() {
    closes++;
    if (failClose) throw StateError('fixture-private-teardown-error');
    // Deliberately deliverable after close: cancellation cannot be the only
    // defense against stale completions from a retired transport.
  }
}

class _Repository implements ServerOperationsGateway {
  @override
  dynamic noSuchMethod(Invocation invocation) =>
      throw StateError('Quota must not call OpenCode operations');
}

/// Exercises the real deletion coordinator without reading or deleting files.
/// A gate can hold cleanup in flight while the profile still exists in storage.
class _MemoryVault extends DraftAttachmentVault {
  _MemoryVault({this.collectionGate, this.collectResult = true})
    : super(
        directory: () async => throw StateError('Unexpected filesystem access'),
      );

  final Completer<bool>? collectionGate;
  final bool collectResult;
  final collectionStarted = Completer<void>();
  final collectedOwners = <String?>[];

  @override
  Future<bool> collect(
    Map<String, Iterable<DraftAttachmentRef>> retained, {
    String? owner,
  }) async {
    collectedOwners.add(owner);
    if (!collectionStarted.isCompleted) collectionStarted.complete();
    return await collectionGate?.future ?? collectResult;
  }
}

class _Connection extends ConnectionController {
  _Connection(
    super.store, {
    super.draftAttachmentVault,
    super.promptPhotoStore,
  });

  int upstreamCalls = 0;
  bool suspended = false;

  @override
  bool get lifecycleSuspended => suspended;

  @override
  Future<void> resumeFromLifecycle() async {
    upstreamCalls++;
    throw StateError('Quota must not wake or probe OpenCode');
  }

  @override
  Future<ServerGateway?> prepareActionTransport() async {
    upstreamCalls++;
    throw StateError('Quota must not use OpenCode transports');
  }

  @override
  Future<ServerOperationsGateway?> prepareActionRepository() async {
    upstreamCalls++;
    throw StateError('Quota must not use OpenCode repositories');
  }

  void signal() => notifyListeners();
}

class _Harness {
  final _Connection connection;
  final ServerProfile profile;
  final ProviderQuotaOverview overview;
  final List<_Gateway> gateways;
  final List<ServerProfile> gatewayProfiles;

  _Harness(
    this.connection,
    this.profile,
    this.overview,
    this.gateways,
    this.gatewayProfiles,
  );

  ProviderQuotaSnapshot response({
    double percent = 25.5,
    String account = 'a',
  }) {
    final value = providerQuotaFixture(
      fetchedAtMs: _now.millisecondsSinceEpoch,
    );
    (value['account'] as Map)['ref'] = account * 64;
    ((value['windows'] as List).first as Map)['usedPercent'] = percent;
    return ProviderQuotaSnapshot.fromJson(value);
  }

  Future<void> firstRead() async {
    final pending = overview.allowAndRefresh();
    gateways.last.result.complete(response());
    await pending;
  }
}

void main() {
  final binding = TestWidgetsFlutterBinding.ensureInitialized();
  const secureChannel = MethodChannel(
    'plugins.it_nomads.com/flutter_secure_storage',
  );
  const backgroundChannel = MethodChannel('oc/background');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  late Map<String, String> secureValues;
  late int secureCalls;

  setUp(() {
    SharedPreferences.setMockInitialValues({});
    secureValues = {};
    secureCalls = 0;
    messenger.setMockMethodCallHandler(secureChannel, (call) async {
      secureCalls++;
      final args = call.arguments as Map;
      final key = args['key'] as String;
      switch (call.method) {
        case 'read':
          return secureValues[key];
        case 'write':
          secureValues[key] = args['value'] as String;
        case 'delete':
          secureValues.remove(key);
      }
      return null;
    });
    messenger.setMockMethodCallHandler(backgroundChannel, (_) async => null);
    binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
  });

  tearDown(() {
    messenger.setMockMethodCallHandler(secureChannel, null);
    messenger.setMockMethodCallHandler(backgroundChannel, null);
  });

  Future<_Harness> harness({
    void Function(ServerProfile)? configure,
    DateTime Function()? clock,
    ProviderQuotaGateway Function(ServerProfile)? factory,
    _MemoryVault? vault,
  }) async {
    final store = ProfileStore(prefs: await SharedPreferences.getInstance());
    final profile = ServerProfile(
      id: 'profile-a',
      name: 'Fixture',
      baseUrl: 'https://collector.example:8443',
      username: 'fixture-user',
      password: _password,
    );
    await store.upsert(profile);
    await store.setActiveId(profile.id);
    configure?.call(profile);
    final memoryVault = vault ?? _MemoryVault();
    if (vault != null) {
      await store.prefs.setBool('oc.draftAttachmentVault', true);
    }
    final photos = PromptPhotoStore(store.prefs, vault: memoryVault);
    final connection = _Connection(
      store,
      draftAttachmentVault: memoryVault,
      promptPhotoStore: photos,
    );
    final gateways = <_Gateway>[];
    final gatewayProfiles = <ServerProfile>[];
    final overview = ProviderQuotaOverview(
      connection,
      clock: clock ?? () => _now,
      gatewayFactory:
          factory ??
          (snapshot) {
            gatewayProfiles.add(snapshot);
            final gateway = _Gateway();
            gateways.add(gateway);
            return gateway;
          },
    );
    addTearDown(photos.dispose);
    addTearDown(connection.dispose);
    addTearDown(overview.dispose);
    return _Harness(connection, profile, overview, gateways, gatewayProfiles);
  }

  test(
    'provider switches discard data, cancel work and require fresh consent',
    () async {
      final h = await harness();
      await h.firstRead();
      final pending = h.overview.refresh();
      final old = h.gateways.last;
      h.overview.selectProvider(QuotaProvider.claude);
      expect(h.overview.provider, QuotaProvider.claude);
      expect(old.closes, 1);
      expect(h.overview.snapshot, isNull);
      expect(h.overview.consented, isFalse);
      final count = h.gateways.length;
      await h.overview.refresh();
      expect(h.gateways.length, count);
      old.result.complete(h.response());
      await pending;
      expect(h.overview.snapshot, isNull);
      await h.overview.allowAndRefresh();
      expect(h.overview.providerSupported, isFalse);
      expect(h.overview.consented, isFalse);
      expect(h.overview.snapshot, isNull);
      expect(h.gateways.length, count);
      expect(h.overview.failure!.kind, QuotaFailureKind.unsupported);
    },
  );

  test('a gateway response cannot impersonate the selected provider', () async {
    final h = await harness();
    final pending = h.overview.allowAndRefresh();
    h.gateways.last.result.complete(
      ProviderQuotaSnapshot.fromJson(
        providerQuotaFixture(provider: QuotaProvider.claude),
      ),
    );
    await pending;
    expect(h.overview.snapshot, isNull);
    expect(h.overview.failure!.kind, QuotaFailureKind.invalidResponse);
  });

  test(
    'construction/getters/refresh have zero calls until informed consent',
    () async {
      final h = await harness();
      expect(h.overview.consented, isFalse);
      expect(h.overview.detached, isFalse);
      expect(h.overview.setupNeeded, isFalse);
      expect(h.overview.canRead, isFalse);
      expect(h.overview.loading, isFalse);
      expect(h.overview.snapshot, isNull);
      expect(h.overview.failure, isNull);
      expect(h.overview.snapshotIsStale, isFalse);
      await h.overview.refresh();
      await h.overview.refresh();
      expect(h.gateways.length, 0);
      expect(h.connection.upstreamCalls, 0);
    },
  );

  test(
    'consented read acquires a copied profile and never wakes upstream',
    () async {
      final h = await harness();
      final pending = h.overview.allowAndRefresh();
      expect(h.overview.consented, isTrue);
      expect(h.overview.loading, isTrue);
      expect(h.gateways.length, 1);
      expect(h.gateways.single.reads, 1);
      expect(identical(h.gatewayProfiles.single, h.profile), isFalse);
      expect(h.gatewayProfiles.single.password == _password, isTrue);
      h.gateways.single.result.complete(h.response());
      await pending;
      expect(h.overview.loading, isFalse);
      expect(h.overview.snapshot?.windows.first.usedPercent, 25.5);
      expect(h.overview.snapshotIsStale, isFalse);
      expect(h.overview.failure, isNull);
      expect(h.gateways.single.closes, 1);
      expect(h.connection.upstreamCalls, 0);
      h.overview.dispose();
      expect(h.gateways.single.closes, 1);
    },
  );

  test(
    'quota consent, reads, and disable never write preferences or secure storage',
    () async {
      final h = await harness();
      final prefs = h.connection.store.prefs;
      final before = {for (final key in prefs.getKeys()) key: prefs.get(key)};
      final secureBefore = Map<String, String>.of(secureValues);
      final callsBefore = secureCalls;
      await h.firstRead();
      h.overview.disable();
      expect({for (final key in prefs.getKeys()) key: prefs.get(key)}, before);
      expect(
        secureValues.length == secureBefore.length &&
            secureBefore.keys.every(
              (key) => secureValues[key] == secureBefore[key],
            ),
        isTrue,
      );
      expect(secureCalls, callsBefore);
    },
  );

  test(
    'a new screen/store after restart never inherits consent or snapshots',
    () async {
      final h = await harness();
      await h.firstRead();
      h.overview.dispose();
      final restartedStore = ProfileStore(prefs: h.connection.store.prefs);
      await restartedStore.load();
      final restarted = _Connection(restartedStore);
      addTearDown(restarted.dispose);
      var calls = 0;
      final overview = ProviderQuotaOverview(
        restarted,
        gatewayFactory: (_) {
          calls++;
          return _Gateway();
        },
      );
      addTearDown(overview.dispose);
      await overview.refresh();
      expect(overview.consented, isFalse);
      expect(overview.snapshot, isNull);
      expect(calls, 0);
    },
  );

  test(
    'missing/unsafe credentials are typed setup failures without gateway creation',
    () async {
      for (final configure in <void Function(ServerProfile)>[
        (profile) => profile.password = '',
        (profile) => profile.requiresPasswordReentry = true,
        (profile) => profile.baseUrl = 'http://remote.example',
        (profile) => profile.baseUrl = 'https://collector.example?auth=fixture',
        (profile) => profile.username = 'ambiguous:user',
      ]) {
        final h = await harness(configure: configure);
        expect(h.overview.setupNeeded, isTrue);
        await h.overview.allowAndRefresh();
        expect(h.overview.failure?.kind, QuotaFailureKind.unavailable);
        expect(h.overview.canRead, isFalse);
        expect(h.gateways.length, 0);
        h.overview.dispose();
        h.connection.dispose();
      }
    },
  );

  test(
    'failed secure-storage restore never downgrades to an anonymous read',
    () async {
      final h = await harness();
      h.overview.dispose();
      messenger.setMockMethodCallHandler(secureChannel, (_) async {
        throw PlatformException(
          code: 'fixture-locked',
          message: 'fixture-private-error',
        );
      });
      final restored = ProfileStore(prefs: h.connection.store.prefs);
      await restored.load();
      final connection = _Connection(restored);
      addTearDown(connection.dispose);
      var calls = 0;
      final overview = ProviderQuotaOverview(
        connection,
        gatewayFactory: (_) {
          calls++;
          return _Gateway();
        },
      );
      addTearDown(overview.dispose);
      expect(restored.active?.requiresPasswordReentry, isTrue);
      await overview.allowAndRefresh();
      expect(overview.setupNeeded, isTrue);
      expect(overview.failure?.kind, QuotaFailureKind.unavailable);
      expect(calls, 0);
    },
  );

  test(
    'a page without an active saved profile cannot acquire consent later',
    () async {
      final store = ProfileStore(prefs: await SharedPreferences.getInstance());
      final connection = _Connection(store);
      addTearDown(connection.dispose);
      var calls = 0;
      final overview = ProviderQuotaOverview(
        connection,
        gatewayFactory: (_) {
          calls++;
          return _Gateway();
        },
      );
      addTearDown(overview.dispose);
      await overview.allowAndRefresh();
      expect(overview.detached, isTrue);
      expect(overview.setupNeeded, isTrue);
      expect(overview.consented, isFalse);
      expect(overview.canRead, isFalse);
      expect(calls, 0);
    },
  );

  test(
    'same-source failed refresh retains only the prior snapshot marked stale',
    () async {
      final h = await harness();
      await h.firstRead();
      final previous = h.overview.snapshot;
      final pending = h.overview.refresh();
      expect(h.overview.snapshot, same(previous));
      h.gateways.last.result.completeError(
        const ProviderQuotaFailure(QuotaFailureKind.collectorAuth),
      );
      await pending;
      expect(h.overview.snapshot, same(previous));
      expect(h.overview.snapshotIsStale, isTrue);
      expect(h.overview.failure?.kind, QuotaFailureKind.collectorAuth);
      final retry = h.overview.refresh();
      expect(h.overview.snapshotIsStale, isTrue);
      h.gateways.last.result.complete(h.response(percent: 40));
      await retry;
      expect(h.overview.snapshot?.windows.first.usedPercent, 40);
      expect(h.overview.snapshotIsStale, isFalse);
      expect(h.overview.failure, isNull);
    },
  );

  test(
    'overlapping explicit reads close old work; late success cannot overwrite',
    () async {
      final h = await harness();
      final first = h.overview.allowAndRefresh();
      final second = h.overview.refresh();
      expect(h.gateways.length, 2);
      expect(h.gateways.first.closes, 1);
      h.gateways.last.result.complete(h.response(percent: 80));
      await second;
      h.gateways.first.result.complete(h.response(percent: 10));
      await first;
      expect(h.overview.snapshot?.windows.first.usedPercent, 80);
      expect(h.overview.failure, isNull);
      expect(h.gateways.every((gateway) => gateway.closes == 1), isTrue);
    },
  );

  test(
    'late failure cannot poison a replacement read or reset its loading flag',
    () async {
      final h = await harness();
      final first = h.overview.allowAndRefresh();
      final second = h.overview.refresh();
      h.gateways.first.result.completeError(
        StateError('fixture-private-response'),
      );
      await first;
      expect(h.overview.loading, isTrue);
      expect(h.overview.failure, isNull);
      h.gateways.last.result.complete(h.response());
      await second;
      expect(h.overview.failure, isNull);
    },
  );

  test(
    'profile URL/principal/password changes detach even without notification',
    () async {
      for (final change in <void Function(ServerProfile)>[
        (profile) => profile.baseUrl = 'https://other.example:8443',
        (profile) => profile.baseUrl = 'https://collector.example:9443',
        (profile) => profile.username = 'other-user',
        (profile) => profile.password = 'fixture-rotated-password',
        (profile) => profile.requiresPasswordReentry = true,
      ]) {
        final h = await harness();
        await h.firstRead();
        final pending = h.overview.refresh();
        change(h.profile);
        h.gateways.last.result.complete(h.response(percent: 90));
        await pending;
        expect(h.overview.detached, isTrue);
        expect(h.overview.consented, isFalse);
        expect(h.overview.snapshot, isNull);
        expect(h.overview.failure, isNull);
        expect(h.gateways.last.closes, 1);
        await h.overview.allowAndRefresh();
        expect(h.gateways.length, 2);
        h.overview.dispose();
        h.connection.dispose();
      }
    },
  );

  test(
    'even same-URL profiles cannot share a screen consent or late response',
    () async {
      final h = await harness();
      final pending = h.overview.allowAndRefresh();
      await h.connection.store.upsert(
        ServerProfile(
          id: 'profile-b',
          name: 'Other',
          baseUrl: h.profile.baseUrl,
          username: h.profile.username,
          password: h.profile.password,
        ),
      );
      await h.connection.store.setActiveId('profile-b');
      h.connection.signal();
      expect(h.gateways.single.closes, 1);
      h.gateways.single.result.complete(h.response());
      await pending;
      expect(h.overview.detached, isTrue);
      expect(h.overview.snapshot, isNull);
      expect(h.overview.canRead, isFalse);
    },
  );

  test(
    'name-only changes do not revoke consent or relabel account data',
    () async {
      final h = await harness();
      await h.firstRead();
      h.profile.name = 'Renamed';
      h.connection.signal();
      expect(h.overview.detached, isFalse);
      expect(h.overview.canRead, isTrue);
      expect(h.overview.snapshot?.windows.first.usedPercent, 25.5);
    },
  );

  test(
    'location switch clears data and cancels before the old request finishes',
    () async {
      final h = await harness();
      await h.firstRead();
      final pending = h.overview.refresh();
      h.connection.locationRevision++;
      h.connection.signal();
      expect(h.overview.detached, isTrue);
      expect(h.overview.snapshot, isNull);
      expect(h.gateways.last.closes, 1);
      h.gateways.last.result.completeError(StateError('fixture-late-error'));
      await pending;
      expect(h.overview.failure, isNull);
      expect(h.overview.loading, isFalse);
    },
  );

  test(
    'transport generations interrupt reads but retained same-source UI reacquires',
    () async {
      final h = await harness();
      await h.firstRead();
      final previous = h.overview.snapshot;
      final pending = h.overview.refresh();
      h.connection.connectionRevision++;
      h.connection.signal();
      expect(h.overview.detached, isFalse);
      expect(h.overview.consented, isTrue);
      expect(h.overview.snapshot, same(previous));
      expect(h.overview.snapshotIsStale, isTrue);
      expect(h.gateways.last.closes, 1);
      h.gateways.last.result.complete(h.response(percent: 80));
      await pending;
      expect(h.overview.snapshot, same(previous));
      final refresh = h.overview.refresh();
      expect(h.gateways.length, 3);
      h.gateways.last.result.complete(h.response(percent: 50));
      await refresh;
      expect(h.overview.snapshot?.windows.first.usedPercent, 50);
      expect(h.connection.upstreamCalls, 0);
    },
  );

  test(
    'repository replacement without revision or notification rejects old completion',
    () async {
      final h = await harness();
      final pending = h.overview.allowAndRefresh();
      h.connection.repository = _Repository();
      h.gateways.single.result.complete(h.response());
      await pending;
      expect(h.overview.snapshot, isNull);
      expect(h.overview.failure?.kind, QuotaFailureKind.unavailable);
      expect(h.gateways.single.closes, 1);
      expect(h.overview.detached, isFalse);
    },
  );

  test(
    'pause cancels even when shared background transport is retained; resume never polls',
    () async {
      final h = await harness();
      final pending = h.overview.allowAndRefresh();
      h.overview.didChangeAppLifecycleState(AppLifecycleState.paused);
      expect(h.gateways.single.closes, 1);
      expect(h.overview.canRead, isFalse);
      await h.overview.refresh();
      expect(h.gateways.length, 1);
      h.overview.didChangeAppLifecycleState(AppLifecycleState.inactive);
      expect(h.overview.canRead, isFalse);
      h.overview.didChangeAppLifecycleState(AppLifecycleState.resumed);
      h.overview.didChangeAppLifecycleState(AppLifecycleState.resumed);
      expect(h.overview.canRead, isTrue);
      expect(h.gateways.length, 1);
      h.gateways.single.result.completeError(StateError('fixture-late-error'));
      await pending;
      expect(h.overview.snapshot, isNull);
      expect(h.connection.upstreamCalls, 0);
    },
  );

  test(
    'shared lifecycle suspension gates reads without invoking recovery',
    () async {
      final h = await harness();
      h.connection.suspended = true;
      h.connection.signal();
      await h.overview.allowAndRefresh();
      expect(h.overview.canRead, isFalse);
      expect(h.gateways.length, 0);
      h.connection.suspended = false;
      h.connection.connectionRevision++;
      h.connection.signal();
      expect(h.overview.canRead, isTrue);
      expect(h.gateways.length, 0);
      expect(h.connection.upstreamCalls, 0);
    },
  );

  test(
    'disable and dispose discard consent/data and ignore late outcomes',
    () async {
      for (final dispose in [false, true]) {
        final h = await harness();
        await h.firstRead();
        final pending = h.overview.refresh();
        var notices = 0;
        h.overview.addListener(() => notices++);
        if (dispose) {
          h.overview.dispose();
        } else {
          h.overview.disable();
        }
        final noticesAfterReset = notices;
        h.gateways.last.result.completeError(StateError('fixture-late-error'));
        await pending;
        await h.overview.refresh();
        expect(h.overview.snapshot, isNull);
        expect(h.overview.failure, isNull);
        expect(h.overview.consented, isFalse);
        expect(notices, noticesAfterReset);
        expect(h.gateways.last.closes, 1);
        h.overview.dispose();
        h.connection.dispose();
      }
    },
  );

  test(
    'disable permits explicit same-visit reconsent, not an old request resurrection',
    () async {
      final h = await harness();
      final old = h.overview.allowAndRefresh();
      h.overview.disable();
      await h.overview.refresh();
      expect(h.gateways.length, 1);
      final next = h.overview.allowAndRefresh();
      h.gateways.last.result.complete(h.response(percent: 60));
      await next;
      h.gateways.first.result.complete(h.response(percent: 10));
      await old;
      expect(h.overview.snapshot?.windows.first.usedPercent, 60);
    },
  );

  test(
    'store-only removal clears retained data even without a connection notification',
    () async {
      final h = await harness();
      await h.firstRead();
      final pending = h.overview.refresh();
      await h.connection.store.remove(h.profile.id);
      expect(h.overview.snapshot, isNull);
      expect(h.overview.detached, isTrue);
      expect(h.gateways.last.closes, 1);
      h.gateways.last.result.complete(h.response());
      await pending;
      expect(h.overview.failure, isNull);
      expect(h.overview.snapshot, isNull);
      final restarted = ProfileStore(prefs: h.connection.store.prefs);
      await restarted.load();
      expect(restarted.profiles, isEmpty);
      expect(secureValues.containsKey('pw.${h.profile.id}'), isFalse);
    },
  );

  test(
    'profile deletion start synchronously cancels consent and reads before any await',
    () async {
      final gate = Completer<bool>();
      final vault = _MemoryVault(collectionGate: gate);
      final h = await harness(vault: vault);
      await h.firstRead();
      final pending = h.overview.refresh();
      final oldGateway = h.gateways.last;
      var notices = 0;
      h.overview.addListener(() => notices++);
      final callsBeforeDelete = secureCalls;

      final deletion = h.connection.deleteProfileAndLocalData(h.profile.id);
      addTearDown(() async {
        if (!gate.isCompleted) gate.complete(true);
        await deletion;
        if (!oldGateway.result.isCompleted) {
          oldGateway.result.complete(h.response());
        }
        await pending;
      });

      // No await or overview getter before these assertions: getters alone can
      // detect a stale scope, but would not prove synchronous hook delivery.
      expect(notices, 1);
      expect(oldGateway.closes, 1);
      expect(vault.collectedOwners, isEmpty);
      expect(secureCalls, callsBeforeDelete);
      expect(h.connection.store.active, same(h.profile));
      expect(h.connection.isProfileReadable(h.profile.id), isFalse);
      expect(h.overview.consented, isFalse);
      expect(h.overview.detached, isTrue);
      expect(h.overview.snapshot, isNull);
      expect(h.overview.loading, isFalse);

      await vault.collectionStarted.future;
      expect(h.connection.store.active, same(h.profile));
      expect(gate.isCompleted, isFalse);
      await h.overview.allowAndRefresh();
      expect(h.gateways.length, 2);

      // A new page opened after the signal also consults the liveness predicate;
      // the not-yet-removed profile row cannot be used to bypass deletion.
      var newCalls = 0;
      final duringDeletion = ProviderQuotaOverview(
        h.connection,
        gatewayFactory: (_) {
          newCalls++;
          return _Gateway();
        },
      );
      addTearDown(duringDeletion.dispose);
      await duringDeletion.allowAndRefresh();
      expect(duringDeletion.detached, isTrue);
      expect(duringDeletion.consented, isFalse);
      expect(newCalls, 0);

      oldGateway.result.complete(h.response(percent: 90));
      await pending;
      expect(h.overview.snapshot, isNull);
      expect(h.overview.failure, isNull);
      expect(notices, 1);

      gate.complete(true);
      final result = await deletion;
      expect(result.complete, isTrue);
      expect(h.connection.store.profiles, isEmpty);
      expect(h.connection.isProfileReadable(h.profile.id), isFalse);
      expect(vault.collectedOwners, [h.profile.id, '']);
      expect(notices, 1);
    },
  );

  test(
    'a removed profile returning unchanged cannot republish an old response or consent',
    () async {
      final h = await harness();
      await h.firstRead();
      final pending = h.overview.refresh();
      final oldGateway = h.gateways.last;
      final result = await h.connection.deleteProfileAndLocalData(h.profile.id);
      expect(result.complete, isTrue);
      expect(oldGateway.closes, 1);
      expect(h.connection.store.profiles, isEmpty);

      // Deliberately reuse every scope value, including the profile id, without
      // a location/transport revision bump. The delete-start hook must retire
      // the old visit permanently, rather than just testing current equality.
      await h.connection.store.upsert(h.profile);
      await h.connection.store.setActiveId(h.profile.id);
      h.connection.signal();
      expect(h.connection.isProfileReadable(h.profile.id), isTrue);
      oldGateway.result.complete(h.response(percent: 95));
      await pending;
      await h.overview.allowAndRefresh();
      expect(h.overview.detached, isTrue);
      expect(h.overview.consented, isFalse);
      expect(h.overview.snapshot, isNull);
      expect(h.overview.failure, isNull);
      expect(h.gateways.length, 2);
      expect(oldGateway.closes, 1);
    },
  );

  test(
    'failed deletion retains the profile but cannot restore old consent or late errors',
    () async {
      final h = await harness(vault: _MemoryVault(collectResult: false));
      await h.firstRead();
      final pending = h.overview.refresh();
      final oldGateway = h.gateways.last;
      final result = await h.connection.deleteProfileAndLocalData(h.profile.id);
      expect(result.complete, isFalse);
      expect(result.removedProfile, isFalse);
      expect(h.connection.store.active, same(h.profile));
      expect(h.connection.isProfileReadable(h.profile.id), isTrue);
      oldGateway.result.completeError(
        StateError('fixture-late-deletion-error'),
      );
      await pending;
      await h.overview.allowAndRefresh();
      expect(h.overview.detached, isTrue);
      expect(h.overview.consented, isFalse);
      expect(h.overview.snapshot, isNull);
      expect(h.overview.failure, isNull);
      expect(h.gateways.length, 2);
      expect(oldGateway.closes, 1);
    },
  );

  for (final lateFailure in [false, true]) {
    testWidgets(
      'parent disposal synchronously cancels a retained read and ignores late ${lateFailure ? 'failure' : 'success'}',
      (tester) async {
        var now = _now;
        final h = await harness(clock: () => now);
        await h.firstRead();
        final pending = h.overview.refresh();
        final oldGateway = h.gateways.last;
        var notices = 0;
        int? revisionWhenNotified;
        h.overview.addListener(() {
          notices++;
          revisionWhenNotified = h.connection.connectionRevision;
        });
        final revision = h.connection.connectionRevision;
        h.connection.dispose();

        // As above, observe the listener and cancellation before reading any
        // lazy scope-checking getter. The hook runs before transport retirement.
        expect(notices, 1);
        expect(oldGateway.closes, 1);
        expect(revisionWhenNotified, revision);
        expect(h.connection.isProfileReadable(h.profile.id), isFalse);
        expect(h.connection.store.active, same(h.profile));
        expect(h.overview.detached, isTrue);
        expect(h.overview.consented, isFalse);
        expect(h.overview.snapshot, isNull);
        expect(h.overview.loading, isFalse);

        if (lateFailure) {
          oldGateway.result.completeError(
            StateError('fixture-late-parent-error'),
          );
        } else {
          oldGateway.result.complete(h.response(percent: 90));
        }
        await pending;
        await h.overview.allowAndRefresh();
        expect(h.overview.failure, isNull);
        expect(h.overview.snapshot, isNull);
        expect(h.gateways.length, 2);
        now = now.add(const Duration(minutes: 2));
        await tester.pump(const Duration(minutes: 2));
        expect(notices, 1);
        expect(oldGateway.closes, 1);
        // Listener removal must remain safe after the parent notifier disposed.
        h.overview.dispose();
        expect(tester.takeException(), isNull);
      },
    );
  }

  test(
    'deleting another profile leaves this visit and its snapshot intact',
    () async {
      final h = await harness();
      await h.firstRead();
      final snapshot = h.overview.snapshot;
      await h.connection.store.upsert(
        ServerProfile(
          id: 'profile-other',
          name: 'Other',
          baseUrl: 'https://other.example',
          username: 'fixture-user',
          password: 'fixture-other-password',
        ),
      );
      final deletion = await h.connection.deleteProfileAndLocalData(
        'profile-other',
      );
      expect(deletion.complete, isTrue);
      expect(h.overview.snapshot, same(snapshot));
      expect(h.overview.detached, isFalse);
      expect(h.overview.consented, isTrue);
      expect(h.overview.snapshotIsStale, isFalse);
    },
  );

  test(
    'new account snapshots atomically replace, never merge, old account windows',
    () async {
      final h = await harness();
      await h.firstRead();
      final previous = h.overview.snapshot;
      final pending = h.overview.refresh();
      expect(h.overview.snapshot, same(previous));
      final value = providerQuotaFixture(
        fetchedAtMs: _now.millisecondsSinceEpoch,
      );
      (value['account'] as Map)['ref'] = 'b' * 64;
      final windows = value['windows'] as List;
      windows.removeLast();
      (windows.single as Map)['usedPercent'] = 95;
      h.gateways.last.result.complete(ProviderQuotaSnapshot.fromJson(value));
      await pending;
      expect(h.overview.snapshot?.account.ref == 'b' * 64, isTrue);
      expect(h.overview.snapshot?.windows.length, 1);
      expect(h.overview.snapshot?.windows.single.usedPercent, 95);

      final mismatch = h.overview.refresh();
      value['account'] = {'status': 'mismatch', 'ref': 'c' * 64};
      value['windows'] = <Object>[];
      value['ordinaryUsageAllowed'] = null;
      value['status'] = 'unavailable';
      value['freshness'] = 'none';
      h.gateways.last.result.complete(ProviderQuotaSnapshot.fromJson(value));
      await mismatch;
      expect(h.overview.snapshot?.windows, isEmpty);
      expect(h.overview.snapshot?.ordinaryUsageAllowed, isNull);
      expect(h.overview.snapshot?.account.status, QuotaAccountStatus.mismatch);
    },
  );

  test(
    'reentrant consent revocation prevents even gateway construction',
    () async {
      final h = await harness();
      h.overview.addListener(() {
        if (h.overview.loading) h.overview.disable();
      });
      await h.overview.allowAndRefresh();
      expect(h.gateways.length, 0);
      expect(h.overview.consented, isFalse);
    },
  );

  test(
    'a factory scope change closes its gateway without dispatching',
    () async {
      late _Harness h;
      final gateway = _Gateway();
      h = await harness(
        factory: (_) {
          h.profile.password = 'fixture-rotated-password';
          return gateway;
        },
      );
      await h.overview.allowAndRefresh();
      expect(gateway.reads, 0);
      expect(gateway.closes, 1);
      expect(h.overview.detached, isTrue);
    },
  );

  test(
    'factory/read/close failures cannot leak raw text into overview state',
    () async {
      final h = await harness(
        factory: (_) => throw StateError('fixture-private-error $_password'),
      );
      await h.overview.allowAndRefresh();
      expect(
        h.overview.failure.toString(),
        'ProviderQuotaFailure(unavailable)',
      );
      expect(h.overview.toString(), 'ProviderQuotaOverview');
      final next = await harness();
      final pending = next.overview.allowAndRefresh();
      next.gateways.single.failClose = true;
      next.gateways.single.result.completeError(
        StateError('fixture-private-error $_password'),
      );
      await pending;
      expect(
        next.overview.failure.toString(),
        'ProviderQuotaFailure(unavailable)',
      );
      expect(next.overview.loading, isFalse);
    },
  );

  testWidgets(
    'expiry/reset timers only notify staleness, never replenish or poll',
    (tester) async {
      var now = _now;
      final h = await harness(clock: () => now);
      final pending = h.overview.allowAndRefresh();
      final value = providerQuotaFixture(
        fetchedAtMs: _now.millisecondsSinceEpoch,
      );
      ((value['windows'] as List).first as Map)['resetsAtMs'] = _now
          .add(const Duration(seconds: 10))
          .millisecondsSinceEpoch;
      h.gateways.single.result.complete(ProviderQuotaSnapshot.fromJson(value));
      await pending;
      var notices = 0;
      h.overview.addListener(() => notices++);
      now = now.add(const Duration(seconds: 10));
      await tester.pump(const Duration(seconds: 10));
      expect(h.overview.snapshotIsStale, isTrue);
      expect(h.overview.snapshot?.windows.first.usedPercent, 25.5);
      expect(h.overview.snapshot?.windows.first.remainingPercent, 74.5);
      expect(notices, 1);
      await tester.pump(const Duration(minutes: 5));
      expect(h.gateways.length, 1);
      expect(notices, 1);
      h.overview.dispose();
    },
  );

  testWidgets(
    'disable cancels expiry notification and binding lifecycle observer is removed',
    (tester) async {
      var now = _now;
      final h = await harness(clock: () => now);
      await h.firstRead();
      h.overview.disable();
      var notices = 0;
      h.overview.addListener(() => notices++);
      now = now.add(const Duration(minutes: 2));
      await tester.pump(const Duration(minutes: 2));
      expect(notices, 0);
      h.overview.dispose();
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
      await tester.pump();
      expect(notices, 0);
      expect(tester.takeException(), isNull);
    },
  );

  test(
    'none freshness and future fetched time are never presented as fresh',
    () async {
      for (final value in [
        providerQuotaFixture(fetchedAtMs: _now.millisecondsSinceEpoch)
          ..['freshness'] = 'none',
        providerQuotaFixture(
          fetchedAtMs: _now
              .add(const Duration(minutes: 1))
              .millisecondsSinceEpoch,
        ),
      ]) {
        final h = await harness();
        final pending = h.overview.allowAndRefresh();
        h.gateways.single.result.complete(
          ProviderQuotaSnapshot.fromJson(value),
        );
        await pending;
        expect(h.overview.snapshotIsStale, isTrue);
        h.overview.dispose();
        h.connection.dispose();
      }
    },
  );
}
