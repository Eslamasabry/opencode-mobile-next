if [ "${1:-}" = reopened ]; then
  status
  exit 0
fi
printf 'started_at=100\n' > "$STATE"
for phase in preparing installing_dependencies installing_ubuntu installing_opencode refreshing_models starting_server ready; do
  write_state "$phase" 'Clock probe' 4096
  [ "$(read_state_value started_at)" = 100 ] || exit 81
done
# A fresh status shell detects missing server ownership and writes failed.
# That transition must retain the operation clock too.
bash "$0" reopened
