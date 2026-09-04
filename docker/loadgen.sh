#!/usr/bin/env bash
# Continuous, weighted query mix against myDB so the monitor sees real workload.
# Started only with:  docker compose --profile demo up
set -u

MYSQL_ARGS=(-h mysql -u fares -pfares --connect-timeout=5 myDB)
WORKERS=4

run() { mysql "${MYSQL_ARGS[@]}" -N -e "$1" >/dev/null 2>&1; }

# a transaction that holds a row lock for a while -> LONG_RUNNING_TRANSACTION
long_txn() {
  mysql "${MYSQL_ARGS[@]}" >/dev/null 2>&1 <<SQL
START TRANSACTION;
SELECT * FROM orders WHERE id = $((RANDOM % 80000 + 1)) FOR UPDATE;
DO SLEEP(45);
COMMIT;
SQL
}

worker() {
  local i=0
  while true; do
    i=$((i + 1))
    local r=$((RANDOM % 100))
    if   [ "$r" -lt 55 ]; then
      run "SELECT id, name, email FROM customers WHERE id = $((RANDOM % 20000 + 1))"
    elif [ "$r" -lt 72 ]; then
      # full scan - orders.customer_id is not indexed
      run "SELECT COUNT(*), COALESCE(SUM(total), 0) FROM orders WHERE customer_id = $((RANDOM % 20000 + 1))"
    elif [ "$r" -lt 84 ]; then
      # join + on-disk temp table
      run "SELECT o.id, SUM(oi.qty * oi.price) v FROM orders o JOIN order_items oi ON oi.order_id = o.id WHERE o.status = 'pending' GROUP BY o.id ORDER BY v DESC LIMIT 20"
    elif [ "$r" -lt 92 ]; then
      run "INSERT INTO orders (customer_id, status, total, created_at) VALUES ($((RANDOM % 20000 + 1)), 'pending', $((RANDOM % 500 + 10)), NOW())"
    elif [ "$r" -lt 97 ]; then
      run "SELECT SLEEP(0.3)"
    else
      run "SELECT p.name, COUNT(*) c FROM order_items oi JOIN products p ON p.id = oi.product_id GROUP BY p.name ORDER BY c DESC LIMIT 10"
    fi
    [ $((i % 250)) -eq 0 ] && long_txn &
    sleep "0.$((RANDOM % 6 + 2))"
  done
}

echo "loadgen: waiting for the seed data..."
until run "SELECT 1 FROM order_items LIMIT 1"; do sleep 3; done
echo "loadgen: seed present, starting $WORKERS workers"

for _ in $(seq 1 "$WORKERS"); do worker & done
wait
