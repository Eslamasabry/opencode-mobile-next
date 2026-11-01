/// Native (`dart:io`) side of `transport_failure.dart`.
library;

import 'dart:io';

/// Checked through [SocketException.osError] so a typo'd hostname is not
/// mislabeled "connection refused". Android reports "No address associated
/// with hostname"; glibc "Name or service not known"; macOS/iOS "nodename
/// nor servname provided".
bool isHostLookupFailure(Object? cause) {
  if (cause is! SocketException) return false;
  final details = '${cause.message} ${cause.osError?.message ?? ''}'
      .toLowerCase();
  return details.contains('failed host lookup') ||
      details.contains('no address associated with hostname') ||
      details.contains('name or service not known') ||
      details.contains('nodename nor servname') ||
      details.contains('temporary failure in name resolution');
}
