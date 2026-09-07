import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/domain/session_command_handoff.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/widgets/session_handoff.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _MetadataRepository implements ServerOperationsGateway {
  Session session = Session(
    id: 'session-1',
    projectID: 'project-1',
    directory: '/srv/project',
  );
  int reads = 0;
  @override
  Future<Session> getSessionDetails(String id) async {
    reads++;
    return session;
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _CommandRepository extends _MetadataRepository
    implements SessionCommandHandoffGateway {
  String serverURL = 'https://code.example.test';
  @override
  SessionCommandHandoff createSessionCommandHandoff({
    required String sessionID,
    required String? directory,
    required String? workspaceID,
    required String username,
  }) => SessionCommandHandoff.openCode1(
    serverURL: serverURL,
    sessionID: sessionID,
    directory: directory,
    workspaceID: workspaceID,
    username: username,
  );
}

class _Controller extends ConnectionController {
  _Controller(super.store, ServerOperationsGateway repo) {
    repository = repo;
  }
  final fixtureProfile = ServerProfile(
    id: 'profile-1',
    name: 'Synthetic server',
    baseUrl: 'https://code.example.test',
    username: 'synthetic-user',
    password: 'synthetic-password-never-copy',
  );
  @override
  ServerProfile get profile => fixtureProfile;
  @override
  Future<ServerOperationsGateway?> prepareActionRepository() async =>
      repository;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test(
    'POSIX resume command preserves shell-special values as literal arguments',
    () {
      const directory = r"/srv/a project/'quoted' $(printf injected) `id`; *";
      const username = r"-user'$(printf injected)";
      final handoff = SessionCommandHandoff.openCode1(
        serverURL: 'https://code.example.test/team/',
        sessionID: 'session-1',
        directory: directory,
        workspaceID: null,
        username: username,
      );
      final result = Process.runSync('sh', [
        '-c',
        'opencode() { printf \'%s\\n\' "\$@"; }\n${handoff.command}',
      ]);
      expect(result.exitCode, 0, reason: '${result.stderr}');
      expect((result.stdout as String).trimRight().split('\n'), [
        'attach',
        'https://code.example.test/team/',
        '--session=session-1',
        '--dir=$directory',
        '--username=$username',
      ]);
      expect(handoff.command, isNot(contains('--password')));
    },
    skip: Platform.isWindows,
  );

  test(
    'loopback, credential URLs, insecure and unverified workspace targets have no command',
    () {
      for (final url in [
        'http://127.0.0.1:4096',
        'https://localhost',
        'https://foo.localhost',
        'https://[::1]',
        'https://[0:0:0:0:0:0:0:1]',
        'https://[::ffff:127.0.0.1]',
        'https://2130706433',
        'https://0177.0.0.1',
        'https://0x7f.0.0.1',
        'https://0.0.0.0',
        'http://code.example.test',
        'https://user:secret@code.example.test',
        'https://code.example.test?password=secret',
        'https://code.example.test#secret',
      ]) {
        expect(
          SessionCommandHandoff.openCode1(
            serverURL: url,
            sessionID: 's1',
            directory: '/srv/project',
            workspaceID: null,
            username: '',
          ).command,
          isNull,
        );
      }
      expect(
        SessionCommandHandoff.openCode1(
          serverURL: 'https://code.example.test',
          sessionID: 's1',
          directory: '/srv/project',
          workspaceID: 'workspace-1',
          username: '',
        ).unavailable,
        SessionCommandUnavailable.unsupportedWorkspace,
      );
      expect(
        SessionCommandHandoff.openCode1(
          serverURL: 'https://code.example.test',
          sessionID: 's1',
          directory: null,
          workspaceID: null,
          username: '',
        ).unavailable,
        SessionCommandUnavailable.missingDirectory,
      );
    },
  );

  test(
    'v1 repository advertises the command facet without exposing transport password',
    () {
      final api = OpenCodeApi(
        baseUrl: 'https://code.example.test',
        username: 'test-user',
        password: 'never-copy-password',
      );
      addTearDown(api.close);
      final repository = SdkProductRepository(api.sdkClient);
      final command = repository
          .createSessionCommandHandoff(
            sessionID: 's1',
            directory: '/srv/project',
            workspaceID: null,
            username: 'test-user',
          )
          .command!;
      expect(command, contains('opencode attach'));
      expect(command, isNot(contains('never-copy-password')));
      repository.setLocation(workspace: 'workspace-1');
      expect(
        repository
            .createSessionCommandHandoff(
              sessionID: 's1',
              directory: '/srv/project',
              workspaceID: null,
              username: '',
            )
            .command,
        isNull,
      );
    },
  );

  Future<({_Controller controller, List<String> clipboard})> mount(
    WidgetTester tester,
    _MetadataRepository repository, {
    bool clipboardFails = false,
  }) async {
    SharedPreferences.setMockInitialValues({});
    final controller = _Controller(
      ProfileStore(prefs: await SharedPreferences.getInstance()),
      repository,
    );
    final clipboard = <String>[];
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    const secureStorage = MethodChannel(
      'plugins.it_nomads.com/flutter_secure_storage',
    );
    messenger.setMockMethodCallHandler(secureStorage, (_) async => null);
    messenger.setMockMethodCallHandler(SystemChannels.platform, (call) async {
      if (call.method == 'Clipboard.setData') {
        if (clipboardFails) {
          throw PlatformException(code: 'clipboard-unavailable');
        }
        clipboard.add((call.arguments as Map)['text'] as String);
      }
      return null;
    });
    addTearDown(() {
      controller.dispose();
      messenger.setMockMethodCallHandler(secureStorage, null);
      messenger.setMockMethodCallHandler(SystemChannels.platform, null);
    });
    await tester.pumpWidget(
      MaterialApp(
        theme: AppTheme.light(),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(
          body: Builder(
            builder: (context) => TextButton(
              onPressed: () => showSessionHandoff(
                context,
                controller: controller,
                sessionID: 'session-1',
                projectID: 'project-1',
              ),
              child: const Text('Continue'),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('Continue'));
    await tester.pumpAndSettle();
    return (controller: controller, clipboard: clipboard);
  }

  testWidgets('preview then copy revalidates session and excludes password', (
    tester,
  ) async {
    final repo = _CommandRepository();
    final fixture = await mount(tester, repo);
    expect(find.text('Copy command'), findsOneWidget);
    expect(fixture.clipboard, isEmpty);
    expect(repo.reads, 1);
    await tester.tap(find.text('Copy command'));
    await tester.pumpAndSettle();
    expect(repo.reads, 2);
    expect(fixture.clipboard.single, contains('--dir=\'/srv/project\''));
    expect(
      fixture.clipboard.single,
      isNot(contains(fixture.controller.profile.password)),
    );
  });

  testWidgets('changed directory during preview blocks clipboard handoff', (
    tester,
  ) async {
    final repo = _CommandRepository();
    final fixture = await mount(tester, repo);
    repo.session = Session(
      id: 'session-1',
      projectID: 'project-1',
      directory: '/another/project',
    );
    await tester.tap(find.text('Copy command'));
    await tester.pumpAndSettle();
    expect(repo.reads, 2);
    expect(fixture.clipboard, isEmpty);
  });

  testWidgets(
    'changed connection scope during preview blocks clipboard handoff',
    (tester) async {
      final repo = _CommandRepository();
      final fixture = await mount(tester, repo);
      fixture.controller.locationRevision++;
      await tester.tap(find.text('Copy command'));
      await tester.pumpAndSettle();
      expect(repo.reads, 1);
      expect(fixture.clipboard, isEmpty);
    },
  );

  testWidgets('connection generation changes block a stale handoff copy', (
    tester,
  ) async {
    final repo = _CommandRepository();
    final fixture = await mount(tester, repo);
    fixture.controller.connectionRevision++;
    await tester.tap(find.text('Copy command'));
    await tester.pumpAndSettle();
    expect(repo.reads, 1);
    expect(fixture.clipboard, isEmpty);
  });

  testWidgets('clipboard failure reports a retryable copy error', (
    tester,
  ) async {
    final repo = _CommandRepository();
    final fixture = await mount(tester, repo, clipboardFails: true);
    await tester.tap(find.text('Copy command'));
    await tester.pumpAndSettle();
    expect(fixture.clipboard, isEmpty);
    expect(find.text('Could not copy the handoff. Try again.'), findsOneWidget);
    expect(
      find.text(
        'Session unavailable or location changed. Return and try again.',
      ),
      findsNothing,
    );
  });

  for (final loopback in [false, true]) {
    testWidgets(
      '${loopback ? 'loopback v1' : 'unsupported protocol'} retains metadata fallback',
      (tester) async {
        final repo = loopback
            ? (_CommandRepository()..serverURL = 'http://127.0.0.1:4096')
            : _MetadataRepository();
        final fixture = await mount(tester, repo);
        expect(find.text('Copy command'), findsNothing);
        await tester.tap(find.text('Copy reference'));
        await tester.pumpAndSettle();
        expect(
          fixture.clipboard.single,
          contains('OpenCode session metadata reference'),
        );
        expect(fixture.clipboard.single, isNot(contains('opencode attach')));
        expect(fixture.clipboard.single, isNot(contains('/srv/project')));
      },
    );
  }
}
