/// Ubuntu packages in the app's own Linux: the installed list, what an
/// install or removal would do, and the long jobs (install, remove, update
/// the system) as scripts that report progress with `::oc` lines.
///
/// Pure Dart. Package names are validated against Debian's naming rules
/// before they reach a script, so typed text never becomes shell.
library;

import '../setup/setup_scripts.dart' show setupPrelude;

/// One installed package.
class LinuxPackage {
  const LinuxPackage({
    required this.name,
    required this.version,
    required this.sizeKb,
    this.essential = false,
    this.required = false,
  });

  final String name;
  final String version;

  /// Installed size as dpkg reports it, in kilobytes.
  final int sizeKb;

  /// Marked essential by Ubuntu, or priority "required": Linux stops
  /// working without it.
  final bool essential;
  final bool required;

  /// Removal is refused for these, and for [linuxKeptPackages].
  bool get locked => essential || required || linuxKeptPackages.contains(name);
}

/// The tools phone setup installs with apt and relies on. Removing one
/// breaks OpenCode, Python or AI Team, so they leave through Remove tool on
/// This phone, not from here.
const linuxKeptPackages = <String>{
  'curl',
  'ca-certificates',
  'git',
  'openssh-client',
  'python3',
  'python3-venv',
  'python3-pip',
  'bash',
  'dash',
  'coreutils',
  'apt',
  'dpkg',
  'procps',
};

/// The query that lists installed packages, one `::pkg` line each.
const linuxPackageListScript = r'''# oc-linux-packages
dpkg-query -W -f='::pkg ${Package}\t${Version}\t${Installed-Size}\t${Essential}\t${Priority}\t${db:Status-Abbrev}\n' 2>/dev/null
exit 0
''';

/// Installed packages, sorted by name. Half-installed ones are left out.
List<LinuxPackage> parseLinuxPackages(String output) {
  final out = <LinuxPackage>[];
  for (final line in output.split('\n')) {
    if (!line.startsWith('::pkg ')) continue;
    final f = line.substring(6).split('\t');
    if (f.length < 6) continue;
    if (!f[5].trim().startsWith('ii')) continue;
    final name = f[0].trim();
    if (!isValidLinuxPackageName(name)) continue;
    out.add(
      LinuxPackage(
        name: name,
        version: f[1].trim(),
        sizeKb: int.tryParse(f[2].trim()) ?? 0,
        essential: f[3].trim().toLowerCase() == 'yes',
        required: f[4].trim().toLowerCase() == 'required',
      ),
    );
  }
  out.sort((a, b) => a.name.compareTo(b.name));
  return out;
}

final _packageName = RegExp(r'^[a-z0-9][a-z0-9+.-]+$');

/// Debian package names: lower case letters, digits and `+ - .`, at least
/// two characters, starting with a letter or digit.
bool isValidLinuxPackageName(String name) =>
    name.length <= 100 && _packageName.hasMatch(name);

/// Package names typed by a person: separated by spaces or commas. Returns
/// null when any of them is not a valid name or there are none or too many.
List<String>? parseTypedPackageNames(String text) {
  final names = text
      .split(RegExp(r'[\s,]+'))
      .where((s) => s.isNotEmpty)
      .toSet()
      .toList();
  if (names.isEmpty || names.length > 10) return null;
  return names.every(isValidLinuxPackageName) ? names : null;
}

/// What apt knows about each name, read without changing anything.
String linuxPackageCheckScript(List<String> names) {
  _requireNames(names);
  return '''# oc-linux-package-check
for p in ${names.join(' ')}; do
  c=\$(apt-cache policy "\$p" 2>/dev/null | sed -n 's/^  Candidate: //p')
  i=\$(dpkg-query -W -f='\${db:Status-Abbrev}' "\$p" 2>/dev/null)
  printf '::cand %s\\t%s\\t%s\\n' "\$p" "\$c" "\$i"
done
exit 0
''';
}

/// Whether apt can install a package, from `::cand` lines.
class LinuxPackageCandidate {
  const LinuxPackageCandidate({
    required this.name,
    required this.version,
    required this.installed,
  });

  final String name;

  /// Null when apt has no such package (or its lists are empty).
  final String? version;
  final bool installed;

  bool get available => version != null;
}

List<LinuxPackageCandidate> parseLinuxPackageCandidates(String output) {
  final out = <LinuxPackageCandidate>[];
  for (final line in output.split('\n')) {
    if (!line.startsWith('::cand ')) continue;
    final f = line.substring(7).split('\t');
    if (f.length < 3) continue;
    final version = f[1].trim();
    out.add(
      LinuxPackageCandidate(
        name: f[0].trim(),
        version: version.isEmpty || version == '(none)' ? null : version,
        installed: f[2].trim().startsWith('ii'),
      ),
    );
  }
  return out;
}

/// Packages apt would remove, without removing anything.
String linuxRemovePlanScript(List<String> names) {
  _requireNames(names);
  return '''# oc-linux-remove-plan
DEBIAN_FRONTEND=noninteractive apt-get -s remove ${names.join(' ')} 2>&1 | sed -n 's/^Remv \\([^ ]*\\).*/::remv \\1/p'
exit 0
''';
}

List<String> parseLinuxRemovePlan(String output) => [
  for (final line in output.split('\n'))
    if (line.startsWith('::remv ') &&
        isValidLinuxPackageName(line.substring(7).trim()))
      line.substring(7).trim(),
];

void _requireNames(List<String> names) {
  if (names.isEmpty || !names.every(isValidLinuxPackageName)) {
    throw ArgumentError.value(names, 'names', 'Not package names.');
  }
}

/// What a long job does.
enum LinuxJobKind { install, remove, update }

/// How a job's script ends: the last line it prints.
const linuxJobExitPrefix = '::oc job-exit ';

/// How long a job may run before it stops itself, so it never assumes
/// unbounded background time (Android 15 caps data-sync services).
const linuxJobBudget = Duration(minutes: 45);

/// The script of a long job. [names] are the packages for install and
/// remove. The script first says what it is (`::oc job <kind>`), reports
/// stages and percent through the setup prelude, and ends with
/// `::oc job-exit <code>`: 0 is done, 124 is the time budget.
String linuxJobScript(LinuxJobKind kind, [List<String> names = const []]) {
  if (kind == LinuxJobKind.install || kind == LinuxJobKind.remove) {
    _requireNames(names);
  }
  final body = switch (kind) {
    LinuxJobKind.install =>
      'oc_stage "Installing ${names.join(', ')}"\n'
          'oc_apt_install ${names.join(' ')}\n',
    LinuxJobKind.remove =>
      'oc_apt_repair || exit \$?\n'
          'oc_stage "Removing ${names.join(', ')}"\n'
          'oc_apt_run "" remove -y ${names.join(' ')}\n',
    LinuxJobKind.update =>
      'oc_apt_repair || exit \$?\n'
          'oc_apt_update || exit \$?\n'
          'oc_stage "Updating packages"\n'
          'oc_apt_run "" upgrade -y --no-install-recommends '
          '-o Dpkg::Options::=--force-confold\n',
  };
  return '''$setupPrelude
export DEBIAN_FRONTEND=noninteractive
printf '::oc job ${kind.name}\\n'
printf '::oc names %s\\n' '${names.join(' ')}'
mkdir -p /root/.oc-builtin
exec 9> /root/.oc-builtin/linux-job.lock
if command -v flock >/dev/null 2>&1 && ! flock -n 9; then
  printf '::oc job-exit 75\\n'
  exit 75
fi
oc_main=\$\$
oc_done=/root/.oc-builtin/linux-job.done
rm -f "\$oc_done"
(
  oc_i=0
  while [ ! -e "\$oc_done" ] && [ \$oc_i -lt ${linuxJobBudget.inSeconds ~/ 5} ]; do
    sleep 5
    oc_i=\$((oc_i + 1))
  done
  [ -e "\$oc_done" ] && exit 0
  printf '::oc job-exit 124\\n'
  kill -TERM \$oc_main
) &
(
$body)
oc_rc=\$?
touch "\$oc_done"
printf '::oc job-exit %s\\n' "\$oc_rc"
exit 0
''';
}
