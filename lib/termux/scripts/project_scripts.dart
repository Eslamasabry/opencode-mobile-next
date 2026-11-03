import '../../domain/workspace_paths.dart';
import 'manager_script.dart';
import 'script_support.dart';

/// Shell that creates `/root/projects/<name>` inside the managed container
/// and prints its absolute path. [name] must already pass
/// `projectFolderNameProblem`, so it is a single safe path segment.
///
/// A new folder becomes a git project with one empty first commit, as the
/// in-app server's new projects are (BuiltinLinux.createFolderScript): AI
/// Team refuses a folder that is not a git repository, so a project made
/// while turning it on failed at once ("not a git repository", found with
/// issue #112). Best effort: the folder exists either way. A folder that
/// is there already is left as it is.
String termuxCreateProjectFolderScript(String name) =>
    '''
set -eu
timeout -k 2s 30s proot-distro login opencode-ubuntu -- sh -c '
set -eu
name="\$1"
case "\$name" in ""|.|..|*/*|.*) echo "invalid-folder-name" >&2; exit 64 ;; esac
dir="$managedProjectsDirectory/\$name"
if [ ! -e "\$dir" ]; then
  mkdir -p "\$dir"
  if command -v git >/dev/null 2>&1 && git init -q "\$dir"; then
    gname=\$(git config user.name || echo "AI Team")
    gmail=\$(git config user.email || echo "aiteam@localhost")
    (cd "\$dir" && git -c user.name="\$gname" -c user.email="\$gmail" commit -q --allow-empty -m "First commit") || true
  fi
fi
test -d "\$dir"
printf "%s\\n" "\$dir"
' -- '$name'
''';

/// Grant/revoke only this app's managed-server recovery permit.
String termuxRecoveryControlScript(String token, {required bool enable}) {
  if (!RegExp(r'^[a-zA-Z0-9_-]{1,64}$').hasMatch(token)) {
    throw ArgumentError.value(token, 'token');
  }
  return '''
set -eu
MANAGER="$termuxManagerPath"
[ -x "\$MANAGER" ] || { echo 'managed-server-missing' >&2; exit 75; }
${enable ? '[ ! -e "$termuxHomeDirectory/.oc/setup.lock" ] || exit 75' : ''}
manager_tmp="\$MANAGER.tmp.\$\$"
cat > "\$manager_tmp" <<'OC_MANAGER_EOF'
$termuxManagerScript
OC_MANAGER_EOF
chmod 700 "\$manager_tmp"
mv "\$manager_tmp" "\$MANAGER"
exec "\$MANAGER" ${enable ? 'recovery-arm' : 'recovery-disarm'} '$token'
''';
}
