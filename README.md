# bff-lk

### 1.0.0
- BSNP-365: Добавить логи с более детальным описанием происходящего

BFF личного кабинета. Фронт ходит только сюда; bff проверяет JWT, а всё остальное делает через
Temporal: допуск, согласия, лиды, профили, полисы, справочники. Своей БД и состояния нет.

Описание: [docs/plan](../docs/plan) — задумано, [docs/fact](../docs/fact) — сделано и почему.

## Состав проекта

Это одно Spring Boot приложение: исходники находятся в `src/`, исполняемый JAR — `target/app.jar`.
Контракты workflow подключены как внешняя библиотека `sb.broker:bff-lk-workflow-api:1.0.0`.

## Устройство

Пакеты приложения — по фичам: `consent`, `lead`, `companyprofile`, `riskprofile`, `policy`, `catalog`.
В каждой фиче:

| Класс | Роль |
| --- | --- |
| `*Controller` | HTTP-адаптер: маршрут, проверка параметров, OpenAPI |
| `*Service` | сценарий: допуск, запуск workflow, ответ |
| `dto/` | HTTP-контракт фронта; имена — как в `docs/plan` |
| `*Mapper` | контракт workflow → HTTP-контракт (package-private) |

Общие пакеты: `access` (допуск и его кэш), `temporal` (единственный адаптер Temporal: запуск и ожидание
workflow, метрики, проба готовности), `session` (асинхронные сессии профилей), `security` (JWT),
`web` (ошибки, тексты, SSE, OpenAPI), `config`.

Правила проверяет `ArchitectureTest` (ArchUnit):

- контроллеры не знают про Temporal;
- HTTP-контракт (`dto`, контроллеры) не зависит от данных контракта workflow — изменение контракта с WDS
  не меняет API фронта незаметно;
- SDK Temporal — только в пакете `temporal`;
- мапперы не публичны, фичи не зависят друг от друга.

## OpenAPI

`openapi/bff-lk-openapi.json` — спецификация для ревью (Swagger UI: `/bff-lk/swagger-ui.html`).
Тексты примеров ошибок берутся из `messages.properties` (`@ErrorExample`, `@OutcomeExample`,
`@ConsentsRequired`), то есть совпадают с ответами API. `OpenApiContractTest` сверяет спецификацию
с `docs/plan/v2`: каждый метод и каждый код из таблиц «Коды ответов» описан; отдельная проверка сверяет сохранённый JSON с runtime. Обновить файл:
`mvn test` (проверка сохранённого JSON сейчас отключена в `OpenApiContractTest`).

## Эксплуатация

| Что | Где |
| --- | --- |
| Живость | `/bff-lk/actuator/health/liveness` — не зависит от Temporal |
| Готовность | `/bff-lk/actuator/health/readiness` — включает Temporal (`DescribeNamespace`, 2 с) |
| Метрики | `/bff-lk/actuator/prometheus`: `bff_workflow_seconds{workflow,operation,outcome}`, `cache_gets_total{cache=bff.access/bff.catalog,result}`, `temporal_*` (SDK) |
| Остановка | graceful, `BFF_SHUTDOWN_TIMEOUT` (30 с) — дать открытым SSE завершиться |
| Нагрузка и сквозные проверки | [e2e/README.md](e2e/README.md), [docs/fact/performance.md](../docs/fact/performance.md) |

## Сборка

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 25)"  # macOS, установленный JDK 25
export PATH="$JAVA_HOME/bin:$PATH"
mvn clean verify
```

Java 25, Spring Boot 4.1.2, Temporal SDK 1.40.0, springdoc 3.2.0, ArchUnit 1.5.1,
Lombok 1.18.48, Caffeine 3.3.1. Версии транзитивных зависимостей согласованы BOM Spring Boot.
Нужны доступные через Nexus или локальный Maven-репозиторий корпоративные артефакты
`bff-lk-workflow-api:1.0.0`, `sb-broker-security-starter:1.0.0`,
`sb-broker-temporal-encryption-starter:1.0.0`. Для Nexus используется `mvn -s settings.xml ...`
с действующими учётными данными; файл содержит шаблон.

Метрики подключены через Actuator и `micrometer-registry-prometheus`. Корпоративный
`sb-broker-starter-observability:1.1.0` содержит встроенные классы старого Spring и вызывает
`NoSuchMethodError` с актуальным Spring Boot, поэтому не используется.
Настройки `custom.micrometer.application-name` и `custom.micrometer.environment` сохранены
как источники общих тегов метрик.

`security-starter:1.0.0` ссылается на прежнее имя автоконфигурации OAuth2 и выдаёт
предупреждение на Boot 4.1. Цепочка безопасности явно задана в `SecurityConfiguration`: JWT,
ответ 401 в JSON, публичные пробы и Swagger. Обходная настройка метрик Temporal удалена:
Temporal 1.40.0 создаёт соответствующий бин сам.

## Запуск локально

```bash
docker compose up -d   # Temporal dev server + UI на :8233
TEMPORAL_URL=localhost:7233 TEMPORAL_NAMESPACE=quotation-calculation mvn spring-boot:run
# или после сборки:
TEMPORAL_URL=localhost:7233 TEMPORAL_NAMESPACE=quotation-calculation java -jar target/app.jar
```

Workflow исполняет workflow-definition-service, активити — сервисы брокера; без них workflow будут ждать
worker'ов. Swagger UI: `http://localhost:8080/bff-lk/swagger-ui.html`.

## Переменные окружения

| Переменная | По умолчанию | Что |
| --- | --- | --- |
| `TEMPORAL_URL` | `temporal.cce-apps.dev.broker-sb.tech:80` | Адрес Temporal |
| `TEMPORAL_NAMESPACE` | `dev` | Namespace окружения (`dev`, `psi`, `prod`), один у всех сервисов |
| `BFF_WORKFLOW_TASK_QUEUE` | `workflow-definition-tq` | Очередь worker'а WDS |
| `BFF_WORKFLOW_AWAIT_TIMEOUT` | `30s` | Ожидание синхронных workflow |
| `BFF_LEAD_AWAIT_TIMEOUT` | `60s` | Ожидание лида, затем `204` |
| `KEYCLOAK_ISSUER_URI` | `https://dev-keycloak.broker-sb.ru/realms/lk` | Ожидаемый `iss` — публичный адрес realm, как его видит браузер |
| `KEYCLOAK_JWK_SET_URI` | `${KEYCLOAK_ISSUER_URI}/protocol/openid-connect/certs` | Откуда брать ключи подписи; внутренний адрес Keycloak, если публичный из кластера недоступен |
| `KEYCLOAK_JWS_ALGORITHMS` | `RS256` | Алгоритмы подписи realm (через запятую) |
| `KEYCLOAK_AUDIENCES` | `account` | Допустимые `aud` |
| `TEMPORAL_ENCRYPTION_MODE` | `DISABLED` | `DISABLED` / `DECRYPT_ONLY` / `ENCRYPT` |
| `TEMPORAL_ENCRYPTION_KEY_ID`, `TEMPORAL_ENCRYPTION_KEY_K1` | — | Активный ключ и ключ AES-256 в Base64 |
| `BFF_CONTEXT_PATH` | `/bff-lk` | Префикс путей API |
| `BFF_ACCESS_CACHE_TTL` | `10s` | Кэш выданного допуска; `0s` — выключен |
| `BFF_CATALOG_CACHE_TTL` | `5m` | Кэш справочников; `0s` — выключен |
| `BFF_OPENAPI_ENABLED` | `true` | Публиковать OpenAPI и Swagger UI |
| `BFF_SHUTDOWN_TIMEOUT` | `30s` | Graceful shutdown |
| `BFF_SSE_HEARTBEAT` | `15s` | Комментарий в молчащий SSE-поток; меньше read timeout ingress (ingress-nginx — 60s), `0s` — выключен |
| `BFF_CALCULATION_OWNER_KEY` | только для локального запуска | Секрет HMAC владельца в `workflowId` предрасчёта; **обязателен в окружении**, один на все поды и ЦОД |
| `BFF_CALCULATION_GATE_TIMEOUT` | `60s` | Сколько POST предрасчёта ждёт «ворота» (затем `204`) |
| `BFF_CALCULATION_PREVIEW_TIMEOUT` | `15m` | Сколько GET предрасчёта ждёт предложения; меньше `bff.sse.timeout` |
| `BFF_CALCULATION_SELECTION_TIMEOUT` | `60s` | Сколько выбор предложения ждёт итог (затем `204`) |
| `BFF_ENVIRONMENT` | `local` | Тег `environment` у метрик |

Остальное (таймауты и префиксы каждого workflow, сценарии лида, тексты) — в
`src/main/resources/application.yaml` и `messages.properties`.

## Тесты

- `WorkflowGatewayTest` — запуск, ожидание, ошибки, `USE_EXISTING`, метрики на тестовом сервере Temporal.
- `*ApiTest` — HTTP-слой целиком (JWT, валидация, коды и тела ответов, SSE, кэши) с подменой Temporal.
- `OpenApiContractTest` — спецификация против `docs/plan`; `MessagesCompletenessTest` — тексты для всех кодов.
- `ArchitectureTest` — архитектурные правила; `OperationsTest` — пробы k8s.
- Сквозные, отказы, контракты сервисов, нагрузка — `e2e/`.

## Общая проверка JWT

`sb-broker-security-starter` предоставляет stateless Bearer-цепочку, стандартную проверку JWT и
`sb.broker.security.AuthenticatedUser`. BFF сохраняет собственный `JsonAuthenticationEntryPoint`
и обработчик AuthenticationException для ответа `401 {"message":"Нет доступа"}`.
Допуск кабинета и согласия по-прежнему проверяет `LkAccessService` через Temporal.

Публичные пути и имя email claim: `sb.broker.security.public-paths` и `sb.broker.security.email-claim`.
Прежний `bff.security.email-claim` сохранён как fallback в YAML; `bff.security.public-paths` заменён новым
именем. Настройки `KEYCLOAK_*` и HTTP-контракт не изменились. Стартер требует непустые issuer и audience,
а токен — exp. Подробности и подключение в других сервисах: [стартер](../others/security-starter/README.md).
