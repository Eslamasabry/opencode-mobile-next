// Census scenes for the ledger part `a-shell`
// (docs/design/ui-ledger/parts/a-shell.json). See tool/capture/census_test.dart.
//
// The share and session-link intents take a test channel, the seam their own
// tests use; the census is a test binary.
// ignore_for_file: invalid_use_of_visible_for_testing_member
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/diagnostics/app_diagnostics.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/server_gateway.dart'
    show CommandInfo, StreamStatus;
import 'package:opencode_mobile/domain/session_handoff.dart' show SessionLink;
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/main.dart';
import 'package:opencode_mobile/platform/session_link.dart';
import 'package:opencode_mobile/platform/share_intent.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_iconography.dart';
import 'package:opencode_mobile/ui/desktop/context_menu.dart';
import 'package:opencode_mobile/ui/desktop/shortcuts.dart';
import 'package:opencode_mobile/ui/kit/kit.dart'
    show KitAction, KitConfirmKind, KitStateView, showKitConfirm;
import 'package:opencode_mobile/ui/screens/capabilities_screen.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/screens/demo_screen.dart';
import 'package:opencode_mobile/ui/screens/home_screen.dart';
import 'package:opencode_mobile/ui/widgets/external_link.dart';
import 'package:opencode_mobile/ui/widgets/saved_server_connection_card.dart';
import 'package:opencode_mobile/ui/widgets/server_switcher_sheet.dart';
import 'package:opencode_mobile/update/desktop_release_check.dart';
import 'package:opencode_mobile/update/shorebird_update_notice.dart';

import '../../fixtures.dart';
import '../census_core.dart';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

Future<CaptureController> _home(
  CensusKit kit, {
  bool needsYou = false,
  StreamStatus? status,
  int? tab,
}) async {
  final controller = await kit.connected();
  if (needsYou) {
    controller.permissions = {samplePermission().id: samplePermission()};
  }
  if (status != null) {
    controller
      ..status = status
      ..lastError =
          'Cannot reach http://192.168.1.20:4096: Connection refused '
          '(errno = 111)';
  }
  await kit.pumpApp(HomeScreen(initialTab: tab), controller: controller);
  return controller;
}

const _refused =
    'Cannot reach http://127.0.0.1:4096: Connection refused (errno = 111)';

Widget _card({String? error, bool starting = false}) => Scaffold(
  body: SafeArea(
    child: SavedServerConnectionCard(
      profileName: 'This phone · Termux',
      baseUrl: 'http://127.0.0.1:4096',
      error: error,
      attempts: 1,
      supportsTermux: true,
      onChangeServer: () {},
      onRetry: () {},
      onOpenTermuxSetup: () {},
      onStartPhoneServer: () {},
      startingPhoneServer: starting,
    ),
  ),
);

/// A check that answers with [state] and never downloads.
class _UpdateService implements AppUpdateService {
  _UpdateService(this.state);
  final AppUpdateState state;
  final hold = Completer<void>();

  @override
  bool get isAvailable => true;

  @override
  Future<AppUpdateState> checkForUpdate() async => state;

  @override
  Future<void> downloadUpdate() => hold.future;
}

class _NoUpdates implements AppUpdateService {
  @override
  bool get isAvailable => false;
  @override
  Future<AppUpdateState> checkForUpdate() async => AppUpdateState.unavailable;
  @override
  Future<void> downloadUpdate() async {}
}

class _ReleaseChecker extends DesktopReleaseChecker {
  @override
  Future<DesktopReleaseInfo?> fetchLatest() async => const DesktopReleaseInfo(
    tag: 'v1.0.45+53',
    htmlUrl: 'https://github.com/example/opencode-mobile/releases/tag/v1.0.45',
  );
}

/// The app shell with its own ScaffoldMessenger key, for the notices that
/// post snackbars through it.
Future<void> _pumpWithMessenger(
  CensusKit kit,
  CaptureController controller,
  Widget Function(GlobalKey<ScaffoldMessengerState> messenger) home,
) async {
  final messenger = GlobalKey<ScaffoldMessengerState>();
  await kit.pumpRaw(
    ProviderScope(
      overrides: [
        bootstrapProvider.overrideWithValue(AppBootstrap(controller.store)),
        connProvider.overrideWithValue(controller),
      ],
      child: MaterialApp(
        debugShowCheckedModeBanner: false,
        theme: captureTheme(),
        scaffoldMessengerKey: messenger,
        navigatorKey: kit.navigatorKey,
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: home(messenger),
      ),
    ),
  );
}

/// The real app (main.dart's OcApp) over the capture controller, dark, with
/// injectable share and session-link intents.
Future<void> _pumpOcApp(
  CensusKit kit,
  CaptureController controller, {
  ShareIntent? share,
  SessionLinkIntent? link,
}) async {
  controller.appearance.value = AppAppearance.dark;
  await kit.pumpRaw(
    ProviderScope(
      overrides: [
        bootstrapProvider.overrideWithValue(AppBootstrap(controller.store)),
        connProvider.overrideWithValue(controller),
      ],
      child: OcApp(
        updateService: _NoUpdates(),
        shareIntent: share,
        sessionLinkIntent: link,
      ),
    ),
  );
}

/// A server with a few project commands.
class _CommandsRepository extends CaptureRepository {
  @override
  Future<List<CommandInfo>> listCommands() async => const [
    CommandInfo(
      name: 'review',
      description: 'Review the current diff for bugs and style',
      subtask: false,
    ),
    CommandInfo(
      name: 'test',
      description: 'Run the test suite and fix what fails',
      agent: 'build',
      subtask: true,
    ),
    CommandInfo(
      name: 'release-notes',
      description: 'Draft release notes from the commits since the last tag',
      subtask: false,
    ),
    CommandInfo(name: 'init', description: 'Write AGENTS.md', subtask: false),
  ];
}

/// Creating a session fails, so shared text cannot open one.
class _NoSessionApi extends CaptureApi {
  @override
  Future<Session> createSession() async =>
      throw ApiException('Cannot reach http://192.168.1.20:4096: timed out');
}

// ---------------------------------------------------------------------------
// Shots
// ---------------------------------------------------------------------------

final aShellArea = CensusArea(
  'a-shell',
  shots: [
    // -- start-up ------------------------------------------------------------
    CensusShot('bootstrap-gate', state: 'starting', (kit) async {
      final hold = Completer<AppBootstrap>();
      kit.onDispose(() {
        if (!hold.isCompleted) hold.completeError(StateError('done'));
      });
      await kit.pumpRaw(
        AppBootstrapGate(
          diagnostics: AppDiagnosticsController(),
          loader: () => hold.future,
        ),
      );
      kit.expectVisible(find.byType(AppBootstrapGate));
    }),
    CensusShot('bootstrap-gate', state: 'failed', (kit) async {
      await kit.pumpRaw(
        AppBootstrapGate(
          diagnostics: AppDiagnosticsController(),
          loader: () async =>
              throw const FileSystemExceptionLike('Secure storage is locked'),
        ),
      );
      kit.expectVisible(find.byKey(const ValueKey('retry-app-bootstrap')));
    }),
    CensusShot(
      'root-connecting',
      state: 'connecting',
      (kit) async {
        final controller = await kit.connected();
        controller.status = StreamStatus.connecting;
        await kit.pumpApp(_card(), controller: controller);
      },
      note: 'SavedServerConnectionCard as _Root shows it (main.dart)',
    ),
    CensusShot('root-connecting', state: 'not-answering', (kit) async {
      final controller = await kit.connected();
      controller.status = StreamStatus.connecting;
      await kit.pumpApp(_card(), controller: controller);
      await kit.settle(const Duration(seconds: 10));
    }, note: 'after 8 s without an answer'),
    CensusShot('root-connecting', state: 'stopped', (kit) async {
      final controller = await kit.connected();
      controller.status = StreamStatus.disconnected;
      await kit.pumpApp(_card(error: _refused), controller: controller);
    }),
    CensusShot('root-connecting', state: 'failed-remote', (kit) async {
      final controller = await kit.connected();
      controller.status = StreamStatus.disconnected;
      await kit.pumpApp(
        Scaffold(
          body: SafeArea(
            child: SavedServerConnectionCard(
              profileName: 'Laptop',
              baseUrl: 'http://100.64.0.7:4096',
              error: 'Cannot reach http://100.64.0.7:4096: timed out',
              attempts: 2,
              supportsTermux: true,
              onChangeServer: () {},
              onRetry: () {},
            ),
          ),
        ),
        controller: controller,
      );
    }),

    // -- the shell -----------------------------------------------------------
    CensusShot('home-shell', state: 'work', (kit) async {
      await _home(kit);
      kit.expectText('Fix flaky checkout test');
    }),
    CensusShot('home-shell', state: 'needs-you', (kit) async {
      await _home(kit, needsYou: true);
    }),
    CensusShot(
      'home-shell',
      state: 'reconnecting',
      (kit) async {
        await _home(kit, status: StreamStatus.reconnecting);
        // Work says it in its own status line once reconnecting has taken a
        // while.
        await kit.settle(const Duration(seconds: 15));
      },
      note: 'Work tab after 15 s of reconnecting',
    ),
    CensusShot(
      'embedded-connection-status-banner',
      state: 'inbox',
      (kit) async {
        await _home(kit, status: StreamStatus.reconnecting, tab: 1);
        kit.expectVisible(
          find.byKey(const ValueKey('connection-status-banner')),
        );
      },
      note: 'the shell banner over the Inbox tab (Work uses its own line)',
    ),
    CensusShot(
      'embedded-connection-status-banner',
      state: 'lost',
      (kit) async {
        await _home(kit, status: StreamStatus.disconnected, tab: 1);
        kit.expectVisible(
          find.byKey(const ValueKey('connection-status-banner')),
        );
      },
      note: 'the shell banner over the Inbox tab once the connection is lost',
    ),
    CensusShot(
      'embedded-connection-status-banner',
      state: 'chat',
      (kit) async {
        final api = CaptureApi()
          ..messagesHandler = (_) async => sampleTranscript();
        final controller = await kit.connected(api: api);
        controller
          ..status = StreamStatus.disconnected
          ..lastError =
              'Cannot reach http://192.168.1.20:4096: Connection refused';
        await kit.pumpApp(
          const ChatScreen(sessionID: checkoutSessionID),
          controller: controller,
        );
        kit.expectVisible(
          find.byKey(const ValueKey('connection-status-banner')),
        );
      },
      note: "the conversation's own connection line (chat_states.dart)",
    ),
    CensusShot('connection-status-details-sheet', (kit) async {
      await _home(kit, status: StreamStatus.reconnecting, tab: 1);
      await kit.tapKey('kit-status-more');
      await kit.tapKey('connection-banner-details');
      kit.expectText('Change server');
    }),
    CensusShot('server-switcher-sheet', (kit) async {
      final controller = await _home(kit);
      await kit.present((context) => showServerSwitcher(context, controller));
      kit.expectText('Manage servers');
    }),

    // -- question sheet (opened from the conversation) ----------------------
    CensusShot('question-sheet', (kit) async {
      final controller = await kit.connected();
      controller.questions = {sampleQuestion().id: sampleQuestion()};
      await kit.pumpApp(const HomeScreen(), controller: controller);
      await kit.present(
        (context) => showQuestionSheet(context, controller, sampleQuestion()),
      );
      kit.expectText('Follow the system');
    }),
    CensusShot('question-sheet-dismiss-dialog', (kit) async {
      final controller = await kit.connected();
      controller.questions = {sampleQuestion().id: sampleQuestion()};
      await kit.pumpApp(const HomeScreen(), controller: controller);
      await kit.present(
        (context) => showQuestionSheet(context, controller, sampleQuestion()),
      );
      await kit.tap(find.text('Dismiss').last);
      kit.expectText('Dismiss this request?');
    }),

    // -- generic sheets and dialogs -------------------------------------------
    CensusShot(
      'confirm-sheet',
      (kit) async {
        await _home(kit);
        await kit.present(
          (context) => showKitConfirm(
            context,
            title: 'Delete this conversation?',
            body: 'It is removed from the server for everyone.',
            confirmLabel: 'Delete',
            kind: KitConfirmKind.destructive,
          ),
        );
        kit.expectText('Delete this conversation?');
      },
      note: 'the generic confirm sheet, with sample destructive copy',
    ),
    CensusShot('external-link-dialog', state: 'https', (kit) async {
      await _home(kit);
      await kit.present(
        (context) => openExternalLink(
          context,
          'https://opencode.ai/docs/share',
          launcher: (_) async => false,
        ),
      );
      kit.expectText('Open external link?');
    }),
    CensusShot('external-link-dialog', state: 'insecure-http', (kit) async {
      await _home(kit);
      await kit.present(
        (context) => openExternalLink(
          context,
          'http://192.168.1.20:3000/preview',
          launcher: (_) async => false,
        ),
      );
      kit.expectText('Open insecure HTTP link?');
    }),

    // -- notices from main.dart ---------------------------------------------
    CensusShot(
      'share-session-failed-banner',
      (kit) async {
        final controller = await kit.connected(api: _NoSessionApi());
        final share = ShareIntent(
          channel: const MethodChannel('oc/share-census'),
        );
        kit.onDispose(share.dispose);
        await _pumpOcApp(kit, controller, share: share);
        share.pending.value = 'Crash log from the checkout page: TypeError…';
        await kit.settle(const Duration(seconds: 2));
        kit.expectVisible(find.byType(MaterialBanner));
      },
      note: 'the real OcApp; creating the session for shared text fails',
    ),
    CensusShot(
      'session-link-server-missing-banner',
      (kit) async {
        final controller = await kit.connected();
        final link = SessionLinkIntent(
          channel: const MethodChannel('oc/link-census'),
        );
        kit.onDispose(link.dispose);
        await _pumpOcApp(kit, controller, link: link);
        link.pending.value = const SessionLink(
          profileID: 'someone-elses-phone',
          sessionID: 'ses_link',
        );
        await kit.settle(const Duration(seconds: 2));
        kit.expectVisible(find.byKey(const Key('session-link-server-missing')));
      },
      note: 'the real OcApp; a scanned link names a server not saved here',
    ),
    CensusShot('shorebird-update-notice', state: 'receiving', (kit) async {
      final controller = await kit.connected();
      final service = _UpdateService(AppUpdateState.available);
      await _pumpWithMessenger(
        kit,
        controller,
        (messenger) => ShorebirdUpdateNotice(
          service: service,
          messengerKey: messenger,
          child: const HomeScreen(),
        ),
      );
      // screen-system-2: the download is silent (owner verdict); nothing
      // about the update shows until it is ready.
      expect(find.text('App update ready'), findsNothing);
    }),
    CensusShot('shorebird-update-notice', state: 'ready', (kit) async {
      final controller = await kit.connected();
      await _pumpWithMessenger(
        kit,
        controller,
        (messenger) => ShorebirdUpdateNotice(
          service: _UpdateService(AppUpdateState.restartRequired),
          messengerKey: messenger,
          child: const HomeScreen(),
        ),
      );
      kit.expectText('App update ready');
    }),
    CensusShot(
      'desktop-release-notice',
      (kit) async {
        final controller = await kit.connected();
        await _pumpWithMessenger(
          kit,
          controller,
          (messenger) => DesktopReleaseNotice(
            messengerKey: messenger,
            enabledOverride: true,
            checker: _ReleaseChecker(),
            currentBuildNumberLoader: () async => 52,
            launcher: (_) async {},
            child: const HomeScreen(),
          ),
        );
        kit.expectTextContaining('is available');
      },
      note: 'desktop builds only; shown here at phone size',
    ),

    // -- desktop surfaces that are ordinary Flutter overlays -------------------
    CensusShot(
      'shortcuts-help-dialog',
      (kit) async {
        await _home(kit);
        await kit.present(showShortcutsHelp);
        kit.expectVisible(find.byType(Dialog));
      },
      note: 'desktop keyboard help; shown here at phone size',
    ),
    CensusShot(
      'command-palette-dialog',
      (kit) async {
        await _home(kit);
        await kit.present(
          (context) => showCommandPalette(context, [
            DesktopCommand(
              label: 'New conversation',
              icon: AppIconography.add,
              onInvoke: () {},
              keys: 'Ctrl+N',
              hint: 'Start a conversation in shopfront',
            ),
            DesktopCommand(
              label: 'Open settings',
              icon: AppIconography.settings,
              onInvoke: () {},
              keys: 'Ctrl+,',
            ),
            DesktopCommand(
              label: 'Keyboard shortcuts',
              icon: AppIconography.info,
              onInvoke: () {},
              keys: 'Ctrl+/',
            ),
          ]),
        );
        kit.expectText('Open settings');
      },
      note: 'desktop command palette (Ctrl+K) with sample commands',
    ),
    CensusShot(
      'embedded-context-menu-region',
      (kit) async {
        await _home(kit);
        final row = find.text('Add dark mode to settings');
        kit.expectVisible(row);
        final at = kit.tester.getCenter(row);
        await kit.present(
          (context) => showContextMenu(context, at, [
            ContextMenuAction(
              label: 'Rename',
              icon: AppIconography.edit,
              onSelected: () {},
            ),
            ContextMenuAction(
              label: 'Share',
              icon: AppIconography.upload,
              onSelected: () {},
            ),
            ContextMenuAction(
              label: 'Delete',
              icon: AppIconography.delete,
              onSelected: () {},
              destructive: true,
            ),
          ]),
        );
        kit.expectText('Rename');
      },
      note: 'desktop right-click menu over a Work row, sample entries',
    ),

    // -- other screens -------------------------------------------------------
    CensusShot(
      'embedded-product-states',
      state: 'error',
      (kit) async {
        await kit.pumpApp(
          Scaffold(
            appBar: AppBar(title: const Text('Commands')),
            body: KitStateView.error(
              title: "Couldn't load commands",
              details: 'Cannot reach http://192.168.1.20:4096: timed out',
              retry: KitAction(label: 'Try again', onPressed: () {}),
            ),
          ),
        );
        kit.expectText('Try again');
      },
      note: 'the kit error state in a plain host screen',
    ),
    CensusShot(
      'embedded-product-states',
      state: 'empty',
      (kit) async {
        await kit.pumpApp(
          Scaffold(
            appBar: AppBar(title: const Text('Commands')),
            body: const KitStateView(
              icon: AppIconography.info,
              title: 'No commands yet',
              body: 'Commands this server offers appear here.',
            ),
          ),
        );
        kit.expectText('No commands yet');
      },
      note: 'the kit empty state in a plain host screen',
    ),
    CensusShot('demo', (kit) async {
      final controller = await kit.disconnected();
      await kit.pumpApp(const DemoScreen(), controller: controller);
      kit.expectVisible(find.byType(DemoScreen));
    }),
    CensusShot('capabilities', state: 'empty', (kit) async {
      final controller = await kit.connected();
      await kit.pumpApp(
        CapabilitiesScreen(controller: controller),
        controller: controller,
      );
      kit.expectVisible(find.byType(TabBar));
    }),
    CensusShot('capabilities', state: 'loaded', (kit) async {
      final controller = await kit.connected(repository: _CommandsRepository());
      await kit.pumpApp(
        CapabilitiesScreen(controller: controller),
        controller: controller,
      );
      kit.expectTextContaining('Review the current diff');
    }),
  ],
  notRendered: {
    'system':
        'not a surface: the non-UI entry points (share, links, '
        'notifications, launcher shortcuts)',
    'global-shortcuts':
        'not a surface: the desktop keyboard-shortcut layer; its visible '
        'parts are shortcuts-help-dialog and command-palette-dialog',
    'embedded-desktop-file-drop-target':
        'desktop-only: the "Drop to attach" highlight appears only during a '
        'native drag-and-drop, which flutter_test cannot start',
    'file-drop-failed-dialog':
        'desktop-only: needs a native drag-and-drop whose handler throws',
  },
);

/// A stand-in for the platform error a bootstrap can raise.
class FileSystemExceptionLike implements Exception {
  const FileSystemExceptionLike(this.message);
  final String message;
  @override
  String toString() => message;
}
