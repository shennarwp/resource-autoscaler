#!/bin/bash
# Generate sustained traffic to the nginx-busy service on the K3s cluster.
# Loops ApacheBench (jordi/ab) in 50k-request chunks until the duration elapses,
# bypassing ab's 50k-request-per-run cap so longer tests run for the full time.
# Usage: ./traffic-k8s.sh [duration_seconds] [concurrency]
#   duration_seconds: how long to run, in seconds (default 600 = 10 min)
#   concurrency:      number of simultaneous requests (default 10)
set -e

if [ -z "$KUBECONFIG" ]; then
  export KUBECONFIG=~/.kube/config-gpd
fi

DURATION=${1:-600}
CONCURRENCY=${2:-10}
URL="http://nginx-busy/"
POD="loadgen-$(date +%s)"

# Remove any stale load pods from previous (aborted) runs
kubectl delete pods -l run=loadgen --ignore-not-found --wait=false 2>/dev/null || true

echo "Sending sustained load to $URL for ${DURATION}s (concurrency $CONCURRENCY)..."

kubectl run "$POD" --rm -i --restart=Never \
  --image=jordi/ab \
  --labels='run=loadgen' \
  --timeout=$((DURATION + 120))s \
  --command -- sh -c \
  'url="http://nginx-busy/"; dur='"$DURATION"'; conc='"$CONCURRENCY"'; start=$(date +%s); n=0; while [ $(( $(date +%s) - start )) -lt $dur ]; do n=$((n+1)); ab -q -n 50000 -c $conc "$url" > /dev/null 2>&1 || true; echo "chunk $n done (elapsed $(( $(date +%s) - start ))s)"; done; echo "LOAD_DONE (total $(( $(date +%s) - start ))s)"' 2>&1

echo "Done."