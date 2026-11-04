fail() { printf 'failure|%s\n' "$1"; exit 0; }
base="$PREFIX/var/lib/proot-distro"
if [ -d "$base/containers/opencode-ubuntu/rootfs" ] && [ -d "$base/installed-rootfs/opencode-ubuntu" ]; then fail unsupportedEntry; fi
if [ -d "$base/containers/opencode-ubuntu/rootfs" ]; then
  root="$base/containers/opencode-ubuntu/rootfs"
elif [ -d "$base/installed-rootfs/opencode-ubuntu" ]; then
  root="$base/installed-rootfs/opencode-ubuntu"
else fail unavailable; fi
[ ! -L "$root" ] && [ ! -L "$root/root" ] || fail unsupportedEntry
home="$root/root"
command -v tar >/dev/null && command -v sha256sum >/dev/null && command -v curl >/dev/null || fail unavailable
# Fixed sources only. Configuration/authentication is exported privately, never
# executed or activated by the transport. The backend chooses import policy.
safe_path() {
  local current= component rest="$1"
  while [ -n "$rest" ]; do
    component=${rest%%/*}
    if [ "$rest" = "$component" ]; then rest=; else rest=${rest#*/}; fi
    current=${current:+$current/}$component
    [ ! -L "$current" ] || fail unsupportedEntry
  done
}
select_item() {
  selected="$1"
  cd "$home"
  case "$1" in
    projects) safe_path projects; cd "$home/projects" 2>/dev/null || return 1; paths=(.) ;;
    config) cd "$home"; paths=(.config/opencode .oc-opencode2/config) ;;
    sessions) cd "$home"; paths=(.local/share/opencode .oc-opencode2/data) ;;
    gitConfig) cd "$home"; paths=(.gitconfig) ;;
    shellFiles) cd "$home"; paths=(.bashrc .bash_profile .profile .zshrc) ;;
    aiTeam)
      files=${PREFIX%/*}; cd "$files"
      relative=${root#"$files/"}
      paths=(home/.oc/aiteam home/.gc home/.dolt "$relative/root/aiteam" "$relative/root/.gc" "$relative/root/.dolt" "$relative/root/.oc-aiteam") ;;

    *) fail invalidSelection ;;
  esac
  for path in "${paths[@]}"; do safe_path "$path"; done
}
# proot-distro's link2symlink turned every Git hard link into a symlink to a
# file in the container's private .l2s store. Those are the only links the
# transport accepts, and only as their target's contents: the target lives in
# Termux storage the app cannot read, so a copied link would dangle.
is_l2s_link() {
  local target
  [ -L "$1" ] || return 1
  target=$(readlink "$1") || return 1
  case "$target" in */proot-distro/*/.l2s/.l2s.*) return 0 ;; esac
  return 1
}
# Reject other links and devices rather than follow them out of an allowed tree.
# Counts and logical bytes are metadata only; find names never leave Termux.
measure_item() {
  count=0; bytes=0
  for path in "${paths[@]}"; do
    [ -e "$path" ] || [ -L "$path" ] || continue
    while IFS= read -r -d '' entry; do
      if is_l2s_link "$entry"; then
        [ -f "$entry" ] && [ -r "$entry" ] || fail linkedFileUnreadable
        size=$(stat -L -c %s "$entry") || fail linkedFileUnreadable
        count=$((count + 1)); bytes=$((bytes + size))
        [ "$count" -le 20000 ] && [ "$bytes" -le 536870912 ] || fail tooLarge
      else fail unsupportedEntry; fi
    done < <(find "$path" ! -type f ! -type d -print0)
    if [ "$selected" = projects ] && [ -n "$(find "$path" -name .git -type f -print -quit)" ]; then fail unsupportedEntry; fi
    while IFS= read -r size; do
      count=$((count + 1)); bytes=$((bytes + size))
      [ "$count" -le 20000 ] && [ "$bytes" -le 536870912 ] || fail tooLarge
    done < <(find "$path" -type f -printf '%s\n')
    dirs=$(find "$path" -type d -printf 'x\n' | wc -l)
    count=$((count + dirs)); [ "$count" -le 20000 ] || fail tooLarge
  done
}
