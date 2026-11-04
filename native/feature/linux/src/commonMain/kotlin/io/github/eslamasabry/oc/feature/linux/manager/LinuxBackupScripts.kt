package io.github.eslamasabry.oc.feature.linux.manager

import io.github.eslamasabry.oc.engine.runtime.backup.LinuxBackupKind

/** Backup format 1; authored guest paths and exclusions match the Flutter backup format. */
public object LinuxBackupScripts {
    public val entries: Set<String> =
        setOf(
            "manifest.json",
            "apt-manual.txt",
            "npm-global.json",
            "pipx.txt",
            "home.tar.gz",
            "services.tar.gz",
            "projects.tar.gz",
        )
    private val never =
        listOf(
            "auth.json",
            ".gitconfig",
            ".git-credentials",
            ".netrc",
            "_netrc",
            ".npmrc",
            ".pypirc",
            ".pgpass",
            ".dockercfg",
            "credentials",
            "credentials.json",
            "id_rsa",
            "id_dsa",
            "id_ecdsa",
            "id_ed25519",
            "*.pem",
            "*.key",
            "*.p12",
            "*.pfx",
            "*.jks",
            "*.keystore",
            ".ssh/id_*",
            "*/.ssh/id_*",
            ".gnupg",
            ".aws",
            ".docker",
            ".env",
            ".env.*",
            ".claude",
            ".claude.json",
            ".config/gh",
            ".config/opencode",
            ".local/share/opencode",
            ".oc-opencode2",
            "*/.git/config",
            "*/.git/config.worktree",
        )
    private val skips =
        listOf(
            "node_modules",
            ".venv",
            "venv",
            "__pycache__",
            "build",
            "dist",
            ".dart_tool",
            ".gradle",
            "target",
            ".next",
        )

    public fun backup(kind: LinuxBackupKind, operation: String): String =
        LinuxPackages.jobFrame(
            LinuxJobKind.Backup,
            marker(operation) + body(kind == LinuxBackupKind.SetupAndProjects),
        )

    public fun restore(operation: String): String =
        LinuxPackages.jobFrame(LinuxJobKind.Restore, marker(operation) + restoreBody)

    private fun marker(operation: String): String {
        require(Regex("[a-f0-9-]{36}").matches(operation))
        return "printf '::oc backup-operation $operation\\n'\n"
    }

    private fun body(withProjects: Boolean): String =
        $$"""
S='/root/.oc-builtin/backup/stage'
rm -rf "$S" && mkdir -p "$S" || exit 70
# tar never opens the archive itself: under proot on a phone its own
# open of a new .tar.gz fails ("Function not implemented"), so the shell
# makes the file and gzip writes it. tar's own status is kept: 0, or 1
# when a file changed while it was read.
oc_pack() {
  oc_out=$1
  shift
  { tar -cf - "$@"; echo $? > "$S/.tar-rc"; } | gzip -c > "$oc_out" || return 1
  oc_rc=$(cat "$S/.tar-rc")
  rm -f "$S/.tar-rc"
  [ "$oc_rc" = 0 ] || [ "$oc_rc" = 1 ]
}
cat > "$S/excludes" <<'OC_EXCLUDES'
$${(never + if (withProjects) skips else emptyList()).joinToString("\n")}
OC_EXCLUDES
oc_stage "Listing what is installed"
printf '::oc percent 5\n'
if command -v apt-mark >/dev/null 2>&1; then
  apt-mark showmanual > "$S/apt-manual.txt" 2>/dev/null || :
fi
if command -v npm >/dev/null 2>&1; then
  npm ls -g --depth=0 --json > "$S/npm-global.json" 2>/dev/null || :
fi
if command -v pipx >/dev/null 2>&1; then
  pipx list --short > "$S/pipx.txt" 2>/dev/null || :
fi
oc_stage "Copying settings files"
printf '::oc percent 15\n'
cd '/root' || exit 71
: > "$S/home.list"
for f in '.bashrc' '.bash_profile' '.bash_aliases' '.profile' '.inputrc' '.gitconfig' '.vimrc' '.tmux.conf' '.config/git' '.config/nvim' '.config/htop' '.ssh/authorized_keys' '.ssh/config' '.ssh/known_hosts'; do
  if [ -e "$f" ] || [ -L "$f" ]; then printf '%s\n' "$f" >> "$S/home.list"; fi
done
oc_pack "$S/home.tar.gz" --exclude-from="$S/excludes" -T "$S/home.list" || exit 72
if [ -d '/root/.oc-builtin/svc/defs' ]; then
  # Saved service environment may contain account secrets. Commands are owner data and remain;
  # the page says to review custom commands before sharing a backup.
  V="$S/services.copy"
  mkdir -p "$V/defs" "$V/want" || exit 73
  for f in '/root/.oc-builtin/svc/defs/'*.svc; do
    [ -f "$f" ] && [ ! -L "$f" ] || continue
    n=${f##*/}
    case "$n" in *[!a-z0-9.-]*|.svc) continue ;; esac
    sed '/^env[.]/d' "$f" > "$V/defs/$n" || exit 73
    oc_private=0
    grep -q '^env[.]' "$f" && oc_private=1
    w="/root/.oc-builtin/svc/want/${n%.svc}"
    if [ -f "$w" ] && [ ! -L "$w" ]; then
      W=off
      IFS= read -r W < "$w" || :
      [ "$oc_private" = 1 ] && W=off
      case "$W" in on|off) printf '%s\n' "$W" > "$V/want/${n%.svc}" ;; esac
    fi
  done
  cd "$V" || exit 73
  oc_pack "$S/services.tar.gz" defs want || exit 73
  rm -rf "$V"
fi
printf '::oc percent 30\n'
$${if (withProjects) projectsPart else ""}rm -f "$S/excludes" "$S/home.list"
printf '::oc percent 100\n'
printf '::oc backup-size %s\n' "$(du -sb "$S" | cut -f1)"
"""
            .trimIndent()

    private val projectsPart: String =
        $$"""
        if [ -d '/root/projects' ]; then
          oc_stage "Copying projects"
          # A remote with a password in it stays on the phone too.
          find '/root/projects' -path '*/.git/config' -type f 2>/dev/null | while IFS= read -r f; do
            if grep -Eq '://[^/[:space:]:@]+:[^/[:space:]@]+@' "$f"; then
              printf '%s\n' "${f#'/root/projects'/}" >> "$S/excludes"
            fi
          done
          cd '/root/projects' || exit 74
          oc_pack "$S/projects.tar.gz" --exclude-from="$S/excludes" . || exit 74
        fi
        """
            .trimIndent() + "\n"
    private val restoreBody: String =
        $$"""
        R='/root/.oc-builtin/restore'
        [ -f "$R/manifest.json" ] || exit 80
        # gzip reads, tar works on the stream (see oc_pack in the backup).
        oc_unpack() {
          oc_in=$1
          shift
          gzip -dc "$oc_in" | tar -xf - "$@"
        }
        oc_list() { gzip -dc "$1" | tar -tf -; }
        if [ -s "$R/apt-manual.txt" ] && command -v apt-cache >/dev/null 2>&1; then
          oc_stage "Installing packages"
          oc_names=""
          oc_n=0
          for n in $(cat "$R/apt-manual.txt"); do
            case "$n" in *[!a-z0-9+.:-]*|"") continue ;; esac
            dpkg -s "$n" >/dev/null 2>&1 && continue
            if apt-cache show "$n" >/dev/null 2>&1; then
              oc_names="$oc_names $n"
              oc_n=$((oc_n + 1))
            else
              printf '::oc skipped %s\n' "$n"
            fi
          done
          if [ -n "$oc_names" ]; then oc_apt_install $oc_names || exit 81; fi
          printf '::oc restored packages %s\n' "$oc_n"
        fi
        if [ -s "$R/npm-global.json" ] && command -v node >/dev/null 2>&1 && command -v npm >/dev/null 2>&1; then
          oc_stage "Installing Node tools"
          node -e 'const d=JSON.parse(require("fs").readFileSync(process.argv[1],"utf8"));for(const n of Object.keys(d.dependencies||{}))if(!/^(npm|corepack)$/.test(n))console.log(n)' "$R/npm-global.json" > "$R/npm.txt" 2>/dev/null || :
          oc_n=0
          while IFS= read -r n; do
            case "$n" in *[!a-z0-9@/._-]*|"") continue ;; esac
            npm ls -g --depth=0 "$n" >/dev/null 2>&1 && continue
            npm install -g "$n" >/dev/null 2>&1 && oc_n=$((oc_n + 1))
          done < "$R/npm.txt"
          printf '::oc restored node %s\n' "$oc_n"
        fi
        if [ -s "$R/pipx.txt" ] && command -v pipx >/dev/null 2>&1; then
          oc_stage "Installing Python tools"
          oc_n=0
          while IFS=' ' read -r n v; do
            case "$n" in *[!A-Za-z0-9._-]*|"") continue ;; esac
            pipx install "$n" >/dev/null 2>&1 && oc_n=$((oc_n + 1))
          done < "$R/pipx.txt"
          printf '::oc restored python %s\n' "$oc_n"
        fi
        printf '::oc percent 70\n'
        if [ -f "$R/home.tar.gz" ]; then
          oc_stage "Putting back settings files"
          oc_unpack "$R/home.tar.gz" -C '/root' --backup=simple --suffix=.before-restore || exit 82
          printf '::oc restored settings %s\n' "$(oc_list "$R/home.tar.gz" | grep -vc '/$')"
        fi
        if [ -f "$R/services.tar.gz" ]; then
          mkdir -p '/root/.oc-builtin/svc'
          oc_unpack "$R/services.tar.gz" -C '/root/.oc-builtin/svc' || exit 83
          printf '::oc restored services %s\n' "$(oc_list "$R/services.tar.gz" | grep -c '^defs/.*\\.svc$')"
        fi
        if [ -f "$R/projects.tar.gz" ]; then
          oc_stage "Putting back projects"
          T="$R/projects.tmp"
          rm -rf "$T" && mkdir -p "$T" '/root/projects' || exit 84
          oc_unpack "$R/projects.tar.gz" -C "$T" || exit 84
          oc_n=0
          for d in "$T"/* "$T"/.[!.]*; do
            [ -e "$d" ] || [ -L "$d" ] || continue
            name=${d##*/}
            dest='/root/projects'/"$name"
            if [ -e "$dest" ] || [ -L "$dest" ]; then
              dest='/root/projects'/"$name (restored)"
              i=1
              while [ -e "$dest" ] || [ -L "$dest" ]; do
                i=$((i + 1))
                dest='/root/projects'/"$name (restored $i)"
              done
            fi
            mv "$d" "$dest" || exit 85
            oc_n=$((oc_n + 1))
          done
          printf '::oc restored projects %s\n' "$oc_n"
        fi
        printf '::oc percent 100\n'
        rm -rf "$R"
        """
            .trimIndent()
}
