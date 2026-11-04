fail() { printf 'failure|%s\n' "$1"; exit 0; }
job="$HOME/.oc/migration/@@JOB@@"
archive="$job/@@ITEM@@.tar"
for directory in "$HOME/.oc" "$HOME/.oc/migration" "$job"; do
  [ -d "$directory" ] && [ ! -L "$directory" ] || fail unsupportedEntry
done
for candidate in "$archive" "$archive.ready" "$job/cancel"; do
  [ ! -L "$candidate" ] || fail unsupportedEntry
  [ ! -e "$candidate" ] || [ -f "$candidate" ] || fail unsupportedEntry
done
[ ! -f "$HOME/.oc/migration/@@JOB@@/cancel" ] || fail cancelled
[ -f "$archive.ready" ] || fail sourceChanged
[ "$(cat "$archive.ready")" = 'archive|@@BYTES@@|@@SHA@@' ] || fail sourceChanged
dd if="$archive" bs=@@CHUNK@@ skip=@@SKIP@@ count=1 2>/dev/null | curl --silent --fail --max-time 30 --connect-timeout 3 --request PUT --header 'Authorization: Bearer @@TOKEN@@' --header 'x-offset: @@OFFSET@@' --data-binary @- 'http://127.0.0.1:@@PORT@@/archive' >/dev/null 2>&1 || fail unavailable
printf 'copied\n'
