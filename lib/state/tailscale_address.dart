import 'profiles.dart';

/// Adds HTTPS for a reviewed bare hostname; never upgrades an explicit HTTP
/// address or infers that any hostname belongs to a private network.
String normalizeTailscaleAddress(String value) =>
    normalizeServerProfileUrl(value);

bool isValidTailscaleAddress(String value) {
  final normalized = normalizeTailscaleAddress(value);
  final uri = Uri.tryParse(normalized);
  return value.length <= 2048 &&
      uri != null &&
      uri.scheme.toLowerCase() == 'https' &&
      uri.port > 0 &&
      uri.port <= 65535 &&
      uri.userInfo.isEmpty &&
      !uri.hasQuery &&
      !uri.hasFragment &&
      validateServerProfileUrl(normalized) == null;
}
