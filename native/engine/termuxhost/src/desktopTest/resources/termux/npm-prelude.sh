set -eu
node() { printf '%s\n' "$MOCK_ARCH"; }
npm() {
  printf '%s\n' "$@" >> "$NPM_CALLS"
  return "$MOCK_NPM_EXIT"
}
