/// Shell prologue the setup runs share.
const termuxSetupBaseScript = r'''set -euo pipefail
export DEBIAN_FRONTEND=noninteractive
if command -v proot-distro >/dev/null 2>&1 &&
   proot-distro login opencode-ubuntu -- /bin/true >/dev/null 2>&1; then exit 0; fi
apt-get update
apt-get -y --no-remove -o Dpkg::Options::="--force-confold" --fix-broken install
apt-get -y --no-remove -o Dpkg::Options::="--force-confold" install proot-distro curl openssl
case "$(uname -m)" in
  aarch64|arm64) arch=arm64; sha=04207713ece899c3740823d33690441ad3a7f0ded1101aca744e2b0f37ac7ff2 ;;
  arm|armv7l|armv8l) arch=armhf; sha=991520b47f6586f38a78505cf016e300b6191bb8ff86a0723481ec23a37ab7f4 ;;
  x86_64|amd64) arch=amd64; sha=c1e67ef7b17a6300e136118bd1dc04725009cb376c1aad10abcf8cd453628d58 ;;
  *) exit 64 ;;
esac
mkdir -p "$HOME/.oc/setup-v2"
archive="$HOME/.oc/setup-v2/ubuntu-base.tar.gz"
root="$PREFIX/var/lib/proot-distro/installed-rootfs/opencode-ubuntu"
# Never erase an existing container, including one from an interrupted setup.
# A damaged extraction needs explicit recovery rather than risking projects.
[ ! -d "$root" ] &&
  [ ! -d "$PREFIX/var/lib/proot-distro/containers/opencode-ubuntu/rootfs" ] || exit 65
curl --fail --location --retry 5 --connect-timeout 20 \
  "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.4-base-$arch.tar.gz" -o "$archive"
printf '%s  %s\n' "$sha" "$archive" | sha256sum -c -
proot-distro install "$archive" --name opencode-ubuntu
proot-distro login opencode-ubuntu -- /bin/true
rm -f "$archive"
''';
