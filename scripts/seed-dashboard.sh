#!/usr/bin/env bash
# Fills the ops dashboard with orders in every column (through the gateway, like a real client).
# Usage: ./scripts/seed-dashboard.sh        (stack must be up; GW defaults to http://localhost:8080)
set -euo pipefail

GW=${GW:-http://localhost:8080}
OPS=$(curl -sf "$GW/api/auth/login" -H 'Content-Type: application/json' \
  -d '{"username":"ops","password":"Passw0rd!"}' | jq -r .accessToken)
H=(-H "Authorization: Bearer $OPS")

create() {  # create <merchant> <name> <phone> <address> <city> <amount>
  curl -sf "$GW/api/orders" "${H[@]}" -H 'Content-Type: application/json' \
    -H "Idempotency-Key: seed-$(date +%s%N)-$RANDOM" -H "X-Correlation-Id: seed-$RANDOM" \
    -d "{\"merchantId\":\"$1\",\"recipientName\":\"$2\",\"recipientPhone\":\"$3\",\"addressLine\":\"$4\",\"city\":\"$5\",\"codAmount\":$6}" \
    | jq -r .id
}
step() { curl -sf -X POST "$GW/api/deliveries/$1/$2" "${H[@]}" -H 'Content-Type: application/json' -d "${3:-{\}}" >/dev/null; }
assigned() {
  for _ in $(seq 1 40); do
    s=$(curl -s "$GW/api/deliveries/$1" "${H[@]}" | jq -r '.status // empty')
    [ "$s" = "ASSIGNED" ] && return 0; sleep 0.5
  done; echo "order $1 was not assigned" >&2; return 1
}
fail() { step "$1" out-for-delivery; step "$1" failed "{\"reason\":\"$2\"}"; }

echo "creating orders..."
D1=$(create M-1001 "Salma Idrissi"       +212612345678 "12 Rue Atlas, Maarif"      Casablanca 249.90)
D2=$(create M-1002 "Youssef Benjelloun"  +212661234567 "45 Av. Hassan II"          Rabat      489.00)
R1=$(create M-1001 "Khadija Alaoui"      +212698765432 "8 Rue de Fes, Gueliz"      Marrakech  1250.00)
F1=$(create M-1002 "Mehdi Tazi"          +212677889900 "21 Bd Zerktouni"           Casablanca 189.50)
O1=$(create M-1001 "Imane Berrada"       +212655443322 "3 Rue Ibn Battouta"        Tanger     720.00)
O2=$(create M-1002 "Anas Lahlou"         +212644556677 "Hay Riad, Secteur 12"      Rabat      99.90)
A1=$(create M-1001 "Hiba Chraibi"        +212633221100 "Bd Anfa"                   Casablanca 359.00)
A2=$(create M-1002 "Othmane Bennani"     +212622334455 "Av. Hassan II"             Kenitra    560.00)
C1=$(create M-1001 "Rachid El Fassi"     +212611223344 "Derb Moulay Idriss"        Fes        310.00)  # no courier zone: stays CREATED

for id in $D1 $D2 $R1 $F1 $O1 $O2 $A1 $A2; do assigned "$id"; done
echo "driving courier steps..."
step "$D1" out-for-delivery; step "$D1" delivered
step "$D2" out-for-delivery; step "$D2" delivered
fail "$R1" "customer absent"; fail "$R1" "phone switched off"; fail "$R1" "customer refused the parcel"
fail "$F1" "customer absent"
step "$O1" out-for-delivery
step "$O2" out-for-delivery
echo "done: 2 delivered, 1 returned, 1 failed, 2 out for delivery, 2 assigned, 1 created (unserved city)"
