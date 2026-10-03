// Census scenes for the ledger part `d-chat-sheets`
// (docs/design/ui-ledger/parts/d-chat-sheets.json). See tool/capture/census_test.dart.
//
// ignore_for_file: invalid_use_of_protected_member, invalid_use_of_visible_for_testing_member
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart' show find;
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api2/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart'
    show PendingQuestion, QuestionChoice, QuestionPrompt, StreamStatus;
import 'package:opencode_mobile/state/session_auto_approval.dart';
import 'package:opencode_mobile/ui/screens/chat/permission_sheet.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/widgets/tool_card.dart';
import 'package:opencode_mobile/voice/controller.dart';
import 'package:opencode_mobile/voice/model_download.dart';
import 'package:opencode_mobile/voice/model_manager.dart';
import 'package:opencode_mobile/voice/notices.dart';
import 'package:opencode_mobile/voice/voice_ui.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../../../test/support/v2_subagent_fixture.dart';
import '../../fixtures.dart';
import '../census_core.dart';
import '../support/d_chat_sheets_fakes.dart';

// ---------------------------------------------------------------------------
// Scene helpers
// ---------------------------------------------------------------------------

/// The real ChatScreen for the checkout session over [transcript].
Future<CaptureController> _chat(
  CensusKit kit, {
  List<MessageWithParts>? transcript,
  bool busy = false,
  bool forms = false,
  List<Todo> todos = const [],
  String sessionID = checkoutSessionID,
  void Function(CaptureController controller)? before,
  Future<void> Function(CaptureController controller)? beforeAsync,
  VoiceComposerController Function(SharedPreferences prefs)? voice,
}) async {
  final api = DChatApi(
    transcript: transcript ?? dFinishedTurn(),
    todoList: todos,
    forms: forms,
  )..busy = busy ? {checkoutSessionID} : {};
  final controller = await kit.connected(api: api);
  before?.call(controller);
  await beforeAsync?.call(controller);
  VoiceComposerController? voiceController;
  if (voice != null) {
    voiceController = voice(await SharedPreferences.getInstance());
    kit.onDispose(voiceController.dispose);
  }
  await kit.pumpApp(
    ChatScreen(sessionID: sessionID, voiceController: voiceController),
    controller: controller,
  );
  return controller;
}

/// Opens the conversation menu (app-bar overflow) and picks [label].
Future<void> _sessionMenu(CensusKit kit, String label) async {
  await kit.tapKey('session-actions-button');
  kit.expectVisible(find.byKey(const Key('session-menu-sheet')));
  await kit.tap(find.text(label).last);
}

Future<void> _openTools(CensusKit kit) async {
  await kit.tapKey('composer-tools-button');
}

PermissionRequest _editPermission() => PermissionRequest(
  id: 'perm_edit',
  sessionID: checkoutSessionID,
  permission: 'edit',
  patterns: const ['test/checkout_test.dart'],
  metadata: const {
    'filepath': '$projectDirectory/test/checkout_test.dart',
    'diff': editPatch,
  },
  always: const ['test/*'],
);

const _scopeQuestion = PendingQuestion(
  id: 'q_checkout',
  sessionID: checkoutSessionID,
  prompts: [
    QuestionPrompt(
      title: 'Test scope',
      question: 'Run the whole suite, or only the checkout tests?',
      multiple: false,
      custom: true,
      choices: [
        QuestionChoice(label: 'Whole suite', description: 'About 4 min'),
        QuestionChoice(label: 'Only checkout', description: 'About 20 s'),
      ],
    ),
  ],
);

const _checksQuestion = PendingQuestion(
  id: 'q_checks',
  sessionID: checkoutSessionID,
  prompts: [
    QuestionPrompt(
      title: 'Checks before the pull request',
      question: 'Which checks should run before I open the pull request?',
      multiple: true,
      custom: true,
      choices: [
        QuestionChoice(label: 'Unit tests', description: '214 tests, 3 min'),
        QuestionChoice(label: 'Widget goldens', description: 'Dark and light'),
        QuestionChoice(label: 'Static analysis', description: 'dart analyze'),
      ],
    ),
  ],
);

const _longQuestion = PendingQuestion(
  id: 'q_release',
  sessionID: checkoutSessionID,
  prompts: [
    QuestionPrompt(
      title: 'Release plan',
      question: 'Which branch should the fix land on?',
      multiple: false,
      custom: false,
      choices: [
        QuestionChoice(label: 'dev', description: 'Next beta'),
        QuestionChoice(label: 'release/2.4', description: 'Hotfix'),
      ],
    ),
    QuestionPrompt(
      title: 'Changelog',
      question: 'Mention the fix in the changelog?',
      multiple: false,
      custom: false,
      choices: [
        QuestionChoice(label: 'Yes', description: 'Under Fixes'),
        QuestionChoice(label: 'No', description: 'Internal only'),
      ],
    ),
    QuestionPrompt(
      title: 'Reviewers',
      question: 'Who should review it?',
      multiple: true,
      custom: true,
      choices: [
        QuestionChoice(label: 'Payments team', description: ''),
        QuestionChoice(label: 'Mobile team', description: ''),
      ],
    ),
  ],
);

Api2FormInfo _deployForm({bool long = false}) => Api2FormInfo(
  id: 'frm_deploy',
  sessionID: checkoutSessionID,
  title: 'Schedule the staging deploy',
  fields: [
    Api2FormField(
      key: 'env',
      type: Api2FormFieldType.string,
      title: 'Environment',
      required: true,
      options: [
        Api2FormOption(
          value: 'staging',
          label: 'Staging',
          description: 'shop-staging.example.dev',
        ),
        Api2FormOption(value: 'preview', label: 'Preview'),
      ],
      defaultValue: 'staging',
    ),
    Api2FormField(
      key: 'date',
      type: Api2FormFieldType.string,
      format: 'date',
      title: 'Deploy date',
      defaultValue: '2026-10-02',
    ),
    Api2FormField(
      key: 'notify',
      type: Api2FormFieldType.boolean,
      title: 'Notify the payments channel',
      defaultValue: true,
    ),
    Api2FormField(
      key: 'notes',
      type: Api2FormFieldType.string,
      title: 'Release notes',
      placeholder: 'What changed for testers',
    ),
    if (long) ...[
      Api2FormField(
        key: 'checks',
        type: Api2FormFieldType.multiselect,
        title: 'Checks to run first',
        options: [
          Api2FormOption(value: 'unit', label: 'Unit tests'),
          Api2FormOption(value: 'golden', label: 'Goldens'),
          Api2FormOption(value: 'e2e', label: 'End-to-end'),
        ],
      ),
      Api2FormField(
        key: 'retries',
        type: Api2FormFieldType.integer,
        title: 'Retries on failure',
        minimum: 0,
        maximum: 5,
        defaultValue: 1,
      ),
    ],
  ],
);

Future<void> _formSheet(CensusKit kit, {bool long = false}) async {
  final form = _deployForm(long: long);
  await _chat(kit, forms: true, before: (c) => c.forms = {form.id: form});
  kit.expectVisible(find.byKey(ValueKey('form-request-card-${form.id}')));
  await kit.tapKey('form-request-answer-${form.id}');
  kit.expectVisible(find.byKey(const Key('form-submit')));
}

Future<void> _openPermissionSheet(
  CensusKit kit,
  PermissionRequest permission, {
  List<MessageWithParts>? transcript,
}) async {
  await _chat(
    kit,
    transcript: transcript ?? sampleTranscript(awaitingPermission: true),
    busy: true,
    before: (c) => c.permissions = {permission.id: permission},
  );
  await kit.tapKey('permission-card-review');
  kit.expectVisible(find.byKey(const Key('permission-sheet')));
}

VoiceComposerController Function(SharedPreferences) _voice({
  VoiceModelState state = VoiceModelState.ready,
  Set<String> installed = const {'base'},
  VoiceDownloadProgress? progress,
  DVoiceOutcome outcome = DVoiceOutcome.listening,
}) =>
    (prefs) => DVoiceController(
      models: DVoiceModels(
        prefs,
        state: state,
        installed: installed,
        progress: progress,
      ),
      outcome: outcome,
    );

/// Voice input from the composer's tools sheet.
Future<void> _openVoice(
  CensusKit kit,
  VoiceComposerController Function(SharedPreferences) voice,
) async {
  await _chat(kit, voice: voice);
  await _openTools(kit);
  await kit.tapKey('composer-tool-voice');
}

Future<void> _voiceSetup(
  CensusKit kit, {
  VoiceModelState state = VoiceModelState.required,
  Set<String> installed = const {},
  VoiceDownloadProgress? progress,
}) async {
  await _openVoice(
    kit,
    _voice(state: state, installed: installed, progress: progress),
  );
  kit.expectText('Local voice input');
}

/// A shell call whose output runs past the preview.
Part _longShellPart() => Part(
  id: 'tool_bash_long',
  messageID: 'msg_assistant',
  type: 'tool',
  callID: 'tool_bash_long',
  toolName: 'bash',
  toolState: ToolState(
    status: 'completed',
    input: const {
      'command': 'flutter test',
      'description': 'Run the full test suite',
    },
    output: [
      for (var i = 1; i <= 40; i++)
        '00:${(i ~/ 2).toString().padLeft(2, '0')} +$i: '
            'checkout ${i.isEven ? 'applies' : 'keeps'} coupon case $i',
      '00:21 +214: All tests passed!',
    ].join('\n'),
    metadata: const {'exit': 0},
  ),
);

Part _subagentPart() => Part(
  id: 'tool_subagent',
  messageID: 'msg_assistant',
  type: 'tool',
  callID: 'tool_subagent',
  toolName: 'subagent',
  toolState: v2SubagentState(childStatus: 'completed', background: false),
);

/// Composer tools › Advanced › Voice conversation.
Future<void> _startConversation(CensusKit kit) async {
  await _chat(kit, voice: _voice());
  await _openTools(kit);
  await kit.tapKey('composer-tools-advanced');
  await kit.tapKey('composer-tool-conversation');
}

/// Opens the turn's folded work, then the one tool card inside it.
Future<void> _expandTool(CensusKit kit) async {
  await kit.tapKey('work-group-header');
  final card = find.descendant(
    of: find.byKey(const Key('work-group-steps')),
    matching: find.byType(ToolCard),
  );
  kit.expectVisible(card, 'the tool card');
  await kit.tap(
    find.descendant(of: card.first, matching: find.byType(InkWell)).first,
  );
}

// ---------------------------------------------------------------------------
// The area
// ---------------------------------------------------------------------------

final dChatSheetsArea = CensusArea(
  'd-chat-sheets',
  shots: [
    // -- permissions ---------------------------------------------------------
    CensusShot(
      'permission-sheet',
      state: 'command',
      (kit) async {
        await _openPermissionSheet(kit, samplePermission());
        kit.expectText(checkoutCommand);
      },
      note: 'Opened from the chat card Review; a v1 shell command request.',
    ),
    CensusShot(
      'permission-sheet',
      state: 'edit-diff',
      (kit) async {
        await _openPermissionSheet(kit, _editPermission());
        kit.expectVisible(find.byKey(const Key('permission-see-full-diff')));
      },
      note: 'An edit request with the diff preview the server attached.',
    ),
    CensusShot(
      'permission-sheet',
      state: 'reject-with-message',
      (kit) async {
        final controller = await _chat(
          kit,
          transcript: sampleTranscript(awaitingPermission: true),
          busy: true,
          before: (c) =>
              c.permissions = {samplePermission().id: samplePermission()},
        );
        // An OpenCode 2 request takes a rejection message; the v2 marker is
        // private to the controller, so the sheet is presented the way
        // showPermissionSheet does with supportsRejectMessage on.
        await kit.present(
          (context) => showModalBottomSheet<void>(
            context: context,
            isScrollControlled: true,
            useSafeArea: true,
            clipBehavior: Clip.antiAlias,
            constraints: const BoxConstraints(maxWidth: 720),
            backgroundColor: Theme.of(context).colorScheme.surfaceContainerLow,
            shape: const RoundedRectangleBorder(
              borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
            ),
            builder: (sheetContext) => Padding(
              padding: EdgeInsets.only(
                bottom: MediaQuery.viewInsetsOf(sheetContext).bottom,
              ),
              child: PermissionSheet(
                permission: samplePermission(),
                onReply: (reply, {message}) async {},
                supportsRejectMessage: true,
                allowPersistentPermission:
                    controller.capabilities.persistentPermissionGrants,
                contextLabel: 'in this conversation',
              ),
            ),
          ),
        );
        await kit.tapKey('permission-reject');
        await kit.enterText(
          find.byKey(const Key('permission-reject-message')),
          'Run only the checkout tests, not the whole suite',
        );
        kit.expectVisible(find.byKey(const Key('permission-reject-send')));
      },
      note:
          'OpenCode 2 "Reject…" with the reason field. Presented with the '
          'same sheet options as showPermissionSheet (the v2 marker that '
          'turns the field on is private to the controller).',
    ),
    CensusShot(
      'permission-sheet-always-dialog',
      (kit) async {
        await _openPermissionSheet(kit, samplePermission());
        await kit.tapKey('permission-allow-always');
        kit.expectVisible(find.byKey(const Key('permission-confirm-always')));
      },
      note:
          'Always allow tapped on the shell command request. Review: the '
          'separator in "Settings ▯ Saved permissions" draws as a missing '
          'glyph with the capture fonts.',
    ),
    CensusShot(
      'embedded-permission-attention-card',
      state: 'command',
      (kit) async {
        await _chat(
          kit,
          transcript: sampleTranscript(awaitingPermission: true),
          busy: true,
          before: (c) =>
              c.permissions = {samplePermission().id: samplePermission()},
        );
        kit.expectVisible(find.byKey(const Key('permission-card-review')));
      },
      note: 'Host: the chat, above the composer, while the shell call waits.',
    ),
    CensusShot(
      'embedded-permission-attention-card',
      state: 'edit',
      (kit) async {
        await _chat(
          kit,
          transcript: sampleTranscript(awaitingPermission: true),
          busy: true,
          before: (c) =>
              c.permissions = {_editPermission().id: _editPermission()},
        );
        kit.expectVisible(find.byKey(const Key('permission-card-review')));
      },
      note: 'Host: the chat; an edit request.',
    ),

    // -- approvals -----------------------------------------------------------
    CensusShot(
      'session-approvals-sheet',
      state: 'ask',
      (kit) async {
        await _chat(kit);
        await kit.tapKey('session-actions-button');
        await kit.tap(find.text('Conversation actions').last);
        await kit.tap(find.text('Approvals').last);
        kit.expectVisible(find.byKey(const Key('session-approvals-sheet')));
      },
      note: 'Conversation menu › Conversation actions › Approvals; default.',
    ),
    CensusShot(
      'session-approvals-sheet',
      state: 'auto',
      (kit) async {
        await _chat(
          kit,
          beforeAsync: (c) => c.setSessionAutoApproval(
            checkoutSessionID,
            const SessionAutoApproval(mode: AutoApprovalMode.autoOnce),
          ),
        );
        await kit.tapKey('auto-approval-indicator');
        kit.expectVisible(find.byKey(const Key('session-approvals-sheet')));
      },
      note: 'Opened from the auto-approval indicator while it is on.',
    ),
    CensusShot(
      'embedded-auto-approval-indicator',
      state: 'on',
      (kit) async {
        await _chat(
          kit,
          busy: true,
          beforeAsync: (c) => c.setSessionAutoApproval(
            checkoutSessionID,
            const SessionAutoApproval(mode: AutoApprovalMode.autoOnce),
          ),
        );
        kit.expectVisible(find.byKey(const Key('auto-approval-indicator')));
      },
      note: 'Host: the chat, strip above the composer; a turn is running.',
    ),
    CensusShot(
      'embedded-auto-approval-indicator',
      state: 'paused',
      (kit) async {
        final controller = await _chat(
          kit,
          beforeAsync: (c) => c.setSessionAutoApproval(
            checkoutSessionID,
            const SessionAutoApproval(mode: AutoApprovalMode.autoOnce),
          ),
        );
        controller
          ..status = StreamStatus.disconnected
          ..lastError = 'Cannot reach http://192.168.1.20:4096';
        controller.notifyListeners();
        await kit.settle();
        kit.expectVisible(find.byKey(const Key('auto-approval-indicator')));
      },
      note: 'Host: the chat after the connection dropped.',
    ),

    // -- timeline and todos --------------------------------------------------
    CensusShot('timeline-sheet', state: 'loaded', (kit) async {
      await _chat(kit, transcript: dLongTranscript());
      await _sessionMenu(kit, 'Timeline');
      kit.expectVisible(find.byKey(const Key('timeline-search')));
    }, note: 'Conversation menu › Timeline.'),
    CensusShot('timeline-sheet', state: 'search', (kit) async {
      await _chat(kit, transcript: dLongTranscript());
      await _sessionMenu(kit, 'Timeline');
      await kit.enterText(find.byKey(const Key('timeline-search')), 'coupon');
      kit.expectVisible(find.byKey(const Key('timeline-search')));
    }, note: 'Searching the timeline for "coupon".'),
    CensusShot(
      'timeline-sheet',
      state: 'fork',
      (kit) async {
        await _chat(kit, transcript: dLongTranscript());
        await _openTools(kit);
        await kit.tapKey('composer-tool-commands');
        await kit.enterText(
          find.byKey(const Key('command-launcher-search')),
          'fork',
        );
        await kit.tapKey('command-mobile-fork');
        // Fork mode: prompts only, each row forks (no separate fork button).
        kit.expectVisible(find.byKey(const ValueKey('timeline-row-msg_u4')));
        if (find
                .byKey(const ValueKey('timeline-fork-msg_u4'))
                .evaluate()
                .isNotEmpty ||
            find
                .byKey(const ValueKey('timeline-row-msg_a4'))
                .evaluate()
                .isNotEmpty) {
          throw CensusMismatch('the timeline is not in fork mode');
        }
      },
      note: 'Command launcher › Fork from prompt: pick a prompt to fork.',
    ),
    CensusShot('todos-sheet', state: 'loaded', (kit) async {
      await _chat(
        kit,
        todos: [
          Todo(
            content: 'Reproduce the flaky checkout test',
            status: 'completed',
          ),
          Todo(content: 'Wait on the settled state', status: 'completed'),
          Todo(
            content: 'Add a regression test for the coupon race',
            status: 'in_progress',
          ),
          Todo(content: 'Run the full suite', status: 'pending'),
        ],
      );
      await _sessionMenu(kit, 'Todos');
      kit.expectText('Run the full suite');
    }, note: 'Conversation menu › Todos.'),
    CensusShot(
      'todos-sheet',
      state: 'empty',
      (kit) async {
        await _chat(kit);
        await _sessionMenu(kit, 'Todos');
        kit.expectTextContaining('todo');
      },
      note: 'Conversation menu › Todos with none planned.',
    ),

    // -- questions -----------------------------------------------------------
    CensusShot(
      'embedded-question-attention-card',
      state: 'inline',
      (kit) async {
        await _chat(
          kit,
          before: (c) => c.questions = {_scopeQuestion.id: _scopeQuestion},
        );
        kit.expectVisible(find.byKey(const Key('question-card-more')));
      },
      note: 'Host: the chat; one prompt answered by a tap.',
    ),
    CensusShot(
      'embedded-question-attention-card',
      state: 'summary',
      (kit) async {
        await _chat(
          kit,
          before: (c) => c.questions = {_longQuestion.id: _longQuestion},
        );
        kit.expectVisible(find.byKey(const Key('question-card-answer')));
      },
      note:
          'Host: the chat; three prompts, so the card summarises and Answer '
          'opens the full sheet.',
    ),
    CensusShot(
      'embedded-question-options',
      state: 'multi-select',
      (kit) async {
        await _chat(
          kit,
          before: (c) => c.questions = {_checksQuestion.id: _checksQuestion},
        );
        await kit.tapKey('question-option-Unit tests');
        await kit.tapKey('question-option-Static analysis');
        kit.expectVisible(find.byKey(const Key('question-card-send')));
      },
      note: 'Host: the chat question card; two of three checks chosen.',
    ),
    CensusShot(
      'embedded-question-options',
      state: 'custom-answer',
      (kit) async {
        await _chat(
          kit,
          before: (c) => c.questions = {_scopeQuestion.id: _scopeQuestion},
        );
        await kit.enterText(
          find.descendant(
            of: find.byKey(const ValueKey('question-card-custom-0')),
            matching: find.byType(TextField),
          ),
          'Only the checkout and cart tests',
        );
        kit.expectVisible(find.byKey(const Key('question-card-send')));
      },
      note: 'Host: the chat question card with a free-text answer typed.',
    ),

    // -- voice ---------------------------------------------------------------
    CensusShot(
      'voice-model-setup-sheet',
      state: 'not-installed',
      (kit) => _voiceSetup(kit),
      note: 'Composer tools › Voice input before any model is downloaded.',
    ),
    CensusShot(
      'voice-model-setup-sheet',
      state: 'downloading',
      (kit) => _voiceSetup(
        kit,
        state: VoiceModelState.downloading,
        progress: const VoiceDownloadProgress(
          received: 61 * 1024 * 1024,
          total: 160 * 1024 * 1024,
          fileName: 'base-encoder.int8.onnx',
        ),
      ),
      note: 'Download in progress (frozen at 38%).',
    ),
    CensusShot(
      'voice-model-setup-sheet',
      state: 'installed',
      (kit) async {
        final prefs = await kit.prefs();
        final models = DVoiceModels(prefs);
        kit.onDispose(models.dispose);
        await _chat(kit);
        await kit.present(
          (context) => showVoiceModelSetupSheet(context, models),
        );
        kit.expectText('Local voice input');
        kit.expectVisible(find.byKey(const Key('voice-delete-base')));
      },
      note: 'The balanced pack installed (as from Settings › Voice).',
    ),
    CensusShot(
      'voice-model-setup-sheet-delete-dialog',
      (kit) async {
        final prefs = await kit.prefs();
        final models = DVoiceModels(prefs);
        kit.onDispose(models.dispose);
        await _chat(kit);
        await kit.present(
          (context) => showVoiceModelSetupSheet(context, models),
        );
        await kit.tapKey('voice-delete-base');
        kit.expectText('Keep Balanced');
      },
      note: 'Delete on the installed pack.',
    ),
    CensusShot(
      'voice-composer-sheet',
      state: 'listening',
      (kit) async {
        await _openVoice(kit, _voice());
        kit.expectVisible(find.byKey(const Key('stop-voice-recording')));
      },
      note: 'Composer tools › Voice input, recording (fake recorder, 0:07).',
    ),
    CensusShot(
      'voice-composer-sheet',
      state: 'draft',
      (kit) async {
        await _openVoice(kit, _voice(outcome: DVoiceOutcome.draft));
        kit.expectVisible(find.byKey(const Key('insert-voice-draft')));
      },
      note: 'Transcript ready for review (fake recognizer).',
    ),
    CensusShot(
      'voice-composer-sheet',
      state: 'mic-denied',
      (kit) async {
        await _openVoice(kit, _voice(outcome: DVoiceOutcome.micDenied));
        kit.expectText('Open app settings');
      },
      note: 'Microphone permission permanently denied.',
    ),
    CensusShot(
      'voice-composer-sheet',
      state: 'conversation-ready',
      (kit) async {
        await _startConversation(kit);
        kit.expectText('Start listening');
      },
      note:
          'Composer tools › Voice conversation: the sheet waits for Start '
          'listening.',
    ),
    CensusShot(
      'embedded-voice-conversation-controls',
      (kit) async {
        await _startConversation(kit);
        await kit.tapKey('voice-composer-cancel');
        kit.expectVisible(find.byKey(const Key('voice-speak-replies')));
      },
      note: 'Host: the chat in voice conversation mode, sheet closed.',
    ),
    CensusShot(
      'voice-notices',
      (kit) async {
        await _chat(kit);
        await kit.push(const VoiceNoticesPage());
        await kit.realWait(const Duration(seconds: 1));
        kit.expectText('Voice licenses');
        kit.expectTextContaining('ONNX Runtime');
      },
      note:
          'Full-screen notices (reached from Settings › Voice). Review: raw '
          'Markdown (#, **, backticks) with the file\'s hard line breaks.',
    ),

    // -- transcript parts ------------------------------------------------------
    CensusShot(
      'embedded-tool-card',
      state: 'shell',
      (kit) async {
        await _chat(kit, transcript: dToolTranscript(shellPart()));
        await _expandTool(kit);
        kit.expectTextContaining('All tests passed');
      },
      note: 'Host: the chat; the folded work opened, the shell call expanded.',
    ),
    CensusShot(
      'embedded-tool-card',
      state: 'long-output',
      (kit) async {
        await _chat(kit, transcript: dToolTranscript(_longShellPart()));
        await _expandTool(kit);
        kit.expectTextContaining('+214: All tests passed!');
      },
      note:
          'Host: the chat; a 41-line shell output. Review: it renders in full '
          'inline (no 13-line cap, no See all) and pushes the card header off '
          'screen; the edit card does cap with See all.',
    ),
    CensusShot(
      'embedded-tool-card',
      state: 'edit',
      (kit) async {
        await _chat(kit, transcript: dToolTranscript(editPart()));
        await _expandTool(kit);
      },
      note: 'Host: the chat; the edit call expanded with its diff.',
    ),
    CensusShot(
      'embedded-tool-card',
      state: 'subagent',
      (kit) async {
        await _chat(kit, transcript: dToolTranscript(_subagentPart()));
        await _expandTool(kit);
        kit.expectVisible(find.byKey(const Key('task-open-session')));
      },
      note:
          'Host: the chat; an OpenCode 2 subagent call with its session '
          'link.',
    ),
    CensusShot(
      'embedded-markdown-text',
      (kit) async {
        await _chat(kit, transcript: dMarkdownTranscript());
        kit.expectTextContaining('What changed');
      },
      note:
          'Host: the chat; heading, list, link, path chip, table, code '
          'block and choices.',
    ),
    CensusShot(
      'markdown-code-reader',
      (kit) async {
        await _chat(kit, transcript: dMarkdownTranscript());
        final options = find.byTooltip('Code options');
        await kit.scrollTo(options);
        await kit.tap(options.first);
        await kit.tap(find.text('Full screen').last);
        kit.expectText('Code reader');
      },
      note: 'Code options › Full screen on the reply code block.',
    ),

    // -- forms ---------------------------------------------------------------
    CensusShot(
      'form-sheet',
      state: 'sheet',
      (kit) => _formSheet(kit),
      note: 'Chat form card › Answer: four fields, so a bottom sheet.',
    ),
    CensusShot(
      'form-sheet',
      state: 'full-screen',
      (kit) => _formSheet(kit, long: true),
      note:
          'Six fields, so the full-screen dialog variant. Review: the '
          '"pinned" Send answers bar floats mid-screen under the last field '
          'with black bands, instead of sitting at the bottom.',
    ),
    CensusShot(
      'form-sheet-dismiss-confirm-sheet',
      (kit) async {
        await _formSheet(kit);
        await kit.tapKey('form-cancel');
        kit.expectVisible(find.byKey(const Key('form-dismiss-confirm')));
      },
      note: 'Dismiss tapped on the form sheet.',
    ),
    CensusShot(
      'form-sheet-date-picker',
      (kit) async {
        await _formSheet(kit);
        await kit.tap(
          find.descendant(
            of: find.byKey(const Key('form-field-date')),
            matching: find.byType(TextField),
          ),
        );
        kit.expectVisible(find.byType(DatePickerDialog));
      },
      note: 'The deploy date field tapped (stock Material date picker).',
    ),
  ],
  notRendered: {
    'embedded-return-brief-panel':
        'removed from the code: ReturnBriefPanel/ReturnBriefCard no longer '
        'exist (the 2026-09-24 Work tab cleanup moved "Unreviewed" into the '
        'session rows; see test/return_brief_widget_test.dart)',
    'embedded-return-brief-panel-status-dialog':
        'removed from the code with the return brief card '
        '(lib/ui/widgets/return_brief_card.dart no longer exists)',
  },
);
