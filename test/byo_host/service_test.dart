import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/byo_host.dart';
import 'package:opencode_mobile/state/byo_host_service.dart';

import 'controller_test.dart' show FakeRunner, MemoryStore;

class SharedRunner extends FakeRunner {
  int disposals = 0;
  @override
  Future<void> dispose() async {
    disposals++;
  }
}

void main() {
  test(
    'gate is default off; missing artifact never reports available',
    () async {
      final service = ByoHostService(
        store: MemoryStore(),
        runner: SharedRunner(),
      );
      expect(await service.available(), isFalse);
      expect(service.newMachine, throwsA(isA<ByoHostFailure>()));
      final noPackage = ByoHostService(
        store: MemoryStore(),
        runner: SharedRunner(),
        enabled: true,
      );
      expect(await noPackage.available(), isFalse);
      await service.dispose();
      await noPackage.dispose();
    },
  );
  test(
    'machines retain independent controllers and releasing one keeps shared runner',
    () async {
      final runner = SharedRunner();
      final service = ByoHostService(
        store: MemoryStore(),
        runner: runner,
        enabled: true,
      );
      final first = service.newMachine(), second = service.newMachine();
      expect(first.profileId, isNot(second.profileId));
      expect(service.machine(second.profileId), same(second));
      await service.release(first.profileId);
      expect(runner.disposals, 0);
      expect(service.machine(second.profileId), same(second));
      await service.dispose();
      expect(runner.disposals, 1);
      expect(
        () => service.machine(second.profileId),
        throwsA(isA<ByoHostFailure>()),
      );
    },
  );
}
