import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/domain/server_gateway.dart'
    show CatalogModel, ModelCost;
import 'package:opencode_mobile/ui/locale_format.dart';
import 'package:opencode_mobile/ui/widgets/pickers.dart' show modelCostLabel;
import 'package:flutter/widgets.dart' show Locale;

void main() {
  test('English output is unchanged', () {
    expect(formatUsd('en', 0.0426, decimals: 3), r'$0.043');
    expect(formatUsd('en', 12.5), r'$12.50');
    expect(formatCompactCount('en', 980), '980');
    expect(formatCompactCount('en', 12400), '12.4k');
    expect(formatCompactCount('en', 1200000), '1.2M');
    expect(formatCount('en', 1234567), '1,234,567');
  });

  test('Arabic follows the locale: symbol after the amount', () {
    // intl's plain `ar` keeps Latin digits; the placement and the RTL marks
    // are the locale's, so the hand-built "\$0.043" is gone.
    final cost = formatUsd('ar', 0.0426, decimals: 3);
    expect(cost, isNot(startsWith(r'$')));
    expect(cost, contains('0.043'));
    expect(formatUsd('ar', 3), isNot(formatUsd('en', 3)));
    // Arabic-Indic digits when the locale asks for them.
    expect(formatCount('ar_EG', 1234), contains('١'));
    expect(formatCompactCount('ar_EG', 12400), contains('١'));
  });

  test('model price label follows the app locale', () {
    const cost = ModelCost(inputPerMillion: 3, outputPerMillion: 15);
    final model = CatalogModel(
      id: 'm',
      providerID: 'p',
      name: 'M',
      enabled: true,
      status: 'active',
      contextLimit: 0,
      outputLimit: 0,
      reasoning: false,
      attachments: false,
      tools: false,
      variants: const [],
      cost: cost,
    );
    final en = modelCostLabel(
      model,
      strings: lookupAppLocalizations(const Locale('en')),
    );
    final ar = modelCostLabel(
      model,
      strings: lookupAppLocalizations(const Locale('ar')),
    );
    expect(en, contains(r'$3.00'));
    expect(ar, isNot(contains(r'$3.00')));
    expect(ar, contains('3.00'));
  });
}
