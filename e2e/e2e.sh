#!/bin/bash
# Сквозные HTTP-сценарии: bff-lk -> Temporal -> WDS -> активити фейковых сервисов. Стенд: ./stand.sh up
source "$(dirname "$0")/env.sh"
E="$E2E_DIR"; B="$BFF"; ROOT_URL="http://127.0.0.1:$BFF_PORT/bff-lk"
PASS=0; FAIL=0
U=$($E/token.sh user@example.com); N=$($E/token.sh noconsent@example.com); X=$($E/token.sh stranger@example.com)
EXPIRED=$($E/token.sh user@example.com -60)
req() { # method url token [body] -> "<code> <body>"
  local args=(-s -o $WORK/last-body -w '%{http_code}' -X "$1" "$2" -H 'Accept: application/json, text/event-stream')
  [ -n "$3" ] && args+=(-H "Authorization: Bearer $3")
  [ -n "$4" ] && args+=(-H 'Content-Type: application/json' -d "$4")
  local code; code=$(curl "${args[@]}" --max-time 120); echo "$code $(cat $WORK/last-body)"
}
expect() { # firstName actual-code expected-code body-substring
  local code="${2%% *}" body="${2#* }"
  if [ "$code" = "$3" ] && [[ "$body" == *"$4"* ]]; then PASS=$((PASS+1)); echo "OK   $1 [$code]";
  else FAIL=$((FAIL+1)); echo "FAIL $1: got [$code] ${body:0:300} expected [$3] *$4*"; fi
}
expect "no token -> 401"                     "$(req GET $B/consents)"                         401 '"message":"Нет доступа"'
expect "expired token -> 401"                "$(req GET $B/consents "$EXPIRED")"              401 'Нет доступа'
expect "garbage token -> 401"                "$(req GET $B/consents 'a.b.c')"                 401 'Нет доступа'
expect "unknown LK user -> 401"              "$(req GET $B/consents "$X")"                    401 'Нет доступа'
expect "consents list"                       "$(req GET $B/consents "$U")"                    200 '"title":"Поручение","status":true,"required":true}'
expect "consents list w/o required consent"  "$(req GET $B/consents "$N")"                    200 '"status":false'
expect "consent file (pdf)"                  "$(req GET $B/consents/PDN/file "$U")"           200 '%PDF-fake'
expect "consent file missing -> 404"         "$(req GET $B/consents/ASSIGNMENT/file "$U")"    404 'Файл согласия не найден'
expect "consent file bad type -> 400"        "$(req GET $B/consents/XXX/file "$U")"           400 'Некорректный тип согласия'
expect "submit consents w/o required ok"     "$(req POST $B/consents "$N" '[{"type":"PDN","consent":true},{"type":"CSI","consent":false}]')" 200 ''
expect "submit w/o PDN -> 400"               "$(req POST $B/consents "$U" '[{"type":"CSI","consent":true}]')" 400 'Нет согласия на обработку'
expect "recall"                              "$(req POST $B/consents/CSI/recall "$U")"        200 ''
expect "recall w/o consents -> outcome"      "$(req POST $B/consents/CSI/recall "$N")"        200 '"outcome":"consents_required"'
expect "catalog dictionary"                  "$(req GET $B/regions "$U")"                     200 '[{"gosbId":"38"}]'
expect "catalog without consents allowed"    "$(req GET $B/vehicle/marks "$N")"               200 'gosbId'
expect "catalog not found -> 404"            "$(req GET $B/city/0000000000000 "$U")"          404 'Город не найден'
expect "catalog rejected by Product Catalog -> 400" "$(req GET $B/regions/9999999999 "$U")" 400 'Запрос не разобран'
expect "catalog bad uuid -> 400"             "$(req GET $B/vehicle/documents/xx "$U")"        400 'не является UUID'
expect "catalog unknown user -> 401"         "$(req GET $B/regions "$X")"                     401 'Нет доступа'
expect "lead: open lead updated -> accepted" "$(req POST $B/leads "$U" '{"fio":"Иванов","phone":"+7999","comment":"","subtype":"CONTACT_BROKER"}')" 200 '{"outcome":"accepted"}'
expect "lead w/o consents"                   "$(req POST $B/leads "$N" '{"fio":"Иванов","phone":"+7999","subtype":"CONTACT_BROKER"}')" 200 '"outcome":"consents_required"'
expect "products dictionary"                 "$(req GET $B/products "$U")"                    200 '[{"gosbId":"38"}]'
expect "insurance categories"                "$(req GET "$B/insurance_categories?includeProducts=true" "$U")" 200 '[{"gosbId":"38"}]'
expect "insurance category bad id -> 400"    "$(req GET $B/insurance_categories/abc "$U")"    400 'не является целым числом'
expect "lead: prolongation -> accepted"      "$(req POST $B/leads "$U" '{"fio":"Иванов","phone":"+7999","subtype":"POLICY_PROLONGATION","productId":3,"policyNumber":"КАСКО-1","insuranceGroup":"транспорт"}')" 200 '{"outcome":"accepted"}'
expect "lead: unknown product -> 422"        "$(req POST $B/leads "$U" '{"fio":"Иванов","phone":"+7999","subtype":"POLICY_PROLONGATION","productId":999,"policyNumber":"N-1"}')" 422 '"outcome":"product_not_found"'
expect "lead: prolongation w/o policy -> 400" "$(req POST $B/leads "$U" '{"fio":"Иванов","phone":"+7999","subtype":"POLICY_PROLONGATION","productId":3}')" 400 'Запрос не разобран'
expect "lead closed scenario -> 422"         "$(req POST $B/leads "$U" '{"fio":"Иванов","phone":"+7999","subtype":"OTHER"}')" 422 'Сценарий создания лида недоступен'
expect "lead unknown user -> 401"            "$(req POST $B/leads "$X" '{"fio":"Иванов","phone":"+7999","subtype":"CONTACT_BROKER"}')" 401 'Нет доступа'
RISK=$(req POST $B/risk-profile-recommendations "$U"); expect "risk initiate -> 202" "$RISK" 202 '"workflowId":"lk-risk-profile-7707083893"'
RID=$(echo "${RISK#* }" | sed -E 's/.*"workflowId":"([^"]+)".*/\1/')
expect "risk SSE complete (service rec w/ group tolerated)" "$(req GET $B/risk-profile-recommendations/$RID "$U")" 200 'event:complete'
expect "risk SSE foreign session"            "$(req GET $B/risk-profile-recommendations/lk-risk-profile-0000000000-x "$U")" 200 'Внутренняя ошибка сервиса'
expect "risk w/o consents -> outcome"        "$(req POST $B/risk-profile-recommendations "$N")" 200 'consents_required'
expect "policies import -> 202"              "$(req POST $B/policies "$U")"                   202 '{"status":"accepted"}'
expect "policies SSE complete"               "$(req GET $B/policies "$U")"                    200 '"policyNumber":"КАСКО-1"'
expect "policies money scale & ISO dates"    "$(req GET $B/policies "$U")"                    200 '"totalInsurancePremium":84500.00,"validFrom":"2026-01-15T00:00:00+03:00"'
CP=$(req POST $B/company-profile "$U"); expect "company profile initiate -> 202" "$CP" 202 '"workflowId":"lk-company-profile-7707083893-'
CID=$(echo "${CP#* }" | sed -E 's/.*"workflowId":"([^"]+)".*/\1/')
CPS=$(req GET $B/company-profile/$CID "$U"); echo "     company profile SSE: ${CPS:0:250}"
expect "company profile SSE complete"        "$CPS"                                           200 'event:complete'
if printf '%s' "$CPS" | python3 -c '
import json, sys
body = sys.stdin.read().split(" ", 1)[1]
events = [line for line in body.splitlines() if line.startswith("event:")]
data = [line[5:].strip() for line in body.splitlines() if line.startswith("data:")]
assert events == ["event:complete"], events
assert len(data) == 1, data
profile = json.loads(data[0])
assert profile["inn"] == "7707083893" and profile["firstName"] == "ООО «Пример»", profile
'; then
  PASS=$((PASS+1)); echo "OK   company profile DTO and single business event"
else
  FAIL=$((FAIL+1)); echo "FAIL company profile DTO or business event count"
fi
expect "company foreign session"             "$(req GET $B/company-profile/lk-company-profile-0000000000-x "$U")" 200 'SESSION_NOT_FOUND'
expect "company blank id -> 400"             "$(req GET $B/company-profile/ "$U")"            400 'Идентификатор операции не передан'
# --- предрасчёты: владелец в workflowId, Update-with-Start, выбор предложения
O=$($E/token.sh other@example.com)
offer() { printf '{"workflowId":"%s","offerId":"%s"}' "$1" "$2"; }
DMS='{"companyType":"company","inn":"7707083893","regionCode":77,"surname":"Иванов","firstName":"Иван","phone":"+74951234567","employees":[{"count":10,"ageGroupId":1,"kladrId":"7700000000000"}],"product":"dms"}'
R=$(req POST $B/calculation/dms/preview "$U" "$DMS"); expect "dms preview -> 202 workflowId" "$R" 202 '"workflowId":"lk-calc-dms-'
WF=$(echo "${R#* }" | sed -E 's/.*"workflowId":"([^"]+)".*/\1/')
expect "dms preview SSE complete"            "$(req GET $B/calculation/dms/$WF "$U")"         200 '"summary":[{"insuranceCompanyId":1'
expect "dms preview of other user"           "$(req GET $B/calculation/dms/$WF "$O")"         200 'Расчёт не найден'
expect "dms preview bad query -> 400"        "$(req GET "$B/calculation/dms/$WF?sortBy=price" "$U")" 400 'Параметры отбора предрасчёта некорректны'
expect "offer: not selectable -> 400"        "$(req POST $B/calculation/offer "$U" "$(offer "$WF" o-2)")" 400 'Предложение нельзя выбрать'
expect "offer: unknown -> 400"               "$(req POST $B/calculation/offer "$U" "$(offer "$WF" nope)")" 400 'Предложение не найдено'
expect "offer: accepted -> 200"              "$(req POST $B/calculation/offer "$U" "$(offer "$WF" o-1)")" 200 ''
expect "offer: other user -> 404"            "$(req POST $B/calculation/offer "$O" "$(offer "$WF" o-1)")" 404 'Выбор предложения недоступен'
expect "dms preview w/o consents -> 200"     "$(req POST $B/calculation/dms/preview "$N" "$DMS")" 200 '"outcome":"consents_required"'
expect "dms preview with email -> 400"       "$(req POST $B/calculation/dms/preview "$U" "${DMS%\}},\"email\":\"x@example.com\"}")" 400 'Тело запроса не принято'
KASKO='{"insurant":{"companyType":"company","inn":"7707083893","regionCode":77,"contacts":{"surname":"Иванов","firstName":"Иван","phone":"+74951234567"},"product":"kasko"},"vehicle":{"vin":"XTA00000000000001","category":"6f1e8c2a-3b47-4d9e-8a15-2c6b7d0e4f11","markId":1,"mark":"Lada","modelId":2,"model":"Vesta","manufactureYear":2024,"enginePower":106,"mileage":1000,"document":{"typeGuid":"6f1e8c2a-3b47-4d9e-8a15-2c6b7d0e4f12","number":"77УН123456","issueDate":"2024-05-01"},"usePurpose":"6f1e8c2a-3b47-4d9e-8a15-2c6b7d0e4f13"},"insuranceContract":{"startDate":"2026-10-08","insuranceCost":1500000}}'
R=$(req POST $B/calculation/kasko "$U" "$KASKO"); expect "kasko preview -> 202" "$R" 202 '"workflowId":"lk-calc-kasko-'
WF=$(echo "${R#* }" | sed -E 's/.*"workflowId":"([^"]+)".*/\1/')
expect "kasko preview SSE complete"          "$(req GET $B/calculation/kasko/$WF "$U")"       200 '"offerId":"k-1","status":"success"'
expect "kasko offer accepted -> 200"         "$(req POST $B/calculation/offer "$U" "$(offer "$WF" k-1)")" 200 ''
DOC='{"typeGuid":"6f1e8c2a-3b47-4d9e-8a15-2c6b7d0e4f12","series":"4510","number":"123456","issueDate":"2020-01-15"}'
CO="{\"company\":{\"inn\":\"7707083893\",\"contacts\":{\"phone\":\"+7\"},\"shortName\":\"ООО\",\"fullName\":\"ООО\",\"ogrn\":\"1\",\"kpp\":\"2\",\"opf\":12300,\"address\":\"Москва\",\"identityDocument\":$DOC}}"
SPECIAL="{\"representative\":{\"regionCode\":77,\"contacts\":{\"surname\":\"Иванов\",\"name\":\"Иван\",\"phone\":\"+7\"}},\"insurant\":$CO,\"vehicles\":[{\"vin\":\"XTA00000000000001\",\"chassisNumber\":\"CH-1\",\"category\":\"6f1e8c2a-3b47-4d9e-8a15-2c6b7d0e4f11\",\"markId\":1,\"mark\":\"Кран\",\"modelId\":2,\"model\":\"КС\",\"manufactureYear\":2020,\"enginePower\":300,\"document\":$DOC,\"usePurpose\":\"6f1e8c2a-3b47-4d9e-8a15-2c6b7d0e4f13\",\"owner\":$CO,\"ownerIsInsurant\":true}],\"insuranceContract\":{\"startDateOsago\":\"2026-10-08\",\"endDateOsago\":\"2027-10-07\"}}"
R=$(req POST $B/calculation/osago/special-vehicle "$U" "$SPECIAL"); expect "osago special -> 202" "$R" 202 '"workflowId":"lk-calc-osago-'
WF=$(echo "${R#* }" | sed -E 's/.*"workflowId":"([^"]+)".*/\1/')
expect "osago SSE complete (closed, no quotes)" "$(req GET $B/calculation/osago/$WF "$U")"    200 '"offers":[]'
expect "osago offer on closed -> 404"        "$(req POST $B/calculation/offer "$U" "$(offer "$WF" x)")" 404 'Выбор предложения недоступен'
expect "openapi public"                      "$(req GET $ROOT_URL/v3/api-docs)" 200 '"openapi"'
metrics() { echo "200 $(curl -s -H 'Accept: text/plain' "$ROOT_URL/actuator/prometheus")"; }
expect "metrics: workflow timings"           "$(metrics)" 200 'bff_workflow_seconds_count{application="bff-lk"'
expect "metrics: access cache"               "$(metrics)" 200 'cache="bff.access"'
expect "metrics: Temporal SDK"               "$(metrics)" 200 'temporal_request_total'
expect "readiness includes Temporal"         "$(req GET $ROOT_URL/actuator/health/readiness)" 200 '"status":"UP"'
expect "health public"                       "$(req GET $ROOT_URL/actuator/health)" 200 'UP'
echo "--- в истории Temporal нет персональных данных"
WF=$(docker exec "$CONTAINER" temporal workflow list --namespace "$TEMPORAL_NAMESPACE" \
     --query "WorkflowType='SubmitAccountLeadWorkflow'" --limit 1 -o json | python3 -c "import sys,json; print(json.load(sys.stdin)[0]['execution']['workflowId'])")
docker exec "$CONTAINER" temporal workflow show --namespace "$TEMPORAL_NAMESPACE" -w "$WF" -o json > "$WORK/history.json"
expect "no plaintext PII in history"         "$(grep -c 'user@example.com\|Иванов\|7707083893' "$WORK/history.json") x" 0 x
expect "payloads carry encryption key id"    "200 $(grep -c 'encryption-key-id' "$WORK/history.json")" 200 ''
echo "PASS=$PASS FAIL=$FAIL"
[ "$FAIL" = 0 ]
