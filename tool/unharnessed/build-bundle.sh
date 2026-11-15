#!/bin/bash
# GitHub-hosted arm64 container ONLY. Never invoke on an owned machine.
set -euo pipefail
test -f /.dockerenv
test "$(uname -m)" = aarch64
exec > >(tee /facts/bundle-build.log) 2>&1
pin=fe2cf2815f78463464b91c598fc0c31c0af98f4c
export DEBIAN_FRONTEND=noninteractive SOURCE_DATE_EPOCH=315532800 PYTHONDONTWRITEBYTECODE=1
apt-get update -qq
apt-get install -y -qq ca-certificates curl git build-essential pkg-config libssl-dev python3
curl -fLsS https://sh.rustup.rs -o /tmp/rustup.sh
sh /tmp/rustup.sh -y --profile minimal --default-toolchain 1.98.1
export PATH="/root/.cargo/bin:/usr/local/bin:/usr/bin:/bin"
git -c core.hooksPath=/dev/null clone --no-checkout https://github.com/unharnessed-agent/Unharnessed.git /src
git -C /src -c core.hooksPath=/dev/null checkout --detach "$pin"
test "$(git -C /src rev-parse HEAD)" = "$pin"
cd /src
start=$(date +%s)
cargo build --locked --release -p uh-cli --bin unharnessed
mkdir -p /bundle/bin /bundle/wheels
cp target/release/unharnessed /bundle/unharnessed
objcopy --strip-debug /bundle/unharnessed
printf '%s\n' "$(( $(date +%s) - start ))" > /facts/bundle-build-seconds.txt
curl -fLsS https://github.com/astral-sh/uv/releases/download/0.13.8/uv-aarch64-unknown-linux-gnu.tar.gz -o /tmp/uv.tar.gz
printf '%s\n' '74f2f5c6024d397481db228db8fc25eed420db192243f88438c118e7e013e585  /tmp/uv.tar.gz' | sha256sum -c -
tar -xzf /tmp/uv.tar.gz -C /tmp
cp /tmp/uv-aarch64-unknown-linux-gnu/uv /bundle/bin/uv
cp -R unharnessed-runtime skills /bundle/
cp LICENSE README.md /bundle/
cp /probe/adapter.py /probe/bootstrap.py /bundle/
printf '%s\n' '{"name":"unharnessed","version":"0.9.8","piConfig":{"name":"unharnessed","configDir":".unharnessed"}}' > /bundle/package.json
/bundle/bin/uv python install 3.11.18 --install-dir /bundle/python
python=$(find /bundle/python -path '*/bin/python3.11' | head -n 1)
test -n "$python"
/bundle/bin/uv venv /tmp/build-venv --python "$python"
/bundle/bin/uv pip install --python /tmp/build-venv/bin/python pip==26.3
for project in /bundle/unharnessed-runtime /bundle/skills/goal /bundle/skills/attach-image; do
  /bundle/bin/uv build --wheel --out-dir /bundle/wheels "$project"
done
cat > /bundle/requirements.in <<'EOF'
unharnessed-runtime==0.1.0
goal==0.1.0
unharnessed-skill-attach-image==0.1.0
dill==0.4.1
requests
httpx
pyyaml
tomli
python-dotenv
pandas
numpy
scipy
beautifulsoup4
lxml
pydantic
tyro
EOF
if [ -f /probe/requirements.lock ]; then
  cp /probe/requirements.lock /bundle/requirements.lock
else
  /bundle/bin/uv pip compile --python /tmp/build-venv/bin/python --generate-hashes --no-emit-index-url \
    --find-links /bundle/wheels --no-emit-find-links /bundle/requirements.in -o /bundle/requirements.lock
fi
/tmp/build-venv/bin/python -m pip download --require-hashes --only-binary=:all: \
  --find-links /bundle/wheels -r /bundle/requirements.lock -d /bundle/wheels
cp /bundle/requirements.lock /facts/requirements.lock
python3 /probe/manifest.py /bundle
cp /bundle/manifest.json /facts/manifest.json
python3 /probe/package_bundle.py /bundle /facts/unharnessed-arm64.tar.gz
sha256sum /facts/unharnessed-arm64.tar.gz > /facts/bundle-sha256.txt
stat -c %s /facts/unharnessed-arm64.tar.gz > /facts/bundle-download-bytes.txt
python3 -B /probe/test_adapter.py
