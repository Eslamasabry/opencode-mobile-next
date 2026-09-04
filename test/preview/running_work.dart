// Native visual fixture only. Run with --target test/preview/running_work.dart.
// Normal builds keep lib/main.dart. Preferences here are in memory; no real
// server, credentials, commands, or stored user drafts are changed.
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/screens/running_work_sheet.dart';

import '../support/managed_shell_fakes.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  SharedPreferences.setMockInitialValues({
    'oc.profiles': jsonEncode([
      {
        'id': 'visual-fixture',
        'name': 'Visual fixture',
        'baseUrl': 'http://localhost',
        'username': '',
      },
    ]),
    'oc.activeProfile': 'visual-fixture',
  });
  final store = ProfileStore(prefs: await SharedPreferences.getInstance());
  await store.load();
  final repo = FakeManagedShellRepository()
    ..shells = [
      sampleShell(),
      sampleShell(id: 'sh_build', command: 'flutter build apk --release'),
    ]
    ..output =
        'Sample output · visual QA\n\n'
        '✓ Composer keeps focus\n✓ Draft text restored\n✓ Prompt reuse preserves typing\n'
        '✓ Navigation search\n✓ Shell output pagination\n✓ Timeout replacement\n'
        '✓ Reconnect refresh\n✓ Stop confirmation\n\nRunning accessibility checks…\n';
  final conn = ConnectionController(store)
    ..repository = repo
    ..status = StreamStatus.connected
    ..sessionsById = {
      'ses_a': Session(id: 'ses_a', title: 'Polish mobile workspace'),
      'ses_review': Session(
        id: 'ses_review',
        title: 'Review navigation and accessibility',
        parentID: 'ses_a',
      ),
      'ses_tests': Session(
        id: 'ses_tests',
        title: 'Check composer interactions',
        parentID: 'ses_a',
      ),
    }
    ..busySessions = {'ses_review', 'ses_tests'};
  runApp(
    MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: AppTheme.dark(),
      home: Builder(
        builder: (context) => Scaffold(
          appBar: AppBar(title: const Text('Running work preview')),
          body: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const Text('Sample data for native visual checks'),
                const SizedBox(height: 24),
                FilledButton(
                  onPressed: () => showRunningWorkSheet(
                    context,
                    controller: conn,
                    sessionID: 'ses_a',
                    shellIDs: {},
                  ),
                  child: const Text('Open running work'),
                ),
              ],
            ),
          ),
        ),
      ),
    ),
  );
}
