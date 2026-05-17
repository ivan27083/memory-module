# Memory Module

![Java](https://img.shields.io/badge/Java-24-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5-green)
![Status](https://img.shields.io/badge/status-Active-blue)

Внешний сервис памяти для AI-агентов, построенный на основе гексагональной архитектуры (ports & adapters). Предоставляет три типа памяти (рабочую, эпизодическую, семантическую), векторный поиск, граф связей и автоматическое обслуживание.

---

## Технологический стек

| Компонент | Технология |
| --------- | ---------- |
| Runtime | Java 24 + Spring Boot 3.3.5 |
| Эпизодическая память | PostgreSQL (JDBC + Flyway) |
| Рабочая память | Redis |
| Граф связей | Kuzu (embedded) |
| Векторный поиск | Qdrant |
| Embeddings | OpenAI-совместимый API (LM Studio) |
| Метрики | Micrometer + Prometheus |
| Event Store | In-memory (по умолчанию) |

---

## Архитектура

```text
┌─────────────────────────────────────────────────┐
│           HTTP Adapters (in/web)                │
│   MemoryController  │  McpController            │
└──────────┬──────────┴───────────────────────────┘
           │
┌──────────▼──────────────────────────────────────┐
│           DefaultMemoryFacade (Application)     │
│  write() → persist + consolidate + event        │
│  retrieve() → RetrievalOrchestrator + event     │
└──┬──────────┬───────────────┬───────────────────┘
   │          │               │
┌──▼──┐  ┌───▼──────┐  ┌─────▼──────────────────┐
│Redis│  │PostgreSQL│  │  RetrievalOrchestrator │
│(WM) │  │(Episodic)│  │  Working + Episodic +  │
└─────┘  └──────────┘  │  SemanticWiki + Vector │
                       └────────────┬───────────┘
                                    │
                             ┌──────▼──────┐
                             │   Qdrant    │
                             │  (Vectors)  │
                             └─────────────┘
```

Запись в `DefaultMemoryFacade.write()` сохраняет запись в репозиторий (Redis / PostgreSQL / SemanticWiki) и асинхронно запускает `MemoryConsolidationService`, который векторизует запись и отправляет в Qdrant.

---

## Быстрый старт

### Требования

- Java 24
- Maven 3.8+
- PostgreSQL 15+
- Redis
- Qdrant
- OpenAI-совместимый embedding-сервер (например, LM Studio)

### Запуск

```bash
mvn spring-boot:run
```

API доступен на `http://localhost:8088`.

### Docker Compose

```bash
docker compose up -d
```

Запускает PostgreSQL (порт 55432), Redis и Qdrant. Приложение запускается отдельно через Maven или IDE.

---

## Переменные окружения

Все значения по умолчанию заданы в `application.yml`. Переопределяются через файл `.env` или переменные окружения.

| Переменная | Умолчание | Описание |
| ---------- | --------- | -------- |
| `MEMORY_DB_URL` | `jdbc:postgresql://localhost:55432/memory_module` | PostgreSQL |
| `MEMORY_DB_USERNAME` | `memory` | Пользователь БД |
| `MEMORY_DB_PASSWORD` | `memory` | Пароль БД |
| `MEMORY_REDIS_HOST` | `localhost` | Redis host |
| `MEMORY_REDIS_PORT` | `6379` | Redis port |
| `MEMORY_QDRANT_URL` | `http://localhost:6333` | Qdrant |
| `MEMORY_EMBEDDING_BASE_URL` | `http://localhost:1234/v1` | Embedding API |
| `MEMORY_EMBEDDING_MODEL` | `text-embedding-granite-embedding-278m-multilingual` | Модель |
| `MEMORY_VECTOR_DIMENSIONS` | `768` | Размерность вектора |
| `MEMORY_EPISODIC_RETENTION` | `P30D` | Срок хранения эпизодов |
| `MEMORY_CLEANUP_CRON` | `0 0 3 * * *` | Расписание очистки |
| `GRAPH_BACKEND` | `kuzu` | `kuzu` или `in-memory` |
| `KUZU_DB_PATH` | `./data/kuzu_graph` | Путь к Kuzu БД |
| `QMD_ENABLED` | `false` | Включить QMD-sidecar |

Скопируйте `.env.example` в `.env` и настройте значения для своей среды.

---

## REST API

### `POST /api/memory/write` → 201

Записывает новую единицу памяти.

```json
{
  "agentId": "agent-dev",
  "sessionId": "session-1",
  "type": "EPISODIC",
  "content": "Пользователь сообщил, что prefer короткие ответы.",
  "metadata": { "source": "chat" }
}
```

Типы памяти: `WORKING`, `EPISODIC`, `SEMANTIC_WIKI`.

### `POST /api/memory/retrieve` → 200

Гибридный поиск (рабочая + эпизодическая + семантическая + векторная).

```json
{
  "agentId": "agent-dev",
  "sessionId": "session-1",
  "prompt": "предпочтения пользователя",
  "limit": 5
}
```

### `POST /api/rag/ingest` → 200

Индексирует внешний документ в векторное хранилище.

```json
{
  "source": "https://docs.example.com/guide",
  "title": "Руководство пользователя",
  "content": "...",
  "metadata": {}
}
```

---

## MCP-инструменты

| Endpoint | Статус | Описание |
| -------- | ------ | -------- |
| `POST /mcp/memory.search` | Реализован | Поиск памяти по запросу |
| `POST /mcp/memory.store` | Реализован | Запись памяти |
| `POST /mcp/memory.update` | 501 | Запланировано |
| `POST /mcp/memory.delete` | 501 | Запланировано |
| `POST /mcp/memory.timeline` | 501 | Запланировано |
| `POST /mcp/memory.conflicts` | 501 | Запланировано |
| `POST /mcp/memory.explain` | 501 | Запланировано |
| `POST /mcp/memory.forget` | 501 | Запланировано |
| `POST /mcp/memory.pin` | 501 | Запланировано |

---

## OpenClaw плагин

`openclaw-memory-module-http/` содержит JS-плагин для OpenClaw, который:

- Автоматически получает память перед построением промпта (`before_prompt_build`)
- Автоматически сохраняет каждый агентский ход (`agent_end`)
- Предоставляет инструменты `memory_module_retrieve`, `memory_module_write`, `memory_module_rag_ingest`

Настройка в конфиге OpenClaw:

```json
{
  "plugins": {
    "memory-module-http": {
      "baseUrl": "http://localhost:8088",
      "agentId": "my-agent",
      "retrieveLimit": 5
    }
  }
}
```

---

## Тестирование

```bash
mvn test
```

Тесты не требуют внешних сервисов — используют фейковые in-memory реализации портов.

---

## Диагностика

**`SQL State: 28P01`** — неверный пароль PostgreSQL. PostgreSQL сервис проекта запускается на порту `55432`. Убедитесь, что `MEMORY_DB_URL` указывает на него, а не на системный PostgreSQL на `5432`.

**`/api/memory/retrieve` возвращает пустой массив** — проверьте, запущен ли embedding-сервер:

```powershell
Invoke-RestMethod http://localhost:1234/v1/models
```

Имя модели в `MEMORY_EMBEDDING_MODEL` должно совпадать с `id` загруженной модели. Размерность модели должна совпадать с `MEMORY_VECTOR_DIMENSIONS`; для уже созданной Qdrant-коллекции изменить размерность нельзя без её пересоздания.

**Кириллица отображается как `????`** — используйте явную кодировку UTF-8 в PowerShell:

```powershell
Invoke-RestMethod `
  -Uri "http://localhost:8088/api/memory/write" `
  -Method Post `
  -ContentType "application/json; charset=utf-8" `
  -Body ([System.Text.Encoding]::UTF8.GetBytes($json))
```

---

## Стратегия интеграции

OpenClaw обращается к модулю через тонкий HTTP-адаптер (`openclaw-memory-module-http`), который отображает события жизненного цикла агента на REST-вызовы:

- `POST /api/memory/write`
- `POST /api/memory/retrieve`
- `POST /api/rag/ingest`

При появлении новых интерфейсов OpenClaw добавьте отдельный inbound-адаптер рядом с `adapter/in/web`, не связывая логику памяти напрямую с ядром runtime.
