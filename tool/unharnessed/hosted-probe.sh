#!/bin/bash
# Invocation is exclusively from the GitHub-hosted workflow's fresh container.
# Never invoke this script, the upstream source, or the resulting binary locally.
set -euo pipefail
test -f /.dockerenv
test "$(uname -m)" = aarch64
test -d /probe
test -d /facts
exec > >(tee /facts/probe.log) 2>&1
pin=fe2cf2815f78463464b91c598fc0c31c0af98f4c
printf '%s\n' "$pin" > /facts/upstream-commit.txt
trap 'printf "%s\n" "$?" > /facts/probe-exit-code.txt' EXIT
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq ca-certificates curl git build-essential pkg-config libssl-dev python3
curl --fail --location --proto '=https' --tlsv1.2 \
  https://sh.rustup.rs -o /tmp/rustup.sh
sh /tmp/rustup.sh -y --profile minimal --default-toolchain 1.98.1
export PATH="/root/.cargo/bin:/usr/local/bin:/usr/bin:/bin"
git -c core.hooksPath=/dev/null clone --no-checkout \
  https://github.com/unharnessed-agent/Unharnessed.git /src
git -C /src -c core.hooksPath=/dev/null checkout --detach "$pin"
test "$(git -C /src rev-parse HEAD)" = "$pin"
cd /src
rustc --version > /facts/rust-version.txt
getconf GNU_LIBC_VERSION > /facts/build-libc.txt
start=$(date +%s)
cargo build --release --locked -p uh-cli --bin unharnessed
printf '%s\n' "$(( $(date +%s) - start ))" > /facts/build-seconds.txt
mkdir -p /package
cp target/release/unharnessed /package/unharnessed
objcopy --strip-debug /package/unharnessed
cp -R unharnessed-runtime /package/unharnessed-runtime
sha256sum /package/unharnessed > /facts/binary-sha256.txt
stat -c %s /package/unharnessed > /facts/binary-bytes.txt
objdump -T /package/unharnessed | sed -n 's/.*(GLIBC_\([^)]*\)).*/\1/p' | sort -Vu > /facts/glibc-symbols.txt
curl --fail --location --proto '=https' --tlsv1.2 \
  https://github.com/astral-sh/uv/releases/download/0.13.8/uv-aarch64-unknown-linux-gnu.tar.gz \
  -o /tmp/uv.tar.gz
printf '%s\n' '74f2f5c6024d397481db228db8fc25eed420db192243f88438c118e7e013e585  /tmp/uv.tar.gz' | sha256sum -c -
tar -xzf /tmp/uv.tar.gz -C /tmp
cp /tmp/uv-aarch64-unknown-linux-gnu/uv /usr/local/bin/uv
cp /package/unharnessed /facts/unharnessed
tar -czf /facts/unharnessed-runtime.tar.gz -C /package unharnessed-runtime
# A scratch home plus an explicit environment allowlist: no ambient auth,
# runner tokens, project config, MCP services or developer sign-in files.
mkdir -p /trial/home /trial/project /trial/cache /trial/data
before=$(cat /sys/class/net/eth0/statistics/rx_bytes)
start=$(date +%s)
set +e
env -i HOME=/trial/home PATH=/usr/local/bin:/usr/bin:/bin \
  XDG_CACHE_HOME=/trial/cache XDG_DATA_HOME=/trial/data \
  UV_CACHE_DIR=/trial/cache/uv UV_PYTHON_INSTALL_DIR=/trial/data/python \
  UV_DEFAULT_INDEX=https://pypi.org/simple \
  UNHARNESSED_CODING_AGENT_DIR=/trial/home/agent \
  UNHARNESSED_PACKAGE_DIR=/package \
  timeout --kill-after=10s 900s /package/unharnessed --unharnessed-bootstrap
bootstrap_exit=$?
set -e
printf '%s\n' "$bootstrap_exit" > /facts/bootstrap-exit-code.txt
printf '%s\n' "$(( $(date +%s) - start ))" > /facts/bootstrap-seconds.txt
after=$(cat /sys/class/net/eth0/statistics/rx_bytes)
printf '%s\n' "$(( after - before ))" > /facts/bootstrap-network-rx-bytes.txt
du -sb /trial/home /trial/cache /trial/data /package > /facts/disk-bytes.txt
env -i PATH=/usr/bin:/bin python3 /probe/acp_probe.py
exit "$bootstrap_exit"
