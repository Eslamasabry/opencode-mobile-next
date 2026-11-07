import 'builtin_linux.dart' show BuiltinLinuxException;

/// Which OpenCode the phone's own server runs.
///
/// Only OpenCode 2 can be installed or started. [openCode1] exists for
/// detection: a record written by an older build names it, and such an
/// installation must move to OpenCode 2 before it can start.
enum OpenCodeRuntime {
  openCode1('opencode1', ''),
  openCode2('opencode2', '2.1.1');

  const OpenCodeRuntime(this.wireName, this.pinnedVersion);
  final String wireName;
  final String pinnedVersion;

  /// Fresh setup only. Missing runtime records still describe older installs.
  static const defaultForNewInstall = openCode2;

  void requireSupported() {
    if (this != openCode2) {
      throw const BuiltinLinuxException(
        'OpenCode 1 must move to OpenCode 2 before starting.',
        code: 'opencode1_migration_required',
      );
    }
  }

  static OpenCodeRuntime parse(String? value) => switch (value?.trim()) {
    null || '' || 'opencode1' => openCode1,
    'opencode2' => openCode2,
    _ => throw const BuiltinLinuxException(
      'The phone server has an unsupported runtime selection.',
      code: 'invalid_runtime',
    ),
  };
}
