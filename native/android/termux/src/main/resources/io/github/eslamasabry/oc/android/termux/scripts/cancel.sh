job="$HOME/.oc/migration/@@JOB@@"
[ ! -L "$HOME/.oc" ] && [ ! -L "$HOME/.oc/migration" ] && [ ! -L "$job" ] || exit 1
mkdir -p "$job"
[ ! -L "$job/cancel" ] || exit 1
if [ ! -e "$job/cancel" ]; then (set -C; : > "$job/cancel"); fi
printf 'cancelled\n'
