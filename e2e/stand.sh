#!/bin/bash
# Стенд сквозного прогона: изолированный Temporal в Docker, WDS и bff из собранных jar,
# JWKS для JWT, фейковые сервисы на их очередях.
#   ./stand.sh build   собрать всё нужное (стартер, генератор и библиотека контрактов, bff, WDS, классы сервисов)
#   ./stand.sh up      поднять стенд
#   ./stand.sh down    остановить стенд и удалить контейнер
# Для сценариев отказов: start-fake | stop-fake | restart-wds | start-bff2
set -euo pipefail
source "$(dirname "$0")/env.sh"
mkdir -p "$WORK"

build() {
  (cd "$OTHERS/security-starter" && mvn -q -B install)
  (cd "$OTHERS/temporal-encryption-starter" && mvn -q -B install)
  # библиотека контрактов — модуль bff, сгенерированный из workflow-api.yaml: проверяем, что код не разошёлся со спецификацией
  (cd "$ROOT/workflow-api-generator" && ./mvnw -q -B -DskipTests install)
  (cd "$ROOT/bff/bff-lk-workflow-api" \
      && java -jar "$ROOT/workflow-api-generator/workflow-api-generator/target/workflow-api-generator-1.0.0-cli.jar" \
          generate --check workflow-api.yaml >/dev/null)
  (cd "$ROOT/bff" && ./mvnw -q -B -DskipTests install)
  (cd "$OTHERS/workflow-definition-service" && mvn -q -B $LOMBOK -DskipTests package \
      && mvn -q -B $LOMBOK dependency:build-classpath -Dmdep.outputFile="$WORK/wds-cp.txt")
  for s in $SERVICES; do (cd "$OTHERS/$s" && mvn -q -B $LOMBOK -DskipTests compile); done
  local cp="$HOME/.m2/repository/sb/broker/sb-broker-temporal-encryption-starter/1.0.0/sb-broker-temporal-encryption-starter-1.0.0.jar"
  for s in $SERVICES; do cp="$cp:$OTHERS/$s/target/classes"; done
  echo "$cp:$OTHERS/workflow-definition-service/target/classes:$(cat "$WORK/wds-cp.txt")" > "$WORK/classpath.txt"
  javac --release 25 -proc:none -nowarn -cp "$(cat "$WORK/classpath.txt")" -d "$WORK/fake-classes" "$E2E_DIR/fake/FakeServices.java"
  echo "build: ok"
}

keys() {
  [ -f "$WORK/key.pem" ] && return
  openssl genrsa -out "$WORK/key.pem" 2048 2>/dev/null
  local modulus; modulus=$(openssl rsa -in "$WORK/key.pem" -noout -modulus | cut -d= -f2)
  mkdir -p "$WORK/jwks"
  python3 - "$modulus" "$WORK/jwks/jwks.json" <<'PY'
import sys, base64, json
b = lambda x: base64.urlsafe_b64encode(x).rstrip(b'=').decode()
n = bytes.fromhex(sys.argv[1]); e = (65537).to_bytes(3, 'big')
json.dump({"keys": [{"kty": "RSA", "kid": "e2e", "use": "sig", "alg": "RS256", "n": b(n), "e": b(e)}]}, open(sys.argv[2], 'w'))
PY
  openssl rand -base64 32 > "$WORK/enc.key"
}

wait_for() { # файл-лог, строка, секунды
  for _ in $(seq 1 "$3"); do grep -q "$2" "$1" 2>/dev/null && return 0; sleep 1; done
  echo "timeout waiting for '$2' in $1"; tail -30 "$1"; exit 1
}

start_fake() {
  nohup java -cp "$WORK/fake-classes:$(cat "$WORK/classpath.txt")" FakeServices "$WORK/enc.key" "$TEMPORAL_URL" "$TEMPORAL_NAMESPACE" \
      >> "$WORK/fake.log" 2>&1 &
}

start_wds() {
  nohup java -jar "$OTHERS/workflow-definition-service/target/template-1.0.0.jar" \
      --spring.config.additional-location="file:$E2E_DIR/wds-e2e.yaml" > "$WORK/${1:-wds}.log" 2>&1 &
  wait_for "$WORK/${1:-wds}.log" 'Started Main' 90
}

start_bff() { # порт, имя лога
  nohup java -jar "$ROOT/bff/bff-lk-app/target/app.jar" --server.port="$1" \
      --spring.security.oauth2.resourceserver.jwt.issuer-uri=http://e2e-issuer \
      --spring.security.oauth2.resourceserver.jwt.jwk-set-uri="http://127.0.0.1:$JWKS_PORT/jwks.json" \
      > "$WORK/$2.log" 2>&1 &
  wait_for "$WORK/$2.log" 'Started BffLkApplication' 90
}

up() {
  [ -f "$WORK/classpath.txt" ] || build
  keys
  export TEMPORAL_ENCRYPTION_KEY_K1; TEMPORAL_ENCRYPTION_KEY_K1=$(cat "$WORK/enc.key")
  docker run -d --name "$CONTAINER" -p "127.0.0.1:$TEMPORAL_PORT:7233" -p "127.0.0.1:$TEMPORAL_UI_PORT:8233" \
      "$TEMPORAL_IMAGE" server start-dev --ip 0.0.0.0 --namespace "$TEMPORAL_NAMESPACE" > /dev/null
  for _ in $(seq 1 30); do docker exec "$CONTAINER" temporal operator namespace describe "$TEMPORAL_NAMESPACE" >/dev/null 2>&1 && break; sleep 1; done
  nohup python3 -m http.server "$JWKS_PORT" --bind 127.0.0.1 --directory "$WORK/jwks" > "$WORK/jwks.log" 2>&1 < /dev/null &
  : > "$WORK/fake.log"; start_fake; wait_for "$WORK/fake.log" 'fake services started' 60
  start_wds wds
  start_bff "$BFF_PORT" bff
  echo "stand is up: bff $BFF, Temporal UI http://127.0.0.1:$TEMPORAL_UI_PORT (namespace $TEMPORAL_NAMESPACE)"
}

down() {
  pkill -f 'FakeServices .*enc.key' || true
  pkill -f 'workflow-definition-service/target/template-1.0.0.jar' || true
  pkill -f 'bff-lk-app/target/app.jar' || true
  pkill -f "http.server $JWKS_PORT" || true
  docker rm -f "$CONTAINER" > /dev/null 2>&1 || true
  echo "stand is down"
}

export TEMPORAL_ENCRYPTION_KEY_K1; TEMPORAL_ENCRYPTION_KEY_K1=$(cat "$WORK/enc.key" 2>/dev/null || true)
case "${1:-}" in
  build) build ;;
  up) up ;;
  down) down ;;
  start-fake) start_fake; sleep 3 ;;
  stop-fake) pkill -f 'FakeServices .*enc.key' || true; sleep 1 ;;
  restart-wds) pkill -f 'workflow-definition-service/target/template-1.0.0.jar' || true; sleep 2; start_wds wds-restarted ;;
  start-bff2) start_bff "$BFF2_PORT" bff2 ;;
  *) echo "usage: $0 build|up|down|start-fake|stop-fake|restart-wds|start-bff2"; exit 2 ;;
esac
