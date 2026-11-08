import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/settings_search.dart';
import 'package:opencode_mobile/domain/settings_search_catalog.dart';
import 'package:opencode_mobile/l10n/app_localizations_en.dart';
import 'package:opencode_mobile/l10n/app_localizations_ar.dart';

void main() {
  List<SettingsSearchDocument> rows({
    bool arabic = false,
    bool available = true,
  }) => settingsSearchRows(
    arabic ? AppLocalizationsAr() : AppLocalizationsEn(),
    supportsBackgroundService: available,
    thermalGuardAvailable: available,
  );

  test('acceptance words and bilingual aliases reach specific rows', () {
    for (final arabic in [false, true]) {
      final index = SettingsSearchIndex(rows(arabic: arabic));
      for (final example in {
        'animations': ('appearance-settings', 'effects-motion'),
        'حركة': ('appearance-settings', 'effects-motion'),
        'heat': ('keep-running', 'keep-running-thermal'),
        'حَرَارَة': ('keep-running', 'keep-running-thermal'),
        'crash': ('app-diagnostics', null),
        'battery': ('keep-running', 'keep-running-battery'),
        'البطارية': ('keep-running', 'keep-running-battery'),
        'reduced motion': ('appearance-settings', 'effects-motion'),
        'احتفالات': ('appearance-settings', 'effects-motion'),
        'keep alive': ('keep-running', 'keep-running-battery'),
      }.entries) {
        final target = index.search(example.key).first.target;
        expect((target.pageId, target.rowId), example.value);
      }
      expect(index.search('animations').first.target.sectionId, 'effects');
    }
  });

  test('typos, adjacent swaps, prefixes and mixed-language words work', () {
    final index = SettingsSearchIndex(rows());
    for (final query in ['animations', ' ANIMA! ', 'effects حركة']) {
      expect(index.search(query).first.target.rowId, 'effects-motion');
    }
    expect(index.search('batery').first.target.rowId, 'keep-running-battery');
    expect(index.search('crsah').first.target.pageId, 'app-diagnostics');
    for (final query in [
      '',
      '   ',
      '!!!',
      'animations banana',
      'zz',
      'vxxratxxn',
      List.filled(10000, 'x').join(),
    ]) {
      expect(index.search(query), isEmpty);
    }
  });

  test('unavailable rows stay absent and crash still opens diagnostics', () {
    final index = SettingsSearchIndex(rows(available: false));
    expect(index.search('heat'), isEmpty);
    expect(index.search('battery'), isEmpty);
    expect(index.search('crash').single.target.pageId, 'app-diagnostics');
    expect(index.search('animations').single.target.rowId, 'effects-motion');
    final withoutGuard = SettingsSearchIndex(
      settingsSearchRows(
        AppLocalizationsEn(),
        supportsBackgroundService: true,
        thermalGuardAvailable: false,
      ),
    );
    expect(withoutGuard.search('heat'), isEmpty);
    expect(withoutGuard.search('battery'), isNotEmpty);
    expect(rows().map((row) => row.id).toSet().length, rows().length);
  });

  test('upgrade retains legacy bilingual aliases and corrects broad doors', () {
    final documents = settingsSearchCatalog(
      AppLocalizationsEn(),
      existing: const [
        SettingsSearchDocument(
          id: 'notifications',
          title: 'Notifications',
          aliases: 'battery background إشعارات',
          target: SettingsSearchTarget(pageId: 'notifications-settings'),
        ),
        SettingsSearchDocument(
          id: 'app-diagnostics-entry',
          title: 'App diagnostics',
          aliases: 'legacydiagnostics تشخيص',
          target: SettingsSearchTarget(pageId: 'obsolete-diagnostics'),
        ),
      ],
      supportsBackgroundService: true,
      thermalGuardAvailable: true,
    );
    final index = SettingsSearchIndex(documents);
    expect(index.search('battery').first.target.rowId, 'keep-running-battery');
    expect(index.search('إشعارات').single.id, 'notifications');
    expect(
      index.search('legacydiagnostics').single.target.pageId,
      'app-diagnostics',
    );
    expect(index.search('تشخيص').single.target.pageId, 'app-diagnostics');
    expect(documents.map((row) => row.id).toSet().length, documents.length);
  });

  test('literal matches outrank fuzzy matches and ties retain input order', () {
    SettingsSearchDocument document(String id, String title, String aliases) =>
        SettingsSearchDocument(
          id: id,
          title: title,
          aliases: aliases,
          target: const SettingsSearchTarget(pageId: 'example'),
        );
    final documents = [
      document('typo', 'Batery', ''),
      document('alias1', 'Power', 'battery'),
      document('alias2', 'Energy', 'battery'),
      document('title', 'Battery', ''),
      document('prefix', 'Batterylife', ''),
    ];
    final index = SettingsSearchIndex(documents);
    documents.clear(); // The index owns its snapshot, not the caller's list.
    expect(index.search('battery').map((hit) => hit.id), [
      'title',
      'alias1',
      'alias2',
      'prefix',
      'typo',
    ]);
  });
}
