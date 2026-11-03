@@ROOTS@@
job="$HOME/.oc/migration/@@JOB@@"
[ ! -L "$HOME/.oc" ] && [ ! -L "$HOME/.oc/migration" ] && [ ! -L "$job" ] || fail unsupportedEntry
mkdir -p "$job"; chmod 700 "$HOME/.oc/migration" "$job"
archive="$job/@@ITEM@@.tar"
for candidate in "$job/packing.lock" "$job/cancel" "$archive" "$archive.ready" "$archive.ready.tmp" "$archive.part" "$archive.list" "$archive.links"; do
  [ ! -L "$candidate" ] || fail unsupportedEntry
  [ ! -e "$candidate" ] || [ -f "$candidate" ] || fail unsupportedEntry
  [ ! -e "$candidate" ] || [ "$(stat -c %h "$candidate")" = 1 ] || fail unsupportedEntry
done
# Resume clears a cancellation marker only after the previous pack released
# this job lock. The previous command may outlive the bridge callback.
exec 9>"$job/packing.lock"; flock -n 9 || fail sourceBusy
rm -f "$job/cancel"

if [ -f "$archive.ready" ]; then
  cat "$archive.ready"
  exit 0
fi
select_item @@ITEM@@ || fail sourceChanged
measure_item
# USTAR overhead and final padding, conservatively reserved before writing.
[ $((bytes + count * 1024 + 10240)) -le @@MAX@@ ] || fail tooLarge
free=$(df -Pk "$job" | awk 'END {printf "%.0f", $4 * 1024}')
[ "$free" -ge $((bytes + count * 1024 + 10240)) ] || fail storage
# Ordinary entries go in as they are. The link2symlink links are archived as
# regular files holding their target's contents (--dereference applies to that
# list only).
: > "$archive.list"; : > "$archive.links"
for path in "${paths[@]}"; do
  [ -e "$path" ] || continue
  while IFS= read -r -d '' entry; do
    if is_l2s_link "$entry"; then printf '%s\0' "$entry" >> "$archive.links"
    else printf '%s\0' "$entry" >> "$archive.list"; fi
  done < <(find "$path" -print0)
done
# All regular hardlinks become independent regular entries, never links.
# ulimit bounds a concurrently growing source. tar detects changed files.
ulimit -f $(((@@MAX@@ + 1023) / 1024))
if ! tar --format=ustar --hard-dereference --no-recursion --null -T "$archive.list" -cf "$archive.part" >/dev/null 2>&1; then fail sourceChanged; fi
if [ -s "$archive.links" ]; then
  if ! tar --format=ustar --dereference --no-recursion --null -T "$archive.links" -rf "$archive.part" >/dev/null 2>&1; then fail linkedFileUnreadable; fi
fi
rm -f "$archive.links"
[ ! -f "$job/cancel" ] || fail cancelled
size=$(stat -c %s "$archive.part")
[ "$size" -le @@MAX@@ ] || fail tooLarge
hash=$(sha256sum "$archive.part"); hash=${hash%% *}
mv "$archive.part" "$archive"
printf 'archive|%s|%s\n' "$size" "$hash" > "$archive.ready.tmp"
mv "$archive.ready.tmp" "$archive.ready"
cat "$archive.ready"
