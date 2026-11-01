/// What a setup backup of the app's own Linux holds, and the shell that
/// makes and restores one (S3, docs/design/linux-manager-2026-10-31/
/// 2-architecture.md §3.1). Pure Dart: the rules, the manifest and the
/// scripts; lib/builtin/linux_backup/linux_backup.dart runs them.
///
/// A backup is one zip the person saves where they like. Inside it:
/// - `manifest.json`: format, kind, when, which Ubuntu, what it holds;
/// - `apt-manual.txt`, `npm-global.json`, `pipx.txt`: what was installed,
///   restored by name (not as binaries, so a newer app and a newer phone
///   still restore it);
/// - `home.tar.gz`: the settings files on [linuxBackupHomeFiles], never what
///   [linuxBackupNeverPatterns] names;
/// - `services.tar.gz`: the services' definitions and on/off choices;
/// - `projects.tar.gz` (with projects): /root/projects without downloaded
///   folders ([linuxBackupProjectSkips]) and without credentials.
///
/// Credentials never leave the phone in a backup (owner, 2026-10-31): no
/// sign-ins, tokens or private SSH keys. The tar steps run inside Linux so
/// modes and links are kept.
library;

import 'dart:convert';

import 'package:flutter/foundation.dart';

import '../linux_manager/linux_packages.dart';
import '../project_export.dart' show ProjectExportPolicy;

/// What to back up.
enum LinuxBackupKind {
  /// Packages, tools, services and settings files.
  setup,

  /// The same, and the projects.
  setupAndProjects,
}

/// The manifest's format; a newer one is refused by an older app.
const linuxBackupFormat = 1;

/// Guest paths.
@immutable
class LinuxBackupPaths {
  const LinuxBackupPaths({
    this.home = '/root',
    this.projects = '/root/projects',
    this.stage = '/root/.oc-builtin/backup/stage',
    this.restore = '/root/.oc-builtin/restore',
    this.services = '/root/.oc-builtin/svc',
  });

  final String home;
  final String projects;
  final String stage;
  final String restore;
  final String services;
}

/// Settings files kept, relative to the home folder. Folders are taken
/// whole, minus [linuxBackupNeverPatterns].
const linuxBackupHomeFiles = [
  '.bashrc',
  '.bash_profile',
  '.bash_aliases',
  '.profile',
  '.inputrc',
  '.gitconfig',
  '.vimrc',
  '.tmux.conf',
  '.config/git',
  '.config/nvim',
  '.config/htop',
  '.ssh/authorized_keys',
  '.ssh/config',
  '.ssh/known_hosts',
];

/// Never in a backup, wherever they are (tar `--exclude` patterns): sign-ins,
/// tokens, keys and private SSH keys.
final linuxBackupNeverPatterns = <String>{
  for (final name in ProjectExportPolicy.privateNames) name,
  for (final ext in ProjectExportPolicy.privateExtensions) '*$ext',
  '.ssh/id_*',
  '*/.ssh/id_*',
  '.gnupg',
  '.aws',
  '.docker',
  '.env',
  '.env.*',
  '.claude',
  '.claude.json',
  '.config/gh',
  '.config/opencode',
  '.local/share/opencode',
  '.oc-opencode2',
};

/// Folders a project can download again, left out of a backup.
const linuxBackupProjectSkips = [
  'node_modules',
  '.venv',
  'venv',
  '__pycache__',
  'build',
  'dist',
  '.dart_tool',
  '.gradle',
  'target',
  '.next',
];

/// The zip entries a backup may hold; nothing else is ever extracted.
const linuxBackupEntries = {
  'manifest.json',
  'apt-manual.txt',
  'npm-global.json',
  'pipx.txt',
  'home.tar.gz',
  'services.tar.gz',
  'projects.tar.gz',
};

String _q(String value) => "'${value.replaceAll("'", "'\\''")}'";

/// The excludes file the backup script writes, one pattern per line.
String linuxBackupExcludes({required bool projects}) => [
  ...linuxBackupNeverPatterns,
  if (projects) ...linuxBackupProjectSkips,
].join('\n');

/// The shell that makes the backup's parts in [LinuxBackupPaths.stage].
/// POSIX sh (the job runs it with dash). Prints `::oc stage`, `::oc
/// percent` and, last, `::oc backup-size` with the bytes it made.
String linuxBackupBody(
  LinuxBackupKind kind, {
  LinuxBackupPaths paths = const LinuxBackupPaths(),
}) {
  final withProjects = kind == LinuxBackupKind.setupAndProjects;
  final s = _q(paths.stage);
  return '''
S=$s
rm -rf "\$S" && mkdir -p "\$S" || exit 70
# tar never opens the archive itself: under proot on a phone its own
# open of a new .tar.gz fails ("Function not implemented"), so the shell
# makes the file and gzip writes it. tar's own status is kept: 0, or 1
# when a file changed while it was read.
oc_pack() {
  oc_out=\$1
  shift
  { tar -cf - "\$@"; echo \$? > "\$S/.tar-rc"; } | gzip -c > "\$oc_out" || return 1
  oc_rc=\$(cat "\$S/.tar-rc")
  rm -f "\$S/.tar-rc"
  [ "\$oc_rc" = 0 ] || [ "\$oc_rc" = 1 ]
}
cat > "\$S/excludes" <<'OC_EXCLUDES'
${linuxBackupExcludes(projects: withProjects)}
OC_EXCLUDES
oc_stage "Listing what is installed"
printf '::oc percent 5\\n'
if command -v apt-mark >/dev/null 2>&1; then
  apt-mark showmanual > "\$S/apt-manual.txt" 2>/dev/null || :
fi
if command -v npm >/dev/null 2>&1; then
  npm ls -g --depth=0 --json > "\$S/npm-global.json" 2>/dev/null || :
fi
if command -v pipx >/dev/null 2>&1; then
  pipx list --short > "\$S/pipx.txt" 2>/dev/null || :
fi
oc_stage "Copying settings files"
printf '::oc percent 15\\n'
cd ${_q(paths.home)} || exit 71
: > "\$S/home.list"
for f in ${linuxBackupHomeFiles.map(_q).join(' ')}; do
  if [ -e "\$f" ] || [ -L "\$f" ]; then printf '%s\\n' "\$f" >> "\$S/home.list"; fi
done
oc_pack "\$S/home.tar.gz" --exclude-from="\$S/excludes" -T "\$S/home.list" || exit 72
if [ -d ${_q('${paths.services}/defs')} ]; then
  cd ${_q(paths.services)} || exit 73
  oc_svc=defs
  [ -d want ] && oc_svc="defs want"
  oc_pack "\$S/services.tar.gz" \$oc_svc || exit 73
fi
printf '::oc percent 30\\n'
${withProjects ? _projectsPart(paths) : ''}rm -f "\$S/excludes" "\$S/home.list"
printf '::oc percent 100\\n'
printf '::oc backup-size %s\\n' "\$(du -sb "\$S" | cut -f1)"
''';
}

String _projectsPart(LinuxBackupPaths paths) {
  final p = _q(paths.projects);
  return '''
if [ -d $p ]; then
  oc_stage "Copying projects"
  # A remote with a password in it stays on the phone too.
  find $p -path '*/.git/config' -type f 2>/dev/null | while IFS= read -r f; do
    if grep -Eq '://[^/[:space:]:@]+:[^/[:space:]@]+@' "\$f"; then
      printf '%s\\n' "\${f#$p/}" >> "\$S/excludes"
    fi
  done
  cd $p || exit 74
  oc_pack "\$S/projects.tar.gz" --exclude-from="\$S/excludes" . || exit 74
fi
''';
}

/// The shell that restores a backup unpacked in [LinuxBackupPaths.restore].
/// Packages are installed by name when Ubuntu still has them (others are
/// listed as `::oc skipped <name>`); settings files a backup replaces keep
/// a `.before-restore` copy; a project whose name is taken comes back as
/// "name (restored)". Prints `::oc restored <part> <count>` per part.
String linuxRestoreBody({LinuxBackupPaths paths = const LinuxBackupPaths()}) {
  final r = _q(paths.restore);
  final p = _q(paths.projects);
  return '''
R=$r
[ -f "\$R/manifest.json" ] || exit 80
# gzip reads, tar works on the stream (see oc_pack in the backup).
oc_unpack() {
  oc_in=\$1
  shift
  gzip -dc "\$oc_in" | tar -xf - "\$@"
}
oc_list() { gzip -dc "\$1" | tar -tf -; }
if [ -s "\$R/apt-manual.txt" ] && command -v apt-cache >/dev/null 2>&1; then
  oc_stage "Installing packages"
  oc_names=""
  oc_n=0
  for n in \$(cat "\$R/apt-manual.txt"); do
    case "\$n" in *[!a-z0-9+.:-]*|"") continue ;; esac
    dpkg -s "\$n" >/dev/null 2>&1 && continue
    if apt-cache show "\$n" >/dev/null 2>&1; then
      oc_names="\$oc_names \$n"
      oc_n=\$((oc_n + 1))
    else
      printf '::oc skipped %s\\n' "\$n"
    fi
  done
  if [ -n "\$oc_names" ]; then oc_apt_install \$oc_names || exit 81; fi
  printf '::oc restored packages %s\\n' "\$oc_n"
fi
if [ -s "\$R/npm-global.json" ] && command -v node >/dev/null 2>&1 && command -v npm >/dev/null 2>&1; then
  oc_stage "Installing Node tools"
  node -e 'const d=JSON.parse(require("fs").readFileSync(process.argv[1],"utf8"));for(const n of Object.keys(d.dependencies||{}))if(!/^(npm|corepack)\$/.test(n))console.log(n)' "\$R/npm-global.json" > "\$R/npm.txt" 2>/dev/null || :
  oc_n=0
  while IFS= read -r n; do
    case "\$n" in *[!a-z0-9@/._-]*|"") continue ;; esac
    npm ls -g --depth=0 "\$n" >/dev/null 2>&1 && continue
    npm install -g "\$n" >/dev/null 2>&1 && oc_n=\$((oc_n + 1))
  done < "\$R/npm.txt"
  printf '::oc restored node %s\\n' "\$oc_n"
fi
if [ -s "\$R/pipx.txt" ] && command -v pipx >/dev/null 2>&1; then
  oc_stage "Installing Python tools"
  oc_n=0
  while IFS=' ' read -r n v; do
    case "\$n" in *[!A-Za-z0-9._-]*|"") continue ;; esac
    pipx install "\$n" >/dev/null 2>&1 && oc_n=\$((oc_n + 1))
  done < "\$R/pipx.txt"
  printf '::oc restored python %s\\n' "\$oc_n"
fi
printf '::oc percent 70\\n'
if [ -f "\$R/home.tar.gz" ]; then
  oc_stage "Putting back settings files"
  oc_unpack "\$R/home.tar.gz" -C ${_q(paths.home)} --backup=simple --suffix=.before-restore || exit 82
  printf '::oc restored settings %s\\n' "\$(oc_list "\$R/home.tar.gz" | grep -vc '/\$')"
fi
if [ -f "\$R/services.tar.gz" ]; then
  mkdir -p ${_q(paths.services)}
  oc_unpack "\$R/services.tar.gz" -C ${_q(paths.services)} || exit 83
  printf '::oc restored services %s\\n' "\$(oc_list "\$R/services.tar.gz" | grep -c '^defs/.*\\.svc\$')"
fi
if [ -f "\$R/projects.tar.gz" ]; then
  oc_stage "Putting back projects"
  T="\$R/projects.tmp"
  rm -rf "\$T" && mkdir -p "\$T" $p || exit 84
  oc_unpack "\$R/projects.tar.gz" -C "\$T" || exit 84
  oc_n=0
  for d in "\$T"/* "\$T"/.[!.]*; do
    [ -e "\$d" ] || [ -L "\$d" ] || continue
    name=\${d##*/}
    dest=$p/"\$name"
    if [ -e "\$dest" ] || [ -L "\$dest" ]; then
      dest=$p/"\$name (restored)"
      i=1
      while [ -e "\$dest" ] || [ -L "\$dest" ]; do
        i=\$((i + 1))
        dest=$p/"\$name (restored \$i)"
      done
    fi
    mv "\$d" "\$dest" || exit 85
    oc_n=\$((oc_n + 1))
  done
  printf '::oc restored projects %s\\n' "\$oc_n"
fi
printf '::oc percent 100\\n'
rm -rf "\$R"
''';
}

/// The whole job scripts (the frame takes the job lock and the time limit).
String linuxBackupJobScript(LinuxBackupKind kind) =>
    linuxJobFrame(LinuxJobKind.backup, linuxBackupBody(kind));

String linuxRestoreJobScript() =>
    linuxJobFrame(LinuxJobKind.restore, linuxRestoreBody());

/// What a backup says about itself.
@immutable
class LinuxBackupManifest {
  const LinuxBackupManifest({
    required this.kind,
    required this.created,
    this.format = linuxBackupFormat,
    this.appVersion = '',
    this.ubuntu = '24.04',
    this.abi = '',
    this.entries = const [],
  });

  final int format;
  final LinuxBackupKind kind;
  final DateTime created;
  final String appVersion;
  final String ubuntu;
  final String abi;

  /// The other zip entries it holds.
  final List<String> entries;

  bool get hasProjects => entries.contains('projects.tar.gz');

  String encode() => const JsonEncoder.withIndent('  ').convert({
    'format': format,
    'kind': kind.name,
    'created': created.toUtc().toIso8601String(),
    'app': appVersion,
    'ubuntu': ubuntu,
    'abi': abi,
    'entries': entries,
  });

  /// Null when [text] is not a backup this app can restore.
  static LinuxBackupManifest? decode(String text) {
    final Object? raw;
    try {
      raw = jsonDecode(text);
    } on FormatException {
      return null;
    }
    if (raw is! Map) return null;
    final format = raw['format'];
    if (format is! int || format < 1 || format > linuxBackupFormat) return null;
    final kindName = raw['kind'];
    final kind = LinuxBackupKind.values
        .where((k) => k.name == kindName)
        .firstOrNull;
    final created = DateTime.tryParse('${raw['created']}');
    if (kind == null || created == null) return null;
    final entries = raw['entries'];
    return LinuxBackupManifest(
      format: format,
      kind: kind,
      created: created,
      appVersion: '${raw['app'] ?? ''}',
      ubuntu: '${raw['ubuntu'] ?? ''}',
      abi: '${raw['abi'] ?? ''}',
      entries: [
        if (entries is List)
          for (final e in entries)
            if (e is String && linuxBackupEntries.contains(e)) e,
      ],
    );
  }
}

/// "opencode-linux-setup-2026-11-01.zip" or "…-setup-projects-…".
String linuxBackupFileName(LinuxBackupKind kind, DateTime now) {
  String two(int n) => n.toString().padLeft(2, '0');
  final day = '${now.year}-${two(now.month)}-${two(now.day)}';
  final what = switch (kind) {
    LinuxBackupKind.setup => 'setup',
    LinuxBackupKind.setupAndProjects => 'setup-projects',
  };
  return 'opencode-linux-$what-$day.zip';
}

/// What the restore job's log says came back.
@immutable
class LinuxRestoreSummary {
  const LinuxRestoreSummary({this.counts = const {}, this.skipped = const []});

  /// `packages`, `node`, `python`, `settings`, `services`, `projects`.
  final Map<String, int> counts;

  /// Packages Ubuntu no longer has.
  final List<String> skipped;

  static LinuxRestoreSummary parse(String log) {
    final counts = <String, int>{};
    final skipped = <String>[];
    for (final line in log.split('\n')) {
      if (line.startsWith('::oc restored ')) {
        final words = line.substring(14).trim().split(' ');
        if (words.length == 2) {
          final n = int.tryParse(words[1]);
          if (n != null) counts[words[0]] = n;
        }
      } else if (line.startsWith('::oc skipped ')) {
        skipped.add(line.substring(13).trim());
      }
    }
    return LinuxRestoreSummary(counts: counts, skipped: skipped);
  }
}

/// The `::oc backup-size` the backup job reported; null when absent.
int? parseBackupSize(String log) {
  for (final line in log.split('\n').reversed) {
    if (line.startsWith('::oc backup-size ')) {
      return int.tryParse(line.substring(17).trim());
    }
  }
  return null;
}
