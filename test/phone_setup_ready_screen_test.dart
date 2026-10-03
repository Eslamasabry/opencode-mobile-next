import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/builtin/builtin_folders.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/builtin_server.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/kit/scenes/setup_ready_scene.dart';
import 'package:opencode_mobile/ui/navigation/chat_route.dart';
import 'package:opencode_mobile/ui/screens/phone_setup/phone_setup_ready_screen.dart';
import 'package:opencode_mobile/ui/screens/phone_setup/phone_setup_routes.dart';
import 'package:opencode_mobile/ui/screens/project_folder_actions.dart';
import 'package:shared_preferences/shared_preferences.dart';

// Screen C (docs/design/phone-setup-v2-2026-09-24.md): one name makes the
// first project and lands in its first conversation, composer focused, with
// no Work screen in between.

class _Store extends ProfileStore {
  _Store({required super.prefs});

  final saved = <ServerProfile>[];
  String? selected;

  @override
  List<ServerProfile> get profiles => List.unmodifiable(saved);

  @override
  String? get activeId => selected;

  @override
  Future<void> setActiveId(String? id) async => selected = id;
}

class _Connection extends ConnectionController {
  _Connection(super.store);

  final locations = <String?>[];
  final connected = <String>[];
  var sessions = 0;
  String? refuseLocation;

  @override
  Future<void> connect(
    ServerProfile profile, {
    bool redetectOnFailure = true,
  }) async {
    connected.add(profile.id);
    api = OpenCodeApi(baseUrl: profile.baseUrl);
  }

  @override
  Future<void> selectLocation({String? directory, String? workspace}) async {
    locations.add(directory);
    locationError = refuseLocation;
  }

  @override
  Future<Session> createSession() async {
    sessions++;
    return Session(id: 'ses_first');
  }
}

/// Project folders in the in-app Ubuntu, as the scripts see them.
class _Linux extends BuiltinLinux {
  final projects = <String>[];
  final created = <String>[];
  String? failCreate;

  @override
  Future<BuiltinLinuxStatus> status() async => const BuiltinLinuxStatus(
    installed: true,
    phase: BuiltinLinuxPhase.ready,
    serverRunning: true,
  );

  @override
  Future<BuiltinLinuxRunResult> run(
    String script, {
    Duration timeout = const Duration(minutes: 2),
  }) async {
    if (script == BuiltinLinux.listProjectsScript()) {
      return BuiltinLinuxRunResult(
        exitCode: 0,
        output: projects.map((name) => '$name\n').join(),
      );
    }
    final create = RegExp(r"dir='([^']*)'").firstMatch(script);
    if (create != null) {
      if (failCreate != null) {
        return BuiltinLinuxRunResult(exitCode: 1, output: failCreate!);
      }
      created.add(create[1]!);
      return BuiltinLinuxRunResult(
        exitCode: 0,
        output: 'created ${create[1]}\n',
      );
    }
    return const BuiltinLinuxRunResult(exitCode: 127, output: 'unexpected');
  }
}

void main() {
  late _Store store;
  late _Connection connection;
  late _Linux linux;
  late List<RouteSettings> pushed;
  final phone = ServerProfile(
    id: 'phone',
    name: 'This phone, built-in (OpenCode 1)',
    baseUrl: BuiltinLinux.serverUrl,
    username: BuiltinLinux.serverUsername,
    password: 'secret',
    serverVersion: '1.18.29',
  );

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    store = _Store(prefs: await SharedPreferences.getInstance())
      ..saved.add(phone)
      ..selected = phone.id;
    connection = _Connection(store)..api = OpenCodeApi(baseUrl: phone.baseUrl);
    linux = _Linux();
    pushed = [];
    ProjectFolderActions.builtinLinuxOverride = linux;
    // Its fake Ubuntu has no files on disk: folders are listed through it.
    ProjectFolderActions.folderListerOverride = BuiltinFolders.throughUbuntu(
      linux,
    ).list;
  });

  tearDown(() {
    ProjectFolderActions.builtinLinuxOverride = null;
    ProjectFolderActions.folderListerOverride = null;
    connection.dispose();
  });

  Future<void> mount(
    WidgetTester tester, {
    Locale locale = const Locale('en'),
    Size size = const Size(400, 800),
    double textScale = 1,
    bool reduceMotion = true,
    Widget? home,
  }) async {
    tester.view
      ..physicalSize = size
      ..devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          bootstrapProvider.overrideWithValue(AppBootstrap(store)),
          connProvider.overrideWithValue(connection),
          builtinLinuxProvider.overrideWithValue(linux),
        ],
        child: MaterialApp(
          locale: locale,
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(context).copyWith(
              textScaler: TextScaler.linear(textScale),
              disableAnimations: reduceMotion,
            ),
            child: child!,
          ),
          routes: {'/home': (_) => const Scaffold(body: Text('App home'))},
          onGenerateRoute: (settings) {
            pushed.add(settings);
            if (settings.name!.startsWith('/chat/')) {
              return MaterialPageRoute<void>(
                settings: settings,
                builder: (_) => Scaffold(body: Text('Chat ${settings.name}')),
              );
            }
            return null;
          },
          home: home ?? const PhoneSetupReadyScreen(),
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  Finder field() => find.byKey(const ValueKey('phone-setup-ready-name'));

  Future<void> create(WidgetTester tester, [String? name]) async {
    if (name != null) await tester.enterText(field(), name);
    await tester.tap(find.byKey(const ValueKey('phone-setup-ready-create')));
    await tester.pumpAndSettle();
  }

  testWidgets('ready, with a suggested name', (tester) async {
    await mount(tester);
    expect(find.text('OpenCode is ready'), findsOneWidget);
    expect(find.text('Name your first project'), findsOneWidget);
    expect(find.text('Letters, numbers, - _ .'), findsOneWidget);
    expect(tester.widget<TextFormField>(field()).controller!.text, 'my-app');
    expect(find.text('Open a folder instead'), findsOneWidget);
    // The words the design rules out.
    for (final banned in ['Ubuntu', '127.0.0.1', 'built-in', 'server']) {
      expect(find.textContaining(banned), findsNothing, reason: banned);
    }
  });

  testWidgets('Create makes the project and opens a new conversation', (
    tester,
  ) async {
    await mount(tester);
    await create(tester);

    expect(linux.created, ['/root/projects/my-app']);
    expect(connection.locations, ['/root/projects/my-app']);
    expect(connection.sessions, 1);
    final chat = pushed.singleWhere((route) => route.name!.startsWith('/chat'));
    expect(chat.name, '/chat/ses_first');
    final arguments = chat.arguments! as ChatRouteArguments;
    expect(arguments.focusComposer, isTrue);
    expect(arguments.discardIfUntouched, isTrue);
    expect(find.text('Chat /chat/ses_first'), findsOneWidget);

    // Back from the conversation is the app, not setup or Work's picker.
    Navigator.of(tester.element(find.text('Chat /chat/ses_first'))).pop();
    await tester.pumpAndSettle();
    expect(find.text('App home'), findsOneWidget);
    expect(find.byType(PhoneSetupReadyScreen), findsNothing);
  });

  testWidgets('a typed name is used as the folder', (tester) async {
    await mount(tester);
    await create(tester, '  hello_world.2 ');
    expect(linux.created, ['/root/projects/hello_world.2']);
  });

  testWidgets('invalid names are refused inline', (tester) async {
    await mount(tester);
    for (final (name, message) in [
      ('', 'Enter a name.'),
      ('a/b', 'Use one name, without slashes.'),
      ('..', 'Use one name, without slashes.'),
      (
        '-dash',
        'Use letters, numbers, - _ or . and start with a letter or number (up to 64).',
      ),
      (
        'has space',
        'Use letters, numbers, - _ or . and start with a letter or number (up to 64).',
      ),
      (
        'x' * 65,
        'Use letters, numbers, - _ or . and start with a letter or number (up to 64).',
      ),
    ]) {
      await create(tester, name);
      expect(find.text(message), findsOneWidget, reason: name);
    }
    expect(linux.created, isEmpty);
    expect(connection.sessions, 0);

    // Editing clears the complaint.
    await tester.enterText(field(), 'ok');
    await tester.pumpAndSettle();
    expect(find.textContaining('Use letters'), findsNothing);
  });

  testWidgets('the name rule speaks Arabic', (tester) async {
    await mount(tester, locale: const Locale('ar'));
    await create(tester, 'a b');
    expect(
      find.text(
        'استخدم أحرفًا لاتينية وأرقامًا و - _ . وابدأ بحرف أو رقم (حتى 64).',
      ),
      findsOneWidget,
    );
  });

  testWidgets('a folder that cannot be made says why and stays', (
    tester,
  ) async {
    linux.failCreate = 'No space left on device';
    await mount(tester);
    await create(tester);
    // Plain words for the device's failure; its own text never shows.
    final l10n = lookupAppLocalizations(const Locale('en'));
    expect(
      find.text(
        'The project could not be created: ${l10n.productErrorStorage}',
      ),
      findsOneWidget,
    );
    expect(find.textContaining('No space left on device'), findsNothing);
    expect(connection.sessions, 0);
    expect(find.byType(PhoneSetupReadyScreen), findsOneWidget);
  });

  testWidgets('a folder the server refuses says why', (tester) async {
    connection.refuseLocation = 'Choose a valid project folder.';
    await mount(tester);
    await create(tester);
    expect(
      find.text(
        'The project could not be opened: Choose a valid project folder.',
      ),
      findsOneWidget,
    );
    expect(connection.sessions, 0);
  });

  testWidgets('a dropped connection is re-opened to the phone server', (
    tester,
  ) async {
    connection.api = null;
    await mount(tester);
    await create(tester);
    expect(connection.connected, ['phone']);
    expect(connection.sessions, 1);
  });

  testWidgets('Open an existing folder uses the in-app folder sheet', (
    tester,
  ) async {
    linux.projects.add('old-project');
    await mount(tester);
    await tester.tap(
      find.byKey(const ValueKey('phone-setup-ready-open-existing')),
    );
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('in-app-projects')), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('open-project-browse')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('in-app-project-old-project')));
    await tester.pumpAndSettle();

    expect(connection.locations, ['/root/projects/old-project']);
    // An existing project may have conversations: the app, not a new one.
    expect(connection.sessions, 0);
    expect(find.text('App home'), findsOneWidget);
    expect(linux.created, isEmpty);
  });

  testWidgets('the celebration plays once, and is still with reduced motion', (
    tester,
  ) async {
    // Loops allowed, as in the app: a resting screen must still settle.
    KitMotion.loops = true;
    addTearDown(() => KitMotion.loops = false);
    await mount(tester, reduceMotion: false);
    // pumpAndSettle returned: the one animation is over and nothing loops.
    expect(tester.hasRunningAnimations, isFalse);
    expect(
      find.byWidgetPredicate(
        (w) => w is KitIllustration && w.scene is SetupReadyScene,
      ),
      findsOneWidget,
    );
    await tester.pumpWidget(const SizedBox());

    await mount(tester);
    // Reduced motion: the finished drawing at once, nothing to wait for.
    expect(tester.hasRunningAnimations, isFalse);
  });

  testWidgets('the finish is celebrated: the longer entrance and one soft '
      'confirmation', (tester) async {
    final haptics = <Object?>[];
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      SystemChannels.platform,
      (call) async {
        if (call.method == 'HapticFeedback.vibrate') {
          haptics.add(call.arguments);
        }
        return null;
      },
    );
    addTearDown(
      () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        SystemChannels.platform,
        null,
      ),
    );
    final drawing = find.byWidgetPredicate(
      (w) => w is KitIllustration && w.scene is SetupReadyScene,
    );

    await mount(tester, reduceMotion: false);
    expect(
      tester.widget<KitIllustration>(drawing).entranceDuration,
      KitMotion.celebration,
    );
    // Once, as the screen arrives; typing or rebuilding does not repeat it.
    await tester.enterText(field(), 'other-name');
    await tester.pumpAndSettle();
    expect(haptics, ['HapticFeedbackType.successNotification']);
    await tester.pumpWidget(const SizedBox());

    // Reduced motion: the finished frame, and no haptic either.
    haptics.clear();
    await mount(tester);
    expect(haptics, isEmpty);
  });

  for (final locale in const [Locale('en'), Locale('ar')]) {
    testWidgets('lays out at 2.5x on 320dp (${locale.languageCode})', (
      tester,
    ) async {
      await mount(
        tester,
        locale: locale,
        size: const Size(320, 640),
        textScale: 2.5,
      );
      expect(tester.takeException(), isNull);
      await tester.scrollUntilVisible(
        field(),
        100,
        scrollable: find.byType(Scrollable).first,
      );
      if (locale.languageCode == 'ar') {
        expect(Directionality.of(tester.element(field())), TextDirection.rtl);
        // The folder name still reads left to right.
        expect(
          tester
              .widget<EditableText>(
                find.descendant(
                  of: field(),
                  matching: find.byType(EditableText),
                ),
              )
              .textDirection,
          TextDirection.ltr,
        );
      }
      final create = find.byKey(const ValueKey('phone-setup-ready-create'));
      await tester.scrollUntilVisible(
        create,
        100,
        scrollable: find.byType(Scrollable).first,
      );
      expect(tester.getSize(create).height, greaterThanOrEqualTo(48));
      final open = find.byKey(
        const ValueKey('phone-setup-ready-open-existing'),
      );
      await tester.scrollUntilVisible(
        open,
        100,
        scrollable: find.byType(Scrollable).first,
      );
      expect(tester.getSize(open).height, greaterThanOrEqualTo(48));
    });
  }

  testWidgets('openPhoneSetupReady takes the progress screen\'s place', (
    tester,
  ) async {
    await mount(
      tester,
      home: Builder(
        builder: (context) => Scaffold(
          body: TextButton(
            onPressed: () => Navigator.of(context).push(
              MaterialPageRoute<void>(
                builder: (context) => Scaffold(
                  body: TextButton(
                    onPressed: () => openPhoneSetupReady(context),
                    child: const Text('Progress'),
                  ),
                ),
              ),
            ),
            child: const Text('Start'),
          ),
        ),
      ),
    );
    await tester.tap(find.text('Start'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Progress'));
    await tester.pumpAndSettle();
    expect(find.byType(PhoneSetupReadyScreen), findsOneWidget);
    expect(find.text('Progress', skipOffstage: false), findsNothing);
  });
}
