import 'dart:io';
import 'dart:ui' as ui;

import '../tool/capture/fixtures.dart' show loadCaptureFonts;

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/l10n/app_localizations_en.dart';
import 'package:opencode_mobile/state/app_locale.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/widgets/language_picker.dart';
import 'package:opencode_mobile/ui/kit/kit_text.dart' show KitLtr;
import 'package:shared_preferences/shared_preferences.dart';
import 'package:shared_preferences_platform_interface/shared_preferences_platform_interface.dart';

// Fixture only: validates this journey before the independently translated
// complete corpus lands. This is not a production Arabic localization.
class _ArabicFixture extends AppLocalizationsEn {
  _ArabicFixture() : super('ar');
  @override
  String get e7LocaleUiLanguage => 'اللغة';
  @override
  String get e7LocaleUiSystem => 'استخدام لغة النظام';
  @override
  String get e7LocaleUiClose => 'إغلاق';
  @override
  String get e7LocaleUiDescription =>
      'اختر لغة التطبيق. تبقى رسائل الخادم والنصوص التي تكتبها بلغتها الأصلية.';
}

class _FixtureDelegate extends LocalizationsDelegate<AppLocalizations> {
  const _FixtureDelegate();
  @override
  bool isSupported(Locale locale) => ['en', 'ar'].contains(locale.languageCode);
  @override
  Future<AppLocalizations> load(Locale locale) => SynchronousFuture(
    locale.languageCode == 'ar' ? _ArabicFixture() : AppLocalizationsEn(),
  );
  @override
  bool shouldReload(_FixtureDelegate old) => false;
}

class _RefusedStore extends InMemorySharedPreferencesStore {
  _RefusedStore(super.data) : super.withData();
  @override
  Future<bool> setValue(String type, String key, Object value) async => false;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late ConnectionController controller;
  late SharedPreferences prefs;
  setUp(() async {
    // Saving a language also asks Android to rebuild its notification copy
    // (oc/background refreshNativeLocale); an unanswered channel would leave
    // the sheet on "Saving language..." for good.
    const background = MethodChannel('oc/background');
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          background,
          (call) async => <String, dynamic>{},
        );
    addTearDown(
      () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(background, null),
    );
    SharedPreferences.setMockInitialValues({});
    prefs = await SharedPreferences.getInstance();
    controller = ConnectionController(
      ProfileStore(prefs: prefs),
      isIsolated: true,
    );
  });
  tearDown(() => controller.dispose());

  Widget host({Locale? locale, double scale = 1, Key? captureKey}) =>
      RepaintBoundary(
        key: captureKey,
        child: MaterialApp(
          debugShowCheckedModeBanner: false,
          locale: locale,
          supportedLocales: const [Locale('en'), Locale('ar')],
          localizationsDelegates: const [
            _FixtureDelegate(),
            ...AppLocalizations.localizationsDelegates,
          ],
          theme: AppTheme.forLocale(
            AppTheme.light(),
            locale ?? const Locale('en'),
          ),
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(
              context,
            ).copyWith(textScaler: TextScaler.linear(scale)),
            child: child!,
          ),
          home: Scaffold(
            body: ListView(
              children: [LanguageSettingsTile(controller: controller)],
            ),
          ),
        ),
      );

  testWidgets('choosing Arabic persists; closing leaves choice unchanged', (
    tester,
  ) async {
    await tester.pumpWidget(host());
    await tester.tap(find.text('Language'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('العربية'));
    await tester.pumpAndSettle();
    expect(controller.appLocale.value, const Locale('ar'));
    expect(prefs.getString(AppLocaleStore.preferenceKey), 'ar');
    await tester.tap(find.text('Language'));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Close'));
    await tester.pumpAndSettle();
    expect(controller.appLocale.value, const Locale('ar'));
  });

  testWidgets('save refusal stays on sheet with old selection and retry copy', (
    tester,
  ) async {
    SharedPreferencesStorePlatform.instance = _RefusedStore(
      await SharedPreferencesStorePlatform.instance.getAll(),
    );
    await tester.pumpWidget(host());
    await tester.tap(find.text('Language'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('العربية'));
    await tester.pumpAndSettle();
    expect(controller.appLocale.value, isNull);
    expect(
      find.textContaining('Your previous choice is still active'),
      findsOneWidget,
    );
    expect(find.byType(BottomSheet), findsOneWidget);
  });

  for (final locale in const [Locale('en'), Locale('ar')]) {
    testWidgets('320dp 2.5x ${locale.languageCode} all choices reachable', (
      tester,
    ) async {
      tester.view.physicalSize = const Size(320, 640);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final captureDir = Platform.environment['OC_LOCALE_CAPTURE_DIR'];
      if (captureDir != null) {
        await tester.runAsync(() async {
          await loadCaptureFonts();
          final font = File('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf');
          final loader = FontLoader('sans-serif')
            ..addFont(
              Future.value(ByteData.sublistView(await font.readAsBytes())),
            );
          await loader.load();
        });
      }
      final key = GlobalKey();
      await tester.pumpWidget(
        host(locale: locale, scale: 2.5, captureKey: key),
      );
      await tester.tap(
        find.text(locale.languageCode == 'ar' ? 'اللغة' : 'Language'),
      );
      await tester.pumpAndSettle();
      final sheet = find.byType(BottomSheet);
      expect(
        Directionality.of(tester.element(sheet)),
        locale.languageCode == 'ar' ? TextDirection.rtl : TextDirection.ltr,
      );
      await tester.ensureVisible(find.text('العربية'));
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      if (captureDir != null) {
        await tester.runAsync(() async {
          final boundary =
              key.currentContext!.findRenderObject()! as RenderRepaintBoundary;
          final image = await boundary.toImage();
          final data = await image.toByteData(format: ui.ImageByteFormat.png);
          await Directory(captureDir).create(recursive: true);
          await File(
            '$captureDir/picker-${locale.languageCode}-320-2.5x.png',
          ).writeAsBytes(data!.buffer.asUint8List());
          image.dispose();
        });
      }
      await tester.tap(find.text('العربية'));
      await tester.pumpAndSettle();
      expect(controller.appLocale.value, const Locale('ar'));
    });
  }

  testWidgets(
    'technical content isolates direction without changing sibling prose',
    (tester) async {
      await tester.pumpWidget(
        const Directionality(
          textDirection: TextDirection.rtl,
          child: Column(
            children: [
              Text('رسالة'),
              KitLtr(child: Text('/work/main.dart')),
            ],
          ),
        ),
      );
      expect(
        Directionality.of(tester.element(find.text('رسالة'))),
        TextDirection.rtl,
      );
      expect(
        Directionality.of(tester.element(find.text('/work/main.dart'))),
        TextDirection.ltr,
      );
      final theme = AppTheme.forLocale(AppTheme.dark(), const Locale('ar'));
      expect(theme.textTheme.titleLarge!.letterSpacing, 0);
      expect(
        theme.textTheme.titleLarge!.fontFamilyFallback,
        contains('Noto Sans Arabic'),
      );
    },
  );
}
