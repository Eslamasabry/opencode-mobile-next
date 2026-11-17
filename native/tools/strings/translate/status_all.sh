#!/usr/bin/env bash
# Word status of every offered language (the app's language list: AppLanguage). One line each.
# Gates: ar es fr id pt ru vi zh ja must read 0 missing, 0 changed and 0 untracked (exit 1 otherwise).
# Usage, from native/:  tools/strings/translate/status_all.sh
set -u
here=$(cd "$(dirname "$0")" && pwd)
fail=0
for l in ar es fr id pt ru vi zh ja; do
  line=$(python3 "$here/translate.py" status --locales "$l" 2>&1 | grep -m1 -E "keys translated") || { echo "$l: status failed"; fail=1; continue; }
  missing=$(sed -E 's/.* ([0-9]+) missing.*/\1/' <<<"$line")
  changed=$(sed -E 's/.* ([0-9]+) changed.*/\1/' <<<"$line")
  untracked=$(sed -E 's/.* ([0-9]+) untracked.*/\1/' <<<"$line")
  echo "$line"
  { [ "$missing" != 0 ] || [ "$changed" != 0 ] || [ "$untracked" != 0 ]; } && fail=1
done
exit $fail
