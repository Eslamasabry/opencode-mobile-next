#!/bin/bash
# testlab-bench.sh <startup|frames|profile|all> <service-account.json|-> <out-dir> [device model] [android version]
# Runs the same Macrobenchmark classes on a real Firebase Test Lab device (free daily quota; billing stays off).
# Project id: $HOME/Storage2/tools/testlab-project-id.txt. Pass "-" as the key to use the gcloud login already on this machine.
set -euo pipefail
what=${1:-startup}; key=${2:--}; out=${3:?out dir}; model=${4:-SC-51E}; ver=${5:-36}
cd "$(dirname "$0")/../.."
gcloud=${GCLOUD:-gcloud}; [ -x $HOME/Storage2/tools/google-cloud-sdk/bin/gcloud ] && gcloud=$HOME/Storage2/tools/google-cloud-sdk/bin/gcloud
proj=${TESTLAB_PROJECT:-$(cat $HOME/Storage2/tools/testlab-project-id.txt 2>/dev/null || echo oc-mobile-testlab-8699)}
[ "$key" != "-" ] && "$gcloud" auth activate-service-account --key-file "$key" >/dev/null
T=io.github.eslamasabry.oc.benchmark
classes=()
case "$what" in startup|all) classes+=("$T.StartupBenchmark") ;; esac
case "$what" in frames|all) classes+=("$T.StreamBenchmark") ;; esac
case "$what" in profile|all) classes+=("$T.StartupProfileGenerator") ;; esac
targets=$(IFS=,; echo "${classes[*]}")
app=$(ls androidApp/build/outputs/apk/preview/benchmark/*.apk | head -1)
test=$(ls benchmark/build/outputs/apk/benchmark/*.apk | head -1)
mkdir -p "$out"
res=oc-bench-$(date +%Y%m%d-%H%M%S)
"$gcloud" firebase test android run --project "$proj" --type instrumentation --app "$app" --test "$test" \
  --device "model=$model,version=$ver" --test-targets "class $targets" \
  --environment-variables additionalTestOutputDir=/sdcard/Download,androidx.benchmark.suppressErrors=EMULATOR\\,LOW-BATTERY\\,UNLOCKED \
  --directories-to-pull /sdcard/Download --results-dir "$res" --timeout 30m
bucket=$("$gcloud" firebase test android run --help >/dev/null 2>&1; echo "")
echo "Results: gs://test-lab-*/$res (pull with: gsutil -m cp -r 'gs://test-lab-*/$res/**' $out)"
gsutil=$(dirname "$gcloud")/gsutil
"$gsutil" -m cp -r "gs://test-lab-*/$res/*" "$out/" 2>/dev/null || true
python3 "$(dirname "$0")/bench_summary.py" "$out" > "$out/summary.md" || true
cat "$out/summary.md"
