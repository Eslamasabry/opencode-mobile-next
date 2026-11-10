// Captures of the model sheet's plain choices (vision slice, 2026-10-20):
// the real sheet over the real ChatScreen at 412x915 dp, real fonts, no
// server.
//
//   flutter test --concurrency=1 tool/capture/vision_model_picker_test.dart
//   flutter test --concurrency=1 --dart-define=VISION_PASS=pass1 \
//     tool/capture/vision_model_picker_test.dart
//
// Output: /home/eslam/Storage2/launch-footage/vision-model-picker/<pass>/
// <scenario>.png (the final pass writes straight into the folder).
import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/widgets/pickers.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:shared_preferences_platform_interface/shared_preferences_platform_interface.dart';

import 'fixtures.dart';
// AUDIT TOOLING (audit/style-coherence): no-op unless AUDIT_STYLE is set.
import 'audit/audit_env.dart';

const _pass = String.fromEnvironment('VISION_PASS', defaultValue: '');
const _root = '/home/eslam/Storage2/launch-footage/vision-model-picker';
String get _dir => _pass.isEmpty ? _root : '$_root/$_pass';

class _Api extends CaptureApi {
  _Api(this.scenarioProviders);

  final ProvidersResponse scenarioProviders;

  @override
  Future<ProvidersResponse> providers() async => scenarioProviders;

  @override
  ServerCapabilities get capabilities =>
      const ServerCapabilities(permissionRequests: true, promptSteering: true);

  @override
  Future<ServerPage<MessageWithParts>> messagePage(
    String id, {
    String? cursor,
    int limit = 100,
  }) async => ServerPage(items: cursor == null ? await messages(id) : const []);
}

CatalogModel _model(
  String provider,
  String id,
  String name, {
  ModelCost? cost,
  int context = 200000,
}) => CatalogModel(
  id: id,
  providerID: provider,
  name: name,
  enabled: true,
  status: 'active',
  contextLimit: context,
  outputLimit: 32000,
  reasoning: true,
  attachments: true,
  tools: true,
  variants: const [],
  cost: cost,
);

/// The catalog the screens load: this scenario's, not the shared sample.
class _Repo extends CaptureRepository {
  _Repo(this.snapshot);

  final CatalogSnapshot snapshot;

  @override
  Future<CatalogSnapshot> loadCatalog() async => snapshot;
}

ModelCost _price(double input, double output) =>
    ModelCost(inputPerMillion: input, outputPerMillion: output);

const _free = ModelCost(inputPerMillion: 0, outputPerMillion: 0);

/// One capture: what the server sends and what was chosen before.
class _Scenario {
  const _Scenario({
    required this.name,
    required this.providers,
    required this.models,
    required this.history,
    this.defaultModel,
    this.marked = const {},
    this.light = false,
    this.width = 412,
    this.textScale = 1,
    this.locale = const Locale('en'),
    this.openAll = false,
  });

  final String name;
  final List<CatalogProvider> providers;
  final List<CatalogModel> models;

  /// Chosen before, oldest first; the last one is in use.
  final List<ModelRef> history;
  final ModelRef? defaultModel;

  /// A phone agent's own `isDefault` marks.
  final Set<String> marked;
  final bool light;
  final double width;
  final double textScale;
  final Locale locale;
  final bool openAll;
}

ModelRef _ref(String provider, String id) =>
    ModelRef(providerID: provider, modelID: id);

final _oc2Providers = const [
  CatalogProvider(id: 'anthropic', name: 'Anthropic', enabled: true),
  CatalogProvider(id: 'openai', name: 'OpenAI', enabled: true),
  CatalogProvider(id: 'opencode', name: 'OpenCode Zen', enabled: true),
];

final _oc2Models = [
  _model(
    'anthropic',
    'claude-sonnet-5-5',
    'Claude Sonnet 5.5',
    cost: _price(3, 15),
  ),
  _model(
    'anthropic',
    'claude-opus-5-5',
    'Claude Opus 5.5',
    cost: _price(5, 25),
  ),
  _model('anthropic', 'claude-haiku-5', 'Claude Haiku 5', cost: _price(1, 5)),
  _model('openai', 'gpt-6-sol', 'GPT-6 Sol', cost: _price(2.5, 15)),
  _model('openai', 'gpt-6-astra', 'GPT-6 Astra', cost: _price(1.25, 10)),
  _model('openai', 'gpt-6-mini', 'GPT-6 mini', cost: _price(.25, 2)),
  _model('opencode', 'big-pickle', 'Big Pickle', cost: _free),
  _model(
    'opencode',
    'nemotron-3-5-super-free',
    'Nemotron 3.5 Super',
    cost: _free,
  ),
  _model('opencode', 'glm-5-3-flash-free', 'GLM-5.3 Flash', cost: _free),
];

final _oc2 = _Scenario(
  name: 'oc2-costs-dark',
  providers: _oc2Providers,
  models: _oc2Models,
  defaultModel: _ref('anthropic', 'claude-sonnet-5-5'),
  history: [
    _ref('openai', 'gpt-6-sol'),
    _ref('anthropic', 'claude-sonnet-5-5'),
  ],
);

_Scenario _like(
  _Scenario base, {
  required String name,
  bool? light,
  double? width,
  double? textScale,
  Locale? locale,
  bool? openAll,
}) => _Scenario(
  name: name,
  providers: base.providers,
  models: base.models,
  history: base.history,
  defaultModel: base.defaultModel,
  marked: base.marked,
  light: light ?? base.light,
  width: width ?? base.width,
  textScale: textScale ?? base.textScale,
  locale: locale ?? base.locale,
  openAll: openAll ?? base.openAll,
);

final _scenarios = [
  _oc2,
  _like(_oc2, name: 'oc2-costs-light', light: true),
  _Scenario(
    name: 'free-only',
    providers: const [
      CatalogProvider(id: 'opencode', name: 'OpenCode Zen', enabled: true),
    ],
    models: [
      _model('opencode', 'big-pickle', 'Big Pickle', cost: _free),
      _model(
        'opencode',
        'nemotron-3-5-super-free',
        'Nemotron 3.5 Super',
        cost: _free,
      ),
      _model('opencode', 'glm-5-3-flash-free', 'GLM-5.3 Flash', cost: _free),
      _model(
        'opencode',
        'deepseek-v4-1-flash-free',
        'DeepSeek V4.1 Flash',
        cost: _free,
      ),
      _model(
        'opencode',
        'mimo-v2-6-flash-free',
        'MiMo-V2.6 Flash',
        cost: _free,
      ),
    ],
    defaultModel: _ref('opencode', 'big-pickle'),
    history: [
      _ref('opencode', 'glm-5-3-flash-free'),
      _ref('opencode', 'big-pickle'),
    ],
  ),
  _Scenario(
    name: 'phone-agent-claude-code',
    providers: const [
      CatalogProvider(id: 'claude', name: 'Claude Code', enabled: true),
    ],
    models: [
      _model('claude', 'claude-opus-5-5', 'Opus 5.5', context: 1000000),
      _model('claude', 'claude-sonnet-5-5', 'Sonnet 5.5', context: 1000000),
      _model('claude', 'claude-haiku-5', 'Haiku 5'),
    ],
    marked: const {'claude-sonnet-5-5'},
    history: [_ref('claude', 'claude-opus-5-5')],
  ),
  _like(_oc2, name: 'all-models', openAll: true),
  _like(_oc2, name: 'narrow-320-2x', width: 320, textScale: 2),
  _like(_oc2, name: 'arabic-rtl', locale: const Locale('ar')),
];

/// Android draws Arabic with its Noto Sans Arabic system face; the
/// workstation's copy stands in for it (the theme's fallback names it).
Future<void> _loadArabic() async {
  Future<void> load(String family, List<String> paths) async {
    final files = [for (final path in paths) File(path)];
    if (files.any((file) => !file.existsSync())) return;
    final loader = FontLoader(family);
    for (final file in files) {
      loader.addFont(
        Future.value(ByteData.sublistView(file.readAsBytesSync())),
      );
    }
    await loader.load();
  }

  // Android's sans-serif is Roboto; Arabic falls back to Noto Sans Arabic.
  await load('sans-serif', const [
    'tool/capture/fonts/Roboto-Regular.ttf',
    'tool/capture/fonts/Roboto-Medium.ttf',
    'tool/capture/fonts/Roboto-Bold.ttf',
  ]);
  await load('Noto Sans Arabic', const [
    '/usr/share/fonts/truetype/noto/NotoSansArabic-Regular.ttf',
    '/usr/share/fonts/truetype/noto/NotoSansArabic-Bold.ttf',
  ]);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);
  setUpAll(_loadArabic);
  for (final scenario in _scenarios) {
    testWidgets('model sheet ${scenario.name}', (tester) async {
      // A phone's density, so hairlines and rings are drawn as thin as
      // they are on the device.
      tester.view.physicalSize = Size(scenario.width * 2, 915 * 2);
      tester.view.devicePixelRatio = 2;
      addTearDown(tester.view.reset);
      SharedPreferencesStorePlatform.instance =
          InMemorySharedPreferencesStore.empty();
      final prefs = await SharedPreferences.getInstance();
      await prefs.clear();
      final now = DateTime.now().millisecondsSinceEpoch;
      final providers = ProvidersResponse(
        providers: [
          for (final provider in scenario.providers)
            ProviderInfo(
              id: provider.id,
              name: provider.name,
              modelIDs: [
                for (final model in scenario.models)
                  if (model.providerID == provider.id) model.id,
              ],
              modelData: {
                for (final model in scenario.models)
                  if (model.providerID == provider.id)
                    model.id: {
                      'name': model.name,
                      if (scenario.marked.contains(model.id)) 'isDefault': true,
                    },
              },
            ),
        ],
        defaultProviderID: scenario.defaultModel?.providerID,
        defaultModelID: scenario.defaultModel?.modelID,
      );
      final catalog = CatalogSnapshot(
        providers: scenario.providers,
        models: scenario.models,
        agents: const [],
      );
      final api = _Api(providers)
        ..busy = {}
        ..messagesHandler = (_) async => [
          MessageWithParts(
            info: messageInfo('msg_user', 'user', created: now - 60000),
            parts: [
              Part(
                id: 'part_prompt',
                messageID: 'msg_user',
                type: 'text',
                text: 'Tidy up the settings page.',
              ),
            ],
          ),
          MessageWithParts(
            info: messageInfo('msg_reply', 'assistant', created: now - 50000),
            parts: [
              Part(
                id: 'part_words',
                messageID: 'msg_reply',
                type: 'text',
                text:
                    'Done. I grouped the settings into five sections and '
                    'removed the duplicate toggles.',
              ),
            ],
          ),
        ];
      final controller = CaptureController(
        SeededProfileStore(
          prefs: prefs,
          seeded: [
            ServerProfile(
              id: 'laptop',
              name: 'Laptop',
              baseUrl: 'http://192.168.1.20:4096',
            ),
          ],
        ),
      );
      controller
        ..api = api
        ..repository = _Repo(catalog)
        ..status = StreamStatus.connected
        ..directory = projectDirectory
        ..sessionsById = Map.of(api.sessionsById)
        ..busySessions = {}
        ..catalogDetailed = true
        ..providers = providers
        ..catalog = catalog;
      addTearDown(controller.dispose);
      for (final ref in scenario.history) {
        await controller.selectModelForSession(checkoutSessionID, ref);
      }
      final key = GlobalKey();
      final theme = AppTheme.forLocale(
        captureTheme(light: scenario.light),
        scenario.locale,
      );
      await tester.pumpWidget(
        RepaintBoundary(
          key: key,
          child: ProviderScope(
            overrides: [
              bootstrapProvider.overrideWithValue(
                AppBootstrap(controller.store),
              ),
              connProvider.overrideWithValue(controller),
            ],
            child: MaterialApp(
              debugShowCheckedModeBanner: false,
              theme: theme,
              locale: scenario.locale,
              localizationsDelegates: AppLocalizations.localizationsDelegates,
              supportedLocales: AppLocalizations.supportedLocales,
              builder: (context, child) => MediaQuery(
                data: MediaQuery.of(context).copyWith(
                  disableAnimations: false,
                  textScaler: TextScaler.linear(scenario.textScale),
                ),
                child: auditWrap(context, child!),
              ),
              home: const ChatScreen(sessionID: checkoutSessionID),
            ),
          ),
        ),
      );
      for (var i = 0; i < 10; i++) {
        await tester.pump(const Duration(milliseconds: 120));
      }
      final context = tester.element(find.byType(ChatScreen));
      // The door from the chat: the composer's model, for this conversation.
      unawaited(
        showModelPicker(
          context,
          applyScope: ModelPickerApplyScope.session,
          sessionID: checkoutSessionID,
        ),
      );
      for (var i = 0; i < 10; i++) {
        await tester.pump(const Duration(milliseconds: 120));
      }
      if (scenario.openAll) {
        await tester.tap(find.byKey(const Key('model-picker-all')));
        for (var i = 0; i < 10; i++) {
          await tester.pump(const Duration(milliseconds: 120));
        }
      }
      expect(tester.takeException(), isNull);
      await writePng(
        '$_dir/${scenario.name}.png',
        await capturePng(tester, key, pixelRatio: 2),
      );
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pump(const Duration(seconds: 1));
    });
  }
}
