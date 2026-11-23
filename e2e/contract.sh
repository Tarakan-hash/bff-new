#!/bin/bash
# Контракты активити: DTO сервисов <-> зеркала в WDS через DefaultDataConverter Temporal.
# Нужна сборка: ./stand.sh build (Temporal не нужен).
source "$(dirname "$0")/env.sh"
[ -f "$WORK/classpath.txt" ] || "$E2E_DIR/stand.sh" build
java -cp "$(cat "$WORK/classpath.txt")" "$E2E_DIR/contract/ContractHarness.java"
