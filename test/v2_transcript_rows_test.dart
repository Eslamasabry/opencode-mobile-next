import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/widgets/pickers.dart';
import 'package:opencode_mobile/ui/widgets/tool_card.dart';
import 'package:shared_preferences/shared_preferences.dart';

Widget _host(Widget child) => MaterialApp(
  theme: AppTheme.light(),
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
    testWidgets('model switch renders the divider pill with tooltip detail', (
      tester,
    ) async {
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
      expect(find.byIcon(Icons.memory_rounded), findsOneWidget);
      final tooltip = tester.widget<Tooltip>(find.byType(Tooltip));
      expect(tooltip.message, 'Previously gpt-5.6-sol');
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
      expect(find.byIcon(Icons.support_agent_rounded), findsOneWidget);
      // No previous value → no tooltip.
      expect(find.byType(Tooltip), findsNothing);
    });

    testWidgets('location switch shows the basename with full-path detail', (
      tester,
    ) async {
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
      expect(find.byIcon(Icons.drive_file_move_outline), findsOneWidget);
      expect(
        tester.widget<Tooltip>(find.byType(Tooltip)).message,
        '/tmp/other-project',
      );
    });
  });

  group('TranscriptNotice variants', () {
    testWidgets('system notice collapses to two lines and expands on tap', (
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
      expect(find.byIcon(Icons.settings_suggest_outlined), findsOneWidget);
      expect(find.textContaining('System update'), findsOneWidget);
      final collapsed = tester.widget<Text>(find.text(longBody));
      expect(collapsed.maxLines, 2);

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
      expect(find.byIcon(Icons.auto_awesome_outlined), findsOneWidget);
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
      expect(find.byIcon(Icons.electric_bolt_outlined), findsOneWidget);
      expect(find.textContaining('Skill ·'), findsOneWidget);
      expect(find.textContaining('My Skill'), findsOneWidget);
    });

    testWidgets('unknown variants degrade to a generic server notice', (
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
      expect(find.textContaining('Server message'), findsOneWidget);
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
      expect(find.textContaining('Context compacted'), findsOneWidget);
      expect(find.byIcon(Icons.compress_rounded), findsOneWidget);

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
      expect(find.text('ran out of room'), findsOneWidget);
    });
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
      home: Scaffold(
        body: ModelCatalogView(controller: controller, applyScope: scope),
      ),
    );

    testWidgets('classic scope keeps the v1 wording', (tester) async {
      await tester.pumpWidget(
        picker(await controller(), ModelPickerApplyScope.classic),
      );
      await tester.pump();
      expect(find.text('Use model and mode'), findsOneWidget);
      expect(
        find.byKey(const Key('model-picker-session-scope-note')),
        findsNothing,
      );
    });

    testWidgets('session scope labels the apply for this session', (
      tester,
    ) async {
      await tester.pumpWidget(
        picker(await controller(), ModelPickerApplyScope.session),
      );
      await tester.pump();
      expect(find.text('Use for this session'), findsOneWidget);
      expect(
        find.byKey(const Key('model-picker-session-scope-note')),
        findsOneWidget,
      );
      expect(
        find.text("Applies to this session's next turns."),
        findsOneWidget,
      );
    });

    testWidgets('new-sessions scope labels the apply as the default', (
      tester,
    ) async {
      await tester.pumpWidget(
        picker(await controller(), ModelPickerApplyScope.newSessions),
      );
      await tester.pump();
      expect(find.text('Use for new sessions'), findsOneWidget);
      expect(
        find.byKey(const Key('model-picker-session-scope-note')),
        findsNothing,
      );
    });
  });
}
