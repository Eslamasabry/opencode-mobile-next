// Opening a project folder: a failure to read the waiting questions (an older
// OpenCode answers that read with an error) never blocks it, and a failure
// that does block it reaches people as plain words, the server's own text
// kept for Details.
import 'dart:ui' show Locale;

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart'
    show PendingQuestion;
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';

import 'support/connection_sse_fixtures.dart';

class _QuestionsFail extends TestRepository {
  _QuestionsFail(super.api);

  @override
  Future<List<PendingQuestion>> listQuestions() async =>
      throw StateError('Could not load pending questions');
}

Future<(ConnectionController, List<ControlledApi>)> _connected(
  WidgetTester tester, {
  bool questionsFail = false,
  bool sessionsFail = false,
}) async {
  final apis = <ControlledApi>[];
  final controller = ConnectionController(
    await memoryProfileStore(),
    apiFactory: (profile) {
      final api = ControlledApi('${profile.id}-${apis.length}');
      if (sessionsFail && apis.isNotEmpty) {
        api.sessionsFailure = ApiException('raw server text 9f3a');
      }
      apis.add(api);
      return api;
    },
    repositoryFactory: questionsFail
        ? (api) => _QuestionsFail(api)
        : testRepositoryFactory,
    eventStreamFactory: streamFactory([]),
  );
  final connect = controller.connect(testProfile('server'));
  await tester.pump();
  apis.single.healthResult.complete(Health(healthy: true, version: '1'));
  await connect;
  await tester.pump();
  return (controller, apis);
}

Future<void> _finish(WidgetTester tester, ConnectionController c) async {
  c.dispose();
  await tester.pump(const Duration(seconds: 10));
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('waiting questions that cannot be read do not block a folder', (
    tester,
  ) async {
    final (controller, _) = await _connected(tester, questionsFail: true);
    await controller.selectLocation(directory: '/work/new');
    await tester.pump();
    expect(controller.locationError, isNull);
    expect(controller.directory, '/work/new');
    await _finish(tester, controller);
  });

  testWidgets(
    'a failure that does block it is plain words with the cause kept',
    (tester) async {
      final (controller, _) = await _connected(tester, sessionsFail: true);
      await controller.selectLocation(directory: '/work/new');
      await tester.pump();
      expect(controller.locationError, isNotNull);
      expect(controller.locationError, isNot(contains('raw server text')));
      expect(controller.locationFailure, isNotNull);
      await _finish(tester, controller);
    },
  );

  test('worktree copy does not name OpenCode on every server', () {
    final copy = lookupAppLocalizations(const Locale('en'));
    for (final text in [
      copy.worktreesCreateHelper,
      copy.e7LibraryOpenCodeCouldNotPrepareThisWorktree,
      copy.e7LibraryWaitForOpenCodeToFinishPreparingThis,
      copy.e7LibraryOpenCodeIsReconnectingTryAgain,
      copy.e7LibraryOpenCodeDidNotSwitchLocations,
    ]) {
      expect(text, isNot(contains('OpenCode')));
    }
  });
}
