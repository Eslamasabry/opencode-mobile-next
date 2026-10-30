import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:xml/xml.dart';

const _resources = 'android/app/src/main/res';
const _kotlin =
    'android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/';
const _background = '${_kotlin}BackgroundConnectionService.kt';

/// The files that draw notifications from native copy: the alert service, the
/// one shared status row, and the idle return notification.
const _notificationSources = [
  _background,
  '${_kotlin}StatusNotification.kt',
  '${_kotlin}StatusNotificationRenderer.kt',
  '${_kotlin}BackgroundWidgetActionController.kt',
  '${_kotlin}NativeIdleReturnNotificationHost.kt',
];

Map<String, XmlElement> _copy(String locale) => {
  for (final element in XmlDocument.parse(
    File('$_resources/$locale/strings.xml').readAsStringSync(),
  ).rootElement.childElements)
    if (element.getAttribute('name')?.startsWith('native_') ?? false)
      element.getAttribute('name')!: element,
};

List<String> _slots(String value) =>
    RegExp(r'%\d+\$[sd]').allMatches(value).map((match) => match[0]!).toList()
      ..sort();

void main() {
  test('notification resources cover both languages and compatible slots', () {
    final english = _copy('values');
    final arabic = _copy('values-ar');
    expect(
      english.keys,
      containsAll([
        'native_alert_permission_title',
        'native_alert_question_title',
        'native_alert_complete_title',
        'native_alert_error_title',
        'native_voice_setup',
        'native_setup_channel',
        'native_phone_server_body',
      ]),
    );
    expect(arabic.keys.toSet(), english.keys.toSet());
    for (final entry in english.entries) {
      final translated = arabic[entry.key]!;
      expect(translated.name.local, entry.value.name.local, reason: entry.key);
      if (entry.value.name.local == 'plurals') {
        final expectedSlots = _slots(entry.value.childElements.first.innerText);
        expect(
          translated.childElements.map((item) => item.getAttribute('quantity')),
          containsAll(['zero', 'one', 'two', 'few', 'many', 'other']),
          reason: entry.key,
        );
        for (final item in translated.childElements) {
          final slots = _slots(item.innerText);
          // Arabic zero/one/two can name the count without a numeral. The
          // caller's extra count is harmless; other forms retain its position.
          if (['zero', 'one', 'two'].contains(item.getAttribute('quantity'))) {
            expect(
              slots.every(expectedSlots.contains),
              isTrue,
              reason: entry.key,
            );
          } else {
            expect(slots, expectedSlots, reason: entry.key);
          }
        }
      } else {
        expect(
          _slots(translated.innerText),
          _slots(entry.value.innerText),
          reason: entry.key,
        );
      }
    }
  });

  test('widget authored fallback and time labels use localized resources', () {
    final widget = [
      for (final name in [
        'SessionsWidgetProvider.kt',
        'SessionWidgetRenderer.kt',
        'SessionWidgetRowEnv.kt',
        'SessionWidgetStripBinder.kt',
        'SessionWidgetCardBinder.kt',
        'WidgetCopy.kt',
      ])
        File('$_kotlin$name').readAsStringSync(),
    ].join('\n');
    expect(widget, isNot(contains('"Untitled conversation"')));
    expect(widget, isNot(contains('-> "now"')));
    for (final resource in [
      // What the conversations widget says must be a resource.
      'widget_v2_empty',
      'widget_v2_offline_seen',
      'widget_v2_ago_min',
      'widget_v2_steer_hint',
      'widget_v2_new_in',
      'widget_v2_open_chat',
      'widget_v2_voice',
    ]) {
      expect(widget, contains('R.string.$resource'), reason: resource);
    }
  });

  test(
    'agent titles are localized templates without English prefix inference',
    () {
      final english = _copy('values');
      final arabic = _copy('values-ar');
      for (final kind in [
        'permission',
        'question',
        'complete',
        'error',
        'checkin',
      ]) {
        final id = 'native_alert_${kind}_title';
        expect(english[id], isNotNull, reason: id);
        expect(arabic[id], isNotNull, reason: id);
        expect(_slots(english[id]!.innerText), [r'%1$s']);
        expect(_slots(arabic[id]!.innerText), [r'%1$s']);
      }
      final source = [
        for (final path in _notificationSources) File(path).readAsStringSync(),
      ].join('\n');
      expect(source, isNot(contains('content.title.startsWith("OpenCode")')));
      expect(source, isNot(contains('content.title.removePrefix("OpenCode")')));
      expect(source, contains('NativeStrings.quantity('));
      for (final entry in english.entries.where(
        (entry) =>
            !entry.key.startsWith('native_voice_') &&
            !entry.key.startsWith('native_setup_') &&
            !entry.key.startsWith('native_phone_server_') &&
            !entry.key.startsWith('native_widget_') &&
            entry.key != 'native_stop',
      )) {
        expect(
          source,
          contains('R.${entry.value.name.local}.${entry.key}'),
          reason: entry.key,
        );
      }
    },
  );
}
