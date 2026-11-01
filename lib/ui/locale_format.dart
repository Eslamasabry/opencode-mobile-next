import 'package:intl/intl.dart' show NumberFormat;

/// Money, counts and compact numbers in the app locale (digits and
/// separators come from intl: Arabic shows Arabic-Indic digits).

/// "$3.00", "$0.123": dollars with [decimals] places.
String formatUsd(String locale, double value, {int decimals = 2}) =>
    NumberFormat.currency(
      locale: locale,
      name: 'USD',
      symbol: r'$',
      decimalDigits: decimals,
    ).format(value);

/// A whole count with the locale's grouping ("12,345").
String formatCount(String locale, int value) =>
    NumberFormat.decimalPattern(locale).format(value);

/// "980", "12.4k", "1.2M": a count in compact form, digits per locale.
String formatCompactCount(String locale, int value) {
  final one = NumberFormat('0.0', locale);
  if (value >= 1000000) return '${one.format(value / 1000000)}M';
  if (value >= 1000) return '${one.format(value / 1000)}k';
  return NumberFormat.decimalPattern(locale).format(value);
}
