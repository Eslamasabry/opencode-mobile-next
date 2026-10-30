import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/widgets/product_states.dart';

Future<String> _say(WidgetTester tester, Locale locale) async {
  late String said;
  await tester.pumpWidget(
    MaterialApp(
      locale: locale,
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      builder: (context, child) => ProductErrorLocale(child: child!),
      home: Builder(
        builder: (context) {
          // A call site that never passes the locale.
          said = productErrorText(TimeoutException('slow'));
          return const SizedBox();
        },
      ),
    ),
  );
  return said;
}

void main() {
  testWidgets('the Arabic app says error words in Arabic', (tester) async {
    final english = lookupAppLocalizations(const Locale('en'));
    final arabic = lookupAppLocalizations(const Locale('ar'));
    expect(arabic.productErrorTimedOut, isNot(english.productErrorTimedOut));
    expect(await _say(tester, const Locale('ar')), arabic.productErrorTimedOut);
    expect(
      await _say(tester, const Locale('en')),
      english.productErrorTimedOut,
    );
  });
}
