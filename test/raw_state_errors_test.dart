// A failure's own text never becomes the words people read: the connection's
// problem, the catalog's and the session list's carry the failure itself (for
// Details) and say plain words.
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/state/connection.dart';

import 'support/connection_sse_fixtures.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('a failed connect says plain words and keeps the cause', (
    tester,
  ) async {
    final controller = ConnectionController(
      await memoryProfileStore(),
      apiFactory: (profile) => ControlledApi('x'),
      repositoryFactory: testRepositoryFactory,
      eventStreamFactory: streamFactory([]),
    );
    final api = ControlledApi('x');
    final failing = ConnectionController(
      await memoryProfileStore(),
      apiFactory: (profile) => api,
      repositoryFactory: testRepositoryFactory,
      eventStreamFactory: streamFactory([]),
    );
    final connect = failing.connect(testProfile('server'));
    await tester.pump();
    api.healthResult.completeError(StateError('raw socket text 77'));
    await connect;
    await tester.pump();
    expect(failing.lastFailure.toString(), contains('raw socket text 77'));
    expect(failing.connectionError, isNotNull);
    expect(failing.connectionError, isNot(contains('raw socket text')));
    failing.dispose();
    controller.dispose();
    await tester.pump(const Duration(seconds: 10));
  });

  testWidgets('a failed session list keeps the failure', (tester) async {
    final api = ControlledApi('x')..sessionsFailure = StateError('raw list 31');
    final controller = ConnectionController(
      await memoryProfileStore(),
      apiFactory: (profile) => api,
      repositoryFactory: testRepositoryFactory,
      eventStreamFactory: streamFactory([]),
    );
    final connect = controller.connect(testProfile('server'));
    await tester.pump();
    api.healthResult.complete(Health(healthy: true, version: '1'));
    await connect;
    await tester.pump(const Duration(seconds: 1));
    expect(controller.sessionsFailure, isNotNull);
    controller.dispose();
    await tester.pump(const Duration(seconds: 10));
  });
}
