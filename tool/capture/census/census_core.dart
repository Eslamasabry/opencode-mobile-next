// The screen census harness: renders every page of the UI ledger
// (docs/design/ui-ledger/ledger.json) it can reach, dark theme, 412x915 dp
// at device pixel ratio 2, with the app's real fonts, into
// docs/qa/screen-census/<area>/<page-id>[--<state>].png, and keeps
// docs/qa/screen-census/manifest.json in step with the ledger.
//
// Entry point and usage: tool/capture/census_test.dart.
//
// An area file (tool/capture/census/areas/<part>.dart) lists its shots and
// the pages it cannot render with the reason. A shot mounts a realistic
// state through [CensusKit] (the capture fixtures, the golden scenes, or a
// minimal fake), opens a sheet or dialog over its parent where the page is
// one, and the harness captures the whole view. One shot failing records the
// error in the manifest and the run moves on.
//
// ignore_for_file: invalid_use_of_visible_for_testing_member
import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/kit/kit_motion.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/widgets/provider_logo.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../audit/audit_env.dart';
import '../fixtures.dart';

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

/// `--dart-define=CENSUS_OUT=<dir>` writes the images (and a manifest of
/// just that run) somewhere else, e.g. a before/after comparison;
/// `--dart-define=CENSUS_LIGHT=true` draws the light theme.
const censusRoot = String.fromEnvironment(
  'CENSUS_OUT',
  defaultValue: 'docs/qa/screen-census',
);
const censusLight = bool.fromEnvironment('CENSUS_LIGHT');
const censusResultsDir = '$censusRoot/.results';
const censusLedgerPath = 'docs/design/ui-ledger/ledger.json';
const censusPartsDir = 'docs/design/ui-ledger/parts';

/// The phone the census draws on: 412x915 dp at 2x (824x1830 px).
const censusLogicalSize = Size(412, 915);
const censusPixelRatio = 2.0;

/// At most this many states per page.
const censusMaxStates = 4;

/// Real time one shot may take before it is cut off.
const censusShotTimeout = Duration(seconds: 90);

/// `--dart-define=CENSUS_AREA=a-shell,b1-chat-screen` renders only those
/// areas; `--dart-define=CENSUS_PAGE=home-shell,activity` only those pages.
const _areaFilter = String.fromEnvironment('CENSUS_AREA');
const _pageFilter = String.fromEnvironment('CENSUS_PAGE');

Set<String> _split(String value) =>
    value.split(',').map((s) => s.trim()).where((s) => s.isNotEmpty).toSet();

// ---------------------------------------------------------------------------
// What an area declares
// ---------------------------------------------------------------------------

/// One image: [page] (a ledger page id) in [state] (null when the page has
/// one meaningful state).
class CensusShot {
  const CensusShot(this.page, this.render, {this.state, this.note});

  final String page;
  final String? state;

  /// Mounts the scene and leaves on screen exactly what the person would see
  /// (a sheet or dialog open over its parent where the page is one).
  final Future<void> Function(CensusKit kit) render;

  /// Optional words for the reviewer: what is shown, what is faked.
  final String? note;

  String get fileName => state == null ? '$page.png' : '$page--$state.png';
}

/// A ledger part (`a-shell`, `b1-chat-screen`, ...): its shots, and the pages
/// it cannot render with the reason.
class CensusArea {
  const CensusArea(this.id, {required this.shots, this.notRendered = const {}});

  final String id;
  final List<CensusShot> shots;
  final Map<String, String> notRendered;
}

// ---------------------------------------------------------------------------
// The kit a shot renders with
// ---------------------------------------------------------------------------

/// Raised by [CensusKit.expectVisible] when the screen is not the one the
/// shot means to show; the shot is recorded as not rendered, never as a
/// misleading image.
class CensusMismatch implements Exception {
  CensusMismatch(this.message);
  final String message;
  @override
  String toString() => 'CensusMismatch: $message';
}

class CensusKit {
  CensusKit._(this.tester);

  final WidgetTester tester;
  final List<FutureOr<void> Function()> _disposers = [];
  final List<String> warnings = [];
  final GlobalKey<NavigatorState> navigatorKey = GlobalKey<NavigatorState>();
  final GlobalKey boundaryKey = GlobalKey();

  /// Runs [body] when the shot ends (after the tree is unmounted).
  void onDispose(FutureOr<void> Function() body) => _disposers.add(body);

  // -- setup ---------------------------------------------------------------

  /// Mock preferences with [values] and the instance behind them.
  Future<SharedPreferences> prefs([
    Map<String, Object> values = const {},
  ]) async {
    SharedPreferences.setMockInitialValues(values);
    return SharedPreferences.getInstance();
  }

  /// A connected [CaptureController] over the "shopfront" sample (one busy
  /// session, three recent ones). Disposed when the shot ends.
  Future<CaptureController> connected({
    CaptureApi? api,
    CaptureRepository? repository,
    Map<String, Object> prefValues = const {},
  }) async {
    final controller = await captureController(
      prefs: await prefs(prefValues),
      api: api,
      repository: repository,
    );
    onDispose(controller.dispose);
    return controller;
  }

  /// A controller with no saved server (first run). Disposed when the shot
  /// ends.
  Future<CaptureController> disconnected({
    Map<String, Object> prefValues = const {},
  }) async {
    final controller = await captureController(
      prefs: await prefs(prefValues),
      connected: false,
    );
    onDispose(controller.dispose);
    return controller;
  }

  /// Replaces the handler of one platform channel for this shot.
  void mockChannel(
    String name,
    Future<Object?>? Function(MethodCall call) handler,
  ) {
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      MethodChannel(name),
      handler,
    );
  }

  /// Pumps [home] inside the app shell the captures use (theme, l10n,
  /// providers, the `/chat/<id>` route), dark theme, and settles.
  Future<void> pumpApp(
    Widget home, {
    ConnectionController? controller,
    ProfileStore? store,
    Map<String, WidgetBuilder> routes = const {},
    bool light = false,
    Duration settleFor = const Duration(seconds: 2),
  }) async {
    final conn = controller ?? await connected();
    await tester.pumpWidget(
      captureApp(
        home: home,
        boundaryKey: boundaryKey,
        controller: conn,
        store: store,
        light: light,
        navigatorKey: navigatorKey,
        routes: routes,
      ),
    );
    await settle(settleFor);
  }

  /// Pumps an already complete widget tree (a golden scene's own app).
  Future<void> pumpRaw(
    Widget app, {
    Duration settleFor = const Duration(seconds: 2),
  }) async {
    await tester.pumpWidget(app);
    await settle(settleFor);
  }

  // -- time ----------------------------------------------------------------

  /// Advances [duration] in 100 ms frames (not pumpAndSettle: ambient
  /// timers may never settle). Exceptions raised while pumping are kept as
  /// warnings for the manifest.
  Future<void> settle([Duration duration = const Duration(seconds: 1)]) async {
    final frames = (duration.inMilliseconds / 100).ceil().clamp(1, 600);
    for (var i = 0; i < frames; i++) {
      await tester.pump(const Duration(milliseconds: 100));
      _drain();
    }
  }

  /// Lets real (not fake-clock) async work finish: asset loads, file IO.
  Future<void> realWait([
    Duration duration = const Duration(milliseconds: 300),
  ]) async {
    await tester.runAsync(() => Future<void>.delayed(duration));
    await settle(const Duration(milliseconds: 300));
  }

  void _drain() {
    for (var i = 0; i < 20; i++) {
      final error = tester.takeException();
      if (error == null) return;
      final text = error.toString().split('\n').take(4).join(' ').trim();
      warnings.add(text.length > 400 ? '${text.substring(0, 400)}…' : text);
    }
  }

  // -- navigation ------------------------------------------------------------

  NavigatorState get navigator {
    final state = navigatorKey.currentState;
    if (state != null) return state;
    return tester.state<NavigatorState>(find.byType(Navigator).last);
  }

  /// A context inside the top route (below Scaffold, providers, theme and
  /// localisations), for calling a `show…` function directly.
  BuildContext get context {
    final scaffolds = find.byType(Scaffold);
    if (scaffolds.evaluate().isNotEmpty) return tester.element(scaffolds.last);
    return navigator.context;
  }

  /// Pushes [page] as a route over the current one and settles.
  Future<void> push(
    Widget page, {
    Duration settleFor = const Duration(seconds: 1),
  }) async {
    unawaited(navigator.push(MaterialPageRoute<void>(builder: (_) => page)));
    await settle(settleFor);
  }

  /// Calls [show] (a `showModalBottomSheet`/`showDialog` wrapper) with a
  /// context inside the top route, without awaiting its result, and settles.
  Future<void> present(
    FutureOr<Object?> Function(BuildContext context) show, {
    Duration settleFor = const Duration(seconds: 1),
  }) async {
    final ctx = context;
    unawaited(Future.sync(() => show(ctx)));
    await settle(settleFor);
  }

  // -- taps ------------------------------------------------------------------

  Future<void> tap(
    Finder finder, {
    Duration settleFor = const Duration(seconds: 1),
    bool scroll = true,
  }) async {
    if (finder.evaluate().isEmpty && scroll) {
      await scrollTo(finder);
    }
    if (finder.evaluate().isEmpty) {
      throw CensusMismatch(
        'nothing to tap: ${finder.describeMatch(Plurality.zero)}',
      );
    }
    await tester.ensureVisible(finder.first);
    await tester.pump();
    await tester.tap(finder.first, warnIfMissed: false);
    await settle(settleFor);
  }

  Future<void> tapText(String text, {Duration? settleFor}) =>
      tap(find.text(text), settleFor: settleFor ?? const Duration(seconds: 1));

  Future<void> tapKey(String key, {Duration? settleFor}) => tap(
    find.byKey(ValueKey(key)),
    settleFor: settleFor ?? const Duration(seconds: 1),
  );

  Future<void> tapTooltip(String tooltip, {Duration? settleFor}) => tap(
    find.byTooltip(tooltip),
    settleFor: settleFor ?? const Duration(seconds: 1),
  );

  Future<void> longPress(
    Finder finder, {
    Duration settleFor = const Duration(seconds: 1),
  }) async {
    if (finder.evaluate().isEmpty) await scrollTo(finder);
    if (finder.evaluate().isEmpty) {
      throw CensusMismatch(
        'nothing to long-press: ${finder.describeMatch(Plurality.zero)}',
      );
    }
    await tester.ensureVisible(finder.first);
    await tester.longPress(finder.first, warnIfMissed: false);
    await settle(settleFor);
  }

  Future<void> enterText(Finder finder, String text) async {
    await tester.enterText(finder.first, text);
    await settle(const Duration(milliseconds: 300));
  }

  /// Scrolls the first scrollable until [finder] is built, if it can.
  Future<void> scrollTo(Finder finder, {double delta = 300}) async {
    final scrollables = find.byType(Scrollable);
    if (scrollables.evaluate().isEmpty) return;
    try {
      await tester.scrollUntilVisible(
        finder,
        delta,
        scrollable: scrollables.first,
        maxScrolls: 30,
      );
    } catch (_) {
      // Left to the caller's own check.
    }
    _drain();
  }

  /// Fails the shot (recorded as not rendered) unless [finder] finds
  /// something: the guard that keeps a wrong screen out of the census.
  void expectVisible(Finder finder, [String? what]) {
    if (finder.evaluate().isEmpty) {
      throw CensusMismatch(
        'expected ${what ?? finder.describeMatch(Plurality.one)} on screen',
      );
    }
  }

  void expectText(String text) => expectVisible(find.text(text), '"$text"');

  void expectTextContaining(String text) =>
      expectVisible(find.textContaining(text), '"…$text…"');
}

// ---------------------------------------------------------------------------
// Platform defaults every shot starts from
// ---------------------------------------------------------------------------

Directory? _scratch;

Directory _scratchDir() =>
    _scratch ??= Directory.systemTemp.createTempSync('oc_census_');

void _installPlatformDefaults(WidgetTester tester) {
  final messenger = tester.binding.defaultBinaryMessenger;
  messenger.setMockMethodCallHandler(
    const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
    (call) async => switch (call.method) {
      'readAll' => <String, String>{},
      'containsKey' => false,
      _ => null,
    },
  );
  messenger.setMockMethodCallHandler(
    const MethodChannel('dev.fluttercommunity.plus/package_info'),
    (call) async => <String, dynamic>{
      'appName': 'OpenCode Mobile',
      'packageName': 'com.opencode.mobile',
      'version': '1.0.44',
      'buildNumber': '52',
      'buildSignature': '',
    },
  );
  messenger.setMockMethodCallHandler(
    const MethodChannel('plugins.flutter.io/path_provider'),
    (call) async => _scratchDir().path,
  );
  // App channels answer "nothing" unless a shot says otherwise.
  for (final name in const [
    'oc/background',
    'oc/camera',
    'oc/link',
    'oc/local_pdf',
    'oc/read-aloud',
    'oc/share',
    'oc/shortcut',
    'oc/tailscale',
    'oc/termux',
    'oc/voice',
  ]) {
    messenger.setMockMethodCallHandler(MethodChannel(name), (call) async {
      if (name == 'oc/termux' && call.method == 'getCapabilities') {
        return <String, Object>{
          'installed': false,
          'version': '',
          'serviceAvailable': false,
          'protocolSupported': false,
          'permissionGranted': false,
        };
      }
      return null;
    });
  }
}

// ---------------------------------------------------------------------------
// Capture
// ---------------------------------------------------------------------------

/// The whole view (routes, sheets, dialogs, overlays) as PNG bytes at the
/// view's physical size.
Future<Uint8List> _captureView(WidgetTester tester) async {
  final view = tester.binding.renderViews.first;
  final layer = view.debugLayer! as OffsetLayer;
  Uint8List? bytes;
  // AUDIT TOOLING: blurred shadows when AUDIT_STYLE is set.
  await auditWithShadows(tester, () async {});
  await tester.runAsync(() async {
    final image = await layer.toImage(view.paintBounds);
    final data = await image.toByteData(format: ui.ImageByteFormat.png);
    bytes = data!.buffer.asUint8List();
    image.dispose();
  });
  return bytes!;
}

void _setPhoneView(WidgetTester tester) {
  tester.view.physicalSize = censusLogicalSize * censusPixelRatio;
  tester.view.devicePixelRatio = censusPixelRatio;
}

// ---------------------------------------------------------------------------
// Results
// ---------------------------------------------------------------------------

class _ShotResult {
  _ShotResult(this.shot, {this.image, this.error, this.warnings = const []});
  final CensusShot shot;
  final String? image;
  final String? error;
  final List<String> warnings;

  Map<String, Object?> toJson() => {
    'page': shot.page,
    'state': shot.state ?? 'default',
    'image': ?image,
    'error': ?error,
    if (warnings.isNotEmpty) 'warnings': warnings,
    'note': ?shot.note,
  };
}

final Map<String, List<_ShotResult>> _results = {};

// ---------------------------------------------------------------------------
// The run
// ---------------------------------------------------------------------------

/// Registers one test per shot of [areas] (after the CENSUS_AREA and
/// CENSUS_PAGE filters) and writes the results and the manifest when done.
void runCensus(List<CensusArea> areas) {
  final areaFilter = _split(_areaFilter);
  final pageFilter = _split(_pageFilter);
  final selected = [
    for (final area in areas)
      if (areaFilter.isEmpty || areaFilter.contains(area.id)) area,
  ];

  setUpAll(() async {
    await loadCaptureFonts();
    // Some widgets ask for the generic `monospace` family, which a phone
    // resolves to its system mono; flutter_test has none, so lend it the
    // app's Geist Mono instead of drawing boxes.
    final mono = FontLoader('monospace');
    final bytes = File(
      'assets/fonts/geist/GeistMono-Variable.ttf',
    ).readAsBytesSync();
    mono.addFont(Future.value(ByteData.sublistView(bytes)));
    await mono.load();
    ProviderLogo.logoLoaderOverride = (_) => null;
    KitMotion.loops = false;
    chatScreenFor = (id) => ChatScreen(sessionID: id);
    for (final area in selected) {
      final dir = Directory('$censusRoot/${area.id}');
      // A whole-area run starts clean so no stale state survives.
      if (pageFilter.isEmpty && dir.existsSync()) {
        for (final f in dir.listSync()) {
          if (f is File && f.path.endsWith('.png')) f.deleteSync();
        }
      }
      dir.createSync(recursive: true);
    }
  });

  tearDownAll(() async {
    ProviderLogo.logoLoaderOverride = null;
    for (final area in selected) {
      _writeAreaResults(area, partial: pageFilter.isNotEmpty);
    }
    writeCensusManifest();
  });

  for (final area in selected) {
    final perPage = <String, int>{};
    for (final shot in area.shots) {
      if (pageFilter.isNotEmpty && !pageFilter.contains(shot.page)) continue;
      final count = perPage[shot.page] = (perPage[shot.page] ?? 0) + 1;
      if (count > censusMaxStates) continue;
      testWidgets(
        '${area.id} · ${shot.page} · ${shot.state ?? 'default'}',
        (tester) => _renderShot(tester, area, shot),
        // A shot that hangs (a real-IO wait that never returns) is cut off
        // and recorded as not completed; the run goes on.
        timeout: const Timeout(censusShotTimeout),
      );
    }
  }
}

Future<void> _renderShot(
  WidgetTester tester,
  CensusArea area,
  CensusShot shot,
) async {
  final kit = CensusKit._(tester);
  _installPlatformDefaults(tester);
  _setPhoneView(tester);
  // Surfaces that follow the system theme (the bootstrap gate, OcApp on
  // "system") draw dark like the rest of the census.
  tester.platformDispatcher.platformBrightnessTestValue =
      (AuditEnv.light ?? censusLight) ? Brightness.light : Brightness.dark;
  final list = _results.putIfAbsent(area.id, () => []);
  // Recorded up front so a shot the timeout cuts off still shows up in the
  // manifest; replaced by the real result when the shot finishes.
  final pending = _ShotResult(
    shot,
    error: 'did not complete within ${censusShotTimeout.inSeconds} s',
  );
  list.add(pending);
  String? error;
  String? image;
  try {
    await shot.render(kit);
    await kit.settle(const Duration(milliseconds: 500));
    // Scenes borrowed from the goldens set 412x915 at 1x; draw at 2x.
    if (tester.view.devicePixelRatio != censusPixelRatio ||
        tester.view.physicalSize != censusLogicalSize * censusPixelRatio) {
      _setPhoneView(tester);
      await kit.settle(const Duration(milliseconds: 300));
    }
    final bytes = await _captureView(tester);
    final path = '$censusRoot/${area.id}/${shot.fileName}';
    await writePng(path, bytes);
    image = '${area.id}/${shot.fileName}';
  } catch (e, st) {
    final text = e.toString().split('\n').take(6).join(' ').trim();
    error = text.length > 600 ? '${text.substring(0, 600)}…' : text;
    debugPrint('census: ${shot.page} · ${shot.state}: $error\n$st');
  } finally {
    try {
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pump(const Duration(seconds: 2));
      kit._drain();
      for (final dispose in kit._disposers.reversed) {
        await dispose();
      }
      await tester.pump(const Duration(seconds: 30));
      kit._drain();
    } catch (e) {
      kit.warnings.add('cleanup: $e');
    }
    tester.view.reset();
    tester.platformDispatcher.clearPlatformBrightnessTestValue();
  }
  list
    ..remove(pending)
    ..add(
      _ShotResult(shot, image: image, error: error, warnings: kit.warnings),
    );
}

void _writeAreaResults(CensusArea area, {required bool partial}) {
  final file = File('$censusResultsDir/${area.id}.json');
  file.parent.createSync(recursive: true);
  final fresh = [
    for (final r in _results[area.id] ?? <_ShotResult>[]) r.toJson(),
  ];
  var shots = fresh;
  if (partial && file.existsSync()) {
    final old = (jsonDecode(file.readAsStringSync()) as Map)['shots'] as List;
    final redone = {for (final r in fresh) r['page']};
    shots = [
      for (final r in old.cast<Map<String, Object?>>())
        if (!redone.contains(r['page'])) r,
      ...fresh,
    ];
  }
  file.writeAsStringSync(
    const JsonEncoder.withIndent(' ').convert({
      'area': area.id,
      'renderedAt': DateTime.now().toUtc().toIso8601String(),
      'declaredShots': [
        for (final s in area.shots)
          {'page': s.page, 'state': s.state ?? 'default'},
      ],
      'notRendered': area.notRendered,
      'shots': shots,
    }),
  );
}

// ---------------------------------------------------------------------------
// Manifest
// ---------------------------------------------------------------------------

/// Which ledger part each page id belongs to: the first part file (sorted)
/// that describes it, after the ledger's own alias and drop rules.
Map<String, String> _pageParts() {
  final overrides =
      jsonDecode(File('$censusPartsDir/_overrides.json').readAsStringSync())
          as Map<String, dynamic>;
  final rename = <String, String>{
    ...(overrides['pageAlias'] as Map? ?? const {}).cast<String, String>(),
    ...(overrides['dropPages'] as Map? ?? const {}).cast<String, String>(),
  };
  final files = Directory(censusPartsDir).listSync().whereType<File>().where((
    f,
  ) {
    final name = f.uri.pathSegments.last;
    return name.endsWith('.json') && !name.startsWith('_');
  }).toList()..sort((a, b) => a.path.compareTo(b.path));
  final result = <String, String>{};
  for (final f in files) {
    final part = f.uri.pathSegments.last.replaceAll('.json', '');
    final data = jsonDecode(f.readAsStringSync()) as Map<String, dynamic>;
    for (final p in (data['pages'] as List? ?? const [])) {
      final raw = (p as Map)['id'] as String;
      result.putIfAbsent(rename[raw] ?? raw, () => part);
    }
  }
  return result;
}

/// Rebuilds manifest.json (and the counts block of README.md) from the
/// ledger and every area's last results.
void writeCensusManifest() {
  final ledger =
      jsonDecode(File(censusLedgerPath).readAsStringSync())
          as Map<String, dynamic>;
  final parts = _pageParts();
  final results = <String, Map<String, dynamic>>{};
  final resultsDir = Directory(censusResultsDir);
  if (resultsDir.existsSync()) {
    for (final f in resultsDir.listSync().whereType<File>()) {
      if (!f.path.endsWith('.json')) continue;
      final data = jsonDecode(f.readAsStringSync()) as Map<String, dynamic>;
      results[data['area'] as String] = data;
    }
  }

  final pages = <Map<String, Object?>>[];
  final counts = <String, Map<String, int>>{}; // area -> rendered/not
  final kindCounts = <String, Map<String, int>>{};
  var images = 0;
  for (final raw in (ledger['pages'] as List).cast<Map<String, dynamic>>()) {
    final id = raw['id'] as String;
    final area = parts[id] ?? 'unassigned';
    final kind = raw['kind'] as String;
    final areaResults = results[area];
    final shots = [
      for (final s in (areaResults?['shots'] as List? ?? const []))
        if ((s as Map)['page'] == id) s.cast<String, Object?>(),
    ];
    final rendered = [
      for (final s in shots)
        if (s['image'] != null)
          {
            'state': s['state'],
            'image': s['image'],
            if (s['warnings'] != null) 'warnings': s['warnings'],
            if (s['note'] != null) 'note': s['note'],
          },
    ];
    final failed = [
      for (final s in shots)
        if (s['error'] != null) {'state': s['state'], 'error': s['error']},
    ];
    final declared =
        (areaResults?['notRendered'] as Map?)?.cast<String, Object?>() ??
        const {};
    String? notRendered;
    if (rendered.isEmpty) {
      if (declared[id] != null) {
        notRendered = declared[id] as String;
      } else if (failed.isNotEmpty) {
        notRendered = 'render failed: ${failed.first['error']}';
      } else if (areaResults == null) {
        notRendered = 'area not rendered yet';
      } else {
        notRendered = 'no census scene yet';
      }
    }
    images += rendered.length;
    final c = counts.putIfAbsent(area, () => {'rendered': 0, 'notRendered': 0});
    final k = kindCounts.putIfAbsent(
      kind,
      () => {'rendered': 0, 'notRendered': 0},
    );
    final bucket = rendered.isEmpty ? 'notRendered' : 'rendered';
    c[bucket] = c[bucket]! + 1;
    k[bucket] = k[bucket]! + 1;
    pages.add({
      'id': id,
      'area': area,
      'ledgerArea': raw['area'],
      'kind': kind,
      'title': raw['title'],
      'file': raw['file'],
      'widget': raw['widget'],
      'reachedFrom': raw['reachedFrom'] ?? const [],
      if (rendered.isNotEmpty) 'states': rendered,
      if (failed.isNotEmpty) 'failedStates': failed,
      'notRendered': ?notRendered,
    });
  }

  final manifest = {
    'generatedAt': DateTime.now().toUtc().toIso8601String(),
    'ledgerGeneratedFrom': ledger['generatedFrom'],
    'viewport': {
      'width': censusLogicalSize.width,
      'height': censusLogicalSize.height,
      'devicePixelRatio': censusPixelRatio,
      'theme': censusLight ? 'light' : 'dark',
    },
    'totals': {
      'pages': pages.length,
      'rendered': pages.where((p) => p['states'] != null).length,
      'notRendered': pages.where((p) => p['states'] == null).length,
      'images': images,
    },
    'byArea': counts,
    'byKind': kindCounts,
    'pages': pages,
  };
  File('$censusRoot/manifest.json')
    ..parent.createSync(recursive: true)
    ..writeAsStringSync(
      '${const JsonEncoder.withIndent('  ').convert(manifest)}\n',
    );
  _writeReadmeCounts(counts, kindCounts, manifest['totals']! as Map);
}

void _writeReadmeCounts(
  Map<String, Map<String, int>> byArea,
  Map<String, Map<String, int>> byKind,
  Map totals,
) {
  final readme = File('$censusRoot/README.md');
  if (!readme.existsSync()) return;
  const start = '<!-- census-counts:start -->';
  const end = '<!-- census-counts:end -->';
  final text = readme.readAsStringSync();
  final a = text.indexOf(start);
  final b = text.indexOf(end);
  if (a < 0 || b < a) return;
  final out = StringBuffer()
    ..writeln(start)
    ..writeln()
    ..writeln(
      '${totals['rendered']} of ${totals['pages']} ledger pages rendered, '
      '${totals['images']} images; ${totals['notRendered']} not rendered '
      '(reasons in `manifest.json`).',
    )
    ..writeln()
    ..writeln('| Area | Rendered | Not rendered |')
    ..writeln('|---|---:|---:|');
  final areas = byArea.keys.toList()..sort();
  for (final area in areas) {
    out.writeln(
      '| `$area` | ${byArea[area]!['rendered']} | '
      '${byArea[area]!['notRendered']} |',
    );
  }
  out
    ..writeln()
    ..writeln('| Kind | Rendered | Not rendered |')
    ..writeln('|---|---:|---:|');
  final kinds = byKind.keys.toList()..sort();
  for (final kind in kinds) {
    out.writeln(
      '| $kind | ${byKind[kind]!['rendered']} | '
      '${byKind[kind]!['notRendered']} |',
    );
  }
  out
    ..writeln()
    ..write(end);
  readme.writeAsStringSync(
    text.replaceRange(a, b + end.length, out.toString()),
  );
}
