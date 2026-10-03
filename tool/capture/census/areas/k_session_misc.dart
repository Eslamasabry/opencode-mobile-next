// Census scenes for the ledger part `k-session-misc`
// (docs/design/ui-ledger/parts/k-session-misc.json). See tool/capture/census_test.dart.
import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart' show CancelToken, ProgressCallback;
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/domain/session_command_handoff.dart';
import 'package:opencode_mobile/domain/session_handoff.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/ui/screens/active_context_screen.dart';
import 'package:opencode_mobile/ui/screens/session_context_screen.dart';
import 'package:opencode_mobile/ui/screens/session_export_screen.dart';
import 'package:opencode_mobile/ui/screens/session_import_screen.dart';
import 'package:opencode_mobile/ui/screens/session_note_screen.dart';
import 'package:opencode_mobile/ui/screens/session_relations_screen.dart';
import 'package:opencode_mobile/ui/widgets/session_handoff.dart';
import 'package:opencode_mobile/ui/widgets/session_handoff_sheets.dart';

import '../../fixtures.dart';
import '../census_core.dart';

// ---------------------------------------------------------------------------
// Fakes
// ---------------------------------------------------------------------------

/// A transport whose `messagePage` (used by session context and run results)
/// simply wraps a fixed message list, the way the real server paginates
/// history.
class _HistoryApi extends CaptureApi {
  _HistoryApi({List<MessageWithParts>? messages}) : _messages = messages;
  final List<MessageWithParts>? _messages;

  @override
  Future<ServerPage<MessageWithParts>> messagePage(
    String id, {
    String? cursor,
    int limit = 100,
  }) async => ServerPage(
    items: cursor == null ? _messages ?? await messages(id) : const [],
  );
}

class _ActiveContextRepository extends CaptureRepository
    implements ActiveContextGateway {
  _ActiveContextRepository({this.rows = const []});
  final List<ActiveContextMessage> rows;
  @override
  bool get activeContextSupported => true;
  @override
  Future<List<ActiveContextMessage>> loadActiveContext(
    String sessionID,
  ) async => rows;
}

const _activeContextRows = [
  ActiveContextMessage(
    id: 'msg_system',
    type: 'system',
    content: [
      ContextContent(
        ContextContentKind.text,
        'Follow the project instructions.',
      ),
    ],
  ),
  ActiveContextMessage(
    id: 'msg_user',
    type: 'user',
    content: [ContextContent(ContextContentKind.text, userPrompt)],
  ),
  ActiveContextMessage(
    id: 'msg_assistant',
    type: 'assistant',
    content: [
      ContextContent(
        ContextContentKind.toolOutput,
        'The checkout tests now pass 20 runs in a row.',
        name: 'bash',
      ),
    ],
  ),
];

class _RelationsRepository extends CaptureRepository {
  _RelationsRepository({this.children = const []});
  final List<Session> children;
  @override
  Future<Session> getSessionDetails(String id) async =>
      sampleSessions()[checkoutSessionID]!.copyWith(
        title: 'Fix flaky checkout test',
      );
  @override
  Future<List<Session>> listSessionChildren(String id) async => children;
}

final _subagentNow = DateTime.now().millisecondsSinceEpoch;
final _subagentOne = Session(
  id: 'ses_explore',
  title: 'Audit the checkout module (@explore subagent)',
  directory: projectDirectory,
  parentID: checkoutSessionID,
  time: SessionTime(
    created: _subagentNow - 20 * 60 * 1000,
    updated: _subagentNow - 18 * 60 * 1000,
  ),
);
final _subagentTwo = Session(
  id: 'ses_write',
  title: 'Draft the fix (@general subagent)',
  directory: projectDirectory,
  parentID: checkoutSessionID,
  time: SessionTime(
    created: _subagentNow - 15 * 60 * 1000,
    updated: _subagentNow - 3 * 60 * 1000,
  ),
);

class _ExportRepository extends CaptureRepository
    implements SessionExportGateway {
  @override
  bool get sessionExportSupported => true;
  @override
  Future<Uint8List> exportSession(
    String sessionID, {
    bool sanitize = true,
    CancelToken? cancelToken,
    ProgressCallback? onReceiveProgress,
  }) async => Uint8List.fromList(utf8.encode('{"data":{"messages":[]}}'));
}

class _ImportRepository extends CaptureRepository
    implements SessionImportGateway {
  @override
  bool get sessionImportSupported => true;
  @override
  Future<Session> importSession(
    SessionImportDocument document,
    SessionImportDestination destination,
  ) async => Session(
    id: document.id,
    title: document.title,
    directory: destination.directory,
    workspaceID: destination.workspaceID,
  );
}

final _importBytes = Uint8List.fromList(
  utf8.encode(
    jsonEncode({
      'data': {
        'info': {
          'id': 'ses_transfer',
          'projectID': 'project_shopfront',
          'title': 'Add dark mode to settings',
          'location': {'directory': '/home/dev/other-laptop/shopfront'},
          'time': {'created': 1, 'updated': 2},
          'cost': 1.18,
          'tokens': {
            'input': 0,
            'output': 0,
            'reasoning': 0,
            'cache': {'read': 0, 'write': 0},
          },
        },
        'messages': [
          {
            'id': 'msg_user',
            'type': 'user',
            'time': {'created': 1},
            'text': 'Add a manual dark mode toggle to Settings.',
          },
        ],
      },
    }),
  ),
);

class _NoteRepository extends CaptureRepository implements SessionNoteGateway {
  _NoteRepository({this.value});
  String? value;
  @override
  bool get sessionNotesSupported => true;
  @override
  Future<String?> loadSessionNote(String sessionID) async => value;
  @override
  Future<void> saveSessionNote(String sessionID, String note) async =>
      value = note;
  @override
  Future<void> removeSessionNote(String sessionID) async => value = null;
}

class _HandoffRepository extends CaptureRepository
    implements SessionCommandHandoffGateway {
  _HandoffRepository({this.workspaceID});
  final String? workspaceID;
  @override
  Future<Session> getSessionDetails(String id) async =>
      sampleSessions()[checkoutSessionID]!.copyWith(
        projectID: 'project_shopfront',
        workspaceID: workspaceID,
      );
  @override
  SessionCommandHandoff createSessionCommandHandoff({
    required String sessionID,
    required String? directory,
    required String? workspaceID,
    required String username,
  }) => SessionCommandHandoff.openCode1(
    serverURL: 'https://code.example.test',
    sessionID: sessionID,
    directory: directory,
    workspaceID: workspaceID,
    username: username,
  );
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

Widget _backdrop(String title) => Scaffold(
  appBar: AppBar(title: Text(title)),
  body: const SizedBox.shrink(),
);

/// Pumps a plausible parent screen, then pushes [build] the way every one of
/// these screens is actually reached (chat, a menu sheet, settings...), so
/// the AppBar's back affordance and PopScope behavior are real.
Future<ConnectionController> _pushed(
  CensusKit kit,
  Widget Function(ConnectionController controller) build, {
  CaptureApi? api,
  CaptureRepository? repository,
  String backdropTitle = 'Fix flaky checkout test',
}) async {
  final controller = await kit.connected(api: api, repository: repository);
  await kit.pumpApp(_backdrop(backdropTitle), controller: controller);
  await kit.push(build(controller));
  return controller;
}

// ---------------------------------------------------------------------------
// Shots
// ---------------------------------------------------------------------------

final kSessionMiscArea = CensusArea(
  'k-session-misc',
  shots: [
    // -- session-context ---------------------------------------------------------
    CensusShot('session-context', (kit) async {
      final controller = await kit.connected(
        api: _HistoryApi(messages: sampleTranscript()),
        repository: _ActiveContextRepository(rows: _activeContextRows),
      );
      controller.catalog = sampleCatalog();
      await kit.pumpApp(
        _backdrop('Fix flaky checkout test'),
        controller: controller,
      );
      await kit.push(
        SessionContextScreen(
          controller: controller,
          sessionID: checkoutSessionID,
        ),
      );
      kit.expectText('Conversation context');
      kit.expectVisible(find.byKey(const ValueKey('open-active-context')));
    }),

    // -- active-context -------------------------------------------------------------
    CensusShot('active-context', state: 'loaded', (kit) async {
      await _pushed(
        kit,
        (controller) => ActiveContextScreen(
          controller: controller,
          sessionID: checkoutSessionID,
        ),
        repository: _ActiveContextRepository(rows: _activeContextRows),
      );
      kit.expectText('Active context');
      kit.expectTextContaining('Follow the project instructions');
    }),
    CensusShot('active-context', state: 'filtered', (kit) async {
      await _pushed(
        kit,
        (controller) => ActiveContextScreen(
          controller: controller,
          sessionID: checkoutSessionID,
        ),
        repository: _ActiveContextRepository(rows: _activeContextRows),
      );
      await kit.tapText('Assistant · 1');
      kit.expectTextContaining('checkout tests now pass');
    }),

    // -- active-context-message ---------------------------------------------------
    CensusShot('active-context-message', (kit) async {
      await _pushed(
        kit,
        (controller) => ActiveContextScreen(
          controller: controller,
          sessionID: checkoutSessionID,
        ),
        repository: _ActiveContextRepository(rows: _activeContextRows),
      );
      await kit.tapKey('active-context-msg_assistant');
      kit.expectTextContaining('checkout tests now pass');
    }),

    // -- session-relations -----------------------------------------------------------
    CensusShot('session-relations', state: 'loaded', (kit) async {
      await _pushed(
        kit,
        (controller) => SessionRelationsScreen(
          controller: controller,
          sessionID: checkoutSessionID,
        ),
        repository: _RelationsRepository(
          children: [_subagentOne, _subagentTwo],
        ),
      );
      kit.expectText('Subagent conversations');
      kit.expectText('Audit the checkout module (@explore subagent)');
    }),
    CensusShot('session-relations', state: 'empty', (kit) async {
      await _pushed(
        kit,
        (controller) => SessionRelationsScreen(
          controller: controller,
          sessionID: checkoutSessionID,
        ),
        repository: _RelationsRepository(),
      );
      kit.expectText('Subagent conversations');
    }),

    // -- continue-on-computer-sheet from a conversation list (the handoff
    // dialog merged into it, slice-P3.11a) --------------------------------------
    CensusShot('continue-on-computer-sheet', state: 'from-list', (kit) async {
      final controller = await kit.connected(repository: _HandoffRepository());
      await kit.pumpApp(
        _backdrop('Fix flaky checkout test'),
        controller: controller,
      );
      await kit.present(
        (context) => showSessionHandoff(
          context,
          controller: controller,
          sessionID: checkoutSessionID,
          projectID: 'project_shopfront',
        ),
      );
      kit.expectText('Continue on computer');
    }),
    CensusShot('continue-on-computer-sheet', state: 'from-list-unavailable', (
      kit,
    ) async {
      final controller = await kit.connected(
        repository: _HandoffRepository(workspaceID: 'wrk_managed'),
      );
      await kit.pumpApp(
        _backdrop('Fix flaky checkout test'),
        controller: controller,
      );
      await kit.present(
        (context) => showSessionHandoff(
          context,
          controller: controller,
          sessionID: checkoutSessionID,
          projectID: 'project_shopfront',
        ),
      );
      kit.expectText('Continue on computer');
    }),

    // -- session-export ---------------------------------------------------------------
    CensusShot('session-export', (kit) async {
      await _pushed(
        kit,
        (controller) => SessionExportScreen(
          controller: controller,
          sessionID: checkoutSessionID,
          markdown: () =>
              Uint8List.fromList(utf8.encode('# Fix flaky checkout test')),
          saveFile: (name, bytes, mime) async => Uri.file('/tmp/$name'),
        ),
        repository: _ExportRepository(),
      );
      kit.expectText('Export conversation');
      kit.expectText('Complete conversation · JSON');
    }),

    // -- session-import -----------------------------------------------------------
    CensusShot('session-import', state: 'empty', (kit) async {
      await _pushed(
        kit,
        (controller) => SessionImportScreen(
          controller: controller,
          pickFile: () async => null,
        ),
        repository: _ImportRepository(),
        backdropTitle: 'Settings',
      );
      kit.expectText('Import conversation');
      kit.expectText('Choose JSON file');
    }),
    CensusShot('session-import', state: 'review', (kit) async {
      await _pushed(
        kit,
        (controller) => SessionImportScreen(
          controller: controller,
          pickFile: () async => SessionImportFile(
            name: 'shopfront-conversation.json',
            length: () async => _importBytes.length,
            read: () => Stream.value(_importBytes),
          ),
        ),
        repository: _ImportRepository(),
        backdropTitle: 'Settings',
      );
      await kit.tapText('Choose JSON file');
      kit.expectText('Add dark mode to settings');
      kit.expectVisible(find.byKey(const ValueKey('import-review-scroll')));
    }),

    // -- session-import-destination-sheet ---------------------------------------------
    CensusShot('session-import-destination-sheet', (kit) async {
      await _pushed(
        kit,
        (controller) => SessionImportScreen(
          controller: controller,
          pickFile: () async => SessionImportFile(
            name: 'shopfront-conversation.json',
            length: () async => _importBytes.length,
            read: () => Stream.value(_importBytes),
          ),
        ),
        repository: _ImportRepository(),
        backdropTitle: 'Settings',
      );
      await kit.tapText('Choose JSON file');
      await kit.tapKey('import-destination');
      kit.expectVisible(find.byKey(const ValueKey('import-destination-sheet')));
      kit.expectText('shopfront');
    }),

    // -- session-note --------------------------------------------------------------
    CensusShot('session-note', state: 'saved', (kit) async {
      await _pushed(
        kit,
        (controller) => SessionNoteScreen(
          controller: controller,
          sessionID: checkoutSessionID,
        ),
        repository: _NoteRepository(
          value: 'Keep the fix minimal and covered by a test.',
        ),
      );
      kit.expectText('Note for the agent');
      kit.expectVisible(find.byKey(const ValueKey('remove-session-note')));
    }),
    CensusShot('session-note', state: 'empty', (kit) async {
      await _pushed(
        kit,
        (controller) => SessionNoteScreen(
          controller: controller,
          sessionID: checkoutSessionID,
        ),
        repository: _NoteRepository(),
      );
      kit.expectText('Note for the agent');
    }),

    // -- session-note-discard-dialog ----------------------------------------------
    CensusShot('session-note-discard-dialog', (kit) async {
      await _pushed(
        kit,
        (controller) => SessionNoteScreen(
          controller: controller,
          sessionID: checkoutSessionID,
        ),
        repository: _NoteRepository(value: 'Keep the fix minimal.'),
      );
      await kit.enterText(
        find.byKey(const ValueKey('session-note-editor')),
        'Keep the fix minimal and add a regression test.',
      );
      await kit.navigator.maybePop();
      await kit.settle();
      kit.expectText('Discard your note changes?');
    }),

    // -- continue-on-computer-sheet -------------------------------------------------
    CensusShot('continue-on-computer-sheet', state: 'available', (kit) async {
      final controller = await kit.connected();
      await kit.pumpApp(
        _backdrop('Fix flaky checkout test'),
        controller: controller,
      );
      await kit.present(
        (context) => showModalBottomSheet<Object?>(
          context: context,
          isScrollControlled: true,
          builder: (_) => ContinueOnComputerSheet(
            command: SessionResumeCommand.build(
              cli: SessionResumeCli.openCode1,
              sessionID: checkoutSessionID,
              directory: projectDirectory,
            ),
          ),
        ),
      );
      kit.expectVisible(
        find.byKey(const ValueKey('continue-on-computer-sheet')),
      );
      kit.expectText('Continue on computer');
    }),
    CensusShot('continue-on-computer-sheet', state: 'unavailable', (kit) async {
      final controller = await kit.connected();
      await kit.pumpApp(
        _backdrop('Fix flaky checkout test'),
        controller: controller,
      );
      await kit.present(
        (context) => showModalBottomSheet<Object?>(
          context: context,
          isScrollControlled: true,
          builder: (_) => ContinueOnComputerSheet(
            command: SessionResumeCommand.build(
              cli: SessionResumeCli.openCode1,
              sessionID: checkoutSessionID,
              directory: null,
            ),
          ),
        ),
      );
      kit.expectVisible(
        find.byKey(const ValueKey('continue-on-computer-unavailable')),
      );
    }),

    // -- continue-on-phone-sheet ---------------------------------------------------------
    CensusShot('continue-on-phone-sheet', (kit) async {
      final controller = await kit.connected();
      await kit.pumpApp(
        _backdrop('Fix flaky checkout test'),
        controller: controller,
      );
      await kit.present(
        (context) => showModalBottomSheet<void>(
          context: context,
          isScrollControlled: true,
          builder: (_) => const ContinueOnPhoneSheet(
            link: SessionLink(
              profileID: 'laptop',
              sessionID: checkoutSessionID,
            ),
          ),
        ),
      );
      kit.expectText('Open on another phone');
    }),
  ],
  notRendered: const {},
);
