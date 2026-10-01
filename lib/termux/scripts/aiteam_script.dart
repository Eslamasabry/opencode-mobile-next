import '../../domain/workspace_paths.dart';
import '../team_scripts.dart';
import 'script_support.dart';

/// The verbs `aiteam.sh` runs detached from the bridge shell (their
/// progress is read back through `status`).
const termuxAiteamDetachedVerbs = {
  'install',
  'init',
  'start',
  'stop',
  'remove',
};

/// The last [lines] of the AI Team live log (`~/.oc/aiteam/aiteam.log`),
/// or nothing when no verb has run yet. Read inline: no script rewrite,
/// no verb lock, so the setup screen can poll it beside `status`.
String termuxAiteamLogTailScript({int lines = 200}) {
  if (lines < 1 || lines > 5000) {
    throw ArgumentError.value(lines, 'lines');
  }
  return 'tail -n $lines "\$HOME/.oc/aiteam/aiteam.log" 2>/dev/null || true\n';
}

/// The project folders of the managed server (`/root/projects/*` inside
/// the rootfs), one name per line, in either proot-distro layout; empty
/// when the rootfs is missing or has no projects yet.
String termuxAiteamProjectsScript() =>
    '''
base="\${PREFIX:-/data/data/com.termux/files/usr}/var/lib/proot-distro"
for rootfs in "\$base/containers/opencode-ubuntu/rootfs" "\$base/installed-rootfs/opencode-ubuntu"; do
  if [ -d "\$rootfs$managedProjectsDirectory" ]; then
    for p in "\$rootfs$managedProjectsDirectory"/*/; do
      [ -d "\$p" ] || continue
      case "\$p" in *.git/) continue ;; esac
      basename "\$p"
    done
    break
  fi
done
''';

/// The bridge shell for one `aiteam.sh` verb: rewrites the script and the
/// pinned downloads from this build, then either runs the verb inline
/// (`status`, `log`) or queues it and launches it detached in its own
/// process group, printing `aiteam-started:<pid>` (or
/// `aiteam-busy:<verb>:<pid>` with exit 75 while another verb still runs).
///
/// `init <project>` also gets the team's script for that project
/// ([TermuxTeamScripts.rigFile]; `--rig` names it), written to
/// `~/.oc/aiteam/rig.sh`. [pinsFile] replaces the pins in tests.
String termuxAiteamVerbScript(
  String verb, {
  List<String> args = const [],
  String? pinsFile,
}) {
  if (!RegExp(r'^[a-z]+$').hasMatch(verb)) {
    throw ArgumentError.value(verb, 'verb');
  }
  for (final arg in args) {
    if (arg.contains('\n') || arg.contains('\x00')) {
      throw ArgumentError.value(arg, 'args', 'Must be a single line.');
    }
  }
  final pins = pinsFile ?? TermuxTeamScripts.pinsFile();
  String? rig;
  if (verb == 'init' && args.isNotEmpty) {
    final project = TermuxTeamScripts.ubuntuPath(args.first);
    final named = args.indexOf('--rig');
    if (project.startsWith('/')) {
      rig = TermuxTeamScripts.rigFile(
        project,
        rig: named >= 0 && named + 1 < args.length ? args[named + 1] : null,
      );
    }
  }
  for (final (text, end) in [
    (pins, 'OC_AITEAM_PINS_EOF'),
    (rig ?? '', 'OC_AITEAM_RIG_EOF'),
  ]) {
    if (text.split('\n').contains(end)) {
      throw ArgumentError.value(text, 'script', 'Holds $end.');
    }
  }
  final quotedArgs = args.map(shellQuote).join(' ');
  final buffer = StringBuffer('''
set -eu
OC_DIR="\$HOME/.oc"
AITEAM="\$OC_DIR/aiteam.sh"
mkdir -p "\$OC_DIR/aiteam"
umask 077
aiteam_tmp="\$AITEAM.tmp.\$\$"
cat > "\$aiteam_tmp" <<'OC_AITEAM_EOF'
${TermuxTeamScripts.aiteamScript}
OC_AITEAM_EOF
chmod 700 "\$aiteam_tmp"
mv "\$aiteam_tmp" "\$AITEAM"
[ -x "\$AITEAM" ] || {
  echo 'aiteam-install-failed' >&2
  exit 74
}
pins_tmp="\$OC_DIR/aiteam-pins.tmp.\$\$"
cat > "\$pins_tmp" <<'OC_AITEAM_PINS_EOF'
${pins}OC_AITEAM_PINS_EOF
mv "\$pins_tmp" "\$OC_DIR/aiteam-pins"
''');
  if (rig != null) {
    buffer.write('''
rig_tmp="\$OC_DIR/aiteam/rig.sh.tmp.\$\$"
cat > "\$rig_tmp" <<'OC_AITEAM_RIG_EOF'
${rig}OC_AITEAM_RIG_EOF
mv "\$rig_tmp" "\$OC_DIR/aiteam/rig.sh"
''');
  }
  if (!termuxAiteamDetachedVerbs.contains(verb)) {
    buffer.write('exec bash "\$AITEAM" $verb $quotedArgs\n');
    return buffer.toString();
  }
  buffer.write('''
bash "\$AITEAM" queue '$verb' || exit \$?
set -m
nohup bash "\$AITEAM" '$verb' $quotedArgs >/dev/null 2>&1 </dev/null &
echo "aiteam-started:\$!"
''');
  return buffer.toString();
}
