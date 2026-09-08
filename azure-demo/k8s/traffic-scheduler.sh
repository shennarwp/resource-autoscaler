#!/bin/bash
# traffic-scheduler.sh
# Sustained load runner for nginx-busy during the app's "peak" window.
# By default matches the backend peak-hours stats: Mon-Fri 07:00-17:59 UTC.
# Loops forever until killed. Log lines go to stdout.
#
# Overrides (env):
#   SCHEDULE_TZ=utc|local   timezone to judge the window (default: utc)
#   SCHEDULE_FROM=7         start hour (24h)
#   SCHEDULE_TO=18          end hour, exclusive (stop when hour reaches this)
#   RUN_DURATION=600        seconds of load per block
#   IDLE_SECONDS=300        gap between load blocks
set -u

FROM=${SCHEDULE_FROM:-7}
TO=${SCHEDULE_TO:-18}
RUN_DURATION=${RUN_DURATION:-600}
IDLE_SECONDS=${IDLE_SECONDS:-300}
MODE=${SCHEDULE_TZ:-utc}

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TRAFFIC="$SCRIPT_DIR/traffic-k8s.sh"

now_hour() { [ "$MODE" = "local" ] && date +%H || date -u +%H; }
now_dow()  { [ "$MODE" = "local" ] && date +%u || date -u +%u; }

in_window() {
  local dow hour
  dow=$(now_dow)
  hour=$(now_hour)
  [ "$dow" -ge 1 ] && [ "$dow" -le 5 ] && [ "$hour" -ge "$FROM" ] && [ "$hour" -lt "$TO" ]
}

echo "[scheduler] started mode=$MODE Mon-Fri ${FROM}:00-${TO}:00, load blocks of ${RUN_DURATION}s (dow=$(now_dow), hour=$(now_hour))"
while true; do
  if in_window; then
    echo "[$(date -Is)] in window -> running load for ${RUN_DURATION}s"
    "$TRAFFIC" "$RUN_DURATION" 10 || true
    echo "[$(date -Is)] load block done, sleeping ${IDLE_SECONDS}s"
    sleep "$IDLE_SECONDS"
  else
    echo "[$(date -Is)] outside window (dow=$(now_dow), hour=$(now_hour)) -> sleeping 5m"
    sleep 300
  fi
done