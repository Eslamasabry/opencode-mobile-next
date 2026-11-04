@@ROOTS@@
free=$(df -Pk "$HOME" | awk 'END {printf "%.0f", $4 * 1024}')
[[ "$free" =~ ^[0-9]+$ ]] || fail storage
printf 'free|%s\n' "$free"
for item in projects config sessions gitConfig shellFiles aiTeam; do
  metadata=$(
    if select_item "$item"; then
      measure_item
      printf 'item|%s|%s|%s\n' "$item" "$bytes" "$count"
    else printf 'item|%s|0|0\n' "$item"; fi
  )
  case "$metadata" in
    'failure|'*) printf 'problem|%s|%s\n' "$item" "${metadata#failure|}" ;;
    *) printf '%s\n' "$metadata" ;;
  esac
done
if [ -f "$HOME/.oc/opencode2-data-mode" ] && [ ! -L "$HOME/.oc/opencode2-data-mode" ] && grep -qxF isolated "$HOME/.oc/opencode2-data-mode"; then printf 'isolated|1\n'; fi
