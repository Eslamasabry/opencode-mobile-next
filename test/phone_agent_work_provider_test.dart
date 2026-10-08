import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/builtin_server.dart';
import 'package:opencode_mobile/builtin/phone_server_healing.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/automation_policy.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';

const _alias = 'server-alias';
const _canonical = 'shared-home';
typedef _LeaseCall = ({
  String profileId,
  String leaseId,
  bool on,
  Duration hold,
});

class _Store extends ProfileStore {
  _Store({required super.prefs});
  final saved = <ServerProfile>[
    ServerProfile(
      id: _alias,
      name: 'This phone',
      baseUrl: BuiltinLinux.serverUrl,
      username: BuiltinLinux.serverUsername,
    ),
  ];
  final updates = ChangeNotifier();
  @override
  List<ServerProfile> get profiles => saved;
  @override
  String? get activeId => saved.firstOrNull?.id;
  @override
  Listenable get changes => updates;
}

class _Connection extends ConnectionController {
  _Connection(super.store, this.busy) : super(isIsolated: true);
  bool? busy;
  final queries = <String>[];
  @override
  bool isProfileReadable(String id) => store.profiles.any((p) => p.id == id);
  @override
  bool? localPhoneAgentWorkBusy(String profileId) {
    queries.add(profileId);
    return busy;
  }

  void observeBusy(bool? value) {
    busy = value;
    notifyListeners();
  }
}

class _Linux extends BuiltinLinux {
  final calls = <_LeaseCall>[];
  int statusReads = 0;
  @override
  Future<BuiltinWorkLeaseStatus> setPhoneAgentChatWorkLease({
    required String profileId,
    required String leaseId,
    required bool on,
    Duration hold = const Duration(minutes: 15),
  }) async {
    calls.add((profileId: profileId, leaseId: leaseId, on: on, hold: hold));
    return BuiltinWorkLeaseStatus(held: on && hold > Duration.zero);
  }

  @override
  Future<BuiltinLinuxStatus> status() async {
    statusReads++;
    return const BuiltinLinuxStatus.absent();
  }

  @override
  Future<void> cancelServerRecovery() async {}
}

void main() {
  final binding = TestWidgetsFlutterBinding.ensureInitialized();

  void providerTest(
    String name,
    bool? initialBusy,
    Future<void> Function(
      WidgetTester tester,
      ProviderContainer container,
      PhoneServerHealing healing,
      _Store store,
      _Connection connection,
      _Linux linux,
    )
    body,
  ) {
    testWidgets(name, (tester) async {
      debugPlatformCapabilities = const PlatformCapabilities.android();
      binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
      SharedPreferences.setMockInitialValues({
        'oc.phoneAgentOwner.$_alias': _canonical,
      });
      final prefs = await SharedPreferences.getInstance();
      AutomationPolicyController.resetShared();
      final store = _Store(prefs: prefs);
      final connection = _Connection(store, initialBusy);
      final linux = _Linux();
      final starter = BuiltinServerStarter(linux: linux);
      final container = ProviderContainer(
        overrides: [
          connProvider.overrideWithValue(connection),
          builtinLinuxProvider.overrideWithValue(linux),
          builtinServerStarterProvider.overrideWithValue(starter),
        ],
      );
      try {
        // Creation, queued lease work and disposal all belong to fakeAsync.
        final healing = container.read(phoneServerHealingProvider);
        await tester.pump();
        await body(tester, container, healing, store, connection, linux);
      } finally {
        container.dispose();
        await tester.pump();
        connection.dispose();
        starter.dispose();
        store.updates.dispose();
        AutomationPolicyController.resetShared();
        debugPlatformCapabilities = null;
      }
    });
  }

  providerTest(
    'real provider queries the readable alias and holds canonical agent work',
    true,
    (tester, container, healing, store, connection, linux) async {
      expect(connection.isProfileReadable(_alias), isTrue);
      expect(connection.isProfileReadable(_canonical), isFalse);
      expect(connection.queries, isNotEmpty);
      expect(connection.queries.every((id) => id == _alias), isTrue);
      expect(linux.calls, hasLength(1));
      final lease = linux.calls.single;
      expect(lease.profileId, _canonical);
      expect(lease.on, isTrue);
      expect(lease.hold, const Duration(minutes: 15));
      expect(RegExp(r'^[A-Za-z0-9_.-]{1,80}$').hasMatch(lease.leaseId), isTrue);
      expect(lease.leaseId.contains(_alias), isFalse);
      expect(lease.leaseId.contains(_canonical), isFalse);
      binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
      healing.setForeground(false);
      binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
      healing.setForeground(false);
      connection.observeBusy(true);
      await tester.pump(const Duration(minutes: 5));
      expect(linux.calls, hasLength(2));
      expect(
        linux.calls.every((c) => c.on && c.leaseId == lease.leaseId),
        isTrue,
      );
      expect(linux.statusReads, 0);
      connection.observeBusy(false);
      await tester.pump();
      expect(linux.calls.last.on, isFalse);
      expect(linux.calls.last.leaseId, lease.leaseId);
      expect(linux.calls.last.profileId, _canonical);
    },
  );

  providerTest(
    'unknown provider work acquires nothing and owner deletion releases a later run',
    null,
    (tester, container, healing, store, connection, linux) async {
      expect(connection.queries, isNotEmpty);
      expect(linux.calls, isEmpty);
      connection.observeBusy(true);
      await tester.pump();
      final lease = linux.calls.single;
      connection.observeBusy(null);
      await tester.pump();
      expect(linux.calls, hasLength(1));
      store.saved.clear();
      store.updates.notifyListeners();
      await tester.pump();
      expect(linux.calls.last.on, isFalse);
      expect(linux.calls.last.leaseId, lease.leaseId);
      expect(linux.calls.last.profileId, _canonical);
    },
  );

  providerTest(
    'disposing the real provider releases its active canonical hold',
    true,
    (tester, container, healing, store, connection, linux) async {
      final lease = linux.calls.single;
      container.invalidate(phoneServerHealingProvider);
      await tester.pump();
      expect(linux.calls.last.on, isFalse);
      expect(linux.calls.last.leaseId, lease.leaseId);
      expect(linux.calls.last.profileId, _canonical);
      connection.observeBusy(true);
      await tester.pump();
      expect(linux.calls.where((c) => c.on), hasLength(1));
    },
  );
}
