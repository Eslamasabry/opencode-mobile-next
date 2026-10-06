// Behaviour of screen-library-4's pages (wave 2b): Server commands,
// References and Skills, and the one skill sheet that replaced the preview
// and activation sheets. Asserts what the person sees, what is sent and
// what is copied.
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/library_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _Repository extends ProductRepository implements SessionSkillGateway {
  List<CommandInfo> commands = const [];
  List<ReferenceInfo> references = const [];
  List<SkillInfo> skills = const [];
  Object? loadFailure;
  Object? activationFailure;
  final activations = <(String, String, bool)>[];
  Session session = Session(id: 'ses_test', title: 'Composer improvements');

  Future<T> _answer<T>(T value) async {
    final failure = loadFailure;
    if (failure != null) throw failure;
    return value;
  }

  @override
  Future<List<CommandInfo>> listCommands() => _answer(commands);

  @override
  Future<List<ReferenceInfo>> listReferences() => _answer(references);

  @override
  Future<List<SkillInfo>> listSkills() => _answer(skills);

  @override
  bool get sessionSkillsSupported => true;

  @override
  Future<Session> getSessionDetails(String id) async => session;

  @override
  Future<void> activateSessionSkill(
    String sessionID,
    String skillID, {
    required bool resume,
  }) async {
    activations.add((sessionID, skillID, resume));
    final failure = activationFailure;
    if (failure != null) throw failure;
  }

  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _Controller extends ConnectionController {
  _Controller(super.store);

  @override
  Future<ServerOperationsGateway?> prepareActionRepository() async =>
      repository;
}

Future<(_Controller, _Repository)> _setup() async {
  SharedPreferences.setMockInitialValues({});
  final repository = _Repository();
  final controller = _Controller(
    ProfileStore(prefs: await SharedPreferences.getInstance()),
  )..repository = repository;
  controller.sessionsById['ses_test'] = repository.session;
  addTearDown(controller.dispose);
  return (controller, repository);
}

Future<void> _pump(WidgetTester tester, Widget page) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    MaterialApp(
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      home: Builder(
        builder: (context) => Scaffold(
          body: Center(
            child: TextButton(
              onPressed: () => Navigator.of(
                context,
              ).push(MaterialPageRoute<bool>(builder: (_) => page)),
              child: const Text('Open'),
            ),
          ),
        ),
      ),
    ),
  );
  await tester.tap(find.text('Open'));
  await tester.pumpAndSettle();
}

String? _copied;

const _skill = SkillInfo(
  id: 'review-id',
  name: 'focused-review',
  description: 'Review a change for correctness.',
  location: '/project/.opencode/skills/review/SKILL.md',
  content:
      '---\nname: focused-review\n---\n# Focused review\n\nReview the current change.\n\n| Gate | Command |\n| --- | --- |\n| Tests | `flutter test` |',
  slashCommand: true,
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    _copied = null;
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(SystemChannels.platform, (call) async {
          if (call.method == 'Clipboard.setData') {
            _copied = (call.arguments as Map)['text'] as String?;
          }
          return null;
        });
  });
  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(SystemChannels.platform, null);
  });

  group('Server commands', () {
    testWidgets('rows say what each command does and who runs it', (
      tester,
    ) async {
      final (controller, repository) = await _setup();
      repository.commands = const [
        CommandInfo(
          name: 'review',
          description: 'Review the working tree',
          agent: 'plan',
          subtask: false,
        ),
        CommandInfo(name: 'ship', subtask: false),
      ];
      await _pump(tester, CommandsScreen(controller: controller));

      // The command sheet (slice-P10.1): plain words first, the slash word
      // as the typing hint, who runs it under.
      expect(find.text('Server commands'), findsWidgets);
      expect(find.text('Review the working tree'), findsOneWidget);
      expect(
        find.textContaining('/review', findRichText: true),
        findsOneWidget,
      );
      expect(
        find.textContaining('Runs with plan', findRichText: true),
        findsOneWidget,
      );
      expect(find.text('/ship'), findsOneWidget);

      await tester.tap(find.byKey(const ValueKey('command-server-review')));
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('command-submit')), findsOneWidget);
    });

    testWidgets('a long list searches, and a miss offers Clear search', (
      tester,
    ) async {
      final (controller, repository) = await _setup();
      repository.commands = [
        for (var i = 0; i < 10; i++)
          CommandInfo(name: 'cmd$i', description: 'Command $i', subtask: false),
      ];
      await _pump(tester, CommandsScreen(controller: controller));

      final search = find.byKey(const Key('command-launcher-search'));
      expect(search, findsOneWidget);
      await tester.enterText(search, 'zzz');
      await tester.pumpAndSettle(const Duration(seconds: 1));
      expect(
        find.byKey(const Key('command-launcher-no-match')),
        findsOneWidget,
      );
      await tester.tap(find.text('Clear search'));
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('command-server-cmd0')), findsOneWidget);
    });

    testWidgets('empty says where commands come from', (tester) async {
      final (controller, _) = await _setup();
      await _pump(tester, CommandsScreen(controller: controller));
      expect(find.text('No server commands found'), findsOneWidget);
      expect(
        find.text('Commands from your project and skills appear here.'),
        findsOneWidget,
      );
    });

    testWidgets('a failed first load explains itself and retries', (
      tester,
    ) async {
      final (controller, repository) = await _setup();
      repository.loadFailure = const ProductException('The server is away.');
      await _pump(tester, CommandsScreen(controller: controller));
      expect(find.text('Couldn’t load commands'), findsOneWidget);
      expect(find.text('The server is away.'), findsOneWidget);

      repository
        ..loadFailure = null
        ..commands = const [CommandInfo(name: 'review', subtask: false)];
      await tester.tap(find.text('Try again'));
      await tester.pumpAndSettle();
      expect(find.text('Couldn’t load commands'), findsNothing);
      expect(find.byKey(const Key('command-server-review')), findsOneWidget);
    });
  });

  group('References', () {
    const reference = ReferenceInfo(
      name: 'platform-docs',
      path: '/references/platform',
      description: 'Platform guidance',
    );

    testWidgets('say what a reference is and open its details', (tester) async {
      final (controller, repository) = await _setup();
      repository.references = const [reference];
      await _pump(tester, ReferencesScreen(controller: controller));

      expect(find.byKey(const ValueKey('references-intro')), findsOneWidget);
      expect(find.text('Platform guidance'), findsOneWidget);
      // The path is not on the row: it waits under the details.
      expect(find.textContaining('/references/platform'), findsNothing);

      await tester.tap(find.byKey(const ValueKey('reference-platform-docs')));
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('reference-sheet')), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('kit-details-toggle')));
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('reference-path')), findsOneWidget);

      await tester.tap(find.byKey(const ValueKey('reference-copy')));
      await tester.pumpAndSettle();
      expect(_copied, '@platform-docs');
      // No snackbar: the button itself says it copied.
      expect(find.byType(SnackBar), findsNothing);
    });

    testWidgets('from a conversation a tap adds the reference', (tester) async {
      final (controller, repository) = await _setup();
      repository.references = const [reference];
      ReferenceInfo? added;
      await _pump(
        tester,
        ReferencesScreen(
          controller: controller,
          onSelected: (value) => added = value,
        ),
      );
      await tester.tap(find.byKey(const ValueKey('reference-platform-docs')));
      await tester.pumpAndSettle();
      expect(added?.name, 'platform-docs');
      expect(find.text('Open'), findsOneWidget);
    });

    testWidgets('empty says what a reference is', (tester) async {
      final (controller, _) = await _setup();
      await _pump(tester, ReferencesScreen(controller: controller));
      expect(find.text('No references configured'), findsOneWidget);
      expect(find.textContaining('A reference is a folder'), findsOneWidget);
    });
  });

  group('Skills and the skill sheet', () {
    testWidgets('preview: no repeated heading, raw source, copy command', (
      tester,
    ) async {
      final (controller, repository) = await _setup();
      repository.skills = const [_skill];
      await _pump(tester, SkillsScreen(controller: controller));

      expect(find.text('Review a change for correctness.'), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('skill-focused-review')));
      await tester.pumpAndSettle();

      expect(find.byKey(const ValueKey('skill-sheet')), findsOneWidget);
      expect(find.byKey(const Key('skill-content-preview')), findsOneWidget);
      // The heading only repeated the sheet's title; the front matter is
      // the server's.
      expect(find.text('Focused review'), findsNothing);
      expect(find.textContaining('name: focused-review'), findsNothing);
      expect(find.text('Review the current change.'), findsOneWidget);
      expect(find.byKey(const ValueKey('skill-activate')), findsNothing);

      await tester.tap(find.text('Raw'));
      await tester.pumpAndSettle();
      expect(find.textContaining('| Gate | Command |'), findsOneWidget);

      await tester.tap(find.byKey(const ValueKey('skill-copy-command')));
      await tester.pumpAndSettle();
      expect(_copied, '/review-id');
    });

    testWidgets('from a conversation: add without running, then back', (
      tester,
    ) async {
      final (controller, repository) = await _setup();
      repository.skills = const [_skill];
      await _pump(
        tester,
        SkillsScreen(controller: controller, sessionID: 'ses_test'),
      );
      await tester.tap(find.byKey(const ValueKey('skill-focused-review')));
      await tester.pumpAndSettle();

      expect(find.text('Composer improvements'), findsOneWidget);
      expect(find.byKey(const Key('skill-activation-preview')), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('skill-resume')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('skill-activate')));
      await tester.pumpAndSettle();

      expect(repository.activations, [('ses_test', 'review-id', false)]);
      expect(find.text('Open'), findsOneWidget);
    });

    testWidgets('a failed add stays in the sheet and says why', (tester) async {
      final (controller, repository) = await _setup();
      repository
        ..skills = const [_skill]
        ..activationFailure = const SessionSkillException(
          SessionSkillFailure.uncertain,
        );
      await _pump(
        tester,
        SkillsScreen(controller: controller, sessionID: 'ses_test'),
      );
      await tester.tap(find.byKey(const ValueKey('skill-focused-review')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('skill-activate')));
      await tester.pumpAndSettle();

      expect(
        find.byKey(const ValueKey('skill-activation-error')),
        findsOneWidget,
      );
      expect(
        find.textContaining('The skill may have been added.'),
        findsOneWidget,
      );
      // Unknown result: no second send from this sheet, and it says why.
      await tester.tap(find.byKey(const ValueKey('skill-activate')));
      await tester.pumpAndSettle();
      expect(repository.activations.length, 1);
      expect(
        find.text('Check the conversation before trying again.'),
        findsWidgets,
      );
      expect(find.byKey(const ValueKey('skill-sheet')), findsOneWidget);
    });
  });
}
