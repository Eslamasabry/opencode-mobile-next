// Text scale and overflow (A11Y-2, docs/ux-system/revamp/STANDARDS.md §18):
//
// 1. The critical flows at AppTheme.maxTextScale (2.5) on a 360x740 phone.
// 2. Gate G6, the kit overflow matrix (A11Y-2, LAY-4, KIT-24; absolute):
//    every part lib/ui/kit/kit.dart exports is found by reading its exports,
//    and every scene of it in test/kit/kit_overflow_scenes.dart is pumped at
//    the LAY-4 overflow sizes, at text 1.0, 1.3 and 2.0, left to right and
//    right to left, with tester.takeException() null each time. A part with
//    no scene, or a scene naming a part the kit no longer exports, fails, and
//    so does any export, part or superclass the manifest cannot read.
//    KitSegmented, once it exists, must stack full-width KitChoiceRows, with
//    no track beside them, at text 2.0 on every phone size. STANDARDS calls
//    G6 absolute; until kit-KitRow-v2 fixes KitRowValue in a KitRow it is a
//    ratchet whose ceiling (kitOverflowCeiling) is fixed here and whose
//    committed baseline must equal what overflows and may only shrink. Kit
//    units add scenes there, never edit this file (PROC-13).
import 'support/complete_message_history.dart';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/api2/models.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit_choice_list.dart'
    show KitChoice, KitChoiceRow;
import 'package:opencode_mobile/ui/kit/kit_segmented.dart'
    show KitSegment, KitSegmented;
import 'package:opencode_mobile/ui/screens/chat/permission_sheet.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/screens/home_screen.dart';
import 'package:opencode_mobile/ui/screens/servers_screen.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:opencode_mobile/ui/widgets/form_renderer.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import 'kit/kit_overflow_matrix.dart';
import 'kit/kit_overflow_scenes.dart';

/// UX-P0-05 / audit rec #5: the global clamp no longer caps accessibility at
/// 2.0x, so the critical flows have to survive the new 2.5x ceiling. Each
/// case renders a flagship surface on a small phone at 2.5x and fails on any
/// layout exception — RenderFlex overflow included.
const _textScale = AppTheme.maxTextScale;

/// A 360x740 logical phone, the narrowest shape the product supports.
const _phone = Size(360, 740);

class _Api extends OpenCodeApi with CompleteMessageHistory {
  _Api() : super(baseUrl: 'http://localhost');

  @override
  Future<List<Session>> sessions() async => [];

  @override
  Future<Map<String, String>> sessionStatuses() async => const {};

  @override
  Future<List<MessageWithParts>> messages(String id) async => [];

  @override
  Future<Session> session(String id) async => Session(id: id);

  @override
  Future<List<FileNode>> listFiles([String path = '']) async => [];

  @override
  Future<Health> health() async => Health(healthy: true, version: '1.18.23');
}

class _Repository implements ProductRepository {
  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  Future<List<WorkspaceProject>> listProjects() async => [];

  @override
  Future<List<WorkspaceInfo>> listWorkspaces() async => [];

  @override
  Future<List<TerminalProcess>> listTerminals() async => [];

  @override
  Future<CatalogSnapshot> loadCatalog() async =>
      const CatalogSnapshot(providers: [], models: [], agents: []);

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

Future<ConnectionController> _controller({
  bool withProfile = true,
  bool withRepository = true,
}) async {
  SharedPreferences.setMockInitialValues({
    if (withProfile) ...{
      'oc.profiles': jsonEncode([
        {
          'id': 'profile-1',
          'name': 'Workstation on the LAN',
          'baseUrl': 'http://localhost:4096',
          'username': '',
        },
      ]),
      'oc.activeProfile': 'profile-1',
    },
  });
  final prefs = await SharedPreferences.getInstance();
  final store = ProfileStore(prefs: prefs);
  await store.load();
  final controller = ConnectionController(store)
    ..api = _Api()
    ..status = StreamStatus.connected;
  if (withRepository) controller.repository = _Repository();
  return controller;
}

/// Pumps [child] at 2.5x on a 360dp phone and returns once settled.
Future<void> _pumpScaled(WidgetTester tester, Widget child) async {
  tester.view.physicalSize = _phone * tester.view.devicePixelRatio;
  tester.view.devicePixelRatio = 1;
  tester.view.physicalSize = _phone;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    MediaQuery(
      data: const MediaQueryData(
        size: _phone,
        textScaler: TextScaler.linear(_textScale),
      ),
      child: child,
    ),
  );
  // Localization delegates resolve asynchronously and several screens load
  // through a future, so give the tree a handful of frames to reach its
  // steady state before the layout is judged.
  for (var i = 0; i < 6; i++) {
    await tester.pump(const Duration(milliseconds: 120));
  }
}

Widget _app(Widget home) => MaterialApp(
  theme: AppTheme.light(),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home: home,
);

Widget _scoped(ConnectionController conn, Widget home) => ProviderScope(
  overrides: [
    connProvider.overrideWithValue(conn),
    bootstrapProvider.overrideWithValue(AppBootstrap(conn.store)),
  ],
  child: _app(home),
);

// ---------------------------------------------------------------------------
// Gate G6: the kit overflow matrix.

const _kitDir = 'lib/ui/kit';

/// Superclasses that make a kit class a part, besides names ending in
/// `Widget` and other kit parts.
const _widgetBases = {'InheritedNotifier', 'InheritedModel', 'InheritedTheme'};

/// Exported classes that extend a class outside the scanned files and are
/// not widgets. Any other exported class whose superclass chain leaves the
/// scanned files at something that is not a widget fails the manifest, so a
/// part that extends `ListTile`, `Builder` or `ValueListenableBuilder` cannot
/// quietly drop out of the matrix. A class with no `extends` is an `Object`
/// and never a widget.
const _nonWidgetClasses = {
  'KitTokens': 'ThemeExtension',
  'KitPageTransitionsBuilder': 'PageTransitionsBuilder',
  // A route lays out its caller's page, not a kit part.
  'KitPageRoute': 'PageRoute',
  'KitZoomController': 'ChangeNotifier',
  // Input, scrolling and log models have no layout of their own.
  'KitNumberFormatter': 'TextInputFormatter',
  'KitScrollBehavior': 'MaterialScrollBehavior',
  'KitLogBuffer': 'ValueNotifier',
};

/// Every `export` and `part` directive in a scanned file; each one must match
/// the strict pattern below it, or the manifest reports it.
final _exportDirective = RegExp(r'^[ \t]*export\b[^;]*;', multiLine: true);
final _exportPattern = RegExp(
  r"^export\s+'([^']+)'(?:\s+(show|hide)\s+([\w\s,]+))?;$",
);
final _partDirective = RegExp(
  r'^[ \t]*part\b(?!\s+of\b)[^;]*;',
  multiLine: true,
);
final _partPattern = RegExp(r"^part\s+'([^']+)';$");
final _classDeclaration = RegExp(
  r'^(?:(?:abstract|final|base|sealed|interface|mixin)\s+)*class\s+([A-Z]\w*)',
  multiLine: true,
);
final _classPattern = RegExp(
  r'^(?:(?:abstract|final|base|sealed|interface|mixin)\s+)*class\s+'
  r'([A-Z]\w*)(?:<[^{]*?>)?\s+extends\s+(\w+)',
  multiLine: true,
);

/// A top-level function named `showKit…`, whatever it returns.
final _showKitPattern = RegExp(
  r'^(?![\s/@])[^\n=;{}]*?\b(showKit\w+)\s*(?:<[^>(]*>)?\s*\(',
  multiLine: true,
);

/// The kit's parts, read from `kit.dart`'s exports, and every directive or
/// class the reader could not account for.
typedef KitManifest = ({Set<String> parts, List<String> problems});

/// Reads the parts from [kitFile]'s exports (following `part` files and
/// re-exports, honouring `show`): every public widget class and every
/// `showKit…` function. It never drops a file or a class silently: an export
/// or part it cannot parse, an export it does not follow, and an exported
/// class whose superclass it cannot classify all land in `problems`.
KitManifest readKitManifest({String kitFile = '$_kitDir/kit.dart'}) {
  final supers = <String, String>{};
  final declared = <String>{};
  final functions = <String>{};
  final problems = <String>[];

  String shown(File file) {
    final path = file.absolute.path;
    final root = Directory.current.absolute.path;
    return path.startsWith('$root/') ? path.substring(root.length + 1) : path;
  }

  File resolve(File from, String uri) {
    const self = 'package:opencode_mobile/';
    if (uri.startsWith(self)) return File('lib/${uri.substring(self.length)}');
    return File.fromUri(from.absolute.uri.resolve(uri));
  }

  // Returns the public names [file] contributes, filtered by [show].
  Set<String> scan(File file, Set<String>? show, Set<String> seen) {
    final path = file.absolute.uri.normalizePath().toFilePath();
    // Keyed by the `show` too: `export 'kit_menu.dart' show KitMenuItem;`
    // in one file must not hide a full `export 'kit_menu.dart';` elsewhere.
    if (!seen.add('$path|${(show?.toList()?..sort())?.join(',')}')) return {};
    final source = file.readAsStringSync();
    final texts = [source];
    for (final directive in _partDirective.allMatches(source)) {
      final text = directive[0]!.trim();
      final m = _partPattern.firstMatch(text);
      if (m == null) {
        problems.add(
          '${shown(file)}: the manifest cannot read `$text` '
          "(write part '<file>';)",
        );
        continue;
      }
      texts.add(resolve(file, m[1]!).readAsStringSync());
    }
    final names = <String>{};
    for (final text in texts) {
      for (final m in _classDeclaration.allMatches(text)) {
        declared.add(m[1]!);
        names.add(m[1]!);
      }
      for (final m in _classPattern.allMatches(text)) {
        supers[m[1]!] = m[2]!;
      }
      for (final m in _showKitPattern.allMatches(text)) {
        functions.add(m[1]!);
        names.add(m[1]!);
      }
    }
    for (final directive in _exportDirective.allMatches(source)) {
      final text = directive[0]!.trim();
      final m = _exportPattern.firstMatch(text);
      if (m == null) {
        problems.add(
          '${shown(file)}: the manifest cannot read `$text` '
          "(write export '<file>'; or export '<file>' show|hide A, B;)",
        );
        continue;
      }
      final uri = m[1]!;
      if (uri.startsWith('dart:') ||
          (uri.startsWith('package:') &&
              !uri.startsWith('package:opencode_mobile/'))) {
        problems.add(
          '${shown(file)}: exports $uri, which the manifest does not follow',
        );
        continue;
      }
      final listed = m[3]?.split(',').map((n) => n.trim()).toSet();
      final hidden = m[2] == 'hide' ? listed! : const <String>{};
      names.addAll(
        scan(
          resolve(file, uri),
          m[2] == 'show' ? listed : null,
          seen,
        ).difference(hidden),
      );
    }
    return show == null ? names : names.intersection(show);
  }

  final exported = scan(File(kitFile), null, {});

  // The superclass chain of [name] inside the scanned files, then the first
  // superclass outside them (null when the chain ends at Object).
  (List<String>, String?) chain(String name) {
    final seen = <String>[name];
    var base = supers[name];
    while (base != null && declared.contains(base) && !seen.contains(base)) {
      seen.add(base);
      base = supers[base];
    }
    return (seen, base);
  }

  bool isWidgetBase(String base) =>
      base.endsWith('Widget') || _widgetBases.contains(base);

  final parts = <String>{};
  for (final name in exported) {
    if (functions.contains(name)) {
      parts.add(name);
      continue;
    }
    final (classes, outside) = chain(name);
    if (outside == null) continue;
    if (isWidgetBase(outside)) {
      parts.add(name);
      continue;
    }
    // Class names alone are not an exemption: a changed or unknown base
    // must fail, and a known utility turned into a widget was included above.
    if (classes.any((type) => _nonWidgetClasses[type] == outside)) continue;
    problems.add(
      '$name extends ${classes.length > 1 ? '${classes.skip(1).join(' → ')} → ' : ''}'
      '$outside, which is neither a widget, a kit class nor a known '
      'non-widget: the manifest cannot tell whether it is a part',
    );
  }
  return (parts: parts, problems: problems);
}

// ---------------------------------------------------------------------------
// The overflow ratchet.

// Stand-ins for the KIT-24 self-test: a segmented part that stacks its rows,
// keeps its track beside them, or stacks rows narrower than itself.
enum _FakeLayout { stack, track, narrow }

class _FakeSegmented extends StatelessWidget {
  const _FakeSegmented(this.layout);

  final _FakeLayout layout;

  static const _labels = ['Only this session', 'Every session on the server'];

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: [
      if (layout == _FakeLayout.track)
        Row(
          children: [
            for (final label in _labels)
              Expanded(
                child: Text(
                  label,
                  softWrap: false,
                  overflow: TextOverflow.clip,
                ),
              ),
          ],
        ),
      for (final label in _labels)
        if (layout == _FakeLayout.narrow)
          Align(
            alignment: AlignmentDirectional.centerStart,
            child: SizedBox(width: 120, child: _FakeChoiceRow(label)),
          )
        else
          _FakeChoiceRow(label),
    ],
  );
}

class _FakeChoiceRow extends StatelessWidget {
  const _FakeChoiceRow(this.label);

  final String label;

  @override
  Widget build(BuildContext context) =>
      Padding(padding: const EdgeInsets.all(12), child: Text(label));
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    // ProfileStore reads passwords through flutter_secure_storage, whose
    // unmocked channel never answers inside testWidgets.
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });

  testWidgets('chat transcript and composer lay out at 2.5x', (tester) async {
    final conn = await _controller(withRepository: false);
    addTearDown(conn.dispose);
    await _pumpScaled(
      tester,
      _scoped(conn, const ChatScreen(sessionID: 'session-1')),
    );

    expect(tester.takeException(), isNull);
    expect(find.byKey(const Key('chat-composer-field')), findsOneWidget);
  });

  testWidgets('the busy composer lays out at 2.5x', (tester) async {
    final conn = await _controller(withRepository: false);
    addTearDown(conn.dispose);
    conn.busySessions = {'session-1'};
    await _pumpScaled(
      tester,
      _scoped(conn, const ChatScreen(sessionID: 'session-1')),
    );

    expect(tester.takeException(), isNull);
    expect(find.byKey(const Key('chat-composer-field')), findsOneWidget);
  });

  testWidgets('the home shell lays out at 2.5x', (tester) async {
    final conn = await _controller();
    addTearDown(conn.dispose);
    final layoutErrors = <FlutterErrorDetails>[];
    final reportError = FlutterError.onError;
    FlutterError.onError = (details) {
      layoutErrors.add(details);
      reportError?.call(details);
    };
    try {
      await _pumpScaled(tester, _scoped(conn, const HomeScreen()));
    } finally {
      FlutterError.onError = reportError;
    }

    expect(
      tester.takeException(),
      isNull,
      reason: layoutErrors.map((details) => details.toString()).join('\n'),
    );
  });

  testWidgets('the Settings tab lays out at 2.5x', (tester) async {
    final conn = await _controller();
    addTearDown(conn.dispose);
    await _pumpScaled(
      tester,
      _scoped(
        conn,
        Scaffold(body: SettingsScreen(controller: conn, embedded: true)),
      ),
    );

    expect(tester.takeException(), isNull);
  });

  testWidgets('settings lays out at 2.5x', (tester) async {
    final conn = await _controller();
    addTearDown(conn.dispose);
    await _pumpScaled(tester, _scoped(conn, SettingsScreen(controller: conn)));

    expect(tester.takeException(), isNull);
  });

  testWidgets('the servers first-run screen lays out at 2.5x', (tester) async {
    final conn = await _controller(withProfile: false);
    addTearDown(conn.dispose);
    await _pumpScaled(tester, _scoped(conn, const ServersScreen()));

    expect(tester.takeException(), isNull);
  });

  testWidgets('the permission sheet lays out at 2.5x', (tester) async {
    await _pumpScaled(
      tester,
      _app(
        Scaffold(
          body: PermissionSheet(
            permission: PermissionRequest(
              id: 'per_1',
              sessionID: 'ses_1',
              permission: 'bash',
              patterns: const [
                'git push origin main --force-with-lease --no-verify',
              ],
              always: const [],
              message: 'Pushing the release branch to the shared remote',
              tool: null,
            ),
            onReply: (reply, {String? message}) async {},
            supportsRejectMessage: true,
          ),
        ),
      ),
    );

    // The retired wrapper draws the one request card (chat-5); Details
    // opens the request sheet, which must lay out at 2.5x too.
    expect(find.byKey(const Key('permission-card-allow')), findsOneWidget);
    expect(tester.takeException(), isNull);
    final details = find.byKey(const Key('permission-card-review'));
    await tester.ensureVisible(details);
    await tester.pumpAndSettle();
    await tester.tap(details);
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('permission-sheet')), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('the form renderer lays out at 2.5x', (tester) async {
    await _pumpScaled(
      tester,
      _app(
        Scaffold(
          body: FormRenderer(
            form: Api2FormInfo(
              id: 'frm_1',
              sessionID: 'ses_1',
              title: 'Connect this workspace to Sentry',
              fields: [
                Api2FormField(
                  key: 'org',
                  type: Api2FormFieldType.string,
                  title: 'Organization slug',
                  description:
                      'The slug shown in your Sentry organization settings.',
                  required: true,
                ),
                Api2FormField(
                  key: 'retention',
                  type: Api2FormFieldType.integer,
                  title: 'Retention in days',
                  required: true,
                ),
                Api2FormField(
                  key: 'env',
                  type: Api2FormFieldType.multiselect,
                  title: 'Environments to watch',
                  options: [
                    Api2FormOption(value: 'prod', label: 'Production'),
                    Api2FormOption(value: 'stage', label: 'Staging'),
                  ],
                ),
                Api2FormField(
                  key: 'notify',
                  type: Api2FormFieldType.boolean,
                  title: 'Notify this session on new issues',
                ),
              ],
            ),
            onSubmit: (_) async {},
            onCancel: () async {},
          ),
        ),
      ),
    );

    expect(find.byKey(const Key('form-submit')), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
  group('G6 kit overflow matrix', () {
    setUpAll(loadKitGalleryFonts);

    test('every exported kit part has a scene, and every scene a part', () {
      final manifest = readKitManifest();
      expect(
        manifest.problems,
        isEmpty,
        reason:
            'The manifest could not read every export, part and class of '
            '$_kitDir/kit.dart, so parts may be missing from the matrix',
      );
      final parts = manifest.parts;
      final covered = {for (final s in kitOverflowScenes) ...s.parts};
      expect(parts, isNotEmpty);
      final missing = parts.difference(covered).toList()..sort();
      final stale = covered.difference(parts).toList()..sort();
      expect(
        missing,
        isEmpty,
        reason:
            'Kit parts with no scene in test/kit/kit_overflow_scenes.dart '
            '(add one per declared state at the end of kitOverflowScenes): '
            '${missing.join(', ')}',
      );
      expect(
        stale,
        isEmpty,
        reason: 'Scenes naming parts kit.dart no longer exports',
      );
      // KIT-24's stacked form needs kit-KitChoiceList's KitChoiceRow; the
      // check turns itself on when that part lands in the kit.
      final choiceRowLanded = Directory(_kitDir)
          .listSync(recursive: true)
          .whereType<File>()
          .where((f) => f.path.endsWith('.dart'))
          .any(
            (f) => RegExp(
              r'^class\s+KitChoiceRow\b',
              multiLine: true,
            ).hasMatch(f.readAsStringSync()),
          );
      if (parts.contains('KitSegmented') && choiceRowLanded) {
        expect(
          kitOverflowScenes.any(
            (s) => s.parts.contains('KitSegmented') && s.labelsOverflow,
          ),
          isTrue,
          reason:
              'KIT-24: KitSegmented needs a labelsOverflow scene proving it '
              'stacks KitChoiceRows when its labels do not fit',
        );
      }
    });

    test('non-widget utilities require their expected superclass', () {
      final dir = Directory.systemTemp.createTempSync('g6_utilities');
      addTearDown(() => dir.deleteSync(recursive: true));
      final kitFile = File('${dir.path}/kit.dart');
      void writeBases(String Function(String) baseOf) {
        kitFile.writeAsStringSync(
          [
            for (final entry in _nonWidgetClasses.entries)
              'class ${entry.key} extends ${baseOf(entry.value)} {}',
          ].join('\n'),
        );
      }

      writeBases((base) => base);
      final utilities = readKitManifest(kitFile: kitFile.path);
      expect(utilities.parts, isEmpty);
      expect(utilities.problems, isEmpty);

      writeBases((_) => 'UnrecognisedBase');
      final changedBases = readKitManifest(kitFile: kitFile.path);
      expect(changedBases.problems, hasLength(_nonWidgetClasses.length));
      for (final name in _nonWidgetClasses.keys) {
        expect(
          changedBases.problems,
          contains(contains('$name extends UnrecognisedBase')),
        );
      }

      writeBases((_) => 'StatelessWidget');
      final widgets = readKitManifest(kitFile: kitFile.path);
      expect(widgets.parts, _nonWidgetClasses.keys.toSet());
      expect(widgets.problems, isEmpty);
    });

    test('the manifest fails loudly on what it cannot read', () {
      final dir = Directory.systemTemp.createTempSync('g6_manifest');
      addTearDown(() => dir.deleteSync(recursive: true));
      void write(String name, String text) =>
          File('${dir.path}/$name').writeAsStringSync(text);
      write('kit.dart', '''
export 'fine.dart';
export 'utilities.dart';
export 'hidden.dart' hide KitHidden;
export "quoted.dart";
export 'io.dart' if (dart.library.html) 'web.dart';
export 'package:flutter/widgets.dart';
''');
      write('fine.dart', '''
part "fine_part.dart";
class KitFine extends StatelessWidget {}
class KitTile extends ListTile {}
class KitListens extends ValueListenableBuilder<int> {}
class KitData {}
KitHandle showKitThing(BuildContext context) => KitHandle();
''');
      write('hidden.dart', '''
class KitHidden extends StatelessWidget {}
class KitShown extends StatelessWidget {}
''');
      File('${dir.path}/utilities.dart').writeAsStringSync('''
class KitScrollBehavior extends MaterialScrollBehavior {}
class KitNumberFormatter extends TextInputFormatter {}
class KitLogBuffer extends ValueNotifier<List<String>> {}
class KitUtilityView extends StatelessWidget {}
''');
      final manifest = readKitManifest(kitFile: '${dir.path}/kit.dart');
      // `hide` is read (the hidden class drops out), unlike the forms below.
      expect(manifest.parts, {
        'KitFine',
        'showKitThing',
        'KitShown',
        'KitUtilityView',
      });
      expect(
        manifest.problems,
        unorderedEquals([
          contains('`part "fine_part.dart";`'),
          contains('`export "quoted.dart";`'),
          contains("`export 'io.dart' if (dart.library.html) 'web.dart';`"),
          contains('exports package:flutter/widgets.dart'),
          contains('KitTile extends ListTile'),
          contains('KitListens extends ValueListenableBuilder'),
        ]),
      );
    });

    testWidgets(
      'the KIT-24 check passes a stack and fails a track, narrow rows or no '
      'part',
      (tester) async {
        // Both real parts are generic. Comparing runtimeType strings with
        // the bare class names silently misses KitSegmented<String> and
        // KitChoiceRow<int>, so KIT-24 never inspects their actual layout.
        final segmented = KitSegmented<String>(
          segments: const [
            KitSegment(value: 'one', label: 'One'),
            KitSegment(value: 'two', label: 'Two'),
          ],
          selected: 'one',
          onChanged: (_) {},
          semanticsLabel: 'Scope',
        );
        final choice = KitChoiceRow<int>(
          choice: const KitChoice(value: 1, title: 'One'),
          selected: true,
          onTap: () {},
        );
        expect(isKitSegmented(segmented), isTrue);
        expect(isKitChoiceRow(choice), isTrue);
        expect(isKitSegmented(choice), isFalse);
        expect(isKitChoiceRow(segmented), isFalse);

        tester.view.devicePixelRatio = 1;
        tester.view.physicalSize = const Size(360, 740);
        addTearDown(tester.view.reset);
        Future<String?> check(Widget child) async {
          await tester.pumpWidget(
            kitOverflowApp(
              key: UniqueKey(),
              scale: 2.0,
              rtl: false,
              home: (_) => ListView(
                padding: const EdgeInsets.all(16),
                children: [child],
              ),
            ),
          );
          await tester.pump();
          expect(tester.takeException(), isNull);
          return kit24Problem(
            isSegmented: (w) => w is _FakeSegmented,
            isChoiceRow: (w) => w is _FakeChoiceRow,
          );
        }

        expect(await check(const _FakeSegmented(_FakeLayout.stack)), isNull);
        expect(
          await check(const _FakeSegmented(_FakeLayout.track)),
          contains('outside its KitChoiceRows'),
        );
        expect(
          await check(const _FakeSegmented(_FakeLayout.narrow)),
          contains('full-width rows'),
        );
        expect(
          await check(const SizedBox()),
          contains('no KitSegmented is shown'),
        );
      },
    );

    final baseline = loadKitOverflowBaseline();

    test('the overflow baseline exists and stays under the ceiling', () {
      expect(
        baseline,
        isNotNull,
        reason:
            '$kitOverflowBaselinePath is missing. It is never recreated from what '
            'overflows today: restore it from git',
      );
      final ids = {for (final s in kitOverflowScenes) s.id};
      expect(ids.length, kitOverflowScenes.length, reason: 'duplicate ids');
      expect(baseline!.keys.toSet().difference(ids), isEmpty);
      final raised = [
        for (final MapEntry(:key, :value) in baseline.entries)
          for (final entry in compareKitOverflows(
            value,
            kitOverflowCeiling[key] ?? const {},
          ).worse)
            '$key $entry',
      ];
      expect(
        raised,
        isEmpty,
        reason:
            '$kitOverflowBaselinePath may only shrink: these entries are not in the '
            'ceiling (kitOverflowCeiling in test/text_scale_overflow_test.dart), '
            'or are larger or different there. Fix the overflow instead',
      );
    });

    // The scenes themselves are pumped by the shard files
    // (test/text_scale_overflow_matrix_<n>_test.dart, kitOverflowShards of
    // them), which run side by side; each takes the scenes whose index leaves
    // its number modulo kitOverflowShards, so every scene is pumped once.
    test('the matrix shards cover every scene exactly once', () {
      final dir = Directory('test');
      final shards = dir
          .listSync()
          .whereType<File>()
          .where(
            (f) => RegExp(
              r'text_scale_overflow_matrix_\d+_test\.dart$',
            ).hasMatch(f.path),
          )
          .toList();
      expect(shards.length, kitOverflowShards);
      for (var i = 0; i < kitOverflowShards; i++) {
        final file = File('test/text_scale_overflow_matrix_${i}_test.dart');
        expect(file.existsSync(), isTrue, reason: '$file is missing');
        expect(
          file.readAsStringSync(),
          contains('registerKitOverflowShard($i)'),
        );
      }
    });
  });
}
