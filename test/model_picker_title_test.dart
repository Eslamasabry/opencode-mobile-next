// The model picker's title reads naturally for every server name, including
// the app's own server, whose name ("On this phone") already starts with "On".
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/widgets/phone_server_card.dart';

void main() {
  phoneServerNoteTests();
  final en = lookupAppLocalizations(const Locale('en'));

  test('a named server reads "New conversations on <name>"', () {
    expect(
      newConversationsTitle(en, 'Workstation'),
      'New conversations on Workstation',
    );
  });

  test('this phone does not read "on On this phone"', () {
    final title = newConversationsTitle(en, en.phoneServerCardTitle);
    expect(title, 'New conversations on this phone');
    expect(title, isNot(contains('On this')));
  });
}

void phoneServerNoteTests() {
  final en = lookupAppLocalizations(const Locale('en'));
  test('this phone: heading and notes do not double "on"', () {
    final name = en.phoneServerCardTitle;
    expect(sectionOnTitle(en, name), 'On this phone');
    expect(sectionOnTitle(en, 'Workstation'), 'On Workstation');
    expect(
      savedOnNote(en, name),
      "Saved on this phone. Other apps using this phone's server get this too.",
    );
    expect(savedOnNote(en, 'Work'), contains('Saved on Work.'));
    expect(
      useMyDefaultDoneNote(en, name, 'Model'),
      'Reset on this phone to your own choices: Model.',
    );
  });
}
