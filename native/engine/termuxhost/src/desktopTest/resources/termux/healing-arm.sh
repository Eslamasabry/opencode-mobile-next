{{functions}}
CURRENT_OPERATION=original
write_state failed synthetic 4096 proot '' '' '' crash
printf '123 456\n' > "$SERVER_PID"
kill() { [ '{{scenario}}' = live-pid ]; }
recovery_port_busy() { [ '{{scenario}}' = port-busy ]; }
case '{{scenario}}' in
  recovery) write_state failed synthetic 4096 proot '' '' '' recovery ;;
  stopped) write_state stopped synthetic 4096 proot ;;
  setup-failed) write_state failed synthetic 4096 proot ;;
  different-port) write_state failed synthetic 4097 proot '' '' '' crash ;;
  switch-pending) touch "$SWITCH_FILE" ;;
esac
recovery_arm permit
[ "$(cat "$RECOVERY_PERMIT")" = permit ]
