#!/usr/bin/env bash
# End-to-end walkthrough against the running docker compose stack (through the gateway).
# Usage: ./scripts/demo.sh            happy path: CREATED -> ASSIGNED -> OUT_FOR_DELIVERY -> DELIVERED
#        ./scripts/demo.sh --fail     saga path: 3 failed attempts -> RETURNED
set -euo pipefail

GW=${GW:-http://localhost:8080}
PASS='Passw0rd!'

say()  { printf '\n\033[1;36m== %s\033[0m\n' "$*"; }
login() { curl -sf "$GW/api/auth/login" -H 'Content-Type: application/json' \
            -d "{\"username\":\"$1\",\"password\":\"$PASS\"}" | jq -r .accessToken; }
order_status() { curl -sf "$GW/api/orders/$ORDER_ID" -H "Authorization: Bearer $MERCHANT" | jq -r .status; }
wait_for() {   # wait_for <expected order status>
  for _ in $(seq 1 40); do
    s=$(order_status); [ "$s" = "$1" ] && { echo "order status: $s"; return 0; }; sleep 0.5
  done; echo "timed out waiting for $1 (last: $s)"; exit 1
}

say "1. Login (merchant, ops)"
MERCHANT=$(login merchant.atlas); OPS=$(login ops)
echo "merchant token: ${MERCHANT:0:40}..."

say "2. Merchant creates a COD order for Casablanca"
ORDER=$(curl -sf "$GW/api/orders" -H "Authorization: Bearer $MERCHANT" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: demo-$(date +%s)" -H 'X-Correlation-Id: demo-walkthrough' \
  -d '{"recipientName":"Salma Idrissi","recipientPhone":"+212612345678","addressLine":"12 Rue Atlas, Maarif","city":"Casablanca","codAmount":249.90}')
echo "$ORDER" | jq '{id, trackingCode, status, codAmount}'
ORDER_ID=$(echo "$ORDER" | jq -r .id)

say "3. Outbox -> Kafka -> dispatch assigns a courier by zone -> order-service projects ASSIGNED"
wait_for ASSIGNED
DELIVERY=$(curl -sf "$GW/api/deliveries/$ORDER_ID" -H "Authorization: Bearer $OPS")
echo "$DELIVERY" | jq '{zone, courierId, status, attempts}'
COURIER_ID=$(echo "$DELIVERY" | jq -r .courierId)
case "$COURIER_ID" in
  c0000000-0000-0000-0000-000000000001) COURIER_USER=courier.casa1 ;;
  c0000000-0000-0000-0000-000000000002) COURIER_USER=courier.casa2 ;;
  *) COURIER_USER=ops ;;
esac
COURIER=$(login "$COURIER_USER"); echo "acting as $COURIER_USER"

if [ "${1:-}" = "--fail" ]; then
  for attempt in 1 2 3; do
    say "4.$attempt Courier goes out, customer absent"
    curl -sf -X POST "$GW/api/deliveries/$ORDER_ID/out-for-delivery" -H "Authorization: Bearer $COURIER" | jq -c '{status, attempts}'
    curl -sf -X POST "$GW/api/deliveries/$ORDER_ID/failed" -H "Authorization: Bearer $COURIER" \
      -H 'Content-Type: application/json' -d '{"reason":"customer absent"}' | jq -c '{status, attempts}'
  done
  say "5. Max attempts reached -> compensation: parcel RETURNED to merchant"
  wait_for RETURNED
else
  say "4. Courier picks up the parcel"
  curl -sf -X POST "$GW/api/deliveries/$ORDER_ID/out-for-delivery" -H "Authorization: Bearer $COURIER" | jq -c '{status, attempts}'
  wait_for OUT_FOR_DELIVERY
  say "5. Courier hands it over and collects the cash"
  curl -sf -X POST "$GW/api/deliveries/$ORDER_ID/delivered" -H "Authorization: Bearer $COURIER" | jq -c '{status, attempts}'
  wait_for DELIVERED
fi

say "6. Notifications sent (ops view)"
sleep 1
curl -sf "$GW/api/notifications?orderId=$ORDER_ID" -H "Authorization: Bearer $OPS" | jq -r '.[] | "\(.channel)\t\(.status)\t\(.message)"'

say "7. The same correlation id in every service's logs"
echo "docker compose logs | grep demo-walkthrough"
