#!/bin/bash
# Отказоустойчивость: параллельные запросы, недоступные сервисы, второй под bff, рестарт WDS, лид > 60 с.
# Стенд: ./stand.sh up
source "$(dirname "$0")/env.sh"
S="$E2E_DIR/stand.sh"; U=$("$E2E_DIR/token.sh" user@example.com); PASS=0; FAIL=0
check() { if eval "$2"; then PASS=$((PASS+1)); echo "OK   $1"; else FAIL=$((FAIL+1)); echo "FAIL $1"; fi; }
imports() { grep -c 'workflow PolicyImportWorkflow' "$WORK/fake.log"; }
LEAD='{"fio":"Иванов","phone":"+7999","subtype":"CONTACT_BROKER"}'

echo "=== 1. 5 параллельных GET /policies -> один импорт"
BEFORE=$(imports)
for i in 1 2 3 4 5; do curl -s --max-time 60 -H "Authorization: Bearer $U" "$BFF/policies" -o "$WORK/par$i.out" & done; wait
check "один импорт на ИНН ($(( $(imports) - BEFORE )))" '[ $(( $(imports) - BEFORE )) = 1 ]'
check "все 5 ответов complete" '[ $(grep -l "event:complete" "$WORK"/par*.out | wc -l) = 5 ]'

echo "=== 2. сервисы недоступны 8 с посреди запроса"
"$S" stop-fake
curl -s --max-time 90 -H "Authorization: Bearer $U" "$BFF/policies" -o "$WORK/outage.out" & CURL=$!
sleep 8; "$S" start-fake; wait $CURL
check "запрос дождался сервисов" 'grep -q "event:complete" "$WORK/outage.out"'

echo "=== 3. POST на одном поде bff, GET на другом"
"$S" start-bff2 > /dev/null
ID=$(curl -s -X POST -H "Authorization: Bearer $U" "$BFF/risk-profile-recommendations" | sed -E 's/.*"workflowId":"([^"]+)".*/\1/')
check "второй под отдал результат сессии первого" \
  'curl -s --max-time 60 -H "Authorization: Bearer $U" "http://127.0.0.1:$BFF2_PORT/bff-lk/api/v1/risk-profile-recommendations/$ID" | grep -q "event:complete"'

echo "=== 4. рестарт WDS посреди workflow (replay)"
"$S" stop-fake
curl -s --max-time 120 -X POST -H "Authorization: Bearer $U" -H 'Content-Type: application/json' -d "$LEAD" \
     "$BFF/leads" -o "$WORK/replay.out" & CURL=$!
sleep 4; "$S" restart-wds; "$S" start-fake; wait $CURL
check "лид принят после рестарта WDS" 'grep -q "\"outcome\":\"accepted\"" "$WORK/replay.out"'
check "нет ошибок недетерминизма" '! grep -qi "nondeterminis" "$WORK/wds-restarted.log"'

echo "=== 5. лид дольше 60 с -> 204, workflow завершается в фоне"
"$S" stop-fake
UPDATES=$(grep -c UpdateAccountLead "$WORK/fake.log")
CODE=$(curl -s --max-time 120 -o /dev/null -w '%{http_code}' -X POST -H "Authorization: Bearer $U" \
       -H 'Content-Type: application/json' -d "$LEAD" "$BFF/leads")
check "ответ 204 ($CODE)" '[ "$CODE" = 204 ]'
"$S" start-fake; sleep 6
check "workflow лида завершился после возврата сервисов" '[ $(grep -c UpdateAccountLead "$WORK/fake.log") -gt $UPDATES ]'

echo "PASS=$PASS FAIL=$FAIL"
[ "$FAIL" = 0 ]
