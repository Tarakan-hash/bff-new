#!/bin/bash
# Замер внутри одной Docker-сети: bff, WDS, фейковые сервисы и нагрузочный клиент работают рядом с Temporal,
# без проброса портов Docker Desktop (на macOS он добавляет ~40-50 мс к каждой передаче задачи worker'у).
#   JAVA_IMAGE=<образ с Java 25> PYTHON_IMAGE=<образ с python3> ./perf-docker.sh [запросов] [параллельность]
#   BFF_EXTRA_ARGS="--bff.cache.access.ttl=0s --bff.cache.catalog.ttl=0s" — замер без кэшей (холодный путь)
set -euo pipefail
source "$(dirname "$0")/env.sh"
JAVA_IMAGE="${JAVA_IMAGE:-eclipse-temurin:25-jre}"
PYTHON_IMAGE="${PYTHON_IMAGE:-python:3.13-slim}"
NET=bfflk-perf-net; P=bfflk-perf
[ -f "$WORK/classpath.txt" ] || "$E2E_DIR/stand.sh" build
mkdir -p "$WORK/perf"; [ -f "$WORK/key.pem" ] || { "$E2E_DIR/stand.sh" up >/dev/null && "$E2E_DIR/stand.sh" down >/dev/null; }

cleanup() { docker rm -f $P-temporal $P-fake $P-wds $P-bff $P-jwks > /dev/null 2>&1 || true; docker network rm $NET > /dev/null 2>&1 || true; }
cleanup; trap cleanup EXIT
docker network create $NET > /dev/null
KEY=$(cat "$WORK/enc.key")
ENV=(-e TEMPORAL_URL=$P-temporal:7233 -e TEMPORAL_NAMESPACE=$TEMPORAL_NAMESPACE -e TEMPORAL_ENCRYPTION_MODE=ENCRYPT
     -e TEMPORAL_ENCRYPTION_KEY_ID=k1 -e TEMPORAL_ENCRYPTION_KEY_K1="$KEY")
MOUNT=(-v "$ROOT:$ROOT:ro" -v "$HOME/.m2:$HOME/.m2:ro" -v "$WORK:$WORK")

# TEMPORAL_DYNAMIC_CONFIG: например "matching.numTaskqueueReadPartitions=1 matching.numTaskqueueWritePartitions=1"
DYNAMIC=(); for kv in ${TEMPORAL_DYNAMIC_CONFIG:-}; do DYNAMIC+=(--dynamic-config-value "$kv"); done
docker run -d --name $P-temporal --network $NET "$TEMPORAL_IMAGE" server start-dev --ip 0.0.0.0 \
    --namespace "$TEMPORAL_NAMESPACE" ${DYNAMIC[@]+"${DYNAMIC[@]}"} > /dev/null
for _ in $(seq 1 30); do docker exec $P-temporal temporal operator namespace describe "$TEMPORAL_NAMESPACE" > /dev/null 2>&1 && break; sleep 1; done
docker run -d --name $P-jwks --network $NET "${MOUNT[@]}" --entrypoint python3 "$PYTHON_IMAGE" \
    -m http.server 8099 --directory "$WORK/jwks" > /dev/null
docker run -d --name $P-fake --network $NET "${MOUNT[@]}" --entrypoint java "$JAVA_IMAGE" \
    -cp "$WORK/fake-classes:$(cat "$WORK/classpath.txt")" FakeServices "$WORK/enc.key" $P-temporal:7233 "$TEMPORAL_NAMESPACE" > /dev/null
docker run -d --name $P-wds --network $NET "${MOUNT[@]}" "${ENV[@]}" --entrypoint java "$JAVA_IMAGE" \
    -jar "$OTHERS/workflow-definition-service/target/template-1.0.0.jar" > /dev/null
docker run -d --name $P-bff --network $NET "${MOUNT[@]}" "${ENV[@]}" --entrypoint java "$JAVA_IMAGE" \
    -jar "$ROOT/bff/bff-lk-app/target/app.jar" --server.port=8080 \
    --spring.security.oauth2.resourceserver.jwt.issuer-uri=http://e2e-issuer \
    --spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://$P-jwks:8099/jwks.json ${BFF_EXTRA_ARGS:-} > /dev/null
for c in wds bff; do
  for _ in $(seq 1 90); do docker logs $P-$c 2>&1 | grep -q 'Started ' && break; sleep 1; done
done
docker run --rm --network $NET "${MOUNT[@]}" -e BFF_HOST=$P-bff -e BFF_PORT=8080 \
    -e TOKEN="$("$E2E_DIR/token.sh" user@example.com)" --entrypoint python3 "$PYTHON_IMAGE" \
    "$E2E_DIR/perf.py" "${1:-200}" "${2:-1}"
if [ -n "${TIMELINE:-}" ]; then
  WF=$(docker exec $P-temporal temporal workflow list --namespace "$TEMPORAL_NAMESPACE" \
       --query "WorkflowType='$TIMELINE'" --limit 1 -o json | python3 -c "import sys,json; print(json.load(sys.stdin)[0]['execution']['workflowId'])")
  docker exec $P-temporal temporal workflow show --namespace "$TEMPORAL_NAMESPACE" -w "$WF" -o json | python3 "$E2E_DIR/timeline.py"
fi
