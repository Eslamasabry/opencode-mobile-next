proot-distro() {
  [ "$1" = login ] && [ "$2" = opencode-ubuntu ] &&
    [ "$3" = -- ] && [ "$5" = --version ] || return 83
  case "$4" in
    opencode) printf '1.18.29\r\n' ;;
    opencode2) printf '%s\r\n' "${MOCK_OC2_VERSION:-opencode2 v0.0.0-beta-18600}" ;;
    *) return 84 ;;
  esac
}
[ "$(runtime_version)" = 1.18.29 ] || exit 85
printf opencode2 > "$RUNTIME_FILE"
[ "$(runtime_version)" = 0.0.0-beta-18600 ] || exit 86
# The stable line names itself `opencode`, not `opencode2`.
MOCK_OC2_VERSION='opencode v2.2.5'
[ "$(runtime_version)" = 2.2.5 ] || exit 89
printf unsupported > "$RUNTIME_FILE"
if runtime_version; then exit 87; fi
if write_state preparing bad 4096; then exit 88; fi
