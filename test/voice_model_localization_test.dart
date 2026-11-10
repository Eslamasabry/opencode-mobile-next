import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/product_repository.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/l10n/app_localizations_en.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/widgets/pickers.dart';
import 'package:opencode_mobile/ui/widgets/provider_logo.dart';
import 'package:opencode_mobile/voice/audio.dart';
import 'package:opencode_mobile/voice/model_download.dart';
import 'package:opencode_mobile/voice/model_manager.dart';
import 'package:opencode_mobile/voice/model_manifest.dart';
import 'package:opencode_mobile/voice/presentation.dart';
import 'package:opencode_mobile/voice/voice_ui.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../tool/capture/fixtures.dart' show capturePng, loadCaptureFonts;
import 'support/e7_voice_model_arabic_fixture.dart';
import 'voice_controller_test.dart' show readyVoiceModelManager;

class _FixtureDelegate extends LocalizationsDelegate<AppLocalizations> {
  const _FixtureDelegate();
  @override
  bool isSupported(Locale locale) => ['en', 'ar'].contains(locale.languageCode);
  @override
  Future<AppLocalizations> load(Locale locale) => SynchronousFuture(
    locale.languageCode == 'ar'
        ? E7VoiceModelArabicFixture()
        : AppLocalizationsEn(),
  );
  @override
  bool shouldReload(_FixtureDelegate old) => false;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  final captureDirectory = Platform.environment['OC_VOICE_MODEL_CAPTURE_DIR'];
  setUp(() => ProviderLogo.logoLoaderOverride = (_) => null);
  tearDown(() => ProviderLogo.logoLoaderOverride = null);

  test(
    'manifest presentation and categorized failures translate without changing data',
    () async {
      final strings = E7VoiceModelArabicFixture();
      final pack = voiceModelPack('base');
      expect(voicePackLabel(pack, strings), 'متوازن');
      expect(voiceLanguageLabel(VoiceLanguage.auto, strings), 'اكتشاف تلقائي');
      expect(pack.encoder.name, 'base-encoder.int8.onnx');
      expect(pack.repository, 'csukuangfj/sherpa-onnx-whisper-base');
      expect(
        voiceErrorText(const VoicePermissionDenied(permanent: true), strings),
        contains('إعدادات'),
      );
      const failure = VoiceDownloadException(
        'HTTP 503 raw detail',
        failure: VoiceDownloadFailure.http,
      );
      expect(voiceErrorText(failure, strings), contains('رفض'));
      expect(failure.toString(), 'HTTP 503 raw detail');
      const preflight = VoiceModelPreflightException(
        'raw preflight',
        support: VoicePackSupport(
          supported: false,
          kind: VoicePackUnsupported.memory,
        ),
        memoryMb: 128,
        pack: VoiceModelPack(
          id: 'custom',
          label: 'CustomPack',
          description: 'server supplied',
          repository: 'repo',
          revision: 'rev',
          files: [],
          minimumMemoryMb: 256,
        ),
      );
      expect(voiceErrorText(preflight, strings), contains('CustomPack'));
      expect(voiceErrorText(preflight, strings), contains('256'));
      expect(voiceErrorText(preflight, strings), contains('128'));
      expect(
        voiceErrorText(StateError('No audio was captured.'), strings),
        'لم يُلتقط أي صوت.',
      );
    },
  );

  Widget host(Widget home, Locale locale, double scale, GlobalKey boundary) =>
      RepaintBoundary(
        key: boundary,
        child: MaterialApp(
          debugShowCheckedModeBanner: false,
          locale: locale,
          supportedLocales: const [Locale('en'), Locale('ar')],
          localizationsDelegates: const [
            _FixtureDelegate(),
            ...AppLocalizations.localizationsDelegates,
          ],
          theme: AppTheme.light().copyWith(
            textTheme: AppTheme.light().textTheme.apply(
              fontFamily: 'sans-serif',
            ),
          ),
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(
              context,
            ).copyWith(textScaler: TextScaler.linear(scale)),
            child: child!,
          ),
          home: home,
        ),
      );

  Future<void> capture(WidgetTester tester, GlobalKey key, String name) async {
    if (captureDirectory == null) return;
    final bytes = await capturePng(tester, key, pixelRatio: 1);
    final file = File('$captureDirectory/$name.png');
    file.parent.createSync(recursive: true);
    file.writeAsBytesSync(bytes);
  }

  for (final locale in const [Locale('en'), Locale('ar')]) {
    for (final scale in [1.0, 2.5]) {
      final variant = '${locale.languageCode}-320-${scale}x';
      testWidgets(
        'voice setup $variant remains reachable and keeps model identity',
        (tester) async {
          tester.view.physicalSize = const Size(320, 740);
          tester.view.devicePixelRatio = 1;
          addTearDown(tester.view.reset);
          if (captureDirectory != null) {
            await tester.runAsync(() async {
              await loadCaptureFonts();
              final font = FontLoader('sans-serif')
                ..addFont(
                  Future.value(
                    ByteData.sublistView(
                      File(
                        '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',
                      ).readAsBytesSync(),
                    ),
                  ),
                );
              await font.load();
            });
          }
          final manager = await readyVoiceModelManager();
          addTearDown(manager.dispose);
          final key = GlobalKey();
          await tester.pumpWidget(
            host(
              Builder(
                builder: (context) => Scaffold(
                  body: TextButton(
                    onPressed: () => showVoiceModelSetupSheet(context, manager),
                    child: const Text('Open'),
                  ),
                ),
              ),
              locale,
              scale,
              key,
            ),
          );
          await tester.tap(find.text('Open'));
          await tester.pumpAndSettle();
          expect(
            Directionality.of(
              tester.element(find.byKey(const ValueKey('kit-sheet-content'))),
            ),
            locale.languageCode == 'ar' ? TextDirection.rtl : TextDirection.ltr,
          );
          expect(
            find.text(locale.languageCode == 'ar' ? 'متوازن' : 'Balanced'),
            findsOneWidget,
          );
          expect(manager.selectedPack.id, 'base');
          await capture(tester, key, 'voice-$variant');
          final cancel = find.byKey(const Key('voice-model-secondary-action'));
          await tester.ensureVisible(cancel);
          await tester.pumpAndSettle();
          expect(tester.getSize(cancel).height, greaterThanOrEqualTo(48));
          expect(tester.takeException(), isNull);
          await tester.tap(cancel);
          await tester.pumpAndSettle();
          expect(find.byKey(const ValueKey('kit-sheet-content')), findsNothing);
          expect(manager.selectedPack.id, 'base');
        },
      );

      testWidgets(
        'model picker $variant localizes controls and preserves server names',
        (tester) async {
          tester.view.physicalSize = const Size(320, 740);
          tester.view.devicePixelRatio = 1;
          addTearDown(tester.view.reset);
          SharedPreferences.setMockInitialValues({});
          final controller =
              ConnectionController(
                  ProfileStore(prefs: await SharedPreferences.getInstance()),
                  isIsolated: true,
                )
                ..catalogDetailed = true
                ..catalog = const CatalogSnapshot(
                  providers: [
                    CatalogProvider(
                      id: 'openai',
                      name: 'OpenAI',
                      enabled: true,
                    ),
                  ],
                  models: [
                    CatalogModel(
                      id: 'gpt-5.4',
                      name: 'GPT-5.4',
                      providerID: 'openai',
                      enabled: true,
                      status: 'active',
                      contextLimit: 128000,
                      outputLimit: 16000,
                      reasoning: true,
                      tools: true,
                      attachments: true,
                      variants: [],
                    ),
                  ],
                  agents: [],
                );
          addTearDown(controller.dispose);
          final key = GlobalKey();
          var closed = false;
          await tester.pumpWidget(
            host(
              Scaffold(
                body: ModelCatalogView(
                  controller: controller,
                  onClose: () => closed = true,
                ),
              ),
              locale,
              scale,
              key,
            ),
          );
          await tester.pumpAndSettle();
          final model = find.text('GPT-5.4');
          final list = find
              .byWidgetPredicate(
                (w) => w is Scrollable && w.axisDirection == AxisDirection.down,
              )
              .first;
          // The kit model sheet (fa9fc679, 3fcace2c) puts search, tabs and a
          // pinned "Use model" above and below the list; at 2.5x the first
          // model is past what the lazy list has built, so scroll to it.
          await tester.scrollUntilVisible(model, 120, scrollable: list);
          await tester.pumpAndSettle();
          expect(model, findsOneWidget);
          expect(tester.takeException(), isNull);
          await capture(tester, key, 'model-$variant');
          final close = find.byTooltip(
            locale.languageCode == 'ar'
                ? 'إغلاق اختيار النموذج'
                : 'Close model selector',
          );
          // Back up to the header the list scrolled away.
          await tester.scrollUntilVisible(close, -120, scrollable: list);
          await tester.pumpAndSettle();
          await tester.tap(close);
          expect(closed, isTrue);
        },
      );
    }
  }
}
