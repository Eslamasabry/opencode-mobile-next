{{functions}}
CURRENT_PORT=4096
CURRENT_OPERATION=original
write_state failed 'synthetic crash' 4096 proot '' '' '' crash
printf '%s' permit > "$RECOVERY_PERMIT"
printf '123 456\n' > "$SERVER_PID"
kill() { [ '{{scenario}}' = live ]; }
recovery_port_busy() { [ '{{scenario}}' = port ]; }
case '{{scenario}}' in
  operation) CURRENT_OPERATION=another; write_state failed changed 4096 proot '' '' '' crash ;;
  permit) rm "$RECOVERY_PERMIT" ;;
  setup-failed) write_state failed 'install failed' 4096 ;;
  stopped) write_state stopped stopped 4096 ;;
esac
recovery_preflight original permit
