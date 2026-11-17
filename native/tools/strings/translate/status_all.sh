#!/usr/bin/env bash
# Word status of every offered language (the app's language list: AppLanguage). One line each.
# Gates: ar es fr id pt ru vi zh must read 0 missing and 0 changed (exit 1 otherwise).
# Japanese is reported as "ja: N missing" and does not gate yet (a full pass is still to be done).
# Usage, from native/:  tools/strings/translate/status_all.sh
set -u
here=$(cd "$(dirname "$0")" && pwd)
fail=0
for l in ar es fr id pt ru vi zh ja; do
  line=$(python3 "$here/translate.py" status --locales "$l" 2>&1 | grep -m1 -E "keys translated") || { echo "$l: status failed"; fail=1; continue; }
  missing=$(sed -E 's/.* ([0-9]+) missing.*/\1/' <<<"$line"); changed=$(sed -E 's/.* ([0-9]+) changed.*/\1/' <<<"$line")
  if [ "$l" = ja ]; then echo "ja: $missing missing, $changed changed (not gating yet)"; continue; fi
  echo "$line"
  { [ "$missing" != 0 ] || [ "$changed" != 0 ]; } && fail=1
done
exit $fail
