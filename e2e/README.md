# Сквозные проверки и замеры bff-lk

Стенд: изолированный Temporal в Docker (порт 17233, namespace `bfflk-e2e` — не пересекается с локальным
Temporal на 7233), WDS и bff из собранных jar, JWT с локальным JWKS, шифрование payload'ов `ENCRYPT`,
фейковые сервисы на их очередях (`fake/FakeServices.java`), отвечающие настоящими DTO сервисов.

## Запуск

```bash
./stand.sh build        # стартер шифрования, bff, WDS, классы сервисов (нужны others/)
./stand.sh up           # поднять стенд
./e2e.sh                # 65 проверок: HTTP, DTO/событие карточки, шифрование истории, метрики, готовность
./resilience.sh         # параллельность, отказ сервисов, второй под, рестарт WDS (replay), лид > 60 с
./contract.sh           # контракты DTO сервисов <-> зеркала WDS через конвертер Temporal (без Temporal)
./perf.py 200 1         # задержка; ./perf.py 800 16 — под нагрузкой
./stand.sh down
```

## Замер без проброса портов Docker Desktop

```bash
JAVA_IMAGE=eclipse-temurin:25-jre PYTHON_IMAGE=python:3.13-slim ./perf-docker.sh 200 1
BFF_EXTRA_ARGS="--bff.cache.access.ttl=0s --bff.cache.catalog.ttl=0s" ./perf-docker.sh 200 1   # без кэшей
TIMELINE=ReadProductCatalogWorkflow ./perf-docker.sh 50 1                                    # шкала событий
TEMPORAL_DYNAMIC_CONFIG="matching.numTaskqueueReadPartitions=1" ./perf-docker.sh 50 1        # dynamic config
```

Всё (Temporal, bff, WDS, сервисы, клиент) работает в одной Docker-сети. Против реального кластера:
`BFF_HOST=<хост> BFF_PORT=<порт> TOKEN=<jwt> ./perf.py 1000 32`.

## Требования

Docker, JDK 25+, Maven, openssl, python3. Порты: 17233, 18233, 18080, 18081, 18099.
Рабочие файлы (ключи, логи, classpath) — в `.work/` (в `.gitignore`).
Результаты замеров и выводы — [docs/fact/performance.md](../../docs/fact/performance.md).

`wds-e2e.yaml` направляет CRM-активити сбора карточки в `e2e-company-crm-tq`, которую обслуживает FakeServices.
Workflow WDS остаются настоящими; обращения в внешнюю CRM в этом сценарии не нужны. Успешная проверка
требует `event:complete`, корректный DTO и ровно одно бизнес-событие, а не любое событие SSE.

Для проверки минимальной корреляции лидов CRM на отдельном PostgreSQL:
`python3 crm-outbox-db.py`. Скрипт применяет миграции, проверяет реальную выборку outbox до/после
подтверждения и удаляет созданный контейнер. Рабочие БД и Kafka не используются.
