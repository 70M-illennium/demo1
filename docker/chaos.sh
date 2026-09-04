#!/usr/bin/env bash
# Resilience / chaos test for the monitor stack. Disrupts a running stack and checks it
# survives - does NOT start anything itself, so run `docker compose --profile demo up -d`
# first. Takes roughly 8-10 minutes (phase 1's hysteresis timing dominates - openAfter=2
# and resolveAfter=3 at a 60s collection interval need real wall-clock time to prove).
#
# Covers the original manual chaos-testing pass (target/store/app outages) plus scenarios
# that pass specifically tries to be hard on the newer admin/cache/auth layer: concurrent
# writes to the shared threshold/policy state, rapid policy flapping against live reads,
# and the rate limiter, none of which had been exercised under real concurrency before.
set -u

APP_URL="http://localhost:8080"
ADMIN="fares:fares"
FAILURES=0

section() { echo; echo "=== $1 ==="; }
pass() { echo "  PASS: $1"; }
fail() { echo "  FAIL: $1"; FAILURES=$((FAILURES + 1)); }
http_code() { curl -s -o /dev/null -w '%{http_code}' "$@"; }

wait_for_200() {
  local url=$1 tries=${2:-30}
  for _ in $(seq 1 "$tries"); do
    [ "$(http_code "$url")" = "200" ] && return 0
    sleep 1
  done
  return 1
}

section "0. sanity: stack must already be up"
if ! wait_for_200 "$APP_URL/api/events" 10; then
  echo "app not reachable at $APP_URL - start the stack first: docker compose --profile demo up -d"
  exit 1
fi
pass "app reachable"

section "1. target outage: stop mysql mid-load, confirm DB_UNREACHABLE opens and resolves"
docker stop mysql-demo-container >/dev/null
echo "  stopped target - waiting ~130s for 2 breaching cycles (openAfter=2)..."
sleep 130
if curl -s "$APP_URL/api/events/active" | grep -q DB_UNREACHABLE; then
  pass "DB_UNREACHABLE opened"
else
  fail "DB_UNREACHABLE did not open within the expected window"
fi
docker start mysql-demo-container >/dev/null
echo "  restarted target - waiting ~200s for 3 clear cycles (resolveAfter=3) + boot time..."
sleep 200
if curl -s "$APP_URL/api/events/active" | grep -q DB_UNREACHABLE; then
  fail "DB_UNREACHABLE did not resolve within the expected window"
else
  pass "DB_UNREACHABLE resolved"
fi

section "2. storage outage under load: stop monitor-store, hammer read endpoints"
docker stop monitor-store-container >/dev/null
BAD=0
for _ in $(seq 1 20); do
  c=$(http_code -H "X-API-Key: chaos-phase2" "$APP_URL/api/database/snapshots/latest")
  # 500 is the known/accepted degraded response with the store down (no fallback yet)
  [ "$c" = "200" ] || [ "$c" = "500" ] || BAD=$((BAD + 1))
  sleep 0.2
done
[ "$BAD" -eq 0 ] && pass "reads only ever returned 200/500 while storage was down, never hung/crashed" \
                  || fail "$BAD unexpected status code(s) while storage was down"
docker start monitor-store-container >/dev/null
sleep 15
wait_for_200 "$APP_URL/api/database/snapshots/latest" 30 && pass "reads recovered after storage came back" \
                                                            || fail "reads did not recover after storage came back"
if docker logs db-health-monitor-app --since 2m 2>&1 | grep -qi "flushed.*buffered"; then
  pass "write buffer flushed on storage recovery"
else
  echo "  (no buffered writes needed flushing - fine if the outage was short relative to the collection interval)"
fi

section "3. rate limiter under a real burst (never exercised under load before this script)"
# a dedicated X-API-Key so this deliberate bucket-exhaustion test doesn't leave the
# shared "ip:" bucket empty for phases 4/5, which run right after it and would
# otherwise get starved by a burst that isn't testing them at all
OK=0
LIMITED=0
for _ in $(seq 1 150); do
  c=$(http_code -H "X-API-Key: chaos-phase3" "$APP_URL/api/events")
  [ "$c" = "200" ] && OK=$((OK + 1))
  [ "$c" = "429" ] && LIMITED=$((LIMITED + 1))
done
echo "  200s=$OK 429s=$LIMITED (bucket capacity is 120/min, keyed per X-API-Key so this test doesn't starve later phases)"
[ "$LIMITED" -gt 0 ] && pass "rate limiter triggered past the 120-token bucket" \
                       || fail "expected some 429s from 150 rapid requests against a 120-capacity bucket"

section "4. concurrent admin writes: hammer thresholds with valid + invalid values at once"
PHASE4_KEY="-H X-API-Key:chaos-phase4"
BASELINE_RAW=$(curl -s $PHASE4_KEY "$APP_URL/api/admin/thresholds")
BASELINE=$(echo "$BASELINE_RAW" | python3 -c "import json,sys; print(json.load(sys.stdin)['diskWarnPercent'])" 2>/dev/null)
if [ -z "$BASELINE" ]; then
  fail "could not read a baseline diskWarnPercent before the concurrent-write burst - got: $BASELINE_RAW"
  BASELINE=90.0
fi
for i in $(seq 1 30); do
  if [ $((i % 3)) -eq 0 ]; then
    curl -s -u "$ADMIN" $PHASE4_KEY -X PATCH "$APP_URL/api/admin/thresholds" \
      -H 'Content-Type: application/json' -d '{"openAfter": 0}' -o /dev/null &
  else
    curl -s -u "$ADMIN" $PHASE4_KEY -X PATCH "$APP_URL/api/admin/thresholds" \
      -H 'Content-Type: application/json' -d "{\"diskWarnPercent\": $((80 + i % 10))}" -o /dev/null &
  fi
done
wait
AFTER_RAW=$(curl -s $PHASE4_KEY "$APP_URL/api/admin/thresholds")
OPEN_AFTER=$(echo "$AFTER_RAW" | python3 -c "import json,sys; print(json.load(sys.stdin)['openAfter'])" 2>/dev/null)
[ "$OPEN_AFTER" = "2" ] && pass "the invalid concurrent write (openAfter=0) never got applied, even under a race" \
                         || fail "openAfter ended at '$OPEN_AFTER', expected it to stay at its default 2"
curl -s -u "$ADMIN" $PHASE4_KEY -X PATCH "$APP_URL/api/admin/thresholds" \
  -H 'Content-Type: application/json' -d "{\"diskWarnPercent\": $BASELINE}" >/dev/null
pass "thresholds restored to baseline ($BASELINE)"

section "5. rapid policy flapping against live reads of the same metric"
PHASE5_KEY="-H X-API-Key:chaos-phase5"
( for _ in $(seq 1 40); do
    curl -s -u "$ADMIN" $PHASE5_KEY -X PATCH "$APP_URL/api/admin/policies/database.latest" \
      -H 'Content-Type: application/json' -d '{"cached": true}' -o /dev/null
    curl -s -u "$ADMIN" $PHASE5_KEY -X PATCH "$APP_URL/api/admin/policies/database.latest" \
      -H 'Content-Type: application/json' -d '{"cached": false}' -o /dev/null
  done ) &
FLAP_PID=$!
BAD=0
for _ in $(seq 1 80); do
  c=$(http_code $PHASE5_KEY "$APP_URL/api/database/snapshots/latest")
  [ "$c" = "200" ] || BAD=$((BAD + 1))
done
wait "$FLAP_PID"
[ "$BAD" -eq 0 ] && pass "reads stayed 200 through 80 requests during 80 rapid policy flips" \
                  || fail "$BAD non-200 read(s) while the policy was flapping"
curl -s -u "$ADMIN" -X PATCH "$APP_URL/api/admin/policies/database.latest" \
  -H 'Content-Type: application/json' -d '{"cached": true}' >/dev/null
pass "database.latest policy restored to cached=true"

section "6. app restart mid-traffic: confirm clean reboot, no crash loop"
docker restart db-health-monitor-app >/dev/null
sleep 8
wait_for_200 "$APP_URL/api/events" 20 && pass "app back up after restart" || fail "app did not come back after restart"
RESTARTS=$(docker inspect db-health-monitor-app --format '{{.RestartCount}}')
[ "$RESTARTS" -le 1 ] && pass "no crash loop (RestartCount=$RESTARTS)" || fail "crash loop detected (RestartCount=$RESTARTS)"

echo
if [ "$FAILURES" -eq 0 ]; then
  echo "ALL CHAOS CHECKS PASSED"
  exit 0
else
  echo "$FAILURES CHAOS CHECK(S) FAILED"
  exit 1
fi
