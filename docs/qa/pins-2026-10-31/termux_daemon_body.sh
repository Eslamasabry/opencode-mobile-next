IFS= read -r PASEO_PASSWORD || [ -n "${PASEO_PASSWORD:-}" ] || exit 66
export PASEO_PASSWORD
# Paseo 0.11 and later refuse the old launch flags (`start --foreground
# --listen ... --no-relay --no-web-ui --no-inject-mcp`: "--listen was
# removed"). The same settings live in the config of the daemon home, set before
# each run, and `daemon run` runs it in the foreground.
daemon_set() { paseo daemon config set "$1" "$2" --home "$3" > /dev/null || exit 70; }
daemon_set daemon.listen "$2" "$1"
daemon_set daemon.relay.enabled false "$1"
daemon_set daemon.mcp.injectIntoAgents false "$1"
daemon_set features.webUi.enabled false "$1"
daemon_set features.dictation.enabled false "$1"
daemon_set features.voiceMode.enabled false "$1"
exec paseo daemon run --home "$1" < /dev/null
