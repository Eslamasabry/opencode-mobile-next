import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/product_repository.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/widgets/pickers.dart';
import 'package:opencode_mobile/ui/widgets/tool_card.dart';
import 'package:shared_preferences/shared_preferences.dart';

Widget _host(Widget child) => MaterialApp(
  theme: AppTheme.light(),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home: Scaffold(body: SingleChildScrollView(child: child)),
);

Part _tagged({
  required String type,
  required String kind,
  String text = '',
  String? header,
  String? url,
}) => Part(
  id: 'v2-0',
  type: type,
  toolName: kind,
  text: text,
  filename: header,
  url: url,
  messageID: 'msg_1',
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('instruction notice never renders raw entry names or values', (
    tester,
  ) async {
    await tester.pumpWidget(
      _host(
        V2TranscriptRow(
          part: _tagged(
            type: 'v2:notice',
            kind: 'instructions',
            text: 'private contents',
            header: 'server.private',
          ),
          messageId: 'msg_note',
        ),
      ),
    );
    expect(find.text('Instructions updated'), findsOneWidget);
    await tester.tap(find.text('Instructions updated'));
    await tester.pumpAndSettle();
    expect(
      find.text(
        "The agent's conversation instructions have been updated for this step.",
      ),
      findsOneWidget,
    );
    expect(find.textContaining('server.private'), findsNothing);
    expect(find.textContaining('private contents'), findsNothing);
  });

  group('v2VariantPart', () {
    test('finds the tagged part and ignores v1 messages', () {
      final tagged = MessageWithParts(
        info: MessageInfo(id: 'm1', sessionID: 's', role: 'user'),
        parts: [_tagged(type: 'v2:switch', kind: 'model', text: 'm2')],
      );
      expect(v2VariantPart(tagged)?.type, 'v2:switch');

      final v1 = MessageWithParts(
        info: MessageInfo(id: 'm2', sessionID: 's', role: 'assistant'),
        parts: [Part(id: 'text-0', type: 'text', text: 'hello')],
      );
      expect(v2VariantPart(v1), isNull);
    });
  });

  group('TranscriptMarker switches', () {
    testWidgets(
      'model switch renders the divider line with what it switched to',
      (tester) async {
        await tester.pumpWidget(
          _host(
            V2TranscriptRow(
              part: _tagged(
                type: 'v2:switch',
                kind: 'model',
                text: 'gpt-5.6-sol · high',
                header: 'gpt-5.6-sol',
              ),
              messageId: 'msg_1',
            ),
          ),
        );
        expect(
          find.byKey(const ValueKey('transcript-marker-model-switched-msg_1')),
          findsOneWidget,
        );
        expect(find.text('Model → gpt-5.6-sol · high'), findsOneWidget);
        expect(find.byIcon(AppIconography.model), findsOneWidget);
        // The switch says what it switched to; the previous value is history
        // (chat-1 rethink: a hover-only detail never reached touch).
        expect(find.byType(Tooltip), findsNothing);
        expect(find.textContaining('Previously'), findsNothing);
      },
    );

    testWidgets('a model switch names the model as the picker does', (
      tester,
    ) async {
      await tester.pumpWidget(
        _host(
          V2TranscriptRow(
            part: _tagged(type: 'v2:switch', kind: 'model', text: 'big-pickle'),
            messageId: 'msg_2',
            modelName: (id) => id == 'big-pickle' ? 'Big Pickle' : null,
          ),
        ),
      );
      expect(find.text('Model → Big Pickle'), findsOneWidget);
      await tester.pumpWidget(
        _host(
          V2TranscriptRow(
            part: _tagged(
              type: 'v2:switch',
              kind: 'model',
              text: 'gpt-5.6-sol · high',
            ),
            messageId: 'msg_3',
            modelName: (id) => id == 'gpt-5.6-sol' ? 'GPT-5.6 Sol' : null,
          ),
        ),
      );
      expect(find.text('Model → GPT-5.6 Sol · high'), findsOneWidget);
    });

    testWidgets('agent switch renders its own icon and copy', (tester) async {
      await tester.pumpWidget(
        _host(
          V2TranscriptRow(
            part: _tagged(type: 'v2:switch', kind: 'agent', text: 'plan'),
            messageId: 'msg_2',
          ),
        ),
      );
      expect(
        find.byKey(const ValueKey('transcript-marker-agent-switched-msg_2')),
        findsOneWidget,
      );
      expect(find.text('Agent → plan'), findsOneWidget);
      expect(find.byIcon(AppIconography.support), findsOneWidget);
      // No previous value → no tooltip.
      expect(find.byType(Tooltip), findsNothing);
    });

    testWidgets('location switch shows the folder it moved to', (tester) async {
      await tester.pumpWidget(
        _host(
          V2TranscriptRow(
            part: _tagged(
              type: 'v2:switch',
              kind: 'location',
              text: 'other-project',
              url: '/tmp/other-project',
            ),
            messageId: 'msg_3',
          ),
        ),
      );
      expect(
        find.byKey(const ValueKey('transcript-marker-location-switched-msg_3')),
        findsOneWidget,
      );
      expect(find.text('Moved → other-project'), findsOneWidget);
      expect(find.byIcon(AppIconography.folderOpen), findsOneWidget);
      expect(find.byType(Tooltip), findsNothing);
    });
  });

  group('TranscriptNotice variants', () {
    testWidgets('system notice is its header until tapped, then shows all', (
      tester,
    ) async {
      final longBody = List.generate(
        12,
        (index) => 'System context line $index.',
      ).join(' ');
      await tester.pumpWidget(
        _host(
          V2TranscriptRow(
            part: _tagged(type: 'v2:notice', kind: 'system', text: longBody),
            messageId: 'msg_4',
          ),
        ),
      );
      expect(
        find.byKey(const ValueKey('transcript-notice-msg_4')),
        findsOneWidget,
      );
      expect(find.byIcon(AppIconography.settingsAdvanced), findsOneWidget);
      expect(find.textContaining('System update'), findsOneWidget);
      // A routine notice costs one line of transcript; the detail is a tap
      // away instead of two lines under every "Context added".
      expect(find.text(longBody), findsNothing);

      await tester.tap(find.byKey(const ValueKey('transcript-notice-msg_4')));
      await tester.pumpAndSettle();
      expect(tester.widget<Text>(find.text(longBody)).maxLines, isNull);
    });

    testWidgets('synthetic notice uses the description as its header', (
      tester,
    ) async {
      await tester.pumpWidget(
        _host(
          V2TranscriptRow(
            part: _tagged(
              type: 'v2:notice',
              kind: 'synthetic',
              text: 'plan-mode reminder',
              header: 'Attached context',
            ),
            messageId: 'msg_5',
          ),
        ),
      );
      expect(find.byIcon(AppIconography.sparkle), findsOneWidget);
      expect(find.textContaining('Attached context'), findsOneWidget);
    });

    testWidgets('skill notice names the skill in the header', (tester) async {
      await tester.pumpWidget(
        _host(
          V2TranscriptRow(
            part: _tagged(
              type: 'v2:notice',
              kind: 'skill',
              text: 'skill body',
              header: 'My Skill',
            ),
            messageId: 'msg_6',
          ),
        ),
      );
      expect(find.byIcon(AppIconography.lightning), findsOneWidget);
      expect(find.textContaining('Skill ·'), findsOneWidget);
      expect(find.textContaining('My Skill'), findsOneWidget);
    });

    testWidgets('an unknown type with nothing but its name is not drawn', (
      tester,
    ) async {
      await tester.pumpWidget(
        _host(
          V2TranscriptRow(
            part: _tagged(
              type: 'v2:notice',
              kind: 'unknown',
              text: 'brand-new-variant',
              header: 'Server message',
            ),
            messageId: 'msg_7',
          ),
        ),
      );
      // Only a type name ("idle", "brand-new-variant"): nothing to read.
      expect(find.textContaining('Server message'), findsNothing);
      expect(find.text('brand-new-variant'), findsNothing);
    });

    testWidgets('an unknown message with real text is still shown', (
      tester,
    ) async {
      await tester.pumpWidget(
        _host(
          V2TranscriptRow(
            part: _tagged(
              type: 'v2:notice',
              kind: 'unknown',
              text: 'The server restarted while this ran.',
              header: 'Server message',
            ),
            messageId: 'msg_8',
          ),
        ),
      );
      expect(find.text('Server message'), findsOneWidget);
    });
  });

  group('compaction rows', () {
    testWidgets('running renders the spinner pill', (tester) async {
      await tester.pumpWidget(
        _host(
          V2TranscriptRow(
            part: _tagged(
              type: 'v2:compaction',
              kind: 'running',
              text: 'Compacting conversation…',
            ),
            messageId: 'msg_8',
          ),
        ),
      );
      await tester.pump();
      expect(
        find.byKey(const ValueKey('compaction-running-msg_8')),
        findsOneWidget,
      );
      expect(find.text('Compacting conversation…'), findsOneWidget);
      expect(find.byType(CircularProgressIndicator), findsOneWidget);
    });

    testWidgets('completed renders an expandable summary notice', (
      tester,
    ) async {
      await tester.pumpWidget(
        _host(
          V2TranscriptRow(
            part: _tagged(
              type: 'v2:compaction',
              kind: 'completed',
              text: '## Objective\nShip the port.',
            ),
            messageId: 'msg_9',
          ),
        ),
      );
      expect(
        find.byKey(const ValueKey('compaction-completed-msg_9')),
        findsOneWidget,
      );
      expect(
        find.textContaining('Earlier messages were summarized'),
        findsOneWidget,
      );
      expect(find.byIcon(AppIconography.collapse), findsOneWidget);

      await tester.tap(
        find.byKey(const ValueKey('compaction-completed-msg_9')),
      );
      await tester.pumpAndSettle();
      // Expanded summary routes through markdown: the heading renders as its
      // own styled line without the ## marker.
      expect(find.text('Objective'), findsOneWidget);
    });

    testWidgets('failed renders the error-tinted notice', (tester) async {
      await tester.pumpWidget(
        _host(
          V2TranscriptRow(
            part: _tagged(
              type: 'v2:compaction',
              kind: 'failed',
              text: 'ran out of room',
            ),
            messageId: 'msg_10',
          ),
        ),
      );
      expect(
        find.byKey(const ValueKey('compaction-failed-msg_10')),
        findsOneWidget,
      );
      expect(find.textContaining('Compaction failed'), findsOneWidget);
      // What it means for the reader; the server's own text is not copy.
      expect(
        find.textContaining('still too long for the model'),
        findsOneWidget,
      );
      expect(find.textContaining('ran out of room'), findsNothing);
      // An old failure is history: nothing to press.
      expect(find.byKey(const Key('transcript-notice-action')), findsNothing);
    });

    testWidgets('the newest failure can be retried from its own line', (
      tester,
    ) async {
      var retried = 0;
      await tester.pumpWidget(
        _host(
          V2TranscriptRow(
            part: _tagged(
              type: 'v2:compaction',
              kind: 'failed',
              text: 'Compaction was interrupted',
            ),
            messageId: 'msg_11',
            onCompactAgain: () => retried++,
          ),
        ),
      );
      expect(find.text('Compact again'), findsOneWidget);
      await tester.tap(find.byKey(const Key('transcript-notice-action')));
      expect(retried, 1);
    });
  });

  testWidgets('a server message is titled by what it says', (tester) async {
    await tester.pumpWidget(
      _host(
        V2TranscriptRow(
          part: _tagged(
            type: 'v2:notice',
            kind: 'something-new',
            text: 'Background command finished\nexit 0 after 2m',
          ),
          messageId: 'msg_12',
        ),
      ),
    );
    expect(find.text('Background command finished'), findsOneWidget);
    expect(find.text('Server message'), findsNothing);
    // The rest waits behind the tap, not repeated with the title.
    await tester.tap(find.byKey(const ValueKey('transcript-notice-msg_12')));
    await tester.pumpAndSettle();
    expect(find.text('exit 0 after 2m'), findsOneWidget);
  });

  group('interleaved tool content', () {
    testWidgets('orders text and file segments as delivered', (tester) async {
      final state = ToolState.fromJson({
        'status': 'completed',
        'input': {'query': 'renders'},
        'output': 'first render:\nafter the fix:',
        'attachments': [
          {'url': 'https://example.test/before.txt', 'name': 'before.txt'},
          {'url': 'https://example.test/after.txt', 'name': 'after.txt'},
        ],
        'contentSegments': [
          {'type': 'text', 'text': 'first render:'},
          {
            'type': 'file',
            'url': 'https://example.test/before.txt',
            'name': 'before.txt',
          },
          {'type': 'text', 'text': 'after the fix:'},
          {
            'type': 'file',
            'url': 'https://example.test/after.txt',
            'name': 'after.txt',
          },
        ],
      }, toolName: 'render-check');
      await tester.pumpWidget(
        _host(ToolCard(toolName: 'render-check', state: state)),
      );
      expect(find.byKey(const Key('tool-interleaved-output')), findsOneWidget);
      final positions = <String, double>{
        for (final label in [
          'first render:',
          'before.txt',
          'after the fix:',
          'after.txt',
        ])
          label: tester.getTopLeft(find.text(label).first).dy,
      };
      expect(positions['first render:']!, lessThan(positions['before.txt']!));
      expect(positions['before.txt']!, lessThan(positions['after the fix:']!));
      expect(positions['after the fix:']!, lessThan(positions['after.txt']!));
    });

    testWidgets('trailing-file order keeps the v1 layout', (tester) async {
      final state = ToolState.fromJson({
        'status': 'completed',
        'input': {},
        'output': 'wrote image',
        'attachments': [
          {'url': 'https://example.test/shot.txt', 'name': 'shot.txt'},
        ],
        'contentSegments': [
          {'type': 'text', 'text': 'wrote image'},
          {
            'type': 'file',
            'url': 'https://example.test/shot.txt',
            'name': 'shot.txt',
          },
        ],
      }, toolName: 'screenshot');
      await tester.pumpWidget(
        _host(ToolCard(toolName: 'screenshot', state: state)),
      );
      expect(find.byKey(const Key('tool-interleaved-output')), findsNothing);
      expect(find.byKey(const Key('tool-output-file')), findsOneWidget);
    });
  });

  group('apply-bar scope labels', () {
    Future<ConnectionController> controller() async {
      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      return ConnectionController(ProfileStore(prefs: prefs))
        ..catalogDetailed = true
        ..catalog = const CatalogSnapshot(
          providers: [
            CatalogProvider(id: 'openai', name: 'OpenAI', enabled: true),
          ],
          models: [
            CatalogModel(
              id: 'gpt-5.6-sol',
              providerID: 'openai',
              name: 'GPT-5.6 Sol',
              enabled: true,
              status: 'active',
              contextLimit: 1050000,
              outputLimit: 128000,
              reasoning: true,
              attachments: true,
              tools: true,
              variants: [],
            ),
          ],
          agents: [],
        )
        ..selectedModel = ModelRef(
          providerID: 'openai',
          modelID: 'gpt-5.6-sol',
        );
    }

    Widget picker(
      ConnectionController controller,
      ModelPickerApplyScope scope,
    ) => MaterialApp(
      theme: AppTheme.light(),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      home: Scaffold(
        body: ModelCatalogView(controller: controller, applyScope: scope),
      ),
    );

    // Since 531bb6ab (rethink R1-R6) the classic apply names what it applies:
    // "Use <model>" (or "Use <model> · <agent>" once an agent is chosen).
    testWidgets('classic scope names the model it applies', (tester) async {
      await tester.pumpWidget(
        picker(await controller(), ModelPickerApplyScope.classic),
      );
      await tester.pump();
      expect(find.text('Use GPT-5.6 Sol'), findsOneWidget);
      expect(find.text('Use for this conversation'), findsNothing);
    });

    testWidgets('session scope labels the apply for this session', (
      tester,
    ) async {
      await tester.pumpWidget(
        picker(await controller(), ModelPickerApplyScope.session),
      );
      await tester.pump();
      // The apply action names the scope; the sheet's subtitle adds when it
      // applies (test/revamp/slice_p3_3_model_sheet_test.dart).
      expect(find.text('Use for this conversation'), findsOneWidget);
    });

    testWidgets('new-sessions scope labels the apply as the default', (
      tester,
    ) async {
      await tester.pumpWidget(
        picker(await controller(), ModelPickerApplyScope.newSessions),
      );
      await tester.pump();
      expect(find.text('Use for new conversations'), findsOneWidget);
      expect(
        find.text("Applies to this conversation's next turns."),
        findsNothing,
      );
    });
  });
}
