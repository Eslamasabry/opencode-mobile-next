if [ "${1:-}" = reopened ]; then
  status
  exit 0
fi
printf opencode2 > "$RUNTIME_FILE"
printf 'started_at=100\n' > "$STATE"
for phase in preparing installing_opencode starting_server ready; do
  write_state "$phase" 'Runtime probe' 4096 proot 0.0.0-beta-18600
  [ "$(read_state_value runtime)" = opencode2 ] || exit 81
done
bash "$0" reopened
